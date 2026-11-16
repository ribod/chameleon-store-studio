package dev.ribod.storestudio.core.validation

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.model.Severity
import dev.ribod.storestudio.model.Violation
import java.net.URI

/**
 * Collection of store listing textual metadata compliance rules (DD-08).
 */
public object MetadataRules {

    public val ALL: List<Rule> = listOf(
        PlayTextTitleLengthRule,
        PlayTextShortDescLengthRule,
        PlayTextFullDescLengthRule,
        PlayTextReleaseNotesLengthRule,
        AscTextNameLengthRule,
        AscTextSubtitleLengthRule,
        AscTextKeywordsLengthRule,
        AscTextPromoLengthRule,
        AscTextDescLengthRule,
        AscTextWhatsNewLengthRule,
        AscTextSupportUrlRule,
        AscTextMarketingUrlRule,
        MetadataPolicyClaimsRule,
        MetadataPolicyPunctuationRule,
        AscTextEmojiForbiddenRule,
    )
}

internal object PlayTextTitleLengthRule : Rule {
    override val id: String = "PLAY_TEXT_TITLE_LENGTH"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.METADATA

    override fun appliesTo(ctx: ValidationContext): Boolean =
        StoreTarget.PLAY_STORE in ctx.configuredTargets && ctx.metadataManifest != null

    override fun check(ctx: ValidationContext): List<Violation> {
        val manifest = ctx.metadataManifest ?: return emptyList()
        return manifest.locales.values
            .filter { it.play.title.length > 30 }
            .map { bundle ->
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = StoreTarget.PLAY_STORE,
                    storeLocale = bundle.locale,
                    message = "Google Play Title in '${bundle.locale}' exceeds 30 characters " +
                        "(found ${bundle.play.title.length}): '${bundle.play.title}'.",
                    hint = "Shorten 'play/title.txt' to at most 30 characters.",
                )
            }
    }
}

internal object PlayTextShortDescLengthRule : Rule {
    override val id: String = "PLAY_TEXT_SHORT_DESC_LENGTH"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.METADATA

    override fun appliesTo(ctx: ValidationContext): Boolean =
        StoreTarget.PLAY_STORE in ctx.configuredTargets && ctx.metadataManifest != null

    override fun check(ctx: ValidationContext): List<Violation> {
        val manifest = ctx.metadataManifest ?: return emptyList()
        return manifest.locales.values
            .filter { it.play.shortDescription.length > 80 }
            .map { bundle ->
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = StoreTarget.PLAY_STORE,
                    storeLocale = bundle.locale,
                    message = "Google Play Short Description in '${bundle.locale}' exceeds " +
                        "80 characters (found ${bundle.play.shortDescription.length}).",
                    hint = "Shorten 'play/short_description.txt' to at most 80 characters.",
                )
            }
    }
}

internal object PlayTextFullDescLengthRule : Rule {
    override val id: String = "PLAY_TEXT_FULL_DESC_LENGTH"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.METADATA

    override fun appliesTo(ctx: ValidationContext): Boolean =
        StoreTarget.PLAY_STORE in ctx.configuredTargets && ctx.metadataManifest != null

    override fun check(ctx: ValidationContext): List<Violation> {
        val manifest = ctx.metadataManifest ?: return emptyList()
        return manifest.locales.values
            .filter { it.play.fullDescription.length > 4000 }
            .map { bundle ->
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = StoreTarget.PLAY_STORE,
                    storeLocale = bundle.locale,
                    message = "Google Play Full Description in '${bundle.locale}' exceeds " +
                        "4000 characters (found ${bundle.play.fullDescription.length}).",
                    hint = "Shorten 'play/full_description.txt' to at most 4000 characters.",
                )
            }
    }
}

internal object PlayTextReleaseNotesLengthRule : Rule {
    override val id: String = "PLAY_TEXT_RELEASE_NOTES_LENGTH"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.METADATA

    override fun appliesTo(ctx: ValidationContext): Boolean =
        StoreTarget.PLAY_STORE in ctx.configuredTargets && ctx.metadataManifest != null

    override fun check(ctx: ValidationContext): List<Violation> {
        val manifest = ctx.metadataManifest ?: return emptyList()
        return manifest.locales.values
            .filter { it.play.releaseNotes.length > 500 }
            .map { bundle ->
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = StoreTarget.PLAY_STORE,
                    storeLocale = bundle.locale,
                    message = "Google Play Release Notes in '${bundle.locale}' exceed " +
                        "500 characters (found ${bundle.play.releaseNotes.length}).",
                    hint = "Shorten 'play/release_notes.txt' to at most 500 characters.",
                )
            }
    }
}

internal object AscTextNameLengthRule : Rule {
    override val id: String = "APPSTORE_TEXT_NAME_LENGTH"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.METADATA

