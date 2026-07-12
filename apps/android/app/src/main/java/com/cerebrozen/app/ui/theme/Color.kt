package com.cerebrozen.app.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette. Shares the hue family with the iOS DesignSystem
// (apps/ios/CereBro/DesignSystem/Theme.swift) and the web tokens
// (design/tokens.css), but the 2026-07 Android "reference gradient" pass moved
// the surfaces to solid indigo fills (see CardFill/Gradients.glass) rather than
// the translucent white overlays the other platforms still use — so treat this
// as the source of truth for Android, not a byte-for-byte mirror.
//
// ── Dusk & Dawn (REDESIGN.md §4.1) ─────────────────────────────────────────
// The app now has two themes resolved through one set of top-level tokens:
// every themed token below is a `get()` property that reads [AppTheme.isNight]
// (snapshot state), so existing screens keep importing the same names and get
// Dawn for free. [NightPalette] holds the exact pre-Dawn values — Night mode
// renders byte-identically to before this change (gated by ContrastTest's
// nightPalette_isByteIdentical test). [DawnPalette] is the warm light theme;
// every Dawn text/surface pairing is contrast-gated ≥ 4.5:1 in ContrastTest.

/** The original deep-indigo theme — values must never change (zero visual
 * change in Night mode is a hard requirement; see ContrastTest). */
internal object NightPalette {
    val night = Color(0xFF100D2B)       // reference gradient floor
    val nightMid = Color(0xFF3A3372)    // reference gradient top
    val nightPurple = Color(0xFF29254D) // fields and secondary surfaces
    val textPrimary = Color(0xFFF5F4FF) // --text (on Night 17.29:1, on CardFill 11.91:1)
    val textSoft = Color(0xFFE1DEEE)    // --soft (on CardFill 9.82:1, on Night 14.25:1)
    val textMuted = Color(0xFFC0BBD4)   // --muted (on CardFill 6.99:1, on Night 10.14:1)
    // --muted-2 — lightened from 0xFF928CAC (2026-07 contrast fix, same lavender-grey
    // hue/saturation): the old value hit only 4.06:1 on CardFill and 3.55:1 on the
    // raised glass-top fill (0xFF39355F). Now: on CardFill 5.16:1, on Night 7.48:1,
    // on SurfaceRaised 4.51:1 — all ≥ the 4.5:1 WCAG gate (see ContrastTest).
    val textMuted2 = Color(0xFFA5A0BA)
    val cardFill = Color(0xFF302C55)    // glass card fill
    val lineStroke = Color(0xFF514B76)
    val eyebrowMuted = Color(0xFFAAA3D0)   // small-caps section eyebrow labels
    val buttonDisabled = Color(0xFF777486) // disabled primary-button fill
    val fieldFill = Color(0xFF302B55)      // focused text-field container
    val chipFill = Color(0xFF39355F)       // unselected pick-chip fill
    // Floating bottom-nav pill — a lifted lavender-indigo capsule over a dark scrim.
    val navPillTop = Color(0xFF413A70)
    val navPillBottom = Color(0xFF28234D)
    val navScrim = Color(0xFF100D2B)
    // Accents (see the accent notes on the themed getters below).
    // Periwinkle brightened 0xFF8B78F2 → 0xFFA89AF6 (2026-07-12, TODO contrast
    // debt): as text it renders on CardFill 302C55 (was 3.75:1, now 5.33),
    // Night 100D2B (7.73) and the raised/glass top 39355F (4.66) — the minimal
    // in-family lighten clearing 4.5 on all three.
    val periwinkle = Color(0xFFA89AF6)  // switches, focus and icon accent
    val cyan = Color(0xFF8FE6EE)        // --cyan (breathing orb)
    val warm = Color(0xFFF0A48C)        // --warm (coral)
    val ok = Color(0xFF7EE0A8)          // --ok (success)
    val danger = Color(0xFFE08A9A)      // --danger
    // Component tokens introduced by the Dawn pass — Night values reproduce the
    // exact colors these sites used before (so Night renders identically).
    val onPrimary = Color(0xFF1C1740)              // = Ink: PrimaryButton text on the white pill
    val chipSelectedFill = Color(0xFFFFFFFF)       // selected PickChip fill (was Color.White)
    val chipSelectedInk = Color(0xFF1C1740)        // selected PickChip text (was Ink)
    val switchThumbOn = Color(0xFF1C1740)          // checked AppSwitch thumb (was Ink)
    val textBright = Color(0xFFFFFFFF)             // brightest chrome text (was Color.White)
    val navSelectedHi = Color(0xB8A89AF6)          // = Periwinkle.copy(alpha = 0.72f)
    val navSelectedLo = Color(0x2EA89AF6)          // = Periwinkle.copy(alpha = 0.18f)
    // Structural veils (soft wells/tracks/hairlines that were White.copy(alpha=…)).
    val veil = Color(0x12FFFFFF)        // = White 7%  (Page trailing icon well)
    val veilSoft = Color(0x0FFFFFFF)    // = White 6%  (switch track, shimmer base)
    val veilWell = Color(0x1AFFFFFF)    // = White 10% (SubPage back-button well)
    val veilStrong = Color(0x2EFFFFFF)  // = White 18% (nav selected icon circle)
    val veilLine = Color(0x1FFFFFFF)    // = White 12% (activity-panel hairlines)
}

