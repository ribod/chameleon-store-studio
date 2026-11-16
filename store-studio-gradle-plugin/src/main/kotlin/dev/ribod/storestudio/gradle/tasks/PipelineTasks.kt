package dev.ribod.storestudio.gradle.tasks

import com.skydoves.sandwich.suspendOnError
import com.skydoves.sandwich.suspendOnException
import com.skydoves.sandwich.suspendOnSuccess
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.publish.PublishPart
import dev.ribod.storestudio.core.publish.appstore.AppStoreBinaryPublishRequest
import dev.ribod.storestudio.core.publish.appstore.AppStoreLocalizedMetadata
import dev.ribod.storestudio.core.publish.appstore.AppStorePublishRequest
import dev.ribod.storestudio.core.publish.appstore.AppStorePublisher
import dev.ribod.storestudio.core.publish.play.PlayBinaryPublishRequest
import dev.ribod.storestudio.core.publish.play.PlayPublishRequest
import dev.ribod.storestudio.core.publish.play.PlayStorePublisher
import dev.ribod.storestudio.core.report.model.StoreListingMetadata
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.Severity
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
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

/**
 * Uploads approved assets and metadata to Google Play Console with publishing gate verification.
 */
@UntrackedTask(because = "Communicates with remote Google Play Developer API endpoints")
public abstract class StorePublishPlayTask : DefaultTask() {

    @get:Input
    @get:Optional
    public abstract val includeBinary: Property<Boolean>

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
    public abstract val exportDirectory: DirectoryProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val reportFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val exportIndexFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val inputsHashFile: RegularFileProperty

    @get:Input
    @get:Optional
    public abstract val packageName: Property<String>

    @get:Internal
    public abstract val serviceAccountJson: Property<String>

    @get:Input
    @get:Optional
    public abstract val changesNotSentForReview: Property<Boolean>

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

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val appIcon: RegularFileProperty

    /** Parts of the listing this task publishes. */
    @get:Input
    public abstract val parts: SetProperty<PublishPart>

    @get:Input
    @get:Optional
    public abstract val cliParts: Property<String>

    @get:Internal
    public abstract val partsFromProperties: Property<String>

    @Option(
        option = "parts",
        description = "Comma-separated parts to publish: binary, texts, images, releaseNotes, all.",
    )
    public fun setPartsOption(value: String) {
        cliParts.set(value)
    }

    /** Severity overrides applied to the metadata rules of text-only publishes. */
    @get:Input
    @get:Optional
    public abstract val severityOverrides: MapProperty<String, Severity>

    /** Whether warnings of the metadata rules fail a text-only publish. */
    @get:Input
    @get:Optional
    public abstract val failOnWarning: Property<Boolean>

    @get:Input
    @get:Optional
    public abstract val dryRun: Property<Boolean>

    /**
     * Dry-run requested through the `storeStudio.publish.dryRun` or `dryRun` Gradle properties,
     * resolved at configuration time so the task action never touches the `Project` model.
     */
    @get:Internal
    public abstract val dryRunFromProperties: Property<Boolean>

    @Option(
        option = "publish-dry-run",
        description = "Validates and displays publish plan without committing changes.",
    )
    public fun setPublishDryRunOption(enabled: Boolean) {
        dryRun.set(enabled)
    }

    @Option(option = "package-name", description = "Target package name on Google Play.")
    public fun setPackageNameOption(pkg: String) {
        packageName.set(pkg)
    }

    @Option(
        option = "changes-not-sent-for-review",
        description = "Saves uploads as draft without submitting for review.",
    )
    public fun setChangesNotSentForReviewOption(enabled: Boolean) {
        changesNotSentForReview.set(enabled)
    }

    /** Release track whose latest release receives the release notes; unset leaves tracks as-is. */
    @get:Input
    @get:Optional
    public abstract val track: Property<String>

    @Option(
        option = "track",
        description = "Google Play track (internal, alpha, beta, production or custom) " +
            "whose latest release receives the release notes.",
    )
    public fun setTrackOption(value: String) {
        track.set(value)
    }

