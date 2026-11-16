# Troubleshooting

Start with the diagnostics task, which checks the JDK, Android SDK, plugins, KSP and credentials and
suggests a fix for each problem:

```bash
./gradlew :app:storeDoctor
```

`./gradlew :app:storeDump` prints the resolved configuration and the full render matrix.

## Setup

??? question "No screenshots are found"
    - Check that KSP is applied in the module (`id("com.google.devtools.ksp")`) and that the
      functions are top-level, `@Composable`, parameterless and not `private`.
    - The KSP manifest is written to `build/generated/ksp/<variant>/resources/META-INF/store-studio/`.
    - In multi-module projects, apply the plugin to the module that contains the screenshots.

??? question "The KSP plugin isn't found"
    Chameleon Store Studio applies KSP for you only if the KSP plugin is on the build classpath. Declare it in
    the root build with `apply false` (see [Installation](../installation.md)).

??? question "Duplicate screenshot id"
    Ids must be unique in a module and across merged modules. The error names both source files.

## Rendering

??? question "Preview host 'ComposeViewAdapter' is missing"
    `androidx.compose.ui:ui-tooling` must be on the rendered variant's classpath. It's added to
    `debugImplementation` automatically; add it yourself if you render another variant or disabled
    `autoDependencies`.

??? question "A screen renders with Robolectric instead of Layoutlib"
    The validation report has an `ASSET_RENDER_FALLBACK_USED` finding with the Layoutlib error. Typical
    causes are code that reads databases, files, `BuildConfig` from another module or native libraries
    while composing. Pass sample state instead.

??? question "A screenshot fails in both engines"
    The task fails with the root exception from the renderer log. The logs are in
    `build/store/render/work/` and `build/store/render/robolectric-work/`.

??? question "Rendering stalls, times out or runs out of memory"
    Store Studio fails the task with an explanation, and such renders are never cached. Raise
    `rendering.rendererJvmArgs` (`-Xmx3g`), lower `batchSize` or `maxParallelProcesses`, and check free
    disk space.

??? question "Text in another language doesn't appear"
    Each locale renders with your localized resources. Make sure the translation exists in
    `values-<lang>` (Android) or `composeResources/values-<lang>` (Compose Multiplatform), and that the
    locale is registered in `storeStudio { locales { } }`.

## Validation

??? question "Validation fails in a library module"
    Library modules only run per-image rules. If listing rules run, the module is being treated as
    an application; make sure it applies `com.android.library` or the Android KMP library plugin.

??? question "I can't lower the severity of a rule"
    Hard store requirements can't go below `ERROR`, because the store would reject the upload. See the
    *hard* rules in [Validation](../validation.md#rules).

## Baselines and CI

??? question "Images drift between my machine and CI"
    Rendering can differ by a few pixels between operating systems. Compare renders from the same
    machine: in CI, render the base branch on the runner (`-PstoreStudio.diff.renderBase=true`) instead
    of verifying a baseline recorded on a laptop. If you must mix platforms, use `PERCEPTUAL`.

??? question "Git repository is shallow"
    `--against` needs the ref in your clone. Use `fetch-depth: 0` in `actions/checkout`.

??? question "Rendering the base revision fails"
    The base build runs in a temporary worktree; its full log is in
    `build/store/diff/base-render/base-render.log`, and the last lines are shown in the error. If the base
    revision doesn't apply Store Studio yet, every image is reported as added.

??? question "The pull request comment isn't posted"
    Pass `--post-pr-comment` (a flag, without `=true`), give the token `pull-requests: write`, and check that the job runs on
    a `pull_request` event (the PR number is read from it) or pass `--pr-number`.

## IDE plugin

??? question "The tool window is empty"
    It reads `build/store/` of each module. Run `./gradlew storeExport` and click **Refresh**.

??? question "Double-click doesn't open the source"
    Navigation needs the Kotlin plugin and an indexed project. Wait for indexing to finish.
