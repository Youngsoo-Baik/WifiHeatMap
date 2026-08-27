package com.example.wifiheatmap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WifiHeatmapColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    secondary = Color(0xFF0E7490),
    tertiary = Color(0xFF7C3AED),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    error = Color(0xFFB91C1C),
)

@Composable
fun WifiHeatmapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WifiHeatmapColors,
        content = content,
    )
}