/** Dawn — the warm cream light theme (REDESIGN.md §4.1, Phase 2). Same hue
 * family, inverted value scale; every ratio documented in ContrastTest. */
internal object DawnPalette {
    val night = Color(0xFFECEEFB)       // the page ground (--cream)
    val nightMid = Color(0xFFDDDBF0)    // backdrop gradient top
    val nightPurple = Color(0xFFE4E2F4) // fields and secondary surfaces
    val textPrimary = Color(0xFF1C1740) // Ink (on bg 14.60:1, on CardFill 15.90:1)
    val textSoft = Color(0xFF37325E)    // on bg 10.22:1, on NightMid 8.69:1
    val textMuted = Color(0xFF4A4570)   // on bg 7.65:1, on NightMid 6.51:1
    val textMuted2 = Color(0xFF5C5684)  // on bg 5.83:1, on NightMid 4.96:1, on chip 5.28:1
    val cardFill = Color(0xFFF7F8FE)    // raised paper card
    val lineStroke = Color(0xFFC9C6E4)
    val eyebrowMuted = Color(0xFF5C5684)   // on bg 5.83:1, on CardFill 6.35:1
    val buttonDisabled = Color(0xFFB9B6CE) // Ink on it 8.54:1
    val fieldFill = Color(0xFFFFFFFF)
    val chipFill = Color(0xFFE4E2F4)
    val navPillTop = Color(0xFFF7F8FE)
    val navPillBottom = Color(0xFFE8E6F7)
    val navScrim = Color(0xFFECEEFB)
    // Accents darkened to survive as TEXT on the cream grounds (gated on the
    // darkest page paint, NightMid 0xFFDDDBF0, and on CardFill/ChipFill):
    val periwinkle = Color(0xFF5545AD)  // on NightMid 5.44:1, on chip 5.80:1
    val cyan = Color(0xFF0B6875)        // on NightMid 4.76:1, on chip 5.07:1
    val warm = Color(0xFF964527)        // on NightMid 4.87:1, on chip 5.19:1
    val ok = Color(0xFF256B4A)          // on NightMid 4.71:1, on chip 5.02:1
    val danger = Color(0xFF993F55)      // on NightMid 4.84:1; bg-on-it (DangerButton) 5.69:1
    // Component tokens.
    val onPrimary = Color(0xFFFFFFFF)              // white on the PeriwinkleDeep pill (7.39:1)
    val chipSelectedFill = Color(0xFF1C1740)       // selected chip inverts to an Ink pill
    val chipSelectedInk = Color(0xFFFFFFFF)        // white label on it (16.85:1)
    val switchThumbOn = Color(0xFFFFFFFF)          // white thumb on the deep-periwinkle track
    val textBright = Color(0xFF1C1740)             // brightest chrome text is Ink on Dawn
    val navSelectedHi = Color(0x4D5545AD)          // periwinkle wash 30% (Ink label ≈10:1 blended)
    val navSelectedLo = Color(0x145545AD)          // periwinkle wash 8%
    // Veils flip to soft ink so wells/tracks stay visible on cream.
    val veil = Color(0x0F1C1740)        // Ink 6%
    val veilSoft = Color(0x0D1C1740)    // Ink 5%
    val veilWell = Color(0x141C1740)    // Ink 8%
    val veilStrong = Color(0x1A1C1740)  // Ink 10%
    val veilLine = Color(0x1F1C1740)    // Ink 12%
}

