package dev.ribod.storestudio.gradle.internal

import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.catalog.DefaultStoreSpecCatalog
import dev.ribod.storestudio.model.EntryKind
import dev.ribod.storestudio.model.ManifestEntry
import dev.ribod.storestudio.model.RenderGeometry
import dev.ribod.storestudio.model.StoreDeviceSlot

/**
 * Concrete matrix execution job representing an individual Composable render task.
 *
 * @property jobId Canonical unique job identifier (`<entryId>__<target>__<slot>__<storeLocale>`).
 * @property slotKey Hierarchy key for baseline comparisons.
 * @property entryId Unique ID of the annotated Composable entry.
 * @property methodFqn JVM bytecode fully qualified name for dynamic reflection invocation.
 * @property target Store platform target.
 * @property slot Concrete physical or promotional device slot.
 * @property storeLocale Store-specific language code.
 * @property localeTag BCP-47 language tag.
 * @property geometry Target physical canvas dimensions and DPI.
 */
internal data class RenderJob(
    val jobId: String,
    val slotKey: String,
    val entryId: String,
    val methodFqn: String,
    val target: StoreTarget,
    val slot: StoreDeviceSlot,
    val storeLocale: String,
    val localeTag: String,
    val geometry: RenderGeometry,
)

/**
 * Matrix expansion engine computing the cartesian product of entries, targets, slots, and locales.
 */
internal object RenderMatrix {

    /**
     * Converts a BCP-47 locale tag (e.g. "en-US", "es-419") into an Android resource qualifier.
     */
    fun bcp47ToAndroidQualifier(bcp47: String): String {
        val parts = bcp47.split("-", "_")
        return when {
            parts.size == 1 -> parts[0].lowercase()
            parts.size >= 2 && parts[1].length == 3 -> "b+${parts[0].lowercase()}+${parts[1]}"
            parts.size >= 2 -> "${parts[0].lowercase()}-r${parts[1].uppercase()}"
            else -> bcp47
        }
    }

    /**
     * Expands manifest entries into discrete render jobs using global fallback configurations.
     *
     * @param entries Manifest entries discovered across compilation modules.
     * @param defaultTargets Fallback targets configured in the Gradle extension.
     * @param defaultDevices Fallback device families configured in the Gradle extension.
     * @param defaultLocales Fallback locales configured in the Gradle extension.
     * @return Deterministically ordered list of all matrix render jobs.
     */
    fun expand(
        entries: List<ManifestEntry>,
        defaultTargets: List<StoreTarget>,
        defaultDevices: List<DeviceFamily>,
        defaultLocales: List<String>,
        extraIphoneSizes: Set<String> = emptySet(),
    ): List<RenderJob> {
        val resolvedTargets = defaultTargets.ifEmpty { listOf(StoreTarget.PLAY_STORE) }
        val resolvedDevices = defaultDevices.ifEmpty { listOf(DeviceFamily.PHONE) }
        val resolvedLocales = defaultLocales.ifEmpty { listOf("en-US") }

        val jobs = mutableListOf<RenderJob>()

        for (entry in entries) {
            when (entry.kind) {
                EntryKind.FEATURE_GRAPHIC -> {
                    // Feature graphics strictly target Google Play promotional banners
                    val slot = StoreDeviceSlot.FEATURE_GRAPHIC
                    val spec = DefaultStoreSpecCatalog[slot]
                    val locales = entry.locales.ifEmpty { resolvedLocales }
                    for (locale in locales) {
                        jobs.add(
                            createJob(
                                entry,
                                StoreTarget.PLAY_STORE,
                                slot,
                                locale,
                                spec.defaultGeometry,
                            ),
                        )
                    }
                }

                EntryKind.SCREENSHOT -> {
                    val targets = entry.targets.ifEmpty { resolvedTargets }
                    val devices = entry.devices.ifEmpty { resolvedDevices }
                    val locales = entry.locales.ifEmpty { resolvedLocales }

                    for (target in targets) {
                        val slots = resolveSlotsForTarget(target, devices, extraIphoneSizes)
                        for (slot in slots) {
                            val spec = DefaultStoreSpecCatalog[slot]
                            for (locale in locales) {
                                jobs.add(
                                    createJob(entry, target, slot, locale, spec.defaultGeometry),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sort deterministically to maintain predictable batch ordering across runs
        return jobs.sortedBy { it.jobId }
    }

    private fun resolveSlotsForTarget(
        target: StoreTarget,
        devices: List<DeviceFamily>,
        extraIphoneSizes: Set<String>,
    ): List<StoreDeviceSlot> {
        val slots = mutableSetOf<StoreDeviceSlot>()
        for (device in devices) {
            when (target) {
                StoreTarget.PLAY_STORE -> when (device) {
                    DeviceFamily.PHONE -> slots.add(StoreDeviceSlot.PHONE)
                    DeviceFamily.TABLET_7 -> slots.add(StoreDeviceSlot.TABLET_7)
                    DeviceFamily.TABLET -> slots.add(StoreDeviceSlot.TABLET_10)
                    DeviceFamily.WATCH -> slots.add(StoreDeviceSlot.WEAR)
                    else -> Unit
                }

                StoreTarget.APP_STORE -> when (device) {
                    DeviceFamily.PHONE -> {
                        slots.add(StoreDeviceSlot.IPHONE_6_9)
                        if (extraIphoneSizes.contains("IPHONE_6_5") ||
                            extraIphoneSizes.contains("6.5")
                        ) {
                            slots.add(StoreDeviceSlot.IPHONE_6_5)
                        }
                    }

                    DeviceFamily.TABLET, DeviceFamily.TABLET_7 -> {
                        slots.add(StoreDeviceSlot.IPAD_13)
                    }

                    else -> Unit
                }
            }
        }
        return slots.toList()
    }

    private fun createJob(
        entry: ManifestEntry,
        target: StoreTarget,
        slot: StoreDeviceSlot,
        locale: String,
        geometry: RenderGeometry,
    ): RenderJob {
        val jobId = "${entry.id}__${target.name}__${slot.name}__$locale"
        val slotKey = "${target.name}/${slot.name}/$locale/${entry.id}"
        return RenderJob(
            jobId = jobId,
            slotKey = slotKey,
            entryId = entry.id,
            methodFqn = entry.functionFqn,
            target = target,
            slot = slot,
            storeLocale = locale,
            localeTag = locale,
            geometry = geometry,
        )
    }
}
