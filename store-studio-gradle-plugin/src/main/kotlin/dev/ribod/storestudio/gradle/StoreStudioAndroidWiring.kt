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

internal fun copyAttributes(source: Configuration, target: Configuration) {
    source.attributes.keySet().forEach { key ->
        @Suppress("UNCHECKED_CAST")
        val attrKey = key as Attribute<Any>
        target.attributes.attribute(attrKey, source.attributes.getAttribute(attrKey)!!)
    }
}

/**
 * Library builds never link Android resources, which Layoutlib and Robolectric need. Enables
 * AGP's `includeAndroidResources` for the library's unit tests (KMP: host tests) and renders
 * with the linked resources and full R classes that AGP produces for them.
 */
internal fun configureLibraryRendering(
    project: Project,
    extension: StoreStudioExtension,
    renderTask: TaskProvider<LayoutlibRenderTask>,
    robolectricTask: TaskProvider<RobolectricFallbackTask>,
    kmp: Boolean,
) {
    renderTask.configure { it.libraryModule.set(true) }
    val enabled = extension.rendering.configureLibraryResources
    val namespace = project.objects.property(String::class.java)

    try {
        AgpLibraryResources.configure(project, kmp, enabled, namespace)
    } catch (e: LinkageError) {
        project.logger.warn(
            "Store Studio: could not configure Android resources for library rendering " +
                "(${e.message}); this module's screenshots are rendered by its app module.",
        )
        return
    }

    project.afterEvaluate {
        if (!enabled.get()) return@afterEvaluate
        project.logger.info(
            "Store Studio: enabled includeAndroidResources for ${project.path} unit tests to " +
                "render library screenshots (opt out with rendering.configureLibraryResources).",
        )

        val variant = extension.variant.getOrElse("debug").ifBlank { "debug" }
        val capVariant = variant.replaceFirstChar { it.uppercase() }
        val testVariant = if (kmp) "androidHostTest" else "${variant}UnitTest"
        val processTaskName = if (kmp) {
            "processAndroidHostTestResources"
        } else {
            "process${capVariant}UnitTestResources"
        }
        // Add the Robolectric harness to the test scope so AGP generates R classes for every
        // Android dependency the fallback renderer loads (e.g. androidx.activity's). Test scope
        // only: nothing is added to the library's published dependencies.
        val testConfigName = if (kmp) {
            "androidHostTestImplementation"
        } else {
            listOf("test${capVariant}Implementation", "testImplementation")
                .firstOrNull { project.configurations.findByName(it) != null }
        }
        if (testConfigName != null &&
            project.configurations.findByName(testConfigName) != null
        ) {
            project.dependencies.add(
                testConfigName,
                "dev.ribod.storestudio:store-studio-testing:" +
                    extension.pluginVersion.getOrElse(StoreStudioBuildConfig.PLUGIN_VERSION),
            )
        }

        val resourceTasks = project.tasks.matching { it.name == processTaskName }
        val intermediates = project.layout.buildDirectory.dir("intermediates")
        val linkedApk = intermediates.map { dir ->
            dir.dir("linked_resources_binary_format/$testVariant").asFileTree
                .matching { it.include("**/*.ap_") }
        }
        // Only the R.jar of the resource-processing task: a stub R jar left over from unit-test
        // builds without includeAndroidResources has IDs that do not match the linked APK.
        val rJar = intermediates.map { dir ->
            dir.dir("compile_and_runtime_r_class_jar/$testVariant").asFileTree
                .matching { it.include("$processTaskName/R.jar") }
        }

        renderTask.configure { task ->
            task.dependsOn(resourceTasks)
            task.resourceApk.from(linkedApk)
            task.rClassPath.from(rJar)
        }
        robolectricTask.configure { task -> task.dependsOn(resourceTasks) }
    }
}

