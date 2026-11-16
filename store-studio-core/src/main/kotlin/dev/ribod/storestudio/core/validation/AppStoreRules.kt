package dev.ribod.storestudio.core.validation

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.Severity
import dev.ribod.storestudio.model.StoreDeviceSlot
import dev.ribod.storestudio.model.Violation

/**
 * Collection of Apple App Store Connect screenshot compliance rules.
 */
public object AppStoreRules {

    public val ALL: List<Rule> = listOf(
        AscScreenshotFormatRule,
        AscScreenshotAlphaRule,
        AscScreenshotColorSpaceRule,
        AscScreenshotDimensionsRule,
        AscScreenshotFileSizeRule,
        AscScreenshotCountMinRule,
        AscScreenshotCountMaxRule,
        AscOrientationConsistencyRule,
        AscIphone69RequiredRule,
        AscIpad13RequiredRule,
        AscIpadUnexpectedRule,
    )
}

internal object AscScreenshotFormatRule : Rule {
    override val id: String = "ASC_SCREENSHOT_FORMAT"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> = ctx.exportIndex.assets
        .filter { it.target == StoreTarget.APP_STORE }
        .filter { it.format != ImageFormat.PNG && it.format != ImageFormat.JPEG }
        .map { asset ->
            Violation(
                ruleId = id,
                severity = defaultSeverity,
                target = asset.target,
                storeLocale = asset.storeLocale,
                deviceSlot = asset.deviceSlot,
                slotKey = asset.slotKey,
                message = "App Store Connect requires PNG or JPEG format, found: ${asset.format}.",
                hint = "Encode screenshot as 24-bit PNG or JPEG.",
            )
        }
}

internal object AscScreenshotAlphaRule : Rule {
    override val id: String = "ASC_SCREENSHOT_ALPHA"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> = ctx.exportIndex.assets
        .filter { it.target == StoreTarget.APP_STORE && it.hasAlpha }
        .map { asset ->
            Violation(
                ruleId = id,
                severity = defaultSeverity,
                target = asset.target,
                storeLocale = asset.storeLocale,
                deviceSlot = asset.deviceSlot,
                slotKey = asset.slotKey,
                message = "App Store Connect strictly rejects images containing alpha channels.",
                hint = "Flatten onto an opaque background in StoreStudio export options.",
            )
        }
}

internal object AscScreenshotColorSpaceRule : Rule {
    override val id: String = "ASC_SCREENSHOT_COLOR_SPACE"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> = ctx.exportIndex.assets
        .filter { it.target == StoreTarget.APP_STORE && it.colorSpace != "sRGB" }
        .map { asset ->
            Violation(
                ruleId = id,
                severity = defaultSeverity,
                target = asset.target,
                storeLocale = asset.storeLocale,
                deviceSlot = asset.deviceSlot,
                slotKey = asset.slotKey,
                message = "Color profile '${asset.colorSpace}' is not sRGB.",
                hint = "Convert color space to sRGB before exporting.",
            )
        }
}

internal object AscScreenshotDimensionsRule : Rule {
    override val id: String = "ASC_SCREENSHOT_DIMENSIONS"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    private val IPHONE_69_SIZES = setOf(
        Pair(1320, 2868),
        Pair(2868, 1320),
        Pair(1290, 2796),
        Pair(2796, 1290),
        Pair(1260, 2736),
        Pair(2736, 1260),
    )

    private val IPHONE_65_SIZES = setOf(
        Pair(1284, 2778),
        Pair(2778, 1284),
        Pair(1242, 2688),
        Pair(2688, 1242),
    )

    private val IPAD_13_SIZES = setOf(
        Pair(2064, 2752),
        Pair(2752, 2064),
        Pair(2048, 2732),
        Pair(2732, 2048),
    )

