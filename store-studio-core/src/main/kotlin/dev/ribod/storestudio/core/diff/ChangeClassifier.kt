package dev.ribod.storestudio.core.diff

import dev.ribod.storestudio.core.hash.AssetHasher
import dev.ribod.storestudio.model.BaselineAsset
import dev.ribod.storestudio.model.BaselineComparison
import dev.ribod.storestudio.model.DiffChange
import dev.ribod.storestudio.model.DiffChangeType
import dev.ribod.storestudio.model.DiffSummary
import dev.ribod.storestudio.model.ExportAsset
import dev.ribod.storestudio.model.SemanticModificationType
import dev.ribod.storestudio.model.StoreDeviceSlot
import dev.ribod.storestudio.model.resolveNaturalDisplayOrder
import kotlin.math.roundToInt

/**
 * Semantic change classification engine between baseline fingerprints and current exports.
 *
 * Implements the per-bucket truth table algorithm specified in DD-10 section 4.2.
 */
public object ChangeClassifier {

    /**
     * Evaluates differences between [baseAssets] and [headAssets].
     *
     * @param baseAssets Historical baseline asset records.
     * @param headAssets Current export asset records.
     * @param comparison STRICT (pixelSha256) or PERCEPTUAL (pHash Hamming distance).
     * @param perceptualThreshold Maximum normalized distance (default 0.05) for perceptual match.
     * @param resolvedBaseImages Optional map of resolved 'before' image paths keyed by slotKey.
     * @return Pair of calculated [DiffSummary] and deterministic sorted list of [DiffChange].
     */
    public fun classify(
        baseAssets: List<BaselineAsset>,
        headAssets: List<ExportAsset>,
        comparison: BaselineComparison = BaselineComparison.STRICT,
        perceptualThreshold: Double = 0.05,
        resolvedBaseImages: Map<String, String>? = null,
    ): Pair<DiffSummary, List<DiffChange>> {
        val baseByBucket = baseAssets.groupBy { extractBucket(it.slotKey) }
        val headByBucket = headAssets.groupBy {
            "${it.target.name}/${it.deviceSlot.name}/${it.storeLocale}"
        }

        val allBuckets = (baseByBucket.keys + headByBucket.keys).toSet().toList()
        val sortedBuckets = allBuckets.sortedWith(
            compareBy(
                { bucketSlotOrder(it) },
                { bucketTargetOrder(it) },
                { bucketLocale(it) },
            ),
        )

        val changes = mutableListOf<DiffChange>()

        for (bucket in sortedBuckets) {
            val baseInBucket = baseByBucket[bucket].orEmpty()
            val headInBucket = headByBucket[bucket].orEmpty()

            val baseById = baseInBucket.associateBy { extractId(it.slotKey) }
            val headById = headInBucket.associateBy { it.id }
            val replacedBaseSlotKeys = mutableSetOf<String>()

            for (head in headInBucket) {
                val baseMatch = baseById[head.id]
                if (baseMatch == null) {
                    val replacedBase = baseInBucket.firstOrNull { candidate ->
                        val candidateId = extractId(candidate.slotKey)
                        candidateId !in headById &&
                            candidate.position == head.position &&
                            candidate.slotKey !in replacedBaseSlotKeys
                    }

                    if (replacedBase != null) {
                        replacedBaseSlotKeys.add(replacedBase.slotKey)
                        val dist = computeDistance(head.pHash, replacedBase.pHash)
                        val sem = resolveSemantics(replacedBase, head)
                        changes.add(
                            DiffChange(
                                type = DiffChangeType.REPLACED,
                                bucket = bucket,
                                id = head.id,
                                basePosition = replacedBase.position,
                                headPosition = head.position,
                                pHashDistance = dist,
                                baseImage = resolveBeforeImage(
                                    replacedBase.slotKey,
                                    resolvedBaseImages,
                                ),
                                headImage = head.file,
                                basePixelSha256 = replacedBase.pixelSha256,
                                headPixelSha256 = head.pixelSha256,
                                semanticType = sem.type,
                                baseTextSha256 = sem.baseTextSha256,
                                headTextSha256 = sem.headTextSha256,
                                removedTexts = sem.removedTexts,
                                addedTexts = sem.addedTexts,
                                hasVisualOverflow = head.hasVisualOverflow,
                                headEngine = head.engine,
                            ),
                        )
                    } else {
                        changes.add(
                            DiffChange(
                                type = DiffChangeType.ADDED,
                                bucket = bucket,
                                id = head.id,
                                basePosition = null,
                                headPosition = head.position,
                                pHashDistance = null,
                                baseImage = null,
                                headImage = head.file,
                                basePixelSha256 = null,
                                headPixelSha256 = head.pixelSha256,
                                semanticType = null,
                                baseTextSha256 = null,
                                headTextSha256 = head.textSha256,
                                removedTexts = emptyList(),
                                addedTexts = head.extractedTexts,
                                hasVisualOverflow = head.hasVisualOverflow,
                                headEngine = head.engine,
                            ),
                        )
                    }
                } else {
                    val samePosition = head.position == baseMatch.position
                    val dist = computeDistance(head.pHash, baseMatch.pHash)
                    val visualMatch = when (comparison) {
                        BaselineComparison.STRICT -> {
                            head.pixelSha256 == baseMatch.pixelSha256
                        }

                        BaselineComparison.PERCEPTUAL -> {
                            head.pixelSha256 == baseMatch.pixelSha256 ||
                                (dist != null && dist <= perceptualThreshold)
                        }
                    }
                    val sem = resolveSemantics(baseMatch, head)
                    // Captured text is deterministic, so a text change always counts, even when the
                    // perceptual distance stays under the threshold
                    val contentMatch = visualMatch &&
                        sem.type != SemanticModificationType.TEXT_AND_VISUAL_MODIFIED

                    val changeType = when {
                        samePosition && contentMatch -> DiffChangeType.UNCHANGED
                        samePosition && !contentMatch -> DiffChangeType.MODIFIED
                        !samePosition && contentMatch -> DiffChangeType.REORDERED
                        else -> DiffChangeType.MOVED_AND_MODIFIED
                    }

                    val isMod = changeType in MODIFICATION_CHANGE_TYPES

                    changes.add(
                        DiffChange(
                            type = changeType,
                            bucket = bucket,
                            id = head.id,
                            basePosition = baseMatch.position,
                            headPosition = head.position,
                            pHashDistance = dist,
                            baseImage = resolveBeforeImage(
                                baseMatch.slotKey,
                                resolvedBaseImages,
                            ),
                            headImage = head.file,
                            basePixelSha256 = baseMatch.pixelSha256,
                            headPixelSha256 = head.pixelSha256,
                            semanticType = if (isMod) sem.type else null,
                            baseTextSha256 = sem.baseTextSha256,
                            headTextSha256 = sem.headTextSha256,
                            removedTexts = if (isMod) sem.removedTexts else emptyList(),
                            addedTexts = if (isMod) sem.addedTexts else emptyList(),
                            hasVisualOverflow = head.hasVisualOverflow,
                            headEngine = head.engine,
                        ),
                    )
                }
            }

            for (base in baseInBucket) {
                val baseId = extractId(base.slotKey)
                if (baseId !in headById && base.slotKey !in replacedBaseSlotKeys) {
                    changes.add(
                        DiffChange(
                            type = DiffChangeType.REMOVED,
                            bucket = bucket,
                            id = baseId,
                            basePosition = base.position,
                            headPosition = null,
                            pHashDistance = null,
                            baseImage = resolveBeforeImage(
                                base.slotKey,
                                resolvedBaseImages,
                            ),
                            headImage = null,
                            basePixelSha256 = base.pixelSha256,
                            headPixelSha256 = null,
                            semanticType = null,
                            baseTextSha256 = base.textSha256,
                            headTextSha256 = null,
                            removedTexts = base.texts,
                            addedTexts = emptyList(),
                            hasVisualOverflow = false,
                            headEngine = "LAYOUTLIB",
                        ),
                    )
                }
            }
        }

        val sortedChanges = changes.sortedWith(
            compareBy(
                { bucketSlotOrder(it.bucket) },
                { bucketTargetOrder(it.bucket) },
                { bucketLocale(it.bucket) },
                { it.headPosition ?: Int.MAX_VALUE },
                { it.basePosition ?: Int.MAX_VALUE },
                { it.id },
            ),
        )

        val summary = DiffSummary(
            added = sortedChanges.count { it.type == DiffChangeType.ADDED },
            removed = sortedChanges.count { it.type == DiffChangeType.REMOVED },
            reordered = sortedChanges.count { it.type == DiffChangeType.REORDERED },
            modified = sortedChanges.count { it.type == DiffChangeType.MODIFIED },
            replaced = sortedChanges.count { it.type == DiffChangeType.REPLACED },
            movedAndModified = sortedChanges.count { it.type == DiffChangeType.MOVED_AND_MODIFIED },
            unchanged = sortedChanges.count { it.type == DiffChangeType.UNCHANGED },
        )

        return Pair(summary, sortedChanges)
    }

