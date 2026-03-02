package com.dailypowders.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PowderBlue,
    onPrimary = SurfaceLight,
    primaryContainer = PowderBlueLight,
    secondary = PowderBlueDark,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight
)

@Composable
fun DailyPowdersTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
