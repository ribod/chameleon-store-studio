package dev.ribod.storestudio.gradle.tasks

import com.skydoves.sandwich.suspendOnError
import com.skydoves.sandwich.suspendOnException
import com.skydoves.sandwich.suspendOnSuccess
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.hash.AssetHasher
import dev.ribod.storestudio.core.metadata.StoreMetadataManifest
import dev.ribod.storestudio.core.metadata.StoreMetadataParser
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
import dev.ribod.storestudio.model.ValidationReport
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
 * Unified lifecycle task orchestrating automated publishing and dry-run inspection
 * across Google Play and Apple App Store marketplaces.
 */
@UntrackedTask(because = "Communicates with remote store developer API endpoints")
public abstract class StorePublishTask : DefaultTask() {

    @get:Input
    @get:Optional
    public abstract val includeBinary: Property<Boolean>

    // Google Play binary properties
    @get:Input
    @get:Optional
    public abstract val playReleaseStatus: Property<String>

    @get:Input
    @get:Optional
    public abstract val playUserFraction: Property<Double>

    @get:Input
    @get:Optional
    public abstract val playInAppUpdatePriority: Property<Int>

    @get:Input
    @get:Optional
    public abstract val playReleaseName: Property<String>

    @get:Input
    @get:Optional
    public abstract val playExistingEditId: Property<String>

    @get:Input
    @get:Optional
    public abstract val playRetainVersionCodes: ListProperty<Long>

    @get:Input
    @get:Optional
    public abstract val playUploadTimeout: Property<Duration>

    @get:Input
    @get:Optional
    public abstract val playUseReleaseNotes: Property<Boolean>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract val playReleaseFiles: ConfigurableFileCollection

    @get:Input
    @get:Optional
    public abstract val playCliReleaseFiles: ListProperty<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract val agpBundle: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract val playMappingFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract val agpMapping: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract val playDebugSymbols: RegularFileProperty

    // App Store binary properties
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract val ascIpaFile: RegularFileProperty

    @get:Input
    @get:Optional
    public abstract val ascBundleShortVersion: Property<String>

    @get:Input
    @get:Optional
    public abstract val ascBundleVersion: Property<String>

    @get:Input
    @get:Optional
    public abstract val ascPlatform: Property<String>

    @get:Input
    @get:Optional
    public abstract val ascBetaGroups: ListProperty<String>

    @get:Input
    @get:Optional
    public abstract val ascSubmitBetaReview: Property<Boolean>

    @get:Input
    @get:Optional
    public abstract val ascSubmitForReview: Property<Boolean>

    @get:Input
    @get:Optional
    public abstract val ascUseReleaseNotesAsWhatToTest: Property<Boolean>

    @get:Input
    @get:Optional
    public abstract val ascPollInterval: Property<Duration>

    @get:Input
    @get:Optional
    public abstract val ascProcessingTimeout: Property<Duration>

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
    public abstract val parts: SetProperty<PublishPart>

    @get:Input
    @get:Optional
    public abstract val cliParts: Property<String>

    @get:Internal
    public abstract val partsFromProperties: Property<String>

    @get:Input
    @get:Optional
    public abstract val severityOverrides: MapProperty<String, Severity>

    @get:Input
    @get:Optional
    public abstract val failOnWarning: Property<Boolean>

    @get:Input
    @get:Optional
    public abstract val targets: SetProperty<StoreTarget>

    @get:Input
    @get:Optional
    public abstract val playPackageName: Property<String>

    @get:Internal
    public abstract val playServiceAccountJson: Property<String>

    @get:Input
    @get:Optional
    public abstract val playChangesNotSentForReview: Property<Boolean>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val appIcon: RegularFileProperty

    @get:Input
    @get:Optional
    public abstract val appStoreBundleId: Property<String>

    @get:Input
    @get:Optional
    public abstract val appStoreKeyId: Property<String>

    @get:Input
    @get:Optional
    public abstract val appStoreIssuerId: Property<String>

    @get:Internal
    public abstract val appStorePrivateKey: Property<String>

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val metadataDirectory: DirectoryProperty