    @Option(
        option = "release-status",
        description = "Release status (completed, inProgress, halted, draft).",
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

    init {
        group = "store studio"
        description = "Uploads assets and metadata to Google Play Console."
        parts.convention(PublishPart.ALL)
        includeBinary.convention(false)
    }

    @TaskAction
    public fun publish() {
        val (selected, withBinary) = resolveEffectivePublishSelection(
            defaultParts = parts.get(),
            defaultIncludeBinary = includeBinary.getOrElse(false),
            cliParts = cliParts.orNull,
            propParts = partsFromProperties.orNull,
        )
        val withImages = PublishPart.IMAGES in selected
        val exportIndex = if (withImages) {
            enforcePublishingGate(
                reportFile.get().asFile,
                exportIndexFile.get().asFile,
                inputsHashFile.get().asFile,
            )
            ExportIndex.fromFile(exportIndexFile.get().asFile)
                .withStoreLocales(storeLocales.getOrElse(emptyMap()), StoreTarget.PLAY_STORE)
        } else {
            ExportIndex()
        }

        val targetPackage = packageName.orNull?.ifBlank { null }
            ?: throw GradleException(
                "Store Studio: Google Play package name must be configured via " +
                    "publishing.googlePlay.packageName or --package-name",
            )

        val assetLocales = exportIndex.assets.map { it.storeLocale }.distinct().sorted()

        val metaDir = if (metadataDirectory.isPresent) metadataDirectory.get().asFile else null
        val loaded = loadPublishMetadata(
            metadataFile.orNull?.asFile,
            metaDir,
            assetLocales,
            storeLocales.getOrElse(emptyMap()),
        )
        val metadata = if (withImages) {
            loaded
        } else {
            validateTextForPublish(
                loaded,
                StoreTarget.PLAY_STORE,
                selected,
                severityOverrides.getOrElse(emptyMap()),
                failOnWarning.getOrElse(false),
                logger,
            )
        }
        val locales = if (withImages) assetLocales else metadata?.locales?.keys.orEmpty().sorted()
        val metadataMap = metadata?.toPlayListingMetadataMap()?.let { map ->
            locales.associateWith { locale -> map[locale] ?: StoreListingMetadata(title = "") }
        } ?: emptyMap()

        val creds = serviceAccountJson.orNull?.ifBlank { null }
            ?: System.getenv("PLAY_SERVICE_ACCOUNT_JSON")?.ifBlank { null }

        val isDryRun = dryRun.getOrElse(false) || dryRunFromProperties.getOrElse(false)
        val iconFile = if (appIcon.isPresent && appIcon.get().asFile.exists()) {
            appIcon.get().asFile
        } else {
            null
        }

        val binaryRequest = if (withBinary) {
            val files = when {
                cliReleaseFiles.getOrElse(emptyList()).isNotEmpty() ->
                    cliReleaseFiles.get().map { File(it) }

                !releaseFiles.isEmpty -> releaseFiles.files.toList()

                agpBundle.isPresent -> listOf(agpBundle.get().asFile)

                else -> throw GradleException(
                    "Store Studio: No release files. Set publishing.googlePlay.release." +
                        "releaseFiles, pass --release-file, or apply the Android Gradle " +
                        "plugin so the 'release' variant bundle can be used. To publish " +
                        "only the store listing without a binary, run 'storePublishPlayListing'.",
                )
            }
            val releaseStatus = parsePlayReleaseStatus(status.getOrElse("completed"))
            val notes = if (useReleaseNotes.getOrElse(true)) {
                metadata?.toPlayReleaseNotesMap().orEmpty()
            } else {
                emptyMap()
            }
            PlayBinaryPublishRequest(
                packageName = targetPackage,
                serviceAccountJson = creds,
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
                dryRun = isDryRun,
                uploadTimeoutMs = uploadTimeout.map { it.toMillis() }
                    .getOrElse(PlayBinaryPublishRequest.DEFAULT_UPLOAD_TIMEOUT_MS),
            )
        } else {
            null
        }

        val request = PlayPublishRequest(
            packageName = targetPackage,
            serviceAccountJson = creds,
            exportDir = exportDirectory.asFile.orNull ?: temporaryDir,
            assets = exportIndex.assets,
            listingsByLocale = metadataMap,
            appIconFile = iconFile,
            dryRun = isDryRun,
            changesNotSentForReview = changesNotSentForReview.getOrElse(false),
            track = requireValidPlayTrack(track.orNull),
            releaseNotesByLocale = metadata?.toPlayReleaseNotesMap().orEmpty(),
            parts = selected,
            binary = binaryRequest,
        )

        runPlayPublish(logger, request)
    }
}

/**
 * Uploads approved assets and metadata to Apple App Store Connect with gate verification.
 */
@UntrackedTask(because = "Communicates with remote Apple App Store Connect API endpoints")
public abstract class StorePublishAppStoreTask : DefaultTask() {

