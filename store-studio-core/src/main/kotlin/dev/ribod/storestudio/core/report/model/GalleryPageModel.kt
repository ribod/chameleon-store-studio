package dev.ribod.storestudio.core.report.model

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.Severity
import dev.ribod.storestudio.model.StoreDeviceSlot
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Visual status of an asset card derived from compliance verification.
 */
@Serializable
public enum class AssetStatus {
    VALID,
    WARNING,
    ERROR,
}

/**
 * Finding attached to an asset card in the gallery report.
 */
@Serializable
public data class ReportViolationItem(
    public val ruleId: String,
    public val severity: Severity,
    public val message: String,
    public val hint: String? = null,
)

/**
 * Single visual asset item rendered in report grids and store mockups.
 */
@Serializable
public data class ReportAssetItem(
    public val slotKey: String,
    public val id: String,
    public val target: StoreTarget,
    public val deviceSlot: StoreDeviceSlot,
    public val storeLocale: String,
    public val position: Int,
    public val widthPx: Int,
    public val heightPx: Int,
    public val format: ImageFormat,
    public val dataUri: String,
    public val fullResRelativePath: String,
    public val pixelSha256: String,
    public val pHash: String,
    public val status: AssetStatus,
    public val violations: List<ReportViolationItem> = emptyList(),
    public val engine: String = "LAYOUTLIB",
)

/**
 * Aggregated metric counters for the report header.
 */
@Serializable
public data class ReportSummary(
    public val totalAssets: Int,
    public val storeCount: Int,
    public val localeCount: Int,
    public val errorCount: Int,
    public val warningCount: Int,
    public val valid: Boolean,
)

/**
 * Localized metadata content for simulated store product pages.
 */
@Serializable
public data class StoreListingMetadata(
    public val title: String,
    public val subtitle: String? = null,
    public val developerName: String = "Mobile Developer",
    public val category: String = "Productivity",
    public val shortDescription: String = "",
    public val fullDescription: String = "",
    public val whatsNew: String = "Bug fixes and performance enhancements.",
    public val rating: String = "4.8",
    public val ratingCount: String = "120K",
    public val downloads: String = "1M+",
    public val ageRating: String = "4+",
)

/**
 * Complete in-memory page model used to generate self-contained HTML reports.
 */
@Serializable
public data class GalleryPageModel(
    public val appName: String,
    public val developerName: String,
    public val category: String,
    public val generatedAt: String,
    public val summary: ReportSummary,
    public val assets: List<ReportAssetItem>,
    public val stores: List<String>,
    public val deviceSlots: List<String>,
    public val locales: List<String>,
    public val metadataByLocale: Map<String, StoreListingMetadata> = emptyMap(),
    public val appIconDataUri: String? = null,
    /** App Store icon; the listing preview falls back to [appIconDataUri] when it is `null`. */
    public val appStoreIconDataUri: String? = null,
) {
    public fun toJson(): String = JSON.encodeToString(this)
    public fun metadataJson(): String = JSON.encodeToString(metadataByLocale)

    public companion object {
        private val JSON = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
