package com.cerebrozen.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── CereBro palette — "Serene" (2026-07-13 premium redesign) ────────────────
//
// Brand direction (owner-specified):
//   Primary   Soft Lavender   #7C6FF0
//   Secondary Soft Sky Blue   #6ECBF5
//   Accent    Mint Green      #7ED9B6
//   Background Warm White     #FAFAFC     Surface  White  #FFFFFF
//   Dark      Elegant Navy    #0F172A
//
// Those five are the *brand fills* and appear verbatim below (see [BrandPrimary],
// [BrandSecondary], [BrandAccent]) on every non-text surface: orbs, gradients,
// charts, progress bars, selection washes, generative art.
//
// They cannot all be used as TEXT, and this app uses accents as text constantly
// ("Try another", "PREMIUM", eyebrows, error copy, milestone lines). Measured on
// the Warm White ground: Sky 1.75:1 and Mint 1.61:1 — far below the 4.5:1 WCAG AA
// floor; Lavender 3.74:1. So each accent carries a *text-safe sibling of the same
// hue* per theme — darkened on Warm White, lightened on Navy. This is the same
// two-tier idea Material 3 uses (`primary` vs `onPrimaryContainer`), and the same
// one the previous palette used.
//
// Every pairing below is machine-verified ≥ 4.5:1 in BOTH themes by ContrastTest,
// which runs on the real tokens — so a palette tweak that breaks legibility fails
// the build instead of shipping. Ratios in the comments are measured, not guessed.

/** Dark — Elegant Navy. */
internal object NightPalette {
    val night = Color(0xFF0F172A)       // page floor (owner spec)
    val nightMid = Color(0xFF1E2A47)    // backdrop gradient top (violet-lifted navy)
    val nightPurple = Color(0xFF1A2439) // fields and secondary surfaces
    val cardFill = Color(0xFF1B2438)    // resting card
    val chipFill = Color(0xFF26324A)    // raised: chips, glass top edge
    val fieldFill = Color(0xFF1B2438)
    val lineStroke = Color(0xFF35425C)
    // Text ladder — ratios on bg / card / raised:
    val textPrimary = Color(0xFFF2F5FA) // 16.34 · 14.17 · 11.74
    val textSoft = Color(0xFFDCE3EE)    // 13.83 · 11.99 ·  9.93
    val textMuted = Color(0xFFB4BFD0)   //  9.61 ·  8.34 ·  6.90
    val textMuted2 = Color(0xFFA1ADC1)  //  7.87 ·  6.83 ·  5.66  (faintest legal text)
    val eyebrowMuted = Color(0xFFA9B6CB) //  8.71 ·  7.55 ·  6.25
    val buttonDisabled = Color(0xFFB9BCCB)
    // Accents AS TEXT on navy. Lavender is lifted #7C6FF0 → #A79BF7: the brand
    // hue itself only reaches 3.29:1 on a raised navy card, so it stays a fill.
    val periwinkle = Color(0xFFA79BF7)  //  7.37 ·  6.40 ·  5.30
    val cyan = Color(0xFF6ECBF5)        //  9.80 ·  8.50 ·  7.04  (brand Sky, works as-is)
    val ok = Color(0xFF7ED9B6)          // 10.62 ·  9.21 ·  7.63  (brand Mint, works as-is)
    val warm = Color(0xFFF5A98F)        //  9.31 ·  8.08 ·  6.69
    val danger = Color(0xFFF08D9E)      //  7.66 ·  6.64 ·  5.50
    // Component tokens.
    val onPrimary = Color(0xFFFFFFFF)          // white on the deep-lavender pill
    val chipSelectedFill = Color(0xFFFFFFFF)   // selected chip = white pill
    val chipSelectedInk = Color(0xFF1A1830)    // …with ink label (17.25:1)
    val switchThumbOn = Color(0xFFFFFFFF)
    val textBright = Color(0xFFFFFFFF)
    val navPillTop = Color(0xFF243150)
    val navPillBottom = Color(0xFF161F35)
    val navScrim = Color(0xFF0F172A)
    val navSelectedHi = Color(0xB87C6FF0)      // brand Lavender @72%
    val navSelectedLo = Color(0x2E7C6FF0)      // brand Lavender @18%
    // Structural veils — soft white wells/tracks/hairlines.
    val veil = Color(0x12FFFFFF)
    val veilSoft = Color(0x0FFFFFFF)
    val veilWell = Color(0x1AFFFFFF)
    val veilStrong = Color(0x2EFFFFFF)
    val veilLine = Color(0x1FFFFFFF)
}

