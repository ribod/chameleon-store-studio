package dev.ribod.storestudio.gradle.tasks

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorePublishBinaryTasksTest {

    @get:Rule
    val tempDir: TemporaryFolder = TemporaryFolder()

    private fun project() = ProjectBuilder.builder().build().also {
        it.pluginManager.apply("dev.ribod.storestudio")
    }

    private fun play(project: org.gradle.api.Project) =
        project.tasks.getByName("storePublishPlayBinary") as StorePublishPlayBinaryTask

    private fun asc(project: org.gradle.api.Project) =
        project.tasks.getByName("storePublishAppStoreBinary") as StorePublishAppStoreBinaryTask

    @Test
    fun tasksRegisteredWithoutValidateDependency() {
        val p = project()
        for (name in listOf("storePublishPlayBinary", "storePublishAppStoreBinary")) {
            val deps = p.tasks.getByName(name).taskDependencies.getDependencies(null).map {
                it.name
            }
            assertTrue(name, "storeParseMetadata" in deps)
            assertFalse(name, "storeValidate" in deps || "exportStoreAssets" in deps)
        }
        assertTrue(p.tasks.findByName("storePublishBinary") != null)
    }

    @Test
    fun dryRunWithoutCredentialsSucceedsOffline() {
        val p = project()
        val aab = tempDir.newFile("app.aab")
        val t = play(p)
        t.packageName.set("com.example.app")
        t.releaseFiles.from(aab)
        t.dryRun.set(true)
        t.useReleaseNotes.set(false)
        t.publish()
    }

    @Test
    fun cliReleaseFileOverridesDslAndTrackOptionWins() {
        val p = project()
        val t = play(p)
        t.packageName.set("com.example.app")
        t.releaseFiles.from(File(tempDir.root, "ignored.aab"))
        t.cliReleaseFiles.set(listOf(tempDir.newFile("cli.aab").path))
        t.setTrackOption("beta")
        t.setReleaseStatusOption("draft")
        t.dryRun.set(true)
        t.useReleaseNotes.set(false)
        t.publish()
        assertTrue(t.track.get() == "beta")
        assertTrue(t.status.get() == "draft")
    }

    @Test
    fun missingReleaseFileFails() {
        val t = play(project())
        t.packageName.set("com.example.app")
        t.useReleaseNotes.set(false)
        assertThrows(GradleException::class.java) { t.publish() }
        t.cliReleaseFiles.set(listOf(File(tempDir.root, "nope.aab").path))
        t.dryRun.set(true)
        assertTrue(
            assertThrows(GradleException::class.java) {
                t.publish()
            }.message!!.contains("nope.aab"),
        )
    }

    @Test
    fun invalidStatusFails() {
        val t = play(project())
        t.packageName.set("com.example.app")
        t.releaseFiles.from(tempDir.newFile("a.aab"))
        t.status.set("bogus")
        t.useReleaseNotes.set(false)
        assertTrue(
            assertThrows(GradleException::class.java) {
                t.publish()
            }.message!!.contains("bogus"),
        )
    }

    @Test
    fun missingIpaFails() {
        val t = asc(project())
        t.bundleId.set("com.example.app")
        t.useReleaseNotesAsWhatToTest.set(false)
        assertTrue(
            assertThrows(GradleException::class.java) {
                t.publish()
            }.message!!.contains("IPA"),
        )
        t.ipaFile.set(File(tempDir.root, "x.ipa"))
        assertThrows(GradleException::class.java) { t.publish() }
    }

    @Test
    fun ascDryRunWithoutCredentialsSucceedsOffline() {
        val t = asc(project())
        val ipa = tempDir.newFile("app.ipa")
        t.bundleId.set("com.example.app")
        t.ipaFile.set(ipa)
        t.bundleShortVersion.set("1.0")
        t.bundleVersion.set("1")
        t.useReleaseNotesAsWhatToTest.set(false)
        t.dryRun.set(true)
        t.publish()
    }
}
