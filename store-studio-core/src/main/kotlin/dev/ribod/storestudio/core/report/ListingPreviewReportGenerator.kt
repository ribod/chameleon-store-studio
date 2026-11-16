package dev.ribod.storestudio.core.report

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.report.model.GalleryPageModel
import dev.ribod.storestudio.model.naturalDisplayOrder

/**
 * Generates standalone, pixel-authentic store listing product page preview reports.
 */
public object ListingPreviewReportGenerator {

    /**
     * Renders [model] into a dedicated store listing preview HTML document.
     */
    public fun generate(model: GalleryPageModel): String {
        val baseCss = ReportResources.text("base.css")
        val previewCss = ReportResources.text("store-preview.css")

        val sb = StringBuilder()
        sb.appendLine("<!DOCTYPE html>")
        sb.appendLine("<html lang=\"en\">")
        sb.appendLine("<head>")
        sb.appendLine("  <meta charset=\"utf-8\">")
        sb.appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        sb.appendLine(
            "  <title>${HtmlEscaper.escape(model.appName)} - Store Listing Preview</title>",
        )
        if (model.appIconDataUri != null) {
            sb.appendLine(
                "  <link rel=\"icon\" href=\"${HtmlEscaper.escape(model.appIconDataUri)}\">",
            )
        }
        sb.appendLine("  <style>")
        sb.appendLine(baseCss)
        sb.appendLine(previewCss)
        sb.appendLine("  </style>")
        sb.appendLine("</head>")
        sb.appendLine("<body>")

        sb.appendLine("<div class=\"preview-container\">")
        sb.appendLine("  <div class=\"preview-controls\">")
        sb.appendLine("    <div class=\"store-selector\">")
        sb.appendLine(
            "      <button class=\"store-btn active play-store\" " +
                "id=\"btn-play\" onclick=\"switchStore('PLAY_STORE')\">Google Play</button>",
        )
        sb.appendLine(
            "      <button class=\"store-btn app-store\" " +
                "id=\"btn-apple\" onclick=\"switchStore('APP_STORE')\">Apple App Store</button>",
        )
        sb.appendLine("    </div>")
        sb.appendLine("    <div class=\"preview-controls-right\">")
        sb.appendLine(
            "      <select class=\"preview-locale-select\" id=\"preview-locale\" " +
                "onchange=\"filterLocale(this.value)\">",
        )
        if (model.locales.size > 1) {
            sb.appendLine("        <option value=\"ALL\">All Locales</option>")
        }
        for (loc in model.locales) {
            sb.appendLine("        <option value=\"$loc\">$loc</option>")
        }
        sb.appendLine("      </select>")
        sb.appendLine(
            "      <span class=\"preview-tagline\">Store Page Simulation</span>",
        )
        sb.appendLine(
            "      <button class=\"btn-icon\" onclick=\"toggleTheme()\" " +
                "title=\"Toggle Theme\">${StoreReportIcons.SUN_MOON}</button>",
        )
        sb.appendLine("    </div>")
        sb.appendLine("  </div>")

        appendGooglePlayMockup(sb, model)
        appendAppleAppStoreMockup(sb, model)

        sb.appendLine("</div>")

        sb.appendLine("  <script id=\"store-catalog-data\" type=\"application/json\">")
        sb.appendLine(StoreLocaleCatalog.toJson().escapeForScriptBlock())
        sb.appendLine("  </script>")
        sb.appendLine("  <script id=\"store-metadata-data\" type=\"application/json\">")
        sb.appendLine(model.metadataJson().escapeForScriptBlock())
        sb.appendLine("  </script>")
        sb.appendLine("<script>")
        sb.appendLine(previewScript())
        sb.appendLine("</script>")
        sb.appendLine("</body>")
        sb.appendLine("</html>")

        return sb.toString()
    }

