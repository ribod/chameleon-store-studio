package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.image.ImageProcessor
import dev.ribod.storestudio.core.image.SizeMismatchPolicy
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.ExportLayout
import dev.ribod.storestudio.model.RenderIndex
import dev.ribod.storestudio.model.RenderJobResult
import dev.ribod.storestudio.model.StoreDeviceSlot
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class StoreExportTaskTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun exportsAlphaFlattenedPngWithDenseOrderingAndHashes() {
        val projectDir = tempDir.newFolder("project")
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()

        val rawImagesDir = tempDir.newFolder("raw")
        val heroRaw = File(rawImagesDir, "hero.png")
        // Create an ARGB raw image with 50% transparency
        val buffered = BufferedImage(1080, 1920, BufferedImage.TYPE_INT_ARGB)
        buffered.setRGB(0, 0, (128 shl 24) or (255 shl 16))
        ImageIO.write(buffered, "png", heroRaw)

        val renderIndexFile = tempDir.newFile("render_index.json")
        val renderIndex = RenderIndex(
            layoutlibVersion = "16.2.1",
            renderedAt = "2026-10-12T20:00:00Z",
            jobs = listOf(
                RenderJobResult(
                    jobId = "hero__PLAY_STORE__PHONE__en-US",
                    slotKey = "PLAY_STORE/PHONE/en-US/hero",
                    entryId = "hero",
                    target = StoreTarget.PLAY_STORE,
                    deviceSlot = StoreDeviceSlot.PHONE,
                    storeLocale = "en-US",
                    localeTag = "en-US",
                    widthPx = 1080,
                    heightPx = 1920,
                    dpi = 480,
                    engine = "LAYOUTLIB",
                    status = "SUCCESS",
                    image = "hero.png",
                ),
            ),
        )
        renderIndexFile.writeText(RenderIndex.encodeToString(renderIndex))

        val exportDir = tempDir.newFolder("export")
        val exportIndexFile = File(exportDir, "index.json")

        val task = project.tasks.register("testExport", StoreExportTask::class.java).get()
        task.renderIndex.set(renderIndexFile)
        task.rawImagesDir.set(rawImagesDir)
        task.backgroundColor.set("#FFFFFF")
        task.sizeTolerancePx.set(2)
        task.sizeMismatchPolicy.set(SizeMismatchPolicy.CROP_OR_PAD)
        task.jpegFallback.set(true)
        task.jpegQuality.set(0.92f)
        task.layout.set(ExportLayout.STORE_LOCALE_DEVICE)
        task.lowercaseFolders.set(true)
        task.exportDirectory.set(exportDir)
        task.exportIndexFile.set(exportIndexFile)

        task.export()

        assertTrue(exportIndexFile.exists())
        val generatedIndex = ExportIndex.fromFile(exportIndexFile)
        assertEquals(1, generatedIndex.assets.size)

        val asset = generatedIndex.assets.first()
        assertEquals("hero", asset.id)
        assertEquals(1, asset.position)
        assertEquals(1080, asset.widthPx)
        assertEquals(1920, asset.heightPx)
        assertFalse(asset.hasAlpha)
        assertEquals("sRGB", asset.colorSpace)
        assertNotNull(asset.pixelSha256)
        assertNotNull(asset.fileSha256)
        assertNotNull(asset.pHash)
        assertNotNull(asset.md5)

        assertEquals("play_store/en-US/phone/01_hero.png", asset.file)
        val exportedPng = File(exportDir, asset.file)
        assertTrue(exportedPng.exists())
        val colorType = ImageProcessor.readPngColorType(exportedPng)
        // Strictly Truecolor RGB without alpha channel
        assertEquals(2, colorType)
    }
}