    override fun appliesTo(ctx: ValidationContext): Boolean =
        StoreTarget.APP_STORE in ctx.configuredTargets && ctx.metadataManifest != null

    override fun check(ctx: ValidationContext): List<Violation> {
        val manifest = ctx.metadataManifest ?: return emptyList()
        return manifest.locales.values
            .filter { it.appStore.name.length > 30 }
            .map { bundle ->
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = StoreTarget.APP_STORE,
                    storeLocale = bundle.locale,
                    message = "App Store App Name in '${bundle.locale}' exceeds 30 characters " +
                        "(found ${bundle.appStore.name.length}): '${bundle.appStore.name}'.",
                    hint = "Shorten 'appstore/name.txt' to at most 30 characters.",
                )
            }
    }
}

internal object AscTextSubtitleLengthRule : Rule {
    override val id: String = "APPSTORE_TEXT_SUBTITLE_LENGTH"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.METADATA

    override fun appliesTo(ctx: ValidationContext): Boolean =
        StoreTarget.APP_STORE in ctx.configuredTargets && ctx.metadataManifest != null

    override fun check(ctx: ValidationContext): List<Violation> {
        val manifest = ctx.metadataManifest ?: return emptyList()
        return manifest.locales.values
            .filter { it.appStore.subtitle.length > 30 }
            .map { bundle ->
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = StoreTarget.APP_STORE,
                    storeLocale = bundle.locale,
                    message = "App Store Subtitle in '${bundle.locale}' exceeds 30 characters " +
                        "(found ${bundle.appStore.subtitle.length}): " +
                        "'${bundle.appStore.subtitle}'.",
                    hint = "Shorten 'appstore/subtitle.txt' to at most 30 characters.",
                )
            }
    }
}

internal object AscTextKeywordsLengthRule : Rule {
    override val id: String = "APPSTORE_TEXT_KEYWORDS_LENGTH"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.METADATA

    override fun appliesTo(ctx: ValidationContext): Boolean =
        StoreTarget.APP_STORE in ctx.configuredTargets && ctx.metadataManifest != null

    override fun check(ctx: ValidationContext): List<Violation> {
        val manifest = ctx.metadataManifest ?: return emptyList()
        return manifest.locales.values
            .map { bundle ->
                val bytes = bundle.appStore.keywords.toByteArray(Charsets.UTF_8).size
                bundle to bytes
            }
            .filter { (_, bytes) -> bytes > 100 }
            .map { (bundle, bytes) ->
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = StoreTarget.APP_STORE,
                    storeLocale = bundle.locale,
                    message = "App Store Keywords in '${bundle.locale}' exceed 100 bytes limit " +
                        "(found $bytes bytes). Multi-byte characters count towards byte total.",
                    hint = "Remove redundant spaces and shorten 'appstore/keywords.txt'.",
                )
            }
    }
}

internal object AscTextPromoLengthRule : Rule {
    override val id: String = "APPSTORE_TEXT_PROMO_LENGTH"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.METADATA

    override fun appliesTo(ctx: ValidationContext): Boolean =
        StoreTarget.APP_STORE in ctx.configuredTargets && ctx.metadataManifest != null

    override fun check(ctx: ValidationContext): List<Violation> {
        val manifest = ctx.metadataManifest ?: return emptyList()
        return manifest.locales.values
            .filter { it.appStore.promotionalText.length > 170 }
            .map { bundle ->
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = StoreTarget.APP_STORE,
                    storeLocale = bundle.locale,
                    message = "App Store Promotional Text in '${bundle.locale}' exceeds " +
                        "170 characters (found ${bundle.appStore.promotionalText.length}).",
                    hint = "Shorten 'appstore/promotional_text.txt' to at most 170 characters.",
                )
            }
    }
}

internal object AscTextDescLengthRule : Rule {
    override val id: String = "APPSTORE_TEXT_DESC_LENGTH"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.METADATA

    override fun appliesTo(ctx: ValidationContext): Boolean =
        StoreTarget.APP_STORE in ctx.configuredTargets && ctx.metadataManifest != null

    override fun check(ctx: ValidationContext): List<Violation> {
        val manifest = ctx.metadataManifest ?: return emptyList()
        return manifest.locales.values
            .filter { it.appStore.description.length > 4000 }
            .map { bundle ->
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = StoreTarget.APP_STORE,
                    storeLocale = bundle.locale,
                    message = "App Store Description in '${bundle.locale}' exceeds " +
                        "4000 characters (found ${bundle.appStore.description.length}).",
                    hint = "Shorten 'appstore/description.txt' to at most 4000 characters.",
                )
            }
    }
}

