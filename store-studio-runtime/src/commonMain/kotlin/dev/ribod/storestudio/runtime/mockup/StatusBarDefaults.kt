package dev.ribod.storestudio.runtime.mockup

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Status bar used when a screenshot passes `statusBar = null`. Defaults to the Gradle
 * `storeStudio { statusBar { ... } }` configuration, or [StatusBarStyle.None] when disabled.
 * Tweak it per screenshot with e.g. `(LocalStatusBarDefaults.current as? StatusBarStyle.Auto)?.copy(battery = 15)`.
 */
public val LocalStatusBarDefaults: ProvidableCompositionLocal<StatusBarStyle> =
    staticCompositionLocalOf { platformStatusBarDefaults() }

/** Platform-provided default (JVM: parsed from `-Dstorestudio.statusBar`, iOS: none). */
internal expect fun platformStatusBarDefaults(): StatusBarStyle

/**
 * Parses the compact `key=value;...` spec written by the Gradle plugin, e.g.
 * `style=auto;time=9:41;wifi=4;cellular=4;battery=100;charging=false;dark=false`.
 * A missing `time`/`wifi`/`cellular`/`battery` key hides that element; `;` and `%` in the time are
 * escaped as `%3B` and `%25`. Null or blank input yields [StatusBarStyle.None].
 */
internal fun parseStatusBarSpec(spec: String?): StatusBarStyle {
    if (spec.isNullOrBlank()) return StatusBarStyle.None
    val map = spec.split(';').filter { it.isNotBlank() }.associate {
        it.substringBefore('=').trim() to it.substringAfter('=', "").trim()
    }
    val time = map["time"]?.replace("%3B", ";")?.replace("%25", "%")
    val wifi = map["wifi"]?.toInt()
    val cellular = map["cellular"]?.toInt()
    val battery = map["battery"]?.toInt()
    val charging = map["charging"].toBoolean()
    val dark = map["dark"].toBoolean()
    return when (map["style"]?.lowercase()) {
        "android" -> StatusBarStyle.Android(time, wifi, cellular, battery, charging, dark)
        "ios" -> StatusBarStyle.Ios(time, wifi, cellular, battery, charging, dark)
        "auto", null -> StatusBarStyle.Auto(time, wifi, cellular, battery, charging, dark)
        else -> throw IllegalArgumentException("Unknown status bar style '${map["style"]}'")
    }
}
