# Quick Start

This guide takes an Android app with Compose from zero to exported, validated store assets. It
assumes the plugin is [installed](installation.md).

## 1. Write a screenshot

A screenshot is a **top-level**, **parameterless** `@Composable` annotated with `@StoreScreenshot`.
Inside a `StoreScreenshotCanvas` you get a headline, a subheadline and a device mockup that frames
your real screen:

```kotlin title="app/src/main/kotlin/com/example/store/StoreScreenshots.kt"
package com.example.store

import androidx.compose.runtime.Composable
import dev.ribod.storestudio.annotations.StoreScreenshot
import dev.ribod.storestudio.runtime.canvas.StoreScreenshotCanvas

@StoreScreenshot(id = "home", order = 1, name = "Home")
@Composable
fun HomeStoreScreenshot() {
    StoreScreenshotCanvas {
        Headline("Plan your week in seconds")
        Subheadline("Smart lists that sort themselves")
        DeviceMockup {
            AppTheme {
                HomeScreen(state = HomeState.sample()) // (1)!
            }
        }
    }
}
```

1. Screenshots can't take parameters, so pass sample state to your screen. This is the same
   requirement Compose previews have.

!!! tip "Keep screenshots in their own file"
    A file like `StoreScreenshots.kt` keeps marketing code apart from your screens. It can live in
    the main source set of your app module; the screenshots never run in your app.

## 2. Render and export

```bash
./gradlew :app:storeExport
```

By default every screenshot is rendered for **both stores** and for **phones and tablets**, which
means Google Play phone and 10-inch tablet, and App Store iPhone 6.9" and iPad 13". The images are
written to `app/build/store/export/`:

```text
app/build/store/export/
├── play_store/en-US/phone/01_home.png        1080 × 1920
├── play_store/en-US/tablet_10/01_home.png    1600 × 2560
├── app_store/en-US/iphone_6_9/01_home.png    1320 × 2868
├── app_store/en-US/ipad_13/01_home.png       2064 × 2752
└── index.json
```

The file name prefix (`01_`) is the screenshot's position in the store carousel, taken from `order`.

## 3. Look at the result

- Open the **Chameleon Store Studio** tool window in Android Studio to browse the gallery and a simulated
  store listing ([IDE plugin](ide/index.md)), or
- generate a self-contained HTML gallery:

```bash
./gradlew :app:storeGallery
# → app/build/store/reports/gallery.html
```

Double-click a screenshot in the IDE gallery to jump to the composable that produced it.

## 4. Validate against store rules

```bash
./gradlew :app:storeValidate
```

With a single screenshot, validation fails, and that's useful: it reports exactly what the stores
would reject.

```text
ERROR PLAY_SCREENSHOT_COUNT_MIN     Google Play requires at least 2 phone screenshots per locale; found 1 for en-US.
ERROR PLAY_FEATURE_GRAPHIC_REQUIRED Google Play requires a 1024x500 feature graphic for locale 'en-US'.
```

Add a second screenshot and a feature graphic:

```kotlin
@StoreScreenshot(id = "stats", order = 2, name = "Statistics")
@Composable
fun StatsStoreScreenshot() {
    StoreScreenshotCanvas {
        Headline("See your progress")
        DeviceMockup { AppTheme { StatsScreen(state = StatsState.sample()) } }
    }
}

@StoreFeatureGraphic(id = "feature_graphic")
@Composable
fun AppFeatureGraphic() {
    StoreFeatureGraphicCanvas {
        Headline("Plan your week in seconds")
    }
}
```

Run `storeValidate` again until it passes. The report is also written to
`app/build/store/validation/report.json` and shown in the IDE's **Validation** tab.

!!! info "Validation runs with `check`"
    In application modules, `storeValidate` is attached to `./gradlew check` by default
    (`validation.attachToCheck`).

## 5. Add languages and choose devices

```kotlin title="app/build.gradle.kts"
import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget

storeStudio {
    targets.set(setOf(StoreTarget.PLAY_STORE, StoreTarget.APP_STORE))
    defaultDevices.set(setOf(DeviceFamily.PHONE, DeviceFamily.TABLET))
    locales {
        register("en-US")
        register("es-MX") {
            playStore.set("es-419") // Google Play's code for Latin American Spanish
        }
    }
}
```

Each locale is rendered with your app's localized resources (Android `strings.xml` or Compose
Multiplatform `Res.string`), so headlines and screens appear in that language.

## What's next

- [Composing Screenshots](composing.md): typography, backgrounds, device frames and status bars.
- [Configuration](configuration.md): every option of the `storeStudio { }` block.
- [Baselines & Visual Diff](baselines-and-diff.md): review screenshot changes in pull requests.
- [Publishing](publishing.md): upload assets and listing text to the store consoles.
