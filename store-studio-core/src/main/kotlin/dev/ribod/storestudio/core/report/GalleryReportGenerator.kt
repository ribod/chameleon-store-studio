package dev.ribod.storestudio.core.report

import dev.ribod.storestudio.core.report.model.AssetStatus
import dev.ribod.storestudio.core.report.model.GalleryPageModel
import dev.ribod.storestudio.core.report.model.ReportAssetItem
import dev.ribod.storestudio.model.StoreDeviceSlot
import dev.ribod.storestudio.model.naturalDisplayOrder

/**
 * Escapes characters for HTML text and attribute safety.
 */
internal object HtmlEscaper {
    fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

/** Prevents `</script>` / `<!--` from terminating an inline JSON script block; JSON.parse restores `<`. */
internal fun String.escapeForScriptBlock(): String = replace("<", "\\u003c")

/**
 * Generates self-contained standalone HTML gallery reports.
 */
public object GalleryReportGenerator {

    /**
     * Renders [model] into a single-file, zero-dependency HTML document.
     */
    public fun generate(model: GalleryPageModel): String {
        val baseCss = ReportResources.text("base.css")
        val galleryCss = ReportResources.text("gallery.css")
        val previewCss = ReportResources.text("store-preview.css")
        val galleryJs = ReportResources.text("gallery.js")

        val sb = StringBuilder()
        sb.appendLine("<!DOCTYPE html>")
        sb.appendLine("<html lang=\"en\">")
        sb.appendLine("<head>")
        sb.appendLine("  <meta charset=\"utf-8\">")
        sb.appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        sb.appendLine(
            "  <title>${HtmlEscaper.escape(model.appName)} - Store Studio Gallery</title>",
        )
        if (model.appIconDataUri != null) {
            sb.appendLine(
                "  <link rel=\"icon\" href=\"${HtmlEscaper.escape(model.appIconDataUri)}\">",
            )
        }
        sb.appendLine("  <style>")
        sb.appendLine(baseCss)
        sb.appendLine(galleryCss)
        sb.appendLine(previewCss)
        sb.appendLine("  </style>")
        sb.appendLine("</head>")
        sb.appendLine("<body>")

        appendHeader(sb, model)

        sb.appendLine("<main class=\"main-content\">")
        appendGallerySection(sb, model)
        appendPreviewSection(sb, model)
        sb.appendLine("</main>")

        appendLightbox(sb)

        sb.appendLine("  <script id=\"store-studio-data\" type=\"application/json\">")
        sb.appendLine(model.toJson().escapeForScriptBlock())
        sb.appendLine("  </script>")
        sb.appendLine("  <script id=\"store-catalog-data\" type=\"application/json\">")
        sb.appendLine(StoreLocaleCatalog.toJson().escapeForScriptBlock())
        sb.appendLine("  </script>")
        sb.appendLine("  <script id=\"store-metadata-data\" type=\"application/json\">")
        sb.appendLine(model.metadataJson().escapeForScriptBlock())
        sb.appendLine("  </script>")
        sb.appendLine("  <script>")
        sb.appendLine(galleryJs)
        sb.appendLine("  </script>")
        sb.appendLine("</body>")
        sb.appendLine("</html>")

        return sb.toString()
    }

    private fun appendHeader(sb: StringBuilder, model: GalleryPageModel) {
        sb.appendLine("<header class=\"app-header\">")
        sb.appendLine("  <div class=\"brand\">")
        if (model.appIconDataUri != null) {
            sb.appendLine(
                "    <div class=\"brand-icon\">" +
                    "<img class=\"brand-icon-img\" src=\"${HtmlEscaper.escape(
                        model.appIconDataUri,
                    )}\" " +
                    "alt=\"${HtmlEscaper.escape(model.appName)}\"></div>",
            )
        } else {
            sb.appendLine("    <div class=\"brand-icon\">✦</div>")
        }
        sb.appendLine("    <div class=\"brand-info\">")
        sb.appendLine("      <h1>${HtmlEscaper.escape(model.appName)}</h1>")
        sb.appendLine(
            "      <p>${HtmlEscaper.escape(model.developerName)} · ${model.generatedAt}</p>",
        )
        sb.appendLine("    </div>")
        sb.appendLine("  </div>")

