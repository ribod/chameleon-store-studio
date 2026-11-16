package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.gradle.StoreStudioBuildConfig
import dev.ribod.storestudio.gradle.dsl.RenderingEngine
import dev.ribod.storestudio.gradle.internal.COMPOSE_VIEW_ADAPTER
import dev.ribod.storestudio.gradle.internal.RenderJob
import dev.ribod.storestudio.gradle.internal.RenderMatrix
import dev.ribod.storestudio.gradle.internal.RendererProcessSlots
import dev.ribod.storestudio.gradle.internal.SemanticsJob
import dev.ribod.storestudio.gradle.internal.ShotResult
import dev.ribod.storestudio.gradle.internal.awaitRenderer
import dev.ribod.storestudio.gradle.internal.environmentFailureLine
import dev.ribod.storestudio.gradle.internal.firstFailureLine
import dev.ribod.storestudio.gradle.internal.isLayoutlibSuccess
import dev.ribod.storestudio.gradle.internal.readLayoutlibResults
import dev.ribod.storestudio.gradle.internal.scrubCredentials
import dev.ribod.storestudio.gradle.internal.writeSemanticsJobs
import dev.ribod.storestudio.model.FailedJobSpec
import dev.ribod.storestudio.model.FailedJobsManifest
import dev.ribod.storestudio.model.RenderDiagnostic
import dev.ribod.storestudio.model.RenderIndex
import dev.ribod.storestudio.model.RenderJobResult
import dev.ribod.storestudio.model.StoreManifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Headless Composable screenshot rendering task executing in external JVM subprocesses.
 *
 * Expands the screenshot render matrix into batched workloads, drives Layoutlib CLI
 * (`com.android.tools.render.common.MainKt`), isolates crashes, detects process stalls,
 * and writes the canonical `index.json` and fallback job records.
 */
// Cacheable so CI can reuse renders when no screen changed. Outputs hold only paths relative to
// rawImagesDir. When the Robolectric fallback has written into these directories, Gradle detects
// the overlap and skips caching for that run.
@CacheableTask
public abstract class LayoutlibRenderTask : DefaultTask() {

    /** Consolidated execution manifest produced by [MergeStoreManifestTask]. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val manifestFile: RegularFileProperty

    /** The standalone preview renderer and Layoutlib framework classpath. */
    @get:Classpath
    public abstract val rendererClasspath: ConfigurableFileCollection

    /**
     * Unpacked Layoutlib native runtime directory (a single directory), produced by a cached
     * artifact transform. Untracked: the Layoutlib version is an input, and fingerprinting the
     * ~175 MB tree on every build would cost more than it protects.
     */
    @get:Internal
    public abstract val layoutlibDir: ConfigurableFileCollection

    /** Target Layoutlib version. */
    @get:Input
    public abstract val layoutlibVersion: Property<String>

    /** Internal preview renderer tool version. */
    @get:Input
    public abstract val rendererVersion: Property<String>

    /** Primary rendering engine strategy configured in the DSL. */
    @get:Input
    public abstract val engine: Property<RenderingEngine>

    /** Secondary fallback engine strategy configured in the DSL. */
    @get:Input
    public abstract val fallbackEngine: Property<RenderingEngine>

    /** Variant runtime dependencies viewed as android-classes-jars. */
    @get:Classpath
    public abstract val appClasspath: ConfigurableFileCollection

    /** Compiled project Kotlin and Java classes. */
    @get:Classpath
    public abstract val projectClasspath: ConfigurableFileCollection

    /**
     * The rendered variant's own classes as provided by AGP (`ScopedArtifact.CLASSES`), added to
     * [projectClasspath], which tracks them.
     */
    @get:Internal
    internal abstract val projectClassJars: ListProperty<RegularFile>

    /** Class directories counterpart of [projectClassJars]. */
    @get:Internal
    internal abstract val projectClassDirs: ListProperty<Directory>

    /** Generated R class JAR collection. */
    // R classes and linked resources are real inputs: a string or color change must re-render
    @get:Classpath
    public abstract val rClassPath: ConfigurableFileCollection

    /** Linked resources package archive (`.ap_`). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val resourceApk: ConfigurableFileCollection

    /** Android package resource namespace. */
    @get:Input
    public abstract val namespace: Property<String>

