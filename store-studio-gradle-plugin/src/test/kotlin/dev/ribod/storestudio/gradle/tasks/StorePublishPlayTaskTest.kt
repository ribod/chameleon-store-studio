package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.hash.AssetHasher
import dev.ribod.storestudio.gradle.dsl.StoreStudioExtension
import dev.ribod.storestudio.model.ExportAsset
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.StoreDeviceSlot
import dev.ribod.storestudio.model.ValidationReport
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorePublishPlayTaskTest {

    @get:Rule
    val tempDir: TemporaryFolder = TemporaryFolder()

    private fun sampleAsset(
        slot: StoreDeviceSlot,
        locale: String = "en-US",
        position: Int,
        file: String,
        fileSha256: String,
    ) = ExportAsset(
        slotKey = "PLAY_STORE/${slot.name}/$locale/shot$position",
        target = StoreTarget.PLAY_STORE,
        deviceSlot = slot,
        storeLocale = locale,
        id = "shot$position",
        order = position,
        position = position,
        file = file,
        format = ImageFormat.PNG,
        widthPx = 1080,
        heightPx = 1920,
        bytes = 100_000L,
        hasAlpha = false,
        colorSpace = "sRGB",
        pixelSha256 = "pixel$position",
        fileSha256 = fileSha256,
        pHash = "0000000000000000",
        md5 = "md5$position",
    )

    @Test
    fun failsWhenValidationReportMissing() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("testPublish", StorePublishPlayTask::class.java).get()

        val exportDir = tempDir.newFolder("export_missing")
        val indexFile = File(exportDir, "index.json").apply { writeText("{}") }
        val reportFile = File(tempDir.root, "non_existent_report.json")
        val hashFile = File(tempDir.root, "non_existent_hash.sha256")

        task.exportDirectory.set(exportDir)
        task.exportIndexFile.set(indexFile)
        task.reportFile.set(reportFile)
        task.inputsHashFile.set(hashFile)
        task.packageName.set("com.example.app")

        val ex = assertThrows(GradleException::class.java) {
            task.publish()
        }
        assertTrue(ex.message!!.contains("Assets were not validated in this build"))
    }

    @Test
    fun failsWhenExportIndexHashMismatches() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testPublishMismatch",
            StorePublishPlayTask::class.java,
        ).get()

        val exportDir = tempDir.newFolder("export_mismatch")
        val indexFile = File(exportDir, "index.json").apply { writeText("""{"assets":[]}""") }
        val reportFile = File(exportDir, "report.json").apply {
            writeText("""{"valid":true,"schemaVersion":1,"generatedAt":"now","violations":[]}""")
        }
        val hashFile = File(exportDir, "inputs.sha256").apply { writeText("wrong_hash") }

        task.exportDirectory.set(exportDir)
        task.exportIndexFile.set(indexFile)
        task.reportFile.set(reportFile)
        task.inputsHashFile.set(hashFile)
        task.packageName.set("com.example.app")

        val ex = assertThrows(GradleException::class.java) {
            task.publish()
        }
        assertTrue(ex.message!!.contains("export index was modified after validation"))
    }

    @Test
    fun executesDryRunSuccessfullyWithCompliantAssets() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("testPublishDry", StorePublishPlayTask::class.java).get()
        configureCompliantDryRun(task, "export_valid", "metadata")

        task.publish()
    }

    @Test
    fun dryRunWithTrackAndReleaseNotesSucceeds() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testPublishTrack",
            StorePublishPlayTask::class.java,
        ).get()
        configureCompliantDryRun(task, "export_track", "metadata_track")
        task.setTrackOption("beta")

        task.publish()
        assertEquals("beta", task.track.get())
    }

    @Test
    fun rejectsInvalidTrackNames() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("testPublishBadTrack", StorePublishPlayTask::class.java)
            .get()
        configureCompliantDryRun(task, "export_bad_track", "metadata_bad_track")
        task.setTrackOption("closed testing")

        val ex = assertThrows(GradleException::class.java) { task.publish() }
        assertTrue(ex.message!!.contains("Invalid Google Play track 'closed testing'"))
    }

    @Test
    fun trackFromDslIsWiredToPublishTasks() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("dev.ribod.storestudio")
        project.extensions.getByType(StoreStudioExtension::class.java)
            .publishing.googlePlay.track.set("alpha")

        val playTask = project.tasks.getByName("storePublishPlay") as StorePublishPlayTask
        val unifiedTask = project.tasks.getByName("storePublish") as StorePublishTask
        assertEquals("alpha", playTask.track.get())
        assertEquals("alpha", unifiedTask.playTrack.get())

        unifiedTask.setTrackOption("production")
        assertEquals("production", unifiedTask.playTrack.get())
    }

    @Test
    fun includeBinaryWithoutReleaseFilesThrowsHelpfulError() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testPublishNoBin",
            StorePublishPlayTask::class.java,
        ).get()
        configureCompliantDryRun(task, "export_nobin", "metadata_nobin")
        task.includeBinary.set(true)

        val ex = assertThrows(GradleException::class.java) { task.publish() }
        assertTrue(ex.message!!.contains("No release files"))
        assertTrue(ex.message!!.contains("storePublishPlayListing"))
    }

    @Test
    fun includeBinaryWithReleaseFileExecutesDryRun() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testPublishWithBin",
            StorePublishPlayTask::class.java,
        ).get()
        configureCompliantDryRun(task, "export_bin", "metadata_bin")
        task.includeBinary.set(true)
        val dummyAab = tempDir.newFile("sample.aab").apply { writeBytes(ByteArray(20)) }
        task.setReleaseFileOption(listOf(dummyAab.path))

        task.publish()
    }

    @Test
    fun listingWithoutBinarySucceedsWithoutReleaseFiles() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testPublishListingOnly",
            StorePublishPlayTask::class.java,
        ).get()
        configureCompliantDryRun(task, "export_listing", "metadata_listing")
        task.includeBinary.set(false)

        task.publish()
    }

    private fun configureCompliantDryRun(
        task: StorePublishPlayTask,
        exportFolder: String,
        metadataFolder: String,
    ) {
        val exportDir = tempDir.newFolder(exportFolder)
        val shotFile = File(exportDir, "PLAY_STORE/en-US/PHONE/01_shot.png").apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(50))
        }
        val fileSha = AssetHasher.fileSha256(shotFile)
        val asset = sampleAsset(
            StoreDeviceSlot.PHONE,
            "en-US",
            1,
            "PLAY_STORE/en-US/PHONE/01_shot.png",
            fileSha,
        )
        val exportIndex = ExportIndex(assets = listOf(asset))
        val indexFile = File(exportDir, "index.json")
        exportIndex.toFile(indexFile)

        val reportFile = File(exportDir, "report.json").apply {
            writeText("""{"valid":true,"schemaVersion":1,"generatedAt":"now","violations":[]}""")
        }
        val hashFile = File(exportDir, "inputs.sha256").apply {
            writeText(AssetHasher.fileSha256(indexFile))
        }

        val metaDir = tempDir.newFolder(metadataFolder).apply {
            val locDir = resolve("en-US/play").apply { mkdirs() }
            locDir.resolve("title.txt").writeText("Sample App")
            locDir.resolve("short_description.txt").writeText("Sample short description")
            locDir.resolve("release_notes.txt").writeText("New agenda view")
        }

        task.exportDirectory.set(exportDir)
        task.exportIndexFile.set(indexFile)
        task.reportFile.set(reportFile)
        task.inputsHashFile.set(hashFile)
        task.metadataDirectory.set(metaDir)
        task.packageName.set("com.example.app")
        task.dryRun.set(true)
    }
}
