package dev.ribod.storestudio.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Dimension compliance validation rule for a device slot.
 */
@Serializable
public sealed interface SizeRule {
    /**
     * Exact list of permissible dimensions (used by Apple App Store and Google Play Feature Graphic).
     *
     * @property accepted List of explicitly accepted physical pixel dimensions.
     * @property allowTransposed Whether transposed (landscape) versions of [accepted] sizes are allowed.
     */
    @Serializable
    @SerialName("exact")
    public data class Exact(
        public val accepted: List<PxSize>,
        public val allowTransposed: Boolean = false,
    ) : SizeRule

    /**
     * Bounded range rule (used by Google Play screenshots).
     *
     * @property minShortSidePx Minimum permissible length for the shorter side in pixels (e.g. 320 px).
     * @property maxLongSidePx Maximum permissible length for the longer side in pixels (e.g. 3840 px).
     * @property maxAspect Maximum permissible aspect ratio (`longSide / shortSide <= maxAspect`, e.g. 2.0).
     * @property requireSquare Whether dimensions must be exactly 1:1 square (e.g. Wear OS).
     */
    @Serializable
    @SerialName("range")
    public data class Range(
        public val minShortSidePx: Int,
        public val maxLongSidePx: Int,
        public val maxAspect: Double,
        public val requireSquare: Boolean = false,
    ) : SizeRule
}
