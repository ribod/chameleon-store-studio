package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.core.diff.ChangeClassifier
import dev.ribod.storestudio.model.BaselineComparison
import dev.ribod.storestudio.model.BaselineManifest
import dev.ribod.storestudio.model.DiffDocument
import dev.ribod.storestudio.model.DiffRefInfo
import dev.ribod.storestudio.model.ExportIndex
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Compares the current export against a base revision.
 *
 * The base comes from, in order of precedence:
 * 1. an export directory rendered from the base revision ([baseExportDir], `--base-export`);
 * 2. the base revision rendered on this machine in a temporary Git worktree (`--render-base`);
 * 3. the committed baseline at a Git ref (`--against`), at the merge base with the default branch,
 *    or on disk.
 *
 * Options 1 and 2 compare two renders made on the same kind of machine, so exact comparison works
 * without committing a baseline. Conforms to DD-10 and data-contracts.md section 8.
 */
@CacheableTask
public abstract class StoreDiffTask : DefaultTask() {

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @get:Input
    @get:Optional
    public abstract val againstRef: Property<String>

    @get:Input
    public abstract val renderBase: Property<Boolean>

    @get:Input
    public abstract val comparison: Property<BaselineComparison>

    @get:Input
    public abstract val perceptualThreshold: Property<Double>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val headExportIndexFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val headExportDirectory: DirectoryProperty

    @get:Internal
    public abstract val baselineFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val baselineFiles: ConfigurableFileCollection

    /** Export directory (with `index.json`) rendered from the base revision. */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val baseExportDir: DirectoryProperty

    @get:Internal
    public abstract val cacheDirectory: DirectoryProperty

    @get:Internal
    public abstract val projectDir: DirectoryProperty

    /** Root directory of the Gradle build, used to locate the build inside a worktree. */
    @get:Internal
    public abstract val rootProjectDir: DirectoryProperty

    /** Gradle path of this project (e.g. `:app`), whose export is rendered for the base. */
    @get:Input
    public abstract val projectPath: Property<String>

    /** Scratch directory for the base worktree, its build log and its export. */
    @get:Internal
    public abstract val workDirectory: DirectoryProperty

    @get:OutputFile
    public abstract val diffJsonFile: RegularFileProperty

    @get:OutputDirectory
    public abstract val beforeImagesDir: DirectoryProperty

    init {
        group = "store studio"
        description = "Computes visual and text changes against a base revision."
        renderBase.convention(false)
        perceptualThreshold.convention(0.05)
        // Only an explicit base export is a declared input; a Git revision can move without
        // Gradle noticing, so those comparisons are never reused
        outputs.doNotCacheIf("the base revision is read from Git") { !baseExportDir.isPresent }
        outputs.upToDateWhen { baseExportDir.isPresent }
    }

    @Option(
        option = "against",
        description = "Git ref or tag to compare against (e.g. main, v1.2.0).",
    )
    public fun setAgainstOption(against: String) {
        againstRef.set(against)
    }

    @Option(
        option = "render-base",
        description = "Render the base revision on this machine in a temporary Git worktree.",
    )
    public fun setRenderBaseOption(render: Boolean) {
        renderBase.set(render)
    }

    @Option(
        option = "base-export",
        description = "Export directory rendered from the base revision " +
            "(relative to the build root).",
    )
    public fun setBaseExportOption(path: String) {
        baseExportDir.set(rootProjectDir.map { it.dir(path) })
    }

