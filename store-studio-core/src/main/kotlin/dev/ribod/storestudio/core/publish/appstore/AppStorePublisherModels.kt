package dev.ribod.storestudio.core.publish.appstore

import com.skydoves.sandwich.ApiResponse
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.publish.PublishPart
import dev.ribod.storestudio.model.ExportAsset
import dev.ribod.storestudio.model.StoreDeviceSlot
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Screenshot display types defined by Apple App Store Connect REST API.
 */
public enum class AscDisplayType(public val apiValue: String) {
    APP_IPHONE_67("APP_IPHONE_67"),
    APP_IPHONE_65("APP_IPHONE_65"),
    APP_IPAD_PRO_3GEN_129("APP_IPAD_PRO_3GEN_129"),
    ;

    public companion object {
        /**
         * Resolves the matching App Store Connect display type for a given [StoreDeviceSlot].
         */
        public fun fromSlot(slot: StoreDeviceSlot): AscDisplayType? = when (slot) {
            StoreDeviceSlot.IPHONE_6_9 -> APP_IPHONE_67
            StoreDeviceSlot.IPHONE_6_5 -> APP_IPHONE_65
            StoreDeviceSlot.IPAD_13 -> APP_IPAD_PRO_3GEN_129
            else -> null
        }
    }
}

/**
 * Localized textual marketing metadata. Everything but [name] and [subtitle] targets the App
 * Store version localization; those two belong to the app info localization.
 */
@Serializable
public data class AppStoreLocalizedMetadata(
    public val description: String? = null,
    public val keywords: String? = null,
    public val promotionalText: String? = null,
    public val whatsNew: String? = null,
    public val name: String? = null,
    public val subtitle: String? = null,
    public val supportUrl: String? = null,
    public val marketingUrl: String? = null,
)

/**
 * Group of visual assets planned for upload into a specific App Store screenshot set.
 */
public data class AppStoreScreenshotBucketPlan(
    public val locale: String,
    public val displayType: AscDisplayType,
    public val slot: StoreDeviceSlot,
    public val assets: List<ExportAsset>,
)

/**
 * Execution status for App Store Connect publication operations.
 */
public enum class AppStorePublishStatus {
    COMMITTED,
    DRY_RUN_LOCAL,
    DRY_RUN_VALIDATED,
    FAILED,
}

/**
 * Summary plan of screenshots and metadata scheduled for synchronization.
 */
public data class AppStorePublishPlan(
    public val bundleId: String,
    public val buckets: List<AppStoreScreenshotBucketPlan>,
    public val metadataByLocale: Map<String, AppStoreLocalizedMetadata>,
    public val status: AppStorePublishStatus,
    /** Parts this plan covers; the others are not touched. */
    public val parts: Set<PublishPart> = PublishPart.ALL,
    public val binary: AppStoreBinaryPublishRequest? = null,
    public val buildId: String? = null,
    public val versionString: String? = null,
    public val buildNumber: String? = null,
)

/**
 * High-level result outcome of an App Store Connect publication run.
 */
public data class AppStorePublishOutcome(
    public val status: AppStorePublishStatus,
    public val appId: String?,
    public val versionId: String?,
    public val plan: AppStorePublishPlan,
    public val formattedReport: String,
    public val buildId: String? = null,
    public val versionString: String? = null,
    public val buildNumber: String? = null,
)

/**
 * Execution parameters and inputs required to publish to App Store Connect.
 */
public data class AppStorePublishRequest(
    public val bundleId: String,
    public val keyId: String?,
    public val issuerId: String?,
    public val privateKey: String?,
    public val exportDir: File,
    public val assets: List<ExportAsset>,
    public val metadataByLocale: Map<String, AppStoreLocalizedMetadata> = emptyMap(),
    public val dryRun: Boolean = false,
    public val pollIntervalMs: Long = 1000L,
    public val maxPollAttempts: Int = 30,
    /** Parts to publish; everything outside them is left untouched on App Store Connect. */
    public val parts: Set<PublishPart> = PublishPart.ALL,
    public val binary: AppStoreBinaryPublishRequest? = null,
) {
    override fun toString(): String =
        "AppStorePublishRequest(bundleId=$bundleId, keyId=$keyId, issuerId=$issuerId, " +
            "privateKey=***, dryRun=$dryRun)"
}

