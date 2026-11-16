package dev.ribod.storestudio.gradle.tasks

import com.skydoves.sandwich.suspendOnError
import com.skydoves.sandwich.suspendOnException
import com.skydoves.sandwich.suspendOnSuccess
import dev.ribod.storestudio.core.publish.appstore.AppStoreBinaryPublishRequest
import dev.ribod.storestudio.core.publish.appstore.AppStorePublisher
import dev.ribod.storestudio.core.publish.appstore.publishBinary
import dev.ribod.storestudio.core.publish.play.PlayBinaryPublishRequest
import dev.ribod.storestudio.core.publish.play.PlayReleaseStatus
import dev.ribod.storestudio.core.publish.play.PlayStorePublisher
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import java.io.File
import java.time.Duration

internal fun parsePlayReleaseStatus(value: String): PlayReleaseStatus {
    val norm = value.trim().replace("_", "").lowercase()
    return PlayReleaseStatus.entries.firstOrNull { it.name.replace("_", "").lowercase() == norm }
        ?: throw GradleException(
            "Store Studio: Invalid release status '$value'. Use completed, inProgress, halted " +
                "or draft.",
        )
}

internal fun blankToNull(value: String?): String? = value?.trim()?.ifEmpty { null }

internal fun parseUploadTimeout(value: String): Duration {
    val trimmed = value.trim()
    return trimmed.toLongOrNull()?.let { Duration.ofSeconds(it) }
        ?: Regex("(\\d+)([ms])", RegexOption.IGNORE_CASE).matchEntire(trimmed)?.let {
            val n = it.groupValues[1].toLong()
            if (it.groupValues[2].equals("m", ignoreCase = true)) {
                Duration.ofMinutes(n)
            } else {
                Duration.ofSeconds(n)
            }
        }
        ?: runCatching { Duration.parse(trimmed) }.getOrNull()
        ?: throw GradleException(
            "Store Studio: Invalid --upload-timeout '$value'. Use e.g. 2m, 120s, or PT2M.",
        )
}

/**
 * Uploads the app binary (AAB) to a Google Play track. Independent of the asset export and
 * validation gate; only the release notes need the parsed metadata.
 */
@UntrackedTask(because = "Communicates with remote Google Play Developer API endpoints")
public abstract class StorePublishPlayBinaryTask : DefaultTask() {

    @get:Input
    @get:Optional
    public abstract val packageName: Property<String>

    @get:Internal
    public abstract val serviceAccountJson: Property<String>

    @get:Input
    @get:Optional
    public abstract val track: Property<String>

    @get:Input
    @get:Optional
    public abstract val status: Property<String>

    @get:Input
    @get:Optional
    public abstract val userFraction: Property<Double>

    @get:Input
    @get:Optional
    public abstract val inAppUpdatePriority: Property<Int>

    @get:Input
    @get:Optional
    public abstract val releaseName: Property<String>

    @get:Input
    @get:Optional
    public abstract val existingEditId: Property<String>

    @get:Input
    @get:Optional
    public abstract val retainVersionCodes: ListProperty<Long>

    @get:Input
    @get:Optional
    public abstract val changesNotSentForReview: Property<Boolean>

    @get:Input
    @get:Optional
    public abstract val uploadTimeout: Property<Duration>

    @get:Input
    @get:Optional
    public abstract val useReleaseNotes: Property<Boolean>

    /** Files configured in the DSL. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract val releaseFiles: ConfigurableFileCollection

    /** Paths given with `--release-file`; override [releaseFiles] and [agpBundle]. */
    @get:Input
    @get:Optional
    public abstract val cliReleaseFiles: ListProperty<String>

    /** The AGP bundle of the release variant, used when no other files are given. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract val agpBundle: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract val mappingFile: RegularFileProperty

    /** The AGP mapping file of the release variant, used when [mappingFile] is unset. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract val agpMapping: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract val debugSymbols: RegularFileProperty

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val metadataDirectory: DirectoryProperty

    /** DSL locale to store locale (`playStore`/`appStore` alias, identity when unset). */
    @get:Input
    @get:Optional
    public abstract val storeLocales: MapProperty<String, String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val metadataFile: RegularFileProperty

