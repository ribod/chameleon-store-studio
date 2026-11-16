package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.catalog.DefaultStoreSpecCatalog
import dev.ribod.storestudio.core.hash.AssetHasher
import dev.ribod.storestudio.core.metadata.StoreMetadataManifest
import dev.ribod.storestudio.core.validation.RuleEngine
import dev.ribod.storestudio.core.validation.ValidationContext
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.RenderIndex
import dev.ribod.storestudio.model.SemanticsSnapshot
import dev.ribod.storestudio.model.Severity
import dev.ribod.storestudio.model.StoreManifest
import dev.ribod.storestudio.model.withFallback
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Validates store listing visual assets against Google Play and Apple App Store compliance rules.
 */
@CacheableTask
public abstract class StoreValidateTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val exportDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val exportIndexFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val renderIndexFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val mergedManifestFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val metadataFile: RegularFileProperty

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val semanticsDir: DirectoryProperty

    /** Robolectric fallback results, replacing the Layoutlib jobs with the same id. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val fallbackIndexFile: RegularFileProperty

    /** Semantics snapshots of the jobs rendered by the fallback engine. */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val fallbackSemanticsDir: DirectoryProperty

    @get:Input
    public abstract val failOnWarning: Property<Boolean>

    @get:Input
    public abstract val featureGraphicMissing: Property<Severity>

    @get:Input
    public abstract val severityOverrides: MapProperty<String, Severity>

    @get:Input
    public abstract val configuredTargets: SetProperty<StoreTarget>

    @get:Input
    public abstract val configuredLocales: SetProperty<String>

    @get:Input
    public abstract val supportsIpad: Property<Boolean>

    /**
     * False for library modules: they contribute part of the app's listing, so only per-image
     * rules apply to them; the application module validates the complete listing.
     */
    @get:Input
    public abstract val listingModule: Property<Boolean>

    @get:OutputFile
    public abstract val reportFile: RegularFileProperty

    @get:OutputFile
    public abstract val inputsHashFile: RegularFileProperty

    init {
        group = "store studio"
        description = "Verifies exported assets against marketplace submission rules."
    }

    @TaskAction
    public fun validate() {
        val exportIndex = ExportIndex.fromFile(exportIndexFile.get().asFile)
        val fallbackIndex = fallbackIndexFile.orNull?.asFile?.takeIf { it.isFile }
            ?.let { RenderIndex.decodeFromString(it.readText()) }
        val renderIndex = if (renderIndexFile.isPresent && renderIndexFile.get().asFile.exists()) {
            RenderIndex.decodeFromString(renderIndexFile.get().asFile.readText())
                .withFallback(fallbackIndex)
        } else {
            null
        }

        val mergedManifest = if (
            mergedManifestFile.isPresent &&
            mergedManifestFile.get().asFile.exists()
        ) {
            runCatching {
                StoreManifest.decodeFromString(mergedManifestFile.get().asFile.readText())
            }.getOrNull()
        } else {
            null
        }

        val metadataManifest = if (
            metadataFile.isPresent &&
            metadataFile.get().asFile.exists()
        ) {
            runCatching {
                StoreMetadataManifest.fromFile(metadataFile.get().asFile)
            }.getOrNull()
        } else {
            null
        }

        fun snapshotsIn(dir: java.io.File?): List<SemanticsSnapshot> =
            dir?.takeIf { it.isDirectory }
                ?.listFiles { _, name -> name.endsWith(".json") }
                ?.mapNotNull { f -> runCatching { SemanticsSnapshot.fromFile(f) }.getOrNull() }
                .orEmpty()
        // A job rendered by the fallback is described by the fallback's snapshot
        val fallbackSnapshots = snapshotsIn(fallbackSemanticsDir.orNull?.asFile)
        val fallbackJobIds = fallbackSnapshots.map { it.jobId }.toSet()
        val snapshots = snapshotsIn(semanticsDir.orNull?.asFile)
            .filterNot { it.jobId in fallbackJobIds } + fallbackSnapshots

        val ctx = ValidationContext(
            catalog = DefaultStoreSpecCatalog,
            exportIndex = exportIndex,
            renderIndex = renderIndex,
            mergedManifest = mergedManifest,
            configuredTargets = configuredTargets.get(),
            configuredLocales = configuredLocales.get(),
            supportsIpad = supportsIpad.get(),
            exportDir = exportDirectory.get().asFile,
            featureGraphicMissingSeverity = featureGraphicMissing.get(),
            metadataManifest = metadataManifest,
            semanticsSnapshots = snapshots,
            listingModule = listingModule.getOrElse(true),
        )

        val report = RuleEngine().run(
            ctx = ctx,
            overrides = severityOverrides.get(),
            failOnWarning = failOnWarning.get(),
        )

        val reportOutput = reportFile.get().asFile
        report.toFile(reportOutput)

        val inputsHash = AssetHasher.fileSha256(exportIndexFile.get().asFile)
        inputsHashFile.get().asFile.apply {
            parentFile?.mkdirs()
            writeText(inputsHash)
        }

        val errors = report.violations.count { it.severity == Severity.ERROR }
        val warnings = report.violations.count { it.severity == Severity.WARNING }
        val infos = report.violations.count { it.severity == Severity.INFO }
        val totalAssets = exportIndex.assets.size
        val totalStores = exportIndex.assets.map { it.target }.distinct().size
        val totalLocales = exportIndex.assets.map { it.storeLocale }.distinct().size

        logger.lifecycle(
            "Store Studio validation: $errors error(s), $warnings warning(s), $infos info " +
                "($totalAssets assets, $totalStores stores, $totalLocales locales)",
        )

        val grouped = report.violations.groupBy {
            val target = it.target?.name ?: "GLOBAL"
            val locale = it.storeLocale ?: "ALL"
            val slot = it.deviceSlot?.name ?: "ALL"
            "$target / $locale / $slot"
        }

        for ((bucket, violations) in grouped) {
            logger.lifecycle("")
            logger.lifecycle(bucket)
            for (violation in violations) {
                val symbol = when (violation.severity) {
                    Severity.ERROR -> "  ✖"
                    Severity.WARNING -> "  ⚠"
                    Severity.INFO -> "  ℹ"
                }
                logger.lifecycle("$symbol ${violation.ruleId}  ${violation.message}")
                if (!violation.hint.isNullOrBlank()) {
                    logger.lifecycle("      hint: ${violation.hint}")
                }
            }
        }

        logger.lifecycle("")
        logger.lifecycle("Full report: ${reportOutput.path}")

        if (!report.valid) {
            throw GradleException(
                "Store Studio validation failed with $errors error(s). " +
                    "See report: ${reportOutput.path}",
            )
        }
    }
}
