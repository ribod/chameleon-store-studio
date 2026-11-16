package dev.ribod.storestudio.model

import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Single composable screen entry extracted by compile-time symbol processing.
 *
 * @property kind Discriminator indicating screenshot or feature graphic.
 * @property id Unique semantic identifier within the compilation module (`^[a-z0-9_-]+$`).
 * @property order Relative sequencing index within the store listing.
 * @property title Optional human-readable localized title or description key.
 * @property functionFqn JVM-facing Fully Qualified Name Layoutlib calls (`<package>.<FacadeClassKt>.<method>`).
 * @property packageName Package enclosing the annotated composable.
 * @property functionName Simple name of the composable function.
 * @property devices Target device families. Empty indicates inherit global Gradle defaults during aggregation.
 * @property targets Target app stores. Empty indicates inherit global Gradle defaults during aggregation.
 * @property locales Target locales. Empty indicates inherit global Gradle defaults during aggregation.
 * @property source Relative source file path and line location for navigation and error tracking.
 */
@Serializable
public data class ManifestEntry(
    public val kind: EntryKind,
    public val id: String,
    public val order: Int = 0,
    public val title: String? = null,
    public val functionFqn: String,
    public val packageName: String,
    public val functionName: String,
    public val devices: List<DeviceFamily> = emptyList(),
    public val targets: List<StoreTarget> = emptyList(),
    public val locales: List<String> = emptyList(),
    public val source: SourceLocation,
) {
    init {
        require(id.isNotBlank()) { "Entry id must not be blank" }
        require(order >= 0) { "Entry order cannot be negative: $order" }
        require(functionFqn.isNotBlank()) { "functionFqn must not be blank" }
        require(functionName.isNotBlank()) { "functionName must not be blank" }
    }
}

/**
 * Per-module store manifest contract produced by KSP (`META-INF/store-studio/store-manifest.json`).
 *
 * Conforms to schema version 1 defined in data-contracts.md.
 *
 * @property schemaVersion Integer schema version (currently 1).
 * @property module Gradle module path (e.g. `":feature:onboarding"`).
 * @property generator Generator identity and version string (e.g. `"store-studio-ksp/0.1.0"`).
 * @property entries Extracted composable entries sorted deterministically by [ManifestEntry.id].
 */
@Serializable
public data class StoreManifest(
    public val schemaVersion: Int = 1,
    public val module: String,
    public val generator: String,
    public val entries: List<ManifestEntry>,
) {
    public companion object {
        /** Canonical JSON serializer conforming to data contract principles. */
        public val json: Json = ManifestJson

        /**
         * Parses a serialized JSON string into a [StoreManifest], verifying schema version compatibility.
         */
        public fun decodeFromString(string: String): StoreManifest {
            val manifest = json.decodeFromString<StoreManifest>(string)
            require(manifest.schemaVersion <= 1) {
                "Unsupported store-manifest.json schema version ${manifest.schemaVersion}. " +
                    "Supported version is <= 1. Please upgrade Store Studio."
            }
            return manifest
        }

        /**
         * Serializes this [StoreManifest] into deterministic canonical JSON format.
         */
        public fun encodeToString(manifest: StoreManifest): String = json.encodeToString(manifest)
    }
}
