package org.jetbrains.kotlinconf.screenshots

import androidx.compose.runtime.Composable
import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreScreenshot
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.runtime.canvas.StoreScreenshotCanvas
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.kotlinconf.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.ui.AgendaScreen
import org.jetbrains.kotlinconf.ui.SessionDetailScreen
import org.jetbrains.kotlinconf.ui.SpeakersScreen
import sample_kotlinconf.shared.generated.resources.Res
import sample_kotlinconf.shared.generated.resources.agenda_headline
import sample_kotlinconf.shared.generated.resources.agenda_subheadline
import sample_kotlinconf.shared.generated.resources.light_headline
import sample_kotlinconf.shared.generated.resources.light_subheadline
import sample_kotlinconf.shared.generated.resources.session_headline
import sample_kotlinconf.shared.generated.resources.session_subheadline
import sample_kotlinconf.shared.generated.resources.speakers_headline
import sample_kotlinconf.shared.generated.resources.speakers_subheadline

@StoreScreenshot(
    id = "kotlinconf_agenda",
    order = 1,
    name = "Conference Schedule",
    devices = [DeviceFamily.PHONE, DeviceFamily.TABLET_7, DeviceFamily.TABLET],
    targets = [StoreTarget.PLAY_STORE, StoreTarget.APP_STORE],
)
@Composable
fun AgendaStoreScreenshot() {
    StoreScreenshotCanvas {
        Headline(stringResource(Res.string.agenda_headline))
        Subheadline(stringResource(Res.string.agenda_subheadline))
        DeviceMockup {
            KotlinConfTheme {
                AgendaScreen()
            }
        }
    }
}

@StoreScreenshot(
    id = "kotlinconf_speakers",
    order = 2,
    name = "World-Class Speakers",
    devices = [DeviceFamily.PHONE, DeviceFamily.TABLET_7, DeviceFamily.TABLET],
    targets = [StoreTarget.PLAY_STORE, StoreTarget.APP_STORE],
)
@Composable
fun SpeakersStoreScreenshot() {
    StoreScreenshotCanvas {
        Headline(stringResource(Res.string.speakers_headline))
        Subheadline(stringResource(Res.string.speakers_subheadline))
        DeviceMockup {
            KotlinConfTheme {
                SpeakersScreen()
            }
        }
    }
}

@StoreScreenshot(
    id = "kotlinconf_session",
    order = 3,
    name = "Session Deep-Dives",
    devices = [DeviceFamily.PHONE, DeviceFamily.TABLET_7, DeviceFamily.TABLET],
    targets = [StoreTarget.PLAY_STORE, StoreTarget.APP_STORE],
)
@Composable
fun SessionDetailStoreScreenshot() {
    StoreScreenshotCanvas {
        Headline(stringResource(Res.string.session_headline))
        Subheadline(stringResource(Res.string.session_subheadline))
        DeviceMockup {
            KotlinConfTheme {
                SessionDetailScreen()
            }
        }
    }
}

@StoreScreenshot(
    id = "kotlinconf_light",
    order = 4,
    name = "Clean Light Theme",
    devices = [DeviceFamily.PHONE, DeviceFamily.TABLET_7, DeviceFamily.TABLET],
    targets = [StoreTarget.PLAY_STORE, StoreTarget.APP_STORE],
)
@Composable
fun LightThemeStoreScreenshot() {
    StoreScreenshotCanvas {
        Headline(stringResource(Res.string.light_headline))
        Subheadline(stringResource(Res.string.light_subheadline))
        DeviceMockup {
            KotlinConfTheme(darkTheme = false) {
                AgendaScreen()
            }
        }
    }
}
