plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.storestudio)
}

android {
    namespace = "org.jetbrains.kotlinconf"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.jetbrains.kotlinconf"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.storestudio.annotations)
    implementation(libs.storestudio.runtime)
}

storeStudio {
    variant.set("debug")
    targets.set(setOf(
        dev.ribod.storestudio.annotations.StoreTarget.PLAY_STORE,
        dev.ribod.storestudio.annotations.StoreTarget.APP_STORE,
    ))
    defaultDevices.set(setOf(
        dev.ribod.storestudio.annotations.DeviceFamily.PHONE,
        dev.ribod.storestudio.annotations.DeviceFamily.TABLET_7,
        dev.ribod.storestudio.annotations.DeviceFamily.TABLET,
    ))
    appStore {
        extraIphoneSizes.set(setOf("IPHONE_6_5"))
    }
    locales {
        register("en-US")
        register("es-MX") {
            playStore.set("es-419")
        }
    }
    export {
        background.set("#19191C")
        sizeTolerancePx.set(2)
    }
    validation {
        failOnWarning.set(false)
    }
    reports {
        appName.set("KotlinConf")
        developerName.set("JetBrains")
        category.set("Events")
    }
}
