package dev.ribod.storestudio.core.publish.play

import com.skydoves.sandwich.ApiResponse
import dev.ribod.storestudio.core.publish.PublishPart
import dev.ribod.storestudio.core.report.model.StoreListingMetadata
import dev.ribod.storestudio.model.ExportAsset
import dev.ribod.storestudio.model.StoreDeviceSlot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Image asset categories recognized by the Google Play Developer API.
 */
public enum class PlayImageType(public val apiValue: String) {
    PHONE_SCREENSHOTS("phoneScreenshots"),
    SEVEN_INCH_SCREENSHOTS("sevenInchScreenshots"),
    TEN_INCH_SCREENSHOTS("tenInchScreenshots"),
    WEAR_SCREENSHOTS("wearScreenshots"),
    FEATURE_GRAPHIC("featureGraphic"),
    ICON("icon"),
    ;

    public companion object {
        /**
         * Maps a [StoreDeviceSlot] to its corresponding Google Play image category.
         */
        public fun fromDeviceSlot(slot: StoreDeviceSlot): PlayImageType? = when (slot) {
            StoreDeviceSlot.PHONE -> PHONE_SCREENSHOTS
            StoreDeviceSlot.TABLET_7 -> SEVEN_INCH_SCREENSHOTS
            StoreDeviceSlot.TABLET_10 -> TEN_INCH_SCREENSHOTS
            StoreDeviceSlot.WEAR -> WEAR_SCREENSHOTS
            StoreDeviceSlot.FEATURE_GRAPHIC -> FEATURE_GRAPHIC
            else -> null
        }
    }
}

/**
 * Google Cloud Service Account credentials parsed from service account JSON.
 */
@Serializable
public data class ServiceAccountCredentials(
    public val client_email: String,
    public val private_key: String,
    public val token_uri: String = "https://oauth2.googleapis.com/token",
) {
    override fun toString(): String =
        "ServiceAccountCredentials(client_email=$client_email, private_key=***, token_uri=$token_uri)"
}

/**
 * OAuth2 Bearer token exchange response from Google authorization servers.
 */
@Serializable
public data class OAuthTokenResponse(
    public val access_token: String,
    public val expires_in: Long? = null,
    public val token_type: String? = null,
)

/**
 * Google Play active edit transaction descriptor.
 */
@Serializable
public data class PlayAppEdit(public val id: String, public val expiryTimeSeconds: String? = null)

/**
 * Localized textual store listing representation on Google Play.
 */
@Serializable
public data class PlayListing(
    public val language: String,
    public val title: String? = null,
    public val shortDescription: String? = null,
    public val fullDescription: String? = null,
    public val video: String? = null,
)

/**
 * Remote image descriptor returned by Google Play image upload endpoints.
 */
@Serializable
public data class PlayImage(
    public val id: String? = null,
    public val url: String? = null,
    public val sha1: String? = null,
    public val sha256: String? = null,
)

/**
 * High-level execution status of a Google Play publication operation.
 */
public enum class PlayPublishStatus {
    COMMITTED,
    DRY_RUN_LOCAL,
    DRY_RUN_VALIDATED,
    FAILED,
}

/**
 * Summary of images scheduled for upload within a specific locale and category.
 */
public data class PlayPublishPlanItem(
    public val locale: String,
    public val imageType: PlayImageType,
    public val count: Int,
    public val files: List<String>,
)

/**
 * Summary of localized metadata fields scheduled for synchronization.
 */
public data class PlayListingPlanItem(
    public val locale: String,
    public val title: String?,
    public val shortDescription: String?,
    public val fullDescription: String?,
)

/**
 * Complete publication plan computed before executing Google Play mutations.
 */
public data class PlayPublishPlan(
    public val packageName: String,
    public val editId: String?,
    public val images: List<PlayPublishPlanItem>,
    public val listings: List<PlayListingPlanItem>,
    public val status: PlayPublishStatus,
    /** Release track whose latest release receives the release notes, if any. */
    public val track: String? = null,
    /** Locales whose release notes are applied to [track]. */
    public val trackReleaseNoteLocales: List<String> = emptyList(),
    /** Non-fatal notes about the plan (e.g. a track without releases). */
    public val warnings: List<String> = emptyList(),
    /** Parts this plan covers; the others are not touched. */
    public val parts: Set<PublishPart> = PublishPart.ALL,
    public val binary: PlayBinaryPublishRequest? = null,
    public val versionCodes: List<Long> = emptyList(),
)

/**
 * Result outcome of a Google Play publication run.
 */
public data class PlayPublishOutcome(
    public val status: PlayPublishStatus,
    public val editId: String?,
    public val plan: PlayPublishPlan,
    public val formattedReport: String,
    public val versionCodes: List<Long> = emptyList(),
)

/**
 * Request payload containing all inputs needed to execute Google Play publication.
 */
public data class PlayPublishRequest(
    public val packageName: String,
    public val serviceAccountJson: String?,
    public val exportDir: File,
    public val assets: List<ExportAsset>,
    public val listingsByLocale: Map<String, StoreListingMetadata> = emptyMap(),
    public val appIconFile: File? = null,
    public val dryRun: Boolean = false,
    public val changesNotSentForReview: Boolean = false,
    /**
     * Release track (e.g. `internal`, `alpha`, `beta`, `production`) whose latest release gets
     * [releaseNotesByLocale]. `null` leaves tracks untouched.
     */
    public val track: String? = null,
    /** Author-provided release notes per Play locale; locales without notes are omitted. */
    public val releaseNotesByLocale: Map<String, String> = emptyMap(),
    /**
     * Parts to publish; everything outside them is left untouched. [PublishPart.RELEASE_NOTES]
     * needs a [track] unless all parts are selected (then a missing track just skips the notes).
     */
    public val parts: Set<PublishPart> = PublishPart.ALL,
    public val binary: PlayBinaryPublishRequest? = null,
) {
    override fun toString(): String =
        "PlayPublishRequest(packageName=$packageName, serviceAccountJson=***, track=$track, dryRun=$dryRun)"
}

