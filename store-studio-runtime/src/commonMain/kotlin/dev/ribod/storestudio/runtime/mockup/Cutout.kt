package dev.ribod.storestudio.runtime.mockup

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Geometric camera and sensor aperture cutouts drawn above virtual screen contents.
 */
@Immutable
public sealed class Cutout {
    /** Seamless display with no hardware camera cutouts or notches. */
    public data object None : Cutout()

    /**
     * Circular centered selfie camera aperture typical on modern Android devices.
     *
     * @property diameterDp Physical diameter of the circular cutout hole.
     * @property topMarginDp Distance from the top screen edge to the cutout center.
     */
    public data class PunchHole(
        public val diameterDp: Dp = 12.dp,
        public val topMarginDp: Dp = 16.dp,
    ) : Cutout()

    /**
     * Pill-shaped interactive Dynamic Island cutout mask typical on modern iPhones.
     *
     * @property widthDp Width of the pill shape.
     * @property heightDp Height of the pill shape.
     * @property cornerRadiusDp Corner radius of the pill shape.
     * @property topMarginDp Distance from the top screen edge to the island top edge.
     */
    public data class DynamicIsland(
        public val widthDp: Dp = 126.dp,
        public val heightDp: Dp = 36.dp,
        public val cornerRadiusDp: Dp = 18.dp,
        public val topMarginDp: Dp = 11.dp,
    ) : Cutout()
}
