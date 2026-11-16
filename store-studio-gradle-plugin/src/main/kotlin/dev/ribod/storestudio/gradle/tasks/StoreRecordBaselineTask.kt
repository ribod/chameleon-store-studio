package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.gradle.StoreStudioBuildConfig
import dev.ribod.storestudio.model.BaselineManifest
import dev.ribod.storestudio.model.ExportIndex
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Records current screenshot hashes into the persistent baseline document.
 */
public abstract class StoreRecordBaselineTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val exportIndexFile: RegularFileProperty

    @get:Input
    public abstract val layoutlibVersion: Property<String>

    @get:OutputFile
    public abstract val baselineFile: RegularFileProperty

    init {
        group = "store studio"
        description = "Records new screenshot hashes into the persistent baseline file."
    }

    @TaskAction
    public fun record() {
        val indexFile = exportIndexFile.get().asFile
        if (!indexFile.exists()) {
            throw GradleException(
                "Cannot record baseline: export index not found at ${indexFile.path}. " +
                    "Ensure storeExport completed successfully.",
            )
        }

        val exportIndex = ExportIndex.fromFile(indexFile)
        val manifest = BaselineManifest.fromExportIndex(
            exportIndex,
            layoutlibVersion = layoutlibVersion.getOrElse(StoreStudioBuildConfig.LAYOUTLIB_VERSION),
            recordedWith = "store-studio/${StoreStudioBuildConfig.PLUGIN_VERSION}",
        )
        val destFile = baselineFile.get().asFile

        manifest.toFile(destFile)

        logger.lifecycle(
            "Store Studio: Baseline recorded successfully to ${destFile.path} " +
                "(${manifest.assets.size} asset(s)).",
        )
    }
}
