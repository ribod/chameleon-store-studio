package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.core.report.DiffReportGenerator
import dev.ribod.storestudio.core.report.DiffSummaryMarkdown
import dev.ribod.storestudio.core.report.ReportImageMode
import dev.ribod.storestudio.model.DiffDocument
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * Generates interactive single-file HTML diff reports and PR Markdown summaries (DD-11).
 */
@CacheableTask
public abstract class ExportStoreDiffHtmlTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val diffJsonFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val headExportIndexFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val headExportDirectory: DirectoryProperty

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val beforeImagesDir: DirectoryProperty

    @get:Input
    public abstract val imageMode: Property<ReportImageMode>

    @get:Input
    public abstract val maxThumbnailDimension: Property<Int>

    @get:Input
    public abstract val thumbnailQuality: Property<Float>

    @get:Input
    public abstract val includeUnchanged: Property<Boolean>

    @get:Input
    public abstract val appName: Property<String>

    @get:Input
    public abstract val developerName: Property<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val appIcon: RegularFileProperty

    @get:OutputFile
    public abstract val outputHtmlFile: RegularFileProperty

    @get:OutputFile
    public abstract val outputSummaryMarkdownFile: RegularFileProperty

    init {
        group = "store studio"
        description = "Generates interactive HTML visual diff report and PR summary markdown."
        imageMode.convention(ReportImageMode.THUMBNAIL)
        maxThumbnailDimension.convention(720)
        thumbnailQuality.convention(0.85f)
        includeUnchanged.convention(false)
    }

    @Option(
        option = "include-unchanged",
        description = "Include unchanged assets in the visual diff report.",
    )
    public fun setIncludeUnchangedOption(include: Boolean) {
        includeUnchanged.set(include)
    }

    @TaskAction
    public fun generate() {
        val diffFile = diffJsonFile.get().asFile
        if (!diffFile.exists()) {
            logger.warn("Store Studio: Diff JSON not found at {}, skipping report", diffFile)
            return
        }

        val diffDoc = DiffDocument.fromFile(diffFile)
        val headDir = headExportDirectory.get().asFile
        val beforeDir = if (beforeImagesDir.isPresent) beforeImagesDir.get().asFile else null
        val iconFile = if (appIcon.isPresent) appIcon.get().asFile else null

        val html = DiffReportGenerator.generate(
            diffDocument = diffDoc,
            headExportDirectory = headDir,
            beforeImagesDirectory = beforeDir,
            imageMode = imageMode.get(),
            maxThumbnailDimension = maxThumbnailDimension.get(),
            thumbnailQuality = thumbnailQuality.get(),
            includeUnchanged = includeUnchanged.get(),
            appName = appName.get(),
            developerName = developerName.get(),
            appIcon = iconFile,
        )

        val htmlOutput = outputHtmlFile.get().asFile
        htmlOutput.parentFile?.mkdirs()
        htmlOutput.writeText(html)

        val markdown = DiffSummaryMarkdown.generate(diffDoc, appName = appName.orNull)
        val mdOutput = outputSummaryMarkdownFile.get().asFile
        mdOutput.parentFile?.mkdirs()
        mdOutput.writeText(markdown)

        logger.lifecycle("Store Studio: Diff report generated at: ${htmlOutput.path}")
        logger.lifecycle("Store Studio: Diff summary markdown generated at: ${mdOutput.path}")
    }
}
