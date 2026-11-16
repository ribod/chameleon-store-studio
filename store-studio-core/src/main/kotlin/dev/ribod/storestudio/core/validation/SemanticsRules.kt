package dev.ribod.storestudio.core.validation

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.model.Severity
import dev.ribod.storestudio.model.StoreDeviceSlot
import dev.ribod.storestudio.model.Violation

/**
 * Semantics-based compliance and accessibility validation rules (DD-18).
 */
public object SemanticsRules {

    public val ALL: List<Rule> = listOf(
        SemanticsTextOverflowRule,
    )
}

internal object SemanticsTextOverflowRule : Rule {
    override val id: String = "SEMANTICS_TEXT_OVERFLOW"
    override val defaultSeverity: Severity = Severity.WARNING
    override val hard: Boolean = false
    override val scope: RuleScope = RuleScope.ASSET

    override fun check(ctx: ValidationContext): List<Violation> {
        val violations = mutableListOf<Violation>()
        val processedSlotKeys = mutableSetOf<String>()

        for (snapshot in ctx.semanticsSnapshots) {
            if (snapshot.hasAnyVisualOverflow) {
                processedSlotKeys.add(snapshot.slotKey)
                val parts = snapshot.slotKey.split('/')
                val target = runCatching { StoreTarget.valueOf(parts[0]) }.getOrNull()
                val slot = runCatching { StoreDeviceSlot.valueOf(parts[1]) }.getOrNull()
                val locale = if (parts.size >= 3) parts[2] else snapshot.locale

                val overflowingTexts = snapshot.nodes
                    .filter { it.hasVisualOverflow }
                    .flatMap { it.texts }
                    .filter { it.isNotBlank() }

                val sample = if (overflowingTexts.isNotEmpty()) {
                    " (\"" + overflowingTexts.take(2).joinToString("\", \"") + "\")"
                } else {
                    ""
                }

                violations.add(
                    Violation(
                        ruleId = id,
                        severity = defaultSeverity,
                        target = target,
                        storeLocale = locale,
                        deviceSlot = slot,
                        slotKey = snapshot.slotKey,
                        message = "Detected visual text truncation/overflow in " +
                            "'${snapshot.jobId}'$sample.",
                        hint = "Adjust font size, maxLines, padding, or container dimensions to " +
                            "prevent clipped or truncated text.",
                    ),
                )
            }
        }

        for (asset in ctx.exportIndex.assets) {
            if (asset.hasVisualOverflow && asset.slotKey !in processedSlotKeys) {
                violations.add(
                    Violation(
                        ruleId = id,
                        severity = defaultSeverity,
                        target = asset.target,
                        storeLocale = asset.storeLocale,
                        deviceSlot = asset.deviceSlot,
                        slotKey = asset.slotKey,
                        message = "Detected visual text truncation/overflow in " +
                            "asset '${asset.id}'.",
                        hint = "Adjust font size, maxLines, padding, or container dimensions to " +
                            "prevent clipped or truncated text.",
                    ),
                )
            }
        }

        return violations
    }
}
