plugins {
    `kotlin-dsl`
}

group = "dev.ribod.storestudio.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    compileOnly(libs.agp.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.multiplatform.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.spotless.gradle.plugin)
    implementation(libs.vanniktech.maven.publish.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("spotless") {
            id = "dev.ribod.storestudio.spotless"
            implementationClass = "SpotlessConventionPlugin"
        }
        register("kotlinJvm") {
            id = "dev.ribod.storestudio.jvm.library"
            implementationClass = "KotlinJvmConventionPlugin"
        }
        register("kotlinMultiplatform") {
            id = "dev.ribod.storestudio.kmp.library"
            implementationClass = "KotlinMultiplatformConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "dev.ribod.storestudio.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("publish") {
            id = "dev.ribod.storestudio.publish"
            implementationClass = "PublishingConventionPlugin"
        }
        register("gradlePlugin") {
            id = "dev.ribod.storestudio.gradle.plugin"
            implementationClass = "GradlePluginConventionPlugin"
        }
    }
}
