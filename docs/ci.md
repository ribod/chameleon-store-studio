# CI Integration

In CI, Chameleon Store Studio acts as a quality gate: it validates the assets and reports screenshot changes on
every pull request.

## `storeCi`

```bash
./gradlew :app:storeCi
```

`storeCi` runs validation and the diff report, prints a summary, and then:

- fails if validation found errors (`ci.failOnValidationErrors`, default `true`);
- fails if images changed, only when `ci.failOnDrift` is `true` (default `false`, since changes in a
  pull request are usually intentional);
- appends the diff summary to the **GitHub Actions job summary** when `GITHUB_STEP_SUMMARY` is set;
- posts or updates a **sticky pull request comment** when `ci.postPrComment` is `true`.

Options on the command line:

```bash
./gradlew :app:storeCi --post-pr-comment --fail-on-drift
```

| Option | Description |
|---|---|
| `--fail-on-drift` / `--no-fail-on-drift` | Fail when images changed. |
| `--fail-on-validation` / `--no-fail-on-validation` | Fail on validation errors. |
| `--post-pr-comment` | Post the summary as a pull request comment. |
| `--pr-number=<n>` | Pull or merge request number, if it can't be detected. |

Boolean options are flags: write `--post-pr-comment`, not `--post-pr-comment=true`.

**Choosing the base.** `storeCi` runs `storeDiff` as a dependency, so pass the base with Gradle
properties (command-line options only reach the task you name):

```bash
# Render the base branch on this runner (recommended)
./gradlew :app:storeCi -PstoreStudio.diff.renderBase=true -PstoreStudio.diff.against=origin/main

# Or compare with an export of the base branch downloaded from a previous run
./gradlew :app:storeCi -PstoreStudio.diff.baseExport=base-export
```

Without them, `storeCi` compares against the committed baseline, if there is one. See
[Baselines & Visual Diff](baselines-and-diff.md#compare-on-the-same-machine).

`storeCiCheck` is a lifecycle alias for `storeCi`.

## Pull request comments

The comment contains the app name, the change counts and a table of modified images. It is updated
in place on every push, identified by `ci.commentMarker`, which is different for each project: two
apps in the same repository keep two separate comments.

| Platform | Detection | Token |
|---|---|---|
| GitHub Actions | `GITHUB_REPOSITORY`, `GITHUB_EVENT_PATH`, `GITHUB_REF` | `ci.githubToken` or `GITHUB_TOKEN` (needs `pull-requests: write`) |
| GitLab CI | `CI_PROJECT_ID`, `CI_MERGE_REQUEST_IID`, `CI_API_V4_URL` | `ci.gitlabToken`, `GITLAB_TOKEN` or `CI_JOB_TOKEN` |

## GitHub Actions example

This workflow renders both the pull request and its base branch on the same Linux runner and compares
them exactly. Nobody has to record or commit a baseline: once the pull request is merged, the new
`main` is the base for the next one.

```yaml title=".github/workflows/store.yml"
name: Store assets

on:
  pull_request:

permissions:
  contents: read
  pull-requests: write

jobs:
  store:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
        with:
          fetch-depth: 0   # the base branch must be in the clone

      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 17

      - uses: gradle/actions/setup-gradle@v6

      - name: Validate and compare with the base branch
        run: >
          ./gradlew :app:storeCi --post-pr-comment
          -PstoreStudio.diff.renderBase=true
          -PstoreStudio.diff.against=origin/${{ github.base_ref }}
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - uses: actions/upload-artifact@v6
        if: always()
        with:
          name: store-reports
          path: |
            app/build/store/reports/
            app/build/store/export/
```

Rendering the base costs one extra build of the module, but most of it comes from the Gradle build
cache. `setup-gradle` writes the cache on `main` and reads it on pull requests, so add a workflow (or a
job) that builds `main` on push, for example running `./gradlew :app:storeExport`.

??? tip "Reuse the export of `main` instead of rendering it"
    If `main` uploads its export as an artifact, pull requests can download it and skip the base
    render:

    ```yaml
    # On push to main
    - run: ./gradlew :app:storeExport
    - uses: actions/upload-artifact@v6
      with:
        name: store-export-main
        path: app/build/store/export/

    # On pull requests, before storeCi (needs `actions: read` permission)
    - name: Download the export of main
      env:
        GH_TOKEN: ${{ github.token }}
      run: |
        run_id=$(gh run list --workflow store.yml --branch main --event push \
          --status success --limit 1 --json databaseId --jq '.[0].databaseId')
        gh run download "$run_id" --name store-export-main --dir base-export
    - run: ./gradlew :app:storeCi --post-pr-comment -PstoreStudio.diff.baseExport=base-export
    ```

    The export comes from the latest `main`, which can be newer than the pull request's base. If the
    branch is behind `main`, the diff also shows `main`'s newer changes reversed. Rendering the base
    branch always compares against the exact merge base.

## Caching

- **Build cache.** `storeRender` is cacheable, so with the Gradle build cache (for example
  `setup-gradle`'s cache, or a remote cache) a pull request that didn't touch any screen restores the
  images instead of rendering them. Renders affected by machine problems (timeouts, out of memory)
  are never cached.
- **Configuration cache.** All tasks support it.
- **Layoutlib** is downloaded once per Gradle user home and reused.

See [Performance & Caching](guides/performance.md) for measured times.

## Rendering consistency

Renders are deterministic on the same machine, but macOS, Windows and Linux can differ by a few
pixels. Compare renders made on the same runner (the workflow above) and keep the default `STRICT`
comparison. A baseline recorded on a laptop and verified on Linux CI is what `PERCEPTUAL` mode exists
for, at the cost of missing some small changes. See
[What counts as a change](baselines-and-diff.md#what-counts-as-a-change).

## Uploading with other tools

`storeExport` also writes the listing texts to `build/store/export-metadata/`, including
`play_store/release-notes/whatsnew-<locale>` files, so a CI step from another tool can upload them
without Store Studio's publishing:

```yaml title=".github/workflows/release.yml"
- run: ./gradlew :app:storeExport
- name: Upload release notes with your Play upload step
  uses: your-org/your-play-upload-action@v1
  with:
    whatsNewDirectory: app/build/store/export-metadata/play_store/release-notes
```

To publish with Store Studio instead, run a narrow task such as
`./gradlew :app:storePublishPlayReleaseNotes --track=production`
(see [Publishing](publishing.md#publish-only-part-of-a-listing)).
