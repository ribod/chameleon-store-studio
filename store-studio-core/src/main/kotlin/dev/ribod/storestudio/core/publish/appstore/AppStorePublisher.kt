package dev.ribod.storestudio.core.publish.appstore

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.ktor.deleteApiResponse
import com.skydoves.sandwich.ktor.getApiResponse
import com.skydoves.sandwich.ktor.patchApiResponse
import com.skydoves.sandwich.ktor.postApiResponse
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.http.installTimeoutAndRetry
import dev.ribod.storestudio.core.publish.PublishPart
import dev.ribod.storestudio.core.publish.asFailure
import dev.ribod.storestudio.core.publish.invalidExportFileMessage
import dev.ribod.storestudio.core.publish.map
import dev.ribod.storestudio.core.publish.resolveExportFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/**
 * High-level orchestrator for publishing visual marketing assets and localized listings
 * to Apple App Store Connect with transactional integrity and dry-run support.
 */
public class AppStorePublisher(internal val api: AppStorePublisherApi) : AutoCloseable {

    public companion object {
        public val EDITABLE_STATES: Set<String> = setOf(
            "PREPARE_FOR_SUBMISSION",
            "DEVELOPER_REJECTED",
            "REJECTED",
            "METADATA_REJECTED",
            "INVALID_BINARY",
        )

        /**
         * Creates a production [AppStorePublisher] backed by [KtorAppStorePublisherApi].
         */
        public fun create(engine: HttpClientEngine = OkHttp.create()): AppStorePublisher =
            AppStorePublisher(KtorAppStorePublisherApi.create(engine))
    }

    /**
     * Constructs a publication plan without mutating remote App Store Connect resources.
     */
    public fun buildPlan(
        request: AppStorePublishRequest,
        status: AppStorePublishStatus = if (request.dryRun) {
            AppStorePublishStatus.DRY_RUN_LOCAL
        } else {
            AppStorePublishStatus.COMMITTED
        },
    ): AppStorePublishPlan {
        val parts = request.parts
        val appStoreAssets = request.assets.filter {
            PublishPart.IMAGES in parts && it.target == StoreTarget.APP_STORE
        }
        val buckets = appStoreAssets.mapNotNull { asset ->
            val displayType = AscDisplayType.fromSlot(asset.deviceSlot) ?: return@mapNotNull null
            Triple(asset.storeLocale, displayType, asset.deviceSlot) to asset
        }.groupBy({ it.first }, { it.second })
            .map { (key, assets) ->
                val (locale, displayType, slot) = key
                AppStoreScreenshotBucketPlan(
                    locale = locale,
                    displayType = displayType,
                    slot = slot,
                    assets = assets.sortedBy { it.position },
                )
            }.sortedWith(compareBy({ it.locale }, { it.displayType.apiValue }))

        return AppStorePublishPlan(
            bundleId = request.bundleId,
            buckets = buckets,
            metadataByLocale = request.metadataByLocale.mapValues { (_, meta) ->
                meta.forParts(parts)
            },
            status = status,
            parts = parts,
            binary = request.binary,
        )
    }

