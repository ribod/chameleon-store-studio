package dev.ribod.storestudio.core.hash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

class AssetHasherTest {

    @Test
    fun pixelSha256IsEncoderIndependent() {
        val width = 100
        val height = 100
        val image1 = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g1 = image1.createGraphics()
        g1.color = Color.BLUE
        g1.fillRect(0, 0, width, height)
        g1.color = Color.YELLOW
        g1.fillOval(20, 20, 60, 60)
        g1.dispose()

        // Encode to PNG bytes
        val baos = ByteArrayOutputStream()
        ImageIO.write(image1, "PNG", baos)
        val pngBytes = baos.toByteArray()

        // Decode into a new BufferedImage (which might use a different internal color model/raster)
        val image2 = ImageIO.read(ByteArrayInputStream(pngBytes))

        val hash1 = AssetHasher.pixelSha256(image1)
        val hash2 = AssetHasher.pixelSha256(image2)

        assertEquals(
            "Decoded raster pixelSha256 must match pre-encoded raster exactly",
            hash1,
            hash2,
        )
        assertEquals(64, hash1.length)
    }

    @Test
    fun pixelSha256DetectsSinglePixelDeviation() {
        val width = 50
        val height = 50
        val img1 = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val img2 = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        // Make identical blue canvas
        for (y in 0 until height) {
            for (x in 0 until width) {
                img1.setRGB(x, y, 0xFF0000FF.toInt())
                img2.setRGB(x, y, 0xFF0000FF.toInt())
            }
        }

        // Tweak exactly 1 pixel in img2
        img2.setRGB(25, 25, 0xFF0000FE.toInt())

        val hash1 = AssetHasher.pixelSha256(img1)
        val hash2 = AssetHasher.pixelSha256(img2)

        assertNotEquals(
            "Cryptographic pixelSha256 must detect single pixel modification",
            hash1,
            hash2,
        )
    }

    @Test
    fun pHashIdenticalImagesDistanceIsZero() {
        val image = BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.GREEN
        g.fillRect(0, 0, 128, 128)
        g.color = Color.RED
        g.fillRect(30, 30, 60, 60)
        g.dispose()

        val pHash1 = AssetHasher.pHash(image)
        val pHash2 = AssetHasher.pHash(image)

        assertEquals(16, pHash1.length)
        assertEquals(pHash1, pHash2)
        assertEquals(0.0, AssetHasher.hammingDistance(pHash1, pHash2), 0.0001)
    }

    @Test
    fun pHashAbsorbsSubtleAntiAliasingPerturbations() {
        val imageA = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)
        val gA = imageA.createGraphics()
        gA.color = Color.WHITE
        gA.fillRect(0, 0, 256, 256)
        gA.color = Color.BLACK
        gA.fillRect(50, 50, 150, 150)
        gA.dispose()

        // Clone and slightly perturb a few edge pixels (simulating subpixel AA drift)
        val imageB = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)
        val gB = imageB.createGraphics()
        gB.drawImage(imageA, 0, 0, null)
        // Add subtle gray pixels along the edge
        for (i in 0 until 10) {
            imageB.setRGB(50 + i, 49, 0xFFE0E0E0.toInt())
        }
        gB.dispose()

        val hashA = AssetHasher.pHash(imageA)
        val hashB = AssetHasher.pHash(imageB)
        val distance = AssetHasher.hammingDistance(hashA, hashB)

        assertTrue(
            "Perceptual distance for subtle AA edge shift must be <= 0.05 (was $distance)",
            distance <= 0.05,
        )
    }

    @Test
    fun pHashDetectsSignificantVisualAlterations() {
        // Vertical stripe layout
        val imgV = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)
        val gV = imgV.createGraphics()
        gV.color = Color.WHITE
        gV.fillRect(0, 0, 256, 256)
        gV.color = Color.BLACK
        gV.fillRect(100, 0, 56, 256)
        gV.dispose()

        // Horizontal stripe layout
        val imgH = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)
        val gH = imgH.createGraphics()
        gH.color = Color.WHITE
        gH.fillRect(0, 0, 256, 256)
        gH.color = Color.BLACK
        gH.fillRect(0, 100, 256, 56)
        gH.dispose()

        val hashV = AssetHasher.pHash(imgV)
        val hashH = AssetHasher.pHash(imgH)
        val distance = AssetHasher.hammingDistance(hashV, hashH)

        assertTrue(
            "Perceptual distance for orthogonal layout change must exceed 0.10 (was $distance)",
            distance >= 0.10,
        )
    }

    @Test
    fun fileHashingProducesExpectedChecksums() {
        val sample = "The quick brown fox jumps over the lazy dog".toByteArray(Charsets.UTF_8)

        // Known SHA-256 and MD5 for "The quick brown fox jumps over the lazy dog"
        val expectedSha256 = "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592"
        val expectedMd5 = "9e107d9d372bb6826bd81d3542a419d6"

        assertEquals(expectedSha256, AssetHasher.fileSha256(sample))
        assertEquals(expectedMd5, AssetHasher.md5(sample))

        val tempFile = File.createTempFile("hasher-test", ".txt")
        try {
            tempFile.writeBytes(sample)
            assertEquals(expectedSha256, AssetHasher.fileSha256(tempFile))
            assertEquals(expectedMd5, AssetHasher.md5(tempFile))
        } finally {
            tempFile.delete()
        }
    }
}
