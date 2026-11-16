package dev.ribod.storestudio.idea.settings

import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font

/**
 * Unified visual palette and styling definitions for Store Studio's native Swing components.
 *
 * Automatically adapts between IntelliJ Light and Dark (Darcula / New UI) themes.
 */
public object StoreStudioTheme {

    // Canvas & Container Backgrounds
    public val canvasBackground: JBColor = JBColor(Color(0xF4F5F7), Color(0x1E1F22))
    public val panelBackground: JBColor = JBColor(Color(0xFFFFFF), Color(0x2B2D30))
    public val cardBackground: JBColor = JBColor(Color(0xFFFFFF), Color(0x2B2D30))
    public val cardBackgroundHover: JBColor = JBColor(Color(0xF0F4FA), Color(0x32353B))

    // Card Borders & Outlines
    public val cardBorder: JBColor = JBColor(Color(0xD1D5DB), Color(0x393B40))
    public val cardBorderSelected: JBColor = JBColor(Color(0x3574F0), Color(0x589DF6))
    public val curtainLineColor: JBColor = JBColor(Color(0x3574F0), Color(0x589DF6))

    // Typography Colors
    public val textPrimary: JBColor = JBColor(Color(0x1F2328), Color(0xDFE1E5))
    public val textMuted: JBColor = JBColor(Color(0x656D76), Color(0x9DA0A8))
    public val textLink: JBColor = JBColor(Color(0x0969DA), Color(0x589DF6))

    // Badges: Store Target
    public val badgePlayStoreBg: JBColor = JBColor(Color(0xE6F4EA), Color(0x1E3B2B))
    public val badgePlayStoreFg: JBColor = JBColor(Color(0x137333), Color(0x81C995))
    public val badgeAppStoreBg: JBColor = JBColor(Color(0xE8F0FE), Color(0x1E2E4A))
    public val badgeAppStoreFg: JBColor = JBColor(Color(0x1A73E8), Color(0x8AB4F8))

    // Badges: Engine
    public val badgeRobolectricBg: JBColor = JBColor(Color(0xFEF7E0), Color(0x3E381A))
    public val badgeRobolectricFg: JBColor = JBColor(Color(0xB06000), Color(0xFDD663))
    public val badgeLayoutlibBg: JBColor = JBColor(Color(0xF1F3F4), Color(0x303236))
    public val badgeLayoutlibFg: JBColor = JBColor(Color(0x5F6368), Color(0xBDC1C6))

    // Validation Status
    public val validBg: JBColor = JBColor(Color(0xE6F4EA), Color(0x1E3B2B))
    public val validFg: JBColor = JBColor(Color(0x137333), Color(0x81C995))
    public val warningBg: JBColor = JBColor(Color(0xFEF7E0), Color(0x3E381A))
    public val warningFg: JBColor = JBColor(Color(0xB06000), Color(0xFDD663))
    public val errorBg: JBColor = JBColor(Color(0xFCE8E6), Color(0x442326))
    public val errorFg: JBColor = JBColor(Color(0xC5221F), Color(0xF28B82))

    // Strokes
    public val regularStroke: BasicStroke = BasicStroke(1f)
    public val selectedStroke: BasicStroke = BasicStroke(2f)
    public val dashedStroke: BasicStroke = BasicStroke(
        1.5f,
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER,
        10f,
        floatArrayOf(4f, 4f),
        0f,
    )

    // Standard Fonts: rebuilt when the IDE font or zoom changes (values are read on every paint)
    public val titleFont: Font get() = fonts().title
    public val subtitleFont: Font get() = fonts().subtitle
    public val badgeFont: Font get() = fonts().badge
    public val monoFont: Font get() = fonts().mono

    private class Fonts(
        val key: Pair<Font, Float>,
        val title: Font,
        val subtitle: Font,
        val badge: Font,
        val mono: Font,
    )

    @Volatile
    private var cachedFonts: Fonts? = null

    private fun fonts(): Fonts {
        val key = UIUtil.getLabelFont() to JBUIScale.scale(1f)
        cachedFonts?.takeIf { it.key == key }?.let { return it }
        return Fonts(
            key = key,
            title = JBUI.Fonts.label().deriveFont(Font.BOLD, JBUI.scale(13).toFloat()),
            subtitle = JBUI.Fonts.label().deriveFont(Font.PLAIN, JBUI.scale(11).toFloat()),
            badge = JBUI.Fonts.miniFont().deriveFont(Font.BOLD),
            mono = JBUI.Fonts.create(Font.MONOSPACED, JBUI.scale(11)),
        ).also { cachedFonts = it }
    }
}
