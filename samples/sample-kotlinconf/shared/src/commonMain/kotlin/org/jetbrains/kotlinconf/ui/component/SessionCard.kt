package org.jetbrains.kotlinconf.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.kotlinconf.model.Session
import org.jetbrains.kotlinconf.theme.KotlinBlue
import org.jetbrains.kotlinconf.theme.KotlinCardBorder
import org.jetbrains.kotlinconf.theme.KotlinChipBackground
import org.jetbrains.kotlinconf.theme.KotlinDarkSurface
import org.jetbrains.kotlinconf.theme.KotlinOrange
import org.jetbrains.kotlinconf.theme.KotlinPurple
import org.jetbrains.kotlinconf.theme.KotlinTextPrimary
import org.jetbrains.kotlinconf.theme.KotlinTextSecondary
import org.jetbrains.kotlinconf.theme.KotlinTextTertiary

@Composable
fun SessionCard(
    session: Session,
    modifier: Modifier = Modifier,
) {
    val trackColor = when (session.track) {
        "Keynote" -> KotlinPurple
        "Multiplatform" -> KotlinOrange
        "Language" -> KotlinBlue
        else -> KotlinTextSecondary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KotlinDarkSurface)
            .border(1.dp, KotlinCardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(trackColor),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = session.time,
                        color = KotlinTextTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        color = KotlinTextTertiary,
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = session.room,
                        color = KotlinTextTertiary,
                        fontSize = 12.sp,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(KotlinChipBackground)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = session.track,
                        color = trackColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = session.title,
                color = KotlinTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 22.sp,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(trackColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = session.speaker.initials,
                        color = trackColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${session.speaker.name} (${session.speaker.company})",
                    color = KotlinTextSecondary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
