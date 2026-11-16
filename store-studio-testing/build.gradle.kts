plugins {
    alias(libs.plugins.dev.ribod.storestudio.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dev.ribod.storestudio.publish)
}

description =
    "Robolectric harness used by Chameleon Store Studio to render screenshots Layoutlib can't."

android {
    namespace = "dev.ribod.storestudio.testing"
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(platform(libs.compose.bom))

    api(projects.storeStudioAnnotations)
    api(projects.storeStudioCore)
    api(projects.storeStudioRuntime)

    api(libs.robolectric)
    api(libs.junit)
    api(libs.compose.ui.test.junit4)
    api(libs.compose.ui.test.manifest)
    api(libs.androidx.activity.compose)
    api(libs.androidx.test.core)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.serialization.json)
}
