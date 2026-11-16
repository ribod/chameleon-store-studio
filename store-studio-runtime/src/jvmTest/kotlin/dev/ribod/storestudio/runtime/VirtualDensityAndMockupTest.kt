package dev.ribod.storestudio.runtime

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.ribod.storestudio.runtime.background.MeshBlob
import dev.ribod.storestudio.runtime.background.StoreBackgrounds
import dev.ribod.storestudio.runtime.mockup.FrameColors
import dev.ribod.storestudio.runtime.mockup.StatusBarStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Validates virtual density computations, status bar defaults, and background gradient safety.
 */
class VirtualDensityAndMockupTest {

    @Test
    fun computesVirtualDensityScalingAccurately() {
        val virtualScreenWidthDp = 412.dp
        val physicalScreenWidthPx = 1080f

        val densityScale = physicalScreenWidthPx / virtualScreenWidthDp.value
        val virtualDensity = Density(density = densityScale, fontScale = 1.0f)

        // 1080 / 412 = ~2.621359
        assertTrue(virtualDensity.density in 2.62f..2.63f)
        assertEquals(1.0f, virtualDensity.fontScale)
    }

    @Test
    fun statusBarStylePresetsProvideValidMarketingTime() {
        val auto = StatusBarStyle.Auto()
        val android = StatusBarStyle.Android()
        val ios = StatusBarStyle.Ios()

        assertEquals("9:41", auto.time)
        assertEquals("9:41", android.time)
        assertEquals("9:41", ios.time)
        assertEquals(null, StatusBarStyle.None.time)
        assertEquals(4, auto.wifi)
        assertEquals(100, auto.battery)
    }

    @Test
    fun frameColorsPresetsMaintainOpaqueColors() {
        val palettes = listOf(
            FrameColors.Dark,
            FrameColors.Graphite,
            FrameColors.Silver,
            FrameColors.Light,
        )

        for (palette in palettes) {
            assertEquals(1.0f, palette.body.alpha, "Chassis body must be fully opaque")
            assertEquals(1.0f, palette.bezel.alpha, "Bezel must be fully opaque")
            assertEquals(1.0f, palette.buttons.alpha, "Buttons must be fully opaque")
            assertEquals(1.0f, palette.cutout.alpha, "Cutout must be fully opaque")
        }
    }

    @Test
    fun linearGradientRequiresAtLeastTwoColorStops() {
        assertFailsWith<IllegalArgumentException> {
            StoreBackgrounds.linear(listOf(Color.Red))
        }

        val validBrush = StoreBackgrounds.linear(Color.Red, Color.Blue)
        assertNotNull(validBrush)
    }

    @Test
    fun meshBlobConstructsWithSensibleDefaults() {
        val blob = MeshBlob(
            centerFraction = Offset(0.5f, 0.5f),
            color = Color.Magenta,
        )

        assertEquals(0.6f, blob.radiusFraction)
        assertEquals(Color.Magenta, blob.color)
    }

    @Test
    fun validatesStoreSafeAreaInsetsAndDefaults() {
        val insets = dev.ribod.storestudio.runtime.mockup.StoreSafeAreaInsets(
            top = 34.dp,
            bottom = 20.dp,
        )
        assertEquals(34.dp, insets.top)
        assertEquals(20.dp, insets.bottom)
        assertEquals(0.dp, insets.start)
        assertEquals(0.dp, insets.end)

        val scope = dev.ribod.storestudio.runtime.mockup.DeviceMockupScopeImpl(insets)
        assertEquals(insets, scope.safeArea)
        assertNotNull(scope.safeAreaPadding)
        assertNotNull(scope.safeAreaModifier)
    }
}
