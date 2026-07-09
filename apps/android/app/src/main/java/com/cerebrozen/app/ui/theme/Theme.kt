package com.cerebro.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CereBroColors = darkColorScheme(
    primary = Periwinkle,
    onPrimary = Ink,
    secondary = Violet,
    background = Night,
    onBackground = TextPrimary,
    surface = NightMid,
    onSurface = TextPrimary,
    surfaceVariant = NightPurple,
    onSurfaceVariant = TextMuted,
    error = Danger,
    outline = LineStroke,
)

@Composable
fun CereBroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CereBroColors,
        typography = Typography,
        content = content,
    )
}
