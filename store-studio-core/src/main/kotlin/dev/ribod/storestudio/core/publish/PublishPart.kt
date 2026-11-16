package dev.ribod.storestudio.core.publish

/**
 * Result of parsing a `--parts` CLI or Gradle property value.
 */
public data class ParsedPublishSelection(
    public val parts: Set<PublishPart>,
    public val includeBinary: Boolean?,
)

/**
 * A independently publishable slice of a store listing. A publish request touches only the
 * parts it selects; everything else on the store is left as it is.
 */
public enum class PublishPart {
    /** Localized texts: titles, descriptions, keywords and other listing copy. */
    LISTING,

    /** Release notes ("What's new"): the Google Play track notes or the App Store `whatsNew`. */
    RELEASE_NOTES,

    /** Visual assets: screenshots and, on Google Play, the feature graphic and app icon. */
    IMAGES,
    ;

    public companion object {
        /** Every part, which is what a full publish uploads. */
        public val ALL: Set<PublishPart> = entries.toSet()

        /**
         * Parses a comma-separated list of publish parts (e.g. `binary,texts,releaseNotes`).
         */
        public fun parseCliParts(raw: String): ParsedPublishSelection {
            val tokens = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) {
                throw IllegalArgumentException(
                    "Unknown publish part '$raw'. Valid values: binary, texts, images, releaseNotes, all.",
                )
            }
            val matchedParts = linkedSetOf<PublishPart>()
            var hasBinary = false
            for (token in tokens) {
                when (token.lowercase().replace("_", "").replace("-", "")) {
                    "binary", "aab", "ipa" -> hasBinary = true

                    "texts", "text", "listing" -> matchedParts += LISTING

                    "images", "image", "screenshots" -> matchedParts += IMAGES

                    "releasenotes", "notes", "whatsnew" -> matchedParts += RELEASE_NOTES

                    "all" -> {
                        matchedParts += ALL
                        hasBinary = true
                    }

                    "listingall", "storefront" -> matchedParts += ALL

                    else -> throw IllegalArgumentException(
                        "Unknown publish part '$token'. Valid values: binary, texts, images, releaseNotes, all.",
                    )
                }
            }
            val includeBinary = when {
                hasBinary -> true
                matchedParts.isNotEmpty() -> false
                else -> null
            }
            return ParsedPublishSelection(
                parts = matchedParts.toSet(),
                includeBinary = includeBinary,
            )
        }
    }
}
