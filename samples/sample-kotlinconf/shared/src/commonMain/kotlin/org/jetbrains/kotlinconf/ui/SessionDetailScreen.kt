package org.jetbrains.kotlinconf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.kotlinconf.model.ConferenceData
import org.jetbrains.kotlinconf.model.Session
import org.jetbrains.kotlinconf.theme.KotlinCardBorder
import org.jetbrains.kotlinconf.theme.KotlinChipBackground
import org.jetbrains.kotlinconf.theme.KotlinDarkBackground
import org.jetbrains.kotlinconf.theme.KotlinDarkSurface
import org.jetbrains.kotlinconf.theme.KotlinOrange
import org.jetbrains.kotlinconf.theme.KotlinPurple
import org.jetbrains.kotlinconf.theme.KotlinTextPrimary
import org.jetbrains.kotlinconf.theme.KotlinTextSecondary
import org.jetbrains.kotlinconf.theme.KotlinTextTertiary
import org.jetbrains.kotlinconf.ui.component.KotlinConfTopBar

@Composable
fun SessionDetailScreen(
    session: Session = ConferenceData.sessions[0],
    modifier: Modifier = Modifier,
) {
    val insets = dev.ribod.storestudio.runtime.mockup.LocalStoreSafeArea.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KotlinDarkBackground),
    ) {
        KotlinConfTopBar(
            title = "Session Overview",
            subtitle = session.track,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 20.dp,
                    bottom = 20.dp + insets.bottom,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(KotlinPurple.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = session.track.uppercase(),
                        color = KotlinPurple,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = "${session.time} • ${session.room}",
                    color = KotlinTextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = session.title,
                color = KotlinTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 28.sp,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KotlinDarkSurface)
                    .border(1.dp, KotlinCardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(KotlinPurple),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = session.speaker.initials,
                            color = KotlinTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = session.speaker.name,
                            color = KotlinTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${session.speaker.role} • ${session.speaker.company}",
                            color = KotlinTextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Abstract",
                color = KotlinTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = session.description,
                color = KotlinTextSecondary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(KotlinPurple)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Add to Calendar",
                        color = KotlinTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(KotlinChipBackground)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Rate Session",
                        color = KotlinOrange,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
