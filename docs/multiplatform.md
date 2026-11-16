# Kotlin Multiplatform

Chameleon Store Studio works with Compose Multiplatform. Screenshots are written once in `commonMain` and
rendered on the JVM through the Android target, for both Google Play and the App Store.

## Setup

Use the Android KMP library plugin (`com.android.kotlin.multiplatform.library`) in the shared
module:

```kotlin title="shared/build.gradle.kts"
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("dev.ribod.storestudio")
}

kotlin {
    android {
        namespace = "com.example.shared"
        compileSdk = 36
        minSdk = 24
    }
    iosArm64()
    iosSimulatorArm64()
}
```

The plugin adds the annotations and runtime to `commonMain` and the KSP processor to the Android and
common metadata configurations. Write screenshots in `commonMain`:

```kotlin title="shared/src/commonMain/kotlin/.../StoreScreenshots.kt"
@StoreScreenshot(
    id = "kotlinconf_agenda",
    order = 1,
    name = "Conference Schedule",
    devices = [DeviceFamily.PHONE, DeviceFamily.TABLET_7, DeviceFamily.TABLET],
    targets = [StoreTarget.PLAY_STORE, StoreTarget.APP_STORE],
)
@Composable
fun AgendaStoreScreenshot() {
    StoreScreenshotCanvas {
        Headline(stringResource(Res.string.agenda_headline))
        Subheadline(stringResource(Res.string.agenda_subheadline))
        DeviceMockup { KotlinConfTheme { AgendaScreen() } }
    }
}
```

Compose Multiplatform resources (`Res.string`, fonts, drawables) are rendered in every configured
locale.

## iOS screenshots

App Store screenshots are rendered on the JVM, not on an iOS simulator. With `DeviceFrame.Auto`, App
Store slots get an iPhone or iPad frame automatically. Enable the [status bar](composing.md#status-bar)
to add an iOS status bar, which `StatusBarStyle.Auto` picks for those frames. Since your UI is Compose, the rendered screen is the same one your iOS app
shows. Platform-specific UI (native iOS views embedded with `UIKitView`) can't be rendered; use
sample content in screenshots instead.

## Application module

Apply the plugin to the Android application module too, and configure the listing there. It merges
the screenshots of the shared module, as described in [Multi-Module Projects](multi-module.md).

The [KotlinConf sample](guides/samples.md) is a complete KMP example.
