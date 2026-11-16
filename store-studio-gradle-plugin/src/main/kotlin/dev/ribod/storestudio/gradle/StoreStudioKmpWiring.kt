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

/** KSP tasks of a KMP module that produce the Android and commonMain manifests. */
private val KMP_MANIFEST_KSP_TASKS: Set<String> = setOf(
    "kspAndroidMain",
    "kspCommonMainKotlinMetadata",
)

internal fun configureKmpProject(
    project: Project,
    extension: StoreStudioExtension,
    renderTask: TaskProvider<LayoutlibRenderTask>,
    mergeTask: TaskProvider<MergeStoreManifestTask>,
    robolectricTask: TaskProvider<RobolectricFallbackTask>,
) {
    renderTask.configure { task ->
        task.kmpModule.set(true)
    }

    val manifestUsage = project.objects.named(
        org.gradle.api.attributes.Usage::class.java,
        "store-studio-manifest",
    )
    val manifestElements = project.configurations
        .maybeCreate("storeStudioManifestElements").apply {
            isCanBeConsumed = true
            isCanBeResolved = false
            attributes.attribute(
                org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE,
                manifestUsage,
            )
        }

    val kmpManifestAndroid = project.layout.buildDirectory.file(
        "generated/ksp/android/androidMain/resources/" +
            "META-INF/store-studio/store-manifest.json",
    )
    val kmpManifestMetadata = project.layout.buildDirectory.file(
        "generated/ksp/metadata/commonMain/resources/" +
            "META-INF/store-studio/store-manifest.json",
    )
    // The only KSP compilations whose manifests are consumed (see configureAutoDependencies)
    val kmpKspTasks = project.tasks.matching { it.name in KMP_MANIFEST_KSP_TASKS }
    project.artifacts.add(manifestElements.name, kmpManifestAndroid) {
        it.builtBy(kmpKspTasks)
    }
    project.artifacts.add(manifestElements.name, kmpManifestMetadata) {
        it.builtBy(kmpKspTasks)
    }

    project.afterEvaluate {
        val debugConfig = project.configurations.findByName("androidDebugImplementation")
            ?: project.configurations.findByName("debugImplementation")
        if (debugConfig != null) {
            project.dependencies.add(
                debugConfig.name,
                "androidx.compose.ui:ui-tooling:${StoreStudioBuildConfig.COMPOSE_UI_HOST_TOOLING_VERSION}",
            )
        }

        val artifactType = Attribute.of("artifactType", String::class.java)
        val androidRuntime = project.configurations.findByName("androidRuntimeClasspath")
            ?: project.configurations.findByName("debugRuntimeClasspath")

        if (androidRuntime != null) {
            renderTask.configure { task ->
                task.appClasspath.from(
                    androidRuntime.incoming.artifactView { view ->
                        view.attributes.attribute(artifactType, "android-classes-jar")
                    }.files,
                )
            }
        }

        // The KMP Android library plugin has no debug-only configuration, so ui-tooling (which
        // hosts the composable in Layoutlib) is resolved separately instead of being added to
        // androidMain, where it would ship with the published library.
        if (debugConfig == null && androidRuntime != null) {
            val toolingConfig = project.configurations.maybeCreate(
                "storeStudioLayoutlibTooling",
            ).apply {
                isCanBeConsumed = false
                isCanBeResolved = true
                copyAttributes(androidRuntime, this)
            }
            project.dependencies.add(
                toolingConfig.name,
                "androidx.compose.ui:ui-tooling:${StoreStudioBuildConfig.COMPOSE_UI_HOST_TOOLING_VERSION}",
            )
            renderTask.configure { task ->
                task.appClasspath.from(
                    toolingConfig.incoming.artifactView { view ->
                        view.attributes.attribute(artifactType, "android-classes-jar")
                        view.lenient(true)
                    }.files,
                )
            }
        }

        val composeResourceTasks = project.tasks.matching {
            it.name == "assembleJvmMainResources" || it.name == "jvmProcessResources"
        }

        renderTask.configure { task ->
            // Own classes come from AGP (AgpVariantWiring)
            task.projectClasspath.from(
                project.files(
                    project.layout.buildDirectory.dir(
                        "intermediates/assets/androidMain/mergeAndroidMainAssets",
                    ),
                ).builtBy(project.tasks.matching { it.name == "mergeAndroidMainAssets" }),
            )
            // The module's own Compose Multiplatform resources (Res.string, ...), assembled in
            // the same classpath layout the app path uses for its dependencies
            task.projectClasspath.from(
                project.layout.buildDirectory.dir(
                    "generated/compose/resourceGenerator/assembledResources/jvmMain",
                ),
            )
            task.projectClasspath.from(
                project.layout.buildDirectory.dir("processedResources/jvm/main"),
            )
            task.dependsOn(composeResourceTasks)
        }

        wireRobolectricFallback(project, extension, androidRuntime, renderTask, robolectricTask)

        mergeTask.configure { task ->
            task.dependsOn(kmpKspTasks)
            task.manifestFiles.from(kmpManifestAndroid)
            task.manifestFiles.from(kmpManifestMetadata)
        }
    }
}

/**
 * Passes processor options to KSP.
 *
 * The extension is accessed reflectively so the plugin keeps working when KSP is loaded by a
 * different classloader scope. Overloads are selected by exact parameter types.
 */
internal fun configureKspArguments(project: Project) {
    val kspExtension = project.extensions.findByName("ksp") as? ExtensionAware ?: return
    val methods = kspExtension.javaClass.methods
    val stringArg = methods.firstOrNull {
        it.name == "arg" &&
            it.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java))
    }
    val providerArg = methods.firstOrNull {
        it.name == "arg" &&
            it.parameterTypes.contentEquals(arrayOf(CommandLineArgumentProvider::class.java))
    }

    stringArg?.invoke(kspExtension, "storeStudio.modulePath", project.path)

    // The absolute root path is only used to relativize source paths, so it is passed as an
    // @Internal argument provider to keep KSP task cache keys machine-independent.
    val rootDir = project.rootDir.absolutePath
    if (providerArg != null) {
        providerArg.invoke(kspExtension, StoreStudioRootDirArgumentProvider(rootDir))
    } else {
        stringArg?.invoke(kspExtension, "storeStudio.projectRootDir", rootDir)
    }
}
