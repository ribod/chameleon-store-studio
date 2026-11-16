package dev.ribod.storestudio.model

import dev.ribod.storestudio.annotations.StoreTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ExportIndexTest {

    @Test
    fun roundTripSerializationProducesExactMatch() {
        val asset = ExportAsset(
            slotKey = "PLAY_STORE/PHONE/en-US/shot1",
            target = StoreTarget.PLAY_STORE,
            deviceSlot = StoreDeviceSlot.PHONE,
            storeLocale = "en-US",
            id = "shot1",
            order = 1,
            position = 1,
            file = "PLAY_STORE/en-US/PHONE/01_shot1.png",
            format = ImageFormat.PNG,
            widthPx = 1080,
            heightPx = 1920,
            bytes = 543210L,
            hasAlpha = false,
            colorSpace = "sRGB",
            pixelSha256 = "abcd1234ef5678",
            fileSha256 = "998877665544",
            pHash = "1122334455667788",
            md5 = "fedcba9876543210",
            engine = "LAYOUTLIB",
        )

        val index = ExportIndex(
            schemaVersion = 1,
            assets = listOf(asset),
        )

        val json = index.toJson()
        val decoded = ExportIndex.fromJson(json)

        assertEquals(1, decoded.schemaVersion)
        assertEquals(1, decoded.assets.size)
        val decodedAsset = decoded.assets.first()
        assertEquals("shot1", decodedAsset.id)
        assertEquals(StoreDeviceSlot.PHONE, decodedAsset.deviceSlot)
        assertEquals(1080, decodedAsset.widthPx)
        assertEquals(1920, decodedAsset.heightPx)
        assertFalse(decodedAsset.hasAlpha)
        assertEquals("abcd1234ef5678", decodedAsset.pixelSha256)
    }
}
