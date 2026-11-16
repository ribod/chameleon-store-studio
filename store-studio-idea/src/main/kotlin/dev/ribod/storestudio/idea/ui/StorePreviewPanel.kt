package dev.ribod.storestudio.idea.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import dev.ribod.storestudio.idea.io.StoreThumbnailService
import dev.ribod.storestudio.idea.model.AppStoreMetadataDto
import dev.ribod.storestudio.idea.model.ModuleStoreArtifacts
import dev.ribod.storestudio.idea.model.PlayMetadataDto
import dev.ribod.storestudio.idea.model.StoreAssetCard
import dev.ribod.storestudio.idea.model.StoreMetadataBundleDto
import dev.ribod.storestudio.idea.navigation.StoreStudioNavigator
import dev.ribod.storestudio.idea.settings.StoreStudioTheme
import org.jetbrains.annotations.VisibleForTesting
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager

/**
 * High-fidelity native Swing simulation of Google Play Store and Apple App Store product pages.
 *
 * Provides live preview of marketing copy, feature graphics, and screenshot carousels,
 * with click-to-edit navigation to metadata files in the project.
 */
public class StorePreviewPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private var modules: List<ModuleStoreArtifacts> = emptyList()
    private var allCards: List<StoreAssetCard> = emptyList()

    private val storeSelector = ComboBox(arrayOf("Google Play Store", "Apple App Store"))
    private val deviceSelector = ComboBox(arrayOf("ALL"))
    private val localeSelector = ComboBox(arrayOf("en-US"))
    private val moduleSelector = ComboBox(arrayOf("ALL"))

    private val contentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private lateinit var scrollPane: JBScrollPane

    /** Everything the preview shows; the UI is only rebuilt when it changes. */
    private data class PreviewState(
        val playStore: Boolean,
        val locale: String,
        val module: String,
        val device: String,
        val moduleDir: java.io.File?,
        val metadata: StoreMetadataBundleDto?,
        val cards: List<StoreAssetCard>,
    )

    private var renderedState: PreviewState? = null

    /** Set while the filter combos are repopulated, so a reload rebuilds the preview once. */
    private var repopulating = false

    /** Number of times the preview UI was rebuilt. */
    @get:VisibleForTesting
    internal var rebuildCount: Int = 0
        private set

    init {
        scrollPane = JBScrollPane(contentPanel).apply {
            border = JBUI.Borders.empty()
            verticalScrollBar.unitIncrement = 16
        }
        setContent(scrollPane)

        setupToolbar()
    }

    private fun setupToolbar() {
        val toolbarPanel = JPanel(
            FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4)),
        ).apply {
            isOpaque = false
            add(JBLabel("Store:"))
            add(storeSelector)
            add(JBLabel("Device:"))
            add(deviceSelector)
            add(JBLabel("Locale:"))
            add(localeSelector)
            add(JBLabel("Module:"))
            add(moduleSelector)
        }

        val listener = java.awt.event.ActionListener { if (!repopulating) renderPreview() }
        storeSelector.addActionListener(listener)
        deviceSelector.addActionListener(listener)
        localeSelector.addActionListener(listener)
        moduleSelector.addActionListener(listener)

        setToolbar(toolbarPanel)
    }

    public fun updateArtifacts(modules: List<ModuleStoreArtifacts>, cards: List<StoreAssetCard>) {
        this.modules = modules
        this.allCards = cards

        val moduleNames = listOf("ALL") + modules.map { it.moduleName }.distinct()
        val deviceNames = listOf("ALL") +
            allCards.map {
                it.deviceSlot
            }.filter { it.isNotBlank() && it != "FEATURE_GRAPHIC" }
                .distinct()
                .sortedBy { deviceSlotOrder(it) }
        val locales = (
            modules.flatMap { it.metadataManifest?.locales?.keys ?: emptySet() } +
                allCards.map { it.storeLocale }
            ).distinct().filter { it.isNotBlank() }

        // Reloads arrive on every Gradle write: keep the user's filters and rebuild at most once
        repopulating = true
        try {
            moduleSelector.replaceOptionsKeepingSelection(moduleNames)
            deviceSelector.replaceOptionsKeepingSelection(deviceNames)
            if (locales.isNotEmpty()) localeSelector.replaceOptionsKeepingSelection(locales)
        } finally {
            repopulating = false
        }

        renderPreview()
    }

    private fun deviceSlotOrder(slot: String): Int = when (slot.uppercase()) {
        "PHONE" -> 10
        "IPHONE_6_5" -> 11
        "IPHONE_6_9" -> 12
        "TABLET_7" -> 20
        "TABLET_10", "TABLET" -> 21
        "IPAD_13", "IPAD" -> 22
        "WEAR" -> 30
        "FEATURE_GRAPHIC" -> 40
        else -> 99
    }

    private fun renderPreview() {
        val isPlayStore = storeSelector.selectedIndex == 0
        val selectedLocale = localeSelector.selectedItem as? String ?: "en-US"
        val selectedModule = moduleSelector.selectedItem as? String ?: "ALL"
        val selectedDevice = deviceSelector.selectedItem as? String ?: "ALL"

        val activeModule =
            modules.find { selectedModule == "ALL" || it.moduleName == selectedModule }
                ?: modules.firstOrNull()
        val metadataBundle = activeModule?.metadataManifest?.locales?.get(selectedLocale)

        val targetType = if (isPlayStore) "PLAY_STORE" else "APP_STORE"
        val matchedCards = allCards.filter { card ->
            val matchModule = selectedModule == "ALL" || card.moduleName == selectedModule
            val matchTarget = card.target.equals(targetType, ignoreCase = true)
            val matchLocale = card.storeLocale.isEmpty() ||
                card.storeLocale.equals(selectedLocale, ignoreCase = true)
            val matchDevice = selectedDevice == "ALL" ||
                card.deviceSlot.equals(selectedDevice, ignoreCase = true) ||
                card.deviceSlot == "FEATURE_GRAPHIC"
            matchModule && matchTarget && matchLocale && matchDevice
        }.sortedWith(
            compareBy(
                { deviceSlotOrder(it.deviceSlot) },
                { it.order },
                { it.id },
            ),
        )

        val state = PreviewState(
            playStore = isPlayStore,
            locale = selectedLocale,
            module = selectedModule,
            device = selectedDevice,
            moduleDir = activeModule?.moduleDir,
            metadata = metadataBundle,
            cards = matchedCards,
        )
        // Reloads that do not change what is shown (most Gradle writes) leave the UI untouched
        if (state == renderedState) return
        val previous = renderedState
        renderedState = state
        rebuildCount++

        // Fresh data for the same listing keeps the scroll position; another listing starts at top
        val sameListing = previous != null && previous.playStore == state.playStore &&
            previous.module == state.module && previous.locale == state.locale
        val scrollPosition = scrollPane.viewport.viewPosition
        contentPanel.removeAll()

        val featureGraphicCard = matchedCards.find {
            it.deviceSlot == "FEATURE_GRAPHIC" || it.id.contains("feature_graphic")
        }
        val screenshotCards = matchedCards.filter {
            it.deviceSlot != "FEATURE_GRAPHIC" && !it.id.contains("feature_graphic")
        }

        val rootCard = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(16, 20)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        if (isPlayStore) {
            buildPlayStorePreview(
                rootCard,
                activeModule,
                metadataBundle?.play,
                featureGraphicCard,
                screenshotCards,
                selectedLocale,
            )
        } else {
            buildAppStorePreview(
                rootCard,
                activeModule,
                metadataBundle?.appStore,
                screenshotCards,
                selectedLocale,
            )
        }

        contentPanel.add(rootCard)
        contentPanel.revalidate()
        contentPanel.repaint()
        if (sameListing) {
            // After the new content has been laid out
            SwingUtilities.invokeLater { scrollPane.viewport.viewPosition = scrollPosition }
        }
    }

    private fun buildPlayStorePreview(
        parent: JPanel,
        module: ModuleStoreArtifacts?,
        playMeta: PlayMetadataDto?,
        featureGraphic: StoreAssetCard?,
        cards: List<StoreAssetCard>,
        locale: String,
    ) {
        val title = playMeta?.title?.takeIf { it.isNotBlank() } ?: module?.moduleName ?: "App Title"
        val shortDesc = playMeta?.shortDescription?.takeIf { it.isNotBlank() }
            ?: "Short marketing description..."
        val fullDesc = playMeta?.fullDescription?.takeIf { it.isNotBlank() }
            ?: "Full product description copy..."

        // 1. Feature Graphic (Hero Banner) if available
        if (featureGraphic != null) {
            val banner = FeatureGraphicComponent(featureGraphic) {
                StoreStudioNavigator.navigate(
                    project = project,
                    sourceLocation = featureGraphic.sourceLocation,
                    functionFqn = featureGraphic.functionFqn,
                    functionName = featureGraphic.functionName,
                    annotationId = featureGraphic.id,
                )
            }
            parent.add(banner)
            parent.add(Box.createVerticalStrut(JBUI.scale(16)))
        }

        // 2. Header with App Icon and Details
        val headerBox = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT

            val iconPlaceholder = RoundedIconComponent(
                JBUI.scale(72),
                JBUI.scale(72),
                JBUI.scale(16),
                { appIconFor(module, isPlayStore = true) },
            ) { file -> StoreStudioNavigator.navigateToFile(project, file) }
            add(iconPlaceholder)
            add(Box.createHorizontalStrut(JBUI.scale(16)))

            val detailsCol = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false

                val titleRow = JPanel(BorderLayout()).apply {
                    isOpaque = false
                    alignmentX = Component.LEFT_ALIGNMENT
                    maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(28))
                    add(
                        JBLabel(title).apply {
                            font = JBFont.label().asBold().deriveFont(JBFont.label().size2D + 5f)
                        },
                        BorderLayout.WEST,
                    )
                    add(
                        createEditLink(
                            module = module,
                            locale = locale,
                            fileName = "title.txt",
                            initialText = title,
                            isPlayStore = true,
                            linkText = "Edit Title →",
                        ),
                        BorderLayout.EAST,
                    )
                }
                add(titleRow)

                add(
                    JBLabel("Verified Developer • In-app purchases").apply {
                        foreground = StoreStudioTheme.badgePlayStoreFg
                        font = StoreStudioTheme.subtitleFont
                    },
                )
                add(Box.createVerticalStrut(JBUI.scale(6)))
                add(
                    JBLabel("4.8 ★  100K+ Downloads  Rated for 3+").apply {
                        foreground = StoreStudioTheme.textMuted
                        font = StoreStudioTheme.subtitleFont
                    },
                )
            }
            add(detailsCol)
        }
        parent.add(headerBox)

        // Install button row
        parent.add(Box.createVerticalStrut(JBUI.scale(14)))
        val actionRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT

            val installBtn = JButton("Install").apply {
                background = Color(0x01, 0x87, 0x5F)
                foreground = Color.WHITE
                isOpaque = true
                preferredSize = Dimension(JBUI.scale(140), JBUI.scale(36))
            }
            add(installBtn)
        }
        parent.add(actionRow)

        // 3. Screenshot Carousel
        parent.add(Box.createVerticalStrut(JBUI.scale(20)))
        parent.add(
            JBLabel("Screenshots").apply {
                font = StoreStudioTheme.titleFont
                alignmentX = Component.LEFT_ALIGNMENT
            },
        )
        parent.add(Box.createVerticalStrut(JBUI.scale(8)))

        val carousel = createScreenshotCarousel(cards)
        parent.add(carousel)

        // 4. About this app & Descriptions
        parent.add(Box.createVerticalStrut(JBUI.scale(20)))
        val descHeader = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(26))
            add(
                JBLabel("About this app").apply {
                    font = StoreStudioTheme.titleFont
                },
                BorderLayout.WEST,
            )
            val linksBox = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0)).apply {
                isOpaque = false
                add(
                    createEditLink(
                        module = module,
                        locale = locale,
                        fileName = "short_description.txt",
                        initialText = shortDesc,
                        isPlayStore = true,
                        linkText = "Edit Short Desc →",
                    ),
                )
                add(
                    createEditLink(
                        module = module,
                        locale = locale,
                        fileName = "full_description.txt",
                        initialText = fullDesc,
                        isPlayStore = true,
                        linkText = "Edit Full Desc →",
                    ),
                )
            }
            add(linksBox, BorderLayout.EAST)
        }
        parent.add(descHeader)

        parent.add(Box.createVerticalStrut(JBUI.scale(6)))
        parent.add(
            JBLabel(shortDesc).apply {
                font = JBFont.label().asBold()
                alignmentX = Component.LEFT_ALIGNMENT
            },
        )

        parent.add(Box.createVerticalStrut(JBUI.scale(10)))
        val fullDescArea = JTextArea(fullDesc).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            isOpaque = false
            font = JBFont.label()
            foreground = StoreStudioTheme.textMuted
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(200))
        }
        parent.add(fullDescArea)
    }

    private fun buildAppStorePreview(
        parent: JPanel,
        module: ModuleStoreArtifacts?,
        appStoreMeta: AppStoreMetadataDto?,
        cards: List<StoreAssetCard>,
        locale: String,
    ) {
        val name =
            appStoreMeta?.name?.takeIf { it.isNotBlank() } ?: module?.moduleName ?: "App Name"
        val subtitle = appStoreMeta?.subtitle?.takeIf { it.isNotBlank() } ?: "App Subtitle"
        val description = appStoreMeta?.description?.takeIf { it.isNotBlank() }
            ?: "App Store description..."
        val promo = appStoreMeta?.promotionalText?.takeIf { it.isNotBlank() } ?: ""

        val headerBox = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT

            val iconPlaceholder = RoundedIconComponent(
                JBUI.scale(80),
                JBUI.scale(80),
                JBUI.scale(18),
                { appIconFor(module, isPlayStore = false) },
            ) { file -> StoreStudioNavigator.navigateToFile(project, file) }
            add(iconPlaceholder)
            add(Box.createHorizontalStrut(JBUI.scale(16)))

            val detailsCol = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false

                val nameRow = JPanel(BorderLayout()).apply {
                    isOpaque = false
                    alignmentX = Component.LEFT_ALIGNMENT
                    maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(28))
                    add(
                        JBLabel(name).apply {
                            font = JBFont.label().asBold().deriveFont(JBFont.label().size2D + 5f)
                        },
                        BorderLayout.WEST,
                    )
                    add(
                        createEditLink(
                            module = module,
                            locale = locale,
                            fileName = "name.txt",
                            initialText = name,
                            isPlayStore = false,
                            linkText = "Edit Name →",
                        ),
                        BorderLayout.EAST,
                    )
                }
                add(nameRow)

                add(
                    JBLabel(subtitle).apply {
                        foreground = StoreStudioTheme.textMuted
                        font = JBFont.label()
                    },
                )
                add(Box.createVerticalStrut(JBUI.scale(8)))

                val getBtn = JButton("GET").apply {
                    background = Color(0x00, 0x71, 0xE3)
                    foreground = Color.WHITE
                    font = JBFont.label().asBold()
                    preferredSize = Dimension(JBUI.scale(80), JBUI.scale(32))
                }
                add(getBtn)
            }
            add(detailsCol)
        }
        parent.add(headerBox)

        // 2. Carousel
        parent.add(Box.createVerticalStrut(JBUI.scale(20)))
        val carousel = createScreenshotCarousel(cards)
        parent.add(carousel)

        // 3. Descriptions & Keywords
        parent.add(Box.createVerticalStrut(JBUI.scale(20)))
        val descHeader = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(26))
            add(
                JBLabel("Description").apply {
                    font = StoreStudioTheme.titleFont
                },
                BorderLayout.WEST,
            )
            add(
                createEditLink(
                    module = module,
                    locale = locale,
                    fileName = "description.txt",
                    initialText = description,
                    isPlayStore = false,
                    linkText = "Edit Description →",
                ),
                BorderLayout.EAST,
            )
        }
        parent.add(descHeader)

        if (promo.isNotBlank()) {
            parent.add(Box.createVerticalStrut(JBUI.scale(6)))
            parent.add(
                JBLabel(promo).apply {
                    font = JBFont.label().asBold()
                    alignmentX = Component.LEFT_ALIGNMENT
                },
            )
        }

        parent.add(Box.createVerticalStrut(JBUI.scale(8)))
        val descArea = JTextArea(description).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            isOpaque = false
            font = JBFont.label()
            foreground = StoreStudioTheme.textMuted
            alignmentX = Component.LEFT_ALIGNMENT
        }
        parent.add(descArea)

        if (!appStoreMeta?.keywords.isNullOrBlank()) {
            parent.add(Box.createVerticalStrut(JBUI.scale(12)))
            val keywordsRow = JPanel(BorderLayout()).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                add(
                    JBLabel("Keywords: ${appStoreMeta.keywords}").apply {
                        font = StoreStudioTheme.monoFont
                        foreground = StoreStudioTheme.textMuted
                    },
                    BorderLayout.WEST,
                )
                add(
                    createEditLink(
                        module = module,
                        locale = locale,
                        fileName = "keywords.txt",
                        initialText = appStoreMeta.keywords,
                        isPlayStore = false,
                        linkText = "Edit Keywords →",
                    ),
                    BorderLayout.EAST,
                )
            }
            parent.add(keywordsRow)
        }
    }

    private fun createScreenshotCarousel(cards: List<StoreAssetCard>): JComponent {
        val carouselPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
        }

        if (cards.isEmpty()) {
            carouselPanel.add(
                JBLabel("No screenshots available for this store target").apply {
                    foreground = StoreStudioTheme.textMuted
                },
            )
        } else {
            for (card in cards) {
                val thumbComp = CarouselThumbnailComponent(card) {
                    StoreStudioNavigator.navigate(
                        project = project,
                        sourceLocation = card.sourceLocation,
                        functionFqn = card.functionFqn,
                        functionName = card.functionName,
                        annotationId = card.id,
                    )
                }
                carouselPanel.add(thumbComp)
                carouselPanel.add(Box.createHorizontalStrut(JBUI.scale(12)))
            }
        }

        val scrollH = JBUI.scale(260 + 36)
        return JBScrollPane(carouselPanel).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBar.unitIncrement = 24
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_NEVER
            preferredSize = Dimension(JBUI.scale(500), scrollH)
            minimumSize = Dimension(JBUI.scale(200), scrollH)
            maximumSize = Dimension(Int.MAX_VALUE, scrollH)
            alignmentX = Component.LEFT_ALIGNMENT
        }
    }

    private fun createEditLink(
        module: ModuleStoreArtifacts?,
        locale: String,
        fileName: String,
        initialText: String,
        isPlayStore: Boolean,
        linkText: String = "Edit Copy →",
    ): Component = ActionLink(linkText) {
        // Probing ~30 candidate paths is disk I/O, so it runs off the EDT
        ApplicationManager.getApplication().executeOnPooledThread {
            val existing = findMetadataFile(module, locale, fileName, isPlayStore)
            if (existing != null) {
                StoreStudioNavigator.navigateToFile(project, existing)
            } else {
                ApplicationManager.getApplication().invokeLater(
                    { createMetadataFile(module, locale, fileName, initialText, isPlayStore) },
                    ModalityState.defaultModalityState(),
                    project.disposed,
                )
            }
        }
    }

    /**
     * Creates the metadata file through the VFS inside an undoable write command and opens it.
     */
    private fun createMetadataFile(
        module: ModuleStoreArtifacts?,
        locale: String,
        fileName: String,
        initialText: String,
        isPlayStore: Boolean,
    ) {
        // The locale becomes a directory name, so only plain tags may pass
        if (!locale.matches(Regex("[A-Za-z0-9_-]+"))) return
        val baseDir = module?.moduleDir ?: project.basePath?.let { File(it) } ?: File(".")
        val store = if (isPlayStore) "play" else "appstore"
        val targetDir = File(baseDir, "store/metadata/$locale/$store")

        val created = WriteCommandAction.writeCommandAction(project)
            .withName("Create Store Metadata File")
            .compute<VirtualFile?, IOException> {
                val dir = VfsUtil.createDirectoryIfMissing(targetDir.path) ?: return@compute null
                dir.findChild(fileName) ?: dir.createChildData(this, fileName).also {
                    VfsUtil.saveText(it, initialText.trim())
                }
            }
        created?.let { OpenFileDescriptor(project, it, 0, 0).navigate(true) }
    }

    private fun findMetadataFile(
        module: ModuleStoreArtifacts?,
        locale: String,
        fileName: String,
        isPlayStore: Boolean,
    ): File? {
        val candidateRoots = mutableListOf<File>()
        module?.moduleDir?.let {
            candidateRoots.add(File(it, "store/metadata"))
            candidateRoots.add(File(it, "store"))
            candidateRoots.add(File(it, "src/main/store"))
            candidateRoots.add(File(it, "src/main/play/listings"))
        }
        project.basePath?.let {
            candidateRoots.add(File(it, "store/metadata"))
            candidateRoots.add(File(it, "store"))
        }

        val store = if (isPlayStore) "play" else "appstore"

        for (root in candidateRoots) {
            val candidate = File(root, "$locale/$store/$fileName")
            if (candidate.isFile) return candidate
        }
        return null
    }

    /** The icon the Gradle plugin recorded for the store in `metadata.json`, or `null` (placeholder). */
    private fun appIconFor(module: ModuleStoreArtifacts?, isPlayStore: Boolean): File? {
        val icons = module?.metadataManifest?.icons ?: return null
        val relative = (if (isPlayStore) icons.googlePlay else icons.appStore) ?: return null
        return File(module.moduleDir, relative)
    }

    private class RoundedIconComponent(
        private val w: Int,
        private val h: Int,
        private val corner: Int,
        private val iconFile: () -> File?,
        private val onDoubleClick: (File) -> Unit,
    ) : JComponent() {
        private val repaintOnLoad: (BufferedImage) -> Unit = { repaint() }

        private companion object {
            const val LOGO_FRACTION = 0.7f
        }

        init {
            preferredSize = Dimension(w, h)
            maximumSize = Dimension(w, h)
            ToolTipManager.sharedInstance().registerComponent(this)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) iconFile()?.let(onDoubleClick)
                }
            })
        }

        // Only clickable once a real icon file has been resolved
        override fun getToolTipText(event: MouseEvent?): String? =
            iconFile()?.let { "${it.name} (Double-click to open the source file)" }

        override fun getCursor(): Cursor = if (iconFile() != null) {
            Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        } else {
            super.getCursor()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON,
                )
                val thumb = iconFile()?.let {
                    StoreThumbnailService.getInstance().getOrLoad(it, w * 2, h * 2, repaintOnLoad)
                }
                if (thumb != null) {
                    g2.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC,
                    )
                    g2.clip =
                        RoundRectangle2D.Float(
                            0f,
                            0f,
                            w.toFloat(),
                            h.toFloat(),
                            corner.toFloat(),
                            corner.toFloat(),
                        )
                    g2.drawImage(thumb, 0, 0, w, h, null)
                    return
                }
                // No icon known: show the chameleon logo instead (`_dark` variant picked by the theme)
                g2.color = StoreStudioTheme.cardBackground
                g2.fillRoundRect(0, 0, w, h, corner, corner)
                val base = IconLoader.getIcon(
                    "/icons/storestudio.svg",
                    RoundedIconComponent::class.java,
                )
                val logo = IconUtil.scale(base, this, w * LOGO_FRACTION / base.iconWidth)
                logo.paintIcon(this, g2, (w - logo.iconWidth) / 2, (h - logo.iconHeight) / 2)
            } finally {
                g2.dispose()
            }
        }
    }

    private class FeatureGraphicComponent(
        private val card: StoreAssetCard,
        private val onDoubleClick: () -> Unit,
    ) : JComponent() {
        private val repaintOnLoad: (BufferedImage) -> Unit = { repaint() }
        private val bannerH = JBUI.scale(140)
        private val bannerW = JBUI.scale(287) // 1024:500 ratio

        init {
            val size = Dimension(bannerW, bannerH)
            preferredSize = size
            minimumSize = size
            maximumSize = size
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "${card.title} - Feature Graphic (Double-click to view source)"
            alignmentX = Component.LEFT_ALIGNMENT

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) onDoubleClick()
                }
            })
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON,
                )
                g2.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                )

                val w = width
                val h = height
                val corner = JBUI.scale(12)

                g2.color = StoreStudioTheme.cardBackground
                g2.fillRoundRect(0, 0, w, h, corner, corner)

                val thumb = StoreThumbnailService.getInstance().getOrLoad(
                    card.imageFile,
                    w * 2,
                    h * 2,
                    repaintOnLoad,
                )

                if (thumb != null) {
                    val clipShape = RoundRectangle2D.Float(
                        0f,
                        0f,
                        w.toFloat(),
                        h.toFloat(),
                        corner.toFloat(),
                        corner.toFloat(),
                    )
                    val oldClip = g2.clip
                    g2.clip(clipShape)
                    g2.drawImage(thumb, 0, 0, w, h, null)
                    g2.clip = oldClip
                }

                g2.color = StoreStudioTheme.cardBorder
                g2.drawRoundRect(0, 0, w - 1, h - 1, corner, corner)
            } finally {
                g2.dispose()
            }
        }
    }

    private class CarouselThumbnailComponent(
        private val card: StoreAssetCard,
        private val onDoubleClick: () -> Unit,
    ) : JComponent() {
        private val repaintOnLoad: (BufferedImage) -> Unit = { repaint() }
        private val cardH = JBUI.scale(260)
        private val cardW: Int

        init {
            val aspect = if (card.heightPx > 0) {
                card.widthPx.toDouble() / card.heightPx
            } else {
                0.5
            }
            cardW = (cardH * aspect).toInt().coerceIn(JBUI.scale(120), JBUI.scale(360))

            val size = Dimension(cardW, cardH)
            preferredSize = size
            minimumSize = size
            maximumSize = size
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "${card.title} • ${card.deviceSlot} (${card.widthPx}×${card.heightPx}) " +
                "- Double-click to view source"

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) onDoubleClick()
                }
            })
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON,
                )
                g2.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                )

                val w = width
                val h = height
                val corner = JBUI.scale(10)

                g2.color = StoreStudioTheme.cardBackground
                g2.fillRoundRect(0, 0, w, h, corner, corner)

                val pad = JBUI.scale(2)
                val availW = w - pad * 2
                val availH = h - pad * 2

                val thumb = StoreThumbnailService.getInstance().getOrLoad(
                    card.imageFile,
                    availW * 2,
                    availH * 2,
                    repaintOnLoad,
                )

                if (thumb != null) {
                    val thumbAspect = thumb.width.toDouble() / thumb.height
                    val availAspect = availW.toDouble() / availH

                    val (drawW, drawH) = if (thumbAspect > availAspect) {
                        Pair(availW, (availW / thumbAspect).toInt().coerceAtLeast(1))
                    } else {
                        Pair((availH * thumbAspect).toInt().coerceAtLeast(1), availH)
                    }
                    val drawX = pad + (availW - drawW) / 2
                    val drawY = pad + (availH - drawH) / 2

                    val clipShape = RoundRectangle2D.Float(
                        drawX.toFloat(),
                        drawY.toFloat(),
                        drawW.toFloat(),
                        drawH.toFloat(),
                        JBUI.scale(8).toFloat(),
                        JBUI.scale(8).toFloat(),
                    )
                    val oldClip = g2.clip
                    g2.clip(clipShape)
                    g2.drawImage(thumb, drawX, drawY, drawW, drawH, null)
                    g2.clip = oldClip
                }

                g2.color = StoreStudioTheme.cardBorder
                g2.drawRoundRect(0, 0, w - 1, h - 1, corner, corner)
            } finally {
                g2.dispose()
            }
        }
    }
}
