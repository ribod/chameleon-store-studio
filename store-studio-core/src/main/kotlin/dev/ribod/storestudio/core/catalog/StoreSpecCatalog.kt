package dev.ribod.storestudio.core.catalog

import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.model.DeviceSlotSpec
import dev.ribod.storestudio.model.StoreDeviceSlot

/**
 * Access interface for the authoritative device and store specification catalog.
 *
 * Provides physical hardware dimensions, DPI, asset limits, and store API identifiers
 * across Google Play and Apple App Store ecosystems.
 */
public interface StoreSpecCatalog {
    /** Current version of the specification rules schema. */
    public val catalogVersion: Int

    /** All defined device slot specifications. */
    public val slots: List<DeviceSlotSpec>

    /** Returns the exact specification for a given [slot]. */
    public operator fun get(slot: StoreDeviceSlot): DeviceSlotSpec

    /**
     * Resolves matching device slot specifications for a specific store target and device family,
     * considering optional sizes enabled in the Gradle extension (e.g. [extraIphone]).
     *
     * The returned list is deterministically sorted by slot ordinal.
     */
    public fun slotsFor(
        target: StoreTarget,
        family: DeviceFamily,
        extraIphone: Set<StoreDeviceSlot> = emptySet(),
    ): List<DeviceSlotSpec>

    /** Returns the promotional feature graphic slot for the given target, if supported. */
    public fun featureGraphicSlot(target: StoreTarget): DeviceSlotSpec?
}
