# Configuration

Everything is configured in the `storeStudio { }` block of the module that applies the plugin. All
options have sensible defaults; most projects only set stores, devices and locales.

## Simple configuration

```kotlin title="app/build.gradle.kts"
import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget

storeStudio {
    targets.set(setOf(StoreTarget.PLAY_STORE, StoreTarget.APP_STORE))
    defaultDevices.set(setOf(DeviceFamily.PHONE, DeviceFamily.TABLET))
    locales {
        register("en-US")
        register("es-MX") { playStore.set("es-419") }
    }
    reports {
        appName.set("My App")
        developerName.set("My Company")
    }
}
```

## Stores, devices and locales

| Option | Default | Description |
|---|---|---|
| `targets` | both stores | `Set<StoreTarget>` rendered when a screenshot doesn't restrict `targets`. |
| `defaultDevices` | `PHONE`, `TABLET` | `Set<DeviceFamily>` rendered when a screenshot doesn't restrict `devices`. |
| `locales { register(tag) { … } }` | `en-US` | BCP-47 tags to render. Each can map to store-specific codes with `playStore` and `appStore`. `locale(tag)` is a shortcut for `register`. |
| `variant` | `"debug"` | Android build variant whose classes and resources are rendered. |
| `autoDependencies` | `true` | Adds the annotations, runtime and KSP processor automatically. |
| `pluginVersion` | plugin version | Version of the libraries added by `autoDependencies`. |

```kotlin
storeStudio {
    appStore {
        supportsIpad.set(true)                       // generate iPad 13" (default: true)
        extraIphoneSizes.set(setOf("IPHONE_6_5"))    // also generate the 6.5" iPhone set
    }
}
```

## Advanced configuration

Each block below is optional. The full option list with defaults is in the reference tables; the
dedicated pages explain when to change them.

```kotlin
storeStudio {
    rendering {                       // see Rendering
        maxParallelProcesses.set(2)
        rendererJvmArgs.set(listOf("-Xmx3g"))
    }
    export {                          // see Export
        background.set("#FFFFFF")
        layout.set(ExportLayout.STORE_LOCALE_DEVICE)
    }
    validation {                      // see Validation
        failOnWarning.set(true)
        severityOverrides.put("ORDER_DUPLICATE", Severity.INFO)
    }
    metadata {                        // see Store Metadata
        directory.set(layout.projectDirectory.dir("store/metadata"))
    }
    baseline {                        // see Baselines & Visual Diff
        required.set(true)
    }
    reports { /* … */ }               // see HTML Reports
    statusBar { /* … */ }             // see Composing Screenshots
    ci { /* … */ }                    // see CI Integration
    publishing { /* … */ }            // see Publishing
}
```

??? info "Imports used in these examples"
    ```kotlin
    import dev.ribod.storestudio.annotations.DeviceFamily
    import dev.ribod.storestudio.annotations.StoreTarget
    import dev.ribod.storestudio.core.image.SizeMismatchPolicy
    import dev.ribod.storestudio.model.ExportLayout
    import dev.ribod.storestudio.model.Severity
    import dev.ribod.storestudio.core.report.ReportImageMode
    import dev.ribod.storestudio.gradle.dsl.BaselineComparison
    import dev.ribod.storestudio.gradle.dsl.RenderingEngine
    import dev.ribod.storestudio.gradle.dsl.StatusBarStyleKind
    ```

## Reference

### `rendering { }`

| Option | Default | Description |
|---|---|---|
| `engine` | `LAYOUTLIB` | Primary renderer: `LAYOUTLIB` or `ROBOLECTRIC`. |
| `fallbackEngine` | `ROBOLECTRIC` | Renders jobs the primary engine couldn't. `NONE` fails the build instead. |
| `maxParallelProcesses` | `2` | Renderer JVMs running at the same time, **across the whole build**. |
| `batchSize` | `40` | Maximum screenshots per renderer process. Jobs are split into evenly sized batches. |
| `timeout` | `10m` | Maximum time per batch. |
| `stallTimeout` | `60s` | A renderer that makes no progress for this long is stopped. |
| `fallbackTimeout` | `5m` | Maximum time for the Robolectric fallback. |
| `rendererJvmArgs` | `["-Xmx2g"]` | JVM arguments of renderer processes. |
| `fallbackJvmArgs` | `["-Xmx2g"]` | JVM arguments of the fallback process. |
| `layoutlibVersion`, `robolectricVersion`, `rendererVersion` | bundled | Engine versions. Change only if instructed. |
| `configureLibraryResources` | `true` | Lets Android library modules render their own screenshots (see [Multi-Module](multi-module.md)). |

### `export { }`

