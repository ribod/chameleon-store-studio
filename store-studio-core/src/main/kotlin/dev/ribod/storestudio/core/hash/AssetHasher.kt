package dev.ribod.storestudio.core.hash

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * High-performance, deterministic cryptographic and perceptual hashing engine for visual assets.
 *
 * Implements the baseline comparison and drift detection primitives specified in DD-09:
 * 1. [pixelSha256]: Canonical decoded RGBA8888 byte buffer SHA-256 (strictly independent of
 *    PNG compression level, metadata chunks, or operating system encoder differences).
 * 2. [fileSha256]: Cryptographic SHA-256 over raw file bytes (used for artifact cache keys).
 * 3. [md5]: MD5 checksum for binary integrity and App Store Connect checksum verification.
 * 4. [pHash]: 64-bit Discrete Cosine Transform (DCT) Perceptual Hash on 32x32 grayscale downscale.
 * 5. [hammingDistance]: Normalized distance (`[0.0, 1.0]`) between 64-bit perceptual hashes.
 */
public object AssetHasher {

    private const val DCT_SIZE = 32
    private const val LOW_FREQ_SIZE = 8

    // Precomputed cosine factors for DCT-II to eliminate runtime trigonometric overhead
    private val COS_TABLE: Array<DoubleArray> = Array(LOW_FREQ_SIZE) { freq ->
        DoubleArray(DCT_SIZE) { n ->
            cos((2 * n + 1) * freq * PI / (2.0 * DCT_SIZE))
        }
    }

    private val ALPHA: DoubleArray = DoubleArray(LOW_FREQ_SIZE) { k ->
        if (k == 0) 1.0 / sqrt(DCT_SIZE.toDouble()) else sqrt(2.0 / DCT_SIZE.toDouble())
    }

    /**
     * Computes the canonical SHA-256 over raw decoded image pixels in row-major order:
     * `width(4B BE) || height(4B BE) || RGBA8888 pixel bytes`.
     *
     * This hash is encoder-independent, meaning two PNG files with identical rendered pixels
     * but different compression levels or ancillary chunks produce identical hashes.
     *
     * @param image Decoded raster image to hash.
     * @return 64-character lowercase hexadecimal SHA-256 hash.
     */
    public fun pixelSha256(image: BufferedImage): String {
        val md = MessageDigest.getInstance("SHA-256")

        // 8-byte big-endian header: width (4 bytes) + height (4 bytes)
        val header = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        header.putInt(image.width)
        header.putInt(image.height)
        md.update(header.array())

        val width = image.width
        val height = image.height

        val canonical = if (image.type == BufferedImage.TYPE_INT_ARGB) {
            image
        } else {
            BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
                val g = createGraphics()
                try {
                    g.drawImage(image, 0, 0, null)
                } finally {
                    g.dispose()
                }
            }
        }

        // Buffer row-by-row to optimize memory and throughput
        val rowBuffer = ByteBuffer.allocate(width * 4)
        val rowPixels = IntArray(width)

        for (y in 0 until height) {
            canonical.getRGB(0, y, width, 1, rowPixels, 0, width)
            rowBuffer.clear()
            for (x in 0 until width) {
                val argb = rowPixels[x]
                val a = (argb ushr 24) and 0xFF
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                rowBuffer.put(r.toByte())
                rowBuffer.put(g.toByte())
                rowBuffer.put(b.toByte())
                rowBuffer.put(a.toByte())
            }
            md.update(rowBuffer.array())
        }

        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes the SHA-256 checksum over the raw bytes of the given [file].
     */
    public fun fileSha256(file: File): String = file.inputStream().use { fileSha256(it) }

    /**
     * Computes the SHA-256 checksum over bytes from [inputStream].
     */
    public fun fileSha256(inputStream: InputStream): String = digest(inputStream, "SHA-256")

    /**
     * Computes the SHA-256 checksum over the given [bytes].
     */
    public fun fileSha256(bytes: ByteArray): String = digest(bytes, "SHA-256")

    /**
     * Computes the MD5 checksum over the raw bytes of the given [file].
     */
    public fun md5(file: File): String = file.inputStream().use { md5(it) }

    /**
     * Computes the MD5 checksum over bytes from [inputStream].
     */
    public fun md5(inputStream: InputStream): String = digest(inputStream, "MD5")

    /**
     * Computes the MD5 checksum over the given [bytes].
     */
    public fun md5(bytes: ByteArray): String = digest(bytes, "MD5")

