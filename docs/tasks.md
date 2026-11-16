# Tasks Reference

All tasks belong to the **store studio** group (`./gradlew tasks --group "store studio"`). Run them
on a module, for example `./gradlew :app:storeExport`.

## Everyday tasks

| Task | Description |
|---|---|
| `storeExport` | Render if needed and export store-ready images. Alias: `exportStoreAssets`. |
| `storeValidate` | Validate images and listing text against store rules. Runs with `check` in app modules. |
| `storeStudio` | Lifecycle: validation, baseline verification, gallery and listing preview. |
| `storeGallery` | HTML gallery. Alias of `exportStoreGalleryHtml`. |
| `storeListingPreview` | HTML store listing preview. Alias of `exportStoreListingPreviewHtml`. |

## Pipeline

| Task | Description |
|---|---|
| `storeMergeManifest` | Merge KSP manifests of this module and its project dependencies. |
| `storePrepareLayoutlib` | Resolve and unpack Layoutlib (cached across builds). |
| `storeRender` | Render with Layoutlib. Cacheable. |
| `storeRenderRobolectric` | Render the jobs Layoutlib couldn't with Robolectric. |
| `storeParseMetadata` | Parse `store/metadata` into `metadata.json`. |
| `storeExportMetadata` | Write the listing texts and release notes as plain files to `build/store/export-metadata/`. Runs after `storeExport`. |

## Baselines and diff

| Task | Options | Description |
|---|---|---|
| `storeRecordBaseline` | | Record the current export as the approved baseline. |
| `storeVerify` | | Fail if the export differs from the committed baseline (`baseline.failOnDrift`). Skipped when there is none, unless `baseline.required`. |
| `storeDiff` | `--render-base`, `--against=<ref>`, `--base-export=<dir>` | Compute changes into `diff.json`. |
| `exportStoreDiffHtml` | `--include-unchanged` | HTML diff report and Markdown summary. |
| `storeDiffReport` | | Lifecycle for the diff report. |

## CI

| Task | Options | Description |
|---|---|---|
| `storeCi` | `--[no-]fail-on-drift`, `--[no-]fail-on-validation`, `--post-pr-comment`, `--pr-number=<n>` | Quality gate with summary and PR comment. |
| `storeCiCheck` | | Lifecycle alias of `storeCi`. |

## Publishing

| Task | Options | Description |
|---|---|---|
| `storePublish` | `--target=ALL|PLAY_STORE|APP_STORE`, `--parts=<parts>`, `--publish-dry-run`, `--track`, `--release-status`, `--release-name`, `--upload-timeout=<duration>`, `--release-file`, `--ipa`, `--package-name`, `--bundle-id`, `--beta-group`, `--submit-beta-review`, `--submit-for-review` | Full release: app binary + complete listing across configured stores. |
| `storePublishListing` | `--target=ALL|PLAY_STORE|APP_STORE`, `--parts=<parts>`, `--publish-dry-run`, `--track`, `--package-name`, `--bundle-id` | Complete listing without binary across configured stores. |
| `storePublishTexts` | same as `storePublishListing` | Listing texts only across configured stores (does not render or export). |
| `storePublishReleaseNotes` | same as `storePublishListing` | Release notes / What's New only across configured stores (does not render or export). |
| `storePublishImages` | same as `storePublishListing` | Visual assets (screenshots, feature graphic, icon) only across configured stores. |
| `storePublishPlay` | `--parts=<parts>`, `--track`, `--release-status`, `--release-name`, `--upload-timeout=<duration>`, `--release-file`, `--mapping-file`, `--user-fraction`, `--in-app-update-priority`, `--existing-edit-id`, `--changes-not-sent-for-review`, `--package-name`, `--publish-dry-run` | Full release: binary + complete listing in a single atomic Google Play Edit. |
| `storePublishPlayListing` | `--parts=<parts>`, `--publish-dry-run`, `--track`, `--changes-not-sent-for-review`, `--package-name` | Complete listing (images, texts, and release notes) on Google Play, without binary. |
| `storePublishPlayTexts` | same as `storePublishPlayListing` | Title and descriptions only (does not render or export). |
| `storePublishPlayReleaseNotes` | same as `storePublishPlayListing` | Release notes only on a track (does not render or export). |
| `storePublishPlayImages` | same as `storePublishPlayListing` | Screenshots, feature graphic, and icon only. |
| `storePublishAppStore` | `--parts=<parts>`, `--publish-dry-run`, `--bundle-id`, `--ipa`, `--bundle-short-version`, `--bundle-version`, `--beta-group`, `--submit-beta-review`, `--submit-for-review` | IPA binary + complete listing on App Store Connect in a single coordinated session (experimental). |
| `storePublishAppStoreListing` | `--parts=<parts>`, `--publish-dry-run`, `--bundle-id` | Complete listing without binary on App Store Connect. |
| `storePublishAppStoreTexts` | same as `storePublishAppStoreListing` | Listing texts only (does not render or export). |
| `storePublishAppStoreReleaseNotes` | same as `storePublishAppStoreListing` | What's New only (does not render or export). |
| `storePublishAppStoreImages` | same as `storePublishAppStoreListing` | Screenshots only. |

