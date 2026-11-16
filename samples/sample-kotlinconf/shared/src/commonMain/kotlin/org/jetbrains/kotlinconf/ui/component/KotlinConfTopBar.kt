package org.jetbrains.kotlinconf.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ribod.storestudio.runtime.mockup.storeSafeAreaPadding
import org.jetbrains.kotlinconf.theme.KotlinDarkBackground
import org.jetbrains.kotlinconf.theme.KotlinOrange
import org.jetbrains.kotlinconf.theme.KotlinPurple
import org.jetbrains.kotlinconf.theme.KotlinTextPrimary
import org.jetbrains.kotlinconf.theme.KotlinTextSecondary

@Composable
fun KotlinConfTopBar(
    title: String = "KotlinConf '26",
    subtitle: String = "Copenhagen • Bella Center",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(KotlinDarkBackground)
            .storeSafeAreaPadding(top = true, bottom = false)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .width(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(KotlinPurple),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = KotlinTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = KotlinTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(KotlinOrange.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = "LIVE",
                color = KotlinOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
