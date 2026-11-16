package dev.ribod.storestudio.core.validation

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.metadata.AppStoreMetadataEntry
import dev.ribod.storestudio.core.metadata.PlayMetadataEntry
import dev.ribod.storestudio.core.metadata.StoreMetadataBundle
import dev.ribod.storestudio.core.metadata.StoreMetadataManifest
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataRulesTest {

    private fun createContext(
        manifest: StoreMetadataManifest?,
        targets: Set<StoreTarget> = setOf(StoreTarget.PLAY_STORE, StoreTarget.APP_STORE),
    ): ValidationContext = ValidationContext(
        exportIndex = ExportIndex(assets = emptyList()),
        metadataManifest = manifest,
        configuredTargets = targets,
    )

    @Test
    fun cleanMetadataPassesWithoutViolations() {
        val bundle = StoreMetadataBundle(
            locale = "en-US",
            play = PlayMetadataEntry(
                title = "Chameleon Store Studio",
                shortDescription = "Automated store listing assets at build time.",
                fullDescription = "Full description within the 4000 character limit.",
                releaseNotes = "Performance improvements.",
            ),
            appStore = AppStoreMetadataEntry(
                name = "Store Studio",
                subtitle = "Automated Marketing",
                keywords = "compose,screenshots,marketing,testing",
                promotionalText = "Check out our latest build automation tooling.",
                description = "Complete App Store version description.",
                releaseNotes = "Bug fixes and enhancements.",
                supportUrl = "https://example.com/support",
                marketingUrl = "https://example.com",
            ),
        )
        val manifest = StoreMetadataManifest(locales = mapOf("en-US" to bundle))
        val report = RuleEngine().run(createContext(manifest))

        val metadataViolations = report.violations.filter {
            it.ruleId.startsWith("PLAY_TEXT_") || it.ruleId.startsWith("APPSTORE_TEXT_") ||
                it.ruleId.startsWith("METADATA_POLICY_")
        }
        assertTrue(metadataViolations.isEmpty())
    }

    @Test
    fun flagsPlayTitleExceeding30Chars() {
        val bundle = StoreMetadataBundle(
            locale = "en-US",
            play = PlayMetadataEntry(title = "This title is way too long for Google Play"),
        )
        val manifest = StoreMetadataManifest(locales = mapOf("en-US" to bundle))
        val report = RuleEngine().run(createContext(manifest))

        val violation = report.violations.firstOrNull { it.ruleId == "PLAY_TEXT_TITLE_LENGTH" }
        assertTrue(violation != null)
        assertEquals(Severity.ERROR, violation?.severity)
    }

    @Test
    fun flagsPlayShortDescriptionExceeding80Chars() {
        val bundle = StoreMetadataBundle(
            locale = "en-US",
            play = PlayMetadataEntry(
                shortDescription = "A".repeat(81),
            ),
        )
        val manifest = StoreMetadataManifest(locales = mapOf("en-US" to bundle))
        val report = RuleEngine().run(createContext(manifest))

        val violation = report.violations.firstOrNull { it.ruleId == "PLAY_TEXT_SHORT_DESC_LENGTH" }
        assertTrue(violation != null)
        assertEquals(Severity.ERROR, violation?.severity)
    }

    @Test
    fun flagsAppStoreKeywordsExceeding100BytesUtf8() {
        val multiByteKeywords = "アンドロイド,スクリーンショット,自動化,テスト,ツール,マーケティング,アプリ"
        val byteCount = multiByteKeywords.toByteArray(Charsets.UTF_8).size
        assertTrue(byteCount > 100)

        val bundle = StoreMetadataBundle(
            locale = "ja-JP",
            appStore = AppStoreMetadataEntry(keywords = multiByteKeywords),
        )
        val manifest = StoreMetadataManifest(locales = mapOf("ja-JP" to bundle))
        val report = RuleEngine().run(createContext(manifest))

        val violation = report.violations.firstOrNull {
            it.ruleId == "APPSTORE_TEXT_KEYWORDS_LENGTH"
        }
        assertTrue(violation != null)
        assertEquals(Severity.ERROR, violation?.severity)
    }

    @Test
    fun flagsAppStorePromotionalTextExceeding170Chars() {
        val bundle = StoreMetadataBundle(
            locale = "en-US",
            appStore = AppStoreMetadataEntry(promotionalText = "P".repeat(171)),
        )
        val manifest = StoreMetadataManifest(locales = mapOf("en-US" to bundle))
        val report = RuleEngine().run(createContext(manifest))

        val violation = report.violations.firstOrNull { it.ruleId == "APPSTORE_TEXT_PROMO_LENGTH" }
        assertTrue(violation != null)
        assertEquals(Severity.ERROR, violation?.severity)
    }

    @Test
    fun flagsInvalidSupportAndMarketingUrls() {
        val bundle = StoreMetadataBundle(
            locale = "en-US",
            appStore = AppStoreMetadataEntry(
                supportUrl = "not-a-valid-url",
                marketingUrl = "ftp://invalid-scheme.com",
            ),
        )
        val manifest = StoreMetadataManifest(locales = mapOf("en-US" to bundle))
        val report = RuleEngine().run(createContext(manifest))

        val supportViolation = report.violations.firstOrNull {
            it.ruleId == "APPSTORE_TEXT_SUPPORT_URL"
        }
        val marketingViolation = report.violations.firstOrNull {
            it.ruleId == "APPSTORE_TEXT_MARKETING_URL"
        }
        assertTrue(supportViolation != null)
        assertTrue(marketingViolation != null)
    }

    @Test
    fun warnsOnRankingClaimsAndExcessivePunctuation() {
        val bundle = StoreMetadataBundle(
            locale = "en-US",
            play = PlayMetadataEntry(
                title = "#1 Best App Ever!!!",
            ),
            appStore = AppStoreMetadataEntry(
                name = "Top Free Tool???",
            ),
        )
        val manifest = StoreMetadataManifest(locales = mapOf("en-US" to bundle))
        val report = RuleEngine().run(createContext(manifest))

        val claims = report.violations.filter { it.ruleId == "METADATA_POLICY_CLAIMS" }
        val punctuation = report.violations.filter { it.ruleId == "METADATA_POLICY_PUNCTUATION" }

        assertTrue(claims.isNotEmpty())
        assertTrue(punctuation.isNotEmpty())
        assertEquals(Severity.WARNING, claims.first().severity)
    }

    @Test
    fun warnsOnAppStoreEmojiUsage() {
        val bundle = StoreMetadataBundle(
            locale = "en-US",
            appStore = AppStoreMetadataEntry(
                name = "Store Studio 🚀",
            ),
        )
        val manifest = StoreMetadataManifest(locales = mapOf("en-US" to bundle))
        val report = RuleEngine().run(createContext(manifest))

        val emojiViolation = report.violations.firstOrNull {
            it.ruleId == "APPSTORE_TEXT_EMOJI_FORBIDDEN"
        }
        assertTrue(emojiViolation != null)
        assertEquals(Severity.WARNING, emojiViolation?.severity)
    }

    private fun hasEmojiWarning(name: String): Boolean {
        val bundle =
            StoreMetadataBundle(locale = "en-US", appStore = AppStoreMetadataEntry(name = name))
        val report = RuleEngine().run(
            createContext(StoreMetadataManifest(locales = mapOf("en-US" to bundle))),
        )
        return report.violations.any { it.ruleId == "APPSTORE_TEXT_EMOJI_FORBIDDEN" }
    }

    @Test
    fun emojiRuleFlagsEmojiPresentationButNotOtherScripts() {
        assertTrue(hasEmojiWarning("Love it \u2764\uFE0F"))
        assertTrue(hasEmojiWarning("Made in \uD83C\uDDE9\uD83C\uDDEA"))
        // CJK Extension B ideograph and a mathematical letter: supplementary, but not emoji
        assertFalse(hasEmojiWarning("\uD840\uDC0B Studio"))
        assertFalse(hasEmojiWarning("\uD835\uDC00lpha"))
        assertFalse(hasEmojiWarning("Rated \u2605\u2605\u2605\u2605\u2605"))
    }
}