    /**
     * Computes the 64-bit Discrete Cosine Transform (DCT) Perceptual Hash ([pHash]) of [image].
     *
     * Algorithm steps per DD-09:
     * 1. Resizes image to 32x32 pixels in 8-bit grayscale using bilinear interpolation.
     * 2. Computes 2D DCT for the top-left 8x8 low-frequency coefficients.
     * 3. Calculates the median value among the 63 AC coefficients (excluding the DC component at 0,0).
     * 4. Generates 64 bits where bit is 1 if coefficient > median, else 0.
     *
     * @param image Visual asset to hash.
     * @return 16-character lowercase hexadecimal string representing the 64-bit hash.
     */
    public fun pHash(image: BufferedImage): String =
        pHashValue(image).toString(16).padStart(16, '0')

    /**
     * Computes the raw 64-bit unsigned integer perceptual hash of [image].
     */
    public fun pHashValue(image: BufferedImage): ULong {
        // Step 1: Grayscale downscale to 32x32
        val resized = BufferedImage(DCT_SIZE, DCT_SIZE, BufferedImage.TYPE_BYTE_GRAY)
        val g = resized.createGraphics()
        try {
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            g.drawImage(image, 0, 0, DCT_SIZE, DCT_SIZE, null)
        } finally {
            g.dispose()
        }

        // Step 2: Extract luminance values
        val matrix = Array(DCT_SIZE) { DoubleArray(DCT_SIZE) }
        val raster = resized.raster
        for (y in 0 until DCT_SIZE) {
            for (x in 0 until DCT_SIZE) {
                matrix[y][x] = raster.getSample(x, y, 0).toDouble()
            }
        }

        // Step 3: Compute separable 2D DCT for top-left 8x8 low frequencies
        val rowDct = Array(DCT_SIZE) { DoubleArray(LOW_FREQ_SIZE) }
        for (y in 0 until DCT_SIZE) {
            for (u in 0 until LOW_FREQ_SIZE) {
                var sum = 0.0
                val cosU = COS_TABLE[u]
                for (x in 0 until DCT_SIZE) {
                    sum += matrix[y][x] * cosU[x]
                }
                rowDct[y][u] = sum
            }
        }

        val dct = Array(LOW_FREQ_SIZE) { DoubleArray(LOW_FREQ_SIZE) }
        for (v in 0 until LOW_FREQ_SIZE) {
            val cosV = COS_TABLE[v]
            for (u in 0 until LOW_FREQ_SIZE) {
                var sum = 0.0
                for (y in 0 until DCT_SIZE) {
                    sum += rowDct[y][u] * cosV[y]
                }
                dct[v][u] = sum * ALPHA[u] * ALPHA[v]
            }
        }

        // Step 4: Extract 63 AC coefficients (excluding DC at 0,0) and compute median
        val acCoefficients = DoubleArray(LOW_FREQ_SIZE * LOW_FREQ_SIZE - 1)
        var acIndex = 0
        for (v in 0 until LOW_FREQ_SIZE) {
            for (u in 0 until LOW_FREQ_SIZE) {
                if (v != 0 || u != 0) {
                    acCoefficients[acIndex++] = dct[v][u]
                }
            }
        }
        acCoefficients.sort()
        val median = acCoefficients[acCoefficients.size / 2]

        // Step 5: Bit generation across all 64 coefficients in row-major order
        var hash = 0UL
        var bitIndex = 0
        for (v in 0 until LOW_FREQ_SIZE) {
            for (u in 0 until LOW_FREQ_SIZE) {
                if (dct[v][u] > median) {
                    hash = hash or (1UL shl (63 - bitIndex))
                }
                bitIndex++
            }
        }

        return hash
    }

    /**
     * Calculates the normalized Hamming distance between two 64-bit perceptual hashes.
     *
     * Value ranges from `0.0` (identical) to `1.0` (all 64 bits differ):
     * - `0.0`: Exact visual perceptual match.
     * - `0.01..0.04`: Minor font anti-aliasing or subpixel rasterization shifts.
     * - `>= 0.05`: Noticeable visual or layout drift.
     * - `>= 0.10`: Significant graphical change.
     */
    public fun hammingDistance(h1: ULong, h2: ULong): Double {
        val xor = (h1 xor h2).toLong()
        return java.lang.Long.bitCount(xor) / 64.0
    }

    /**
     * Calculates the normalized Hamming distance between two 16-character hexadecimal pHash strings.
     */
    public fun hammingDistance(h1: String, h2: String): Double {
        require(h1.length == 16) { "pHash string must be 16 hexadecimal characters: '$h1'" }
        require(h2.length == 16) { "pHash string must be 16 hexadecimal characters: '$h2'" }
        val u1 = h1.toULong(radix = 16)
        val u2 = h2.toULong(radix = 16)
        return hammingDistance(u1, u2)
    }

    private fun digest(inputStream: InputStream, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        val buffer = ByteArray(8192)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            md.update(buffer, 0, read)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun digest(bytes: ByteArray, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
