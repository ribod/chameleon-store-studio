package dev.ribod.storestudio.gradle.internal

import java.io.File

private val FAILURE_LINE = Regex(
    """^(Exception in thread .*|([\w$]+\.)+[\w$]*(Exception|Error)(:.*)?|Caused by: .*)$""",
)

/**
 * Returns the first exception line of a renderer worker log (e.g.
 * `java.lang.IllegalArgumentException: Resource APK path is missing`), so failures can name their
 * root cause instead of only pointing at the log file.
 */
internal fun firstFailureLine(log: File): String? {
    if (!log.isFile) return null
    return log.useLines { lines ->
        lines.map { it.trim() }.firstOrNull { FAILURE_LINE.matches(it) }
    }
}

private val ENVIRONMENT_FAILURE = Regex("OutOfMemoryError|No space left on device")

/**
 * Returns the first log line showing that the worker died because of the machine rather than the
 * rendered code: out of memory or out of disk space.
 */
internal fun environmentFailureLine(log: File): String? {
    if (!log.isFile) return null
    return log.useLines { lines ->
        lines.map { it.trim() }.firstOrNull { ENVIRONMENT_FAILURE.containsMatchIn(it) }
    }
}