    /**
     * Whether this module is an Android/KMP library. Libraries without linked resources skip
     * rendering instead of failing; their screenshots are rendered by the consuming app module.
     */
    @get:Input
    public abstract val libraryModule: Property<Boolean>

    /** Target Android API level for rendering. */
    @get:Input
    public abstract val apiLevel: Property<String>

    /** Number of screenshot jobs assigned per batch process run (does not change the output). */
    @get:Internal
    public abstract val batchSize: Property<Int>

    /** Maximum parallel subprocesses spawned concurrently (does not change the output). */
    @get:Internal
    public abstract val maxParallelProcesses: Property<Int>

    /** Global execution timeout for a complete batch run. */
    @get:Internal
    public abstract val executionTimeout: Property<Duration>

    /** Inactivity duration before terminating a stalled process. */
    @get:Internal
    public abstract val stallTimeout: Property<Duration>

    /** Custom JVM arguments supplied to external renderer worker processes. */
    @get:Input
    public abstract val rendererJvmArgs: ListProperty<String>

    /** Value of `-Dstorestudio.statusBar` (absent when the status bar is disabled). */
    @get:Input
    @get:Optional
    public abstract val statusBar: Property<String>

    /** Configured store targets. */
    @get:Input
    public abstract val targets: ListProperty<StoreTarget>

    /** Configured default device families. */
    @get:Input
    public abstract val defaultDevices: ListProperty<DeviceFamily>

    /** Configured marketing localization locales. */
    @get:Input
    public abstract val locales: ListProperty<String>

    /** Additional legacy iPhone slot sizes to generate beyond the primary 6.9-inch slot. */
    @get:Input
    @get:Optional
    public abstract val extraIphoneSizes: SetProperty<String>

    /** Whether the host module is Kotlin Multiplatform. */
    @get:Input
    public abstract val kmpModule: Property<Boolean>

    /** Scratch working directory for input manifests and renderer process logs. */
    @get:Internal
    public abstract val workDir: DirectoryProperty

    /** Build-wide renderer JVM limit shared by all render tasks. */
    @get:Internal
    internal abstract val processSlots: Property<RendererProcessSlots>

    /** Output directory containing raw rendered screenshot PNG files. */
    @get:OutputDirectory
    public abstract val rawImagesDir: DirectoryProperty

    /** Output directory containing semantics tree snapshot JSON files (build/store/semantics). */
    @get:OutputDirectory
    public abstract val semanticsDir: DirectoryProperty

    /** Canonical index manifest tracking all job outcomes (`build/store/render/index.json`). */
    @get:OutputFile
    public abstract val renderIndex: RegularFileProperty

    /** Job manifest isolating failures eligible for fallback (`failed_jobs.json`). */
    @get:OutputFile
    public abstract val failedJobsFile: RegularFileProperty

    init {
        group = "store studio"
        description = "Executes headless Composable screenshot rendering via Layoutlib CLI."
    }