| Option | Default | Description |
|---|---|---|
| `background` | `"#FFFFFF"` | Color used to flatten transparency (`#RRGGBB`). |
| `sizeTolerancePx` | `2` | Allowed size deviation before adjusting (0–2). |
| `sizeMismatchPolicy` | `CROP_OR_PAD` | `CROP_OR_PAD` adjusts within tolerance; `FAIL` stops the export. |
| `jpegFallback` | `true` | Re-encode as JPEG when a PNG exceeds the store's size limit. |
| `jpegQuality` | `0.92` | Starting JPEG quality for that fallback. |
| `layout` | `STORE_LOCALE_DEVICE` | Folder structure: `STORE_LOCALE_DEVICE`, `LOCALE_STORE_DEVICE` or `LOCALE_DEVICE`. |
| `lowercaseFolders` | `true` | `play_store/phone` instead of `PLAY_STORE/PHONE`. |

### `validation { }`

| Option | Default | Description |
|---|---|---|
| `failOnWarning` | `false` | Treat warnings as errors. |
| `featureGraphicMissing` | `ERROR` | Severity when Google Play's feature graphic is missing. |
| `severityOverrides` | empty | `ruleId → Severity`. Hard store requirements can't go below `ERROR`. |
| `attachToCheck` | `true` | Run `storeValidate` as part of `check` in application modules. |

### `metadata { }`

| Option | Default | Description |
|---|---|---|
| `directory` | `store/metadata` | Folder with listing text (see [Store Metadata](metadata.md)). |
| `defaultLocale` | `"en-US"` | Locale used when a translation is missing. |

### `baseline { }`

| Option | Default | Description |
|---|---|---|
| `file` | `.store/baseline.json` | Committed baseline, when you use one. |
| `comparison` | `STRICT` | `STRICT`: any pixel or text difference is a change. `PERCEPTUAL`: tolerates small rendering differences between operating systems; text changes still count. |
| `perceptualThreshold` | `0.05` | Maximum perceptual distance (0–1) still considered unchanged. |
| `failOnDrift` | `true` | `storeVerify` fails when images changed. |
| `required` | `false` | `storeVerify` fails when no baseline is recorded, instead of skipping. |
| `baseExportDir` | unset | Export of the base revision to compare against (same as `--base-export`). |

### `icons { }`

| Option | Default | Description |
|---|---|---|
| `googlePlay` | `ic_launcher-playstore.png`, else `mipmap-*/ic_launcher.png` or `.webp` | Icon shown in the Google Play preview and uploaded to Google Play. |
| `appStore` | the largest PNG of `AppIcon.appiconset` | Icon shown in the App Store preview. |

```kotlin
storeStudio {
    icons {
        googlePlay.set(layout.projectDirectory.file("art/play-icon.png"))
        appStore.set(rootProject.layout.projectDirectory.file("iosApp/AppIcon.png"))
    }
}
```

Each store uses its own platform's icon first. Only the conventional names above are searched, in the
module and then in the root project; anything else must be configured. A store without an icon of
its own previews the other store's icon, and shows the Chameleon placeholder when neither exists.
Only the Google Play icon is ever uploaded, so an iOS icon is never sent to Google Play.

### `reports { }`

| Option | Default | Description |
|---|---|---|
| `appName` | project name | App name shown in reports and the listing preview. |
| `developerName` | `"Mobile Developer"` | Publisher shown in the listing preview. |
| `category` | `"Productivity"` | Store category shown in the listing preview. |
| `htmlGallery`, `storeListingPreview` | `true` | Include these reports in the `storeStudio` lifecycle task. |
| `imageMode` | `THUMBNAIL` | `THUMBNAIL` (small embedded JPEGs), `FULL` (embedded originals) or `LINK` (relative links). |
| `maxThumbnailDimension` | `720` | Longest side of embedded thumbnails. |
| `thumbnailQuality` | `0.85` | JPEG quality of thumbnails. |
| `includeUnchangedInDiff` | `false` | Show unchanged images in the diff report. |
| `outputDirectory` | `build/store/reports` | Where reports are written. |

### `statusBar { }`

