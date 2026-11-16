package dev.ribod.storestudio.model

import kotlinx.serialization.Serializable

/**
 * Spatial orientation of an image canvas.
 */
@Serializable
public enum class Orientation {
    /** Height exceeds width (standard phone/tablet showcase). */
    PORTRAIT,

    /** Width exceeds height (e.g. landscape video/tablet previews, feature graphics). */
    LANDSCAPE,

    /** Width equals height (e.g. 512x512 smartwatch display). */
    SQUARE,
}
