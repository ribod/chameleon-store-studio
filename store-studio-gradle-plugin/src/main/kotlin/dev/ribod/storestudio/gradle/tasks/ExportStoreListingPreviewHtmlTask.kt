package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.core.report.ListingPreviewReportGenerator
import dev.ribod.storestudio.core.report.model.GalleryPageModel
import org.gradle.api.tasks.CacheableTask

/**
 * Generates an authentic standalone HTML preview of simulated store product listings.
 */
@CacheableTask
public abstract class ExportStoreListingPreviewHtmlTask : StoreHtmlReportTask() {

    override fun reportName(): String = "listing preview"

    init {
        description = "Generates an authentic standalone HTML preview of store product listings."
    }

    override fun render(model: GalleryPageModel): String =
        ListingPreviewReportGenerator.generate(model)
}
