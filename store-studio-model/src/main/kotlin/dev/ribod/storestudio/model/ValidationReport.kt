package dev.ribod.storestudio.model

import dev.ribod.storestudio.annotations.StoreTarget
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Severity ranking for rule validation results.
 *
 * Ranking order allows comparing severity levels:
 * - [ERROR]: Blocks publishing and fails verification tasks.
 * - [WARNING]: Indicates non-critical store inconsistencies or recommended practices.
 * - [INFO]: Informational telemetry and promotion eligibility diagnostics.
 */
@Serializable
public enum class Severity(public val rank: Int) {
    INFO(1),
    WARNING(2),
    ERROR(3),
}

/**
 * Machine-readable report summarizing store compliance verification.
 *
 * Conforms to schema version 1 defined in data-contracts.md section 6.
 *
 * Written by the validation task to `build/store/validation/report.json`
 * and consumed by CI/CD reporting tools and IDE problems views.
 */
@Serializable
public data class ValidationReport(
    public val schemaVersion: Int = 1,
    public val valid: Boolean,
    public val violations: List<Violation> = emptyList(),
) {

    public fun toJson(json: Json = DEFAULT_JSON): String = json.encodeToString(this)

    public fun toFile(file: File, json: Json = DEFAULT_JSON) {
        file.parentFile?.mkdirs()
        file.writeText(toJson(json))
    }

    public companion object {
        private val DEFAULT_JSON = ArtifactJson

        public fun fromJson(content: String, json: Json = DEFAULT_JSON): ValidationReport =
            json.decodeFromString(serializer(), content)

        public fun fromFile(file: File, json: Json = DEFAULT_JSON): ValidationReport =
            fromJson(file.readText(), json)
    }
}

/**
 * Detailed description of a single store compliance rule violation or diagnostic.
 */
@Serializable
public data class Violation(
    /** Unique rule identifier from the compliance rules catalog. */
    public val ruleId: String,
    /** Assessed severity of this finding. */
    public val severity: Severity,
    /** Targeted store marketplace if violation is store-scoped. */
    public val target: StoreTarget? = null,
    /** Affected store locale tag if violation is locale-scoped. */
    public val storeLocale: String? = null,
    /** Affected hardware display slot if violation is slot-scoped. */
    public val deviceSlot: StoreDeviceSlot? = null,
    /** Specific canonical asset slot key if violation applies to an individual image. */
    public val slotKey: String? = null,
    /** Human-readable explanation of why this rule failed. */
    public val message: String,
    /** Actionable guidance explaining how to resolve this finding. */
    public val hint: String? = null,
)
