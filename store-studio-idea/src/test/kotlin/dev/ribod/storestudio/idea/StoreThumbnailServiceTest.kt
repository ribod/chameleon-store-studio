package dev.ribod.storestudio.idea

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ribod.storestudio.idea.io.StoreThumbnailService
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

class StoreThumbnailServiceTest : BasePlatformTestCase() {

    private lateinit var imageFile: File

    override fun setUp() {
        super.setUp()
        StoreThumbnailService.getInstance().clear()
        val dir = Files.createTempDirectory("thumbs").toFile().apply { deleteOnExit() }
        imageFile = File(dir, "shot.png")
        ImageIO.write(BufferedImage(400, 800, BufferedImage.TYPE_INT_ARGB), "png", imageFile)
    }

    fun testConcurrentRequestsShareOneDecodeAndAllCallbacksFire() {
        val service = StoreThumbnailService.getInstance()
        val delivered = mutableListOf<BufferedImage>()

        repeat(3) {
            assertNull(service.getOrLoad(imageFile, 200, 400) { delivered += it })
        }
        waitUntil { delivered.size == 3 }

        assertEquals(1, delivered.toSet().size)
        assertSame(delivered.first(), service.getOrLoad(imageFile, 200, 400) {})
    }

    fun testRequestsWithinTheSameSizeBucketReuseTheCachedThumbnail() {
        val service = StoreThumbnailService.getInstance()
        var loaded: BufferedImage? = null
        service.getOrLoad(imageFile, 195, 420) { loaded = it }
        waitUntil { loaded != null }

        assertSame(loaded, service.getOrLoad(imageFile, 200, 400) { fail("unexpected decode") })
        assertEquals(256, StoreThumbnailService.bucket(193))
        assertEquals(64, StoreThumbnailService.bucket(0))
    }

    fun testInvalidateStaleDropsThumbnailsOfChangedFiles() {
        val service = StoreThumbnailService.getInstance()
        var loaded: BufferedImage? = null
        service.getOrLoad(imageFile, 200, 400) { loaded = it }
        waitUntil { loaded != null }

        imageFile.setLastModified(imageFile.lastModified() - 60_000)
        service.invalidateStale()

        var reloaded: BufferedImage? = null
        assertNull(service.getOrLoad(imageFile, 200, 400) { reloaded = it })
        waitUntil { reloaded != null }
    }

    fun testFailedDecodeIsNotRequeuedUntilTheFileChanges() {
        val service = StoreThumbnailService.getInstance()
        val broken = File(imageFile.parentFile, "broken.png")
        broken.writeText("not a png")
        broken.setLastModified(1_000_000L)
        service.getOrLoad(broken, 200, 400) { fail("unexpected load of broken file") }
        settle()

        // Same lastModified: the failed decode is remembered, so a now-valid file is not re-queued
        ImageIO.write(BufferedImage(400, 800, BufferedImage.TYPE_INT_ARGB), "png", broken)
        broken.setLastModified(1_000_000L)
        var loaded: BufferedImage? = null
        assertNull(service.getOrLoad(broken, 200, 400) { loaded = it })
        settle()
        assertNull(loaded)

        // A changed lastModified retries the decode normally
        broken.setLastModified(2_000_000L)
        assertNull(service.getOrLoad(broken, 200, 400) { loaded = it })
        waitUntil { loaded != null }
    }

    private fun settle() {
        repeat(50) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            Thread.sleep(10)
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            assertTrue("Timed out waiting for thumbnail", System.currentTimeMillis() < deadline)
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            Thread.sleep(10)
        }
    }
}
