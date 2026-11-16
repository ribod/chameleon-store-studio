package dev.ribod.storestudio.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File

class ImageProcessorTest {

    @Test
    fun flattenCompositesSemiTransparentPixelsOntoBackground() {
        val src = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        // Red with 50% opacity (alpha = 128)
        src.setRGB(0, 0, (128 shl 24) or (255 shl 16))
        // Fully opaque green (alpha = 255)
        src.setRGB(1, 0, (255 shl 24) or (255 shl 8))
        // Fully transparent (alpha = 0)
        src.setRGB(0, 1, 0x00000000)
        // Opaque blue (alpha = 255)
        src.setRGB(1, 1, (255 shl 24) or 255)

        // Flatten onto opaque white (0xFFFFFF)
        val result = ImageProcessor.flatten(src, 0xFFFFFF)
        val out = result.image

        assertEquals(BufferedImage.TYPE_INT_RGB, out.type)
        assertEquals(50.0, result.transparentPixelPercentage, 0.01)

        val opaqueGreen = out.getRGB(1, 0) and 0xFFFFFF
        assertEquals(0x00FF00, opaqueGreen)

        val transparentWhite = out.getRGB(0, 1) and 0xFFFFFF
        assertEquals(0xFFFFFF, transparentWhite)

        val semiRed = out.getRGB(0, 0) and 0xFFFFFF
        val r = (semiRed ushr 16) and 0xFF
        val g = (semiRed ushr 8) and 0xFF
        val b = semiRed and 0xFF
        // Straight alpha source-over blend of red (255,0,0) with white (255,255,255) at 128/255
        assertEquals(255, r)
        assertTrue(g in 126..128)
        assertTrue(b in 126..128)
    }

    @Test
    fun adjustSizeReturnsOriginalWhenDimensionsMatch() {
        val src = BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB)
        val result = ImageProcessor.adjustSize(
            src = src,
            expectedWidth = 100,
            expectedHeight = 200,
            tolerancePx = 2,
            policy = SizeMismatchPolicy.CROP_OR_PAD,
            backgroundRgb = 0xFFFFFF,
        )

        assertFalse(result.adjusted)
        assertEquals(src, result.image)
    }

    @Test
    fun adjustSizePerformsSymmetricalCropWithinTolerance() {
        val src = BufferedImage(102, 202, BufferedImage.TYPE_INT_RGB)
        val result = ImageProcessor.adjustSize(
            src = src,
            expectedWidth = 100,
            expectedHeight = 200,
            tolerancePx = 2,
            policy = SizeMismatchPolicy.CROP_OR_PAD,
            backgroundRgb = 0xFFFFFF,
        )

        assertTrue(result.adjusted)
        assertEquals(100, result.image.width)
        assertEquals(200, result.image.height)
        assertEquals(2, result.deltaWidth)
        assertEquals(2, result.deltaHeight)
    }

    @Test
    fun adjustSizePerformsSymmetricalPadWithinTolerance() {
        val src = BufferedImage(99, 199, BufferedImage.TYPE_INT_RGB)
        val result = ImageProcessor.adjustSize(
            src = src,
            expectedWidth = 100,
            expectedHeight = 200,
            tolerancePx = 2,
            policy = SizeMismatchPolicy.CROP_OR_PAD,
            backgroundRgb = 0xFFFFFF,
        )

        assertTrue(result.adjusted)
        assertEquals(100, result.image.width)
        assertEquals(200, result.image.height)
        assertEquals(-1, result.deltaWidth)
        assertEquals(-1, result.deltaHeight)
    }

    @Test
    fun adjustSizeThrowsWhenDeltaExceedsTolerance() {
        val src = BufferedImage(105, 200, BufferedImage.TYPE_INT_RGB)
        assertThrows(IllegalArgumentException::class.java) {
            ImageProcessor.adjustSize(
                src = src,
                expectedWidth = 100,
                expectedHeight = 200,
                tolerancePx = 2,
                policy = SizeMismatchPolicy.CROP_OR_PAD,
                backgroundRgb = 0xFFFFFF,
            )
        }
    }

    @Test
    fun adjustSizeThrowsWhenPolicyIsFailAndDeviationExists() {
        val src = BufferedImage(101, 200, BufferedImage.TYPE_INT_RGB)
        assertThrows(IllegalArgumentException::class.java) {
            ImageProcessor.adjustSize(
                src = src,
                expectedWidth = 100,
                expectedHeight = 200,
                tolerancePx = 2,
                policy = SizeMismatchPolicy.FAIL,
                backgroundRgb = 0xFFFFFF,
            )
        }
    }

    @Test
    fun writePng24EncodesType2ColorFormat() {
        val image = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
        val tempFile = File.createTempFile("test_export", ".png")
        try {
            tempFile.outputStream().use { out ->
                ImageProcessor.writePng24(image, out)
            }

            val colorType = ImageProcessor.readPngColorType(tempFile)
            // Color type 2 is Truecolor RGB without alpha
            assertEquals(2, colorType)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun parseHexColorParsesOpaqueColorSuccessfully() {
        assertEquals(0xFFFFFF, ImageProcessor.parseHexColor("#FFFFFF"))
        assertEquals(0x123456, ImageProcessor.parseHexColor("#123456"))
        assertEquals(0x000000, ImageProcessor.parseHexColor("#000000"))

        assertThrows(IllegalArgumentException::class.java) {
            ImageProcessor.parseHexColor("#80FFFFFF")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImageProcessor.parseHexColor("red")
        }
    }
}
