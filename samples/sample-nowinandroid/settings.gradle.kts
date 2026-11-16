pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "sample-nowinandroid"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

includeBuild("../../")

include(":app")
include(":core:designsystem")
include(":feature:foryou")
include(":feature:bookmarks")
include(":feature:interests")