    /** DSL locale to Google Play locale (`playStore` alias, identity when unset). */
    @get:Input
    @get:Optional
    public abstract val playLocales: MapProperty<String, String>

    /** DSL locale to App Store Connect locale (`appStore` alias, identity when unset). */
    @get:Input
    @get:Optional
    public abstract val appStoreLocales: MapProperty<String, String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val metadataFile: RegularFileProperty

    @get:Input
    @get:Optional
    public abstract val dryRun: Property<Boolean>

    /**
     * Dry-run requested through the `storeStudio.publish.dryRun` or `dryRun` Gradle properties,
     * resolved at configuration time so the task action never touches the `Project` model.
     */
    @get:Internal
    public abstract val dryRunFromProperties: Property<Boolean>

    @get:Input
    @get:Optional
    public abstract val targetMarketplace: Property<String>

    @Option(
        option = "publish-dry-run",
        description = "Validates and displays publish plan without committing changes.",
    )
    public fun setPublishDryRunOption(enabled: Boolean) {
        dryRun.set(enabled)
    }

    @Option(
        option = "target",
        description = "Target marketplace to publish (ALL, PLAY_STORE, or APP_STORE).",
    )
    public fun setTargetOption(target: String) {
        targetMarketplace.set(target)
    }

    /**
     * Google Play track whose latest release receives the release notes; unset leaves tracks as-is.
     */
    @get:Input
    @get:Optional
    public abstract val playTrack: Property<String>

    @Option(
        option = "track",
        description = "Google Play track (internal, alpha, beta, production or custom) " +
            "whose latest release receives the release notes.",
    )
    public fun setTrackOption(value: String) {
        playTrack.set(value)
    }

    @Option(option = "package-name", description = "Target package name on Google Play.")
    public fun setPackageNameOption(pkg: String) {
        playPackageName.set(pkg)
    }

    @Option(
        option = "bundle-id",
        description = "Target iOS bundle identifier on App Store Connect.",
    )
    public fun setBundleIdOption(id: String) {
        appStoreBundleId.set(id)
    }

    @Option(
        option = "release-status",
        description = "Google Play release status (completed, inProgress, halted, draft).",
    )
    public fun setReleaseStatusOption(value: String) {
        playReleaseStatus.set(value)
    }

    @Option(option = "release-name", description = "Google Play release name.")
    public fun setReleaseNameOption(value: String) {
        playReleaseName.set(value)
    }

    @Option(
        option = "upload-timeout",
        description = "Google Play upload and socket timeout (e.g. 2m, 120s, PT2M, or seconds).",
    )
    public fun setUploadTimeoutOption(value: String) {
        playUploadTimeout.set(parseUploadTimeout(value))
    }

    @Option(
        option = "release-file",
        description = "Google Play release file (.aab, repeatable).",
    )
    public fun setReleaseFileOption(values: List<String>) {
        playCliReleaseFiles.set(values)
    }

    @Option(option = "ipa", description = "Apple App Store IPA file to upload.")
    public fun setIpaOption(value: String) {
        ascIpaFile.set(File(value))
    }

    @Option(
        option = "parts",
        description = "Comma-separated parts to publish: binary, texts, images, releaseNotes, all.",
    )
    public fun setPartsOption(value: String) {
        cliParts.set(value)
    }

    @Option(
        option = "changes-not-sent-for-review",
        description = "Saves Google Play uploads as draft without submitting for review.",
    )
    public fun setChangesNotSentForReviewOption(enabled: Boolean) {
        playChangesNotSentForReview.set(enabled)
    }

    @Option(
        option = "user-fraction",
        description = "Google Play rollout fraction (0-1) for inProgress.",
    )
    public fun setUserFractionOption(value: String) {
        playUserFraction.set(
            value.toDoubleOrNull()
                ?: throw GradleException("Store Studio: Invalid --user-fraction '$value'."),
        )
    }

    @Option(
        option = "mapping-file",
        description = "Google Play R8/ProGuard mapping file to upload.",
    )
    public fun setMappingFileOption(value: String) {
        playMappingFile.set(File(value))
    }

