package dev.ribod.storestudio.gradle.dsl

import dev.ribod.storestudio.model.Severity
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration options controlling app store compliance verification and severity rules.
 */
public abstract class ValidationOptions @Inject constructor(objects: ObjectFactory) {

    /**
     * Whether warnings should be treated as fatal errors failing build verification.
     */
    public val failOnWarning: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    /**
     * Severity applied when a required Google Play 1024x500 feature graphic is missing.
     */
    public val featureGraphicMissing: Property<Severity> =
        objects.property(Severity::class.java)
            .convention(Severity.ERROR)

    /**
     * Custom ruleId-to-severity override map.
     *
     * Store hard requirements cannot be downgraded below [Severity.ERROR].
     */
    public val severityOverrides: MapProperty<String, Severity> =
        objects.mapProperty(String::class.java, Severity::class.java)
            .convention(emptyMap())

    /**
     * Whether the store compliance validation task automatically attaches to the lifecycle check.
     */
    public val attachToCheck: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)
}