    private fun resolveBeforeImage(
        slotKey: String,
        resolvedBaseImages: Map<String, String>?,
    ): String = when {
        resolvedBaseImages == null -> "AVAILABLE"
        resolvedBaseImages.containsKey(slotKey) -> resolvedBaseImages.getValue(slotKey)
        else -> "UNAVAILABLE"
    }

    private fun computeDistance(pHashHead: String, pHashBase: String): Double? = runCatching {
        val raw = AssetHasher.hammingDistance(pHashHead, pHashBase)
        (raw * 10000.0).roundToInt() / 10000.0
    }.getOrNull()

    private fun extractBucket(slotKey: String): String {
        val idx = slotKey.lastIndexOf('/')
        return if (idx >= 0) slotKey.substring(0, idx) else slotKey
    }

    private fun extractId(slotKey: String): String {
        val idx = slotKey.lastIndexOf('/')
        return if (idx >= 0) slotKey.substring(idx + 1) else slotKey
    }

    private fun bucketTargetOrder(bucket: String): Int {
        val target = bucket.substringBefore('/')
        return if (target == "PLAY_STORE") 0 else 1
    }

    private fun bucketSlotOrder(bucket: String): Int {
        val parts = bucket.split('/')
        if (parts.size < 2) return 99
        val slotName = parts[1]
        return resolveNaturalDisplayOrder(slotName)
    }

