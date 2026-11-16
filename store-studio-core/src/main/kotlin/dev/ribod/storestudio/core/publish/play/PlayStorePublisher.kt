package dev.ribod.storestudio.core.publish.play

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.ktor.deleteApiResponse
import com.skydoves.sandwich.ktor.getApiResponse
import com.skydoves.sandwich.ktor.postApiResponse
import com.skydoves.sandwich.ktor.putApiResponse
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.http.installTimeoutAndRetry
import dev.ribod.storestudio.core.publish.PublishPart
import dev.ribod.storestudio.core.publish.asFailure
import dev.ribod.storestudio.core.publish.invalidExportFileMessage
import dev.ribod.storestudio.core.publish.map
import dev.ribod.storestudio.core.publish.resolveExportFile
import dev.ribod.storestudio.model.ImageFormat
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.cio.readChannel
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.net.URLEncoder

/**
 * High-level orchestrator for publishing visual marketing assets and localized listings
 * to the Google Play Store with transactional integrity, dry-run support, and atomic rollback.
 */
public class PlayStorePublisher(private val api: PlayPublisherApi) : AutoCloseable {

    public companion object {
        /**
         * Creates a production [PlayStorePublisher] backed by [KtorPlayPublisherApi].
         */
        public fun create(engine: HttpClientEngine = OkHttp.create()): PlayStorePublisher =
            PlayStorePublisher(KtorPlayPublisherApi.create(engine))
    }

