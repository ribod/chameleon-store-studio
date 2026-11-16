package dev.ribod.storestudio.core.metadata

import dev.ribod.storestudio.core.publish.appstore.AppStoreLocalizedMetadata
import dev.ribod.storestudio.core.report.model.StoreListingMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Textual metadata targeting a Google Play store listing.
 */
@Serializable
public data class PlayMetadataEntry(
    public val title: String = "",
    public val shortDescription: String = "",
    public val fullDescription: String = "",
    public val releaseNotes: String = "",
) {
    public fun isNotEmpty(): Boolean = title.isNotBlank() || shortDescription.isNotBlank() ||
        fullDescription.isNotBlank() || releaseNotes.isNotBlank()
}

/**
 * Textual metadata targeting an Apple App Store Connect version and app info localization.
 */
@Serializable
public data class AppStoreMetadataEntry(
    public val name: String = "",
    public val subtitle: String = "",
    public val keywords: String = "",
    public val promotionalText: String = "",
    public val description: String = "",
    public val releaseNotes: String = "",
    public val supportUrl: String = "",
    public val marketingUrl: String = "",
) {
    public fun isNotEmpty(): Boolean =
        name.isNotBlank() || subtitle.isNotBlank() || keywords.isNotBlank() ||
            promotionalText.isNotBlank() || description.isNotBlank() ||
            releaseNotes.isNotBlank() || supportUrl.isNotBlank() || marketingUrl.isNotBlank()
}

/**
 * Consolidated store metadata bundle for a single locale tag.
 */
@Serializable
public data class StoreMetadataBundle(
    public val locale: String,
    public val play: PlayMetadataEntry = PlayMetadataEntry(),
    public val appStore: AppStoreMetadataEntry = AppStoreMetadataEntry(),
) {
    public fun isNotEmpty(): Boolean = play.isNotEmpty() || appStore.isNotEmpty()
}

/**
 * App icon of each store as a path relative to the module directory (`null` when none is known).
 * The Gradle plugin resolves them (configured value, else convention) and the IDE only displays them.
 */
@Serializable
public data class StoreIcons(
    public val googlePlay: String? = null,
    public val appStore: String? = null,
)

/**
 * Machine-readable manifest serializing localized listing copy across stores.
 *
 * Conforms to DD-08 specification and is emitted to `build/store/metadata/metadata.json`.
 */
@Serializable
public data class StoreMetadataManifest(
    public val schemaVersion: Int = 1,
    public val defaultLocale: String = "en-US",
    public val locales: Map<String, StoreMetadataBundle> = emptyMap(),
    public val icons: StoreIcons = StoreIcons(),
) {
    public fun toJson(json: Json = DEFAULT_JSON): String = json.encodeToString(this)

    public fun toFile(file: File, json: Json = DEFAULT_JSON) {
        file.parentFile?.mkdirs()
        file.writeText(toJson(json))
    }

    public fun toPlayListingMetadataMap(
        defaultAppName: String = "",
        developerName: String = "Mobile Developer",
        category: String = "Productivity",
    ): Map<String, StoreListingMetadata> = locales.mapValues { (locale, bundle) ->
        val play = bundle.play
        StoreListingMetadata(
            title = play.title.ifBlank { defaultAppName },
            developerName = developerName,
            category = category,
            shortDescription = play.shortDescription,
            fullDescription = play.fullDescription,
            whatsNew = play.releaseNotes.ifBlank { "Bug fixes and performance enhancements." },
        )
    }

    /**
     * Google Play release notes actually written by the author, per locale. Unlike
     * [toPlayListingMetadataMap] no placeholder text is substituted, so nothing is published for
     * locales without notes.
     */
    public fun toPlayReleaseNotesMap(): Map<String, String> =
        locales.mapValues { (_, bundle) -> bundle.play.releaseNotes.trim() }
            .filterValues { it.isNotEmpty() }

    public fun toAppStoreMetadataMap(): Map<String, AppStoreLocalizedMetadata> =
        locales.mapValues { (_, bundle) ->
            val asc = bundle.appStore
            AppStoreLocalizedMetadata(
                description = asc.description.ifBlank { null },
                keywords = asc.keywords.ifBlank { null },
                promotionalText = asc.promotionalText.ifBlank { null },
                whatsNew = asc.releaseNotes.ifBlank { null },
                name = asc.name.ifBlank { null },
                subtitle = asc.subtitle.ifBlank { null },
                supportUrl = asc.supportUrl.ifBlank { null },
                marketingUrl = asc.marketingUrl.ifBlank { null },
            )
        }

    public companion object {
        private val DEFAULT_JSON = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        public fun fromJson(content: String, json: Json = DEFAULT_JSON): StoreMetadataManifest =
            json.decodeFromString(content)

        public fun fromFile(file: File, json: Json = DEFAULT_JSON): StoreMetadataManifest =
            fromJson(file.readText(), json)
    }
}

/**
 * Utility parser extracting Text-as-Code store listing copy from filesystem layouts.
 */
public object StoreMetadataParser {