    @get:Input
    @get:Optional
    public abstract val includeBinary: Property<Boolean>

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
    public abstract val exportDirectory: DirectoryProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val reportFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val exportIndexFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val inputsHashFile: RegularFileProperty

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

    /** Parts of the listing this task publishes. */
    @get:Input
    public abstract val parts: SetProperty<PublishPart>

    @get:Input
    @get:Optional
    public abstract val cliParts: Property<String>

    @get:Internal
    public abstract val partsFromProperties: Property<String>

    @Option(
        option = "parts",
        description = "Comma-separated parts to publish: binary, texts, images, releaseNotes, all.",
    )
    public fun setPartsOption(value: String) {
        cliParts.set(value)
    }

    /** Severity overrides applied to the metadata rules of text-only publishes. */
    @get:Input
    @get:Optional
    public abstract val severityOverrides: MapProperty<String, Severity>

    /** Whether warnings of the metadata rules fail a text-only publish. */
    @get:Input
    @get:Optional
    public abstract val failOnWarning: Property<Boolean>

    @get:Input
    @get:Optional
    public abstract val dryRun: Property<Boolean>

    /**
     * Dry-run requested through the `storeStudio.publish.dryRun` or `dryRun` Gradle properties,
     * resolved at configuration time so the task action never touches the `Project` model.
     */
    @get:Internal
    public abstract val dryRunFromProperties: Property<Boolean>

    @Option(
        option = "publish-dry-run",
        description = "Validates and displays publish plan without committing changes.",
    )
    public fun setPublishDryRunOption(enabled: Boolean) {
        dryRun.set(enabled)
    }

