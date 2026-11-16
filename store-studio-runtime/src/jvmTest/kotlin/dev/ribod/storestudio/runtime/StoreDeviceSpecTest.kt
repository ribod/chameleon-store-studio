package dev.ribod.storestudio.runtime

import dev.ribod.storestudio.runtime.model.FrameType
import dev.ribod.storestudio.runtime.model.StoreDeviceSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates display geometry conversions and standard baseline specifications.
 */
class StoreDeviceSpecTest {

    @Test
    fun computesDpDimensionsCorrectly() {
        val spec = StoreDeviceSpec(
            slot = "TEST_DEVICE",
            widthPx = 1080,
            heightPx = 1920,
            dpi = 480,
            frame = FrameType.ANDROID_PHONE,
        )

        // 1080 * 160 / 480 = 360 dp
        assertEquals(360f, spec.widthDp)
        // 1920 * 160 / 480 = 640 dp
        assertEquals(640f, spec.heightDp)
    }

    @Test
    fun standardPresetsMaintainPositiveDimensions() {
        val presets = listOf(
            StoreDeviceSpec.PlayPhone,
            StoreDeviceSpec.PlayTablet7,
            StoreDeviceSpec.PlayTablet10,
            StoreDeviceSpec.Iphone69,
            StoreDeviceSpec.Iphone65,
            StoreDeviceSpec.Ipad13,
            StoreDeviceSpec.FeatureGraphic,
        )

        for (preset in presets) {
            assertTrue(preset.widthPx > 0, "Preset ${preset.slot} must have positive widthPx")
            assertTrue(preset.heightPx > 0, "Preset ${preset.slot} must have positive heightPx")
            assertTrue(preset.dpi > 0, "Preset ${preset.slot} must have positive dpi")
            assertTrue(preset.widthDp > 0f, "Preset ${preset.slot} must have positive widthDp")
            assertTrue(preset.heightDp > 0f, "Preset ${preset.slot} must have positive heightDp")
        }
    }

    @Test
    fun standardPresetsAssociateCorrectFrameTypes() {
        assertEquals(FrameType.ANDROID_PHONE, StoreDeviceSpec.PlayPhone.frame)
        assertEquals(FrameType.IPHONE_DYNAMIC_ISLAND, StoreDeviceSpec.Iphone69.frame)
        assertEquals(FrameType.IPAD_MODERN, StoreDeviceSpec.Ipad13.frame)
        assertEquals(FrameType.ANDROID_TABLET, StoreDeviceSpec.PlayTablet10.frame)
        assertEquals(FrameType.NONE, StoreDeviceSpec.FeatureGraphic.frame)
    }
}
