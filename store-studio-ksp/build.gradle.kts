plugins {
    alias(libs.plugins.dev.ribod.storestudio.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dev.ribod.storestudio.publish)
}

description =
    "KSP processor that discovers Chameleon Store Studio screenshots and writes their manifest."

dependencies {
    implementation(projects.storeStudioAnnotations)
    implementation(projects.storeStudioModel)
    implementation(libs.ksp.api)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
