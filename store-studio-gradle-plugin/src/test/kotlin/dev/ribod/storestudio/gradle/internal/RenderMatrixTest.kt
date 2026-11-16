package dev.ribod.storestudio.gradle.internal

import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.model.EntryKind
import dev.ribod.storestudio.model.ManifestEntry
import dev.ribod.storestudio.model.SourceLocation
import dev.ribod.storestudio.model.StoreDeviceSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderMatrixTest {

    @Test
    fun convertsBcp47ToAndroidQualifier() {
        assertEquals("en-rUS", RenderMatrix.bcp47ToAndroidQualifier("en-US"))
        assertEquals("es-rMX", RenderMatrix.bcp47ToAndroidQualifier("es_MX"))
        assertEquals("ja", RenderMatrix.bcp47ToAndroidQualifier("ja"))
        assertEquals("b+es+419", RenderMatrix.bcp47ToAndroidQualifier("es-419"))
    }

    @Test
    fun expandsFeatureGraphicExclusivelyToPlayStore() {
        val entry = ManifestEntry(
            kind = EntryKind.FEATURE_GRAPHIC,
            id = "main_banner",
            order = 0,
            title = "Main Banner",
            functionFqn = "com.example.BannerKt.MainBanner",
            packageName = "com.example",
            functionName = "MainBanner",
            devices = emptyList(),
            targets = emptyList(),
            locales = listOf("en-US", "es-MX"),
            source = SourceLocation("feature/Banner.kt", 10),
        )

        val jobs = RenderMatrix.expand(
            entries = listOf(entry),
            defaultTargets = listOf(StoreTarget.PLAY_STORE, StoreTarget.APP_STORE),
            defaultDevices = listOf(DeviceFamily.PHONE),
            defaultLocales = listOf("en-US"),
        )

        assertEquals(2, jobs.size)
        assertTrue(jobs.all { it.target == StoreTarget.PLAY_STORE })
        assertTrue(jobs.all { it.slot == StoreDeviceSlot.FEATURE_GRAPHIC })
        assertTrue(jobs.all { it.geometry.size.width == 1024 && it.geometry.size.height == 500 })
        assertEquals("main_banner__PLAY_STORE__FEATURE_GRAPHIC__en-US", jobs[0].jobId)
        assertEquals("main_banner__PLAY_STORE__FEATURE_GRAPHIC__es-MX", jobs[1].jobId)
    }

    @Test
    fun expandsScreenshotAcrossTargetsAndDevices() {
        val entry = ManifestEntry(
            kind = EntryKind.SCREENSHOT,
            id = "hero_screen",
            order = 1,
            title = "Hero",
            functionFqn = "com.example.HeroKt.HeroScreen",
            packageName = "com.example",
            functionName = "HeroScreen",
            devices = listOf(DeviceFamily.PHONE, DeviceFamily.TABLET),
            targets = listOf(StoreTarget.PLAY_STORE, StoreTarget.APP_STORE),
            locales = listOf("en-US"),
            source = SourceLocation("feature/Hero.kt", 15),
        )

        val jobs = RenderMatrix.expand(
            entries = listOf(entry),
            defaultTargets = emptyList(),
            defaultDevices = emptyList(),
            defaultLocales = emptyList(),
        )

        // Evaluates to four discrete jobs across targets and device families
        assertEquals(4, jobs.size)
        val slots = jobs.map { it.slot }.toSet()
        assertTrue(slots.contains(StoreDeviceSlot.PHONE))
        assertTrue(slots.contains(StoreDeviceSlot.TABLET_10))
        assertTrue(slots.contains(StoreDeviceSlot.IPHONE_6_9))
        assertTrue(slots.contains(StoreDeviceSlot.IPAD_13))
    }

    @Test
    fun inheritsGlobalDefaultsWhenEntrySpecifiesEmptyAttributes() {
        val entry = ManifestEntry(
            kind = EntryKind.SCREENSHOT,
            id = "onboarding",
            order = 2,
            title = null,
            functionFqn = "com.example.OnboardingKt.OnboardingScreen",
            packageName = "com.example",
            functionName = "OnboardingScreen",
            devices = emptyList(),
            targets = emptyList(),
            locales = emptyList(),
            source = SourceLocation("feature/Onboarding.kt", 20),
        )

        val jobs = RenderMatrix.expand(
            entries = listOf(entry),
            defaultTargets = listOf(StoreTarget.PLAY_STORE),
            defaultDevices = listOf(DeviceFamily.PHONE),
            defaultLocales = listOf("en-US", "fr-FR"),
        )

        assertEquals(2, jobs.size)
        assertEquals("onboarding__PLAY_STORE__PHONE__en-US", jobs[0].jobId)
        assertEquals("onboarding__PLAY_STORE__PHONE__fr-FR", jobs[1].jobId)
    }

    @Test
    fun expandsExtraIphoneSizesWhenConfigured() {
        val entry = ManifestEntry(
            kind = EntryKind.SCREENSHOT,
            id = "detail_screen",
            order = 3,
            title = "Detail",
            functionFqn = "com.example.DetailKt.DetailScreen",
            packageName = "com.example",
            functionName = "DetailScreen",
            devices = listOf(DeviceFamily.PHONE),
            targets = listOf(StoreTarget.APP_STORE),
            locales = listOf("en-US"),
            source = SourceLocation("feature/Detail.kt", 25),
        )

        val jobs = RenderMatrix.expand(
            entries = listOf(entry),
            defaultTargets = emptyList(),
            defaultDevices = emptyList(),
            defaultLocales = emptyList(),
            extraIphoneSizes = setOf("IPHONE_6_5"),
        )

        assertEquals(2, jobs.size)
        val slots = jobs.map { it.slot }.toSet()
        assertTrue(slots.contains(StoreDeviceSlot.IPHONE_6_9))
        assertTrue(slots.contains(StoreDeviceSlot.IPHONE_6_5))
    }
}
