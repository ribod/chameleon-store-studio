package dev.ribod.storestudio.runtime.mockup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ribod.storestudio.runtime.model.FrameType
import dev.ribod.storestudio.runtime.model.LocalInsideMockup
import dev.ribod.storestudio.runtime.model.LocalStoreContext

/**
 * Renders an app screen framed within an ultra-crisp, purely vector hardware device chassis.
 *
 * Rather than scaling a rasterized bitmap, this component computes a virtual density matching
 * the physical screen width in density-independent pixels. All typography and vector drawings
 * within [content] rasterize natively at full resolution without blurriness.
 *
 * @param modifier Layout modifiers applied to the device mockup container.
 * @param frame Hardware frame design to display, defaulting to [DeviceFrame.Auto].
 * @param statusBar Status bar styling; `null` uses [LocalStatusBarDefaults] (off unless enabled in Gradle).
 * @param colors Color palette applied to the outer chassis, bezels, and buttons.
 * @param screenWidthDp Virtual display width used by wrapped composables to compute layouts.
 * @param safeAreaPadding When true, insets content below status bar and above gesture navigation.
 * @param screenBackground Screen glass background color; defaults to dark or light based on icons.
 * @param content Screen composable content to display inside the device mockup.
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
) {
    val storeContext = LocalStoreContext.current

    // Guard against recursive mockup nesting that would corrupt virtual layout densities
    if (LocalInsideMockup.current) {
        if (storeContext.isStoreRendering) {
            error("DeviceMockup cannot be nested inside another DeviceMockup screen content.")
        }
    }

    // Resolve the active frame specification based on store target and device slot
    val resolvedFrame = if (frame is DeviceFrame.Auto) {
        frame.resolveFrame(storeContext.deviceSpec.frame)
    } else {
        frame
    }

    // Fast-path for borderless presentations
    if (resolvedFrame is DeviceFrame.None) {
        Box(modifier = modifier) {
            CompositionLocalProvider(LocalInsideMockup provides true) {
                content()
            }
        }
        return
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight

        // Maintain frame aspect ratio within available layout bounds
        val containerAspect = availableWidth.value / availableHeight.value
        val (mockupWidth, mockupHeight) = if (containerAspect > resolvedFrame.aspect) {
            val h = availableHeight
            val w = (availableHeight.value * resolvedFrame.aspect).dp
            w to h
        } else {
            val w = availableWidth
            val h = (availableWidth.value / resolvedFrame.aspect).dp
            w to h
        }

        val bezelWidth = mockupWidth * resolvedFrame.bezelFraction
        val screenWidth = mockupWidth - (bezelWidth * 2)
        val screenHeight = mockupHeight - (bezelWidth * 2)

        val outerCornerRadius = mockupWidth * resolvedFrame.cornerRadiusFraction
        val screenCornerRadius = screenWidth * resolvedFrame.screenCornerRadiusFraction

        val effectiveVirtualWidthDp = if (screenWidthDp != Dp.Unspecified) {
            screenWidthDp
        } else {
            resolvedFrame.defaultScreenWidthDp
        }

        // Draw hardware buttons and outer chassis body
        DeviceChassisBackground(
            modifier = Modifier.size(mockupWidth, mockupHeight),
            colors = colors,
            outerCornerRadius = outerCornerRadius,
            bezelWidth = bezelWidth,
            isTablet = resolvedFrame.isTablet,
        )

        // Calculate virtual density for high-DPI native rasterization of app content
        val currentDensity = LocalDensity.current
        val screenWidthPx = with(currentDensity) { screenWidth.toPx() }
        val virtualDensityScale = screenWidthPx / effectiveVirtualWidthDp.value
        val virtualDensity = Density(
            density = virtualDensityScale,
            fontScale = currentDensity.fontScale,
        )

        val isIosFamily = resolvedFrame is DeviceFrame.PhoneIsland ||
            resolvedFrame is DeviceFrame.TabletSlim

        val requestedStatusBar = statusBar ?: LocalStatusBarDefaults.current
        val effectiveStatusBar = when (requestedStatusBar) {
            is StatusBarStyle.Auto -> with(requestedStatusBar) {
                if (isIosFamily) {
                    StatusBarStyle.Ios(time, wifi, cellular, battery, charging, darkIcons)
                } else {
                    StatusBarStyle.Android(time, wifi, cellular, battery, charging, darkIcons)
                }
            }

            else -> requestedStatusBar
        }

        val resolvedScreenBg = screenBackground
            ?: if (effectiveStatusBar.darkIcons) Color.White else Color(0xFF141416)

        // Render framed app screen with virtual density and screen clipping
        Box(
            modifier = Modifier
                .size(screenWidth, screenHeight)
                .clip(RoundedCornerShape(screenCornerRadius))
                .background(resolvedScreenBg),
        ) {
            val safeAreaInsets = StoreSafeAreaInsets(
                top = resolvedFrame.statusBarHeightDp,
                bottom = resolvedFrame.bottomInsetDp,
            )

            CompositionLocalProvider(
                LocalDensity provides virtualDensity,
                LocalInsideMockup provides true,
                LocalStoreSafeArea provides safeAreaInsets,
            ) {
                val contentModifier = if (safeAreaPadding) {
                    Modifier
                        .fillMaxSize()
                        .padding(
                            top = resolvedFrame.statusBarHeightDp,
                            bottom = resolvedFrame.bottomInsetDp,
                        )
                } else {
                    Modifier.fillMaxSize()
                }

                Box(modifier = contentModifier) {
                    content()
                }

                // Draw status bar overlay across the top edge
                if (effectiveStatusBar !is StatusBarStyle.None &&
                    resolvedFrame.statusBarHeightDp > 0.dp
                ) {
                    StatusBarContent(
                        style = effectiveStatusBar,
                        isIosStyle = isIosFamily,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(resolvedFrame.statusBarHeightDp)
                            .align(Alignment.TopCenter),
                    )
                }

                // Draw camera cutout apertures on top of display content
                when (val cutout = resolvedFrame.cutout) {
                    is Cutout.PunchHole -> {
                        Box(
                            modifier = Modifier
                                .padding(top = cutout.topMarginDp)
                                .size(cutout.diameterDp)
                                .clip(RoundedCornerShape(cutout.diameterDp / 2))
                                .background(colors.cutout)
                                .align(Alignment.TopCenter),
                        )
                    }

                    is Cutout.DynamicIsland -> {
                        Box(
                            modifier = Modifier
                                .padding(top = cutout.topMarginDp)
                                .width(cutout.widthDp)
                                .height(cutout.heightDp)
                                .clip(RoundedCornerShape(cutout.cornerRadiusDp))
                                .background(colors.cutout)
                                .align(Alignment.TopCenter),
                        )
                    }

                    is Cutout.None -> {}
                }

                // Draw bottom gesture navigation pill
                if (resolvedFrame.bottomInsetDp > 0.dp) {
                    val pillColor = if (effectiveStatusBar.darkIcons) {
                        Color(0x99000000)
                    } else {
                        Color(0x99FFFFFF)
                    }
                    val pillWidth = if (isIosFamily) 140.dp else 96.dp
                    val pillHeight = if (isIosFamily) 4.dp else 3.dp
                    Box(
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .width(pillWidth)
                            .height(pillHeight)
                            .clip(RoundedCornerShape(pillHeight / 2))
                            .background(pillColor)
                            .align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

/**
 * Draws the vector chassis body, side hardware buttons, and glass bezel border.
 */