    /**
     * Executes the publication workflow for the given [request].
     */
    public suspend fun publish(
        request: AppStorePublishRequest,
    ): ApiResponse<AppStorePublishOutcome> {
        val validatedBinary = if (request.binary != null) {
            when (val v = validateAndInspectIpa(request.binary)) {
                is ApiResponse.Success -> v.data
                is ApiResponse.Failure -> return v.asFailure()
            }
        } else {
            null
        }

        val initialPlan = buildPlan(request).copy(
            versionString = validatedBinary?.version,
            buildNumber = validatedBinary?.buildNumber,
        )

        val hasCredentials = !request.keyId.isNullOrBlank() &&
            !request.issuerId.isNullOrBlank() &&
            !request.privateKey.isNullOrBlank()

        if (request.dryRun && !hasCredentials) {
            val report = formatPlanReport(initialPlan, null, null)
            return ApiResponse.Success(
                AppStorePublishOutcome(
                    status = AppStorePublishStatus.DRY_RUN_LOCAL,
                    appId = null,
                    versionId = null,
                    plan = initialPlan,
                    formattedReport = report,
                    versionString = validatedBinary?.version,
                    buildNumber = validatedBinary?.buildNumber,
                ),
            )
        }

        if (!hasCredentials) {
            return ApiResponse.Failure.Error(
                "Missing App Store Connect credentials. Configure keyId, issuerId, and " +
                    "privateKey in DSL or ASC_KEY_ID, ASC_ISSUER_ID, ASC_PRIVATE_KEY env vars.",
            )
        }

        // A dry run uploads nothing, so it does not need the files
        if (!request.dryRun) {
            invalidExportFileMessage(
                request.exportDir,
                initialPlan.buckets.flatMap { b -> b.assets.map { it.file } },
            )?.let { return ApiResponse.Failure.Error("Store Studio: $it") }
        }

        val tokens = AppStoreTokens(request.keyId, request.issuerId, request.privateKey)
        fun freshToken() = tokens.fresh()

        val appsResponse = api.findApps(freshToken(), request.bundleId)
        val apps = when (appsResponse) {
            is ApiResponse.Success -> appsResponse.data.data
            is ApiResponse.Failure -> return appsResponse.asFailure()
        }
        val app = apps.firstOrNull() ?: return ApiResponse.Failure.Error(
            "Store Studio: No application found with bundle ID '${request.bundleId}' " +
                "on App Store Connect.",
        )
        val appId = app.id ?: return ApiResponse.Failure.Error(
            "Store Studio: App Store Connect returned application with missing ID.",
        )

        val needsEditableVersion =
            request.parts.isNotEmpty() || request.binary?.submitForReview == true
        val editableVersion = if (needsEditableVersion) {
            val versionsResponse = api.listEditableVersions(freshToken(), appId)
            val versions = when (versionsResponse) {
                is ApiResponse.Success -> versionsResponse.data.data
                is ApiResponse.Failure -> return versionsResponse.asFailure()
            }
            val editableVersions = versions.filter { version ->
                version.attributes?.appStoreState in EDITABLE_STATES
            }
            if (request.binary != null && request.binary.submitForReview) {
                editableVersions.firstOrNull {
                    it.attributes?.versionString == validatedBinary!!.version
                } ?: return ApiResponse.Failure.Error(
                    "Store Studio: No editable App Store version '${validatedBinary!!.version}' " +
                        "to submit; create it in App Store Connect first.",
                )
            } else {
                val found = editableVersions.firstOrNull()
                if (found == null && request.parts.isNotEmpty()) {
                    return ApiResponse.Failure.Error(
                        "Store Studio: No editable version found for '${request.bundleId}'. " +
                            "Ensure an app version is in PREPARE_FOR_SUBMISSION or REJECTED " +
                            "state in App Store Connect.",
                    )
                }
                found
            }
        } else {
            null
        }
        val versionId = if (editableVersion != null) {
            editableVersion.id ?: return ApiResponse.Failure.Error(
                "Store Studio: App Store Connect returned editable version with missing ID.",
            )
        } else {
            null
        }

        val groupIds = if (request.binary != null) {
            when (
                val g = resolveAppStoreBetaGroups(
                    api = api,
                    appId = appId,
                    bundleId = request.bundleId,
                    requestedNames = request.binary.betaGroups,
                    token = ::freshToken,
                )
            ) {
                is ApiResponse.Success -> g.data
                is ApiResponse.Failure -> return g.asFailure()
            }
        } else {
            emptyList()
        }

        if (request.dryRun) {
            val validatedPlan = initialPlan.copy(status = AppStorePublishStatus.DRY_RUN_VALIDATED)
            val report = formatPlanReport(validatedPlan, appId, versionId)
            return ApiResponse.Success(
                AppStorePublishOutcome(
                    status = AppStorePublishStatus.DRY_RUN_VALIDATED,
                    appId = appId,
                    versionId = versionId,
                    plan = validatedPlan,
                    formattedReport = report,
                    versionString = validatedBinary?.version,
                    buildNumber = validatedBinary?.buildNumber,
                ),
            )
        }

        val buildId = if (request.binary != null) {
            when (
                val u = uploadAndDistributeIpaBuild(
                    api = api,
                    appId = appId,
                    r = request.binary,
                    version = validatedBinary!!.version,
                    buildNumber = validatedBinary.buildNumber,
                    groupIds = groupIds,
                    token = ::freshToken,
                )
            ) {
                is ApiResponse.Success -> u.data
                is ApiResponse.Failure -> return u.asFailure()
            }
        } else {
            null
        }

        val textSelected = PublishPart.LISTING in request.parts ||
            PublishPart.RELEASE_NOTES in request.parts
        if (versionId != null && (initialPlan.buckets.isNotEmpty() || textSelected)) {
            val locResponse = api.listVersionLocalizations(freshToken(), versionId)
            val locResources = when (locResponse) {
                is ApiResponse.Success -> locResponse.data.data
                is ApiResponse.Failure -> return locResponse.asFailure()
            }
            val localeToLocId = locResources.associate {
                (it.attributes?.locale.orEmpty()) to (it.id.orEmpty())
            }

            for (bucket in initialPlan.buckets) {
                val locId = localeToLocId[bucket.locale] ?: return ApiResponse.Failure.Error(
                    "Store Studio: Localization '${bucket.locale}' not found in App Store version.",
                )

                val setsResponse = api.listScreenshotSets(freshToken(), locId)
                val existingSets = when (setsResponse) {
                    is ApiResponse.Success -> setsResponse.data.data
                    is ApiResponse.Failure -> return setsResponse.asFailure()
                }

                val targetSet = existingSets.firstOrNull {
                    it.attributes?.screenshotDisplayType == bucket.displayType.apiValue
                }
                val setId = if (targetSet != null) {
                    targetSet.id.orEmpty()
                } else {
                    val createResponse = api.createScreenshotSet(
                        token = freshToken(),
                        localizationId = locId,
                        displayType = bucket.displayType.apiValue,
                    )
                    when (createResponse) {
                        is ApiResponse.Success -> createResponse.data.data.id.orEmpty()
                        is ApiResponse.Failure -> return createResponse.asFailure()
                    }
                }

                val listResponse = api.listScreenshots(freshToken(), setId)
                val existingScreenshots = when (listResponse) {
                    is ApiResponse.Success -> listResponse.data.data
                    is ApiResponse.Failure -> return listResponse.asFailure()
                }
                // New screenshots go up first so a failed upload keeps the old ones. A set holds
                // at most MAX_SCREENSHOTS_PER_SET, so only the overflow is deleted up front.
                val oldIds = existingScreenshots.mapNotNull { it.id }
                val overflow = (oldIds.size + bucket.assets.size - MAX_SCREENSHOTS_PER_SET)
                    .coerceIn(0, oldIds.size)
                suspend fun deleteOld(ids: List<String>): ApiResponse.Failure<*>? {
                    for (id in ids) {
                        val del = api.deleteScreenshot(freshToken(), id)
                        if (del is ApiResponse.Failure) return del
                    }
                    return null
                }
                deleteOld(oldIds.take(overflow))?.let { return it.asFailure() }

                val uploadedIds = mutableListOf<String>()
                for (asset in bucket.assets) {
                    val file = resolveExportFile(request.exportDir, asset.file)!!
                    val fileBytes = file.readBytes()
                    val reserveResponse = api.reserveScreenshot(
                        token = freshToken(),
                        screenshotSetId = setId,
                        fileName = file.name,
                        fileSize = fileBytes.size.toLong(),
                    )
                    val reservation = when (reserveResponse) {
                        is ApiResponse.Success -> reserveResponse.data.data
                        is ApiResponse.Failure -> return reserveResponse.asFailure()
                    }
                    val screenshotId = reservation.id ?: return ApiResponse.Failure.Error(
                        "Store Studio: Screenshot reservation returned missing ID.",
                    )
                    val operations = reservation.attributes?.uploadOperations.orEmpty()

                    for (op in operations) {
                        uploadOperationRangeError(op, fileBytes.size.toLong())?.let {
                            return ApiResponse.Failure.Error("Store Studio: $it")
                        }
                        val chunk = fileBytes.copyOfRange(
                            op.offset.toInt(),
                            (op.offset + op.length).toInt(),
                        )
                        val uploadResponse = api.executeUploadOperation(op, chunk)
                        if (uploadResponse is ApiResponse.Failure) {
                            return uploadResponse.asFailure()
                        }
                    }

                    val commitResponse = api.commitScreenshot(freshToken(), screenshotId, asset.md5)
                    if (commitResponse is ApiResponse.Failure) {
                        return commitResponse.asFailure()
                    }

                    var isComplete = false
                    var attempts = 0
                    while (!isComplete && attempts < request.maxPollAttempts) {
                        attempts++
                        delay(request.pollIntervalMs)
                        val pollResponse = api.getScreenshot(freshToken(), screenshotId)
                        val state = when (pollResponse) {
                            is ApiResponse.Success ->
                                pollResponse.data.data.attributes?.assetDeliveryState?.state

                            is ApiResponse.Failure -> return pollResponse.asFailure()
                        }
                        if (state == "COMPLETE") {
                            isComplete = true
                        } else if (state == "FAILED") {
                            return ApiResponse.Failure.Error(
                                "Store Studio: App Store rejected screenshot '${file.name}'.",
                            )
                        }
                    }
                    if (!isComplete) {
                        return ApiResponse.Failure.Error(
                            "Store Studio: Timed out waiting for App Store to process '${file.name}'.",
                        )
                    }
                    uploadedIds.add(screenshotId)
                }

                deleteOld(oldIds.drop(overflow))?.let { return it.asFailure() }

                if (uploadedIds.isNotEmpty()) {
                    val reorderResponse = api.reorderScreenshots(freshToken(), setId, uploadedIds)
                    if (reorderResponse is ApiResponse.Failure) {
                        return reorderResponse.asFailure()
                    }
                }
            }

            // The plan holds only the fields of the selected parts; the others stay as they are
            val textToSend = if (textSelected) initialPlan.metadataByLocale else emptyMap()
            for ((locale, meta) in textToSend) {
                val locId = localeToLocId[locale] ?: continue
                val updateResponse = api.updateLocalizationMetadata(freshToken(), locId, meta)
                if (updateResponse is ApiResponse.Failure) {
                    return updateResponse.asFailure()
                }
            }

            if (PublishPart.LISTING in request.parts) {
                val infoFailure = publishAppInfoLocalizations(
                    freshToken(),
                    appId,
                    initialPlan.metadataByLocale,
                )
                if (infoFailure != null) return infoFailure
            }
        }

        if (
            request.binary != null &&
            buildId != null &&
            editableVersion != null &&
            versionId != null &&
            (
                editableVersion.attributes?.versionString == validatedBinary!!.version ||
                    request.binary.submitForReview
                )
        ) {
            when (
                val attachResult = attachAndSubmitVersionForReview(
                    api = api,
                    appId = appId,
                    versionId = versionId,
                    buildId = buildId,
                    submitForReview = request.binary.submitForReview,
                    platform = request.binary.platform,
                    token = ::freshToken,
                )
            ) {
                is ApiResponse.Success -> Unit
                is ApiResponse.Failure -> return attachResult.asFailure()
            }
        }

        val finalPlan = initialPlan.copy(buildId = buildId)
        val report = formatPlanReport(finalPlan, appId, versionId)
        return ApiResponse.Success(
            AppStorePublishOutcome(
                status = AppStorePublishStatus.COMMITTED,
                appId = appId,
                versionId = versionId,
                plan = finalPlan,
                formattedReport = report,
                buildId = buildId,
                versionString = validatedBinary?.version,
                buildNumber = validatedBinary?.buildNumber,
            ),
        )
    }

