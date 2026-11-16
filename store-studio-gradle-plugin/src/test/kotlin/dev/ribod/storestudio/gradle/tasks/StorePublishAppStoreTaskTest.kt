package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.hash.AssetHasher
import dev.ribod.storestudio.model.ExportAsset
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.StoreDeviceSlot
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorePublishAppStoreTaskTest {

    @get:Rule
    val tempDir: TemporaryFolder = TemporaryFolder()

    private fun sampleAsset(
        slot: StoreDeviceSlot,
        locale: String = "en-US",
        position: Int,
        file: String,
        fileSha256: String,
    ) = ExportAsset(
        slotKey = "APP_STORE/${slot.name}/$locale/shot$position",
        target = StoreTarget.APP_STORE,
        deviceSlot = slot,
        storeLocale = locale,
        id = "shot$position",
        order = position,
        position = position,
        file = file,
        format = ImageFormat.PNG,
        widthPx = 1320,
        heightPx = 2868,
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
            "testPublishAppStore",
            StorePublishAppStoreTask::class.java,
        ).get()

        val exportDir = tempDir.newFolder("export_missing")
        val indexFile = File(exportDir, "index.json").apply { writeText("{}") }
        val reportFile = File(tempDir.root, "non_existent_report.json")
        val hashFile = File(tempDir.root, "non_existent_hash.sha256")

        task.exportDirectory.set(exportDir)
        task.exportIndexFile.set(indexFile)
        task.reportFile.set(reportFile)
        task.inputsHashFile.set(hashFile)
        task.bundleId.set("com.example.app.ios")

        val ex = assertThrows(GradleException::class.java) {
            task.publish()
        }
        assertTrue(ex.message!!.contains("Assets were not validated in this build"))
    }

    @Test
    fun failsWhenExportIndexHashMismatches() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testPublishAppStoreMismatch",
            StorePublishAppStoreTask::class.java,
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
        task.bundleId.set("com.example.app.ios")

        val ex = assertThrows(GradleException::class.java) {
            task.publish()
        }
        assertTrue(ex.message!!.contains("export index was modified after validation"))
    }

    @Test
    fun failsWhenBundleIdMissing() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testPublishAppStoreNoBundleId",
            StorePublishAppStoreTask::class.java,
        ).get()

        val exportDir = tempDir.newFolder("export_no_bundle")
        val indexFile = File(exportDir, "index.json").apply { writeText("""{"assets":[]}""") }
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

        val ex = assertThrows(GradleException::class.java) {
            task.publish()
        }
        assertTrue(ex.message!!.contains("Apple App Store bundle identifier must be configured"))
    }

    @Test
    fun executesDryRunSuccessfullyWithCompliantAssets() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testPublishAppStoreDry",
            StorePublishAppStoreTask::class.java,
        ).get()

        val exportDir = tempDir.newFolder("export_valid")
        val shotFile = File(exportDir, "APP_STORE/en-US/IPHONE_6_9/01_shot.png").apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(50))
        }
        val fileSha = AssetHasher.fileSha256(shotFile)
        val asset = sampleAsset(
            StoreDeviceSlot.IPHONE_6_9,
            "en-US",
            1,
            "APP_STORE/en-US/IPHONE_6_9/01_shot.png",
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

        val metaDir = tempDir.newFolder("metadata").apply {
            val locDir = resolve("en-US/appstore").apply { mkdirs() }
            locDir.resolve("description.txt").writeText("Sample iOS App Description")
            locDir.resolve("keywords.txt").writeText("sample, ios, app")
        }

        task.exportDirectory.set(exportDir)
        task.exportIndexFile.set(indexFile)
        task.reportFile.set(reportFile)
        task.inputsHashFile.set(hashFile)
        task.metadataDirectory.set(metaDir)
        task.bundleId.set("com.example.app.ios")
        task.dryRun.set(true)

        task.publish()
    }

    @Test
    fun executesUnifiedIpaAndListingDryRunViaPartsOption() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testUnifiedAscDryRun",
            StorePublishAppStoreTask::class.java,
        ).get()

        val metaDir = tempDir.newFolder("meta_asc_unified").apply {
            resolve("en-US/appstore").mkdirs()
            resolve("en-US/appstore/name.txt").writeText("Sample iOS App")
            resolve("en-US/appstore/description.txt").writeText("Sample iOS App Description")
            resolve("en-US/appstore/release_notes.txt").writeText("Release notes")
        }
        val ipaFile = tempDir.newFile("app_unified.ipa").apply { writeBytes(ByteArray(32)) }

        task.metadataDirectory.set(metaDir)
        task.bundleId.set("com.example.app.ios")
        task.setIpaOption(ipaFile.path)
        task.setBundleShortVersionOption("1.0.0")
        task.setBundleVersionOption("10")
        task.setBetaGroupOption(listOf("Beta"))
        task.setSubmitBetaReviewOption(true)
        task.setSubmitForReviewOption(true)
        task.setPartsOption("binary,texts,releaseNotes")
        task.dryRun.set(true)

        task.publish()
    }
}
