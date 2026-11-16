# Annotations

Screenshots are discovered at compile time by the KSP processor from two annotations in
`dev.ribod.storestudio.annotations`.

## `@StoreScreenshot`

Marks a composable as a store screenshot.

```kotlin
@StoreScreenshot(
    id = "onboarding_welcome",
    order = 1,
    name = "Welcome",
    devices = [DeviceFamily.PHONE, DeviceFamily.TABLET],
    targets = [StoreTarget.PLAY_STORE, StoreTarget.APP_STORE],
    locales = ["en-US", "es-MX"],
)
@Composable
fun WelcomeStoreScreenshot() { /* ... */ }
```

| Parameter | Default | Description |
|---|---|---|
| `id` | *required* | Stable identifier. Used in file names, baselines and diffs, so don't rename it casually. Lowercase letters, digits, `_` and `-` only (`^[a-z0-9_-]+$`). |
| `order` | `0` | Position in the store carousel (1 = first). Exported files are prefixed with their position (`01_`, `02_` …). |
| `name` | `""` | Human-readable title shown in reports and in the IDE. Falls back to `id`. |
| `devices` | `[]` | Device families to render. Empty means the configured [`defaultDevices`](configuration.md#stores-devices-and-locales). |
| `targets` | `[]` | Stores to render for. Empty means all configured [`targets`](configuration.md#stores-devices-and-locales). |
| `locales` | `[]` | BCP-47 tags to render. Empty means all configured locales. |

## `@StoreFeatureGraphic`

Marks a composable as the Google Play **feature graphic**, the 1024 × 500 banner at the top of
the listing. Google Play requires one per locale.

```kotlin
@StoreFeatureGraphic(id = "feature_graphic", name = "Launch banner")
@Composable
fun AppFeatureGraphic() {
    StoreFeatureGraphicCanvas {
        Headline("Plan your week in seconds")
    }
}
```

| Parameter | Default | Description |
|---|---|---|
| `id` | *required* | Stable identifier, same rules as above. |
| `name` | `""` | Human-readable title. |
| `locales` | `[]` | BCP-47 tags to render. Empty means all configured locales. |

Feature graphics are always rendered for Google Play only.

## Rules enforced at compile time

The KSP processor fails the build with a clear message, pointing at the function, when an annotated
function:

1. is **not top-level** (it is declared inside a class or object);
2. is **not `@Composable`**;
3. **takes parameters**, not even ones with default values (use sample data inside the function instead);
4. is **`private` or `protected`** (it must be callable by the renderer);
5. has an **invalid id** (outside `^[a-z0-9_-]+$`);
6. has a **duplicate id** within the module.

The processor writes a `store-manifest.json` per module, which `storeMergeManifest` combines with the
manifests of the modules it depends on. See [Multi-Module Projects](multi-module.md).

## Enums

| `StoreTarget` | Store |
|---|---|
| `PLAY_STORE` | Google Play |
| `APP_STORE` | Apple App Store |

| `DeviceFamily` | Rendered as |
|---|---|
| `PHONE` | Play phone; App Store iPhone 6.9" (and 6.5" if enabled) |
| `TABLET_7` | Play 7-inch tablet; App Store iPad 13" |
| `TABLET` | Play 10-inch tablet; App Store iPad 13" |
| `WATCH` | Play Wear OS (square) |
| `DESKTOP`, `TV`, `AUTO`, `XR` | Reserved; not rendered yet |

The exact sizes are listed in [Core Concepts](concepts.md#targets-device-families-and-slots).