    /**
     * Publishes the app name and subtitle, which live on the editable app info rather than on
     * the version. Returns a failure, or null when done (or when there is nothing to send).
     */
    private suspend fun publishAppInfoLocalizations(
        token: String,
        appId: String,
        metadataByLocale: Map<String, AppStoreLocalizedMetadata>,
    ): ApiResponse<AppStorePublishOutcome>? {
        val withInfoTexts = metadataByLocale.filterValues { it.name != null || it.subtitle != null }
        if (withInfoTexts.isEmpty()) return null

        val infosResponse = api.listAppInfos(token, appId)
        val infos = when (infosResponse) {
            is ApiResponse.Success -> infosResponse.data.data
            is ApiResponse.Failure -> return infosResponse.asFailure()
        }
        val appInfoId = infos.firstOrNull { it.attributes?.appStoreState in EDITABLE_STATES }?.id
            ?: return ApiResponse.Failure.Error(
                "Store Studio: No editable app info found to publish the name and subtitle. " +
                    "Ensure the app has a version in PREPARE_FOR_SUBMISSION or REJECTED state.",
            )

        val locsResponse = api.listAppInfoLocalizations(token, appInfoId)
        val localeToId = when (locsResponse) {
            is ApiResponse.Success -> locsResponse.data.data.associate {
                it.attributes?.locale.orEmpty() to it.id.orEmpty()
            }

            is ApiResponse.Failure -> return locsResponse.asFailure()
        }
        for ((locale, meta) in withInfoTexts) {
            val existingId = localeToId[locale]
            val response = if (existingId != null) {
                api.updateAppInfoLocalization(token, existingId, meta.name, meta.subtitle)
            } else {
                api.createAppInfoLocalization(token, appInfoId, locale, meta.name, meta.subtitle)
            }
            if (response is ApiResponse.Failure) return response.asFailure()
        }
        return null
    }

