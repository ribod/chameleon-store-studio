package dev.ribod.storestudio.core.catalog

import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.model.CountRule
import dev.ribod.storestudio.model.DeviceSlotSpec
import dev.ribod.storestudio.model.FrameTypeName
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.PxSize
import dev.ribod.storestudio.model.RenderGeometry
import dev.ribod.storestudio.model.SizeRule
import dev.ribod.storestudio.model.SourceRef
import dev.ribod.storestudio.model.StoreDeviceSlot

/**
 * Authoritative default implementation of [StoreSpecCatalog].
 *
 * Encapsulates physical hardware dimensions, screen pixel densities, asset count rules,
 * file formats, and remote store API identifiers as documented by Google Play Console
 * and Apple App Store Connect developer specifications.
 *
 * All default rendering dimensions satisfy the integer density conversion invariant:
 * `(widthPx * 160) % dpi == 0 && (heightPx * 160) % dpi == 0`
 * ensuring that Layoutlib (pixel-based) and Robolectric (dp qualifier-based) produce
 * identical pixel grids without 1-pixel rounding discrepancies.
 */
public object DefaultStoreSpecCatalog : StoreSpecCatalog {

    /** Current catalog version. Must be incremented on any rule or dimension change. */
    override val catalogVersion: Int = 1

    private const val PLAY_STORE_URL =
        "https://support.google.com/googleplay/android-developer/answer/9866151"
    private const val APP_STORE_URL =
        "https://developer.apple.com/help/app-store-connect/reference/screenshot-specifications"

    private val STANDARD_FORMATS = setOf(ImageFormat.PNG, ImageFormat.JPEG)
    private val PLAY_SCREENSHOT_RANGE = SizeRule.Range(
        minShortSidePx = 320,
        maxLongSidePx = 3840,
        maxAspect = 2.0,
    )

