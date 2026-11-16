package dev.ribod.storestudio.runtime.semantics

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import dev.ribod.storestudio.runtime.model.StoreContext
import java.io.File
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

private const val SEMANTICS_DIR_PROPERTY = "storestudio.semanticsDir"
private const val SEMANTICS_JOBS_FILE_PROPERTY = "storestudio.semanticsJobsFile"

/** Capture is driven by `-D` flags the headless renderers pass at JVM startup. */
internal actual fun isStoreSemanticsCaptureEnabled(): Boolean =
    System.getProperty(SEMANTICS_DIR_PROPERTY) != null

public actual fun Modifier.storeSemanticsProbe(
    context: StoreContext,
    callingMethodFqn: String?,
): Modifier {
    // Outside headless rendering (IDE previews, apps) the probe adds no modifier nodes at all
    if (!isStoreSemanticsCaptureEnabled()) return this
    return this then SemanticsProbeElement(context, callingMethodFqn)
}

/** Equal elements reuse their node across recompositions instead of rebuilding the chain. */
private data class SemanticsProbeElement(
    val context: StoreContext,
    val callingMethodFqn: String?,
) : ModifierNodeElement<SemanticsProbeNode>() {
    override fun create(): SemanticsProbeNode = SemanticsProbeNode(context, callingMethodFqn)

    override fun update(node: SemanticsProbeNode) {
        node.context = context
        node.callingMethodFqn = callingMethodFqn
    }
}

/**
 * Captures after every draw so the last rendered frame wins: content can change (e.g. after async
 * state) without this node being re-laid out. Unchanged snapshots are not rewritten.
 */
private class SemanticsProbeNode(var context: StoreContext, var callingMethodFqn: String?) :
    Modifier.Node(),
    DrawModifierNode,
    GlobalPositionAwareModifierNode {

    private var coordinates: LayoutCoordinates? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        coordinates?.let { captureSemantics(it, context, callingMethodFqn) }
    }
}

private fun captureSemantics(
    coordinates: LayoutCoordinates,
    context: StoreContext,
    callingMethodFqn: String?,
) {
    val dirPath = System.getProperty(SEMANTICS_DIR_PROPERTY) ?: return
    val semanticsDir = File(dirPath)
    if (!semanticsDir.exists()) {
        semanticsDir.mkdirs()
    }

    val semanticsOwner = extractSemanticsOwner(coordinates) ?: return
    val rootNode = extractRootSemanticsNode(semanticsOwner) ?: return

    val extractedNodes = mutableListOf<RawSemanticsNode>()
    traverseSemantics(rootNode, extractedNodes)

    val finalNodes = detectOverlappingTextNodes(extractedNodes)

    val jobsFilePath = System.getProperty(SEMANTICS_JOBS_FILE_PROPERTY)
    val resolvedJob = resolveJobInfo(context, jobsFilePath, callingMethodFqn)

    val jsonOutput = formatSnapshotJson(
        jobId = resolvedJob.jobId,
        slotKey = resolvedJob.slotKey,
        locale = resolvedJob.locale ?: context.locale,
        nodes = finalNodes,
    )

    val targetFile = semanticsDir.resolve("${resolvedJob.jobId}.json")
    // Every frame is captured so the final frame wins, but unchanged snapshots are not rewritten
    val fingerprint = jsonOutput.hashCode()
    if (writtenSnapshots[targetFile.path] == fingerprint && targetFile.isFile) return
    targetFile.writeText(jsonOutput)
    writtenSnapshots[targetFile.path] = fingerprint
}

private val writtenSnapshots = ConcurrentHashMap<String, Int>()

/** Parsed render jobs, reloaded only when the jobs file changes on disk. */
private object JobsFileCache {
    private var key: Triple<String, Long, Long>? = null
    private var jobs: List<JobRecord> = emptyList()

    @Synchronized
    fun jobs(file: File): List<JobRecord> {
        val currentKey = Triple(file.path, file.lastModified(), file.length())
        if (currentKey != key) {
            jobs = parseJobRecords(file.readText())
            key = currentKey
        }
        return jobs
    }
}

/**
 * Parses the jobs file written by the Gradle plugin (`SemanticsJobsFile.kt`): one job per line,
 * tab-separated `jobId, slotKey, methodFqn, slot, target, storeLocale`.
 */
