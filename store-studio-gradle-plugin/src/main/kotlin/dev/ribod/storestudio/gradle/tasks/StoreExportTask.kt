package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.core.catalog.DefaultStoreSpecCatalog
import dev.ribod.storestudio.core.hash.AssetHasher
import dev.ribod.storestudio.core.image.ImageProcessor
import dev.ribod.storestudio.core.image.SizeMismatchPolicy
import dev.ribod.storestudio.gradle.internal.ExportPaths
import dev.ribod.storestudio.model.ExportAsset
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.ExportLayout
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.RenderIndex
import dev.ribod.storestudio.model.SemanticsSnapshot
import dev.ribod.storestudio.model.StoreManifest
import dev.ribod.storestudio.model.withFallback
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.imageio.ImageIO
import javax.inject.Inject

/**
 * Validates dimensions, removes alpha transparency, and exports store-ready assets.
 */
@CacheableTask
public abstract class StoreExportTask : DefaultTask() {

    @get:Inject
    public abstract val fileSystemOperations: FileSystemOperations

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val renderIndex: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val rawImagesDir: DirectoryProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val mergedManifest: RegularFileProperty

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val semanticsDir: DirectoryProperty

    /** Robolectric fallback results, replacing the Layoutlib jobs with the same id. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val fallbackIndexFile: RegularFileProperty

    /** Images of the jobs rendered by the fallback engine. */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val fallbackImagesDir: DirectoryProperty

    /** Semantics snapshots of the jobs rendered by the fallback engine. */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val fallbackSemanticsDir: DirectoryProperty

    @get:Input
    public abstract val backgroundColor: Property<String>

    @get:Input
    public abstract val sizeTolerancePx: Property<Int>

    @get:Input
    public abstract val sizeMismatchPolicy: Property<SizeMismatchPolicy>

    @get:Input
    public abstract val jpegFallback: Property<Boolean>

    @get:Input
    public abstract val jpegQuality: Property<Float>

    @get:Input
    public abstract val layout: Property<ExportLayout>

    @get:Input
    public abstract val lowercaseFolders: Property<Boolean>

    @get:OutputDirectory
    public abstract val exportDirectory: DirectoryProperty

    @get:OutputFile
    public abstract val exportIndexFile: RegularFileProperty

    init {
        group = "store studio"
        description = "Processes, normalizes, and exports store-compliant visual assets."
    }

    @TaskAction
    public fun export() {
        val bgHex = backgroundColor.get()
        val bgRgb = ImageProcessor.parseHexColor(bgHex)
        val tolerance = sizeTolerancePx.get()
        require(tolerance in 0..2) {
            "storeStudio.export.sizeTolerancePx must be between 0 and 2 (found: $tolerance). " +
                "Store Studio never silently rescales."
        }
        val policy = sizeMismatchPolicy.get()
        val allowFallback = jpegFallback.get()
        val initialJpegQuality = jpegQuality.get()
        val exportLayout = layout.get()
        val lowercase = lowercaseFolders.get()

        val exportDir = exportDirectory.get().asFile
        fileSystemOperations.delete { spec -> spec.delete(exportDir) }
        exportDir.mkdirs()

        val fallbackIndex = fallbackIndexFile.orNull?.asFile?.takeIf { it.isFile }
            ?.let { RenderIndex.decodeFromString(it.readText()) }
        val parsedRenderIndex = RenderIndex.decodeFromString(renderIndex.get().asFile.readText())
            .withFallback(fallbackIndex)
        val successfulJobs = parsedRenderIndex.jobs.filter {
            it.status == "SUCCESS" && !it.image.isNullOrBlank()
        }

        val orderLookup = if (mergedManifest.isPresent && mergedManifest.get().asFile.exists()) {
            runCatching {
                StoreManifest.decodeFromString(
                    mergedManifest.get().asFile.readText(),
                ).entries.associate {
                    it.id to it.order
                }
            }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

        val catalog = DefaultStoreSpecCatalog
        val rawRoot = rawImagesDir.get().asFile
        val semanticsRoot = semanticsDir.orNull?.asFile
        val fallbackRawRoot = fallbackImagesDir.orNull?.asFile
        val fallbackSemanticsRoot = fallbackSemanticsDir.orNull?.asFile
        // Images are independent: decode, flatten, hash and encode them in parallel. Bounded,
        // since each in-flight image holds a few full-size rasters on the daemon heap.
        val pool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_PARALLEL_IMAGES),
        )
        val pending = mutableListOf<Future<ExportAsset>>()

