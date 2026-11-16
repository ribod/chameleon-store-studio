package dev.ribod.storestudio.model

import dev.ribod.storestudio.annotations.StoreTarget
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Diagnostic record associated with a headless screenshot rendering execution.
 *
 * Captures non-fatal warnings, missing class notices, or fatal rendering problems
 * encountered during Layoutlib or fallback engine runs.
 *
 * @property severity Importance classification ("INFO", "WARNING", "ERROR").
 * @property code Machine-readable diagnostic error code (e.g. "LAYOUTLIB_FAILED_FALLBACK_USED").
 * @property message Detailed technical description or stack trace summary.
 */
@Serializable
public data class RenderDiagnostic(
    public val severity: String,
    public val code: String,
    public val message: String,
)

/**
 * Execution outcome for a single render matrix job.
 *
 * Represents the rendered state of an asset targeting a specific store, device slot, and locale.
 *
 * @property jobId Canonical unique job identifier
 *   (`<entryId>__<target>__<deviceSlot>__<storeLocale>`).
 * @property slotKey Canonical hierarchy key (`<target>/<deviceSlot>/<storeLocale>/<entryId>`).
 * @property entryId Identifier of the source annotated Composable.
 * @property target Distribution platform store target.
 * @property deviceSlot Target physical or promotional device slot.
 * @property storeLocale Store-specific language code (e.g. "es-419", "en-US").
 * @property localeTag BCP-47 language tag (e.g. "es-MX", "en-US").
 * @property widthPx Rendered canvas width in pixels.
 * @property heightPx Rendered canvas height in pixels.
 * @property dpi Screen pixel density.
 * @property engine Rendering engine utilized ("LAYOUTLIB", "ROBOLECTRIC").
 * @property status Execution status ("SUCCESS", "FAILED", "SKIPPED").
 * @property image Relative path to the rendered raw PNG file, or null on failure.
 * @property durationMs Execution duration in milliseconds.
 * @property diagnostics List of diagnostics and warnings collected during rendering.
 */
@Serializable
public data class RenderJobResult(
    public val jobId: String,
    public val slotKey: String,
    public val entryId: String,
    public val target: StoreTarget,
    public val deviceSlot: StoreDeviceSlot,
    public val storeLocale: String,
    public val localeTag: String,
    public val widthPx: Int,
    public val heightPx: Int,
    public val dpi: Int,
    public val engine: String,
    public val status: String,
    public val image: String? = null,
    public val durationMs: Long = 0L,
    public val diagnostics: List<RenderDiagnostic> = emptyList(),
)

/**
 * Canonical index manifest produced by headless rendering tasks (`build/store/render/index.json`).
 *
 * Conforms to schema version 1 defined in data-contracts.md.
 *
 * @property schemaVersion Integer contract version (currently 1).
 * @property engineDefault Default configured rendering engine name.
 * @property layoutlibVersion Layoutlib framework version employed.
 * @property renderedAt ISO 8601 timestamp string representing execution completion.
 * @property jobs List of individual matrix render job execution results.
 */
@Serializable
public data class RenderIndex(
    public val schemaVersion: Int = 1,
    public val engineDefault: String = "LAYOUTLIB",
    public val layoutlibVersion: String,
    public val renderedAt: String,
    public val jobs: List<RenderJobResult>,
) {
    public companion object {
        /** Canonical JSON serializer conforming to data contract principles. */
        public val json: Json = ManifestJson

        /**
         * Parses a serialized JSON string into a [RenderIndex], verifying schema compatibility.
         */
        public fun decodeFromString(string: String): RenderIndex {
            val index = json.decodeFromString<RenderIndex>(string)
            require(index.schemaVersion <= 1) {
                "Unsupported render/index.json schema version ${index.schemaVersion}. " +
                    "Supported version is <= 1. Please upgrade Store Studio."
            }
            return index
        }

        /**
         * Serializes this [RenderIndex] into deterministic canonical JSON format.
         */
        public fun encodeToString(index: RenderIndex): String = json.encodeToString(index)
    }
}

/**
 * Applies the fallback engine's results (`render/robolectric/index.json`): each of its jobs
 * replaces the Layoutlib job with the same id. Images of jobs whose `engine` is `ROBOLECTRIC` are
 * relative to the fallback's image directory, all others to Layoutlib's.
 */
public fun RenderIndex.withFallback(fallback: RenderIndex?): RenderIndex {
    if (fallback == null || fallback.jobs.isEmpty()) return this
    val fallbackById = fallback.jobs.associateBy { it.jobId }
    return copy(jobs = jobs.map { fallbackById[it.jobId] ?: it })
}
