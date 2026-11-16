package dev.ribod.storestudio.gradle.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Result record parsed from Layoutlib's `results.json` output for a single screenshot preview.
 *
 * Captures image generation paths, status codes, missing or broken classes, and execution problems.
 *
 * @property imagePath Relative path to the rendered output image, if generated.
 * @property status Top-level status string reported by the runner (e.g. "SUCCESS").
 * @property brokenClasses List of class names that failed to load or link during rendering.
 * @property missingClasses List of classes absent from the classpath at execution time.
 * @property problems List of non-fatal measurement or rendering warnings.
 * @property message Detailed error message or root stack trace snippet.
 */
internal data class ShotResult(
    val imagePath: String?,
    val status: String?,
    val brokenClasses: List<String>,
    val missingClasses: List<String>,
    val problems: List<String>,
    val message: String?,
)

/**
 * The renderer's preview adapter host class.
 *
 * When this class is absent from the render classpath, Layoutlib emits an empty placeholder
 * but still misreports status as SUCCESS.
 */
internal const val COMPOSE_VIEW_ADAPTER: String = "androidx.compose.ui.tooling.ComposeViewAdapter"

/**
 * Evaluates whether a Layoutlib execution produced a valid, high-fidelity render.
 *
 * Verifies that the status is successful, no classes were broken, the preview adapter
 * was present, no fatal layout problems occurred, and a non-empty image file exists on disk.
 */
internal fun isLayoutlibSuccess(result: ShotResult?, image: File?): Boolean = result != null &&
    (result.status == null || result.status == "SUCCESS") &&
    result.brokenClasses.isEmpty() &&
    result.problems.isEmpty() &&
    result.missingClasses.none { it.endsWith("ComposeViewAdapter") } &&
    image != null &&
    image.isFile &&
    image.length() > 0L

/**
 * Parses the Layoutlib runner's `results.json` file into a map keyed by preview identifier.
 */
internal fun readLayoutlibResults(file: File): Map<String, ShotResult> {
    if (!file.isFile) return emptyMap()

    val root = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()
        ?: return emptyMap()

    val resultsArray = root["screenshotResults"] as? JsonArray ?: return emptyMap()

    return resultsArray.mapNotNull { element ->
        val obj = element.jsonObject
        val previewId =
            (obj["previewId"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val errorObj = obj["error"] as? JsonObject

        val brokenClasses = (errorObj?.get("brokenClasses") as? JsonArray)?.mapNotNull { item ->
            (item as? JsonObject)?.let { (it["className"] as? JsonPrimitive)?.contentOrNull }
        } ?: emptyList()

        val missingClasses = (errorObj?.get("missingClasses") as? JsonArray)?.mapNotNull { item ->
            (item as? JsonPrimitive)?.contentOrNull
                ?: (item as? JsonObject)?.let { (it["className"] as? JsonPrimitive)?.contentOrNull }
        } ?: emptyList()

        val problems = (errorObj?.get("problems") as? JsonArray)?.mapNotNull { problem ->
            (problem as? JsonObject)?.let {
                val stack = (it["stackTrace"] as? JsonPrimitive)?.contentOrNull
                stack?.lineSequence()?.firstOrNull()?.takeIf { line -> line.isNotBlank() }
                    ?: (it["html"] as? JsonPrimitive)?.contentOrNull
            }
        } ?: emptyList()

        val message = (errorObj?.get("message") as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: (errorObj?.get("stackTrace") as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotBlank() }

        val status = (errorObj?.get("status") as? JsonPrimitive)?.contentOrNull
        val imagePath = (obj["imagePath"] as? JsonPrimitive)?.contentOrNull

        previewId to ShotResult(imagePath, status, brokenClasses, missingClasses, problems, message)
    }.toMap()
}
