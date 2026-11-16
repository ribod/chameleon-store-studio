package dev.ribod.storestudio.core.report

import dev.ribod.storestudio.model.DiffChange
import dev.ribod.storestudio.model.DiffChangeType
import dev.ribod.storestudio.model.DiffDocument
import dev.ribod.storestudio.model.DiffRefInfo
import dev.ribod.storestudio.model.DiffSummary
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffSummaryMarkdownTest {

    @Test
    fun `generates expected sticky comment format`() {
        val diff = DiffDocument(
            schemaVersion = 1,
            base = DiffRefInfo(ref = "v1.2.0", commit = "a1b2c3d", source = "GIT_BASELINE"),
            head = DiffRefInfo(ref = "main", source = "CURRENT_EXPORT"),
            summary = DiffSummary(
                added = 1,
                removed = 0,
                reordered = 0,
                modified = 1,
                replaced = 0,
                movedAndModified = 0,
                unchanged = 5,
            ),
            changes = listOf(
                DiffChange(
                    type = DiffChangeType.MODIFIED,
                    bucket = "PLAY_STORE/PHONE/en-US",
                    id = "hero",
                    basePosition = 1,
                    headPosition = 1,
                    pHashDistance = 0.0812,
                ),
                DiffChange(
                    type = DiffChangeType.ADDED,
                    bucket = "PLAY_STORE/PHONE/en-US",
                    id = "promo",
                    basePosition = null,
                    headPosition = 2,
                ),
            ),
        )

        val md = DiffSummaryMarkdown.generate(diff)
        assertTrue(md.contains("<!-- store-studio-diff -->"))
        assertTrue(md.contains("### 📸 Store Studio diff — `v1.2.0` (a1b2c3d) → `main`"))
        assertTrue(md.contains("_Base: baseline committed at `v1.2.0`._"))
        assertTrue(md.contains("| 🟢 Added | 1 |"))
        assertTrue(md.contains("| 🟠 Modified | 1 |"))
        assertTrue(md.contains("| ⚪ Unchanged | 5 |"))
        assertTrue(md.contains("<details><summary>Changed items (2)</summary>"))
        assertTrue(md.contains("| PLAY_STORE/PHONE/en-US | hero | Modified (Δ 0.0812) | 1 |"))
        assertTrue(md.contains("| PLAY_STORE/PHONE/en-US | promo | Added | 2 |"))
    }

    @Test
    fun `truncates long changes lists over 50 items`() {
        val changes = (1..60).map { i ->
            DiffChange(
                type = DiffChangeType.MODIFIED,
                bucket = "PLAY_STORE/PHONE/en-US",
                id = "screen_$i",
                basePosition = i,
                headPosition = i,
            )
        }
        val diff = DiffDocument(
            base = DiffRefInfo(ref = "v1.0.0", source = "GIT_BASELINE"),
            head = DiffRefInfo(ref = "HEAD", source = "CURRENT_EXPORT"),
            summary = DiffSummary(modified = 60),
            changes = changes,
        )

        val md = DiffSummaryMarkdown.generate(diff)
        assertTrue(md.contains("... and 10 more, see HTML report"))
        assertTrue(md.contains("screen_50"))
        assertTrue(!md.contains("screen_51"))
    }

    @Test
    fun `handles empty diff with friendly message`() {
        val diff = DiffDocument(
            base = DiffRefInfo(ref = "v1.0.0", source = "GIT_BASELINE"),
            head = DiffRefInfo(ref = "HEAD", source = "CURRENT_EXPORT"),
            summary = DiffSummary(unchanged = 10),
            changes = emptyList(),
        )

        val md = DiffSummaryMarkdown.generate(diff)
        assertTrue(md.contains("No visual changes detected."))
    }

    @Test
    fun `says when the base was rendered on the same machine`() {
        val diff = DiffDocument(
            schemaVersion = 1,
            base = DiffRefInfo(ref = "origin/main", commit = "a1b2c3d", source = "RENDERED_REF"),
            head = DiffRefInfo(ref = "HEAD", source = "CURRENT_EXPORT"),
            summary = DiffSummary(),
            changes = emptyList(),
        )

        val md = DiffSummaryMarkdown.generate(diff)

        assertTrue(md.contains("_Base: `origin/main` rendered on this machine._"))
    }

    @Test
    fun `names the app in the title`() {
        val diff = DiffDocument(
            schemaVersion = 1,
            base = DiffRefInfo(ref = "main", source = "RENDERED_REF"),
            head = DiffRefInfo(ref = "HEAD", source = "CURRENT_EXPORT"),
            summary = DiffSummary(),
            changes = emptyList(),
        )

        val md = DiffSummaryMarkdown.generate(diff, appName = "KotlinConf")

        assertTrue(md.contains("### 📸 Store Studio diff · KotlinConf — `main` → `HEAD`"))
    }

    @Test
    fun `backticks in a ref cannot break out of the code span`() {
        val diff = DiffDocument(
            schemaVersion = 1,
            base = DiffRefInfo(ref = "a`b", source = "RENDERED_REF"),
            head = DiffRefInfo(ref = "HEAD", source = "CURRENT_EXPORT"),
            summary = DiffSummary(),
            changes = emptyList(),
        )

        assertTrue(DiffSummaryMarkdown.generate(diff).contains("`a'b`"))
    }
}
