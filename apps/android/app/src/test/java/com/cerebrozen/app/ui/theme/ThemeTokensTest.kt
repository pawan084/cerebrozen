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
        AppTheme.hour = 12
    }

    @Test
    fun theWindowBackgroundResourceMatchesTheNightFloor() {
        // res/values/colors.xml paints the window and, on Android 12+, the
        // platform splash the launcher icon sits on. NightPalette.night is what
        // Compose paints one frame later. When these disagree, every cold launch
        // steps from one dark indigo to a different one — which is exactly what
        // shipped: the resource still held the iOS #080B22 while the Android
        // palette had moved to #100D2B.
        val resource = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
            .getColor(com.cerebrozen.app.R.color.night)
        assertEquals(
            "@color/night must equal NightPalette.night",
            NightPalette.night,
            Color(resource),
        )
    }

    @Test
    fun appearanceChoiceIsConsistentAtEveryHour() {
        // REDESIGN §4.1 says "Sleep tab AND wind-down hours always Night". Only
        // the first half was built. Found on a device at 23:43: Home was showing
        // a banner reading "The day is winding down" in full-brightness Dawn.
        assertTrue("21:00 is wind-down", isWindDownHour(21))
        assertTrue("and so is 03:00", isWindDownHour(3))
        assertFalse("but 20:00 is not", isWindDownHour(20))
        assertFalse("nor is 09:00", isWindDownHour(9))

        // System follows the OS setting at every hour.
        assertFalse("light OS stays light at 23:00", nightFor(ThemeMode.System, systemDark = false, hour = 23))
        assertFalse("light OS at midday stays Dawn", nightFor(ThemeMode.System, systemDark = false, hour = 12))
        assertTrue("dark OS at midday is still Night", nightFor(ThemeMode.System, systemDark = true, hour = 12))

        // An explicit choice is a choice. The clock does not get a vote.
        assertFalse("explicit Dawn survives 23:00", nightFor(ThemeMode.Dawn, systemDark = false, hour = 23))
        assertFalse("explicit Dawn survives 03:00", nightFor(ThemeMode.Dawn, systemDark = true, hour = 3))
        assertTrue("explicit Night survives midday", nightFor(ThemeMode.Night, systemDark = false, hour = 12))
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
    fun typography_ships_the_brand_serif_headings() {
        assertNotNull(Typography.displaySmall.fontFamily)
        assertEquals(Typography.displaySmall.fontFamily, Typography.headlineSmall.fontFamily)
        assertTrue("display must be larger than headline",
            Typography.displaySmall.fontSize.value > Typography.headlineSmall.fontSize.value)
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
