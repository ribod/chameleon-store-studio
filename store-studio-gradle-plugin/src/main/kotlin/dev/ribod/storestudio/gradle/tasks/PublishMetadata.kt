package dev.ribod.storestudio.gradle.tasks

import com.skydoves.sandwich.suspendOnError
import com.skydoves.sandwich.suspendOnException
import com.skydoves.sandwich.suspendOnSuccess
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.hash.AssetHasher
import dev.ribod.storestudio.core.metadata.StoreMetadataManifest
import dev.ribod.storestudio.core.metadata.StoreMetadataParser
import dev.ribod.storestudio.core.publish.PublishPart
import dev.ribod.storestudio.core.publish.appstore.AppStorePublishRequest
import dev.ribod.storestudio.core.publish.appstore.AppStorePublisher
import dev.ribod.storestudio.core.publish.play.PlayPublishRequest
import dev.ribod.storestudio.core.publish.play.PlayStorePublisher
import dev.ribod.storestudio.core.validation.MetadataRules
import dev.ribod.storestudio.core.validation.RuleEngine
import dev.ribod.storestudio.core.validation.ValidationContext
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.Severity
import dev.ribod.storestudio.model.ValidationReport
import kotlinx.coroutines.runBlocking
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File

/**
 * Loads listing metadata for publishing: the consolidated `metadata.json` when present and
 * non-empty, otherwise the raw metadata directory. The result is keyed by store locale:
 * [storeLocales] maps each DSL locale to the code the store uses (identity when absent).
 */
internal fun loadPublishMetadata(
    metadataFile: File?,
    metadataDir: File?,
    locales: List<String>,
    storeLocales: Map<String, String> = emptyMap(),
): StoreMetadataManifest? {
    if (metadataFile != null && metadataFile.exists()) {
        val manifest = runCatching { StoreMetadataManifest.fromFile(metadataFile) }.getOrNull()
        if (manifest != null && manifest.locales.isNotEmpty()) {
            return manifest.withStoreLocales(storeLocales)
        }
    }
    if (metadataDir != null && metadataDir.exists()) {
        return StoreMetadataParser.parse(
            metadataDir = metadataDir,
            configuredLocales = locales + storeLocales.keys,
        ).withStoreLocales(storeLocales)
    }
    return null
}

private fun Map<String, String>.requireDistinct() {
    val clash = entries.groupBy({ it.value }, { it.key }).filterValues { it.size > 1 }
    if (clash.isNotEmpty()) {
        throw GradleException(
            "Store Studio: more than one locale maps to the same store locale: " +
                clash.entries.joinToString { "${it.key} <- ${it.value.joinToString()}" } +
                ". Fix the playStore/appStore aliases in storeStudio.locales.",
        )
    }
}

/** Renames every locale through [storeLocales]; two locales ending up equal fail the build. */
private fun <T> Map<String, T>.renameLocales(
    storeLocales: Map<String, String>,
    transform: (String, T) -> T,
): Map<String, T> {
    val out = LinkedHashMap<String, T>()
    for ((locale, value) in this) {
        val store = storeLocales[locale] ?: locale
        if (out.put(store, transform(store, value)) != null) {
            throw GradleException(
                "Store Studio: more than one locale maps to the store locale '$store' " +
                    "(${keys.filter { (storeLocales[it] ?: it) == store }.joinToString()}). " +
                    "Fix the playStore/appStore aliases in storeStudio.locales.",
            )
        }
    }
    return out
}

internal fun StoreMetadataManifest.withStoreLocales(
    storeLocales: Map<String, String>,
): StoreMetadataManifest = if (storeLocales.isEmpty()) {
    this
} else {
    storeLocales.requireDistinct()
    copy(
        defaultLocale = storeLocales[defaultLocale] ?: defaultLocale,
        locales = locales.renameLocales(storeLocales) { store, bundle ->
            bundle.copy(locale = store)
        },
    )
}

/** Re-keys the assets of [target] by store locale; other targets are left as they are. */
internal fun ExportIndex.withStoreLocales(
    storeLocales: Map<String, String>,
    target: StoreTarget,
): ExportIndex {
    storeLocales.requireDistinct()
    return copy(
        assets = assets.map { asset ->
            if (asset.target == target) {
                asset.copy(storeLocale = storeLocales[asset.storeLocale] ?: asset.storeLocale)
            } else {
                asset
            }
        },
    )
}

/**
 * Validates a Google Play track identifier: one of the standard tracks or a custom track name,
 * without whitespace or path separators.
 */
