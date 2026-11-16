package dev.ribod.storestudio.annotations

/**
 * Represents the target commercial device category for application store listing assets.
 *
 * Each [DeviceFamily] maps to canonical hardware specifications, aspect ratios, and resolution
 * requirements defined by app stores (Google Play Console and Apple App Store Connect).
 *
 * When an entry is annotated with `@StoreScreenshot`, it can target one or more device families:
 * ```kotlin
 * @StoreScreenshot(
 *     id = "tablet_dashboard",
 *     devices = [DeviceFamily.TABLET]
 * )
 * @Composable
 * fun TabletDashboardScreenshot() {
 *     // Canvas layout optimized for larger screens
 * }
 * ```
 *
 * @see StoreScreenshot
 * @see StoreTarget
 */
public enum class DeviceFamily {
    /**
     * Standard handheld smartphones (e.g., Google Pixel, iPhone).
     *
     * Maps to:
     * - Google Play: Standard Phone display slot (1080x1920 or modern tall aspect ratios).
     * - Apple App Store: 6.9" display (`IPHONE_6_9`) and 6.7" / 6.5" slots (`IPHONE_6_5`).
     */
    PHONE,

    /**
     * Tablets and large screen devices (e.g., Pixel Tablet, iPad Pro 11", iPad Pro 13").
     *
     * Maps to:
     * - Google Play: 10-inch tablet listings (`TABLET_10`).
     * - Apple App Store: iPad 13" display (`IPAD_13`).
     */
    TABLET,

    /**
     * Compact 7-inch tablets (e.g., Nexus 7, smaller Android tablets).
     *
     * Maps to:
     * - Google Play: 7-inch tablet listings (`TABLET_7`).
     * - Apple App Store: No equivalent display type.
     */
    TABLET_7,

    /**
     * Desktop operating systems (e.g., macOS App Store, Windows Store).
     *
     * Reserved for multiplatform desktop applications utilizing Compose Multiplatform Desktop.
     */
    DESKTOP,

    /**
     * Wearable wrist devices (e.g., Wear OS, Apple Watch).
     *
     * Maps to circular and rectangular smartwatch display slots.
     */
    WATCH,

    /**
     * Television devices (e.g., Android TV, Google TV, Apple TV).
     *
     * Maps to 16:9 landscape 10-foot UI showcases (1080p / 4K).
     */
    TV,

    /**
     * Automotive head units and dashboards (e.g., Android Auto, Android Automotive OS).
     */
    AUTO,

    /**
     * Extended reality and spatial computing platforms (e.g., Android XR, Apple visionOS).
     */
    XR,
}

/**
 * Natural commercial display order for device families: phones ascending by physical size,
 * tablets ascending by physical size, desktop, wearables, living room (TV), automotive,
 * and spatial/extended reality (XR).
 */
public val DeviceFamily.naturalDisplayOrder: Int
    get() = when (this) {
        DeviceFamily.PHONE -> 10
        DeviceFamily.TABLET_7 -> 20
        DeviceFamily.TABLET -> 21
        DeviceFamily.DESKTOP -> 25
        DeviceFamily.WATCH -> 30
        DeviceFamily.TV -> 35
        DeviceFamily.AUTO -> 36
        DeviceFamily.XR -> 37
    }
