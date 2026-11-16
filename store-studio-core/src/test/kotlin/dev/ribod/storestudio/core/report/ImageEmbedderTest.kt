package dev.ribod.storestudio.core.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class ImageEmbedderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun embedModeLinkReturnsRelativePathOnly() {
        val baseDir = tempFolder.newFolder("output")
        val imageFile = File(baseDir, "phone/screenshot1.png")
        imageFile.parentFile.mkdirs()
        createTestImage(imageFile, 1080, 1920)

        val embedded = ImageEmbedder.embed(
            imageFile = imageFile,
            relativePath = "phone/screenshot1.png",
            mode = ReportImageMode.LINK,
        )

        assertEquals("phone/screenshot1.png", embedded.fullResRelativePath)
        assertEquals("phone/screenshot1.png", embedded.dataUri)
        assertEquals(1080, embedded.widthPx)
        assertEquals(1920, embedded.heightPx)
        assertFalse(embedded.dataUri.startsWith("data:image/"))
    }

    @Test
    fun embedModeFullEncodesBase64Png() {
        val baseDir = tempFolder.newFolder("output")
        val imageFile = File(baseDir, "screen.png")
        createTestImage(imageFile, 100, 100)

        val embedded = ImageEmbedder.embed(
            imageFile = imageFile,
            relativePath = "screen.png",
            mode = ReportImageMode.FULL,
        )

        assertTrue(embedded.dataUri.startsWith("data:image/png;base64,"))
        assertEquals("screen.png", embedded.fullResRelativePath)
        assertEquals(100, embedded.widthPx)
        assertEquals(100, embedded.heightPx)
    }

    @Test
    fun embedModeThumbnailDownscalesAndEncodesJpeg() {
        val baseDir = tempFolder.newFolder("output")
        val imageFile = File(baseDir, "large.png")
        createTestImage(imageFile, 1080, 1920)

        val embedded = ImageEmbedder.embed(
            imageFile = imageFile,
            relativePath = "large.png",
            mode = ReportImageMode.THUMBNAIL,
            maxSidePx = 360,
        )

        assertTrue(embedded.dataUri.startsWith("data:image/jpeg;base64,"))
        assertEquals(1080, embedded.widthPx)
        assertEquals(1920, embedded.heightPx)
        assertTrue(embedded.dataUri.length < imageFile.length() * 2)
    }

    @Test
    fun embedModeThumbnailEncodesSmallImageWithAlpha() {
        val imageFile = File(tempFolder.newFolder("output"), "alpha.png")
        ImageIO.write(BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB), "png", imageFile)

        val embedded = ImageEmbedder.embed(imageFile, "alpha.png", ReportImageMode.THUMBNAIL)

        assertTrue(embedded.dataUri.startsWith("data:image/jpeg;base64,"))
        assertEquals(40, embedded.widthPx)
    }

    @Test
    fun embedNonExistentFileThrowsIllegalStateException() {
        val baseDir = tempFolder.newFolder("output")
        val missingFile = File(baseDir, "missing.png")

        assertThrows(IllegalStateException::class.java) {
            ImageEmbedder.embed(
                imageFile = missingFile,
                relativePath = "missing.png",
                mode = ReportImageMode.THUMBNAIL,
            )
        }
    }

    private fun createTestImage(target: File, width: Int, height: Int) {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.MAGENTA
        g.fillRect(0, 0, width, height)
        g.dispose()
        ImageIO.write(img, "png", target)
    }
}
