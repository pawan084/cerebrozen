package com.cerebrozen.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun CereBroTheme(content: @Composable () -> Unit) {
    // Built per composition (not a top-level val) so the scheme re-resolves the
    // themed tokens when AppTheme flips between Night and Dawn.
    //
    // `primary` is the BRAND plum (a fill — Material paints it behind
    // `onPrimary`, and under sliders/switches/indicators). Text that needs to
    // *read* as the accent uses the themed [Periwinkle] token instead, which
    // carries the contrast-safe variant per theme.
    //
    // `onPrimary` is the themed [OnPrimary] role, not a hardcoded white: on
    // Night the accent is a pale plum and its ink is dark (`--on-accent`), so a
    // white label there would be all but invisible.
    val scheme = if (AppTheme.isNight) {
        darkColorScheme(
            primary = BrandPrimary,
            onPrimary = Cream,
            primaryContainer = LavenderPillFloor,
            onPrimaryContainer = Cream,
            secondary = BrandSecondary,
            onSecondary = Cream,
            tertiary = BrandAccent,
            onTertiary = Cream,
            background = Night,
            onBackground = TextPrimary,
            surface = CardFill,
            onSurface = TextPrimary,
            surfaceVariant = ChipFill,
            onSurfaceVariant = TextMuted,
            error = Danger,
            onError = OnDanger,
            outline = LineStroke,
            outlineVariant = LineStroke,
        )
    } else {
        lightColorScheme(
            primary = BrandPrimary,
            onPrimary = OnPrimary,
            primaryContainer = LavenderPillFloor,
            onPrimaryContainer = OnPrimary,
            secondary = BrandSecondary,
            onSecondary = Cream,
            tertiary = BrandAccent,
            onTertiary = Cream,
            background = Night,
            onBackground = TextPrimary,
            surface = CardFill,
            onSurface = TextPrimary,
            surfaceVariant = ChipFill,
            onSurfaceVariant = TextMuted,
            error = Danger,
            onError = OnDanger,
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
