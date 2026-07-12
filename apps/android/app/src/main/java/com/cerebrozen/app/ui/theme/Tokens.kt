package com.cerebrozen.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Corner-radius ladder — mirrors the iOS Radius scale so shapes stay consistent
 * instead of each screen hardcoding its own RoundedCornerShape values. */
object Radius {
    val chip = 13.dp
    val field = 14.dp
    val card = 18.dp
    val hero = 22.dp
    val pill = 26.dp
    val round = 50.dp
}

/** Per-section accent hue — orients the aurora backdrop tint and the title glow by
 * context (mirrors the iOS Theme.Accent). Getters so the themed accents resolve
 * per theme instead of being captured at class-load time. */
object Accent {
    val home: Color get() = Periwinkle
    val sleep: Color get() = Violet
    val talk: Color get() = Cyan
    val journal: Color get() = Periwinkle
    val breathe: Color get() = Teal
    val crisis: Color get() = Warm
    val default: Color get() = Periwinkle
}

/** Centralised stroke treatments (mirrors the iOS Theme.Stroke). Brushes are
 * `get()` properties so they re-resolve when the theme flips (a load-time `val`
 * would freeze whichever theme was active first). */
object Stroke {
    /** Top-lit bevel edge — bright at the top, fading down, like the glass cards.
     * Dawn: a white highlight settling into the light hairline. */
    val bevel: Brush
        get() = if (AppTheme.isNight) {
            Brush.verticalGradient(listOf(Color(0xFF625B86), Color(0xFF464064)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFC9C6E4)))
        }

    /** Flat hairline (the --line token). */
    val hairline: Color get() = LineStroke

    /** Bottom-nav pill border — a light catch on Night, a soft ink hairline on Dawn. */
    val navPill: Brush
        get() = if (AppTheme.isNight) {
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.08f)))
        } else {
            Brush.verticalGradient(listOf(Ink.copy(alpha = 0.14f), Ink.copy(alpha = 0.05f)))
        }
}

/** Common gradient fills — `get()` properties for the same per-theme reason. */
object Gradients {
    /** Primary CTA sweep. Night: the near-white pill (paired with [Ink] text via
     * [OnPrimary]). Dawn: a white pill disappears into the cream ground, so the
     * one action that matters becomes a deep-periwinkle pill with white text
     * (OnPrimary white on 0xFF5545AD = 7.39:1 — see ContrastTest). */
    val primary: Brush
        get() = if (AppTheme.isNight) {
            Brush.horizontalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF7F5FC)))
        } else {
            Brush.horizontalGradient(listOf(Color(0xFF5545AD), Color(0xFF4A3B9C)))
        }

    /** Card/glass surface fill — a solid top-lit indigo pane on Night (the
     * reference-gradient pass made these opaque; see the note in Color.kt), a
     * raised white paper pane on Dawn. */
    val glass: Brush
        get() = if (AppTheme.isNight) {
            Brush.verticalGradient(listOf(Color(0xFF39355F), Color(0xFF2D294F)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF3F2FB)))
        }

    /** The page backdrop base — deep night, or a pale dawn sky. */
    val night: Brush
        get() = if (AppTheme.isNight) {
            Brush.verticalGradient(listOf(NightMid, Night))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFF7F8FE), Color(0xFFECEEFB)))
        }
}
