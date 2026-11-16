package dev.ribod.storestudio.model

import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.annotations.naturalDisplayOrder
import kotlinx.serialization.Serializable

/**
 * Enumeration of all discrete physical device slots and promotional graphics recognized
 * across Google Play and Apple App Store specifications.
 *
 * Each slot is strictly bound to a single [target] store.
 *
 * @property target The target distribution platform ([StoreTarget.PLAY_STORE] or
 * [StoreTarget.APP_STORE]).
 */
@Serializable
public enum class StoreDeviceSlot(public val target: StoreTarget) {
    // --- Google Play Console Slots ---
    /** Google Play standard phone screenshot slot (portrait/landscape, e.g. 1080x1920). */
    PHONE(StoreTarget.PLAY_STORE),

    /** Google Play 7-inch tablet screenshot slot (e.g. 1200x1920). */
    TABLET_7(StoreTarget.PLAY_STORE),

    /** Google Play 10-inch tablet screenshot slot (e.g. 1600x2560). */
    TABLET_10(StoreTarget.PLAY_STORE),

    /** Google Play Wear OS smartwatch screenshot slot (512x512). */
    WEAR(StoreTarget.PLAY_STORE),

    /** Google Play Feature Graphic promo banner slot (exact 1024x500). */
    FEATURE_GRAPHIC(StoreTarget.PLAY_STORE),

    // --- Apple App Store Connect Slots ---

    /** Apple App Store 6.9" Super Retina XDR display slot (iPhone 16 Pro Max, 1320x2868). */
    IPHONE_6_9(StoreTarget.APP_STORE),

    /** Apple App Store legacy 6.5" display slot (optional, 1284x2778 or 1242x2688). */
    IPHONE_6_5(StoreTarget.APP_STORE),

    /** Apple App Store 13" iPad Pro display slot (2064x2752). */
    IPAD_13(StoreTarget.APP_STORE),
}

/**
 * Natural commercial display order for device slots, ordering phone form factors
 * ascending by physical size, tablet form factors ascending by physical size,
 * followed by wearables and promotional banners.
 */
public val StoreDeviceSlot.naturalDisplayOrder: Int
    get() = when (this) {
        StoreDeviceSlot.PHONE -> 10
        StoreDeviceSlot.IPHONE_6_5 -> 11
        StoreDeviceSlot.IPHONE_6_9 -> 12
        StoreDeviceSlot.TABLET_7 -> 20
        StoreDeviceSlot.TABLET_10 -> 21
        StoreDeviceSlot.IPAD_13 -> 22
        StoreDeviceSlot.WEAR -> 30
        StoreDeviceSlot.FEATURE_GRAPHIC -> 50
    }

/**
 * Resolves the natural display order for any recognized slot or device family name,
 * returning 99 if unrecognized.
 */
public fun resolveNaturalDisplayOrder(name: String): Int =
    runCatching { StoreDeviceSlot.valueOf(name).naturalDisplayOrder }
        .getOrElse {
            runCatching { DeviceFamily.valueOf(name).naturalDisplayOrder }.getOrDefault(99)
        }