internal fun parseJobRecords(content: String): List<JobRecord> = content.lineSequence()
    .filter { it.isNotBlank() }
    .map { line ->
        val fields = line.split('\t')
        JobRecord(
            jobId = fields.getOrNull(0),
            slotKey = fields.getOrNull(1),
            methodFqn = fields.getOrNull(2),
            slot = fields.getOrNull(3),
            target = fields.getOrNull(4),
            locale = fields.getOrNull(5),
        )
    }
    .toList()

/** Reflective lookups resolved once per class; capture runs on every frame. */
private val reflectiveMethods = ConcurrentHashMap<String, Optional<Method>>()

private fun Class<*>.findMethod(key: String, predicate: (Method) -> Boolean): Method? =
    reflectiveMethods.computeIfAbsent("$name#$key") {
        Optional.ofNullable(methods.firstOrNull(predicate))
    }.orElse(null)

private fun extractSemanticsOwner(coordinates: LayoutCoordinates): Any? = runCatching {
    val getLayoutNode = coordinates.javaClass.findMethod("layoutNode") {
        it.name == "getLayoutNode"
    } ?: return null
    val layoutNode = getLayoutNode.invoke(coordinates) ?: return null
    val getOwner = layoutNode.javaClass.findMethod("owner") { it.name.startsWith("getOwner") }
        ?: return null
    val owner = getOwner.invoke(layoutNode) ?: return null
    val getSemanticsOwner = owner.javaClass.findMethod("semanticsOwner") {
        it.name.startsWith("getSemanticsOwner")
    } ?: return null
    getSemanticsOwner.invoke(owner)
}.getOrNull()

private fun extractRootSemanticsNode(semanticsOwner: Any): SemanticsNode? = runCatching {
    val type = semanticsOwner.javaClass
    val getUnmerged = type.findMethod("unmergedRoot") { it.name == "getUnmergedRootSemanticsNode" }
        ?: type.findMethod("root") { it.name == "getRootSemanticsNode" }
    getUnmerged?.invoke(semanticsOwner) as? SemanticsNode
}.getOrNull()

private fun traverseSemantics(node: SemanticsNode, output: MutableList<RawSemanticsNode>) {
    val texts = mutableListOf<String>()
    val textsProp = node.config.getOrNull(SemanticsProperties.Text)
    if (textsProp != null) {
        texts.addAll(textsProp.map { it.text })
    }
    val editable = node.config.getOrNull(SemanticsProperties.EditableText)
    if (editable != null) {
        texts.add(editable.text)
    }

    val contentDesc = node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
    val role = node.config.getOrNull(SemanticsProperties.Role)?.toString()

    var hasVisualOverflow = false
    val getTextLayoutResultAction = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)
    if (getTextLayoutResultAction != null) {
        val results = mutableListOf<TextLayoutResult>()
        runCatching {
            getTextLayoutResultAction.action?.invoke(results)
            hasVisualOverflow = results.any { res ->
                val totalLength = res.layoutInput.text.length
                val lastLineEnd = if (res.lineCount > 0) {
                    res.getLineEnd(res.lineCount - 1, visibleEnd = true)
                } else {
                    totalLength
                }
                val hasEllipsis = (0 until res.lineCount).any { res.isLineEllipsized(it) }
                val isTruncated = lastLineEnd < totalLength
                val lineOverflowsWidth = (0 until res.lineCount).any {
                    (res.getLineRight(it) - res.getLineLeft(it)) > (res.size.width + 1.5f)
                }
                val overflowsHeight = res.lineCount > 0 &&
                    res.getLineBottom(res.lineCount - 1) > (res.size.height + 1.5f)

                hasEllipsis || isTruncated || lineOverflowsWidth || overflowsHeight
            }
        }
    }

    val bounds = node.boundsInRoot
    if (texts.isNotEmpty() || contentDesc != null || hasVisualOverflow) {
        output.add(
            RawSemanticsNode(
                id = node.id,
                texts = texts,
                contentDescription = contentDesc,
                role = role,
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom,
                hasVisualOverflow = hasVisualOverflow,
            ),
        )
    }

    for (child in node.children) {
        traverseSemantics(child, output)
    }
}

