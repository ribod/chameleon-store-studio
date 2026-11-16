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

class ListingPreviewReportGeneratorTest {

    @Test
    fun generateRendersStandaloneStorePreviewHtml() {
        val model = GalleryPageModel(
            appName = "Now in Android",
            developerName = "Google Samples",
            category = "News & Magazines",
            generatedAt = "2026-10-14 12:00:00",
            summary = ReportSummary(
                totalAssets = 1,
                storeCount = 1,
                localeCount = 2,
                errorCount = 0,
                warningCount = 0,
                valid = true,
            ),
            assets = listOf(
                ReportAssetItem(
                    slotKey = "app_icon",
                    id = "app_icon",
                    target = StoreTarget.PLAY_STORE,
                    deviceSlot = StoreDeviceSlot.PHONE,
                    storeLocale = "en-US",
                    position = 1,
                    widthPx = 512,
                    heightPx = 512,
                    format = ImageFormat.PNG,
                    dataUri = "data:image/png;base64,icon123",
                    fullResRelativePath = "phone/icon.png",
                    pixelSha256 = "sha256abc",
                    pHash = "phash123",
                    status = AssetStatus.VALID,
                ),
            ),
            stores = listOf("PLAY_STORE", "APP_STORE"),
            deviceSlots = listOf("PHONE"),
            locales = listOf("en-US", "es-ES"),
            metadataByLocale = mapOf(
                "en-US" to StoreListingMetadata(
                    title = "Now in Android",
                    developerName = "Google Samples",
                    shortDescription = "Learn Android best practices with this showcase app.",
                    fullDescription = "A fully functional Android app built entirely with Kotlin.",
                    whatsNew = "Added Material 3 Expressive support.",
                ),
            ),
        )

        val html = ListingPreviewReportGenerator.generate(model)

        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<title>Now in Android - Store Listing Preview</title>"))
        assertTrue(html.contains("class=\"store-btn active play-store\""))
        assertTrue(html.contains("class=\"store-btn app-store\""))
        assertTrue(html.contains("id=\"play-mockup\""))
        assertTrue(html.contains("id=\"apple-mockup\""))
        assertTrue(html.contains("Store Page Simulation"))
        assertFalse(html.contains("Authentic Store Product Page Simulation"))
        assertFalse(html.contains("🔍"))
        assertFalse(html.contains("◑"))
        assertTrue(html.contains("data-i18n=\"play.install\""))
        assertTrue(html.contains("data-i18n=\"apple.get\""))
        assertTrue(html.contains("data-i18n=\"apple.whatsNew\""))
        assertFalse(html.contains("https://cdn."))
        assertFalse(html.contains("https://fonts."))
    }

    @Test
    fun generateRendersAppIconWhenProvided() {
        val model = GalleryPageModel(
            appName = "Now in Android",
            developerName = "Google Samples",
            category = "News",
            generatedAt = "2026-10-14 12:00:00",
            summary = ReportSummary(1, 1, 1, 0, 0, true),
            assets = emptyList(),
            stores = listOf("PLAY_STORE", "APP_STORE"),
            deviceSlots = listOf("PHONE"),
            locales = listOf("en-US"),
            appIconDataUri = "data:image/png;base64,myicon",
        )

        val html = ListingPreviewReportGenerator.generate(model)

        assertTrue(html.contains("play-app-icon-img"))
        assertTrue(html.contains("apple-app-icon-img"))
        assertTrue(html.contains("data:image/png;base64,myicon"))
    }
}
