package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.metadata.StoreMetadataManifest
import dev.ribod.storestudio.gradle.internal.ExportPaths
import dev.ribod.storestudio.model.ExportLayout
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

/**
 * Writes the parsed listing texts and release notes as plain files, so other tools and CI steps
 * can consume them.
 *
 * Per enabled store and locale, `metadata/` next to the images holds one file per non-empty
 * field, named like the input files (`title.txt`, `release_notes.txt`, ...). With
 * [ExportLayout.LOCALE_DEVICE], where both stores share the locale folder, each store gets
 * `<locale>/metadata/<store>/` instead. Google Play release notes are also written to
 * `<play store folder>/release-notes/whatsnew-<locale>` (no extension), the flat layout Play
 * upload tooling expects.
 */
@CacheableTask
public abstract class ExportStoreMetadataTask : DefaultTask() {

    @get:Inject
    public abstract val fileSystemOperations: FileSystemOperations

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val metadataFile: RegularFileProperty

    @get:Input
    public abstract val layout: Property<ExportLayout>

    @get:Input
    public abstract val lowercaseFolders: Property<Boolean>

    @get:Input
    public abstract val targets: SetProperty<StoreTarget>

    /** Kept apart from the image export: `storeExport` wipes its own directory on every run. */
    @get:OutputDirectory
    public abstract val outputDirectory: DirectoryProperty

    init {
        group = "store studio"
        description = "Exports listing texts and release notes as plain files."
    }

    @TaskAction
    public fun export() {
        val outDir = outputDirectory.get().asFile
        fileSystemOperations.delete { spec -> spec.delete(outDir) }
        outDir.mkdirs()

        val manifest = StoreMetadataManifest.fromFile(metadataFile.get().asFile)
        val exportLayout = layout.get()
        val lowercase = lowercaseFolders.get()
        var written = 0

        fun write(file: File, text: String) {
            if (text.isBlank()) return
            file.parentFile.mkdirs()
            file.writeText(text)
            written++
        }

        for (target in targets.get()) {
            val store = ExportPaths.storeFolder(target, lowercase)
            for ((locale, bundle) in manifest.locales) {
                val base = ExportPaths.localeFolder(exportLayout, lowercase, target, locale)
                val shared = exportLayout == ExportLayout.LOCALE_DEVICE
                val dir = outDir.resolve(if (shared) "$base/metadata/$store" else "$base/metadata")
                val fields: Map<String, String> = when (target) {
                    StoreTarget.PLAY_STORE -> with(bundle.play) {
                        mapOf(
                            "title" to title,
                            "short_description" to shortDescription,
                            "full_description" to fullDescription,
                            "release_notes" to releaseNotes,
                        )
                    }

                    StoreTarget.APP_STORE -> with(bundle.appStore) {
                        mapOf(
                            "name" to name,
                            "subtitle" to subtitle,
                            "keywords" to keywords,
                            "promotional_text" to promotionalText,
                            "description" to description,
                            "release_notes" to releaseNotes,
                            "support_url" to supportUrl,
                            "marketing_url" to marketingUrl,
                        )
                    }
                }
                for ((name, text) in fields) write(dir.resolve("$name.txt"), text)
                if (target == StoreTarget.PLAY_STORE) {
                    val whatsNew = outDir.resolve("$store/release-notes/whatsnew-$locale")
                    write(whatsNew, bundle.play.releaseNotes)
                }
            }
        }

        logger.lifecycle("Store Studio: Exported $written listing text file(s) to ${outDir.path}.")
    }
}
