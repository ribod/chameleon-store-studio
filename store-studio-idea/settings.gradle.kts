pluginManagement {
    // Shares the repository's convention plugins (Spotless/ktlint rules) with this standalone build
    includeBuild("../build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "store-studio-idea"
