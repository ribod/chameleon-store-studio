package dev.ribod.storestudio.gradle.dsl

import org.gradle.api.Named
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration specification for a targeted marketing localization locale.
 *
 * @param name BCP-47 language tag identifying the locale (e.g. "en-US", "es-MX").
 */
public abstract class LocaleSpec @Inject constructor(
    private val name: String,
    objects: ObjectFactory,
) : Named {

    override fun getName(): String = name

    /**
     * Optional store-specific language code mapping for Google Play Console (e.g. "es-419").
     *
     * When left unset, the canonical [name] tag is used directly.
     */
    public val playStore: Property<String> = objects.property(String::class.java)

    /**
     * Optional store-specific language code mapping for Apple App Store Connect.
     */
    public val appStore: Property<String> = objects.property(String::class.java)
}
