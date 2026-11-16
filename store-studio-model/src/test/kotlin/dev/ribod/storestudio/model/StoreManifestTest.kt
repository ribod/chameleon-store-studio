package dev.ribod.storestudio.model

import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreManifestTest {

    @Test
    fun roundTripSerializationMatchesContract() {
        val entry1 = ManifestEntry(
            kind = EntryKind.SCREENSHOT,
            id = "onboarding_hero",
            order = 1,
            title = "Track every habit",
            functionFqn = "com.example.store.OnboardingHeroKt.OnboardingHeroStore",
            packageName = "com.example.store",
            functionName = "OnboardingHeroStore",
            devices = listOf(DeviceFamily.PHONE),
            targets = emptyList(),
            locales = emptyList(),
            source = SourceLocation("feature/onboarding/src/main/kotlin/OnboardingHero.kt", 42),
        )
        val entry2 = ManifestEntry(
            kind = EntryKind.FEATURE_GRAPHIC,
            id = "feature_graphic",
            order = 0,
            title = null,
            functionFqn = "com.example.store.FeatureGraphicKt.FeatureGraphic",
            packageName = "com.example.store",
            functionName = "FeatureGraphic",
            devices = emptyList(),
            targets = listOf(StoreTarget.PLAY_STORE),
            locales = emptyList(),
            source = SourceLocation("feature/onboarding/src/main/kotlin/FeatureGraphic.kt", 12),
        )

        val manifest = StoreManifest(
            schemaVersion = 1,
            module = ":feature:onboarding",
            generator = "store-studio-ksp/0.1.0",
            entries = listOf(entry1, entry2),
        )

        val jsonString = StoreManifest.encodeToString(manifest)
        val decoded = StoreManifest.decodeFromString(jsonString)

        assertEquals(manifest, decoded)
        assertTrue(jsonString.contains("\"schemaVersion\": 1"))
        assertTrue(jsonString.contains("\"module\": \":feature:onboarding\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeOrder() {
        ManifestEntry(
            kind = EntryKind.SCREENSHOT,
            id = "test",
            order = -1,
            functionFqn = "a.B.c",
            packageName = "a",
            functionName = "c",
            source = SourceLocation("Test.kt", 1),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidLineNumber() {
        SourceLocation("Test.kt", 0)
    }
}
