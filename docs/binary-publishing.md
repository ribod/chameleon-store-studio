# Binary Publishing

Binary publishing uploads the **app itself**: an Android App Bundle to a Google Play track, and an iOS `.ipa` to App Store Connect as a TestFlight build. Google Play only accepts App Bundles (`.aab`), so an `.apk` is rejected before anything is uploaded.

If you want to publish **only** the app binary (with mapping and release notes) without rendering, exporting, or validating screenshots, use the dedicated binary tasks: `storePublishPlayBinary` (Google Play), `storePublishAppStoreBinary` (App Store Connect), or `storePublishBinary` (all configured stores).

If you want to publish both the binary and the complete store listing (texts, images, release notes) in one command — executed in a single atomic Google Play Edit on Android, and in a single coordinated App Store Connect session on iOS (uploading the IPA, syncing screenshots and metadata, attaching the build, and submitting for review only after both are ready) — use the full release tasks: `storePublishPlay`, `storePublishAppStore`, or `storePublish`. See [Publishing](publishing.md).

!!! warning "App Store Connect binary publishing is experimental"
    App Store Connect binary publishing is covered by tests against simulated APIs and has not been
    run against the live App Store yet. Use `--publish-dry-run` first, try it on a test app, and please
    [report issues](https://github.com/ribod/chameleon-store-studio/issues). Google Play binary
    publishing (`storePublishPlayBinary`) is verified in production.

```bash
./gradlew :app:storePublishPlayBinary --publish-dry-run     # show the Play plan, change nothing
./gradlew :app:storePublishPlayBinary --track=internal      # upload an AAB to the internal track
./gradlew :app:storePublishAppStoreBinary --ipa=build/Store.ipa --publish-dry-run
```

!!! note "Use `--publish-dry-run`, not `--dry-run`"
    `--dry-run` is also a Gradle option that skips every task. Chameleon Store Studio's flag is named
    `--publish-dry-run` so Gradle does not swallow it. The equivalent property is
    `-PstoreStudio.publish.dryRun=true`.

## Tasks

| Task | Uploads | Needs |
|---|---|---|
| `storePublishPlayBinary` | AAB (plus mapping and native debug symbols) to a Google Play track. | `googlePlay.packageName` or the Android namespace, and a service account. |
| `storePublishAppStoreBinary` | IPA as a build in App Store Connect (TestFlight). | `appStore.bundleId`, an API key, and `appStore.build.ipaFile`. |
| `storePublishBinary` | Lifecycle task. Runs `storePublishPlayBinary` when the Android application plugin is applied or release files are set, and `storePublishAppStoreBinary` when an IPA is set. | The above, for the configured store. |

These dedicated binary tasks do not render or validate screenshots and do not touch store listing assets. To publish both binary and store listing in one command, see [Publishing](publishing.md).

## Credentials

Binary publishing uses the same credentials as [listing publishing](publishing.md):

| Store | Setting | Environment variable |
|---|---|---|
| Google Play | `publishing.googlePlay.serviceAccountJson` (contents or path) | `PLAY_SERVICE_ACCOUNT_JSON` |
| App Store Connect | `publishing.appStore.keyId`, `issuerId`, `privateKey` | `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_PRIVATE_KEY` |

The service account needs permission to release to tracks in the Play Console. The App Store Connect
API key needs the App Manager role, as described in [Publishing](publishing.md#app-store-connect).
Never commit them.

## Google Play

```kotlin title="app/build.gradle.kts"
storeStudio {
    publishing {
        googlePlay {
            serviceAccountJson.set(providers.environmentVariable("PLAY_SERVICE_ACCOUNT_JSON"))
            release {
                track.set("internal")
                status.set("completed")
            }
        }
    }
}
```

With no `releaseFiles`, the task uploads the Android Gradle Plugin's bundle of the `variant` build
type (default `release`), together with its R8 mapping file. Apply the Android application plugin and
run `./gradlew :app:storePublishPlayBinary`.

**What happens.** Within one Play Developer API *edit*, Store Studio uploads each AAB, uploads
the mapping and native debug symbols for each new version code, sets the release on the track (status,
rollout fraction, in-app update priority, name, release notes and retained version codes), and
validates the edit. Then it commits the edit. If anything fails after the edit was created, Store
Studio deletes that edit, so the track is left as it was. With `existingEditId`, Store Studio stages
into an edit you opened and never commits it.

Release notes come from the store metadata: `<locale>/play/release_notes.txt`, sent for every locale
that has one (500 characters maximum). See [Store Metadata](metadata.md).

| DSL property | CLI flag | Default | Description |
|---|---|---|---|
| `googlePlay.release.releaseFiles` | `--release-file=<path>` (repeatable) | empty | `.aab` files to upload. Empty uses the AGP bundle of `variant`. |
| `googlePlay.release.variant` | | `"release"` | Android variant whose bundle and mapping file are used when no release files are given. |
| `googlePlay.release.track` | `--track=<name>`, `-PstoreStudio.publish.track` | `googlePlay.track`, then `internal` | `internal`, `alpha`, `beta`, `production` or a custom track. |
| `googlePlay.release.status` | `--release-status=<status>`, `-PstoreStudio.publish.releaseStatus` | `completed` | `completed`, `inProgress`, `halted` or `draft`. |
| `googlePlay.release.userFraction` | `--user-fraction=<0-1>` | unset | Share of users for a staged rollout. Required with `inProgress`, allowed with `inProgress` or `halted`. Greater than 0 and less than 1. |
| `googlePlay.release.inAppUpdatePriority` | `--in-app-update-priority=<0-5>` | unset | In-app update priority. |
| `googlePlay.release.releaseName` | `--release-name=<name>` | unset | Name of the release shown in the Play Console. |
| `googlePlay.release.mappingFile` | `--mapping-file=<path>` | AGP mapping of `variant` | R8 or ProGuard mapping file to upload for each new version code. |
| `googlePlay.release.debugSymbols` | | unset | Native debug symbols archive. |
| `googlePlay.release.existingEditId` | `--existing-edit-id=<id>` | unset | Stage into this open edit. Store Studio does not commit it. |
| `googlePlay.release.retainVersionCodes` | | empty | Earlier version codes to keep in the release. |
| `googlePlay.release.uploadTimeout` | `--upload-timeout=<duration>` | `2m` | Timeout for each upload. |
| `googlePlay.release.useReleaseNotes` | | `true` | Send the release notes of the store metadata. |
| `googlePlay.changesNotSentForReview` | `--changes-not-sent-for-review` | `false` | Commit the release without sending it for review. |
| `googlePlay.packageName` | | Android namespace | Application id on Google Play. There is no CLI flag on the binary tasks. |

`--publish-dry-run` validates the upload with Google Play and then aborts the edit. Without
credentials, a dry run only checks the configuration and the files, and says so in the report.

!!! tip "Staging without releasing"
    Open an edit in the Play Console or with the API, pass its id with `--existing-edit-id`, and
    Store Studio stages the binary and release settings into it. Review and commit the edit yourself.

## App Store Connect (IPA)

```kotlin title="app/build.gradle.kts"
storeStudio {
    publishing {
        appStore {
            bundleId.set("com.example.app")
            keyId.set(providers.environmentVariable("ASC_KEY_ID"))
            issuerId.set(providers.environmentVariable("ASC_ISSUER_ID"))
            privateKey.set(providers.environmentVariable("ASC_PRIVATE_KEY"))
            build {
                ipaFile.set(layout.projectDirectory.file("build/ios/Store.ipa"))
                betaGroups.set(listOf("Internal testers"))
            }
        }
    }
}
```

The IPA is uploaded through the App Store Connect **Build Uploads REST API**, so no Xcode, `altool` or
Transporter is needed. It runs on Linux and on any CI agent. You still build and sign the IPA
yourself, with whatever toolchain you use.

Store Studio reads `CFBundleShortVersionString` and `CFBundleVersion` from the IPA's `Info.plist`,
unless you set them. Then it:

1. finds the app by bundle id and resolves the beta groups you named;
2. reserves the IPA as a build upload and uploads it in the chunks Apple specifies;
3. polls until App Store Connect finishes processing, renewing the API token every 15 minutes;
4. sets the TestFlight *What to Test* text per locale from `<locale>/appstore/release_notes.txt`;
5. adds the build to the beta groups, and submits it for beta review if asked;
6. submits for App Store review only when `submitForReview` is set.

Steps 5 and 6 are off unless you ask for them. Submitting for review requires an editable App Store
version whose version string matches the build, created in App Store Connect first.

!!! note "Binary-only vs. full App Store release"
    `storePublishAppStoreBinary` uploads **only** the IPA build (and TestFlight metadata/groups).
    If you want to upload the IPA together with the full App Store listing in one go, use
    `storePublishAppStore` instead — it runs a single coordinated session that uploads and processes
    the IPA, syncs screenshots and localizations (`appStoreVersionLocalizations` and
    `appInfoLocalizations`), attaches the build to the editable version, and submits for review
    only after both binary and listing/screenshots are ready.

| DSL property | CLI flag | Default | Description |
|---|---|---|---|
| `appStore.bundleId` | | unset | Bundle id. The binary task has no `--bundle-id` flag. |
| `appStore.build.ipaFile` | `--ipa=<path>` | unset (required) | IPA to upload. |
| `appStore.build.bundleShortVersion` | `--bundle-short-version=<v>` | from the IPA | `CFBundleShortVersionString`. |
| `appStore.build.bundleVersion` | `--bundle-version=<n>` | from the IPA | `CFBundleVersion`, the build number. |
| `appStore.build.platform` | | `IOS` | Platform of the build. |
| `appStore.build.betaGroups` | `--beta-group=<name>` (repeatable) | empty | TestFlight groups to add the build to. An unknown name fails the task. |
| `appStore.build.submitBetaReview` | `--submit-beta-review` | `false` | Submit the build for TestFlight beta app review. |
| `appStore.build.submitForReview` | `--submit-for-review` | `false` | Attach the build to the editable version and submit it for App Store review. |
| `appStore.build.useReleaseNotesAsWhatToTest` | | `true` | Use the App Store release notes as *What to Test*. |
| `appStore.build.pollInterval` | | `15s` | Interval between processing checks. |
| `appStore.build.processingTimeout` | | `1h` | Maximum wait for processing. The upload may still finish after a timeout. |

With `--publish-dry-run` and credentials, Store Studio finds the app and resolves the beta groups, but
uploads nothing. Without credentials, the dry run only reads the IPA and reports the version it would
upload.

## Continuous integration

In CI pipelines, choose the publishing scope that matches your workflow:

- **Binary only:** If your CI only produces and deploys application binaries (or if screenshots are validated in a separate workflow), use `storePublishPlayBinary` / `storePublishAppStoreBinary` / `storePublishBinary`. These tasks skip rendering, screenshot validation, and listing text uploads.
- **Full release:** To deploy both the application binary and the store listing (screenshots, feature graphic, localized descriptions, and release notes) in one command — executed in a single atomic Google Play Edit — use `storePublishPlay` / `storePublishAppStore` / `storePublish`.

These examples are plain shell commands. Put the secrets in your CI's secret store and expose them as
environment variables, which the plugin reads.

=== "GitHub Actions"

    ```bash
    # Secrets: PLAY_SERVICE_ACCOUNT_JSON, ASC_KEY_ID, ASC_ISSUER_ID, ASC_PRIVATE_KEY
    ./gradlew :app:bundleRelease                                  # the AAB and mapping file
    ./gradlew :app:storePublishPlayBinary --track=internal
    ./gradlew :app:storePublishAppStoreBinary --ipa=build/ios/Store.ipa --beta-group="Internal testers"
    ```

=== "GitLab CI"

    ```bash
    # CI/CD variables (masked, protected): PLAY_SERVICE_ACCOUNT_JSON, ASC_KEY_ID, ASC_ISSUER_ID, ASC_PRIVATE_KEY
    ./gradlew :app:bundleRelease
    ./gradlew :app:storePublishPlayBinary --track=internal
    ./gradlew :app:storePublishAppStoreBinary --ipa=build/ios/Store.ipa --beta-group="Internal testers"
    ```

=== "Bitrise or any shell"

    ```bash
    export PLAY_SERVICE_ACCOUNT_JSON="$(cat "$HOME/keys/play-sa.json")"
    export ASC_KEY_ID=ABC123DEF4
    export ASC_ISSUER_ID=69a6de70-xxxx-47e3-e053-5b8c7c11a4d1
    export ASC_PRIVATE_KEY="$(cat "$HOME/keys/AuthKey_ABC123DEF4.p8")"

    ./gradlew :app:storePublishBinary --track=internal
    ```

Run the same commands with `--publish-dry-run` in a pull request job to check the configuration
without uploading anything. Use the protected production credentials only in jobs that run on the
release branch, as described in [CI Integration](ci.md).

## Limitations

- **Google Play (`PLAY_STORE`) is verified in production:** Both binary publishing
  (`storePublishPlayBinary` — AAB upload, R8 mapping file, release notes, track & status configuration)
  and listing/metadata publishing (`storePublishPlay` — descriptions, release notes, screenshots, feature
  graphic) have been tested end-to-end against Google Play Console and work as expected.
- **App Store Connect (`APP_STORE`) not yet tested against the live store:** App Store Connect behavior
  is currently covered by tests with simulated APIs; real responses may differ, for example in processing
  times or error messages. Endpoints were checked against Apple's OpenAPI specification, not against a
  live account.
- **You build and sign the IPA and the AAB.** Store Studio uploads them; it does not sign or
  export them.
- **Play release notes are limited to 500 characters**, and the upload stops before the network call
  if a locale exceeds that.
- **Screenshots and listing text are not part of these tasks.** Use [Publishing](publishing.md) for
  them.
