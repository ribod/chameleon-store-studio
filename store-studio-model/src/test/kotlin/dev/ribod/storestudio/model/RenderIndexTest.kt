package dev.ribod.storestudio.model

import dev.ribod.storestudio.annotations.StoreTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderIndexTest {

    @Test
    fun serializesAndDeserializesCanonicalRenderIndex() {
        val diagnostic = RenderDiagnostic(
            severity = "WARNING",
            code = "LAYOUTLIB_FAILED_FALLBACK_USED",
            message = "Layoutlib broken class MapView",
        )
        val job = RenderJobResult(
            jobId = "onboarding_hero__PLAY_STORE__PHONE__es-419",
            slotKey = "PLAY_STORE/PHONE/es-419/onboarding_hero",
            entryId = "onboarding_hero",
            target = StoreTarget.PLAY_STORE,
            deviceSlot = StoreDeviceSlot.PHONE,
            storeLocale = "es-419",
            localeTag = "es-MX",
            widthPx = 1080,
            heightPx = 1920,
            dpi = 420,
            engine = "LAYOUTLIB",
            status = "SUCCESS",
            image = "render/raw/onboarding_hero__PLAY_STORE__PHONE__es-419.png",
            durationMs = 812L,
            diagnostics = listOf(diagnostic),
        )
        val index = RenderIndex(
            schemaVersion = 1,
            engineDefault = "LAYOUTLIB",
            layoutlibVersion = "16.2.1",
            renderedAt = "2026-10-05T21:00:00Z",
            jobs = listOf(job),
        )

        val json = RenderIndex.encodeToString(index)
        assertTrue(json.contains("onboarding_hero__PLAY_STORE__PHONE__es-419"))
        assertTrue(json.contains("LAYOUTLIB_FAILED_FALLBACK_USED"))

        val decoded = RenderIndex.decodeFromString(json)
        assertEquals(1, decoded.schemaVersion)
        assertEquals("16.2.1", decoded.layoutlibVersion)
        assertEquals(1, decoded.jobs.size)
        assertEquals("onboarding_hero", decoded.jobs[0].entryId)
        assertEquals(StoreDeviceSlot.PHONE, decoded.jobs[0].deviceSlot)
        assertEquals("WARNING", decoded.jobs[0].diagnostics[0].severity)
    }

    @Test
    fun serializesAndDeserializesFailedJobsManifest() {
        val failedJob = FailedJobSpec(
            jobId = "settings__APP_STORE__IPHONE_6_9__en-US",
            slotKey = "APP_STORE/IPHONE_6_9/en-US/settings",
            entryId = "settings",
            methodFqn = "com.example.SettingsKt.SettingsScreenshot",
            target = StoreTarget.APP_STORE,
            deviceSlot = StoreDeviceSlot.IPHONE_6_9,
            storeLocale = "en-US",
            localeTag = "en-US",
            widthPx = 1320,
            heightPx = 2868,
            widthDp = 440,
            heightDp = 956,
            dpi = 480,
            outputPath = "/path/to/raw/settings.png",
            failureReason = "Layoutlib broken class",
        )
        val manifest = FailedJobsManifest(
            schemaVersion = 1,
            jobs = listOf(failedJob),
        )

        val json = FailedJobsManifest.encodeToString(manifest)
        val decoded = FailedJobsManifest.decodeFromString(json)
        assertEquals(1, decoded.schemaVersion)
        assertEquals(1, decoded.jobs.size)
        assertEquals("settings", decoded.jobs[0].entryId)
        assertEquals(440, decoded.jobs[0].widthDp)
    }
}
