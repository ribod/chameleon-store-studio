package dev.ribod.storestudio.core.baseline

import dev.ribod.storestudio.core.hash.AssetHasher
import dev.ribod.storestudio.model.BaselineComparison
import dev.ribod.storestudio.model.BaselineManifest
import dev.ribod.storestudio.model.ExportIndex
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

/**
 * Categorization of visual regression drift.
 */
@Serializable
public enum class BaselineDriftType {
    /** Asset present in export but missing from recorded baseline. */
    ADDED,

    /** Asset present in baseline but missing from current export. */
    REMOVED,

    /** Physical pixel dimensions differ from baseline recording. */
    DIMENSION_MISMATCH,

    /** Sequence position within the category bucket changed. */
    POSITION_MISMATCH,

    /** Rendered pixels drifted beyond the allowed comparison threshold. */
    PIXEL_DRIFT,

    /** Text captured while rendering differs from the recorded baseline. */
    TEXT_DRIFT,
}

/**
 * Describes a specific detected visual regression drift.
 */
@Serializable
public data class BaselineDrift(
    public val slotKey: String,
    public val type: BaselineDriftType,
    public val message: String,
    public val currentHash: String? = null,
    public val baselineHash: String? = null,
    public val distance: Double? = null,
)

/**
 * Result of comparing current exported assets against the persistent baseline.
 */
@Serializable
public data class BaselineVerificationResult(
    public val passed: Boolean,
    public val comparison: BaselineComparison,
    public val perceptualThreshold: Double,
    public val totalCurrentAssets: Int,
    public val totalBaselineAssets: Int,
    public val matchedCount: Int,
    public val drifts: List<BaselineDrift>,
) {

    public fun toJson(json: Json = DEFAULT_JSON): String = json.encodeToString(this)

    public fun toFile(file: File, json: Json = DEFAULT_JSON) {
        file.parentFile?.mkdirs()
        file.writeText(toJson(json))
    }

    /**
     * Formats an actionable human-readable diagnostic message.
     */
    public fun formatReport(): String {
        if (passed) {
            return "Store Studio: Baseline verification passed. All $matchedCount assets " +
                "match baseline ($comparison mode)."
        }

        val sb = StringBuilder()
        sb.appendLine("Store Studio: Visual drift detected in ${drifts.size} asset(s):")
        for (drift in drifts) {
            sb.appendLine("  ✖ ${drift.slotKey}: ${drift.message}")
        }
        sb.appendLine()
        sb.appendLine("To accept changes and update baseline, run: ./gradlew storeRecordBaseline")
        return sb.toString().trimEnd()
    }

    public companion object {
        private val DEFAULT_JSON = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        public fun fromJson(
            content: String,
            json: Json = DEFAULT_JSON,
        ): BaselineVerificationResult = json.decodeFromString(serializer(), content)

        public fun fromFile(file: File, json: Json = DEFAULT_JSON): BaselineVerificationResult =
            fromJson(file.readText(), json)
    }
}

/**
 * Engine evaluating visual asset parity between working exports and recorded baselines.
 */
public object BaselineVerifier {

