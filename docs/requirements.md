# Requirements

## Build toolchain

| Tool | Version | Notes |
|---|---|---|
| JDK | 17 or newer | Required by Layoutlib and Compose rendering. The plugin fails early with a clear message on older JDKs. |
| Gradle | 9.3 or newer | Configuration cache and build cache are supported. |
| Kotlin | 2.4 | Kotlin JVM, Android or Multiplatform. |
| Android Gradle Plugin | 9.1 | Application, library, or the Android KMP library plugin (`com.android.kotlin.multiplatform.library`). |
| KSP | 2.3 | Discovers annotated composables at compile time. |
| Compose | Jetpack Compose or Compose Multiplatform | Screenshots are regular composables. |
| Android min SDK | 24 | Of the module that contains the screenshots. |

!!! info "Tested versions"
    The versions above are the ones Chameleon Store Studio is built and tested with. Older Kotlin, AGP
    or KSP versions may work but are not verified yet; see [Compatibility](compatibility.md).

## IDE plugin

- Android Studio or IntelliJ IDEA **2024.2 or newer** (build 242+).
- Verified against IntelliJ IDEA 2024.2 through 2026.2 and Android Studio 2024.2 through 2026.2.
- The Kotlin plugin is optional; it enables jumping from an image to its composable.

## Disk and memory

- **Layoutlib** is downloaded once and unpacked into Gradle's cache (about 175 MB per Layoutlib
  version), shared by every module and project.
- **Renderer processes** use up to 2 GB of heap each by default, with at most two running at the same
  time across the whole build. Both values are configurable; see [Rendering](rendering.md).
- **Gradle daemon:** Store Studio itself adds only a few megabytes of classes to the daemon. Typical
  Android and KMP builds that use KSP run fine with Gradle's default memory settings.