        sb.appendLine("  <div class=\"stats-bar\">")
        sb.appendLine(
            "    <div class=\"stat-pill\">Assets: <strong>" +
                "${model.summary.totalAssets}</strong></div>",
        )
        sb.appendLine(
            "    <div class=\"stat-pill\">Stores: <strong>" +
                "${model.summary.storeCount}</strong></div>",
        )
        sb.appendLine(
            "    <div class=\"stat-pill\">Locales: <strong>" +
                "${model.summary.localeCount}</strong></div>",
        )
        if (model.summary.valid) {
            sb.appendLine(
                "    <div class=\"stat-pill success\">" +
                    "${StoreReportIcons.CHECK} Passed</div>",
            )
        } else {
            sb.appendLine(
                "    <div class=\"stat-pill\">Errors: <strong>" +
                    "${model.summary.errorCount}</strong></div>",
            )
        }
        sb.appendLine("  </div>")

        sb.appendLine("  <div class=\"header-actions\">")
        sb.appendLine("    <div class=\"view-tabs\">")
        sb.appendLine(
            "      <button class=\"view-tab active\" data-view=\"gallery\">" +
                "Gallery</button>",
        )
        sb.appendLine(
            "      <button class=\"view-tab\" data-view=\"preview\">" +
                "Store Preview</button>",
        )
        sb.appendLine("    </div>")
        sb.appendLine(
            "    <button class=\"btn-icon\" id=\"theme-toggle\" title=\"Toggle Theme\">" +
                "${StoreReportIcons.SUN_MOON}</button>",
        )
        sb.appendLine("  </div>")
        sb.appendLine("</header>")
    }

    private fun appendGallerySection(sb: StringBuilder, model: GalleryPageModel) {
        val sortedDeviceSlots = model.deviceSlots.sortedBy { slotName ->
            runCatching { StoreDeviceSlot.valueOf(slotName).naturalDisplayOrder }
                .getOrDefault(99)
        }
        val sortedAssets = model.assets.sortedWith(
            compareBy({ it.deviceSlot.naturalDisplayOrder }, { it.position }),
        )

        sb.appendLine("<section id=\"gallery-section\">")
        sb.appendLine("  <div class=\"toolbar\">")
        sb.appendLine("    <div class=\"filters-group\">")

        sb.appendLine(
            "      <button class=\"filter-pill store-filter-pill active\" " +
                "data-val=\"ALL\">All Stores</button>",
        )
        for (store in model.stores) {
            val label = if (store == "PLAY_STORE") "Google Play" else "App Store"
            sb.appendLine(
                "      <button class=\"filter-pill store-filter-pill\" " +
                    "data-val=\"$store\">$label</button>",
            )
        }

        sb.appendLine("      <span style=\"color: var(--color-border)\">|</span>")
        sb.appendLine(
            "      <button class=\"filter-pill device-filter-pill active\" " +
                "data-val=\"ALL\">All Devices</button>",
        )
        for (device in sortedDeviceSlots) {
            sb.appendLine(
                "      <button class=\"filter-pill device-filter-pill\" " +
                    "data-val=\"$device\">$device</button>",
            )
        }

        sb.appendLine("      <select class=\"filter-select\" id=\"locale-select\">")
        sb.appendLine("        <option value=\"ALL\">All Locales</option>")
        for (locale in model.locales) {
            sb.appendLine("        <option value=\"$locale\">$locale</option>")
        }
        sb.appendLine("      </select>")
        sb.appendLine("    </div>")

        sb.appendLine("    <div class=\"search-box\">")
        sb.appendLine("      <span class=\"search-icon\">${StoreReportIcons.SEARCH}</span>")
        sb.appendLine(
            "      <input type=\"text\" class=\"search-input\" id=\"search-input\" " +
                "placeholder=\"Search screenshot ID or slot...\">",
        )
        sb.appendLine("    </div>")
        sb.appendLine("  </div>")

        sb.appendLine("  <div class=\"gallery-grid\">")
        for (asset in sortedAssets) {
            appendAssetCard(sb, asset)
        }
        sb.appendLine("  </div>")
        sb.appendLine("</section>")
    }

    private fun appendAssetCard(sb: StringBuilder, asset: ReportAssetItem) {
        val statusClass = when (asset.status) {
            AssetStatus.VALID -> "valid"
            AssetStatus.WARNING -> "warning"
            AssetStatus.ERROR -> "error"
        }
        val slotName = asset.deviceSlot.name
        val targetName = asset.target.name
        val posFmt = "#%02d".format(asset.position)

        sb.appendLine(
            "    <div class=\"asset-card\" data-store=\"$targetName\" " +
                "data-device=\"$slotName\" data-locale=\"${HtmlEscaper.escape(
                    asset.storeLocale,
                )}\" " +
                "data-slot-key=\"${HtmlEscaper.escape(asset.slotKey)}\" " +
                "data-id=\"${HtmlEscaper.escape(asset.id)}\" " +
                "data-uri=\"${HtmlEscaper.escape(asset.dataUri)}\" " +
                "data-fullres=\"${HtmlEscaper.escape(asset.fullResRelativePath)}\" " +
                "data-dims=\"${asset.widthPx} × ${asset.heightPx}\" " +
                "data-format=\"${asset.format.name}\" " +
                "data-sha=\"${HtmlEscaper.escape(asset.pixelSha256)}\" " +
                "data-phash=\"${HtmlEscaper.escape(asset.pHash)}\">",
        )

        sb.appendLine("      <div class=\"asset-card-header\">")
        sb.appendLine("        <div class=\"badge-group\">")
        sb.appendLine("          <span class=\"badge badge-position\">$posFmt</span>")
        sb.appendLine("          <span class=\"badge badge-slot\">$slotName</span>")
        sb.appendLine(
            "          <span class=\"badge badge-locale\">${HtmlEscaper.escape(
                asset.storeLocale,
            )}</span>",
        )
        if (asset.engine.equals("ROBOLECTRIC", ignoreCase = true)) {
            sb.appendLine(
                "          <span class=\"badge badge-engine\" title=\"Rendered via Robolectric Native Graphics fallback\">⚙️ ROBOLECTRIC</span>",
            )
        }
        sb.appendLine("        </div>")
        sb.appendLine(
            "        <span class=\"badge badge-status $statusClass\">" +
                "${asset.status}</span>",
        )
        sb.appendLine("      </div>")

        sb.appendLine("      <div class=\"asset-card-preview\">")
        sb.appendLine(
            "        <img class=\"asset-img\" src=\"${HtmlEscaper.escape(asset.dataUri)}\" " +
                "alt=\"${HtmlEscaper.escape(asset.slotKey)}\" loading=\"lazy\">",
        )
        sb.appendLine("      </div>")

        sb.appendLine("      <div class=\"asset-card-footer\">")
        sb.appendLine(
            "        <span class=\"asset-id-text\" " +
                "title=\"${HtmlEscaper.escape(asset.id)}\">${HtmlEscaper.escape(asset.id)}</span>",
        )
        sb.appendLine(
            "        <span class=\"asset-dims\">${asset.widthPx} × " +
                "${asset.heightPx}</span>",
        )
        sb.appendLine("      </div>")
        sb.appendLine("    </div>")
    }

    private fun appendPreviewSection(sb: StringBuilder, model: GalleryPageModel) {
        sb.appendLine("<section id=\"preview-section\" style=\"display: none;\">")
        sb.appendLine("  <div class=\"preview-container\">")
        sb.appendLine("    <div class=\"preview-controls\">")
        sb.appendLine("      <div class=\"store-selector\">")
        sb.appendLine(
            "        <button class=\"store-btn active play-store\" " +
                "data-store-target=\"PLAY_STORE\">Google Play</button>",
        )
        sb.appendLine(
            "        <button class=\"store-btn app-store\" " +
                "data-store-target=\"APP_STORE\">Apple App Store</button>",
        )
        sb.appendLine("      </div>")
        sb.appendLine("      <div class=\"preview-controls-right\">")
        sb.appendLine(
            "        <select class=\"preview-locale-select\" id=\"preview-section-locale\">",
        )
        if (model.locales.size > 1) {
            sb.appendLine("          <option value=\"ALL\">All Locales</option>")
        }
        for (loc in model.locales) {
            sb.appendLine("          <option value=\"$loc\">$loc</option>")
        }
        sb.appendLine("        </select>")
        sb.appendLine(
            "        <span class=\"preview-tagline\">Store Page Simulation</span>",
        )
        sb.appendLine("      </div>")
        sb.appendLine("    </div>")

        ListingPreviewReportGenerator.appendGooglePlayMockup(sb, model)
        ListingPreviewReportGenerator.appendAppleAppStoreMockup(sb, model)

        sb.appendLine("  </div>")
        sb.appendLine("</section>")
    }

    private fun appendLightbox(sb: StringBuilder) {
        sb.appendLine("<div class=\"lightbox-modal\" id=\"lightbox-modal\">")
        sb.appendLine("  <div class=\"lightbox-dialog\">")
        sb.appendLine("    <div class=\"lightbox-preview\">")
        sb.appendLine("      <img id=\"modal-img\" src=\"\" alt=\"Preview\">")
        sb.appendLine("    </div>")
        sb.appendLine("    <div class=\"lightbox-sidebar\">")
        sb.appendLine(
            "      <button class=\"btn-icon lightbox-close\" id=\"modal-close\">" +
                "${StoreReportIcons.CLOSE}</button>",
        )
        sb.appendLine("      <div class=\"detail-row\">")
        sb.appendLine("        <span class=\"detail-label\">Slot Key</span>")
        sb.appendLine("        <span class=\"detail-val\" id=\"modal-slot\"></span>")
        sb.appendLine("      </div>")
        sb.appendLine("      <div class=\"detail-row\">")
        sb.appendLine("        <span class=\"detail-label\">Marketplace</span>")
        sb.appendLine("        <span class=\"detail-val\" id=\"modal-target\"></span>")
        sb.appendLine("      </div>")
        sb.appendLine("      <div class=\"detail-row\">")
        sb.appendLine("        <span class=\"detail-label\">Locale</span>")
        sb.appendLine("        <span class=\"detail-val\" id=\"modal-locale\"></span>")
        sb.appendLine("      </div>")
        sb.appendLine("      <div class=\"detail-row\">")
        sb.appendLine("        <span class=\"detail-label\">Dimensions</span>")
        sb.appendLine("        <span class=\"detail-val\" id=\"modal-dims\"></span>")
        sb.appendLine("      </div>")
        sb.appendLine("      <div class=\"detail-row\">")
        sb.appendLine("        <span class=\"detail-label\">Format</span>")
        sb.appendLine("        <span class=\"detail-val\" id=\"modal-format\"></span>")
        sb.appendLine("      </div>")
        sb.appendLine("      <div class=\"detail-row\">")
        sb.appendLine("        <span class=\"detail-label\">Pixel SHA-256</span>")
        sb.appendLine("        <span class=\"detail-val mono\" id=\"modal-sha\"></span>")
        sb.appendLine("      </div>")
        sb.appendLine("      <div class=\"detail-row\">")
        sb.appendLine("        <span class=\"detail-label\">Perceptual Hash (pHash)</span>")
        sb.appendLine("        <span class=\"detail-val mono\" id=\"modal-phash\"></span>")
        sb.appendLine("      </div>")
        sb.appendLine(
            "      <a id=\"modal-fullres-link\" href=\"#\" target=\"_blank\" " +
                "style=\"font-size: 13px; font-weight: 600; margin-top: 8px;\">" +
                "Open Full Resolution File ${StoreReportIcons.EXTERNAL_LINK}</a>",
        )
        sb.appendLine("    </div>")
        sb.appendLine("  </div>")
        sb.appendLine("</div>")
    }
}
