package dev.ribod.storestudio.core.report

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.report.model.AssetStatus
import dev.ribod.storestudio.core.report.model.GalleryPageModel
import dev.ribod.storestudio.core.report.model.ReportAssetItem
import dev.ribod.storestudio.core.report.model.ReportSummary
import dev.ribod.storestudio.core.report.model.StoreListingMetadata
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.StoreDeviceSlot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryReportGeneratorTest {

    @Test
    fun generateRendersCompleteOfflineGalleryHtml() {
        val model = GalleryPageModel(
            appName = "Now in Android",
            developerName = "Google Samples",
            category = "News & Magazines",
            generatedAt = "2026-10-14 12:00:00",
            summary = ReportSummary(
                totalAssets = 1,
                storeCount = 1,
                localeCount = 1,
                errorCount = 0,
                warningCount = 0,
                valid = true,
            ),
            assets = listOf(
                ReportAssetItem(
                    slotKey = "for_you_feed",
                    id = "for_you_feed",
                    target = StoreTarget.PLAY_STORE,
                    deviceSlot = StoreDeviceSlot.PHONE,
                    storeLocale = "en-US",
                    position = 1,
                    widthPx = 1080,
                    heightPx = 2400,
                    format = ImageFormat.PNG,
                    dataUri = "data:image/jpeg;base64,abc123",
                    fullResRelativePath = "phone/screen1.png",
                    pixelSha256 = "sha256abc",
                    pHash = "phash123",
                    status = AssetStatus.VALID,
                ),
            ),
            stores = listOf("PLAY_STORE"),
            deviceSlots = listOf("PHONE"),
            locales = listOf("en-US"),
        )

        val html = GalleryReportGenerator.generate(model)

        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<title>Now in Android - Store Studio Gallery</title>"))
        assertTrue(html.contains("Google Samples"))
        assertTrue(html.contains("for_you_feed"))
        assertTrue(html.contains("data-store=\"PLAY_STORE\""))
        assertTrue(html.contains("data-device=\"PHONE\""))
        assertTrue(html.contains("data-locale=\"en-US\""))
        assertTrue(html.contains("class=\"badge badge-locale\">en-US</span>"))
        assertTrue(html.contains("id=\"lightbox-modal\""))
        assertFalse(html.contains("https://cdn."))
        assertFalse(html.contains("https://fonts."))
    }

    @Test
    fun generateIncludesStorePreviewSection() {
        val model = GalleryPageModel(
            appName = "KotlinConf App",
            developerName = "JetBrains",
            category = "Events",
            generatedAt = "2026-10-14 12:00:00",
            summary = ReportSummary(
                totalAssets = 1,
                storeCount = 1,
                localeCount = 1,
                errorCount = 0,
                warningCount = 0,
                valid = true,
            ),
            assets = emptyList(),
            stores = listOf("PLAY_STORE"),
            deviceSlots = listOf("PHONE"),
            locales = listOf("en-US"),
            metadataByLocale = mapOf(
                "en-US" to StoreListingMetadata(
                    title = "KotlinConf 2026",
                    shortDescription = "Official KotlinConf app",
                ),
            ),
        )

        val html = GalleryReportGenerator.generate(model)

        assertTrue(html.contains("id=\"preview-section\""))
        assertTrue(html.contains("id=\"gallery-section\""))
        assertTrue(html.contains("data-view=\"preview\""))
        assertTrue(html.contains("data-view=\"gallery\""))
        assertTrue(html.contains("KotlinConf 2026"))
        assertTrue(html.contains("view-tab active"))
        assertTrue(html.contains("Store Page Simulation"))
        assertFalse(html.contains("Authentic Store Product Page Simulation"))
        assertTrue(html.contains("id=\"preview-section-locale\""))
        assertFalse(html.contains("🔍"))
        assertFalse(html.contains("◑"))
    }

    @Test
    fun generateEscapesHostileValuesInScriptBlocksAndAttributes() {
        val evil = "</script><script>alert(1)</script>"
        val model = GalleryPageModel(
            appName = "App",
            developerName = "Dev",
            category = "Tools",
            generatedAt = "2026-10-14 12:00:00",
            summary = ReportSummary(1, 1, 1, 0, 0, true),
            assets = listOf(
                ReportAssetItem(
                    slotKey = "k",
                    id = "k",
                    target = StoreTarget.PLAY_STORE,
                    deviceSlot = StoreDeviceSlot.PHONE,
                    storeLocale = "en-US",
                    position = 1,
                    widthPx = 1080,
                    heightPx = 2400,
                    format = ImageFormat.PNG,
                    dataUri = "data:x\"$evil",
                    fullResRelativePath = "a\"$evil",
                    pixelSha256 = "s",
                    pHash = "p",
                    status = AssetStatus.VALID,
                ),
            ),
            stores = listOf("PLAY_STORE"),
            deviceSlots = listOf("PHONE"),
            locales = listOf("en-US"),
            metadataByLocale = mapOf(
                "en-US" to StoreListingMetadata(title = evil, shortDescription = evil),
            ),
        )

        val html = GalleryReportGenerator.generate(model)

        assertFalse(html.contains("<script>alert(1)"))
        assertFalse(html.contains(evil))
    }
}
