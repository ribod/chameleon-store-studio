package dev.ribod.storestudio.annotations

/**
 * Marks a top-level `@Composable` function as a promotional store listing screenshot canvas.
 *
 * Functions annotated with [StoreScreenshot] are discovered statically during compilation by the
 * `store-studio-ksp` processor and serialized into the build's `store-manifest.json`. Downstream
 * Gradle tasks headlessly render these composables to exact pixel specifications for each target
 * device family and locale.
 *
 * ### Compiler Constraints (Enforced by KSP2):
 * 1. **Top-Level Function:** Must be declared at the package level (not inside a class or object).
 * 2. **Zero Parameters:** Must not take any arguments. Use default values or state mockups.
 * 3. **Composable:** Must be annotated with `androidx.compose.runtime.Composable`.
 * 4. **Visibility:** Must be `public` or `internal` (private functions cannot be invoked externally).
 * 5. **Unique ID:** The [id] must match regex `^[a-z0-9_-]+$` and be unique within the module.
 *
 * ### Example Usage:
 * ```kotlin
 * @StoreScreenshot(
 *     id = "01_onboarding_welcome",
 *     order = 1,
 *     name = "Welcome & Habits Tracking",
 *     devices = [DeviceFamily.PHONE, DeviceFamily.TABLET],
 *     targets = [StoreTarget.PLAY_STORE, StoreTarget.APP_STORE]
 * )
 * @Composable
 * fun OnboardingWelcomeStoreScreenshot() {
 *     // Advertising canvas with promotional text and phone mockup
 * }
 * ```
 *
 * @property id Unique and immutable technical identifier. Used for exported file names, baseline
 *   hashes, and pull request visual diff tracking.
 * @property order Relative display order in the store listing carousel (e.g., 1 for 1st slot,
 *   2 for 2nd slot). Defaults to 0 (auto-ordered by discovery).
 * @property name Human-readable display label shown in HTML galleries, before/after diff reports,
 *   and the IntelliJ plugin tool window.
 * @property devices Filter restricting this screenshot to specific device families. If empty,
 *   the asset inherits the global device configuration set in Gradle.
 * @property targets Filter restricting this screenshot to specific app stores (Google Play, Apple
 *   App Store). If empty, the asset targets all configured stores.
 * @property locales Filter restricting this screenshot to specific BCP-47 language tags (e.g.,
 *   `["en-US", "es-MX"]`). If empty, the asset is rendered for all configured project locales.
 *
 * @see StoreFeatureGraphic
 * @see DeviceFamily
 * @see StoreTarget
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class StoreScreenshot(
    val id: String,
    val order: Int = 0,
    val name: String = "",
    val devices: Array<DeviceFamily> = [],
    val targets: Array<StoreTarget> = [],
    val locales: Array<String> = [],
)
