package com.google.samples.apps.nowinandroid.feature.foryou

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.google.samples.apps.nowinandroid.core.designsystem.theme.NiaTheme
import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreScreenshot
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.runtime.canvas.StoreScreenshotCanvas

@StoreScreenshot(
    id = "foryou_feed",
    order = 1,
    name = "Curated News Feed",
    devices = [DeviceFamily.PHONE, DeviceFamily.TABLET_7, DeviceFamily.TABLET],
    targets = [StoreTarget.PLAY_STORE, StoreTarget.APP_STORE],
)
@Composable
fun ForYouStoreScreenshot() {
    StoreScreenshotCanvas(
        background = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F2027),
                Color(0xFF203A43),
                Color(0xFF2C5364),
            ),
        ),
    ) {
        Headline("Stay Ahead with Android Insights")
        Subheadline("Real-time announcements, deep dives, and expert tutorials")
        DeviceMockup {
            NiaTheme {
                ForYouScreen()
            }
        }
    }
}

@StoreScreenshot(
    id = "foryou_dark",
    order = 2,
    name = "Dark Theme Experience",
    devices = [DeviceFamily.PHONE],
    targets = [StoreTarget.PLAY_STORE, StoreTarget.APP_STORE],
)
@Composable
fun ForYouDarkStoreScreenshot() {
    StoreScreenshotCanvas {
        Headline(stringResource(R.string.foryou_dark_headline))
        Subheadline(stringResource(R.string.foryou_dark_subheadline))
        DeviceMockup {
            NiaTheme(darkTheme = true) {
                ForYouScreen()
            }
        }
    }
}