    @Option(
        option = "in-app-update-priority",
        description = "Google Play in-app update priority, 0-5.",
    )
    public fun setInAppUpdatePriorityOption(value: String) {
        playInAppUpdatePriority.set(
            value.toIntOrNull()
                ?: throw GradleException(
                    "Store Studio: Invalid --in-app-update-priority '$value'.",
                ),
        )
    }

    @Option(
        option = "existing-edit-id",
        description = "Stage Google Play changes into this open edit instead of " +
            "committing a new one.",
    )
    public fun setExistingEditIdOption(value: String) {
        playExistingEditId.set(value)
    }

    @Option(option = "bundle-version", description = "App Store CFBundleVersion (build number).")
    public fun setBundleVersionOption(value: String) {
        ascBundleVersion.set(value)
    }

    @Option(option = "bundle-short-version", description = "App Store CFBundleShortVersionString.")
    public fun setBundleShortVersionOption(value: String) {
        ascBundleShortVersion.set(value)
    }

    @Option(option = "beta-group", description = "App Store TestFlight beta group (repeatable).")
    public fun setBetaGroupOption(values: List<String>) {
        ascBetaGroups.set(values)
    }

    @Option(
        option = "submit-beta-review",
        description = "Submit the App Store build for beta review.",
    )
    public fun setSubmitBetaReviewOption(enabled: Boolean) {
        ascSubmitBetaReview.set(enabled)
    }

    @Option(option = "submit-for-review", description = "Submit the build for App Store review.")
    public fun setSubmitForReviewOption(enabled: Boolean) {
        ascSubmitForReview.set(enabled)
    }

    init {
        group = "store studio"
        description = "Publishes visual assets and metadata across configured application stores."
        parts.convention(PublishPart.ALL)
        includeBinary.convention(false)
    }

    @TaskAction
    public fun publish() {
        val (selectedParts, withBinary) = resolveEffectivePublishSelection(
            defaultParts = parts.get(),
            defaultIncludeBinary = includeBinary.getOrElse(false),
            cliParts = cliParts.orNull,
            propParts = partsFromProperties.orNull,
        )
        val withImages = PublishPart.IMAGES in selectedParts
        val exportIndex = if (withImages) {
            enforcePublishingGate(
                reportFile.get().asFile,
                exportIndexFile.get().asFile,
                inputsHashFile.get().asFile,
            )
            ExportIndex.fromFile(exportIndexFile.get().asFile)
        } else {
            ExportIndex()
        }

        val metaDir = if (metadataDirectory.isPresent) metadataDirectory.get().asFile else null

        val isDryRun = dryRun.getOrElse(false) || dryRunFromProperties.getOrElse(false)

        val selectedTarget = targetMarketplace.orNull?.trim()?.uppercase() ?: "ALL"

        val configuredTargets = targets.getOrElse(emptySet())
        val hasPlayAssets = exportIndex.assets.any { it.target == StoreTarget.PLAY_STORE }
        val hasAppStoreAssets = exportIndex.assets.any { it.target == StoreTarget.APP_STORE }

        val shouldPublishPlay = (selectedTarget == "ALL" || selectedTarget == "PLAY_STORE") &&
            if (withImages) {
                hasPlayAssets || configuredTargets.contains(StoreTarget.PLAY_STORE)
            } else {
                configuredTargets.contains(StoreTarget.PLAY_STORE) ||
                    !playPackageName.orNull.isNullOrBlank()
            }

        val shouldPublishAppStore = (selectedTarget == "ALL" || selectedTarget == "APP_STORE") &&
            if (withImages) {
                hasAppStoreAssets || configuredTargets.contains(StoreTarget.APP_STORE)
            } else {
                configuredTargets.contains(StoreTarget.APP_STORE) ||
                    !appStoreBundleId.orNull.isNullOrBlank()
            }

        if (!shouldPublishPlay && !shouldPublishAppStore) {
            logger.lifecycle(
                "Store Studio: No matching marketplace targets found for publication " +
                    "(target filter: $selectedTarget).",
            )
            return
        }

        if (shouldPublishPlay) {
            publishGooglePlay(
                exportIndex = exportIndex.withStoreLocales(
                    playLocales.getOrElse(emptyMap()),
                    StoreTarget.PLAY_STORE,
                ),
                metaDir = metaDir,
                isDryRun = isDryRun,
                selectedParts = selectedParts,
                withBinary = withBinary,
            )
        }

        if (shouldPublishAppStore) {
            publishAppStore(
                exportIndex = exportIndex.withStoreLocales(
                    appStoreLocales.getOrElse(emptyMap()),
                    StoreTarget.APP_STORE,
                ),
                metaDir = metaDir,
                isDryRun = isDryRun,
                selectedParts = selectedParts,
                withBinary = withBinary,
            )
        }
    }

