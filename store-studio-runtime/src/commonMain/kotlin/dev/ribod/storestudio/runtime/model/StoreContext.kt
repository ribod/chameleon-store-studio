package dev.ribod.storestudio.runtime.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget

/**
 * Ambient execution context provided to Store Studio composables during preview and rendering.
 *
 * Exposes target store marketplace rules, device dimensions, locale tags, and headless execution
 * indicators to allow screenshots to adapt their layouts deterministically.
 *
 * @property target Target store marketplace (Google Play or Apple App Store).
 * @property deviceFamily High-level device class (Phone, Tablet, Desktop, Wear).
 * @property deviceSpec Physical and virtual display specifications for the current slot.
 * @property locale BCP-47 language tag (e.g. "en-US", "es-MX") declared for the render run.
 * @property screenshotId Unique screenshot identifier as registered in the manifest.
 * @property isStoreRendering True when executed inside headless Layoutlib or Robolectric runners;
 *   false inside interactive IDE previews and unit tests.
 */
@Immutable
public data class StoreContext(
    public val target: StoreTarget,
    public val deviceFamily: DeviceFamily,
    public val deviceSpec: StoreDeviceSpec,
    public val locale: String,
    public val screenshotId: String,
    public val isStoreRendering: Boolean,
) {
    public companion object {
        /**
         * Default fallback context used when rendering within interactive IDE previews or unit tests
         * without an explicit ambient provider.
         */
        public val Preview: StoreContext = StoreContext(
            target = StoreTarget.APP_STORE,
            deviceFamily = DeviceFamily.PHONE,
            deviceSpec = StoreDeviceSpec.Iphone69,
            locale = "en-US",
            screenshotId = "preview",
            isStoreRendering = false,
        )
    }
}

/**
 * Composition local providing the active [StoreContext] to screenshot composable hierarchies.
 */
public val LocalStoreContext: ProvidableCompositionLocal<StoreContext> =
    staticCompositionLocalOf { StoreContext.Preview }

/**
 * Internal composition local guarding against recursive or nested DeviceMockup invocations.
 */
public val LocalInsideMockup: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }
