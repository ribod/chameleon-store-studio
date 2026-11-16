package dev.ribod.storestudio.runtime.mockup

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ribod.storestudio.runtime.model.FrameType

/**
 * Geometric parameters and styling properties for a vector device hardware chassis.
 *
 * Each frame defines the chassis aspect ratio, border thickness, corner radii, display cutouts,
 * and virtual layout dimensions without relying on external raster graphics.
 */
@Immutable
public sealed class DeviceFrame {
    /** Outer chassis aspect ratio (width divided by height). */
    public abstract val aspect: Float

    /** Bezel border thickness as a fraction of total device width. */
    public abstract val bezelFraction: Float

    /** Outer chassis corner radius as a fraction of total device width. */
    public abstract val cornerRadiusFraction: Float

    /** Inner active display glass corner radius as a fraction of screen width. */
    public abstract val screenCornerRadiusFraction: Float

    /** Camera or sensor aperture cutout style. */
    public abstract val cutout: Cutout

    /** Target virtual width at which the wrapped app UI calculates its layout. */
    public abstract val defaultScreenWidthDp: Dp

    /** Status bar overlay height in virtual density-independent pixels. */
    public abstract val statusBarHeightDp: Dp

    /** Bottom gesture navigation pill inset height in virtual density-independent pixels. */
    public abstract val bottomInsetDp: Dp

    /** Indicates whether the frame represents a tablet or large-format form factor. */
    public open val isTablet: Boolean get() = false

    /**
     * Resolves the concrete [DeviceFrame] associated with a given [FrameType] specification.
     */
    public fun resolveFrame(type: FrameType): DeviceFrame = when (type) {
        FrameType.ANDROID_PHONE -> PhonePunchHole
        FrameType.IPHONE_DYNAMIC_ISLAND -> PhoneIsland
        FrameType.ANDROID_TABLET -> TabletAndroid
        FrameType.IPAD_MODERN -> TabletSlim
        FrameType.NONE -> None
    }

    /**
     * Automatic frame resolver dynamically mapping to the active slot's frame classification.
     */
    public data object Auto : DeviceFrame() {
        override val aspect: Float get() = PhoneIsland.aspect
        override val bezelFraction: Float get() = PhoneIsland.bezelFraction
        override val cornerRadiusFraction: Float get() = PhoneIsland.cornerRadiusFraction
        override val screenCornerRadiusFraction: Float
            get() = PhoneIsland.screenCornerRadiusFraction
        override val cutout: Cutout get() = PhoneIsland.cutout
        override val defaultScreenWidthDp: Dp get() = PhoneIsland.defaultScreenWidthDp
        override val statusBarHeightDp: Dp get() = PhoneIsland.statusBarHeightDp
        override val bottomInsetDp: Dp get() = PhoneIsland.bottomInsetDp
        override val isTablet: Boolean get() = false
    }

    /** Modern Android smartphone frame featuring a centered punch-hole camera. */
    public data object PhonePunchHole : DeviceFrame() {
        override val aspect: Float = 0.46f
        override val bezelFraction: Float = 0.025f
        override val cornerRadiusFraction: Float = 0.10f
        override val screenCornerRadiusFraction: Float = 0.08f
        override val cutout: Cutout = Cutout.PunchHole(diameterDp = 10.dp, topMarginDp = 10.dp)
        override val defaultScreenWidthDp: Dp = 412.dp
        override val statusBarHeightDp: Dp = 34.dp
        override val bottomInsetDp: Dp = 20.dp
        override val isTablet: Boolean = false
    }

    /** Modern iPhone hardware frame featuring rounded corners and Dynamic Island cutout. */
    public data object PhoneIsland : DeviceFrame() {
        override val aspect: Float = 0.465f
        override val bezelFraction: Float = 0.022f
        override val cornerRadiusFraction: Float = 0.12f
        override val screenCornerRadiusFraction: Float = 0.10f
        override val cutout: Cutout = Cutout.DynamicIsland()
        override val defaultScreenWidthDp: Dp = 440.dp
        override val statusBarHeightDp: Dp = 54.dp
        override val bottomInsetDp: Dp = 34.dp
        override val isTablet: Boolean = false
    }

    /** Modern iPad Pro frame with uniform thin borders and rounded display corners. */
    public data object TabletSlim : DeviceFrame() {
        override val aspect: Float = 0.75f
        override val bezelFraction: Float = 0.030f
        override val cornerRadiusFraction: Float = 0.035f
        override val screenCornerRadiusFraction: Float = 0.025f
        override val cutout: Cutout = Cutout.None
        override val defaultScreenWidthDp: Dp = 1024.dp
        override val statusBarHeightDp: Dp = 28.dp
        override val bottomInsetDp: Dp = 20.dp
        override val isTablet: Boolean = true
    }

    /** Modern Android tablet frame featuring uniform bezels and spacious display layout. */
    public data object TabletAndroid : DeviceFrame() {
        override val aspect: Float = 0.625f
        override val bezelFraction: Float = 0.035f
        override val cornerRadiusFraction: Float = 0.04f
        override val screenCornerRadiusFraction: Float = 0.025f
        override val cutout: Cutout = Cutout.None
        override val defaultScreenWidthDp: Dp = 800.dp
        override val statusBarHeightDp: Dp = 28.dp
        override val bottomInsetDp: Dp = 20.dp
        override val isTablet: Boolean = true
    }

    /** Borderless frame with zero bezels or insets, displaying full-bleed screen content. */
    public data object None : DeviceFrame() {
        override val aspect: Float = 1.0f
        override val bezelFraction: Float = 0f
        override val cornerRadiusFraction: Float = 0f
        override val screenCornerRadiusFraction: Float = 0f
        override val cutout: Cutout = Cutout.None
        override val defaultScreenWidthDp: Dp = 412.dp
        override val statusBarHeightDp: Dp = 0.dp
        override val bottomInsetDp: Dp = 0.dp
        override val isTablet: Boolean = false
    }

    /**
     * Custom frame configuration allowing explicit override of all chassis metrics.
     */
    public data class Custom(
        override val aspect: Float,
        override val bezelFraction: Float,
        override val cornerRadiusFraction: Float,
        override val screenCornerRadiusFraction: Float,
        override val cutout: Cutout,
        override val defaultScreenWidthDp: Dp,
        override val statusBarHeightDp: Dp,
        override val bottomInsetDp: Dp,
        override val isTablet: Boolean = false,
    ) : DeviceFrame()
}