    private fun publishGooglePlay(
        exportIndex: ExportIndex,
        metaDir: File?,
        isDryRun: Boolean,
        selectedParts: Set<PublishPart>,
        withBinary: Boolean,
    ) {
        val withImages = PublishPart.IMAGES in selectedParts
        val assetLocales = exportIndex.assets.map { it.storeLocale }.distinct().sorted()
        val targetPackage = playPackageName.orNull?.ifBlank { null }
        if (targetPackage == null) {
            logger.lifecycle(
                "Store Studio: Skipping Google Play publication (package name not configured).",
            )
            return
        }

        val creds = playServiceAccountJson.orNull?.ifBlank { null }
            ?: System.getenv("PLAY_SERVICE_ACCOUNT_JSON")?.ifBlank { null }

        val iconFile = if (withImages && appIcon.isPresent && appIcon.get().asFile.exists()) {
            appIcon.get().asFile
        } else {
            null
        }

        val loaded = loadPublishMetadata(
            metadataFile.orNull?.asFile,
            metaDir,
            assetLocales,
            playLocales.getOrElse(emptyMap()),
        )
        val metadata = if (withImages) {
            loaded
        } else {
            validateTextForPublish(
                loaded,
                StoreTarget.PLAY_STORE,
                selectedParts,
                severityOverrides.getOrElse(emptyMap()),
                failOnWarning.getOrElse(false),
                logger,
            )
        }
        val locales = if (withImages) assetLocales else metadata?.locales?.keys.orEmpty().sorted()
        val metadataMap = metadata?.toPlayListingMetadataMap()?.let { map ->
            locales.associateWith { locale -> map[locale] ?: StoreListingMetadata(title = "") }
        } ?: emptyMap()

        val binaryRequest = if (withBinary) {
            val files = when {
                playCliReleaseFiles.getOrElse(emptyList()).isNotEmpty() ->
                    playCliReleaseFiles.get().map { File(it) }

                !playReleaseFiles.isEmpty -> playReleaseFiles.files.toList()

                agpBundle.isPresent -> listOf(agpBundle.get().asFile)

                else -> emptyList()
            }
            if (files.isNotEmpty()) {
                val releaseStatus = parsePlayReleaseStatus(
                    playReleaseStatus.getOrElse("completed"),
                )
                val notes = if (playUseReleaseNotes.getOrElse(true)) {
                    metadata?.toPlayReleaseNotesMap().orEmpty()
                } else {
                    emptyMap()
                }
                PlayBinaryPublishRequest(
                    packageName = targetPackage,
                    serviceAccountJson = creds,
                    releaseFiles = files,
                    track = requireValidPlayTrack(playTrack.orNull) ?: "internal",
                    status = releaseStatus,
                    userFraction = playUserFraction.orNull,
                    inAppUpdatePriority = playInAppUpdatePriority.orNull,
                    releaseName = blankToNull(playReleaseName.orNull),
                    releaseNotesByLocale = notes,
                    mappingFile = (playMappingFile.orNull ?: agpMapping.orNull)?.asFile,
                    debugSymbolsFile = playDebugSymbols.orNull?.asFile,
                    changesNotSentForReview = playChangesNotSentForReview.getOrElse(false),
                    existingEditId = blankToNull(playExistingEditId.orNull),
                    retainVersionCodes = playRetainVersionCodes.getOrElse(emptyList()),
                    dryRun = isDryRun,
                    uploadTimeoutMs = playUploadTimeout.map { it.toMillis() }
                        .getOrElse(PlayBinaryPublishRequest.DEFAULT_UPLOAD_TIMEOUT_MS),
                )
            } else {
                null
            }
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
            changesNotSentForReview = playChangesNotSentForReview.getOrElse(false),
            track = requireValidPlayTrack(playTrack.orNull),
            releaseNotesByLocale = metadata?.toPlayReleaseNotesMap().orEmpty(),
            parts = selectedParts,
            binary = binaryRequest,
        )

        runPlayPublish(logger, request)
    }

