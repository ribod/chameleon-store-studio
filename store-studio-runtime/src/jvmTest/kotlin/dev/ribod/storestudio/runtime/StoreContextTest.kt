package dev.ribod.storestudio.runtime

import androidx.compose.ui.unit.dp
import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.runtime.canvas.inferStoreContext
import dev.ribod.storestudio.runtime.model.FrameType
import dev.ribod.storestudio.runtime.model.StoreContext
import dev.ribod.storestudio.runtime.model.StoreDeviceSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Validates [StoreContext] default state and fallback values.
 */
class StoreContextTest {

    @Test
    fun previewContextHasSensibleFallbacks() {
        val preview = StoreContext.Preview

        assertEquals(StoreTarget.APP_STORE, preview.target)
        assertEquals(DeviceFamily.PHONE, preview.deviceFamily)
        assertEquals("IPHONE_6_9", preview.deviceSpec.slot)
        assertEquals(FrameType.IPHONE_DYNAMIC_ISLAND, preview.deviceSpec.frame)
        assertEquals("en-US", preview.locale)
        assertEquals("preview", preview.screenshotId)
        assertFalse(preview.isStoreRendering)
    }

    @Test
    fun infersAndroidPhoneContextFromGeometry() {
        val context = inferStoreContext(360.dp, 640.dp, StoreContext.Preview)

        assertEquals(StoreTarget.PLAY_STORE, context.target)
        assertEquals(DeviceFamily.PHONE, context.deviceFamily)
        assertEquals(FrameType.ANDROID_PHONE, context.deviceSpec.frame)
    }

    @Test
    fun infersIphoneContextFromTallAspectGeometry() {
        val context = inferStoreContext(440.dp, 956.dp, StoreContext.Preview)

        assertEquals(StoreTarget.APP_STORE, context.target)
        assertEquals(DeviceFamily.PHONE, context.deviceFamily)
        assertEquals(FrameType.IPHONE_DYNAMIC_ISLAND, context.deviceSpec.frame)
    }

    @Test
    fun infersAndroidTablet7ContextFromGeometry() {
        val context = inferStoreContext(600.dp, 960.dp, StoreContext.Preview)

        assertEquals(StoreTarget.PLAY_STORE, context.target)
        assertEquals(DeviceFamily.TABLET_7, context.deviceFamily)
        assertEquals(FrameType.ANDROID_TABLET, context.deviceSpec.frame)
    }

    @Test
    fun infersAndroidTablet10ContextFromGeometry() {
        val context = inferStoreContext(800.dp, 1280.dp, StoreContext.Preview)

        assertEquals(StoreTarget.PLAY_STORE, context.target)
        assertEquals(DeviceFamily.TABLET, context.deviceFamily)
        assertEquals(FrameType.ANDROID_TABLET, context.deviceSpec.frame)
    }

    @Test
    fun infersIpadContextFromAspectGeometry() {
        val context = inferStoreContext(1032.dp, 1376.dp, StoreContext.Preview)

        assertEquals(StoreTarget.APP_STORE, context.target)
        assertEquals(DeviceFamily.TABLET, context.deviceFamily)
        assertEquals(FrameType.IPAD_MODERN, context.deviceSpec.frame)
    }

    @Test
    fun infersFeatureGraphicContextFromWideAspectGeometry() {
        val context = inferStoreContext(1024.dp, 500.dp, StoreContext.Preview)

        assertEquals(StoreTarget.PLAY_STORE, context.target)
        assertEquals(FrameType.NONE, context.deviceSpec.frame)
    }

    @Test
    fun preservesExplicitCustomContext() {
        val custom = StoreContext(
            target = StoreTarget.PLAY_STORE,
            deviceFamily = DeviceFamily.TABLET,
            deviceSpec = StoreDeviceSpec.PlayTablet10,
            locale = "es-ES",
            screenshotId = "custom_shot",
            isStoreRendering = true,
        )
        val result = inferStoreContext(360.dp, 640.dp, custom)

        assertEquals(custom, result)
    }
}
