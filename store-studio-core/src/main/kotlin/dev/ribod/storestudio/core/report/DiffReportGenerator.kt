package dev.ribod.storestudio.core.report

import dev.ribod.storestudio.core.publish.resolveExportFile
import dev.ribod.storestudio.model.DiffChange
import dev.ribod.storestudio.model.DiffChangeType
import dev.ribod.storestudio.model.DiffDocument
import dev.ribod.storestudio.model.SemanticModificationType
import dev.ribod.storestudio.model.resolveNaturalDisplayOrder
import java.io.File
import java.util.Base64

/**
 * Generates self-contained standalone HTML visual diff reports (DD-11).
 */
public object DiffReportGenerator {

    /**
     * Renders [diffDocument] into a single-file, zero-dependency HTML document.
     *
     * @param diffDocument Structured diff document with classified visual changes.
     * @param headExportDirectory Root directory containing current exported images.
     * @param beforeImagesDirectory Optional directory containing resolved historical base images.
     * @param imageMode Embedding strategy (THUMBNAIL, FULL, or LINK).
     * @param maxThumbnailDimension Maximum pixel dimension for embedded thumbnails.
     * @param thumbnailQuality JPEG compression quality factor.
     * @param includeUnchanged Whether to render unchanged items in the report cards.
     * @param appName Application name shown in header.
     * @param developerName Developer or publisher name.
     * @param appIcon Optional path to app icon.
     */
    public fun generate(
        diffDocument: DiffDocument,
        headExportDirectory: File,
        beforeImagesDirectory: File? = null,
        imageMode: ReportImageMode = ReportImageMode.THUMBNAIL,
        maxThumbnailDimension: Int = 720,
        thumbnailQuality: Float = 0.85f,
        includeUnchanged: Boolean = false,
        appName: String = "Chameleon Store Studio",
        developerName: String = "Mobile Developer",
        appIcon: File? = null,
    ): String {
        val baseCss = ReportResources.text("base.css")
        val diffCss = ReportResources.text("diff.css")
        val diffJs = ReportResources.text("diff.js")

        val appIconDataUri = if (appIcon != null && appIcon.exists() && appIcon.isFile) {
            val bytes = appIcon.readBytes()
            val b64 = Base64.getEncoder().encodeToString(bytes)
            val mime = when (appIcon.extension.lowercase()) {
                "webp" -> "image/webp"
                "jpg", "jpeg" -> "image/jpeg"
                "svg" -> "image/svg+xml"
                else -> "image/png"
            }
            "data:$mime;base64,$b64"
        } else {
            null
        }

        val embeddedCache = mutableMapOf<String, EmbeddedImage>()

        fun resolveAndEmbed(file: File?, relPath: String): EmbeddedImage? {
            if (file == null || !file.exists() || !file.isFile) return null
            return embeddedCache.getOrPut(file.canonicalPath) {
                ImageEmbedder.embed(
                    imageFile = file,
                    relativePath = relPath,
                    mode = imageMode,
                    maxSidePx = maxThumbnailDimension,
                    quality = thumbnailQuality,
                )
            }
        }

        fun resolveBaseFile(change: DiffChange): File? {
            val b = change.baseImage ?: return null
            if (b == "UNAVAILABLE" || b == "AVAILABLE") return null
            if (beforeImagesDirectory != null) {
                // Absolute paths (written by StoreDiffTask) resolve to themselves; must stay inside.
                resolveExportFile(beforeImagesDirectory, b)?.let { return it }
                val slotKeySanitized = "${change.bucket}/${change.id}".replace('/', '_') + ".png"
                val bySlotKey = beforeImagesDirectory.resolve(slotKeySanitized)
                if (bySlotKey.exists()) return bySlotKey
                if (change.basePixelSha256 != null) {
                    val bySha = beforeImagesDirectory.resolve("${change.basePixelSha256}.png")
                    if (bySha.exists()) return bySha
                }
            }
            return null
        }

        val sb = StringBuilder()
        sb.appendLine("<!DOCTYPE html>")
        sb.appendLine("<html lang=\"en\" data-theme=\"dark\">")
        sb.appendLine("<head>")
        sb.appendLine("  <meta charset=\"utf-8\">")
        sb.appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        sb.appendLine(
            "  <title>${HtmlEscaper.escape(appName)} - Visual Diff Report</title>",
        )
        if (appIconDataUri != null) {
            sb.appendLine("  <link rel=\"icon\" href=\"${HtmlEscaper.escape(appIconDataUri)}\">")
        }
        sb.appendLine("  <style>")
        sb.appendLine(baseCss)
        sb.appendLine(diffCss)
        sb.appendLine("  </style>")
        sb.appendLine("</head>")
        sb.appendLine("<body>")

        val displayedChanges = if (includeUnchanged) {
            diffDocument.changes
        } else {
            diffDocument.changes.filter { it.type != DiffChangeType.UNCHANGED }
        }

        appendHeader(sb, diffDocument, appName, appIconDataUri)
        appendToolbar(sb, displayedChanges)

        sb.appendLine("<main class=\"diff-content\">")

        if (displayedChanges.isEmpty()) {
            sb.appendLine("  <div class=\"empty-state\" id=\"diff-empty-banner\">")
            sb.appendLine("    <h2>No visual differences detected</h2>")
            sb.appendLine(
                "    <p>All assets strictly match historical baseline fingerprints.</p>",
            )
            sb.appendLine("  </div>")
        } else {
            val byBucket = displayedChanges.groupBy { it.bucket }
            for ((bucket, bucketChanges) in byBucket) {
                appendBucketSection(
                    sb = sb,
                    bucket = bucket,
                    changes = bucketChanges,
                    headExportDir = headExportDirectory,
                    resolveBase = ::resolveBaseFile,
                    embed = ::resolveAndEmbed,
                )
            }
            sb.appendLine(
                "  <div class=\"empty-state\" id=\"diff-empty-banner\" style=\"display:none;\">",
            )
            sb.appendLine("    <h2>No matching changes found</h2>")
            sb.appendLine("    <p>Try clearing active filters or search terms.</p>")
            sb.appendLine("  </div>")
        }
        sb.appendLine("</main>")

        sb.appendLine("  <script id=\"store-diff-data\" type=\"application/json\">")
        sb.appendLine(diffDocument.toJson().escapeForScriptBlock())
        sb.appendLine("  </script>")
        sb.appendLine("  <script>")
        sb.appendLine(diffJs)
        sb.appendLine("  </script>")
        sb.appendLine("</body>")
        sb.appendLine("</html>")

        return sb.toString()
    }

