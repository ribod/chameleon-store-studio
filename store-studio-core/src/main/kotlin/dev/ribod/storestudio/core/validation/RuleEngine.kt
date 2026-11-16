package dev.ribod.storestudio.core.validation

import dev.ribod.storestudio.model.Severity
import dev.ribod.storestudio.model.ValidationReport
import dev.ribod.storestudio.model.Violation
import kotlin.math.min

/**
 * Data-driven validation engine executing store compliance verification.
 */
public class RuleEngine(public val rules: List<Rule> = DEFAULT_RULES) {

    /**
     * Executes all applicable compliance rules against the provided [ctx].
     *
     * @param ctx Execution context containing export index, catalog specs, and manifests.
     * @param overrides Custom rule severity mappings configured in the Gradle DSL.
     * @param failOnWarning When true, warnings are treated as build-failing violations.
     */
    public fun run(
        ctx: ValidationContext,
        overrides: Map<String, Severity> = emptyMap(),
        failOnWarning: Boolean = false,
    ): ValidationReport {
        val knownRuleIds = rules.map { it.id }.toSet()
        val unknownOverrides = overrides.keys - knownRuleIds

        if (unknownOverrides.isNotEmpty()) {
            val suggestions = unknownOverrides.map { unknown ->
                val closest = findClosestRuleId(unknown, knownRuleIds)
                if (closest != null) "'$unknown' (did you mean '$closest'?)" else "'$unknown'"
            }
            throw IllegalArgumentException(
                "Unknown ruleId(s) in validation.severityOverrides: ${suggestions.joinToString()}",
            )
        }

        val violations = mutableListOf<Violation>()

        val activeRules = rules.filter { rule ->
            (ctx.listingModule || rule.scope == RuleScope.ASSET) && rule.appliesTo(ctx)
        }
        for (rule in activeRules) {
            val override = overrides[rule.id]
            val effectiveSeverity = if (override != null) {
                if (rule.hard && override.rank < Severity.ERROR.rank) {
                    violations.add(
                        Violation(
                            ruleId = "VALIDATION_OVERRIDE_IGNORED",
                            severity = Severity.WARNING,
                            message = "Override for hard rule '${rule.id}' to '$override' " +
                                "was ignored; store hard requirements cannot be downgraded.",
                            hint = "Remove the override or set severity to ERROR.",
                        ),
                    )
                    rule.defaultSeverity
                } else {
                    override
                }
            } else {
                rule.defaultSeverity
            }

            val ruleViolations = rule.check(ctx)
            for (violation in ruleViolations) {
                violations.add(violation.copy(severity = effectiveSeverity))
            }
        }

        // De-duplicate: suppress PLAY_SCREENSHOT_COUNT_MIN if LOCALE_COVERAGE already flagged zero
        val zeroCoverageLocales = violations
            .filter { it.ruleId == "LOCALE_COVERAGE" }
            .mapNotNull { it.storeLocale }
            .toSet()

        val filteredViolations = violations.filterNot { violation ->
            violation.ruleId == "PLAY_SCREENSHOT_COUNT_MIN" &&
                violation.storeLocale in zeroCoverageLocales
        }

        val sortedViolations = filteredViolations.sortedWith(
            compareBy(
                { -it.severity.rank },
                { it.target?.name ?: "" },
                { it.storeLocale ?: "" },
                { it.deviceSlot?.name ?: "" },
                { it.slotKey ?: "" },
                { it.ruleId },
            ),
        )

        val isValid = sortedViolations.none {
            it.severity == Severity.ERROR || (failOnWarning && it.severity == Severity.WARNING)
        }

        return ValidationReport(
            schemaVersion = 1,
            valid = isValid,
            violations = sortedViolations,
        )
    }

    private fun findClosestRuleId(target: String, candidates: Set<String>): String? =
        candidates.minByOrNull {
            levenshteinDistance(target.lowercase(), it.lowercase())
        }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        var cost = IntArray(lhsLen + 1) { it }
        var newCost = IntArray(lhsLen + 1)

        for (i in 1..rhsLen) {
            newCost[0] = i
            for (j in 1..lhsLen) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }

    public companion object {
        public val DEFAULT_RULES: List<Rule> =
            PlayRules.ALL + AppStoreRules.ALL + CrossCuttingRules.ALL +
                MetadataRules.ALL + SemanticsRules.ALL
    }
}
