pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/ij-platform-plugin/ij-platform-plugins")
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/ij-platform-plugin/ij-platform-plugins")
    }
}

rootProject.name = "compose-store-studio"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

includeBuild("build-logic")

include(":store-studio-annotations")
include(":store-studio-model")
include(":store-studio-core")
include(":store-studio-ksp")
include(":store-studio-runtime")
include(":store-studio-testing")
include(":store-studio-gradle-plugin")
