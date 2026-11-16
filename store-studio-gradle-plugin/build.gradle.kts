plugins {
    alias(libs.plugins.dev.ribod.storestudio.gradle.plugin)
    alias(libs.plugins.dev.ribod.storestudio.publish)
}

description =
    "Gradle plugin that renders, exports, validates, diffs and publishes store screenshots from Compose code."

gradlePlugin {
    plugins {
        register("storeStudio") {
            id = "dev.ribod.storestudio"
            implementationClass = "dev.ribod.storestudio.gradle.StoreStudioPlugin"
            displayName = "Chameleon Store Studio Gradle Plugin"
            description =
                "Automated Store-as-Code pipeline transforming Composables into app store assets"
        }
    }
}

// Default versions the plugin injects into consumer builds, sourced from the version catalog so
// they are updated in one place.
val buildConfigValues: Map<String, String> = mapOf(
    "PLUGIN_VERSION" to project.version.toString(),
    "LAYOUTLIB_VERSION" to libs.versions.layoutlib.get(),
    "COMPOSE_PREVIEW_RENDERER_VERSION" to libs.versions.composePreviewRenderer.get(),
    "COMPOSE_UI_HOST_TOOLING_VERSION" to libs.versions.composeUiHostTooling.get(),
    "ROBOLECTRIC_VERSION" to libs.versions.robolectric.get(),
    "JUNIT_VERSION" to libs.versions.junit4.get(),
    "ANDROIDX_TEST_CORE_VERSION" to libs.versions.androidxTestCore.get(),
)

val generateBuildConfig = tasks.register("generateStoreStudioBuildConfig") {
    val values = buildConfigValues
    val outputDir = layout.buildDirectory.dir("generated/source/storeStudioBuildConfig")
    inputs.properties(values)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file(
            "dev/ribod/storestudio/gradle/StoreStudioBuildConfig.kt",
        ).asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("package dev.ribod.storestudio.gradle")
                appendLine()
                appendLine("/** Generated from gradle/libs.versions.toml. Do not edit. */")
                appendLine("internal object StoreStudioBuildConfig {")
                values.forEach { (key, value) ->
                    appendLine("    const val $key: String = \"$value\"")
                }
                appendLine("}")
            },
        )
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generateBuildConfig)
}

dependencies {
    implementation(projects.storeStudioCore)
    implementation(libs.kotlinx.serialization.json)

    compileOnly(libs.agp.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)

    testImplementation(libs.junit)
    testImplementation(gradleTestKit())
}

// Gradle pins org.jetbrains:annotations (strictly 13.0) and kotlin-stdlib on the buildscript
// classpath; the plugin's transitive copies make that pin unsatisfiable, so keep them off it.
configurations.configureEach {
    if (name == "apiElements" || name == "runtimeElements") {
        exclude(group = "org.jetbrains", module = "annotations")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
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
