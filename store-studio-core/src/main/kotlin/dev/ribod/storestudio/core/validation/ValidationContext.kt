package dev.ribod.storestudio.core.validation

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.catalog.DefaultStoreSpecCatalog
import dev.ribod.storestudio.core.catalog.StoreSpecCatalog
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.RenderIndex
import dev.ribod.storestudio.model.Severity
import dev.ribod.storestudio.model.StoreManifest
import java.io.File

/**
 * Execution context provided to all compliance validation rules.
 */
public data class ValidationContext(
    public val catalog: StoreSpecCatalog = DefaultStoreSpecCatalog,
    public val exportIndex: ExportIndex,
    public val renderIndex: RenderIndex? = null,
    public val mergedManifest: StoreManifest? = null,
    public val configuredTargets: Set<StoreTarget> = setOf(
        StoreTarget.PLAY_STORE,
        StoreTarget.APP_STORE,
    ),
    public val configuredLocales: Set<String> = setOf("en-US"),
    public val supportsIpad: Boolean = true,
    public val exportDir: File? = null,
    public val featureGraphicMissingSeverity: Severity = Severity.ERROR,
    public val metadataManifest: dev.ribod.storestudio.core.metadata.StoreMetadataManifest? = null,
    public val semanticsSnapshots: List<dev.ribod.storestudio.model.SemanticsSnapshot> =
        emptyList(),
    /**
     * Whether this module produces the store listing (the application module). Library modules
     * only contribute some of its screenshots, so listing completeness rules (counts, required
     * assets, locale coverage, metadata) only run for the listing; per-image [RuleScope.ASSET]
     * rules run everywhere.
     */
    public val listingModule: Boolean = true,
)

/**
 * Self-contained compliance rule enforcing app store policies or technical invariants.
 */
public interface Rule {
    /** Unique, stable rule identifier used in reports and severity overrides. */
    public val id: String

    /** Baseline severity applied when no custom project override is declared. */
    public val defaultSeverity: Severity

    /**
     * Whether this rule represents an absolute store requirement.
     *
     * Hard rules cannot be downgraded below [Severity.ERROR] because violating them
     * causes store API upload rejection or metadata processing failure.
     */
    public val hard: Boolean

    /** Structural scope of this rule. */
    public val scope: RuleScope

    /** Determines whether this rule applies to the current execution context. */
    public fun appliesTo(ctx: ValidationContext): Boolean = true

    /** Executes rule evaluation and returns all detected violations. */
    public fun check(ctx: ValidationContext): List<dev.ribod.storestudio.model.Violation>
}
