package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.report.ReportImageMode
import dev.ribod.storestudio.model.DiffChange
import dev.ribod.storestudio.model.DiffChangeType
import dev.ribod.storestudio.model.DiffDocument
import dev.ribod.storestudio.model.DiffRefInfo
import dev.ribod.storestudio.model.DiffSummary
import dev.ribod.storestudio.model.ExportAsset
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.StoreDeviceSlot
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class ExportStoreDiffHtmlTaskTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun generatesHtmlReportAndMarkdownSummary() {
        val testDir = tempFolder.newFolder("diff-test")
        val project = ProjectBuilder.builder().withProjectDir(testDir).build()
        val task = project.tasks.register(
            "exportStoreDiffHtml",
            ExportStoreDiffHtmlTask::class.java,
        ).get()

        val exportDir = File(testDir, "export")
        exportDir.mkdirs()
        val dummyImg = File(exportDir, "01_hero.png").apply {
            val img = BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = Color.BLUE
            g.fillRect(0, 0, 100, 200)
            g.dispose()
            ImageIO.write(img, "png", this)
        }

        val exportIndexFile = File(exportDir, "index.json")
        val exportIndex = ExportIndex(
            schemaVersion = 1,
            assets = listOf(
                ExportAsset(
                    slotKey = "PLAY_STORE/PHONE/en-US/hero",
                    target = StoreTarget.PLAY_STORE,
                    deviceSlot = StoreDeviceSlot.PHONE,
                    storeLocale = "en-US",
                    id = "hero",
                    order = 1,
                    position = 1,
                    file = "01_hero.png",
                    format = ImageFormat.PNG,
                    widthPx = 1080,
                    heightPx = 1920,
                    bytes = dummyImg.length(),
                    pixelSha256 = "sha123",
                    fileSha256 = "filesha123",
                    md5 = "md5123",
                    pHash = "0000000000000000",
                ),
            ),
        )
        exportIndex.toFile(exportIndexFile)

        val diffJsonFile = File(testDir, "diff.json")
        val diffDoc = DiffDocument(
            schemaVersion = 1,
            base = DiffRefInfo(ref = "v1.0.0", commit = "1234567", source = "GIT_BASELINE"),
            head = DiffRefInfo(ref = "HEAD", source = "CURRENT_EXPORT"),
            summary = DiffSummary(added = 1),
            changes = listOf(
                DiffChange(
                    type = DiffChangeType.ADDED,
                    bucket = "PLAY_STORE/PHONE/en-US",
                    id = "hero",
                    headPosition = 1,
                    headImage = "01_hero.png",
                    headPixelSha256 = "sha123",
                ),
            ),
        )
        diffDoc.toFile(diffJsonFile)

        val outputHtml = File(testDir, "diff.html")
        val outputMd = File(testDir, "diff-summary.md")

        task.diffJsonFile.set(diffJsonFile)
        task.headExportIndexFile.set(exportIndexFile)
        task.headExportDirectory.set(exportDir)
        task.imageMode.set(ReportImageMode.LINK)
        task.appName.set("Test Diff App")
        task.developerName.set("Diff Tester")
        task.outputHtmlFile.set(outputHtml)
        task.outputSummaryMarkdownFile.set(outputMd)

        task.generate()

        assertTrue(outputHtml.exists())
        val htmlContent = outputHtml.readText()
        assertTrue(htmlContent.contains("Test Diff App"))
        assertTrue(htmlContent.contains("hero"))

        assertTrue(outputMd.exists())
        val mdContent = outputMd.readText()
        assertTrue(mdContent.contains("<!-- store-studio-diff -->"))
        assertTrue(mdContent.contains("Test Diff App") || mdContent.contains("v1.0.0"))
    }
}