    @TaskAction
    public fun diff() {
        val indexFile = headExportIndexFile.get().asFile
        if (!indexFile.exists()) {
            throw GradleException(
                "Store Studio: Export index not found at '${indexFile.path}'. " +
                    "Ensure storeExport ran successfully.",
            )
        }
        val headExportIndex = ExportIndex.fromFile(indexFile)

        val isInsideWorkTree = runGit("rev-parse", "--is-inside-work-tree").first == 0

        val base = when {
            baseExportDir.isPresent -> {
                val dir = baseExportDir.get().asFile
                exportBase(dir, DiffRefInfo(ref = dir.name, commit = null, source = "BASE_EXPORT"))
            }

            renderBase.get() -> {
                if (!isInsideWorkTree) {
                    throw GradleException(
                        "Store Studio: --render-base needs a Git repository to check out the base.",
                    )
                }
                renderBaseRevision()
            }

            isInsideWorkTree -> resolveGitBaseline()

            else -> resolveFilesystemBaseline()
        }
        val headRef = if (isInsideWorkTree) {
            resolveGitHead()
        } else {
            DiffRefInfo(ref = "WORKING_TREE", commit = null, source = "CURRENT_EXPORT")
        }

        val cacheDir = cacheDirectory.get().asFile
        cacheDir.mkdirs()
        val beforeDir = beforeImagesDir.get().asFile
        beforeDir.deleteRecursively()
        beforeDir.mkdirs()

        for (headAsset in headExportIndex.assets) {
            val src = headExportDirectory.get().asFile.resolve(headAsset.file)
            if (src.exists()) {
                val cached = cacheDir.resolve("${headAsset.pixelSha256}.png")
                if (!cached.exists()) {
                    src.copyTo(cached, overwrite = true)
                }
            }
        }

        val resolvedBaseImages = mutableMapOf<String, String>()
        for (bAsset in base.manifest.assets) {
            val destFile = beforeDir.resolve("${bAsset.slotKey.replace('/', '_')}.png")
            val source = base.images[bAsset.slotKey]?.takeIf { it.exists() }
                ?: cacheDir.resolve("${bAsset.pixelSha256}.png").takeIf { it.exists() }
            resolvedBaseImages[bAsset.slotKey] = if (source != null) {
                source.copyTo(destFile, overwrite = true)
                destFile.absolutePath
            } else {
                "UNAVAILABLE"
            }
        }

        val (summary, changes) = ChangeClassifier.classify(
            baseAssets = base.manifest.assets,
            headAssets = headExportIndex.assets,
            comparison = comparison.get(),
            perceptualThreshold = perceptualThreshold.get(),
            resolvedBaseImages = resolvedBaseImages,
        )

        val diffDoc = DiffDocument(
            schemaVersion = 1,
            base = base.ref,
            head = headRef,
            summary = summary,
            changes = changes,
        )

        val output = diffJsonFile.get().asFile
        diffDoc.toFile(output)

        logger.lifecycle(
            "Store Studio: Diff computed against '${base.ref.ref}' " +
                "(${base.ref.commit ?: base.ref.source}): ${summary.totalChanges} changed " +
                "asset(s), ${summary.unchanged} unchanged.",
        )
    }

    private class ResolvedBase(
        val manifest: BaselineManifest,
        val ref: DiffRefInfo,
        /** Base images by slot key, when the base is a full export. */
        val images: Map<String, File> = emptyMap(),
    )

    private fun exportBase(dir: File, ref: DiffRefInfo): ResolvedBase {
        val index = dir.resolve("index.json")
        if (!index.exists()) {
            throw GradleException(
                "Store Studio: No 'index.json' in base export '${dir.path}'. Point it to the " +
                    "'build/store/export' directory produced by storeExport.",
            )
        }
        val exportIndex = ExportIndex.fromFile(index)
        return ResolvedBase(
            manifest = BaselineManifest.fromExportIndex(exportIndex),
            ref = ref,
            images = exportIndex.assets.associate { it.slotKey to dir.resolve(it.file) },
        )
    }

