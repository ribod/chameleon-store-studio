package org.jetbrains.kotlinconf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.kotlinconf.model.ConferenceData
import org.jetbrains.kotlinconf.theme.KotlinDarkBackground
import org.jetbrains.kotlinconf.ui.component.KotlinConfTopBar
import org.jetbrains.kotlinconf.ui.component.SpeakerCard

@Composable
fun SpeakersScreen(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KotlinDarkBackground),
    ) {
        KotlinConfTopBar(
            title = "Speakers",
            subtitle = "Industry Experts & Core Contributors",
        )

        Spacer(modifier = Modifier.height(8.dp))

        val insets = dev.ribod.storestudio.runtime.mockup.LocalStoreSafeArea.current

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = 24.dp + insets.bottom,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ConferenceData.speakers) { speaker ->
                SpeakerCard(speaker = speaker)
            }
        }
    }
}