internal fun configureAutoDependencies(
    project: Project,
    extension: StoreStudioExtension,
    isKmp: Boolean,
) {
    if (!project.pluginManager.hasPlugin("com.google.devtools.ksp")) {
        runCatching {
            project.pluginManager.apply("com.google.devtools.ksp")
        }.onFailure { error ->
            project.logger.warn(
                "Store Studio: could not apply the KSP plugin automatically " +
                    "(${error.message}). Apply 'com.google.devtools.ksp' manually.",
            )
        }
    }

    if (!isKmp) {
        // Android: KSP copies the shared 'ksp' configuration into its per-variant processor
        // classpaths before our afterEvaluate runs, so the processor is registered when the
        // configuration is created. The provider defers reading the DSL until resolution.
        val processor = project.provider {
            val enabled = extension.autoDependencies.getOrElse(true) &&
                // KSP 2.x rejects dependencies on the shared 'ksp' configuration in KMP projects
                !project.pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")
            val version =
                extension.pluginVersion.getOrElse(StoreStudioBuildConfig.PLUGIN_VERSION)
            if (enabled) {
                project.dependencies.create("dev.ribod.storestudio:store-studio-ksp:$version")
            } else {
                null
            }
        }
        project.configurations.matching { it.name == "ksp" }.configureEach { config ->
            config.dependencies.addLater(processor)
        }
    }

    project.afterEvaluate {
        if (!extension.autoDependencies.getOrElse(true)) return@afterEvaluate

        val version = extension.pluginVersion.getOrElse(StoreStudioBuildConfig.PLUGIN_VERSION)
        val targetConfig = if (isKmp && project.configurations
                .findByName("commonMainImplementation") != null
        ) {
            "commonMainImplementation"
        } else {
            "implementation"
        }

        if (project.configurations.findByName(targetConfig) != null) {
            project.dependencies.add(
                targetConfig,
                "dev.ribod.storestudio:store-studio-annotations:$version",
            )
            project.dependencies.add(
                targetConfig,
                "dev.ribod.storestudio:store-studio-runtime:$version",
            )
        }

        // KMP: the processor only runs for the Android and commonMain metadata compilations,
        // the two manifests storeMergeManifest consumes (Android is wired above).
        if (!project.pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
            return@afterEvaluate
        }
        val addedConfigs = mutableSetOf<String>()
        project.configurations.all { config ->
            val name = config.name
            val matches = !name.contains("Test", ignoreCase = true) &&
                (name.startsWith("kspAndroid") || name == "kspCommonMainMetadata")
            if (matches && addedConfigs.add(name)) {
                project.dependencies.add(
                    name,
                    "dev.ribod.storestudio:store-studio-ksp:$version",
                )
            }
        }
    }
}

