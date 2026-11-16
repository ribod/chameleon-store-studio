package dev.ribod.storestudio.model

import kotlinx.serialization.Serializable

/**
 * Name-compatible hardware frame identifiers for DeviceMockup in the runtime engine.
 *
 * Mapped to visual vectors in `store-studio-runtime`.
 */
@Serializable
public enum class FrameTypeName {
    /** Full-bleed canvas with no hardware bezel or frame. */
    NONE,

    /** Modern Android smartphone frame (e.g. Pixel with centered punch-hole camera). */
    ANDROID_PHONE,

    /** Modern Android tablet frame with slim uniform bezels. */
    ANDROID_TABLET,

    /** Modern iPhone bezel with Dynamic Island cutout mask. */
    IPHONE_DYNAMIC_ISLAND,

    /** Modern iPad Pro frame with uniform thin borders. */
    IPAD_MODERN,
}
