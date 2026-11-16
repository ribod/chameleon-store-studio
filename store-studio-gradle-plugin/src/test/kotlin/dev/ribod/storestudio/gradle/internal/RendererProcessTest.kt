package dev.ribod.storestudio.gradle.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration

/** Fake renderer: a child JVM running this main from the test classpath. */
object FakeRenderer {
    @JvmStatic
    fun main(args: Array<String>) {
        val results = File(args[1])
        when (args[0]) {
            "env" -> results.writeText(
                listOf(
                    "GITHUB_TOKEN",
                    "ACTIONS_ID_TOKEN_REQUEST_TOKEN",
                    "STORE_STUDIO_TEST_VAR",
                ).mapNotNull(System::getenv).joinToString(),
            )

            "success" -> results.writeText(
                """{"screenshotResults":[{"previewId":"a","imagePath":"a.png",""" +
                    """"error":{"status":"SUCCESS","brokenClasses":[],"missingClasses":[],"problems":[]}}]}""",
            )

            "hang" -> Thread.sleep(60_000)
        }
    }
}

class RendererProcessTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private fun start(mode: String, results: File): Process = ProcessBuilder(
        System.getProperty("java.home") + "/bin/java",
        "-cp",
        System.getProperty("java.class.path"),
        FakeRenderer::class.java.name,
        mode,
        results.absolutePath,
    ).also {
        it.environment()["GITHUB_TOKEN"] = "secret"
        it.environment()["ACTIONS_ID_TOKEN_REQUEST_TOKEN"] =
            "secret"
        it.environment()["STORE_STUDIO_TEST_VAR"] = "kept"
    }
        .scrubCredentials()
        .start()

    @Test
    fun childSeesOnlyAllowlistedEnv() {
        val out = tempDir.newFile()
        start("env", out).waitFor()
        assertEquals("kept", out.readText())
    }

    @Test
    fun deliveredResultsEndTheWaitWithoutFailure() {
        val out = tempDir.newFile()
        val process = start("success", out)
        val failure =
            awaitRenderer(
                process,
                out,
                setOf("a"),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                100,
            )
        assertNull(failure)
        assertEquals(1, readLayoutlibResults(out).size)
    }

    @Test
    fun stalledRendererIsKilledAndReported() {
        val out = tempDir.newFile()
        out.delete()
        val process = start("hang", out)
        val failure =
            awaitRenderer(
                process,
                out,
                setOf("a"),
                Duration.ofMillis(500),
                Duration.ofSeconds(30),
                100,
            )
        assertNotNull(failure)
        assertTrue(failure!!.startsWith("stalled"))
        process.waitFor()
        assertFalse(process.isAlive)
    }
}