/** Light — Warm White. */
internal object DawnPalette {
    val night = Color(0xFFFAFAFC)       // page ground (owner spec)
    val nightMid = Color(0xFFECEDF5)    // darkest page paint — worst case for dark text
    val nightPurple = Color(0xFFF0F1F8)
    val cardFill = Color(0xFFFFFFFF)    // Surface = White (owner spec)
    val chipFill = Color(0xFFEFF0F7)
    val fieldFill = Color(0xFFFFFFFF)
    val lineStroke = Color(0xFFDFE1EC)
    // Text ladder — ratios on bg / bgTop / card / raised:
    val textPrimary = Color(0xFF1A1830) // 16.55 · 14.79 · 17.25 · 15.18
    val textSoft = Color(0xFF45426B)    //  8.97 ·  8.02 ·  9.36 ·  8.23
    val textMuted = Color(0xFF5A5680)   //  6.55 ·  5.85 ·  6.82 ·  6.00
    val textMuted2 = Color(0xFF655F8A)  //  5.66 ·  5.06 ·  5.90 ·  5.19
    val eyebrowMuted = Color(0xFF655F8A)
    val buttonDisabled = Color(0xFFB9BCCB)
    // Accents AS TEXT on warm white — same hues, darkened until each clears AA.
    val periwinkle = Color(0xFF5B4BC4)  //  6.20 ·  5.54 ·  6.46 ·  5.69
    val cyan = Color(0xFF0E6E8C)        //  5.55 ·  4.96 ·  5.79 ·  5.09
    val ok = Color(0xFF1E7A5C)          //  5.04 ·  4.51 ·  5.26 ·  4.63
    val warm = Color(0xFF9C4A2C)        //  5.87 ·  5.25 ·  6.12 ·  5.39
    val danger = Color(0xFFA63F57)      //  5.81 ·  5.19 ·  6.05 ·  5.32
    // Component tokens.
    val onPrimary = Color(0xFFFFFFFF)
    val chipSelectedFill = Color(0xFF1A1830)   // selected chip inverts to an ink pill
    val chipSelectedInk = Color(0xFFFFFFFF)    // …with a white label (17.25:1)
    val switchThumbOn = Color(0xFFFFFFFF)
    val textBright = Color(0xFF1A1830)
    val navPillTop = Color(0xFFFFFFFF)
    val navPillBottom = Color(0xFFF1F2F9)
    val navScrim = Color(0xFFFAFAFC)
    val navSelectedHi = Color(0x4D7C6FF0)
    val navSelectedLo = Color(0x147C6FF0)
    // Veils flip to soft ink so wells/tracks stay visible on warm white.
    val veil = Color(0x0F1A1830)
    val veilSoft = Color(0x0D1A1830)
    val veilWell = Color(0x141A1830)
    val veilStrong = Color(0x1A1A1830)
    val veilLine = Color(0x1F1A1830)
}

// ── Brand fills — theme-independent, NEVER text ─────────────────────────────
// The owner palette verbatim. Safe anywhere colour is decoration, not language:
// orbs, gradients, charts, progress fills, selection washes, generative art.
val BrandPrimary = Color(0xFF7C6FF0)    // Soft Lavender
val BrandSecondary = Color(0xFF6ECBF5)  // Soft Sky Blue
val BrandAccent = Color(0xFF7ED9B6)     // Mint Green

/** The one deep-lavender the primary pill wears in BOTH themes (white label:
 * 4.72:1 on the top stop, 6.46:1 on the floor). Brand Lavender itself only makes
 * 3.90:1 under white, so the CTA deepens rather than the label darkening. */
val LavenderPillTop = Color(0xFF6D5FE8)
val LavenderPillFloor = Color(0xFF5B4BC4)

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

// Accents — the text-safe siblings. Use [BrandPrimary]/[BrandSecondary]/
// [BrandAccent] instead when painting a fill rather than writing a word.
val Periwinkle: Color get() = if (AppTheme.isNight) NightPalette.periwinkle else DawnPalette.periwinkle
val Cyan: Color get() = if (AppTheme.isNight) NightPalette.cyan else DawnPalette.cyan
val Warm: Color get() = if (AppTheme.isNight) NightPalette.warm else DawnPalette.warm
val Ok: Color get() = if (AppTheme.isNight) NightPalette.ok else DawnPalette.ok
val Danger: Color get() = if (AppTheme.isNight) NightPalette.danger else DawnPalette.danger

val OnPrimary: Color get() = if (AppTheme.isNight) NightPalette.onPrimary else DawnPalette.onPrimary
val ChipSelectedFill: Color get() = if (AppTheme.isNight) NightPalette.chipSelectedFill else DawnPalette.chipSelectedFill
val ChipSelectedInk: Color get() = if (AppTheme.isNight) NightPalette.chipSelectedInk else DawnPalette.chipSelectedInk
val SwitchThumbOn: Color get() = if (AppTheme.isNight) NightPalette.switchThumbOn else DawnPalette.switchThumbOn
val TextBright: Color get() = if (AppTheme.isNight) NightPalette.textBright else DawnPalette.textBright
val NavSelectedHi: Color get() = if (AppTheme.isNight) NightPalette.navSelectedHi else DawnPalette.navSelectedHi
val NavSelectedLo: Color get() = if (AppTheme.isNight) NightPalette.navSelectedLo else DawnPalette.navSelectedLo

