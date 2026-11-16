package dev.ribod.storestudio.idea.io

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import dev.ribod.storestudio.idea.model.DiffDocumentDto
import dev.ribod.storestudio.idea.model.ExportAssetDto
import dev.ribod.storestudio.idea.model.ExportIndexDto
import dev.ribod.storestudio.idea.model.MergedManifestDto
import dev.ribod.storestudio.idea.model.ModuleStoreArtifacts
import dev.ribod.storestudio.idea.model.RenderIndexDto
import dev.ribod.storestudio.idea.model.RenderJobDto
import dev.ribod.storestudio.idea.model.StoreAssetCard
import dev.ribod.storestudio.idea.model.StoreMetadataManifestDto
import dev.ribod.storestudio.idea.model.ValidationReportDto
import dev.ribod.storestudio.idea.model.ViolationDto
import java.io.File
import java.util.concurrent.CancellationException

/**
 * Discovers and deserializes Store Studio build artifacts across all Gradle modules in the project.
 *
 * Pure, thread-agnostic I/O used by [StoreArtifactsService], which owns reload scheduling.
 */
public object StoreArtifactReader {

    private val gson = Gson()
    private val LOG = logger<StoreArtifactReader>()

    /**
     * Discovers all modules in the project containing `build/store/` and parses their artifacts.
     */
    public fun loadAll(project: Project): List<ModuleStoreArtifacts> {
        val candidateDirs = discoverModuleStoreDirs(project)
        return candidateDirs.mapNotNull { (moduleName, storeDir) ->
            loadModuleArtifacts(moduleName, storeDir)
        }
    }

    /**
     * Flattens and unifies assets from exported outputs, raw render fallback, and manifests
     * into a list of [StoreAssetCard]s ready for visual display in the Gallery.
     */
    public fun computeAssetCards(artifacts: List<ModuleStoreArtifacts>): List<StoreAssetCard> {
        val cards = mutableListOf<StoreAssetCard>()

        for (module in artifacts) {
            val manifestEntries = module.manifest?.entries?.associateBy { it.id } ?: emptyMap()
            val violationsBySlot =
                module.validationReport?.violations?.groupBy { it.slotKey ?: "" } ?: emptyMap()
            val generalViolations =
                module.validationReport?.violations?.filter { it.slotKey.isNullOrBlank() }
                    ?: emptyList()

            // 1. Process exported assets first (final production deliverables)
            val exportDir = File(module.buildStoreDir, "export")
            val exportedSlots = mutableSetOf<String>()

            if (module.exportIndex != null) {
                for (asset in module.exportIndex.assets) {
                    val imageFile = exportDir.childWithin(asset.file)
                    if (imageFile != null && imageFile.isFile) {
                        val entry = manifestEntries[asset.id]
                        val cardViolations = (violationsBySlot[asset.slotKey] ?: emptyList()) +
                            generalViolations.filter {
                                (it.target == null || it.target == asset.target) &&
                                    (it.deviceSlot == null || it.deviceSlot == asset.deviceSlot) &&
                                    (it.storeLocale == null || it.storeLocale == asset.storeLocale)
                            }

                        cards.add(
                            StoreAssetCard(
                                moduleName = module.moduleName,
                                slotKey = asset.slotKey,
                                target = asset.target,
                                deviceSlot = asset.deviceSlot,
                                storeLocale = asset.storeLocale,
                                id = asset.id,
                                title = entry?.title?.takeIf { it.isNotBlank() } ?: asset.id,
                                order = asset.order,
                                imageFile = imageFile,
                                widthPx = asset.widthPx,
                                heightPx = asset.heightPx,
                                engine = asset.engine,
                                functionFqn = entry?.functionFqn,
                                functionName = entry?.functionName,
                                sourceLocation = entry?.source,
                                violations = cardViolations,
                            ),
                        )
                        exportedSlots.add(asset.slotKey)
                    }
                }
            }

            // 2. Process raw rendered assets if not yet exported
            // (e.g. midway during task execution)
            if (module.renderIndex != null) {
                val renderRawDir = File(module.buildStoreDir, "render/raw")
                val fallbackRawDir = File(module.buildStoreDir, "render/robolectric/raw")
                for (job in module.renderIndex.jobs) {
                    if (job.slotKey !in exportedSlots && job.status == "SUCCESS") {
                        val rawDir = if (job.engine ==
                            "ROBOLECTRIC"
                        ) {
                            fallbackRawDir
                        } else {
                            renderRawDir
                        }
                        val imageFile = rawDir.childWithin(job.image)
                        if (imageFile != null && imageFile.isFile) {
                            val entry = manifestEntries[job.entryId]
                            cards.add(
                                StoreAssetCard(
                                    moduleName = module.moduleName,
                                    slotKey = job.slotKey,
                                    target = job.target,
                                    deviceSlot = job.deviceSlot,
                                    storeLocale = job.storeLocale,
                                    id = job.entryId,
                                    title = entry?.title?.takeIf { it.isNotBlank() } ?: job.entryId,
                                    order = entry?.order ?: 0,
                                    imageFile = imageFile,
                                    widthPx = job.widthPx,
                                    heightPx = job.heightPx,
                                    engine = job.engine,
                                    functionFqn = entry?.functionFqn,
                                    functionName = entry?.functionName,
                                    sourceLocation = entry?.source,
                                    violations = violationsBySlot[job.slotKey] ?: emptyList(),
                                ),
                            )
                        }
                    }
                }
            }
        }

        return cards.sortedWith(
            compareBy(
                { it.moduleName },
                { it.target },
                { it.deviceSlot },
                { it.storeLocale },
                { it.order },
                { it.id },
            ),
        )
    }

