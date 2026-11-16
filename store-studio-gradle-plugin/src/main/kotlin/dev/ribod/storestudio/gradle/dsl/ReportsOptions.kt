package dev.ribod.storestudio.gradle.dsl

import dev.ribod.storestudio.core.report.ReportImageMode
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration options controlling self-contained HTML reports and store previews.
 */
public abstract class ReportsOptions @Inject constructor(objects: ObjectFactory) {

    /**
     * Whether to generate the minimalist gallery HTML report.
     */
    public val htmlGallery: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)

    /**
     * Whether to generate the authentic store listing preview HTML report.
     */
    public val storeListingPreview: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)

    /**
     * Embedding strategy for screenshot assets inside generated HTML reports.
     */
    public val imageMode: Property<ReportImageMode> =
        objects.property(ReportImageMode::class.java)
            .convention(ReportImageMode.THUMBNAIL)

    /**
     * Maximum width/height pixel dimension for downscaled embedded thumbnails.
     */
    public val maxThumbnailDimension: Property<Int> = objects.property(Int::class.java)
        .convention(720)

    /**
     * Display name of the application shown in reports and store previews.
     */
    public val appName: Property<String> = objects.property(String::class.java)

    /**
     * Developer or publisher organization name displayed in simulated store headers.
     */
    public val developerName: Property<String> = objects.property(String::class.java)
        .convention("Mobile Developer")

    /**
     * Store genre or category shown in store previews (e.g., 'Productivity', 'News').
     */
    public val category: Property<String> = objects.property(String::class.java)
        .convention("Productivity")

    /**
     * JPEG quality factor for downscaled thumbnails (0.0 to 1.0, default: 0.85).
     */
    public val thumbnailQuality: Property<Float> = objects.property(Float::class.java)
        .convention(0.85f)

    /**
     * Whether to include unchanged assets in the visual diff report.
     */
    public val includeUnchangedInDiff: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    /**
     * Target directory where generated HTML reports are written.
     */
    public abstract val outputDirectory: DirectoryProperty
}