    private fun formatPlanReport(
        plan: AppStorePublishPlan,
        appId: String?,
        versionId: String?,
    ): String {
        val border = "=".repeat(80)
        val sb = StringBuilder()
        sb.appendLine(border)
        val titleLine = when (plan.status) {
            AppStorePublishStatus.COMMITTED -> "Store Studio: App Store Connect Publication Result"

            AppStorePublishStatus.DRY_RUN_LOCAL ->
                "Store Studio: App Store Connect Publish Plan (Offline Dry Run)"

            AppStorePublishStatus.DRY_RUN_VALIDATED ->
                "Store Studio: App Store Connect Publish Plan (Remote Dry Run)"

            AppStorePublishStatus.FAILED -> "Store Studio: App Store Connect Publication Failed"
        }
        sb.appendLine(titleLine)
        val appInfo = if (appId != null && versionId != null) {
            "Bundle: ${plan.bundleId} (App ID: $appId, Version ID: $versionId)"
        } else if (appId != null) {
            "Bundle: ${plan.bundleId} (App ID: $appId)"
        } else {
            "Bundle: ${plan.bundleId}"
        }
        sb.appendLine(appInfo)
        sb.appendLine(border)
        if (plan.binary != null) appendBinary(sb, plan)
        if (PublishPart.IMAGES in plan.parts) appendScreenshots(sb, plan)
        if (PublishPart.LISTING in plan.parts || PublishPart.RELEASE_NOTES in plan.parts) {
            appendMetadata(sb, plan)
        }
        sb.appendLine(border)
        when (plan.status) {
            AppStorePublishStatus.DRY_RUN_LOCAL -> sb.appendLine(
                "Offline dry-run complete. Configure credentials to validate " +
                    "with App Store Connect.",
            )

            AppStorePublishStatus.DRY_RUN_VALIDATED -> sb.appendLine(
                "Remote dry-run validated successfully with App Store Connect. " +
                    "No mutations committed.",
            )

            AppStorePublishStatus.COMMITTED -> sb.appendLine(
                "Publication committed successfully to App Store Connect.",
            )

            AppStorePublishStatus.FAILED -> sb.appendLine("Publication failed.")
        }
        return sb.toString().trimEnd()
    }

