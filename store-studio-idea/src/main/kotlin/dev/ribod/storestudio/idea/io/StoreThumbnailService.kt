package dev.ribod.storestudio.idea.io

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Memory-bounded, deduplicating thumbnail cache for Store Studio.
 *
 * Paint code may ask for the same thumbnail on every frame; each (file, size bucket) is decoded
 * at most once at a time on a small bounded pool, and the cache is capped by total pixels rather
 * than entry count so large iPad/feature-graphic assets cannot exhaust the IDE heap.
 */
@Service(Service.Level.APP)
public class StoreThumbnailService : Disposable {

    private class Entry(val image: BufferedImage, val lastModified: Long) {
        val pixels: Long get() = image.width.toLong() * image.height
    }

    private val lock = Any()
    private val cache = LinkedHashMap<String, Entry>(64, 0.75f, true)
    private var cachedPixels = 0L

    // A set: paint code passes its stored callback on every frame while a decode is pending
    private val pending = HashMap<String, MutableSet<(BufferedImage) -> Unit>>()

    // Keys whose decode failed, mapped to the file's lastModified; avoids re-decoding a broken file on every repaint
    private val failed = HashMap<String, Long>()

    private val decoder = AppExecutorUtil.createBoundedApplicationPoolExecutor(
        "Store Studio Thumbnails",
        MAX_PARALLEL_DECODES,
    )

    @Volatile
    private var disposed = false

    /**
     * Returns a cached thumbnail that fits [targetWidth]x[targetHeight], or `null` while it is being
     * decoded off the EDT; [onLoaded] is then invoked on the EDT once it is available.
     *
     * Requested sizes are rounded up to a bucket so zooming does not trigger a decode per step.
     * While a new size is loading, a previously cached size of the same file is returned instead.
     */
    public fun getOrLoad(
        file: File,
        targetWidth: Int,
        targetHeight: Int,
        onLoaded: (BufferedImage) -> Unit,
    ): BufferedImage? {
        val bucketW = bucket(targetWidth)
        val bucketH = bucket(targetHeight)
        val key = "${file.path}|${bucketW}x$bucketH"

        synchronized(lock) {
            cache[key]?.let { return it.image }
            failed[key]?.let { if (it == file.lastModified()) return closestCached(file) }
            val callbacks = pending[key]
            if (callbacks != null) {
                callbacks.add(onLoaded)
                return closestCached(file)
            }
            pending[key] = linkedSetOf(onLoaded)
        }

        decoder.execute { decode(file, key, bucketW, bucketH) }
        return synchronized(lock) { closestCached(file) }
    }

    /**
     * Drops cached thumbnails whose source file changed or disappeared, so rebuilt assets written to
     * the same path are re-decoded. Performs file I/O, call it off the EDT.
     */
    public fun invalidateStale() {
        val snapshot = synchronized(lock) { cache.entries.map { it.key to it.value.lastModified } }
        val stale = snapshot.filter { (key, lastModified) ->
            File(key.substringBeforeLast('|')).lastModified() != lastModified
        }
        if (stale.isEmpty()) return
        synchronized(lock) {
            stale.forEach { (key, _) -> cache.remove(key)?.let { cachedPixels -= it.pixels } }
        }
    }

    /** Clears all cached thumbnails. */
    public fun clear() {
        synchronized(lock) {
            cache.clear()
            cachedPixels = 0
        }
    }

    override fun dispose() {
        disposed = true
        clear()
    }

    private fun decode(file: File, key: String, maxW: Int, maxH: Int) {
        val lastModified = file.lastModified()
        val scaled = if (disposed || !file.isFile) {
            null
        } else {
            try {
                readSubsampled(file, maxW, maxH)?.let { scaleProportionally(it, maxW, maxH) }
            } catch (e: Exception) {
                // Files can be read while Gradle is still writing them; the next reload retries
                thisLogger().debug("Could not decode thumbnail ${file.path}", e)
                null
            }
        }

        val callbacks = synchronized(lock) {
            if (scaled != null && !disposed) put(key, Entry(scaled, lastModified))
            if (scaled == null && !disposed) failed[key] = lastModified
            pending.remove(key).orEmpty()
        }
        if (scaled == null || callbacks.isEmpty()) return

        ApplicationManager.getApplication().invokeLater(
            { callbacks.forEach { it(scaled) } },
            ModalityState.any(),
        )
    }

    /**
     * Decodes [file] at a reduced resolution when it is much larger than the requested size, so a
     * large iPad or feature-graphic asset does not allocate its full raster for a small thumbnail.
     * At least twice the requested size is kept for the final bilinear downscale.
     */
    private fun readSubsampled(file: File, maxW: Int, maxH: Int): BufferedImage? =
        ImageIO.createImageInputStream(file)?.use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull()
                ?: return@use null
            try {
                reader.input = input
                val ratio = minOf(reader.getWidth(0) / maxW, reader.getHeight(0) / maxH)
                val step = (ratio / 2).coerceAtLeast(1)
                val param = reader.defaultReadParam
                if (step > 1) param.setSourceSubsampling(step, step, 0, 0)
                reader.read(0, param)
            } finally {
                reader.dispose()
            }
        }

    private fun put(key: String, entry: Entry) {
        cache.remove(key)?.let { cachedPixels -= it.pixels }
        cache[key] = entry
        cachedPixels += entry.pixels
        val iterator = cache.values.iterator()
        while (cachedPixels > MAX_CACHED_PIXELS && cache.size > 1 && iterator.hasNext()) {
            cachedPixels -= iterator.next().pixels
            iterator.remove()
        }
    }

    private fun closestCached(file: File): BufferedImage? {
        val prefix = "${file.path}|"
        return cache.entries.lastOrNull { it.key.startsWith(prefix) }?.value?.image
    }

    private fun scaleProportionally(src: BufferedImage, maxW: Int, maxH: Int): BufferedImage {
        val srcW = src.width
        val srcH = src.height
        if (srcW <= 0 || srcH <= 0) return src

        val ratioW = maxW.toDouble() / srcW
        val ratioH = maxH.toDouble() / srcH
        val scale = minOf(ratioW, ratioH).coerceAtMost(1.0)

        val destW = (srcW * scale).toInt().coerceAtLeast(1)
        val destH = (srcH * scale).toInt().coerceAtLeast(1)

        val dest = BufferedImage(destW, destH, BufferedImage.TYPE_INT_ARGB)
        val g2 = dest.createGraphics()
        try {
            g2.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.drawImage(src, 0, 0, destW, destH, null)
        } finally {
            g2.dispose()
        }
        return dest
    }

    public companion object {
        /** Upper bound for cached thumbnail pixels (~128 MB as ARGB). */
        private const val MAX_CACHED_PIXELS = 32L * 1024 * 1024
        private const val MAX_PARALLEL_DECODES = 2
        private const val SIZE_BUCKET_PX = 64

        public fun getInstance(): StoreThumbnailService = service()

        /** Rounds a requested dimension up to the next multiple of [SIZE_BUCKET_PX]. */
        internal fun bucket(size: Int): Int {
            val clamped = size.coerceAtLeast(1)
            return ((clamped + SIZE_BUCKET_PX - 1) / SIZE_BUCKET_PX) * SIZE_BUCKET_PX
        }
    }
}