    private fun publishAppStore(
        exportIndex: ExportIndex,
        metaDir: File?,
        isDryRun: Boolean,
        selectedParts: Set<PublishPart>,
        withBinary: Boolean,
    ) {
        val withImages = PublishPart.IMAGES in selectedParts
        val assetLocales = exportIndex.assets.map { it.storeLocale }.distinct().sorted()
        val targetBundleId = appStoreBundleId.orNull?.ifBlank { null }
        if (targetBundleId == null) {
            logger.lifecycle(
                "Store Studio: Skipping App Store publication (bundle ID not configured).",
            )
            return
        }

        val resolvedKeyId = appStoreKeyId.orNull?.ifBlank { null }
            ?: System.getenv("ASC_KEY_ID")?.ifBlank { null }
        val resolvedIssuerId = appStoreIssuerId.orNull?.ifBlank { null }
            ?: System.getenv("ASC_ISSUER_ID")?.ifBlank { null }
        val resolvedPrivateKey = appStorePrivateKey.orNull?.ifBlank { null }
            ?: System.getenv("ASC_PRIVATE_KEY")?.ifBlank { null }

        val loaded = loadPublishMetadata(
            metadataFile.orNull?.asFile,
            metaDir,
            assetLocales,
            appStoreLocales.getOrElse(emptyMap()),
        )
        val metadata = if (withImages) {
            loaded
        } else {
            validateTextForPublish(
                loaded,
                StoreTarget.APP_STORE,
                selectedParts,
                severityOverrides.getOrElse(emptyMap()),
                failOnWarning.getOrElse(false),
                logger,
            )
        }
        val locales = if (withImages) assetLocales else metadata?.locales?.keys.orEmpty().sorted()
        val metadataMap = metadata?.toAppStoreMetadataMap()?.let { map ->
            locales.associateWith { locale -> map[locale] ?: AppStoreLocalizedMetadata() }
        } ?: emptyMap()

        val binaryRequest = if (withBinary) {
            val ipa = ascIpaFile.orNull?.asFile
            if (ipa != null) {
                val whatToTest = if (ascUseReleaseNotesAsWhatToTest.getOrElse(true)) {
                    metadata?.toAppStoreMetadataMap().orEmpty()
                        .mapNotNull { (locale, meta) -> meta.whatsNew?.let { locale to it } }
                        .toMap()
                } else {
                    emptyMap()
                }
                AppStoreBinaryPublishRequest(
                    bundleId = targetBundleId,
                    keyId = resolvedKeyId,
                    issuerId = resolvedIssuerId,
                    privateKey = resolvedPrivateKey,
                    ipaFile = ipa,
                    bundleShortVersion = blankToNull(ascBundleShortVersion.orNull),
                    bundleVersion = blankToNull(ascBundleVersion.orNull),
                    platform = ascPlatform.getOrElse("IOS"),
                    whatToTestByLocale = whatToTest,
                    betaGroups = ascBetaGroups.getOrElse(emptyList()),
                    submitBetaReview = ascSubmitBetaReview.getOrElse(false),
                    submitForReview = ascSubmitForReview.getOrElse(false),
                    dryRun = isDryRun,
                    pollIntervalMs = ascPollInterval.map { it.toMillis() }.getOrElse(15_000L),
                    processingTimeoutMs = ascProcessingTimeout.map {
                        it.toMillis()
                    }.getOrElse(3_600_000L),
                )
            } else {
                null
            }
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
            parts = selectedParts,
            binary = binaryRequest,
        )

        runAppStorePublish(logger, request)
    }
}
