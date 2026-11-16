package dev.ribod.storestudio.core.validation

/**
 * Granular scope at which a compliance rule evaluates.
 */
public enum class RuleScope {
    /** Evaluates properties of an individual rendered image file. */
    ASSET,

    /** Evaluates the collection of assets within a (target, deviceSlot, storeLocale) bucket. */
    BUCKET,

    /** Evaluates a store-specific submission set (e.g. App Store display family set). */
    SET,

    /** Evaluates presence and consistency across a single store locale. */
    LOCALE,

    /** Evaluates overall project-wide settings, configuration, and matrix completeness. */
    PROJECT,

    /** Evaluates store listing textual metadata. */
    METADATA,
}
