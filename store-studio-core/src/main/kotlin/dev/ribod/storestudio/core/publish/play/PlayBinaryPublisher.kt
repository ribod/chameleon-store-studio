package dev.ribod.storestudio.core.publish.play

import com.skydoves.sandwich.ApiResponse
import dev.ribod.storestudio.core.publish.asFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val MAX_RELEASE_NOTES_CHARS = 500

private fun localFailure(message: String): ApiResponse<PlayBinaryPublishOutcome> =
    ApiResponse.Failure.Error(message)

internal fun validateBinaryRequest(r: PlayBinaryPublishRequest): String? {
    if (r.releaseFiles.isEmpty()) return "No release files given; provide a .aab."
    val files = r.releaseFiles + listOfNotNull(r.mappingFile, r.debugSymbolsFile)
    files.firstOrNull { !it.isFile }?.let { return "File does not exist: ${it.path}" }
    r.releaseFiles.firstOrNull { it.extension.lowercase() !in setOf("aab") }
        ?.let {
            return "Unsupported release file (expected .aab, APKs are not accepted): ${it.path}"
        }
    val fraction = r.userFraction
    if (fraction != null) {
        if (r.status != PlayReleaseStatus.IN_PROGRESS && r.status != PlayReleaseStatus.HALTED) {
            return "userFraction is only valid with status IN_PROGRESS or HALTED, not ${r.status}."
        }
        if (fraction <= 0.0 || fraction >= 1.0) {
            return "userFraction must be greater than 0 and less than 1, got $fraction."
        }
    } else if (r.status == PlayReleaseStatus.IN_PROGRESS) {
        return "status IN_PROGRESS requires userFraction (e.g. 0.1)."
    }
    r.inAppUpdatePriority?.let {
        if (it !in 0..5) return "inAppUpdatePriority must be between 0 and 5, got $it."
    }
    r.releaseNotesByLocale.entries.firstOrNull { it.value.length > MAX_RELEASE_NOTES_CHARS }
        ?.let {
            return "Release notes for '${it.key}' are ${it.value.length} characters; " +
                "Google Play allows at most $MAX_RELEASE_NOTES_CHARS."
        }
    return null
}

private fun trackBody(r: PlayBinaryPublishRequest, versionCodes: List<Long>): JsonObject =
    buildJsonObject {
        put("track", r.track)
        put(
            "releases",
            JsonArray(
                listOf(
                    buildJsonObject {
                        r.releaseName?.let { put("name", it) }
                        put(
                            "versionCodes",
                            JsonArray(
                                versionCodes.map {
                                    JsonPrimitive(it.toString())
                                },
                            ),
                        )
                        put("status", r.status.apiValue)
                        r.userFraction?.let { put("userFraction", it) }
                        r.inAppUpdatePriority?.let { put("inAppUpdatePriority", it) }
                        if (r.releaseNotesByLocale.isNotEmpty()) {
                            put(
                                "releaseNotes",
                                JsonArray(
                                    r.releaseNotesByLocale.map { (lang, text) ->
                                        buildJsonObject {
                                            put("language", lang)
                                            put("text", text)
                                        }
                                    },
                                ),
                            )
                        }
                    },
                ),
            ),
        )
    }

private fun report(
    r: PlayBinaryPublishRequest,
    status: PlayPublishStatus,
    editId: String?,
    versionCodes: List<Long>,
): String {
    val border = "=".repeat(80)
    val sb = StringBuilder()
    sb.appendLine(border)
    val mode = when (status) {
        PlayPublishStatus.COMMITTED ->
            if (r.existingEditId !=
                null
            ) {
                "Binary Staged (Not Committed)"
            } else {
                "Binary Published Successfully"
            }

        PlayPublishStatus.DRY_RUN_LOCAL -> "Binary Publish Plan (Offline Dry Run)"

        PlayPublishStatus.DRY_RUN_VALIDATED -> "Binary Publish Plan (Validated Dry Run)"

        PlayPublishStatus.FAILED -> "Binary Publish Failed"
    }
    sb.appendLine("Chameleon Store Studio - Google Play $mode")
    sb.appendLine("Target Package: ${r.packageName}")
    editId?.let { sb.appendLine("Play Edit ID:   $it") }
    sb.appendLine(border)
    sb.appendLine("[Release on Track '${r.track}']")
    sb.appendLine("  Status: ${r.status.apiValue}" + (r.userFraction?.let { " ($it)" } ?: ""))
    r.releaseFiles.forEach { sb.appendLine("  - ${it.name} (${it.length()} bytes)") }
    r.mappingFile?.let { sb.appendLine("  Mapping file: ${it.name}") }
    r.debugSymbolsFile?.let { sb.appendLine("  Debug symbols: ${it.name}") }
    if (versionCodes.isNotEmpty()) sb.appendLine("  Version codes: ${versionCodes.joinToString()}")
    if (r.releaseNotesByLocale.isNotEmpty()) {
        sb.appendLine("  Release notes: ${r.releaseNotesByLocale.keys.sorted().joinToString()}")
    }
    sb.appendLine(border)
    when (status) {
        PlayPublishStatus.DRY_RUN_LOCAL ->
            sb.appendLine(
                "Offline dry-run complete. Configure credentials to validate with Google Play.",
            )

        PlayPublishStatus.DRY_RUN_VALIDATED ->
            sb.appendLine("Remote dry-run validated successfully with Google Play. Edit aborted.")

        PlayPublishStatus.COMMITTED -> if (r.existingEditId != null) {
            sb.appendLine(
                "Changes were staged in edit ${r.existingEditId} and NOT committed; " +
                    "commit that edit yourself to release.",
            )
        }

        else -> Unit
    }
    return sb.toString().trimEnd()
}