internal fun requireValidPlayTrack(track: String?): String? {
    val value = track?.trim()?.ifEmpty { null } ?: return null
    if (value.any { it.isWhitespace() || it == '/' }) {
        throw GradleException(
            "Store Studio: Invalid Google Play track '$value'. Use internal, alpha, beta, " +
                "production or the name of a custom testing track.",
        )
    }
    return value
}

/**
 * Publishing gate shared by all publish tasks: the export must have been validated in this
 * build, without errors, and must not have changed since (checked against `inputs.sha256`).
 */
internal fun enforcePublishingGate(reportFile: File, exportIndexFile: File, inputsHashFile: File) {
    if (!reportFile.exists() || !inputsHashFile.exists()) {
        throw GradleException(
            "Assets were not validated in this build (storeValidate excluded). " +
                "Publishing requires prior compliance validation.",
        )
    }

    val report = ValidationReport.fromFile(reportFile)
    if (!report.valid) {
        throw GradleException(
            "Publishing aborted: export report contains validation errors. " +
                "Run storeValidate to view findings.",
        )
    }

    val expectedHash = inputsHashFile.readText().trim()
    val currentHash = AssetHasher.fileSha256(exportIndexFile)
    if (expectedHash != currentHash) {
        throw GradleException(
            "Publishing aborted: export index was modified after validation. " +
                "Re-run storeValidate.",
        )
    }
}

/**
 * Gate for publishes without images: there is no export to validate, so the metadata rules of
 * the selected [parts] run instead, failing the build on errors like `storeValidate` would.
 * Returns the metadata, which must exist.
 */
internal fun validateTextForPublish(
    metadata: StoreMetadataManifest?,
    target: StoreTarget,
    parts: Set<PublishPart>,
    severityOverrides: Map<String, Severity>,
    failOnWarning: Boolean,
    logger: Logger,
): StoreMetadataManifest {
    if (metadata == null || metadata.locales.isEmpty()) {
        throw GradleException(
            "Store Studio: No store metadata found to publish. Add text files under " +
                "'<locale>/play/' or '<locale>/appstore/' in the metadata directory.",
        )
    }
    val notesRules = setOf("PLAY_TEXT_RELEASE_NOTES_LENGTH", "APPSTORE_TEXT_WHATS_NEW_LENGTH")
    val rules = MetadataRules.ALL.filter {
        if (it.id in notesRules) {
            PublishPart.RELEASE_NOTES in parts
        } else {
            PublishPart.LISTING in parts
        }
    }
    val ctx = ValidationContext(
        exportIndex = ExportIndex(),
        configuredTargets = setOf(target),
        metadataManifest = metadata,
    )
    val report = RuleEngine(rules).run(
        ctx = ctx,
        overrides = severityOverrides.filterKeys { key -> rules.any { it.id == key } },
        failOnWarning = failOnWarning,
    )
    for (violation in report.violations) {
        val locale = violation.storeLocale?.let { " [$it]" }.orEmpty()
        logger.lifecycle("${violation.severity} ${violation.ruleId}$locale ${violation.message}")
    }
    if (!report.valid) {
        throw GradleException(
            "Publishing aborted: the store metadata has " +
                "${report.violations.count { it.severity == Severity.ERROR }} error(s). " +
                "Fix the texts listed above.",
        )
    }
    return metadata
}

/** Publishes [request] to Google Play, logging the report or failing the build. */
internal fun runPlayPublish(logger: Logger, request: PlayPublishRequest) {
    PlayStorePublisher.create().use { publisher ->
        runBlocking {
            publisher.publish(request).suspendOnSuccess {
                logger.lifecycle(data.formattedReport)
            }.suspendOnError {
                val msg = "Store Studio: Google Play publication failed: $payload"
                logger.error(msg)
                throw GradleException(msg)
            }.suspendOnException {
                val msg = "Store Studio: Google Play publication exception: " + throwable.message
                logger.error(msg, throwable)
                throw GradleException(msg, throwable)
            }
        }
    }
}

/** Publishes [request] to App Store Connect, logging the report or failing the build. */
internal fun runAppStorePublish(logger: Logger, request: AppStorePublishRequest) {
    AppStorePublisher.create().use { publisher ->
        runBlocking {
            publisher.publish(request).suspendOnSuccess {
                logger.lifecycle(data.formattedReport)
            }.suspendOnError {
                val msg = "Store Studio: App Store Connect publication failed: $payload"
                logger.error(msg)
                throw GradleException(msg)
            }.suspendOnException {
                val msg = "Store Studio: App Store Connect publication exception: " +
                    throwable.message
                logger.error(msg, throwable)
                throw GradleException(msg, throwable)
            }
        }
    }
}
