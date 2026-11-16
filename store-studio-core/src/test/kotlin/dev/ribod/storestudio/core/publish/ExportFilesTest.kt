package dev.ribod.storestudio.core.publish

import dev.ribod.storestudio.core.ci.CiPlatform
import dev.ribod.storestudio.core.ci.PrCommentTarget
import dev.ribod.storestudio.core.publish.play.PlayServiceAccountJwt
import dev.ribod.storestudio.core.publish.play.ServiceAccountCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExportFilesTest {

    @get:Rule
    val tempDir: TemporaryFolder = TemporaryFolder()

    @Test
    fun resolvesFileInsideExportDir() {
        val dir = tempDir.newFolder("export")
        File(dir, "a.png").writeBytes(ByteArray(1))
        assertNotNull(resolveExportFile(dir, "a.png"))
        assertNull(invalidExportFileMessage(dir, listOf("a.png")))
    }

    @Test
    fun rejectsTraversalAndMissingFiles() {
        val dir = tempDir.newFolder("export")
        File(tempDir.root, "secret.txt").writeText("x")
        assertNull(resolveExportFile(dir, "../secret.txt"))
        assertNull(resolveExportFile(dir, "missing.png"))
        assertTrue(invalidExportFileMessage(dir, listOf("../secret.txt"))!!.contains("secret.txt"))
    }

    @Test
    fun toStringRedactsSecrets() {
        val credentials = ServiceAccountCredentials("a@b.com", "PRIVATE-KEY-BODY")
        assertFalse(credentials.toString().contains("PRIVATE-KEY-BODY"))
        val target = PrCommentTarget(CiPlatform.GITHUB, "tok-123", "o/r", 1)
        assertFalse(target.toString().contains("tok-123"))
    }

    @Test
    fun rejectsNonGoogleTokenUri() {
        fun json(uri: String) =
            """{"client_email":"a@b.com","private_key":"k","token_uri":"$uri"}"""
        assertEquals(
            "https://oauth2.googleapis.com/token",
            PlayServiceAccountJwt.parseCredentials(json("https://oauth2.googleapis.com/token"))
                .token_uri,
        )
        listOf("http://oauth2.googleapis.com/token", "https://evil.example.com/token")
            .forEach {
                assertThrows(IllegalArgumentException::class.java) {
                    PlayServiceAccountJwt.parseCredentials(json(it))
                }
            }
    }
}
