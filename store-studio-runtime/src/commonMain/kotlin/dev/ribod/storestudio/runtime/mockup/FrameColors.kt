package dev.ribod.storestudio.runtime.mockup

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Color palette configurations applied to vector device hardware mockups.
 *
 * Defines tones for the outer aluminum/titanium chassis, front display bezel, hardware buttons,
 * and sensor cutout apertures.
 *
 * @property body Outer hardware chassis and edge tint.
 * @property bezel Display glass border tint surrounding the active screen area.
 * @property buttons Hardware side volume and power buttons tint.
 * @property cutout Sensor aperture cutout color (typically pure black).
 */
@Immutable
public data class FrameColors(
    public val body: Color,
    public val bezel: Color,
    public val buttons: Color,
    public val cutout: Color = Color.Black,
) {
    public companion object {
        /** Dark matte chassis with obsidian black bezels. */
        public val Dark: FrameColors = FrameColors(
            body = Color(0xFF1E1E22),
            bezel = Color(0xFF121214),
            buttons = Color(0xFF2C2C30),
            cutout = Color.Black,
        )

        /** Neutral graphite titanium chassis. */
        public val Graphite: FrameColors = FrameColors(
            body = Color(0xFF38383C),
            bezel = Color(0xFF18181A),
            buttons = Color(0xFF48484E),
            cutout = Color.Black,
        )

        /** Silver polished metallic chassis with dark bezels. */
        public val Silver: FrameColors = FrameColors(
            body = Color(0xFFE4E4E8),
            bezel = Color(0xFF151516),
            buttons = Color(0xFFC8C8CC),
            cutout = Color.Black,
        )

        /** Light ceramic white chassis for high-contrast light previews. */
        public val Light: FrameColors = FrameColors(
            body = Color(0xFFF2F2F5),
            bezel = Color(0xFF202022),
            buttons = Color(0xFFE0E0E4),
            cutout = Color.Black,
        )
    }
}
