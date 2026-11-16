plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.storestudio)
}

kotlin {
    android {
        namespace = "org.jetbrains.kotlinconf.shared"
        compileSdk = 36
        minSdk = 26
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.storestudio.annotations)
            api(libs.storestudio.runtime)
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.multiplatform.material3)
            implementation(compose.components.resources)
        }
    }
}