// --- JSON:API DTOs for App Store Connect API v1 ---

@Serializable
public data class AscResource<A>(
    public val id: String? = null,
    public val type: String,
    public val attributes: A? = null,
)

@Serializable
public data class AscSingleResponse<A>(public val data: AscResource<A>)

@Serializable
public data class AscListResponse<A>(public val data: List<AscResource<A>> = emptyList())

@Serializable
public data class AscAppAttributes(
    public val bundleId: String? = null,
    public val name: String? = null,
)

@Serializable
public data class AscVersionAttributes(
    public val versionString: String? = null,
    public val appStoreState: String? = null,
)

@Serializable
public data class AscLocalizationAttributes(
    public val locale: String? = null,
    public val description: String? = null,
    public val keywords: String? = null,
    public val promotionalText: String? = null,
    public val whatsNew: String? = null,
    public val supportUrl: String? = null,
    public val marketingUrl: String? = null,
)

@Serializable
public data class AscAppInfoAttributes(public val appStoreState: String? = null)

@Serializable
public data class AscAppInfoLocalizationAttributes(
    public val locale: String? = null,
    public val name: String? = null,
    public val subtitle: String? = null,
)

@Serializable
public data class AscUpdateAppInfoLocalizationData(
    public val id: String,
    public val type: String = "appInfoLocalizations",
    public val attributes: AscAppInfoLocalizationAttributes,
)

@Serializable
public data class AscUpdateAppInfoLocalizationRequest(
    public val data: AscUpdateAppInfoLocalizationData,
)

@Serializable
public data class AscCreateAppInfoLocalizationRelationships(
    public val appInfo: AscRelationshipDataWrapper,
)

@Serializable
public data class AscCreateAppInfoLocalizationData(
    public val type: String = "appInfoLocalizations",
    public val attributes: AscAppInfoLocalizationAttributes,
    public val relationships: AscCreateAppInfoLocalizationRelationships,
)

@Serializable
public data class AscCreateAppInfoLocalizationRequest(
    public val data: AscCreateAppInfoLocalizationData,
)

@Serializable
public data class AscScreenshotSetAttributes(public val screenshotDisplayType: String? = null)

@Serializable
public data class AscUploadHeader(public val name: String, public val value: String)

@Serializable
public data class AscUploadOperation(
    public val method: String,
    public val url: String,
    public val length: Long,
    public val offset: Long,
    public val requestHeaders: List<AscUploadHeader> = emptyList(),
)

@Serializable
public data class AscAssetDeliveryState(
    public val state: String,
    public val errors: List<AscErrorDetail>? = null,
)

@Serializable
public data class AscErrorDetail(
    public val code: String? = null,
    public val description: String? = null,
)

@Serializable
public data class AscScreenshotAttributes(
    public val fileSize: Long? = null,
    public val fileName: String? = null,
    public val sourceFileChecksum: String? = null,
    public val uploadOperations: List<AscUploadOperation> = emptyList(),
    public val assetDeliveryState: AscAssetDeliveryState? = null,
)

@Serializable
public data class AscRelationshipItem(public val type: String, public val id: String)

@Serializable
public data class AscRelationshipDataWrapper(public val data: AscRelationshipItem)

@Serializable
public data class AscCreateScreenshotSetRelationships(
    public val appStoreVersionLocalization: AscRelationshipDataWrapper,
)

@Serializable
public data class AscCreateScreenshotSetAttributes(public val screenshotDisplayType: String)

