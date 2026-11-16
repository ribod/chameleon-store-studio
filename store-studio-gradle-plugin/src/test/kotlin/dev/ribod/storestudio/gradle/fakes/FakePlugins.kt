package dev.ribod.storestudio.gradle.fakes

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Stand-in for `com.android.application` (registered under that id in test resources) that only
 * creates the configurations and task names Store Studio wires against, so task wiring can be
 * tested without AGP or an Android SDK.
 */
class FakeAndroidAppPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        listOf(
            "implementation",
            "api",
            "debugImplementation",
            "debugApi",
            "releaseImplementation",
            "debugRuntimeClasspath",
        ).forEach { project.configurations.maybeCreate(it) }
        listOf(
            "compileDebugKotlin",
            "compileReleaseKotlin",
            "processDebugResources",
            "processDebugManifest",
        ).forEach { project.tasks.register(it) }
    }
}

/** Stand-in for `com.google.devtools.ksp` with KSP's per-variant configurations and tasks. */
class FakeKspPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        listOf("ksp", "kspDebug", "kspRelease", "kspTest", "kspTestDebug")
            .forEach { project.configurations.maybeCreate(it) }
        listOf(
            "kspDebugKotlin",
            "kspReleaseKotlin",
            "kspDebugUnitTestKotlin",
            "kspDebugAndroidTestKotlin",
        ).forEach { project.tasks.register(it) }
    }
}
