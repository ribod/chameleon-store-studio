package dev.ribod.storestudio.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Geometric bounds of a semantics node in root coordinate space.
 */
@Serializable
public data class SemanticsBounds(
    public val left: Float,
    public val top: Float,
    public val right: Float,
    public val bottom: Float,
)

/**
 * Extracted Compose accessibility and text node information.
 */
@Serializable
public data class ExtractedSemanticsNode(
    public val id: Int,
    public val texts: List<String> = emptyList(),
    public val contentDescription: String? = null,
    public val role: String? = null,
    public val bounds: SemanticsBounds? = null,
    public val hasVisualOverflow: Boolean = false,
)

/**
 * Snapshot of the semantics tree captured during layout or render pass (DD-18).
 */
@Serializable
public data class SemanticsSnapshot(
    public val schemaVersion: Int = 1,
    public val jobId: String,
    public val slotKey: String,
    public val locale: String? = null,
    public val textSha256: String,
    public val nodes: List<ExtractedSemanticsNode> = emptyList(),
    public val hasAnyVisualOverflow: Boolean = nodes.any { it.hasVisualOverflow },
) {

    public fun toJson(json: Json = DEFAULT_JSON): String = json.encodeToString(this)

    public fun toFile(file: File, json: Json = DEFAULT_JSON) {
        file.parentFile?.mkdirs()
        file.writeText(toJson(json))
    }

    public companion object {
        private val DEFAULT_JSON = ArtifactJson

        public fun fromJson(content: String, json: Json = DEFAULT_JSON): SemanticsSnapshot =
            json.decodeFromString(serializer(), content)

        public fun fromFile(file: File, json: Json = DEFAULT_JSON): SemanticsSnapshot =
            fromJson(file.readText(), json)

        /**
         * Calculates deterministic SHA-256 hash across all normalized strings in the snapshot.
         */
        public fun computeTextSha256(nodes: List<ExtractedSemanticsNode>): String {
            val lines = nodes.flatMap { node ->
                node.texts + listOfNotNull(node.contentDescription)
            }.map { it.trim() }.filter { it.isNotEmpty() }

            val payload = lines.joinToString("\n")
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(payload.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
