package com.cerebrozen.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The user's appearance choice (You → Appearance), persisted as `theme_mode`. */
enum class ThemeMode { System, Night, Dawn }

/**
 * The Dusk & Dawn theme switch (REDESIGN.md §4.1). Screens never read this
 * directly — they keep importing the top-level tokens in Color.kt/Tokens.kt,
 * whose getters resolve against [isNight] on every composition. Because the
 * three inputs are snapshot state, any composable that reads a token
 * recomposes automatically when the theme flips.
 */
object AppTheme {
    /** User preference; initialised from `Session.prefGet("theme_mode")` in CereBroApp. */
    var mode by mutableStateOf(ThemeMode.System)

    /** Fed by `isSystemInDarkTheme()` at the top of CereBroApp. */
    var systemDark by mutableStateOf(true)

    /** Contexts that are always Night regardless of preference: the splash,
     * the signed-out onboarding/auth funnel (bespoke night art), and the
     * Sleep tab (sleep contexts always Night — REDESIGN §4.1). */
    var forceNight by mutableStateOf(false)

    val isNight: Boolean
        get() = forceNight || when (mode) {
            ThemeMode.System -> systemDark
            ThemeMode.Night -> true
            ThemeMode.Dawn -> false
        }
}

/** `theme_mode` preference string → [ThemeMode] (unknown/absent → System). Pure, testable. */
fun themeModeFromPref(raw: String?): ThemeMode = when (raw) {
    "night" -> ThemeMode.Night
    "dawn" -> ThemeMode.Dawn
    else -> ThemeMode.System
}

/** [ThemeMode] → its `theme_mode` preference string. Pure, testable. */
fun ThemeMode.prefValue(): String = when (this) {
    ThemeMode.System -> "system"
    ThemeMode.Night -> "night"
    ThemeMode.Dawn -> "dawn"
}