    @get:Input
    @get:Optional
    public abstract val dryRun: Property<Boolean>

    @get:Internal
    public abstract val dryRunFromProperties: Property<Boolean>

    init {
        group = "store studio"
        description = "Uploads the app bundle to a Google Play track."
    }

    @Option(option = "track", description = "Google Play track to release to.")
    public fun setTrackOption(value: String) {
        track.set(value)
    }

    @Option(
        option = "release-status",
        description = "Release status: completed, inProgress, halted or draft.",
    )
    public fun setReleaseStatusOption(value: String) {
        status.set(value)
    }

    @Option(option = "user-fraction", description = "Rollout fraction (0-1) for inProgress.")
    public fun setUserFractionOption(value: String) {
        userFraction.set(
            value.toDoubleOrNull()
                ?: throw GradleException("Store Studio: Invalid --user-fraction '$value'."),
        )
    }

    @Option(
        option = "upload-timeout",
        description = "Upload and socket timeout (e.g. 2m, 120s, PT2M, or seconds).",
    )
    public fun setUploadTimeoutOption(value: String) {
        uploadTimeout.set(parseUploadTimeout(value))
    }

    @Option(option = "release-file", description = "AAB to upload (repeatable).")
    public fun setReleaseFileOption(values: List<String>) {
        cliReleaseFiles.set(values)
    }

    @Option(option = "mapping-file", description = "R8/ProGuard mapping file to upload.")
    public fun setMappingFileOption(value: String) {
        mappingFile.set(File(value))
    }

    @Option(option = "release-name", description = "Name of the release.")
    public fun setReleaseNameOption(value: String) {
        releaseName.set(value)
    }

    @Option(option = "in-app-update-priority", description = "In-app update priority, 0-5.")
    public fun setInAppUpdatePriorityOption(value: String) {
        inAppUpdatePriority.set(
            value.toIntOrNull()
                ?: throw GradleException(
                    "Store Studio: Invalid --in-app-update-priority '$value'.",
                ),
        )
    }

    @Option(
        option = "existing-edit-id",
        description = "Stage into this open edit instead of committing a new one.",
    )
    public fun setExistingEditIdOption(value: String) {
        existingEditId.set(value)
    }

    @Option(
        option = "changes-not-sent-for-review",
        description = "Do not send the changes automatically for review.",
    )
    public fun setChangesNotSentOption(enabled: Boolean) {
        changesNotSentForReview.set(enabled)
    }

    @Option(option = "publish-dry-run", description = "Validates without committing changes.")
    public fun setPublishDryRunOption(enabled: Boolean) {
        dryRun.set(enabled)
    }

