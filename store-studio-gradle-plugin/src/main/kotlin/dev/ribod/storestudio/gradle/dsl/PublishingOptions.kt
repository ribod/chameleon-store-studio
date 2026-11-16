package dev.ribod.storestudio.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.time.Duration
import javax.inject.Inject

/**
 * Credentials and destination metadata required for publishing assets to application stores.
 */
public abstract class PublishingOptions @Inject constructor(objects: ObjectFactory) {
    /** Google Play Console publication credentials and package bindings. */
    public val googlePlay: GooglePlayPublishingOptions =
        objects.newInstance(GooglePlayPublishingOptions::class.java)

    /** Apple App Store Connect publication credentials and bundle bindings. */
    public val appStore: AppStorePublishingOptions =
        objects.newInstance(AppStorePublishingOptions::class.java)

    /** Configures Google Play Console publishing settings. */
    public fun googlePlay(action: Action<GooglePlayPublishingOptions>) {
        action.execute(googlePlay)
    }

    /** Configures Apple App Store Connect publishing settings. */
    public fun appStore(action: Action<AppStorePublishingOptions>) {
        action.execute(appStore)
    }
}

/**
 * Publication parameters for Google Play Console.
 */
public abstract class GooglePlayPublishingOptions @Inject constructor(objects: ObjectFactory) {
    /** Target application package name on Google Play. */
    public val packageName: Property<String> = objects.property(String::class.java)

    /** Service account private key JSON file contents or path. */
    public val serviceAccountJson: Property<String> = objects.property(String::class.java)

    /** When true, uploads are saved as draft changes not sent automatically for review. */
    public val changesNotSentForReview: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    /**
     * Release track (`internal`, `alpha`, `beta`, `production` or a custom testing track) whose
     * latest release receives the localized release notes from store metadata. Unset by default,
     * which leaves tracks untouched. Can also be passed as `--track` or
     * `-PstoreStudio.publish.track`.
     */
    public val track: Property<String> = objects.property(String::class.java)

    /** App binary (AAB) upload settings used by `storePublishPlayBinary`. */
    public val release: GooglePlayReleaseOptions =
        objects.newInstance(GooglePlayReleaseOptions::class.java)

    /** Configures the binary release. */
    public fun release(action: Action<GooglePlayReleaseOptions>) {
        action.execute(release)
    }
}

/** Google Play binary release settings. */
public abstract class GooglePlayReleaseOptions @Inject constructor(objects: ObjectFactory) {
    /**
     * Files to upload (`.aab`). When empty, the Android Gradle Plugin's bundle of
     * [variant] is used.
     */
    public val releaseFiles: ConfigurableFileCollection = objects.fileCollection()

    /** Android variant whose bundle and mapping file are uploaded by default. */
    public val variant: Property<String> = objects.property(String::class.java)
        .convention("release")

    /** Target track; defaults to `googlePlay.track`, then `internal`. */
    public val track: Property<String> = objects.property(String::class.java)

    /** `completed`, `inProgress`, `halted` or `draft`. */
    public val status: Property<String> = objects.property(String::class.java)
        .convention("completed")

    /** Fraction of users (0 < f < 1) for staged rollouts; required with `inProgress`. */
    public val userFraction: Property<Double> = objects.property(Double::class.java)

    /** In-app update priority, 0..5. */
    public val inAppUpdatePriority: Property<Int> = objects.property(Int::class.java)

    public val releaseName: Property<String> = objects.property(String::class.java)

    /** R8/ProGuard mapping file; defaults to the AGP mapping of [variant]. */
    public val mappingFile: RegularFileProperty = objects.fileProperty()

    /** Native debug symbols archive. */
    public val debugSymbols: RegularFileProperty = objects.fileProperty()

    /** Open edit to stage into; it is then NOT committed by Store Studio. */
    public val existingEditId: Property<String> = objects.property(String::class.java)

    /** Version codes of earlier uploads to keep in the release. */
    public val retainVersionCodes: ListProperty<Long> = objects.listProperty(Long::class.java)

    public val uploadTimeout: Property<Duration> = objects.property(Duration::class.java)
        .convention(Duration.ofMinutes(2))

    /** Send the release notes of the store metadata with the release. */
    public val useReleaseNotes: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)
}

/** App Store Connect binary (IPA) upload settings. */
public abstract class AppStoreBuildOptions @Inject constructor(objects: ObjectFactory) {
    public val ipaFile: RegularFileProperty = objects.fileProperty()

    /** CFBundleShortVersionString; read from the IPA when unset. */
    public val bundleShortVersion: Property<String> = objects.property(String::class.java)

    /** CFBundleVersion; read from the IPA when unset. */
    public val bundleVersion: Property<String> = objects.property(String::class.java)

    public val platform: Property<String> = objects.property(String::class.java)
        .convention("IOS")

    /** TestFlight beta group names to add the build to. */
    public val betaGroups: ListProperty<String> = objects.listProperty(String::class.java)

    public val submitBetaReview: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    public val submitForReview: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    /** Use the App Store release notes of the metadata as TestFlight "What to test". */
    public val useReleaseNotesAsWhatToTest: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)

    public val pollInterval: Property<Duration> = objects.property(Duration::class.java)
        .convention(Duration.ofSeconds(15))

    public val processingTimeout: Property<Duration> = objects.property(Duration::class.java)
        .convention(Duration.ofHours(1))
}

/**
 * Publication parameters for Apple App Store Connect.
 */
public abstract class AppStorePublishingOptions @Inject constructor(objects: ObjectFactory) {
    /** Target application bundle identifier on the App Store. */
    public val bundleId: Property<String> = objects.property(String::class.java)

    /** App Store Connect API Key ID. */
    public val keyId: Property<String> = objects.property(String::class.java)

    /** App Store Connect API Issuer ID. */
    public val issuerId: Property<String> = objects.property(String::class.java)

    /** App Store Connect PKCS#8 private key contents (AuthKey_*.p8). */
    public val privateKey: Property<String> = objects.property(String::class.java)

    /** IPA upload settings used by `storePublishAppStoreBinary`. */
    public val build: AppStoreBuildOptions =
        objects.newInstance(AppStoreBuildOptions::class.java)

    /** Configures the binary build. */
    public fun build(action: Action<AppStoreBuildOptions>) {
        action.execute(build)
    }
}
