package dev.ribod.storestudio.annotations

/**
 * Identifies the target digital distribution platform (application store).
 *
 * Used to restrict screenshots or metadata to specific stores when requirements or marketing
 * strategies differ between Android and iOS ecosystems.
 *
 * If no targets are explicitly specified on `@StoreScreenshot`, the asset targets all stores
 * configured in the root `storeStudio { targets.set(...) }` Gradle extension block.
 *
 * Example:
 * ```kotlin
 * @StoreScreenshot(
 *     id = "apple_pay_banner",
 *     targets = [StoreTarget.APP_STORE]
 * )
 * @Composable
 * fun ApplePayShowcase() {
 *     // Only rendered and exported for Apple App Store Connect
 * }
 * ```
 *
 * @see StoreScreenshot
 * @see DeviceFamily
 */
public enum class StoreTarget {
    /**
     * Google Play Store (Google Play Console).
     *
     * Validates assets against Google Play requirements:
     * - Phone, 7-inch tablet, 10-inch tablet screenshot buckets (max 8 per bucket).
     * - PNG or JPEG format, 24-bit RGB (no alpha channel), 320px minimum, 3840px maximum.
     * - Aspect ratio 16:9 or 2:1.
     */
    PLAY_STORE,

    /**
     * Apple App Store (App Store Connect).
     *
     * Validates assets against Apple App Store Connect guidelines:
     * - Strict resolution requirements (e.g., 1320x2868 for 6.9" display, 2064x2752 for 13" iPad).
     * - Exact pixel dimensions, 72 dpi, sRGB or Display P3, flattened RGB (no alpha channel).
     * - Max 10 screenshots per display type.
     */
    APP_STORE,
}
