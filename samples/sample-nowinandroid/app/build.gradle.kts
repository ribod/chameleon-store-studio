plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.storestudio)
}

android {
    namespace = "com.google.samples.apps.nowinandroid"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.google.samples.apps.nowinandroid"
        minSdk = 24
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

storeStudio {
    variant.set("debug")
    targets.set(
        setOf(
            dev.ribod.storestudio.annotations.StoreTarget.PLAY_STORE,
            dev.ribod.storestudio.annotations.StoreTarget.APP_STORE,
        ),
    )
    defaultDevices.set(
        setOf(
            dev.ribod.storestudio.annotations.DeviceFamily.PHONE,
            dev.ribod.storestudio.annotations.DeviceFamily.TABLET_7,
            dev.ribod.storestudio.annotations.DeviceFamily.TABLET,
        ),
    )
    appStore {
        extraIphoneSizes.set(setOf("IPHONE_6_5"))
    }
    locales {
        register("en-US")
        register("es-MX") {
            playStore.set("es-419")
        }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.feature.foryou)
    implementation(projects.feature.bookmarks)
    implementation(projects.feature.interests)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

    debugImplementation(libs.compose.ui.tooling)
}
