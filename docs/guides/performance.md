# Performance & Caching

## Measured times

Measured on a **MacBook Air (Apple M3)** with the two [samples](samples.md), running
`:app:storeExport :app:storeValidate :app:storeVerify`:

| Scenario | KotlinConf (50 assets) | Now in Android (58 assets) |
|---|---|---|
| Cold run (no prior caches) | ~71 s | ~71 s |
| `clean`, restored from build cache | ~6 s | ~3 s |

Rendering costs about **0.3 s per image** with Layoutlib, and about **3 s per image** for screens that
need the Robolectric fallback. Most of the clean-build time is compiling the app itself, which Gradle
would do anyway.

## What makes it fast

- **Incremental tasks.** Each step declares its inputs and outputs; unchanged steps are skipped.
- **Build cache.** `storeRender` is cacheable, so CI runs and branch switches reuse renders.
- **Configuration cache.** Supported by every task.
- **Shared Layoutlib.** Downloaded once and unpacked by an artifact transform into the Gradle user
  home, shared by all modules and projects.
- **Batching.** Each renderer JVM renders up to `batchSize` images, amortizing startup.
- **Build-wide process limit.** `maxParallelProcesses` is shared across modules, so parallel builds
  don't exhaust memory.
- **Parallel export.** Image encoding and hashing run in parallel.

## Tuning

| Situation | Change |
|---|---|
| Many images and plenty of RAM | Raise `rendering.maxParallelProcesses` to 3–4. |
| CI runner with little memory | `maxParallelProcesses = 1`, smaller `batchSize`. |
| Out-of-memory in renderers | Raise `rendererJvmArgs` to `-Xmx3g`. |
| Slow fallback renders | Find the screens with the `ROBOLECTRIC` badge and make them render in Layoutlib (usually by passing sample state). |
| Only working on one feature | Run the task on that module: `./gradlew :feature:foryou:storeExport`. |

Enable the build cache in `gradle.properties` to benefit from cached renders:

```properties title="gradle.properties"
org.gradle.caching=true
org.gradle.configuration-cache=true
```
