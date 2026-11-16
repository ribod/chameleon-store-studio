import dev.ribod.storestudio.buildlogic.iosTargetsEnabled

plugins {
    alias(libs.plugins.dev.ribod.storestudio.kmp.library)
    alias(libs.plugins.dev.ribod.storestudio.publish)
}

description = "Annotations that mark Compose functions as Google Play and App Store screenshots."

kotlin {
    if (iosTargetsEnabled) {
        iosArm64()
        iosSimulatorArm64()
        iosX64()
    }
}

// The public API that consumers compile against is tracked in api/; run updateKotlinAbi after an
// intentional change. `check` fails when it drifts.
kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation { }
}

// These modules run on the consumer's buildscript classpath with the Kotlin stdlib that Gradle
// embeds (2.2 on Gradle 9.1-9.3, the floor for AGP 9); newer stdlib APIs would fail at runtime.
kotlin {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    }
}
