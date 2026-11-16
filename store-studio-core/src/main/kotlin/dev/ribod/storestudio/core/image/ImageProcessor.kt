package dev.ribod.storestudio.core.image

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.OutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.metadata.IIOMetadataNode
import kotlin.math.abs

/**
 * Result of flattening image transparency onto an opaque background.
 */
public data class FlattenResult(
    public val image: BufferedImage,
    public val transparentPixelPercentage: Double,
)

/**
 * Result of sizing verification and crop/pad adjustment.
 */
public data class SizeAdjustResult(
    public val image: BufferedImage,
    public val adjusted: Boolean,
    public val deltaWidth: Int,
    public val deltaHeight: Int,
)

/**
 * Bit-exact, deterministic image transformation and encoding engine for app store compliance.
 *
 * Enforces store acceptance rules:
 * - Alpha channel stripping onto opaque backgrounds producing 24-bit RGB rasters.
 * - Symmetrical crop and pad bounded by physical pixel tolerance.
 * - Truecolor PNG encoding with sRGB metadata chunks and no timestamp chunks.
 * - Controlled JPEG fallback encoding with configurable quality levels.
 */
public object ImageProcessor {

    private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

    /**
     * Composites [src] over an opaque [backgroundRgb] into a `TYPE_INT_RGB` raster.
     *
     * Computes straight alpha source-over compositing row-by-row to guarantee identical
     * bit-exact output across all operating systems and Java virtual machine implementations.
     */
    public fun flatten(src: BufferedImage, backgroundRgb: Int): FlattenResult {
        val width = src.width
        val height = src.height
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        val bgR = (backgroundRgb ushr 16) and 0xFF
        val bgG = (backgroundRgb ushr 8) and 0xFF
        val bgB = backgroundRgb and 0xFF

        val row = IntArray(width)
        val outRow = IntArray(width)
        var transparentPixels = 0L
        val totalPixels = width.toLong() * height.toLong()

        for (y in 0 until height) {
            src.getRGB(0, y, width, 1, row, 0, width)
            for (x in 0 until width) {
                val pixel = row[x]
                val alpha = (pixel ushr 24) and 0xFF

                if (alpha < 255) {
                    transparentPixels++
                }

                if (alpha == 255) {
                    outRow[x] = pixel and 0xFFFFFF
                } else {
                    val invAlpha = 255 - alpha
                    val r = (((pixel ushr 16) and 0xFF) * alpha + bgR * invAlpha + 127) / 255
                    val g = (((pixel ushr 8) and 0xFF) * alpha + bgG * invAlpha + 127) / 255
                    val b = ((pixel and 0xFF) * alpha + bgB * invAlpha + 127) / 255
                    outRow[x] = (r shl 16) or (g shl 8) or b
                }
            }
            out.setRGB(0, y, width, 1, outRow, 0, width)
        }

        val percentage = if (totalPixels > 0) {
            (transparentPixels.toDouble() / totalPixels.toDouble()) * 100.0
        } else {
            0.0
        }

        return FlattenResult(out, percentage)
    }

    /**
     * Inspects dimensions against catalog specs and executes symmetrical crop or pad adjustments.
     */
    public fun adjustSize(
        src: BufferedImage,
        expectedWidth: Int,
        expectedHeight: Int,
        tolerancePx: Int,
        policy: SizeMismatchPolicy,
        backgroundRgb: Int,
    ): SizeAdjustResult {
        val currentW = src.width
        val currentH = src.height
        val deltaW = currentW - expectedWidth
        val deltaH = currentH - expectedHeight

        if (deltaW == 0 && deltaH == 0) {
            return SizeAdjustResult(src, adjusted = false, 0, 0)
        }

        if (abs(deltaW) > tolerancePx || abs(deltaH) > tolerancePx) {
            throw IllegalArgumentException(
                "Rendered ${currentW}x$currentH deviates from expected " +
                    "${expectedWidth}x$expectedHeight beyond tolerance $tolerancePx px " +
                    "(delta: ${deltaW}x$deltaH)",
            )
        }

        if (policy == SizeMismatchPolicy.FAIL) {
            throw IllegalArgumentException(
                "Rendered ${currentW}x$currentH does not match expected " +
                    "${expectedWidth}x$expectedHeight exactly and sizeMismatchPolicy is FAIL",
            )
        }

        val targetType = if (src.type == BufferedImage.TYPE_INT_RGB) {
            BufferedImage.TYPE_INT_RGB
        } else {
            BufferedImage.TYPE_INT_ARGB
        }

        val out = BufferedImage(expectedWidth, expectedHeight, targetType)
        val graphics = out.createGraphics()
        try {
            if (targetType == BufferedImage.TYPE_INT_RGB) {
                graphics.color = Color(backgroundRgb)
                graphics.fillRect(0, 0, expectedWidth, expectedHeight)
            }

            val cropX = if (deltaW > 0) deltaW / 2 else 0
            val cropY = if (deltaH > 0) deltaH / 2 else 0
            val padX = if (deltaW < 0) (-deltaW) / 2 else 0
            val padY = if (deltaH < 0) (-deltaH) / 2 else 0

            val drawW = minOf(currentW - cropX, expectedWidth - padX)
            val drawH = minOf(currentH - cropY, expectedHeight - padY)

            val sub = src.getSubimage(cropX, cropY, drawW, drawH)
            graphics.drawImage(sub, padX, padY, null)
        } finally {
            graphics.dispose()
        }

        return SizeAdjustResult(out, adjusted = true, deltaW, deltaH)
    }

