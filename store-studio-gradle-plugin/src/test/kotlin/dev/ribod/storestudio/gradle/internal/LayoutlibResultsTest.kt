package dev.ribod.storestudio.gradle.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LayoutlibResultsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun parsesSuccessfulLayoutlibResults() {
        val file = tempFolder.newFile("results.json")
        file.writeText(
            """
            {
              "screenshotResults": [
                {
                  "previewId": "hero__PLAY_STORE__PHONE__en-US",
                  "imagePath": "hero.png",
                  "error": {
                    "status": "SUCCESS",
                    "brokenClasses": [],
                    "missingClasses": [],
                    "problems": []
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val results = readLayoutlibResults(file)
        assertEquals(1, results.size)
        val shot = results["hero__PLAY_STORE__PHONE__en-US"]
        assertEquals("hero.png", shot?.imagePath)
        assertEquals("SUCCESS", shot?.status)
        assertTrue(shot?.brokenClasses?.isEmpty() == true)

        val imageFile = tempFolder.newFile("hero.png")
        imageFile.writeText("PNG_DATA")
        assertTrue(isLayoutlibSuccess(shot, imageFile))
    }

    @Test
    fun detectsMissingComposeViewAdapterAsFailure() {
        val file = tempFolder.newFile("results_missing.json")
        file.writeText(
            """
            {
              "screenshotResults": [
                {
                  "previewId": "screen__APP_STORE__IPHONE_6_9__en-US",
                  "imagePath": "screen.png",
                  "error": {
                    "status": "SUCCESS",
                    "brokenClasses": [],
                    "missingClasses": ["androidx.compose.ui.tooling.ComposeViewAdapter"],
                    "problems": []
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val results = readLayoutlibResults(file)
        val shot = results["screen__APP_STORE__IPHONE_6_9__en-US"]
        val imageFile = tempFolder.newFile("screen.png")
        imageFile.writeText("PLACEHOLDER")

        assertFalse(isLayoutlibSuccess(shot, imageFile))
    }

    @Test
    fun detectsBrokenClassesAsFailure() {
        val file = tempFolder.newFile("results_broken.json")
        file.writeText(
            """
            {
              "screenshotResults": [
                {
                  "previewId": "broken_shot",
                  "imagePath": "broken.png",
                  "error": {
                    "status": "SUCCESS",
                    "brokenClasses": [{"className": "com.example.BrokenView"}],
                    "missingClasses": [],
                    "problems": []
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val results = readLayoutlibResults(file)
        val shot = results["broken_shot"]
        val imageFile = tempFolder.newFile("broken.png")
        imageFile.writeText("BROKEN")

        assertFalse(isLayoutlibSuccess(shot, imageFile))
        assertEquals(listOf("com.example.BrokenView"), shot?.brokenClasses)
    }

    @Test
    fun handlesEmptyOrMissingResultsFileGracefully() {
        val nonExistent = File(tempFolder.root, "absent.json")
        val results = readLayoutlibResults(nonExistent)
        assertTrue(results.isEmpty())
    }
}
