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

/**
 * Core Gradle plugin for Chameleon Store Studio (ID: `dev.ribod.storestudio`).
 *
 * Automates the discovery, compilation, headless rendering, validation, and publication
 * of store-compliant screenshots and promotional assets from Compose and Compose Multiplatform.
 */
public class StoreStudioPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Enforce JDK 17 minimum requirement for Layoutlib and modern Compose toolchains
        val currentJava = JavaVersion.current()
        if (currentJava < JavaVersion.VERSION_17) {
            error(
                "Store Studio: Layoutlib and Compose rendering require JDK 17+ " +
                    "(current: $currentJava). Configure Gradle toolchains or JAVA_HOME.",
            )
        }

        val extension = project.extensions.create("storeStudio", StoreStudioExtension::class.java)
        extension.reports.outputDirectory.convention(
            project.layout.buildDirectory.dir("store/reports"),
        )
        extension.reports.appName.convention(project.provider { project.name })
        extension.ci.commentMarker.convention(
            "<!-- STORE_STUDIO_DIFF_COMMENT ${project.isolated.rootProject.name}${project.path} -->",
        )

        // Set up decoupled Layoutlib configurations and artifacts
        val layoutlibConfigs = configureLayoutlibConfigurations(project, extension)

        // Register tasks lazily
        val (renderTask, mergeTask, robolectricTask) = registerTasks(
            project,
            extension,
            layoutlibConfigs,
        )

        // Configure reactive hooks for Android, Kotlin Multiplatform, and KSP
        configureReactiveHooks(project, extension, renderTask, mergeTask, robolectricTask)
    }

    private fun configureLayoutlibConfigurations(
        project: Project,
        extension: StoreStudioExtension,
    ): LayoutlibConfigurations {
        val renderer = project.configurations.maybeCreate("storeStudioLayoutlibRenderer").apply {
            isCanBeConsumed = false
            isCanBeResolved = true
        }
        // Versions are resolved lazily so values set in the `storeStudio { }` DSL take effect
        project.dependencies.addProvider(
            renderer.name,
            extension.rendering.rendererVersion.map {
                "com.android.tools.compose:compose-preview-renderer:$it"
            },
        )
        project.dependencies.addProvider(
            renderer.name,
            extension.rendering.layoutlibVersion.map {
                "com.android.tools.layoutlib:layoutlib:$it"
            },
        )

        val runtime = project.configurations.maybeCreate("storeStudioLayoutlibRuntime").apply {
            isCanBeConsumed = false
            isCanBeResolved = true
        }
        project.dependencies.addProvider(
            runtime.name,
            extension.rendering.layoutlibVersion.map {
                "com.android.tools.layoutlib:layoutlib-runtime:$it:${LayoutlibPlatform.osClassifier()}"
            },
        )

        val resources = project.configurations.maybeCreate("storeStudioLayoutlibResources").apply {
            isCanBeConsumed = false
            isCanBeResolved = true
        }
        project.dependencies.addProvider(
            resources.name,
            extension.rendering.layoutlibVersion.map {
                "com.android.tools.layoutlib:layoutlib-resources:$it"
            },
        )

        return LayoutlibConfigurations(renderer, runtime, resources)
    }

    private fun configureReactiveHooks(
        project: Project,
        extension: StoreStudioExtension,
        renderTask: TaskProvider<LayoutlibRenderTask>,
        mergeTask: TaskProvider<MergeStoreManifestTask>,
        robolectricTask: TaskProvider<RobolectricFallbackTask>,
    ) {
        // Hook into Android application and library plugins
        project.pluginManager.withPlugin("com.android.application") {
            configureAutoDependencies(project, extension, isKmp = false)
            configureAndroidProject(project, extension, renderTask, mergeTask, robolectricTask)
        }
        project.pluginManager.withPlugin("com.android.library") {
            configureAutoDependencies(project, extension, isKmp = false)
            configureAndroidProject(project, extension, renderTask, mergeTask, robolectricTask)
            configureLibraryRendering(project, extension, renderTask, robolectricTask, kmp = false)
        }
        project.pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
            wireAgpVariant(project, extension, renderTask, robolectricTask, kmp = true)
            configureLibraryRendering(project, extension, renderTask, robolectricTask, kmp = true)
        }

        // Hook into Kotlin Multiplatform
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            configureAutoDependencies(project, extension, isKmp = true)
            configureKmpProject(project, extension, renderTask, mergeTask, robolectricTask)
        }

        // Configure KSP symbol processor arguments when KSP is active
        project.pluginManager.withPlugin("com.google.devtools.ksp") {
            configureKspArguments(project)
        }
    }
}
