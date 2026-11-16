# Compatibility

## Build tools

Chameleon Store Studio 0.1.0 is tested against two stacks, the oldest supported and the latest:

| Tool | Minimum tested | Latest tested |
|---|---|---|
| JDK | 21 (17 is the minimum) | 21 |
| Gradle | 9.3.1 | 9.6.0 |
| Android Gradle Plugin | 9.1.1 | 9.4.1 |
| Kotlin | 2.4.10 | 2.4.20 |
| KSP | 2.3.12 | 2.3.12 |

AGP 9.4 requires Gradle 9.6. The repository's own build uses Compose Multiplatform 1.11.0.

Verified against a standalone consumer project with both stacks. Other versions may work but
haven't been verified. Reports are welcome.

No special buildscript setup is needed.

### Isolated projects

Not supported yet. Wiring Compose Multiplatform resources of dependency modules reads the other
project's `buildDirectory` and tasks through `findProject`, which isolated projects forbids.

### Android plugins

| Plugin | Supported |
|---|---|
| `com.android.application` | Yes |
| `com.android.library` | Yes |
| `com.android.kotlin.multiplatform.library` | Yes |
| Kotlin Multiplatform with the legacy `androidTarget()` | Not tested |

## IDE plugin

The plugin is checked with the IntelliJ Plugin Verifier, run locally with
`./gradlew -p store-studio-idea verifyPlugin`, against:

| IDE | Versions |
|---|---|
| IntelliJ IDEA | 2024.2 – 2026.2 |
| Android Studio | 2024.2 – 2026.2 |

## Stores

| Store | Assets | Publishing |
|---|---|---|
| Google Play | Phone, 7" and 10" tablet, Wear OS, feature graphic | Supported |
| App Store | iPhone 6.9", iPhone 6.5", iPad 13" | Experimental, not tested against the real store |
