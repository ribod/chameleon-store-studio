package dev.ribod.storestudio.core.catalog

import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.PxSize
import dev.ribod.storestudio.model.RenderGeometry
import dev.ribod.storestudio.model.SizeRule
import dev.ribod.storestudio.model.StoreDeviceSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultStoreSpecCatalogTest {

    @Test
    fun catalogVersionIsPositive() {
        assertTrue(DefaultStoreSpecCatalog.catalogVersion > 0)
    }

    @Test
    fun containsAllRequiredSlots() {
        val expectedSlots = setOf(
            StoreDeviceSlot.PHONE,
            StoreDeviceSlot.TABLET_7,
            StoreDeviceSlot.TABLET_10,
            StoreDeviceSlot.WEAR,
            StoreDeviceSlot.FEATURE_GRAPHIC,
            StoreDeviceSlot.IPHONE_6_9,
            StoreDeviceSlot.IPHONE_6_5,
            StoreDeviceSlot.IPAD_13,
        )
        assertEquals(expectedSlots, DefaultStoreSpecCatalog.slots.map { it.slot }.toSet())
    }

    @Test
    fun integerDensityInvariantHoldsForAllSlots() {
        for (spec in DefaultStoreSpecCatalog.slots) {
            val geom = spec.defaultGeometry
            assertEquals(
                "Width ${geom.size.width}px @ ${geom.dpi}dpi must yield integer dp",
                0,
                (geom.size.width * 160) % geom.dpi,
            )
            assertEquals(
                "Height ${geom.size.height}px @ ${geom.dpi}dpi must yield integer dp",
                0,
                (geom.size.height * 160) % geom.dpi,
            )
            assertTrue(geom.widthDp > 0)
            assertTrue(geom.heightDp > 0)
        }
    }

    @Test
    fun renderGeometryStringFormatting() {
        val geom = RenderGeometry(PxSize(1320, 2868), 480)
        assertEquals("spec:width=1320px,height=2868px,dpi=480", geom.layoutlibDeviceSpec())
        assertEquals("w440dp-h956dp-port-480dpi", geom.robolectricQualifiers())

        val landscapeGeom = RenderGeometry(PxSize(2868, 1320), 480)
        assertEquals("w956dp-h440dp-land-480dpi", landscapeGeom.robolectricQualifiers())
    }

    @Test
    fun playStoreSlotResolution() {
        assertEquals(
            listOf(StoreDeviceSlot.PHONE),
            DefaultStoreSpecCatalog.slotsFor(StoreTarget.PLAY_STORE, DeviceFamily.PHONE).map {
                it.slot
            },
        )
        assertEquals(
            listOf(StoreDeviceSlot.TABLET_10),
            DefaultStoreSpecCatalog.slotsFor(StoreTarget.PLAY_STORE, DeviceFamily.TABLET).map {
                it.slot
            },
        )
        assertEquals(
            listOf(StoreDeviceSlot.TABLET_7),
            DefaultStoreSpecCatalog.slotsFor(StoreTarget.PLAY_STORE, DeviceFamily.TABLET_7).map {
                it.slot
            },
        )
        assertEquals(
            listOf(StoreDeviceSlot.WEAR),
            DefaultStoreSpecCatalog.slotsFor(StoreTarget.PLAY_STORE, DeviceFamily.WATCH).map {
                it.slot
            },
        )
    }

    @Test
    fun appStoreSlotResolution() {
        // Without extra sizes: only IPHONE_6_9
        assertEquals(
            listOf(StoreDeviceSlot.IPHONE_6_9),
            DefaultStoreSpecCatalog.slotsFor(StoreTarget.APP_STORE, DeviceFamily.PHONE).map {
                it.slot
            },
        )

        // With extra IPHONE_6_5 enabled
        assertEquals(
            listOf(StoreDeviceSlot.IPHONE_6_9, StoreDeviceSlot.IPHONE_6_5),
            DefaultStoreSpecCatalog.slotsFor(
                StoreTarget.APP_STORE,
                DeviceFamily.PHONE,
                extraIphone = setOf(StoreDeviceSlot.IPHONE_6_5),
            ).map { it.slot },
        )

        assertEquals(
            listOf(StoreDeviceSlot.IPAD_13),
            DefaultStoreSpecCatalog.slotsFor(StoreTarget.APP_STORE, DeviceFamily.TABLET).map {
                it.slot
            },
        )

        // Unsupported families for App Store yield empty lists cleanly
        assertTrue(
            DefaultStoreSpecCatalog.slotsFor(
                StoreTarget.APP_STORE,
                DeviceFamily.TABLET_7,
            ).isEmpty(),
        )
        assertTrue(
            DefaultStoreSpecCatalog.slotsFor(StoreTarget.APP_STORE, DeviceFamily.WATCH).isEmpty(),
        )
    }

    @Test
    fun featureGraphicResolution() {
        val playGraphic = DefaultStoreSpecCatalog.featureGraphicSlot(StoreTarget.PLAY_STORE)
        assertNotNull(playGraphic)
        assertEquals(StoreDeviceSlot.FEATURE_GRAPHIC, playGraphic?.slot)
        assertEquals(PxSize(1024, 500), playGraphic?.defaultGeometry?.size)

        val appStoreGraphic = DefaultStoreSpecCatalog.featureGraphicSlot(StoreTarget.APP_STORE)
        assertNull(appStoreGraphic)
    }

    @Test
    fun complianceRulesAndFormats() {
        for (spec in DefaultStoreSpecCatalog.slots) {
            assertFalse("Alpha channels must not be permitted by store rules", spec.alphaAllowed)
            assertTrue("PNG must be supported", spec.formats.contains(ImageFormat.PNG))
            assertEquals("sRGB", spec.colorSpace)
            assertTrue("Documentation sources must be present", spec.sources.isNotEmpty())
        }
    }

    @Test
    fun playScreenshotDimensionsPassRangeConstraints() {
        for (spec in DefaultStoreSpecCatalog.slots.filter {
            it.slot.target == StoreTarget.PLAY_STORE
        }) {
            when (val rule = spec.sizeRule) {
                is SizeRule.Range -> {
                    val size = spec.defaultGeometry.size
                    assertTrue(size.shortSide >= rule.minShortSidePx)
                    assertTrue(size.longSide <= rule.maxLongSidePx)
                    assertTrue(
                        (size.longSide.toDouble() / size.shortSide.toDouble()) <= rule.maxAspect,
                    )
                    if (rule.requireSquare) {
                        assertEquals(size.width, size.height)
                    }
                }

                is SizeRule.Exact -> {
                    assertTrue(spec.defaultGeometry.size in rule.accepted)
                }
            }
        }
    }
}
