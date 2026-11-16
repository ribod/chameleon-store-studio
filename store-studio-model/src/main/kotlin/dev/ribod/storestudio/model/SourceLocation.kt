package dev.ribod.storestudio.model

import kotlinx.serialization.Serializable

/**
 * Source code reference capturing the relative file path and line number of an annotated composable.
 *
 * Utilized by the IDE plugin for double-click source navigation and error diagnostics.
 *
 * @property path Project-relative file path with forward slashes (e.g. `feature/onboarding/src/main/.../Onboarding.kt`).
 * @property line 1-based source code line number.
 */
@Serializable
public data class SourceLocation(public val path: String, public val line: Int) {
    init {
        require(path.isNotBlank()) { "Source path must not be blank" }
        require(line >= 1) { "Source line must be >= 1: $line" }
    }
}
