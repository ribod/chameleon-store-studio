package dev.ribod.storestudio.gradle.internal

/** Env vars renderer JVMs may inherit; everything else (CI tokens, secrets) is dropped. */
internal val ALLOWED_ENV_VARS = setOf(
    "PATH", "HOME", "USER", "USERNAME", "LOGNAME", "SHELL", "LANG", "TZ",
    "TMPDIR", "TMP", "TEMP", "JAVA_HOME", "GRADLE_USER_HOME", "ANDROID_HOME",
    "ANDROID_SDK_ROOT", "ANDROID_USER_HOME", "DISPLAY", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS",
    // Windows
    "SystemRoot", "SYSTEMROOT", "windir", "COMSPEC", "PATHEXT", "APPDATA", "LOCALAPPDATA",
    "USERPROFILE", "PROGRAMDATA", "ProgramFiles", "NUMBER_OF_PROCESSORS",
    "PROCESSOR_ARCHITECTURE",
)

internal val ALLOWED_ENV_PREFIXES = listOf("LC_", "XDG_", "ROBOLECTRIC_", "STORE_STUDIO_")

/** Renderer JVMs run user app code: give them an allowlisted environment only. */
internal fun ProcessBuilder.scrubCredentials(): ProcessBuilder = apply {
    environment().keys.retainAll {
        it in ALLOWED_ENV_VARS || ALLOWED_ENV_PREFIXES.any(it::startsWith)
    }
}
