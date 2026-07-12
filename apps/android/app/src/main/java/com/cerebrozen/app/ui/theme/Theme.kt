package com.cerebrozen.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun CereBroTheme(content: @Composable () -> Unit) {
    // Built per composition (not a top-level val) so the scheme re-resolves the
    // themed tokens when AppTheme flips between Night and Dawn.
    val scheme = if (AppTheme.isNight) {
        darkColorScheme(
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
    } else {
        lightColorScheme(
            primary = Periwinkle,
            onPrimary = OnPrimary,
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
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content,
    )
}