    internal fun loadModuleArtifacts(moduleName: String, storeDir: File): ModuleStoreArtifacts? {
        if (!storeDir.isDirectory) return null

        val manifestFile = File(storeDir, "manifest/merged.json")
        val renderFile = File(storeDir, "render/index.json")
        val exportFile = File(storeDir, "export/index.json")
        val validationFile = File(storeDir, "validation/report.json")
        val diffFile = File(storeDir, "diff/diff.json")
        val metadataFile = File(storeDir, "metadata/metadata.json")

        val manifest = parseJsonSafe(manifestFile, MergedManifestDto::class.java)
        // Robolectric fallback results live next to Layoutlib's and replace the same job ids
        val fallbackIndex =
            parseJsonSafe(
                File(storeDir, "render/robolectric/index.json"),
                RenderIndexDto::class.java,
            )
        val renderIndex = parseJsonSafe(renderFile, RenderIndexDto::class.java)
            ?.withFallback(fallbackIndex)
        val exportIndex = parseJsonSafe(exportFile, ExportIndexDto::class.java)
        val validationReport = parseJsonSafe(validationFile, ValidationReportDto::class.java)
        val diffDocument = parseJsonSafe(diffFile, DiffDocumentDto::class.java)
        val metadataManifest = parseJsonSafe(metadataFile, StoreMetadataManifestDto::class.java)

        val moduleDir = storeDir.parentFile?.parentFile ?: storeDir

        return ModuleStoreArtifacts(
            moduleName = moduleName,
            moduleDir = moduleDir,
            buildStoreDir = storeDir,
            manifest = manifest,
            renderIndex = renderIndex,
            exportIndex = exportIndex,
            validationReport = validationReport,
            diffDocument = diffDocument,
            metadataManifest = metadataManifest,
        )
    }

    private fun <T> parseJsonSafe(file: File, clazz: Class<T>): T? {
        if (!file.isFile || file.length() == 0L) return null
        return try {
            file.reader().use { gson.fromJson(it, clazz) }
        } catch (e: Exception) {
            if (e is ControlFlowException || e is CancellationException) throw e
            // Gradle may still be writing the file; the next reload picks it up
            LOG.warn("Store Studio: could not read ${file.path}: ${e.message}")
            null
        }
    }

    private fun discoverModuleStoreDirs(project: Project): Map<String, File> {
        val result = LinkedHashMap<String, File>()

        // 1. Primary route: query IntelliJ ModuleManager
        // The module list is a thread-safe snapshot; no read lock is required
        val modules = ModuleManager.getInstance(project).modules
        for (module in modules) {
            val modulePath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: continue
            val storeDir = File(modulePath, "build/store")
            if (storeDir.isDirectory && storeDir.list()?.isNotEmpty() == true) {
                val moduleName = formatModuleName(modulePath, project.basePath)
                result[moduleName] = storeDir
            }
        }

        // 2. Fallback scan if external system models are not loaded
        if (result.isEmpty()) {
            val basePath = project.basePath?.let { File(it) }
            if (basePath != null && basePath.isDirectory) {
                scanDirForStoreOutputs(basePath, basePath, result, depth = 0)
            }
        }

        return result
    }

    private fun scanDirForStoreOutputs(
        dir: File,
        projectRoot: File,
        result: MutableMap<String, File>,
        depth: Int,
    ) {
        if (depth > 5) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (!child.isDirectory) continue
            if (child.name == "build") {
                val storeDir = File(child, "store")
                if (storeDir.isDirectory && storeDir.list()?.isNotEmpty() == true) {
                    val modulePath = dir.absolutePath
                    val moduleName = formatModuleName(modulePath, projectRoot.absolutePath)
                    result[moduleName] = storeDir
                }
            } else if (child.name !in IGNORED_DIRS) {
                scanDirForStoreOutputs(child, projectRoot, result, depth + 1)
            }
        }
    }

    private fun formatModuleName(modulePath: String, basePath: String?): String {
        if (basePath == null) return File(modulePath).name
        val relative =
            File(modulePath).relativeToOrNull(File(basePath))?.path ?: File(modulePath).name
        return if (relative.isEmpty() || relative == ".") {
            File(basePath).name
        } else {
            relative.replace(File.separatorChar, ':').let {
                if (!it.startsWith(":")) ":$it" else it
            }
        }
    }

    private val IGNORED_DIRS =
        setOf(".gradle", ".git", ".idea", "build", "node_modules", "src", "out")

    private fun RenderIndexDto.withFallback(fallback: RenderIndexDto?): RenderIndexDto {
        if (fallback == null || fallback.jobs.isEmpty()) return this
        val fallbackById = fallback.jobs.associateBy { it.jobId }
        return copy(jobs = jobs.map { fallbackById[it.jobId] ?: it })
    }
}

/**
 * Resolves [relative] under this directory, or returns null when it is absolute or escapes this
 * directory via `..` or a symlink. Build output is untrusted JSON, so paths must stay contained.
 */
internal fun File.childWithin(relative: String): File? {
    if (File(relative).isAbsolute) return null
    val base = canonicalFile
    val child = File(this, relative)
    return child.takeIf { it.canonicalPath.startsWith(base.path + File.separator) }
}
