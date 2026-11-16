@file:Suppress("ktlint:standard:max-line-length")

package dev.ribod.storestudio.core.publish.appstore

import com.dd.plist.NSDictionary
import com.dd.plist.PropertyListParser
import com.skydoves.sandwich.ApiResponse
import dev.ribod.storestudio.core.publish.asFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile

private val INFO_PLIST = Regex("^Payload/[^/]+\\.app/Info\\.plist$")

internal data class ValidatedIpaBinary(val version: String, val buildNumber: String)

internal fun validateAndInspectIpa(
    r: AppStoreBinaryPublishRequest,
): ApiResponse<ValidatedIpaBinary> {
    val ipa = r.ipaFile
    if (!ipa.isFile) return fail("IPA file not found: ${ipa.path}")
    if (!ipa.name.endsWith(".ipa", ignoreCase = true)) return fail("Not an .ipa file: ${ipa.name}")

    val plist = if (r.bundleShortVersion == null || r.bundleVersion == null) {
        readIpaVersions(ipa)
    } else {
        null to null
    }
    val version = r.bundleShortVersion ?: plist.first
        ?: return fail(
            "Cannot resolve CFBundleShortVersionString: not in the IPA Info.plist; set bundleShortVersion.",
        )
    val build = r.bundleVersion ?: plist.second
        ?: return fail(
            "Cannot resolve CFBundleVersion: not in the IPA Info.plist; set bundleVersion.",
        )
    return ApiResponse.Success(ValidatedIpaBinary(version = version, buildNumber = build))
}

internal suspend fun resolveAppStoreBetaGroups(
    api: AppStorePublisherApi,
    appId: String,
    bundleId: String,
    requestedNames: List<String>,
    token: () -> String,
): ApiResponse<List<String>> {
    if (requestedNames.isEmpty()) return ApiResponse.Success(emptyList())
    val groups = when (val r = api.listBetaGroups(token(), appId)) {
        is ApiResponse.Success -> r.data.data
        is ApiResponse.Failure -> return r.failure("list beta groups")
    }
    val byName = groups.associate { it.attributes?.name to it.id }
    val unknown = requestedNames.filter { it !in byName }
    if (unknown.isNotEmpty()) {
        return fail(
            "Unknown beta group(s) $unknown for '$bundleId'. Available: " +
                "${byName.keys.filterNotNull().sorted()}.",
        )
    }
    return ApiResponse.Success(requestedNames.mapNotNull { byName[it] })
}

internal suspend fun uploadAndDistributeIpaBuild(
    api: AppStorePublisherApi,
    appId: String,
    r: AppStoreBinaryPublishRequest,
    version: String,
    buildNumber: String,
    groupIds: List<String>,
    token: () -> String,
): ApiResponse<String> {
    val ipa = r.ipaFile
    val upload =
        when (
            val resp = api.createBuildUpload(
                token(),
                appId,
                version,
                buildNumber,
                r.platform,
            )
        ) {
            is ApiResponse.Success -> resp.data.data.id

            is ApiResponse.Failure -> return resp.failure(
                "create build upload (is $version ($buildNumber) already uploaded?)",
            )
        } ?: return fail("App Store Connect returned a build upload without id.")

    val file = when (
        val resp = api.createBuildUploadFile(
            token(),
            upload,
            ipa.name,
            ipa.length(),
        )
    ) {
        is ApiResponse.Success -> resp.data.data
        is ApiResponse.Failure -> return resp.failure("reserve IPA upload")
    }
    val fileId =
        file.id ?: return fail("App Store Connect returned a build upload file without id.")

    val size = ipa.length()
    RandomAccessFile(ipa, "r").use { raf ->
        for (op in file.attributes?.uploadOperations.orEmpty()) {
            uploadOperationRangeError(op, size)?.let { return fail(it) }
            val chunk = withContext(Dispatchers.IO) {
                ByteArray(op.length.toInt()).also {
                    raf.seek(op.offset)
                    raf.readFully(it)
                }
            }
            val resp = api.executeUploadOperation(op, chunk)
            if (resp is ApiResponse.Failure) {
                return resp.failure(
                    "upload IPA chunk at offset ${op.offset}",
                )
            }
        }
    }

    val marked = api.updateBuildUploadFile(token(), fileId, uploaded = true)
    if (marked is ApiResponse.Failure) return marked.failure("mark IPA as uploaded")

    val startedAt = System.currentTimeMillis()
    var buildId: String? = null
    while (true) {
        delay(r.pollIntervalMs)
        val resp = when (val res = api.getBuildUpload(token(), upload)) {
            is ApiResponse.Success -> res.data
            is ApiResponse.Failure -> return res.failure("poll build upload")
        }
        val st = resp.data.attributes?.state
        when (st?.state) {
            "COMPLETE" -> {
                buildId = resp.buildId
                break
            }

            "FAILED" -> {
                val details = st.errors.joinToString("; ") { "${it.code}: ${it.description}" }
                return fail(
                    "App Store Connect failed to process the IPA: ${details.ifBlank {
                        "no details provided"
                    }}",
                )
            }
        }
        if (System.currentTimeMillis() - startedAt >= r.processingTimeoutMs) {
            return fail(
                "Timed out after ${r.processingTimeoutMs / 1000}s waiting for build processing " +
                    "(last state: ${st?.state}). The upload $upload may still finish; check App Store Connect.",
            )
        }
    }
    val bid =
        buildId
            ?: return fail(
                "Build upload $upload is COMPLETE but has no build relationship yet; retry later.",
            )

    if (r.whatToTestByLocale.isNotEmpty()) {
        val existing = when (val res = api.listBetaBuildLocalizations(token(), bid)) {
            is ApiResponse.Success -> res.data.data
            is ApiResponse.Failure -> return res.failure("list beta build localizations")
        }
        for ((locale, text) in r.whatToTestByLocale) {
            val current = existing.firstOrNull { it.attributes?.locale == locale }?.id
            val res = if (current != null) {
                api.updateBetaBuildLocalization(token(), current, text)
            } else {
                api.createBetaBuildLocalization(token(), bid, locale, text)
            }
            if (res is ApiResponse.Failure) return res.failure("set What to Test for $locale")
        }
    }
    if (groupIds.isNotEmpty()) {
        val res = api.addBuildToBetaGroups(token(), bid, groupIds)
        if (res is ApiResponse.Failure) return res.failure("add build to beta groups")
    }
    if (r.submitBetaReview) {
        val res = api.submitBetaReview(token(), bid)
        if (res is ApiResponse.Failure) return res.failure("submit for beta app review")
    }
    return ApiResponse.Success(bid)
}

