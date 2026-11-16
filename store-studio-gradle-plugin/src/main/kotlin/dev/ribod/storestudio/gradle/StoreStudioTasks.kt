package dev.ribod.storestudio.gradle

import dev.ribod.storestudio.core.image.AppIconResolver
import dev.ribod.storestudio.core.publish.PublishPart
import dev.ribod.storestudio.gradle.dsl.StoreStudioExtension
import dev.ribod.storestudio.gradle.internal.AgpLibraryResources
import dev.ribod.storestudio.gradle.internal.AgpVariantWiring
import dev.ribod.storestudio.gradle.internal.LayoutlibPlatform
import dev.ribod.storestudio.gradle.internal.RendererProcessSlots
import dev.ribod.storestudio.gradle.internal.StoreStudioRootDirArgumentProvider
import dev.ribod.storestudio.gradle.internal.UnpackLayoutlibTransform
import dev.ribod.storestudio.gradle.tasks.ExportStoreDiffHtmlTask
import dev.ribod.storestudio.gradle.tasks.ExportStoreGalleryHtmlTask
import dev.ribod.storestudio.gradle.tasks.ExportStoreListingPreviewHtmlTask
import dev.ribod.storestudio.gradle.tasks.ExportStoreMetadataTask
import dev.ribod.storestudio.gradle.tasks.LayoutlibRenderTask
import dev.ribod.storestudio.gradle.tasks.MergeStoreManifestTask
import dev.ribod.storestudio.gradle.tasks.ParseStoreMetadataTask
import dev.ribod.storestudio.gradle.tasks.RobolectricFallbackTask
import dev.ribod.storestudio.gradle.tasks.StoreCiTask
import dev.ribod.storestudio.gradle.tasks.StoreDiffTask
import dev.ribod.storestudio.gradle.tasks.StoreDoctorTask
import dev.ribod.storestudio.gradle.tasks.StoreDumpTask
import dev.ribod.storestudio.gradle.tasks.StoreExportTask
import dev.ribod.storestudio.gradle.tasks.StorePublishAppStoreBinaryTask
import dev.ribod.storestudio.gradle.tasks.StorePublishAppStoreTask
import dev.ribod.storestudio.gradle.tasks.StorePublishPlayBinaryTask
import dev.ribod.storestudio.gradle.tasks.StorePublishPlayTask
import dev.ribod.storestudio.gradle.tasks.StorePublishTask
import dev.ribod.storestudio.gradle.tasks.StoreRecordBaselineTask
import dev.ribod.storestudio.gradle.tasks.StoreValidateTask
import dev.ribod.storestudio.gradle.tasks.StoreVerifyBaselineTask
import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.CommandLineArgumentProvider
import java.io.File

internal fun registerTasks(
    project: Project,
    extension: StoreStudioExtension,
    layoutlibConfigs: LayoutlibConfigurations,
): Triple<
    TaskProvider<LayoutlibRenderTask>,
    TaskProvider<MergeStoreManifestTask>,
    TaskProvider<RobolectricFallbackTask>,
    > {
    val tasks = project.tasks
    val layout = project.layout

    // Register diagnostic tasks
    val doctorTask = tasks.register("storeDoctor", StoreDoctorTask::class.java) { task ->
        task.configuredVariant.set(extension.variant)
        task.androidPluginApplied.set(
            project.pluginManager.hasPlugin("com.android.application") ||
                project.pluginManager.hasPlugin("com.android.library"),
        )
        task.kspPluginApplied.set(project.pluginManager.hasPlugin("com.google.devtools.ksp"))
        task.hasGooglePlayConfigured.set(extension.publishing.googlePlay.packageName.isPresent)
        task.hasAppStoreConfigured.set(extension.publishing.appStore.bundleId.isPresent)
        task.targets.set(extension.targets.map { list -> list.map { it.name }.toSet() })
    }

    val dumpTask = tasks.register("storeDump", StoreDumpTask::class.java) { task ->
        task.variant.set(extension.variant)
        task.targets.set(extension.targets.map { list -> list.map { it.name }.toSet() })
        task.defaultDevices.set(
            extension.defaultDevices.map { list -> list.map { it.name }.toSet() },
        )
        task.locales.set(extension.locales.map { it.name })
        task.renderingEngine.set(extension.rendering.engine.map { it.name })
        task.baselineComparison.set(extension.baseline.comparison.map { it.name })
    }

    // The unpacked Layoutlib comes from a cached artifact transform shared by all modules;
    // storePrepareLayoutlib remains as the lifecycle task that materializes it.
    val unpackedLayoutlib = UnpackLayoutlibTransform.register(
        project,
        layoutlibConfigs.runtime,
        layoutlibConfigs.resources,
    )
    val prepareLayoutlibTask = tasks.register("storePrepareLayoutlib") { task ->
        task.group = "store studio"
        task.description =
            "Resolves the headless Layoutlib native runtime (cached per version)."
        task.dependsOn(unpackedLayoutlib)
    }

    val mergeManifestTask = tasks.register(
        "storeMergeManifest",
        MergeStoreManifestTask::class.java,
    ) { task ->
        task.mergedManifest.set(layout.buildDirectory.file("store/manifest/merged.json"))
    }

    // Register headless rendering execution task
    val rendererSlots =
        RendererProcessSlots.register(project, extension.rendering.maxParallelProcesses)
    val renderTask = tasks.register("storeRender", LayoutlibRenderTask::class.java) { task ->
        task.processSlots.set(rendererSlots)
        task.usesService(rendererSlots)
        task.dependsOn(prepareLayoutlibTask)
        task.dependsOn(mergeManifestTask)
        task.manifestFile.set(mergeManifestTask.flatMap { it.mergedManifest })
        task.rendererClasspath.from(layoutlibConfigs.renderer)
        task.layoutlibDir.from(unpackedLayoutlib)
        task.layoutlibVersion.set(extension.rendering.layoutlibVersion)
        task.rendererVersion.set(extension.rendering.rendererVersion)
        task.engine.set(extension.rendering.engine)
        task.fallbackEngine.set(extension.rendering.fallbackEngine)
        task.batchSize.set(extension.rendering.batchSize)
        task.maxParallelProcesses.set(extension.rendering.maxParallelProcesses)
        task.executionTimeout.set(extension.rendering.timeout)
        task.stallTimeout.set(extension.rendering.stallTimeout)
        task.rendererJvmArgs.set(extension.rendering.rendererJvmArgs)
        task.statusBar.set(project.provider { extension.statusBar.toSystemProperty() })
        task.targets.set(extension.targets)
        task.defaultDevices.set(extension.defaultDevices)
        task.extraIphoneSizes.set(extension.appStore.extraIphoneSizes)
        task.locales.set(
            project.provider {
                extension.locales.map { it.name }.ifEmpty { listOf("en-US") }
            },
        )
        task.workDir.set(layout.buildDirectory.dir("store/render/work"))
        task.rawImagesDir.set(layout.buildDirectory.dir("store/render/raw"))
        task.semanticsDir.set(layout.buildDirectory.dir("store/semantics"))
        task.renderIndex.set(layout.buildDirectory.file("store/render/index.json"))
        task.failedJobsFile.set(layout.buildDirectory.file("store/render/failed_jobs.json"))
        task.apiLevel.convention("36")
        task.namespace.convention("")
        task.kmpModule.convention(false)
        task.libraryModule.convention(false)
    }

    // Register Robolectric fallback task
    val robolectricTask = tasks.register(
        "storeRenderRobolectric",
        RobolectricFallbackTask::class.java,
    ) { task ->
        task.failedJobsFile.set(renderTask.flatMap { it.failedJobsFile })
        task.renderIndexFile.set(renderTask.flatMap { it.renderIndex })
        // Its own outputs: storeRender's stay untouched, so both are up to date and cacheable
        task.fallbackIndexFile.set(
            layout.buildDirectory.file("store/render/robolectric/index.json"),
        )
        task.fallbackImagesDir.set(layout.buildDirectory.dir("store/render/robolectric/raw"))
        task.fallbackSemanticsDir.set(
            layout.buildDirectory.dir("store/render/robolectric/semantics"),
        )
        task.workDir.set(layout.buildDirectory.dir("store/render/robolectric-work"))
        task.fallbackEngine.set(extension.rendering.fallbackEngine)
        task.processTimeout.set(extension.rendering.fallbackTimeout)
        task.jvmArgs.set(extension.rendering.fallbackJvmArgs)
        task.statusBar.set(project.provider { extension.statusBar.toSystemProperty() })
        task.processSlots.set(rendererSlots)
        task.usesService(rendererSlots)
        task.namespace.convention("")
    }

    renderTask.configure { task ->
        task.finalizedBy(robolectricTask)
    }

    // Register export and validation pipeline tasks
    val exportTask = tasks.register("storeExport", StoreExportTask::class.java) { task ->
        task.renderIndex.set(renderTask.flatMap { it.renderIndex })
        task.rawImagesDir.set(renderTask.flatMap { it.rawImagesDir })
        task.semanticsDir.set(renderTask.flatMap { it.semanticsDir })
        task.fallbackIndexFile.set(robolectricTask.flatMap { it.fallbackIndexFile })
        task.fallbackImagesDir.set(robolectricTask.flatMap { it.fallbackImagesDir })
        task.fallbackSemanticsDir.set(robolectricTask.flatMap { it.fallbackSemanticsDir })
        task.mergedManifest.set(mergeManifestTask.flatMap { it.mergedManifest })
        task.backgroundColor.set(extension.export.background)
        task.sizeTolerancePx.set(extension.export.sizeTolerancePx)
        task.sizeMismatchPolicy.set(extension.export.sizeMismatchPolicy)
        task.jpegFallback.set(extension.export.jpegFallback)
        task.jpegQuality.set(extension.export.jpegQuality)
        task.layout.set(extension.export.layout)
        task.lowercaseFolders.set(extension.export.lowercaseFolders)
        task.exportDirectory.set(layout.buildDirectory.dir("store/export"))
        task.exportIndexFile.set(layout.buildDirectory.file("store/export/index.json"))
    }

    val metaDirProvider = project.provider {
        val dir = extension.metadata.directory.orNull?.asFile
        if (dir != null && dir.exists()) extension.metadata.directory.get() else null
    }

    val playLocalesProvider = project.provider {
        extension.locales.associate { it.name to (it.playStore.orNull ?: it.name) }
    }
    val appStoreLocalesProvider = project.provider {
        extension.locales.associate { it.name to (it.appStore.orNull ?: it.name) }
    }

    // Icons: configured value, else the platform's convention. Roots are the module and the build
    // root, because the iOS icon of a multiplatform project lives outside the shared module.
    val iconRoots = listOf(
        project.projectDir,
        project.isolated.rootProject.projectDirectory.asFile,
    ).distinct()
    val googlePlayIcon = extension.icons.googlePlay.asFile
        .orElse(project.provider { AppIconResolver.findAndroid(iconRoots) })
    val appStoreIcon = extension.icons.appStore.asFile
        .orElse(project.provider { AppIconResolver.findIos(iconRoots) })
    // Previews fall back to the other platform's icon; publishing never does
    val googlePlayPreviewIcon = googlePlayIcon.orElse(appStoreIcon)
    val appStorePreviewIcon = appStoreIcon.orElse(googlePlayIcon)
    val googlePlayIconFile = layout.file(googlePlayIcon)
    val googlePlayPreviewIconFile = layout.file(googlePlayPreviewIcon)
    val appStorePreviewIconFile = layout.file(appStorePreviewIcon)
    fun moduleRelative(icon: Provider<File>) =
        icon.map { it.canonicalFile.relativeTo(project.projectDir.canonicalFile).path }

    val parseMetadataTask = tasks.register(
        "storeParseMetadata",
        ParseStoreMetadataTask::class.java,
    ) { task ->
        task.metadataDirectory.set(metaDirProvider)
        task.defaultLocale.set(extension.metadata.defaultLocale)
        task.configuredLocales.set(
            project.provider {
                extension.locales.map { it.name }.ifEmpty { listOf("en-US") }.toSet()
            },
        )
        task.googlePlayIcon.set(moduleRelative(googlePlayPreviewIcon))
        task.appStoreIcon.set(moduleRelative(appStorePreviewIcon))
        task.metadataFile.set(layout.buildDirectory.file("store/metadata/metadata.json"))
    }

    val exportMetadataTask = tasks.register(
        "storeExportMetadata",
        ExportStoreMetadataTask::class.java,
    ) { task ->
        task.metadataFile.set(parseMetadataTask.flatMap { it.metadataFile })
        task.layout.set(extension.export.layout)
        task.lowercaseFolders.set(extension.export.lowercaseFolders)
        task.targets.set(extension.targets)
        // A sibling of store/export: storeExport owns and wipes that directory, and
        // overlapping outputs would disable caching for both tasks.
        task.outputDirectory.set(layout.buildDirectory.dir("store/export-metadata"))
    }
    // A plain storeExport also produces the texts; the image task stays independent.
    exportTask.configure { it.finalizedBy(exportMetadataTask) }

    val validateTask = tasks.register("storeValidate", StoreValidateTask::class.java) { task ->
        task.dependsOn(exportTask)
        task.dependsOn(parseMetadataTask)
        task.exportDirectory.set(exportTask.flatMap { it.exportDirectory })
        task.exportIndexFile.set(exportTask.flatMap { it.exportIndexFile })
        task.renderIndexFile.set(renderTask.flatMap { it.renderIndex })
        task.semanticsDir.set(renderTask.flatMap { it.semanticsDir })
        task.fallbackIndexFile.set(robolectricTask.flatMap { it.fallbackIndexFile })
        task.fallbackSemanticsDir.set(robolectricTask.flatMap { it.fallbackSemanticsDir })
        task.mergedManifestFile.set(mergeManifestTask.flatMap { it.mergedManifest })
        task.metadataFile.set(parseMetadataTask.flatMap { it.metadataFile })
        task.failOnWarning.set(extension.validation.failOnWarning)
        task.featureGraphicMissing.set(extension.validation.featureGraphicMissing)
        task.severityOverrides.set(extension.validation.severityOverrides)
        task.configuredTargets.set(extension.targets)
        task.configuredLocales.set(
            project.provider {
                extension.locales.map { it.name }.ifEmpty { listOf("en-US") }.toSet()
            },
        )
        task.supportsIpad.set(extension.appStore.supportsIpad)
        // Library modules (set by configureLibraryRendering) are not a store listing
        task.listingModule.set(renderTask.flatMap { it.libraryModule }.map { !it })
        task.reportFile.set(layout.buildDirectory.file("store/validation/report.json"))
        task.inputsHashFile.set(layout.buildDirectory.file("store/validation/inputs.sha256"))
    }

    val verifyBaselineTask = tasks.register(
        "storeVerify",
        StoreVerifyBaselineTask::class.java,
    ) { task ->
        task.dependsOn(exportTask)
        task.exportIndexFile.set(exportTask.flatMap { it.exportIndexFile })
        task.baselineFile.set(extension.baseline.file)
        task.baselineFiles.from(
            project.provider {
                val file = extension.baseline.file.get().asFile
                if (file.exists()) listOf(file) else emptyList()
            },
        )
        task.comparison.set(extension.baseline.comparison)
        task.perceptualThreshold.set(extension.baseline.perceptualThreshold)
        task.failOnDrift.set(extension.baseline.failOnDrift)
        task.required.set(extension.baseline.required)
        task.reportFile.set(layout.buildDirectory.file("store/verification/report.json"))
    }

    val recordBaselineTask =
        tasks.register("storeRecordBaseline", StoreRecordBaselineTask::class.java) { task ->
            task.dependsOn(exportTask)
            task.exportIndexFile.set(exportTask.flatMap { it.exportIndexFile })
            task.layoutlibVersion.set(extension.rendering.layoutlibVersion)
            task.baselineFile.set(extension.baseline.file)
        }

    // -PstoreStudio.publish.track overrides the DSL; --track on the command line wins over both
    val playTrack = project.providers.gradleProperty("storeStudio.publish.track")
        .orElse(extension.publishing.googlePlay.track)

    // Dry-run can also be forced with -PstoreStudio.publish.dryRun=true or -PdryRun=true
    val dryRunFromProperties = project.providers.gradleProperty("storeStudio.publish.dryRun")
        .map { it == "true" }
        .orElse(false)
        .zip(
            project.providers.gradleProperty("dryRun").map { it == "true" }.orElse(false),
        ) { publishDryRun, dryRun -> publishDryRun || dryRun }
    val partsFromProperties = project.providers.gradleProperty("storeStudio.publish.parts")

    // Publishing tasks. Those that upload images depend on the validated export and enforce the
    // publishing gate; text-only ones depend on the parsed metadata alone and run the metadata
    // rules instead, so they never trigger rendering or export.
    val playRelease = extension.publishing.googlePlay.release
    val ascBuild = extension.publishing.appStore.build
    val ascBetaGroupsFromProperties =
        project.providers.gradleProperty("storeStudio.publish.betaGroups")
            .map { raw -> raw.split(',').map { it.trim() }.filter { it.isNotEmpty() } }
            .orElse(ascBuild.betaGroups)
    val ascSubmitBetaReviewFromProperties =
        project.providers.gradleProperty("storeStudio.publish.submitBetaReview")
            .map { it.toBoolean() }
            .orElse(ascBuild.submitBetaReview)
    val ascSubmitForReviewFromProperties =
        project.providers.gradleProperty("storeStudio.publish.submitForReview")
            .map { it.toBoolean() }
            .orElse(ascBuild.submitForReview)

    fun registerPublishPlay(
        name: String,
        parts: Set<PublishPart>,
        includeBinary: Boolean,
        summary: String,
    ) = tasks.register(name, StorePublishPlayTask::class.java) { task ->
        task.description = summary
        task.parts.set(parts)
        task.includeBinary.set(includeBinary)
        task.partsFromProperties.set(partsFromProperties)
        task.dryRunFromProperties.set(dryRunFromProperties)
        if (PublishPart.IMAGES in parts) {
            task.dependsOn(validateTask)
            task.exportDirectory.set(exportTask.flatMap { it.exportDirectory })
            task.reportFile.set(validateTask.flatMap { it.reportFile })
            task.exportIndexFile.set(exportTask.flatMap { it.exportIndexFile })
            task.inputsHashFile.set(validateTask.flatMap { it.inputsHashFile })
            task.appIcon.set(googlePlayIconFile)
        } else {
            task.dependsOn(parseMetadataTask)
            task.severityOverrides.set(extension.validation.severityOverrides)
            task.failOnWarning.set(extension.validation.failOnWarning)
        }
        task.packageName.set(
            extension.publishing.googlePlay.packageName.orElse(
                // The rendered variant's namespace, read from AGP (AgpVariantWiring)
                renderTask.flatMap { it.namespace },
            ),
        )
        task.serviceAccountJson.set(extension.publishing.googlePlay.serviceAccountJson)
        task.changesNotSentForReview.set(
            extension.publishing.googlePlay.changesNotSentForReview,
        )
        task.track.convention(
            project.providers.gradleProperty("storeStudio.publish.track")
                .orElse(playRelease.track)
                .orElse(extension.publishing.googlePlay.track)
                .orElse("internal"),
        )
        task.metadataDirectory.set(metaDirProvider)
        task.storeLocales.set(playLocalesProvider)
        task.metadataFile.set(parseMetadataTask.flatMap { it.metadataFile })

        if (includeBinary) {
            task.status.convention(
                project.providers.gradleProperty("storeStudio.publish.releaseStatus")
                    .orElse(playRelease.status),
            )
            task.userFraction.convention(playRelease.userFraction)
            task.inAppUpdatePriority.convention(playRelease.inAppUpdatePriority)
            task.releaseName.convention(playRelease.releaseName)
            task.existingEditId.convention(playRelease.existingEditId)
            task.retainVersionCodes.convention(playRelease.retainVersionCodes)
            task.uploadTimeout.convention(playRelease.uploadTimeout)
            task.useReleaseNotes.convention(playRelease.useReleaseNotes)
            task.releaseFiles.from(playRelease.releaseFiles)
            task.mappingFile.convention(playRelease.mappingFile)
            task.debugSymbols.convention(playRelease.debugSymbols)
        }
    }

    fun registerPublishAppStore(
        name: String,
        parts: Set<PublishPart>,
        includeBinary: Boolean,
        summary: String,
    ) = tasks.register(name, StorePublishAppStoreTask::class.java) { task ->
        task.description = summary
        task.parts.set(parts)
        task.includeBinary.set(includeBinary)
        task.partsFromProperties.set(partsFromProperties)
        task.dryRunFromProperties.set(dryRunFromProperties)
        if (PublishPart.IMAGES in parts) {
            task.dependsOn(validateTask)
            task.exportDirectory.set(exportTask.flatMap { it.exportDirectory })
            task.reportFile.set(validateTask.flatMap { it.reportFile })
            task.exportIndexFile.set(exportTask.flatMap { it.exportIndexFile })
            task.inputsHashFile.set(validateTask.flatMap { it.inputsHashFile })
        } else {
            task.dependsOn(parseMetadataTask)
            task.severityOverrides.set(extension.validation.severityOverrides)
            task.failOnWarning.set(extension.validation.failOnWarning)
        }
        task.bundleId.set(extension.publishing.appStore.bundleId)
        task.keyId.set(extension.publishing.appStore.keyId)
        task.issuerId.set(extension.publishing.appStore.issuerId)
        task.privateKey.set(extension.publishing.appStore.privateKey)
        task.metadataDirectory.set(metaDirProvider)
        task.storeLocales.set(appStoreLocalesProvider)
        task.metadataFile.set(parseMetadataTask.flatMap { it.metadataFile })

        if (includeBinary) {
            task.ipaFile.convention(ascBuild.ipaFile)
            task.bundleShortVersion.convention(ascBuild.bundleShortVersion)
            task.bundleVersion.convention(ascBuild.bundleVersion)
            task.platform.convention(ascBuild.platform)
            task.betaGroups.convention(ascBetaGroupsFromProperties)
            task.submitBetaReview.convention(ascSubmitBetaReviewFromProperties)
            task.submitForReview.convention(ascSubmitForReviewFromProperties)
            task.useReleaseNotesAsWhatToTest.convention(ascBuild.useReleaseNotesAsWhatToTest)
            task.pollInterval.convention(ascBuild.pollInterval)
            task.processingTimeout.convention(ascBuild.processingTimeout)
        }
    }

    registerPublishPlay(
        "storePublishPlay",
        PublishPart.ALL,
        includeBinary = true,
        "Publishes the app binary (AAB) and complete store listing to Google Play " +
            "in a single atomic edit.",
    )
    registerPublishPlay(
        "storePublishPlayListing",
        PublishPart.ALL,
        includeBinary = false,
        "Uploads the complete store listing (screenshots, feature graphic, icon, texts, " +
            "and release notes) to Google Play Console without uploading a binary.",
    )
    registerPublishPlay(
        "storePublishPlayTexts",
        setOf(PublishPart.LISTING),
        includeBinary = false,
        "Publishes only the listing texts (title and descriptions) to Google Play. " +
            "Does not render or export.",
    )
    registerPublishPlay(
        "storePublishPlayReleaseNotes",
        setOf(PublishPart.RELEASE_NOTES),
        includeBinary = false,
        "Publishes only the release notes to a Google Play track (requires a track). " +
            "Does not render or export.",
    )
    registerPublishPlay(
        "storePublishPlayImages",
        setOf(PublishPart.IMAGES),
        includeBinary = false,
        "Uploads only the screenshots, feature graphic and icon to Google Play.",
    )

    registerPublishAppStore(
        "storePublishAppStore",
        PublishPart.ALL,
        includeBinary = true,
        "Publishes the IPA binary and complete store listing to Apple App Store Connect.",
    )
    registerPublishAppStore(
        "storePublishAppStoreListing",
        PublishPart.ALL,
        includeBinary = false,
        "Uploads the complete store listing (screenshots, texts, and What's New) to Apple " +
            "App Store Connect without uploading a binary.",
    )
    registerPublishAppStore(
        "storePublishAppStoreTexts",
        setOf(PublishPart.LISTING),
        includeBinary = false,
        "Publishes only the listing texts (name, subtitle, description, keywords, " +
            "promotional text, support and marketing URLs) to App Store Connect. " +
            "Does not render or export.",
    )
    registerPublishAppStore(
        "storePublishAppStoreReleaseNotes",
        setOf(PublishPart.RELEASE_NOTES),
        includeBinary = false,
        "Publishes only the release notes (What's New) to App Store Connect. " +
            "Does not render or export.",
    )
    registerPublishAppStore(
        "storePublishAppStoreImages",
        setOf(PublishPart.IMAGES),
        includeBinary = false,
        "Uploads only the screenshots to App Store Connect.",
    )

    // Binary publishing: independent of render/export/validate; only release notes need metadata
    tasks.register("storePublishPlayBinary", StorePublishPlayBinaryTask::class.java) { task ->
        task.dependsOn(parseMetadataTask)
        task.dryRunFromProperties.set(dryRunFromProperties)
        task.packageName.set(
            extension.publishing.googlePlay.packageName.orElse(
                renderTask.flatMap { it.namespace },
            ),
        )
        task.serviceAccountJson.set(extension.publishing.googlePlay.serviceAccountJson)
        task.changesNotSentForReview.set(
            extension.publishing.googlePlay.changesNotSentForReview,
        )
        task.track.convention(
            project.providers.gradleProperty("storeStudio.publish.track")
                .orElse(playRelease.track)
                .orElse(extension.publishing.googlePlay.track)
                .orElse("internal"),
        )
        task.status.convention(
            project.providers.gradleProperty("storeStudio.publish.releaseStatus")
                .orElse(playRelease.status),
        )
        task.userFraction.convention(playRelease.userFraction)
        task.inAppUpdatePriority.convention(playRelease.inAppUpdatePriority)
        task.releaseName.convention(playRelease.releaseName)
        task.existingEditId.convention(playRelease.existingEditId)
        task.retainVersionCodes.convention(playRelease.retainVersionCodes)
        task.uploadTimeout.convention(playRelease.uploadTimeout)
        task.useReleaseNotes.convention(playRelease.useReleaseNotes)
        task.releaseFiles.from(playRelease.releaseFiles)
        task.mappingFile.convention(playRelease.mappingFile)
        task.debugSymbols.convention(playRelease.debugSymbols)
        task.metadataDirectory.set(metaDirProvider)
        task.storeLocales.set(playLocalesProvider)
        task.metadataFile.set(parseMetadataTask.flatMap { it.metadataFile })
    }
    tasks.register(
        "storePublishAppStoreBinary",
        StorePublishAppStoreBinaryTask::class.java,
    ) { task ->
        task.dependsOn(parseMetadataTask)
        task.dryRunFromProperties.set(dryRunFromProperties)
        task.bundleId.set(extension.publishing.appStore.bundleId)
        task.keyId.set(extension.publishing.appStore.keyId)
        task.issuerId.set(extension.publishing.appStore.issuerId)
        task.privateKey.set(extension.publishing.appStore.privateKey)
        task.ipaFile.convention(ascBuild.ipaFile)
        task.bundleShortVersion.convention(ascBuild.bundleShortVersion)
        task.bundleVersion.convention(ascBuild.bundleVersion)
        task.platform.convention(ascBuild.platform)
        task.betaGroups.convention(ascBetaGroupsFromProperties)
        task.submitBetaReview.convention(ascSubmitBetaReviewFromProperties)
        task.submitForReview.convention(ascSubmitForReviewFromProperties)
        task.useReleaseNotesAsWhatToTest.convention(ascBuild.useReleaseNotesAsWhatToTest)
        task.pollInterval.convention(ascBuild.pollInterval)
        task.processingTimeout.convention(ascBuild.processingTimeout)
        task.metadataDirectory.set(metaDirProvider)
        task.storeLocales.set(appStoreLocalesProvider)
        task.metadataFile.set(parseMetadataTask.flatMap { it.metadataFile })
    }
    // Runs the binary uploads that are configured: Play when files or an Android plugin exist,
    // App Store Connect when an IPA is set. Use the specific tasks to force one.
    tasks.register("storePublishBinary", DefaultTask::class.java) { task ->
        task.group = "store studio"
        task.description = "Uploads the app binaries (AAB and/or IPA) that are configured."
        task.dependsOn(
            project.provider {
                buildList {
                    val androidApplied = project.pluginManager.hasPlugin(
                        "com.android.application",
                    )
                    if (androidApplied || !playRelease.releaseFiles.isEmpty) {
                        add("storePublishPlayBinary")
                    }
                    if (ascBuild.ipaFile.isPresent) add("storePublishAppStoreBinary")
                }
            },
        )
    }

    // Register HTML reporting and store preview tasks
    val galleryHtmlTask = tasks.register(
        "exportStoreGalleryHtml",
        ExportStoreGalleryHtmlTask::class.java,
    ) { task ->
        task.dependsOn(exportTask)
        task.mustRunAfter(validateTask)
        task.exportDirectory.set(exportTask.flatMap { it.exportDirectory })
        task.exportIndexFile.set(exportTask.flatMap { it.exportIndexFile })
        task.validationReportFile.set(validateTask.flatMap { it.reportFile })
        task.metadataDirectory.set(metaDirProvider)
        task.metadataFile.set(parseMetadataTask.flatMap { it.metadataFile })
        task.imageMode.set(extension.reports.imageMode)
        task.maxThumbnailDimension.set(extension.reports.maxThumbnailDimension)
        task.appName.set(extension.reports.appName)
        task.developerName.set(extension.reports.developerName)
        task.category.set(extension.reports.category)
        task.appIcon.set(googlePlayPreviewIconFile)
        task.outputFile.set(extension.reports.outputDirectory.file("gallery.html"))
    }

    val listingPreviewHtmlTask = tasks.register(
        "exportStoreListingPreviewHtml",
        ExportStoreListingPreviewHtmlTask::class.java,
    ) { task ->
        task.dependsOn(exportTask)
        task.mustRunAfter(validateTask)
        task.exportDirectory.set(exportTask.flatMap { it.exportDirectory })
        task.exportIndexFile.set(exportTask.flatMap { it.exportIndexFile })
        task.validationReportFile.set(validateTask.flatMap { it.reportFile })
        task.metadataDirectory.set(metaDirProvider)
        task.metadataFile.set(parseMetadataTask.flatMap { it.metadataFile })
        task.imageMode.set(extension.reports.imageMode)
        task.maxThumbnailDimension.set(extension.reports.maxThumbnailDimension)
        task.appName.set(extension.reports.appName)
        task.developerName.set(extension.reports.developerName)
        task.category.set(extension.reports.category)
        task.appIcon.set(googlePlayPreviewIconFile)
        task.appStoreIcon.set(appStorePreviewIconFile)
        task.outputFile.set(extension.reports.outputDirectory.file("listing-preview.html"))
    }

    val diffTask = tasks.register("storeDiff", StoreDiffTask::class.java) { task ->
        task.dependsOn(exportTask)
        task.headExportIndexFile.set(exportTask.flatMap { it.exportIndexFile })
        task.headExportDirectory.set(exportTask.flatMap { it.exportDirectory })
        task.baselineFile.set(extension.baseline.file)
        task.baselineFiles.from(
            project.provider {
                val file = extension.baseline.file.get().asFile
                if (file.exists()) listOf(file) else emptyList()
            },
        )
        task.comparison.set(extension.baseline.comparison)
        task.perceptualThreshold.set(extension.baseline.perceptualThreshold)
        // -PstoreStudio.diff.* reach storeDiff when it runs as a dependency (e.g. of storeCi),
        // where its command-line options can't be passed
        val diffProperty = { name: String ->
            project.providers.gradleProperty("storeStudio.diff.$name")
        }
        task.againstRef.convention(diffProperty("against"))
        task.renderBase.convention(
            diffProperty("renderBase").map {
                it.toBoolean()
            }.orElse(false),
        )
        task.baseExportDir.convention(
            project.layout.dir(
                diffProperty("baseExport").map { project.rootDir.resolve(it) },
            ).orElse(extension.baseline.baseExportDir),
        )
        task.rootProjectDir.set(project.isolated.rootProject.projectDirectory)
        task.projectPath.set(project.path)
        task.workDirectory.set(layout.buildDirectory.dir("store/diff/base-render"))
        task.cacheDirectory.set(
            project.layout.projectDirectory.dir(".gradle/store-studio/cache/images"),
        )
        task.projectDir.set(project.layout.projectDirectory)
        task.diffJsonFile.set(layout.buildDirectory.file("store/diff/diff.json"))
        task.beforeImagesDir.set(layout.buildDirectory.dir("store/diff/base-images"))
    }

    // Recording and checking a baseline in one build: check against the new recording
    verifyBaselineTask.configure { it.mustRunAfter(recordBaselineTask) }
    diffTask.configure { it.mustRunAfter(recordBaselineTask) }

    val diffHtmlTask = tasks.register(
        "exportStoreDiffHtml",
        ExportStoreDiffHtmlTask::class.java,
    ) { task ->
        task.dependsOn(diffTask)
        task.diffJsonFile.set(diffTask.flatMap { it.diffJsonFile })
        task.headExportIndexFile.set(exportTask.flatMap { it.exportIndexFile })
        task.headExportDirectory.set(exportTask.flatMap { it.exportDirectory })
        task.beforeImagesDir.set(diffTask.flatMap { it.beforeImagesDir })
        task.imageMode.set(extension.reports.imageMode)
        task.maxThumbnailDimension.set(extension.reports.maxThumbnailDimension)
        task.thumbnailQuality.set(extension.reports.thumbnailQuality)
        task.includeUnchanged.set(extension.reports.includeUnchangedInDiff)
        task.appName.set(extension.reports.appName)
        task.developerName.set(extension.reports.developerName)
        task.appIcon.set(googlePlayPreviewIconFile)
        task.outputHtmlFile.set(extension.reports.outputDirectory.file("diff.html"))
        task.outputSummaryMarkdownFile.set(
            extension.reports.outputDirectory.file("diff-summary.md"),
        )
    }

    tasks.register("storeDiffReport", DefaultTask::class.java) { task ->
        task.group = "store studio"
        task.description = "Generates the interactive HTML diff report and markdown summary."
        task.dependsOn(diffHtmlTask)
    }

    val ciTask = tasks.register("storeCi", StoreCiTask::class.java) { task ->
        task.dependsOn(validateTask, diffHtmlTask)
        task.diffJsonFile.set(diffTask.flatMap { it.diffJsonFile })
        task.diffSummaryMarkdownFile.set(diffHtmlTask.flatMap { it.outputSummaryMarkdownFile })
        task.diffHtmlFile.set(diffHtmlTask.flatMap { it.outputHtmlFile })
        task.validationReportFile.set(validateTask.flatMap { it.reportFile })
        task.failOnDrift.set(extension.ci.failOnDrift)
        task.failOnValidationErrors.set(extension.ci.failOnValidationErrors)
        task.postPrComment.set(extension.ci.postPrComment)
        task.commentMarker.set(extension.ci.commentMarker)
        task.prNumber.set(extension.ci.prNumber)
        task.githubToken.set(extension.ci.githubToken)
        task.gitlabToken.set(extension.ci.gitlabToken)
    }

    tasks.register("storeCiCheck", DefaultTask::class.java) { task ->
        task.group = "store studio"
        task.description =
            "Lifecycle task running full CI quality gate (compliance + visual diff)."
        task.dependsOn(ciTask)
    }

    tasks.register("storeGallery", DefaultTask::class.java) { task ->
        task.group = "store studio"
        task.description = "Generates the minimalist HTML gallery report."
        task.dependsOn(galleryHtmlTask)
    }

    tasks.register("storeListingPreview", DefaultTask::class.java) { task ->
        task.group = "store studio"
        task.description = "Generates the authentic store listing preview HTML report."
        task.dependsOn(listingPreviewHtmlTask)
    }

    tasks.register("exportStoreAssets", DefaultTask::class.java) { task ->
        task.group = "store studio"
        task.description = "Exports, flattens, and optimizes visual store assets."
        task.dependsOn(exportTask)
    }

    fun registerPublishMultiStore(
        name: String,
        parts: Set<PublishPart>,
        includeBinary: Boolean,
        summary: String,
    ) = tasks.register(name, StorePublishTask::class.java) { task ->
        task.description = summary
        task.parts.set(parts)
        task.includeBinary.set(includeBinary)
        task.partsFromProperties.set(partsFromProperties)
        task.dryRunFromProperties.set(dryRunFromProperties)
        if (PublishPart.IMAGES in parts) {
            task.dependsOn(validateTask)
            task.exportDirectory.set(exportTask.flatMap { it.exportDirectory })
            task.reportFile.set(validateTask.flatMap { it.reportFile })
            task.exportIndexFile.set(exportTask.flatMap { it.exportIndexFile })
            task.inputsHashFile.set(validateTask.flatMap { it.inputsHashFile })
            task.appIcon.set(googlePlayIconFile)
        } else {
            task.dependsOn(parseMetadataTask)
            task.severityOverrides.set(extension.validation.severityOverrides)
            task.failOnWarning.set(extension.validation.failOnWarning)
        }
        task.targets.set(extension.targets)
        task.playPackageName.set(
            extension.publishing.googlePlay.packageName.orElse(
                // The rendered variant's namespace, read from AGP (AgpVariantWiring)
                renderTask.flatMap { it.namespace },
            ),
        )
        task.playServiceAccountJson.set(extension.publishing.googlePlay.serviceAccountJson)
        task.playChangesNotSentForReview.set(
            extension.publishing.googlePlay.changesNotSentForReview,
        )
        task.playTrack.convention(
            project.providers.gradleProperty("storeStudio.publish.track")
                .orElse(playRelease.track)
                .orElse(extension.publishing.googlePlay.track)
                .orElse("internal"),
        )
        task.appStoreBundleId.set(extension.publishing.appStore.bundleId)
        task.appStoreKeyId.set(extension.publishing.appStore.keyId)
        task.appStoreIssuerId.set(extension.publishing.appStore.issuerId)
        task.appStorePrivateKey.set(extension.publishing.appStore.privateKey)
        task.metadataDirectory.set(metaDirProvider)
        task.playLocales.set(playLocalesProvider)
        task.appStoreLocales.set(appStoreLocalesProvider)
        task.metadataFile.set(parseMetadataTask.flatMap { it.metadataFile })

        if (includeBinary) {
            task.playReleaseStatus.convention(
                project.providers.gradleProperty("storeStudio.publish.releaseStatus")
                    .orElse(playRelease.status),
            )
            task.playUserFraction.convention(playRelease.userFraction)
            task.playInAppUpdatePriority.convention(playRelease.inAppUpdatePriority)
            task.playReleaseName.convention(playRelease.releaseName)
            task.playExistingEditId.convention(playRelease.existingEditId)
            task.playRetainVersionCodes.convention(playRelease.retainVersionCodes)
            task.playUploadTimeout.convention(playRelease.uploadTimeout)
            task.playUseReleaseNotes.convention(playRelease.useReleaseNotes)
            task.playReleaseFiles.from(playRelease.releaseFiles)
            task.playMappingFile.convention(playRelease.mappingFile)
            task.playDebugSymbols.convention(playRelease.debugSymbols)

            task.ascIpaFile.convention(ascBuild.ipaFile)
            task.ascBundleShortVersion.convention(ascBuild.bundleShortVersion)
            task.ascBundleVersion.convention(ascBuild.bundleVersion)
            task.ascPlatform.convention(ascBuild.platform)
            task.ascBetaGroups.convention(ascBetaGroupsFromProperties)
            task.ascSubmitBetaReview.convention(ascSubmitBetaReviewFromProperties)
            task.ascSubmitForReview.convention(ascSubmitForReviewFromProperties)
            task.ascUseReleaseNotesAsWhatToTest.convention(
                ascBuild.useReleaseNotesAsWhatToTest,
            )
            task.ascPollInterval.convention(ascBuild.pollInterval)
            task.ascProcessingTimeout.convention(ascBuild.processingTimeout)
        }
    }

    val publishTask = registerPublishMultiStore(
        "storePublish",
        PublishPart.ALL,
        includeBinary = true,
        "Publishes binaries, visual assets, and metadata across configured application " +
            "stores.",
    )
    registerPublishMultiStore(
        "storePublishListing",
        PublishPart.ALL,
        includeBinary = false,
        "Publishes visual assets and metadata (without binaries) across configured " +
            "application stores.",
    )
    registerPublishMultiStore(
        "storePublishTexts",
        setOf(PublishPart.LISTING),
        includeBinary = false,
        "Publishes only the listing texts across configured application stores. " +
            "Does not render or export.",
    )
    registerPublishMultiStore(
        "storePublishImages",
        setOf(PublishPart.IMAGES),
        includeBinary = false,
        "Uploads only the visual assets across configured application stores.",
    )
    registerPublishMultiStore(
        "storePublishReleaseNotes",
        setOf(PublishPart.RELEASE_NOTES),
        includeBinary = false,
        "Publishes only the release notes across configured application stores. " +
            "Does not render or export.",
    )

    // Register main lifecycle umbrella task
    tasks.register("storeStudio", DefaultTask::class.java) { task ->
        task.group = "store studio"
        task.description =
            "Primary lifecycle task running validation and baseline verification."
        task.dependsOn(validateTask, verifyBaselineTask)
        task.dependsOn(
            project.provider {
                val reportsList = mutableListOf<Any>()
                if (extension.reports.htmlGallery.get()) {
                    reportsList.add(galleryHtmlTask)
                }
                if (extension.reports.storeListingPreview.get()) {
                    reportsList.add(listingPreviewHtmlTask)
                }
                reportsList
            },
        )
    }

    // Attach validation to standard lifecycle check task when enabled on application projects
    project.afterEvaluate {
        val isApp = project.plugins.hasPlugin("com.android.application")
        if (isApp && extension.validation.attachToCheck.get()) {
            tasks.matching { it.name == "check" }.configureEach { checkTask ->
                checkTask.dependsOn(validateTask)
            }
        }
    }

    return Triple(renderTask, mergeManifestTask, robolectricTask)
}

internal data class LayoutlibConfigurations(
    val renderer: Configuration,
    val runtime: Configuration,
    val resources: Configuration,
)