The fake device status bar drawn by `DeviceMockup`. **Off by default**: screenshots have no status
bar unless you enable it here or pass `statusBar` to a single `DeviceMockup`. See
[Composing Screenshots](composing.md#status-bar).

| Option | Default | Description |
|---|---|---|
| `enabled` | `false` | Draws the status bar on every device mockup that doesn't override it. |
| `style` | `AUTO` | `AUTO` picks the iOS look for iPhone and iPad frames and the Android look for the rest. `ANDROID` and `IOS` force a look. |
| `time` | `"9:41"` | Clock text. |
| `wifi`, `cellular` | `4` | Signal levels, `0` to `4`. |
| `battery` | `100` | Battery percentage, `0` to `100`. At 20% or less it turns red, unless charging. |
| `charging` | `false` | Shows a charging bolt. |
| `darkIcons` | `false` | Dark ink instead of white. This applies to every screenshot, so override it on dark screens. |
| `showTime`, `showWifi`, `showCellular`, `showBattery` | `true` | Hides the element when `false`. Hidden elements take no space. |

```kotlin title="app/build.gradle.kts"
storeStudio {
    statusBar {
        enabled.set(true)
        style.set(StatusBarStyleKind.AUTO)
        time.set("9:41")
        wifi.set(4)
        battery.set(100)
    }
}
```

Out-of-range values (for example `wifi` set to 5) fail the build with a message naming the option.

### `ci { }`

| Option | Default | Description |
|---|---|---|
| `failOnValidationErrors` | `true` | `storeCi` fails on validation errors. |
| `failOnDrift` | `false` | `storeCi` fails when images changed. |
| `postPrComment` | `false` | Post or update a sticky pull request comment. |
| `commentMarker` | one per project | Marker used to find the comment to update. Each project gets its own, so several apps in one repository keep separate comments. |
| `prNumber` | auto-detected | Pull or merge request number. |
| `githubToken`, `gitlabToken` | `GITHUB_TOKEN`, `GITLAB_TOKEN` | Tokens used to comment. |

### `publishing { }`

| Option | Default | Description |
|---|---|---|
| `googlePlay.packageName` | Android namespace | Application id on Google Play. |
| `googlePlay.serviceAccountJson` | `PLAY_SERVICE_ACCOUNT_JSON` | Service account key (contents or path). |
| `googlePlay.track` | unset | Track whose latest release receives release notes (`internal`, `alpha`, `beta`, `production` or custom). |
| `googlePlay.changesNotSentForReview` | `false` | Save as a draft without sending for review. |
| `googlePlay.release { }` | | Binary upload to a Play track. See [Binary Publishing](binary-publishing.md#google-play). |
| `googlePlay.release.releaseFiles` | empty | AAB files. Empty uses the AGP bundle of `variant`. |
| `googlePlay.release.variant` | `"release"` | Variant whose bundle and mapping file are uploaded. |
| `googlePlay.release.track` | `googlePlay.track`, then `internal` | Track for the binary release. |
| `googlePlay.release.status` | `completed` | `completed`, `inProgress`, `halted` or `draft`. |
| `googlePlay.release.userFraction` | unset | Rollout fraction, between 0 and 1. Required with `inProgress`. |
| `googlePlay.release.inAppUpdatePriority` | unset | In-app update priority, `0` to `5`. |
| `googlePlay.release.releaseName`, `mappingFile`, `debugSymbols` | unset | Release name, R8 mapping file and native debug symbols. |
| `googlePlay.release.existingEditId` | unset | Stage into an open edit, which Store Studio then does not commit. |
| `googlePlay.release.retainVersionCodes` | empty | Earlier version codes to keep in the release. |
| `googlePlay.release.uploadTimeout` | `2m` | Timeout for each upload. |
| `googlePlay.release.useReleaseNotes` | `true` | Send the release notes of the store metadata. |
| `appStore.bundleId` | unset | Bundle id on App Store Connect. |
| `appStore.keyId`, `appStore.issuerId` | `ASC_KEY_ID`, `ASC_ISSUER_ID` | App Store Connect API key identifiers. |
| `appStore.privateKey` | `ASC_PRIVATE_KEY` | Contents of the `AuthKey_*.p8` file. |
| `appStore.build { }` | | IPA upload to App Store Connect. See [Binary Publishing](binary-publishing.md#app-store-connect-ipa). |
| `appStore.build.ipaFile` | unset | IPA to upload. Required by `storePublishAppStoreBinary`. |
| `appStore.build.bundleShortVersion`, `bundleVersion` | from the IPA | Version and build number. |
| `appStore.build.betaGroups` | empty | TestFlight groups to add the build to. |
| `appStore.build.submitBetaReview`, `submitForReview` | `false` | Submit for TestFlight beta review or App Store review. |
| `appStore.build.useReleaseNotesAsWhatToTest` | `true` | Use the release notes as TestFlight *What to Test*. |
| `appStore.build.pollInterval`, `processingTimeout` | `15s`, `1h` | Processing checks and their maximum duration. |

!!! tip "Keep secrets out of build files"
    Read credentials from environment variables or Gradle properties, for example
    `serviceAccountJson.set(providers.environmentVariable("PLAY_SERVICE_ACCOUNT_JSON"))`.