See [Publishing](publishing.md#publish-only-part-of-a-listing) for what each task publishes and [Selecting parts with `--parts`](publishing.md#selecting-parts-with-parts) for `--parts=<parts>` (`binary`, `texts`, `images`, `releaseNotes`, `all`).

Gradle properties: `-PstoreStudio.publish.dryRun=true`, `-PstoreStudio.publish.track=<track>`, `-PstoreStudio.publish.parts=<parts>`, `-PstoreStudio.publish.betaGroups=<g1,g2>`, `-PstoreStudio.publish.submitBetaReview=true|false`, and `-PstoreStudio.publish.submitForReview=true|false`.

## Binary publishing

These tasks upload only the app binary itself. They do not render, export, or validate screenshots, and they do not touch listing texts or images. Details and every option are in [Binary Publishing](binary-publishing.md).

| Task | Options | Description |
|---|---|---|
| `storePublishPlayBinary` | `--release-file=<path>` (repeatable), `--track`, `--release-status`, `--user-fraction`, `--in-app-update-priority`, `--release-name`, `--upload-timeout=<duration>`, `--mapping-file`, `--existing-edit-id`, `--changes-not-sent-for-review`, `--publish-dry-run` | Binary + mapping + release notes only (does not render or export). Uploads the AAB to a Google Play track. Without `--release-file`, uses the AGP bundle of `release.variant`. |
| `storePublishAppStoreBinary` | `--ipa=<path>`, `--bundle-short-version`, `--bundle-version`, `--beta-group=<name>` (repeatable), `--submit-beta-review`, `--submit-for-review`, `--publish-dry-run` | IPA binary only. Uploads the IPA to App Store Connect and waits for processing. |
| `storePublishBinary` | | Lifecycle: binaries only for configured stores. Runs the Play binary task when an Android application is configured or release files are set, and the App Store task when an IPA is set. |

Use `--publish-dry-run`, not `--dry-run`: Gradle handles `--dry-run` itself and skips the tasks.

## Gradle properties for the diff

Command-line options only reach the task you name. When `storeDiff` runs as a dependency (of
`storeCi`, `storeDiffReport` or `exportStoreDiffHtml`), set its base with properties:
`-PstoreStudio.diff.renderBase=true`, `-PstoreStudio.diff.against=<ref>` and
`-PstoreStudio.diff.baseExport=<dir>`.

## Diagnostics

| Task | Description |
|---|---|
| `storeDoctor` | Check JDK, Android SDK, plugins, KSP and configuration, with fixes for each problem. |
| `storeDump` | Print the resolved configuration and render matrix (screenshots × slots × locales). |
