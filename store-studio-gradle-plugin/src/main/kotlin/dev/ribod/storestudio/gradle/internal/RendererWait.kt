package dev.ribod.storestudio.gradle.internal

import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Waits for a renderer process until it exits or has written results for every id in [wantedIds].
 * Kills it on [totalTimeout] or when no new result lands within [stallTimeout]. Returns the failure
 * reason, or null when the process finished or delivered everything.
 */
internal fun awaitRenderer(
    process: Process,
    resultsFile: File,
    wantedIds: Set<String>,
    stallTimeout: Duration,
    totalTimeout: Duration,
    pollMs: Long = 2000,
): String? {
    val started = System.currentTimeMillis()
    var lastProgressTime = started
    var landedCount = 0
    var resultsStamp = -1L to -1L
    var currentResults = emptyMap<String, ShotResult>()
    try {
        while (true) {
            if (System.currentTimeMillis() - started >= totalTimeout.toMillis()) {
                return if (process.isAlive) "timed out after ${totalTimeout.toMinutes()}m" else null
            }
            if (process.waitFor(pollMs, TimeUnit.MILLISECONDS)) return null

            // Only re-parse the results file when the renderer has written to it
            val stamp = resultsFile.lastModified() to resultsFile.length()
            if (stamp != resultsStamp) {
                resultsStamp = stamp
                currentResults = readLayoutlibResults(resultsFile)
            }
            if (currentResults.keys.containsAll(wantedIds)) return null

            if (currentResults.size > landedCount) {
                landedCount = currentResults.size
                lastProgressTime = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - lastProgressTime > stallTimeout.toMillis()) {
                return "stalled for ${stallTimeout.seconds}s"
            }
        }
    } finally {
        // Also runs when the build is cancelled or the wait throws, so no worker JVM is leaked
        if (process.isAlive) process.destroyForcibly()
    }
}