    @TaskAction
    public fun publish() {
        val pkg = blankToNull(packageName.orNull)
            ?: throw GradleException(
                "Store Studio: Google Play package name must be configured via " +
                    "publishing.googlePlay.packageName or --package-name",
            )
        val files = when {
            cliReleaseFiles.getOrElse(emptyList()).isNotEmpty() ->
                cliReleaseFiles.get().map { File(it) }

            !releaseFiles.isEmpty -> releaseFiles.files.toList()

            agpBundle.isPresent -> listOf(agpBundle.get().asFile)

            else -> throw GradleException(
                "Store Studio: No release files. Set publishing.googlePlay.release.releaseFiles, " +
                    "pass --release-file, or apply the Android Gradle plugin so the " +
                    "'release' variant bundle can be used.",
            )
        }
        val releaseStatus = parsePlayReleaseStatus(status.getOrElse("completed"))
        val notes = if (useReleaseNotes.getOrElse(true)) {
            val dir = metadataDirectory.orNull?.asFile
            loadPublishMetadata(
                metadataFile.orNull?.asFile,
                dir,
                emptyList(),
                storeLocales.getOrElse(emptyMap()),
            )
                ?.toPlayReleaseNotesMap().orEmpty()
        } else {
            emptyMap()
        }
        val request = PlayBinaryPublishRequest(
            packageName = pkg,
            serviceAccountJson = blankToNull(serviceAccountJson.orNull)
                ?: blankToNull(System.getenv("PLAY_SERVICE_ACCOUNT_JSON")),
            releaseFiles = files,
            track = requireValidPlayTrack(track.orNull) ?: "internal",
            status = releaseStatus,
            userFraction = userFraction.orNull,
            inAppUpdatePriority = inAppUpdatePriority.orNull,
            releaseName = blankToNull(releaseName.orNull),
            releaseNotesByLocale = notes,
            mappingFile = (mappingFile.orNull ?: agpMapping.orNull)?.asFile,
            debugSymbolsFile = debugSymbols.orNull?.asFile,
            changesNotSentForReview = changesNotSentForReview.getOrElse(false),
            existingEditId = blankToNull(existingEditId.orNull),
            retainVersionCodes = retainVersionCodes.getOrElse(emptyList()),
            dryRun = dryRun.getOrElse(false) || dryRunFromProperties.getOrElse(false),
            uploadTimeoutMs = uploadTimeout.map { it.toMillis() }
                .getOrElse(PlayBinaryPublishRequest.DEFAULT_UPLOAD_TIMEOUT_MS),
        )
        runPlayBinaryPublish(logger, request)
    }
}

/** Uploads the IPA to App Store Connect (TestFlight), optionally submitting it for review. */
@UntrackedTask(because = "Communicates with remote Apple App Store Connect API endpoints")
public abstract class StorePublishAppStoreBinaryTask : DefaultTask() {

    @get:Input
    @get:Optional
    public abstract val bundleId: Property<String>

    @get:Input
    @get:Optional
    public abstract val keyId: Property<String>

    @get:Input
    @get:Optional
    public abstract val issuerId: Property<String>

    @get:Internal
    public abstract val privateKey: Property<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract val ipaFile: RegularFileProperty

    @get:Input
    @get:Optional
    public abstract val bundleShortVersion: Property<String>

    @get:Input
    @get:Optional
    public abstract val bundleVersion: Property<String>

    @get:Input
    @get:Optional
    public abstract val platform: Property<String>

    @get:Input
    @get:Optional
    public abstract val betaGroups: ListProperty<String>

    @get:Input
    @get:Optional
    public abstract val submitBetaReview: Property<Boolean>

    @get:Input
    @get:Optional
    public abstract val submitForReview: Property<Boolean>

    @get:Input
    @get:Optional
    public abstract val useReleaseNotesAsWhatToTest: Property<Boolean>

    @get:Input
    @get:Optional
    public abstract val pollInterval: Property<Duration>

    @get:Input
    @get:Optional
    public abstract val processingTimeout: Property<Duration>

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val metadataDirectory: DirectoryProperty

    /** DSL locale to store locale (`playStore`/`appStore` alias, identity when unset). */
    @get:Input
    @get:Optional
    public abstract val storeLocales: MapProperty<String, String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val metadataFile: RegularFileProperty

    @get:Input
    @get:Optional
    public abstract val dryRun: Property<Boolean>

    @get:Internal
    public abstract val dryRunFromProperties: Property<Boolean>

    init {
        group = "store studio"
        description = "Uploads the IPA to Apple App Store Connect."
    }

    @Option(option = "ipa", description = "IPA file to upload.")
    public fun setIpaOption(value: String) {
        ipaFile.set(File(value))
    }

    @Option(option = "bundle-version", description = "CFBundleVersion (build number).")
    public fun setBundleVersionOption(value: String) {
        bundleVersion.set(value)
    }

    @Option(option = "bundle-short-version", description = "CFBundleShortVersionString.")
    public fun setBundleShortVersionOption(value: String) {
        bundleShortVersion.set(value)
    }

    @Option(option = "beta-group", description = "TestFlight beta group (repeatable).")
    public fun setBetaGroupOption(values: List<String>) {
        betaGroups.set(values)
    }

