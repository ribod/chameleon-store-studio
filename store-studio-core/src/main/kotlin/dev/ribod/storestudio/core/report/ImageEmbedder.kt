package dev.ribod.storestudio.core.report

import dev.ribod.storestudio.core.image.ImageProcessor
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Encapsulates an embedded report image asset with full-resolution reference.
 */
public data class EmbeddedImage(
    /** Data URI (base64) or relative filesystem link suitable for img src attributes. */
    public val dataUri: String,
    /** Relative filesystem path from report to uncompressed full-resolution asset. */
    public val fullResRelativePath: String,
    /** Original physical pixel width. */
    public val widthPx: Int,
    /** Original physical pixel height. */
    public val heightPx: Int,
)

/**
 * Embeds, downscales, and encodes visual assets for self-contained HTML reports.
 */
public object ImageEmbedder {

    private const val MAX_PIXELS = 100_000_000L

    /** Reads the pixel size from the image header without decoding the pixels. */
    private fun readSize(file: File): Pair<Int, Int> {
        ImageIO.createImageInputStream(file)?.use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (readers.hasNext()) {
                val reader = readers.next()
                try {
                    reader.input = stream
                    return reader.getWidth(0) to reader.getHeight(0)
                } finally {
                    reader.dispose()
                }
            }
        }
        error("Failed to decode image at ${file.path}")
    }

    /**
     * Resolves [imageFile] into an [EmbeddedImage] according to the declared [mode].
     */
    public fun embed(
        imageFile: File,
        relativePath: String,
        mode: ReportImageMode,
        maxSidePx: Int = 720,
        quality: Float = 0.85f,
    ): EmbeddedImage {
        check(imageFile.exists()) { "Image file does not exist: ${imageFile.path}" }

        val (origWidth, origHeight) = readSize(imageFile)
        check(mode == ReportImageMode.LINK || origWidth.toLong() * origHeight <= MAX_PIXELS) {
            "Image is too large to embed (${origWidth}x$origHeight): ${imageFile.path}"
        }

        val uri = when (mode) {
            ReportImageMode.LINK -> relativePath

            ReportImageMode.FULL -> {
                val bytes = imageFile.readBytes()
                val mime = if (imageFile.extension.equals("jpg", ignoreCase = true) ||
                    imageFile.extension.equals("jpeg", ignoreCase = true)
                ) {
                    "image/jpeg"
                } else {
                    "image/png"
                }
                "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
            }

            ReportImageMode.THUMBNAIL -> {
                val image = ImageIO.read(imageFile)
                    ?: error("Failed to decode image at ${imageFile.path}")
                val maxDim = max(origWidth, origHeight)
                val scale = if (maxDim > maxSidePx) maxSidePx.toDouble() / maxDim else 1.0
                val scaledW = max(1, (origWidth * scale).roundToInt())
                val scaledH = max(1, (origHeight * scale).roundToInt())
                // Always RGB: the JDK JPEG writer throws on images with an alpha channel
                val targetImage = BufferedImage(scaledW, scaledH, BufferedImage.TYPE_INT_RGB)
                val g = targetImage.createGraphics()
                try {
                    g.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                    )
                    g.setRenderingHint(
                        RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY,
                    )
                    g.color = Color.WHITE
                    g.fillRect(0, 0, scaledW, scaledH)
                    g.drawImage(image, 0, 0, scaledW, scaledH, null)
                } finally {
                    g.dispose()
                }

                val buffer = ByteArrayOutputStream()
                ImageProcessor.writeJpeg(targetImage, buffer, quality)
                "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(buffer.toByteArray())
            }
        }

        return EmbeddedImage(
            dataUri = uri,
            fullResRelativePath = relativePath,
            widthPx = origWidth,
            heightPx = origHeight,
        )
    }
}