    /**
     * Executes the publication workflow for the given [request].
     */
    public suspend fun publish(request: PlayPublishRequest): ApiResponse<PlayPublishOutcome> {
        if (request.binary != null) {
            validateBinaryRequest(request.binary)?.let { return ApiResponse.Failure.Error(it) }
        }
        val parts = request.parts
        val doImages = PublishPart.IMAGES in parts
        val doListing = PublishPart.LISTING in parts
        // A track is optional only for a full publish; asking for the notes alone requires it
        val doNotes = PublishPart.RELEASE_NOTES in parts && request.track != null
        val notesWithoutTrack =
            request.binary == null && PublishPart.RELEASE_NOTES in parts && request.track == null
        if (notesWithoutTrack && parts != PublishPart.ALL) {
            return ApiResponse.Failure.Error(
                "Publishing Google Play release notes requires a track (internal, alpha, beta, " +
                    "production or a custom track). Set publishing.googlePlay.track or --track.",
            )
        }
        val listings = if (doListing) request.listingsByLocale else emptyMap()
        val playAssets = request.assets.filter { doImages && it.target == StoreTarget.PLAY_STORE }
        val assetsByBucket = playAssets.mapNotNull { asset ->
            val imageType = PlayImageType.fromDeviceSlot(asset.deviceSlot) ?: return@mapNotNull null
            (asset.storeLocale to imageType) to asset
        }.groupBy({ it.first }, { it.second })

        val planImages = assetsByBucket.map { (key, assets) ->
            val (locale, imageType) = key
            PlayPublishPlanItem(
                locale = locale,
                imageType = imageType,
                count = assets.size,
                files = assets.sortedBy { it.position }.map { it.file },
            )
        }.sortedWith(compareBy({ it.locale }, { it.imageType.name }))

        val planListings = listings.map { (locale, meta) ->
            PlayListingPlanItem(
                locale = locale,
                title = meta.title.ifBlank { null },
                shortDescription = meta.shortDescription.ifBlank { null },
                fullDescription = meta.fullDescription.ifBlank { null },
            )
        }.sortedBy { it.locale }

        val trackNotes = request.releaseNotesByLocale.filterValues { it.isNotBlank() }
        val trackWarnings = mutableListOf<String>()
        if (request.binary == null && doNotes && trackNotes.isEmpty()) {
            trackWarnings += "No release notes found in store metadata; " +
                "track '${request.track}' is left unchanged."
        }

        val initialPlan = PlayPublishPlan(
            packageName = request.packageName,
            editId = null,
            images = planImages,
            listings = planListings,
            status = if (request.dryRun) {
                PlayPublishStatus.DRY_RUN_LOCAL
            } else {
                PlayPublishStatus.COMMITTED
            },
            track = request.track,
            trackReleaseNoteLocales = if (doNotes) trackNotes.keys.sorted() else emptyList(),
            warnings = trackWarnings.toList(),
            parts = parts,
            binary = request.binary,
        )

        if (request.dryRun && request.serviceAccountJson.isNullOrBlank()) {
            val report = formatPlanReport(initialPlan)
            return ApiResponse.Success(
                PlayPublishOutcome(
                    status = PlayPublishStatus.DRY_RUN_LOCAL,
                    editId = null,
                    plan = initialPlan,
                    formattedReport = report,
                ),
            )
        }

        if (request.serviceAccountJson.isNullOrBlank()) {
            return ApiResponse.Failure.Error(
                "Missing Google Play service account credentials. Provide " +
                    "serviceAccountJson in DSL or PLAY_SERVICE_ACCOUNT_JSON env var.",
            )
        }

        // Checked before any remote call so a bad file cannot leave images deleted in the edit
        invalidExportFileMessage(request.exportDir, playAssets.map { it.file })
            ?.let { return ApiResponse.Failure.Error(it) }

        val authResponse = api.authenticate(request.serviceAccountJson)
        val token = when (authResponse) {
            is ApiResponse.Success -> authResponse.data
            is ApiResponse.Failure -> return authResponse.asFailure()
        }

        val ownsEdit = request.binary?.existingEditId == null
        val editId =
            request.binary?.existingEditId
                ?: when (val insertResponse = api.insertEdit(request.packageName, token)) {
                    is ApiResponse.Success -> insertResponse.data.id
                    is ApiResponse.Failure -> return insertResponse.asFailure()
                }

        var rollbackRequired = ownsEdit
        suspend fun <T> fail(failure: ApiResponse.Failure<*>): ApiResponse<T> {
            if (ownsEdit) api.deleteEdit(request.packageName, editId, token)
            rollbackRequired = false
            return failure.asFailure()
        }
        var stagedVersionCodes: List<Long> = emptyList()
        try {
            if (request.binary != null) {
                val stageResult = stageBinaryInEdit(api, editId, token, request.binary)
                when (stageResult) {
                    is ApiResponse.Success -> stagedVersionCodes = stageResult.data

                    is ApiResponse.Failure -> {
                        return fail(stageResult)
                    }
                }
            }

            for ((key, assets) in assetsByBucket) {
                val (locale, imageType) = key
                val deleteResponse = api.deleteAllImages(
                    request.packageName,
                    editId,
                    locale,
                    imageType,
                    token,
                )
                if (deleteResponse is ApiResponse.Failure) {
                    return fail(deleteResponse)
                }

                val sortedAssets = assets.sortedBy { it.position }
                for (asset in sortedAssets) {
                    val bytes = resolveExportFile(request.exportDir, asset.file)!!.readBytes()
                    val mime = if (asset.format == ImageFormat.JPEG) {
                        "image/jpeg"
                    } else {
                        "image/png"
                    }
                    val uploadResponse = api.uploadImage(
                        request.packageName,
                        editId,
                        locale,
                        imageType,
                        bytes,
                        mime,
                        token,
                    )
                    if (uploadResponse is ApiResponse.Failure) {
                        return fail(uploadResponse)
                    }
                }
            }

            if (doImages && request.appIconFile != null && request.appIconFile.exists()) {
                val defaultLocale = request.listingsByLocale.keys.firstOrNull() ?: "en-US"
                val iconBytes = request.appIconFile.readBytes()
                val iconResponse = api.uploadImage(
                    request.packageName,
                    editId,
                    defaultLocale,
                    PlayImageType.ICON,
                    iconBytes,
                    "image/png",
                    token,
                )
                if (iconResponse is ApiResponse.Failure) {
                    return fail(iconResponse)
                }
            }

            for ((locale, meta) in listings) {
                val listing = PlayListing(
                    language = locale,
                    title = meta.title.ifBlank { null },
                    shortDescription = meta.shortDescription.ifBlank { null },
                    fullDescription = meta.fullDescription.ifBlank { null },
                )
                val listingResponse = api.updateListing(
                    request.packageName,
                    editId,
                    locale,
                    listing,
                    token,
                )
                if (listingResponse is ApiResponse.Failure) {
                    return fail(listingResponse)
                }
            }

            if (request.binary == null && doNotes && trackNotes.isNotEmpty()) {
                val trackResponse = api.getTrack(request.packageName, editId, request.track, token)
                val trackJson = when (trackResponse) {
                    is ApiResponse.Success -> trackResponse.data

                    is ApiResponse.Failure -> {
                        return fail(trackResponse)
                    }
                }
                val updatedTrack = PlayTrackReleaseNotes.apply(trackJson, trackNotes)
                if (updatedTrack == null) {
                    trackWarnings += "Track '${request.track}' has no releases yet; " +
                        "release notes were not updated."
                } else {
                    val updateResponse = api.updateTrack(
                        request.packageName,
                        editId,
                        request.track,
                        updatedTrack,
                        token,
                    )
                    if (updateResponse is ApiResponse.Failure) {
                        return fail(updateResponse)
                    }
                }
            }

            val validateResponse = api.validateEdit(request.packageName, editId, token)
            if (validateResponse is ApiResponse.Failure) {
                return fail(validateResponse)
            }

            if (request.dryRun) {
                if (ownsEdit) api.deleteEdit(request.packageName, editId, token)
                rollbackRequired = false
                val finalPlan = initialPlan.copy(
                    editId = editId,
                    status = PlayPublishStatus.DRY_RUN_VALIDATED,
                    warnings = trackWarnings.toList(),
                    versionCodes = stagedVersionCodes,
                )
                return ApiResponse.Success(
                    PlayPublishOutcome(
                        status = PlayPublishStatus.DRY_RUN_VALIDATED,
                        editId = editId,
                        plan = finalPlan,
                        formattedReport = formatPlanReport(finalPlan),
                        versionCodes = stagedVersionCodes,
                    ),
                )
            } else {
                if (ownsEdit) {
                    val commitResponse = api.commitEdit(
                        request.packageName,
                        editId,
                        request.changesNotSentForReview,
                        token,
                    )
                    if (commitResponse is ApiResponse.Failure) {
                        api.deleteEdit(request.packageName, editId, token)
                        rollbackRequired = false
                        return commitResponse.asFailure()
                    }
                }
                rollbackRequired = false
                val finalPlan = initialPlan.copy(
                    editId = editId,
                    status = PlayPublishStatus.COMMITTED,
                    warnings = trackWarnings.toList(),
                    versionCodes = stagedVersionCodes,
                )
                return ApiResponse.Success(
                    PlayPublishOutcome(
                        status = PlayPublishStatus.COMMITTED,
                        editId = editId,
                        plan = finalPlan,
                        formattedReport = formatPlanReport(finalPlan),
                        versionCodes = stagedVersionCodes,
                    ),
                )
            }
        } catch (e: CancellationException) {
            if (rollbackRequired) {
                withContext(NonCancellable) {
                    runCatching { api.deleteEdit(request.packageName, editId, token) }
                }
            }
            throw e
        } catch (e: Exception) {
            if (rollbackRequired) {
                runCatching { api.deleteEdit(request.packageName, editId, token) }
            }
            return ApiResponse.Failure.Exception(e)
        }
    }