@Serializable
public data class AscCreateScreenshotSetData(
    public val type: String = "appScreenshotSets",
    public val attributes: AscCreateScreenshotSetAttributes,
    public val relationships: AscCreateScreenshotSetRelationships,
)

@Serializable
public data class AscCreateScreenshotSetRequest(public val data: AscCreateScreenshotSetData)

@Serializable
public data class AscReserveScreenshotRelationships(
    public val appScreenshotSet: AscRelationshipDataWrapper,
)

@Serializable
public data class AscReserveScreenshotAttributes(
    public val fileName: String,
    public val fileSize: Long,
)

@Serializable
public data class AscReserveScreenshotData(
    public val type: String = "appScreenshots",
    public val attributes: AscReserveScreenshotAttributes,
    public val relationships: AscReserveScreenshotRelationships,
)

@Serializable
public data class AscReserveScreenshotRequest(public val data: AscReserveScreenshotData)

@Serializable
public data class AscCommitScreenshotAttributes(
    public val uploaded: Boolean = true,
    public val sourceFileChecksum: String,
)

@Serializable
public data class AscCommitScreenshotData(
    public val id: String,
    public val type: String = "appScreenshots",
    public val attributes: AscCommitScreenshotAttributes,
)

@Serializable
public data class AscCommitScreenshotRequest(public val data: AscCommitScreenshotData)

@Serializable
public data class AscReorderScreenshotsRequest(public val data: List<AscRelationshipItem>)

@Serializable
public data class AscUpdateLocalizationData(
    public val id: String,
    public val type: String = "appStoreVersionLocalizations",
    public val attributes: AscLocalizationAttributes,
)

@Serializable
public data class AscUpdateLocalizationRequest(public val data: AscUpdateLocalizationData)

/**
 * Transport contract for App Store Connect API v1.
 */
public interface AppStorePublisherApi : AutoCloseable {
    public suspend fun findApps(
        token: String,
        bundleId: String,
    ): ApiResponse<AscListResponse<AscAppAttributes>>

    public suspend fun listEditableVersions(
        token: String,
        appId: String,
    ): ApiResponse<AscListResponse<AscVersionAttributes>>

    public suspend fun listVersionLocalizations(
        token: String,
        versionId: String,
    ): ApiResponse<AscListResponse<AscLocalizationAttributes>>

    public suspend fun listScreenshotSets(
        token: String,
        localizationId: String,
    ): ApiResponse<AscListResponse<AscScreenshotSetAttributes>>

    public suspend fun createScreenshotSet(
        token: String,
        localizationId: String,
        displayType: String,
    ): ApiResponse<AscSingleResponse<AscScreenshotSetAttributes>>

    public suspend fun listScreenshots(
        token: String,
        screenshotSetId: String,
    ): ApiResponse<AscListResponse<AscScreenshotAttributes>>

    public suspend fun deleteScreenshot(token: String, screenshotId: String): ApiResponse<Unit>

    public suspend fun reserveScreenshot(
        token: String,
        screenshotSetId: String,
        fileName: String,
        fileSize: Long,
    ): ApiResponse<AscSingleResponse<AscScreenshotAttributes>>

    public suspend fun executeUploadOperation(
        operation: AscUploadOperation,
        chunkBytes: ByteArray,
    ): ApiResponse<Unit>

    public suspend fun commitScreenshot(
        token: String,
        screenshotId: String,
        md5Checksum: String,
    ): ApiResponse<AscSingleResponse<AscScreenshotAttributes>>

    public suspend fun getScreenshot(
        token: String,
        screenshotId: String,
    ): ApiResponse<AscSingleResponse<AscScreenshotAttributes>>

    public suspend fun reorderScreenshots(
        token: String,
        screenshotSetId: String,
        screenshotIds: List<String>,
    ): ApiResponse<Unit>

    public suspend fun updateLocalizationMetadata(
        token: String,
        localizationId: String,
        metadata: AppStoreLocalizedMetadata,
    ): ApiResponse<AscSingleResponse<AscLocalizationAttributes>>

