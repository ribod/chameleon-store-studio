package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.core.baseline.BaselineVerifier
import dev.ribod.storestudio.model.BaselineComparison
import dev.ribod.storestudio.model.BaselineManifest
import dev.ribod.storestudio.model.ExportIndex
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Compares current export hashes against the tracked baseline to detect regressions.
 */
@CacheableTask
public abstract class StoreVerifyBaselineTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val exportIndexFile: RegularFileProperty

    @get:Internal
    public abstract val baselineFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val baselineFiles: ConfigurableFileCollection

    @get:Input
    public abstract val comparison: Property<BaselineComparison>

    @get:Input
    public abstract val perceptualThreshold: Property<Double>

    @get:Input
    public abstract val failOnDrift: Property<Boolean>

    /** Whether a missing baseline fails the task instead of skipping the verification. */
    @get:Input
    public abstract val required: Property<Boolean>

    @get:OutputFile
    public abstract val reportFile: RegularFileProperty

    init {
        group = "store studio"
        description = "Verifies rendered screenshots against historical baselines."
    }

    @TaskAction
    public fun verify() {
        val bFile = baselineFile.get().asFile
        if (!bFile.exists()) {
            if (required.getOrElse(false)) {
                throw GradleException(
                    "Store Studio: No baseline found at '${bFile.path}'. " +
                        "Run './gradlew storeRecordBaseline' to establish your initial baseline.",
                )
            }
            logger.lifecycle(
                "Store Studio: No baseline recorded at '${bFile.path}'; skipping verification. " +
                    "Record one with storeRecordBaseline, or review changes with " +
                    "'storeDiff --against=main --render-base'.",
            )
            reportFile.get().asFile.delete()
            return
        }

        val indexFile = exportIndexFile.get().asFile
        if (!indexFile.exists()) {
            throw GradleException(
                "Store Studio: Export index not found at '${indexFile.path}'. " +
                    "Ensure storeExport completed successfully.",
            )
        }

        val baseline = BaselineManifest.fromFile(bFile)
        val exportIndex = ExportIndex.fromFile(indexFile)

        val result = BaselineVerifier.verify(
            baseline = baseline,
            current = exportIndex,
            comparison = comparison.get(),
            perceptualThreshold = perceptualThreshold.get(),
        )

        val reportOutput = reportFile.get().asFile
        result.toFile(reportOutput)

        logger.lifecycle(result.formatReport())

        if (!result.passed && failOnDrift.get()) {
            throw GradleException(result.formatReport())
        }
    }
}
