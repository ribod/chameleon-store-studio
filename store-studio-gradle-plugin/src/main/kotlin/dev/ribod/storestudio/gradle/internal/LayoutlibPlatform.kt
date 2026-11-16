package dev.ribod.storestudio.gradle.internal

/**
 * Utility determining the native host platform classifier for Layoutlib binaries.
 *
 * Layoutlib publishes native runtime artifacts with OS-specific classifiers
 * (`mac-arm`, `mac`, `linux`, `win`) containing platform C++ rendering engines.
 */
internal object LayoutlibPlatform {

    /**
     * Resolves the host operating system classifier string.
     *
     * Inspects standard system properties to differentiate Apple Silicon ARM from Intel x86_64,
     * as well as standard Linux and Windows environments.
     *
     * @param osName System property `os.name`.
     * @param osArch System property `os.arch`.
     * @return Layoutlib Maven classifier string (`mac-arm`, `mac`, `linux`, or `win`).
     */
    fun osClassifier(
        osName: String = System.getProperty("os.name") ?: "",
        osArch: String = System.getProperty("os.arch") ?: "",
    ): String {
        val os = osName.lowercase()
        val arch = osArch.lowercase()

        // Match macOS platforms, distinguishing Apple Silicon from Intel
        if ("mac" in os || "darwin" in os) {
            return if ("aarch64" in arch || "arm" in arch) "mac-arm" else "mac"
        }

        // Match Windows architectures
        if ("win" in os) {
            return "win"
        }

        // Default to Linux x86_64 for Linux and Unix-like environments
        return "linux"
    }
}
