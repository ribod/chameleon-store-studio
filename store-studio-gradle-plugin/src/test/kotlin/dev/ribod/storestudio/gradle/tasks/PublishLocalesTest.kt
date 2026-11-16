package dev.ribod.storestudio.gradle.tasks

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PublishLocalesTest {

    @get:Rule
    val tempDir: TemporaryFolder = TemporaryFolder()

    private fun metadataDir(vararg locales: String) = tempDir.newFolder("meta").also { root ->
        for (locale in locales) {
            root.resolve("$locale/play").mkdirs()
            root.resolve("$locale/play/release_notes.txt").writeText("notes $locale")
        }
    }

    @Test
    fun aliasedLocaleIsKeyedByStoreLocale() {
        val dir = metadataDir("en-US", "es-MX")
        val manifest = loadPublishMetadata(
            null,
            dir,
            listOf("en-US", "es-MX"),
            mapOf("en-US" to "en-US", "es-MX" to "es-419"),
        )!!
        assertEquals(setOf("en-US", "es-419"), manifest.toPlayReleaseNotesMap().keys)
        assertEquals("notes es-MX", manifest.toPlayReleaseNotesMap()["es-419"])
        assertEquals("es-419", manifest.locales.getValue("es-419").locale)
    }

    @Test
    fun withoutAliasesLocalesAreUnchanged() {
        val manifest = loadPublishMetadata(null, metadataDir("en-US", "es-MX"), emptyList())!!
        assertEquals(setOf("en-US", "es-MX"), manifest.toPlayReleaseNotesMap().keys)
    }

    @Test
    fun collidingStoreLocalesFail() {
        val dir = metadataDir("es-MX", "es-ES")
        val error = assertThrows(GradleException::class.java) {
            loadPublishMetadata(
                null,
                dir,
                emptyList(),
                mapOf("es-MX" to "es-419", "es-ES" to "es-419"),
            )
        }
        assertTrue(error.message!!, error.message!!.contains("es-419"))
    }

    @Test
    fun playBinaryTaskFailsOnCollidingAliases() {
        val p = ProjectBuilder.builder().build().also {
            it.pluginManager.apply("dev.ribod.storestudio")
        }
        val t = p.tasks.getByName("storePublishPlayBinary") as StorePublishPlayBinaryTask
        t.packageName.set("com.example.app")
        t.releaseFiles.from(tempDir.newFile("app.aab"))
        t.dryRun.set(true)
        t.metadataDirectory.set(metadataDir("es-MX", "es-ES"))
        t.storeLocales.set(mapOf("es-MX" to "es-419", "es-ES" to "es-419"))
        assertThrows(GradleException::class.java) { t.publish() }
    }
}