internal suspend fun attachAndSubmitVersionForReview(
    api: AppStorePublisherApi,
    appId: String,
    versionId: String,
    buildId: String,
    submitForReview: Boolean,
    platform: String = "IOS",
    token: () -> String,
): ApiResponse<Unit> {
    val attach = api.attachBuildToVersion(token(), versionId, buildId)
    if (attach is ApiResponse.Failure) {
        return attach.failure("attach build to App Store version")
    }
    if (submitForReview) {
        val sub = when (val r = api.createReviewSubmission(token(), appId, platform)) {
            is ApiResponse.Success -> r.data.data.id
            is ApiResponse.Failure -> return r.failure("create review submission")
        } ?: return fail("App Store Connect returned a review submission without id.")
        val item = api.createReviewSubmissionItem(token(), sub, versionId)
        if (item is ApiResponse.Failure) return item.failure("add version to review submission")
        val done = api.submitReviewSubmission(token(), sub)
        if (done is ApiResponse.Failure) return done.failure("submit for App Store review")
    }
    return ApiResponse.Success(Unit)
}

/**
 * Uploads an IPA through the App Store Connect "Build Uploads" API (buildUploads / buildUploadFiles),
 * waits for processing, then optionally sets TestFlight "What to test", beta groups, beta review and
 * App Store submission. See AppStoreBinaryModels.kt for what was verified against Apple's OpenAPI spec.
 */
