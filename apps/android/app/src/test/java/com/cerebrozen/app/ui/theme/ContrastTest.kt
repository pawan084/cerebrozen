package com.cerebrozen.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The token contrast gate (REDESIGN.md §4.2), parameterized over both themes
 * (§4.1 Dusk & Dawn): every text role must reach the WCAG AA 4.5:1 ratio
 * against every surface it legitimately appears on, in Night AND in Dawn.
 * Runs as a plain JVM test over the real Color tokens (which resolve through
 * AppTheme), so a palette tweak that breaks legibility fails the build
 * instead of shipping.
 *
 * Night surfaces under test: [Night] (the page backdrop), [Surface]/[CardFill]
 * (resting card fill) and [SurfaceRaised] (0xFF39355F — also the top stop of
 * `Gradients.glass`, i.e. the lightest paint a glass card actually renders,
 * and the chip fill). Passing on SurfaceRaised implies passing on everything
 * darker in the elevation ladder.
 *
 * Dawn surfaces under test: [Night] (= the cream page ground 0xFFECEEFB),
 * [NightMid] (0xFFDDDBF0 — the darkest paint of the page backdrop gradient,
 * the worst case for Dawn's dark-on-light text), [CardFill] (0xFFF7F8FE) and
 * [SurfaceRaised]/[ChipFill] (0xFFE4E2F4).
 */
class ContrastTest {

    /** WCAG 2.x contrast ratio between two opaque ARGB colors (1.0..21.0). */
    private fun contrast(fgArgb: Int, bgArgb: Int): Double {
        fun luminance(argb: Int): Double {
            fun channel(raw: Int): Double {
                val c = raw / 255.0
                return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            val r = channel((argb shr 16) and 0xFF)
            val g = channel((argb shr 8) and 0xFF)
            val b = channel(argb and 0xFF)
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }
        val l1 = luminance(fgArgb)
        val l2 = luminance(bgArgb)
        val (hi, lo) = if (l1 >= l2) l1 to l2 else l2 to l1
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertContrast(name: String, fg: Color, bg: Color, min: Double = 4.5) {
        val ratio = contrast(fg.toArgb(), bg.toArgb())
        assertTrue(
            "$name: contrast ${"%.2f".format(ratio)}:1 is below the ${min}:1 gate",
            ratio >= min,
        )
    }

    /** Run [block] with the top-level tokens resolved to the given theme,
     * restoring AppTheme afterwards so tests never leak state. */
    private fun inTheme(mode: ThemeMode, block: () -> Unit) {
        val prevMode = AppTheme.mode
        val prevForce = AppTheme.forceNight
        val prevSystem = AppTheme.systemDark
        AppTheme.forceNight = false
        AppTheme.mode = mode
        try {
            block()
        } finally {
            AppTheme.mode = prevMode
            AppTheme.forceNight = prevForce
            AppTheme.systemDark = prevSystem
        }
    }

    private fun night(block: () -> Unit) = inTheme(ThemeMode.Night, block)
    private fun dawn(block: () -> Unit) = inTheme(ThemeMode.Dawn, block)

    // ── Night (the original assertions — ratios unchanged) ──────────────────

    @Test
    fun night_textFaint_meetsAA_onEverySurface() = night {
        // TextMuted2/TextFaint is the faintest legal text — the 2026-07 fix
        // lightened it from 0xFF928CAC precisely to clear these three.
        assertContrast("TextMuted2 on CardFill", TextMuted2, CardFill)          // 5.16:1
        assertContrast("TextMuted2 on Night", TextMuted2, Night)                // 7.48:1
        assertContrast("TextMuted2 on SurfaceRaised", TextMuted2, SurfaceRaised) // 4.51:1
    }

    @Test
    fun night_textMuted_meetsAA() = night {
        assertContrast("TextMuted on CardFill", TextMuted, CardFill) // 6.99:1
        assertContrast("TextMuted on Night", TextMuted, Night)       // 10.14:1
    }

    @Test
    fun night_textSoft_meetsAA() = night {
        assertContrast("TextSoft on CardFill", TextSoft, CardFill) // 9.82:1
        assertContrast("TextSoft on Night", TextSoft, Night)       // 14.25:1
    }

    @Test
    fun night_periwinkleAsText_meetsAA_onEverySurface() = night {
        // The last un-gated debt (TODO 2026-07-12): Periwinkle renders as text
        // (labels like "Try another", "Try together") on cards and glass tops.
        // Brightened 0xFF8B78F2 → 0xFFA89AF6 to clear all three grounds.
        assertContrast("Periwinkle on CardFill", Periwinkle, CardFill)           // 5.33:1
        assertContrast("Periwinkle on Night", Periwinkle, Night)                 // 7.73:1
        assertContrast("Periwinkle on SurfaceRaised", Periwinkle, SurfaceRaised) // 4.66:1
    }

    @Test
    fun night_eyebrow_meetsAA() = night {
        // Small-caps section labels render at label size — held to normal-text 4.5:1.
        assertContrast("EyebrowMuted on Night", EyebrowMuted, Night)       // 7.95:1
        assertContrast("EyebrowMuted on CardFill", EyebrowMuted, CardFill) // 5.48:1
    }

    @Test
    fun night_primaryButtonText_meetsAA() = night {
        // PrimaryButton draws OnPrimary (= Ink on Night) over Gradients.primary
        // (white -> 0xFFF7F5FC); 0xFFF7F5FC is the gradient's darker stop, i.e.
        // the worst case. Keep the hex in sync with Gradients.primary in
        // Tokens.kt (Brush stops aren't readable from a plain JVM test).
        assertContrast("Ink on PrimaryButtonFill", Ink, PrimaryButtonFill)       // 16.36:1
        assertContrast("OnPrimary on primary-gradient floor", OnPrimary, Color(0xFFF7F5FC)) // 15.59:1
    }

    @Test
    fun night_textPrimary_clearsAA_withRoomToSpare() = night {
        // Display text would only need 3.0:1; it clears full AA easily — gate at 4.5.
        assertContrast("TextPrimary on Night", TextPrimary, Night)       // 17.29:1
        assertContrast("TextPrimary on CardFill", TextPrimary, CardFill) // 11.91:1
    }

    @Test
    fun night_dangerButton_meetsAA() = night {
        // DangerButton draws the Night token over the Danger fill.
        assertContrast("Night on Danger", Night, Danger) // 7.42:1
    }

    @Test
    fun night_selectedChip_meetsAA() = night {
        assertContrast("ChipSelectedInk on ChipSelectedFill", ChipSelectedInk, ChipSelectedFill) // 16.36:1
    }

    // ── Night regression: the palette must be byte-identical to pre-Dawn ────

    @Test
    fun nightPalette_isByteIdentical_toPreDawnValues() = night {
        // "Zero visual change in Night mode" is a hard requirement of the Dawn
        // pass: every themed token must resolve to the exact pre-Dawn value.
        val expected = mapOf(
            "Night" to (Night to Color(0xFF100D2B)),
            "NightMid" to (NightMid to Color(0xFF3A3372)),
            "NightPurple" to (NightPurple to Color(0xFF29254D)),
            "TextPrimary" to (TextPrimary to Color(0xFFF5F4FF)),
            "TextSoft" to (TextSoft to Color(0xFFE1DEEE)),
            "TextMuted" to (TextMuted to Color(0xFFC0BBD4)),
            "TextMuted2" to (TextMuted2 to Color(0xFFA5A0BA)),
            "CardFill" to (CardFill to Color(0xFF302C55)),
            "LineStroke" to (LineStroke to Color(0xFF514B76)),
            "EyebrowMuted" to (EyebrowMuted to Color(0xFFAAA3D0)),
            "ButtonDisabled" to (ButtonDisabled to Color(0xFF777486)),
            "FieldFill" to (FieldFill to Color(0xFF302B55)),
            "ChipFill" to (ChipFill to Color(0xFF39355F)),
            "NavPillTop" to (NavPillTop to Color(0xFF413A70)),
            "NavPillBottom" to (NavPillBottom to Color(0xFF28234D)),
            "NavScrim" to (NavScrim to Color(0xFF100D2B)),
            // Deliberate post-Dawn change (2026-07-12): brightened from 0xFF8B78F2
            // to clear the 4.5:1 gate as text on CardFill/raised — see Color.kt.
            "Periwinkle" to (Periwinkle to Color(0xFFA89AF6)),
            "Cyan" to (Cyan to Color(0xFF8FE6EE)),
            "Warm" to (Warm to Color(0xFFF0A48C)),
            "Ok" to (Ok to Color(0xFF7EE0A8)),
            "Danger" to (Danger to Color(0xFFE08A9A)),
        )
        expected.forEach { (name, pair) ->
            assertEquals("$name changed in Night mode", pair.second.toArgb(), pair.first.toArgb())
        }
        // The new component/veil tokens must reproduce the exact literals their
        // call sites used before the Dawn pass (rendered 8-bit ARGB identical).
        assertEquals("OnPrimary", Ink.toArgb(), OnPrimary.toArgb())
        assertEquals("ChipSelectedFill", Color.White.toArgb(), ChipSelectedFill.toArgb())
        assertEquals("ChipSelectedInk", Ink.toArgb(), ChipSelectedInk.toArgb())
        assertEquals("SwitchThumbOn", Ink.toArgb(), SwitchThumbOn.toArgb())
        assertEquals("TextBright", Color.White.toArgb(), TextBright.toArgb())
        assertEquals("NavSelectedHi", Periwinkle.copy(alpha = 0.72f).toArgb(), NavSelectedHi.toArgb())
        assertEquals("NavSelectedLo", Periwinkle.copy(alpha = 0.18f).toArgb(), NavSelectedLo.toArgb())
        assertEquals("Veil", Color.White.copy(alpha = 0.07f).toArgb(), Veil.toArgb())
        assertEquals("VeilSoft", Color.White.copy(alpha = 0.06f).toArgb(), VeilSoft.toArgb())
        assertEquals("VeilWell", Color.White.copy(alpha = 0.10f).toArgb(), VeilWell.toArgb())
        assertEquals("VeilStrong", Color.White.copy(alpha = 0.18f).toArgb(), VeilStrong.toArgb())
        assertEquals("VeilLine", Color.White.copy(alpha = 0.12f).toArgb(), VeilLine.toArgb())
        // Art constants replaced themed tokens at fixed-dark-art sites 1:1.
        assertEquals("ArtScrim", Night.toArgb(), ArtScrim.toArgb())
        assertEquals("ArtTextSoft", TextSoft.toArgb(), ArtTextSoft.toArgb())
    }

    // ── Dawn (REDESIGN §4.1 Phase 2) ─────────────────────────────────────────

    @Test
    fun dawn_textRoles_meetAA_onEverySurface() = dawn {
        // Ratios measured 2026-07 (see the Dawn palette notes in Color.kt).
        assertContrast("TextPrimary on bg", TextPrimary, Night)              // 14.60:1
        assertContrast("TextPrimary on NightMid", TextPrimary, NightMid)     // 12.41:1
        assertContrast("TextPrimary on CardFill", TextPrimary, CardFill)     // 15.90:1
        assertContrast("TextPrimary on SurfaceRaised", TextPrimary, SurfaceRaised) // 13.23:1

        assertContrast("TextSoft on bg", TextSoft, Night)                    // 10.22:1
        assertContrast("TextSoft on NightMid", TextSoft, NightMid)           // 8.69:1
        assertContrast("TextSoft on CardFill", TextSoft, CardFill)           // 11.13:1
        assertContrast("TextSoft on SurfaceRaised", TextSoft, SurfaceRaised) // 9.26:1

        assertContrast("TextMuted on bg", TextMuted, Night)                  // 7.65:1
        assertContrast("TextMuted on NightMid", TextMuted, NightMid)         // 6.51:1
        assertContrast("TextMuted on CardFill", TextMuted, CardFill)         // 8.34:1
        assertContrast("TextMuted on SurfaceRaised", TextMuted, SurfaceRaised) // 6.94:1

        assertContrast("TextMuted2 on bg", TextMuted2, Night)                // 5.83:1
        assertContrast("TextMuted2 on NightMid", TextMuted2, NightMid)       // 4.96:1
        assertContrast("TextMuted2 on CardFill", TextMuted2, CardFill)       // 6.35:1
        assertContrast("TextMuted2 on SurfaceRaised", TextMuted2, SurfaceRaised) // 5.28:1

        assertContrast("EyebrowMuted on bg", EyebrowMuted, Night)            // 5.83:1
        assertContrast("EyebrowMuted on CardFill", EyebrowMuted, CardFill)   // 6.35:1
    }

    @Test
    fun dawn_accentsAsText_meetAA() = dawn {
        // Accents are used as text labels all over the signed-in app, so the
        // Dawn palette darkens each until it clears 4.5:1 even on the darkest
        // page paint (NightMid 0xFFDDDBF0) and on every card fill.
        listOf(
            Triple("Periwinkle", Periwinkle, doubleArrayOf(6.40, 5.44, 6.97, 5.80)),
            Triple("Cyan", Cyan, doubleArrayOf(5.59, 4.76, 6.09, 5.07)),
            Triple("Warm", Warm, doubleArrayOf(5.72, 4.87, 6.24, 5.19)),
            Triple("Ok", Ok, doubleArrayOf(5.54, 4.71, 6.04, 5.02)),
            Triple("Danger", Danger, doubleArrayOf(5.69, 4.84, 6.20, 5.16)),
        ).forEach { (name, accent, _) ->
            assertContrast("$name on bg", accent, Night)
            assertContrast("$name on NightMid", accent, NightMid)
            assertContrast("$name on CardFill", accent, CardFill)
            assertContrast("$name on SurfaceRaised", accent, SurfaceRaised)
        }
    }

    @Test
    fun dawn_primaryButton_meetsAA() = dawn {
        // Dawn's primary pill is a deep-periwinkle fill with white text (a white
        // pill vanishes on cream — see Gradients.primary in Tokens.kt). The
        // lighter gradient stop (0xFF5545AD) is the worst case for white text.
        // Keep the hexes in sync with Gradients.primary.
        assertContrast("OnPrimary on Dawn primary top", OnPrimary, Color(0xFF5545AD))   // 7.39:1
        assertContrast("OnPrimary on Dawn primary floor", OnPrimary, Color(0xFF4A3B9C)) // 8.76:1
        // Disabled keeps Ink text on the Dawn disabled fill.
        assertContrast("Ink on ButtonDisabled(Dawn)", Ink, ButtonDisabled)              // 8.54:1
    }

    @Test
    fun dawn_dangerButton_meetsAA() = dawn {
        // DangerButton text is the Night token — cream on Dawn's deep danger fill.
        assertContrast("Night(bg) on Danger", Night, Danger) // 5.69:1
    }

    @Test
    fun dawn_selectedChip_meetsAA() = dawn {
        // On Dawn the selected PickChip inverts to an Ink pill with white text.
        assertContrast("ChipSelectedInk on ChipSelectedFill", ChipSelectedInk, ChipSelectedFill) // 16.85:1
        assertContrast("TextBright(Ink) on bg", TextBright, Night)                               // 14.60:1
    }

    @Test
    fun roleAliases_trackTheirSourceTokens() {
        listOf(ThemeMode.Night, ThemeMode.Dawn).forEach { mode ->
            inTheme(mode) {
                // The role layer must stay a true alias of the palette it documents.
                assertTrue(Surface == CardFill)
                assertTrue(SurfaceRaised == ChipFill)
                assertTrue(SurfaceField == FieldFill)
                assertTrue(Line == LineStroke)
                assertTrue(TextSecondary == TextSoft)
                assertTrue(TextFaint == TextMuted2)
                assertTrue(AccentSoft == PeriwinkleSoft)
            }
        }
    }

    // ── Theme plumbing ───────────────────────────────────────────────────────

    @Test
    fun forceNight_overridesDawnPreference() {
        val prevMode = AppTheme.mode
        val prevForce = AppTheme.forceNight
        try {
            AppTheme.mode = ThemeMode.Dawn
            AppTheme.forceNight = true   // Sleep tab / signed-out funnel / splash
            assertTrue(AppTheme.isNight)
            assertEquals(Color(0xFF100D2B).toArgb(), Night.toArgb())
        } finally {
            AppTheme.mode = prevMode
            AppTheme.forceNight = prevForce
        }
    }

    @Test
    fun themeMode_prefRoundTrip() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, themeModeFromPref(mode.prefValue()))
        }
        assertEquals(ThemeMode.System, themeModeFromPref(null))
        assertEquals(ThemeMode.System, themeModeFromPref("garbage"))
    }
}
