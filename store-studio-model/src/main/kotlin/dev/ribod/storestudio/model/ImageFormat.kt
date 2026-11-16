package dev.ribod.storestudio.model

import kotlinx.serialization.Serializable

/**
 * Image encoding format accepted by store ingestion APIs.
 */
@Serializable
public enum class ImageFormat {
    /** 24-bit Lossless RGB Portable Network Graphics. Default and recommended format. */
    PNG,

    /** Lossy Joint Photographic Experts Group image format (fallback for strict file size limits). */
    JPEG,
}
