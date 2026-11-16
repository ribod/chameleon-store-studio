package dev.ribod.storestudio.gradle.internal

import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * The only place that touches AGP's typed API. Kept in its own class so the plugin class itself
 * loads in projects without AGP; this object is only loaded once an Android library plugin applies.
 */
internal object AgpLibraryResources {

    /**
     * Enables `includeAndroidResources` for the library's unit tests (KMP: host tests) when
     * [enabled] is true, and captures the KMP namespace into [namespace]. Runs in `finalizeDsl`,
     * i.e. after the build script, so values set in `storeStudio { }` are honored.
     */
    fun configure(
        project: Project,
        kmp: Boolean,
        enabled: Provider<Boolean>,
        namespace: Property<String>,
    ) {
        if (kmp) {
            project.extensions.getByType(KotlinMultiplatformAndroidComponentsExtension::class.java)
                .finalizeDsl { dsl ->
                    dsl.namespace?.let(namespace::set)
                    if (enabled.get()) dsl.withHostTest { isIncludeAndroidResources = true }
                }
        } else {
            project.extensions.getByType(LibraryAndroidComponentsExtension::class.java)
                .finalizeDsl { dsl ->
                    if (enabled.get()) dsl.testOptions.unitTests.isIncludeAndroidResources = true
                }
        }
    }
}