    public suspend fun listAppInfos(
        token: String,
        appId: String,
    ): ApiResponse<AscListResponse<AscAppInfoAttributes>>

    public suspend fun listAppInfoLocalizations(
        token: String,
        appInfoId: String,
    ): ApiResponse<AscListResponse<AscAppInfoLocalizationAttributes>>

    public suspend fun updateAppInfoLocalization(
        token: String,
        appInfoLocalizationId: String,
        name: String?,
        subtitle: String?,
    ): ApiResponse<AscSingleResponse<AscAppInfoLocalizationAttributes>>

    public suspend fun createAppInfoLocalization(
        token: String,
        appInfoId: String,
        locale: String,
        name: String?,
        subtitle: String?,
    ): ApiResponse<AscSingleResponse<AscAppInfoLocalizationAttributes>>

    // --- IPA upload via Build Uploads API (see AppStoreBinaryModels.kt). Defaults keep fakes compiling. ---

    public suspend fun createBuildUpload(
        token: String,
        appId: String,
        shortVersion: String,
        buildVersion: String,
        platform: String,
    ): ApiResponse<AscSingleResponse<AscBuildUploadAttributes>> = notImplemented()

    public suspend fun createBuildUploadFile(
        token: String,
        buildUploadId: String,
        fileName: String,
        fileSize: Long,
    ): ApiResponse<AscSingleResponse<AscBuildUploadFileAttributes>> = notImplemented()

    public suspend fun updateBuildUploadFile(
        token: String,
        fileId: String,
        uploaded: Boolean,
    ): ApiResponse<AscSingleResponse<AscBuildUploadFileAttributes>> = notImplemented()

    /** Fetches the upload with `include=build`; the resulting build id is in [AscBuildUploadResponse]. */
    public suspend fun getBuildUpload(
        token: String,
        buildUploadId: String,
    ): ApiResponse<AscBuildUploadResponse> = notImplemented()

    public suspend fun listBetaGroups(
        token: String,
        appId: String,
    ): ApiResponse<AscListResponse<AscBetaGroupAttributes>> = notImplemented()

    public suspend fun addBuildToBetaGroups(
        token: String,
        buildId: String,
        groupIds: List<String>,
    ): ApiResponse<Unit> = notImplemented()

    public suspend fun listBetaBuildLocalizations(
        token: String,
        buildId: String,
    ): ApiResponse<AscListResponse<AscBetaBuildLocalizationAttributes>> = notImplemented()

    public suspend fun createBetaBuildLocalization(
        token: String,
        buildId: String,
        locale: String,
        whatsNew: String,
    ): ApiResponse<AscSingleResponse<AscBetaBuildLocalizationAttributes>> = notImplemented()

    public suspend fun updateBetaBuildLocalization(
        token: String,
        localizationId: String,
        whatsNew: String,
    ): ApiResponse<AscSingleResponse<AscBetaBuildLocalizationAttributes>> = notImplemented()

    public suspend fun submitBetaReview(token: String, buildId: String): ApiResponse<Unit> =
        notImplemented()

    public suspend fun attachBuildToVersion(
        token: String,
        versionId: String,
        buildId: String,
    ): ApiResponse<Unit> = notImplemented()

    public suspend fun createReviewSubmission(
        token: String,
        appId: String,
        platform: String,
    ): ApiResponse<AscSingleResponse<AscReviewSubmissionAttributes>> = notImplemented()

    public suspend fun createReviewSubmissionItem(
        token: String,
        reviewSubmissionId: String,
        versionId: String,
    ): ApiResponse<Unit> = notImplemented()

    public suspend fun submitReviewSubmission(
        token: String,
        reviewSubmissionId: String,
    ): ApiResponse<AscSingleResponse<AscReviewSubmissionAttributes>> = notImplemented()

    override fun close() {}
}

internal fun <T> notImplemented(): ApiResponse<T> = ApiResponse.Failure.Error("not implemented")
