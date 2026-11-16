# Change Log

## 0.1.0 — 2026-11-16

First public release. Experimental.

- `@StoreScreenshot` and `@StoreFeatureGraphic` with KSP discovery and compile-time checks.
- Runtime library: `StoreScreenshotCanvas`, `StoreFeatureGraphicCanvas`, device mockups, status bars,
  backgrounds, typography and `StoreContext`.
- Headless rendering with Layoutlib and automatic Robolectric fallback, with a build-wide process
  limit and build cache support.
- Export at exact Google Play and App Store sizes, with transparency flattening and JPEG fallback.
- About fifty validation rules for images and listing text.
- Store metadata from text files, with default-locale fallback.
- App icon per store with `icons { }`, found by convention when not configured and shown in the IDE
  Store Preview and the HTML reports.
- Visual and text diff against the base branch rendered on the same machine, an export of the base,
  or a committed hash-only baseline. Exact comparison by default.
- HTML gallery, store listing preview and diff reports.
- `storeCi` quality gate with GitHub and GitLab pull request comments.
- Publishing to Google Play and (experimental) App Store Connect.
- IDE plugin for Android Studio and IntelliJ IDEA: gallery, store preview, diff, validation and
  publishing.
- Binary publishing: `storePublishPlayBinary` uploads an AAB to a Google Play track, with
  mapping and debug symbols, rollout options and staging into an existing edit.
  `storePublishAppStoreBinary` uploads an IPA to App Store Connect through the Build Uploads REST
  API, without Xcode, and can add it to TestFlight groups and submit it for review. `storePublishBinary`
  runs the configured ones. Google Play binary publishing verified in production; App Store Connect tested against simulated APIs.
- Configurable status bar: `storeStudio { statusBar { … } }` sets the style, time, signal, battery and
  icon colors for every device mockup (off by default). Each screenshot can override it with
  `DeviceMockup(statusBar = …)`.
- Publishing hierarchy: `storePublish` and store-specific tasks `storePublishPlay` (single atomic Google Play Edit) and `storePublishAppStore` publish the full release (binary + complete listing). `storePublishListing`, `storePublishPlayListing`, and `storePublishAppStoreListing` publish complete listings without binaries. Granular tasks (`storePublishPlayTexts`, `storePublishPlayReleaseNotes`, `storePublishPlayImages`, `storePublishAppStoreTexts`, `storePublishAppStoreReleaseNotes`, `storePublishAppStoreImages`) publish specific listing parts. Text-only tasks skip rendering and export. Dedicated binary tasks (`storePublishBinary`, `storePublishPlayBinary`, `storePublishAppStoreBinary`) upload binaries only.
- A single metadata layout, `<locale>/play/` and `<locale>/appstore/`. `storeParseMetadata` warns
  about files from other layouts.
- `storeExportMetadata`, run after `storeExport`, writes the listing texts and Google Play
  `whatsnew-<locale>` release notes to `build/store/export-metadata/`.
- Tested with AGP 9.1.1 to 9.4.1, Kotlin 2.4.10 to 2.4.20 and Gradle 9.3.1 to 9.6.0.
- The plugin modules are compiled against Kotlin API 2.2, so they run on Gradle's embedded stdlib.
- Model types live in `dev.ribod.storestudio.model`; the metadata manifest types live in
  `dev.ribod.storestudio.core.metadata`.
- HTML reports escape embedded JSON and attribute values.
- File-size limits come from the store catalog: 8 MB for Google Play screenshots, 15 MB for the
  Google Play feature graphic, and a 10 MiB App Store warning (Apple publishes no hard limit).
