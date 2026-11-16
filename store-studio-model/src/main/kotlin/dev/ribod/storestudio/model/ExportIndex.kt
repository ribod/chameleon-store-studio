package dev.ribod.storestudio.model

import dev.ribod.storestudio.annotations.StoreTarget
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manifest indexing all export-ready, store-compliant visual assets.
 *
 * Conforms to schema version 1 defined in data-contracts.md section 5.
 *
 * Assets indexed in this document are strictly guaranteed to be:
 * - Rendered and validated against physical hardware catalog dimensions.
 * - Free of alpha channels (RGB 24-bit or opaque JPEG) for store compliance.
 * - Normalized into the sRGB color space.
 * - Deterministically named and ordered within their respective device slot buckets.
 * - Fully fingerprinted with cryptographic (pixelSha256, fileSha256, md5)
 *   and perceptual (pHash) checksums.
 */
@Serializable
public data class ExportIndex(
    public val schemaVersion: Int = 1,
    public val assets: List<ExportAsset> = emptyList(),
) {

    public fun toJson(json: Json = DEFAULT_JSON): String = json.encodeToString(this)

    public fun toFile(file: File, json: Json = DEFAULT_JSON) {
        file.parentFile?.mkdirs()
        file.writeText(toJson(json))
    }

    public companion object {
        private val DEFAULT_JSON = ArtifactJson

        public fun fromJson(content: String, json: Json = DEFAULT_JSON): ExportIndex =
            json.decodeFromString(serializer(), content)

        public fun fromFile(file: File, json: Json = DEFAULT_JSON): ExportIndex =
            fromJson(file.readText(), json)
    }
}

/**
 * Metadata descriptor for a single store-ready visual asset.
 */
@Serializable
public data class ExportAsset(
    /** Canonical hierarchical slot identifier: target/deviceSlot/storeLocale/id */
    public val slotKey: String,
    /** Targeted marketplace (Google Play or Apple App Store). */
    public val target: StoreTarget,
    /** Hardware display category or graphic slot. */
    public val deviceSlot: StoreDeviceSlot,
    /** Normalized store localization tag (e.g., 'en-US', 'es-419'). */
    public val storeLocale: String,
    /** Logical identifier corresponding to the source screenshot entry. */
    public val id: String,
    /** Declared ordering sequence from the screenshot annotation. */
    public val order: Int,
    /** Dense 1-based sequential position within the bucket. */
    public val position: Int,
    /** Relative filesystem path from the export root directory. */
    public val file: String,
    /** Encoded image container format (PNG or JPEG). */
    public val format: ImageFormat,
    /** Physical width in pixels. */
    public val widthPx: Int,
    /** Physical height in pixels. */
    public val heightPx: Int,
    /** Total file size in bytes on disk. */
    public val bytes: Long,
    /** Whether the image contains transparency (always false for store assets). */
    public val hasAlpha: Boolean = false,
    /** Color profile standard (always 'sRGB'). */
    public val colorSpace: String = "sRGB",
    /** Encoder-independent SHA-256 hash across canonical RGBA pixel raster. */
    public val pixelSha256: String,
    /** Cryptographic SHA-256 hash over the encoded file binary. */
    public val fileSha256: String,
    /** 64-bit Discrete Cosine Transform perceptual hash (hexadecimal). */
    public val pHash: String,
    /** Binary MD5 digest for App Store Connect upload checksums. */
    public val md5: String,
    /** Name of the execution engine that rendered the source pixels. */
    public val engine: String = "LAYOUTLIB",
    /** Optional accessibility/semantics tree text hash. */
    public val textSha256: String? = null,
    /** Extracted visible texts and accessibility labels in reading order. */
    public val extractedTexts: List<String> = emptyList(),
    /** Indicates if any text node reported visual truncation/overflow. */
    public val hasVisualOverflow: Boolean = false,
)
