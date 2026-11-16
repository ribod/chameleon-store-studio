# Installation

## Gradle plugin

The Gradle plugin and the libraries are published to **Maven Central**. Make sure `mavenCentral()` is
in both repository blocks (projects created with Android Studio already have it):

```kotlin title="settings.gradle.kts"
pluginManagement {
    repositories {
        google()
        mavenCentral()        // Store Studio Gradle plugin
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

Declare the plugins once in the root build so every module shares the same plugin classes:

```kotlin title="build.gradle.kts (root)"
plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
    id("dev.ribod.storestudio") version "0.1.0" apply false
}
```

Apply Chameleon Store Studio and KSP in the module that contains your screenshots, usually the application
module:

```kotlin title="app/build.gradle.kts"
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("dev.ribod.storestudio")
}
```

That's all. When the plugin is applied it automatically adds:

| Added dependency | Configuration |
|---|---|
| `dev.ribod.storestudio:store-studio-annotations` | `implementation` (KMP: `commonMainImplementation`) |
| `dev.ribod.storestudio:store-studio-runtime` | `implementation` (KMP: `commonMainImplementation`) |
| `dev.ribod.storestudio:store-studio-ksp` | `ksp` (KMP: the Android and common metadata KSP configurations) |
| `androidx.compose.ui:ui-tooling` | `debugImplementation`, used to host composables while rendering |

To manage these dependencies yourself, disable the automatic setup:

```kotlin title="app/build.gradle.kts"
storeStudio {
    autoDependencies.set(false)
}

dependencies {
    implementation("dev.ribod.storestudio:store-studio-annotations:0.1.0")
    implementation("dev.ribod.storestudio:store-studio-runtime:0.1.0")
    ksp("dev.ribod.storestudio:store-studio-ksp:0.1.0")
}
```

??? tip "Version catalog"
    ```toml title="gradle/libs.versions.toml"
    [versions]
    storestudio = "0.1.0"

    [plugins]
    storestudio = { id = "dev.ribod.storestudio", version.ref = "storestudio" }
    ```

    ```kotlin title="app/build.gradle.kts"
    plugins {
        alias(libs.plugins.storestudio)
    }
    ```

Check the setup with the diagnostics task:

```bash
./gradlew :app:storeDoctor
```

### Using a local build

To try an unreleased build, publish it to your local Maven repository from the Store Studio repo:

```bash
./gradlew publishToMavenLocal
```

This publishes version 0.1.0 of the Gradle plugin and the libraries to `~/.m2`. In your project's
`settings.gradle.kts`, add `mavenLocal()` as the **first** repository in both blocks:

```kotlin title="settings.gradle.kts"
pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
```

For the IDE plugin, run `./gradlew -p store-studio-idea buildPlugin`. The zip is written to
`store-studio-idea/build/distributions/` and installs via **Settings → Plugins → ⚙ → Install Plugin
from Disk…**.

## IDE plugin

!!! info "JetBrains Marketplace: coming soon"
    Until the plugin is listed on the JetBrains Marketplace, install it from disk.

1. Download `store-studio-idea-<version>.zip` from the
   [GitHub releases](https://github.com/ribod/chameleon-store-studio/releases).
2. In Android Studio or IntelliJ IDEA open **Settings → Plugins**.
3. Click the gear icon, choose **Install Plugin from Disk…**, and select the zip (don't unzip it).
4. Restart the IDE. The **Store Studio** tool window appears on the right side.

See the [IDE plugin](ide/index.md) pages for what each tab does.

## Next steps

Follow the [Quick Start](quickstart.md) to write and export your first screenshot.
