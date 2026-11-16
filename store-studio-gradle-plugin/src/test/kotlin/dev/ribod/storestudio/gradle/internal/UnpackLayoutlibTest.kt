package dev.ribod.storestudio.gradle.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class UnpackLayoutlibTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun zip(name: String, vararg entries: Pair<String, String>): File =
        tempFolder.newFile(name).also { file ->
            ZipOutputStream(FileOutputStream(file)).use { zip ->
                for ((path, content) in entries) {
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
        }

    @Test
    fun extractsRuntimeAndInjectsFrameworkResources() {
        val runtimeJar =
            zip("layoutlib-runtime.jar", "data/build.prop" to "ro.build.version.sdk=36")
        val resourcesJar =
            zip("layoutlib-resources.jar", "res/values/strings.xml" to "<resources/>")
        val destinationDir = tempFolder.newFolder("layoutlib_out")
        File(destinationDir, "stale.txt").writeText("from a previous version")

        unpackLayoutlib(runtimeJar, resourcesJar, destinationDir)

        assertEquals("ro.build.version.sdk=36", File(destinationDir, "data/build.prop").readText())
        assertTrue(File(destinationDir, "data/framework_res.jar").isFile)
        assertFalse(File(destinationDir, "stale.txt").exists())
    }

    @Test
    fun refusesEntriesOutsideTheDestination() {
        val runtimeJar = zip("evil.jar", "../escaped.txt" to "nope")
        val resourcesJar =
            zip("layoutlib-resources.jar", "res/values/strings.xml" to "<resources/>")
        val destinationDir = tempFolder.newFolder("layoutlib_out")

        assertThrows(IllegalArgumentException::class.java) {
            unpackLayoutlib(runtimeJar, resourcesJar, destinationDir)
        }
        assertFalse(File(tempFolder.root, "escaped.txt").exists())
    }
}
