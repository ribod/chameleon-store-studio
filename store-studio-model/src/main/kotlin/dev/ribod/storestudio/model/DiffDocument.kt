package dev.ribod.storestudio.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Categorization of visual and structural changes between baseline and working assets.
 *
 * Conforms to schema version 1 defined in data-contracts.md section 8 and DD-10.
 */
@Serializable
public enum class DiffChangeType {
    ADDED,
    REMOVED,
    REORDERED,
    MODIFIED,
    REPLACED,
    MOVED_AND_MODIFIED,
    UNCHANGED,
}

/**
 * Fine-grained semantic classification for visual modifications (DD-18).
 */
@Serializable
public enum class SemanticModificationType {
    VISUAL_LAYOUT_ONLY,
    TEXT_AND_VISUAL_MODIFIED,
    UNKNOWN,
}

/**
 * Git revision reference provenance and metadata.
 */
@Serializable
public data class DiffRefInfo(
    public val ref: String,
    public val commit: String? = null,
    public val source: String,
)

/**
 * Aggregated metric counts across all evaluated visual asset slots.
 */
@Serializable
public data class DiffSummary(
    public val added: Int = 0,
    public val removed: Int = 0,
    public val reordered: Int = 0,
    public val modified: Int = 0,
    public val replaced: Int = 0,
    public val movedAndModified: Int = 0,
    public val unchanged: Int = 0,
) {
    public val totalChanges: Int
        get() = added + removed + reordered + modified + replaced + movedAndModified

    public val totalAssets: Int
        get() = totalChanges + unchanged
}

/**
 * Fine-grained visual difference record for a single asset slot.
 */
@Serializable
public data class DiffChange(
    public val type: DiffChangeType,
    public val bucket: String,
    public val id: String,
    public val basePosition: Int? = null,
    public val headPosition: Int? = null,
    public val pHashDistance: Double? = null,
    public val baseImage: String? = null,
    public val headImage: String? = null,
    public val basePixelSha256: String? = null,
    public val headPixelSha256: String? = null,
    public val semanticType: SemanticModificationType? = null,
    public val baseTextSha256: String? = null,
    public val headTextSha256: String? = null,
    public val removedTexts: List<String> = emptyList(),
    public val addedTexts: List<String> = emptyList(),
    public val hasVisualOverflow: Boolean = false,
    public val headEngine: String = "LAYOUTLIB",
)

/**
 * Machine-readable visual regression and change diff document.
 *
 * Written to 'build/store/diff/diff.json' by StoreDiffTask (DD-10).
 */
@Serializable
public data class DiffDocument(
    public val schemaVersion: Int = 1,
    public val base: DiffRefInfo,
    public val head: DiffRefInfo,
    public val summary: DiffSummary,
    public val changes: List<DiffChange> = emptyList(),
) {

    public fun toJson(json: Json = DEFAULT_JSON): String = json.encodeToString(this)

    public fun toFile(file: File, json: Json = DEFAULT_JSON) {
        file.parentFile?.mkdirs()
        file.writeText(toJson(json))
    }

    public companion object {
        private val DEFAULT_JSON = ArtifactJson

        public fun fromJson(content: String, json: Json = DEFAULT_JSON): DiffDocument =
            json.decodeFromString(serializer(), content)

        public fun fromFile(file: File, json: Json = DEFAULT_JSON): DiffDocument =
            fromJson(file.readText(), json)
    }
}
