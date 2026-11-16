package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.report.ReportImageMode
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

class ExportStoreListingPreviewHtmlTaskTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun generatesListingPreviewHtmlWithAuthenticStoreMockups() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "exportStoreListingPreviewHtml",
            ExportStoreListingPreviewHtmlTask::class.java,
        ).get()

        val baseDir = tempFolder.newFolder("store")
        val exportDir = File(baseDir, "export")
        exportDir.mkdirs()

        val imageFile = File(exportDir, "play/phone/en-US/screen1.png")
        imageFile.parentFile.mkdirs()
        createTestImage(imageFile, 1080, 1920)

        val exportIndexFile = File(exportDir, "index.json")
        val exportIndex = ExportIndex(
            schemaVersion = 1,
            assets = listOf(
                ExportAsset(
                    slotKey = "play/phone/en-US/screen1",
                    target = StoreTarget.PLAY_STORE,
                    deviceSlot = StoreDeviceSlot.PHONE,
                    storeLocale = "en-US",
                    id = "screen1",
                    order = 1,
                    position = 1,
                    file = "play/phone/en-US/screen1.png",
                    format = ImageFormat.PNG,
                    widthPx = 1080,
                    heightPx = 1920,
                    bytes = imageFile.length(),
                    pixelSha256 = "abc123pixel",
                    fileSha256 = "abc123file",
                    pHash = "0000ffff",
                    md5 = "md5abc",
                ),
            ),
        )
        exportIndex.toFile(exportIndexFile)

        val outputFile = File(baseDir, "reports/listing-preview.html")

        task.exportDirectory.set(exportDir)
        task.exportIndexFile.set(exportIndexFile)
        task.imageMode.set(ReportImageMode.LINK)
        task.maxThumbnailDimension.set(720)
        task.appName.set("Preview Showcase App")
        task.developerName.set("Showcase Team")
        task.category.set("Productivity")
        task.outputFile.set(outputFile)

        task.generate()

        assertTrue(outputFile.exists())
        val html = outputFile.readText()
        assertTrue(html.contains("Preview Showcase App"))
        assertTrue(html.contains("Showcase Team"))
        assertTrue(html.contains("Instalar"))
        assertTrue(html.contains("OBTENER"))
        assertTrue(html.contains("class=\"store-btn active play-store\""))
        assertTrue(html.contains("id=\"play-mockup\""))
        assertTrue(html.contains("id=\"apple-mockup\""))
    }

    private fun createTestImage(target: File, width: Int, height: Int) {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.GREEN
        g.fillRect(0, 0, width, height)
        g.dispose()
        ImageIO.write(img, "png", target)
    }
}