    /**
     * Checks out the base revision in a temporary worktree, runs this project's `storeExport`
     * there once, and compares against that export.
     */
    private fun renderBaseRevision(): ResolvedBase {
        val target = againstRef.orNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: findMergeBase()
            ?: throw GradleException(
                "Store Studio: Could not find a base revision. Pass --against=<ref>.",
            )
        requireRef(target)
        val (_, commitSha) = runGit("rev-parse", "--short", target)
        val ref = DiffRefInfo(
            ref = target,
            commit = commitSha.ifBlank { null },
            source = "RENDERED_REF",
        )

        val topLevel = File(runGit("rev-parse", "--show-toplevel").second).canonicalFile
        val rootDir = rootProjectDir.get().asFile.canonicalFile
        val buildRelPath = rootDir.relativeTo(topLevel).path
        val moduleRelPath = projectDir.get().asFile.canonicalFile.relativeTo(rootDir).path

        val workDir = workDirectory.get().asFile
        val worktree = workDir.resolve("worktree")
        val log = workDir.resolve("base-render.log")
        workDir.mkdirs()
        // Clean up a worktree left behind by an interrupted run
        runGit("worktree", "prune")
        if (worktree.exists()) {
            runGit("worktree", "remove", "--force", worktree.absolutePath)
            worktree.deleteRecursively()
        }

        val (addCode, addOut) = runGit("worktree", "add", "--detach", worktree.absolutePath, target)
        if (addCode != 0) {
            throw GradleException(
                "Store Studio: Could not check out '$target' for rendering: $addOut",
            )
        }
        logger.lifecycle(
            "Store Studio: Rendering base '$target' ($commitSha) in a temporary worktree…",
        )
        try {
            val buildDir = worktree.resolve(buildRelPath)
            // local.properties (sdk.dir) is not committed but the base build needs it
            for (relPath in setOf("", buildRelPath)) {
                val local = topLevel.resolve(relPath).resolve("local.properties")
                if (local.exists()) {
                    val dest = worktree.resolve(relPath).resolve("local.properties")
                    local.copyTo(dest, overwrite = true)
                }
            }

            val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
            val wrapperName = if (isWindows) "gradlew.bat" else "gradlew"
            // The nearest wrapper: the build's own, or one in a parent directory (run with -p)
            val wrapper = generateSequence(buildDir) { dir ->
                dir.parentFile?.takeIf { dir.canonicalPath != worktree.canonicalPath }
            }.map { it.resolve(wrapperName) }.firstOrNull { it.exists() }
                ?: throw GradleException(
                    "Store Studio: No Gradle wrapper found for '$buildRelPath' in '$target'; " +
                        "cannot render it.",
                )
            val taskPath = projectPath.get().removeSuffix(":") + ":storeExport"
            val result = log.outputStream().use { logStream ->
                execOperations.exec { spec ->
                    spec.workingDir = buildDir
                    spec.commandLine(
                        wrapper.absolutePath,
                        "-p",
                        buildDir.absolutePath,
                        taskPath,
                        "--no-daemon",
                        "--build-cache",
                        "--console=plain",
                    )
                    spec.standardOutput = logStream
                    spec.errorOutput = logStream
                    spec.isIgnoreExitValue = true
                }
            }

            if (result.exitValue != 0) {
                val logText = log.readText()
                if ("not found in project" in logText || "not found in root project" in logText) {
                    logger.warn(
                        "Store Studio: '$target' does not configure Store Studio for " +
                            "'${projectPath.get()}'. All current assets will be marked as ADDED.",
                    )
                    return ResolvedBase(BaselineManifest(), ref.copy(source = "EMPTY"))
                }
                throw GradleException(
                    "Store Studio: Rendering the base revision '$target' failed.\n" +
                        logText.lines().takeLast(LOG_TAIL_LINES).joinToString("\n") +
                        "\nFull log: ${log.path}",
                )
            }

            val exported = buildDir.resolve(moduleRelPath).resolve("build/store/export")
            val baseExport = workDir.resolve("export")
            baseExport.deleteRecursively()
            exported.copyRecursively(baseExport, overwrite = true)
            return exportBase(baseExport, ref)
        } finally {
            runGit("worktree", "remove", "--force", worktree.absolutePath)
            worktree.deleteRecursively()
            runGit("worktree", "prune")
        }
    }