val Veil: Color get() = if (AppTheme.isNight) NightPalette.veil else DawnPalette.veil
val VeilSoft: Color get() = if (AppTheme.isNight) NightPalette.veilSoft else DawnPalette.veilSoft
val VeilWell: Color get() = if (AppTheme.isNight) NightPalette.veilWell else DawnPalette.veilWell
val VeilStrong: Color get() = if (AppTheme.isNight) NightPalette.veilStrong else DawnPalette.veilStrong
val VeilLine: Color get() = if (AppTheme.isNight) NightPalette.veilLine else DawnPalette.veilLine

// ── Decorative constants (fills only, never text) ───────────────────────────
val PeriwinkleDeep = LavenderPillFloor
val PeriwinkleSoft = Color(0xFFC7C1F8)   // soft lavender wash / on-art eyebrow
val Iris = Color(0xFF9A8FF4)
val Violet = Color(0xFF6D5FE8)
val Teal = BrandAccent                    // mint is the breathe/lotus accent

// Cream/Ink: light text on always-dark art, dark ink on always-light art.
val Cream = Color(0xFFF2F5FA)
val Ink = Color(0xFF1A1830)

// Text/scrims over always-dark art (photo heroes, gradient game tiles): these
// panels keep their night art in BOTH themes, so their overlay colours must not
// follow the theme.
val ArtScrim = Color(0xFF0F172A)
val ArtTextSoft = Color(0xFFDCE3EE)

// List-thumbnail gradient floors — the darker partners for the art gradients.
val ThumbBlue = Color(0xFF3E8FC4)
val ThumbRose = Color(0xFFC4738F)
val ThumbIndigo = Color(0xFF6257DA)

// Generative-artwork accents (ContentArt.kt). Constants, not themed getters:
// content art keeps its deep navy base in both themes, so its hues must not
// follow the theme. These are the brand fills.
val ArtPeriwinkle = BrandPrimary
val ArtCyan = BrandSecondary
val ArtWarm = Color(0xFFF5A98F)

// ── Semantic roles — screens should prefer these over the raw palette ───────
val Surface: Color get() = CardFill              // resting card fill
val SurfaceRaised: Color get() = ChipFill        // lifted fill (chips, glass top edge)
val SurfaceField: Color get() = FieldFill        // text-field container
val Line: Color get() = LineStroke               // hairline dividers/strokes
val TextSecondary: Color get() = TextSoft        // supporting copy
val TextFaint: Color get() = TextMuted2          // faintest legal text (≥4.5:1 everywhere)
val AccentSoft: Color get() = PeriwinkleSoft     // soft accent (tints, selection washes)

// ── Signed-out funnel surfaces (always Night — bespoke navy art) ────────────
val GratitudeCardFill = Color(0xFF2A3A55)
val GratitudeAvatarFill = Color(0xFF3D4E6B)
val GratitudeCaption = Color(0xFFC2CCDA)
val InfoCardFill = Color(0xFF1B2438)
val InfoCardStroke = Color(0xFF35425C)
val InfoCardHint = Color(0xFFC4CDDA)
val InfoCardDivider = Color(0xFF2C3950)
val WelcomeGradientTop = Color(0xFF233051)
val WelcomeGradientBottom = Color(0xFF0F172A)
val WelcomeTitleText = Color(0xFFE8ECF4)
val WelcomeSubtitleText = Color(0xFFC4CDDA)
val WelcomeSecondaryText = Color(0xFFE8ECF4)
val WelcomeOrbMid = Color(0xFFEFEDFF)
val WelcomeOrbEdge = Color(0xFFB6ACF7)
val PrimaryButtonFill = Color(0xFF6D5FE8)
val PrimaryButtonInk = Color(0xFFFFFFFF)
val PrimaryButtonDisabledFill = Color(0xFF6B7183)
val ResetDoneFill = Color(0xFF1B2438)
val FunnelHeaderTop = Color(0xFF233051)
val FunnelHeaderBottom = Color(0xFF0F172A)
val FunnelBodyText = Color(0xFFC4CDDA)
val ProgressTrack = Color(0xFF35425C)
val PickRowSelectedFill = Color(0xFF2F3E5C)
val PickRowFill = Color(0xFF1B2438)
val PickRowStroke = Color(0xFF35425C)
val PickRowChevron = Color(0xFF8E9BB0)
val PickCardStroke = Color(0xFF3D4A66)
val DotUnselectedFill = Color(0xFF26324A)
val AuthEyebrow = Color(0xFFB6ACF7)
val AuthFieldLabel = Color(0xFFDCE3EE)
