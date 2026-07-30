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

    /** Local hour, refreshed each minute by CereBroApp. Held here rather than
     * read inline so [isNight] stays a pure function of its inputs. */
    var hour by mutableStateOf(12)

    val isNight: Boolean
        get() = forceNight || nightFor(mode, systemDark, hour)
}

/** The hour the day starts winding down. Shared with Home's wind-down banner so
 * the theme and the copy can never disagree about when evening is. */
const val WIND_DOWN_FROM_HOUR = 21

/** True between [WIND_DOWN_FROM_HOUR] and 06:00 — REDESIGN §4.1's "wind-down
 * hours". Pure. */
fun isWindDownHour(hour: Int): Boolean = hour >= WIND_DOWN_FROM_HOUR || hour < 6

/**
 * Night resolution (REDESIGN §4.1), pure so the matrix is a test.
 *
 * The wind-down clause is the half of §4.1 that was never built: "Sleep tab **and
 * wind-down hours** always Night". Found on a device at 23:43 — Home was showing
 * a banner reading "The day is winding down" in full-brightness Dawn, the exact
 * bright-screen-at-bedtime harm the sleep-context rule exists to prevent, on the
 * screen that names the problem out loud.
 *
 * It applies to [ThemeMode.System] ONLY. An explicit Dawn choice is a choice,
 * and the clock does not get to overrule it; System means "read the room", and
 * at 23:43 the room is dark whatever the OS toggle says.
 */
fun nightFor(mode: ThemeMode, systemDark: Boolean, hour: Int): Boolean = when (mode) {
    ThemeMode.System -> systemDark || isWindDownHour(hour)
    ThemeMode.Night -> true
    ThemeMode.Dawn -> false
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
