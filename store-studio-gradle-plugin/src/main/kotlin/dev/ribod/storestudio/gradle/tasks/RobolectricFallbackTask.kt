package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.gradle.dsl.RenderingEngine
import dev.ribod.storestudio.gradle.internal.RendererProcessSlots
import dev.ribod.storestudio.gradle.internal.SemanticsJob
import dev.ribod.storestudio.gradle.internal.firstFailureLine
import dev.ribod.storestudio.gradle.internal.scrubCredentials
import dev.ribod.storestudio.gradle.internal.writeSemanticsJobs
import dev.ribod.storestudio.model.FailedJobsManifest
import dev.ribod.storestudio.model.RenderDiagnostic
import dev.ribod.storestudio.model.RenderIndex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
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
import java.util.concurrent.TimeUnit

internal data class RobolectricResultItem(
    val jobId: String,
    val success: Boolean,
    val errorMessage: String? = null,
)

/**
 * Secondary headless screenshot rendering task executing Robolectric Native Graphics in an isolated JVM.
 *
 * Renders the jobs Layoutlib recorded in `failed_jobs.json` into its own outputs
 * ([fallbackIndexFile], [fallbackImagesDir], [fallbackSemanticsDir]); consumers combine them with
 * the Layoutlib index via `RenderIndex.withFallback`. It never modifies storeRender's outputs, so
 * both stay up to date and cacheable. With nothing to recover it writes empty outputs.
 * Uses Native Graphics (RNG) with Compose Test Rule and Skia rasterization to recover incompatible
 * Composables (such as AndroidView, native Canvas, SQLite, or runtime dependencies).
 */