    /** Uploads AAB files to a track in one edit; see [PlayBinaryPublishRequest]. */
    public suspend fun publishBinary(
        request: PlayBinaryPublishRequest,
    ): ApiResponse<PlayBinaryPublishOutcome> = publishBinary(api, request)

    private fun formatPlanReport(plan: PlayPublishPlan): String {
        val sb = StringBuilder()
        val border = "=".repeat(80)
        sb.appendLine(border)
        val modeStr = when (plan.status) {
            PlayPublishStatus.COMMITTED -> "Published Successfully"
            PlayPublishStatus.DRY_RUN_LOCAL -> "Publish Plan (Offline Dry Run)"
            PlayPublishStatus.DRY_RUN_VALIDATED -> "Publish Plan (Validated Dry Run)"
            PlayPublishStatus.FAILED -> "Publish Failed"
        }
        sb.appendLine("🏪 Chameleon Store Studio — Google Play $modeStr")
        sb.appendLine("Target Package: ${plan.packageName}")
        if (plan.editId != null) {
            sb.appendLine("Play Edit ID:   ${plan.editId}")
        }
        sb.appendLine(border)
        if (plan.binary != null) {
            val b = plan.binary
            sb.appendLine("[Binary Release on Track '${b.track}']")
            sb.appendLine(
                "  Status: ${b.status.apiValue}" + (
                    b.userFraction?.let {
                        " ($it)"
                    } ?: ""
                    ),
            )
            b.releaseFiles.forEach { sb.appendLine("  - ${it.name} (${it.length()} bytes)") }
            b.mappingFile?.let { sb.appendLine("  Mapping file: ${it.name}") }
            b.debugSymbolsFile?.let { sb.appendLine("  Debug symbols: ${it.name}") }
            if (plan.versionCodes.isNotEmpty()) {
                sb.appendLine("  Version codes: ${plan.versionCodes.joinToString()}")
            }
            if (b.releaseNotesByLocale.isNotEmpty()) {
                sb.appendLine(
                    "  Release notes: ${b.releaseNotesByLocale.keys.sorted().joinToString()}",
                )
            }
        }
        if (PublishPart.IMAGES in plan.parts) appendImages(sb, plan)
        if (PublishPart.LISTING in plan.parts) appendListings(sb, plan)
        if (plan.binary == null && plan.track != null && PublishPart.RELEASE_NOTES in plan.parts) {
            sb.appendLine("[Release Notes for Track '${plan.track}']")
            if (plan.trackReleaseNoteLocales.isEmpty()) {
                sb.appendLine("  (No release notes scheduled)")
            } else {
                sb.appendLine("  • ${plan.trackReleaseNoteLocales.joinToString(", ")}")
            }
        }
        for (warning in plan.warnings) {
            sb.appendLine("  ⚠ $warning")
        }
        sb.appendLine(border)
        if (plan.status == PlayPublishStatus.DRY_RUN_LOCAL) {
            sb.appendLine(
                "Offline dry-run complete. Configure credentials to validate with Google Play.",
            )
        } else if (plan.status == PlayPublishStatus.DRY_RUN_VALIDATED) {
            sb.appendLine(
                "Remote dry-run validated successfully with Google Play. Edit aborted.",
            )
        }
        return sb.toString().trimEnd()
    }

