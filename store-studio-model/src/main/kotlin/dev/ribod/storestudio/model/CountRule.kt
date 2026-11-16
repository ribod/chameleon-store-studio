package dev.ribod.storestudio.model

import kotlinx.serialization.Serializable

/**
 * Asset count limits required by store presence policies.
 *
 * @property min Minimum required number of assets for this slot to publish a release.
 * @property max Maximum allowable number of assets per locale.
 * @property recommendedMin Recommended minimum number of assets for optimal conversion.
 */
@Serializable
public data class CountRule(
    public val min: Int,
    public val max: Int,
    public val recommendedMin: Int? = null,
) {
    init {
        require(min >= 0) { "Min count cannot be negative: $min" }
        require(max >= min) { "Max count ($max) must be greater than or equal to min ($min)" }
    }
}