    private fun previewScript(): String = """
          var STORE_STRINGS = {};
          var APP_METADATA = {};
          try {
            var catEl = document.getElementById('store-catalog-data');
            if (catEl) STORE_STRINGS = JSON.parse(catEl.textContent);
            var metaEl = document.getElementById('store-metadata-data');
            if (metaEl) APP_METADATA = JSON.parse(metaEl.textContent);
          } catch (e) {}

          function resolveLocale(loc) {
            if (!loc || loc === 'ALL') return 'en-US';
            if (STORE_STRINGS[loc]) return loc;
            if (loc.indexOf('es') === 0 && STORE_STRINGS['es-MX']) return 'es-MX';
            return 'en-US';
          }

          function getNested(obj, path) {
            var parts = path.split('.');
            var curr = obj;
            for (var i = 0; i < parts.length; i++) {
              if (!curr) return undefined;
              curr = curr[parts[i]];
            }
            return curr;
          }

          function applyLocalization(loc) {
            var target = resolveLocale(loc);
            var dict = STORE_STRINGS[target] || STORE_STRINGS['en-US'] || {};
            var meta = (APP_METADATA && (APP_METADATA[loc] || APP_METADATA[target])) || {};

            document.querySelectorAll('[data-i18n]').forEach(function(el) {
              var key = el.getAttribute('data-i18n');
              var val = getNested(dict, key);
              if (val !== undefined && val !== null) {
                var svg = el.querySelector('svg');
                if (svg) {
                  el.innerHTML = svg.outerHTML + ' ' + val;
                } else {
                  el.textContent = val;
                }
              }
            });

            document.querySelectorAll('[data-meta]').forEach(function(el) {
              var field = el.getAttribute('data-meta');
              if (meta && meta[field]) {
                el.textContent = meta[field];
              }
            });
          }

          function toggleTheme() {
            var curr = document.documentElement.getAttribute('data-theme') || 'dark';
            var next = curr === 'light' ? 'dark' : 'light';
            document.documentElement.setAttribute('data-theme', next);
          }

          function switchStore(store) {
            var play = document.getElementById('play-mockup');
            var apple = document.getElementById('apple-mockup');
            var btnPlay = document.getElementById('btn-play');
            var btnApple = document.getElementById('btn-apple');
            if (store === 'APP_STORE') {
              play.style.display = 'none';
              apple.style.display = 'block';
              btnPlay.classList.remove('active');
              btnApple.classList.add('active');
            } else {
              play.style.display = 'block';
              apple.style.display = 'none';
              btnPlay.classList.add('active');
              btnApple.classList.remove('active');
            }
          }

          function filterLocale(loc) {
            document.querySelectorAll(
              '.play-screenshot-item, .apple-screenshot-item'
            ).forEach(function(el) {
              var itemLoc = el.getAttribute('data-locale');
              var matchLoc = (loc === 'ALL' || itemLoc === loc);
              var slotMatched = el.getAttribute('data-slot-visible') !== 'false';
              el.style.display = (matchLoc && slotMatched) ? 'block' : 'none';
            });
            document.querySelectorAll('.play-feature-banner').forEach(function(banner) {
              var bannerLoc = banner.getAttribute('data-locale');
              if (loc === 'ALL') {
                var isPrimary = banner.getAttribute('data-is-primary') === 'true';
                banner.style.display = isPrimary ? 'block' : 'none';
              } else {
                banner.style.display = (bannerLoc === loc) ? 'block' : 'none';
              }
            });
            applyLocalization(loc);
          }

          document.querySelectorAll('.play-device-tab').forEach(function(tab) {
            tab.addEventListener('click', function() {
              document.querySelectorAll('.play-device-tab').forEach(function(t) {
                t.classList.remove('active');
              });
              tab.classList.add('active');
              var slot = tab.getAttribute('data-slot');
              var locEl = document.getElementById('preview-locale');
              var currentLoc = locEl ? locEl.value : 'ALL';
              document.querySelectorAll('.play-screenshot-item').forEach(function(item) {
                var itemSlot = item.getAttribute('data-slot');
                var matchSlot = (slot === 'ALL' || itemSlot === slot);
                item.setAttribute('data-slot-visible', matchSlot ? 'true' : 'false');
                var itemLoc = item.getAttribute('data-locale');
                var matchLoc = (currentLoc === 'ALL' || itemLoc === currentLoc);
                item.style.display = (matchSlot && matchLoc) ? 'block' : 'none';
              });
            });
          });

          document.querySelectorAll('.apple-device-tab').forEach(function(tab) {
            tab.addEventListener('click', function() {
              document.querySelectorAll('.apple-device-tab').forEach(function(t) {
                t.classList.remove('active');
              });
              tab.classList.add('active');
              var slot = tab.getAttribute('data-slot');
              var locEl = document.getElementById('preview-locale');
              var currentLoc = locEl ? locEl.value : 'ALL';
              document.querySelectorAll('.apple-screenshot-item').forEach(function(item) {
                var itemSlot = item.getAttribute('data-slot');
                var matchSlot = (slot === 'ALL' || itemSlot === slot);
                item.setAttribute('data-slot-visible', matchSlot ? 'true' : 'false');
                var itemLoc = item.getAttribute('data-locale');
                var matchLoc = (currentLoc === 'ALL' || itemLoc === currentLoc);
                item.style.display = (matchSlot && matchLoc) ? 'block' : 'none';
              });
            });
          });

          var initLoc = document.getElementById('preview-locale');
          applyLocalization(initLoc ? initLoc.value : 'ALL');
    """.trimIndent()