    private fun appendImages(sb: StringBuilder, plan: PlayPublishPlan) {
        sb.appendLine("[Images Scheduled for Upload]")
        if (plan.images.isEmpty()) {
            sb.appendLine("  (No Play Store visual assets found)")
        } else {
            for (item in plan.images) {
                sb.appendLine(
                    "  • %-7s / %-22s: %d asset(s)".format(
                        item.locale,
                        item.imageType.apiValue,
                        item.count,
                    ),
                )
            }
        }
    }

    private fun appendListings(sb: StringBuilder, plan: PlayPublishPlan) {
        sb.appendLine("[Text Listings Scheduled for Sync]")
        if (plan.listings.isEmpty()) {
            sb.appendLine("  (No localized text listings found)")
        } else {
            for (listing in plan.listings) {
                val title = listing.title?.let { "\"$it\"" } ?: "<unchanged>"
                val short = listing.shortDescription?.let { "(${it.length} chars)" } ?: "<none>"
                val line = "  • %-7s: Title=%-20s ShortDesc=%s"
                    .format(listing.locale, title, short)
                sb.appendLine(line)
            }
        }
    }

    override fun close() {
        api.close()
    }
}

/**
 * Concrete implementation of [PlayPublisherApi] invoking Google Play Developer API v3
 * using Ktor Client and Sandwich.
 */
