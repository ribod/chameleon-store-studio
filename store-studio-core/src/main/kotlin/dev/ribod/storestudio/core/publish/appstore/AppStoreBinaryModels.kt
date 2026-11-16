package dev.ribod.storestudio.core.publish.appstore

import kotlinx.serialization.Serializable
import java.io.File

/*
 * Resource types, attribute names and states below were verified against Apple's official App Store
 * Connect OpenAPI specification (app-store-connect-openapi-specification.zip): buildUploads,
 * buildUploadFiles, betaBuildLocalizations, betaAppReviewSubmissions, betaGroups/{id}/relationships/builds,
 * appStoreVersions/{id}/relationships/build, reviewSubmissions, reviewSubmissionItems.
 * Not verified against a live account: runtime behaviour (e.g. when the build relationship is populated).
 */

/** Request for [AppStorePublisher.publishBinary]. */
public data class AppStoreBinaryPublishRequest(
    public val bundleId: String,
    public val keyId: String?,
    public val issuerId: String?,
    public val privateKey: String?,
    public val ipaFile: File,
    /** CFBundleShortVersionString; read from the IPA Info.plist when null. */
    public val bundleShortVersion: String? = null,
    /** CFBundleVersion; read from the IPA Info.plist when null. */
    public val bundleVersion: String? = null,
    public val platform: String = "IOS",
    public val whatToTestByLocale: Map<String, String> = emptyMap(),
    /** Names of TestFlight beta groups to add the build to. */
    public val betaGroups: List<String> = emptyList(),
    public val submitBetaReview: Boolean = false,
    public val submitForReview: Boolean = false,
    public val dryRun: Boolean = false,
    public val pollIntervalMs: Long = 15_000L,
    public val processingTimeoutMs: Long = 60L * 60L * 1000L,
) {
    override fun toString(): String =
        "AppStoreBinaryPublishRequest(bundleId=$bundleId, keyId=$keyId, privateKey=***, " +
            "ipaFile=$ipaFile, dryRun=$dryRun)"
}

public data class AppStoreBinaryPublishOutcome(
    public val status: AppStorePublishStatus,
    public val buildId: String?,
    public val version: String,
    public val buildNumber: String,
    public val formattedReport: String,
)

@Serializable
public data class AscStateDetail(
    public val code: String? = null,
    public val description: String? = null,
)

/** `state` is one of AWAITING_UPLOAD, PROCESSING, FAILED, COMPLETE. */
@Serializable
public data class AscBuildUploadState(
    public val state: String? = null,
    public val errors: List<AscStateDetail> = emptyList(),
    public val warnings: List<AscStateDetail> = emptyList(),
)

@Serializable
public data class AscBuildUploadAttributes(
    public val cfBundleShortVersionString: String? = null,
    public val cfBundleVersion: String? = null,
    public val platform: String? = null,
    public val state: AscBuildUploadState? = null,
)

@Serializable
public data class AscBuildUploadFileAttributes(
    public val fileName: String? = null,
    public val fileSize: Long? = null,
    public val uti: String? = null,
    public val assetType: String? = null,
    public val uploaded: Boolean? = null,
    public val uploadOperations: List<AscUploadOperation> = emptyList(),
)

@Serializable
public data class AscNullableRelationship(public val data: AscRelationshipItem? = null)

@Serializable
public data class AscBuildUploadRelationships(public val build: AscNullableRelationship? = null)

@Serializable
public data class AscBuildUploadData(
    public val id: String,
    public val attributes: AscBuildUploadAttributes? = null,
    public val relationships: AscBuildUploadRelationships? = null,
)

@Serializable
public data class AscBuildUploadResponse(public val data: AscBuildUploadData) {
    public val buildId: String? get() = data.relationships?.build?.data?.id
}

@Serializable
public data class AscBetaGroupAttributes(
    public val name: String? = null,
    public val isInternalGroup: Boolean? = null,
)

@Serializable
public data class AscBetaBuildLocalizationAttributes(
    public val locale: String? = null,
    public val whatsNew: String? = null,
)

@Serializable
public data class AscReviewSubmissionAttributes(
    public val platform: String? = null,
    public val state: String? = null,
    public val submitted: Boolean? = null,
)

// --- request bodies (private to the transport) ---

@Serializable
internal data class AscRel(val data: AscRelationshipItem)

@Serializable
internal data class AscBody<A, R>(val data: AscBodyData<A, R>)

@Serializable
internal data class AscBodyData<A, R>(
    val type: String,
    val id: String? = null,
    val attributes: A? = null,
    val relationships: R? = null,
)

@Serializable
internal data class AscNoAttrs(val unused: String? = null)

@Serializable
internal data class AscNoRels(val unused: String? = null)

@Serializable
internal data class BuildUploadCreateAttrs(
    val cfBundleShortVersionString: String,
    val cfBundleVersion: String,
    val platform: String,
)

@Serializable
internal data class AppRel(val app: AscRel)

@Serializable
internal data class BuildUploadFileCreateAttrs(
    val fileName: String,
    val fileSize: Long,
    val uti: String,
    val assetType: String,
)

@Serializable
internal data class BuildUploadRel(val buildUpload: AscRel)

@Serializable
internal data class UploadedAttrs(val uploaded: Boolean)

@Serializable
internal data class LocaleWhatsNewAttrs(val locale: String? = null, val whatsNew: String)

@Serializable
internal data class BuildRel(val build: AscRel)

@Serializable
internal data class PlatformAttrs(val platform: String)

@Serializable
internal data class SubmittedAttrs(val submitted: Boolean)

@Serializable
internal data class ReviewItemRel(val reviewSubmission: AscRel, val appStoreVersion: AscRel)

@Serializable
internal data class BuildLinkageBody(val data: AscRelationshipItem)

@Serializable
internal data class BuildLinkagesBody(val data: List<AscRelationshipItem>)
