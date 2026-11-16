package dev.ribod.storestudio.core.diff

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.model.BaselineAsset
import dev.ribod.storestudio.model.BaselineComparison
import dev.ribod.storestudio.model.DiffChangeType
import dev.ribod.storestudio.model.ExportAsset
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.SemanticModificationType
import dev.ribod.storestudio.model.StoreDeviceSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeClassifierTest {

    private fun sampleExportAsset(
        id: String,
        position: Int,
        pixelSha: String = "aaaa",
        pHash: String = "1111222233334444",
        target: StoreTarget = StoreTarget.PLAY_STORE,
        slot: StoreDeviceSlot = StoreDeviceSlot.PHONE,
        locale: String = "en-US",
        textSha: String? = null,
        texts: List<String> = emptyList(),
        hasVisualOverflow: Boolean = false,
    ) = ExportAsset(
        slotKey = "${target.name}/${slot.name}/$locale/$id",
        target = target,
        deviceSlot = slot,
        storeLocale = locale,
        id = id,
        order = position,
        position = position,
        file = "$target/$slot/$locale/0${position}_$id.png",
        format = ImageFormat.PNG,
        widthPx = 1080,
        heightPx = 1920,
        bytes = 1024L,
        pixelSha256 = pixelSha,
        fileSha256 = "file_$pixelSha",
        md5 = "md5_$pixelSha",
        pHash = pHash,
        textSha256 = textSha,
        extractedTexts = texts,
        hasVisualOverflow = hasVisualOverflow,
    )

    private fun sampleBaselineAsset(
        id: String,
        position: Int,
        pixelSha: String = "aaaa",
        pHash: String = "1111222233334444",
        target: String = "PLAY_STORE",
        slot: String = "PHONE",
        locale: String = "en-US",
        textSha: String? = null,
        texts: List<String> = emptyList(),
    ) = BaselineAsset(
        slotKey = "$target/$slot/$locale/$id",
        position = position,
        widthPx = 1080,
        heightPx = 1920,
        pixelSha256 = pixelSha,
        pHash = pHash,
        textSha256 = textSha,
        texts = texts,
    )

    @Test
    fun `classifies unchanged assets when position and pixels match`() {
        val base = listOf(sampleBaselineAsset("hero", 1, pixelSha = "1111"))
        val head = listOf(sampleExportAsset("hero", 1, pixelSha = "1111"))

        val (summary, changes) = ChangeClassifier.classify(base, head)
        assertEquals(1, summary.unchanged)
        assertEquals(0, summary.totalChanges)
        assertEquals(DiffChangeType.UNCHANGED, changes.first().type)
        assertEquals(1, changes.first().basePosition)
        assertEquals(1, changes.first().headPosition)
    }

    @Test
    fun `classifies modified asset when position matches but pixels differ`() {
        val base = listOf(
            sampleBaselineAsset("hero", 1, pixelSha = "1111", pHash = "0000000000000000"),
        )
        val head = listOf(
            sampleExportAsset("hero", 1, pixelSha = "2222", pHash = "ffffffffffffffff"),
        )

        val (summary, changes) = ChangeClassifier.classify(base, head)
        assertEquals(1, summary.modified)
        assertEquals(DiffChangeType.MODIFIED, changes.first().type)
        assertEquals(1, changes.first().basePosition)
        assertEquals(1, changes.first().headPosition)
    }

    @Test
    fun `classifies reordered asset when position changed but pixels match`() {
        val base = listOf(
            sampleBaselineAsset("hero", 1, pixelSha = "1111"),
            sampleBaselineAsset("details", 2, pixelSha = "2222"),
        )
        val head = listOf(
            sampleExportAsset("details", 1, pixelSha = "2222"),
            sampleExportAsset("hero", 2, pixelSha = "1111"),
        )

        val (summary, changes) = ChangeClassifier.classify(base, head)
        assertEquals(2, summary.reordered)
        assertEquals(0, summary.modified)
        assertEquals(DiffChangeType.REORDERED, changes[0].type)
        assertEquals(DiffChangeType.REORDERED, changes[1].type)
    }

    @Test
    fun `classifies moved and modified when both position and pixels differ`() {
        val base = listOf(
            sampleBaselineAsset("hero", 1, pixelSha = "1111", pHash = "0000000000000000"),
        )
        val head = listOf(
            sampleExportAsset("hero", 2, pixelSha = "2222", pHash = "ffffffffffffffff"),
        )

        val (summary, changes) = ChangeClassifier.classify(base, head)
        assertEquals(1, summary.movedAndModified)
        assertEquals(DiffChangeType.MOVED_AND_MODIFIED, changes.first().type)
        assertEquals(1, changes.first().basePosition)
        assertEquals(2, changes.first().headPosition)
    }

    @Test
    fun `classifies replaced asset when new id takes position of removed asset`() {
        val base = listOf(sampleBaselineAsset("old_feature", 1))
        val head = listOf(sampleExportAsset("new_feature", 1))

        val (summary, changes) = ChangeClassifier.classify(base, head)
        assertEquals(1, summary.replaced)
        assertEquals(0, summary.added)
        assertEquals(0, summary.removed)
        val change = changes.first()
        assertEquals(DiffChangeType.REPLACED, change.type)
        assertEquals("new_feature", change.id)
        assertEquals(1, change.basePosition)
        assertEquals(1, change.headPosition)
    }

    @Test
    fun `classifies added and removed assets correctly`() {
        val base = listOf(sampleBaselineAsset("removed_screen", 2))
        val head = listOf(sampleExportAsset("added_screen", 1))

        val (summary, changes) = ChangeClassifier.classify(base, head)
        assertEquals(1, summary.added)
        assertEquals(1, summary.removed)
        assertEquals(0, summary.replaced)

        val added = changes.first { it.type == DiffChangeType.ADDED }
        val removed = changes.first { it.type == DiffChangeType.REMOVED }

        assertEquals("added_screen", added.id)
        assertNull(added.basePosition)
        assertEquals(1, added.headPosition)

        assertEquals("removed_screen", removed.id)
        assertEquals(2, removed.basePosition)
        assertNull(removed.headPosition)
    }

    @Test
    fun `respects strict comparison mode`() {
        val base = listOf(
            sampleBaselineAsset("hero", 1, pixelSha = "1111", pHash = "0000000000000000"),
        )
        val head = listOf(
            sampleExportAsset("hero", 1, pixelSha = "2222", pHash = "0000000000000001"),
        )

        val (summaryStrict, _) = ChangeClassifier.classify(
            baseAssets = base,
            headAssets = head,
            comparison = BaselineComparison.STRICT,
        )
        assertEquals(1, summaryStrict.modified)

        val (summaryPerceptual, _) = ChangeClassifier.classify(
            baseAssets = base,
            headAssets = head,
            comparison = BaselineComparison.PERCEPTUAL,
            perceptualThreshold = 0.05,
        )
        assertEquals(1, summaryPerceptual.unchanged)
    }

    @Test
    fun `defaults to strict so a color-only change is modified`() {
        val base = listOf(sampleBaselineAsset("hero", 1, pixelSha = "purple"))
        val head = listOf(sampleExportAsset("hero", 1, pixelSha = "green"))

        val (summary, _) = ChangeClassifier.classify(base, head)

        assertEquals(1, summary.modified)
    }

    @Test
    fun `counts a text change as modified even under the perceptual threshold`() {
        val base = listOf(
            sampleBaselineAsset(
                "speakers",
                2,
                pixelSha = "1111",
                textSha = "sha_world_class",
                texts = listOf("World-Class Speakers"),
            ),
        )
        val head = listOf(
            sampleExportAsset(
                "speakers",
                2,
                pixelSha = "2222",
                textSha = "sha_meet",
                texts = listOf("Meet the Speakers"),
            ),
        )

        val (summary, changes) = ChangeClassifier.classify(
            baseAssets = base,
            headAssets = head,
            comparison = BaselineComparison.PERCEPTUAL,
        )

        assertEquals(1, summary.modified)
        val change = changes.first()
        assertEquals(0.0, change.pHashDistance)
        assertEquals(SemanticModificationType.TEXT_AND_VISUAL_MODIFIED, change.semanticType)
        assertEquals(listOf("World-Class Speakers"), change.removedTexts)
        assertEquals(listOf("Meet the Speakers"), change.addedTexts)
    }

    @Test
    fun `counts a text change in a reordered asset as moved and modified`() {
        val base = listOf(
            sampleBaselineAsset("hero", 1, pixelSha = "1111", textSha = "v1", texts = listOf("A")),
        )
        val head = listOf(
            sampleExportAsset("hero", 2, pixelSha = "2222", textSha = "v2", texts = listOf("B")),
        )

        val (summary, _) = ChangeClassifier.classify(
            baseAssets = base,
            headAssets = head,
            comparison = BaselineComparison.PERCEPTUAL,
        )

        assertEquals(1, summary.movedAndModified)
    }

    @Test
    fun `keeps perceptual tolerance for rendering noise when the text is unchanged`() {
        val base = listOf(
            sampleBaselineAsset(
                "hero",
                1,
                pixelSha = "1111",
                textSha = "same",
                texts = listOf("A"),
            ),
        )
        val head = listOf(
            sampleExportAsset("hero", 1, pixelSha = "2222", textSha = "same", texts = listOf("A")),
        )

        val (summary, _) = ChangeClassifier.classify(
            baseAssets = base,
            headAssets = head,
            comparison = BaselineComparison.PERCEPTUAL,
        )

        assertEquals(1, summary.unchanged)
    }

    @Test
    fun `sorts changes by natural display order with phones first and feature graphic last`() {
        val head = listOf(
            sampleExportAsset(
                "fg",
                1,
                target = StoreTarget.PLAY_STORE,
                slot = StoreDeviceSlot.FEATURE_GRAPHIC,
            ),
            sampleExportAsset(
                "phone",
                1,
                target = StoreTarget.PLAY_STORE,
                slot = StoreDeviceSlot.PHONE,
            ),
            sampleExportAsset(
                "tablet",
                1,
                target = StoreTarget.PLAY_STORE,
                slot = StoreDeviceSlot.TABLET_7,
            ),
            sampleExportAsset(
                "iphone",
                1,
                target = StoreTarget.APP_STORE,
                slot = StoreDeviceSlot.IPHONE_6_9,
            ),
            sampleExportAsset(
                "ipad",
                1,
                target = StoreTarget.APP_STORE,
                slot = StoreDeviceSlot.IPAD_13,
            ),
        )

        val (_, changes) = ChangeClassifier.classify(emptyList(), head)
        val orderedSlots = changes.map { it.bucket.split('/')[1] }

        // Phones (10, 12) -> Tablets (20, 22) -> Feature Graphic (50)
        assertEquals(
            listOf("PHONE", "IPHONE_6_9", "TABLET_7", "IPAD_13", "FEATURE_GRAPHIC"),
            orderedSlots,
        )
    }

    @Test
    fun `classifies visual layout only when pixels differ but texts match`() {
        val base = listOf(
            sampleBaselineAsset(
                "hero",
                1,
                pixelSha = "1111",
                pHash = "0000000000000000",
                textSha = "sha_text_same",
                texts = listOf("Welcome to the app"),
            ),
        )
        val head = listOf(
            sampleExportAsset(
                "hero",
                1,
                pixelSha = "2222",
                pHash = "ffffffffffffffff",
                textSha = "sha_text_same",
                texts = listOf("Welcome to the app"),
            ),
        )

        val (summary, changes) = ChangeClassifier.classify(base, head)
        assertEquals(1, summary.modified)
        val change = changes.first()
        assertEquals(DiffChangeType.MODIFIED, change.type)
        assertEquals(SemanticModificationType.VISUAL_LAYOUT_ONLY, change.semanticType)
        assertEquals("sha_text_same", change.baseTextSha256)
        assertEquals("sha_text_same", change.headTextSha256)
        assertTrue(change.removedTexts.isEmpty())
        assertTrue(change.addedTexts.isEmpty())
    }

    @Test
    fun `classifies text and visual modified with text drift diffs`() {
        val base = listOf(
            sampleBaselineAsset(
                "hero",
                1,
                pixelSha = "1111",
                pHash = "0000000000000000",
                textSha = "sha_text_v1",
                texts = listOf("Old Heading", "Common text"),
            ),
        )
        val head = listOf(
            sampleExportAsset(
                "hero",
                1,
                pixelSha = "2222",
                pHash = "ffffffffffffffff",
                textSha = "sha_text_v2",
                texts = listOf("New Heading", "Common text"),
            ),
        )

        val (summary, changes) = ChangeClassifier.classify(base, head)
        assertEquals(1, summary.modified)
        val change = changes.first()
        assertEquals(DiffChangeType.MODIFIED, change.type)
        assertEquals(SemanticModificationType.TEXT_AND_VISUAL_MODIFIED, change.semanticType)
        assertEquals("sha_text_v1", change.baseTextSha256)
        assertEquals("sha_text_v2", change.headTextSha256)
        assertEquals(listOf("Old Heading"), change.removedTexts)
        assertEquals(listOf("New Heading"), change.addedTexts)
    }

    @Test
    fun `propagates hasVisualOverflow to diff changes`() {
        val base = listOf(sampleBaselineAsset("hero", 1))
        val head = listOf(sampleExportAsset("hero", 1, hasVisualOverflow = true))

        val (_, changes) = ChangeClassifier.classify(base, head)
        val change = changes.first()
        assertTrue(change.hasVisualOverflow)
    }

    @Test
    fun `classifies legacy baseline without semantics as unknown without spurious drift`() {
        val base = listOf(
            sampleBaselineAsset(
                "hero",
                1,
                pixelSha = "1111",
                textSha = null,
                texts = emptyList(),
            ),
        )
        val head = listOf(
            sampleExportAsset(
                "hero",
                1,
                pixelSha = "2222",
                pHash = "ffffffffffffffff",
                textSha = "sha_text_current",
                texts = listOf("Current text"),
            ),
        )

        val (_, changes) = ChangeClassifier.classify(base, head)
        val change = changes.first()
        assertEquals(SemanticModificationType.UNKNOWN, change.semanticType)
        assertTrue(change.removedTexts.isEmpty())
        assertTrue(change.addedTexts.isEmpty())
    }
}
