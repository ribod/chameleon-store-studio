package com.google.samples.apps.nowinandroid.feature.interests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.samples.apps.nowinandroid.core.designsystem.component.NiaButton
import com.google.samples.apps.nowinandroid.core.designsystem.component.NiaCard
import com.google.samples.apps.nowinandroid.core.designsystem.component.NiaOutlinedButton
import com.google.samples.apps.nowinandroid.core.designsystem.component.NiaTopAppBar
import dev.ribod.storestudio.runtime.mockup.LocalStoreSafeArea
import dev.ribod.storestudio.runtime.mockup.storeSafeAreaPadding

data class InterestTopic(
    val id: String,
    val name: String,
    val description: String,
    val following: Boolean,
)

val sampleInterestTopics = listOf(
    InterestTopic(
        id = "1",
        name = "Jetpack Compose",
        description = "Modern declarative toolkit for building native Android and " +
            "multiplatform UIs.",
        following = true,
    ),
    InterestTopic(
        id = "2",
        name = "Kotlin Multiplatform",
        description = "Share business logic and Compose UI seamlessly across mobile and desktop.",
        following = true,
    ),
    InterestTopic(
        id = "3",
        name = "Android Performance",
        description = "Profiling, Baseline Profiles, Layoutlib headless rendering and " +
            "jank reduction.",
        following = false,
    ),
    InterestTopic(
        id = "4",
        name = "Architecture & Patterns",
        description = "Unidirectional data flow, reactive repositories, and modular scalability.",
        following = true,
    ),
)

/** Interest topics resolved from standard Android resources strings.xml. */
@Composable
fun localizedInterestTopics(): List<InterestTopic> = listOf(
    InterestTopic(
        id = "1",
        name = "Jetpack Compose",
        description = stringResource(R.string.interests_1_desc),
        following = true,
    ),
    InterestTopic(
        id = "2",
        name = stringResource(R.string.interests_2_name),
        description = stringResource(R.string.interests_2_desc),
        following = true,
    ),
    InterestTopic(
        id = "3",
        name = stringResource(R.string.interests_3_name),
        description = stringResource(R.string.interests_3_desc),
        following = false,
    ),
    InterestTopic(
        id = "4",
        name = stringResource(R.string.interests_4_name),
        description = stringResource(R.string.interests_4_desc),
        following = true,
    ),
)

@Composable
fun InterestsScreen(
    topics: List<InterestTopic> = localizedInterestTopics(),
    modifier: Modifier = Modifier,
) {
    val insets = LocalStoreSafeArea.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NiaTopAppBar(
                title = stringResource(R.string.interests_title),
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
                        text = stringResource(R.string.interests_prompt),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                items(topics) { topic ->
                    InterestTopicItem(topic = topic)
                }
            }
        }
    }
}

@Composable
fun InterestTopicItem(
    topic: InterestTopic,
    modifier: Modifier = Modifier,
) {
    var isFollowing by remember { mutableStateOf(topic.following) }

    NiaCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = topic.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (isFollowing) {
                NiaOutlinedButton(onClick = { isFollowing = false }) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Following",
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.interests_btn_following))
                }
            } else {
                NiaButton(onClick = { isFollowing = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Follow",
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.interests_btn_follow))
                }
            }
        }
    }
}
