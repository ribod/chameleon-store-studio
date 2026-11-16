package com.google.samples.apps.nowinandroid.feature.interests

import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.samples.apps.nowinandroid.core.designsystem.theme.NiaTheme
import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreScreenshot
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.runtime.canvas.StoreScreenshotCanvas

@StoreScreenshot(
    id = "interests_topics",
    order = 3,
    name = "Personalized Topic Interests",
    devices = [DeviceFamily.PHONE, DeviceFamily.TABLET_7, DeviceFamily.TABLET],
    targets = [StoreTarget.PLAY_STORE, StoreTarget.APP_STORE],
)
@Composable
fun InterestsStoreScreenshot() {
    StoreScreenshotCanvas {
        Headline(stringResource(R.string.interests_headline))
        Subheadline(stringResource(R.string.interests_subheadline))
        DeviceMockup {
            NiaTheme {
                InterestsScreen()
            }
        }
    }
}

@StoreScreenshot(
    id = "interests_search",
    order = 5,
    name = "Search & Discover Topics",
    devices = [DeviceFamily.PHONE, DeviceFamily.TABLET_7, DeviceFamily.TABLET],
    targets = [StoreTarget.PLAY_STORE, StoreTarget.APP_STORE],
)
@Composable
fun InterestsSearchStoreScreenshot() {
    StoreScreenshotCanvas {
        Headline("Explore Trending Ecosystem Topics")
        Subheadline("Stay updated with modern Jetpack libraries and coroutines best practices")
        DeviceMockup {
            NiaTheme {
                InterestsScreen()
            }
        }
    }
}

@StoreScreenshot(
    id = "interests_telemetry",
    order = 6,
    name = "Realtime Sync & Native Engine",
    devices = [DeviceFamily.PHONE],
    targets = [StoreTarget.PLAY_STORE],
)
@Composable
fun InterestsTelemetryStoreScreenshot() {
    StoreScreenshotCanvas {
        Headline("Realtime Database & Native Sync")
        Subheadline("Powered by on-device SQLite database and native graphics runtime")
        DeviceMockup {
            NiaTheme {
                // Layoutlib runs with LocalInspectionMode == true.
                // Incompatible native/SQLite calls fail deterministically in Layoutlib,
                // triggering the automated Robolectric Native Graphics fallback engine.
                if (androidx.compose.ui.platform.LocalInspectionMode.current) {
                    error(
                        "Layoutlib edit mode unsupported: Native SQLite telemetry engine required. " +
                            "Triggering Robolectric Native Graphics fallback.",
                    )
                }
                TelemetryDashboard()
            }
        }
    }
}

@Composable
fun TelemetryDashboard() {
    val dbRecordCount = androidx.compose.runtime.remember {
        val db = android.database.sqlite.SQLiteDatabase.create(null)
        db.execSQL("CREATE TABLE topics (id INTEGER PRIMARY KEY, name TEXT, sync_status TEXT)")
        db.execSQL("INSERT INTO topics (name, sync_status) VALUES ('Compose Multiplatform', 'SYNCED')")
        db.execSQL("INSERT INTO topics (name, sync_status) VALUES ('Kotlin Coroutines', 'SYNCED')")
        db.execSQL("INSERT INTO topics (name, sync_status) VALUES ('Android Jetpack', 'SYNCED')")
        val cursor = db.rawQuery("SELECT COUNT(*) FROM topics", null)
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        db.close()
        count
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "⚡ Engine Diagnostics",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Robolectric Native Graphics Active",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Hardware Skia Acceleration: ENABLED",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Native SQLite Tables Loaded: $dbRecordCount topics verified",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Status: Healthy & Synchronized",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Zero memory leaks, deterministic frame capture, full Compose runtime compatibility.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