    override fun check(ctx: ValidationContext): List<Violation> = ctx.exportIndex.assets
        .filter { it.target == StoreTarget.APP_STORE }
        .filterNot { asset ->
            val size = Pair(asset.widthPx, asset.heightPx)
            when (asset.deviceSlot) {
                StoreDeviceSlot.IPHONE_6_9 -> size in IPHONE_69_SIZES
                StoreDeviceSlot.IPHONE_6_5 -> size in IPHONE_65_SIZES
                StoreDeviceSlot.IPAD_13 -> size in IPAD_13_SIZES
                else -> false
            }
        }
        .map { asset ->
            Violation(
                ruleId = id,
                severity = defaultSeverity,
                target = asset.target,
                storeLocale = asset.storeLocale,
                deviceSlot = asset.deviceSlot,
                slotKey = asset.slotKey,
                message = "Dimensions ${asset.widthPx}x${asset.heightPx} are not an accepted " +
                    "Apple App Store size for slot ${asset.deviceSlot}.",
                hint = "Render with standard catalog dimensions for ${asset.deviceSlot}.",
            )
        }
}

internal object AscScreenshotFileSizeRule : Rule {
    override val id: String = "ASC_SCREENSHOT_FILE_SIZE"
    override val defaultSeverity: Severity = Severity.WARNING
    override val hard: Boolean = false
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> = ctx.exportIndex.assets
        .filter { it.target == StoreTarget.APP_STORE }
        .mapNotNull { asset ->
            val maxBytes =
                runCatching { ctx.catalog[asset.deviceSlot].softMaxBytes }.getOrNull()
                    ?: return@mapNotNull null
            if (asset.bytes <= maxBytes) return@mapNotNull null
            Violation(
                ruleId = id,
                severity = defaultSeverity,
                target = asset.target,
                storeLocale = asset.storeLocale,
                deviceSlot = asset.deviceSlot,
                slotKey = asset.slotKey,
                message = "File size ${asset.bytes} bytes exceeds the recommended App Store " +
                    "limit of $maxBytes bytes (Apple publishes no hard limit).",
                hint = "Enable storeStudio.export.jpegFallback or lower source canvas size.",
            )
        }
}

internal object AscScreenshotCountMinRule : Rule {
    override val id: String = "ASC_SCREENSHOT_COUNT_MIN"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.SET

    override fun check(ctx: ValidationContext): List<Violation> {
        val sets = ctx.exportIndex.assets
            .filter { it.target == StoreTarget.APP_STORE }
            .groupBy { Pair(it.storeLocale, it.deviceSlot) }

        val violations = mutableListOf<Violation>()
        for ((key, assets) in sets) {
            if (assets.isEmpty()) {
                violations.add(
                    Violation(
                        ruleId = id,
                        severity = defaultSeverity,
                        target = StoreTarget.APP_STORE,
                        storeLocale = key.first,
                        deviceSlot = key.second,
                        message = "App Store requires at least 1 screenshot per present set.",
                        hint = "Add at least one screenshot for ${key.second} in ${key.first}.",
                    ),
                )
            }
        }
        return violations
    }
}

internal object AscScreenshotCountMaxRule : Rule {
    override val id: String = "ASC_SCREENSHOT_COUNT_MAX"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.SET

    override fun check(ctx: ValidationContext): List<Violation> {
        val sets = ctx.exportIndex.assets
            .filter { it.target == StoreTarget.APP_STORE }
            .groupBy { Pair(it.storeLocale, it.deviceSlot) }

        val violations = mutableListOf<Violation>()
        for ((key, assets) in sets) {
            if (assets.size > 10) {
                violations.add(
                    Violation(
                        ruleId = id,
                        severity = defaultSeverity,
                        target = StoreTarget.APP_STORE,
                        storeLocale = key.first,
                        deviceSlot = key.second,
                        message = "App Store allows at most 10 screenshots per display set; " +
                            "found ${assets.size} for ${key.second} in ${key.first}.",
                        hint = "Remove excess screenshots to stay within the 10-item limit.",
                    ),
                )
            }
        }
        return violations
    }
}

internal object AscOrientationConsistencyRule : Rule {
    override val id: String = "ASC_ORIENTATION_CONSISTENCY"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.SET

    override fun check(ctx: ValidationContext): List<Violation> {
        val sets = ctx.exportIndex.assets
            .filter { it.target == StoreTarget.APP_STORE }
            .groupBy { Pair(it.storeLocale, it.deviceSlot) }

        val violations = mutableListOf<Violation>()
        for ((key, assets) in sets) {
            val portraitCount = assets.count { it.widthPx < it.heightPx }
            val landscapeCount = assets.count { it.widthPx > it.heightPx }

            if (portraitCount > 0 && landscapeCount > 0) {
                violations.add(
                    Violation(
                        ruleId = id,
                        severity = defaultSeverity,
                        target = StoreTarget.APP_STORE,
                        storeLocale = key.first,
                        deviceSlot = key.second,
                        message = "App Store Connect requires all screenshots in a display set " +
                            "to share the same orientation (found $portraitCount portrait and " +
                            "$landscapeCount landscape in ${key.first} for ${key.second}).",
                        hint = "Render either all portrait or all landscape screenshots in set.",
                    ),
                )
            }
        }
        return violations
    }
}

internal object AscIphone69RequiredRule : Rule {
    override val id: String = "ASC_IPHONE_69_REQUIRED"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.LOCALE

    override fun appliesTo(ctx: ValidationContext): Boolean =
        ctx.configuredTargets.contains(StoreTarget.APP_STORE)

    override fun check(ctx: ValidationContext): List<Violation> {
        val assetsByLocale = ctx.exportIndex.assets
            .filter {
                it.target == StoreTarget.APP_STORE &&
                    it.deviceSlot == StoreDeviceSlot.IPHONE_6_9
            }
            .groupBy { it.storeLocale }

        val violations = mutableListOf<Violation>()
        for (locale in ctx.configuredLocales) {
            val count = assetsByLocale[locale]?.size ?: 0
            if (count == 0) {
                violations.add(
                    Violation(
                        ruleId = id,
                        severity = defaultSeverity,
                        target = StoreTarget.APP_STORE,
                        storeLocale = locale,
                        deviceSlot = StoreDeviceSlot.IPHONE_6_9,
                        message = "App Store requires an iPhone 6.9\" screenshot set for $locale.",
                        hint = "Add @StoreScreenshot annotated Composable for PHONE family.",
                    ),
                )
            }
        }
        return violations
    }
}

internal object AscIpad13RequiredRule : Rule {
    override val id: String = "ASC_IPAD_13_REQUIRED"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.LOCALE

    override fun appliesTo(ctx: ValidationContext): Boolean =
        ctx.configuredTargets.contains(StoreTarget.APP_STORE) && ctx.supportsIpad

    override fun check(ctx: ValidationContext): List<Violation> {
        val assetsByLocale = ctx.exportIndex.assets
            .filter {
                it.target == StoreTarget.APP_STORE &&
                    it.deviceSlot == StoreDeviceSlot.IPAD_13
            }
            .groupBy { it.storeLocale }

        val violations = mutableListOf<Violation>()
        for (locale in ctx.configuredLocales) {
            val count = assetsByLocale[locale]?.size ?: 0
            if (count == 0) {
                violations.add(
                    Violation(
                        ruleId = id,
                        severity = defaultSeverity,
                        target = StoreTarget.APP_STORE,
                        storeLocale = locale,
                        deviceSlot = StoreDeviceSlot.IPAD_13,
                        message = "App Store requires an iPad 13\" set for $locale because " +
                            "appStore.supportsIpad is true.",
                        hint = "Add DeviceFamily.TABLET to defaultDevices or " +
                            "set supportsIpad = false.",
                    ),
                )
            }
        }
        return violations
    }
}

internal object AscIpadUnexpectedRule : Rule {
    override val id: String = "ASC_IPAD_UNEXPECTED"
    override val defaultSeverity: Severity = Severity.WARNING
    override val hard: Boolean = false
    override val scope: RuleScope = RuleScope.LOCALE

    override fun appliesTo(ctx: ValidationContext): Boolean =
        ctx.configuredTargets.contains(StoreTarget.APP_STORE) && !ctx.supportsIpad

    override fun check(ctx: ValidationContext): List<Violation> {
        val ipadAssets = ctx.exportIndex.assets.filter {
            it.target == StoreTarget.APP_STORE &&
                it.deviceSlot == StoreDeviceSlot.IPAD_13
        }

        if (ipadAssets.isNotEmpty()) {
            return listOf(
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = StoreTarget.APP_STORE,
                    message = "iPad screenshots were generated but " +
                        "appStore.supportsIpad is false; these assets will not be published.",
                    hint = "Set storeStudio.appStore.supportsIpad.set(true) or " +
                        "remove TABLET family.",
                ),
            )
        }
        return emptyList()
    }
}
