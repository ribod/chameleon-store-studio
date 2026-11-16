package dev.ribod.storestudio.runtime.mockup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders the mock status bar with pure vector icons and marketing time typography. Elements whose
 * style value is `null` are omitted and take no space.
 */
@Composable
internal fun StatusBarContent(
    style: StatusBarStyle,
    isIosStyle: Boolean,
    modifier: Modifier = Modifier,
) {
    if (style is StatusBarStyle.None) return

    val iconColor = if (style.darkIcons) Color.Black else Color.White
    // iOS: larger clock and glyphs, cellular first; Android: Wi-Fi first
    val iconSize = if (isIosStyle) 16.dp else 15.dp
    val batteryWidth = if (isIosStyle) 24.dp else 22.dp
    val batteryHeight = if (isIosStyle) 12.dp else 11.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isIosStyle) 28.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        style.time?.let {
            Text(
                text = it,
                color = iconColor,
                fontSize = if (isIosStyle) 15.sp else 14.sp,
                fontWeight = if (isIosStyle) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val cellular = style.cellular
            val wifi = style.wifi
            val battery = style.battery
            if (isIosStyle) {
                if (cellular != null) CellularSignalIcon(iconColor, iconSize, cellular)
                if (wifi != null) WifiSignalIcon(iconColor, iconSize, wifi)
            } else {
                if (wifi != null) WifiSignalIcon(iconColor, iconSize, wifi)
                if (cellular != null) CellularSignalIcon(iconColor, iconSize, cellular)
            }
            if (battery != null) {
                BatteryIcon(iconColor, batteryWidth, batteryHeight, battery, style.charging)
            }
        }
    }
}

private const val INACTIVE_ALPHA = 0.3f

/** Four ascending rounded bars; the first [level] bars are solid, the rest dimmed. */
@Composable
internal fun CellularSignalIcon(
    color: Color,
    size: Dp,
    level: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val barCount = 4
        val totalWidth = this.size.width
        val totalHeight = this.size.height
        val gap = totalWidth * 0.12f
        val barWidth = (totalWidth - (barCount - 1) * gap) / barCount
        val corner = CornerRadius(barWidth * 0.35f, barWidth * 0.35f)

        for (i in 0 until barCount) {
            val barHeight = totalHeight * (0.35f + 0.65f * i / (barCount - 1))
            drawRoundRect(
                color = if (i < level) color else color.copy(alpha = INACTIVE_ALPHA),
                topLeft = Offset(i * (barWidth + gap), totalHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
        }
    }
}

/**
 * Wi-Fi fan: a bottom wedge plus three concentric arc bands (levels 1..4); segments above [level]
 * are dimmed.
 */
@Composable
internal fun WifiSignalIcon(color: Color, size: Dp, level: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val sweep = 90f
        val start = 270f - sweep / 2f
        val radius = minOf(this.size.width / 1.4142f, this.size.height / 0.95f)
        val apex = Offset(this.size.width / 2f, (this.size.height + radius) / 2f)
        val gap = radius * 0.08f
        val edges = floatArrayOf(0.30f, 0.55f, 0.78f, 1f).map { it * radius }

        // No Path(): on Android the jvm runtime artifact would reach Skiko-only classes.
        for (i in 0 until 4) {
            val tint = if (i < level) color else color.copy(alpha = INACTIVE_ALPHA)
            if (i == 0) {
                drawArc(
                    color = tint,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset(apex.x - edges[0], apex.y - edges[0]),
                    size = Size(edges[0] * 2, edges[0] * 2),
                )
            } else {
                val inner = edges[i - 1] + gap
                val band = edges[i] - inner
                val mid = inner + band / 2f
                drawArc(
                    color = tint,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(apex.x - mid, apex.y - mid),
                    size = Size(mid * 2, mid * 2),
                    style = Stroke(width = band, cap = StrokeCap.Butt),
                )
            }
        }
    }
}

/** Rounded battery shell with nub, proportional fill (red at <= 20%) and a bolt when charging. */
@Composable
internal fun BatteryIcon(
    color: Color,
    width: Dp,
    height: Dp,
    level: Int,
    charging: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width, height)) {
        val totalWidth = this.size.width
        val totalHeight = this.size.height
        val nubWidth = totalWidth * 0.08f
        val bodyWidth = totalWidth - nubWidth - 1f
        val strokeWidth = totalHeight * 0.10f
        val radius = totalHeight * 0.28f

        // Shell (dimmed outline)
        drawRoundRect(
            color = color.copy(alpha = 0.45f),
            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = Size(bodyWidth - strokeWidth, totalHeight - strokeWidth),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = strokeWidth),
        )

        // Fill proportional to level
        val pad = strokeWidth * 2f
        val maxFill = bodyWidth - pad * 2
        val fillWidth = maxFill * level.coerceIn(0, 100) / 100f
        val low = level <= 20 && !charging
        if (fillWidth > 0f) {
            drawRoundRect(
                color = if (low) Color(0xFFFF3B30) else color,
                topLeft = Offset(pad, pad),
                size = Size(fillWidth, totalHeight - pad * 2),
                cornerRadius = CornerRadius(radius * 0.5f, radius * 0.5f),
            )
        }

        // Positive contact nub
        val nubHeight = totalHeight * 0.38f
        drawRoundRect(
            color = color.copy(alpha = 0.45f),
            topLeft = Offset(bodyWidth + 1f, (totalHeight - nubHeight) / 2f),
            size = Size(nubWidth, nubHeight),
            cornerRadius = CornerRadius(nubWidth * 0.4f, nubWidth * 0.4f),
        )

        if (charging) {
            val cx = bodyWidth / 2f
            val cy = totalHeight / 2f
            val h = totalHeight * 0.7f
            val w = h * 0.55f
            // Contrast ink where the fill covers the bolt, icon ink over the empty part
            val ink = if (level >= 50) {
                if (color == Color.Black) Color.White else Color.Black
            } else {
                color
            }
            val stroke = h * 0.2f
            val top = Offset(cx + w * 0.22f, cy - h / 2f)
            val midLeft = Offset(cx - w * 0.3f, cy + h * 0.06f)
            val midRight = Offset(cx + w * 0.3f, cy - h * 0.06f)
            val bottom = Offset(cx - w * 0.22f, cy + h / 2f)
            drawLine(ink, top, midLeft, stroke, StrokeCap.Round)
            drawLine(ink, midLeft, midRight, stroke, StrokeCap.Round)
            drawLine(ink, midRight, bottom, stroke, StrokeCap.Round)
        }
    }
}
