package com.google.samples.apps.nowinandroid

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.samples.apps.nowinandroid.core.designsystem.component.NiaNavigationBar
import com.google.samples.apps.nowinandroid.core.designsystem.component.NiaNavigationBarItem
import com.google.samples.apps.nowinandroid.feature.bookmarks.BookmarksScreen
import com.google.samples.apps.nowinandroid.feature.foryou.ForYouScreen
import com.google.samples.apps.nowinandroid.feature.interests.InterestsScreen
import com.google.samples.apps.nowinandroid.navigation.NiaDestination

@Composable
fun NiaApp(modifier: Modifier = Modifier) {
    var currentDestination by remember { mutableStateOf(NiaDestination.FOR_YOU) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NiaNavigationBar {
                NiaNavigationBarItem(
                    selected = currentDestination == NiaDestination.FOR_YOU,
                    onClick = { currentDestination = NiaDestination.FOR_YOU },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "For You",
                        )
                    },
                    label = { Text("For You") },
                )
                NiaNavigationBarItem(
                    selected = currentDestination == NiaDestination.BOOKMARKS,
                    onClick = { currentDestination = NiaDestination.BOOKMARKS },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Bookmarks",
                        )
                    },
                    label = { Text("Saved") },
                )
                NiaNavigationBarItem(
                    selected = currentDestination == NiaDestination.INTERESTS,
                    onClick = { currentDestination = NiaDestination.INTERESTS },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Interests",
                        )
                    },
                    label = { Text("Interests") },
                )
            }
        },
    ) { padding ->
        when (currentDestination) {
            NiaDestination.FOR_YOU -> ForYouScreen(modifier = Modifier.padding(padding))
            NiaDestination.BOOKMARKS -> BookmarksScreen(modifier = Modifier.padding(padding))
            NiaDestination.INTERESTS -> InterestsScreen(modifier = Modifier.padding(padding))
        }
    }
}
