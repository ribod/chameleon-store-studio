package dev.ribod.storestudio.runtime.model

import androidx.compose.runtime.Immutable

/**
 * Geometric hardware and display specifications for a targeted store device slot.
 *
 * Provides both physical pixel measurements and computed density-independent dimensions
 * derived from the official store submission standards.
 *
 * @property slot Unique store device slot name (e.g. "PHONE", "IPHONE_6_9").
 * @property widthPx Physical display width in pixels.
 * @property heightPx Physical display height in pixels.
 * @property dpi Display density in dots per inch.
 * @property frame Hardware bezel styling to apply when wrapping UI in a mockup.
 */
@Immutable
public data class StoreDeviceSpec(
    public val slot: String,
    public val widthPx: Int,
    public val heightPx: Int,
    public val dpi: Int,
    public val frame: FrameType,
) {
    /** Display width in density-independent pixels. */
    public val widthDp: Float get() = widthPx * 160f / dpi

    /** Display height in density-independent pixels. */
    public val heightDp: Float get() = heightPx * 160f / dpi

    public companion object {
        /** Google Play Phone baseline specification (1080x1920 @ 480 dpi). */
        public val PlayPhone: StoreDeviceSpec = StoreDeviceSpec(
            slot = "PHONE",
            widthPx = 1080,
            heightPx = 1920,
            dpi = 480,
            frame = FrameType.ANDROID_PHONE,
        )

        /** Google Play 7-inch tablet baseline specification (1200x1920 @ 320 dpi). */
        public val PlayTablet7: StoreDeviceSpec = StoreDeviceSpec(
            slot = "TABLET_7",
            widthPx = 1200,
            heightPx = 1920,
            dpi = 320,
            frame = FrameType.ANDROID_TABLET,
        )

        /** Google Play 10-inch tablet baseline specification (1600x2560 @ 320 dpi). */
        public val PlayTablet10: StoreDeviceSpec = StoreDeviceSpec(
            slot = "TABLET_10",
            widthPx = 1600,
            heightPx = 2560,
            dpi = 320,
            frame = FrameType.ANDROID_TABLET,
        )

        /** Apple App Store 6.9-inch iPhone flagship specification (1320x2868 @ 460 dpi). */
        public val Iphone69: StoreDeviceSpec = StoreDeviceSpec(
            slot = "IPHONE_6_9",
            widthPx = 1320,
            heightPx = 2868,
            dpi = 460,
            frame = FrameType.IPHONE_DYNAMIC_ISLAND,
        )

        /** Apple App Store 6.5-inch iPhone standard specification (1284x2778 @ 460 dpi). */
        public val Iphone65: StoreDeviceSpec = StoreDeviceSpec(
            slot = "IPHONE_6_5",
            widthPx = 1284,
            heightPx = 2778,
            dpi = 460,
            frame = FrameType.IPHONE_DYNAMIC_ISLAND,
        )

        /** Apple App Store 13-inch iPad Pro flagship specification (2064x2752 @ 264 dpi). */
        public val Ipad13: StoreDeviceSpec = StoreDeviceSpec(
            slot = "IPAD_13",
            widthPx = 2064,
            heightPx = 2752,
            dpi = 264,
            frame = FrameType.IPAD_MODERN,
        )

        /** Google Play Feature Graphic specification (1024x500 @ 160 dpi). */
        public val FeatureGraphic: StoreDeviceSpec = StoreDeviceSpec(
            slot = "FEATURE_GRAPHIC",
            widthPx = 1024,
            heightPx = 500,
            dpi = 160,
            frame = FrameType.NONE,
        )
    }
}
