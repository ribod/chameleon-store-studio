# AI Agent Setup Guide

> **For AI coding assistants (Claude Code, Cursor, Gemini CLI, GitHub Copilot, Codex):** follow this
> guide to add Chameleon Store Studio (`dev.ribod.storestudio`) to an Android or Kotlin Multiplatform
> project. Point your assistant to this page:
> `https://ribod.github.io/chameleon-store-studio/plugin-agent-guides/`

## Goal

1. Apply the `dev.ribod.storestudio` Gradle plugin and KSP to the module that contains the screens.
2. Configure `storeStudio { }` with the app's stores, devices and locales.
3. Write `@StoreScreenshot` composables that reuse the app's real screens with sample state.
4. Run the export and validation and fix what they report.

## Rules for the agent

- Screenshot functions must be **top-level**, **`@Composable`**, **without parameters**, and
  **public or internal**.
- `id` must match `^[a-z0-9_-]+$` and be unique across all modules.
- Don't call ViewModels, DI, network or databases inside screenshots: pass **sample state** to the
  stateless screen composable. If the screen has no stateless variant, split it first
  (`HomeRoute` → `HomeScreen(state)`).
- Put headlines in string resources so every locale is translated.
- Don't add the Store Studio libraries manually: the plugin adds them (`autoDependencies`).
- Don't run publishing tasks unless the user explicitly asks; always use `--publish-dry-run` first.

## Step 1: Plugins

The plugin and libraries are published to Maven Central: `settings.gradle.kts` must include
`mavenCentral()` (and `google()`) in both `pluginManagement.repositories` and
`dependencyResolutionManagement.repositories`.

Root `build.gradle.kts`:

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.3.12" apply false
    id("dev.ribod.storestudio") version "0.1.0" apply false
}
```

Module `build.gradle.kts` (application module, or the module that owns the screens):

```kotlin
plugins {
    // existing Android / Kotlin / Compose plugins
    id("com.google.devtools.ksp")
    id("dev.ribod.storestudio")
}
```

If the project uses a version catalog, add the plugins there and use `alias(...)` instead.

## Step 2: Configuration

```kotlin
import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget

storeStudio {
    targets.set(setOf(StoreTarget.PLAY_STORE, StoreTarget.APP_STORE)) // only PLAY_STORE for Android-only apps
    defaultDevices.set(setOf(DeviceFamily.PHONE, DeviceFamily.TABLET))
    locales {
        register("en-US")
        // one register(...) per language the app supports, e.g.:
        register("es-MX") { playStore.set("es-419") }
    }
    reports {
        appName.set("<App name>")
        developerName.set("<Developer>")
    }
}
```

## Step 3: Screenshots

Create `StoreScreenshots.kt` in the module's main source set (`src/main/kotlin` or
`src/commonMain/kotlin`):

```kotlin
package com.example.store

import androidx.compose.runtime.Composable
import dev.ribod.storestudio.annotations.StoreFeatureGraphic
import dev.ribod.storestudio.annotations.StoreScreenshot
import dev.ribod.storestudio.runtime.canvas.StoreFeatureGraphicCanvas
import dev.ribod.storestudio.runtime.canvas.StoreScreenshotCanvas

@StoreScreenshot(id = "home", order = 1, name = "Home")
@Composable
fun HomeStoreScreenshot() {
    StoreScreenshotCanvas {
        Headline("<Benefit-oriented headline>")
        Subheadline("<Supporting line>")
        DeviceMockup {
            AppTheme { HomeScreen(state = SampleData.homeState, onItemClick = {}) }
        }
    }
}

@StoreFeatureGraphic(id = "feature_graphic")
@Composable
fun AppFeatureGraphic() {
    StoreFeatureGraphicCanvas {
        Headline("<App tagline>")
    }
}
```

Create at least **two** (ideally four or more) screenshots with distinct `order` values, plus one
feature graphic if Google Play is a target.

## Step 4: Verify

```bash
./gradlew :app:storeDoctor        # environment and configuration
./gradlew :app:storeExport        # images in app/build/store/export/
./gradlew :app:storeValidate      # must finish with 0 errors
./gradlew :app:storeGallery       # app/build/store/reports/gallery.html
```

To review screenshot changes later, compare with the base branch rendered on the same machine:
`./gradlew :app:storeDiffReport -PstoreStudio.diff.renderBase=true`. Don't commit a baseline unless the
user asks for one.

If `storeValidate` fails, read each finding's rule id and hint and fix the cause (add screenshots, add
the feature graphic, shorten metadata text). Don't lower severities to make it pass unless the user
asks.

## Reference

- [Annotations](annotations.md)
- [Composing Screenshots](composing.md)
- [Configuration](configuration.md)
- [Validation rules](validation.md)
- [Tasks](tasks.md)
