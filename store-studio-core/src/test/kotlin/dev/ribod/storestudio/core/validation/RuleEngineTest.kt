package dev.ribod.storestudio.core.validation

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.model.ExportAsset
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.RenderDiagnostic
import dev.ribod.storestudio.model.RenderIndex
import dev.ribod.storestudio.model.RenderJobResult
import dev.ribod.storestudio.model.Severity
import dev.ribod.storestudio.model.StoreDeviceSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private fun sampleAsset(
        slotKey: String = "PLAY_STORE/PHONE/en-US/shot1",
        target: StoreTarget = StoreTarget.PLAY_STORE,
        deviceSlot: StoreDeviceSlot = StoreDeviceSlot.PHONE,
        storeLocale: String = "en-US",
        id: String = "shot1",
        order: Int = 1,
        position: Int = 1,
        file: String = "PLAY_STORE/en-US/PHONE/01_shot1.png",
        format: ImageFormat = ImageFormat.PNG,
        widthPx: Int = 1080,
        heightPx: Int = 1920,
        bytes: Long = 1_000_000L,
        hasAlpha: Boolean = false,
        colorSpace: String = "sRGB",
    ) = ExportAsset(
        slotKey = slotKey,
        target = target,
        deviceSlot = deviceSlot,
        storeLocale = storeLocale,
        id = id,
        order = order,
        position = position,
        file = file,
        format = format,
        widthPx = widthPx,
        heightPx = heightPx,
        bytes = bytes,
        hasAlpha = hasAlpha,
        colorSpace = colorSpace,
        pixelSha256 = "dummy_pixel",
        fileSha256 = "dummy_file",
        pHash = "0000000000000000",
        md5 = "dummy_md5",
    )

    @Test
    fun flagsPlayScreenshotWithAlphaChannel() {
        val asset = sampleAsset(hasAlpha = true)
        val ctx = ValidationContext(
            exportIndex = ExportIndex(assets = listOf(asset)),
            configuredTargets = setOf(StoreTarget.PLAY_STORE),
            configuredLocales = setOf("en-US"),
        )

        val report = RuleEngine().run(ctx)
        assertFalse(report.valid)
        assertTrue(report.violations.any { it.ruleId == "PLAY_SCREENSHOT_ALPHA" })
    }

    @Test
    fun flagsPlayScreenshotExceedingFileSizeLimit() {
        val asset = sampleAsset(bytes = 9_000_000L)
        val ctx = ValidationContext(
            exportIndex = ExportIndex(assets = listOf(asset)),
            configuredTargets = setOf(StoreTarget.PLAY_STORE),
            configuredLocales = setOf("en-US"),
        )

        val report = RuleEngine().run(ctx)
        assertFalse(report.valid)
        assertTrue(report.violations.any { it.ruleId == "PLAY_SCREENSHOT_FILE_SIZE" })
    }

    @Test
    fun fileSizeRulesReadLimitsFromCatalog() {
        val catalog = dev.ribod.storestudio.core.catalog.DefaultStoreSpecCatalog
        fun run(a: ExportAsset, rule: Rule) = rule.check(
            ValidationContext(exportIndex = ExportIndex(assets = listOf(a))),
        )

        val fgMax = catalog[StoreDeviceSlot.FEATURE_GRAPHIC].maxBytes!!
        assertEquals(15_000_000L, fgMax)
        fun fg(bytes: Long) =
            sampleAsset(deviceSlot = StoreDeviceSlot.FEATURE_GRAPHIC, bytes = bytes)
        assertTrue(run(fg(fgMax), PlayFeatureGraphicFileSizeRule).isEmpty())
        assertEquals(1, run(fg(fgMax + 1), PlayFeatureGraphicFileSizeRule).size)

        val playMax = catalog[StoreDeviceSlot.PHONE].maxBytes!!
        assertTrue(run(sampleAsset(bytes = playMax), PlayScreenshotFileSizeRule).isEmpty())
        assertEquals(1, run(sampleAsset(bytes = playMax + 1), PlayScreenshotFileSizeRule).size)

        val ascSlot = StoreDeviceSlot.IPHONE_6_9
        val ascMax = catalog[ascSlot].softMaxBytes!!
        fun asc(bytes: Long) =
            sampleAsset(target = StoreTarget.APP_STORE, deviceSlot = ascSlot, bytes = bytes)
        assertTrue(run(asc(ascMax), AscScreenshotFileSizeRule).isEmpty())
        val v = run(asc(ascMax + 1), AscScreenshotFileSizeRule).single()
        assertEquals(Severity.WARNING, v.severity)
        assertFalse(AscScreenshotFileSizeRule.hard)
    }

    @Test
    fun flagsPlayScreenshotViolatingAspectRatioLimit() {
        val asset = sampleAsset(widthPx = 1000, heightPx = 2500) // 2.5:1 ratio > 2.0
        val ctx = ValidationContext(
            exportIndex = ExportIndex(assets = listOf(asset)),
            configuredTargets = setOf(StoreTarget.PLAY_STORE),
            configuredLocales = setOf("en-US"),
        )

        val report = RuleEngine().run(ctx)
        assertFalse(report.valid)
        assertTrue(report.violations.any { it.ruleId == "PLAY_SCREENSHOT_ASPECT_RATIO" })
    }

    @Test
    fun flagsAscOrientationInconsistencyWithinSameSet() {
        val shot1 = sampleAsset(
            slotKey = "APP_STORE/IPHONE_6_9/en-US/shot1",
            target = StoreTarget.APP_STORE,
            deviceSlot = StoreDeviceSlot.IPHONE_6_9,
            widthPx = 1320,
            heightPx = 2868, // Portrait
        )
        val shot2 = sampleAsset(
            slotKey = "APP_STORE/IPHONE_6_9/en-US/shot2",
            target = StoreTarget.APP_STORE,
            deviceSlot = StoreDeviceSlot.IPHONE_6_9,
            id = "shot2",
            widthPx = 2868,
            heightPx = 1320, // Landscape
        )

        val ctx = ValidationContext(
            exportIndex = ExportIndex(assets = listOf(shot1, shot2)),
            configuredTargets = setOf(StoreTarget.APP_STORE),
            configuredLocales = setOf("en-US"),
            supportsIpad = false,
        )

        val report = RuleEngine().run(ctx)
        assertFalse(report.valid)
        assertTrue(report.violations.any { it.ruleId == "ASC_ORIENTATION_CONSISTENCY" })
    }

    @Test
    fun preventsDowngradingHardRuleBelowError() {
        val asset = sampleAsset(hasAlpha = true)
        val ctx = ValidationContext(
            exportIndex = ExportIndex(assets = listOf(asset)),
            configuredTargets = setOf(StoreTarget.PLAY_STORE),
            configuredLocales = setOf("en-US"),
        )

        val report = RuleEngine().run(
            ctx = ctx,
            overrides = mapOf("PLAY_SCREENSHOT_ALPHA" to Severity.WARNING),
        )

        // Hard rule must remain ERROR and emit override ignored warning
        assertFalse(report.valid)
        assertTrue(
            report.violations.any {
                it.ruleId == "PLAY_SCREENSHOT_ALPHA" && it.severity == Severity.ERROR
            },
        )
        assertTrue(report.violations.any { it.ruleId == "VALIDATION_OVERRIDE_IGNORED" })
    }

    @Test
    fun throwsWhenUnknownRuleIdIsConfiguredInOverrides() {
        val ctx = ValidationContext(
            exportIndex = ExportIndex(),
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            RuleEngine().run(
                ctx = ctx,
                overrides = mapOf("PLAY_SCRENSHOT_ALPHA" to Severity.WARNING),
            )
        }
        assertTrue(exception.message!!.contains("PLAY_SCREENSHOT_ALPHA"))
    }

    @Test
    fun flagsFailedRenderJobsFromRenderIndex() {
        val renderIndex = RenderIndex(
            layoutlibVersion = "16.2.1",
            renderedAt = "2026-10-12T20:00:00Z",
            jobs = listOf(
                RenderJobResult(
                    jobId = "broken__PLAY_STORE__PHONE__en-US",
                    slotKey = "PLAY_STORE/PHONE/en-US/broken",
                    entryId = "broken",
                    target = StoreTarget.PLAY_STORE,
                    deviceSlot = StoreDeviceSlot.PHONE,
                    storeLocale = "en-US",
                    localeTag = "en-US",
                    widthPx = 1080,
                    heightPx = 1920,
                    dpi = 480,
                    engine = "LAYOUTLIB",
                    status = "FAILED",
                ),
            ),
        )

        val ctx = ValidationContext(
            exportIndex = ExportIndex(),
            renderIndex = renderIndex,
            configuredTargets = setOf(StoreTarget.PLAY_STORE),
            configuredLocales = setOf("en-US"),
        )

        val report = RuleEngine().run(ctx)
        assertFalse(report.valid)
        assertTrue(report.violations.any { it.ruleId == "ASSET_RENDER_FAILED" })
    }

    @Test
    fun failsWhenFailOnWarningIsEnabledAndWarningsExist() {
        val shot1 = sampleAsset(order = 1)
        val shot2 = sampleAsset(id = "shot2", order = 1)
        val fg = sampleAsset(
            slotKey = "PLAY_STORE/FEATURE_GRAPHIC/en-US/fg",
            deviceSlot = StoreDeviceSlot.FEATURE_GRAPHIC,
            widthPx = 1024,
            heightPx = 500,
        )

        val ctx = ValidationContext(
            exportIndex = ExportIndex(assets = listOf(shot1, shot2, fg)),
            configuredTargets = setOf(StoreTarget.PLAY_STORE),
            configuredLocales = setOf("en-US"),
        )

        val reportNormal = RuleEngine().run(ctx, failOnWarning = false)
        assertTrue(reportNormal.valid)

        val reportFail = RuleEngine().run(ctx, failOnWarning = true)
        assertFalse(reportFail.valid)
    }

    @Test
    fun libraryModulesOnlyRunPerImageRules() {
        // One phone screenshot, no feature graphic: incomplete as a listing
        val assets = listOf(
            sampleAsset(),
            sampleAsset(slotKey = "PLAY_STORE/PHONE/en-US/alpha", id = "alpha", hasAlpha = true),
        )
        fun run(listing: Boolean) = RuleEngine().run(
            ValidationContext(
                exportIndex = ExportIndex(assets = assets),
                configuredTargets = setOf(StoreTarget.PLAY_STORE),
                configuredLocales = setOf("en-US"),
                listingModule = listing,
            ),
        ).violations.map { it.ruleId }.toSet()

        val app = run(listing = true)
        val library = run(listing = false)

        assertTrue("PLAY_FEATURE_GRAPHIC_REQUIRED" in app)
        assertFalse("PLAY_FEATURE_GRAPHIC_REQUIRED" in library)
        // Problems in the images themselves are still reported for libraries
        assertTrue("PLAY_SCREENSHOT_ALPHA" in library)
    }
}