    private fun appendHeader(
        sb: StringBuilder,
        diff: DiffDocument,
        appName: String,
        appIconDataUri: String? = null,
    ) {
        val s = diff.summary
        sb.appendLine("<header class=\"diff-header\">")
        sb.appendLine("  <div class=\"diff-brand\">")
        if (appIconDataUri != null) {
            sb.appendLine(
                "    <div class=\"diff-brand-icon\">" +
                    "<img class=\"diff-brand-icon-img\" src=\"${HtmlEscaper.escape(
                        appIconDataUri,
                    )}\" " +
                    "alt=\"${HtmlEscaper.escape(appName)}\"></div>",
            )
        } else {
            sb.appendLine("    <div class=\"diff-brand-icon\">✦</div>")
        }
        sb.appendLine("    <div>")
        sb.appendLine("      <h1>${HtmlEscaper.escape(appName)} · Visual Diff</h1>")
        val baseCommit = diff.base.commit?.let { " ($it)" }.orEmpty()
        sb.appendLine("      <div class=\"diff-provenance\">")
        sb.appendLine(
            "        Comparing <code>${HtmlEscaper.escape(diff.base.ref)}</code>$baseCommit " +
                "→ <code>${HtmlEscaper.escape(diff.head.ref)}</code> · " +
                HtmlEscaper.escape(DiffSummaryMarkdown.describeBaseSource(diff).replace("`", "")),
        )
        sb.appendLine("      </div>")
        sb.appendLine("    </div>")
        sb.appendLine("  </div>")

        sb.appendLine("  <div class=\"diff-stats-bar\">")
        sb.appendLine(
            "    <div class=\"stat-chip stat-chip-added\" data-filter=\"ADDED\">" +
                "${StoreReportIcons.PLUS} Added: <strong>${s.added}</strong></div>",
        )
        sb.appendLine(
            "    <div class=\"stat-chip stat-chip-removed\" data-filter=\"REMOVED\">" +
                "${StoreReportIcons.MINUS} Removed: <strong>${s.removed}</strong></div>",
        )
        sb.appendLine(
            "    <div class=\"stat-chip stat-chip-modified\" data-filter=\"MODIFIED\">" +
                "${StoreReportIcons.EDIT_3} Modified: <strong>${s.modified}</strong></div>",
        )
        sb.appendLine(
            "    <div class=\"stat-chip stat-chip-reordered\" data-filter=\"REORDERED\">" +
                "${StoreReportIcons.ARROW_UP_DOWN} Reordered: " +
                "<strong>${s.reordered}</strong></div>",
        )
        sb.appendLine(
            "    <div class=\"stat-chip stat-chip-moved-modified\" " +
                "data-filter=\"MOVED_AND_MODIFIED\">" +
                "${StoreReportIcons.ARROW_UP_DOWN} Moved & Modified: " +
                "<strong>${s.movedAndModified}</strong></div>",
        )
        sb.appendLine(
            "    <div class=\"stat-chip stat-chip-replaced\" data-filter=\"REPLACED\">" +
                "${StoreReportIcons.REFRESH_CW} Replaced: <strong>${s.replaced}</strong></div>",
        )
        if (s.unchanged > 0) {
            sb.appendLine(
                "    <div class=\"stat-chip stat-chip-unchanged\" data-filter=\"UNCHANGED\">" +
                    "Unchanged: <strong>${s.unchanged}</strong></div>",
            )
        }
        sb.appendLine(
            "    <button class=\"btn-icon\" id=\"theme-toggle\" title=\"Toggle Theme\">" +
                "${StoreReportIcons.SUN_MOON}</button>",
        )
        sb.appendLine("  </div>")
        sb.appendLine("</header>")
    }

    private fun appendToolbar(sb: StringBuilder, displayedChanges: List<DiffChange>) {
        val sortedDeviceSlots = displayedChanges.mapNotNull { change ->
            val parts = change.bucket.split('/')
            if (parts.size >= 2) parts[1] else null
        }.distinct().sortedBy { resolveNaturalDisplayOrder(it) }

        sb.appendLine("<div class=\"diff-toolbar\">")
        sb.appendLine("  <div class=\"filter-group\">")
        sb.appendLine(
            "    <button class=\"filter-btn active\" data-store=\"ALL\">All Stores</button>",
        )
        sb.appendLine(
            "    <button class=\"filter-btn\" data-store=\"PLAY_STORE\">Google Play</button>",
        )
        sb.appendLine(
            "    <button class=\"filter-btn\" data-store=\"APP_STORE\">App Store</button>",
        )
        if (sortedDeviceSlots.size > 1) {
            sb.appendLine("    <span style=\"color: var(--color-border)\">|</span>")
            sb.appendLine(
                "    <button class=\"filter-btn active\" data-device=\"ALL\">All Devices</button>",
            )
            for (slot in sortedDeviceSlots) {
                sb.appendLine(
                    "    <button class=\"filter-btn\" data-device=\"$slot\">$slot</button>",
                )
            }
        }
        sb.appendLine("  </div>")
        sb.appendLine("  <div class=\"search-field\">")
        sb.appendLine("    <span class=\"icon\">${StoreReportIcons.SEARCH}</span>")
        sb.appendLine(
            "    <input type=\"text\" id=\"search-input\" " +
                "placeholder=\"Search screenshot id or bucket...\">",
        )
        sb.appendLine("  </div>")
        sb.appendLine("</div>")
    }

    private fun appendBucketSection(
        sb: StringBuilder,
        bucket: String,
        changes: List<DiffChange>,
        headExportDir: File,
        resolveBase: (DiffChange) -> File?,
        embed: (File?, String) -> EmbeddedImage?,
    ) {
        val parts = bucket.split('/')
        val target = parts.getOrNull(0) ?: "STORE"
        val slot = parts.getOrNull(1) ?: "SLOT"
        val locale = parts.getOrNull(2) ?: "LOCALE"
        val targetLabel = if (target == "PLAY_STORE") "Google Play" else "App Store"

        sb.appendLine(
            "<section class=\"bucket-section\" data-target=\"$target\" " +
                "data-slot=\"$slot\" data-locale=\"$locale\">",
        )
        sb.appendLine("  <div class=\"bucket-header\">")
        sb.appendLine("    <div class=\"bucket-title\">")
        sb.appendLine("      <span>$targetLabel · $slot · $locale</span>")
        sb.appendLine("      <span class=\"bucket-tag\">${changes.size} item(s)</span>")
        sb.appendLine("    </div>")
        sb.appendLine("  </div>")

        appendBucketStrip(sb, changes, headExportDir, resolveBase, embed)

        sb.appendLine("  <div class=\"diff-cards-grid\">")
        for (change in changes) {
            appendChangeCard(sb, change, headExportDir, resolveBase, embed)
        }
        sb.appendLine("  </div>")
        sb.appendLine("</section>")
    }

    private fun appendBucketStrip(
        sb: StringBuilder,
        changes: List<DiffChange>,
        headExportDir: File,
        resolveBase: (DiffChange) -> File?,
        embed: (File?, String) -> EmbeddedImage?,
    ) {
        sb.appendLine("  <div class=\"bucket-strip\">")

        sb.appendLine("    <div class=\"strip-row\">")
        sb.appendLine("      <span class=\"strip-row-label\">Base</span>")
        val baseItems = changes.filter { it.basePosition != null }
            .sortedBy { it.basePosition }
        for (item in baseItems) {
            val baseFile = resolveBase(item)
            val embedded = embed(baseFile, "base_${item.id}.png")
            sb.appendLine(
                "      <div class=\"strip-item\" " +
                    "title=\"Base #${item.basePosition} - ${HtmlEscaper.escape(item.id)}\">",
            )
            if (embedded != null) {
                sb.appendLine(
                    "        <img src=\"${HtmlEscaper.escape(
                        embedded.dataUri,
                    )}\" alt=\"${HtmlEscaper.escape(item.id)}\">",
                )
            } else {
                sb.appendLine(
                    "        <span class=\"strip-item-placeholder\">${HtmlEscaper.escape(
                        item.id,
                    )}</span>",
                )
            }
            sb.appendLine("        <span class=\"strip-item-pos\">#${item.basePosition}</span>")
            sb.appendLine("      </div>")
        }
        sb.appendLine("    </div>")

        sb.appendLine("    <div class=\"strip-row\">")
        sb.appendLine("      <span class=\"strip-row-label\">Head</span>")
        val headItems = changes.filter { it.headPosition != null }
            .sortedBy { it.headPosition }
        for (item in headItems) {
            val headFile = item.headImage?.let { resolveExportFile(headExportDir, it) }
            val embedded = embed(headFile, item.headImage.orEmpty())
            sb.appendLine(
                "      <div class=\"strip-item\" " +
                    "title=\"Head #${item.headPosition} - ${HtmlEscaper.escape(item.id)}\">",
            )
            if (embedded != null) {
                sb.appendLine(
                    "        <img src=\"${HtmlEscaper.escape(
                        embedded.dataUri,
                    )}\" alt=\"${HtmlEscaper.escape(item.id)}\">",
                )
            } else {
                sb.appendLine(
                    "        <span class=\"strip-item-placeholder\">${HtmlEscaper.escape(
                        item.id,
                    )}</span>",
                )
            }
            sb.appendLine("        <span class=\"strip-item-pos\">#${item.headPosition}</span>")
            sb.appendLine("      </div>")
        }
        sb.appendLine("    </div>")

        sb.appendLine("  </div>")
    }

    private fun appendChangeCard(
        sb: StringBuilder,
        change: DiffChange,
        headExportDir: File,
        resolveBase: (DiffChange) -> File?,
        embed: (File?, String) -> EmbeddedImage?,
    ) {
        val typeClass = when (change.type) {
            DiffChangeType.ADDED -> "card-added"
            DiffChangeType.REMOVED -> "card-removed"
            DiffChangeType.MODIFIED -> "card-modified"
            DiffChangeType.REORDERED -> "card-reordered"
            DiffChangeType.MOVED_AND_MODIFIED -> "card-moved-modified"
            DiffChangeType.REPLACED -> "card-replaced"
            DiffChangeType.UNCHANGED -> "card-unchanged"
        }

        val badgeClass = when (change.type) {
            DiffChangeType.ADDED -> "badge-added"
            DiffChangeType.REMOVED -> "badge-removed"
            DiffChangeType.MODIFIED -> "badge-modified"
            DiffChangeType.REORDERED -> "badge-reordered"
            DiffChangeType.MOVED_AND_MODIFIED -> "badge-moved-modified"
            DiffChangeType.REPLACED -> "badge-replaced"
            DiffChangeType.UNCHANGED -> "badge-unchanged"
        }

        val badgeIcon = when (change.type) {
            DiffChangeType.ADDED -> StoreReportIcons.PLUS
            DiffChangeType.REMOVED -> StoreReportIcons.MINUS
            DiffChangeType.MODIFIED -> StoreReportIcons.EDIT_3
            DiffChangeType.REORDERED -> StoreReportIcons.ARROW_UP_DOWN
            DiffChangeType.MOVED_AND_MODIFIED -> StoreReportIcons.ARROW_UP_DOWN
            DiffChangeType.REPLACED -> StoreReportIcons.REFRESH_CW
            DiffChangeType.UNCHANGED -> StoreReportIcons.CHECK
        }

        val deltaText = change.pHashDistance?.let { " (Δ $it)" }.orEmpty()
        val badgeLabel = "${change.type.name.replace('_', ' ')}$deltaText"

        val posText = when {
            change.basePosition != null && change.headPosition != null &&
                change.basePosition != change.headPosition ->
                "#${change.basePosition} → #${change.headPosition}"

            change.headPosition != null -> "#${change.headPosition}"

            change.basePosition != null -> "#${change.basePosition} (removed)"

            else -> ""
        }

        sb.appendLine(
            "    <div class=\"diff-card $typeClass\" data-type=\"${change.type.name}\" " +
                "data-id=\"${HtmlEscaper.escape(change.id)}\" " +
                "data-bucket=\"${HtmlEscaper.escape(change.bucket)}\">",
        )
        val semanticBadgeHtml = when (change.semanticType) {
            SemanticModificationType.VISUAL_LAYOUT_ONLY ->
                " <span class=\"diff-card-badge badge-semantic-layout\">🎨 Layout Only</span>"

            SemanticModificationType.TEXT_AND_VISUAL_MODIFIED ->
                " <span class=\"diff-card-badge badge-semantic-text\">📝 Text Modified</span>"

            else -> ""
        }

        val overflowBadgeHtml = if (change.hasVisualOverflow) {
            " <span class=\"diff-card-badge badge-semantic-overflow\">⚠️ Visual Overflow</span>"
        } else {
            ""
        }

        val textChangesCount = change.removedTexts.size + change.addedTexts.size
        val semanticPillHtml = when {
            change.semanticType == SemanticModificationType.VISUAL_LAYOUT_ONLY ->
                " <span class=\"diff-meta-pill diff-pill-layout\">0 text drift</span>"

            textChangesCount > 0 ->
                " <span class=\"diff-meta-pill diff-pill-text-drift\">" +
                    "$textChangesCount texts changed (-${change.removedTexts.size}, " +
                    "+${change.addedTexts.size})</span>"

            else -> ""
        }

        val overflowPillHtml = if (change.hasVisualOverflow) {
            " <span class=\"diff-meta-pill diff-pill-overflow\">⚠️ Text Clipped</span>"
        } else {
            ""
        }

        val enginePillHtml = if (change.headEngine.equals("ROBOLECTRIC", ignoreCase = true)) {
            " <span class=\"diff-meta-pill diff-pill-engine\" title=\"Rendered via Robolectric Native Graphics fallback\">⚙️ Fallback (Robolectric)</span>"
        } else {
            ""
        }

        sb.appendLine("      <div class=\"diff-card-header\">")
        sb.appendLine(
            "        <div class=\"diff-card-badges\">" +
                "<span class=\"diff-card-badge $badgeClass\">" +
                "$badgeIcon $badgeLabel</span>" +
                "$semanticBadgeHtml$overflowBadgeHtml</div>",
        )
        sb.appendLine("        <div class=\"diff-card-meta\">")
        sb.appendLine(
            "          <span class=\"diff-id-label\">" +
                "${HtmlEscaper.escape(change.id)}</span>",
        )
        if (posText.isNotBlank()) {
            sb.appendLine("          <span class=\"diff-pos-badge\">$posText</span>")
        }
        if (semanticPillHtml.isNotBlank()) {
            sb.appendLine("         $semanticPillHtml")
        }
        if (overflowPillHtml.isNotBlank()) {
            sb.appendLine("         $overflowPillHtml")
        }
        if (enginePillHtml.isNotBlank()) {
            sb.appendLine("         $enginePillHtml")
        }
        sb.appendLine("        </div>")
        sb.appendLine("      </div>")

        val baseFile = resolveBase(change)
        val headFile = change.headImage?.let { resolveExportFile(headExportDir, it) }
        val baseEmbedded = embed(baseFile, "base_${change.id}.png")
        val headEmbedded = embed(headFile, change.headImage.orEmpty())

        if (baseEmbedded != null && headEmbedded != null) {
            appendInteractiveViewer(sb, baseEmbedded, headEmbedded)
        } else if (headEmbedded != null) {
            appendSingleImageViewer(sb, headEmbedded, "Current (Head)")
            if (change.basePixelSha256 != null && baseEmbedded == null) {
                appendHashFallbackBox(sb, change)
            }
        } else if (baseEmbedded != null) {
            appendSingleImageViewer(sb, baseEmbedded, "Baseline (Base)")
        } else {
            appendHashFallbackBox(sb, change)
        }

        val hasTextDiff = change.removedTexts.isNotEmpty() || change.addedTexts.isNotEmpty()
        if (hasTextDiff || change.hasVisualOverflow) {
            sb.appendLine("      <div class=\"diff-text-box\">")
            if (hasTextDiff) {
                sb.appendLine("        <div class=\"diff-text-title\">Semantic Text Drift</div>")
                for (removed in change.removedTexts) {
                    sb.appendLine(
                        "        <div class=\"diff-text-line diff-text-line-removed\">" +
                            "- ${HtmlEscaper.escape(removed)}</div>",
                    )
                }
                for (added in change.addedTexts) {
                    sb.appendLine(
                        "        <div class=\"diff-text-line diff-text-line-added\">" +
                            "+ ${HtmlEscaper.escape(added)}</div>",
                    )
                }
            }
            if (change.hasVisualOverflow) {
                sb.appendLine("        <div class=\"diff-text-overflow-note\">")
                sb.appendLine("          <span class=\"diff-text-overflow-icon\">⚠️</span>")
                sb.appendLine(
                    "          <span><strong>Visual Text Overflow:</strong> One or more Text " +
                        "elements in this slot exceeded layout bounds and were clipped or " +
                        "truncated.</span>",
                )
                sb.appendLine("        </div>")
            }
            sb.appendLine("      </div>")
        }

        sb.appendLine("    </div>")
    }

    private fun appendInteractiveViewer(
        sb: StringBuilder,
        base: EmbeddedImage,
        head: EmbeddedImage,
    ) {
        sb.appendLine("      <div class=\"viewer-mode-bar\">")
        sb.appendLine("        <div class=\"viewer-modes\">")
        sb.appendLine(
            "          <button class=\"viewer-mode-btn active\" data-mode=\"side-by-side\">" +
                "${StoreReportIcons.COLUMNS} Side-by-side</button>",
        )
        sb.appendLine(
            "          <button class=\"viewer-mode-btn\" data-mode=\"slider\">" +
                "${StoreReportIcons.SPLIT} Split Slider</button>",
        )
        sb.appendLine(
            "          <button class=\"viewer-mode-btn\" data-mode=\"onion\">" +
                "${StoreReportIcons.LAYERS} Onion Skin</button>",
        )
        sb.appendLine(
            "          <button class=\"viewer-mode-btn\" data-mode=\"difference\">" +
                "${StoreReportIcons.CONTRAST} Difference</button>",
        )
        sb.appendLine("        </div>")
        sb.appendLine("      </div>")

        sb.appendLine("      <div class=\"viewer-stage\">")

        sb.appendLine("        <div class=\"viewer-stage-content\" data-mode=\"side-by-side\">")
        sb.appendLine("          <div class=\"stage-side-by-side\">")
        sb.appendLine("            <div class=\"stage-column\">")
        sb.appendLine("              <span class=\"stage-column-title\">Baseline (Before)</span>")
        sb.appendLine(
            "              <img class=\"stage-image\" src=\"${HtmlEscaper.escape(
                base.dataUri,
            )}\" " +
                "alt=\"Baseline image\">",
        )
        sb.appendLine("            </div>")
        sb.appendLine("            <div class=\"stage-column\">")
        sb.appendLine("              <span class=\"stage-column-title\">Current (After)</span>")
        sb.appendLine(
            "              <img class=\"stage-image\" src=\"${HtmlEscaper.escape(
                head.dataUri,
            )}\" " +
                "alt=\"Current image\">",
        )
        sb.appendLine("            </div>")
        sb.appendLine("          </div>")
        sb.appendLine("        </div>")

        sb.appendLine(
            "        <div class=\"viewer-stage-content\" data-mode=\"slider\" " +
                "style=\"display:none;\">",
        )
        sb.appendLine("          <div class=\"stage-slider-container\">")
        sb.appendLine(
            "            <img class=\"stage-slider-base\" src=\"${HtmlEscaper.escape(
                base.dataUri,
            )}\" " +
                "alt=\"Base slider\">",
        )
        sb.appendLine(
            "            <img class=\"stage-slider-head\" src=\"${HtmlEscaper.escape(
                head.dataUri,
            )}\" " +
                "alt=\"Head slider\">",
        )
        sb.appendLine("            <div class=\"stage-slider-divider\"></div>")
        sb.appendLine("          </div>")
        sb.appendLine("          <div class=\"stage-slider-toolbar\">")
        sb.appendLine("            <span class=\"stage-slider-label\">Baseline</span>")
        sb.appendLine(
            "            <input type=\"range\" class=\"stage-slider-input\" " +
                "min=\"0\" max=\"100\" value=\"50\">",
        )
        sb.appendLine("            <span class=\"stage-slider-label\">Current</span>")
        sb.appendLine("          </div>")
        sb.appendLine("        </div>")

        sb.appendLine(
            "        <div class=\"viewer-stage-content\" data-mode=\"onion\" " +
                "style=\"display:none;\">",
        )
        sb.appendLine("          <div class=\"stage-onion-container\">")
        sb.appendLine(
            "            <img class=\"stage-onion-base\" src=\"${HtmlEscaper.escape(
                base.dataUri,
            )}\" " +
                "alt=\"Base onion\">",
        )
        sb.appendLine(
            "            <img class=\"stage-onion-head\" src=\"${HtmlEscaper.escape(
                head.dataUri,
            )}\" " +
                "alt=\"Head onion\">",
        )
        sb.appendLine("          </div>")
        sb.appendLine("          <div class=\"stage-slider-toolbar\">")
        sb.appendLine(
            "            <span class=\"stage-slider-label stage-onion-label\">Opacity: 50%</span>",
        )
        sb.appendLine(
            "            <input type=\"range\" class=\"stage-slider-input stage-onion-input\" " +
                "min=\"0\" max=\"1\" step=\"0.01\" value=\"0.5\">",
        )
        sb.appendLine("          </div>")
        sb.appendLine("        </div>")

        sb.appendLine(
            "        <div class=\"viewer-stage-content\" data-mode=\"difference\" " +
                "style=\"display:none;\">",
        )
        sb.appendLine("          <div class=\"stage-difference-container\">")
        sb.appendLine(
            "            <img class=\"stage-difference-base\" src=\"${HtmlEscaper.escape(
                base.dataUri,
            )}\" " +
                "alt=\"Base difference\">",
        )
        sb.appendLine(
            "            <img class=\"stage-difference-head\" src=\"${HtmlEscaper.escape(
                head.dataUri,
            )}\" " +
                "alt=\"Head difference\">",
        )
        sb.appendLine("          </div>")
        sb.appendLine("        </div>")

        sb.appendLine("      </div>")
    }

    private fun appendSingleImageViewer(sb: StringBuilder, image: EmbeddedImage, title: String) {
        sb.appendLine("      <div class=\"viewer-stage\">")
        sb.appendLine("        <div class=\"stage-column\">")
        sb.appendLine("          <span class=\"stage-column-title\">$title</span>")
        sb.appendLine(
            "          <img class=\"stage-image\" src=\"${HtmlEscaper.escape(
                image.dataUri,
            )}\" alt=\"${HtmlEscaper.escape(title)}\">",
        )
        sb.appendLine("        </div>")
        sb.appendLine("      </div>")
    }

    private fun appendHashFallbackBox(sb: StringBuilder, change: DiffChange) {
        sb.appendLine("      <div class=\"hash-fallback-box\">")
        sb.appendLine("        <div class=\"hash-fallback-title\">")
        sb.appendLine("          ${StoreReportIcons.HASH} Historical Image Unavailable")
        sb.appendLine("        </div>")
        sb.appendLine(
            "        <p class=\"hash-fallback-desc\">" +
                "Baseline tracks cryptographic fingerprints without committing binary images " +
                "to Git (ADR-004). Interactive before/after comparisons require a local pixel " +
                "cache or CI base artifacts.</p>",
        )
        sb.appendLine("        <table class=\"hash-table\">")
        change.basePixelSha256?.let { sha ->
            val baseShort = sha.take(16)
            sb.appendLine(
                "          <tr><td>Base SHA-256</td><td><code>$baseShort…</code></td></tr>",
            )
        }
        change.headPixelSha256?.let { sha ->
            val headShort = sha.take(16)
            sb.appendLine(
                "          <tr><td>Head SHA-256</td><td><code>$headShort…</code></td></tr>",
            )
        }
        change.baseTextSha256?.let { sha ->
            val baseTextShort = sha.take(16)
            sb.appendLine(
                "          <tr><td>Base Text SHA</td><td><code>$baseTextShort…</code></td></tr>",
            )
        }
        change.headTextSha256?.let { sha ->
            val headTextShort = sha.take(16)
            sb.appendLine(
                "          <tr><td>Head Text SHA</td><td><code>$headTextShort…</code></td></tr>",
            )
        }
        if (change.pHashDistance != null) {
            sb.appendLine(
                "          <tr><td>pHash Distance</td><td><code>${change.pHashDistance}</code> " +
                    "(threshold 0.05)</td></tr>",
            )
        }
        sb.appendLine("        </table>")
        sb.appendLine("      </div>")
    }
}
