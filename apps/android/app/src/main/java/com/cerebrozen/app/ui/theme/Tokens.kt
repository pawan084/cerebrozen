package com.cerebrozen.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
    val talk: Color get() = Cyan
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
    /** Top-lit bevel edge — bright at the top, fading down, like light on glass.
     * Plum-toned in both arms since the Light Dawn port; the stops straddle the
     * theme's `--line` so the edge reads as lit, not as a second outline. */
    val bevel: Brush
        get() = if (AppTheme.isNight) {
            Brush.verticalGradient(listOf(Color(0xFF4C3B52), Color(0xFF2E2033)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE7E0E6)))
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
/**
 * Card elevation. Theme-aware, because a shadow does different work in each.
 *
 * On Night a card separates by fill — a lighter indigo pane on a near-black
 * ground — and the shadow is barely a hint. On Dawn the fill cannot separate at
 * all: paper on paper is about 1.1:1 whatever the values, so the shadow IS the
 * depth. It was tuned once, for dark, and Dawn inherited a shadow far too shy to
 * lift a near-white card off a near-white page — which is most of why the light
 * theme looked flat beside the dark one.
 *
 * Warm-toned on Dawn (an ink-brown rather than pure black) so the shade reads as
 * paper light rather than a grey smudge.
 */
object CardShadow {
    val elevation: Dp get() = if (AppTheme.isNight) 8.dp else 16.dp
    // Plum-toned on Dawn (an ink-plum rather than pure black), matching
    // tokens.css `--shadow: rgba(90, 43, 92, …)`.
    val ambient: Color get() = if (AppTheme.isNight) Color(0x26000000) else Color(0x1C5A2B5C)
    val spot: Color get() = if (AppTheme.isNight) Color(0x30000000) else Color(0x38351933)

    /** The floating nav pill sits higher than a card and carries a deeper drop.
     * It used one hardcoded 40% black in both themes; under an ivory capsule
     * that reads as a grey smudge, so Dawn gets the same depth in plum. */
    val navAmbient: Color get() = if (AppTheme.isNight) Color(0x66000000) else Color(0x2E5A2B5C)
    val navSpot: Color get() = if (AppTheme.isNight) Color(0x66000000) else Color(0x38351933)
}

object Gradients {
    /**
     * Primary CTA — the **accent fill** (tokens.css
     * `--btn-primary-bg: linear-gradient(accent, accent-2)`), themed.
     *
     * It has to flip: on Dawn the accent is a deep plum carrying near-white ink
     * (10.61:1 / 6.16:1), on Night it is a pale plum carrying the dark
     * `--on-accent` (8.77:1 / 5.73:1). A single fixed gradient can only be
     * legible under one of those two inks. The label is always [OnPrimary].
     */
    val primary: Brush
        get() = Brush.horizontalGradient(listOf(Periwinkle, Accent2))

    /** Card surface — a top-lit pane straddling `--surface-raised`. */
    val glass: Brush
        get() = if (AppTheme.isNight) {
            Brush.verticalGradient(listOf(Color(0xFF2B1E2F), Color(0xFF1F1522)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFFFFEFC), Color(0xFFFBF6F7)))
        }

    /** The page backdrop base — `--surface-field` fading into `--surface`. */
    val night: Brush
        get() = Brush.verticalGradient(listOf(NightMid, Night))

    /** The brand sweep — lavender → sky → mint. Decorative only (progress fills,
     * orb rims, chart strokes, celebration art). Never sits under text. */
    val brand: Brush
        get() = Brush.horizontalGradient(listOf(BrandPrimary, BrandSecondary, BrandAccent))

    /** Calm two-stop lavender→sky, for progress bars and rings. */
    val calm: Brush
        get() = Brush.horizontalGradient(listOf(BrandPrimary, BrandSecondary))
}