    override val slots: List<DeviceSlotSpec> = listOf(
        // --- Google Play Console Slots ---
        DeviceSlotSpec(
            slot = StoreDeviceSlot.PHONE,
            storeApiId = "phoneScreenshots",
            storeApiIdVerified = true,
            families = setOf(DeviceFamily.PHONE),
            defaultGeometry = RenderGeometry(PxSize(1080, 1920), dpi = 480),
            sizeRule = PLAY_SCREENSHOT_RANGE,
            count = CountRule(min = 2, max = 8, recommendedMin = 4),
            formats = STANDARD_FORMATS,
            alphaAllowed = false,
            colorSpace = "sRGB",
            maxBytes = 8_000_000L,
            softMaxBytes = null,
            frame = FrameTypeName.ANDROID_PHONE,
            optional = false,
            sources = listOf(
                SourceRef(
                    url = PLAY_STORE_URL,
                    verifiedOn = "2026-10-05",
                    note = "Google Play phone screenshot specifications",
                ),
            ),
        ),
        DeviceSlotSpec(
            slot = StoreDeviceSlot.TABLET_7,
            storeApiId = "sevenInchScreenshots",
            storeApiIdVerified = true,
            families = setOf(DeviceFamily.TABLET_7),
            defaultGeometry = RenderGeometry(PxSize(1200, 1920), dpi = 320),
            sizeRule = PLAY_SCREENSHOT_RANGE,
            count = CountRule(min = 0, max = 8, recommendedMin = 4),
            formats = STANDARD_FORMATS,
            alphaAllowed = false,
            colorSpace = "sRGB",
            maxBytes = 8_000_000L,
            softMaxBytes = null,
            frame = FrameTypeName.ANDROID_TABLET,
            optional = false,
            sources = listOf(
                SourceRef(
                    url = PLAY_STORE_URL,
                    verifiedOn = "2026-10-05",
                    note = "Google Play 7-inch tablet screenshot specifications",
                ),
            ),
        ),
        DeviceSlotSpec(
            slot = StoreDeviceSlot.TABLET_10,
            storeApiId = "tenInchScreenshots",
            storeApiIdVerified = true,
            families = setOf(DeviceFamily.TABLET),
            defaultGeometry = RenderGeometry(PxSize(1600, 2560), dpi = 320),
            sizeRule = PLAY_SCREENSHOT_RANGE,
            count = CountRule(min = 0, max = 8, recommendedMin = 4),
            formats = STANDARD_FORMATS,
            alphaAllowed = false,
            colorSpace = "sRGB",
            maxBytes = 8_000_000L,
            softMaxBytes = null,
            frame = FrameTypeName.ANDROID_TABLET,
            optional = false,
            sources = listOf(
                SourceRef(
                    url = PLAY_STORE_URL,
                    verifiedOn = "2026-10-05",
                    note = "Google Play 10-inch tablet screenshot specifications",
                ),
            ),
        ),
        DeviceSlotSpec(
            slot = StoreDeviceSlot.WEAR,
            storeApiId = "wearScreenshots",
            storeApiIdVerified = true,
            families = setOf(DeviceFamily.WATCH),
            defaultGeometry = RenderGeometry(PxSize(512, 512), dpi = 320),
            sizeRule = SizeRule.Range(
                minShortSidePx = 384,
                maxLongSidePx = 3840,
                maxAspect = 1.0,
                requireSquare = true,
            ),
            count = CountRule(min = 1, max = 8),
            formats = STANDARD_FORMATS,
            alphaAllowed = false,
            colorSpace = "sRGB",
            maxBytes = 8_000_000L,
            softMaxBytes = null,
            frame = FrameTypeName.NONE,
            optional = false,
            sources = listOf(
                SourceRef(
                    url = PLAY_STORE_URL,
                    verifiedOn = "2026-10-05",
                    note = "Google Play Wear OS specs (square 1:1, unmasked UI)",
                ),
            ),
        ),
        DeviceSlotSpec(
            slot = StoreDeviceSlot.FEATURE_GRAPHIC,
            storeApiId = "featureGraphic",
            storeApiIdVerified = true,
            families = emptySet(),
            defaultGeometry = RenderGeometry(PxSize(1024, 500), dpi = 320),
            sizeRule = SizeRule.Exact(
                accepted = listOf(PxSize(1024, 500)),
                allowTransposed = false,
            ),
            count = CountRule(min = 0, max = 1),
            formats = STANDARD_FORMATS,
            alphaAllowed = false,
            colorSpace = "sRGB",
            maxBytes = 15_000_000L,
            softMaxBytes = null,
            frame = FrameTypeName.NONE,
            optional = false,
            sources = listOf(
                SourceRef(
                    url = PLAY_STORE_URL,
                    verifiedOn = "2026-10-05",
                    note = "Google Play promotional feature graphic (exact 1024x500 banner)",
                ),
            ),
        ),

        // --- Apple App Store Connect Slots ---
        DeviceSlotSpec(
            slot = StoreDeviceSlot.IPHONE_6_9,
            storeApiId = "APP_IPHONE_67",
            storeApiIdVerified = false,
            families = setOf(DeviceFamily.PHONE),
            defaultGeometry = RenderGeometry(PxSize(1320, 2868), dpi = 480),
            sizeRule = SizeRule.Exact(
                accepted = listOf(
                    PxSize(1320, 2868),
                    PxSize(1290, 2796),
                    PxSize(1260, 2736),
                ),
                allowTransposed = true,
            ),
            count = CountRule(min = 1, max = 10),
            formats = STANDARD_FORMATS,
            alphaAllowed = false,
            colorSpace = "sRGB",
            maxBytes = null,
            softMaxBytes = 10L * 1024 * 1024,
            frame = FrameTypeName.IPHONE_DYNAMIC_ISLAND,
            optional = false,
            sources = listOf(
                SourceRef(
                    url = APP_STORE_URL,
                    verifiedOn = "2026-10-05",
                    note = "App Store 6.9\" display set required for iPhone applications",
                ),
            ),
        ),
        DeviceSlotSpec(
            slot = StoreDeviceSlot.IPHONE_6_5,
            storeApiId = "APP_IPHONE_65",
            storeApiIdVerified = false,
            families = setOf(DeviceFamily.PHONE),
            defaultGeometry = RenderGeometry(PxSize(1284, 2778), dpi = 480),
            sizeRule = SizeRule.Exact(
                accepted = listOf(
                    PxSize(1284, 2778),
                    PxSize(1242, 2688),
                ),
                allowTransposed = true,
            ),
            count = CountRule(min = 1, max = 10),
            formats = STANDARD_FORMATS,
            alphaAllowed = false,
            colorSpace = "sRGB",
            maxBytes = null,
            softMaxBytes = 10L * 1024 * 1024,
            frame = FrameTypeName.IPHONE_DYNAMIC_ISLAND,
            optional = true,
            sources = listOf(
                SourceRef(
                    url = APP_STORE_URL,
                    verifiedOn = "2026-10-05",
                    note = "App Store legacy 6.5\" display set (opt-in via extraIphoneSizes)",
                ),
            ),
        ),
        DeviceSlotSpec(
            slot = StoreDeviceSlot.IPAD_13,
            storeApiId = "APP_IPAD_PRO_3GEN_129",
            storeApiIdVerified = false,
            families = setOf(DeviceFamily.TABLET),
            defaultGeometry = RenderGeometry(PxSize(2064, 2752), dpi = 320),
            sizeRule = SizeRule.Exact(
                accepted = listOf(
                    PxSize(2064, 2752),
                    PxSize(2048, 2732),
                ),
                allowTransposed = true,
            ),
            count = CountRule(min = 1, max = 10),
            formats = STANDARD_FORMATS,
            alphaAllowed = false,
            colorSpace = "sRGB",
            maxBytes = null,
            softMaxBytes = 10L * 1024 * 1024,
            frame = FrameTypeName.IPAD_MODERN,
            optional = false,
            sources = listOf(
                SourceRef(
                    url = APP_STORE_URL,
                    verifiedOn = "2026-10-05",
                    note = "App Store 13\" iPad display set required for iPad-capable apps",
                ),
            ),
        ),
    )