@CacheableTask
public abstract class RobolectricFallbackTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val failedJobsFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val renderIndexFile: RegularFileProperty

    @get:Classpath
    public abstract val robolectricClasspath: ConfigurableFileCollection

    @get:Classpath
    public abstract val bootClasspath: ConfigurableFileCollection

    @get:Classpath
    public abstract val appClasspath: ConfigurableFileCollection

    @get:Classpath
    public abstract val projectClasspath: ConfigurableFileCollection

    @get:Classpath
    public abstract val rClassPath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val resourceApk: ConfigurableFileCollection

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val mergedManifestFile: RegularFileProperty

    @get:Input
    public abstract val namespace: Property<String>

    @get:Input
    public abstract val fallbackEngine: Property<RenderingEngine>

    /** JVM arguments of the Robolectric worker process (`rendering.fallbackJvmArgs`). */
    @get:Input
    public abstract val jvmArgs: ListProperty<String>

    /** Value of `-Dstorestudio.statusBar` (absent when the status bar is disabled). */
    @get:Input
    @get:Optional
    public abstract val statusBar: Property<String>

    /** Build-wide renderer JVM limit shared with the Layoutlib render tasks. */
    @get:Internal
    internal abstract val processSlots: Property<RendererProcessSlots>

    /** Results of the jobs this task rendered; the same shape as storeRender's index. */
    @get:OutputFile
    public abstract val fallbackIndexFile: RegularFileProperty

    /** Images rendered by Robolectric; [fallbackIndexFile] paths are relative to it. */
    @get:OutputDirectory
    public abstract val fallbackImagesDir: DirectoryProperty

    /** Semantics snapshots of the jobs rendered by Robolectric. */
    @get:OutputDirectory
    public abstract val fallbackSemanticsDir: DirectoryProperty

    @get:Internal
    public abstract val workDir: DirectoryProperty

    /** Maximum time the Robolectric worker process may run. */
    @get:Internal
    public abstract val processTimeout: Property<Duration>

    init {
        group = "store studio"
        description =
            "Executes headless Composable screenshot fallback rendering via Robolectric Native Graphics."
    }

    @TaskAction
    public fun executeFallback() {
        val imagesDir = fallbackImagesDir.get().asFile
        val semanticsOutDir = fallbackSemanticsDir.get().asFile
        // Outputs always reflect this run, also when there is nothing to recover
        imagesDir.deleteRecursively()
        semanticsOutDir.deleteRecursively()
        imagesDir.mkdirs()
        semanticsOutDir.mkdirs()

        val fFile = failedJobsFile.get().asFile
        val manifest = if (fFile.isFile) {
            FailedJobsManifest.decodeFromString(fFile.readText())
        } else {
            null
        }
        val renderIndex = RenderIndex.decodeFromString(renderIndexFile.get().asFile.readText())
        if (fallbackEngine.get() != RenderingEngine.ROBOLECTRIC || manifest == null ||
            manifest.jobs.isEmpty()
        ) {
            writeFallbackIndex(renderIndex, emptyList())
            logger.info("Store Studio: no failed jobs; Robolectric fallback has nothing to render.")
            return
        }

        logger.lifecycle(
            "Store Studio: Executing Robolectric Native Graphics fallback for " +
                "${manifest.jobs.size} failed screenshot(s)...",
        )

        val work = workDir.get().asFile.resolve("robolectric").apply { mkdirs() }
        val testConfigDir = work.resolve("test_config").apply { mkdirs() }
        val testConfigRelPath = "com/android/tools/test_config.properties"
        val testConfigProps = testConfigDir.resolve(testConfigRelPath).apply {
            parentFile.mkdirs()
        }

        val resApkPath = resourceApk.files.firstOrNull()?.absolutePath ?: ""
        val customPkg = namespace.getOrElse("")

        val generatedManifest = work.resolve("AndroidManifest.xml")
        val baseManifestContent = if (mergedManifestFile.orNull?.asFile?.isFile == true) {
            mergedManifestFile.get().asFile.readText()
        } else {
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="$customPkg">
                <application>
                </application>
            </manifest>
            """.trimIndent()
        }

        val patchedManifestContent = if (!baseManifestContent.contains(
                "androidx.activity.ComponentActivity",
            )
        ) {
            if (baseManifestContent.contains("</application>")) {
                baseManifestContent.replace(
                    "</application>",
                    "    <activity android:name=\"androidx.activity.ComponentActivity\" android:exported=\"true\" />\n    </application>",
                )
            } else if (baseManifestContent.contains("<application/>") ||
                baseManifestContent.contains("<application />")
            ) {
                baseManifestContent.replace(
                    Regex("<application\\s*/>"),
                    "<application><activity android:name=\"androidx.activity.ComponentActivity\" android:exported=\"true\" /></application>",
                )
            } else {
                baseManifestContent.replace(
                    "</manifest>",
                    "<application><activity android:name=\"androidx.activity.ComponentActivity\" android:exported=\"true\" /></application></manifest>",
                )
            }
        } else {
            baseManifestContent
        }
        generatedManifest.writeText(patchedManifestContent)
        val manifestPath = generatedManifest.absolutePath

        testConfigProps.writeText(
            """
            android_merged_resources=$resApkPath
            android_merged_manifest=$manifestPath
            android_merged_assets=
            android_resource_apk=$resApkPath
            android_custom_package=$customPkg
            """.trimIndent(),
        )

        // failed_jobs.json (a cacheable storeRender output) holds relative image paths; the
        // runner writes to absolute paths inside this task's own image directory
        val runnerJobsFile = work.resolve("failed_jobs_resolved.json")
        runnerJobsFile.writeText(
            FailedJobsManifest.encodeToString(
                manifest.copy(
                    jobs = manifest.jobs.map {
                        it.copy(outputPath = imagesDir.resolve(it.outputPath).path)
                    },
                ),
            ),
        )

        val semanticsJobsFile = work.resolve("semantics_jobs.tsv")
        writeSemanticsJobs(
            semanticsJobsFile,
            manifest.jobs.map { job ->
                SemanticsJob(
                    jobId = job.jobId,
                    slotKey = job.slotKey,
                    methodFqn = job.methodFqn,
                    slot = job.deviceSlot.name,
                    target = job.target.name,
                    storeLocale = job.storeLocale,
                )
            },
        )

        val resultsFile = work.resolve("robolectric_results.json")
        val logFile = work.resolve("robolectric_runner.log")

        val javaBin = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath

        val fullClasspath = buildList {
            add(testConfigDir.absolutePath)
            bootClasspath.files.forEach { add(it.absolutePath) }
            robolectricClasspath.files.forEach { add(it.absolutePath) }
            appClasspath.files.forEach { add(it.absolutePath) }
            projectClasspath.files.forEach { add(it.absolutePath) }
            rClassPath.files.forEach { add(it.absolutePath) }
        }.distinct().joinToString(File.pathSeparator)

        val command = listOf(
            javaBin,
            "--enable-native-access=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens=java.base/java.text=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
            "-Djava.awt.headless=true",
            "-Drobolectric.logging=stdout",
            *jvmArgs.getOrElse(emptyList()).toTypedArray(),
            *listOfNotNull(statusBar.orNull?.let { "-Dstorestudio.statusBar=$it" }).toTypedArray(),
            "-Dstorestudio.failedJobsFile=${runnerJobsFile.absolutePath}",
            "-Dstorestudio.resultsFile=${resultsFile.absolutePath}",
            "-Dstorestudio.semanticsDir=${semanticsOutDir.absolutePath}",
            "-Dstorestudio.semanticsJobsFile=${semanticsJobsFile.absolutePath}",
            "-cp",
            fullClasspath,
            "org.junit.runner.JUnitCore",
            "dev.ribod.storestudio.testing.StoreStudioRobolectricRunner",
        )

        val limit = processTimeout.get()
        val runWorker = {
            val process = ProcessBuilder(command)
                .scrubCredentials()
                .directory(work)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(logFile))
                .start()
            try {
                process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS)
            } finally {
                // Also runs when the build is cancelled, so the worker JVM is never leaked
                if (process.isAlive) process.destroyForcibly()
            }
        }
        val exitedInTime = processSlots.orNull?.withSlot(runWorker) ?: runWorker()
        if (!exitedInTime) {
            throw GradleException(
                "Store Studio: Robolectric fallback process timed out after ${limit.toMinutes()} " +
                    "minute(s) (rendering.fallbackTimeout). See logs at ${logFile.absolutePath}",
            )
        }

        val resultsJson = if (resultsFile.isFile) resultsFile.readText() else null
        val results = mutableListOf<RobolectricResultItem>()
        if (!resultsJson.isNullOrBlank()) {
            runCatching {
                val element = Json.parseToJsonElement(resultsJson)
                val arr = element.jsonObject["results"]?.jsonArray
                arr?.forEach { item ->
                    val obj = item.jsonObject
                    val jId = obj["jobId"]?.jsonPrimitive?.content ?: ""
                    val succ = obj["success"]?.jsonPrimitive?.booleanOrNull ?: false
                    val err = obj["errorMessage"]?.jsonPrimitive?.contentOrNull
                    results.add(RobolectricResultItem(jId, succ, err))
                }
            }
        }

        val resultsByJobId = results.associateBy { it.jobId }

        // Fallback results for the jobs this task handled; storeRender's index stays untouched
        val failedJobSpecsByJobId = manifest.jobs.associateBy { it.jobId }
        val fallbackJobs = renderIndex.jobs.mapNotNull { currentJob ->
            val fallbackResult = resultsByJobId[currentJob.jobId] ?: return@mapNotNull null
            val failedSpec = failedJobSpecsByJobId[currentJob.jobId]
            if (fallbackResult.success) {
                val imgFile = imagesDir.resolve(failedSpec?.outputPath ?: "${currentJob.jobId}.png")
                val reason = failedSpec?.failureReason ?: "Layoutlib failure"
                currentJob.copy(
                    status = "SUCCESS",
                    engine = "ROBOLECTRIC",
                    image = imgFile.takeIf { it.isFile }
                        ?.relativeTo(imagesDir)?.invariantSeparatorsPath,
                    diagnostics = currentJob.diagnostics + RenderDiagnostic(
                        severity = "WARNING",
                        code = "LAYOUTLIB_FAILED_FALLBACK_USED",
                        message = "Rendered via Robolectric Native Graphics fallback engine " +
                            "($reason)",
                    ),
                )
            } else {
                currentJob.copy(
                    status = "FAILED",
                    engine = "ROBOLECTRIC",
                    diagnostics = currentJob.diagnostics + RenderDiagnostic(
                        severity = "ERROR",
                        code = "ROBOLECTRIC_FALLBACK_FAILED",
                        message = fallbackResult.errorMessage
                            ?: "Robolectric fallback execution failed",
                    ),
                )
            }
        }
        writeFallbackIndex(renderIndex, fallbackJobs)

        val succeededCount = results.count { it.success }
        val failedCount = manifest.jobs.size - succeededCount

        logger.lifecycle(
            "Store Studio: Robolectric fallback completed " +
                "($succeededCount recovered, $failedCount failed).",
        )

        if (failedCount > 0) {
            val logSnippet = if (logFile.isFile) {
                logFile.readLines().takeLast(30).joinToString("\n")
            } else {
                ""
            }
            val rootCause = firstFailureLine(logFile)?.let { "Root cause: $it\n" }.orEmpty()
            throw GradleException(
                "Store Studio: $failedCount screenshot(s) failed in both " +
                    "Layoutlib and Robolectric fallback.\n" + rootCause +
                    "See ${logFile.absolutePath} for details.\nLast log lines:\n$logSnippet",
            )
        }
    }

    private fun writeFallbackIndex(
        renderIndex: RenderIndex,
        jobs: List<dev.ribod.storestudio.model.RenderJobResult>,
    ) {
        fallbackIndexFile.get().asFile.apply { parentFile.mkdirs() }.writeText(
            RenderIndex.encodeToString(
                renderIndex.copy(engineDefault = "ROBOLECTRIC", jobs = jobs),
            ),
        )
    }
}
