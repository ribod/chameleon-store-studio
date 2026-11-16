package org.jetbrains.kotlinconf.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = KotlinPurple,
    secondary = KotlinOrange,
    tertiary = KotlinPink,
    background = KotlinDarkBackground,
    surface = KotlinDarkSurface,
    onPrimary = KotlinTextPrimary,
    onSecondary = KotlinTextPrimary,
    onBackground = KotlinTextPrimary,
    onSurface = KotlinTextPrimary,
)

private val LightColorScheme = lightColorScheme(
    primary = KotlinPurple,
    secondary = KotlinOrange,
    tertiary = KotlinPink,
    background = KotlinLightBackground,
    surface = KotlinLightSurface,
    onPrimary = KotlinTextPrimary,
    onSecondary = KotlinTextPrimary,
    onBackground = KotlinDarkBackground,
    onSurface = KotlinDarkBackground,
)

@Composable
fun KotlinConfTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
