package com.cerebrozen.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Corner-radius ladder — mirrors the iOS Radius scale so shapes stay consistent
 * instead of each screen hardcoding its own RoundedCornerShape values. */
object Radius {
    val chip = 13.dp
    val field = 14.dp
    val card = 20.dp
    val hero = 22.dp
    val pill = 26.dp
    val round = 50.dp
}

/** Per-section accent hue — orients the aurora backdrop tint and the title glow by
 * context (mirrors the iOS Theme.Accent). */
object Accent {
    val home = Periwinkle
    val sleep = Violet
    val talk = Cyan
    val journal = Periwinkle
    val breathe = Teal
    val crisis = Warm
    val default = Periwinkle
}

/** Centralised stroke treatments (mirrors the iOS Theme.Stroke). */
object Stroke {
    /** Top-lit bevel edge — bright at the top, fading down, like the glass cards. */
    val bevel = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.06f)))
    /** Flat hairline (the --line token). */
    val hairline = LineStroke
}

/** Common gradient fills (mirrors the iOS Theme.Gradient). */
object Gradients {
    /** Primary CTA sweep. */
    val primary = Brush.horizontalGradient(listOf(Periwinkle, Iris))
    /** Frosted-glass fill — a top-lit translucent pane. */
    val glass = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.045f)))
    /** The night backdrop base. */
    val night = Brush.verticalGradient(listOf(NightMid, Night))
}
