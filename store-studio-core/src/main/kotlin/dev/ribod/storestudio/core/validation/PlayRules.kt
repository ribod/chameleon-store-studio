package dev.ribod.storestudio.core.validation

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.image.ImageProcessor
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.Severity
import dev.ribod.storestudio.model.StoreDeviceSlot
import dev.ribod.storestudio.model.Violation
import java.io.File

/**
 * Collection of Google Play Console screenshot and feature graphic compliance rules.
 */
public object PlayRules {

    public val ALL: List<Rule> = listOf(
        PlayScreenshotFormatRule,
        PlayScreenshotAlphaRule,
        PlayScreenshotFileSizeRule,
        PlayScreenshotMinSideRule,
        PlayScreenshotMaxSideRule,
        PlayScreenshotAspectRatioRule,
        PlayWearSquareRule,
        PlayScreenshotCountMinRule,
        PlayScreenshotCountMaxRule,
        PlayPromoEligibilityRule,
        PlayFeatureGraphicRequiredRule,
        PlayFeatureGraphicDimensionsRule,
        PlayFeatureGraphicFormatRule,
        PlayFeatureGraphicFileSizeRule,
        PlayFeatureGraphicDuplicateRule,
        PlayTabletPartialRule,
    )
}

internal object PlayScreenshotFormatRule : Rule {
    override val id: String = "PLAY_SCREENSHOT_FORMAT"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> = ctx.exportIndex.assets
        .filter { it.target == StoreTarget.PLAY_STORE }
        .filter { it.format != ImageFormat.PNG && it.format != ImageFormat.JPEG }
        .map { asset ->
            Violation(
                ruleId = id,
                severity = defaultSeverity,
                target = asset.target,
                storeLocale = asset.storeLocale,
                deviceSlot = asset.deviceSlot,
                slotKey = asset.slotKey,
                message = "Google Play requires PNG or JPEG format, found: ${asset.format}.",
                hint = "Encode as 24-bit PNG or high-quality JPEG.",
            )
        }
}

internal object PlayScreenshotAlphaRule : Rule {
    override val id: String = "PLAY_SCREENSHOT_ALPHA"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> {
        val violations = mutableListOf<Violation>()
        val playAssets = ctx.exportIndex.assets.filter { it.target == StoreTarget.PLAY_STORE }

        for (asset in playAssets) {
            var hasColorTypeIssue = false
            if (ctx.exportDir != null && asset.format == ImageFormat.PNG) {
                val file = File(ctx.exportDir, asset.file)
                if (file.exists()) {
                    runCatching {
                        val colorType = ImageProcessor.readPngColorType(file)
                        if (colorType != 2) {
                            hasColorTypeIssue = true
                        }
                    }
                }
            }

            if (asset.hasAlpha || hasColorTypeIssue) {
                violations.add(
                    Violation(
                        ruleId = id,
                        severity = defaultSeverity,
                        target = asset.target,
                        storeLocale = asset.storeLocale,
                        deviceSlot = asset.deviceSlot,
                        slotKey = asset.slotKey,
                        message = "Google Play rejects PNGs with transparency or alpha channels.",
                        hint = "Flatten onto an opaque background in StoreStudio export settings.",
                    ),
                )
            }
        }
        return violations
    }
}

internal object PlayScreenshotFileSizeRule : Rule {
    override val id: String = "PLAY_SCREENSHOT_FILE_SIZE"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> {
        return ctx.exportIndex.assets
            .filter {
                it.target == StoreTarget.PLAY_STORE &&
                    it.deviceSlot != StoreDeviceSlot.FEATURE_GRAPHIC
            }
            .mapNotNull { asset ->
                val maxBytes = runCatching { ctx.catalog[asset.deviceSlot].maxBytes }.getOrNull()
                    ?: return@mapNotNull null
                if (asset.bytes <= maxBytes) return@mapNotNull null
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = asset.target,
                    storeLocale = asset.storeLocale,
                    deviceSlot = asset.deviceSlot,
                    slotKey = asset.slotKey,
                    message = "File size of ${asset.bytes} bytes exceeds Google Play " +
                        "limit of $maxBytes bytes.",
                    hint = "Enable storeStudio.export.jpegFallback or reduce image dimensions.",
                )
            }
    }
}