public class KtorPlayPublisherApi(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val uploadBaseUrl: String = DEFAULT_UPLOAD_BASE_URL,
) : PlayPublisherApi {

    public companion object {
        public const val DEFAULT_BASE_URL: String =
            "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
        public const val DEFAULT_UPLOAD_BASE_URL: String =
            "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"

        public const val DEFAULT_TIMEOUT_MS: Long = 2 * 60 * 1000L

        private val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        public fun create(engine: HttpClientEngine = OkHttp.create()): KtorPlayPublisherApi {
            val httpClient = HttpClient(engine) {
                install(ContentNegotiation) {
                    json(JSON)
                }
                installTimeoutAndRetry()
            }
            return KtorPlayPublisherApi(httpClient)
        }
    }

    override suspend fun authenticate(serviceAccountJson: String): ApiResponse<String> {
        // Parse/sign errors can quote private_key fragments, so their text is never propagated
        val (credentials, jwt) = try {
            PlayServiceAccountJwt.parseCredentials(serviceAccountJson).let {
                it to PlayServiceAccountJwt.createSignedJwt(it)
            }
        } catch (_: Exception) {
            return ApiResponse.Failure.Error("Invalid Google Play service account JSON")
        }

        val grantType = URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8")
        val assertion = URLEncoder.encode(jwt, "UTF-8")
        val formBody = "grant_type=$grantType&assertion=$assertion"

        val response = client.postApiResponse<OAuthTokenResponse>(credentials.token_uri) {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(formBody)
        }
        return response.map { it.access_token }
    }

    override suspend fun insertEdit(packageName: String, token: String): ApiResponse<PlayAppEdit> {
        val url = "$baseUrl/${encodePathSegment(packageName)}/edits"
        return client.postApiResponse<PlayAppEdit>(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/json")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
    }

    override suspend fun deleteAllImages(
        packageName: String,
        editId: String,
        language: String,
        imageType: PlayImageType,
        token: String,
    ): ApiResponse<Unit> {
        val url = "$baseUrl/${encodePathSegment(
            packageName,
        )}/edits/$editId/listings/${encodePathSegment(language)}/${imageType.apiValue}"
        val response = client.deleteApiResponse<String>(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        return response.map {}
    }

    override suspend fun uploadImage(
        packageName: String,
        editId: String,
        language: String,
        imageType: PlayImageType,
        imageBytes: ByteArray,
        mimeType: String,
        token: String,
    ): ApiResponse<PlayImage> {
        val path = "${encodePathSegment(
            packageName,
        )}/edits/$editId/listings/${encodePathSegment(language)}/${imageType.apiValue}"
        val url = "$uploadBaseUrl/$path?uploadType=media"
        return client.postApiResponse<PlayImage>(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.parse(mimeType))
            setBody(imageBytes)
        }
    }

    override suspend fun updateListing(
        packageName: String,
        editId: String,
        language: String,
        listing: PlayListing,
        token: String,
    ): ApiResponse<PlayListing> {
        val url = "$baseUrl/${encodePathSegment(
            packageName,
        )}/edits/$editId/listings/${encodePathSegment(language)}"
        return client.putApiResponse<PlayListing>(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/json")
            contentType(ContentType.Application.Json)
            setBody(listing)
        }
    }

    override suspend fun validateEdit(
        packageName: String,
        editId: String,
        token: String,
    ): ApiResponse<PlayAppEdit> {
        val url = "$baseUrl/${encodePathSegment(packageName)}/edits/$editId:validate"
        return client.postApiResponse<PlayAppEdit>(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/json")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
    }

    override suspend fun commitEdit(
        packageName: String,
        editId: String,
        changesNotSentForReview: Boolean,
        token: String,
    ): ApiResponse<PlayAppEdit> {
        val url = "$baseUrl/${encodePathSegment(packageName)}/edits/$editId:commit" +
            "?changesNotSentForReview=$changesNotSentForReview"
        return client.postApiResponse<PlayAppEdit>(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/json")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
    }

    override suspend fun deleteEdit(
        packageName: String,
        editId: String,
        token: String,
    ): ApiResponse<Unit> {
        val url = "$baseUrl/${encodePathSegment(packageName)}/edits/$editId"
        val response = client.deleteApiResponse<String>(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        return response.map {}
    }

    override suspend fun getTrack(
        packageName: String,
        editId: String,
        track: String,
        token: String,
    ): ApiResponse<JsonObject> {
        val url = "$baseUrl/${encodePathSegment(
            packageName,
        )}/edits/$editId/tracks/${encodePathSegment(track)}"
        return client.getApiResponse<JsonObject>(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/json")
        }
    }

    override suspend fun updateTrack(
        packageName: String,
        editId: String,
        track: String,
        trackJson: JsonObject,
        token: String,
    ): ApiResponse<JsonObject> {
        val url = "$baseUrl/${encodePathSegment(
            packageName,
        )}/edits/$editId/tracks/${encodePathSegment(track)}"
        return client.putApiResponse<JsonObject>(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/json")
            contentType(ContentType.Application.Json)
            setBody(trackJson)
        }
    }

    override suspend fun uploadBundle(
        packageName: String,
        editId: String,
        file: File,
        token: String,
        timeoutMs: Long,
    ): ApiResponse<PlayBundle> = uploadFile(
        "${encodePathSegment(packageName)}/edits/$editId/bundles",
        file,
        token,
        timeoutMs,
    )

    override suspend fun uploadDeobfuscationFile(
        packageName: String,
        editId: String,
        versionCode: Long,
        type: String,
        file: File,
        token: String,
        timeoutMs: Long,
    ): ApiResponse<Unit> {
        val path = "${encodePathSegment(
            packageName,
        )}/edits/$editId/apks/$versionCode/deobfuscationFiles/$type"
        return uploadFile<JsonObject>(path, file, token, timeoutMs).map {}
    }

    // Streams from disk so multi-hundred-MB bundles never sit in memory. Needs HttpTimeout installed.
    private suspend inline fun <reified T> uploadFile(
        path: String,
        file: File,
        token: String,
        timeoutMs: Long,
    ): ApiResponse<T> = client.postApiResponse<T>("$uploadBaseUrl/$path?uploadType=media") {
        header(HttpHeaders.Authorization, "Bearer $token")
        header(HttpHeaders.Accept, "application/json")
        timeout {
            requestTimeoutMillis = timeoutMs
            socketTimeoutMillis = timeoutMs
        }
        setBody(FileStreamContent(file))
    }

    private class FileStreamContent(private val file: File) : OutgoingContent.ReadChannelContent() {
        override val contentType: ContentType = ContentType.Application.OctetStream
        override val contentLength: Long = file.length()
        override fun readFrom(): ByteReadChannel = file.readChannel()
    }

    private fun encodePathSegment(segment: String): String =
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    override fun close() {
        client.close()
    }
}
