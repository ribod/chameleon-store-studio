package dev.ribod.storestudio.gradle.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProcessLogsTest {

    @get:Rule
    val tempDir: TemporaryFolder = TemporaryFolder()

    @Test
    fun findsLayoutlibStartupException() {
        val log = tempDir.newFile("renderer.log").apply {
            writeText(
                """
                Loading layoutlib from /tmp/layoutlib
                Exception in thread "main" java.lang.IllegalArgumentException: Resource APK path is missing
                	at com.android.tools.render.Main.main(Main.kt:42)
                """.trimIndent(),
            )
        }
        assertEquals(
            "Exception in thread \"main\" java.lang.IllegalArgumentException: Resource APK path is missing",
            firstFailureLine(log),
        )
    }

    @Test
    fun findsRobolectricFailureIgnoringRegularLogLines() {
        val log = tempDir.newFile("robolectric.log").apply {
            writeText(
                """
                V/ActivityScenario: Update currentActivityStage to DESTROYED
                E
                java.lang.NoClassDefFoundError: androidx/navigationevent/R${'$'}id
                Caused by: java.lang.ClassNotFoundException: androidx.navigationevent.R${'$'}id
                """.trimIndent(),
            )
        }
        assertEquals(
            "java.lang.NoClassDefFoundError: androidx/navigationevent/R\$id",
            firstFailureLine(log),
        )
    }

    @Test
    fun returnsNullForMissingOrCleanLogs() {
        assertNull(firstFailureLine(tempDir.root.resolve("absent.log")))
        assertNull(
            firstFailureLine(
                tempDir.newFile("ok.log").apply {
                    writeText("Rendered 3 screenshots\n")
                },
            ),
        )
    }

    @Test
    fun recognizesFailuresCausedByTheMachine() {
        val oom = tempDir.newFile("oom.log").apply {
            writeText(
                "Rendering...\nException in thread \"main\" java.lang.OutOfMemoryError: Java heap space\n",
            )
        }
        val disk = tempDir.newFile("disk.log").apply {
            writeText("Caused by: java.io.IOException: No space left on device\n")
        }
        val code = tempDir.newFile("code.log").apply {
            writeText("java.lang.NoClassDefFoundError: com/example/Missing\n")
        }

        assertEquals(
            "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space",
            environmentFailureLine(oom),
        )
        assertEquals(
            "Caused by: java.io.IOException: No space left on device",
            environmentFailureLine(disk),
        )
        // Problems in the rendered code are left to the fallback engine
        assertNull(environmentFailureLine(code))
    }
}