/** Bundle accepted by Play; [sha256] is absent when Play does not report it. */
@Serializable
public data class PlayBundle(public val versionCode: Long, public val sha256: String? = null)

/** Release status values accepted by the Play track API. */
public enum class PlayReleaseStatus(public val apiValue: String) {
    COMPLETED("completed"),
    IN_PROGRESS("inProgress"),
    HALTED("halted"),
    DRAFT("draft"),
}

/** Result of a binary publication run. */
public data class PlayBinaryPublishOutcome(
    public val status: PlayPublishStatus,
    public val editId: String?,
    public val versionCodes: List<Long>,
    public val formattedReport: String,
)

/** Inputs for uploading AAB files to a Google Play track. */
public data class PlayBinaryPublishRequest(
    public val packageName: String,
    public val serviceAccountJson: String?,
    public val releaseFiles: List<File>,
    public val track: String = "internal",
    public val status: PlayReleaseStatus = PlayReleaseStatus.COMPLETED,
    public val userFraction: Double? = null,
    /** In-app update priority, 0..5. */
    public val inAppUpdatePriority: Int? = null,
    public val releaseName: String? = null,
    public val releaseNotesByLocale: Map<String, String> = emptyMap(),
    /** R8/ProGuard mapping file, uploaded for every new version code. */
    public val mappingFile: File? = null,
    /** Native debug symbols file, uploaded for every new version code. */
    public val debugSymbolsFile: File? = null,
    public val changesNotSentForReview: Boolean = false,
    /** Reuse this open edit: it is neither created, committed nor deleted by the publisher. */
    public val existingEditId: String? = null,
    /** Version codes already on the track that stay in the release. */
    public val retainVersionCodes: List<Long> = emptyList(),
    public val dryRun: Boolean = false,
    public val uploadTimeoutMs: Long = DEFAULT_UPLOAD_TIMEOUT_MS,
) {
    override fun toString(): String =
        "PlayBinaryPublishRequest(packageName=$packageName, serviceAccountJson=***, " +
            "releaseFiles=$releaseFiles, track=$track, dryRun=$dryRun)"

    public companion object {
        public const val DEFAULT_UPLOAD_TIMEOUT_MS: Long = 2 * 60 * 1000L
    }
}

/**
 * Low-level API abstraction for communicating with Google Play Developer API v3.
 */
public interface PlayPublisherApi : AutoCloseable {
    public suspend fun authenticate(serviceAccountJson: String): ApiResponse<String>

    public suspend fun insertEdit(packageName: String, token: String): ApiResponse<PlayAppEdit>

    public suspend fun deleteAllImages(
        packageName: String,
        editId: String,
        language: String,
        imageType: PlayImageType,
        token: String,
    ): ApiResponse<Unit>

    public suspend fun uploadImage(
        packageName: String,
        editId: String,
        language: String,
        imageType: PlayImageType,
        imageBytes: ByteArray,
        mimeType: String,
        token: String,
    ): ApiResponse<PlayImage>

    public suspend fun updateListing(
        packageName: String,
        editId: String,
        language: String,
        listing: PlayListing,
        token: String,
    ): ApiResponse<PlayListing>

    public suspend fun validateEdit(
        packageName: String,
        editId: String,
        token: String,
    ): ApiResponse<PlayAppEdit>

    public suspend fun commitEdit(
        packageName: String,
        editId: String,
        changesNotSentForReview: Boolean,
        token: String,
    ): ApiResponse<PlayAppEdit>

    public suspend fun deleteEdit(
        packageName: String,
        editId: String,
        token: String,
    ): ApiResponse<Unit>

    /**
     * Reads a release track within an edit. The track is returned as raw JSON so that updating
     * it round-trips fields this library does not model (rollout, country targeting, ...).
     */
    public suspend fun getTrack(
        packageName: String,
        editId: String,
        track: String,
        token: String,
    ): ApiResponse<JsonObject> =
        ApiResponse.Failure.Error("Track updates are not supported by ${this::class.simpleName}")

    /** Replaces a release track within an edit with [trackJson]. */
    public suspend fun updateTrack(
        packageName: String,
        editId: String,
        track: String,
        trackJson: JsonObject,
        token: String,
    ): ApiResponse<JsonObject> =
        ApiResponse.Failure.Error("Track updates are not supported by ${this::class.simpleName}")

    /** Uploads an Android App Bundle (streamed from [file]) into an edit. */
    public suspend fun uploadBundle(
        packageName: String,
        editId: String,
        file: File,
        token: String,
        timeoutMs: Long = PlayBinaryPublishRequest.DEFAULT_UPLOAD_TIMEOUT_MS,
    ): ApiResponse<PlayBundle> =
        ApiResponse.Failure.Error("Binary uploads are not supported by ${this::class.simpleName}")

    /** Uploads a deobfuscation file; [type] is `proguard` or `nativeCode`. */
    public suspend fun uploadDeobfuscationFile(
        packageName: String,
        editId: String,
        versionCode: Long,
        type: String,
        file: File,
        token: String,
        timeoutMs: Long = PlayBinaryPublishRequest.DEFAULT_UPLOAD_TIMEOUT_MS,
    ): ApiResponse<Unit> =
        ApiResponse.Failure.Error("Binary uploads are not supported by ${this::class.simpleName}")

    override fun close() {}
}
