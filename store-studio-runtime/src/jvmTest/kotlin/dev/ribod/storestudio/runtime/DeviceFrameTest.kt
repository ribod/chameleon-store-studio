package dev.ribod.storestudio.runtime

import androidx.compose.ui.unit.dp
import dev.ribod.storestudio.runtime.mockup.Cutout
import dev.ribod.storestudio.runtime.mockup.DeviceFrame
import dev.ribod.storestudio.runtime.model.FrameType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Validates frame resolution mapping, aspect ratios, and cutout assignments.
 */
class DeviceFrameTest {

    @Test
    fun resolvesAllCatalogFrameTypesCorrectly() {
        val auto = DeviceFrame.Auto

        assertEquals(DeviceFrame.PhonePunchHole, auto.resolveFrame(FrameType.ANDROID_PHONE))
        assertEquals(DeviceFrame.PhoneIsland, auto.resolveFrame(FrameType.IPHONE_DYNAMIC_ISLAND))
        assertEquals(DeviceFrame.TabletAndroid, auto.resolveFrame(FrameType.ANDROID_TABLET))
        assertEquals(DeviceFrame.TabletSlim, auto.resolveFrame(FrameType.IPAD_MODERN))
        assertEquals(DeviceFrame.None, auto.resolveFrame(FrameType.NONE))
    }

    @Test
    fun framesMaintainValidAspectRatiosAndPositiveBezels() {
        val frames = listOf(
            DeviceFrame.PhonePunchHole,
            DeviceFrame.PhoneIsland,
            DeviceFrame.TabletSlim,
            DeviceFrame.TabletAndroid,
        )

        for (frame in frames) {
            assertTrue(frame.aspect in 0.3f..1.5f, "Frame aspect must be within reasonable bounds")
            assertTrue(frame.bezelFraction > 0f, "Bezel fraction must be positive")
            assertTrue(frame.cornerRadiusFraction > 0f, "Corner radius fraction must be positive")
            assertTrue(
                frame.screenCornerRadiusFraction > 0f,
                "Screen corner radius fraction must be positive",
            )
            assertTrue(frame.defaultScreenWidthDp > 0.dp, "Default screen width must be positive")
        }
    }

    @Test
    fun framesAssignCorrectCutoutVariants() {
        assertIs<Cutout.PunchHole>(DeviceFrame.PhonePunchHole.cutout)
        assertIs<Cutout.DynamicIsland>(DeviceFrame.PhoneIsland.cutout)
        assertIs<Cutout.None>(DeviceFrame.TabletSlim.cutout)
        assertIs<Cutout.None>(DeviceFrame.TabletAndroid.cutout)
        assertIs<Cutout.None>(DeviceFrame.None.cutout)
    }

    @Test
    fun tabletsClassifiedCorrectly() {
        assertFalse(DeviceFrame.PhonePunchHole.isTablet)
        assertFalse(DeviceFrame.PhoneIsland.isTablet)
        assertTrue(DeviceFrame.TabletAndroid.isTablet)
        assertTrue(DeviceFrame.TabletSlim.isTablet)
        assertFalse(DeviceFrame.None.isTablet)
    }

    @Test
    fun framesDefineNonNegativeSafeAreaInsets() {
        val frames = listOf(
            DeviceFrame.PhonePunchHole,
            DeviceFrame.PhoneIsland,
            DeviceFrame.TabletSlim,
            DeviceFrame.TabletAndroid,
            DeviceFrame.None,
        )

        for (frame in frames) {
            assertTrue(frame.statusBarHeightDp >= 0.dp)
            assertTrue(frame.bottomInsetDp >= 0.dp)
        }
    }
}
