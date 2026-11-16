package dev.ribod.storestudio.gradle.dsl

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/**
 * Configuration options specifying filesystem locations for textual store listing metadata.
 */
public abstract class MetadataOptions @Inject constructor(
    objects: ObjectFactory,
    layout: ProjectLayout,
) {
    /** Directory containing metadata listings (descriptions, changelogs, promotional text). */
    public val directory: DirectoryProperty = objects.directoryProperty()
        .convention(layout.projectDirectory.dir("store/metadata"))

    /** Default fallback locale when localized metadata is omitted (defaults to en-US). */
    public val defaultLocale: org.gradle.api.provider.Property<String> =
        objects.property(String::class.java).convention("en-US")
}
