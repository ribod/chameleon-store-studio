package dev.ribod.storestudio.idea.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.ui.JBUI
import dev.ribod.storestudio.idea.io.StoreThumbnailService
import dev.ribod.storestudio.idea.model.StoreAssetCard
import dev.ribod.storestudio.idea.settings.StoreStudioSettings
import dev.ribod.storestudio.idea.settings.StoreStudioTheme
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingUtilities

/**
 * High-performance native Java2D canvas rendering marketing screenshots at authentic aspect ratios.
 *
 * Implements [Scrollable] so IntelliJ's JBScrollPane handles bounded scrolling smoothly,
 * ensuring all elements are viewable with generous bottom padding.
 */
public class StoreGalleryCanvas(
    private val project: Project,
    private val onCardDoubleClicked: (StoreAssetCard) -> Unit,
) : JComponent(),
    Scrollable {

    private var allCards: List<StoreAssetCard> = emptyList()
    private var filteredCards: List<StoreAssetCard> = emptyList()

    // Bounds are stored parallel to filteredCards; the cards are data classes whose deep
    // hashCode would otherwise be computed for every card on every paint and hit test.
    private var cardBounds: Array<Rectangle> = emptyArray()
    private var hoveredCard: StoreAssetCard? = null
    private var selectedCard: StoreAssetCard? = null

    // One instance, so the thumbnail service keeps a single pending callback per image
    private val repaintOnLoad: (java.awt.image.BufferedImage) -> Unit = { repaint() }

    private var zoom: Float = 1.0f
    private var lastDragScreenPoint: Point? = null

    // Project service: looked up once instead of for every card on every frame
    private val settings = StoreStudioSettings.getInstance(project)

    // Scaled like the fonts and badges so card proportions hold on HiDPI displays
    private val baseCardWidth = JBUI.scale(220)
    private val cardPadding = JBUI.scale(18)

    init {
        isOpaque = true
        background = StoreStudioTheme.canvasBackground

        val mouseHandler = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) {
                    lastDragScreenPoint = e.locationOnScreen
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    val clicked = hitTest(e.point)
                    if (clicked != null) {
                        selectedCard = clicked
                        repaint()
                        showContextMenu(clicked, e.point)
                    }
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                lastDragScreenPoint = null
            }

            override fun mouseDragged(e: MouseEvent) {
                val lastPoint = lastDragScreenPoint ?: return
                val currentPoint = e.locationOnScreen
                val dy = currentPoint.y - lastPoint.y
                lastDragScreenPoint = currentPoint

                val viewport = SwingUtilities.getAncestorOfClass(
                    JViewport::class.java,
                    this@StoreGalleryCanvas,
                ) as? JViewport
                if (viewport != null) {
                    val pos = viewport.viewPosition
                    val maxY = (height - viewport.height).coerceAtLeast(0)
                    val newY = (pos.y - dy).coerceIn(0, maxY)
                    viewport.viewPosition = Point(pos.x, newY)
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                val hit = hitTest(e.point)
                if (hit !== hoveredCard) {
                    val previous = hoveredCard
                    hoveredCard = hit
                    cursor = if (hit != null) {
                        Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    } else {
                        Cursor.getDefaultCursor()
                    }
                    repaintCards(previous, hit)
                }
            }

            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    val clicked = hitTest(e.point)
                    selectedCard = clicked
                    if (e.clickCount == 2 && clicked != null) {
                        onCardDoubleClicked(clicked)
                    }
                    repaint()
                }
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                if (e.isControlDown || e.isMetaDown) {
                    val factor = if (e.wheelRotation < 0) 1.15f else 0.85f
                    applyZoom(zoom * factor)
                } else {
                    // Forward scrolling directly to the enclosing JScrollPane
                    val scrollPane = SwingUtilities.getAncestorOfClass(
                        JScrollPane::class.java,
                        this@StoreGalleryCanvas,
                    ) as? JScrollPane
                    if (scrollPane != null) {
                        scrollPane.dispatchEvent(
                            SwingUtilities.convertMouseEvent(
                                this@StoreGalleryCanvas,
                                e,
                                scrollPane,
                            ),
                        )
                    } else {
                        parent?.dispatchEvent(
                            SwingUtilities.convertMouseEvent(this@StoreGalleryCanvas, e, parent),
                        )
                    }
                }
            }
        }

        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
        addMouseWheelListener(mouseHandler)
    }

    public fun setCards(cards: List<StoreAssetCard>) {
        // Reloads deliver new card instances; keep the selection on the same asset
        selectedCard = selectedCard?.let { old ->
            cards.firstOrNull { it.moduleName == old.moduleName && it.slotKey == old.slotKey }
        }
        hoveredCard = null
        this.allCards = cards
        this.filteredCards = cards
        relayout()
        repaint()
    }

    public fun filterCards(
        moduleFilter: String,
        targetFilter: String,
        deviceFilter: String,
        localeFilter: String,
    ) {
        filteredCards = allCards.filter { card ->
            (moduleFilter == "ALL" || card.moduleName == moduleFilter) &&
                (targetFilter == "ALL" || card.target == targetFilter) &&
                (deviceFilter == "ALL" || card.deviceSlot == deviceFilter) &&
                (localeFilter == "ALL" || card.storeLocale == localeFilter)
        }
        relayout()
        repaint()
    }

    public fun zoomIn() {
        applyZoom(zoom * 1.25f)
    }

    public fun zoomOut() {
        applyZoom(zoom / 1.25f)
    }

    public fun resetZoom() {
        zoom = 1.0f
        relayout()
        repaint()
    }

    private fun applyZoom(newZoom: Float) {
        val clamped = newZoom.coerceIn(0.4f, 2.5f)
        if (clamped == zoom) return
        zoom = clamped
        relayout()
        repaint()
    }

    private fun relayout(triggerRevalidate: Boolean = true) {
        if (filteredCards.isEmpty()) {
            cardBounds = emptyArray()
            preferredSize = Dimension(JBUI.scale(320), JBUI.scale(200))
            if (triggerRevalidate) revalidate()
            return
        }

        val viewport = SwingUtilities.getAncestorOfClass(
            JViewport::class.java,
            this,
        ) as? JViewport
        val viewportW = viewport?.width?.takeIf { it > 0 } ?: width.coerceAtLeast(1)

        val effectiveWidth = (baseCardWidth * zoom).toInt().coerceAtLeast(100)
        val spacing = (cardPadding * zoom).toInt().coerceAtLeast(8)
        val availableW = viewportW.coerceAtLeast(effectiveWidth + spacing * 2)

        val columns = ((availableW - spacing) / (effectiveWidth + spacing)).coerceAtLeast(1)

        val colX = IntArray(columns) { i -> spacing + i * (effectiveWidth + spacing) }
        val colY = IntArray(columns) { spacing }

        val bounds = arrayOfNulls<Rectangle>(filteredCards.size)
        for ((index, card) in filteredCards.withIndex()) {
            var bestCol = 0
            var minY = colY[0]
            for (i in 1 until columns) {
                if (colY[i] < minY) {
                    minY = colY[i]
                    bestCol = i
                }
            }

            val x = colX[bestCol]
            val y = colY[bestCol]

            val aspect = if (card.widthPx > 0) card.heightPx.toDouble() / card.widthPx else 1.77
            val imageHeight = (effectiveWidth * aspect).toInt().coerceIn(120, 900)
            val headerH = (28 * zoom).toInt().coerceAtLeast(20)
            val footerH = (52 * zoom).toInt().coerceAtLeast(36)
            val totalHeight = headerH + imageHeight + footerH

            bounds[index] = Rectangle(x, y, effectiveWidth, totalHeight)
            colY[bestCol] += totalHeight + spacing
        }

        cardBounds = bounds.requireNoNulls()

        val maxColY = colY.maxOrNull() ?: spacing
        // Generous bottom padding so the user can easily view the last item with space
        val bottomPadding = JBUI.scale(80)
        preferredSize = Dimension(availableW, maxColY + bottomPadding)
        if (triggerRevalidate) revalidate()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
            )

            g2.color = StoreStudioTheme.canvasBackground
            g2.fillRect(0, 0, width, height)

            if (filteredCards.isEmpty()) {
                paintEmptyState(g2)
                return
            }

            val clipBounds = g2.clipBounds ?: Rectangle(0, 0, width, height)

            val bounds = cardBounds
            for ((index, card) in filteredCards.withIndex()) {
                val cardRect = bounds.getOrNull(index) ?: break
                if (cardRect.intersects(clipBounds)) {
                    paintCard(g2, card, cardRect)
                }
            }
        } finally {
            g2.dispose()
        }
    }

    private fun paintEmptyState(g2: Graphics2D) {
        g2.color = StoreStudioTheme.textMuted
        g2.font = StoreStudioTheme.titleFont
        val text = "No assets match active filters"
        val fm = g2.fontMetrics
        val tx = (width - fm.stringWidth(text)) / 2
        val ty = height / 2
        g2.drawString(text, tx, ty)
    }

    private fun paintCard(g2: Graphics2D, card: StoreAssetCard, r: Rectangle) {
        // Identity, not data-class equality: this runs for every visible card on every frame
        val isHovered = card === hoveredCard
        val isSelected = card === selectedCard
        val corner = (10 * zoom).toInt().coerceAtLeast(6)

        // 1. Background & Shadow
        val shape = RoundRectangle2D.Float(
            r.x.toFloat(),
            r.y.toFloat(),
            r.width.toFloat(),
            r.height.toFloat(),
            corner.toFloat(),
            corner.toFloat(),
        )
        g2.color = if (isHovered) {
            StoreStudioTheme.cardBackgroundHover
        } else {
            StoreStudioTheme.cardBackground
        }
        g2.fill(shape)

        // Border
        g2.color = when {
            isSelected -> StoreStudioTheme.cardBorderSelected
            isHovered -> StoreStudioTheme.cardBorderSelected.brighter()
            else -> StoreStudioTheme.cardBorder
        }
        g2.stroke = if (isSelected) {
            StoreStudioTheme.selectedStroke
        } else {
            StoreStudioTheme.regularStroke
        }
        g2.draw(shape)

        // 2. Header Bar: Target badge & Engine badge
        val headerH = (28 * zoom).toInt().coerceAtLeast(20)
        paintHeaderBadges(
            g2,
            card,
            r.x + JBUI.scale(8),
            r.y + JBUI.scale(6),
            r.width - JBUI.scale(16),
            headerH,
        )

        // 3. Image Area
        val imageY = r.y + headerH
        val footerH = (52 * zoom).toInt().coerceAtLeast(36)
        val imageH = r.height - headerH - footerH
        val imageW = r.width

        val thumb = StoreThumbnailService.getInstance().getOrLoad(
            card.imageFile,
            imageW * 2,
            imageH * 2,
            repaintOnLoad,
        )

        if (thumb != null) {
            val thumbAspect = thumb.width.toDouble() / thumb.height
            val slotAspect = imageW.toDouble() / imageH

            val (drawW, drawH) = if (thumbAspect > slotAspect) {
                Pair(imageW, (imageW / thumbAspect).toInt())
            } else {
                Pair((imageH * thumbAspect).toInt(), imageH)
            }
            val drawX = r.x + (imageW - drawW) / 2
            val drawY = imageY + (imageH - drawH) / 2

            val oldClip = g2.clip
            g2.clip(Rectangle(r.x, imageY, imageW, imageH))
            g2.drawImage(thumb, drawX, drawY, drawW, drawH, null)
            g2.clip = oldClip
        } else {
            g2.color = StoreStudioTheme.canvasBackground
            g2.fillRect(r.x + 2, imageY, imageW - 4, imageH)
            g2.color = StoreStudioTheme.textMuted
            g2.font = StoreStudioTheme.subtitleFont
            val loadText = "Loading preview..."
            val fm = g2.fontMetrics
            val strX = r.x + (imageW - fm.stringWidth(loadText)) / 2
            val strY = imageY + imageH / 2
            g2.drawString(loadText, strX, strY)
        }

        // 4. Footer Bar
        val footerY = imageY + imageH
        paintFooterInfo(
            g2,
            card,
            r.x + JBUI.scale(8),
            footerY + JBUI.scale(6),
            r.width - JBUI.scale(16),
            footerH,
        )
    }

    private fun paintHeaderBadges(
        g2: Graphics2D,
        card: StoreAssetCard,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) {
        var curX = x
        val badgeH = (16 * zoom).toInt().coerceAtLeast(12)

        // Target badge
        val isPlay = card.target.contains("PLAY", ignoreCase = true)
        val targetText = if (isPlay) "PLAY STORE" else "APP STORE"
        val targetBg = if (isPlay) {
            StoreStudioTheme.badgePlayStoreBg
        } else {
            StoreStudioTheme.badgeAppStoreBg
        }
        val targetFg = if (isPlay) {
            StoreStudioTheme.badgePlayStoreFg
        } else {
            StoreStudioTheme.badgeAppStoreFg
        }
        curX += drawBadge(g2, targetText, curX, y, badgeH, targetBg, targetFg) + JBUI.scale(4)

        // Engine badge
        if (settings.showEngineBadges) {
            curX += drawEngineBadge(g2, card, curX, y, badgeH)
        }

        // Validation badge if any
        if (settings.showValidationBadges && card.violations.isNotEmpty()) {
            val hasErrors = card.violations.any { it.severity.equals("ERROR", ignoreCase = true) }
            val (valText, valBg, valFg) = if (hasErrors) {
                Triple("ERROR", StoreStudioTheme.errorBg, StoreStudioTheme.errorFg)
            } else {
                Triple("WARNING", StoreStudioTheme.warningBg, StoreStudioTheme.warningFg)
            }
            drawBadge(g2, valText, curX, y, badgeH, valBg, valFg)
        }
    }

    private fun drawEngineBadge(
        g2: Graphics2D,
        card: StoreAssetCard,
        x: Int,
        y: Int,
        badgeH: Int,
    ): Int {
        val isRobolectric = card.engine.contains("ROBOLECTRIC", ignoreCase = true)
        val engineText = if (isRobolectric) "ROBOLECTRIC" else "LAYOUTLIB"
        val engineBg = if (isRobolectric) {
            StoreStudioTheme.badgeRobolectricBg
        } else {
            StoreStudioTheme.badgeLayoutlibBg
        }
        val engineFg = if (isRobolectric) {
            StoreStudioTheme.badgeRobolectricFg
        } else {
            StoreStudioTheme.badgeLayoutlibFg
        }
        return drawBadge(g2, engineText, x, y, badgeH, engineBg, engineFg) + JBUI.scale(4)
    }

    private fun paintFooterInfo(
        g2: Graphics2D,
        card: StoreAssetCard,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) {
        g2.color = StoreStudioTheme.textPrimary
        g2.font = StoreStudioTheme.titleFont
        val titleText = "#${card.order} ${card.title}"
        val fmTitle = g2.fontMetrics
        val clippedTitle = clipString(titleText, fmTitle, w)
        g2.drawString(clippedTitle, x, y + fmTitle.ascent)

        g2.color = StoreStudioTheme.textMuted
        g2.font = StoreStudioTheme.subtitleFont
        val fmSub = g2.fontMetrics
        val devicePrefix = if (settings.showDeviceBadges) {
            "${card.deviceSlot} • "
        } else {
            ""
        }
        val subText = "$devicePrefix${card.storeLocale} • ${card.widthPx}×${card.heightPx} px"
        val clippedSub = clipString(subText, fmSub, w)
        g2.drawString(clippedSub, x, y + fmTitle.height + fmSub.ascent + JBUI.scale(2))
    }

    private fun drawBadge(
        g2: Graphics2D,
        text: String,
        x: Int,
        y: Int,
        h: Int,
        bg: java.awt.Color,
        fg: java.awt.Color,
    ): Int {
        g2.font = StoreStudioTheme.badgeFont
        val fm = g2.fontMetrics
        val textW = fm.stringWidth(text)
        val pad = JBUI.scale(5)
        val totalW = textW + pad * 2
        val corner = JBUI.scale(4)

        g2.color = bg
        g2.fillRoundRect(x, y, totalW, h, corner, corner)
        g2.color = fg
        val textY = y + (h - fm.height) / 2 + fm.ascent
        g2.drawString(text, x + pad, textY)

        return totalW
    }

    private fun clipString(text: String, fm: java.awt.FontMetrics, maxW: Int): String {
        if (fm.stringWidth(text) <= maxW) return text
        val ellipsis = "…"
        // Binary search for the longest prefix that fits (same result as trimming one char at a time)
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (fm.stringWidth(text.substring(0, mid) + ellipsis) <=
                maxW
            ) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        return if (low > 0) text.substring(0, low) + ellipsis else ""
    }

    private fun hitTest(pt: Point): StoreAssetCard? {
        val bounds = cardBounds
        for ((index, card) in filteredCards.withIndex()) {
            if (bounds.getOrNull(index)?.contains(pt) == true) return card
        }
        return null
    }

    /** Repaints only the cards whose hover state changed. */
    private fun repaintCards(vararg cards: StoreAssetCard?) {
        val bounds = cardBounds
        for (card in cards) {
            val index = filteredCards.indexOfFirst { it === card }
            bounds.getOrNull(index)?.let {
                repaint(it.x - 2, it.y - 2, it.width + 4, it.height + 4)
            }
        }
    }

    private fun showContextMenu(card: StoreAssetCard, pt: Point) {
        val group = DefaultActionGroup(
            cardAction("Jump to Composable Source") { onCardDoubleClicked(card) },
            cardAction("Copy Slot Key") {
                CopyPasteManager.getInstance().setContents(StringSelection(card.slotKey))
            },
            cardAction("Copy Image Path") {
                CopyPasteManager.getInstance().setContents(
                    StringSelection(card.imageFile.absolutePath),
                )
            },
            cardAction("Open Image in Editor") {
                LocalFileSystem.getInstance().findFileByIoFile(card.imageFile)?.let { vf ->
                    FileEditorManager.getInstance(project).openFile(vf, true)
                }
            },
        )
        ActionManager.getInstance()
            .createActionPopupMenu("StoreStudioGalleryCard", group)
            .component
            .show(this, pt.x, pt.y)
    }

    private fun cardAction(text: String, perform: () -> Unit): AnAction = object : AnAction(text) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        override fun actionPerformed(e: AnActionEvent) = perform()
    }

    override fun doLayout() {
        super.doLayout()
        relayout(triggerRevalidate = false)
    }

    // --- Scrollable implementation ---

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = JBUI.scale(24)

    override fun getScrollableBlockIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = visibleRect.height.coerceAtLeast(JBUI.scale(50))

    override fun getScrollableTracksViewportWidth(): Boolean = true

    override fun getScrollableTracksViewportHeight(): Boolean = false
}
