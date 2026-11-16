package dev.ribod.storestudio.runtime

import dev.ribod.storestudio.runtime.semantics.parseJobRecords
import kotlin.test.Test
import kotlin.test.assertEquals

class SemanticsJobsParsingTest {

    @Test
    fun parsesOneJobPerTabSeparatedLine() {
        val content = listOf(
            listOf(
                "home__PLAY_STORE__PHONE__en-US",
                "PLAY_STORE/PHONE/en-US/home",
                "com.example.ScreensKt.Home",
                "PHONE",
                "PLAY_STORE",
                "en-US",
            ),
            emptyList(),
            listOf(
                "list__APP_STORE__IPAD_13__de-DE",
                "APP_STORE/IPAD_13/de-DE/list",
                "com.example.ScreensKt.List",
                "IPAD_13",
                "APP_STORE",
                "de-DE",
            ),
        ).joinToString("\n") { it.joinToString("\t") }

        val jobs = parseJobRecords(content)

        assertEquals(2, jobs.size)
        assertEquals("home__PLAY_STORE__PHONE__en-US", jobs[0].jobId)
        assertEquals("com.example.ScreensKt.List", jobs[1].methodFqn)
        assertEquals("IPAD_13", jobs[1].slot)
        assertEquals("APP_STORE", jobs[1].target)
        assertEquals("de-DE", jobs[1].locale)
    }
}
