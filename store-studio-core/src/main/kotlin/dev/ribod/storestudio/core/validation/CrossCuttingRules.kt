package dev.ribod.storestudio.core.validation

import dev.ribod.storestudio.core.hash.AssetHasher
import dev.ribod.storestudio.model.RenderJobResult
import dev.ribod.storestudio.model.Severity
import dev.ribod.storestudio.model.Violation
import java.io.File

/**
 * Cross-cutting compliance and consistency rules across all marketplaces.
 */
public object CrossCuttingRules {

    public val ALL: List<Rule> = listOf(
        AssetRenderFailedRule,
        AssetRenderFallbackUsedRule,
        AssetDimensionsMismatchRule,
        AssetAlphaChannelRule,
        AssetIndexStaleRule,
        LocaleCoverageRule,
        LocaleCoverageInconsistentRule,
        OrderDuplicateRule,
    )
}

internal object AssetRenderFallbackUsedRule : Rule {
    override val id: String = "ASSET_RENDER_FALLBACK_USED"
    override val defaultSeverity: Severity = Severity.INFO
    override val hard: Boolean = false
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> {
        val renderIndex = ctx.renderIndex ?: return emptyList()
        return renderIndex.jobs
            .filter { it.engine.equals("ROBOLECTRIC", ignoreCase = true) }
            .map { job ->
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = job.target,
                    storeLocale = job.storeLocale,
                    deviceSlot = job.deviceSlot,
                    slotKey = "${job.target}/${job.deviceSlot}/${job.storeLocale}/${job.entryId}",
                    message = "Screenshot '${job.entryId}' was rendered using the " +
                        "Robolectric Native Graphics fallback engine.",
                    hint = "Layoutlib was skipped or encountered an issue. " +
                        "Render succeeded via Robolectric Native Graphics.",
                )
            }
    }
}

internal object AssetRenderFailedRule : Rule {
    override val id: String = "ASSET_RENDER_FAILED"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> {
        val renderIndex = ctx.renderIndex ?: return emptyList()
        return renderIndex.jobs
            .filter { it.status == "FAILED" || it.status == "SKIPPED" }
            .map { job ->
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = job.target,
                    storeLocale = job.storeLocale,
                    deviceSlot = job.deviceSlot,
                    slotKey = "${job.target}/${job.deviceSlot}/${job.storeLocale}/${job.entryId}",
                    message = "Screenshot '${job.entryId}' failed to render (${job.status}).",
                    hint = "Inspect build/store/render/renderer.log for " +
                        "JVM compilation or runtime crash.",
                )
            }
    }
}

internal object AssetDimensionsMismatchRule : Rule {
    override val id: String = "ASSET_DIMENSIONS_MISMATCH"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> {
        return ctx.exportIndex.assets.mapNotNull { asset ->
            val spec =
                runCatching { ctx.catalog[asset.deviceSlot] }.getOrNull() ?: return@mapNotNull null
            val expected = spec.defaultGeometry.size
            val matchesNormal = asset.widthPx == expected.width && asset.heightPx == expected.height
            val matchesTransposed =
                asset.widthPx == expected.height && asset.heightPx == expected.width

            if (!matchesNormal && !matchesTransposed) {
                Violation(
                    ruleId = id,
                    severity = defaultSeverity,
                    target = asset.target,
                    storeLocale = asset.storeLocale,
                    deviceSlot = asset.deviceSlot,
                    slotKey = asset.slotKey,
                    message = "Asset dimensions ${asset.widthPx}x${asset.heightPx} deviate from " +
                        "catalog baseline ${expected.width}x${expected.height}.",
                    hint = "Verify catalog geometry in store-studio-core or run storeDump.",
                )
            } else {
                null
            }
        }
    }
}

internal object AssetAlphaChannelRule : Rule {
    override val id: String = "ASSET_ALPHA_CHANNEL"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> = ctx.exportIndex.assets
        .filter { it.hasAlpha }
        .map { asset ->
            Violation(
                ruleId = id,
                severity = defaultSeverity,
                target = asset.target,
                storeLocale = asset.storeLocale,
                deviceSlot = asset.deviceSlot,
                slotKey = asset.slotKey,
                message = "Exported file has an active alpha channel.",
                hint = "Verify flattening pipeline in storeExport.",
            )
        }
}

internal object AssetIndexStaleRule : Rule {
    override val id: String = "ASSET_INDEX_STALE"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> {
        val exportDir = ctx.exportDir ?: return emptyList()
        val violations = mutableListOf<Violation>()

        for (asset in ctx.exportIndex.assets) {
            val file = File(exportDir, asset.file)
            if (!file.exists()) {
                violations.add(
                    Violation(
                        ruleId = id,
                        severity = defaultSeverity,
                        target = asset.target,
                        storeLocale = asset.storeLocale,
                        deviceSlot = asset.deviceSlot,
                        slotKey = asset.slotKey,
                        message = "Exported file '${asset.file}' is missing from disk.",
                        hint = "Re-run storeExport to regenerate missing binaries.",
                    ),
                )
            } else {
                val diskHash = AssetHasher.fileSha256(file)
                if (diskHash != asset.fileSha256) {
                    violations.add(
                        Violation(
                            ruleId = id,
                            severity = defaultSeverity,
                            target = asset.target,
                            storeLocale = asset.storeLocale,
                            deviceSlot = asset.deviceSlot,
                            slotKey = asset.slotKey,
                            message = "File on disk modified after export (${asset.file}).",
                            hint = "Re-run storeExport; do not edit build/store/export manually.",
                        ),
                    )
                }
            }
        }
        return violations
    }
}

internal object LocaleCoverageRule : Rule {
    override val id: String = "LOCALE_COVERAGE"
    override val defaultSeverity: Severity = Severity.ERROR
    override val hard: Boolean = true
    override val scope: RuleScope = RuleScope.LOCALE

    override fun check(ctx: ValidationContext): List<Violation> {
        val violations = mutableListOf<Violation>()
        for (target in ctx.configuredTargets) {
            val targetAssets = ctx.exportIndex.assets.filter { it.target == target }
            val assetsByLocale = targetAssets.groupBy { it.storeLocale }

            for (locale in ctx.configuredLocales) {
                val count = assetsByLocale[locale]?.size ?: 0
                if (count == 0) {
                    violations.add(
                        Violation(
                            ruleId = id,
                            severity = defaultSeverity,
                            target = target,
                            storeLocale = locale,
                            message = "Marketplace $target has no exported assets for " +
                                "locale '$locale'.",
                            hint = "Ensure @StoreScreenshot components cover locale '$locale'.",
                        ),
                    )
                }
            }
        }
        return violations
    }
}

internal object LocaleCoverageInconsistentRule : Rule {
    override val id: String = "LOCALE_COVERAGE_INCONSISTENT"
    override val defaultSeverity: Severity = Severity.WARNING
    override val hard: Boolean = false
    override val scope: RuleScope = RuleScope.LOCALE

    override fun check(ctx: ValidationContext): List<Violation> {
        val violations = mutableListOf<Violation>()
        val defaultLocale = ctx.configuredLocales.firstOrNull() ?: "en-US"

        val buckets = ctx.exportIndex.assets.groupBy { Pair(it.target, it.deviceSlot) }
        for ((bucket, assets) in buckets) {
            val countByLocale = assets.groupBy { it.storeLocale }.mapValues { it.value.size }
            val defaultCount = countByLocale[defaultLocale] ?: 0

            for (locale in ctx.configuredLocales) {
                if (locale != defaultLocale) {
                    val count = countByLocale[locale] ?: 0
                    if (count > 0 && count < defaultCount) {
                        violations.add(
                            Violation(
                                ruleId = id,
                                severity = defaultSeverity,
                                target = bucket.first,
                                storeLocale = locale,
                                deviceSlot = bucket.second,
                                message =
                                "Locale '$locale' has $count assets in ${bucket.second} " +
                                    "compared to $defaultCount in default locale '$defaultLocale'.",
                                hint = "Align screenshot counts across localized listings.",
                            ),
                        )
                    }
                }
            }
        }
        return violations
    }
}

internal object OrderDuplicateRule : Rule {
    override val id: String = "ORDER_DUPLICATE"
    override val defaultSeverity: Severity = Severity.WARNING
    override val hard: Boolean = false
    override val scope: RuleScope = RuleScope.BUCKET

    override fun check(ctx: ValidationContext): List<Violation> {
        val violations = mutableListOf<Violation>()
        val assetsByBucket = ctx.exportIndex.assets.groupBy {
            Triple(it.target, it.storeLocale, it.deviceSlot)
        }

        for ((bucket, assets) in assetsByBucket) {
            val orders = assets.groupBy { it.order }
            for ((order, orderAssets) in orders) {
                if (orderAssets.size > 1) {
                    violations.add(
                        Violation(
                            ruleId = id,
                            severity = defaultSeverity,
                            target = bucket.first,
                            storeLocale = bucket.second,
                            deviceSlot = bucket.third,
                            message = "Multiple screenshots share order = $order in " +
                                "${bucket.third} (${orderAssets.joinToString { it.id }}).",
                            hint = "Assign unique, sequential order parameters.",
                        ),
                    )
                }
            }
        }
        return violations
    }
}
