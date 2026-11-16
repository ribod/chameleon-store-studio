package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.core.publish.PublishPart
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorePublishPartsTest {

    @get:Rule
    val tempDir: TemporaryFolder = TemporaryFolder()

    private val textTasks = listOf(
        "storePublishPlayTexts",
        "storePublishPlayReleaseNotes",
        "storePublishAppStoreTexts",
        "storePublishAppStoreReleaseNotes",
        "storePublishTexts",
        "storePublishReleaseNotes",
    )
    private val imageTasks = listOf(
        "storePublishPlayImages",
        "storePublishAppStoreImages",
        "storePublishPlayListing",
        "storePublishAppStoreListing",
        "storePublishImages",
        "storePublishListing",
    )
    private val heavyTasks = setOf("storeRender", "storeExport", "storeValidate")

    private fun appliedProject(): Project = ProjectBuilder.builder().build().also {
        it.pluginManager.apply("dev.ribod.storestudio")
    }

    private fun transitiveDependencyNames(task: Task): Set<String> {
        val seen = mutableSetOf<Task>()
        fun visit(current: Task) {
            for (dep in current.taskDependencies.getDependencies(current)) {
                if (seen.add(dep)) visit(dep)
            }
        }
        visit(task)
        return seen.map { it.name }.toSet()
    }

    @Test
    fun textTasksDoNotDependOnRenderExportOrValidate() {
        val project = appliedProject()
        for (name in textTasks) {
            val deps = transitiveDependencyNames(project.tasks.getByName(name))
            assertTrue("$name should depend on storeParseMetadata", "storeParseMetadata" in deps)
            assertEquals("$name pulls in $deps", emptySet<String>(), deps.intersect(heavyTasks))
        }
    }

    @Test
    fun imageTasksKeepTheValidationGate() {
        val project = appliedProject()
        for (name in imageTasks) {
            assertTrue(
                "$name must depend on storeValidate",
                "storeValidate" in transitiveDependencyNames(project.tasks.getByName(name)),
            )
        }
    }

    @Test
    fun tasksSelectTheirParts() {
        val project = appliedProject()
        fun parts(name: String): Set<PublishPart> =
            when (val task = project.tasks.getByName(name)) {
                is StorePublishPlayTask -> task.parts.get()
                is StorePublishAppStoreTask -> task.parts.get()
                is StorePublishTask -> task.parts.get()
                else -> error(name)
            }
        assertEquals(PublishPart.ALL, parts("storePublishPlay"))
        assertEquals(PublishPart.ALL, parts("storePublishAppStore"))
        assertEquals(PublishPart.ALL, parts("storePublish"))
        assertEquals(PublishPart.ALL, parts("storePublishPlayListing"))
        assertEquals(PublishPart.ALL, parts("storePublishAppStoreListing"))
        assertEquals(PublishPart.ALL, parts("storePublishListing"))
        assertEquals(setOf(PublishPart.LISTING), parts("storePublishPlayTexts"))
        assertEquals(setOf(PublishPart.LISTING), parts("storePublishAppStoreTexts"))
        assertEquals(setOf(PublishPart.LISTING), parts("storePublishTexts"))
        assertEquals(setOf(PublishPart.RELEASE_NOTES), parts("storePublishPlayReleaseNotes"))
        assertEquals(setOf(PublishPart.RELEASE_NOTES), parts("storePublishAppStoreReleaseNotes"))
        assertEquals(setOf(PublishPart.RELEASE_NOTES), parts("storePublishReleaseNotes"))
        assertEquals(setOf(PublishPart.IMAGES), parts("storePublishPlayImages"))
        assertEquals(setOf(PublishPart.IMAGES), parts("storePublishAppStoreImages"))
        assertEquals(setOf(PublishPart.IMAGES), parts("storePublishImages"))
    }

    @Test
    fun listingOnlyDryRunNeedsNoExport() {
        val task = playTask("playListing", setOf(PublishPart.LISTING), title = "Sample App")
        task.publish()
    }

    @Test
    fun releaseNotesOnlyWithoutTrackFails() {
        val task = playTask("playNotes", setOf(PublishPart.RELEASE_NOTES), title = "Sample App")
        val ex = assertThrows(GradleException::class.java) { task.publish() }
        assertTrue(ex.message!!.contains("requires a track"))
    }

    @Test
    fun textRulesGateTheTextOnlyPublish() {
        val tooLong = "T".repeat(31)
        val task = playTask("playLongTitle", setOf(PublishPart.LISTING), title = tooLong)
        val ex = assertThrows(GradleException::class.java) { task.publish() }
        assertTrue(ex.message!!.contains("Publishing aborted"))
    }

    @Test
    fun listingTaskIgnoresReleaseNotesRules() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("playLongNotes", StorePublishPlayTask::class.java).get()
        val meta = tempDir.newFolder("meta_notes").also { it.resolve("en-US/play").mkdirs() }
        meta.resolve("en-US/play/title.txt").writeText("Sample App")
        meta.resolve("en-US/play/release_notes.txt").writeText("n".repeat(501))
        task.parts.set(setOf(PublishPart.LISTING))
        task.metadataDirectory.set(meta)
        task.packageName.set("com.example.app")
        task.dryRun.set(true)
        task.publish()

        task.parts.set(setOf(PublishPart.RELEASE_NOTES))
        task.track.set("beta")
        assertThrows(GradleException::class.java) { task.publish() }
        assertFalse(task.parts.get().contains(PublishPart.LISTING))
    }

    @Test
    fun cliPartsOverridesDefaultPartsAndSkipsExportGateForTexts() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("multiCliParts", StorePublishTask::class.java).get()
        val meta = tempDir.newFolder("meta_multi_cli").apply {
            resolve("en-US/play").mkdirs()
            resolve("en-US/play/title.txt").writeText("Sample App")
            resolve("en-US/play/release_notes.txt").writeText("What's new")
            resolve("en-US/appstore").mkdirs()
            resolve("en-US/appstore/name.txt").writeText("Sample App")
            resolve("en-US/appstore/release_notes.txt").writeText("What's new")
        }
        task.metadataDirectory.set(meta)
        task.playPackageName.set("com.example.app")
        task.playTrack.set("production")
        task.appStoreBundleId.set("com.example.app.ios")
        task.dryRun.set(true)
        task.setPartsOption("texts,releaseNotes")
        task.publish()
    }

    @Test
    fun invalidCliPartsFailsWithHelpfulMessage() {
        val task = playTask("playInvalidParts", PublishPart.ALL, title = "Sample App")
        task.setPartsOption("texts,unknownPart")
        val ex = assertThrows(GradleException::class.java) { task.publish() }
        assertTrue(ex.message!!.contains("Unknown publish part 'unknownPart'"))
    }

    private fun playTask(
        name: String,
        parts: Set<PublishPart>,
        title: String,
    ): StorePublishPlayTask {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(name, StorePublishPlayTask::class.java).get()
        val meta = tempDir.newFolder("meta_$name")
        File(meta, "en-US/play").apply { mkdirs() }.resolve("title.txt").writeText(title)
        task.parts.set(parts)
        task.metadataDirectory.set(meta)
        task.packageName.set("com.example.app")
        task.dryRun.set(true)
        return task
    }
}
