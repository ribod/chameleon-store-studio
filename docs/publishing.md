# Publishing

Chameleon Store Studio can publish the entire application release (app binary, store listing texts, images, and release notes) or individual listing parts directly to Google Play and App Store Connect. Publishing is always an explicit task. It never runs as part of `build`, `check` or `storeStudio`.

```bash
./gradlew :app:storePublish --publish-dry-run        # show the plan, change nothing
./gradlew :app:storePublish                          # full release across configured stores
./gradlew :app:storePublishListing                   # complete listing only (no binary)
./gradlew :app:storePublishPlay                      # full release on Google Play (1 atomic Edit)
```

## Task hierarchy

Store Studio organizes publishing into three distinct scopes:

1. **Full Release** (`storePublish`, `storePublishPlay`, `storePublishAppStore`): Publishes the **entire release** (app binary + store listing texts, images, and release notes). On Google Play, `storePublishPlay` uploads the binary (AAB), R8 mapping file, native debug symbols, track release & release notes, localized descriptions, and visual assets inside a **single atomic Google Play Edit** and commits once. On App Store Connect, `storePublishAppStore` runs a **single coordinated flow**: it authenticates once, resolves the app and editable version once, uploads and processes the `.ipa`, updates TestFlight *What to Test* and beta groups, uploads all store screenshots and localizations, attaches the processed build to the editable version, and — only after both binary and listing/screenshots are ready — submits the version for App Store review (when `submitForReview = true`).
2. **Complete Listing** (`storePublishListing`, `storePublishPlayListing`, `storePublishAppStoreListing`): Publishes the **complete store listing** (texts + images + release notes) without uploading a binary.
3. **Only Binary** (`storePublishBinary`, `storePublishPlayBinary`, `storePublishAppStoreBinary`): Uploads only the app binary, mapping file, and release notes to a track or TestFlight without rendering, validating, or updating store listing assets. See [Binary Publishing](binary-publishing.md).

## Publish only part of a listing

`storePublishListing` uploads the complete store listing. To publish just a part, for example only the release notes from CI or only texts, use the narrower granular tasks:

