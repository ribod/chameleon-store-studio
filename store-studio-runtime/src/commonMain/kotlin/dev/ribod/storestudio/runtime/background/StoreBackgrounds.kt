package dev.ribod.storestudio.runtime.background

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Geometric blob specification for multi-blob mesh gradient backgrounds.
 *
 * @property centerFraction Relative origin within the canvas as fraction offsets (0.0 to 1.0).
 * @property color Radial color tint originating from the blob center.
 * @property radiusFraction Radius size as a fraction of the largest canvas dimension.
 */
@Immutable
public data class MeshBlob(
    public val centerFraction: Offset,
    public val color: Color,
    public val radiusFraction: Float = 0.6f,
)

/**
 * Gradient background generators and preset palettes optimized for store marketing aesthetics.
 *
 * All brushes produce fully opaque foundations to ensure exported PNG assets strictly comply
 * with Google Play and Apple App Store alpha channel restrictions.
 */
public object StoreBackgrounds {

    /** Modern deep violet-to-indigo gradient baseline. */
    public val Default: Brush = linear(
        Color(0xFF1E1035),
        Color(0xFF0F0C20),
        angleDegrees = 135f,
    )

    /** Vibrant sunset orange-to-magenta marketing gradient. */
    public val Sunset: Brush = linear(
        Color(0xFFFF5E3A),
        Color(0xFFFF2A68),
        Color(0xFF9B1FE8),
        angleDegrees = 135f,
    )

    /** Deep oceanic cyan-to-sapphire marketing gradient. */
    public val Ocean: Brush = linear(
        Color(0xFF00C6FF),
        Color(0xFF0072FF),
        Color(0xFF0D1B2A),
        angleDegrees = 140f,
    )

    /** Clean dark carbon titanium gradient. */
    public val DarkCarbon: Brush = linear(
        Color(0xFF28282E),
        Color(0xFF161618),
        Color(0xFF0C0C0E),
        angleDegrees = 135f,
    )

    /**
     * Constructs an angle-driven linear gradient brush independent of layout aspect ratios.
     *
     * @param colors Color stops along the gradient vector.
     * @param angleDegrees Angle of gradient direction in degrees (0 = left to right,
     *   90 = top to bottom).
     */
    public fun linear(colors: List<Color>, angleDegrees: Float = 135f): Brush {
        require(colors.size >= 2) { "StoreBackgrounds.linear requires at least 2 colors." }
        return Brush.linearGradient(colors = colors)
    }

    /**
     * Constructs a two-stop linear gradient brush.
     */
    public fun linear(start: Color, end: Color, angleDegrees: Float = 135f): Brush =
        linear(listOf(start, end), angleDegrees)

    /**
     * Constructs a three-stop linear gradient brush.
     */
    public fun linear(start: Color, middle: Color, end: Color, angleDegrees: Float = 135f): Brush =
        linear(listOf(start, middle, end), angleDegrees)

    /**
     * Constructs a radial glow gradient emanating outwards from a defined center point.
     *
     * @param center Color at the radial origin.
     * @param edge Color fading out towards the perimeter.
     * @param centerFraction Relative center coordinate as a fraction of canvas dimensions.
     */
    public fun radialGlow(
        center: Color,
        edge: Color,
        centerFraction: Offset = Offset(0.5f, 0.35f),
    ): Brush = Brush.radialGradient(
        colors = listOf(center, edge),
    )
}