internal object PlayScreenshotMinSideRule : Rule {
    override val id: String = "PLAY_SCREENSHOT_MIN_SIDE"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> = ctx.exportIndex.assets
        .filter {
            it.target == StoreTarget.PLAY_STORE &&
                it.deviceSlot != StoreDeviceSlot.FEATURE_GRAPHIC &&
                minOf(it.widthPx, it.heightPx) < 320
        }
        .map { asset ->
            Violation(
                ruleId = id,
                severity = defaultSeverity,
                target = asset.target,
                storeLocale = asset.storeLocale,
                deviceSlot = asset.deviceSlot,
                slotKey = asset.slotKey,
                message = "Short side ${minOf(
                    asset.widthPx,
                    asset.heightPx,
                )}px is below 320px.",
                hint = "Render at higher density or select a standard physical device spec.",
            )
        }
}

internal object PlayScreenshotMaxSideRule : Rule {
    override val id: String = "PLAY_SCREENSHOT_MAX_SIDE"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> = ctx.exportIndex.assets
        .filter {
            it.target == StoreTarget.PLAY_STORE &&
                it.deviceSlot != StoreDeviceSlot.FEATURE_GRAPHIC &&
                maxOf(it.widthPx, it.heightPx) > 3840
        }
        .map { asset ->
            Violation(
                ruleId = id,
                severity = defaultSeverity,
                target = asset.target,
                storeLocale = asset.storeLocale,
                deviceSlot = asset.deviceSlot,
                slotKey = asset.slotKey,
                message = "Long side ${maxOf(asset.widthPx, asset.heightPx)}px exceeds 3840px.",
                hint = "Render at a supported target resolution within catalog bounds.",
            )
        }
}

internal object PlayScreenshotAspectRatioRule : Rule {
    override val id: String = "PLAY_SCREENSHOT_ASPECT_RATIO"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> = ctx.exportIndex.assets
        .filter {
            it.target == StoreTarget.PLAY_STORE &&
                it.deviceSlot != StoreDeviceSlot.FEATURE_GRAPHIC &&
                maxOf(it.widthPx, it.heightPx) > 2 * minOf(it.widthPx, it.heightPx)
        }
        .map { asset ->
            Violation(
                ruleId = id,
                severity = defaultSeverity,
                target = asset.target,
                storeLocale = asset.storeLocale,
                deviceSlot = asset.deviceSlot,
                slotKey = asset.slotKey,
                message = "Aspect ratio exceeds Google Play 2:1 maximum limit.",
                hint = "Ensure screenshot dimensions maintain an aspect ratio <= 2.0.",
            )
        }
}

internal object PlayWearSquareRule : Rule {
    override val id: String = "PLAY_WEAR_SQUARE"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> = ctx.exportIndex.assets
        .filter {
            it.target == StoreTarget.PLAY_STORE &&
                it.deviceSlot == StoreDeviceSlot.WEAR &&
                (it.widthPx != it.heightPx || it.widthPx < 384)
        }
        .map { asset ->
            Violation(
                ruleId = id,
                severity = defaultSeverity,
                target = asset.target,
                storeLocale = asset.storeLocale,
                deviceSlot = asset.deviceSlot,
                slotKey = asset.slotKey,
                message = "Wear screenshots must be 1:1 square and at least 384x384px.",
                hint = "Render Wear OS screenshots with equal width and height >= 384px.",
            )
        }
}

internal object PlayScreenshotCountMinRule : Rule {
    override val id: String = "PLAY_SCREENSHOT_COUNT_MIN"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.BUCKET

    override fun appliesTo(ctx: ValidationContext): Boolean =
        ctx.configuredTargets.contains(StoreTarget.PLAY_STORE)

    override fun check(ctx: ValidationContext): List<Violation> {
        val violations = mutableListOf<Violation>()
        val phoneAssetsByLocale = ctx.exportIndex.assets
            .filter {
                it.target == StoreTarget.PLAY_STORE &&
                    it.deviceSlot == StoreDeviceSlot.PHONE
            }
            .groupBy { it.storeLocale }

        for (locale in ctx.configuredLocales) {
            val count = phoneAssetsByLocale[locale]?.size ?: 0
            if (count in 1..1) {
                violations.add(
                    Violation(
                        ruleId = id,
                        severity = defaultSeverity,
                        target = StoreTarget.PLAY_STORE,
                        storeLocale = locale,
                        deviceSlot = StoreDeviceSlot.PHONE,
                        message = "Google Play requires at least 2 phone screenshots per " +
                            "locale; found $count for $locale.",
                        hint = "Add another @StoreScreenshot annotated Composable for $locale.",
                    ),
                )
            }
        }
        return violations
    }
}

internal object PlayScreenshotCountMaxRule : Rule {
    override val id: String = "PLAY_SCREENSHOT_COUNT_MAX"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.BUCKET