    public fun parse(
        metadataDir: File?,
        configuredLocales: Collection<String> = emptyList(),
        defaultLocale: String = "en-US",
    ): StoreMetadataManifest {
        if (metadataDir == null || !metadataDir.exists() || !metadataDir.isDirectory) {
            return StoreMetadataManifest(defaultLocale = defaultLocale)
        }

        val allLocales = (configuredLocales + defaultLocale + discoverLocales(metadataDir))
            .filter { LOCALE_PATTERN.matches(it) } // locale becomes a path segment
            .distinct()
            .sorted()

        val rawBundles = mutableMapOf<String, StoreMetadataBundle>()
        for (locale in allLocales) {
            val playDir = metadataDir.resolve("$locale/play")
            val appStoreDir = metadataDir.resolve("$locale/appstore")

            val playEntry = PlayMetadataEntry(
                title = readFile(playDir, "title.txt"),
                shortDescription = readFile(playDir, "short_description.txt"),
                fullDescription = readFile(playDir, "full_description.txt"),
                releaseNotes = readFile(playDir, "release_notes.txt"),
            )

            val appStoreEntry = AppStoreMetadataEntry(
                name = readFile(appStoreDir, "name.txt"),
                subtitle = readFile(appStoreDir, "subtitle.txt"),
                keywords = readFile(appStoreDir, "keywords.txt"),
                promotionalText = readFile(appStoreDir, "promotional_text.txt"),
                description = readFile(appStoreDir, "description.txt"),
                releaseNotes = readFile(appStoreDir, "release_notes.txt"),
                supportUrl = readFile(appStoreDir, "support_url.txt"),
                marketingUrl = readFile(appStoreDir, "marketing_url.txt"),
            )

            val bundle = StoreMetadataBundle(
                locale = locale,
                play = playEntry,
                appStore = appStoreEntry,
            )
            if (bundle.isNotEmpty()) {
                rawBundles[locale] = bundle
            }
        }

        if (rawBundles.isEmpty()) {
            return StoreMetadataManifest(defaultLocale = defaultLocale)
        }

        val defaultBundle = rawBundles[defaultLocale]
        val resolvedBundles = mutableMapOf<String, StoreMetadataBundle>()

        for (locale in allLocales) {
            val raw = rawBundles[locale]
            if (raw == null && defaultBundle == null) continue

            val resolvedPlay = PlayMetadataEntry(
                title = raw?.play?.title?.ifBlank { null }
                    ?: defaultBundle?.play?.title.orEmpty(),
                shortDescription = raw?.play?.shortDescription?.ifBlank { null }
                    ?: defaultBundle?.play?.shortDescription.orEmpty(),
                fullDescription = raw?.play?.fullDescription?.ifBlank { null }
                    ?: defaultBundle?.play?.fullDescription.orEmpty(),
                releaseNotes = raw?.play?.releaseNotes?.ifBlank { null }
                    ?: defaultBundle?.play?.releaseNotes.orEmpty(),
            )

            val resolvedAppStore = AppStoreMetadataEntry(
                name = raw?.appStore?.name?.ifBlank { null }
                    ?: defaultBundle?.appStore?.name.orEmpty(),
                subtitle = raw?.appStore?.subtitle?.ifBlank { null }
                    ?: defaultBundle?.appStore?.subtitle.orEmpty(),
                keywords = raw?.appStore?.keywords?.ifBlank { null }
                    ?: defaultBundle?.appStore?.keywords.orEmpty(),
                promotionalText = raw?.appStore?.promotionalText?.ifBlank { null }
                    ?: defaultBundle?.appStore?.promotionalText.orEmpty(),
                description = raw?.appStore?.description?.ifBlank { null }
                    ?: defaultBundle?.appStore?.description.orEmpty(),
                releaseNotes = raw?.appStore?.releaseNotes?.ifBlank { null }
                    ?: defaultBundle?.appStore?.releaseNotes.orEmpty(),
                supportUrl = raw?.appStore?.supportUrl?.ifBlank { null }
                    ?: defaultBundle?.appStore?.supportUrl.orEmpty(),
                marketingUrl = raw?.appStore?.marketingUrl?.ifBlank { null }
                    ?: defaultBundle?.appStore?.marketingUrl.orEmpty(),
            )

            val resolvedBundle = StoreMetadataBundle(
                locale = locale,
                play = resolvedPlay,
                appStore = resolvedAppStore,
            )
            if (resolvedBundle.isNotEmpty()) {
                resolvedBundles[locale] = resolvedBundle
            }
        }

        return StoreMetadataManifest(
            defaultLocale = defaultLocale,
            locales = resolvedBundles,
        )
    }

    private val LOCALE_PATTERN = Regex("^[A-Za-z0-9_-]+$")

    private fun discoverLocales(dir: File): Set<String> = dir.listFiles().orEmpty()
        .filter { it.isDirectory && isLocaleTag(it.name) }
        .map { it.name }
        .toSet()

    private fun isLocaleTag(name: String): Boolean =
        (name.length in 2..10 && name.contains("-")) || name.length == 2

    private fun readFile(dir: File, filename: String): String {
        val file = dir.resolve(filename)
        return if (file.isFile) file.readText().trim() else ""
    }
}