// ── Themed tokens (resolve per theme on every read) ─────────────────────────
val Night: Color get() = if (AppTheme.isNight) NightPalette.night else DawnPalette.night
val NightMid: Color get() = if (AppTheme.isNight) NightPalette.nightMid else DawnPalette.nightMid
val NightPurple: Color get() = if (AppTheme.isNight) NightPalette.nightPurple else DawnPalette.nightPurple
val TextPrimary: Color get() = if (AppTheme.isNight) NightPalette.textPrimary else DawnPalette.textPrimary
val TextSoft: Color get() = if (AppTheme.isNight) NightPalette.textSoft else DawnPalette.textSoft
val TextMuted: Color get() = if (AppTheme.isNight) NightPalette.textMuted else DawnPalette.textMuted
val TextMuted2: Color get() = if (AppTheme.isNight) NightPalette.textMuted2 else DawnPalette.textMuted2
val CardFill: Color get() = if (AppTheme.isNight) NightPalette.cardFill else DawnPalette.cardFill
val LineStroke: Color get() = if (AppTheme.isNight) NightPalette.lineStroke else DawnPalette.lineStroke
val EyebrowMuted: Color get() = if (AppTheme.isNight) NightPalette.eyebrowMuted else DawnPalette.eyebrowMuted
val ButtonDisabled: Color get() = if (AppTheme.isNight) NightPalette.buttonDisabled else DawnPalette.buttonDisabled
val FieldFill: Color get() = if (AppTheme.isNight) NightPalette.fieldFill else DawnPalette.fieldFill
val ChipFill: Color get() = if (AppTheme.isNight) NightPalette.chipFill else DawnPalette.chipFill
val NavPillTop: Color get() = if (AppTheme.isNight) NightPalette.navPillTop else DawnPalette.navPillTop
val NavPillBottom: Color get() = if (AppTheme.isNight) NightPalette.navPillBottom else DawnPalette.navPillBottom
val NavScrim: Color get() = if (AppTheme.isNight) NightPalette.navScrim else DawnPalette.navScrim

// Accents. These are used as *text* all over the signed-in app ("Try another",
// "PREMIUM", eyebrows, error copy), so the ones that appear as text carry a
// darker Dawn variant that passes 4.5:1 on the cream grounds. Purely
// decorative accents (Teal/Iris/Violet/PeriwinkleDeep/PeriwinkleSoft — orb
// art, thumbnail gradients, aurora tints) stay single-valued below.
val Periwinkle: Color get() = if (AppTheme.isNight) NightPalette.periwinkle else DawnPalette.periwinkle
val Cyan: Color get() = if (AppTheme.isNight) NightPalette.cyan else DawnPalette.cyan
val Warm: Color get() = if (AppTheme.isNight) NightPalette.warm else DawnPalette.warm
val Ok: Color get() = if (AppTheme.isNight) NightPalette.ok else DawnPalette.ok
val Danger: Color get() = if (AppTheme.isNight) NightPalette.danger else DawnPalette.danger

// Component tokens introduced by the Dawn pass (Night values byte-identical to
// the literals the components used before — see NightPalette).
/** PrimaryButton label — Ink on the Night white pill, white on the Dawn deep-periwinkle pill. */
val OnPrimary: Color get() = if (AppTheme.isNight) NightPalette.onPrimary else DawnPalette.onPrimary
val ChipSelectedFill: Color get() = if (AppTheme.isNight) NightPalette.chipSelectedFill else DawnPalette.chipSelectedFill
val ChipSelectedInk: Color get() = if (AppTheme.isNight) NightPalette.chipSelectedInk else DawnPalette.chipSelectedInk
val SwitchThumbOn: Color get() = if (AppTheme.isNight) NightPalette.switchThumbOn else DawnPalette.switchThumbOn
/** Brightest chrome text (SubPage titles, ContentRow titles) — pure white on Night, Ink on Dawn. */
val TextBright: Color get() = if (AppTheme.isNight) NightPalette.textBright else DawnPalette.textBright
/** Bottom-nav selected-cell radial stops (Periwinkle wash tuned per theme). */
val NavSelectedHi: Color get() = if (AppTheme.isNight) NightPalette.navSelectedHi else DawnPalette.navSelectedHi
val NavSelectedLo: Color get() = if (AppTheme.isNight) NightPalette.navSelectedLo else DawnPalette.navSelectedLo

// Structural veils — the soft white-on-Night wells/tracks/hairlines that would
// vanish on cream; they flip to soft ink on Dawn.
val Veil: Color get() = if (AppTheme.isNight) NightPalette.veil else DawnPalette.veil
val VeilSoft: Color get() = if (AppTheme.isNight) NightPalette.veilSoft else DawnPalette.veilSoft
val VeilWell: Color get() = if (AppTheme.isNight) NightPalette.veilWell else DawnPalette.veilWell
val VeilStrong: Color get() = if (AppTheme.isNight) NightPalette.veilStrong else DawnPalette.veilStrong
val VeilLine: Color get() = if (AppTheme.isNight) NightPalette.veilLine else DawnPalette.veilLine

