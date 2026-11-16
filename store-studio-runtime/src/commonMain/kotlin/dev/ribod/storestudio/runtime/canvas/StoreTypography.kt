package dev.ribod.storestudio.runtime.canvas

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import dev.ribod.storestudio.annotations.DeviceFamily

/**
 * Typography styling configurations for store screenshot marketing copy.
 *
 * @property headline Large prominent headline title style.
 * @property subheadline Secondary descriptive subtitle style.
 * @property caption Small annotations or supplementary disclosure style.
 */
@Immutable
public data class StoreTypography(
    public val headline: TextStyle,
    public val subheadline: TextStyle,
    public val caption: TextStyle,
) {
    public companion object {
        /**
         * Compact, balanced typography tuned for smartphone viewport constraints.
         */
        public val Phone: StoreTypography = StoreTypography(
            headline = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            ),
            subheadline = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = Color(0xFFD0D0D8),
                textAlign = TextAlign.Center,
            ),
            caption = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Light,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = Color(0xFFA0A0A8),
                textAlign = TextAlign.Center,
            ),
        )

        /**
         * Spacious typography tuned for tablet and large-format viewport displays.
         */
        public val Tablet: StoreTypography = StoreTypography(
            headline = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            ),
            subheadline = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = Color(0xFFD0D0D8),
                textAlign = TextAlign.Center,
            ),
            caption = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = Color(0xFFA0A0A8),
                textAlign = TextAlign.Center,
            ),
        )

        /**
         * Default fallback typography profile using phone-scaled typography.
         */
        public val Default: StoreTypography = Phone

        /**
         * Resolves the recommended typography profile for a specific device family.
         */
        public fun forDevice(deviceFamily: DeviceFamily): StoreTypography = when (deviceFamily) {
            DeviceFamily.TABLET, DeviceFamily.TABLET_7, DeviceFamily.DESKTOP,
            DeviceFamily.TV, DeviceFamily.AUTO, DeviceFamily.XR,
            -> Tablet

            else -> Phone
        }
    }
}

/**
 * Composition local providing active typography styling within store screenshot scopes.
 */
public val LocalStoreTypography: ProvidableCompositionLocal<StoreTypography> =
    staticCompositionLocalOf { StoreTypography.Default }
