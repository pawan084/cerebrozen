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
            // Per-theme, not the constant BrandPrimary. `primary` is the ink
            // Material hands to every unstyled TextButton, and BrandPrimary is
            // a DARK plum (#8A4A78) — on Night's page it measured 2.96:1, on a
            // Night card 2.68:1, against a 4.5:1 floor. Twenty-three
            // TextButtons carried no explicit colour and inherited exactly
            // that, which is what the device walk kept flagging as
            // "Next" / "Previous" / "Pause" at 2.6-2.8:1 on the offline
            // guidance screens. Periwinkle is the same role resolved per
            // theme: 9.70:1 here, 9.91:1 in Dawn.
            //
            // `onPrimary` moves with it, and has to: this fill is now a PALE
            // plum in Night, so the Cream ink that suited the dark one would
            // be all but invisible on it — which is precisely what the comment
            // above this block already warned about.
            primary = Periwinkle,
            onPrimary = OnPrimary,
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
            // Same role, same reasoning as the dark scheme above. In Dawn
            // Periwinkle resolves to the dark plum BrandPrimary approximated,
            // so filled components look as they did and TextButton ink gains
            // contrast (5.75:1 -> 9.91:1).
            primary = Periwinkle,
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
