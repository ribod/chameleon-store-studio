package dev.ribod.storestudio.core.validation

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.model.ExportAsset
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.ExtractedSemanticsNode
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.SemanticsSnapshot
import dev.ribod.storestudio.model.Severity
import dev.ribod.storestudio.model.StoreDeviceSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticsRulesTest {

    private fun sampleExportAsset(id: String, slotKey: String, hasOverflow: Boolean) = ExportAsset(
        slotKey = slotKey,
        target = StoreTarget.PLAY_STORE,
        deviceSlot = StoreDeviceSlot.PHONE,
        storeLocale = "en-US",
        id = id,
        order = 1,
        position = 1,
        file = "sample.png",
        format = ImageFormat.PNG,
        widthPx = 1080,
        heightPx = 1920,
        bytes = 1024L,
        pixelSha256 = "dummyPixel",
        fileSha256 = "dummyFile",
        pHash = "dummyPhash",
        md5 = "dummyMd5",
        hasVisualOverflow = hasOverflow,
    )

    @Test
    fun `detects text overflow in semantics snapshots with warning severity`() {
        val snapshot = SemanticsSnapshot(
            jobId = "sample_job",
            slotKey = "PLAY_STORE/PHONE/en-US/sample",
            locale = "en-US",
            textSha256 = "abc",
            nodes = listOf(
                ExtractedSemanticsNode(
                    id = 1,
                    texts = listOf("This text was truncated"),
                    hasVisualOverflow = true,
                ),
            ),
        )

        val ctx = ValidationContext(
            exportIndex = ExportIndex(),
            semanticsSnapshots = listOf(snapshot),
        )

        val violations = SemanticsTextOverflowRule.check(ctx)
        assertEquals(1, violations.size)
        val v = violations.first()
        assertEquals("SEMANTICS_TEXT_OVERFLOW", v.ruleId)
        assertEquals(Severity.WARNING, v.severity)
        assertEquals("PLAY_STORE/PHONE/en-US/sample", v.slotKey)
        assertTrue(v.message.contains("This text was truncated"))
    }

    @Test
    fun `detects text overflow fallback from export assets`() {
        val asset =
            sampleExportAsset("screen1", "PLAY_STORE/PHONE/en-US/screen1", hasOverflow = true)
        val ctx = ValidationContext(
            exportIndex = ExportIndex(assets = listOf(asset)),
            semanticsSnapshots = emptyList(),
        )

        val violations = SemanticsTextOverflowRule.check(ctx)
        assertEquals(1, violations.size)
        assertEquals("SEMANTICS_TEXT_OVERFLOW", violations.first().ruleId)
    }

    @Test
    fun `passes cleanly when no text overflow occurs`() {
        val snapshot = SemanticsSnapshot(
            jobId = "sample_job",
            slotKey = "PLAY_STORE/PHONE/en-US/sample",
            locale = "en-US",
            textSha256 = "abc",
            nodes = listOf(
                ExtractedSemanticsNode(
                    id = 1,
                    texts = listOf("Fits perfectly"),
                    hasVisualOverflow = false,
                ),
            ),
        )

        val ctx = ValidationContext(
            exportIndex = ExportIndex(),
            semanticsSnapshots = listOf(snapshot),
        )

        val violations = SemanticsTextOverflowRule.check(ctx)
        assertTrue(violations.isEmpty())
    }
}
