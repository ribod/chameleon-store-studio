package dev.ribod.storestudio.runtime.mockup

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Insets representing hardware display safe area margins (status bar, dynamic island,
 * gesture navigation pill, display cutouts, and corner radii).
 */
@Immutable
public data class StoreSafeAreaInsets(
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
    val start: Dp = 0.dp,
    val end: Dp = 0.dp,
) {
    public val asPaddingValues: PaddingValues
        get() = PaddingValues(start = start, top = top, end = end, bottom = bottom)
}

/**
 * CompositionLocal providing active safe area insets of the surrounding [DeviceMockup] chassis.
 */
public val LocalStoreSafeArea: ProvidableCompositionLocal<StoreSafeAreaInsets> =
    compositionLocalOf { StoreSafeAreaInsets() }

/**
 * Applies active device safe area padding to an application composable or screen container.
 *
 * Useful for building authentic edge-to-edge screens where the app's background or top app bar
 * extends fully beneath the status bar and navigation pill while inner contents respect insets.
 *
 * @param top When true, applies the top inset (status bar / camera cutout).
 * @param bottom When true, applies the bottom inset (gesture bar).
 * @param horizontal When true, applies start and end insets.
 */
@Composable
public fun Modifier.storeSafeAreaPadding(
    top: Boolean = true,
    bottom: Boolean = true,
    horizontal: Boolean = true,
): Modifier {
    val insets = LocalStoreSafeArea.current
    return this.padding(
        top = if (top) insets.top else 0.dp,
        bottom = if (bottom) insets.bottom else 0.dp,
        start = if (horizontal) insets.start else 0.dp,
        end = if (horizontal) insets.end else 0.dp,
    )
}

/**
 * Scope provided to content inside [DeviceMockup] exposing hardware safe area metrics.
 */
public interface DeviceMockupScope {
    /** Hardware safe area insets for the active device frame. */
    public val safeArea: StoreSafeAreaInsets

    /** Safe area insets represented as [PaddingValues]. */
    public val safeAreaPadding: PaddingValues

    /** Convenience modifier applying full top and bottom safe area padding. */
    public val safeAreaModifier: Modifier
}

internal class DeviceMockupScopeImpl(override val safeArea: StoreSafeAreaInsets) :
    DeviceMockupScope {
    override val safeAreaPadding: PaddingValues
        get() = safeArea.asPaddingValues

    override val safeAreaModifier: Modifier
        get() = Modifier.padding(top = safeArea.top, bottom = safeArea.bottom)
}
