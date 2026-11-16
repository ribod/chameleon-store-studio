import dev.ribod.storestudio.buildlogic.iosTargetsEnabled

plugins {
    alias(libs.plugins.dev.ribod.storestudio.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dev.ribod.storestudio.publish)
}

description =
    "Compose building blocks for store screenshots: canvas, headlines, device mockups and backgrounds."

kotlin {
    if (iosTargetsEnabled) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.storeStudioAnnotations)
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.multiplatform.material3)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(projects.storeStudioCore)
        }
    }
}

// The public API that consumers compile against is tracked in api/; run updateKotlinAbi after an
// intentional change. `check` fails when it drifts.
kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation { }
}
