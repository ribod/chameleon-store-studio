package com.google.samples.apps.nowinandroid.feature.bookmarks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.samples.apps.nowinandroid.core.designsystem.component.NiaCard
import com.google.samples.apps.nowinandroid.core.designsystem.component.NiaTopAppBar
import dev.ribod.storestudio.runtime.mockup.LocalStoreSafeArea
import dev.ribod.storestudio.runtime.mockup.storeSafeAreaPadding

data class BookmarkedResource(
    val id: String,
    val title: String,
    val description: String,
    val topic: String,
    val date: String,
)

val sampleSavedResources = listOf(
    BookmarkedResource(
        id = "1",
        title = "Compose Multiplatform 1.8: iOS & Desktop Stability",
        description = "Share business logic and UI seamlessly across Android and iOS platforms.",
        topic = "KMP",
        date = "Saved yesterday",
    ),
    BookmarkedResource(
        id = "2",
        title = "Automated Store Listing Pipelines with Compose Store Studio",
        description = "Turn Composable previews into validated, localized app store screenshots.",
        topic = "Tooling",
        date = "Saved 3 days ago",
    ),
    BookmarkedResource(
        id = "3",
        title = "Deep Dive into Layoutlib Headless Rendering Architecture",
        description = "Rendering pixel-perfect vector frames directly from the Gradle build.",
        topic = "Architecture",
        date = "Saved last week",
    ),
)

/** Saved articles resolved from standard Android resources strings.xml. */
@Composable
fun localizedSavedResources(): List<BookmarkedResource> = listOf(
    BookmarkedResource(
        id = "1",
        title = stringResource(R.string.bookmarks_1_title),
        description = stringResource(R.string.bookmarks_1_desc),
        topic = "KMP",
        date = stringResource(R.string.bookmarks_1_date),
    ),
    BookmarkedResource(
        id = "2",
        title = stringResource(R.string.bookmarks_2_title),
        description = stringResource(R.string.bookmarks_2_desc),
        topic = stringResource(R.string.bookmarks_2_topic),
        date = stringResource(R.string.bookmarks_2_date),
    ),
    BookmarkedResource(
        id = "3",
        title = stringResource(R.string.bookmarks_3_title),
        description = stringResource(R.string.bookmarks_3_desc),
        topic = stringResource(R.string.bookmarks_3_topic),
        date = stringResource(R.string.bookmarks_3_date),
    ),
)

@Composable
fun BookmarksScreen(
    resources: List<BookmarkedResource> = localizedSavedResources(),
    modifier: Modifier = Modifier,
) {
    val insets = LocalStoreSafeArea.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NiaTopAppBar(
                title = stringResource(R.string.bookmarks_title),
                modifier = Modifier.storeSafeAreaPadding(top = true, bottom = false),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp + insets.bottom,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.bookmarks_reading_list, resources.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                items(resources) { item ->
                    BookmarkedCard(item = item)
                }
            }
        }
    }
}

@Composable
fun BookmarkedCard(
    item: BookmarkedResource,
    modifier: Modifier = Modifier,
) {
    NiaCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.topic.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Remove Bookmark",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
