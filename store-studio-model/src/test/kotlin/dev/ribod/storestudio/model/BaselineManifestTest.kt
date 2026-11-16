package dev.ribod.storestudio.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaselineManifestTest {

    @Test
    fun roundTripSerializationProducesExactMatch() {
        val asset = BaselineAsset(
            slotKey = "PLAY_STORE/PHONE/en-US/foryou_feed",
            position = 1,
            widthPx = 1080,
            heightPx = 1920,
            pixelSha256 = "9f2c8301b2a4f00112233445566778899aabbccddeeff001122334455667788",
            pHash = "c3a1f0e07c3e0f81",
            textSha256 = null,
        )

        val manifest = BaselineManifest(
            schemaVersion = 1,
            recordedWith = "store-studio/0.1.0",
            layoutlibVersion = "16.2.1",
            assets = listOf(asset),
        )

        val json = manifest.toJson()
        val decoded = BaselineManifest.fromJson(json)

        assertEquals(1, decoded.schemaVersion)
        assertEquals("store-studio/0.1.0", decoded.recordedWith)
        assertEquals("16.2.1", decoded.layoutlibVersion)
        assertEquals(1, decoded.assets.size)

        val decodedAsset = decoded.assets.first()
        assertEquals("PLAY_STORE/PHONE/en-US/foryou_feed", decodedAsset.slotKey)
        assertEquals(1, decodedAsset.position)
        assertEquals(1080, decodedAsset.widthPx)
        assertEquals(1920, decodedAsset.heightPx)
        assertEquals(
            "9f2c8301b2a4f00112233445566778899aabbccddeeff001122334455667788",
            decodedAsset.pixelSha256,
        )
        assertEquals("c3a1f0e07c3e0f81", decodedAsset.pHash)
        assertNull(decodedAsset.textSha256)
    }
}
