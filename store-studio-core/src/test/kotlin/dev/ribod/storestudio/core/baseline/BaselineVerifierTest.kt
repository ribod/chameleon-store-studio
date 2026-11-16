package dev.ribod.storestudio.core.baseline

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.model.BaselineAsset
import dev.ribod.storestudio.model.BaselineComparison
import dev.ribod.storestudio.model.BaselineManifest
import dev.ribod.storestudio.model.ExportAsset
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.StoreDeviceSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineVerifierTest {

    private fun createExportAsset(
        slotKey: String = "PLAY_STORE/PHONE/en-US/shot1",
        position: Int = 1,
        widthPx: Int = 1080,
        heightPx: Int = 1920,
        pixelSha256: String = "pixel_hash_1",
        pHash: String = "0000000000000000",
        textSha256: String? = null,
        texts: List<String> = emptyList(),
    ) = ExportAsset(
        slotKey = slotKey,
        target = StoreTarget.PLAY_STORE,
        deviceSlot = StoreDeviceSlot.PHONE,
        storeLocale = "en-US",
        id = "shot1",
        order = 1,
        position = position,
        file = "PLAY_STORE/en-US/phone/01_shot1.png",
        format = ImageFormat.PNG,
        widthPx = widthPx,
        heightPx = heightPx,
        bytes = 1024L,
        pixelSha256 = pixelSha256,
        fileSha256 = "file_hash_1",
        pHash = pHash,
        md5 = "md5_1",
        textSha256 = textSha256,
        extractedTexts = texts,
    )

    private fun createBaselineAsset(
        slotKey: String = "PLAY_STORE/PHONE/en-US/shot1",
        position: Int = 1,
        widthPx: Int = 1080,
        heightPx: Int = 1920,
        pixelSha256: String = "pixel_hash_1",
        pHash: String = "0000000000000000",
        textSha256: String? = null,
        texts: List<String> = emptyList(),
    ) = BaselineAsset(
        slotKey = slotKey,
        position = position,
        widthPx = widthPx,
        heightPx = heightPx,
        pixelSha256 = pixelSha256,
        pHash = pHash,
        textSha256 = textSha256,
        texts = texts,
    )

    @Test
    fun exactMatchPassesStrictAndPerceptual() {
        val current = ExportIndex(assets = listOf(createExportAsset()))
        val baseline = BaselineManifest(assets = listOf(createBaselineAsset()))

        val strictResult = BaselineVerifier.verify(
            baseline = baseline,
            current = current,
            comparison = BaselineComparison.STRICT,
        )
        assertTrue(strictResult.passed)
        assertEquals(0, strictResult.drifts.size)
        assertEquals(1, strictResult.matchedCount)

        val perceptualResult = BaselineVerifier.verify(
            baseline = baseline,
            current = current,
            comparison = BaselineComparison.PERCEPTUAL,
        )
        assertTrue(perceptualResult.passed)
        assertEquals(0, perceptualResult.drifts.size)
        assertEquals(1, perceptualResult.matchedCount)
    }

    @Test
    fun minorHashDifferencePassesPerceptualWithinThreshold() {
        // 1 bit difference out of 64: distance = 1/64 = 0.015625 <= 0.05
        val current = ExportIndex(
            assets = listOf(
                createExportAsset(
                    pixelSha256 = "pixel_hash_modified",
                    pHash = "0000000000000001",
                ),
            ),
        )
        val baseline = BaselineManifest(
            assets = listOf(
                createBaselineAsset(
                    pixelSha256 = "pixel_hash_1",
                    pHash = "0000000000000000",
                ),
            ),
        )

        val strictResult = BaselineVerifier.verify(
            baseline = baseline,
            current = current,
            comparison = BaselineComparison.STRICT,
        )
        assertFalse(strictResult.passed)
        assertEquals(1, strictResult.drifts.size)
        assertEquals(BaselineDriftType.PIXEL_DRIFT, strictResult.drifts.first().type)

        val perceptualResult = BaselineVerifier.verify(
            baseline = baseline,
            current = current,
            comparison = BaselineComparison.PERCEPTUAL,
            perceptualThreshold = 0.05,
        )
        assertTrue(perceptualResult.passed)
        assertEquals(0, perceptualResult.drifts.size)
    }

    @Test
    fun significantVisualDifferenceFailsPerceptual() {
        // 16 bits difference: distance = 16/64 = 0.25 > 0.05
        val current = ExportIndex(
            assets = listOf(
                createExportAsset(
                    pixelSha256 = "pixel_hash_2",
                    pHash = "000000000000ffff",
                ),
            ),
        )
        val baseline = BaselineManifest(
            assets = listOf(
                createBaselineAsset(
                    pixelSha256 = "pixel_hash_1",
                    pHash = "0000000000000000",
                ),
            ),
        )

        val result = BaselineVerifier.verify(
            baseline = baseline,
            current = current,
            comparison = BaselineComparison.PERCEPTUAL,
            perceptualThreshold = 0.05,
        )
        assertFalse(result.passed)
        assertEquals(1, result.drifts.size)
        val drift = result.drifts.first()
        assertEquals(BaselineDriftType.PIXEL_DRIFT, drift.type)
        assertEquals(0.25, drift.distance ?: 0.0, 0.001)
        assertTrue(drift.message.contains("pHash distance exceeds threshold"))
    }

    @Test
    fun detectsAddedAndRemovedAssets() {
        val current = ExportIndex(
            assets = listOf(
                createExportAsset(slotKey = "PLAY_STORE/PHONE/en-US/new_shot"),
            ),
        )
        val baseline = BaselineManifest(
            assets = listOf(
                createBaselineAsset(slotKey = "PLAY_STORE/PHONE/en-US/old_shot"),
            ),
        )

        val result = BaselineVerifier.verify(baseline = baseline, current = current)
        assertFalse(result.passed)
        assertEquals(2, result.drifts.size)

        val removed = result.drifts.find { it.type == BaselineDriftType.REMOVED }
        assertEquals("PLAY_STORE/PHONE/en-US/old_shot", removed?.slotKey)

        val added = result.drifts.find { it.type == BaselineDriftType.ADDED }
        assertEquals("PLAY_STORE/PHONE/en-US/new_shot", added?.slotKey)
    }

    @Test
    fun detectsDimensionAndPositionMismatch() {
        val current = ExportIndex(
            assets = listOf(
                createExportAsset(position = 2, widthPx = 1200),
            ),
        )
        val baseline = BaselineManifest(
            assets = listOf(
                createBaselineAsset(position = 1, widthPx = 1080),
            ),
        )

        val result = BaselineVerifier.verify(baseline = baseline, current = current)
        assertFalse(result.passed)
        assertEquals(2, result.drifts.size)
        assertTrue(result.drifts.any { it.type == BaselineDriftType.DIMENSION_MISMATCH })
        assertTrue(result.drifts.any { it.type == BaselineDriftType.POSITION_MISMATCH })
    }

    @Test
    fun diagnosticReportFormatsActionableGuidance() {
        val current = ExportIndex(
            assets = listOf(
                createExportAsset(pHash = "000000000000ffff", pixelSha256 = "new"),
            ),
        )
        val baseline = BaselineManifest(
            assets = listOf(
                createBaselineAsset(pHash = "0000000000000000", pixelSha256 = "old"),
            ),
        )

        val result = BaselineVerifier.verify(baseline = baseline, current = current)
        val report = result.formatReport()

        assertTrue(report.contains("Visual drift detected in 1 asset(s):"))
        assertTrue(report.contains("PLAY_STORE/PHONE/en-US/shot1"))
        assertTrue(report.contains("./gradlew storeRecordBaseline"))
    }

    @Test
    fun textChangeDriftsInEveryComparisonMode() {
        val current = ExportIndex(
            assets = listOf(
                createExportAsset(textSha256 = "sha_meet", texts = listOf("Meet the Speakers")),
            ),
        )
        val baseline = BaselineManifest(
            assets = listOf(
                createBaselineAsset(
                    textSha256 = "sha_world",
                    texts = listOf("World-Class Speakers"),
                ),
            ),
        )

        for (comparison in BaselineComparison.entries) {
            val result = BaselineVerifier.verify(baseline, current, comparison = comparison)

            assertFalse(result.passed)
            val drift = result.drifts.single()
            assertEquals(BaselineDriftType.TEXT_DRIFT, drift.type)
            assertTrue(drift.message.contains("removed \"World-Class Speakers\""))
            assertTrue(drift.message.contains("added \"Meet the Speakers\""))
        }
    }

    @Test
    fun missingTextOnOneSideDoesNotDrift() {
        val current = ExportIndex(
            assets = listOf(createExportAsset(textSha256 = "sha_new", texts = listOf("New"))),
        )
        val baseline = BaselineManifest(assets = listOf(createBaselineAsset()))

        val result = BaselineVerifier.verify(baseline, current)

        assertTrue(result.passed)
    }

    @Test
    fun defaultsToStrictComparison() {
        val current = ExportIndex(assets = listOf(createExportAsset(pixelSha256 = "green")))
        val baseline =
            BaselineManifest(assets = listOf(createBaselineAsset(pixelSha256 = "purple")))

        val result = BaselineVerifier.verify(baseline, current)

        assertEquals(BaselineComparison.STRICT, result.comparison)
        assertEquals(BaselineDriftType.PIXEL_DRIFT, result.drifts.single().type)
    }
}
