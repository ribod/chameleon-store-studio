package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.hash.AssetHasher
import dev.ribod.storestudio.core.publish.PublishPart
import dev.ribod.storestudio.model.ExportAsset
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.StoreDeviceSlot
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorePublishTaskTest {

    @get:Rule
    val tempDir: TemporaryFolder = TemporaryFolder()

    private fun sampleAsset(
        target: StoreTarget,
        slot: StoreDeviceSlot,
        locale: String = "en-US",
        position: Int,
        file: String,
        fileSha256: String,
    ) = ExportAsset(
        slotKey = "${target.name}/${slot.name}/$locale/shot$position",
        target = target,
        deviceSlot = slot,
        storeLocale = locale,
        id = "shot$position",
        order = position,
        position = position,
        file = file,
        format = ImageFormat.PNG,
        widthPx = if (target == StoreTarget.PLAY_STORE) 1080 else 1320,
        heightPx = if (target == StoreTarget.PLAY_STORE) 1920 else 2868,
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
        val task = project.tasks.register(
            "testPublishGate",
            StorePublishTask::class.java,
        ).get()

        val exportDir = tempDir.newFolder("export_gate")
        val indexFile = File(exportDir, "index.json").apply { writeText("{}") }
        val reportFile = File(tempDir.root, "non_existent_report.json")
        val hashFile = File(tempDir.root, "non_existent_hash.sha256")

        task.exportDirectory.set(exportDir)
        task.exportIndexFile.set(indexFile)
        task.reportFile.set(reportFile)
        task.inputsHashFile.set(hashFile)

        val ex = assertThrows(GradleException::class.java) {
            task.publish()
        }
        assertTrue(ex.message!!.contains("Assets were not validated in this build"))
    }

    @Test
    fun executesUnifiedDryRunForBothStores() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testUnifiedDryRun",
            StorePublishTask::class.java,
        ).get()

        val exportDir = tempDir.newFolder("export_unified")
        val playShot = File(exportDir, "PLAY_STORE/en-US/PHONE/01_shot.png").apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(50))
        }
        val appStoreShot = File(exportDir, "APP_STORE/en-US/IPHONE_6_9/01_shot.png").apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(50))
        }

        val playAsset = sampleAsset(
            StoreTarget.PLAY_STORE,
            StoreDeviceSlot.PHONE,
            "en-US",
            1,
            "PLAY_STORE/en-US/PHONE/01_shot.png",
            AssetHasher.fileSha256(playShot),
        )
        val appStoreAsset = sampleAsset(
            StoreTarget.APP_STORE,
            StoreDeviceSlot.IPHONE_6_9,
            "en-US",
            1,
            "APP_STORE/en-US/IPHONE_6_9/01_shot.png",
            AssetHasher.fileSha256(appStoreShot),
        )

        val exportIndex = ExportIndex(assets = listOf(playAsset, appStoreAsset))
        val indexFile = File(exportDir, "index.json")
        exportIndex.toFile(indexFile)

        val reportFile = File(exportDir, "report.json").apply {
            writeText("""{"valid":true,"schemaVersion":1,"generatedAt":"now","violations":[]}""")
        }
        val hashFile = File(exportDir, "inputs.sha256").apply {
            writeText(AssetHasher.fileSha256(indexFile))
        }

        val metaDir = tempDir.newFolder("metadata").apply {
            val playDir = resolve("en-US/play").apply { mkdirs() }
            playDir.resolve("title.txt").writeText("Sample App")
            playDir.resolve("full_description.txt").writeText("Sample Description")
            val appStoreDir = resolve("en-US/appstore").apply { mkdirs() }
            appStoreDir.resolve("description.txt").writeText("Sample Description")
        }

        task.exportDirectory.set(exportDir)
        task.exportIndexFile.set(indexFile)
        task.reportFile.set(reportFile)
        task.inputsHashFile.set(hashFile)
        task.metadataDirectory.set(metaDir)
        task.playPackageName.set("com.example.app")
        task.appStoreBundleId.set("com.example.app.ios")
        task.dryRun.set(true)

        task.publish()
    }

    @Test
    fun skipsUnconfiguredStoreGracefullyInDryRun() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testSkipUnconfigured",
            StorePublishTask::class.java,
        ).get()

        val exportDir = tempDir.newFolder("export_skip")
        val playShot = File(exportDir, "PLAY_STORE/en-US/PHONE/01_shot.png").apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(50))
        }
        val playAsset = sampleAsset(
            StoreTarget.PLAY_STORE,
            StoreDeviceSlot.PHONE,
            "en-US",
            1,
            "PLAY_STORE/en-US/PHONE/01_shot.png",
            AssetHasher.fileSha256(playShot),
        )

        val exportIndex = ExportIndex(assets = listOf(playAsset))
        val indexFile = File(exportDir, "index.json")
        exportIndex.toFile(indexFile)

        val reportFile = File(exportDir, "report.json").apply {
            writeText("""{"valid":true,"schemaVersion":1,"generatedAt":"now","violations":[]}""")
        }
        val hashFile = File(exportDir, "inputs.sha256").apply {
            writeText(AssetHasher.fileSha256(indexFile))
        }

        task.exportDirectory.set(exportDir)
        task.exportIndexFile.set(indexFile)
        task.reportFile.set(reportFile)
        task.inputsHashFile.set(hashFile)
        task.playPackageName.set("com.example.app")
        // appStoreBundleId not configured: should skip gracefully!
        task.dryRun.set(true)

        task.publish()
    }

    @Test
    fun filtersExecutionByTargetOption() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testTargetFilter",
            StorePublishTask::class.java,
        ).get()

        val exportDir = tempDir.newFolder("export_filter")
        val playShot = File(exportDir, "PLAY_STORE/en-US/PHONE/01_shot.png").apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(50))
        }
        val playAsset = sampleAsset(
            StoreTarget.PLAY_STORE,
            StoreDeviceSlot.PHONE,
            "en-US",
            1,
            "PLAY_STORE/en-US/PHONE/01_shot.png",
            AssetHasher.fileSha256(playShot),
        )

        val exportIndex = ExportIndex(assets = listOf(playAsset))
        val indexFile = File(exportDir, "index.json")
        exportIndex.toFile(indexFile)

        val reportFile = File(exportDir, "report.json").apply {
            writeText("""{"valid":true,"schemaVersion":1,"generatedAt":"now","violations":[]}""")
        }
        val hashFile = File(exportDir, "inputs.sha256").apply {
            writeText(AssetHasher.fileSha256(indexFile))
        }

        task.exportDirectory.set(exportDir)
        task.exportIndexFile.set(indexFile)
        task.reportFile.set(reportFile)
        task.inputsHashFile.set(hashFile)
        task.playPackageName.set("com.example.app")
        task.targetMarketplace.set("PLAY_STORE")
        task.dryRun.set(true)

        task.publish()
    }

    @Test
    fun executesDryRunWithBinaryIncludedWhenConfigured() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testBinaryPublishTask",
            StorePublishTask::class.java,
        ).get()

        val exportDir = tempDir.newFolder("export_bin_task")
        val playShot = File(exportDir, "PLAY_STORE/en-US/PHONE/01_shot.png").apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(50))
        }
        val playAsset = sampleAsset(
            StoreTarget.PLAY_STORE,
            StoreDeviceSlot.PHONE,
            "en-US",
            1,
            "PLAY_STORE/en-US/PHONE/01_shot.png",
            AssetHasher.fileSha256(playShot),
        )
        val exportIndex = ExportIndex(assets = listOf(playAsset))
        val indexFile = File(exportDir, "index.json")
        exportIndex.toFile(indexFile)

        val reportFile = File(exportDir, "report.json").apply {
            writeText("""{"valid":true,"schemaVersion":1,"generatedAt":"now","violations":[]}""")
        }
        val hashFile = File(exportDir, "inputs.sha256").apply {
            writeText(AssetHasher.fileSha256(indexFile))
        }

        val dummyAab = tempDir.newFile("app_umbrella.aab").apply { writeBytes(ByteArray(20)) }
        val dummyIpa = tempDir.newFile("app_umbrella.ipa").apply { writeBytes(ByteArray(20)) }

        task.exportDirectory.set(exportDir)
        task.exportIndexFile.set(indexFile)
        task.reportFile.set(reportFile)
        task.inputsHashFile.set(hashFile)
        task.playPackageName.set("com.example.app")
        task.appStoreBundleId.set("com.example.app.ios")
        task.includeBinary.set(true)
        task.setReleaseFileOption(listOf(dummyAab.path))
        task.setIpaOption(dummyIpa.path)
        task.dryRun.set(true)

        task.publish()
    }

    @Test
    fun executesTextOnlyDryRunForBothStoresWithoutExportGate() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testTextOnlyMultiStore",
            StorePublishTask::class.java,
        ).get()

        val metaDir = tempDir.newFolder("meta_text_only").apply {
            resolve("en-US/play").mkdirs()
            resolve("en-US/play/title.txt").writeText("My Game")
            resolve("en-US/play/release_notes.txt").writeText("Fixed bugs")
            resolve("en-US/appstore").mkdirs()
            resolve("en-US/appstore/name.txt").writeText("My Game")
            resolve("en-US/appstore/release_notes.txt").writeText("Fixed bugs")
        }

        task.parts.set(setOf(PublishPart.LISTING, PublishPart.RELEASE_NOTES))
        task.metadataDirectory.set(metaDir)
        task.playPackageName.set("com.example.app")
        task.playTrack.set("production")
        task.appStoreBundleId.set("com.example.app.ios")
        task.dryRun.set(true)

        task.publish()
    }

    @Test
    fun cliOptionsWirePlayAndAppStoreFlags() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testCliFlags",
            StorePublishTask::class.java,
        ).get()

        val mapping = tempDir.newFile("mapping.txt")
        task.setPartsOption("binary,texts")
        task.setChangesNotSentForReviewOption(true)
        task.setUserFractionOption("0.25")
        task.setMappingFileOption(mapping.path)
        task.setInAppUpdatePriorityOption("3")
        task.setExistingEditIdOption("edit-42")
        task.setBundleVersionOption("101")
        task.setBundleShortVersionOption("2.1.0")
        task.setBetaGroupOption(listOf("Internal", "QA"))
        task.setSubmitBetaReviewOption(true)
        task.setSubmitForReviewOption(true)

        assertEquals("binary,texts", task.cliParts.get())
        assertTrue(task.playChangesNotSentForReview.get())
        assertEquals(0.25, task.playUserFraction.get(), 0.0001)
        assertEquals(mapping, task.playMappingFile.get().asFile)
        assertEquals(3, task.playInAppUpdatePriority.get())
        assertEquals("edit-42", task.playExistingEditId.get())
        assertEquals("101", task.ascBundleVersion.get())
        assertEquals("2.1.0", task.ascBundleShortVersion.get())
        assertEquals(listOf("Internal", "QA"), task.ascBetaGroups.get())
        assertTrue(task.ascSubmitBetaReview.get())
        assertTrue(task.ascSubmitForReview.get())
    }
}
