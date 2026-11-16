package dev.ribod.storestudio.runtime

import androidx.compose.ui.Modifier
import dev.ribod.storestudio.model.SemanticsSnapshot
import dev.ribod.storestudio.runtime.model.StoreContext
import dev.ribod.storestudio.runtime.semantics.storeSemanticsProbe
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class StoreSemanticsProbeTest {

    @Test
    fun probeModifierAttachesWithoutError() {
        val modifier = Modifier.storeSemanticsProbe(StoreContext.Preview)
        assertNotNull(modifier)
    }

    @Test
    fun probeIsNoOpWhenSemanticsCaptureIsDisabled() {
        val previous = System.clearProperty("storestudio.semanticsDir")
        try {
            assertSame(Modifier, Modifier.storeSemanticsProbe(StoreContext.Preview))
        } finally {
            previous?.let { System.setProperty("storestudio.semanticsDir", it) }
        }
    }

    @Test
    fun probeAttachesNodesWhenSemanticsCaptureIsEnabled() {
        val previous = System.setProperty("storestudio.semanticsDir", "build/semantics-test")
        try {
            assertNotSame(Modifier, Modifier.storeSemanticsProbe(StoreContext.Preview))
        } finally {
            if (previous == null) {
                System.clearProperty("storestudio.semanticsDir")
            } else {
                System.setProperty("storestudio.semanticsDir", previous)
            }
        }
    }

    @Test
    fun formatsSnapshotJsonCompatibleWithCoreModel() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "storestudio_semantics_test")
        tempDir.mkdirs()
        tempDir.deleteOnExit()

        val sampleJson = """
            {
              "schemaVersion": 1,
              "jobId": "test_job",
              "slotKey": "PLAY_STORE/PHONE/en-US/test_job",
              "locale": "en-US",
              "textSha256": "abcdef123456",
              "hasAnyVisualOverflow": false,
              "nodes": [
                {
                  "id": 1,
                  "texts": ["Headline Text"],
                  "contentDescription": "Headline",
                  "role": "Text",
                  "bounds": {"left": 0.0, "top": 0.0, "right": 200.0, "bottom": 50.0},
                  "hasVisualOverflow": false
                }
              ]
            }
        """.trimIndent()

        val snapshotFile = tempDir.resolve("test_job.json")
        snapshotFile.writeText(sampleJson)

        val parsed = SemanticsSnapshot.fromFile(snapshotFile)
        assertEquals("test_job", parsed.jobId)
        assertEquals(1, parsed.nodes.size)
        assertEquals("Headline Text", parsed.nodes.first().texts.first())
        assertEquals(false, parsed.hasAnyVisualOverflow)
    }
}
