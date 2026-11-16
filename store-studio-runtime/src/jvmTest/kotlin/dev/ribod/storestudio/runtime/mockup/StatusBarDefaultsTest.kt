package dev.ribod.storestudio.runtime.mockup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StatusBarDefaultsTest {
    @Test
    fun absentOrBlankSpecIsNone() {
        assertEquals(StatusBarStyle.None, parseStatusBarSpec(null))
        assertEquals(StatusBarStyle.None, parseStatusBarSpec(" "))
    }

    @Test
    fun jvmDefaultIsNoneWithoutSystemProperty() {
        assertEquals(StatusBarStyle.None, platformStatusBarDefaults())
    }

    @Test
    fun parsesFullSpec() {
        val style = parseStatusBarSpec(
            "style=ios;time=10%3B05;wifi=2;cellular=0;battery=15;charging=true;dark=true",
        )
        assertEquals(StatusBarStyle.Ios("10;05", 2, 0, 15, true, true), style)
    }

    @Test
    fun missingKeysHideElements() {
        val style = parseStatusBarSpec("style=android;wifi=4")
        assertEquals(StatusBarStyle.Android(null, 4, null, null, false, false), style)
    }

    @Test
    fun rangesAreValidated() {
        assertFailsWith<IllegalArgumentException> { StatusBarStyle.Auto(wifi = 5) }
        assertFailsWith<IllegalArgumentException> { StatusBarStyle.Ios(cellular = -1) }
        assertFailsWith<IllegalArgumentException> { StatusBarStyle.Android(battery = 101) }
        assertFailsWith<IllegalArgumentException> { parseStatusBarSpec("style=auto;wifi=9") }
        assertFailsWith<IllegalArgumentException> { parseStatusBarSpec("style=foo") }
    }

    @Test
    fun copyKeepsDataSemantics() {
        val base = StatusBarStyle.Auto()
        assertEquals(StatusBarStyle.Auto(wifi = 2, battery = 15), base.copy(wifi = 2, battery = 15))
    }
}