    /**
     * Encodes [image] as a 24-bit Truecolor PNG without alpha into [out].
     */
    public fun writePng24(image: BufferedImage, out: OutputStream) {
        require(image.type == BufferedImage.TYPE_INT_RGB) {
            "Image must be BufferedImage.TYPE_INT_RGB to write 24-bit PNG"
        }

        val writers = ImageIO.getImageWritersByFormatName("png")
        check(writers.hasNext()) { "No PNG ImageWriter registered in ImageIO" }
        val writer = writers.next()
        try {
            val param = writer.defaultWriteParam
            if (param.canWriteCompressed()) {
                param.compressionMode = ImageWriteParam.MODE_EXPLICIT
                param.compressionQuality = 0.0f
            }

            val typeSpecifier = ImageTypeSpecifier.createFromRenderedImage(image)
            val metadata = writer.getDefaultImageMetadata(typeSpecifier, param)

            runCatching {
                val root = IIOMetadataNode("javax_imageio_png_1.0")
                val srgb = IIOMetadataNode("sRGB").apply {
                    setAttribute("renderingIntent", "Perceptual")
                }
                root.appendChild(srgb)
                metadata.mergeTree("javax_imageio_png_1.0", root)
            }

            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                writer.write(null, IIOImage(image, null, metadata), param)
            }
        } finally {
            writer.dispose()
        }
    }

    /**
     * Encodes [image] as a JPEG container with specified compression [quality].
     */
    public fun writeJpeg(image: BufferedImage, out: OutputStream, quality: Float) {
        val writers = ImageIO.getImageWritersByFormatName("jpeg")
        check(writers.hasNext()) { "No JPEG ImageWriter registered in ImageIO" }
        val writer = writers.next()
        try {
            val param = writer.defaultWriteParam
            param.compressionMode = ImageWriteParam.MODE_EXPLICIT
            param.compressionQuality = quality.coerceIn(0.0f, 1.0f)

            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }
    }

    /**
     * Reads the PNG color type field directly from the IHDR chunk without decoding pixels.
     *
     * Value mappings:
     * - 0: Grayscale
     * - 2: Truecolor RGB (24-bit)
     * - 3: Indexed color
     * - 4: Grayscale with alpha
     * - 6: Truecolor RGBA (32-bit)
     */
    public fun readPngColorType(file: File): Int = file.inputStream().use { stream ->
        val header = ByteArray(26)
        val read = stream.read(header)
        check(read == 26) { "File is too small to be a valid PNG: ${file.name}" }

        val signature = byteArrayOf(
            0x89.toByte(),
            0x50.toByte(),
            0x4E.toByte(),
            0x47.toByte(),
            0x0D.toByte(),
            0x0A.toByte(),
            0x1A.toByte(),
            0x0A.toByte(),
        )
        for (i in signature.indices) {
            check(header[i] == signature[i]) { "Invalid PNG magic signature in ${file.name}" }
        }

        check(
            header[12] == 'I'.code.toByte() &&
                header[13] == 'H'.code.toByte() &&
                header[14] == 'D'.code.toByte() &&
                header[15] == 'R'.code.toByte(),
        ) { "Missing mandatory IHDR chunk in PNG: ${file.name}" }

        header[25].toInt() and 0xFF
    }

    /**
     * Parses a 6-character hex color string into a 24-bit RGB integer.
     */
    public fun parseHexColor(hex: String): Int {
        require(hex.matches(HEX_COLOR)) {
            "Background color must be formatted as '#RRGGBB' with no alpha channel, but was '$hex'"
        }
        return hex.substring(1).toInt(16)
    }
}
