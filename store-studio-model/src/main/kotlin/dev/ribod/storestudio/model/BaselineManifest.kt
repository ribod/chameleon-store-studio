package dev.ribod.storestudio.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Committed baseline document indexing approved visual asset fingerprints.
 *
 * Conforms to schema version 1 defined in data-contracts.md section 7 and DD-09.
 *
 * This file is version-controlled in Git (typically `.store/baseline.json`) to enforce
 * visual regression drift detection without committing binary images (ADR-004).
 */
@Serializable
public data class BaselineManifest(
    public val schemaVersion: Int = 1,
    public val recordedWith: String = UNKNOWN,
    public val layoutlibVersion: String = UNKNOWN,
    public val assets: List<BaselineAsset> = emptyList(),
) {

    public fun toJson(json: Json = DEFAULT_JSON): String = json.encodeToString(this)

    public fun toFile(file: File, json: Json = DEFAULT_JSON) {
        file.parentFile?.mkdirs()
        file.writeText(toJson(json))
    }

    public companion object {
        private val DEFAULT_JSON = ArtifactJson

        /** Placeholder when the producing tool or layoutlib version is not known. */
        public const val UNKNOWN: String = "unknown"

        public fun fromJson(content: String, json: Json = DEFAULT_JSON): BaselineManifest =
            json.decodeFromString(serializer(), content)

        public fun fromFile(file: File, json: Json = DEFAULT_JSON): BaselineManifest =
            fromJson(file.readText(), json)

        /**
         * Builds the fingerprints of an export, used both to record a baseline and to compare
         * against an export rendered from another revision.
         */
        public fun fromExportIndex(
            index: ExportIndex,
            layoutlibVersion: String = UNKNOWN,
            recordedWith: String = UNKNOWN,
        ): BaselineManifest = BaselineManifest(
            recordedWith = recordedWith,
            layoutlibVersion = layoutlibVersion,
            assets = index.assets.map { asset ->
                BaselineAsset(
                    slotKey = asset.slotKey,
                    position = asset.position,
                    widthPx = asset.widthPx,
                    heightPx = asset.heightPx,
                    pixelSha256 = asset.pixelSha256,
                    pHash = asset.pHash,
                    textSha256 = asset.textSha256,
                    texts = asset.extractedTexts,
                )
            }.sortedBy { it.slotKey },
        )
    }
}

/**
 * Fingerprint metadata for a single baseline visual asset.
 */
@Serializable
public data class BaselineAsset(
    /** Canonical hierarchical slot identifier: target/deviceSlot/storeLocale/id */
    public val slotKey: String,
    /** Dense 1-based sequential position within the bucket. */
    public val position: Int,
    /** Physical width in pixels. */
    public val widthPx: Int,
    /** Physical height in pixels. */
    public val heightPx: Int,
    /** Encoder-independent SHA-256 hash across canonical RGBA pixel raster. */
    public val pixelSha256: String,
    /** 64-bit Discrete Cosine Transform perceptual hash (hexadecimal). */
    public val pHash: String,
    /** Optional accessibility/semantics tree text hash. */
    public val textSha256: String? = null,
    /** Extracted visible texts and accessibility labels in reading order. */
    public val texts: List<String> = emptyList(),
)