    @TaskAction
    public fun render() {
        check(JavaVersion.current() >= JavaVersion.VERSION_17) {
            "Store Studio: Headless Layoutlib rendering requires JDK 17+ " +
                "(current: ${JavaVersion.current()}). Configure a Java toolchain or JAVA_HOME."
        }

        val manifestContent = manifestFile.get().asFile.readText()
        val manifest = StoreManifest.decodeFromString(manifestContent)

        // Expand the manifest entries into individual target, slot, and locale jobs
        val jobs = RenderMatrix.expand(
            entries = manifest.entries,
            defaultTargets = targets.getOrElse(emptyList()),
            defaultDevices = defaultDevices.getOrElse(emptyList()),
            defaultLocales = locales.getOrElse(emptyList()),
            extraIphoneSizes = extraIphoneSizes.getOrElse(emptySet()),
        )

        val rawDir = rawImagesDir.get().asFile
        val work = workDir.get().asFile
        val semDir = semanticsDir.get().asFile
        rawDir.deleteRecursively()
        work.deleteRecursively()
        semDir.deleteRecursively()
        rawDir.mkdirs()
        work.mkdirs()
        semDir.mkdirs()

        if (jobs.isEmpty()) {
            writeEmptyOutputs(rawDir)
            logger.lifecycle("Store Studio: No screenshot entries discovered. Rendering skipped.")
            return
        }

        if (libraryModule.getOrElse(false) && resourceApk.isEmpty) {
            writeEmptyOutputs(rawDir)
            logger.lifecycle(
                "Store Studio: ${jobs.size} screenshot(s) skipped: this Android library has no " +
                    "linked resources, so they are rendered by the application module that " +
                    "depends on it. Enable storeStudio.rendering.configureLibraryResources to " +
                    "render them standalone.",
            )
            return
        }

        if (engine.get() == RenderingEngine.ROBOLECTRIC) {
            logger.lifecycle(
                "Store Studio: Primary engine configured to ROBOLECTRIC. Delegating ${jobs.size} jobs to fallback runner.",
            )
            processJobOutcomes(jobs, emptyMap(), rawDir)
            return
        }

        val batches = balancedBatches(jobs, batchSize.get())
        val parallelLimit = maxParallelProcesses.get().coerceAtLeast(1).coerceAtMost(batches.size)
        val executor = Executors.newFixedThreadPool(parallelLimit)
        val slots = processSlots.orNull
        // File collections can only be resolved on the task's own thread (it holds the worker
        // lease), and are identical for every batch, so resolve them once up front
        val renderInputs = resolveRenderInputs()

        logger.lifecycle(
            "Store Studio: Rendering ${jobs.size} screenshots across ${batches.size} batch(es) " +
                "with up to $parallelLimit parallel worker process(es)...",
        )

        val batchResults = mutableMapOf<Int, Map<String, ShotResult>>()
        val environmentFailures = java.util.Collections.synchronizedList(mutableListOf<String>())
        val tasks = batches.mapIndexed { index, batchJobs ->
            executor.submit {
                val run = { executeBatch(index, batchJobs, work, rawDir, renderInputs) }
                val outcome = slots?.withSlot(run) ?: run()
                outcome.environmentFailure?.let { environmentFailures += "batch $index: $it" }
                val results = outcome.results
                synchronized(batchResults) {
                    batchResults[index] = results
                }
            }
        }

        executor.shutdown()
        // No pool-level limit: time queued for a process slot behind other modules must not count.
        // executeBatch enforces the stall and total timeouts on actual execution.
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        } catch (e: InterruptedException) {
            // Build cancelled: interrupt the batch threads so they destroy their worker JVMs
            executor.shutdownNow()
            throw e
        }

        // A failed batch leaves its jobs to the fallback engine; report why it failed
        for ((index, task) in tasks.withIndex()) {
            runCatching { task.get() }.onFailure { error ->
                val cause = (error as? java.util.concurrent.ExecutionException)?.cause ?: error
                logger.warn("Store Studio: Layoutlib batch $index failed: $cause", cause)
            }
        }

        // Consolidate results across all completed batches
        val aggregatedResults = mutableMapOf<String, ShotResult>()
        for (map in batchResults.values) {
            aggregatedResults.putAll(map)
        }

        processJobOutcomes(jobs, aggregatedResults, rawDir)

