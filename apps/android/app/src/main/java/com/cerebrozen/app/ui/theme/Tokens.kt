package com.cerebrozen.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Corner-radius ladder. Softened across the board in the Serene pass — rounder
 * shapes read calmer, and the premium wellness reference set (Calm, Finch,
 * Balance) all sit well above Material's default 12dp card. */
object Radius {
    val chip = 16.dp
    val field = 16.dp
    val card = 22.dp
    val hero = 26.dp
    val pill = 28.dp
    val round = 50.dp
}

/** Elevation ladder — soft, wide, low-opacity shadows (a calm lift, never a hard
 * drop). Wellness UI wants light that diffuses, not edges that cut. */
object Elevation {
    val card = 10.dp
    val focus = 16.dp
    val hero = 20.dp
    val nav = 22.dp
}

/** Consistent vertical rhythm. Three tiers only, so proximity actually groups:
 * items inside a group hug, sections breathe. */
object Space {
    val tight = 6.dp     // label → value
    val item = 12.dp     // between items in a group
    val group = 16.dp    // between groups in a section
    val section = 28.dp  // between sections
}

/**
 * Per-section accent — orients icons, labels and the title glow by context.
 * These are the **text-safe** accents (see Color.kt): `Accent` values are used as
 * label colour (e.g. the InfoBanner action), so they must clear 4.5:1 in both
 * themes. For a decorative backdrop hue use [AuroraTint] instead.
 */
object Accent {
    val home: Color get() = Periwinkle
    val sleep: Color get() = Periwinkle
    val talk: Color get() = Periwinkle  // Coach wears the brand coral
    val journal: Color get() = Periwinkle
    val breathe: Color get() = Ok
    val crisis: Color get() = Warm
    val default: Color get() = Periwinkle
}

/** Decorative backdrop hues for the aurora — the brand fills, verbatim. Never
 * text, so they don't need the text-safe treatment. */
object AuroraTint {
    val home = BrandPrimary
    val sleep = Violet
    val talk = BrandSecondary
    val default = BrandPrimary
}

/** Centralised stroke treatments. Brushes are `get()` properties so they
 * re-resolve when the theme flips. */
object Stroke {
    /** Top-lit bevel edge — bright at the top, fading down, like light on glass. */
    val bevel: Brush
        get() = if (AppTheme.isNight) {
            Brush.verticalGradient(listOf(Color(0xFF46536E), Color(0xFF2B364C)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE4E6EF)))
        }

    /** Flat hairline (the --line token). */
    val hairline: Color get() = LineStroke

    /** Bottom-nav pill border. */
    val navPill: Brush
        get() = if (AppTheme.isNight) {
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f)))
        } else {
            Brush.verticalGradient(listOf(Ink.copy(alpha = 0.10f), Ink.copy(alpha = 0.04f)))
        }
}

/** Common gradient fills — `get()` properties for the same per-theme reason. */
object Gradients {
    /**
     * Primary CTA. One deep-lavender pill with a white label in BOTH themes:
     * brand Lavender #7C6FF0 only reaches 3.90:1 under white text, so the pill
     * deepens to #6D5FE8 → #5B4BC4 (4.72:1 / 6.46:1). Unifying the CTA across
     * themes also means the one action that matters looks the same everywhere.
     */
    val primary: Brush
        get() = Brush.horizontalGradient(listOf(LavenderPillTop, LavenderPillFloor))

    /** Card surface — a top-lit pane. Navy on dark, white paper on light. */
    val glass: Brush
        get() = if (AppTheme.isNight) {
            Brush.verticalGradient(listOf(Color(0xFF26324A), Color(0xFF1B2438)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF7F8FC)))
        }

    /** The page backdrop base. */
    val night: Brush
        get() = if (AppTheme.isNight) {
            Brush.verticalGradient(listOf(NightMid, Night))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFAFAFC)))
        }

    /** The brand sweep — lavender → sky → mint. Decorative only (progress fills,
     * orb rims, chart strokes, celebration art). Never sits under text. */
    val brand: Brush
        get() = Brush.horizontalGradient(listOf(BrandPrimary, BrandSecondary, BrandAccent))

    /** Calm two-stop lavender→sky, for progress bars and rings. */
    val calm: Brush
        get() = Brush.horizontalGradient(listOf(BrandPrimary, BrandSecondary))
}
