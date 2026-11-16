package dev.ribod.storestudio.model

import kotlinx.serialization.Serializable

/**
 * Headless rendering geometry combining exact pixel size and screen density.
 *
 * Enforces the mathematical invariant:
 * `widthPx * 160 % dpi == 0 && heightPx * 160 % dpi == 0`
 * so that density-independent pixel conversions (`dp = px * 160 / dpi`) evaluate to exact
 * integer values without fractional rounding discrepancies between Layoutlib and Robolectric.
 *
 * @property size Exact target physical pixel dimensions.
 * @property dpi Screen pixel density (dots per inch).
 */
@Serializable
public data class RenderGeometry(public val size: PxSize, public val dpi: Int) {
    init {
        require(dpi > 0) { "DPI must be positive: $dpi" }
        require((size.width * 160) % dpi == 0) {
            "Width ${size.width}px @ ${dpi}dpi results in non-integer dp: ${(size.width * 160.0) / dpi}"
        }
        require((size.height * 160) % dpi == 0) {
            "Height ${size.height}px @ ${dpi}dpi results in non-integer dp: ${(size.height * 160.0) / dpi}"
        }
    }

    /** Width in density-independent pixels (`dp`). */
    public val widthDp: Int get() = size.width * 160 / dpi

    /** Height in density-independent pixels (`dp`). */
    public val heightDp: Int get() = size.height * 160 / dpi

    /**
     * Formats the device specification string accepted by Layoutlib and Android Studio Preview:
     * `spec:width=<width>px,height=<height>px,dpi=<dpi>`
     */
    public fun layoutlibDeviceSpec(): String =
        "spec:width=${size.width}px,height=${size.height}px,dpi=$dpi"

    /**
     * Formats the device qualifier string fragment for Robolectric Native Graphics:
     * `w<widthDp>dp-h<heightDp>dp-<orientation>-<dpi>dpi`
     */
    public fun robolectricQualifiers(): String {
        val orient = if (size.width > size.height) "land" else "port"
        return "w${widthDp}dp-h${heightDp}dp-$orient-${dpi}dpi"
    }
}