internal object AscTextWhatsNewLengthRule : Rule {
    override val id: String = "APPSTORE_TEXT_WHATS_NEW_LENGTH"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.METADATA

    override fun appliesTo(ctx: ValidationContext): Boolean =
        StoreTarget.APP_STORE in ctx.configuredTargets && ctx.metadataManifest != null

    override fun check(ctx: ValidationContext): List<Violation> {
        val manifest = ctx.metadataManifest ?: return emptyList()
        return manifest.locales.values
            .filter { it.appStore.releaseNotes.length > 4000 }
            .map { bundle ->
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = StoreTarget.APP_STORE,
                    storeLocale = bundle.locale,
                    message = "App Store Release Notes in '${bundle.locale}' exceed " +
                        "4000 characters (found ${bundle.appStore.releaseNotes.length}).",
                    hint = "Shorten 'appstore/release_notes.txt' to at most 4000 characters.",
                )
            }
    }
}

internal object AscTextSupportUrlRule : Rule {
    override val id: String = "APPSTORE_TEXT_SUPPORT_URL"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.METADATA

    override fun appliesTo(ctx: ValidationContext): Boolean =
        StoreTarget.APP_STORE in ctx.configuredTargets && ctx.metadataManifest != null

    override fun check(ctx: ValidationContext): List<Violation> {
        val manifest = ctx.metadataManifest ?: return emptyList()
        return manifest.locales.values
            .filter {
                it.appStore.supportUrl.isNotBlank() && !isValidHttpUri(it.appStore.supportUrl)
            }
            .map { bundle ->
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = StoreTarget.APP_STORE,
                    storeLocale = bundle.locale,
                    message = "Invalid Support URL '${bundle.appStore.supportUrl}' in " +
                        "'${bundle.locale}' ('appstore/support_url.txt'). " +
                        "Must be a valid http:// or https:// URL.",
                    hint = "Provide a full URL starting with https:// (e.g., https://example.com).",
                )
            }
    }
}

internal object AscTextMarketingUrlRule : Rule {
    override val id: String = "APPSTORE_TEXT_MARKETING_URL"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.METADATA

    override fun appliesTo(ctx: ValidationContext): Boolean =
        StoreTarget.APP_STORE in ctx.configuredTargets && ctx.metadataManifest != null

    override fun check(ctx: ValidationContext): List<Violation> {
        val manifest = ctx.metadataManifest ?: return emptyList()
        return manifest.locales.values
            .filter {
                it.appStore.marketingUrl.isNotBlank() && !isValidHttpUri(it.appStore.marketingUrl)
            }
            .map { bundle ->
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = StoreTarget.APP_STORE,
                    storeLocale = bundle.locale,
                    message = "Invalid Marketing URL '${bundle.appStore.marketingUrl}' in " +
                        "'${bundle.locale}' ('appstore/marketing_url.txt'). " +
                        "Must be a valid http:// or https:// URL.",
                    hint = "Provide a full URL starting with https:// (e.g., https://example.com).",
                )
            }
    }
}

internal object MetadataPolicyClaimsRule : Rule {
    override val id: String = "METADATA_POLICY_CLAIMS"
    override val defaultSeverity: Severity = Severity.WARNING
    override val hard: Boolean = false
    override val scope: RuleScope = RuleScope.METADATA

    private val CLAIMS_REGEX = Regex(
        "\\b(#1|best|top|free|gratis|download now)\\b",
        RegexOption.IGNORE_CASE,
    )

    override fun appliesTo(ctx: ValidationContext): Boolean = ctx.metadataManifest != null

    override fun check(ctx: ValidationContext): List<Violation> {
        val manifest = ctx.metadataManifest ?: return emptyList()
        val violations = mutableListOf<Violation>()

        for (bundle in manifest.locales.values) {
            if (StoreTarget.PLAY_STORE in ctx.configuredTargets) {
                val match = CLAIMS_REGEX.find(bundle.play.title)
                    ?: CLAIMS_REGEX.find(bundle.play.shortDescription)
                if (match != null) {
                    violations.add(
                        Violation(
                            ruleId = id,
                            severity = defaultSeverity,
                            target = StoreTarget.PLAY_STORE,
                            storeLocale = bundle.locale,
                            message = "Google Play copy in '${bundle.locale}' contains ranking " +
                                "or pricing claim: '${match.value}'.",
                            hint = "Stores restrict claims like '#1', 'Best', or 'Free' in titles.",
                        ),
                    )
                }
            }

            if (StoreTarget.APP_STORE in ctx.configuredTargets) {
                val match = CLAIMS_REGEX.find(bundle.appStore.name)
                    ?: CLAIMS_REGEX.find(bundle.appStore.subtitle)
                if (match != null) {
                    violations.add(
                        Violation(
                            ruleId = id,
                            severity = defaultSeverity,
                            target = StoreTarget.APP_STORE,
                            storeLocale = bundle.locale,
                            message = "App Store copy in '${bundle.locale}' contains ranking " +
                                "or pricing claim: '${match.value}'.",
                            hint = "Stores restrict claims like '#1', 'Best', or 'Free' in titles.",
                        ),
                    )
                }
            }
        }

        return violations
    }
}

