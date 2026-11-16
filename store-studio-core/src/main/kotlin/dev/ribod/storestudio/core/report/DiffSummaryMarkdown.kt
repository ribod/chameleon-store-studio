package dev.ribod.storestudio.core.report

import dev.ribod.storestudio.model.DiffChange
import dev.ribod.storestudio.model.DiffChangeType
import dev.ribod.storestudio.model.DiffDocument
import dev.ribod.storestudio.model.SemanticModificationType

/**
 * Generates PR-ready sticky comment Markdown summaries conforming to DD-11 §4.5.5 and DD-12.
 */
public object DiffSummaryMarkdown {

    private const val MAX_DISPLAYED_CHANGES = 50

    /**
     * Renders [diff] into a deterministic, GitHub-flavored Markdown comment. [appName] tells the
     * comments of several apps in the same pull request apart.
     */
    public fun generate(diff: DiffDocument, appName: String? = null): String {
        val sb = StringBuilder()
        sb.appendLine("<!-- store-studio-diff -->")

        val baseCommit = diff.base.commit?.let { " ($it)" }.orEmpty()
        val app = appName?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        val title = "### 📸 Store Studio diff$app — " +
            "${code(diff.base.ref)}$baseCommit → ${code(diff.head.ref)}"
        sb.appendLine(title)
        sb.appendLine()
        sb.appendLine("_Base: ${describeBaseSource(diff)}._")
        sb.appendLine()

        sb.appendLine("| Change | Count |")
        sb.appendLine("|---|---:|")
        sb.appendLine("| 🟢 Added | ${diff.summary.added} |")
        sb.appendLine("| 🔴 Removed | ${diff.summary.removed} |")
        sb.appendLine("| 🟠 Modified | ${diff.summary.modified} |")
        sb.appendLine("| 🔵 Reordered | ${diff.summary.reordered} |")
        sb.appendLine("| 🟠🔵 Moved & modified | ${diff.summary.movedAndModified} |")
        sb.appendLine("| 🟣 Replaced | ${diff.summary.replaced} |")
        sb.appendLine("| ⚪ Unchanged | ${diff.summary.unchanged} |")

        val nonUnchanged = diff.changes.filter { it.type != DiffChangeType.UNCHANGED }
        if (nonUnchanged.isEmpty()) {
            sb.appendLine()
            sb.appendLine("<p>No visual changes detected.</p>")
        } else {
            sb.appendLine()
            sb.appendLine("<details><summary>Changed items (${nonUnchanged.size})</summary>")
            sb.appendLine()
            sb.appendLine("| Bucket | Id | Change | Position |")
            sb.appendLine("|---|---|---|---|")

            for (change in nonUnchanged.take(MAX_DISPLAYED_CHANGES)) {
                val bucketEscaped = escapeTable(change.bucket)
                val idEscaped = escapeTable(change.id)
                val labelEscaped = escapeTable(formatChangeLabel(change))
                val posEscaped = escapeTable(formatPosition(change))
                sb.appendLine("| $bucketEscaped | $idEscaped | $labelEscaped | $posEscaped |")
            }

            if (nonUnchanged.size > MAX_DISPLAYED_CHANGES) {
                val remaining = nonUnchanged.size - MAX_DISPLAYED_CHANGES
                sb.appendLine(
                    "| ... | ... | ... and $remaining more, see HTML report | ... |",
                )
            }

            sb.appendLine("</details>")
        }

        val fallbackCount = diff.changes.count {
            it.headEngine.equals("ROBOLECTRIC", ignoreCase = true)
        }
        if (fallbackCount > 0) {
            sb.appendLine()
            sb.appendLine(
                "> ⚙️ **Note:** $fallbackCount asset(s) were rendered using the Robolectric Native Graphics fallback engine.",
            )
        }

        return sb.toString().trimEnd()
    }

    /** Git refs may contain backticks, which would break out of the inline code span. */
    private fun code(ref: String) = "`" + ref.replace('`', '\'') + "`"

    private fun formatChangeLabel(change: DiffChange): String {
        val delta = change.pHashDistance?.let { " (Δ $it)" }.orEmpty()
        val textDriftDetail = when {
            change.semanticType == SemanticModificationType.VISUAL_LAYOUT_ONLY ->
                " *(layout only)*"

            change.semanticType == SemanticModificationType.TEXT_AND_VISUAL_MODIFIED -> {
                val remCount = change.removedTexts.size
                val addCount = change.addedTexts.size
                if (remCount > 0 || addCount > 0) {
                    " *(text: +$addCount, -$remCount)*"
                } else {
                    " *(text modified)*"
                }
            }

            else -> ""
        }
        val overflowNotice = if (change.hasVisualOverflow) " ⚠️*(overflow)*" else ""
        val engineNotice = if (change.headEngine.equals(
                "ROBOLECTRIC",
                ignoreCase = true,
            )
        ) {
            " ⚙️*(fallback)*"
        } else {
            ""
        }
        val suffix = "$delta$textDriftDetail$overflowNotice$engineNotice"
        return when (change.type) {
            DiffChangeType.ADDED -> "Added$overflowNotice$engineNotice"
            DiffChangeType.REMOVED -> "Removed"
            DiffChangeType.MODIFIED -> "Modified$suffix"
            DiffChangeType.REORDERED -> "Reordered$overflowNotice$engineNotice"
            DiffChangeType.MOVED_AND_MODIFIED -> "Moved & modified$suffix"
            DiffChangeType.REPLACED -> "Replaced$suffix"
            DiffChangeType.UNCHANGED -> "Unchanged$overflowNotice$engineNotice"
        }
    }

    private fun formatPosition(change: DiffChange): String = when {
        change.basePosition != null && change.headPosition != null &&
            change.basePosition != change.headPosition ->
            "${change.basePosition} → ${change.headPosition}"

        change.headPosition != null -> change.headPosition.toString()

        change.basePosition != null -> "${change.basePosition} (removed)"

        else -> "-"
    }

    private fun escapeTable(text: String): String = text
        .replace("\r\n", " ")
        .replace("\n", " ")
        .replace("\r", " ")
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("|", "\\|")
        .replace("`", "&#96;")

    /** Where the base fingerprints came from, so reviewers know what the diff is relative to. */
    internal fun describeBaseSource(diff: DiffDocument): String = when (diff.base.source) {
        "RENDERED_REF" -> "${code(diff.base.ref)} rendered on this machine"
        "BASE_EXPORT" -> "export of the base revision (${code(diff.base.ref)})"
        "GIT_BASELINE" -> "baseline committed at ${code(diff.base.ref)}"
        "LOCAL_DISK" -> "local baseline file"
        else -> "no baseline found, every image is new"
    }
}