    private fun appendBinary(sb: StringBuilder, plan: AppStorePublishPlan) {
        val binary = plan.binary ?: return
        sb.appendLine("[IPA Build Upload]")
        sb.appendLine("  • File: ${binary.ipaFile.name} (${binary.ipaFile.length()} bytes)")
        val versionLabel = when {
            plan.versionString != null && plan.buildNumber != null ->
                "${plan.versionString} (${plan.buildNumber})"

            else -> "${binary.bundleShortVersion ?: "?"} (${binary.bundleVersion ?: "?"})"
        }
        sb.appendLine("  • Version: $versionLabel")
        sb.appendLine("  • Platform: ${binary.platform}")
        plan.buildId?.let { sb.appendLine("  • Build ID: $it") }
        val whatToTestLocales = binary.whatToTestByLocale.keys.sorted()
        sb.appendLine(
            "  • TestFlight What to Test: ${whatToTestLocales.ifEmpty {
                listOf("-")
            }.joinToString(", ")}",
        )
        sb.appendLine(
            "  • Beta groups: ${binary.betaGroups.ifEmpty { listOf("-") }.joinToString(", ")}",
        )
        sb.appendLine("  • Beta review: ${binary.submitBetaReview}")
        sb.appendLine("  • App Store review: ${binary.submitForReview}")
    }

    private fun appendScreenshots(sb: StringBuilder, plan: AppStorePublishPlan) {
        sb.appendLine("[Screenshots]")
        if (plan.buckets.isEmpty()) {
            sb.appendLine("  (No App Store screenshots found in export index)")
        } else {
            for (bucket in plan.buckets) {
                val line = "  • %-7s / %-24s : %d images (slot: %s)"
                    .format(
                        bucket.locale,
                        bucket.displayType.apiValue,
                        bucket.assets.size,
                        bucket.slot,
                    )
                sb.appendLine(line)
            }
        }
    }

    private fun appendMetadata(sb: StringBuilder, plan: AppStorePublishPlan) {
        sb.appendLine("[Metadata]")
        if (plan.metadataByLocale.isEmpty()) {
            sb.appendLine("  (No localized text metadata found)")
            return
        }
        for ((locale, meta) in plan.metadataByLocale) {
            val fields = buildList {
                if (PublishPart.LISTING in plan.parts) {
                    add("Name=" + (meta.name?.let { "\"$it\"" } ?: "<none>"))
                    add("Subtitle=" + (meta.subtitle?.let { "\"$it\"" } ?: "<none>"))
                    add("Support=" + (meta.supportUrl ?: "<none>"))
                    add("Marketing=" + (meta.marketingUrl ?: "<none>"))
                    add("Desc=" + (meta.description?.let { "(${it.length} chars)" } ?: "<none>"))
                    add("Keywords=" + (meta.keywords?.let { "\"$it\"" } ?: "<none>"))
                }
                if (PublishPart.RELEASE_NOTES in plan.parts) {
                    add("WhatsNew=" + (meta.whatsNew?.let { "(${it.length} chars)" } ?: "<none>"))
                }
            }
            sb.appendLine("  • %-7s : %s".format(locale, fields.joinToString(" ")))
        }
    }

    override fun close() {
        api.close()
    }
}

/**
 * Concrete implementation of [AppStorePublisherApi] communicating with App Store Connect API v1.
 */
