package dev.ribod.storestudio.model

import kotlinx.serialization.Serializable

/**
 * Discriminator distinguishing asset types discovered by Store Studio symbol processing.
 */
@Serializable
public enum class EntryKind {
    /**
     * Standard user interface screenshot annotated with `@StoreScreenshot`.
     */
    SCREENSHOT,

    /**
     * Storefront promotional banner annotated with `@StoreFeatureGraphic` (Google Play 1024x500).
     */
    FEATURE_GRAPHIC,
}
