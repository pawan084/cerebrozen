package com.cerebrozen.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun CereBroTheme(content: @Composable () -> Unit) {
    // Built per composition (not a top-level val) so the scheme re-resolves the
    // themed tokens when AppTheme flips between Night and Dawn.
    //
    // `primary` is the BRAND lavender (a fill — Material paints it behind
    // `onPrimary` white, and under sliders/switches/indicators). Text that needs
    // to *read* as the accent uses the themed [Periwinkle] token instead, which
    // carries the contrast-safe variant per theme.
    val scheme = if (AppTheme.isNight) {
        darkColorScheme(
            primary = BrandPrimary,
            onPrimary = Color.White,
            primaryContainer = LavenderPillFloor,
            onPrimaryContainer = Color.White,
            secondary = BrandSecondary,
            onSecondary = Ink,
            tertiary = BrandAccent,
            onTertiary = Ink,
            background = Night,
            onBackground = TextPrimary,
            surface = CardFill,
            onSurface = TextPrimary,
            surfaceVariant = ChipFill,
            onSurfaceVariant = TextMuted,
            error = Danger,
            onError = Night,
            outline = LineStroke,
            outlineVariant = LineStroke,
        )
    } else {
        lightColorScheme(
            primary = BrandPrimary,
            onPrimary = Color.White,
            primaryContainer = LavenderPillFloor,
            onPrimaryContainer = Color.White,
            secondary = BrandSecondary,
            onSecondary = Ink,
            tertiary = BrandAccent,
            onTertiary = Ink,
            background = Night,
            onBackground = TextPrimary,
            surface = CardFill,
            onSurface = TextPrimary,
            surfaceVariant = ChipFill,
            onSurfaceVariant = TextMuted,
            error = Danger,
            onError = Night,
            outline = LineStroke,
            outlineVariant = LineStroke,
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content,
    )
}
