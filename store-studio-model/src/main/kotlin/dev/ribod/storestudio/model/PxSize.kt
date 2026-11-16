package dev.ribod.storestudio.model

import kotlinx.serialization.Serializable

/**
 * Physical pixel dimensions (width and height in integer pixels).
 *
 * @property width Width in pixels (must be > 0).
 * @property height Height in pixels (must be > 0).
 */
@Serializable
public data class PxSize(public val width: Int, public val height: Int) {
    init {
        require(width > 0) { "Width must be positive: $width" }
        require(height > 0) { "Height must be positive: $height" }
    }

    /** The larger of [width] or [height]. */
    public val longSide: Int get() = maxOf(width, height)

    /** The smaller of [width] or [height]. */
    public val shortSide: Int get() = minOf(width, height)

    /** Canvas orientation derived from dimensions. */
    public val orientation: Orientation
        get() = when {
            width == height -> Orientation.SQUARE
            width < height -> Orientation.PORTRAIT
            else -> Orientation.LANDSCAPE
        }

    /** Returns transposed dimensions (width and height swapped). */
    public fun transposed(): PxSize = PxSize(height, width)

    override fun toString(): String = "${width}x$height"
}
