package dev.ribod.storestudio.gradle.internal

import dev.ribod.storestudio.gradle.tasks.LayoutlibRenderTask.Companion.balancedBatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RenderBatchingTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    @Test
    fun batchesAreAsFewAsAllowedAndEvenlySized() {
        assertEquals(listOf(25, 25), balancedBatches(List(50) { it }, 40).map { it.size })
        assertEquals(listOf(20), balancedBatches(List(20) { it }, 40).map { it.size })
        assertEquals(listOf(21, 20), balancedBatches(List(41) { it }, 40).map { it.size })
        assertEquals(listOf(27, 27, 27), balancedBatches(List(81) { it }, 40).map { it.size })
        assertEquals(emptyList<List<Int>>(), balancedBatches(emptyList<Int>(), 40))
        // Non-positive sizes fall back to one job per batch
        assertEquals(3, balancedBatches(List(3) { it }, 0).size)
    }

    @Test
    fun semanticsJobsAreWrittenOnePerLineInFieldOrder() {
        val file = tmp.newFile("jobs.tsv")
        writeSemanticsJobs(
            file,
            listOf(
                SemanticsJob(
                    "home__PLAY_STORE__PHONE__en-US",
                    "PLAY_STORE/PHONE/en-US/home",
                    "com.example.ScreensKt.Home",
                    "PHONE",
                    "PLAY_STORE",
                    "en-US",
                ),
                SemanticsJob(
                    "home__APP_STORE__IPHONE_6_9__es-MX",
                    "APP_STORE/IPHONE_6_9/es-MX/home",
                    "com.example.ScreensKt.Home",
                    "IPHONE_6_9",
                    "APP_STORE",
                    "es-MX",
                ),
            ),
        )

        val lines = file.readLines()
        assertEquals(2, lines.size)
        assertEquals(
            listOf(
                "home__PLAY_STORE__PHONE__en-US",
                "PLAY_STORE/PHONE/en-US/home",
                "com.example.ScreensKt.Home",
                "PHONE",
                "PLAY_STORE",
                "en-US",
            ),
            lines.first().split('\t'),
        )
    }

    @Test
    fun semanticsJobFieldsMustNotContainSeparators() {
        val file = tmp.newFile("jobs.tsv")
        assertThrows(IllegalArgumentException::class.java) {
            writeSemanticsJobs(
                file,
                listOf(SemanticsJob("a\tb", "s", "m", "PHONE", "PLAY_STORE", "en-US")),
            )
        }
    }
}
