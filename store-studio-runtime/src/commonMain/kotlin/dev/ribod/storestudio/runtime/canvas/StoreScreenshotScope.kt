package dev.ribod.storestudio.runtime.canvas

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ribod.storestudio.runtime.mockup.DeviceFrame
import dev.ribod.storestudio.runtime.mockup.FrameColors
import dev.ribod.storestudio.runtime.mockup.StatusBarStyle
import dev.ribod.storestudio.runtime.model.StoreContext
import dev.ribod.storestudio.runtime.mockup.DeviceMockup as BaseDeviceMockup

/**
 * DSL scope providing layout components and styling controls inside a store screenshot canvas.
 *
 * Annotated with [LayoutScopeMarker] to restrict store marketing components to screenshot
 * declarations, preventing accidental usage within production application UI.
 */
@LayoutScopeMarker
@Stable
public interface StoreScreenshotScope {
    /** Active store ambient execution context. */
    public val storeContext: StoreContext

    /**
     * Prominent headline title displayed above or alongside framed device mockups.
     */
    @Composable
    public fun Headline(
        text: String,
        modifier: Modifier = Modifier,
        style: TextStyle = LocalStoreTypography.current.headline,
        maxLines: Int = Int.MAX_VALUE,
        overflow: TextOverflow = TextOverflow.Clip,
    )

    /**
     * Secondary descriptive subheadline subtitle.
     */
    @Composable
    public fun Subheadline(
        text: String,
        modifier: Modifier = Modifier,
        style: TextStyle = LocalStoreTypography.current.subheadline,
        maxLines: Int = Int.MAX_VALUE,
        overflow: TextOverflow = TextOverflow.Clip,
    )

    /**
     * Frames an application composable within an ultra-crisp vector device chassis.
     */
    @Composable
    public fun DeviceMockup(
        modifier: Modifier = Modifier,
        frame: DeviceFrame = DeviceFrame.Auto,
        statusBar: StatusBarStyle? = null,
        colors: FrameColors = FrameColors.Dark,
        screenWidthDp: Dp = Dp.Unspecified,
        safeAreaPadding: Boolean = false,
        screenBackground: Color? = null,
        content: @Composable () -> Unit,
    )

    /** Aligns a child element horizontally within the canvas column arrangement. */
    public fun Modifier.align(alignment: Alignment.Horizontal): Modifier

    /** Distributes available vertical space within the screenshot canvas. */
    public fun Modifier.weight(weight: Float): Modifier
}

/**
 * Concrete internal implementation of [StoreScreenshotScope] delegating layout to [ColumnScope].
 */
internal class StoreScreenshotScopeImpl(
    override val storeContext: StoreContext,
    private val columnScope: ColumnScope,
) : StoreScreenshotScope {

    @Composable
    override fun Headline(
        text: String,
        modifier: Modifier,
        style: TextStyle,
        maxLines: Int,
        overflow: TextOverflow,
    ) {
        Text(
            text = text,
            modifier = modifier.fillMaxWidth(),
            style = style,
            maxLines = maxLines,
            overflow = overflow,
        )
        Spacer(modifier = Modifier.height(4.dp))
    }

    @Composable
    override fun Subheadline(
        text: String,
        modifier: Modifier,
        style: TextStyle,
        maxLines: Int,
        overflow: TextOverflow,
    ) {
        Text(
            text = text,
            modifier = modifier.fillMaxWidth(),
            style = style,
            maxLines = maxLines,
            overflow = overflow,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    @Composable
    override fun DeviceMockup(
        modifier: Modifier,
        frame: DeviceFrame,
        statusBar: StatusBarStyle?,
        colors: FrameColors,
        screenWidthDp: Dp,
        safeAreaPadding: Boolean,
        screenBackground: Color?,
        content: @Composable () -> Unit,
    ) {
        val effectiveModifier = if (modifier == Modifier) {
            with(columnScope) { Modifier.weight(1f) }
        } else {
            modifier
        }
        BaseDeviceMockup(
            modifier = effectiveModifier,
            frame = frame,
            statusBar = statusBar,
            colors = colors,
            screenWidthDp = screenWidthDp,
            safeAreaPadding = safeAreaPadding,
            screenBackground = screenBackground,
            content = content,
        )
    }

    override fun Modifier.align(alignment: Alignment.Horizontal): Modifier =
        with(columnScope) { this@align.align(alignment) }

    override fun Modifier.weight(weight: Float): Modifier =
        with(columnScope) { this@weight.weight(weight) }
}
