package dev.ribod.storestudio.gradle.dsl

import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.gradle.StoreStudioBuildConfig
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

/**
 * Primary Gradle extension DSL for configuring the Chameleon Store Studio pipeline.
 *
 * Configured in `build.gradle.kts` via:
 * ```kotlin
 * storeStudio {
 *     targets.set(setOf(StoreTarget.PLAY_STORE, StoreTarget.APP_STORE))
 *     defaultDevices.set(setOf(DeviceFamily.PHONE, DeviceFamily.TABLET))
 *     locales {
 *         register("en-US")
 *         register("es-MX") {
 *             playStore.set("es-419")
 *         }
 *     }
 * }
 * ```
 */
public abstract class StoreStudioExtension @Inject constructor(objects: ObjectFactory) {
    /** Target Android build variant or compilation used for screenshot execution. */
    public val variant: Property<String> = objects.property(String::class.java)
        .convention("debug")

    /** Whether to automatically inject annotations, runtime, and KSP dependencies. */
    public val autoDependencies: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)

    /** Store Studio library and processor version to inject automatically. */
    public val pluginVersion: Property<String> = objects.property(String::class.java)
        .convention(StoreStudioBuildConfig.PLUGIN_VERSION)

    /** Global set of marketplaces targeted for screenshot generation and publishing. */
    public val targets: SetProperty<StoreTarget> = objects.setProperty(StoreTarget::class.java)
        .convention(setOf(StoreTarget.PLAY_STORE, StoreTarget.APP_STORE))

    /** Baseline device families rendered when a screenshot specifies no explicit device. */
    public val defaultDevices: SetProperty<DeviceFamily> = objects.setProperty(
        DeviceFamily::class.java,
    )
        .convention(setOf(DeviceFamily.PHONE, DeviceFamily.TABLET))

    /** Configured collection of target localization languages. */
    public val locales: NamedDomainObjectContainer<LocaleSpec> =
        objects.domainObjectContainer(LocaleSpec::class.java)

    /** Headless rendering engine parameters and process isolation settings. */
    public val rendering: RenderingOptions = objects.newInstance(RenderingOptions::class.java)

    /** Fake device status bar drawn on device mockups (off by default). */
    public val statusBar: StatusBarOptions = objects.newInstance(StatusBarOptions::class.java)

    /** Asset export, alpha flattening, and encoding options. */
    public val export: ExportOptions = objects.newInstance(ExportOptions::class.java)

    /** Store compliance rules engine and verification options. */
    public val validation: ValidationOptions = objects.newInstance(ValidationOptions::class.java)

    /** Apple App Store specific submission overrides. */
    public val appStore: AppStoreOptions = objects.newInstance(AppStoreOptions::class.java)

    /** App icon location of each store. */
    public val icons: IconOptions = objects.newInstance(IconOptions::class.java)

    /** Filesystem paths for textual store listing descriptions. */
    public val metadata: MetadataOptions = objects.newInstance(MetadataOptions::class.java)

    /** Visual regression baseline configuration and drift thresholds. */
    public val baseline: BaselineOptions = objects.newInstance(BaselineOptions::class.java)

    /** Store publication credentials and package identifiers. */
    public val publishing: PublishingOptions = objects.newInstance(PublishingOptions::class.java)

    /** HTML gallery and store listing preview reports options. */
    public val reports: ReportsOptions = objects.newInstance(ReportsOptions::class.java)

    /** Continuous Integration quality gates, drift enforcement, and PR commenting options. */
    public val ci: CiOptions = objects.newInstance(CiOptions::class.java)

    /** Configures asset export options block. */
    public fun export(action: Action<ExportOptions>) {
        action.execute(export)
    }

    /** Configures store compliance validation options block. */
    public fun validation(action: Action<ValidationOptions>) {
        action.execute(validation)
    }

    /** Configures target locales container. */
    public fun locales(action: Action<NamedDomainObjectContainer<LocaleSpec>>) {
        action.execute(locales)
    }

    /** Convenience helper registering a locale directly. */
    public fun locale(name: String, action: Action<LocaleSpec> = Action {}) {
        locales.register(name, action)
    }

    /** Configures rendering options block. */
    public fun rendering(action: Action<RenderingOptions>) {
        action.execute(rendering)
    }

    /** Configures the fake status bar block. */
    public fun statusBar(action: Action<StatusBarOptions>) {
        action.execute(statusBar)
    }

    /** Configures App Store options block. */
    public fun appStore(action: Action<AppStoreOptions>) {
        action.execute(appStore)
    }

    /** Configures the per-store app icon locations. */
    public fun icons(action: Action<IconOptions>) {
        action.execute(icons)
    }

    /** Configures metadata options block. */
    public fun metadata(action: Action<MetadataOptions>) {
        action.execute(metadata)
    }

    /** Configures baseline options block. */
    public fun baseline(action: Action<BaselineOptions>) {
        action.execute(baseline)
    }

    /** Configures publishing options block. */
    public fun publishing(action: Action<PublishingOptions>) {
        action.execute(publishing)
    }

    /** Configures reports options block. */
    public fun reports(action: Action<ReportsOptions>) {
        action.execute(reports)
    }

    /** Configures continuous integration (CI) options block. */
    public fun ci(action: Action<CiOptions>) {
        action.execute(ci)
    }
}
