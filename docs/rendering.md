# Rendering

`./gradlew storeRender` turns every screenshot × store slot × locale into an image, without an
emulator or device. It runs automatically before `storeExport`.

## Engines

**Layoutlib** (default) is the rendering engine behind Android Studio's Compose previews. Store
Studio runs it in separate JVM processes, each rendering a batch of screenshots. It is fast (about
0.3 seconds per image in the samples) and renders your real resources, fonts and themes.

**Robolectric** with native graphics is the automatic **fallback**. Some code can't run inside
Layoutlib, such as certain Android framework calls, databases or native libraries. Jobs that fail in
Layoutlib are written to `failed_jobs.json` and rendered again by `storeRenderRobolectric`, which
runs a full Android runtime on the JVM. It is slower (about 3 seconds per image) but more
compatible. The fallback writes its results to its own folder (`build/store/render/robolectric/`),
and export, validation and the IDE combine both.

```kotlin
storeStudio {
    rendering {
        engine.set(RenderingEngine.LAYOUTLIB)          // or ROBOLECTRIC to use only Robolectric
        fallbackEngine.set(RenderingEngine.ROBOLECTRIC) // or NONE to fail instead of falling back
    }
}
```

Screenshots rendered by the fallback show a `ROBOLECTRIC` badge in the IDE and an informational
`ASSET_RENDER_FALLBACK_USED` finding in validation, so you can see which screens Layoutlib couldn't
handle.

## Performance and memory

| Option | Default | Effect |
|---|---|---|
| `maxParallelProcesses` | `2` | Renderer JVMs running at the same time. The limit is **shared by all modules** of the build, so parallel builds don't start `modules × 2` processes. |
| `batchSize` | `40` | Maximum screenshots per process. Jobs are split into the fewest batches of at most this size, evenly sized so parallel processes finish together. |
| `rendererJvmArgs` | `-Xmx2g` | Heap of each renderer. Increase it for very large tablet sets. |
| `fallbackJvmArgs` | `-Xmx2g` | Heap of the Robolectric process. |

Renderer JVMs get only an allowlisted environment (paths, locale, Java/Android SDK variables); `STORE_STUDIO_*` and `ROBOLECTRIC_*` variables pass through, CI tokens and credentials do not.

Layoutlib itself is downloaded once and unpacked into Gradle's cache by an artifact transform, so it
is shared by every module and survives `clean`.

`storeRender` is **cacheable**: with the Gradle build cache enabled, CI builds that didn't change any
screen restore the images instead of rendering them. See
[Performance & Caching](guides/performance.md).

## Timeouts and failures

| Option | Default | Effect |
|---|---|---|
| `timeout` | `10m` | Maximum time for one batch. Batches waiting for a free process get their own time. |
| `stallTimeout` | `60s` | A renderer that produces no new image for this long is stopped. |
| `fallbackTimeout` | `5m` | Maximum time for the Robolectric fallback. |

Chameleon Store Studio distinguishes two kinds of failures:

- **Failures in your code** (a class that can't load, an exception while composing) go to the
  fallback engine. If the fallback also fails, the build fails with the root cause from the
  renderer log.
- **Failures caused by the machine** (a renderer that stalls, runs out of memory, runs out of disk
  space or exceeds the timeout) fail the task with a message explaining what to adjust. The
  fallback still runs for those jobs, but a degraded render is never stored in the build cache.

Renderer logs are kept in `build/store/render/work/` (Layoutlib) and
`build/store/render/robolectric-work/` (Robolectric).

## Library modules

Android library modules don't link their resources by default, so they can't be rendered on their
own. With `rendering.configureLibraryResources` (default `true`), Store Studio enables
`includeAndroidResources` for the library's unit tests and renders with the linked resources. Set it
to `false` to leave your unit test configuration untouched; the library's screenshots are then
rendered by the app module that depends on it. See [Multi-Module Projects](multi-module.md).

## Troubleshooting

??? question "Preview host 'ComposeViewAdapter' is missing from the render classpath"
    Layoutlib needs `androidx.compose.ui:ui-tooling` in the rendered variant. Store Studio adds it to
    `debugImplementation` automatically; if you render another variant or disabled
    `autoDependencies`, add it yourself.

??? question "A screen always falls back to Robolectric"
    Look for `ASSET_RENDER_FALLBACK_USED` in the validation report, or the `ROBOLECTRIC` badge in the
    IDE. The reason Layoutlib failed is in the render index diagnostics. Common causes are code that
    touches databases, files or native libraries while composing. Passing sample state instead of
    real repositories usually fixes it.

??? question "Rendering stops with an out-of-memory or stall error"
    Raise `rendererJvmArgs` (for example `-Xmx3g`), lower `batchSize`, or lower
    `maxParallelProcesses` on machines with little memory.