@Composable
private fun DeviceChassisBackground(
    modifier: Modifier,
    colors: FrameColors,
    outerCornerRadius: Dp,
    bezelWidth: Dp,
    isTablet: Boolean,
) {
    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val totalHeight = size.height
        val cornerPx = outerCornerRadius.toPx()
        val bezelPx = bezelWidth.toPx()

        if (!isTablet) {
            // Draw hardware side buttons on the outer perimeter
            val buttonWidth = totalWidth * 0.008f
            val volumeBtnHeight = totalHeight * 0.06f
            val powerBtnHeight = totalHeight * 0.08f

            // Draw left volume buttons
            drawRoundRect(
                color = colors.buttons,
                topLeft = Offset(-buttonWidth * 0.5f, totalHeight * 0.22f),
                size = Size(buttonWidth, volumeBtnHeight),
                cornerRadius = CornerRadius(buttonWidth, buttonWidth),
            )
            drawRoundRect(
                color = colors.buttons,
                topLeft = Offset(-buttonWidth * 0.5f, totalHeight * 0.30f),
                size = Size(buttonWidth, volumeBtnHeight),
                cornerRadius = CornerRadius(buttonWidth, buttonWidth),
            )

            // Draw right power button
            drawRoundRect(
                color = colors.buttons,
                topLeft = Offset(totalWidth - buttonWidth * 0.5f, totalHeight * 0.25f),
                size = Size(buttonWidth, powerBtnHeight),
                cornerRadius = CornerRadius(buttonWidth, buttonWidth),
            )
        }

        // Draw main chassis outer body
        drawRoundRect(
            color = colors.body,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(cornerPx, cornerPx),
        )

        // Draw inner bezel border
        val innerCornerPx = (cornerPx - bezelPx).coerceAtLeast(0f)
        drawRoundRect(
            color = colors.bezel,
            topLeft = Offset(bezelPx * 0.5f, bezelPx * 0.5f),
            size = Size(totalWidth - bezelPx, totalHeight - bezelPx),
            cornerRadius = CornerRadius(innerCornerPx, innerCornerPx),
            style = Stroke(width = bezelPx),
        )
    }
}
