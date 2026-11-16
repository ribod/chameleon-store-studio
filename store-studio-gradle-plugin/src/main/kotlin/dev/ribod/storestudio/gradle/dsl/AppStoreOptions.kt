package dev.ribod.storestudio.gradle.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

/**
 * Platform-specific customization options for Apple App Store submissions.
 */
public abstract class AppStoreOptions @Inject constructor(objects: ObjectFactory) {
    /** Whether the application includes iPad target listings and generates 13" iPad assets. */
    public val supportsIpad: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)

    /** Additional legacy iPhone slot sizes to generate beyond the primary 6.9-inch slot. */
    public val extraIphoneSizes: SetProperty<String> = objects.setProperty(String::class.java)
        .convention(emptySet())
}
