package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.metadata.PlayMetadataEntry
import dev.ribod.storestudio.core.metadata.StoreMetadataBundle
import dev.ribod.storestudio.core.metadata.StoreMetadataManifest
import dev.ribod.storestudio.model.ExportAsset
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.Severity
import dev.ribod.storestudio.model.StoreDeviceSlot
import dev.ribod.storestudio.model.ValidationReport
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreValidateTaskTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private fun sampleAsset(
        slotKey: String,
        target: StoreTarget,
        deviceSlot: StoreDeviceSlot,
        storeLocale: String = "en-US",
        id: String,
        position: Int,
        file: String,
        fileSha256: String = "dummy_file",
        widthPx: Int = 1080,
        heightPx: Int = 1920,
    ) = ExportAsset(
        slotKey = slotKey,
        target = target,
        deviceSlot = deviceSlot,
        storeLocale = storeLocale,
        id = id,
        order = position,
        position = position,
        file = file,
        format = ImageFormat.PNG,
        widthPx = widthPx,
        heightPx = heightPx,
        bytes = 500_000L,
        hasAlpha = false,
        colorSpace = "sRGB",
        pixelSha256 = "dummy_pixel",
        fileSha256 = fileSha256,
        pHash = "0000000000000000",
        md5 = "dummy_md5",
    )

    @Test
    fun passesValidationAndWritesReportAndInputsHashForCompliantAssets() {
        val projectDir = tempDir.newFolder("project")
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()

        val exportDir = tempDir.newFolder("export")
        val shot1File = File(exportDir, "PLAY_STORE/en-US/PHONE/01_shot1.png").apply {
            parentFile.mkdirs()
            writeText("dummy")
        }
        val shot2File = File(exportDir, "PLAY_STORE/en-US/PHONE/02_shot2.png").apply {
            parentFile.mkdirs()
            writeText("dummy")
        }
        val fgFile = File(exportDir, "PLAY_STORE/en-US/FEATURE_GRAPHIC/01_fg.png").apply {
            parentFile.mkdirs()
            writeText("dummy")
        }

        val asset1 = sampleAsset(
            slotKey = "PLAY_STORE/PHONE/en-US/shot1",
            target = StoreTarget.PLAY_STORE,
            deviceSlot = StoreDeviceSlot.PHONE,
            id = "shot1",
            position = 1,
            file = "PLAY_STORE/en-US/PHONE/01_shot1.png",
            fileSha256 = dev.ribod.storestudio.core.hash.AssetHasher.fileSha256(shot1File),
        )
        val asset2 = sampleAsset(
            slotKey = "PLAY_STORE/PHONE/en-US/shot2",
            target = StoreTarget.PLAY_STORE,
            deviceSlot = StoreDeviceSlot.PHONE,
            id = "shot2",
            position = 2,
            file = "PLAY_STORE/en-US/PHONE/02_shot2.png",
            fileSha256 = dev.ribod.storestudio.core.hash.AssetHasher.fileSha256(shot2File),
        )
        val fg = sampleAsset(
            slotKey = "PLAY_STORE/FEATURE_GRAPHIC/en-US/fg",
            target = StoreTarget.PLAY_STORE,
            deviceSlot = StoreDeviceSlot.FEATURE_GRAPHIC,
            id = "fg",
            position = 1,
            file = "PLAY_STORE/en-US/FEATURE_GRAPHIC/01_fg.png",
            fileSha256 = dev.ribod.storestudio.core.hash.AssetHasher.fileSha256(fgFile),
            widthPx = 1024,
            heightPx = 500,
        )

        val exportIndex = ExportIndex(assets = listOf(asset1, asset2, fg))
        val exportIndexFile = File(exportDir, "index.json")
        exportIndex.toFile(exportIndexFile)

        val reportFile = File(tempDir.newFolder("validation"), "report.json")
        val inputsHashFile = File(tempDir.root, "inputs.sha256")

        val task = project.tasks.register("testValidate", StoreValidateTask::class.java).get()
        task.exportDirectory.set(exportDir)
        task.exportIndexFile.set(exportIndexFile)
        task.failOnWarning.set(false)
        task.featureGraphicMissing.set(Severity.ERROR)
        task.configuredTargets.set(setOf(StoreTarget.PLAY_STORE))
        task.configuredLocales.set(setOf("en-US"))
        task.supportsIpad.set(false)
        task.reportFile.set(reportFile)
        task.inputsHashFile.set(inputsHashFile)

        task.validate()

        assertTrue(reportFile.exists())
        assertTrue(inputsHashFile.exists())

        val report = ValidationReport.fromFile(reportFile)
        assertTrue(report.valid)
    }

    @Test
    fun writesReportBeforeThrowingWhenValidationFails() {
        val projectDir = tempDir.newFolder("project2")
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()

        val exportDir = tempDir.newFolder("export2")
        // Only 1 screenshot for Play Store (minimum is 2)
        val asset1 = sampleAsset(
            slotKey = "PLAY_STORE/PHONE/en-US/shot1",
            target = StoreTarget.PLAY_STORE,
            deviceSlot = StoreDeviceSlot.PHONE,
            id = "shot1",
            position = 1,
            file = "PLAY_STORE/en-US/PHONE/01_shot1.png",
        )
        val exportIndex = ExportIndex(assets = listOf(asset1))
        val exportIndexFile = File(exportDir, "index.json")
        exportIndex.toFile(exportIndexFile)

        val reportFile = File(tempDir.newFolder("validation2"), "report.json")
        val inputsHashFile = File(tempDir.root, "inputs2.sha256")

        val task = project.tasks.register("testValidateFail", StoreValidateTask::class.java).get()
        task.exportDirectory.set(exportDir)
        task.exportIndexFile.set(exportIndexFile)
        task.failOnWarning.set(false)
        task.featureGraphicMissing.set(Severity.ERROR)
        task.configuredTargets.set(setOf(StoreTarget.PLAY_STORE))
        task.configuredLocales.set(setOf("en-US"))
        task.supportsIpad.set(false)
        task.reportFile.set(reportFile)
        task.inputsHashFile.set(inputsHashFile)

        val exception = assertThrows(GradleException::class.java) {
            task.validate()
        }

        assertTrue(exception.message!!.contains("Store Studio validation failed"))
        // Report MUST be written even when failing
        assertTrue(reportFile.exists())
        val report = ValidationReport.fromFile(reportFile)
        assertFalse(report.valid)
        assertTrue(report.violations.any { it.ruleId == "PLAY_SCREENSHOT_COUNT_MIN" })
    }

    @Test
    fun failsValidationWhenMetadataTitleExceeds30Chars() {
        val project = ProjectBuilder.builder().build()
        val exportDir = tempDir.newFolder("export_meta")
        val exportIndexFile = File(exportDir, "index.json")
        ExportIndex(assets = emptyList()).toFile(exportIndexFile)

        val metaBundle = StoreMetadataBundle(
            locale = "en-US",
            play = PlayMetadataEntry(
                title = "Title That Is Way Longer Than 30 Chars",
            ),
        )
        val metaManifest = StoreMetadataManifest(
            locales = mapOf("en-US" to metaBundle),
        )
        val metaFile = File(tempDir.root, "metadata.json")
        metaManifest.toFile(metaFile)

        val reportFile = File(tempDir.newFolder("validation_meta"), "report.json")
        val inputsHashFile = File(tempDir.root, "inputs_meta.sha256")

        val task = project.tasks.register("testValidateMeta", StoreValidateTask::class.java).get()
        task.exportDirectory.set(exportDir)
        task.exportIndexFile.set(exportIndexFile)
        task.metadataFile.set(metaFile)
        task.failOnWarning.set(false)
        task.featureGraphicMissing.set(Severity.INFO)
        task.configuredTargets.set(setOf(StoreTarget.PLAY_STORE))
        task.configuredLocales.set(setOf("en-US"))
        task.supportsIpad.set(false)
        task.reportFile.set(reportFile)
        task.inputsHashFile.set(inputsHashFile)

        val exception = assertThrows(GradleException::class.java) {
            task.validate()
        }

        assertTrue(exception.message!!.contains("Store Studio validation failed"))
        val report = ValidationReport.fromFile(reportFile)
        assertFalse(report.valid)
        assertTrue(report.violations.any { it.ruleId == "PLAY_TEXT_TITLE_LENGTH" })
    }
}
