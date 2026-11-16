package dev.ribod.storestudio.idea.model

import java.io.File

/**
 * Data Transfer Objects (DTOs) for Chameleon Store Studio JSON artifacts.
 *
 * Designed to be deserialized using IntelliJ's bundled com.google.gson.Gson
 * to ensure zero runtime classpath friction and complete version decoupling.
 */

public data class MergedManifestDto(
    val schemaVersion: Int = 1,
    val module: String = "",
    val generator: String = "",
    val entries: List<ManifestEntryDto> = emptyList(),
)

public data class ManifestEntryDto(
    val kind: String = "SCREENSHOT",
    val id: String = "",
    val order: Int = 0,
    val title: String = "",
    val functionFqn: String = "",
    val packageName: String = "",
    val functionName: String = "",
    val devices: List<String> = emptyList(),
    val targets: List<String> = emptyList(),
    val locales: List<String> = emptyList(),
    val source: SourceLocationDto? = null,
)

public data class SourceLocationDto(val path: String = "", val line: Int = 1)

public data class RenderIndexDto(
    val schemaVersion: Int = 1,
    val engineDefault: String = "LAYOUTLIB",
    val layoutlibVersion: String = "",
    val renderedAt: String = "",
    val jobs: List<RenderJobDto> = emptyList(),
)

public data class RenderJobDto(
    val jobId: String = "",
    val slotKey: String = "",
    val entryId: String = "",
    val target: String = "",
    val deviceSlot: String = "",
    val storeLocale: String = "",
    val localeTag: String = "",
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val dpi: Int = 0,
    val engine: String = "LAYOUTLIB",
    val status: String = "SUCCESS",
    val image: String = "",
    val durationMs: Long = 0,
    val diagnostics: List<RenderDiagnosticDto> = emptyList(),
)

public data class RenderDiagnosticDto(
    val severity: String = "",
    val code: String = "",
    val message: String = "",
)

public data class ExportIndexDto(
    val schemaVersion: Int = 1,
    val assets: List<ExportAssetDto> = emptyList(),
)

public data class ExportAssetDto(
    val slotKey: String = "",
    val target: String = "",
    val deviceSlot: String = "",
    val storeLocale: String = "",
    val id: String = "",
    val order: Int = 0,
    val position: Int = 0,
    val file: String = "",
    val format: String = "PNG",
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val bytes: Long = 0,
    val hasAlpha: Boolean = false,
    val colorSpace: String = "sRGB",
    val pixelSha256: String = "",
    val fileSha256: String = "",
    val pHash: String = "",
    val md5: String = "",
    val engine: String = "LAYOUTLIB",
)

public data class ValidationReportDto(
    val schemaVersion: Int = 1,
    val valid: Boolean = true,
    val violations: List<ViolationDto> = emptyList(),
)

public data class ViolationDto(
    val ruleId: String = "",
    val severity: String = "INFO",
    val target: String? = null,
    val storeLocale: String? = null,
    val deviceSlot: String? = null,
    val slotKey: String? = null,
    val message: String = "",
    val hint: String? = null,
)

public data class DiffDocumentDto(
    val schemaVersion: Int = 1,
    val base: DiffRefInfoDto? = null,
    val head: DiffRefInfoDto? = null,
    val summary: DiffSummaryDto = DiffSummaryDto(),
    val changes: List<DiffChangeDto> = emptyList(),
)

public data class DiffRefInfoDto(
    val ref: String = "",
    val commit: String? = null,
    val source: String = "",
)

public data class DiffSummaryDto(
    val added: Int = 0,
    val removed: Int = 0,
    val reordered: Int = 0,
    val modified: Int = 0,
    val replaced: Int = 0,
    val movedAndModified: Int = 0,
    val unchanged: Int = 0,
) {
    val totalChanges: Int
        get() = added + removed + reordered + modified + replaced + movedAndModified

    val totalAssets: Int
        get() = totalChanges + unchanged
}

public data class DiffChangeDto(
    val type: String = "UNCHANGED",
    val bucket: String = "",
    val id: String = "",
    val basePosition: Int? = null,
    val headPosition: Int? = null,
    val pHashDistance: Double? = null,
    val baseImage: String? = null,
    val headImage: String? = null,
    val basePixelSha256: String? = null,
    val headPixelSha256: String? = null,
    val semanticType: String? = null,
    val baseTextSha256: String? = null,
    val headTextSha256: String? = null,
    val removedTexts: List<String> = emptyList(),
    val addedTexts: List<String> = emptyList(),
    val hasVisualOverflow: Boolean = false,
    val headEngine: String = "LAYOUTLIB",
)

public data class StoreMetadataManifestDto(
    val schemaVersion: Int = 1,
    val defaultLocale: String = "en-US",
    val locales: Map<String, StoreMetadataBundleDto> = emptyMap(),
    val icons: StoreIconsDto = StoreIconsDto(),
)

/** App icon per store as a path relative to the module directory; written by the Gradle plugin. */
public data class StoreIconsDto(val googlePlay: String? = null, val appStore: String? = null)

public data class StoreMetadataBundleDto(
    val locale: String = "",
    val play: PlayMetadataDto = PlayMetadataDto(),
    val appStore: AppStoreMetadataDto = AppStoreMetadataDto(),
)

public data class PlayMetadataDto(
    val title: String = "",
    val shortDescription: String = "",
    val fullDescription: String = "",
    val releaseNotes: String = "",
)

public data class AppStoreMetadataDto(
    val name: String = "",
    val subtitle: String = "",
    val keywords: String = "",
    val promotionalText: String = "",
    val description: String = "",
    val releaseNotes: String = "",
    val supportUrl: String = "",
    val marketingUrl: String = "",
)

/**
 * Encapsulates the entire discovered state of a Gradle module that applies Store Studio.
 */
public data class ModuleStoreArtifacts(
    val moduleName: String,
    val moduleDir: File,
    val buildStoreDir: File,
    val manifest: MergedManifestDto? = null,
    val renderIndex: RenderIndexDto? = null,
    val exportIndex: ExportIndexDto? = null,
    val validationReport: ValidationReportDto? = null,
    val diffDocument: DiffDocumentDto? = null,
    val metadataManifest: StoreMetadataManifestDto? = null,
) {
    val hasArtifacts: Boolean
        get() = manifest != null || exportIndex != null || renderIndex != null ||
            validationReport != null
}

/**
 * Flattened asset card representation for Gallery and visual browser components.
 */
public data class StoreAssetCard(
    val moduleName: String,
    val slotKey: String,
    val target: String,
    val deviceSlot: String,
    val storeLocale: String,
    val id: String,
    val title: String,
    val order: Int,
    val imageFile: File,
    val widthPx: Int,
    val heightPx: Int,
    val engine: String,
    val functionFqn: String? = null,
    val functionName: String? = null,
    val sourceLocation: SourceLocationDto? = null,
    val violations: List<ViolationDto> = emptyList(),
)
