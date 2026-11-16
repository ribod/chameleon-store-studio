import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel

plugins {
    alias(libs.plugins.kotlin.jvm.idea)
    alias(libs.plugins.intellij.platform)
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.dev.ribod.storestudio.spotless)
}

group = "dev.ribod.storestudio"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(libs.versions.intellijIdea.get())
        bundledPlugin("org.jetbrains.kotlin")
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation(libs.junit)
}

intellijPlatform {
    buildSearchableOptions = false
    // No .form files or Java @NotNull instrumentation needed for this Kotlin-only plugin
    instrumentCode = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }
    // From the JetBrains Marketplace account; nothing is signed or published without them
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
    pluginVerification {
        ides {
            // A single IDE, as listed by printProductsReleases (e.g. -PstoreStudio.verify.ide=
            // AI-2026.2.1.8). CI runs one job per supported IDE this way, so each runner downloads
            // only one IDE (~2-4 GB) instead of the whole set (~25-30 GB).
            val singleIde = providers.gradleProperty("storeStudio.verify.ide").orNull
            if (singleIde != null) {
                val code = singleIde.substringBefore('-')
                create(IntelliJPlatformType.fromCode(code), singleIde.substringAfter('-'))
                return@ides
            }
            // Lowest supported platform (sinceBuild 242) for IDEA and Android Studio; each can be
            // skipped with -PstoreStudio.verify.<ideaMinimum|androidStudioMinimum>=false when disk
            // space is short (~3 GB per extracted IDE)
            fun minimumEnabled(name: String) =
                providers.gradleProperty("storeStudio.verify.$name").orNull != "false"
            if (minimumEnabled("ideaMinimum")) {
                create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.2.6")
            }
            if (minimumEnabled("androidStudioMinimum")) {
                create(IntelliJPlatformType.AndroidStudio, "2024.2.1.11")
            }
            // Every supported IDE release on one machine, only on request: it downloads and
            // extracts ~25-30 GB. Verify IDEs that are already installed with `-PstoreStudio.verify.localIdes` instead.
            if (providers.gradleProperty("storeStudio.verify.recommended").orNull == "true") {
                recommended()
            }
            // Installed IDEs, verified without downloading anything:
            // -PstoreStudio.verify.localIdes=/Applications/Android Studio.app,...
            providers.gradleProperty("storeStudio.verify.localIdes").orNull
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                ?.forEach { local(it) }
        }
        failureLevel = listOf(
            FailureLevel.INVALID_PLUGIN,
            FailureLevel.NOT_DYNAMIC,
            FailureLevel.COMPATIBILITY_PROBLEMS,
        )
    }
}

// Runs the plugin in an installed IDE with its own clean sandbox (config, plugins, system under
// build/idea-sandbox), e.g. for the docs screenshots, without touching the user's IDE settings:
// ./gradlew -p store-studio-idea runLocalIde -PstoreStudio.runIde.localPath="/Applications/Android Studio.app"
providers.gradleProperty("storeStudio.runIde.localPath").orNull?.let { ideHome ->
    intellijPlatformTesting.runIde.register("runLocalIde") {
        localPath = file(ideHome)
        task {
            jvmArgs(
                // Skip the first-run wizard, data-sharing consent and trust prompt of a fresh sandbox
                "-Ddisable.android.first.run=true",
                "-Djb.consents.confirmation.enabled=false",
                "-Djb.privacy.policy.text=<!--999.999-->",
                "-Didea.trust.all.projects=true",
            )
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            // The oldest supported IDE (sinceBuild 242) provides the Kotlin 1.9 stdlib API; newer
            // stdlib APIs would compile but fail there with NoSuchMethodError.
            apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
        }
    }
}

// Supported IDE releases, one per line (e.g. AI-2026.2.1.8): as listed by `./gradlew -p store-studio-idea printProductsReleases`.
// -PstoreStudio.verify.eap=true lists EAP and release-candidate builds instead.
tasks.printProductsReleases {
    types = listOf(
        IntelliJPlatformType.IntellijIdeaCommunity,
        IntelliJPlatformType.IntellijIdeaUltimate,
        IntelliJPlatformType.AndroidStudio,
    )
    channels = if (providers.gradleProperty("storeStudio.verify.eap").orNull == "true") {
        listOf(ProductRelease.Channel.EAP, ProductRelease.Channel.RC)
    } else {
        listOf(ProductRelease.Channel.RELEASE)
    }
}
