# Composing Screenshots

The `store-studio-runtime` library (Compose Multiplatform: Android, JVM and iOS) provides the
building blocks for marketing screenshots. Everything is regular Compose, so you can also draw your
own layouts.

## `StoreScreenshotCanvas`

The root of a screenshot. It fills the slot, paints a background, applies padding and typography
suited to the device, and exposes a column scope with marketing helpers.

```kotlin
StoreScreenshotCanvas(
    background = StoreBackgrounds.Ocean,
    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
) {
    Headline("Your money, organized")
    Subheadline("Budgets, goals and insights in one place")
    DeviceMockup(modifier = Modifier.weight(1f)) {
        AppTheme { DashboardScreen(DashboardState.sample()) }
    }
}
```

| Parameter | Default | Description |
|---|---|---|
| `modifier` | `Modifier` | Applied to the canvas. |
| `background` | `StoreBackgrounds.Default` | Any `Brush`. |
| `contentPadding` | per device | Padding around the content; phones and tablets get different defaults. |
| `typography` | per device | `StoreTypography` used by `Headline` and `Subheadline`. |
| `content` | | `StoreScreenshotScope.() -> Unit` |

Inside the scope:

| Member | Description |
|---|---|
| `Headline(text, modifier, style, maxLines, overflow)` | Large title. |
| `Subheadline(text, …)` | Secondary line. |
| `DeviceMockup(…) { }` | Frames your screen in a device (see below). |
| `Modifier.align(Alignment.Horizontal)` | Aligns a child horizontally. |
| `Modifier.weight(Float)` | Distributes remaining vertical space. |
| `storeContext` | The current [`StoreContext`](#storecontext). |

For the Google Play feature graphic use `StoreFeatureGraphicCanvas`, which has the same scope with
padding suited to the 1024 × 500 banner.

!!! tip "Use your localized resources"
    Headlines are plain strings, so read them from your resources (`stringResource(R.string.…)` or
    Compose Multiplatform `stringResource(Res.string.…)`). Each configured locale is rendered with
    the matching translations.

## Device mockups

`DeviceMockup` draws a vector device frame around your content, with a status bar and the safe-area
insets of the device.

```kotlin
DeviceMockup(
    frame = DeviceFrame.Auto,
    statusBar = StatusBarStyle.Auto(time = "9:41"),
    colors = FrameColors.Graphite,
    safeAreaPadding = true,
) {
    AppTheme { HomeScreen(HomeState.sample()) }
}
```

| Parameter | Default | Description |
|---|---|---|
| `frame` | `DeviceFrame.Auto` | Device shape. `Auto` picks a frame matching the slot being rendered. |
| `statusBar` | `null` | Status bar on top of the screen. `null` uses the global configuration, which is off by default. See [Status bar](#status-bar). |
| `colors` | `FrameColors.Dark` | Frame colors: `Dark`, `Graphite`, `Silver`, `Light`, or your own `FrameColors(body, bezel, buttons, cutout)`. |
| `screenWidthDp` | per frame | Logical width of the screen inside the frame. |
| `safeAreaPadding` | `false` | Pads the content by the status bar and gesture-bar insets. |
| `screenBackground` | `null` | Color behind your content. |

**Frames** (`DeviceFrame`): `Auto`, `PhonePunchHole` (Android phone with a centered camera),
`PhoneIsland` (iPhone with Dynamic Island), `TabletSlim` (iPad Pro), `TabletAndroid`, `None` (no
frame), and `Custom(…)` for your own proportions.

**Safe area.** Content inside a mockup can read the insets through `LocalStoreSafeArea` or apply them
with `Modifier.storeSafeAreaPadding(top, bottom, horizontal)`, useful when your screen draws edge to
edge.

## Status bar

The status bar is **off by default**. Marketing screenshots often look better without fake system
chrome, so turn it on when you want it.

Enable it for the whole project in the [`statusBar { }`](configuration.md#statusbar) block:

```kotlin title="app/build.gradle.kts"
import dev.ribod.storestudio.gradle.dsl.StatusBarStyleKind

storeStudio {
    statusBar {
        enabled.set(true)
        style.set(StatusBarStyleKind.AUTO)   // iOS look on iPhone/iPad frames, Android otherwise
        battery.set(15)
        charging.set(true)
    }
}
```

Then every `DeviceMockup` draws it, unless a screenshot passes its own `statusBar`:

| Value of `statusBar` | Result |
|---|---|
| `null` (default) | The global configuration: `LocalStatusBarDefaults`. Hidden when the project has it disabled. |
| `StatusBarStyle.None` | Hidden, even when enabled globally. |
| `StatusBarStyle.Auto(…)`, `Android(…)`, `Ios(…)` | Drawn with these values only. The global settings are not merged in. |

```kotlin
DeviceMockup(
    statusBar = StatusBarStyle.Auto(wifi = 2, battery = 15, charging = true),
) {
    AppTheme { HomeScreen(HomeState.sample()) }
}
```

Every value passed to a style is the complete configuration of that screenshot. `darkIcons` defaults
to `false` (white icons), so a screenshot on a dark background should set it explicitly:
`StatusBarStyle.Auto(darkIcons = true)`.

To change one value of the global style for a single screenshot, copy it from
`LocalStatusBarDefaults`:

```kotlin
val statusBar = (LocalStatusBarDefaults.current as? StatusBarStyle.Auto)
    ?.copy(darkIcons = true, battery = 40)

DeviceMockup(statusBar = statusBar) { /* … */ }
```

`as? StatusBarStyle.Auto` only matches when the global style is `AUTO`. For `ANDROID` or `IOS`, cast
to `StatusBarStyle.Android` or `StatusBarStyle.Ios` instead. When the global status bar is disabled,
the cast gives `null` and the screenshot draws no status bar.

Each style takes these parameters, all of them optional:

| Parameter | Default | Description |
|---|---|---|
| `time` | `"9:41"` (`StatusBarStyle.DEFAULT_TIME`) | Clock text. `null` hides it. |
| `wifi` | `4` | Wi-Fi level, `0` to `4`. `null` hides the icon. |
| `cellular` | `4` | Cellular level, `0` to `4`. `null` hides the icon. |
| `battery` | `100` | Battery percentage, `0` to `100`. `null` hides the icon. |
| `charging` | `false` | Adds a charging bolt. |
| `darkIcons` | `false` | Dark ink instead of white. |

Hidden elements take no space. Icons are drawn with vector primitives, so the output is the same on
every platform. The battery turns red at 20% or less, unless it is charging. Wi-Fi is drawn as a fan of
arcs and cellular as rising bars.

## Backgrounds

`StoreBackgrounds` provides ready-made brushes and helpers:

| Brush | Description |
|---|---|
| `StoreBackgrounds.Default` | Neutral gradient. |
| `StoreBackgrounds.Sunset`, `Ocean`, `DarkCarbon` | Preset gradients. |
| `StoreBackgrounds.linear(start, end, angleDegrees)` | Two- or three-color linear gradient, or `linear(colors, angleDegrees)`. |
| `StoreBackgrounds.radialGlow(…)` | Soft radial "mesh" built from `MeshBlob`s. |

## Typography

`StoreTypography(headline, subheadline, caption)` holds the text styles. `StoreTypography.Phone` and
`StoreTypography.Tablet` are the defaults, and `StoreTypography.forDevice(deviceFamily)` picks one.
Pass your own to `StoreScreenshotCanvas(typography = …)` to use your brand fonts.

## `StoreContext`

`LocalStoreContext.current` tells the composable what is being rendered, so one screenshot can adapt
to each slot:

| Property | Description |
|---|---|
| `target` | `PLAY_STORE` or `APP_STORE`. |
| `deviceFamily` | `PHONE`, `TABLET`, … |
| `deviceSpec` | Slot name, pixel size, dpi and frame type. |
| `locale` | BCP-47 tag being rendered. |
| `screenshotId` | The annotation's `id`. |
| `isStoreRendering` | `true` in the headless renderers, `false` in IDE previews. |

```kotlin
val context = LocalStoreContext.current
val headline = if (context.target == StoreTarget.APP_STORE) "Made for iPhone" else "Made for Android"
```

## Previewing in the IDE

Screenshots are composables, so you can add `@Preview` to see them in Android Studio while you write
them. In previews `StoreContext.Preview` is used and `isStoreRendering` is `false`.

## Text checks

While rendering, the canvas captures the text on screen and detects text that is cut off or
overflowing (common with long translations). Findings appear as `SEMANTICS_TEXT_OVERFLOW` warnings in
[validation](validation.md), and text changes are reported separately in the
[visual diff](baselines-and-diff.md).