public suspend fun AppStorePublisher.publishBinary(
    request: AppStoreBinaryPublishRequest,
): ApiResponse<AppStoreBinaryPublishOutcome> {
    val validated = when (val v = validateAndInspectIpa(request)) {
        is ApiResponse.Success -> v.data
        is ApiResponse.Failure -> return v.asFailure()
    }
    val ipa = request.ipaFile
    val version = validated.version
    val build = validated.buildNumber

    val hasCreds = !request.keyId.isNullOrBlank() && !request.issuerId.isNullOrBlank() &&
        !request.privateKey.isNullOrBlank()
    val plan = buildString {
        appendLine("IPA: ${ipa.name} (${ipa.length()} bytes)")
        appendLine("Bundle ID: ${request.bundleId}  Platform: ${request.platform}")
        appendLine("Version: $version ($build)")
        appendLine(
            "What to test locales: ${request.whatToTestByLocale.keys.sorted().ifEmpty {
                listOf("-")
            }}",
        )
        appendLine("Beta groups: ${request.betaGroups.ifEmpty { listOf("-") }}")
        appendLine(
            "Submit beta review: ${request.submitBetaReview}  Submit for App Store review: ${request.submitForReview}",
        )
    }
    fun outcome(status: AppStorePublishStatus, buildId: String?, extra: String) =
        ApiResponse.Success(
            AppStoreBinaryPublishOutcome(
                status,
                buildId,
                version,
                build,
                plan + extra,
            ),
        )

    if (request.dryRun && !hasCreds) {
        return outcome(
            AppStorePublishStatus.DRY_RUN_LOCAL,
            null,
            "Dry run: local validation only.\n",
        )
    }
    if (!hasCreds) {
        return fail(
            "Missing App Store Connect credentials. Configure ASC_KEY_ID, ASC_ISSUER_ID and " +
                "ASC_PRIVATE_KEY (or the DSL equivalents).",
        )
    }

    // The JWT lives 20 min but processing can take longer; AppStoreTokens renews it before it expires.
    val tokens = AppStoreTokens(request.keyId!!, request.issuerId!!, request.privateKey!!)
    fun freshToken() = tokens.fresh()

    val appId = when (val r = api.findApps(freshToken(), request.bundleId)) {
        is ApiResponse.Success -> r.data.data.firstOrNull()?.id
            ?: return fail(
                "No application found with bundle ID '${request.bundleId}' on App Store Connect.",
            )

        is ApiResponse.Failure -> return r.failure("find app")
    }

    val groupIds = when (
        val g = resolveAppStoreBetaGroups(
            api,
            appId,
            request.bundleId,
            request.betaGroups,
            ::freshToken,
        )
    ) {
        is ApiResponse.Success -> g.data
        is ApiResponse.Failure -> return g.asFailure()
    }

    if (request.dryRun) {
        return outcome(
            AppStorePublishStatus.DRY_RUN_VALIDATED,
            null,
            "Dry run: app $appId found, beta groups resolved; nothing uploaded.\n",
        )
    }

    val bid = when (
        val u = uploadAndDistributeIpaBuild(
            api,
            appId,
            request,
            version,
            build,
            groupIds,
            ::freshToken,
        )
    ) {
        is ApiResponse.Success -> u.data
        is ApiResponse.Failure -> return u.asFailure()
    }

    if (request.submitForReview) {
        val versions = when (val r = api.listEditableVersions(freshToken(), appId)) {
            is ApiResponse.Success -> r.data.data
            is ApiResponse.Failure -> return r.failure("list editable App Store versions")
        }
        val versionId = versions.firstOrNull { it.attributes?.versionString == version }?.id
            ?: return fail(
                "No editable App Store version '$version' to submit; create it in App Store Connect first.",
            )
        when (
            val s = attachAndSubmitVersionForReview(
                api,
                appId,
                versionId,
                bid,
                submitForReview = true,
                platform = request.platform,
                token = ::freshToken,
            )
        ) {
            is ApiResponse.Success -> Unit
            is ApiResponse.Failure -> return s.asFailure()
        }
    }
    return outcome(AppStorePublishStatus.COMMITTED, bid, "Uploaded and processed; build $bid.\n")
}

private fun fail(message: String): ApiResponse.Failure.Error =
    ApiResponse.Failure.Error("Store Studio: $message")

private fun ApiResponse.Failure<*>.failure(step: String): ApiResponse.Failure<Nothing> =
    when (this) {
        is ApiResponse.Failure.Error -> ApiResponse.Failure.Error(
            "Store Studio: failed to $step: $payload",
        )

        is ApiResponse.Failure.Exception -> ApiResponse.Failure.Exception(
            RuntimeException("Store Studio: failed to $step: ${throwable.message}", throwable),
        )
    }

/** Returns (CFBundleShortVersionString, CFBundleVersion) from Payload/APP.app/Info.plist (XML or binary). */
internal fun readIpaVersions(ipa: File): Pair<String?, String?> = try {
    ZipFile(ipa).use { zip ->
        val entry = zip.entries().asSequence().firstOrNull { INFO_PLIST.matches(it.name) }
            ?: return null to null
        val dict = zip.getInputStream(entry).use { PropertyListParser.parse(it) } as? NSDictionary
        fun str(k: String) = dict?.get(k)?.toJavaObject()?.toString()?.takeIf { it.isNotBlank() }
        str("CFBundleShortVersionString") to str("CFBundleVersion")
    }
} catch (_: Exception) {
    null to null // unreadable zip/plist: caller reports the unresolved version
}

/** Server-supplied chunk bounds must lie inside the file before any bytes are read. */
internal fun uploadOperationRangeError(op: AscUploadOperation, size: Long): String? =
    if (op.offset < 0 || op.length <= 0 || op.offset + op.length > size) {
        "Upload operation out of range (offset=${op.offset}, length=${op.length}, file=$size)."
    } else {
        null
    }