internal object MetadataPolicyPunctuationRule : Rule {
    override val id: String = "METADATA_POLICY_PUNCTUATION"
    override val defaultSeverity: Severity = Severity.WARNING
    override val hard: Boolean = false
    override val scope: RuleScope = RuleScope.METADATA

    private val EXCESSIVE_PUNCTUATION = Regex("(!{2,}|\\?{2,})")

    override fun appliesTo(ctx: ValidationContext): Boolean = ctx.metadataManifest != null

    override fun check(ctx: ValidationContext): List<Violation> {
        val manifest = ctx.metadataManifest ?: return emptyList()
        val violations = mutableListOf<Violation>()

        for (bundle in manifest.locales.values) {
            if (StoreTarget.PLAY_STORE in ctx.configuredTargets) {
                val match = EXCESSIVE_PUNCTUATION.find(bundle.play.title)
                    ?: EXCESSIVE_PUNCTUATION.find(bundle.play.shortDescription)
                if (match != null) {
                    violations.add(
                        Violation(
                            ruleId = id,
                            severity = defaultSeverity,
                            target = StoreTarget.PLAY_STORE,
                            storeLocale = bundle.locale,
                            message = "Google Play copy in '${bundle.locale}' contains excessive " +
                                "punctuation: '${match.value}'.",
                            hint = "Remove repeated punctuation from titles and descriptions.",
                        ),
                    )
                }
            }

            if (StoreTarget.APP_STORE in ctx.configuredTargets) {
                val match = EXCESSIVE_PUNCTUATION.find(bundle.appStore.name)
                    ?: EXCESSIVE_PUNCTUATION.find(bundle.appStore.subtitle)
                if (match != null) {
                    violations.add(
                        Violation(
                            ruleId = id,
                            severity = defaultSeverity,
                            target = StoreTarget.APP_STORE,
                            storeLocale = bundle.locale,
                            message = "App Store copy in '${bundle.locale}' contains excessive " +
                                "punctuation: '${match.value}'.",
                            hint = "Remove repeated punctuation from titles and subtitles.",
                        ),
                    )
                }
            }
        }

        return violations
    }
}

internal object AscTextEmojiForbiddenRule : Rule {
    override val id: String = "APPSTORE_TEXT_EMOJI_FORBIDDEN"
    override val defaultSeverity: Severity = Severity.WARNING
    override val hard: Boolean = false
    override val scope: RuleScope = RuleScope.METADATA

    // Pictographs, emoticons, transport/map symbols and flags (U+1F000-U+1FAFF), plus the
    // emoji presentation selector and keycap mark that turn plain symbols into emoji (e.g. a
    // heart or digit followed by U+FE0F/U+20E3). Matching every supplementary character instead
    // would flag rare CJK ideographs and mathematical letters in localized listings.
    private val EMOJI_REGEX = Regex("[\\x{1F000}-\\x{1FAFF}\\x{FE0F}\\x{20E3}]")

    override fun appliesTo(ctx: ValidationContext): Boolean =
        StoreTarget.APP_STORE in ctx.configuredTargets && ctx.metadataManifest != null

    override fun check(ctx: ValidationContext): List<Violation> {
        val manifest = ctx.metadataManifest ?: return emptyList()
        val violations = mutableListOf<Violation>()

        for (bundle in manifest.locales.values) {
            val asc = bundle.appStore
            val hasEmoji = EMOJI_REGEX.containsMatchIn(asc.name) ||
                EMOJI_REGEX.containsMatchIn(asc.subtitle) ||
                EMOJI_REGEX.containsMatchIn(asc.keywords)

            if (hasEmoji) {
                violations.add(
                    Violation(
                        ruleId = id,
                        severity = defaultSeverity,
                        target = StoreTarget.APP_STORE,
                        storeLocale = bundle.locale,
                        message = "App Store metadata in '${bundle.locale}' contains emoji " +
                            "symbols, which Apple review guidelines reject.",
                        hint = "Remove emoji characters from name, subtitle, and keywords.",
                    ),
                )
            }
        }

        return violations
    }
}

private fun isValidHttpUri(url: String): Boolean = runCatching {
    val uri = URI(url)
    val validScheme = uri.scheme.equals("http", ignoreCase = true) ||
        uri.scheme.equals("https", ignoreCase = true)
    validScheme && !uri.host.isNullOrBlank()
}.getOrDefault(false)
