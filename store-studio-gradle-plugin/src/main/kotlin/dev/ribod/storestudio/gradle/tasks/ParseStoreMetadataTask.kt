package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.core.metadata.StoreIcons
import dev.ribod.storestudio.core.metadata.StoreMetadataParser
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Extracts and consolidates localized store listing text into `build/store/metadata/metadata.json`.
 */
@CacheableTask
public abstract class ParseStoreMetadataTask : DefaultTask() {

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val metadataDirectory: DirectoryProperty

    @get:Input
    public abstract val defaultLocale: Property<String>

    @get:Input
    public abstract val configuredLocales: SetProperty<String>

    /** Icon paths relative to the module directory, recorded in `metadata.json` for the IDE. */
    @get:Input
    @get:Optional
    public abstract val googlePlayIcon: Property<String>

    @get:Input
    @get:Optional
    public abstract val appStoreIcon: Property<String>

    @get:OutputFile
    public abstract val metadataFile: RegularFileProperty

    init {
        group = "store studio"
        description = "Parses Text-as-Code store listing metadata with default locale fallback."
    }

    @TaskAction
    public fun parse() {
        val metaDir = if (metadataDirectory.isPresent) metadataDirectory.get().asFile else null
        val defaultLoc = defaultLocale.getOrElse("en-US")
        val locales = configuredLocales.getOrElse(emptySet())

        metaDir?.let(::warnAboutRemovedLayouts)

        val manifest = StoreMetadataParser.parse(
            metadataDir = metaDir,
            configuredLocales = locales,
            defaultLocale = defaultLoc,
        ).copy(icons = StoreIcons(googlePlayIcon.orNull, appStoreIcon.orNull))

        val outputFile = metadataFile.get().asFile
        manifest.toFile(outputFile)

        logger.lifecycle(
            "Store Studio: Consolidated metadata for ${manifest.locales.size} locale(s) " +
                "into ${outputFile.path}",
        )
    }

    /** Flags unsupported metadata files or directories so they do not silently produce empty listings. */
    private fun warnAboutRemovedLayouts(dir: java.io.File) {
        val (removed, locales) = dir.listFiles().orEmpty().filter { it.isDirectory }
            .partition { it.name in REMOVED_STORE_DIRS }
        val strayTexts = locales.flatMap { locale ->
            locale.listFiles { file -> file.extension == "txt" }.orEmpty().toList()
        }
        val legacy = removed + strayTexts
        for (path in legacy) {
            logger.warn(
                "Store Studio: Ignoring '${path.relativeTo(
                    dir,
                ).path}' in the metadata directory. " +
                    "Use '<locale>/play/' and '<locale>/appstore/' folders.",
            )
        }
    }

    private companion object {
        val REMOVED_STORE_DIRS = setOf("android", "ios", "appstore")
    }
}