internal fun configureAndroidProject(
    project: Project,
    extension: StoreStudioExtension,
    renderTask: TaskProvider<LayoutlibRenderTask>,
    mergeTask: TaskProvider<MergeStoreManifestTask>,
    robolectricTask: TaskProvider<RobolectricFallbackTask>,
) {
    wireAgpVariant(project, extension, renderTask, robolectricTask, kmp = false)

    // Automatically provide ui-tooling and ui-test-manifest on debug configuration for Layoutlib & Robolectric hosting
    project.dependencies.add(
        "debugImplementation",
        "androidx.compose.ui:ui-tooling:${StoreStudioBuildConfig.COMPOSE_UI_HOST_TOOLING_VERSION}",
    )
    project.dependencies.add(
        "debugImplementation",
        "androidx.compose.ui:ui-test-manifest:${StoreStudioBuildConfig.COMPOSE_UI_HOST_TOOLING_VERSION}",
    )

    project.afterEvaluate {
        val variant = extension.variant.getOrElse("debug").ifBlank { "debug" }
        val capVariant = variant.replaceFirstChar { it.uppercase() }
        val artifactType = Attribute.of("artifactType", String::class.java)

        val runtimeConfig = project.configurations.findByName("${variant}RuntimeClasspath")
        if (runtimeConfig != null) {
            renderTask.configure { task ->
                task.appClasspath.from(
                    runtimeConfig.incoming.artifactView { view ->
                        view.attributes.attribute(artifactType, "android-classes-jar")
                    }.files,
                )
            }
        }

        val depConfigs = listOfNotNull(
            project.configurations.findByName("implementation"),
            project.configurations.findByName("${variant}Implementation"),
            project.configurations.findByName("api"),
            project.configurations.findByName("${variant}Api"),
        )

        val processResourcesTask = project.tasks.matching {
            it.name == "process${capVariant}Resources"
        }

        renderTask.configure { task ->
            // Own classes come from AGP (AgpVariantWiring); dependency modules' classes from
            // appClasspath, whose artifact view carries their compile tasks.
            task.dependsOn(processResourcesTask)
            // Include Compose Multiplatform resources for project and dependent subprojects
            for (config in depConfigs) {
                config.dependencies
                    .withType(org.gradle.api.artifacts.ProjectDependency::class.java)
                    .all { dep ->
                        project.findProject(dep.path)?.let { depProject ->
                            // Declared with their producing tasks so the directories are
                            // complete before rendering reads them
                            task.projectClasspath.from(
                                project.files(
                                    depProject.layout.buildDirectory.dir(
                                        "generated/compose/resourceGenerator/" +
                                            "assembledResources/jvmMain",
                                    ),
                                ).builtBy(
                                    depProject.tasks.matching {
                                        it.name == "assembleJvmMainResources"
                                    },
                                ),
                            )
                            task.projectClasspath.from(
                                project.files(
                                    depProject.layout.buildDirectory.dir(
                                        "processedResources/jvm/main",
                                    ),
                                ).builtBy(
                                    depProject.tasks.matching {
                                        it.name == "jvmProcessResources"
                                    },
                                ),
                            )
                        }
                    }
            }
            task.projectClasspath.from(
                project.layout.buildDirectory.dir(
                    "generated/compose/resourceGenerator/assembledResources/jvmMain",
                ),
            )
            task.projectClasspath.from(
                project.layout.buildDirectory.dir(
                    "processedResources/jvm/main",
                ),
            )
            task.projectClasspath.from(
                project.files(
                    project.layout.buildDirectory.dir(
                        "intermediates/assets/$variant/merge${capVariant}Assets",
                    ),
                ).builtBy(project.tasks.matching { it.name == "merge${capVariant}Assets" }),
            )
            // Tracked inputs point at the exact directories process<Variant>Resources writes;
            // a tree over all of build/intermediates would overlap unrelated task outputs.
            val intermediates = project.layout.buildDirectory.dir("intermediates")
            for (rJarDir in listOf(
                "compile_and_runtime_not_namespaced_r_class_jar",
                "compile_and_runtime_r_class_jar",
            )) {
                task.rClassPath.from(
                    intermediates.map { dir ->
                        dir.dir("$rJarDir/$variant").asFileTree.matching { pattern ->
                            pattern.include("process*Resources/R.jar")
                        }
                    },
                )
            }
            task.resourceApk.from(
                intermediates.map { dir ->
                    dir.dir("linked_resources_binary_format/$variant").asFileTree
                        .matching { pattern -> pattern.include("**/*.ap_") }
                },
            )
        }

        wireRobolectricFallback(project, extension, runtimeConfig, renderTask, robolectricTask)
        robolectricTask.configure { task -> task.dependsOn(processResourcesTask) }

        val localManifest = project.layout.buildDirectory.file(
            "generated/ksp/$variant/resources/" +
                "META-INF/store-studio/store-manifest.json",
        )

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
        val kspTasks = project.tasks.matching { it.name == "ksp${capVariant}Kotlin" }
        // builtBy lets consumers' storeMergeManifest depend on exactly this KSP task
        project.artifacts.add(manifestElements.name, localManifest) { it.builtBy(kspTasks) }

        val incomingManifests = project.configurations
            .maybeCreate("storeStudioIncomingManifests").apply {
                isCanBeConsumed = false
                isCanBeResolved = true
                attributes.attribute(
                    org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE,
                    manifestUsage,
                )
            }

        // Project dependencies publish their manifests through storeStudioManifestElements;
        // the artifacts carry their KSP task, so no cross-project task lookup is needed.
        for (config in depConfigs) {
            config.dependencies
                .withType(org.gradle.api.artifacts.ProjectDependency::class.java)
                .all { dep -> project.dependencies.add(incomingManifests.name, dep) }
        }

        mergeTask.configure { task ->
            task.dependsOn(kspTasks)
            task.manifestFiles.from(localManifest)
            task.manifestFiles.from(
                incomingManifests.incoming.artifactView { view ->
                    view.lenient(true)
                }.files,
            )
        }
    }
}