    override fun check(ctx: ValidationContext): List<Violation> {
        val violations = mutableListOf<Violation>()
        val assetsByBucket = ctx.exportIndex.assets
            .filter {
                it.target == StoreTarget.PLAY_STORE &&
                    it.deviceSlot != StoreDeviceSlot.FEATURE_GRAPHIC
            }
            .groupBy { Triple(it.target, it.storeLocale, it.deviceSlot) }

        for ((bucket, assets) in assetsByBucket) {
            if (assets.size > 8) {
                violations.add(
                    Violation(
                        ruleId = id,
                        severity = defaultSeverity,
                        target = bucket.first,
                        storeLocale = bucket.second,
                        deviceSlot = bucket.third,
                        message = "Google Play allows at most 8 screenshots per " +
                            "slot; found ${assets.size} for ${bucket.third} in ${bucket.second}.",
                        hint = "Remove excess screenshots or restrict them using devices filter.",
                    ),
                )
            }
        }
        return violations
    }
}

internal object PlayPromoEligibilityRule : Rule {
    override val id: String = "PLAY_PROMO_ELIGIBILITY"
    override val defaultSeverity: Severity = Severity.INFO
    override val hard: Boolean = false
    override val scope: RuleScope = RuleScope.BUCKET

    override fun check(ctx: ValidationContext): List<Violation> {
        val violations = mutableListOf<Violation>()
        val phoneAssetsByLocale = ctx.exportIndex.assets
            .filter {
                it.target == StoreTarget.PLAY_STORE &&
                    it.deviceSlot == StoreDeviceSlot.PHONE
            }
            .groupBy { it.storeLocale }

        for ((locale, assets) in phoneAssetsByLocale) {
            val highResCount = assets.count { minOf(it.widthPx, it.heightPx) >= 1080 }
            if (highResCount < 4) {
                violations.add(
                    Violation(
                        ruleId = id,
                        severity = defaultSeverity,
                        target = StoreTarget.PLAY_STORE,
                        storeLocale = locale,
                        deviceSlot = StoreDeviceSlot.PHONE,
                        message = "Store promotion requires at least 4 phone screenshots with " +
                            "min side >= 1080px (found $highResCount in $locale).",
                        hint = "Render at least 4 high-resolution phone screenshots.",
                    ),
                )
            }
        }
        return violations
    }
}

internal object PlayFeatureGraphicRequiredRule : Rule {
    override val id: String = "PLAY_FEATURE_GRAPHIC_REQUIRED"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = false
    override val scope: RuleScope = RuleScope.LOCALE

    override fun appliesTo(ctx: ValidationContext): Boolean =
        ctx.configuredTargets.contains(StoreTarget.PLAY_STORE)

    override fun check(ctx: ValidationContext): List<Violation> {
        val fgAssetsByLocale = ctx.exportIndex.assets
            .filter {
                it.target == StoreTarget.PLAY_STORE &&
                    it.deviceSlot == StoreDeviceSlot.FEATURE_GRAPHIC
            }
            .groupBy { it.storeLocale }

        val violations = mutableListOf<Violation>()
        for (locale in ctx.configuredLocales) {
            val count = fgAssetsByLocale[locale]?.size ?: 0
            if (count == 0) {
                violations.add(
                    Violation(
                        ruleId = id,
                        severity = ctx.featureGraphicMissingSeverity,
                        target = StoreTarget.PLAY_STORE,
                        storeLocale = locale,
                        deviceSlot = StoreDeviceSlot.FEATURE_GRAPHIC,
                        message = "Google Play requires a 1024x500 feature graphic " +
                            "for locale '$locale'.",
                        hint = "Annotate a 1024x500 composable with @StoreFeatureGraphic.",
                    ),
                )
            }
        }
        return violations
    }
}

internal object PlayFeatureGraphicDimensionsRule : Rule {
    override val id: String = "PLAY_FEATURE_GRAPHIC_DIMENSIONS"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> = ctx.exportIndex.assets
        .filter {
            it.target == StoreTarget.PLAY_STORE &&
                it.deviceSlot == StoreDeviceSlot.FEATURE_GRAPHIC &&
                (it.widthPx != 1024 || it.heightPx != 500)
        }
        .map { asset ->
            Violation(
                ruleId = id,
                severity = defaultSeverity,
                target = asset.target,
                storeLocale = asset.storeLocale,
                deviceSlot = asset.deviceSlot,
                slotKey = asset.slotKey,
                message = "Feature graphic must be exactly 1024x500px, " +
                    "found ${asset.widthPx}x${asset.heightPx}px.",
                hint = "Resize canvas to width = 1024px, height = 500px.",
            )
        }
}

internal object PlayFeatureGraphicFormatRule : Rule {
    override val id: String = "PLAY_FEATURE_GRAPHIC_FORMAT"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> = ctx.exportIndex.assets
        .filter {
            it.target == StoreTarget.PLAY_STORE &&
                it.deviceSlot == StoreDeviceSlot.FEATURE_GRAPHIC &&
                ((it.format != ImageFormat.PNG && it.format != ImageFormat.JPEG) || it.hasAlpha)
        }
        .map { asset ->
            Violation(
                ruleId = id,
                severity = defaultSeverity,
                target = asset.target,
                storeLocale = asset.storeLocale,
                deviceSlot = asset.deviceSlot,
                slotKey = asset.slotKey,
                message = "Feature graphic must be opaque PNG or JPEG without alpha.",
                hint = "Flatten onto an opaque background.",
            )
        }
}

internal object PlayFeatureGraphicFileSizeRule : Rule {
    override val id: String = "PLAY_FEATURE_GRAPHIC_FILE_SIZE"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> {
        val maxBytes = ctx.catalog[StoreDeviceSlot.FEATURE_GRAPHIC].maxBytes ?: return emptyList()
        return ctx.exportIndex.assets
            .filter {
                it.target == StoreTarget.PLAY_STORE &&
                    it.deviceSlot == StoreDeviceSlot.FEATURE_GRAPHIC &&
                    it.bytes > maxBytes
            }
            .map { asset ->
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = asset.target,
                    storeLocale = asset.storeLocale,
                    deviceSlot = asset.deviceSlot,
                    slotKey = asset.slotKey,
                    message = "Feature graphic size ${asset.bytes} bytes " +
                        "exceeds the $maxBytes bytes limit.",
                    hint = "Compress feature graphic PNG or convert to JPEG.",
                )
            }
    }
}

internal object PlayFeatureGraphicDuplicateRule : Rule {
    override val id: String = "PLAY_FEATURE_GRAPHIC_DUPLICATE"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.LOCALE

    override fun check(ctx: ValidationContext): List<Violation> {
        val fgAssetsByLocale = ctx.exportIndex.assets
            .filter {
                it.target == StoreTarget.PLAY_STORE &&
                    it.deviceSlot == StoreDeviceSlot.FEATURE_GRAPHIC
            }
            .groupBy { it.storeLocale }

        val violations = mutableListOf<Violation>()
        for ((locale, assets) in fgAssetsByLocale) {
            if (assets.size > 1) {
                violations.add(
                    Violation(
                        ruleId = id,
                        severity = defaultSeverity,
                        target = StoreTarget.PLAY_STORE,
                        storeLocale = locale,
                        deviceSlot = StoreDeviceSlot.FEATURE_GRAPHIC,
                        message = "Multiple feature graphics (${assets.size}) found for $locale.",
                        hint = "Define only one @StoreFeatureGraphic per store locale.",
                    ),
                )
            }
        }
        return violations
    }
}

internal object PlayTabletPartialRule : Rule {
    override val id: String = "PLAY_TABLET_PARTIAL"
    override val defaultSeverity: Severity = Severity.WARNING
    override val hard: Boolean = false
    override val scope: RuleScope = RuleScope.LOCALE

    override fun check(ctx: ValidationContext): List<Violation> {
        val tabletAssets = ctx.exportIndex.assets.filter {
            it.target == StoreTarget.PLAY_STORE &&
                (
                    it.deviceSlot == StoreDeviceSlot.TABLET_7 ||
                        it.deviceSlot == StoreDeviceSlot.TABLET_10
                    )
        }
        if (tabletAssets.isEmpty()) return emptyList()

        val localesWithTablets = tabletAssets.map { it.storeLocale }.toSet()
        val missingLocales = ctx.configuredLocales - localesWithTablets
        if (missingLocales.isEmpty() || localesWithTablets.isEmpty()) return emptyList()

        return missingLocales.map { locale ->
            Violation(
                ruleId = id,
                severity = defaultSeverity,
                target = StoreTarget.PLAY_STORE,
                storeLocale = locale,
                deviceSlot = StoreDeviceSlot.TABLET_10,
                message = "Locale '$locale' has no tablet screenshots while other locales do.",
                hint = "Provide tablet screenshots across all configured locales.",
            )
        }
    }
}