    /**
     * Evaluates [current] export index against [baseline] reference.
     *
     * @param baseline Tracked baseline manifest from `.store/baseline.json`.
     * @param current Freshly exported asset index from `build/store/export/index.json`.
     * @param comparison STRICT (cryptographic pixelSha256) or PERCEPTUAL (DCT pHash).
     * @param perceptualThreshold Max normalized Hamming distance for PERCEPTUAL mode
     *   (default 0.05).
     */
    public fun verify(
        baseline: BaselineManifest,
        current: ExportIndex,
        comparison: BaselineComparison = BaselineComparison.STRICT,
        perceptualThreshold: Double = 0.05,
    ): BaselineVerificationResult {
        val baselineMap = baseline.assets.associateBy { it.slotKey }
        val currentMap = current.assets.associateBy { it.slotKey }

        val drifts = mutableListOf<BaselineDrift>()
        var matched = 0

        // Check for removed assets (in baseline but not in current)
        for (bAsset in baseline.assets) {
            if (bAsset.slotKey !in currentMap) {
                drifts.add(
                    BaselineDrift(
                        slotKey = bAsset.slotKey,
                        type = BaselineDriftType.REMOVED,
                        message = "Asset exists in baseline but was not exported.",
                    ),
                )
            }
        }

        // Check current assets against baseline
        for (cAsset in current.assets) {
            val bAsset = baselineMap[cAsset.slotKey]
            if (bAsset == null) {
                drifts.add(
                    BaselineDrift(
                        slotKey = cAsset.slotKey,
                        type = BaselineDriftType.ADDED,
                        message = "New asset detected but not recorded in baseline.",
                    ),
                )
                continue
            }

            var assetHasDrift = false

            // Dimension check
            if (cAsset.widthPx != bAsset.widthPx || cAsset.heightPx != bAsset.heightPx) {
                drifts.add(
                    BaselineDrift(
                        slotKey = cAsset.slotKey,
                        type = BaselineDriftType.DIMENSION_MISMATCH,
                        message = "Dimensions changed from ${bAsset.widthPx}x${bAsset.heightPx} " +
                            "to ${cAsset.widthPx}x${cAsset.heightPx}.",
                    ),
                )
                assetHasDrift = true
            }

            // Position check
            if (cAsset.position != bAsset.position) {
                drifts.add(
                    BaselineDrift(
                        slotKey = cAsset.slotKey,
                        type = BaselineDriftType.POSITION_MISMATCH,
                        message = "Position changed from ${bAsset.position} to ${cAsset.position}.",
                    ),
                )
                assetHasDrift = true
            }

            // Pixel / Perceptual comparison
            when (comparison) {
                BaselineComparison.STRICT -> {
                    if (cAsset.pixelSha256 != bAsset.pixelSha256) {
                        drifts.add(
                            BaselineDrift(
                                slotKey = cAsset.slotKey,
                                type = BaselineDriftType.PIXEL_DRIFT,
                                message = "Pixel SHA-256 mismatch (${cAsset.pixelSha256.take(8)} " +
                                    "vs ${bAsset.pixelSha256.take(8)}).",
                                currentHash = cAsset.pixelSha256,
                                baselineHash = bAsset.pixelSha256,
                            ),
                        )
                        assetHasDrift = true
                    }
                }

                BaselineComparison.PERCEPTUAL -> {
                    if (cAsset.pixelSha256 != bAsset.pixelSha256) {
                        val distance = AssetHasher.hammingDistance(cAsset.pHash, bAsset.pHash)
                        if (distance > perceptualThreshold) {
                            val distFmt = String.format(Locale.US, "%.4f", distance)
                            val threshFmt = String.format(Locale.US, "%.4f", perceptualThreshold)
                            drifts.add(
                                BaselineDrift(
                                    slotKey = cAsset.slotKey,
                                    type = BaselineDriftType.PIXEL_DRIFT,
                                    message = "pHash distance exceeds threshold " +
                                        "($distFmt > $threshFmt).",
                                    currentHash = cAsset.pHash,
                                    baselineHash = bAsset.pHash,
                                    distance = distance,
                                ),
                            )
                            assetHasDrift = true
                        }
                    }
                }
            }

            // Captured text is deterministic, so it is compared exactly in every mode
            val baseTextSha = bAsset.textSha256
            val currentTextSha = cAsset.textSha256
            if (baseTextSha != null && currentTextSha != null && baseTextSha != currentTextSha) {
                val removed = bAsset.texts.filter { it !in cAsset.extractedTexts }
                val added = cAsset.extractedTexts.filter { it !in bAsset.texts }
                val details = buildList {
                    if (removed.isNotEmpty()) add("removed ${removed.joinToString { "\"$it\"" }}")
                    if (added.isNotEmpty()) add("added ${added.joinToString { "\"$it\"" }}")
                }.joinToString("; ")
                drifts.add(
                    BaselineDrift(
                        slotKey = cAsset.slotKey,
                        type = BaselineDriftType.TEXT_DRIFT,
                        message = "Captured text changed" +
                            if (details.isEmpty()) "." else ": $details.",
                        currentHash = currentTextSha,
                        baselineHash = baseTextSha,
                    ),
                )
                assetHasDrift = true
            }

            if (!assetHasDrift) {
                matched++
            }
        }

        return BaselineVerificationResult(
            passed = drifts.isEmpty(),
            comparison = comparison,
            perceptualThreshold = perceptualThreshold,
            totalCurrentAssets = current.assets.size,
            totalBaselineAssets = baseline.assets.size,
            matchedCount = matched,
            drifts = drifts,
        )
    }
}