    internal fun appendGooglePlayMockup(sb: StringBuilder, model: GalleryPageModel) {
        val playAssets = model.assets.filter { it.target == StoreTarget.PLAY_STORE }
        val featureGraphics = playAssets.filter { it.deviceSlot.name == "FEATURE_GRAPHIC" }
        val screenshots = playAssets
            .filter { it.deviceSlot.name != "FEATURE_GRAPHIC" }
            .sortedWith(compareBy({ it.deviceSlot.naturalDisplayOrder }, { it.position }))
        val primaryLocale = model.locales.firstOrNull() ?: "en-US"
        val initialLetter = model.appName.take(1).uppercase()

        sb.appendLine("  <div class=\"play-mockup\" id=\"play-mockup\">")

        sb.appendLine("    <div class=\"play-top-bar\">")
        sb.appendLine("      <div class=\"play-top-brand\">")
        sb.appendLine("        ${StoreReportIcons.GOOGLE_PLAY_LOGO}")
        sb.appendLine("        <span>Google Play</span>")
        sb.appendLine("      </div>")
        sb.appendLine("      <div class=\"play-top-nav-links\">")
        sb.appendLine(
            "        <span class=\"play-nav-link\" data-i18n=\"play.nav.0\">Games</span>",
        )
        sb.appendLine(
            "        <span class=\"play-nav-link active\" data-i18n=\"play.nav.1\">Apps</span>",
        )
        sb.appendLine(
            "        <span class=\"play-nav-link\" data-i18n=\"play.nav.2\">Movies</span>",
        )
        sb.appendLine(
            "        <span class=\"play-nav-link\" data-i18n=\"play.nav.3\">Books</span>",
        )
        sb.appendLine(
            "        <span class=\"play-nav-link\" data-i18n=\"play.nav.4\">Children</span>",
        )
        sb.appendLine("      </div>")
        sb.appendLine("      <div class=\"play-top-actions\">")
        sb.appendLine(
            "        <button class=\"play-icon-btn\" title=\"Search\">" +
                "${StoreReportIcons.SEARCH}</button>",
        )
        sb.appendLine(
            "        <div class=\"play-avatar\">${HtmlEscaper.escape(initialLetter)}</div>",
        )
        sb.appendLine("      </div>")
        sb.appendLine("    </div>")

        sb.appendLine("    <div class=\"play-main\">")
        sb.appendLine("      <div class=\"play-hero\">")
        if (model.appIconDataUri != null) {
            sb.appendLine("        <div class=\"play-app-icon\">")
            sb.appendLine(
                "          <img class=\"play-app-icon-img\" src=\"${HtmlEscaper.escape(
                    model.appIconDataUri,
                )}\" " +
                    "alt=\"${HtmlEscaper.escape(model.appName)}\">",
            )
            sb.appendLine("        </div>")
        } else {
            sb.appendLine(
                "        <div class=\"play-app-icon\">" +
                    "${HtmlEscaper.escape(initialLetter)}</div>",
            )
        }
        sb.appendLine("        <div class=\"play-hero-info\">")
        sb.appendLine(
            "          <h1 class=\"play-title\" data-meta=\"title\">" +
                "${HtmlEscaper.escape(model.appName)}</h1>",
        )
        sb.appendLine(
            "          <div class=\"play-dev-link\">" +
                "${HtmlEscaper.escape(model.developerName)} " +
                "${StoreReportIcons.CHECK}</div>",
        )
        sb.appendLine(
            "          <div class=\"play-tags\" data-i18n=\"play.tags\">" +
                "Contains ads · In-app purchases</div>",
        )
        sb.appendLine("          <div class=\"play-metrics-ribbon\">")
        sb.appendLine("            <div class=\"play-metric-item\">")
        sb.appendLine(
            "              <div class=\"play-metric-val\">4.8 " +
                "${StoreReportIcons.STAR_GOLD}</div>",
        )
        sb.appendLine(
            "              <div class=\"play-metric-label\" data-i18n=\"play.reviews\">" +
                "124K reviews</div>",
        )
        sb.appendLine("            </div>")
        sb.appendLine("            <div class=\"play-metric-item\">")
        sb.appendLine(
            "              <div class=\"play-metric-val\" data-meta=\"downloads\">10B+</div>",
        )
        sb.appendLine(
            "              <div class=\"play-metric-label\" data-i18n=\"play.downloads\">" +
                "Downloads</div>",
        )
        sb.appendLine("            </div>")
        sb.appendLine("            <div class=\"play-metric-item\">")
        sb.appendLine(
            "              <div class=\"play-metric-val\" data-meta=\"ageRating\">Everyone</div>",
        )
        sb.appendLine(
            "              <div class=\"play-metric-label\" data-i18n=\"play.contentRating\">" +
                "Rated for 3+</div>",
        )
        sb.appendLine("            </div>")
        sb.appendLine("          </div>")
        sb.appendLine("          <div class=\"play-actions-row\">")
        sb.appendLine(
            "            <button class=\"play-btn-install\" data-i18n=\"play.install\">" +
                "Install</button>",
        )
        sb.appendLine(
            "            <button class=\"play-btn-share\" data-i18n=\"play.share\">" +
                "${StoreReportIcons.SHARE} Share</button>",
        )
        sb.appendLine("          </div>")
        sb.appendLine("          <div class=\"play-device-hint\">")
        sb.appendLine("            ${StoreReportIcons.DEVICES}")
        sb.appendLine(
            "            <span data-i18n=\"play.deviceHint\">" +
                "This app is available on all your devices</span>",
        )
        sb.appendLine("          </div>")
        sb.appendLine("        </div>")
        sb.appendLine("      </div>")

        for (fg in featureGraphics) {
            val isPrimary = fg.storeLocale == primaryLocale
            val initialDisplay = if (isPrimary) "" else " style=\"display: none;\""
            sb.appendLine(
                "      <div class=\"play-feature-banner\" " +
                    "data-locale=\"${HtmlEscaper.escape(fg.storeLocale)}\" " +
                    "data-is-primary=\"$isPrimary\"$initialDisplay>",
            )
            sb.appendLine(
                "        <img src=\"${HtmlEscaper.escape(fg.dataUri)}\" " +
                    "alt=\"Feature Graphic (${HtmlEscaper.escape(fg.storeLocale)})\">",
            )
            sb.appendLine("      </div>")
        }

        val playSlots = screenshots.map { it.deviceSlot }.distinct()
            .sortedBy { it.naturalDisplayOrder }
        if (playSlots.size > 1) {
            sb.appendLine("      <div class=\"play-device-tabs\">")
            sb.appendLine(
                "        <button class=\"play-device-tab active\" " +
                    "data-slot=\"ALL\" data-i18n=\"play.allDevices\">All devices</button>",
            )
            for (slot in playSlots) {
                val label = when (slot.name) {
                    "PHONE" -> "Phone"
                    "TABLET_7" -> "7\" Tablet"
                    "TABLET_10" -> "10\" Tablet"
                    "WEAR" -> "Watch"
                    else -> slot.name
                }
                sb.appendLine(
                    "        <button class=\"play-device-tab\" " +
                        "data-slot=\"${slot.name}\">$label</button>",
                )
            }
            sb.appendLine("      </div>")
        }

        sb.appendLine("      <div class=\"play-carousel\">")
        for (ss in screenshots) {
            sb.appendLine(
                "        <div class=\"play-screenshot-item\" " +
                    "data-slot=\"${ss.deviceSlot.name}\" " +
                    "data-locale=\"${HtmlEscaper.escape(ss.storeLocale)}\">",
            )
            sb.appendLine(
                "          <img src=\"${HtmlEscaper.escape(
                    ss.dataUri,
                )}\" alt=\"${HtmlEscaper.escape(ss.id)}\">",
            )
            sb.appendLine("        </div>")
        }
        sb.appendLine("      </div>")

        val metadata = model.metadataByLocale.values.firstOrNull()
        val fullText = when {
            metadata != null && metadata.fullDescription.isNotBlank() -> metadata.fullDescription
            metadata != null && metadata.shortDescription.isNotBlank() -> metadata.shortDescription
            else -> "Get the official mobile application. Explore content tailored for you."
        }

        sb.appendLine("      <div class=\"play-section-title\">")
        sb.appendLine("        <span data-i18n=\"play.about\">About this app</span>")
        sb.appendLine("        <span>${StoreReportIcons.CHEVRON_RIGHT}</span>")
        sb.appendLine("      </div>")
        if (metadata != null && metadata.shortDescription.isNotBlank() &&
            metadata.shortDescription != fullText
        ) {
            sb.appendLine(
                "      <p class=\"play-about-desc\" data-meta=\"shortDescription\" " +
                    "style=\"font-weight: 500;\">" +
                    "${HtmlEscaper.escape(metadata.shortDescription)}</p>",
            )
        }
        sb.appendLine(
            "      <p class=\"play-about-desc\" data-meta=\"fullDescription\">" +
                "${HtmlEscaper.escape(fullText)}</p>",
        )

        sb.appendLine("      <div class=\"play-chips\">")
        sb.appendLine(
            "        <span class=\"play-chip\" data-i18n=\"play.chip1\">#1 Top Free</span>",
        )
        sb.appendLine(
            "        <span class=\"play-chip\" data-meta=\"category\">" +
                "${HtmlEscaper.escape(model.category)}</span>",
        )
        sb.appendLine(
            "        <span class=\"play-chip\" data-i18n=\"play.chip3\">" +
                "Productivity &amp; Tools</span>",
        )
        sb.appendLine("      </div>")

        sb.appendLine("      <div class=\"play-section-title\">")
        sb.appendLine("        <span data-i18n=\"play.safety\">Data safety</span>")
        sb.appendLine("        <span>${StoreReportIcons.CHEVRON_RIGHT}</span>")
        sb.appendLine("      </div>")
        sb.appendLine("      <div class=\"play-safety-card\">")
        sb.appendLine(
            "        <div class=\"play-safety-intro\" data-i18n=\"play.safetyIntro\">" +
                "Safety starts with understanding how developers collect your data.</div>",
        )
        sb.appendLine("        <div class=\"play-safety-badge\">")
        sb.appendLine("          ${StoreReportIcons.SHIELD_CHECK}")
        sb.appendLine(
            "          <span data-i18n=\"play.safety1\">" +
                "No data shared with third parties</span>",
        )
        sb.appendLine("        </div>")
        sb.appendLine("        <div class=\"play-safety-badge\">")
        sb.appendLine("          ${StoreReportIcons.LOCK}")
        sb.appendLine(
            "          <span data-i18n=\"play.safety2\">" +
                "Data encrypted in transit</span>",
        )
        sb.appendLine("        </div>")
        sb.appendLine("        <div class=\"play-safety-badge\">")
        sb.appendLine("          ${StoreReportIcons.TRASH}")
        sb.appendLine(
            "          <span data-i18n=\"play.safety3\">" +
                "You can request that data be deleted</span>",
        )
        sb.appendLine("        </div>")
        sb.appendLine("      </div>")

        sb.appendLine("    </div>")
        sb.appendLine("  </div>")
    }

    internal fun appendAppleAppStoreMockup(sb: StringBuilder, model: GalleryPageModel) {
        val appStoreAssets = model.assets
            .filter { it.target == StoreTarget.APP_STORE }
            .sortedWith(compareBy({ it.deviceSlot.naturalDisplayOrder }, { it.position }))
        val metadata = model.metadataByLocale.values.firstOrNull()
        val initialLetter = model.appName.take(1).uppercase()

        sb.appendLine("  <div class=\"apple-mockup\" id=\"apple-mockup\" style=\"display: none;\">")

        sb.appendLine("    <div class=\"apple-top-nav\">")
        sb.appendLine("      <div class=\"apple-nav-left\">")
        sb.appendLine("        ${StoreReportIcons.APPLE_LOGO}")
        sb.appendLine("        <span class=\"apple-nav-link\">Store</span>")
        sb.appendLine("        <span class=\"apple-nav-link\">Mac</span>")
        sb.appendLine("        <span class=\"apple-nav-link\">iPad</span>")
        sb.appendLine("        <span class=\"apple-nav-link\">iPhone</span>")
        sb.appendLine("        <span class=\"apple-nav-link\">Watch</span>")
        sb.appendLine(
            "        <span class=\"apple-nav-link\" data-i18n=\"apple.nav.5\">Support</span>",
        )
        sb.appendLine("      </div>")
        sb.appendLine("      <div>${StoreReportIcons.SEARCH}</div>")
        sb.appendLine("    </div>")

        sb.appendLine("    <div class=\"apple-main\">")
        sb.appendLine("      <div class=\"apple-header\">")
        val appleIcon = model.appStoreIconDataUri ?: model.appIconDataUri
        if (appleIcon != null) {
            sb.appendLine("        <div class=\"apple-icon\">")
            sb.appendLine(
                "          <img class=\"apple-app-icon-img\" src=\"$appleIcon\" " +
                    "alt=\"${HtmlEscaper.escape(model.appName)}\">",
            )
            sb.appendLine("        </div>")
        } else {
            sb.appendLine(
                "        <div class=\"apple-icon\">" +
                    "${HtmlEscaper.escape(initialLetter)}</div>",
            )
        }
        sb.appendLine("        <div class=\"apple-header-info\">")
        sb.appendLine(
            "          <h1 class=\"apple-title\" data-meta=\"title\">" +
                "${HtmlEscaper.escape(model.appName)}</h1>",
        )
        sb.appendLine(
            "          <p class=\"apple-subtitle\" data-meta=\"category\">" +
                "${HtmlEscaper.escape(model.category)}</p>",
        )
        sb.appendLine(
            "          <div class=\"apple-pricing\" data-i18n=\"apple.pricing\">" +
                "Free · Offers In-App Purchases</div>",
        )
        sb.appendLine("          <div class=\"apple-actions-row\">")
        sb.appendLine(
            "            <button class=\"apple-btn-get\" data-i18n=\"apple.get\">GET</button>",
        )
        sb.appendLine(
            "            <button class=\"apple-btn-share\" title=\"Share\">" +
                "${StoreReportIcons.SHARE}</button>",
        )
        sb.appendLine("          </div>")
        sb.appendLine("        </div>")
        sb.appendLine("      </div>")

        sb.appendLine("      <div class=\"apple-shelf\">")
        sb.appendLine("        <div class=\"apple-shelf-col\">")
        sb.appendLine(
            "          <div class=\"apple-shelf-term\" data-i18n=\"apple.ratingsTerm\">" +
                "2.1M RATINGS</div>",
        )
        sb.appendLine(
            "          <div class=\"apple-shelf-def\">4.8 ★★★★★</div>",
        )
        sb.appendLine(
            "          <div class=\"apple-shelf-sub\" data-i18n=\"apple.ratingsSub\">" +
                "out of 5 stars</div>",
        )
        sb.appendLine("        </div>")
        sb.appendLine("        <div class=\"apple-shelf-col\">")
        sb.appendLine(
            "          <div class=\"apple-shelf-term\" data-i18n=\"apple.ageTerm\">AGE</div>",
        )
        sb.appendLine(
            "          <div class=\"apple-shelf-def\" data-meta=\"ageRating\">4+</div>",
        )
        sb.appendLine(
            "          <div class=\"apple-shelf-sub\" data-i18n=\"apple.ageSub\">" +
                "Years Old</div>",
        )
        sb.appendLine("        </div>")
        sb.appendLine("        <div class=\"apple-shelf-col\">")
        sb.appendLine(
            "          <div class=\"apple-shelf-term\" data-i18n=\"apple.chartTerm\">CHART</div>",
        )
        sb.appendLine(
            "          <div class=\"apple-shelf-def\">No. 1</div>",
        )
        sb.appendLine(
            "          <div class=\"apple-shelf-sub\" data-meta=\"category\">in " +
                "${HtmlEscaper.escape(model.category)}</div>",
        )
        sb.appendLine("        </div>")
        sb.appendLine("        <div class=\"apple-shelf-col\">")
        sb.appendLine(
            "          <div class=\"apple-shelf-term\" data-i18n=\"apple.devTerm\">" +
                "DEVELOPER</div>",
        )
        sb.appendLine(
            "          <div class=\"apple-shelf-def\" style=\"font-size: 13px;\">" +
                "${HtmlEscaper.escape(model.developerName)}</div>",
        )
        sb.appendLine(
            "          <div class=\"apple-shelf-sub\" data-i18n=\"apple.devSub\">Developer</div>",
        )
        sb.appendLine("        </div>")
        sb.appendLine("        <div class=\"apple-shelf-col\">")
        sb.appendLine(
            "          <div class=\"apple-shelf-term\" data-i18n=\"apple.langTerm\">" +
                "LANGUAGE</div>",
        )
        sb.appendLine(
            "          <div class=\"apple-shelf-def\" data-i18n=\"apple.langDef\">EN</div>",
        )
        sb.appendLine(
            "          <div class=\"apple-shelf-sub\" data-i18n=\"apple.langSub\">" +
                "+ 12 More</div>",
        )
        sb.appendLine("        </div>")
        sb.appendLine("        <div class=\"apple-shelf-col\">")
        sb.appendLine(
            "          <div class=\"apple-shelf-term\" data-i18n=\"apple.sizeTerm\">SIZE</div>",
        )
        sb.appendLine(
            "          <div class=\"apple-shelf-def\" data-i18n=\"apple.sizeDef\">48 MB</div>",
        )
        sb.appendLine(
            "          <div class=\"apple-shelf-sub\" data-i18n=\"apple.sizeSub\">" +
                "Universal</div>",
        )
        sb.appendLine("        </div>")
        sb.appendLine("      </div>")

        val appleSlots = appStoreAssets.map { it.deviceSlot }.distinct()
            .sortedBy { it.naturalDisplayOrder }
        if (appleSlots.size > 1) {
            sb.appendLine("      <div class=\"apple-device-tabs\">")
            sb.appendLine(
                "        <button class=\"apple-device-tab active\" " +
                    "data-slot=\"ALL\" data-i18n=\"apple.allDevices\">All</button>",
            )
            for (slot in appleSlots) {
                val label = if (slot.name.startsWith("IPAD")) "iPad" else "iPhone"
                sb.appendLine(
                    "        <button class=\"apple-device-tab\" " +
                        "data-slot=\"${slot.name}\">$label</button>",
                )
            }
            sb.appendLine("      </div>")
        }

        sb.appendLine("      <div class=\"apple-carousel\">")
        for (ss in appStoreAssets) {
            sb.appendLine(
                "        <div class=\"apple-screenshot-item\" " +
                    "data-slot=\"${ss.deviceSlot.name}\" " +
                    "data-locale=\"${HtmlEscaper.escape(ss.storeLocale)}\">",
            )
            sb.appendLine(
                "          <img src=\"${HtmlEscaper.escape(
                    ss.dataUri,
                )}\" alt=\"${HtmlEscaper.escape(ss.id)}\">",
            )
            sb.appendLine("        </div>")
        }
        sb.appendLine("      </div>")

        val whatsNewText = metadata?.whatsNew?.ifBlank { null }
            ?: "Bug fixes and performance enhancements."

        sb.appendLine(
            "      <h2 class=\"apple-section-title\" data-i18n=\"apple.whatsNew\">" +
                "What's New</h2>",
        )
        sb.appendLine(
            "      <div class=\"apple-whats-new-meta\" data-i18n=\"apple.version\">" +
                "Version 1.0.0 · 2d ago</div>",
        )
        sb.appendLine(
            "      <p class=\"apple-whats-new-text\" data-meta=\"whatsNew\">" +
                "${HtmlEscaper.escape(whatsNewText)}</p>",
        )

        sb.appendLine(
            "      <h2 class=\"apple-section-title\" data-i18n=\"apple.info\">Information</h2>",
        )
        sb.appendLine("      <table class=\"apple-info-table\">")
        sb.appendLine("        <tr class=\"apple-info-row\">")
        sb.appendLine(
            "          <td class=\"apple-info-key\" data-i18n=\"apple.seller\">Provider</td>",
        )
        sb.appendLine(
            "          <td class=\"apple-info-val\">" +
                "${HtmlEscaper.escape(model.developerName)}</td>",
        )
        sb.appendLine("        </tr>")
        sb.appendLine("        <tr class=\"apple-info-row\">")
        sb.appendLine(
            "          <td class=\"apple-info-key\" data-i18n=\"apple.size\">Size</td>",
        )
        sb.appendLine(
            "          <td class=\"apple-info-val\" data-i18n=\"apple.sizeDef\">48 MB</td>",
        )
        sb.appendLine("        </tr>")
        sb.appendLine("        <tr class=\"apple-info-row\">")
        sb.appendLine(
            "          <td class=\"apple-info-key\" data-i18n=\"apple.category\">Category</td>",
        )
        sb.appendLine(
            "          <td class=\"apple-info-val\" data-meta=\"category\">" +
                "${HtmlEscaper.escape(model.category)}</td>",
        )
        sb.appendLine("        </tr>")
        sb.appendLine("        <tr class=\"apple-info-row\">")
        sb.appendLine(
            "          <td class=\"apple-info-key\" data-i18n=\"apple.compatibility\">" +
                "Compatibility</td>",
        )
        sb.appendLine(
            "          <td class=\"apple-info-val\" data-i18n=\"apple.compatibilityVal\">" +
                "Requires iOS 16.0 or later</td>",
        )
        sb.appendLine("        </tr>")
        sb.appendLine("        <tr class=\"apple-info-row\">")
        sb.appendLine(
            "          <td class=\"apple-info-key\" data-i18n=\"apple.languages\">Languages</td>",
        )
        sb.appendLine(
            "          <td class=\"apple-info-val\" data-i18n=\"apple.languagesVal\">" +
                "English, Spanish</td>",
        )
        sb.appendLine("        </tr>")
        sb.appendLine("        <tr class=\"apple-info-row\">")
        sb.appendLine(
            "          <td class=\"apple-info-key\" data-i18n=\"apple.ageRating\">Age Rating</td>",
        )
        sb.appendLine(
            "          <td class=\"apple-info-val\" data-meta=\"ageRating\">4+</td>",
        )
        sb.appendLine("        </tr>")
        sb.appendLine("        <tr class=\"apple-info-row\">")
        sb.appendLine(
            "          <td class=\"apple-info-key\" data-i18n=\"apple.copyright\">Copyright</td>",
        )
        sb.appendLine(
            "          <td class=\"apple-info-val\">© 2026 " +
                "${HtmlEscaper.escape(model.developerName)}</td>",
        )
        sb.appendLine("        </tr>")
        sb.appendLine("        <tr class=\"apple-info-row\">")
        sb.appendLine(
            "          <td class=\"apple-info-key\" data-i18n=\"apple.price\">Price</td>",
        )
        sb.appendLine(
            "          <td class=\"apple-info-val\" data-i18n=\"apple.freeVal\">Free</td>",
        )
        sb.appendLine("        </tr>")
        sb.appendLine("      </table>")

        sb.appendLine("    </div>")
        sb.appendLine("  </div>")
    }
}