    private fun resolveGitBaseline(): ResolvedBase {
        // "./" makes the path relative to the project instead of the repository root
        val baselineRelPath = if (baselineFile.isPresent) {
            "./" + baselineFile.get().asFile.relativeTo(projectDir.get().asFile).path
        } else {
            "./.store/baseline.json"
        }

        if (againstRef.isPresent && againstRef.get().isNotBlank()) {
            val target = againstRef.get().trim()
            requireRef(target)
            val (_, commitSha) = runGit("rev-parse", "--short", target)
            val (showCode, showOut) = runGit("show", "$target:$baselineRelPath")
            val manifest = if (showCode == 0) {
                BaselineManifest.fromJson(showOut)
            } else {
                logger.warn(
                    "Store Studio: No baseline at '$baselineRelPath' in '$target'. All current " +
                        "assets will be marked as ADDED. To compare without a committed " +
                        "baseline, add --render-base.",
                )
                BaselineManifest()
            }
            return ResolvedBase(
                manifest,
                DiffRefInfo(
                    ref = target,
                    commit = commitSha.ifBlank { null },
                    source = if (showCode == 0) "GIT_BASELINE" else "EMPTY",
                ),
            )
        }

        val mergeBaseSha = findMergeBase()
        val (showCode, showOut) = if (mergeBaseSha != null) {
            runGit("show", "$mergeBaseSha:$baselineRelPath")
        } else {
            Pair(1, "")
        }
        return when {
            showCode == 0 -> ResolvedBase(
                BaselineManifest.fromJson(showOut),
                DiffRefInfo(ref = "origin/main", commit = mergeBaseSha, source = "GIT_BASELINE"),
            )

            baselineFile.isPresent && baselineFile.get().asFile.exists() -> ResolvedBase(
                BaselineManifest.fromFile(baselineFile.get().asFile),
                DiffRefInfo(ref = "LOCAL_BASELINE", commit = mergeBaseSha, source = "LOCAL_DISK"),
            )

            else -> ResolvedBase(
                BaselineManifest(),
                DiffRefInfo(ref = "origin/main", commit = mergeBaseSha, source = "EMPTY"),
            )
        }
    }

    private fun resolveGitHead(): DiffRefInfo {
        val (_, headSha) = runGit("rev-parse", "--short", "HEAD")
        val (_, statusOut) = runGit("status", "--porcelain")
        return DiffRefInfo(
            ref = if (statusOut.isBlank()) "HEAD" else "WORKING_TREE",
            commit = headSha.ifBlank { null },
            source = "CURRENT_EXPORT",
        )
    }

    private fun resolveFilesystemBaseline(): ResolvedBase =
        if (baselineFile.isPresent && baselineFile.get().asFile.exists()) {
            ResolvedBase(
                BaselineManifest.fromFile(baselineFile.get().asFile),
                DiffRefInfo(ref = "LOCAL_BASELINE", commit = null, source = "LOCAL_DISK"),
            )
        } else {
            ResolvedBase(
                BaselineManifest(),
                DiffRefInfo(ref = "NONE", commit = null, source = "EMPTY"),
            )
        }

    private fun requireRef(target: String) {
        if (runGit("rev-parse", "--verify", "$target^{commit}").first == 0) return
        val isShallow = runGit("rev-parse", "--is-shallow-repository").second == "true"
        throw GradleException(
            if (isShallow) {
                "Store Studio: Git repository is shallow; ref '$target' not found. " +
                    "Configure 'fetch-depth: 0' in CI."
            } else {
                "Store Studio: Git ref '$target' does not exist. " +
                    "Check the branch name or fetch it (git fetch origin $target)."
            },
        )
    }

    private fun findMergeBase(): String? {
        val (codeOrigin, shaOrigin) = runGit("merge-base", "HEAD", "origin/main")
        if (codeOrigin == 0 && shaOrigin.isNotBlank()) return shaOrigin.take(10)

        val (codeMain, shaMain) = runGit("merge-base", "HEAD", "main")
        if (codeMain == 0 && shaMain.isNotBlank()) return shaMain.take(10)

        val (codeHead, shaHead) = runGit("rev-parse", "--short", "HEAD")
        if (codeHead == 0 && shaHead.isNotBlank()) return shaHead
        return null
    }

    private fun runGit(vararg args: String): Pair<Int, String> {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val result = execOperations.exec { spec ->
            spec.workingDir = projectDir.get().asFile
            spec.commandLine("git", *args)
            spec.standardOutput = out
            spec.errorOutput = err
            spec.isIgnoreExitValue = true
        }
        val text = if (result.exitValue == 0) out else err
        return Pair(result.exitValue, text.toString(Charsets.UTF_8).trim())
    }

    private companion object {
        const val LOG_TAIL_LINES = 30
    }
}
