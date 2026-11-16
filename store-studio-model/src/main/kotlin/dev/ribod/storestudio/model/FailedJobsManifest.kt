package dev.ribod.storestudio.model

import dev.ribod.storestudio.annotations.StoreTarget
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Isolated job specification for an asset that failed during primary engine execution.
 *
 * Consumed by secondary fallback runners (such as Robolectric Native Graphics) to
 * re-render exclusively the failed items without repeating successful jobs.
 *
 * @property jobId Canonical job identifier matching the primary run.
 * @property slotKey Canonical hierarchy key.
 * @property entryId Identifier of the source annotated Composable.
 * @property methodFqn JVM bytecode fully qualified name for dynamic reflection invocation.
 * @property target Store platform target.
 * @property deviceSlot Target device slot.
 * @property storeLocale Store-specific language code.
 * @property localeTag BCP-47 language tag.
 * @property widthPx Target width in physical pixels.
 * @property heightPx Target height in physical pixels.
 * @property widthDp Density-independent canvas width.
 * @property heightDp Density-independent canvas height.
 * @property dpi Screen pixel density.
 * @property outputPath Target raw output PNG path, relative to the render raw images directory.
 * @property failureReason Diagnostic explanation of primary engine failure.
 */
@Serializable
public data class FailedJobSpec(
    public val jobId: String,
    public val slotKey: String,
    public val entryId: String,
    public val methodFqn: String,
    public val target: StoreTarget,
    public val deviceSlot: StoreDeviceSlot,
    public val storeLocale: String,
    public val localeTag: String,
    public val widthPx: Int,
    public val heightPx: Int,
    public val widthDp: Int,
    public val heightDp: Int,
    public val dpi: Int,
    public val outputPath: String,
    public val failureReason: String,
)

/**
 * Manifest containing jobs eligible for fallback execution (`build/store/render/failed_jobs.json`).
 *
 * @property schemaVersion Integer contract version (currently 1).
 * @property jobs List of failed jobs requiring fallback processing.
 */
@Serializable
public data class FailedJobsManifest(
    public val schemaVersion: Int = 1,
    public val jobs: List<FailedJobSpec>,
) {
    public companion object {
        /** Canonical JSON serializer conforming to data contract principles. */
        public val json: Json = ManifestJson

        /**
         * Parses a serialized JSON string into a [FailedJobsManifest].
         */
        public fun decodeFromString(string: String): FailedJobsManifest {
            val manifest = json.decodeFromString<FailedJobsManifest>(string)
            require(manifest.schemaVersion <= 1) {
                "Unsupported failed_jobs.json schema version ${manifest.schemaVersion}. " +
                    "Supported version is <= 1. Please upgrade Store Studio."
            }
            return manifest
        }

        /**
         * Serializes this [FailedJobsManifest] into canonical JSON format.
         */
        public fun encodeToString(manifest: FailedJobsManifest): String =
            json.encodeToString(manifest)
    }
}