    private val slotMap: Map<StoreDeviceSlot, DeviceSlotSpec> = slots.associateBy { it.slot }

    init {
        slots.forEach(::checkInvariants)
    }

    private fun checkInvariants(spec: DeviceSlotSpec) {
        val geom = spec.defaultGeometry

        // Invariant 1: Density conversion yields integer dp
        require((geom.size.width * 160) % geom.dpi == 0) {
            "Catalog invariant violated for ${spec.slot}: width ${geom.size.width}px @ ${geom.dpi}dpi non-integer dp"
        }
        require((geom.size.height * 160) % geom.dpi == 0) {
            "Catalog invariant violated for ${spec.slot}: height ${geom.size.height}px @ ${geom.dpi}dpi non-integer dp"
        }

        // Invariant 2: Default geometry satisfies its own sizeRule
        when (val rule = spec.sizeRule) {
            is SizeRule.Exact -> {
                val matches = geom.size in rule.accepted ||
                    (rule.allowTransposed && geom.size.transposed() in rule.accepted)
                require(matches) {
                    "Catalog invariant violated for ${spec.slot}: default geometry ${geom.size} not in accepted sizes ${rule.accepted}"
                }
            }

            is SizeRule.Range -> {
                val short = geom.size.shortSide
                val long = geom.size.longSide
                require(short >= rule.minShortSidePx) {
                    "Catalog invariant violated for ${spec.slot}: short side $short < min ${rule.minShortSidePx}"
                }
                require(long <= rule.maxLongSidePx) {
                    "Catalog invariant violated for ${spec.slot}: long side $long > max ${rule.maxLongSidePx}"
                }
                val aspect = long.toDouble() / short.toDouble()
                require(aspect <= rule.maxAspect + 1e-6) {
                    "Catalog invariant violated for ${spec.slot}: aspect $aspect > maxAspect ${rule.maxAspect}"
                }
                if (rule.requireSquare) {
                    require(geom.size.width == geom.size.height) {
                        "Catalog invariant violated for ${spec.slot}: requireSquare is true but size is ${geom.size}"
                    }
                }
            }
        }

        // Invariant 3: Device families mapping
        if (spec.slot == StoreDeviceSlot.FEATURE_GRAPHIC) {
            require(spec.families.isEmpty()) {
                "Catalog invariant violated: FEATURE_GRAPHIC must not define device families"
            }
        } else {
            require(spec.families.isNotEmpty()) {
                "Catalog invariant violated for ${spec.slot}: screenshot slots must define at least one device family"
            }
        }

        // Invariant 4: Remote store API parameter naming convention
        when (spec.slot.target) {
            StoreTarget.PLAY_STORE -> {
                require(!spec.storeApiId.startsWith("APP_")) {
                    "Catalog invariant violated for ${spec.slot}: Play Store API ID '${spec.storeApiId}' must not start with APP_"
                }
            }

            StoreTarget.APP_STORE -> {
                require(spec.storeApiId.startsWith("APP_")) {
                    "Catalog invariant violated for ${spec.slot}: App Store API ID '${spec.storeApiId}' must start with APP_"
                }
            }
        }

        // Invariant 5: Official documentation citations
        require(spec.sources.isNotEmpty()) {
            "Catalog invariant violated for ${spec.slot}: must define at least one documentation source reference"
        }

        // Invariant 6: Count bounds
        require(spec.count.min >= 0 && spec.count.max >= spec.count.min) {
            "Catalog invariant violated for ${spec.slot}: invalid count bounds ${spec.count}"
        }
    }

    override operator fun get(slot: StoreDeviceSlot): DeviceSlotSpec =
        slotMap[slot] ?: error("No specification defined for slot: $slot")

    override fun slotsFor(
        target: StoreTarget,
        family: DeviceFamily,
        extraIphone: Set<StoreDeviceSlot>,
    ): List<DeviceSlotSpec> = slots.asSequence()
        .filter { it.slot.target == target && family in it.families }
        .filter { !it.optional || it.slot in extraIphone }
        .sortedBy { it.slot.ordinal }
        .toList()

    override fun featureGraphicSlot(target: StoreTarget): DeviceSlotSpec? =
        if (target == StoreTarget.PLAY_STORE) get(StoreDeviceSlot.FEATURE_GRAPHIC) else null
}
