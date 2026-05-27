package com.survival.mirror.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4A9FFF),
    secondary = Color(0xFF0F3460),
    tertiary = Color(0xFF1A1A2E),
    background = Color(0xFF1A1A2E),
    surface = Color(0xFF0F3460),
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun MiroirTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = MiroirTypography,
        content = content
    )
}
