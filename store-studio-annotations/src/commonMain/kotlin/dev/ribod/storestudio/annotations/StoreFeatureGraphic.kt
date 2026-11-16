package dev.ribod.storestudio.annotations

/**
 * Marks a top-level `@Composable` function as a promotional Feature Graphic (Promo Banner) for Google Play.
 *
 * Google Play Console requires a Feature Graphic banner (exact dimensions: **1024 x 500 px**)
 * displayed prominently at the top of the store listing and in search recommendations.
 *
 * Functions annotated with [StoreFeatureGraphic] are discovered by `store-studio-ksp`, rendered at
 * 1024x500 resolution, and published to Google Play's `FEATURE_GRAPHIC` slot by `storePublishPlay`.
 *
 * ### Compiler Constraints (Enforced by KSP2):
 * 1. **Top-Level Function:** Must be declared at the package level.
 * 2. **Zero Parameters:** Must not take any arguments.
 * 3. **Composable:** Must be annotated with `androidx.compose.runtime.Composable`.
 * 4. **Visibility:** Must be `public` or `internal`.
 * 5. **Unique ID:** The [id] must match regex `^[a-z0-9_-]+$` and be unique within the module.
 *
 * ### Example Usage:
 * ```kotlin
 * @StoreFeatureGraphic(
 *     id = "promo_banner_summer",
 *     name = "Summer Campaign Feature Graphic"
 * )
 * @Composable
 * fun SummerFeatureGraphic() {
 *     // Composable banner rendered at 1024x500 px
 * }
 * ```
 *
 * @property id Unique and immutable technical identifier. Used for file naming, baseline hashes,
 *   and drift tracking.
 * @property name Human-readable display label shown in HTML galleries and the IDE tool window.
 * @property locales Filter restricting this graphic to specific BCP-47 language tags (e.g.,
 *   `["en-US"]`). If empty, the banner is rendered for all configured project locales.
 *
 * @see StoreScreenshot
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class StoreFeatureGraphic(
    val id: String,
    val name: String = "",
    val locales: Array<String> = [],
)