/** Merged manifest, namespace and boot classpath from AGP's typed Variant API. */
internal fun wireAgpVariant(
    project: Project,
    extension: StoreStudioExtension,
    renderTask: TaskProvider<LayoutlibRenderTask>,
    robolectricTask: TaskProvider<RobolectricFallbackTask>,
    kmp: Boolean,
) {
    try {
        AgpVariantWiring.configure(project, kmp, extension.variant, renderTask, robolectricTask)
        AgpVariantWiring.configureBinary(
            project,
            extension.publishing.googlePlay.release.variant,
        )
    } catch (e: LinkageError) {
        project.logger.warn(
            "Store Studio: could not read the Android variant through AGP's API " +
                "(${e.message}); the Robolectric fallback may lack the merged manifest.",
        )
    }
}

/**
 * Resolves the Robolectric harness against the variant's runtime attributes and hands the
 * fallback runner the same classes and resources the Layoutlib render task uses.
 */
internal fun wireRobolectricFallback(
    project: Project,
    extension: StoreStudioExtension,
    runtimeConfig: Configuration?,
    renderTask: TaskProvider<LayoutlibRenderTask>,
    robolectricTask: TaskProvider<RobolectricFallbackTask>,
) {
    val artifactType = Attribute.of("artifactType", String::class.java)
    val robolectricConfig = project.configurations.maybeCreate("storeStudioRobolectric").apply {
        isCanBeConsumed = false
        isCanBeResolved = true
        if (runtimeConfig != null) copyAttributes(runtimeConfig, this)
    }
    val version = extension.pluginVersion.getOrElse(StoreStudioBuildConfig.PLUGIN_VERSION)
    listOf(
        "dev.ribod.storestudio:store-studio-testing:$version",
        "org.robolectric:robolectric:${extension.rendering.robolectricVersion.get()}",
        "junit:junit:${StoreStudioBuildConfig.JUNIT_VERSION}",
        "androidx.test:core:${StoreStudioBuildConfig.ANDROIDX_TEST_CORE_VERSION}",
    ).forEach { project.dependencies.add(robolectricConfig.name, it) }

    robolectricTask.configure { task ->
        task.robolectricClasspath.from(
            robolectricConfig.incoming.artifactView { view ->
                view.attributes.attribute(artifactType, "android-classes-jar")
                view.lenient(true)
            }.files,
        )
        if (runtimeConfig != null) {
            task.appClasspath.from(
                runtimeConfig.incoming.artifactView { view ->
                    view.attributes.attribute(artifactType, "android-classes-jar")
                }.files,
            )
        }
        task.projectClasspath.from(renderTask.map { it.projectClasspath })
        task.rClassPath.from(renderTask.map { it.rClassPath })
        task.resourceApk.from(renderTask.map { it.resourceApk })
        task.namespace.set(renderTask.flatMap { it.namespace })
    }
}
