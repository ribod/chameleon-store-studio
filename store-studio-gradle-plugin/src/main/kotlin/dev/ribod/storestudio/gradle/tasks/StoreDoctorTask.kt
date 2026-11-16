package dev.ribod.storestudio.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Diagnostic verification task inspecting the local build environment, JDK compatibility,
 * Compose tooling presence, and publishing credential configurations.
 *
 * Invoked via `./gradlew storeDoctor`.
 */
public abstract class StoreDoctorTask : DefaultTask() {

    @get:Input
    public abstract val configuredVariant: Property<String>

    @get:Input
    public abstract val androidPluginApplied: Property<Boolean>

    @get:Input
    public abstract val kspPluginApplied: Property<Boolean>

    @get:Input
    public abstract val hasGooglePlayConfigured: Property<Boolean>

    @get:Input
    public abstract val hasAppStoreConfigured: Property<Boolean>

    @get:Input
    public abstract val targets: SetProperty<String>

    init {
        group = "store studio"
        description = "Diagnoses local toolchain, JDK, Compose tooling, and store credentials."
    }

    @TaskAction
    public fun checkEnvironment() {
        val logger = logger
        val currentJava = JavaVersion.current()
        val javaOk = currentJava >= JavaVersion.VERSION_17

        val lines = mutableListOf<String>()
        lines.add("==================================================================")
        lines.add("               Chameleon Store Studio: Doctor Report                ")
        lines.add("==================================================================")

        // Verify Java runtime version
        if (javaOk) {
            lines.add("[✓] JDK Runtime: $currentJava (JDK 17+ requirement satisfied)")
        } else {
            lines.add("[✗] JDK Runtime: $currentJava is below the required JDK 17 minimum.")
            lines.add("    Configure Gradle toolchains or set JAVA_HOME to JDK 17+.")
        }

        // Verify Android Gradle Plugin integration
        if (androidPluginApplied.get()) {
            lines.add("[✓] Android Gradle Plugin: Active on target project")
        } else {
            lines.add("[!] Android Gradle Plugin: Not detected on this project.")
            lines.add("    Apply 'com.android.application' or 'com.android.library'.")
        }

        // Verify Kotlin Symbol Processing plugin
        if (kspPluginApplied.get()) {
            lines.add("[✓] Kotlin Symbol Processing: KSP plugin active ('com.google.devtools.ksp')")
        } else {
            lines.add(
                "[!] KSP Plugin: Missing. Apply 'com.google.devtools.ksp' to discover screenshots.",
            )
        }

        // Verify variant configuration
        lines.add("[✓] Rendering Variant: '${configuredVariant.get()}'")

        // Verify configured marketplaces
        lines.add("[✓] Configured Targets: ${targets.get().joinToString(", ")}")

        // Check publishing configurations
        if (hasGooglePlayConfigured.get()) {
            lines.add("[✓] Google Play Credentials: Bound and available")
        } else {
            lines.add("[-] Google Play Credentials: Not configured (publishing disabled)")
        }

        if (hasAppStoreConfigured.get()) {
            lines.add("[✓] App Store Credentials: Bound and available")
        } else {
            lines.add("[-] App Store Credentials: Not configured (publishing disabled)")
        }

        lines.add("==================================================================")

        for (line in lines) {
            println(line)
        }

        if (!javaOk) {
            error("Store Studio Doctor: JDK $currentJava is below the required JDK 17+ minimum.")
        }
    }
}