internal suspend fun stageBinaryInEdit(
    api: PlayPublisherApi,
    editId: String,
    token: String,
    r: PlayBinaryPublishRequest,
): ApiResponse<List<Long>> {
    val timeout = r.uploadTimeoutMs
    val newCodes = mutableListOf<Long>()
    val pkg = r.packageName
    for (file in r.releaseFiles) {
        val upload = api.uploadBundle(pkg, editId, file, token, timeout)
        when (upload) {
            is ApiResponse.Success -> newCodes += upload.data.versionCode
            is ApiResponse.Failure -> return upload.asFailure()
        }
    }
    val extras = listOfNotNull(
        r.mappingFile?.let { "proguard" to it },
        r.debugSymbolsFile?.let { "nativeCode" to it },
    )
    for (code in newCodes) {
        for ((type, file) in extras) {
            val up = api.uploadDeobfuscationFile(pkg, editId, code, type, file, token, timeout)
            if (up is ApiResponse.Failure) return up.asFailure()
        }
    }
    val allCodes = (r.retainVersionCodes + newCodes).distinct()
    val trackResponse = api.updateTrack(
        pkg,
        editId,
        r.track,
        trackBody(r, allCodes),
        token,
    )
    if (trackResponse is ApiResponse.Failure) return trackResponse.asFailure()
    return ApiResponse.Success(allCodes)
}

internal suspend fun publishBinary(
    api: PlayPublisherApi,
    request: PlayBinaryPublishRequest,
): ApiResponse<PlayBinaryPublishOutcome> {
    validateBinaryRequest(request)?.let { return localFailure(it) }
    val credentials = request.serviceAccountJson
    if (credentials.isNullOrBlank()) {
        if (!request.dryRun) {
            return localFailure(
                "Missing Google Play service account credentials. Provide " +
                    "serviceAccountJson in DSL or PLAY_SERVICE_ACCOUNT_JSON env var.",
            )
        }
        return ApiResponse.Success(
            PlayBinaryPublishOutcome(
                PlayPublishStatus.DRY_RUN_LOCAL,
                null,
                emptyList(),
                report(request, PlayPublishStatus.DRY_RUN_LOCAL, null, emptyList()),
            ),
        )
    }

    val token = when (val auth = api.authenticate(credentials)) {
        is ApiResponse.Success -> auth.data
        is ApiResponse.Failure -> return auth.asFailure()
    }
    val pkg = request.packageName
    val ownsEdit = request.existingEditId == null
    val editId = request.existingEditId ?: when (val insert = api.insertEdit(pkg, token)) {
        is ApiResponse.Success -> insert.data.id
        is ApiResponse.Failure -> return insert.asFailure()
    }

    var rollbackRequired = ownsEdit
    suspend fun fail(failure: ApiResponse.Failure<*>): ApiResponse<PlayBinaryPublishOutcome> {
        if (rollbackRequired) api.deleteEdit(pkg, editId, token)
        rollbackRequired = false
        return failure.asFailure()
    }

    try {
        val stageResult = stageBinaryInEdit(api, editId, token, request)
        val allCodes = when (stageResult) {
            is ApiResponse.Success -> stageResult.data
            is ApiResponse.Failure -> return fail(stageResult)
        }
        val validate = api.validateEdit(pkg, editId, token)
        if (validate is ApiResponse.Failure) return fail(validate)

        val status = if (request.dryRun) {
            PlayPublishStatus.DRY_RUN_VALIDATED
        } else {
            PlayPublishStatus.COMMITTED
        }
        if (request.dryRun) {
            if (ownsEdit) api.deleteEdit(pkg, editId, token)
            rollbackRequired = false
        } else if (ownsEdit) {
            val commit = api.commitEdit(pkg, editId, request.changesNotSentForReview, token)
            if (commit is ApiResponse.Failure) return fail(commit)
            rollbackRequired = false
        }
        return ApiResponse.Success(
            PlayBinaryPublishOutcome(
                status,
                editId,
                allCodes,
                report(request, status, editId, allCodes),
            ),
        )
    } catch (e: CancellationException) {
        if (rollbackRequired) {
            withContext(NonCancellable) { runCatching { api.deleteEdit(pkg, editId, token) } }
        }
        throw e
    } catch (e: Exception) {
        if (rollbackRequired) runCatching { api.deleteEdit(pkg, editId, token) }
        return ApiResponse.Failure.Exception(e)
    }
}