        // Failures caused by the machine (memory, disk, time limits) say nothing about the
        // screens. Failing the task keeps such a degraded render out of the build cache; the
        // fallback still renders these jobs, since failed_jobs.json is already written.
        if (environmentFailures.isNotEmpty()) {
            throw GradleException(
                "Store Studio: Layoutlib could not render every screenshot because of the build " +
                    "environment:\n" + environmentFailures.joinToString("\n") { "  - $it" } +
                    "\nIncrease storeStudio.rendering.rendererJvmArgs (-Xmx), timeout or " +
                    "stallTimeout, or free disk space, then re-run.",
            )
        }
    }

    /** Results of one renderer process, and why it ended early if the environment caused it. */
    private class BatchOutcome(
        val results: Map<String, ShotResult>,
        val environmentFailure: String? = null,
    )

    private fun writeEmptyOutputs(rawDir: File) {
        val emptyIndex = RenderIndex(
            schemaVersion = 1,
            engineDefault = "LAYOUTLIB",
            layoutlibVersion = layoutlibVersion.getOrElse(StoreStudioBuildConfig.LAYOUTLIB_VERSION),
            renderedAt = Instant.now().toString(),
            jobs = emptyList(),
        )
        renderIndex.get().asFile.writeText(RenderIndex.encodeToString(emptyIndex))
        val emptyFailed = FailedJobsManifest(schemaVersion = 1, jobs = emptyList())
        failedJobsFile.get().asFile.writeText(FailedJobsManifest.encodeToString(emptyFailed))
    }

    /** File system inputs shared by all batches, resolved on the task thread. */
    private class RenderInputs(
        val layoutlibPath: String,
        val appClasspath: List<String>,
        val projectClasspath: List<String>,
        val resourceApkPath: String?,
        val rendererClasspath: String?,
        val namespace: String,
        val apiLevel: String,
        val jvmArgs: List<String>,
        val semanticsDir: String,
        val statusBar: String?,
        val stallTimeout: Duration,
        val executionTimeout: Duration,
    )

    private fun resolveRenderInputs(): RenderInputs {
        val renderer = rendererClasspath.files.takeIf { it.isNotEmpty() }
            ?.joinToString(File.pathSeparator) { it.absolutePath }
        return resolveRenderInputs(renderer)
    }

    private fun resolveRenderInputs(renderer: String?): RenderInputs = RenderInputs(
        // Only resolved when a renderer runs (tests and minimal setups have none)
        layoutlibPath = if (renderer != null) layoutlibDir.singleFile.absolutePath else "",
        appClasspath = appClasspath.files.map { it.absolutePath },
        projectClasspath = (projectClasspath.files + rClassPath.files).map { it.absolutePath },
        resourceApkPath = resourceApk.files.firstOrNull()?.absolutePath,
        rendererClasspath = renderer,
        namespace = namespace.getOrElse(""),
        apiLevel = apiLevel.getOrElse("36"),
        jvmArgs = rendererJvmArgs.getOrElse(listOf("-Xmx2g")),
        semanticsDir = semanticsDir.get().asFile.absolutePath,
        statusBar = statusBar.orNull,
        stallTimeout = stallTimeout.get(),
        executionTimeout = executionTimeout.get(),
    )

    private fun executeBatch(
        batchIndex: Int,
        batchJobs: List<RenderJob>,
        work: File,
        rawDir: File,
        inputs: RenderInputs,
    ): BatchOutcome {
        val resultsFile = work.resolve("results_batch_$batchIndex.json")
        val metaFolder = work.resolve("meta_batch_$batchIndex").apply { mkdirs() }
        val logFile = work.resolve("renderer_batch_$batchIndex.log")

        val inputJson = buildJsonObject {
            put("fontsPath", "")
            put("layoutlibPath", inputs.layoutlibPath)
            put("outputFolder", rawDir.absolutePath)
            put("metaDataFolder", metaFolder.absolutePath)
            put("resultsFilePath", resultsFile.absolutePath)
            putJsonArray("classPath") {
                inputs.appClasspath.forEach { add(it) }
            }
            putJsonArray("projectClassPath") {
                inputs.projectClasspath.forEach { add(it) }
            }
            put("namespace", inputs.namespace)
            inputs.resourceApkPath?.let { put("resourceApkPath", it) }
            putJsonArray("screenshots") {
                batchJobs.forEach { job ->
                    addJsonObject {
                        put("previewId", job.jobId)
                        put("methodFQN", job.methodFqn)
                        putJsonArray("methodParams") {}
                        putJsonObject("previewParams") {
                            put("apiLevel", inputs.apiLevel)
                            put(
                                "device",
                                "spec:width=${job.geometry.widthDp}dp," +
                                    "height=${job.geometry.heightDp}dp,dpi=${job.geometry.dpi}",
                            )
                            put("locale", RenderMatrix.bcp47ToAndroidQualifier(job.storeLocale))
                            put("uiMode", "0")
                            put("fontScale", "1.0")
                        }
                    }
                }
            }
        }

        val inputFile = work.resolve("input_batch_$batchIndex.json")
        inputFile.writeText(Json.encodeToString(JsonObject.serializer(), inputJson))

        // If no renderer classpath is resolved (e.g. minimal test), skip process execution
        val cp = inputs.rendererClasspath ?: return BatchOutcome(emptyMap())

        val batchJobsFile = work.resolve("batch_jobs_$batchIndex.tsv")
        writeSemanticsJobs(
            batchJobsFile,
            batchJobs.map { job ->
                SemanticsJob(
                    jobId = job.jobId,
                    slotKey = job.slotKey,
                    methodFqn = job.methodFqn,
                    slot = job.slot.name,
                    target = job.target.name,
                    storeLocale = job.storeLocale,
                )
            },
        )

        val javaBin = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath
        val command = buildList {
            add(javaBin)
            add("--enable-native-access=ALL-UNNAMED")
            add("-Djava.awt.headless=true")
            add("-Dstorestudio.semanticsDir=${inputs.semanticsDir}")
            add("-Dstorestudio.semanticsJobsFile=${batchJobsFile.absolutePath}")
            inputs.statusBar?.let { add("-Dstorestudio.statusBar=$it") }
            addAll(inputs.jvmArgs)
            add("-cp")
            add(cp)
            add(RENDERER_MAIN_CLASS)
            add(inputFile.absolutePath)
        }

        val started = System.currentTimeMillis()
        val process = ProcessBuilder(command)
            .scrubCredentials()
            .redirectErrorStream(true)
            .redirectOutput(logFile)
            .start()

        val wantedIds = batchJobs.map { it.jobId }.toSet()
        var environmentFailure = awaitRenderer(
            process,
            resultsFile,
            wantedIds,
            inputs.stallTimeout,
            inputs.executionTimeout,
        )
        environmentFailure?.let {
            logger.warn("Store Studio: Layoutlib batch $batchIndex: $it. Process terminated.")
        }

        val results = if (resultsFile.isFile) readLayoutlibResults(resultsFile) else emptyMap()
        if (results.isEmpty()) {
            logger.warn(
                "Store Studio: Layoutlib batch $batchIndex produced no results. " +
                    "Root cause: ${firstFailureLine(
                        logFile,
                    ) ?: "unknown"} (log: ${logFile.absolutePath})",
            )
        }
        if (environmentFailure == null && !results.keys.containsAll(wantedIds)) {
            environmentFailure = environmentFailureLine(logFile)
        }
        return BatchOutcome(results, environmentFailure)
    }

    private fun processJobOutcomes(
        jobs: List<RenderJob>,
        resultsById: Map<String, ShotResult>,
        rawDir: File,
    ) {
        val jobResults = mutableListOf<RenderJobResult>()
        val failedJobSpecs = mutableListOf<FailedJobSpec>()
        var successCount = 0
        var failureCount = 0

        for (job in jobs) {
            val shot = resultsById[job.jobId]
            val imageFile = shot?.imagePath?.let { rawDir.resolve(it) }
            val isSuccess = isLayoutlibSuccess(shot, imageFile)

            if (isSuccess && imageFile != null) {
                successCount++
                jobResults.add(
                    RenderJobResult(
                        jobId = job.jobId,
                        slotKey = job.slotKey,
                        entryId = job.entryId,
                        target = job.target,
                        deviceSlot = job.slot,
                        storeLocale = job.storeLocale,
                        localeTag = job.localeTag,
                        widthPx = job.geometry.size.width,
                        heightPx = job.geometry.size.height,
                        dpi = job.geometry.dpi,
                        engine = "LAYOUTLIB",
                        status = "SUCCESS",
                        image = imageFile.relativeTo(rawDir).invariantSeparatorsPath,
                        durationMs = 0L,
                        diagnostics = emptyList(),
                    ),
                )
            } else {
                failureCount++
                val reason = classifyFailureReason(shot)
                val diagnostic = RenderDiagnostic(
                    severity = "WARNING",
                    code = "LAYOUTLIB_RENDER_FAILED",
                    message = reason,
                )

                jobResults.add(
                    RenderJobResult(
                        jobId = job.jobId,
                        slotKey = job.slotKey,
                        entryId = job.entryId,
                        target = job.target,
                        deviceSlot = job.slot,
                        storeLocale = job.storeLocale,
                        localeTag = job.localeTag,
                        widthPx = job.geometry.size.width,
                        heightPx = job.geometry.size.height,
                        dpi = job.geometry.dpi,
                        engine = "LAYOUTLIB",
                        status = "FAILED",
                        image = null,
                        durationMs = 0L,
                        diagnostics = listOf(diagnostic),
                    ),
                )

                // Relative to rawImagesDir, so cached outputs stay valid on other machines/paths
                val targetImagePath = "${job.jobId}.png"
                failedJobSpecs.add(
                    FailedJobSpec(
                        jobId = job.jobId,
                        slotKey = job.slotKey,
                        entryId = job.entryId,
                        methodFqn = job.methodFqn,
                        target = job.target,
                        deviceSlot = job.slot,
                        storeLocale = job.storeLocale,
                        localeTag = job.localeTag,
                        widthPx = job.geometry.size.width,
                        heightPx = job.geometry.size.height,
                        widthDp = job.geometry.widthDp,
                        heightDp = job.geometry.heightDp,
                        dpi = job.geometry.dpi,
                        outputPath = targetImagePath,
                        failureReason = reason,
                    ),
                )
            }
        }

        // Warn developers if ComposeViewAdapter was missing across any failed preview
        val missingAdapter = resultsById.values.any { shot ->
            shot.missingClasses.any { cls -> cls.endsWith("ComposeViewAdapter") }
        }
        if (missingAdapter) {
            val remediation = if (kmpModule.getOrElse(false)) {
                "Add 'androidx.compose.ui:ui-tooling' to your Android runtime dependencies: " +
                    "androidRuntimeClasspath(compose.uiTooling)"
            } else {
                "Add debugImplementation(\"androidx.compose.ui:ui-tooling:<version>\")"
            }
            logger.warn(
                "Store Studio: Composable screenshots cannot render — preview host " +
                    "'$COMPOSE_VIEW_ADAPTER' (Compose 'ui-tooling') is missing from the " +
                    "render classpath. $remediation",
            )
        }

        val index = RenderIndex(
            schemaVersion = 1,
            engineDefault = "LAYOUTLIB",
            layoutlibVersion = layoutlibVersion.getOrElse(StoreStudioBuildConfig.LAYOUTLIB_VERSION),
            renderedAt = Instant.now().toString(),
            jobs = jobResults,
        )
        renderIndex.get().asFile.writeText(RenderIndex.encodeToString(index))

        val failedManifest = FailedJobsManifest(schemaVersion = 1, jobs = failedJobSpecs)
        failedJobsFile.get().asFile.writeText(FailedJobsManifest.encodeToString(failedManifest))

        logger.lifecycle(
            "Store Studio: Headless rendering complete " +
                "($successCount succeeded, $failureCount failed).",
        )

        // Fail build immediately if fallback is explicitly disabled and failures occurred
        if (failureCount > 0 && fallbackEngine.get() == RenderingEngine.NONE) {
            throw GradleException(
                "Store Studio: $failureCount screenshot(s) failed to render in Layoutlib " +
                    "and fallback engine is set to NONE.",
            )
        }
    }

    private fun classifyFailureReason(shot: ShotResult?): String = when {
        shot == null -> "No execution result recorded"

        shot.brokenClasses.isNotEmpty() -> "Broken classes: ${shot.brokenClasses.joinToString()}"

        shot.missingClasses.any { it.endsWith("ComposeViewAdapter") } ->
            "Preview host '$COMPOSE_VIEW_ADAPTER' (Compose ui-tooling) missing from classpath"

        shot.missingClasses.isNotEmpty() -> "Missing classes: ${shot.missingClasses.joinToString()}"

        shot.problems.isNotEmpty() -> "Render problem: ${shot.problems.first()}"

        !shot.message.isNullOrBlank() -> shot.message

        !shot.status.isNullOrBlank() && shot.status != "SUCCESS" -> "Status: ${shot.status}"

        else -> "No image produced on disk"
    }

    internal companion object {
        const val RENDERER_MAIN_CLASS: String = "com.android.tools.render.common.MainKt"

        /**
         * Splits [jobs] into the fewest batches of at most [batchSize] jobs, sized evenly so that
         * parallel worker processes finish at about the same time (50 jobs, size 40: 25 + 25).
         */
        fun <T> balancedBatches(jobs: List<T>, batchSize: Int): List<List<T>> {
            if (jobs.isEmpty()) return emptyList()
            val limit = batchSize.coerceAtLeast(1)
            val count = (jobs.size + limit - 1) / limit
            val evenSize = (jobs.size + count - 1) / count
            return jobs.chunked(evenSize)
        }
    }
}
