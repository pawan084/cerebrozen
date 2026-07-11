package com.cerebrozen.app.ui.theme

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
