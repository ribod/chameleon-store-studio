package org.jetbrains.kotlinconf.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.kotlinconf.model.Speaker
import org.jetbrains.kotlinconf.theme.KotlinCardBorder
import org.jetbrains.kotlinconf.theme.KotlinDarkSurface
import org.jetbrains.kotlinconf.theme.KotlinPurple
import org.jetbrains.kotlinconf.theme.KotlinTextPrimary
import org.jetbrains.kotlinconf.theme.KotlinTextSecondary

@Composable
fun SpeakerCard(
    speaker: Speaker,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KotlinDarkSurface)
            .border(1.dp, KotlinCardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(KotlinPurple),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = speaker.initials,
                    color = KotlinTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = speaker.name,
                    color = KotlinTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${speaker.role} • ${speaker.company}",
                    color = KotlinPurple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = speaker.bio,
                    color = KotlinTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}
