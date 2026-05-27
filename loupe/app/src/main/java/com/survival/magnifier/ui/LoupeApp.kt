package com.survival.magnifier.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val LoupeDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4A9FFF),
    secondary = Color(0xFF0F3460),
    tertiary = Color(0xFF1A1A2E),
    background = Color(0xFF1A1A2E),
    surface = Color(0xFF1A1A2E)
)

@Composable
fun LoupeApp() {
    MaterialTheme(colorScheme = LoupeDarkColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A2E))
        ) {
            MagnifierScreen()
        }
    }
}
