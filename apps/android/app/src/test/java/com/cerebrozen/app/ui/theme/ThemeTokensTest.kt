package com.cerebrozen.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dusk & Dawn theme plumbing beyond the ContrastTest ratio gate: the
 * mode/forceNight/systemDark resolution matrix, the pref-string round trip,
 * the per-theme getter tokens (Accent/Stroke/Gradients must RE-resolve when
 * the theme flips — a load-time capture is the documented bug), the brand
 * Typography, and CereBroTheme composing a real MaterialTheme in both themes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeTokensTest {

    @get:Rule val compose = createComposeRule()

    @After
    fun restore() {
        AppTheme.mode = ThemeMode.System
        AppTheme.systemDark = true
        AppTheme.forceNight = false
    }

    @Test
    fun isNight_resolves_the_full_mode_matrix() {
        AppTheme.forceNight = false
        AppTheme.mode = ThemeMode.System
        AppTheme.systemDark = true
        assertTrue(AppTheme.isNight)
        AppTheme.systemDark = false
        assertFalse("System follows the OS setting", AppTheme.isNight)

        AppTheme.mode = ThemeMode.Night
        assertTrue("explicit Night wins over a light system", AppTheme.isNight)
        AppTheme.mode = ThemeMode.Dawn
        AppTheme.systemDark = true
        assertFalse("explicit Dawn wins over a dark system", AppTheme.isNight)

        AppTheme.forceNight = true
        assertTrue("forceNight (splash/auth/sleep) beats every preference", AppTheme.isNight)
    }

    @Test
    fun theme_mode_pref_strings_round_trip() {
        assertEquals(ThemeMode.Night, themeModeFromPref("night"))
        assertEquals(ThemeMode.Dawn, themeModeFromPref("dawn"))
        assertEquals(ThemeMode.System, themeModeFromPref("system"))
        assertEquals("unknown values fall back to System", ThemeMode.System, themeModeFromPref("plaid"))
        assertEquals("absent pref falls back to System", ThemeMode.System, themeModeFromPref(null))
        ThemeMode.entries.forEach { mode ->
            assertEquals("every mode must survive a save/load cycle", mode, themeModeFromPref(mode.prefValue()))
        }
    }

    @Test
    fun accent_stroke_and_gradient_tokens_re_resolve_per_theme() {
        fun snapshot(): List<Any> = listOf(
            Accent.home, Accent.sleep, Accent.talk, Accent.journal,
            Accent.breathe, Accent.crisis, Accent.default,
            Stroke.bevel, Stroke.hairline, Stroke.navPill,
            Gradients.primary, Gradients.glass, Gradients.night,
        )
        AppTheme.mode = ThemeMode.Night
        val night = snapshot()
        AppTheme.mode = ThemeMode.Dawn
        val dawn = snapshot()
        // The themed accents must actually change (a load-time `val` would freeze
        // whichever theme initialized first — the documented failure mode).
        // Cyan/Warm/LineStroke are palette-split tokens; Violet (sleep) is not.
        assertNotEquals("talk accent (Cyan) must re-resolve", night[2], dawn[2])
        assertNotEquals("crisis accent (Warm) must re-resolve", night[5], dawn[5])
        assertNotEquals("hairline must re-resolve", night[8], dawn[8])
        assertEquals("section accents stay in one family per theme", dawn[0], dawn[3])
        night.forEach(::assertNotNull)
        dawn.forEach(::assertNotNull)
    }

    @Test
    fun typography_ships_one_rounded_family_across_the_scale() {
        // The Serene pass replaced the display-serif + system-sans pairing with a
        // single rounded family (Nunito), so headings and body share letterform
        // DNA and vertical rhythm. Every role must resolve to that one family.
        assertNotNull(Typography.displayLarge.fontFamily)
        listOf(
            Typography.displayMedium, Typography.displaySmall,
            Typography.headlineMedium, Typography.headlineSmall,
            Typography.titleLarge, Typography.titleMedium, Typography.titleSmall,
            Typography.bodyLarge, Typography.bodyMedium, Typography.bodySmall,
            Typography.labelLarge, Typography.labelMedium, Typography.labelSmall,
        ).forEach { style ->
            assertEquals(
                "every type role shares the one rounded family",
                Typography.displayLarge.fontFamily, style.fontFamily,
            )
        }
        assertTrue("display must be larger than headline",
            Typography.displaySmall.fontSize.value > Typography.headlineSmall.fontSize.value)
        assertTrue("headline must be larger than body",
            Typography.headlineSmall.fontSize.value > Typography.bodyMedium.fontSize.value)
    }

    @Test
    fun cereBroTheme_composes_a_material_theme_in_night() {
        AppTheme.mode = ThemeMode.Night
        var background = Color.Unspecified
        compose.setContent {
            CereBroTheme {
                background = MaterialTheme.colorScheme.background
                Text("night", Modifier.testTag("probe"))
            }
        }
        compose.onNodeWithTag("probe").assertTextEquals("night")
        assertEquals("Night background is the deep indigo ground", NightPalette.night, background)
    }

    @Test
    fun cereBroTheme_composes_a_material_theme_in_dawn() {
        AppTheme.mode = ThemeMode.Dawn
        var background = Color.Unspecified
        var onPrimary = Color.Unspecified
        compose.setContent {
            CereBroTheme {
                background = MaterialTheme.colorScheme.background
                onPrimary = MaterialTheme.colorScheme.onPrimary
                Text("dawn", Modifier.testTag("probe"))
            }
        }
        compose.onNodeWithTag("probe").assertTextEquals("dawn")
        assertEquals("Dawn background is the cream ground", DawnPalette.night, background)
        assertEquals(DawnPalette.onPrimary, onPrimary)
    }
}