// ── Theme-independent colors ────────────────────────────────────────────────
// Decorative accents: never used as text on themed surfaces (verified 2026-07:
// orb/lotus art, thumbnail gradients, aurora tints, title-glow shadows only).
val PeriwinkleDeep = Color(0xFF5545AD)
val PeriwinkleSoft = Color(0xFFC5BDF3)
val Iris = Color(0xFF9A87F5)
val Violet = Color(0xFF7665D4)
val Teal = Color(0xFF6FE0E6)        // --teal (lotus / breathe accent, matches iOS)

// Cream/Ink stay constants: every Cream consumer is light text/fills on
// always-dark art (GradientHero panels, HeroCard photo scrims, game tiles, the
// Sleep tab which is force-Night) and every Ink consumer is dark ink on
// light/white art (breathe-orb count, celebration check, funnel pills) — all
// grounds that do not change with the theme (verified per-usage, 2026-07).
val Cream = Color(0xFFECEEFB)       // --cream
val Ink = Color(0xFF1C1740)         // --ink

// Constants for text/scrims over always-dark art (photo heroes, gradient game
// tiles): these panels keep their night art in both themes, so their overlay
// colors must NOT follow the theme. Values = the Night-theme backdrop floor
// and TextSoft, so Night renders identically.
val ArtScrim = Color(0xFF100D2B)
val ArtTextSoft = Color(0xFFE1DEEE)

// List-thumbnail gradient floors (UI chrome for content-row artwork). The tops
// reuse the brand accents (Periwinkle/Cyan/Warm/Iris); these are the darker
// gradient partners that don't map to an existing palette token.
val ThumbBlue = Color(0xFF5B8FD0)   // cyan thumbnail floor
val ThumbRose = Color(0xFFB86B8F)   // coral thumbnail floor
val ThumbIndigo = Color(0xFF6F7BF7) // iris thumbnail floor

// ---------------------------------------------------------------------------
// Semantic roles (REDESIGN.md §4.2) — screens should prefer these over the raw
// palette constants above. They are aliases of the themed getters, so both
// themes flow through them. Every text role is contrast-gated ≥ 4.5:1 on its
// surfaces in both themes (ContrastTest).
// ---------------------------------------------------------------------------
val Surface: Color get() = CardFill             // resting card fill
val SurfaceRaised: Color get() = ChipFill       // lifted fill (chips, glass-card top edge)
val SurfaceField: Color get() = FieldFill       // text-field container
val Line: Color get() = LineStroke              // hairline dividers/strokes
val TextSecondary: Color get() = TextSoft       // supporting copy
val TextFaint: Color get() = TextMuted2         // faintest legal text (≥4.5:1 everywhere)
val AccentSoft: Color get() = PeriwinkleSoft    // soft accent (tints, selected-state washes)

// Onboarding / Auth surface tokens (bespoke funnel art — promoted from inline
// hex). The signed-out funnel is ALWAYS Night (AppTheme.forceNight), so these
// stay single-valued constants.
val GratitudeCardFill = Color(0xFF493453)
val GratitudeAvatarFill = Color(0xFF5A547F)
val GratitudeCaption = Color(0xFFC9C5DA)
val InfoCardFill = Color(0xFF302D54)
val InfoCardStroke = Color(0xFF514C73)
val InfoCardHint = Color(0xFFCBC7D8)
val InfoCardDivider = Color(0xFF464166)
val WelcomeGradientTop = Color(0xFF3B3474)
val WelcomeGradientBottom = Color(0xFF12102F)
val WelcomeTitleText = Color(0xFFD8D5E5)
val WelcomeSubtitleText = Color(0xFFBDB8D0)
val WelcomeSecondaryText = Color(0xFFE4E1EC)
val WelcomeOrbMid = Color(0xFFF4F1FF)
val WelcomeOrbEdge = Color(0xFFC9C3FF)
val PrimaryButtonFill = Color(0xFFFCFBFF)
val PrimaryButtonInk = Color(0xFF211C50)
val PrimaryButtonDisabledFill = Color(0xFF9998A7)
val ResetDoneFill = Color(0xFF302D50)
val FunnelHeaderTop = Color(0xFF393270)
val FunnelHeaderBottom = Color(0xFF11102E)
val FunnelBodyText = Color(0xFFD0CCDE)
val ProgressTrack = Color(0xFF484361)
val PickRowSelectedFill = Color(0xFF4A456F)
val PickRowFill = Color(0xFF302C56)
val PickRowStroke = Color(0xFF504B74)
val PickRowChevron = Color(0xFF9993B4)
val PickCardStroke = Color(0xFF575178)
val DotUnselectedFill = Color(0xFF3B3766)
val AuthEyebrow = Color(0xFFB5AEE1)
val AuthFieldLabel = Color(0xFFE0DDEE)
