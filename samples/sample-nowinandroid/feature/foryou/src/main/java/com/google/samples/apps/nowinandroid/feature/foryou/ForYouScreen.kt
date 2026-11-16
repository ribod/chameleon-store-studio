package com.google.samples.apps.nowinandroid.feature.foryou

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.samples.apps.nowinandroid.core.designsystem.component.NiaCard
import com.google.samples.apps.nowinandroid.core.designsystem.component.NiaTopAppBar
import com.google.samples.apps.nowinandroid.core.designsystem.theme.NiaBrand
import androidx.compose.ui.res.stringResource
import com.google.samples.apps.nowinandroid.core.designsystem.theme.NiaSecondary
import com.google.samples.apps.nowinandroid.core.designsystem.theme.NiaTeal
import dev.ribod.storestudio.runtime.mockup.LocalStoreSafeArea
import dev.ribod.storestudio.runtime.mockup.storeSafeAreaPadding

data class Topic(val id: String, val name: String, val selected: Boolean)
data class NewsResource(
    val id: String,
    val title: String,
    val content: String,
    val topic: String,
    val date: String,
    val bookmarked: Boolean,
)

val sampleTopics = listOf(
    Topic("1", "Compose", true),
    Topic("2", "Architecture", true),
    Topic("3", "Kotlin Multiplatform", false),
    Topic("4", "Performance", false),
    Topic("5", "Android Studio", true),
)

val sampleNews = listOf(
    NewsResource(
        id = "1",
        title = "Now in Android: 110 - Android 16 Developer Preview & Compose 1.8",
        content = "Explore the new photo picker, health records, and predictive back transitions.",
        topic = "Compose",
        date = "October 2026",
        bookmarked = true,
    ),
    NewsResource(
        id = "2",
        title = "Modern Android Architecture: Offline-first with Jetpack Room & Flow",
        content = "Deep dive into real-world caching strategies and reactive synchronization.",
        topic = "Architecture",
        date = "October 2026",
        bookmarked = false,
    ),
    NewsResource(
        id = "3",
        title = "Building High-Performance UIs with Layoutlib & Preview Renderer",
        content = "Automating pixel-perfect Google Play and App Store screenshots at build time.",
        topic = "Performance",
        date = "September 2026",
        bookmarked = true,
    ),
)

/** Topic chips resolved from standard Android resources strings.xml. */
@Composable
fun localizedTopics(): List<Topic> = listOf(
    Topic("1", "Compose", true),
    Topic("2", stringResource(R.string.foryou_topic_architecture), true),
    Topic("3", stringResource(R.string.foryou_topic_kmp), false),
    Topic("4", stringResource(R.string.foryou_topic_performance), false),
    Topic("5", "Android Studio", true),
)

/** News feed resolved from standard Android resources strings.xml. */
@Composable
fun localizedNews(): List<NewsResource> = listOf(
    NewsResource(
        id = "1",
        title = stringResource(R.string.foryou_news_1_title),
        content = stringResource(R.string.foryou_news_1_content),
        topic = "Compose",
        date = stringResource(R.string.foryou_news_1_date),
        bookmarked = true,
    ),
    NewsResource(
        id = "2",
        title = stringResource(R.string.foryou_news_2_title),
        content = stringResource(R.string.foryou_news_2_content),
        topic = stringResource(R.string.foryou_topic_architecture),
        date = stringResource(R.string.foryou_news_2_date),
        bookmarked = false,
    ),
    NewsResource(
        id = "3",
        title = stringResource(R.string.foryou_news_3_title),
        content = stringResource(R.string.foryou_news_3_content),
        topic = stringResource(R.string.foryou_topic_performance),
        date = stringResource(R.string.foryou_news_3_date),
        bookmarked = true,
    ),
)

@Composable
fun ForYouScreen(
    topics: List<Topic> = localizedTopics(),
    newsList: List<NewsResource> = localizedNews(),
    modifier: Modifier = Modifier,
) {
    val insets = LocalStoreSafeArea.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NiaTopAppBar(
                title = "Now in Android",
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
                        text = stringResource(R.string.foryou_interests_prompt),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(topics) { topic ->
                            FilterChip(
                                selected = topic.selected,
                                onClick = {},
                                label = { Text(topic.name) },
                                leadingIcon = if (topic.selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor =
                                        MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor =
                                        MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                }

                items(newsList) { item ->
                    NewsResourceCard(item = item)
                }
            }
        }
    }
}

@Composable
fun NewsResourceCard(
    item: NewsResource,
    modifier: Modifier = Modifier,
) {
    var isBookmarked by remember { mutableStateOf(item.bookmarked) }

    NiaCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (item.id) {
                                    "1" -> NiaBrand
                                    "2" -> NiaSecondary
                                    else -> NiaTeal
                                },
                            ),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.topic.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { isBookmarked = !isBookmarked }) {
                    Icon(
                        imageVector = if (isBookmarked) {
                            Icons.Default.Bookmark
                        } else {
                            Icons.Default.BookmarkBorder
                        },
                        contentDescription = "Bookmark",
                        tint = if (isBookmarked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
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
                text = item.content,
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
