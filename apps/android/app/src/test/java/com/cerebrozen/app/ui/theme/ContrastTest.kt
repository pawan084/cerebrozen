package com.cerebrozen.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The token contrast gate, parameterized over both themes of the "Serene" palette
 * (Color.kt): every text role must reach WCAG AA 4.5:1 against every surface it
 * legitimately appears on, in Navy AND in Warm White. Runs as a plain JVM test
 * over the real Color tokens (which resolve through AppTheme), so a palette tweak
 * that breaks legibility fails the build instead of shipping.
 *
 * This gate is why the brand palette is split in two (see the header comment in
 * Color.kt): the owner-specified Sky #6ECBF5 and Mint #7ED9B6 measure 1.75:1 and
 * 1.61:1 as text on the Warm White ground — unreadable. They ship verbatim as
 * FILLS ([BrandSecondary]/[BrandAccent]); the [Cyan]/[Ok] tokens carry the
 * text-safe siblings that these tests enforce.
 *
 * Night surfaces under test: [Night] (page backdrop), [Surface]/[CardFill]
 * (resting card) and [SurfaceRaised] (chip fill + the lightest stop of
 * `Gradients.glass`, i.e. the lightest paint a card actually renders).
 *
 * Dawn surfaces: [Night] (the warm-white ground), [NightMid] (the darkest paint
 * of the page backdrop — worst case for dark-on-light text), [CardFill] (white)
 * and [SurfaceRaised].
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
            "$name: contrast ${"%.2f".format(ratio)}:1 is below the $min:1 gate",
            ratio >= min,
        )
    }

    /** Composite an alpha colour over an opaque one — how a wash actually renders. */
    private fun composite(over: Color, under: Color): Color {
        val a = over.alpha
        return Color(
            red = over.red * a + under.red * (1 - a),
            green = over.green * a + under.green * (1 - a),
            blue = over.blue * a + under.blue * (1 - a),
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

    /** Every surface a text role can legitimately land on, per theme. */
    private fun surfaces(light: Boolean): List<Pair<String, Color>> = buildList {
        add("bg" to Night)
        if (light) add("bgTop" to NightMid)   // darkest page paint — dark-text worst case
        add("card" to Surface)
        add("raised" to SurfaceRaised)
    }

    /** The full text ladder + every accent that is used as a label somewhere. */
    private fun textRoles(): List<Pair<String, Color>> = listOf(
        "TextPrimary" to TextPrimary,
        "TextSecondary" to TextSecondary,
        "TextMuted" to TextMuted,
        "TextFaint" to TextFaint,
        "EyebrowMuted" to EyebrowMuted,
        "Periwinkle" to Periwinkle,
        "Cyan" to Cyan,
        "Ok" to Ok,
        "Warm" to Warm,
        "Danger" to Danger,
    )

    // ── The core gate: every role on every surface, in both themes ───────────

    @Test
    fun night_everyTextRole_meetsAA_onEverySurface() = night {
        textRoles().forEach { (role, colour) ->
            surfaces(light = false).forEach { (name, bg) ->
                assertContrast("$role on $name (Night)", colour, bg)
            }
        }
    }

    @Test
    fun dawn_everyTextRole_meetsAA_onEverySurface() = dawn {
        textRoles().forEach { (role, colour) ->
            surfaces(light = true).forEach { (name, bg) ->
                assertContrast("$role on $name (Dawn)", colour, bg)
            }
        }
    }

    // ── Controls ─────────────────────────────────────────────────────────────

    @Test
    fun primaryButton_meetsAA_inBothThemes() {
        // The CTA is one deep-lavender pill with a white label in both themes.
        // Both gradient stops are gated; the TOP stop is the worst case.
        // Keep these hexes in sync with Gradients.primary (Brush stops aren't
        // readable from a plain JVM test).
        listOf(ThemeMode.Night, ThemeMode.Dawn).forEach { mode ->
            inTheme(mode) {
                assertContrast("OnPrimary on pill top ($mode)", OnPrimary, LavenderPillTop)     // 4.72:1
                assertContrast("OnPrimary on pill floor ($mode)", OnPrimary, LavenderPillFloor) // 6.46:1
                assertContrast("Ink on ButtonDisabled ($mode)", Ink, ButtonDisabled)            // 9.13:1
            }
        }
    }

    @Test
    fun brandPrimary_isNotUsedAsText_because_itCannotBe() {
        // Documents the reason the palette is split. Brand Lavender under a white
        // label is 3.90:1 — below AA. If someone "simplifies" Gradients.primary
        // back to BrandPrimary, this test explains why it broke.
        val whiteOnBrand = contrast(Color.White.toArgb(), BrandPrimary.toArgb())
        assertTrue(
            "BrandPrimary now passes as a text ground (%.2f:1) — the pill could be simplified"
                .format(whiteOnBrand),
            whiteOnBrand < 4.5,
        )
    }

    @Test
    fun dangerButton_meetsAA_inBothThemes() {
        // DangerButton draws the Night token over the Danger fill: dark ink on a
        // soft coral in Night, warm white on a deep rose in Dawn.
        night { assertContrast("Night on Danger", Night, Danger) }  // 7.66:1
        dawn { assertContrast("Night on Danger", Night, Danger) }   // 6.05:1
    }

    @Test
    fun selectedChip_meetsAA_inBothThemes() {
        listOf(ThemeMode.Night, ThemeMode.Dawn).forEach { mode ->
            inTheme(mode) {
                assertContrast(
                    "ChipSelectedInk on ChipSelectedFill ($mode)",
                    ChipSelectedInk, ChipSelectedFill,
                ) // 17.25:1 both
            }
        }
    }

    // ── Washes: colour laid over a surface at low alpha ──────────────────────

    @Test
    fun infoBanner_kindWash_keepsTextAA_inBothThemes() {
        // Content banners tint SurfaceRaised with a leading 10% wash of the kind's
        // art accent. Gate the WORST case — text sitting on the full-strength blend.
        listOf(ThemeMode.Night, ThemeMode.Dawn).forEach { mode ->
            inTheme(mode) {
                listOf("program", "sleep", "meditation", "soundscape").forEach { kind ->
                    val washed = composite(
                        com.cerebrozen.app.ui.screens.artAccent(kind).copy(alpha = 0.10f),
                        SurfaceRaised,
                    )
                    assertContrast("TextSecondary on $kind wash ($mode)", TextSecondary, washed)
                    assertContrast("TextFaint on $kind wash ($mode)", TextFaint, washed)
                }
            }
        }
    }

    @Test
    fun focusCard_brandWash_keepsTextAA_inBothThemes() {
        // The FocusCard (the primary daily action) lifts SurfaceRaised with a 10%
        // BRAND-lavender wash. Mint and Sky are light hues, so on Navy a wash
        // *raises* the ground and squeezes light text — this is the gate that
        // caught TextFaint at 4.34:1 during the redesign.
        listOf(ThemeMode.Night, ThemeMode.Dawn).forEach { mode ->
            inTheme(mode) {
                listOf(
                    "BrandPrimary" to BrandPrimary,
                    "BrandSecondary" to BrandSecondary,
                    "BrandAccent" to BrandAccent,
                ).forEach { (name, brand) ->
                    val washed = composite(brand.copy(alpha = 0.10f), SurfaceRaised)
                    assertContrast("TextPrimary on $name wash ($mode)", TextPrimary, washed)
                    assertContrast("TextSecondary on $name wash ($mode)", TextSecondary, washed)
                    assertContrast("TextFaint on $name wash ($mode)", TextFaint, washed)
                }
            }
        }
    }

    // ── The palette pin: values are deliberate, not incidental ───────────────

    @Test
    fun brandFills_matchTheOwnerSpecification_exactly() {
        // The five brand colours ship verbatim as fills. If a future pass "fixes"
        // their contrast by changing them, the brand changes — fail loudly instead.
        assertEquals("BrandPrimary", Color(0xFF7C6FF0).toArgb(), BrandPrimary.toArgb())
        assertEquals("BrandSecondary", Color(0xFF6ECBF5).toArgb(), BrandSecondary.toArgb())
        assertEquals("BrandAccent", Color(0xFF7ED9B6).toArgb(), BrandAccent.toArgb())
        night { assertEquals("Dark background", Color(0xFF0F172A).toArgb(), Night.toArgb()) }
        dawn {
            assertEquals("Light background", Color(0xFFFAFAFC).toArgb(), Night.toArgb())
            assertEquals("Light surface", Color(0xFFFFFFFF).toArgb(), Surface.toArgb())
        }
    }

    @Test
    fun roleAliases_trackTheirSourceTokens() {
        listOf(ThemeMode.Night, ThemeMode.Dawn).forEach { mode ->
            inTheme(mode) {
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
            assertEquals(Color(0xFF0F172A).toArgb(), Night.toArgb())
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
