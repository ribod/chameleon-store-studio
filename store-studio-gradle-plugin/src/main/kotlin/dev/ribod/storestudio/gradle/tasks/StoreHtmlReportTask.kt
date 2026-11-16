package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.core.metadata.StoreMetadataManifest
import dev.ribod.storestudio.core.metadata.StoreMetadataParser
import dev.ribod.storestudio.core.report.EmbeddedImage
import dev.ribod.storestudio.core.report.ImageEmbedder
import dev.ribod.storestudio.core.report.ReportImageMode
import dev.ribod.storestudio.core.report.model.AssetStatus
import dev.ribod.storestudio.core.report.model.GalleryPageModel
import dev.ribod.storestudio.core.report.model.ReportAssetItem
import dev.ribod.storestudio.core.report.model.ReportSummary
import dev.ribod.storestudio.core.report.model.ReportViolationItem
import dev.ribod.storestudio.core.report.model.StoreListingMetadata
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.Severity
import dev.ribod.storestudio.model.ValidationReport
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Shared implementation of the self-contained HTML reports over exported store assets
 * ([ExportStoreGalleryHtmlTask], [ExportStoreListingPreviewHtmlTask]): both build the same page
 * model and differ only in the generator that renders it.
 */
@CacheableTask
public abstract class StoreHtmlReportTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val exportIndexFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val exportDirectory: DirectoryProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val validationReportFile: RegularFileProperty

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val metadataDirectory: DirectoryProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val metadataFile: RegularFileProperty

    @get:Input
    public abstract val imageMode: Property<ReportImageMode>

    @get:Input
    public abstract val maxThumbnailDimension: Property<Int>

    @get:Input
    public abstract val appName: Property<String>

    @get:Input
    public abstract val developerName: Property<String>

    @get:Input
    public abstract val category: Property<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val appIcon: RegularFileProperty

    /** App Store icon; only the listing preview uses it, other reports show [appIcon]. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val appStoreIcon: RegularFileProperty

    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    private fun iconDataUri(icon: RegularFileProperty): String? {
        val file = icon.orNull?.asFile?.takeIf { it.exists() } ?: return null
        val mime = when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }
        return "data:$mime;base64," + java.util.Base64.getEncoder().encodeToString(file.readBytes())
    }

    /** Report name used in log messages, e.g. "gallery". */
    protected abstract fun reportName(): String

    /** Renders the page model into the report's HTML. */
    protected abstract fun render(model: GalleryPageModel): String

    init {
        group = "store studio"
    }

    @TaskAction
    public fun generate() {
        val indexFile = exportIndexFile.get().asFile
        if (!indexFile.exists()) {
            logger.warn(
                "Export index does not exist at {}, skipping {} report",
                indexFile,
                reportName(),
            )
            return
        }

        val exportIndex = ExportIndex.fromFile(indexFile)
        val validationReport = if (
            validationReportFile.isPresent &&
            validationReportFile.get().asFile.exists()
        ) {
            runCatching {
                ValidationReport.fromFile(validationReportFile.get().asFile)
            }.getOrNull()
        } else {
            null
        }

        val violationsBySlot = validationReport?.violations
            ?.filter { it.slotKey != null }
            ?.groupBy { it.slotKey!! }
            ?: emptyMap()

        val reportFile = outputFile.get().asFile
        val reportDir = reportFile.absoluteFile.parentFile
        reportDir.mkdirs()

        val exportDir = exportDirectory.get().asFile
        val mode = imageMode.get()
        val maxSide = maxThumbnailDimension.get()

        val reportAssets = exportIndex.assets.map { asset ->
            val assetFile = exportDir.resolve(asset.file)
            val relPath = if (assetFile.exists()) {
                reportDir.toPath().relativize(assetFile.toPath()).toString().replace('\\', '/')
            } else {
                asset.file
            }

            val embedded = if (assetFile.exists()) {
                ImageEmbedder.embed(
                    imageFile = assetFile,
                    relativePath = relPath,
                    mode = mode,
                    maxSidePx = maxSide,
                )
            } else {
                EmbeddedImage(
                    dataUri = relPath,
                    fullResRelativePath = relPath,
                    widthPx = asset.widthPx,
                    heightPx = asset.heightPx,
                )
            }

            val slotViolations = violationsBySlot[asset.slotKey].orEmpty().map {
                ReportViolationItem(
                    ruleId = it.ruleId,
                    severity = it.severity,
                    message = it.message,
                    hint = it.hint,
                )
            }

            val status = when {
                slotViolations.any { it.severity == Severity.ERROR } -> AssetStatus.ERROR
                slotViolations.any { it.severity == Severity.WARNING } -> AssetStatus.WARNING
                else -> AssetStatus.VALID
            }

            ReportAssetItem(
                slotKey = asset.slotKey,
                id = asset.id,
                target = asset.target,
                deviceSlot = asset.deviceSlot,
                storeLocale = asset.storeLocale,
                position = asset.position,
                widthPx = asset.widthPx,
                heightPx = asset.heightPx,
                format = asset.format,
                dataUri = embedded.dataUri,
                fullResRelativePath = embedded.fullResRelativePath,
                pixelSha256 = asset.pixelSha256,
                pHash = asset.pHash,
                status = status,
                violations = slotViolations,
                engine = asset.engine,
            )
        }

        val stores = exportIndex.assets.map { it.target.name }.distinct().sorted()
        val devices = exportIndex.assets.map { it.deviceSlot.name }.distinct().sorted()
        val locales = exportIndex.assets.map { it.storeLocale }.distinct().sorted()

        val errorCount = validationReport?.violations?.count { it.severity == Severity.ERROR } ?: 0
        val warningCount = validationReport?.violations
            ?.count { it.severity == Severity.WARNING } ?: 0
        val valid = validationReport?.valid ?: (errorCount == 0)

        val summary = ReportSummary(
            totalAssets = exportIndex.assets.size,
            storeCount = stores.size,
            localeCount = locales.size,
            errorCount = errorCount,
            warningCount = warningCount,
            valid = valid,
        )

        val metaDir = if (metadataDirectory.isPresent) metadataDirectory.get().asFile else null
        val metadataMap = loadMetadataMap(locales, metaDir)

        val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())

        val appIconDataUri = iconDataUri(appIcon)
        val appStoreIconDataUri = iconDataUri(appStoreIcon)

        val model = GalleryPageModel(
            appName = appName.get(),
            developerName = developerName.get(),
            category = category.get(),
            generatedAt = timestamp,
            summary = summary,
            assets = reportAssets,
            stores = stores,
            deviceSlots = devices,
            locales = locales,
            metadataByLocale = metadataMap,
            appIconDataUri = appIconDataUri,
            appStoreIconDataUri = appStoreIconDataUri,
        )

        reportFile.writeText(render(model))
        logger.lifecycle(
            "Store Studio {} report generated: file://{}",
            reportName(),
            reportFile.absolutePath,
        )
    }

    private fun loadMetadataMap(
        locales: List<String>,
        baseMetaDir: File?,
    ): Map<String, StoreListingMetadata> {
        val app = appName.get()
        val dev = developerName.get()
        val cat = category.get()

        val metaFile = if (metadataFile.isPresent && metadataFile.get().asFile.exists()) {
            metadataFile.get().asFile
        } else {
            null
        }

        if (metaFile != null) {
            val manifest = runCatching {
                StoreMetadataManifest.fromFile(metaFile)
            }.getOrNull()
            if (manifest != null && manifest.locales.isNotEmpty()) {
                val map = manifest.toPlayListingMetadataMap(app, dev, cat)
                return locales.associateWith { locale ->
                    map[locale] ?: StoreListingMetadata(
                        title = app,
                        developerName = dev,
                        category = cat,
                    )
                }
            }
        }

        if (baseMetaDir != null && baseMetaDir.exists()) {
            val manifest = StoreMetadataParser.parse(
                metadataDir = baseMetaDir,
                configuredLocales = locales,
            )
            val map = manifest.toPlayListingMetadataMap(app, dev, cat)
            return locales.associateWith { locale ->
                map[locale] ?: StoreListingMetadata(
                    title = app,
                    developerName = dev,
                    category = cat,
                )
            }
        }

        return locales.associateWith {
            StoreListingMetadata(
                title = app,
                developerName = dev,
                category = cat,
            )
        }
    }
}
