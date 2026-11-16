package dev.ribod.storestudio.idea.ui

import com.intellij.util.ui.JBUI
import dev.ribod.storestudio.idea.io.StoreThumbnailService
import dev.ribod.storestudio.idea.settings.StoreStudioTheme
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.swing.JComponent

/**
 * Interactive Before / After split-curtain canvas for visual regression diffing.
 *
 * Allows dragging a vertical curtain across the canvas to compare baseline and working
 * screenshots in real time with pixel accuracy.
 */
public class DiffSliderCanvas : JComponent() {

    private var baseFile: File? = null
    private var headFile: File? = null

    // Split position as a fraction [0.0 .. 1.0]
    private var splitRatio: Float = 0.5f
    private var isDragging: Boolean = false

    // One instance, so the thumbnail service keeps a single pending callback per image
    private val repaintOnLoad: (BufferedImage) -> Unit = { repaint() }

    init {
        isOpaque = true
        background = StoreStudioTheme.canvasBackground
        preferredSize = Dimension(JBUI.scale(420), JBUI.scale(560))

        val mouseHandler = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (isOverSplitter(e.x)) {
                    isDragging = true
                    updateSplit(e.x)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                isDragging = false
            }

            override fun mouseDragged(e: MouseEvent) {
                if (isDragging) {
                    updateSplit(e.x)
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                cursor = if (isOverSplitter(e.x)) {
                    Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
                } else {
                    Cursor.getDefaultCursor()
                }
            }
        }

        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
    }

    public fun setImages(base: File?, head: File?) {
        this.baseFile = base
        this.headFile = head
        repaint()
    }

    private fun isOverSplitter(x: Int): Boolean {
        val splitX = (width * splitRatio).toInt()
        return kotlin.math.abs(x - splitX) <= JBUI.scale(12)
    }

    private fun updateSplit(x: Int) {
        val w = width.coerceAtLeast(1)
        splitRatio = (x.toFloat() / w).coerceIn(0.05f, 0.95f)
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )

            val w = width
            val h = height
            val splitX = (w * splitRatio).toInt()

            // Thumbnails are cached per size bucket, so resizing the panel picks up a matching image
            val thumbnails = StoreThumbnailService.getInstance()
            val baseImage = baseFile?.let { thumbnails.getOrLoad(it, w, h, repaintOnLoad) }
            val headImage = headFile?.let { thumbnails.getOrLoad(it, w, h, repaintOnLoad) }

            // 1. Draw Baseline image clipped to the left of the split line
            val oldClip = g2.clip
            g2.setClip(0, 0, splitX, h)
            if (baseImage != null) {
                drawCentered(g2, baseImage, w, h)
            } else {
                paintPlaceholder(g2, 0, 0, splitX, h, "No Baseline Image")
            }

            // 2. Draw Head image clipped to the right of the split line
            g2.setClip(splitX, 0, w - splitX, h)
            if (headImage != null) {
                drawCentered(g2, headImage, w, h)
            } else {
                paintPlaceholder(g2, splitX, 0, w - splitX, h, "No Working Image")
            }

            // Restore full clip for splitter chrome
            g2.clip = oldClip

            // 3. Draw Splitter Line & Handle
            g2.color = StoreStudioTheme.curtainLineColor
            g2.stroke = BasicStroke(JBUI.scale(2).toFloat())
            g2.drawLine(splitX, 0, splitX, h)

            // Splitter center knob
            val knobRadius = JBUI.scale(14)
            val centerY = h / 2
            g2.color = StoreStudioTheme.curtainLineColor
            g2.fillOval(splitX - knobRadius, centerY - knobRadius, knobRadius * 2, knobRadius * 2)
            g2.color = Color.WHITE
            g2.drawOval(splitX - knobRadius, centerY - knobRadius, knobRadius * 2, knobRadius * 2)

            // Handle arrows
            g2.font = StoreStudioTheme.badgeFont
            val fm = g2.fontMetrics
            val arrows = "◀ ▶"
            g2.drawString(arrows, splitX - fm.stringWidth(arrows) / 2, centerY + fm.ascent / 2 - 1)

            // 4. Badges (BASELINE on left, WORKING on right)
            drawBadge(
                g2,
                "BASELINE",
                JBUI.scale(12),
                JBUI.scale(12),
                Color(0x65, 0x6D, 0x76),
                Color.WHITE,
            )
            val workingBadgeW = fm.stringWidth("WORKING") + JBUI.scale(12)
            drawBadge(
                g2,
                "WORKING",
                w - workingBadgeW - JBUI.scale(12),
                JBUI.scale(12),
                Color(0x09, 0x69, 0xDA),
                Color.WHITE,
            )
        } finally {
            g2.dispose()
        }
    }

    /** Draws [image] centered, scaled down to fit the canvas but never enlarged. */
    private fun drawCentered(g2: Graphics2D, image: BufferedImage, w: Int, h: Int) {
        val scale = minOf(w.toDouble() / image.width, h.toDouble() / image.height, 1.0)
        val drawW = (image.width * scale).toInt().coerceAtLeast(1)
        val drawH = (image.height * scale).toInt().coerceAtLeast(1)
        g2.drawImage(image, (w - drawW) / 2, (h - drawH) / 2, drawW, drawH, null)
    }

    private fun paintPlaceholder(g2: Graphics2D, x: Int, y: Int, w: Int, h: Int, text: String) {
        g2.color = StoreStudioTheme.canvasBackground
        g2.fillRect(x, y, w, h)
        g2.color = StoreStudioTheme.textMuted
        g2.font = StoreStudioTheme.subtitleFont
        val fm = g2.fontMetrics
        g2.drawString(text, x + (w - fm.stringWidth(text)) / 2, y + h / 2)
    }

    private fun drawBadge(g2: Graphics2D, text: String, x: Int, y: Int, bg: Color, fg: Color) {
        g2.font = StoreStudioTheme.badgeFont
        val fm = g2.fontMetrics
        val padX = JBUI.scale(6)
        val padY = JBUI.scale(3)
        val textW = fm.stringWidth(text)
        val badgeW = textW + padX * 2
        val badgeH = fm.height + padY * 2

        g2.color = bg
        g2.fillRoundRect(x, y, badgeW, badgeH, JBUI.scale(6), JBUI.scale(6))
        g2.color = fg
        g2.drawString(text, x + padX, y + padY + fm.ascent)
    }
}
