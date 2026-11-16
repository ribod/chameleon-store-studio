package dev.ribod.storestudio.core.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreMetadataManifestTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun parsesCanonicalDirectoryLayoutSuccessfully() {
        val rootDir = tempFolder.root
        val enDir = rootDir.resolve("en-US").apply { mkdirs() }
        val playDir = enDir.resolve("play").apply { mkdirs() }
        val appStoreDir = enDir.resolve("appstore").apply { mkdirs() }

        playDir.resolve("title.txt").writeText("Now in Android\n")
        playDir.resolve("short_description.txt").writeText("App description here\n")
        playDir.resolve("full_description.txt").writeText("Long description here\n")
        playDir.resolve("release_notes.txt").writeText("Bug fixes\n")

        appStoreDir.resolve("name.txt").writeText("Now in Android\n")
        appStoreDir.resolve("subtitle.txt").writeText("Official App\n")
        appStoreDir.resolve("keywords.txt").writeText("android,news\n")
        appStoreDir.resolve("promotional_text.txt").writeText("Check it out\n")
        appStoreDir.resolve("description.txt").writeText("App Store description\n")
        appStoreDir.resolve("release_notes.txt").writeText("What's new\n")
        appStoreDir.resolve("support_url.txt").writeText("https://example.com/support\n")
        appStoreDir.resolve("marketing_url.txt").writeText("https://example.com\n")

        val manifest = StoreMetadataParser.parse(
            metadataDir = rootDir,
            configuredLocales = listOf("en-US"),
            defaultLocale = "en-US",
        )

        assertEquals("en-US", manifest.defaultLocale)
        val bundle = manifest.locales["en-US"]
        assertTrue(bundle != null)

        assertEquals("Now in Android", bundle?.play?.title)
        assertEquals("App description here", bundle?.play?.shortDescription)
        assertEquals("Long description here", bundle?.play?.fullDescription)
        assertEquals("Bug fixes", bundle?.play?.releaseNotes)

        assertEquals("Now in Android", bundle?.appStore?.name)
        assertEquals("Official App", bundle?.appStore?.subtitle)
        assertEquals("android,news", bundle?.appStore?.keywords)
        assertEquals("Check it out", bundle?.appStore?.promotionalText)
        assertEquals("App Store description", bundle?.appStore?.description)
        assertEquals("What's new", bundle?.appStore?.releaseNotes)
        assertEquals("https://example.com/support", bundle?.appStore?.supportUrl)
        assertEquals("https://example.com", bundle?.appStore?.marketingUrl)
    }

    @Test
    fun appliesDefaultLocaleFallbackToMissingFields() {
        val rootDir = tempFolder.root
        val enDir = rootDir.resolve("en-US/play").apply { mkdirs() }
        enDir.resolve("title.txt").writeText("English Title")
        enDir.resolve("short_description.txt").writeText("English Short Desc")

        val esDir = rootDir.resolve("es-MX/play").apply { mkdirs() }
        esDir.resolve("title.txt").writeText("Titulo en Espanol")

        val manifest = StoreMetadataParser.parse(
            metadataDir = rootDir,
            configuredLocales = listOf("en-US", "es-MX"),
            defaultLocale = "en-US",
        )

        val esBundle = manifest.locales["es-MX"]
        assertTrue(esBundle != null)
        assertEquals("Titulo en Espanol", esBundle?.play?.title)
        assertEquals("English Short Desc", esBundle?.play?.shortDescription)
    }

    @Test
    fun ignoresRemovedLayoutsAndFileAliases() {
        val rootDir = tempFolder.root
        rootDir.resolve("en-US").apply { mkdirs() }.resolve("description.txt").writeText("Bare")
        rootDir.resolve("android/en-US").apply { mkdirs() }.resolve("title.txt").writeText("Old")
        rootDir.resolve("en-US/play").apply { mkdirs() }.resolve("whats_new.txt").writeText("Alias")

        val manifest = StoreMetadataParser.parse(rootDir, listOf("en-US"))

        assertTrue(manifest.locales.isEmpty())
    }

    @Test
    fun skipsLocalesThatCouldEscapeTheMetadataDir() {
        val rootDir = tempFolder.root
        rootDir.resolve("secret/play").apply { mkdirs() }.resolve("title.txt").writeText("Leak")
        val metadata = rootDir.resolve("metadata").apply { mkdirs() }
        listOf("en-US", "pt_BR", "zh-Hans").forEach {
            metadata.resolve("$it/play").apply { mkdirs() }.resolve("title.txt").writeText(it)
        }

        val manifest = StoreMetadataParser.parse(metadata, listOf("pt_BR", "../secret", "a/b"))

        assertEquals(setOf("en-US", "pt_BR", "zh-Hans"), manifest.locales.keys)
    }

    @Test
    fun handlesMissingMetadataDirectoryGracefully() {
        val nonExistent = tempFolder.root.resolve("does_not_exist")
        val manifest = StoreMetadataParser.parse(
            metadataDir = nonExistent,
            configuredLocales = listOf("en-US"),
        )
        assertTrue(manifest.locales.isEmpty())
    }

    @Test
    fun roundTripsJsonSerialization() {
        val bundle = StoreMetadataBundle(
            locale = "en-US",
            play = PlayMetadataEntry(title = "App Title", shortDescription = "Short"),
            appStore = AppStoreMetadataEntry(name = "App Name", keywords = "key1,key2"),
        )
        val manifest = StoreMetadataManifest(
            defaultLocale = "en-US",
            locales = mapOf("en-US" to bundle),
        )

        val json = manifest.toJson()
        val decoded = StoreMetadataManifest.fromJson(json)

        assertEquals(manifest.defaultLocale, decoded.defaultLocale)
        assertEquals(manifest.locales.size, decoded.locales.size)
        assertEquals(
            manifest.locales["en-US"]?.play?.title,
            decoded.locales["en-US"]?.play?.title,
        )
    }
}
