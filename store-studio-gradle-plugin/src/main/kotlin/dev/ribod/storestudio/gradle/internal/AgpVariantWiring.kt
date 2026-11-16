package dev.ribod.storestudio.gradle.internal

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import dev.ribod.storestudio.gradle.tasks.LayoutlibRenderTask
import dev.ribod.storestudio.gradle.tasks.RobolectricFallbackTask
import dev.ribod.storestudio.gradle.tasks.StorePublishPlayBinaryTask
import dev.ribod.storestudio.gradle.tasks.StorePublishPlayTask
import dev.ribod.storestudio.gradle.tasks.StorePublishTask
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/**
 * Wires the rendered variant's AGP artifacts (merged manifest, namespace, boot classpath) into the
 * render tasks through AGP's typed Variant API instead of guessed `build/intermediates` paths and
 * reflection. Like [AgpLibraryResources], it is only loaded once an Android plugin applies.
 */
internal object AgpVariantWiring {

    /**
     * Must be called while the Android plugin is being applied: AGP rejects `onVariants`
     * callbacks registered after its variants were created. [variantName] is read in the callback,
     * after the build script ran; KMP Android libraries have a single variant and ignore it.
     */
    fun configure(
        project: Project,
        kmp: Boolean,
        variantName: Provider<String>,
        renderTask: TaskProvider<LayoutlibRenderTask>,
        robolectricTask: TaskProvider<RobolectricFallbackTask>,
    ) {
        @Suppress("UNCHECKED_CAST")
        val components = project.extensions.findByName("androidComponents")
            as? AndroidComponentsExtension<*, *, Variant> ?: return
        components.onVariants(components.selector().all()) { variant ->
            val rendered = kmp || variant.name == variantName.getOrElse("debug").ifBlank { "debug" }
            if (!rendered) return@onVariants
            val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
            // The module's compiled classes, carrying their compile tasks as dependencies
            variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
                .use(renderTask)
                .toGet(
                    ScopedArtifact.CLASSES,
                    LayoutlibRenderTask::projectClassJars,
                    LayoutlibRenderTask::projectClassDirs,
                )
            // Only readable once AGP finalized the DSL, i.e. from variant callbacks
            val bootClasspath = components.sdkComponents.bootClasspath
            renderTask.configure { task ->
                task.namespace.set(variant.namespace)
                task.projectClasspath.from(task.projectClassJars, task.projectClassDirs)
            }
            robolectricTask.configure { task ->
                task.mergedManifestFile.set(manifest)
                task.bootClasspath.from(bootClasspath)
            }
        }
    }

    /**
     * Feeds the release variant's bundle and R8 mapping file to `storePublishPlayBinary`; the
     * artifact providers carry their producer tasks, so building the bundle is a dependency.
     */
    fun configureBinary(project: Project, releaseVariant: Provider<String>) {
        @Suppress("UNCHECKED_CAST")
        val components = project.extensions.findByName("androidComponents")
            as? AndroidComponentsExtension<*, *, Variant> ?: return
        components.onVariants(components.selector().all()) { variant ->
            if (variant !is ApplicationVariant || variant.name != releaseVariant.get()) {
                return@onVariants
            }
            val bundle = variant.artifacts.get(SingleArtifact.BUNDLE)
            val mapping = variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
            project.tasks.withType(StorePublishPlayBinaryTask::class.java).configureEach { task ->
                task.agpBundle.set(bundle)
                task.agpMapping.set(mapping)
            }
            project.tasks.withType(StorePublishPlayTask::class.java).matching {
                it.name ==
                    "storePublishPlay"
            }
                .configureEach { task ->
                    task.agpBundle.set(bundle)
                    task.agpMapping.set(mapping)
                }
            project.tasks.withType(StorePublishTask::class.java).matching {
                it.name ==
                    "storePublish"
            }
                .configureEach { task ->
                    task.agpBundle.set(bundle)
                    task.agpMapping.set(mapping)
                }
        }
    }
}
