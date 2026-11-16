package dev.ribod.storestudio.core.report

import dev.ribod.storestudio.model.DiffChange
import dev.ribod.storestudio.model.DiffChangeType
import dev.ribod.storestudio.model.DiffDocument
import dev.ribod.storestudio.model.DiffRefInfo
import dev.ribod.storestudio.model.DiffSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class DiffReportGeneratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun generateRendersOfflineDiffHtmlWithHashFallback() {
        val diff = DiffDocument(
            base = DiffRefInfo(ref = "v1.0.0", commit = "a1b2c3d", source = "GIT_BASELINE"),
            head = DiffRefInfo(ref = "HEAD", source = "CURRENT_EXPORT"),
            summary = DiffSummary(
                added = 1,
                modified = 1,
            ),
            changes = listOf(
                DiffChange(
                    type = DiffChangeType.MODIFIED,
                    bucket = "PLAY_STORE/PHONE/en-US",
                    id = "onboarding",
                    basePosition = 1,
                    headPosition = 1,
                    pHashDistance = 0.08,
                    baseImage = "UNAVAILABLE",
                    headImage = "PLAY_STORE/PHONE/en-US/01_onboarding.png",
                    basePixelSha256 = "11112222333344445555666677778888",
                    headPixelSha256 = "9999aaaabbbbccccddddeeeeffff0000",
                ),
                DiffChange(
                    type = DiffChangeType.ADDED,
                    bucket = "PLAY_STORE/PHONE/en-US",
                    id = "checkout",
                    basePosition = null,
                    headPosition = 2,
                    headImage = "PLAY_STORE/PHONE/en-US/02_checkout.png",
                    headPixelSha256 = "22223333444455556666777788889999",
                ),
            ),
        )

        val headDir = tempFolder.newFolder("export")
        val html = DiffReportGenerator.generate(
            diffDocument = diff,
            headExportDirectory = headDir,
            appName = "Test App",
        )

        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<title>Test App - Visual Diff Report</title>"))
        assertTrue(html.contains("Comparing <code>v1.0.0</code> (a1b2c3d) → <code>HEAD</code>"))
        assertTrue(html.contains("onboarding"))
        assertTrue(html.contains("checkout"))
        assertTrue(html.contains("Historical Image Unavailable"))
        assertTrue(html.contains("Base SHA-256"))
        assertFalse(html.contains("http://"))
        assertFalse(html.contains("https://"))
    }

    @Test
    fun generateRendersInteractiveComparisonWhenBeforeImagesAvailable() {
        val headDir = tempFolder.newFolder("export")
        val beforeDir = tempFolder.newFolder("base-images")

        val headImgFile = File(headDir, "01_hero.png").apply {
            parentFile.mkdirs()
            val img = BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = Color.BLUE
            g.fillRect(0, 0, 100, 200)
            g.dispose()
            ImageIO.write(img, "png", this)
        }

        val baseImgFile = File(beforeDir, "base_hero.png").apply {
            val img = BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = Color.RED
            g.fillRect(0, 0, 100, 200)
            g.dispose()
            ImageIO.write(img, "png", this)
        }

        val diff = DiffDocument(
            base = DiffRefInfo(ref = "v1.0.0", source = "GIT_BASELINE"),
            head = DiffRefInfo(ref = "HEAD", source = "CURRENT_EXPORT"),
            summary = DiffSummary(modified = 1),
            changes = listOf(
                DiffChange(
                    type = DiffChangeType.MODIFIED,
                    bucket = "PLAY_STORE/PHONE/en-US",
                    id = "hero",
                    basePosition = 1,
                    headPosition = 1,
                    pHashDistance = 0.12,
                    baseImage = baseImgFile.name,
                    headImage = "01_hero.png",
                    basePixelSha256 = "base123",
                    headPixelSha256 = "head123",
                ),
            ),
        )

        val html = DiffReportGenerator.generate(
            diffDocument = diff,
            headExportDirectory = headDir,
            beforeImagesDirectory = beforeDir,
            appName = "Interactive App",
        )

        assertTrue(html.contains("Side-by-side"))
        assertTrue(html.contains("Split Slider"))
        assertTrue(html.contains("Onion Skin"))
        assertTrue(html.contains("Difference"))
        assertTrue(html.contains("data:image/jpeg;base64,"))
    }

    @Test
    fun generateDoesNotEmbedImagesOutsideTheirDirectories() {
        val root = tempFolder.newFolder("root")
        val headDir = File(root, "export").apply { mkdirs() }
        val beforeDir = File(root, "base-images").apply { mkdirs() }
        val png = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
        ImageIO.write(png, "png", File(root, "secret_head.png"))
        val outsideBase = File(root, "secret_base.png").also { ImageIO.write(png, "png", it) }

        val diff = DiffDocument(
            base = DiffRefInfo(ref = "v1.0.0", source = "GIT_BASELINE"),
            head = DiffRefInfo(ref = "HEAD", source = "CURRENT_EXPORT"),
            summary = DiffSummary(modified = 1),
            changes = listOf(
                DiffChange(
                    type = DiffChangeType.MODIFIED,
                    bucket = "PLAY_STORE/PHONE/en-US",
                    id = "hero",
                    basePosition = 1,
                    headPosition = 1,
                    baseImage = outsideBase.absolutePath,
                    headImage = "../secret_head.png",
                ),
            ),
        )

        val html = DiffReportGenerator.generate(
            diffDocument = diff,
            headExportDirectory = headDir,
            beforeImagesDirectory = beforeDir,
            appName = "Escape App",
        )

        assertFalse(html.contains("data:image/"))
    }

    @Test
    fun generateRendersDeviceFilterPillsInNaturalOrderAndOmitsUnusedDevices() {
        val diff = DiffDocument(
            base = DiffRefInfo(ref = "v1.0.0", source = "GIT_BASELINE"),
            head = DiffRefInfo(ref = "HEAD", source = "CURRENT_EXPORT"),
            summary = DiffSummary(added = 2),
            changes = listOf(
                DiffChange(
                    type = DiffChangeType.ADDED,
                    bucket = "PLAY_STORE/TABLET_7/en-US",
                    id = "tab_screen",
                    basePosition = null,
                    headPosition = 1,
                    headImage = "tab.png",
                    headPixelSha256 = "sha_tab",
                ),
                DiffChange(
                    type = DiffChangeType.ADDED,
                    bucket = "PLAY_STORE/PHONE/en-US",
                    id = "phone_screen",
                    basePosition = null,
                    headPosition = 1,
                    headImage = "phone.png",
                    headPixelSha256 = "sha_phone",
                ),
            ),
        )

        val headDir = tempFolder.newFolder("export2")
        val html = DiffReportGenerator.generate(
            diffDocument = diff,
            headExportDirectory = headDir,
            appName = "Filter Test App",
        )

        assertTrue(html.contains("data-device=\"ALL\""))
        assertTrue(html.contains("data-device=\"PHONE\""))
        assertTrue(html.contains("data-device=\"TABLET_7\""))
        val phoneIndex = html.indexOf("data-device=\"PHONE\"")
        val tabIndex = html.indexOf("data-device=\"TABLET_7\"")
        assertTrue(phoneIndex < tabIndex)

        // Verifies dynamic omission: XR, DESKTOP, TV, AUTO are not rendered
        assertFalse(html.contains("data-device=\"XR\""))
        assertFalse(html.contains("data-device=\"DESKTOP\""))
        assertFalse(html.contains("data-device=\"TV\""))
        assertFalse(html.contains("data-device=\"AUTO\""))
    }

    @Test
    fun generateRendersAppIconWhenProvided() {
        val headDir = tempFolder.newFolder("export-icon")
        val iconFile = File(headDir, "icon.png").apply {
            val img = BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = Color.GREEN
            g.fillRect(0, 0, 32, 32)
            g.dispose()
            ImageIO.write(img, "png", this)
        }

        val diff = DiffDocument(
            base = DiffRefInfo(ref = "v1.0.0", source = "LOCAL_BASELINE"),
            head = DiffRefInfo(ref = "HEAD", source = "CURRENT_EXPORT"),
            summary = DiffSummary(),
            changes = emptyList(),
        )

        val htmlWithIcon = DiffReportGenerator.generate(
            diffDocument = diff,
            headExportDirectory = headDir,
            appName = "Icon App",
            appIcon = iconFile,
        )

        assertTrue(htmlWithIcon.contains("<link rel=\"icon\" href=\"data:image/png;base64,"))
        assertTrue(htmlWithIcon.contains("<img class=\"diff-brand-icon-img\""))
        assertFalse(htmlWithIcon.contains("<div class=\"diff-brand-icon\">✦</div>"))
        assertFalse(htmlWithIcon.contains("http://"))
        assertFalse(htmlWithIcon.contains("https://"))

        val htmlWithoutIcon = DiffReportGenerator.generate(
            diffDocument = diff,
            headExportDirectory = headDir,
            appName = "Icon App",
            appIcon = null,
        )

        assertFalse(htmlWithoutIcon.contains("<link rel=\"icon\""))
        assertTrue(htmlWithoutIcon.contains("<div class=\"diff-brand-icon\">✦</div>"))
    }
}