        val buckets = successfulJobs.groupBy { job ->
            Triple(job.target, job.deviceSlot, job.storeLocale)
        }
        for ((bucketTriple, bucketJobs) in buckets) {
            val (target, deviceSlot, storeLocale) = bucketTriple
            val sortedJobs = bucketJobs.sortedWith(
                compareBy(
                    { orderLookup[it.entryId] ?: 0 },
                    { it.entryId },
                ),
            )

            for ((index, job) in sortedJobs.withIndex()) {
                pending += pool.submit<ExportAsset> {
                    val position = index + 1
                    val fileStem = "%02d_%s".format(position, job.entryId)
                    val byFallback = job.engine == "ROBOLECTRIC" && fallbackRawRoot != null
                    val rawFile = File(if (byFallback) fallbackRawRoot else rawRoot, job.image!!)
                    check(rawFile.exists()) { "Rendered raw file does not exist: ${rawFile.path}" }

                    val rawImage = ImageIO.read(rawFile)
                        ?: error("Failed to decode raw PNG image at: ${rawFile.path}")

                    val slotSpec = catalog[job.deviceSlot]
                    val expectedSize = slotSpec.defaultGeometry.size

                    val adjustedResult = ImageProcessor.adjustSize(
                        src = rawImage,
                        expectedWidth = expectedSize.width,
                        expectedHeight = expectedSize.height,
                        tolerancePx = tolerance,
                        policy = policy,
                        backgroundRgb = bgRgb,
                    )

                    val flattenResult = ImageProcessor.flatten(adjustedResult.image, bgRgb)
                    val finalImage = flattenResult.image

                    val pixelSha256 = AssetHasher.pixelSha256(finalImage)
                    val pHash = AssetHasher.pHash(finalImage)

                    val pngBuffer = ByteArrayOutputStream()
                    ImageProcessor.writePng24(finalImage, pngBuffer)
                    val pngBytes = pngBuffer.toByteArray()

                    val maxBytes = slotSpec.maxBytes
                    val (format, extension, finalBytes) = if (
                        maxBytes != null &&
                        pngBytes.size > maxBytes &&
                        allowFallback
                    ) {
                        var quality = initialJpegQuality
                        var chosenBytes: ByteArray? = null
                        while (quality >= 0.80f) {
                            val jpegBuffer = ByteArrayOutputStream()
                            ImageProcessor.writeJpeg(finalImage, jpegBuffer, quality)
                            val candidate = jpegBuffer.toByteArray()
                            if (candidate.size <= maxBytes) {
                                chosenBytes = candidate
                                break
                            }
                            quality -= 0.04f
                        }
                        if (chosenBytes != null) {
                            Triple(ImageFormat.JPEG, "jpg", chosenBytes)
                        } else {
                            val fallbackBuffer = ByteArrayOutputStream()
                            ImageProcessor.writeJpeg(finalImage, fallbackBuffer, 0.80f)
                            Triple(ImageFormat.JPEG, "jpg", fallbackBuffer.toByteArray())
                        }
                    } else {
                        Triple(ImageFormat.PNG, "png", pngBytes)
                    }

                    val slotName = if (lowercase) deviceSlot.name.lowercase() else deviceSlot.name
                    val relativePath = "%s/%s/%s.%s".format(
                        ExportPaths.localeFolder(exportLayout, lowercase, target, storeLocale),
                        slotName,
                        fileStem,
                        extension,
                    )
                    val destFile = File(exportDir, relativePath)
                    destFile.parentFile.mkdirs()
                    destFile.writeBytes(finalBytes)

                    val fileSha256 = AssetHasher.fileSha256(destFile)
                    val md5 = AssetHasher.md5(destFile)

                    // The fallback's snapshot when it has one, otherwise Layoutlib's (as before
                    // the engines had separate outputs)
                    val snapshotFile = listOfNotNull(
                        fallbackSemanticsRoot.takeIf { byFallback },
                        semanticsRoot,
                    ).map { it.resolve("${job.jobId}.json") }.firstOrNull { it.isFile }
                    val semanticsSnapshot = snapshotFile?.let {
                        runCatching { SemanticsSnapshot.fromFile(it) }.getOrNull()
                    }

                    ExportAsset(
                        slotKey = job.slotKey,
                        target = job.target,
                        deviceSlot = job.deviceSlot,
                        storeLocale = job.storeLocale,
                        id = job.entryId,
                        order = orderLookup[job.entryId] ?: 0,
                        position = position,
                        file = relativePath,
                        format = format,
                        widthPx = finalImage.width,
                        heightPx = finalImage.height,
                        bytes = destFile.length(),
                        hasAlpha = false,
                        colorSpace = "sRGB",
                        pixelSha256 = pixelSha256,
                        fileSha256 = fileSha256,
                        pHash = pHash,
                        md5 = md5,
                        engine = job.engine,
                        textSha256 = semanticsSnapshot?.textSha256,
                        extractedTexts = semanticsSnapshot?.nodes?.flatMap { node ->
                            node.texts + listOfNotNull(node.contentDescription)
                        } ?: emptyList(),
                        hasVisualOverflow = semanticsSnapshot?.hasAnyVisualOverflow ?: false,
                    )
                }
            }
        }

        val exportedAssets = try {
            pending.map { future ->
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        } finally {
            pool.shutdownNow()
        }

        val sortedAssets = exportedAssets.sortedBy { it.slotKey }
        val finalIndex = ExportIndex(schemaVersion = 1, assets = sortedAssets)
        finalIndex.toFile(exportIndexFile.get().asFile)

        logger.lifecycle(
            "Store Studio: Exported ${sortedAssets.size} compliant asset(s) to ${exportDir.path}.",
        )
    }

    private companion object {
        /** Upper bound for images processed at once (each holds several full-size rasters). */
        const val MAX_PARALLEL_IMAGES = 4
    }
}