public class KtorAppStorePublisherApi(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : AppStorePublisherApi {

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://api.appstoreconnect.apple.com"

        private val JSON: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
            explicitNulls = false
        }

        public fun create(engine: HttpClientEngine = OkHttp.create()): KtorAppStorePublisherApi {
            val httpClient = HttpClient(engine) {
                install(ContentNegotiation) {
                    json(JSON)
                }
                installTimeoutAndRetry()
            }
            return KtorAppStorePublisherApi(httpClient)
        }
    }

    override suspend fun findApps(
        token: String,
        bundleId: String,
    ): ApiResponse<AscListResponse<AscAppAttributes>> {
        val url = "$baseUrl/v1/apps?filter[bundleId]=${bundleId.encodeURLParameter()}"
        return client.getApiResponse(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/json")
        }
    }

    override suspend fun listEditableVersions(
        token: String,
        appId: String,
    ): ApiResponse<AscListResponse<AscVersionAttributes>> {
        val states = AppStorePublisher.EDITABLE_STATES.joinToString(",")
        val url = "$baseUrl/v1/apps/$appId/appStoreVersions?filter[appStoreState]=$states"
        return client.getApiResponse(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/json")
        }
    }

    override suspend fun listVersionLocalizations(
        token: String,
        versionId: String,
    ): ApiResponse<AscListResponse<AscLocalizationAttributes>> {
        val url = "$baseUrl/v1/appStoreVersions/$versionId/appStoreVersionLocalizations"
        return client.getApiResponse(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/json")
        }
    }

    override suspend fun listScreenshotSets(
        token: String,
        localizationId: String,
    ): ApiResponse<AscListResponse<AscScreenshotSetAttributes>> {
        val url = "$baseUrl/v1/appStoreVersionLocalizations/$localizationId/appScreenshotSets"
        return client.getApiResponse(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/json")
        }
    }

    override suspend fun createScreenshotSet(
        token: String,
        localizationId: String,
        displayType: String,
    ): ApiResponse<AscSingleResponse<AscScreenshotSetAttributes>> {
        val url = "$baseUrl/v1/appScreenshotSets"
        val payload = AscCreateScreenshotSetRequest(
            data = AscCreateScreenshotSetData(
                attributes = AscCreateScreenshotSetAttributes(screenshotDisplayType = displayType),
                relationships = AscCreateScreenshotSetRelationships(
                    appStoreVersionLocalization = AscRelationshipDataWrapper(
                        data = AscRelationshipItem(
                            type = "appStoreVersionLocalizations",
                            id = localizationId,
                        ),
                    ),
                ),
            ),
        )
        return client.postApiResponse(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }

    override suspend fun listScreenshots(
        token: String,
        screenshotSetId: String,
    ): ApiResponse<AscListResponse<AscScreenshotAttributes>> {
        val url = "$baseUrl/v1/appScreenshotSets/$screenshotSetId/appScreenshots"
        return client.getApiResponse(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/json")
        }
    }

    override suspend fun deleteScreenshot(token: String, screenshotId: String): ApiResponse<Unit> {
        val url = "$baseUrl/v1/appScreenshots/$screenshotId"
        val response = client.deleteApiResponse<String>(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        return response.map {}
    }

    override suspend fun reserveScreenshot(
        token: String,
        screenshotSetId: String,
        fileName: String,
        fileSize: Long,
    ): ApiResponse<AscSingleResponse<AscScreenshotAttributes>> {
        val url = "$baseUrl/v1/appScreenshots"
        val payload = AscReserveScreenshotRequest(
            data = AscReserveScreenshotData(
                attributes = AscReserveScreenshotAttributes(
                    fileName = fileName,
                    fileSize = fileSize,
                ),
                relationships = AscReserveScreenshotRelationships(
                    appScreenshotSet = AscRelationshipDataWrapper(
                        data = AscRelationshipItem(
                            type = "appScreenshotSets",
                            id = screenshotSetId,
                        ),
                    ),
                ),
            ),
        )
        return client.postApiResponse(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }

    override suspend fun executeUploadOperation(
        operation: AscUploadOperation,
        chunkBytes: ByteArray,
    ): ApiResponse<Unit> = try {
        val response = client.request(operation.url) {
            this.method = HttpMethod.parse(operation.method)
            for (hdr in operation.requestHeaders) {
                header(hdr.name, hdr.value)
            }
            setBody(chunkBytes)
        }
        if (response.status.isSuccess()) {
            ApiResponse.Success(Unit)
        } else {
            ApiResponse.Failure.Error(
                "Chunk upload failed with status ${response.status.value}",
            )
        }
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        ApiResponse.Failure.Exception(e)
    }

    override suspend fun commitScreenshot(
        token: String,
        screenshotId: String,
        md5Checksum: String,
    ): ApiResponse<AscSingleResponse<AscScreenshotAttributes>> {
        val url = "$baseUrl/v1/appScreenshots/$screenshotId"
        val payload = AscCommitScreenshotRequest(
            data = AscCommitScreenshotData(
                id = screenshotId,
                attributes = AscCommitScreenshotAttributes(
                    uploaded = true,
                    sourceFileChecksum = md5Checksum,
                ),
            ),
        )
        return client.patchApiResponse(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }

    override suspend fun getScreenshot(
        token: String,
        screenshotId: String,
    ): ApiResponse<AscSingleResponse<AscScreenshotAttributes>> {
        val url = "$baseUrl/v1/appScreenshots/$screenshotId"
        return client.getApiResponse(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/json")
        }
    }

    override suspend fun reorderScreenshots(
        token: String,
        screenshotSetId: String,
        screenshotIds: List<String>,
    ): ApiResponse<Unit> {
        val url = "$baseUrl/v1/appScreenshotSets/$screenshotSetId/relationships/appScreenshots"
        val payload = AscReorderScreenshotsRequest(
            data = screenshotIds.map { AscRelationshipItem(type = "appScreenshots", id = it) },
        )
        val response = client.patchApiResponse<String>(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        return response.map {}
    }

    override suspend fun updateLocalizationMetadata(
        token: String,
        localizationId: String,
        metadata: AppStoreLocalizedMetadata,
    ): ApiResponse<AscSingleResponse<AscLocalizationAttributes>> {
        val url = "$baseUrl/v1/appStoreVersionLocalizations/$localizationId"
        val payload = AscUpdateLocalizationRequest(
            data = AscUpdateLocalizationData(
                id = localizationId,
                attributes = AscLocalizationAttributes(
                    description = metadata.description?.ifBlank { null },
                    keywords = metadata.keywords?.ifBlank { null },
                    promotionalText = metadata.promotionalText?.ifBlank { null },
                    whatsNew = metadata.whatsNew?.ifBlank { null },
                    supportUrl = metadata.supportUrl?.ifBlank { null },
                    marketingUrl = metadata.marketingUrl?.ifBlank { null },
                ),
            ),
        )
        return client.patchApiResponse(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }

    override suspend fun listAppInfos(
        token: String,
        appId: String,
    ): ApiResponse<AscListResponse<AscAppInfoAttributes>> {
        val url = "$baseUrl/v1/apps/$appId/appInfos"
        return client.getApiResponse(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/json")
        }
    }

    override suspend fun listAppInfoLocalizations(
        token: String,
        appInfoId: String,
    ): ApiResponse<AscListResponse<AscAppInfoLocalizationAttributes>> {
        val url = "$baseUrl/v1/appInfos/$appInfoId/appInfoLocalizations"
        return client.getApiResponse(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/json")
        }
    }

    override suspend fun updateAppInfoLocalization(
        token: String,
        appInfoLocalizationId: String,
        name: String?,
        subtitle: String?,
    ): ApiResponse<AscSingleResponse<AscAppInfoLocalizationAttributes>> {
        val url = "$baseUrl/v1/appInfoLocalizations/$appInfoLocalizationId"
        val payload = AscUpdateAppInfoLocalizationRequest(
            data = AscUpdateAppInfoLocalizationData(
                id = appInfoLocalizationId,
                attributes = AscAppInfoLocalizationAttributes(name = name, subtitle = subtitle),
            ),
        )
        return client.patchApiResponse(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }

    override suspend fun createAppInfoLocalization(
        token: String,
        appInfoId: String,
        locale: String,
        name: String?,
        subtitle: String?,
    ): ApiResponse<AscSingleResponse<AscAppInfoLocalizationAttributes>> {
        val url = "$baseUrl/v1/appInfoLocalizations"
        val payload = AscCreateAppInfoLocalizationRequest(
            data = AscCreateAppInfoLocalizationData(
                attributes = AscAppInfoLocalizationAttributes(
                    locale = locale,
                    name = name,
                    subtitle = subtitle,
                ),
                relationships = AscCreateAppInfoLocalizationRelationships(
                    appInfo = AscRelationshipDataWrapper(
                        data = AscRelationshipItem(type = "appInfos", id = appInfoId),
                    ),
                ),
            ),
        )
        return client.postApiResponse(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }

    private fun HttpRequestBuilder.json(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
    }

    private fun HttpRequestBuilder.accept(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
        header(HttpHeaders.Accept, "application/json")
    }

    override suspend fun createBuildUpload(
        token: String,
        appId: String,
        shortVersion: String,
        buildVersion: String,
        platform: String,
    ): ApiResponse<AscSingleResponse<AscBuildUploadAttributes>> =
        client.postApiResponse("$baseUrl/v1/buildUploads") {
            json(token)
            setBody(
                AscBody(
                    AscBodyData(
                        type = "buildUploads",
                        attributes = BuildUploadCreateAttrs(shortVersion, buildVersion, platform),
                        relationships = AppRel(AscRel(AscRelationshipItem("apps", appId))),
                    ),
                ),
            )
        }

    override suspend fun createBuildUploadFile(
        token: String,
        buildUploadId: String,
        fileName: String,
        fileSize: Long,
    ): ApiResponse<AscSingleResponse<AscBuildUploadFileAttributes>> =
        client.postApiResponse("$baseUrl/v1/buildUploadFiles") {
            json(token)
            setBody(
                AscBody(
                    AscBodyData(
                        type = "buildUploadFiles",
                        attributes = BuildUploadFileCreateAttrs(
                            fileName,
                            fileSize,
                            "com.apple.ipa",
                            "ASSET",
                        ),
                        relationships = BuildUploadRel(
                            AscRel(AscRelationshipItem("buildUploads", buildUploadId)),
                        ),
                    ),
                ),
            )
        }

    override suspend fun updateBuildUploadFile(
        token: String,
        fileId: String,
        uploaded: Boolean,
    ): ApiResponse<AscSingleResponse<AscBuildUploadFileAttributes>> =
        client.patchApiResponse("$baseUrl/v1/buildUploadFiles/$fileId") {
            json(token)
            setBody(
                AscBody<UploadedAttrs, AscNoRels>(
                    AscBodyData(
                        type = "buildUploadFiles",
                        id = fileId,
                        attributes = UploadedAttrs(uploaded),
                    ),
                ),
            )
        }

    override suspend fun getBuildUpload(
        token: String,
        buildUploadId: String,
    ): ApiResponse<AscBuildUploadResponse> =
        client.getApiResponse("$baseUrl/v1/buildUploads/$buildUploadId?include=build") {
            accept(token)
        }

    override suspend fun listBetaGroups(
        token: String,
        appId: String,
    ): ApiResponse<AscListResponse<AscBetaGroupAttributes>> =
        client.getApiResponse("$baseUrl/v1/apps/$appId/betaGroups?limit=200") { accept(token) }

    override suspend fun addBuildToBetaGroups(
        token: String,
        buildId: String,
        groupIds: List<String>,
    ): ApiResponse<Unit> {
        for (groupId in groupIds) {
            val r = client.postApiResponse<String>(
                "$baseUrl/v1/betaGroups/$groupId/relationships/builds",
            ) {
                json(token)
                setBody(BuildLinkagesBody(listOf(AscRelationshipItem("builds", buildId))))
            }
            if (r is ApiResponse.Failure) return r.map {}
        }
        return ApiResponse.Success(Unit)
    }

    override suspend fun listBetaBuildLocalizations(
        token: String,
        buildId: String,
    ): ApiResponse<AscListResponse<AscBetaBuildLocalizationAttributes>> =
        client.getApiResponse("$baseUrl/v1/builds/$buildId/betaBuildLocalizations?limit=200") {
            accept(token)
        }

    override suspend fun createBetaBuildLocalization(
        token: String,
        buildId: String,
        locale: String,
        whatsNew: String,
    ): ApiResponse<AscSingleResponse<AscBetaBuildLocalizationAttributes>> =
        client.postApiResponse("$baseUrl/v1/betaBuildLocalizations") {
            json(token)
            setBody(
                AscBody(
                    AscBodyData(
                        type = "betaBuildLocalizations",
                        attributes = LocaleWhatsNewAttrs(locale, whatsNew),
                        relationships = BuildRel(AscRel(AscRelationshipItem("builds", buildId))),
                    ),
                ),
            )
        }

    override suspend fun updateBetaBuildLocalization(
        token: String,
        localizationId: String,
        whatsNew: String,
    ): ApiResponse<AscSingleResponse<AscBetaBuildLocalizationAttributes>> =
        client.patchApiResponse("$baseUrl/v1/betaBuildLocalizations/$localizationId") {
            json(token)
            setBody(
                AscBody<LocaleWhatsNewAttrs, AscNoRels>(
                    AscBodyData(
                        type = "betaBuildLocalizations",
                        id = localizationId,
                        attributes = LocaleWhatsNewAttrs(whatsNew = whatsNew),
                    ),
                ),
            )
        }

    override suspend fun submitBetaReview(token: String, buildId: String): ApiResponse<Unit> =
        client.postApiResponse<String>("$baseUrl/v1/betaAppReviewSubmissions") {
            json(token)
            setBody(
                AscBody<AscNoAttrs, BuildRel>(
                    AscBodyData(
                        type = "betaAppReviewSubmissions",
                        relationships = BuildRel(AscRel(AscRelationshipItem("builds", buildId))),
                    ),
                ),
            )
        }.map {}

    override suspend fun attachBuildToVersion(
        token: String,
        versionId: String,
        buildId: String,
    ): ApiResponse<Unit> = client.patchApiResponse<String>(
        "$baseUrl/v1/appStoreVersions/$versionId/relationships/build",
    ) {
        json(token)
        setBody(BuildLinkageBody(AscRelationshipItem("builds", buildId)))
    }.map {}

    override suspend fun createReviewSubmission(
        token: String,
        appId: String,
        platform: String,
    ): ApiResponse<AscSingleResponse<AscReviewSubmissionAttributes>> =
        client.postApiResponse("$baseUrl/v1/reviewSubmissions") {
            json(token)
            setBody(
                AscBody(
                    AscBodyData(
                        type = "reviewSubmissions",
                        attributes = PlatformAttrs(platform),
                        relationships = AppRel(AscRel(AscRelationshipItem("apps", appId))),
                    ),
                ),
            )
        }

    override suspend fun createReviewSubmissionItem(
        token: String,
        reviewSubmissionId: String,
        versionId: String,
    ): ApiResponse<Unit> = client.postApiResponse<String>("$baseUrl/v1/reviewSubmissionItems") {
        json(token)
        setBody(
            AscBody<AscNoAttrs, ReviewItemRel>(
                AscBodyData(
                    type = "reviewSubmissionItems",
                    relationships = ReviewItemRel(
                        AscRel(AscRelationshipItem("reviewSubmissions", reviewSubmissionId)),
                        AscRel(AscRelationshipItem("appStoreVersions", versionId)),
                    ),
                ),
            ),
        )
    }.map {}

    override suspend fun submitReviewSubmission(
        token: String,
        reviewSubmissionId: String,
    ): ApiResponse<AscSingleResponse<AscReviewSubmissionAttributes>> =
        client.patchApiResponse("$baseUrl/v1/reviewSubmissions/$reviewSubmissionId") {
            json(token)
            setBody(
                AscBody<SubmittedAttrs, AscNoRels>(
                    AscBodyData(
                        type = "reviewSubmissions",
                        id = reviewSubmissionId,
                        attributes = SubmittedAttrs(true),
                    ),
                ),
            )
        }

    override fun close() {
        client.close()
    }
}

private const val MAX_SCREENSHOTS_PER_SET = 10

/** Keeps only the fields that belong to the selected [parts]. */
private fun AppStoreLocalizedMetadata.forParts(parts: Set<PublishPart>): AppStoreLocalizedMetadata {
    val listing = PublishPart.LISTING in parts
    val notes = PublishPart.RELEASE_NOTES in parts
    return AppStoreLocalizedMetadata(
        description = description.takeIf { listing },
        keywords = keywords.takeIf { listing },
        promotionalText = promotionalText.takeIf { listing },
        whatsNew = whatsNew.takeIf { notes },
        name = name.takeIf { listing },
        subtitle = subtitle.takeIf { listing },
        supportUrl = supportUrl.takeIf { listing },
        marketingUrl = marketingUrl.takeIf { listing },
    )
}
