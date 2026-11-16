package dev.ribod.storestudio.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticsSnapshotTest {

    @Test
    fun `serializes and deserializes semantics snapshot faithfully`() {
        val snapshot = SemanticsSnapshot(
            schemaVersion = 1,
            jobId = "testJob",
            slotKey = "PLAY_STORE/PHONE/en-US/screenshot_1",
            locale = "en-US",
            textSha256 = "abc123sha",
            nodes = listOf(
                ExtractedSemanticsNode(
                    id = 1,
                    texts = listOf("Welcome", "To App"),
                    contentDescription = "Main Header",
                    role = "Header",
                    bounds = SemanticsBounds(left = 0f, top = 0f, right = 100f, bottom = 50f),
                    hasVisualOverflow = false,
                ),
                ExtractedSemanticsNode(
                    id = 2,
                    texts = listOf("Button Action"),
                    contentDescription = null,
                    role = "Button",
                    bounds = SemanticsBounds(left = 10f, top = 60f, right = 90f, bottom = 90f),
                    hasVisualOverflow = true,
                ),
            ),
        )

        val json = snapshot.toJson()
        val parsed = SemanticsSnapshot.fromJson(json)

        assertEquals(snapshot.schemaVersion, parsed.schemaVersion)
        assertEquals(snapshot.jobId, parsed.jobId)
        assertEquals(snapshot.slotKey, parsed.slotKey)
        assertEquals(snapshot.locale, parsed.locale)
        assertEquals(snapshot.textSha256, parsed.textSha256)
        assertEquals(2, parsed.nodes.size)
        assertTrue(parsed.hasAnyVisualOverflow)
        assertEquals("Welcome", parsed.nodes[0].texts[0])
        assertTrue(parsed.nodes[1].hasVisualOverflow)
    }

    @Test
    fun `computeTextSha256 produces deterministic hash for identical texts`() {
        val nodesA = listOf(
            ExtractedSemanticsNode(id = 1, texts = listOf("Title", "Subtitle")),
            ExtractedSemanticsNode(
                id = 2,
                texts = listOf("Click Here"),
                contentDescription = "Btn",
            ),
        )
        val nodesB = listOf(
            ExtractedSemanticsNode(id = 10, texts = listOf("Title", "Subtitle")),
            ExtractedSemanticsNode(
                id = 20,
                texts = listOf("Click Here"),
                contentDescription = "Btn",
            ),
        )
        val nodesC = listOf(
            ExtractedSemanticsNode(id = 1, texts = listOf("Title", "Subtitle Modified")),
        )

        val hashA = SemanticsSnapshot.computeTextSha256(nodesA)
        val hashB = SemanticsSnapshot.computeTextSha256(nodesB)
        val hashC = SemanticsSnapshot.computeTextSha256(nodesC)

        assertEquals(hashA, hashB)
        assertFalse(hashA == hashC)
        assertEquals(64, hashA.length)
    }
}
