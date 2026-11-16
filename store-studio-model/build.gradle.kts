plugins {
    alias(libs.plugins.dev.ribod.storestudio.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dev.ribod.storestudio.publish)
}

description =
    "Serializable artifact contracts shared by the Chameleon Store Studio processor, Gradle plugin and IDE plugin."

// Serializable artifact contracts (manifests, indexes, reports) shared by the KSP processor,
// the Gradle plugin and core without pulling in networking or reporting dependencies.
dependencies {
    api(projects.storeStudioAnnotations)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}

// These modules run on the consumer's buildscript classpath with the Kotlin stdlib that Gradle
// embeds (2.2 on Gradle 9.1-9.3, the floor for AGP 9); newer stdlib APIs would fail at runtime.
kotlin {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    }
}
