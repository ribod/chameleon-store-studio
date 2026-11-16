package dev.ribod.storestudio.runtime.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.runtime.background.StoreBackgrounds
import dev.ribod.storestudio.runtime.model.FrameType
import dev.ribod.storestudio.runtime.model.LocalStoreContext
import dev.ribod.storestudio.runtime.model.StoreContext
import dev.ribod.storestudio.runtime.model.StoreDeviceSpec
import dev.ribod.storestudio.runtime.semantics.isStoreSemanticsCaptureEnabled
import dev.ribod.storestudio.runtime.semantics.storeSemanticsProbe

/**
 * Root canvas container providing ambient layout context and styling for store screenshots.
 *
 * Automatically guarantees an opaque background to eliminate transparent pixels that would cause
 * asset rejection on Google Play or Apple App Store. Provides the [StoreScreenshotScope] DSL
 * to child composables.
 *
 * @param modifier Modifiers applied to the outer canvas layout.
 * @param background Decorative background brush, defaulting to [StoreBackgrounds.Default].
 * @param contentPadding Outer canvas padding; when null, slot-specific defaults are applied.
 * @param typography Typography hierarchy applied to headline and subheadline elements.
 * @param content Screenshot layout body scoped to [StoreScreenshotScope].
 */
@Composable
public fun StoreScreenshotCanvas(
    modifier: Modifier = Modifier,
    background: Brush = StoreBackgrounds.Default,
    contentPadding: PaddingValues? = null,
    typography: StoreTypography? = null,
    content: @Composable StoreScreenshotScope.() -> Unit,
) {
    val storeContext = LocalStoreContext.current
    // The caller is only needed by the headless semantics probe; resolve it once per composition
    // instead of walking the stack on every recomposition.
    val callingMethodFqn = remember {
        if (isStoreSemanticsCaptureEnabled()) inferCallingMethodFqn() else null
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .background(background),
    ) {
        val ambientLocale = Locale.current.toLanguageTag()
        val inferredContext = inferStoreContext(maxWidth, maxHeight, storeContext, ambientLocale)
        val effectivePadding = contentPadding
            ?: resolveDefaultPadding(inferredContext.deviceFamily)
        val effectiveTypography = typography
            ?: StoreTypography.forDevice(inferredContext.deviceFamily)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(effectivePadding)
                .storeSemanticsProbe(inferredContext, callingMethodFqn),
        ) {
            CompositionLocalProvider(
                LocalStoreContext provides inferredContext,
                LocalStoreTypography provides effectiveTypography,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val columnScope = this
                    val scope = remember(inferredContext, columnScope) {
                        StoreScreenshotScopeImpl(
                            storeContext = inferredContext,
                            columnScope = columnScope,
                        )
                    }
                    scope.content()
                }
            }
        }
    }
}

/**
 * Root canvas container specifically tailored for Google Play feature graphics (1024x500 px).
 *
 * @param modifier Modifiers applied to the outer canvas layout.
 * @param background Decorative background brush, defaulting to [StoreBackgrounds.Default].
 * @param content Feature graphic layout body scoped to [StoreScreenshotScope].
 */
@Composable
public fun StoreFeatureGraphicCanvas(
    modifier: Modifier = Modifier,
    background: Brush = StoreBackgrounds.Default,
    contentPadding: PaddingValues? = null,
    content: @Composable StoreScreenshotScope.() -> Unit,
) {
    StoreScreenshotCanvas(
        modifier = modifier,
        background = background,
        contentPadding = contentPadding ?: PaddingValues(horizontal = 32.dp, vertical = 20.dp),
        content = content,
    )
}

/**
 * Dynamically resolves execution context from canvas viewport geometry when unconfigured.
 */
internal fun inferStoreContext(
    width: Dp,
    height: Dp,
    current: StoreContext,
    ambientLocale: String = current.locale,
): StoreContext {
    if (current != StoreContext.Preview) {
        return current
    }

    val aspect = if (height.value > 0f) width.value / height.value else 0.5f

    val spec = when {
        aspect >= 1.5f -> StoreDeviceSpec.FeatureGraphic
        width >= 700.dp && aspect >= 0.70f -> StoreDeviceSpec.Ipad13
        width >= 700.dp -> StoreDeviceSpec.PlayTablet10
        width in 500.dp..700.dp -> StoreDeviceSpec.PlayTablet7
        width > 430.dp && aspect < 0.50f -> StoreDeviceSpec.Iphone69
        width in 400.dp..430.dp && aspect < 0.50f -> StoreDeviceSpec.Iphone65
        else -> StoreDeviceSpec.PlayPhone
    }

    val target = when (spec.frame) {
        FrameType.IPHONE_DYNAMIC_ISLAND, FrameType.IPAD_MODERN -> StoreTarget.APP_STORE
        else -> StoreTarget.PLAY_STORE
    }

    val family = when {
        spec == StoreDeviceSpec.FeatureGraphic -> DeviceFamily.PHONE

        spec.frame == FrameType.ANDROID_TABLET || spec.frame == FrameType.IPAD_MODERN ->
            if (spec == StoreDeviceSpec.PlayTablet7) DeviceFamily.TABLET_7 else DeviceFamily.TABLET

        else -> DeviceFamily.PHONE
    }

    return StoreContext(
        target = target,
        deviceFamily = family,
        deviceSpec = spec,
        locale = ambientLocale,
        screenshotId = current.screenshotId,
        isStoreRendering = current.isStoreRendering,
    )
}

/**
 * Resolves default adaptive content padding based on targeted hardware form factor.
 */
private fun resolveDefaultPadding(deviceFamily: DeviceFamily): PaddingValues = when (deviceFamily) {
    DeviceFamily.PHONE -> PaddingValues(
        start = 20.dp,
        top = 24.dp,
        end = 20.dp,
        bottom = 0.dp,
    )

    DeviceFamily.TABLET, DeviceFamily.TABLET_7 ->
        PaddingValues(start = 32.dp, top = 32.dp, end = 32.dp, bottom = 0.dp)

    DeviceFamily.WATCH -> PaddingValues(all = 20.dp)

    DeviceFamily.DESKTOP, DeviceFamily.TV, DeviceFamily.AUTO, DeviceFamily.XR ->
        PaddingValues(start = 40.dp, top = 40.dp, end = 40.dp, bottom = 0.dp)
}

/** First application frame on the current call stack, i.e. the @StoreScreenshot function. */
internal fun inferCallingMethodFqn(): String? = Throwable().stackTraceToString()
    .lineSequence()
    .map { it.trim() }
    .filter { it.startsWith("at ") }
    .map { it.removePrefix("at ").trim() }
    .firstOrNull { line ->
        !line.startsWith("java.") &&
            !line.startsWith("javax.") &&
            !line.startsWith("jdk.") &&
            !line.startsWith("sun.") &&
            !line.startsWith("kotlin.") &&
            !line.startsWith("kotlinx.") &&
            !line.startsWith("android.") &&
            !line.startsWith("androidx.") &&
            !line.startsWith("com.android.") &&
            !line.startsWith("dev.ribod.storestudio.") &&
            !line.contains("ComposableSingletons") &&
            !line.substringBefore('(').contains("\$lambda")
    }
    ?.substringBefore('(')
    ?.trim()
