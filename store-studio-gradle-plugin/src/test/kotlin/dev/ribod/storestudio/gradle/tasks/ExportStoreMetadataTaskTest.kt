package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.metadata.AppStoreMetadataEntry
import dev.ribod.storestudio.core.metadata.PlayMetadataEntry
import dev.ribod.storestudio.core.metadata.StoreMetadataBundle
import dev.ribod.storestudio.core.metadata.StoreMetadataManifest
import dev.ribod.storestudio.core.metadata.StoreMetadataParser
import dev.ribod.storestudio.model.ExportLayout
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExportStoreMetadataTaskTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val manifest = StoreMetadataManifest(
        locales = mapOf(
            "en-US" to StoreMetadataBundle(
                locale = "en-US",
                play = PlayMetadataEntry(title = "Title", releaseNotes = "Notes"),
                appStore = AppStoreMetadataEntry(name = "Name", releaseNotes = "ASC notes"),
            ),
            "es-MX" to StoreMetadataBundle(
                locale = "es-MX",
                play = PlayMetadataEntry(title = "Titulo"),
            ),
        ),
    )

    private fun run(
        layout: ExportLayout,
        lowercase: Boolean = true,
        targets: Set<StoreTarget> = setOf(StoreTarget.PLAY_STORE, StoreTarget.APP_STORE),
        source: StoreMetadataManifest = manifest,
    ): List<String> {
        val out = tempDir.newFolder()
        val file = tempDir.newFile().also { source.toFile(it) }
        val task = ProjectBuilder.builder().build().tasks.register(
            "storeExportMetadata",
            ExportStoreMetadataTask::class.java,
        ) { t ->
            t.metadataFile.set(file)
            t.layout.set(layout)
            t.lowercaseFolders.set(lowercase)
            t.targets.set(targets)
            t.outputDirectory.set(out)
        }.get()
        task.export()
        return out.walkTopDown().filter(File::isFile).map {
            it.relativeTo(out).path
        }.sorted().toList()
    }

    @Test
    fun storeLocaleLayoutWritesNonEmptyFieldsAndWhatsNew() {
        assertEquals(
            listOf(
                "app_store/en-US/metadata/name.txt",
                "app_store/en-US/metadata/release_notes.txt",
                "play_store/en-US/metadata/release_notes.txt",
                "play_store/en-US/metadata/title.txt",
                "play_store/es-MX/metadata/title.txt",
                "play_store/release-notes/whatsnew-en-US",
            ),
            run(ExportLayout.STORE_LOCALE_DEVICE),
        )
    }

    @Test
    fun localeStoreLayoutAndUppercaseFolders() {
        assertEquals(
            listOf(
                "PLAY_STORE/release-notes/whatsnew-en-US",
                "en-US/PLAY_STORE/metadata/release_notes.txt",
                "en-US/PLAY_STORE/metadata/title.txt",
                "es-MX/PLAY_STORE/metadata/title.txt",
            ),
            run(
                ExportLayout.LOCALE_STORE_DEVICE,
                lowercase = false,
                targets = setOf(StoreTarget.PLAY_STORE),
            ),
        )
    }

    @Test
    fun localeDeviceLayoutKeepsStoresApart() {
        val files = run(ExportLayout.LOCALE_DEVICE)
        assertEquals(
            listOf(
                "en-US/metadata/app_store/release_notes.txt",
                "en-US/metadata/play_store/release_notes.txt",
            ),
            files.filter { it.endsWith("release_notes.txt") },
        )
    }

    @Test
    fun disabledStoresAreSkippedAndFallbackLocalesAreFilled() {
        val metaDir = tempDir.newFolder("metadata")
        metaDir.resolve("en-US/play").apply {
            mkdirs()
        }.resolve("release_notes.txt").writeText("Hi\n")
        val parsed = StoreMetadataParser.parse(metaDir, listOf("en-US", "fr-FR"), "en-US")
        val files =
            run(
                ExportLayout.STORE_LOCALE_DEVICE,
                targets = setOf(StoreTarget.PLAY_STORE),
                source = parsed,
            )
        assertEquals(
            listOf(
                "play_store/en-US/metadata/release_notes.txt",
                "play_store/fr-FR/metadata/release_notes.txt",
                "play_store/release-notes/whatsnew-en-US",
                "play_store/release-notes/whatsnew-fr-FR",
            ),
            files,
        )
        assertFalse(files.any { it.startsWith("app_store") })
    }
}
