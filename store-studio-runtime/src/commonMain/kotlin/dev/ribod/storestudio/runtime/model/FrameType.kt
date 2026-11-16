package dev.ribod.storestudio.runtime.model

/**
 * Visual hardware frame classifications for [dev.ribod.storestudio.runtime.mockup.DeviceFrame].
 *
 * Mapped to name-compatible specifications in `:store-studio-core` for cross-module catalog
 * consistency without coupling the core data contracts to Compose Multiplatform UI toolkits.
 */
public enum class FrameType {
    /** Full-bleed canvas with no hardware bezel or frame borders. */
    NONE,

    /** Modern Android smartphone frame featuring a centered punch-hole camera. */
    ANDROID_PHONE,

    /** Modern Android tablet frame featuring uniform slim bezels. */
    ANDROID_TABLET,

    /** Modern iPhone hardware frame featuring rounded corners and Dynamic Island cutout. */
    IPHONE_DYNAMIC_ISLAND,

    /** Modern iPad Pro frame with uniform thin borders and rounded display corners. */
    IPAD_MODERN,
}