    @Option(
        option = "bundle-id",
        description = "Target iOS bundle identifier on App Store Connect.",
    )
    public fun setBundleIdOption(id: String) {
        bundleId.set(id)
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

    init {
        group = "store studio"
        description = "Uploads assets and metadata to Apple App Store Connect."
        parts.convention(PublishPart.ALL)
        includeBinary.convention(false)
    }

    @TaskAction
    public fun publish() {
        val (selected, withBinary) = resolveEffectivePublishSelection(
            defaultParts = parts.get(),
            defaultIncludeBinary = includeBinary.getOrElse(false),
            cliParts = cliParts.orNull,
            propParts = partsFromProperties.orNull,
        )
        val withImages = PublishPart.IMAGES in selected
        val exportIndex = if (withImages) {
            enforcePublishingGate(
                reportFile.get().asFile,
                exportIndexFile.get().asFile,
                inputsHashFile.get().asFile,
            )
            ExportIndex.fromFile(exportIndexFile.get().asFile)
                .withStoreLocales(storeLocales.getOrElse(emptyMap()), StoreTarget.APP_STORE)
        } else {
            ExportIndex()
        }

        val targetBundleId = bundleId.orNull?.ifBlank { null }
            ?: throw GradleException(
                "Store Studio: Apple App Store bundle identifier must be configured via " +
                    "publishing.appStore.bundleId or --bundle-id",
            )

        val assetLocales = exportIndex.assets.map { it.storeLocale }.distinct().sorted()

        val metaDir = if (metadataDirectory.isPresent) metadataDirectory.get().asFile else null
        val loaded = loadPublishMetadata(
            metadataFile.orNull?.asFile,
            metaDir,
            assetLocales,
            storeLocales.getOrElse(emptyMap()),
        )
        val metadata = if (withImages) {
            loaded
        } else {
            validateTextForPublish(
                loaded,
                StoreTarget.APP_STORE,
                selected,
                severityOverrides.getOrElse(emptyMap()),
                failOnWarning.getOrElse(false),
                logger,
            )
        }
        val locales = if (withImages) assetLocales else metadata?.locales?.keys.orEmpty().sorted()
        val metadataMap = metadata?.toAppStoreMetadataMap()?.let { map ->
            locales.associateWith { locale -> map[locale] ?: AppStoreLocalizedMetadata() }
        } ?: emptyMap()

        val resolvedKeyId = keyId.orNull?.ifBlank { null }
            ?: System.getenv("ASC_KEY_ID")?.ifBlank { null }
        val resolvedIssuerId = issuerId.orNull?.ifBlank { null }
            ?: System.getenv("ASC_ISSUER_ID")?.ifBlank { null }
        val resolvedPrivateKey = privateKey.orNull?.ifBlank { null }
            ?: System.getenv("ASC_PRIVATE_KEY")?.ifBlank { null }

        val isDryRun = dryRun.getOrElse(false) || dryRunFromProperties.getOrElse(false)

        val binaryRequest = if (withBinary) {
            val ipa = ipaFile.orNull?.asFile
                ?: throw GradleException(
                    "Store Studio: No IPA file. Set publishing.appStore.build.ipaFile or " +
                        "pass --ipa. To publish only the App Store listing without a binary, " +
                        "run 'storePublishAppStoreListing'.",
                )
            val whatToTest = if (useReleaseNotesAsWhatToTest.getOrElse(true)) {
                metadata?.toAppStoreMetadataMap().orEmpty()
                    .mapNotNull { (locale, meta) -> meta.whatsNew?.let { locale to it } }.toMap()
            } else {
                emptyMap()
            }
            AppStoreBinaryPublishRequest(
                bundleId = targetBundleId,
                keyId = resolvedKeyId,
                issuerId = resolvedIssuerId,
                privateKey = resolvedPrivateKey,
                ipaFile = ipa,
                bundleShortVersion = blankToNull(bundleShortVersion.orNull),
                bundleVersion = blankToNull(bundleVersion.orNull),
                platform = platform.getOrElse("IOS"),
                whatToTestByLocale = whatToTest,
                betaGroups = betaGroups.getOrElse(emptyList()),
                submitBetaReview = submitBetaReview.getOrElse(false),
                submitForReview = submitForReview.getOrElse(false),
                dryRun = isDryRun,
                pollIntervalMs = pollInterval.map { it.toMillis() }.getOrElse(15_000L),
                processingTimeoutMs = processingTimeout.map { it.toMillis() }.getOrElse(3_600_000L),
            )
        } else {
            null
        }

        val request = AppStorePublishRequest(
            bundleId = targetBundleId,
            keyId = resolvedKeyId,
            issuerId = resolvedIssuerId,
            privateKey = resolvedPrivateKey,
            exportDir = exportDirectory.asFile.orNull ?: temporaryDir,
            assets = exportIndex.assets,
            metadataByLocale = metadataMap,
            dryRun = isDryRun,
            parts = selected,
            binary = binaryRequest,
        )

        runAppStorePublish(logger, request)
    }
}

internal fun resolveEffectivePublishSelection(
    defaultParts: Set<PublishPart>,
    defaultIncludeBinary: Boolean,
    cliParts: String?,
    propParts: String?,
): Pair<Set<PublishPart>, Boolean> {
    val raw = cliParts?.takeIf { it.isNotBlank() } ?: propParts?.takeIf { it.isNotBlank() }
        ?: return defaultParts to defaultIncludeBinary
    val parsed = try {
        PublishPart.parseCliParts(raw)
    } catch (e: IllegalArgumentException) {
        throw GradleException("Store Studio: ${e.message}", e)
    }
    return parsed.parts to (parsed.includeBinary ?: defaultIncludeBinary)
}