    @Option(option = "submit-beta-review", description = "Submit the build for beta review.")
    public fun setSubmitBetaReviewOption(enabled: Boolean) {
        submitBetaReview.set(enabled)
    }

    @Option(option = "submit-for-review", description = "Submit the build for App Store review.")
    public fun setSubmitForReviewOption(enabled: Boolean) {
        submitForReview.set(enabled)
    }

    @Option(option = "publish-dry-run", description = "Validates without uploading.")
    public fun setPublishDryRunOption(enabled: Boolean) {
        dryRun.set(enabled)
    }

    @TaskAction
    public fun publish() {
        val id = blankToNull(bundleId.orNull)
            ?: throw GradleException(
                "Store Studio: Apple App Store bundle identifier must be configured via " +
                    "publishing.appStore.bundleId",
            )
        val ipa = ipaFile.orNull?.asFile
            ?: throw GradleException(
                "Store Studio: No IPA file. Set publishing.appStore.build.ipaFile or pass --ipa.",
            )
        val whatToTest = if (useReleaseNotesAsWhatToTest.getOrElse(true)) {
            loadPublishMetadata(
                metadataFile.orNull?.asFile,
                metadataDirectory.orNull?.asFile,
                emptyList(),
                storeLocales.getOrElse(emptyMap()),
            )
                ?.toAppStoreMetadataMap().orEmpty()
                .mapNotNull { (locale, meta) -> meta.whatsNew?.let { locale to it } }.toMap()
        } else {
            emptyMap()
        }
        val request = AppStoreBinaryPublishRequest(
            bundleId = id,
            keyId = blankToNull(keyId.orNull) ?: blankToNull(System.getenv("ASC_KEY_ID")),
            issuerId = blankToNull(issuerId.orNull) ?: blankToNull(System.getenv("ASC_ISSUER_ID")),
            privateKey = blankToNull(privateKey.orNull)
                ?: blankToNull(System.getenv("ASC_PRIVATE_KEY")),
            ipaFile = ipa,
            bundleShortVersion = blankToNull(bundleShortVersion.orNull),
            bundleVersion = blankToNull(bundleVersion.orNull),
            platform = platform.getOrElse("IOS"),
            whatToTestByLocale = whatToTest,
            betaGroups = betaGroups.getOrElse(emptyList()),
            submitBetaReview = submitBetaReview.getOrElse(false),
            submitForReview = submitForReview.getOrElse(false),
            dryRun = dryRun.getOrElse(false) || dryRunFromProperties.getOrElse(false),
            pollIntervalMs = pollInterval.map { it.toMillis() }.getOrElse(15_000L),
            processingTimeoutMs = processingTimeout.map { it.toMillis() }.getOrElse(3_600_000L),
        )
        runAppStoreBinaryPublish(logger, request)
    }
}

internal fun runPlayBinaryPublish(logger: Logger, request: PlayBinaryPublishRequest) {
    PlayStorePublisher.create().use { publisher ->
        runBlocking {
            publisher.publishBinary(request).suspendOnSuccess {
                logger.lifecycle(data.formattedReport)
            }.suspendOnError {
                val msg = "Store Studio: Google Play binary publication failed: $payload"
                logger.error(msg)
                throw GradleException(msg)
            }.suspendOnException {
                val msg = "Store Studio: Google Play binary publication exception: " +
                    throwable.message
                logger.error(msg, throwable)
                throw GradleException(msg, throwable)
            }
        }
    }
}

internal fun runAppStoreBinaryPublish(logger: Logger, request: AppStoreBinaryPublishRequest) {
    AppStorePublisher.create().use { publisher ->
        runBlocking {
            publisher.publishBinary(request).suspendOnSuccess {
                logger.lifecycle(data.formattedReport)
            }.suspendOnError {
                val msg = "Store Studio: App Store Connect binary publication failed: $payload"
                logger.error(msg)
                throw GradleException(msg)
            }.suspendOnException {
                val msg = "Store Studio: App Store Connect binary publication exception: " +
                    throwable.message
                logger.error(msg, throwable)
                throw GradleException(msg, throwable)
            }
        }
    }
}
