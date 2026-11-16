package org.jetbrains.kotlinconf.promo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ribod.storestudio.annotations.StoreFeatureGraphic
import org.jetbrains.kotlinconf.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.theme.KotlinOrange
import org.jetbrains.kotlinconf.theme.KotlinPink
import org.jetbrains.kotlinconf.theme.KotlinPurple
import org.jetbrains.kotlinconf.theme.KotlinTextPrimary
import org.jetbrains.kotlinconf.theme.KotlinTextSecondary

@StoreFeatureGraphic(
    id = "kotlinconf_feature_graphic",
    name = "KotlinConf Google Play Feature Graphic",
)
@Composable
fun KotlinConfGooglePlayFeatureGraphic() {
    KotlinConfTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF141416),
                            Color(0xFF221738),
                            Color(0xFF181024),
                        ),
                    ),
                )
                .padding(horizontal = 36.dp, vertical = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .height(18.dp)
                                .width(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(KotlinPurple, KotlinOrange),
                                    ),
                                ),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "COPENHAGEN • BELLA CENTER",
                            color = KotlinOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "KotlinConf '26",
                        color = KotlinTextPrimary,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "The Global Conference for Kotlin Developers",
                        color = KotlinTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillBadge(label = "3 Tracks")
                        PillBadge(label = "45+ Talks")
                        PillBadge(label = "May 22-23")
                    }
                }

                Spacer(modifier = Modifier.width(24.dp))

                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    KotlinPurple,
                                    KotlinPink,
                                    KotlinOrange,
                                ),
                            ),
                        )
                        .border(
                            width = 2.dp,
                            color = Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(24.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "K",
                        color = Color.White,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PillBadge(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = KotlinTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
