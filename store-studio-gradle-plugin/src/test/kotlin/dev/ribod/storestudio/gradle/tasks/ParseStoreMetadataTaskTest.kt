package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.core.metadata.StoreMetadataManifest
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ParseStoreMetadataTaskTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun parseStoreMetadataEmitsValidJsonWithFallback() {
        val project = ProjectBuilder.builder().build()
        val metaDir = tempDir.newFolder("metadata")

        val enDir = metaDir.resolve("en-US/play").apply { mkdirs() }
        enDir.resolve("title.txt").writeText("Now in Android")
        enDir.resolve("short_description.txt").writeText("Official sample application.")

        val esDir = metaDir.resolve("es-MX/play").apply { mkdirs() }
        esDir.resolve("title.txt").writeText("Now in Android ES")

        val outputFile = tempDir.newFile("metadata.json")

        val task = project.tasks.register(
            "storeParseMetadata",
            ParseStoreMetadataTask::class.java,
        ) { t ->
            t.metadataDirectory.set(metaDir)
            t.defaultLocale.set("en-US")
            t.configuredLocales.set(setOf("en-US", "es-MX"))
            t.metadataFile.set(outputFile)
        }.get()

        task.parse()

        assertTrue(outputFile.exists())
        val manifest = StoreMetadataManifest.fromFile(outputFile)
        assertEquals("en-US", manifest.defaultLocale)
        assertEquals(2, manifest.locales.size)

        val esBundle = manifest.locales["es-MX"]
        assertTrue(esBundle != null)
        assertEquals("Now in Android ES", esBundle?.play?.title)
        assertEquals("Official sample application.", esBundle?.play?.shortDescription)
    }

    @Test
    fun parseStoreMetadataHandlesMissingDirectoryGracefully() {
        val project = ProjectBuilder.builder().build()
        val outputFile = tempDir.newFile("metadata_empty.json")

        val task = project.tasks.register(
            "storeParseMetadataEmpty",
            ParseStoreMetadataTask::class.java,
        ) { t ->
            t.defaultLocale.set("en-US")
            t.configuredLocales.set(setOf("en-US"))
            t.metadataFile.set(outputFile)
        }.get()

        task.parse()

        assertTrue(outputFile.exists())
        val manifest = StoreMetadataManifest.fromFile(outputFile)
        assertTrue(manifest.locales.isEmpty())
    }

    @Test
    fun parseStoreMetadataRecordsTheStoreIcons() {
        val project = ProjectBuilder.builder().build()
        val outputFile = tempDir.newFile("metadata_icons.json")

        val task = project.tasks.register(
            "storeParseMetadataIcons",
            ParseStoreMetadataTask::class.java,
        ) { t ->
            t.defaultLocale.set("en-US")
            t.configuredLocales.set(setOf("en-US"))
            t.googlePlayIcon.set("app/src/main/ic_launcher-playstore.png")
            t.metadataFile.set(outputFile)
        }.get()

        task.parse()

        val icons = StoreMetadataManifest.fromFile(outputFile).icons
        assertEquals("app/src/main/ic_launcher-playstore.png", icons.googlePlay)
        assertEquals(null, icons.appStore)
    }
}
