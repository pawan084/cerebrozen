package com.cerebrozen.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The token contrast gate (docs/REDESIGN_V2.md §2), parameterized over both
 * themes: every text role must reach the WCAG AA 4.5:1 ratio against every
 * surface it legitimately appears on, in Light Dawn AND in Night. Runs as a
 * plain JVM test over the real Color tokens (which resolve through AppTheme),
 * so a palette tweak that breaks legibility fails the build instead of shipping.
 *
 * The gate is **per legal pairing**, as on the web side: a tonal role must clear
 * 4.5:1 on the three neutral grounds and on *its own* `-soft` wash. Gating amber
 * against a danger wash is a pairing that never occurs and would force the brand
 * colours needlessly dark.
 *
 * The three neutral grounds map onto Android's historical token names:
 *
 * | canonical         | token                        | Dawn      | Night     |
 * |-------------------|------------------------------|-----------|-----------|
 * | `--surface`       | [Night]                      | `#F8F4EE` | `#171019` |
 * | `--surface-raised`| [Surface]/[CardFill]         | `#FFFCF8` | `#241927` |
 * | `--surface-field` | [SurfaceRaised]/[ChipFill],  | `#F3ECF3` | `#302237` |
 * |                   | [SurfaceField], [NightMid]   |           |           |
 *
 * Passing on all three implies passing on every stop of the glass/backdrop
 * gradients, which are interpolations between them.
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

    /** The three neutral grounds, in the resolved theme. */
    private fun grounds(): List<Pair<String, Color>> = listOf(
        "surface" to Night,
        "surface-raised" to Surface,
        "surface-field" to SurfaceRaised,
    )

    private fun assertOnEveryGround(name: String, fg: Color) =
        grounds().forEach { (gname, bg) -> assertContrast("$name on $gname", fg, bg) }

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

    // ── Night — the plum dark appearance ────────────────────────────────────

    @Test
    fun night_textFaint_meetsAA_onEverySurface() = night {
        // TextMuted2/TextFaint is the faintest legal text (`--text-faint`).
        assertContrast("TextMuted2 on CardFill", TextMuted2, CardFill)          // 7.71:1
        assertContrast("TextMuted2 on Night", TextMuted2, Night)                // 8.53:1
        assertContrast("TextMuted2 on SurfaceRaised", TextMuted2, SurfaceRaised) // 6.80:1
    }

    @Test
    fun night_textMuted_meetsAA() = night {
        assertContrast("TextMuted on CardFill", TextMuted, CardFill) // 7.71:1
        assertContrast("TextMuted on Night", TextMuted, Night)       // 8.53:1
    }

    @Test
    fun night_textSoft_meetsAA() = night {
        assertContrast("TextSoft on CardFill", TextSoft, CardFill) // 11.74:1
        assertContrast("TextSoft on Night", TextSoft, Night)       // 12.98:1
    }

    @Test
    fun night_periwinkleAsText_meetsAA_onEverySurface() = night {
        // Periwinkle is the `--accent` role (plum since the Light Dawn port). It
        // renders as text (labels like "Try another", "Try together") on cards
        // and glass tops, so it is gated as text, not just as a fill.
        assertContrast("Periwinkle on CardFill", Periwinkle, CardFill)           // 8.77:1
        assertContrast("Periwinkle on Night", Periwinkle, Night)                 // 9.70:1
        assertContrast("Periwinkle on SurfaceRaised", Periwinkle, SurfaceRaised) // 7.74:1
    }

    @Test
    fun night_eyebrow_meetsAA() = night {
        // Small-caps section labels render at label size — held to normal-text 4.5:1.
        assertContrast("EyebrowMuted on Night", EyebrowMuted, Night)       // 8.53:1
        assertContrast("EyebrowMuted on CardFill", EyebrowMuted, CardFill) // 7.71:1
    }

    @Test
    fun night_primaryButtonText_meetsAA() = night {
        // The primary CTA is an ACCENT FILL in both themes (tokens.css
        // `--btn-primary-bg`), so on Night it is a pale plum carrying the dark
        // `--on-accent` ink — the reverse of Dawn. Gradients.primary runs
        // accent -> accent-2; both stops are gated because a Brush's stops
        // aren't readable from a plain JVM test.
        assertContrast("OnPrimary on PrimaryButtonFill", OnPrimary, PrimaryButtonFill) // 8.77:1
        assertContrast("OnPrimary on primary-gradient floor", OnPrimary, Accent2)      // 5.73:1
        // The disabled pill keeps Ink as its label in both themes, so on Night
        // the disabled fill has to be the LIGHT one.
        assertContrast("Ink on ButtonDisabled(Night)", Ink, ButtonDisabled)            // 5.24:1
    }

    @Test
    fun night_textPrimary_clearsAA_withRoomToSpare() = night {
        // Display text would only need 3.0:1; it clears full AA easily — gate at 4.5.
        assertContrast("TextPrimary on Night", TextPrimary, Night)       // 17.36:1
        assertContrast("TextPrimary on CardFill", TextPrimary, CardFill) // 15.70:1
    }

    @Test
    fun night_dangerButton_meetsAA() = night {
        // DangerButton draws the OnDanger role over the Danger fill.
        assertContrast("OnDanger on Danger", OnDanger, Danger) // 8.29:1
        assertEquals("OnDanger(Night)", Night.toArgb(), OnDanger.toArgb())
    }

    @Test
    fun night_selectedChip_meetsAA() = night {
        // The selected PickChip is an accent pill carrying `--on-accent`.
        assertContrast("ChipSelectedInk on ChipSelectedFill", ChipSelectedInk, ChipSelectedFill) // 8.77:1
    }

    // ── The palette is pinned, byte for byte, to design/tokens.css ──────────

    @Test
    fun nightPalette_pinsTheCanonicalPlumValues() = night {
        // The Night palette is a hand-kept mirror of tokens.css
        // `:root[data-theme="night"]` (cross-stack contract, ARCHITECTURE.md).
        // Drifting one value silently un-mirrors the whole client, so every role
        // is pinned here — this is the test that makes "same tokens everywhere"
        // a fact rather than a claim.
        val expected = mapOf(
            // --surface / --surface-raised / --surface-field / --line
            "Night" to (Night to Color(0xFF171019)),
            "NightMid" to (NightMid to Color(0xFF302237)),
            "NightPurple" to (NightPurple to Color(0xFF302237)),
            "CardFill" to (CardFill to Color(0xFF241927)),
            "FieldFill" to (FieldFill to Color(0xFF302237)),
            "ChipFill" to (ChipFill to Color(0xFF302237)),
            "LineStroke" to (LineStroke to Color(0xFF3E3043)),
            // --text / --text-secondary / --text-faint
            "TextPrimary" to (TextPrimary to Color(0xFFFAF5FB)),
            "TextSoft" to (TextSoft to Color(0xFFDED4E0)),
            "TextMuted" to (TextMuted to Color(0xFFB9ABB9)),
            "TextMuted2" to (TextMuted2 to Color(0xFFB9ABB9)),
            "EyebrowMuted" to (EyebrowMuted to Color(0xFFB9ABB9)),
            // --accent family
            "Periwinkle" to (Periwinkle to Color(0xFFD9ACDE)),
            "Accent2" to (Accent2 to Color(0xFFC580B5)),
            "AccentSoft" to (AccentSoft to Color(0xFF3A2A3E)),
            "OnAccent" to (OnAccent to Color(0xFF241927)),
            // tonal roles + their washes
            "Ok" to (Ok to Color(0xFFAFD6B2)),
            "OkSoft" to (OkSoft to Color(0xFF1E2A20)),
            "Warm" to (Warm to Color(0xFFF29AB0)),
            "WarmSoft" to (WarmSoft to Color(0xFF351E25)),
            "Danger" to (Danger to Color(0xFFFF8C82)),
            "DangerSoft" to (DangerSoft to Color(0xFF3A211E)),
            "Amber" to (Amber to Color(0xFFF0C37F)),
            "AmberSoft" to (AmberSoft to Color(0xFF453622)),
            "Cyan" to (Cyan to Color(0xFF9CC4DC)),
            "Info" to (Info to Color(0xFF9CC4DC)),
            "InfoSoft" to (InfoSoft to Color(0xFF22323C)),
        )
        expected.forEach { (name, pair) ->
            assertEquals("$name drifted from tokens.css (Night)", pair.second.toArgb(), pair.first.toArgb())
        }
        // Component roles are derived from the canonical ones — pin the derivation
        // rather than a second copy of the hex, so they can never disagree.
        assertEquals("OnPrimary", OnAccent.toArgb(), OnPrimary.toArgb())
        assertEquals("ChipSelectedFill", Periwinkle.toArgb(), ChipSelectedFill.toArgb())
        assertEquals("ChipSelectedInk", OnAccent.toArgb(), ChipSelectedInk.toArgb())
        assertEquals("SwitchThumbOn", OnAccent.toArgb(), SwitchThumbOn.toArgb())
        assertEquals("TextBright", TextPrimary.toArgb(), TextBright.toArgb())
        assertEquals("PrimaryButtonFill", Periwinkle.toArgb(), PrimaryButtonFill.toArgb())
        assertEquals("PrimaryButtonInk", OnAccent.toArgb(), PrimaryButtonInk.toArgb())
        // Veils stay light-on-dark on Night.
        assertEquals("Veil", Color.White.copy(alpha = 0.07f).toArgb(), Veil.toArgb())
        assertEquals("VeilSoft", Color.White.copy(alpha = 0.06f).toArgb(), VeilSoft.toArgb())
        assertEquals("VeilWell", Color.White.copy(alpha = 0.10f).toArgb(), VeilWell.toArgb())
        assertEquals("VeilStrong", Color.White.copy(alpha = 0.18f).toArgb(), VeilStrong.toArgb())
        assertEquals("VeilLine", Color.White.copy(alpha = 0.12f).toArgb(), VeilLine.toArgb())
        // Art constants track the fixed-dark-art sites 1:1.
        assertEquals("ArtScrim", Night.toArgb(), ArtScrim.toArgb())
        assertEquals("ArtTextSoft", TextSoft.toArgb(), ArtTextSoft.toArgb())
    }

    @Test
    fun dawnPalette_pinsTheCanonicalLightValues() = dawn {
        // design/tokens.css `:root`, byte for byte — Light Dawn, the default
        // appearance.
        //
        // This block previously called itself "the mirror of tokens.css :root"
        // while pinning values taken from ref/mobile.html instead: indigo ink
        // (#1C1740) against the canonical warm #211D20, and an indigo accent
        // against the plum #5A2B5C. Nothing else could catch it —
        // sync-tokens.mjs gates the four globals.css files and cannot read
        // Kotlin — so this test WAS the gate, and it was locking the drift in.
        // If a value here disagrees with design/tokens.css, tokens.css wins
        // (REDESIGN_V2 §6: where the spec and this repo disagree, the spec does).
        val expected = mapOf(
            "Night" to (Night to Color(0xFFF8F4EE)),          // --surface
            "NightMid" to (NightMid to Color(0xFFF3ECF3)),    // --surface-field
            "NightPurple" to (NightPurple to Color(0xFFF3ECF3)),
            "CardFill" to (CardFill to Color(0xFFFFFCF8)),    // --surface-raised
            "FieldFill" to (FieldFill to Color(0xFFF3ECF3)),
            "ChipFill" to (ChipFill to Color(0xFFF3ECF3)),
            "LineStroke" to (LineStroke to Color(0xFFDFD9D3)), // --line
            "TextPrimary" to (TextPrimary to Color(0xFF211D20)), // --text
            "TextSoft" to (TextSoft to Color(0xFF514A50)),    // --text-secondary
            "TextMuted" to (TextMuted to Color(0xFF686267)),  // --text-faint
            "TextMuted2" to (TextMuted2 to Color(0xFF686267)),
            "EyebrowMuted" to (EyebrowMuted to Color(0xFF315C7A)), // --info
            "Periwinkle" to (Periwinkle to Color(0xFF5A2B5C)), // --accent
            "Accent2" to (Accent2 to Color(0xFF8A4A78)),      // --accent-2
            "AccentSoft" to (AccentSoft to Color(0xFFE9DDEA)), // --accent-soft
            "OnAccent" to (OnAccent to Color(0xFFFFFCF8)),    // --on-accent
            "Ok" to (Ok to Color(0xFF49634F)),                // --ok
            "OkSoft" to (OkSoft to Color(0xFFE5EDE3)),
            "Warm" to (Warm to Color(0xFFA45161)),            // --warm
            "WarmSoft" to (WarmSoft to Color(0xFFFAE9EA)),
            "Danger" to (Danger to Color(0xFFC23A33)),        // --danger
            "DangerSoft" to (DangerSoft to Color(0xFFFFE9E6)),
            "Amber" to (Amber to Color(0xFF92611D)),          // --amber
            "AmberSoft" to (AmberSoft to Color(0xFFFFF1D8)),
            "Cyan" to (Cyan to Color(0xFF315C7A)),            // --info
            "Info" to (Info to Color(0xFF315C7A)),
            "InfoSoft" to (InfoSoft to Color(0xFFE7F0F5)),
        )
        expected.forEach { (name, pair) ->
            assertEquals("$name drifted from tokens.css (Dawn)", pair.second.toArgb(), pair.first.toArgb())
        }
        assertEquals("OnPrimary", OnAccent.toArgb(), OnPrimary.toArgb())
        assertEquals("ChipSelectedFill", Periwinkle.toArgb(), ChipSelectedFill.toArgb())
        assertEquals("ChipSelectedInk", OnAccent.toArgb(), ChipSelectedInk.toArgb())
        assertEquals("TextBright", TextPrimary.toArgb(), TextBright.toArgb())
        // Ink and Cream are the theme-independent art constants; on Dawn they
        // coincide with --text and --surface-raised, which is what makes them
        // safe to use on light and dark art respectively.
        assertEquals("Ink", TextPrimary.toArgb(), Ink.toArgb())
        assertEquals("Cream", CardFill.toArgb(), Cream.toArgb())
    }

    // ── Dawn — the default appearance ───────────────────────────────────────

    @Test
    fun dawn_textRoles_meetAA_onEverySurface() = dawn {
        assertContrast("TextPrimary on bg", TextPrimary, Night)               // 15.20:1
        assertContrast("TextPrimary on NightMid", TextPrimary, NightMid)      // 14.35:1
        assertContrast("TextPrimary on CardFill", TextPrimary, CardFill)      // 16.28:1
        assertContrast("TextPrimary on SurfaceRaised", TextPrimary, SurfaceRaised) // 14.35:1

        assertContrast("TextSoft on bg", TextSoft, Night)                     // 7.84:1
        assertContrast("TextSoft on NightMid", TextSoft, NightMid)            // 7.40:1
        assertContrast("TextSoft on CardFill", TextSoft, CardFill)            // 8.40:1
        assertContrast("TextSoft on SurfaceRaised", TextSoft, SurfaceRaised)  // 7.40:1

        assertContrast("TextMuted on bg", TextMuted, Night)                   // 5.43:1
        assertContrast("TextMuted on NightMid", TextMuted, NightMid)          // 5.13:1
        assertContrast("TextMuted on CardFill", TextMuted, CardFill)          // 5.81:1
        assertContrast("TextMuted on SurfaceRaised", TextMuted, SurfaceRaised) // 5.13:1

        assertContrast("TextMuted2 on bg", TextMuted2, Night)                 // 5.43:1
        assertContrast("TextMuted2 on NightMid", TextMuted2, NightMid)        // 5.13:1
        assertContrast("TextMuted2 on CardFill", TextMuted2, CardFill)        // 5.81:1
        assertContrast("TextMuted2 on SurfaceRaised", TextMuted2, SurfaceRaised) // 5.13:1

        assertContrast("EyebrowMuted on bg", EyebrowMuted, Night)             // 5.43:1
        assertContrast("EyebrowMuted on CardFill", EyebrowMuted, CardFill)    // 5.81:1
    }

    @Test
    fun dawn_accentsAsText_meetAA() = dawn {
        // Accents are used as text labels all over the signed-in app, so every
        // tonal role clears 4.5:1 on all three neutral grounds. Four of them
        // (`--text-faint`, `--warm`, `--danger`, `--amber`) were darkened 4–9%
        // from the prototype values to get here — see REDESIGN_V2 §2.
        listOf(
            "Periwinkle" to Periwinkle,  // 9.91 / 10.61 / 9.36
            "Accent2" to Accent2,        // 5.75 / 6.16 / 5.43
            "Cyan" to Cyan,              // 6.51 / 6.98 / 6.15
            "Warm" to Warm,              // 4.88 / 5.23 / 4.61
            "Ok" to Ok,                  // 6.03 / 6.45 / 5.69
            "Danger" to Danger,          // 4.86 / 5.20 / 4.59
            "Amber" to Amber,            // 4.85 / 5.20 / 4.58
        ).forEach { (name, accent) ->
            assertOnEveryGround(name, accent)
            assertContrast("$name on NightMid", accent, NightMid)
        }
    }

    @Test
    fun tonalRolesClearAA_onTheirOwnWash_inBothThemes() {
        // The second half of the per-pairing gate: a tonal role must also be
        // legible on the wash it is paired with (a sage tick on a sage tint, an
        // amber caption on an amber banner). This is where the prototype's
        // lighter tonal values failed hardest.
        listOf(ThemeMode.Night, ThemeMode.Dawn).forEach { mode ->
            inTheme(mode) {
                listOf(
                    "accent" to (Periwinkle to AccentSoft),
                    "accent-2" to (Accent2 to AccentSoft),
                    "ok" to (Ok to OkSoft),
                    "warm" to (Warm to WarmSoft),
                    "danger" to (Danger to DangerSoft),
                    "amber" to (Amber to AmberSoft),
                    "info" to (Info to InfoSoft),
                ).forEach { (name, pair) ->
                    assertContrast("$name on its own wash ($mode)", pair.first, pair.second)
                }
            }
        }
    }

    @Test
    fun tonalRolesAreLegibleOnEveryGround_inBothThemes() {
        // The whole gate in one place, so a new theme or a re-toned role cannot
        // pass by only being checked on the ground it was designed against.
        listOf(ThemeMode.Night, ThemeMode.Dawn).forEach { mode ->
            inTheme(mode) {
                listOf(
                    "TextPrimary" to TextPrimary,
                    "TextSecondary" to TextSecondary,
                    "TextFaint" to TextFaint,
                    "TextMuted" to TextMuted,
                    "EyebrowMuted" to EyebrowMuted,
                    "Periwinkle" to Periwinkle,
                    "Accent2" to Accent2,
                    "Ok" to Ok,
                    "Warm" to Warm,
                    "Danger" to Danger,
                    "Amber" to Amber,
                    "Info" to Info,
                ).forEach { (name, role) -> assertOnEveryGround("$name ($mode)", role) }
            }
        }
    }

    @Test
    fun dawn_primaryButton_meetsAA() = dawn {
        // Dawn's primary pill is the accent fill with `--on-accent` text: a
        // near-white pill is invisible on an ivory ground (REDESIGN_V2 §2).
        // Gradients.primary runs accent -> accent-2; both stops are gated.
        assertContrast("OnPrimary on Dawn primary top", OnPrimary, PrimaryButtonFill) // 10.61:1
        assertContrast("OnPrimary on Dawn primary floor", OnPrimary, Accent2)         // 6.16:1
        // Disabled keeps Ink text on the Dawn disabled fill.
        assertContrast("Ink on ButtonDisabled(Dawn)", Ink, ButtonDisabled)            // 11.90:1
    }

    @Test
    fun dawn_dangerButton_meetsAA() = dawn {
        // DangerButton text is the OnDanger role — the ivory ground on the deep
        // danger fill.
        assertContrast("OnDanger on Danger", OnDanger, Danger) // 4.86:1
        assertEquals("OnDanger(Dawn)", Night.toArgb(), OnDanger.toArgb())
    }

    @Test
    fun dawn_selectedChip_meetsAA() = dawn {
        assertContrast("ChipSelectedInk on ChipSelectedFill", ChipSelectedInk, ChipSelectedFill) // 10.61:1
        assertContrast("TextBright on bg", TextBright, Night)                                    // 15.20:1
    }

    // ── W21 banner wash ──────────────────────────────────────────────────────

    @Test
    fun infoBanner_kindWash_keepsTextAA_inBothThemes() {
        // Content banners (program strip, evening wind-down) tint SurfaceRaised
        // with a leading 10% wash of the kind's art accent, fading to
        // transparent by 55% width. Gate the WORST case — text sitting on the
        // full-strength blend — even though the body copy starts further in and
        // the trailing action label sits past the gradient on plain
        // SurfaceRaised (already gated above in both themes).
        fun composite(over: Color, under: Color): Color {
            val a = over.alpha
            return Color(
                red = over.red * a + under.red * (1 - a),
                green = over.green * a + under.green * (1 - a),
                blue = over.blue * a + under.blue * (1 - a),
            )
        }
        listOf(ThemeMode.Night, ThemeMode.Dawn).forEach { mode ->
            inTheme(mode) {
                listOf("program", "sleep").forEach { kind ->
                    val washed = composite(
                        com.cerebrozen.app.ui.screens.artAccent(kind).copy(alpha = 0.10f),
                        SurfaceRaised,
                    )
                    assertContrast("TextSecondary on $kind-washed banner ($mode)", TextSecondary, washed)
                }
            }
        }
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
                assertTrue(Info == Cyan)
                // AccentSoft is the accent's own wash (`--accent-soft`), and it
                // must FLIP with the theme: it used to alias a single constant,
                // which made the Night wash a pale lavender on a dark card.
                assertTrue(AccentSoft == if (mode == ThemeMode.Night) NightPalette.accentSoft else DawnPalette.accentSoft)
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
            AppTheme.forceNight = true   // internal preview/test seam
            assertTrue(AppTheme.isNight)
            assertEquals(Color(0xFF171019).toArgb(), Night.toArgb())
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