    private fun bucketLocale(bucket: String): String {
        val parts = bucket.split('/')
        return if (parts.size >= 3) parts[2] else ""
    }

    private val MODIFICATION_CHANGE_TYPES = setOf(
        DiffChangeType.MODIFIED,
        DiffChangeType.MOVED_AND_MODIFIED,
        DiffChangeType.REPLACED,
    )

    private fun resolveSemantics(base: BaselineAsset?, head: ExportAsset?): SemanticResolution {
        val baseSha = base?.textSha256
        val headSha = head?.textSha256
        val baseTexts = base?.texts.orEmpty()
        val headTexts = head?.extractedTexts.orEmpty()

        if ((baseSha == null && baseTexts.isEmpty()) ||
            (headSha == null && headTexts.isEmpty())
        ) {
            return SemanticResolution(
                type = SemanticModificationType.UNKNOWN,
                baseTextSha256 = baseSha,
                headTextSha256 = headSha,
                removedTexts = emptyList(),
                addedTexts = emptyList(),
            )
        }

        val textMatches = when {
            baseSha != null && headSha != null -> baseSha == headSha
            baseTexts.isNotEmpty() || headTexts.isNotEmpty() -> baseTexts == headTexts
            else -> false
        }

        val semanticType = if (textMatches) {
            SemanticModificationType.VISUAL_LAYOUT_ONLY
        } else {
            SemanticModificationType.TEXT_AND_VISUAL_MODIFIED
        }

        val removed = if (!textMatches && baseTexts.isNotEmpty()) {
            baseTexts.filter { it !in headTexts }
        } else {
            emptyList()
        }

        val added = if (!textMatches && headTexts.isNotEmpty()) {
            headTexts.filter { it !in baseTexts }
        } else {
            emptyList()
        }

        return SemanticResolution(
            type = semanticType,
            baseTextSha256 = baseSha,
            headTextSha256 = headSha,
            removedTexts = removed,
            addedTexts = added,
        )
    }

    private data class SemanticResolution(
        val type: SemanticModificationType?,
        val baseTextSha256: String?,
        val headTextSha256: String?,
        val removedTexts: List<String>,
        val addedTexts: List<String>,
    )
}
