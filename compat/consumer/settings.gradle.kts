pluginManagement {
    repositories { mavenLocal(); google(); mavenCentral(); gradlePluginPortal() }

    // Versions come from -Pcompat.* (the CI matrix); the defaults are the latest combination tested.
    // Store Studio is the one published to Maven Local, so this resolves its published metadata.
    fun v(name: String, default: String) = providers.gradleProperty(name).getOrElse(default)
    plugins {
        id("com.android.application") version v("compat.agp", "9.4.1")
        id("org.jetbrains.kotlin.plugin.compose") version v("compat.kotlin", "2.4.20")
        id("com.google.devtools.ksp") version v("compat.ksp", "2.3.12")
        // Keep in step with `version` in the root gradle.properties
        id("dev.ribod.storestudio") version v("storeStudio.version", "0.1.0")
    }
}
dependencyResolutionManagement {
    repositories { mavenLocal(); google(); mavenCentral() }
}
rootProject.name = "consumer"
include(":app")
