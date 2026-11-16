package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.core.report.GalleryReportGenerator
import dev.ribod.storestudio.core.report.model.GalleryPageModel
import org.gradle.api.tasks.CacheableTask

/**
 * Generates a self-contained HTML gallery report for exported store assets.
 */
@CacheableTask
public abstract class ExportStoreGalleryHtmlTask : StoreHtmlReportTask() {

    override fun reportName(): String = "gallery"

    init {
        description = "Generates a self-contained HTML gallery report for exported assets."
    }

    override fun render(model: GalleryPageModel): String = GalleryReportGenerator.generate(model)
}
