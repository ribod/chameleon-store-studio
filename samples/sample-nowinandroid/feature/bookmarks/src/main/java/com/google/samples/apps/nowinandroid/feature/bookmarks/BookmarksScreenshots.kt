package com.google.samples.apps.nowinandroid.feature.bookmarks

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.google.samples.apps.nowinandroid.core.designsystem.theme.NiaTheme
import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreScreenshot
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.runtime.canvas.StoreScreenshotCanvas

@StoreScreenshot(
    id = "bookmarks_saved",
    order = 4,
    name = "Offline Saved Articles",
    devices = [DeviceFamily.PHONE, DeviceFamily.TABLET_7, DeviceFamily.TABLET],
    targets = [StoreTarget.PLAY_STORE, StoreTarget.APP_STORE],
)
@Composable
fun BookmarksStoreScreenshot() {
    StoreScreenshotCanvas {
        Headline(
            text = "Never Lose Track of Groundbreaking Android Architecture Articles " +
                "and Library Releases Ever Again",
            maxLines = 1,
        )
        Subheadline(stringResource(R.string.bookmarks_subheadline))
        DeviceMockup {
            NiaTheme {
                BookmarksScreen()
            }
        }
    }
}
