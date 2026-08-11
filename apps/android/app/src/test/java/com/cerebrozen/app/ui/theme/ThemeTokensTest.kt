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
 * Theme plumbing beyond the ContrastTest ratio gate: the
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
        // Light Dawn is the base appearance — restore the real default, not the
        // pre-port night-first one.
        AppTheme.systemDark = false
        AppTheme.forceNight = false
        AppTheme.hour = 12
    }

    @Test
    fun lightDawnIsTheDefaultAppearance() {
        // docs/REDESIGN_V2.md §2: a "Light Dawn visual system" with an "optional
        // dark appearance later". The app was built night-first; the port makes
        // light the base, so an untouched install with no OS signal yet is Dawn.
        assertEquals(ThemeMode.System, AppTheme.mode)
        assertFalse("systemDark starts false — the base appearance is light", AppTheme.systemDark)
        assertFalse("so a fresh AppTheme resolves to Dawn", AppTheme.isNight)
        assertEquals(DawnPalette.night, Night)
    }

    @Test
    @Config(sdk = [34], qualifiers = "notnight")
    fun theWindowBackgroundResourceMatchesTheDefaultGround() {
        // res/values/colors.xml paints the window and, on Android 12+, the
        // platform splash the launcher icon sits on; Compose paints its own
        // ground one frame later. When these disagree, every cold launch steps
        // from one colour to another — which is exactly what shipped once
        // before (the resource held the iOS value while the palette had moved).
        //
        // Light Dawn is the default appearance, so the UNQUALIFIED resource is
        // the Dawn ground. Before the port there was only the dark value, so a
        // light-theme device flashed deep indigo on every launch.
        assertEquals(
            "@color/night must equal DawnPalette.night",
            DawnPalette.night,
            Color(windowBackground()),
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "night")
    fun theWindowBackgroundResourceMatchesTheNightFloor() {
        assertEquals(
            "values-night/@color/night must equal NightPalette.night",
            NightPalette.night,
            Color(windowBackground()),
        )
    }

    private fun windowBackground(): Int = androidx.test.core.app.ApplicationProvider
        .getApplicationContext<android.content.Context>()
        .getColor(com.cerebrozen.app.R.color.night)

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
        assertTrue("the internal forceNight seam beats every preference", AppTheme.isNight)
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
        assertNotEquals("talk accent (Info) must re-resolve", night[2], dawn[2])
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
    // ── Every themed token resolves, in BOTH themes ─────────────────────────
    //
    // Reflection over ColorKt on purpose: the file has ~55 top-level `get()`
    // tokens and a hand-kept list here would silently stop covering the next
    // one added. Each getter must return a real paint (alpha > 0) under Night
    // AND Dawn — an unset default (Color.Unspecified / transparent) is a screen
    // painting nothing, and the Dawn arm of a getter is exactly the branch a
    // Night-only device fleet never executes.
    @Test
    fun everyThemedColorTokenResolvesInBothThemes() {
        val getters = Class.forName("com.cerebrozen.app.ui.theme.ColorKt")
            .methods.filter {
                java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    it.parameterCount == 0 && it.name.startsWith("get")
            }
        assertTrue("reflection found the token getters", getters.size >= 40)
        for (night in listOf(true, false)) {
            AppTheme.mode = if (night) ThemeMode.Night else ThemeMode.Dawn
            AppTheme.forceNight = false
            getters.forEach { getter ->
                val value = getter.invoke(null)
                if (value is Long) { // Compose Color is an inline value class over ULong
                    val color = Color(value.toULong())
                    assertTrue(
                        "${getter.name} resolves to a transparent paint in ${if (night) "Night" else "Dawn"}",
                        color.alpha > 0f,
                    )
                }
            }
        }
        // And the theme actually switches what a role means: the page floor is
        // the palette's own ground on each side, not a shared constant.
        AppTheme.mode = ThemeMode.Night
        assertEquals(NightPalette.night, Night)
        AppTheme.mode = ThemeMode.Dawn
        assertEquals(DawnPalette.night, Night)
    }

    // ── The spacing/elevation scales are actually scales ────────────────────
    // Three tiers that group by proximity only work if the tiers are ordered;
    // a refactor that flattened two of them would pass every screenshotless
    // test while quietly un-grouping every screen.
    @Test
    fun spacingAndElevationTiersStayOrdered() {
        assertTrue(Space.tight < Space.item)
        assertTrue(Space.item < Space.group)
        assertTrue(Space.group < Space.section)
        assertTrue(Elevation.card < Elevation.focus)
        assertTrue(Elevation.focus < Elevation.hero)
        assertTrue(Elevation.hero < Elevation.nav)
    }

    @Test
    fun sectionAccentsAndCtaGradientResolveInBothThemes() {
        listOf(true, false).forEach { night ->
            AppTheme.mode = if (night) ThemeMode.Night else ThemeMode.Dawn
            // The aurora tints are brand marks — fully opaque in both themes.
            listOf(AuroraTint.home, AuroraTint.sleep, AuroraTint.talk, AuroraTint.default)
                .forEach { assertEquals(1f, it.alpha) }
            // The CTA pill is the accent fill, so unlike the aurora it MUST
            // flip: Dawn's deep plum carries near-white ink, Night's pale plum
            // carries dark `--on-accent` ink. One fixed gradient can only be
            // legible under one of the two (ratios pinned in ContrastTest).
            assertNotNull(Gradients.primary)
            assertEquals(if (night) NightPalette.onPrimary else DawnPalette.onPrimary, OnPrimary)
            assertEquals(if (night) NightPalette.periwinkle else DawnPalette.periwinkle, PrimaryButtonFill)
        }
    }
}