private data class ResolvedJob(val jobId: String, val slotKey: String, val locale: String?)

private fun resolveJobInfo(
    context: StoreContext,
    jobsFilePath: String?,
    callingMethodFqn: String?,
): ResolvedJob {
    if (jobsFilePath != null) {
        val file = File(jobsFilePath)
        if (file.isFile) {
            val matched = runCatching { JobsFileCache.jobs(file) }.getOrNull()
                ?.let { jobs -> matchJob(jobs, context, callingMethodFqn) }
            if (matched != null) return matched
        }
    }

    val entryId = callingMethodFqn?.substringAfterLast('.')?.substringBefore('$')
        ?: if (context.screenshotId.isNotBlank() && context.screenshotId != "preview") {
            context.screenshotId
        } else {
            "screenshot"
        }

    val targetName = context.target.name
    val slotName = context.deviceSpec.slot
    val locale = context.locale

    return ResolvedJob(
        jobId = "${entryId}__${targetName}__${slotName}__$locale",
        slotKey = "$targetName/$slotName/$locale/$entryId",
        locale = locale,
    )
}

private fun matchJob(
    jobs: List<JobRecord>,
    context: StoreContext,
    callingMethodFqn: String?,
): ResolvedJob? = runCatching {
    val stack = Thread.currentThread().stackTrace
    val slotName = context.deviceSpec.slot
    val targetName = context.target.name
    val normalizedContextLocale = normalizeLocaleTag(context.locale)

    val methodFqnsInJobs = jobs.mapNotNull { it.methodFqn }.toSet()
    val matchingFrame = stack.firstOrNull { frame ->
        val full = "${frame.className}.${frame.methodName}"
        methodFqnsInJobs.any { jobMethod ->
            val cleanJob = jobMethod.substringBefore('$')
            full == cleanJob || full.startsWith("$cleanJob$")
        }
    }
    val effectiveCallingMethod = matchingFrame?.let {
        "${it.className}.${it.methodName}"
    } ?: callingMethodFqn

    fun matchesMethod(job: JobRecord): Boolean {
        if (job.methodFqn == null) return false
        val cleanJob = job.methodFqn.substringBefore('$').trim()
        if (!effectiveCallingMethod.isNullOrBlank()) {
            val cleanCalling = effectiveCallingMethod.substringBefore('$').trim()
            if (cleanCalling == cleanJob) return true
            val jobMethodName = cleanJob.substringAfterLast('.')
            val callingMethodName = cleanCalling.substringAfterLast('.')
            if (jobMethodName == callingMethodName) {
                val jobPkg = cleanJob.substringBeforeLast('.', "").substringBeforeLast('.', "")
                val callingPkg = cleanCalling.substringBeforeLast('.', "")
                    .substringBeforeLast('.', "")
                if (jobPkg.isNotEmpty() && jobPkg == callingPkg) return true
            }
            return false
        }
        return stack.any { frame ->
            val full = "${frame.className}.${frame.methodName}"
            full == cleanJob || full.startsWith("$cleanJob$")
        }
    }

    fun matchesSlot(job: JobRecord): Boolean = job.slot == null || job.slot == slotName

    fun matchesTarget(job: JobRecord): Boolean = job.target == null || job.target == targetName

    fun matchesLocale(job: JobRecord): Boolean =
        job.locale == null || normalizeLocaleTag(job.locale) == normalizedContextLocale

    val exactMatch = jobs.firstOrNull { job ->
        matchesMethod(job) && matchesSlot(job) && matchesTarget(job) && matchesLocale(job)
    }
    if (exactMatch?.jobId != null && exactMatch.slotKey != null) {
        return@runCatching ResolvedJob(exactMatch.jobId, exactMatch.slotKey, exactMatch.locale)
    }

    val methodAndLocaleMatch = jobs.firstOrNull { job ->
        matchesMethod(job) && matchesSlot(job) && matchesLocale(job)
    }
    if (methodAndLocaleMatch?.jobId != null && methodAndLocaleMatch.slotKey != null) {
        return@runCatching ResolvedJob(
            methodAndLocaleMatch.jobId,
            methodAndLocaleMatch.slotKey,
            methodAndLocaleMatch.locale,
        )
    }

    val methodOnlyMatch = jobs.firstOrNull { job ->
        matchesMethod(job) && matchesLocale(job)
    }
    if (methodOnlyMatch?.jobId != null && methodOnlyMatch.slotKey != null) {
        return@runCatching ResolvedJob(
            methodOnlyMatch.jobId,
            methodOnlyMatch.slotKey,
            methodOnlyMatch.locale,
        )
    }

    null
}.getOrNull()

