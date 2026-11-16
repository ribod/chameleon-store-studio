package dev.ribod.storestudio.core.publish.play

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Applies localized release notes to the latest release of a Google Play track resource.
 *
 * Works on the raw track JSON so every other field (version codes, rollout fraction, country
 * targeting, ...) is sent back exactly as Google Play returned it.
 */
internal object PlayTrackReleaseNotes {

    /** Release statuses in the order a release is considered "the latest" one to annotate. */
    private val STATUS_PRIORITY = listOf("draft", "inProgress", "halted", "completed")

    /**
     * Returns [track] with [notesByLocale] merged into its latest release, replacing notes for
     * the same language and keeping the others; `null` when the track has no releases.
     */
    fun apply(track: JsonObject, notesByLocale: Map<String, String>): JsonObject? {
        val releases = track["releases"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        if (releases.isEmpty()) return null

        val targetIndex = STATUS_PRIORITY.firstNotNullOfOrNull { status ->
            releases.indexOfFirst { it["status"]?.jsonPrimitive?.content == status }
                .takeIf { it >= 0 }
        } ?: 0

        val updatedReleases = releases.mapIndexed { index, release ->
            if (index == targetIndex) mergeNotes(release, notesByLocale) else release
        }
        return JsonObject(track + ("releases" to JsonArray(updatedReleases)))
    }

    private fun mergeNotes(release: JsonObject, notesByLocale: Map<String, String>): JsonObject {
        val existing = release["releaseNotes"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        val kept = existing.filter { it["language"]?.jsonPrimitive?.content !in notesByLocale }
        val added = notesByLocale.toSortedMap().map { (language, text) ->
            JsonObject(mapOf("language" to JsonPrimitive(language), "text" to JsonPrimitive(text)))
        }
        return JsonObject(release + ("releaseNotes" to JsonArray(kept + added)))
    }
}