| Task | Publishes |
|---|---|
| `storePublish` | Full release (binary + complete listing) to every configured store. |
| `storePublishListing` | Complete store listing (texts + images + release notes) to every configured store, without binary. |
| `storePublishTexts` | Listing texts only across every configured store (does not render or export). |
| `storePublishReleaseNotes` | Release notes / What's New only across every configured store (does not render or export). |
| `storePublishImages` | Visual assets (screenshots, feature graphic, icon) only across every configured store. |
| `storePublishPlay` | Full release (binary + complete listing) in a single atomic Google Play Edit. |
| `storePublishPlayListing` | Complete listing (images, texts, and release notes) on Google Play, without binary. |
| `storePublishPlayTexts` | Title, short and full description only (does not render or export). |
| `storePublishPlayReleaseNotes` | Release notes of a track only (needs `--track` or `track` in the DSL; does not render or export). |
| `storePublishPlayImages` | Screenshots, feature graphic, and icon only. |
| `storePublishAppStore` | Full release (IPA binary + complete listing) on App Store Connect. |
| `storePublishAppStoreListing` | Complete listing (texts + images + What's New) on App Store Connect, without binary. |
| `storePublishAppStoreTexts` | Listing texts only: name, subtitle, description, keywords, promotional text, support and marketing URLs (does not render or export). |
| `storePublishAppStoreReleaseNotes` | What's New only (does not render or export). |
| `storePublishAppStoreImages` | Screenshots only. |

Anything a task does not publish is left as it is in the store. All of them accept the options of
their store (below), including `--publish-dry-run`.

```bash
./gradlew :app:storePublishPlayReleaseNotes --track=production      # release notes only
./gradlew :app:storePublishPlayTexts --publish-dry-run              # preview a text update
./gradlew :app:storePublishPlayListing                              # complete listing without binary
./gradlew :app:storePublishPlayImages                               # screenshots only
```

Text-only tasks (`Texts` and `ReleaseNotes`) do not render or export. They depend only on
`storeParseMetadata` and run only the [metadata validation rules](validation.md#listing-text),
honoring severity overrides and `failOnWarning`. Tasks that include images (`storePublish`, `storePublishListing`, `storePublishImages`, `storePublishPlay`, `storePublishPlayListing`, and the store-specific `Images` tasks) enforce the full safety checks described below.

### Selecting parts with `--parts`

Instead of memorizing separate task names, you can pass `--parts=<comma-separated>` (or `-PstoreStudio.publish.parts=<comma-separated>`) to `storePublish`, `storePublishListing`, `storePublishPlay`, `storePublishPlayListing`, `storePublishAppStore`, or `storePublishAppStoreListing` to pick any combination of parts:

- `binary` — app binary (AAB on Google Play, IPA on App Store Connect)
- `texts` — store listing texts (title, descriptions, subtitle, keywords, URLs)
- `images` — screenshots, feature graphic, and the Google Play icon from [`icons { }`](configuration.md#icons)
- `releaseNotes` — track release notes on Google Play, What's New (and TestFlight *What to Test*) on App Store Connect
- `all` — all listing parts (`texts`, `images`, `releaseNotes`); on full-release tasks (`storePublish`, `storePublishPlay`, `storePublishAppStore`), also includes `binary`

```bash
./gradlew :app:storePublish --parts=texts,releaseNotes          # texts + release notes on both stores
./gradlew :app:storePublishPlay --parts=binary,texts           # AAB + Play listing texts in 1 atomic Edit
./gradlew :app:storePublishAppStore --parts=binary,images      # IPA + App Store screenshots in 1 session
./gradlew :app:storePublishListing --parts=images              # images only across all configured stores
```

## Safety checks

Before uploading images, `storePublish` (and the `Images` tasks):

1. runs `storeValidate` and refuses to publish if there are **validation errors**;
2. refuses to publish if the export changed **after** it was validated;
3. with `--publish-dry-run` (or `-PstoreStudio.publish.dryRun=true`), prints exactly what would be
   uploaded, per store, locale and device type, without changing anything in the stores.

Always run a dry run first.

## Google Play

```kotlin title="app/build.gradle.kts"
storeStudio {
    publishing {
        googlePlay {
            packageName.set("com.example.app")    // defaults to the Android namespace
            serviceAccountJson.set(providers.environmentVariable("PLAY_SERVICE_ACCOUNT_JSON"))
            track.set("production")               // optional: where release notes go
            changesNotSentForReview.set(false)
        }
    }
}
```

**Credentials.** Create a service account in Google Cloud, grant it access to your app in the Play
Console (*Users and permissions*, with permission to manage the store listing), and download its JSON
key. Pass the key's contents or its path through `serviceAccountJson` or the
`PLAY_SERVICE_ACCOUNT_JSON` environment variable. Never commit it.

**What is uploaded.** Within a single Play Developer API *edit*:

- when running `storePublishPlay` (full release): the application binary (`.aab`), R8 mapping file, native debug symbols, and track release configuration (status, rollout fraction, release name, and in-app update priority);
- for each locale and image type (phone, 7-inch tablet, 10-inch tablet, Wear OS, feature graphic, icon), the existing images are replaced by the exported ones, in carousel order;
- the listing text (title, short and full description) for each locale with metadata;
- release notes, when a `track` is configured, on that track's release.

The edit is validated and committed atomically at the end. If anything fails (binary staging, image upload, metadata update, or validation), the entire edit is aborted/deleted and the release and listing stay exactly as they were.

!!! info "Verified in production"
    Google Play publishing (`storePublishPlay` — atomic binary + store listing assets and metadata; and `storePublishPlayListing` / `storePublishPlayBinary`) has been tested end-to-end against Google Play in production and is verified working as expected.

### Google Play task options

`storePublishPlay` (full release) accepts both binary and listing options:

| Option | Description |
|---|---|
| `--publish-dry-run` | Show the plan only, validate the edit, and abort without committing. |
| `--track=<name>` | `internal`, `alpha`, `beta`, `production` or a custom track. Also `-PstoreStudio.publish.track`. |
| `--release-status=<status>` | Release status on the track: `completed`, `inProgress`, `halted`, or `draft`. Default: `completed`. |
| `--release-name=<name>` | Name of the release shown in Google Play Console. |
| `--upload-timeout=<duration>` | Timeout for binary uploads (e.g. `2m`, `120s`). Default: `2m`. |
| `--release-file=<path>` | Path to `.aab` to upload (repeatable). Defaults to the AGP bundle of `release.variant`. |
| `--mapping-file=<path>` | Path to R8/ProGuard mapping file. Defaults to the AGP mapping of `release.variant`. |
| `--user-fraction=<fraction>` | Rollout fraction (`0.0`–`1.0`) for staged rollouts (`inProgress` or `halted`). |
| `--in-app-update-priority=<0-5>` | In-app update priority. |
| `--existing-edit-id=<id>` | Stage into an existing open edit and do not commit. |
| `--changes-not-sent-for-review` | Save the changes without sending them for review, for apps that require it. |
| `--package-name=<id>` | Override the application id. |

For publishing only the store listing without uploading a binary, use `storePublishPlayListing` (accepts `--track`, `--changes-not-sent-for-review`, `--package-name`, `--publish-dry-run`). See [above](#publish-only-part-of-a-listing) for granular tasks.

## App Store Connect

!!! warning "Experimental: not tested against the real App Store"
    App Store Connect publishing is implemented and covered by tests against a simulated API, but it
    **has not been tested against the real App Store Connect yet**. Use `--publish-dry-run`, try it
    on a test app first, and please [report issues](https://github.com/ribod/chameleon-store-studio/issues).

```kotlin title="app/build.gradle.kts"
storeStudio {
    publishing {
        appStore {
            bundleId.set("com.example.app")
            keyId.set(providers.environmentVariable("ASC_KEY_ID"))
            issuerId.set(providers.environmentVariable("ASC_ISSUER_ID"))
            privateKey.set(providers.environmentVariable("ASC_PRIVATE_KEY"))
        }
    }
}
```

**Credentials.** Create an API key in App Store Connect (*Users and Access → Integrations*) with the
App Manager role. `privateKey` is the contents of the downloaded `AuthKey_<id>.p8` file.

**What is uploaded.**

- when running `storePublishAppStore` (full release), Store Studio executes a **single coordinated flow**:
    1. authenticates once and resolves the app and editable version (`Prepare for Submission` / `Rejected`) once;
    2. uploads and processes the `.ipa` via the Build Uploads API, then updates TestFlight *What to Test* and adds the build to any configured beta groups (and submits for beta review when `--submit-beta-review` / `submitBetaReview = true`);
    3. for the editable app version, replaces each locale's screenshot sets (iPhone 6.9", iPhone 6.5", iPad 13") with the exported images;
    4. updates `appStoreVersionLocalizations` (description, keywords, promotional text, What's New, URLs) and `appInfoLocalizations` (name, subtitle) from metadata;
    5. attaches the processed build to the editable version;
    6. only after both the binary and listing/screenshots are ready, submits the version for App Store review (when `--submit-for-review` / `submitForReview = true`).

### App Store Connect task options

`storePublishAppStore` (full release) accepts both binary and listing options:

| Option | Description |
|---|---|
| `--publish-dry-run` | Show the plan only. |
| `--bundle-id=<id>` | Override the bundle id. |
| `--ipa=<path>` | Path to IPA file to upload. |
| `--bundle-short-version=<v>` | `CFBundleShortVersionString` override. |
| `--bundle-version=<n>` | `CFBundleVersion` build number override. |
| `--beta-group=<name>` | TestFlight beta group to add the build to (repeatable). |
| `--submit-beta-review` | Submit the build for TestFlight beta app review. |
| `--submit-for-review` | Submit the build for App Store review. |

To publish the complete App Store listing without uploading a binary, use `storePublishAppStoreListing`.

## App binaries

Store Studio provides full release tasks (`storePublishPlay`, `storePublishAppStore`, `storePublish`) that publish the app binary and store listing together. If you want to publish **only** the app binary (an AAB to a Google Play track, or an IPA to TestFlight) without rendering or updating store listing assets, use the dedicated binary tasks:

- `storePublishPlayBinary` (Google Play)
- `storePublishAppStoreBinary` (App Store Connect)
- `storePublishBinary` (configured stores)

They are independent of the listing tasks and of the validation gate. See [Binary Publishing](binary-publishing.md).

## From the IDE

The [Publish dialog](ide/publishing.md) runs the same tasks, with dry run enabled by default.