private fun normalizeLocaleTag(tag: String?): String =
    tag?.replace("-r", "-")?.replace('_', '-')?.lowercase().orEmpty()

internal data class JobRecord(
    val jobId: String?,
    val slotKey: String?,
    val methodFqn: String?,
    val slot: String?,
    val target: String?,
    val locale: String?,
)

private data class RawSemanticsNode(
    val id: Int,
    val texts: List<String>,
    val contentDescription: String?,
    val role: String?,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val hasVisualOverflow: Boolean,
)

private fun computeSha256(nodes: List<RawSemanticsNode>): String {
    val lines = nodes.flatMap { node ->
        node.texts + listOfNotNull(node.contentDescription)
    }.map { it.trim() }.filter { it.isNotEmpty() }

    val payload = lines.joinToString("\n")
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(payload.toByteArray(StandardCharsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun formatSnapshotJson(
    jobId: String,
    slotKey: String,
    locale: String?,
    nodes: List<RawSemanticsNode>,
): String {
    val sb = StringBuilder()
    val textSha = computeSha256(nodes)
    val anyOverflow = nodes.any { it.hasVisualOverflow }
    sb.appendLine("{")
    sb.appendLine("  \"schemaVersion\": 1,")
    sb.appendLine("  \"jobId\": \"${escapeJson(jobId)}\",")
    sb.appendLine("  \"slotKey\": \"${escapeJson(slotKey)}\",")
    if (locale != null) {
        sb.appendLine("  \"locale\": \"${escapeJson(locale)}\",")
    } else {
        sb.appendLine("  \"locale\": null,")
    }
    sb.appendLine("  \"textSha256\": \"$textSha\",")
    sb.appendLine("  \"hasAnyVisualOverflow\": $anyOverflow,")
    sb.appendLine("  \"nodes\": [")
    nodes.forEachIndexed { i, node ->
        val comma = if (i < nodes.size - 1) "," else ""
        sb.appendLine("    {")
        sb.appendLine("      \"id\": ${node.id},")
        val textsArr = node.texts.joinToString(", ") { "\"${escapeJson(it)}\"" }
        sb.appendLine("      \"texts\": [$textsArr],")
        val descStr = node.contentDescription?.let { "\"${escapeJson(it)}\"" } ?: "null"
        sb.appendLine("      \"contentDescription\": $descStr,")
        val roleStr = node.role?.let { "\"${escapeJson(it)}\"" } ?: "null"
        sb.appendLine("      \"role\": $roleStr,")
        sb.appendLine(
            "      \"bounds\": {\"left\": ${node.left}, \"top\": ${node.top}, " +
                "\"right\": ${node.right}, \"bottom\": ${node.bottom}},",
        )
        sb.appendLine("      \"hasVisualOverflow\": ${node.hasVisualOverflow}")
        sb.appendLine("    }$comma")
    }
    sb.appendLine("  ]")
    sb.appendLine("}")
    return sb.toString()
}

private fun detectOverlappingTextNodes(nodes: List<RawSemanticsNode>): List<RawSemanticsNode> {
    val overlappingIds = mutableSetOf<Int>()
    val textNodes = nodes.filter { it.texts.isNotEmpty() }
    for (i in 0 until textNodes.size) {
        for (j in (i + 1) until textNodes.size) {
            val a = textNodes[i]
            val b = textNodes[j]
            val interW = minOf(a.right, b.right) - maxOf(a.left, b.left)
            val interH = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            if (interW > 8f && interH > 8f) {
                overlappingIds.add(a.id)
                overlappingIds.add(b.id)
            }
        }
    }
    if (overlappingIds.isEmpty()) return nodes
    return nodes.map { node ->
        if (node.id in overlappingIds) node.copy(hasVisualOverflow = true) else node
    }
}

private fun escapeJson(str: String): String = str
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")
