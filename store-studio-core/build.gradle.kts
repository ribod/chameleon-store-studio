plugins {
    alias(libs.plugins.dev.ribod.storestudio.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dev.ribod.storestudio.publish)
}

description =
    "Export, validation, diff, reporting and store publishing engine of Chameleon Store Studio."

dependencies {
    api(projects.storeStudioAnnotations)
    api(projects.storeStudioModel)
    implementation(libs.kotlinx.serialization.json)
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    api(libs.sandwich.ktor)
    implementation(libs.dd.plist)

    testImplementation(libs.junit)
    testImplementation(libs.sandwich.test)
}

// These modules run on the consumer's buildscript classpath with the Kotlin stdlib that Gradle
// embeds (2.2 on Gradle 9.1-9.3, the floor for AGP 9); newer stdlib APIs would fail at runtime.
kotlin {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    }
}
