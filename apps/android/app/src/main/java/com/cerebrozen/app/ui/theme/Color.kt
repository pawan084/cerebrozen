package com.cerebrozen.app.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette. The canonical values live in `design/tokens.css` (the shared
// role scale for web/admin/app) and are mirrored here by hand — change them in
// the same commit, per the cross-stack contract table in docs/ARCHITECTURE.md.
//
// ── Light Dawn (docs/REDESIGN_V2.md §2, owner ruling §6) ────────────────────
// The 2026-08 port inverts the system. The product spec names a "Light Dawn
// visual system" with an "optional dark appearance later", where this app was
// built night-first on deep indigo, and it replaces the accent family:
// lavender/periwinkle becomes **plum**, with sage, rose and amber as the tonal
// set.
//
// Structure is unchanged on purpose: every themed token below is still a
// `get()` property reading [AppTheme.isNight] (snapshot state), so screens keep
// importing the same names. What changed is which palette is the default and
// what the two palettes contain:
//
//   * [DawnPalette] is now the canonical Light Dawn scale and the DEFAULT
//     appearance (AppTheme.systemDark starts false).
//   * [NightPalette] is re-toned from indigo to the plum night values. It is
//     the opt-in dark appearance — still a real theme, not a legacy shim.
//
// Every value marked "canonical" below is byte-identical to `design/tokens.css`.
// Values NOT in that file (component roles this client has and the web does
// not — nav pill, disabled fill, veils, funnel art) are derived from canonical
// roles and named as such. Every text/fill pairing is contrast-gated ≥ 4.5:1 in
// ContrastTest, in BOTH themes.
//
// One deliberate collapse: the web scale has three text roles and Android has
// four (textPrimary/textSoft/textMuted/textMuted2) plus an eyebrow. The extra
// tiers map onto the canonical faint role rather than inventing off-scale
// values, so `TextMuted`, `TextMuted2`/`TextFaint` and `EyebrowMuted` all
// resolve to `--text-faint`. Three honest tiers beat five drifting ones.

/** Night — the opt-in dark appearance, re-toned to plum (tokens.css
 * `:root[data-theme="night"]`). Every role marked canonical is verbatim. */
internal object NightPalette {
    val night = Color(0xFF171019)       // canonical --surface (page ground)
    val nightMid = Color(0xFF302237)    // canonical --surface-field (backdrop gradient top)
    val nightPurple = Color(0xFF302237) // canonical --surface-field
    val textPrimary = Color(0xFFFAF5FB) // canonical --text
    val textSoft = Color(0xFFDED4E0)    // canonical --text-secondary
    val textMuted = Color(0xFFB9ABB9)   // canonical --text-faint
    val textMuted2 = Color(0xFFB9ABB9)  // canonical --text-faint (faintest legal text)
    val cardFill = Color(0xFF241927)    // canonical --surface-raised
    val lineStroke = Color(0xFF3E3043)  // canonical --line
    val eyebrowMuted = Color(0xFFB9ABB9)   // canonical --text-faint
    // Derived: the disabled primary-button fill has to carry [Ink] as its label
    // (PrimaryButton draws Ink when disabled in both themes), so on Night it is
    // a light plum-grey rather than a dark one. Ink on it: 5.24:1.
    val buttonDisabled = Color(0xFF9A8C9C)
    val fieldFill = Color(0xFF302237)      // canonical --surface-field
    val chipFill = Color(0xFF302237)       // canonical --surface-field
    // Floating bottom-nav pill — derived from the elevation ladder: a raised
    // capsule (field → raised) over the page ground.
    val navPillTop = Color(0xFF302237)
    val navPillBottom = Color(0xFF241927)
    val navScrim = Color(0xFF171019)
    // Accents — canonical. `periwinkle` keeps its historical NAME (it is read by
    // ~200 call sites) but is now the plum --accent; `cyan` is --info.
    val periwinkle = Color(0xFFD9ACDE)  // canonical --accent
    val accent2 = Color(0xFFC580B5)     // canonical --accent-2
    val accentSoft = Color(0xFF3A2A3E)  // canonical --accent-soft
    val onAccent = Color(0xFF241927)    // canonical --on-accent
    val cyan = Color(0xFF9CC4DC)        // canonical --info
    val infoSoft = Color(0xFF22323C)    // canonical --info-soft
    val warm = Color(0xFFF29AB0)        // canonical --warm
    val warmSoft = Color(0xFF351E25)    // canonical --warm-soft
    val ok = Color(0xFFAFD6B2)          // canonical --ok
    val okSoft = Color(0xFF1E2A20)      // canonical --ok-soft
    val danger = Color(0xFFFF8C82)      // canonical --danger
    val dangerSoft = Color(0xFF3A211E)  // canonical --danger-soft
    val amber = Color(0xFFF0C37F)       // canonical --amber
    val amberSoft = Color(0xFF453622)   // canonical --amber-soft
    // Component roles, all derived from the canonical roles above.
    val onPrimary = Color(0xFF241927)              // = --on-accent: label on the accent pill
    val chipSelectedFill = Color(0xFFD9ACDE)       // selected PickChip = an accent pill
    val chipSelectedInk = Color(0xFF241927)        // its label (--on-accent), 8.77:1
    val switchThumbOn = Color(0xFF241927)          // checked AppSwitch thumb (--on-accent)
    val textBright = Color(0xFFFAF5FB)             // brightest chrome text (--text)
    // Selected nav cell: an accent wash quiet enough that the near-white label
    // on top keeps its ratio (the label is TextPrimary, not --on-accent).
    val navSelectedHi = Color(0x4DD9ACDE)          // accent 30%
    val navSelectedLo = Color(0x14D9ACDE)          // accent 8%
    // Structural veils (soft wells/tracks/hairlines) — light on a dark ground.
    val veil = Color(0x12FFFFFF)        // White 7%  (Page trailing icon well)
    val veilSoft = Color(0x0FFFFFFF)    // White 6%  (switch track, shimmer base)
    val veilWell = Color(0x1AFFFFFF)    // White 10% (SubPage back-button well)
    val veilStrong = Color(0x2EFFFFFF)  // White 18% (nav selected icon circle)
    val veilLine = Color(0x1FFFFFFF)    // White 12% (activity-panel hairlines)
}

/** Light Dawn — the canonical default appearance (tokens.css `:root`).
 *
 * Warm ivory grounds, plum accent, and one elevation ladder of honest
 * soft-solids: `--surface` is the page, `--surface-raised` the card,
 * `--surface-field` the well. Card-versus-ground contrast is ~1.1:1 in any
 * light theme, so depth is carried by shadow (see [CardShadow]) and by the
 * hairline, never by the fill. */
internal object DawnPalette {
    // CANONICAL — every value below is `design/tokens.css :root`, byte for byte.
    //
    // This used to be taken "byte-for-byte with the Light Dawn phone in
    // mobile.html", a DIFFERENT source from tokens.css, and the two disagree on
    // every neutral: mobile.html's ink is indigo (#1C1740), the canonical text
    // role is a warm near-black (#211D20). The result was an Android Dawn that
    // matched no other client, with nothing to catch it — sync-tokens.mjs gates
    // the four globals.css files and cannot read Kotlin, and ContrastTest pinned
    // these very values while calling itself "the mirror of tokens.css :root".
    //
    // Screen authors noticed even if the tooling did not: the light-dawn screens
    // are full of raw hex like #F3ECF3 (== --surface-field exactly) and #292323
    // (≈ --text), written by hand because the tokens did not carry them.
    //
    // REDESIGN_V2 §6: "follow ref/ strictly … where the spec and this repo
    // disagree, the spec wins", and §2 adopts --text/--text-secondary/--accent/
    // --accent-2/--ok/--info exactly as specified. tokens.css is that spec.
    val night = Color(0xFFF8F4EE)       // --surface
    val nightMid = Color(0xFFF3ECF3)    // --surface-field
    val nightPurple = Color(0xFFF3ECF3) // --surface-field
    val textPrimary = Color(0xFF211D20) // --text
    val textSoft = Color(0xFF514A50)    // --text-secondary
    val textMuted = Color(0xFF686267)   // --text-faint
    val textMuted2 = Color(0xFF686267)  // --text-faint
    val cardFill = Color(0xFFFFFCF8)    // --surface-raised
    val lineStroke = Color(0xFFDFD9D3)  // --line
    val eyebrowMuted = Color(0xFF315C7A) // --info (the eyebrow role on light)
    val buttonDisabled = Color(0xFFDFD9D3) // --line
    val fieldFill = Color(0xFFF3ECF3)   // --surface-field
    val chipFill = Color(0xFFF3ECF3)    // --surface-field
    val navPillTop = Color(0xFFFFFCF8)  // --surface-raised
    val navPillBottom = Color(0xFFFFFCF8)
    val navScrim = Color(0xFFF8F4EE)    // --surface
    // The plum accent, not the old indigo. tokens.css darkened --warm/--danger/
    // --amber/--text-faint specifically so every role clears 4.5:1 on all three
    // grounds — the check-contrast.mjs gate proves it for these exact values,
    // and ContrastTest re-proves it here.
    val periwinkle = Color(0xFF5A2B5C)  // --accent
    val accent2 = Color(0xFF8A4A78)     // --accent-2
    val accentSoft = Color(0xFFE9DDEA)  // --accent-soft
    val onAccent = Color(0xFFFFFCF8)    // --on-accent
    val cyan = Color(0xFF315C7A)        // --info
    val infoSoft = Color(0xFFE7F0F5)    // --info-soft
    val warm = Color(0xFFA45161)        // --warm
    val warmSoft = Color(0xFFFAE9EA)    // --warm-soft
    val ok = Color(0xFF49634F)          // --ok
    val okSoft = Color(0xFFE5EDE3)      // --ok-soft
    val danger = Color(0xFFC23A33)      // --danger
    val dangerSoft = Color(0xFFFFE9E6)  // --danger-soft
    val amber = Color(0xFF92611D)       // --amber
    val amberSoft = Color(0xFFFFF1D8)   // --amber-soft
    // Component roles.
    val onPrimary = Color(0xFFFFFCF8)   // --on-accent
    val chipSelectedFill = Color(0xFF5A2B5C) // --accent
    val chipSelectedInk = Color(0xFFFFFCF8)  // --on-accent
    val switchThumbOn = Color(0xFFFFFCF8)   // --on-accent
    val textBright = Color(0xFF211D20)      // == textPrimary (--text)
    // Nav selection wash, tinted from the accent rather than the retired indigo.
    val navSelectedHi = Color(0x335A2B5C)
    val navSelectedLo = Color(0x145A2B5C)
    // Veils are the ink at low alpha, so wells/tracks stay visible on ivory —
    // they follow --text, which is why these carry the canonical warm value.
    val veil = Color(0x0F211D20)
    val veilSoft = Color(0x0D211D20)
    val veilWell = Color(0x14211D20)
    val veilStrong = Color(0x1A211D20)
    val veilLine = Color(0x1F211D20)
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
// "PREMIUM", eyebrows, error copy), so every one of them clears 4.5:1 on all
// three neutral grounds AND on its own `-soft` wash, in both themes
// (ContrastTest). Purely decorative hues stay single-valued further down.
val Periwinkle: Color get() = if (AppTheme.isNight) NightPalette.periwinkle else DawnPalette.periwinkle
val Accent2: Color get() = if (AppTheme.isNight) NightPalette.accent2 else DawnPalette.accent2
/** The ink that reads on an [Periwinkle]/[Accent2] fill (`--on-accent`). */
val OnAccent: Color get() = if (AppTheme.isNight) NightPalette.onAccent else DawnPalette.onAccent
val Cyan: Color get() = if (AppTheme.isNight) NightPalette.cyan else DawnPalette.cyan
val Info: Color get() = Cyan
val InfoSoft: Color get() = if (AppTheme.isNight) NightPalette.infoSoft else DawnPalette.infoSoft
val Warm: Color get() = if (AppTheme.isNight) NightPalette.warm else DawnPalette.warm
val WarmSoft: Color get() = if (AppTheme.isNight) NightPalette.warmSoft else DawnPalette.warmSoft
val Ok: Color get() = if (AppTheme.isNight) NightPalette.ok else DawnPalette.ok
val OkSoft: Color get() = if (AppTheme.isNight) NightPalette.okSoft else DawnPalette.okSoft
val Danger: Color get() = if (AppTheme.isNight) NightPalette.danger else DawnPalette.danger
val DangerSoft: Color get() = if (AppTheme.isNight) NightPalette.dangerSoft else DawnPalette.dangerSoft
val Amber: Color get() = if (AppTheme.isNight) NightPalette.amber else DawnPalette.amber
val AmberSoft: Color get() = if (AppTheme.isNight) NightPalette.amberSoft else DawnPalette.amberSoft

/** DangerButton's label over the [Danger] fill. It happens to resolve to the same
 * paint as the page ground in both themes — but that is a *foreground* role, not a
 * background one, so it gets its own name and its own contrast assertions
 * (Night 8.29:1, Dawn 4.86:1). Screens must never reach for [Night] as ink. */
val OnDanger: Color get() = if (AppTheme.isNight) NightPalette.night else DawnPalette.night

/**
 * Secondary text ON a [DangerSoft] panel.
 *
 * The crisis screen's "CereBro is not an emergency service and cannot monitor
 * your safety" was a raw `Color(0xFF542D34)` written into the screen file — a
 * dark maroon that lands 10.02:1 on Dawn's pale `dangerSoft` and **1.27:1** on
 * Night's dark one, so the disclaimer under the call-112 banner was invisible
 * in the theme most people use at night, on the most safety-critical screen in
 * the product. Dawn keeps exactly the colour it already had; Night gets one
 * that can be read (8.78:1). Found by walking all 58 routes on a CPH2681,
 * 2026-08-20 — a light-theme review passes this every time.
 */
/**
 * The Explore hero — the tappable box-breathing door.
 *
 * It was a three-stop gradient that MIXED constant art (a peach and a lavender
 * written as raw hex in the screen file) with `AccentSoft`, which flips. In
 * Night that put a dark stop between two light ones and left the eyebrow —
 * also a raw hex, a deep plum — sitting on a mid-tone wash it could not be
 * read against. Now it follows the same shape [MixerHeroTop] and
 * [SleepHeroTop] already use: every stop resolves per theme, and the eyebrow
 * is [Cyan] like the other hero eyebrows (10.44:1 Night, 5.44:1 Dawn).
 */
val ExploreHeroStart: Color get() = if (AppTheme.isNight) NightPalette.nightMid else Color(0xFFFFE2D6)
val ExploreHeroEnd: Color get() = if (AppTheme.isNight) NightPalette.night else Color(0xFFE1CDEA)
val ExploreHeroOrbCore: Color get() = if (AppTheme.isNight) NightPalette.cardFill else Color.White
val ExploreHeroOrbEdge: Color get() = if (AppTheme.isNight) NightPalette.nightMid else Color(0xFFD5B8E1)

/**
 * The Tele-MANAS card on the crisis screen — the one action that must read as
 * urgent before anything else on the page.
 *
 * Deliberately the SAME red in both themes: it is a brand-urgency fill, not a
 * surface, and the ink on it ([OnDanger]-ish white) is chosen against this red
 * rather than against the page. It is a token now only so that no screen file
 * has to carry a hex.
 */
val CrisisPrimaryFill: Color get() = Color(0xFFD93B36)

/**
 * A filled primary button with nothing to submit yet.
 *
 * Dawn's stop is the mid-lavender this used to be as a literal; Night's is the
 * field surface, because a light-lavender pill on a dark page reads as ENABLED
 * — the disabled state was louder than the live one.
 */
val DisabledFill: Color get() = if (AppTheme.isNight) NightPalette.fieldFill else DawnPalette.fieldFill

/** The label on [DisabledFill] — muted on purpose, and legible in both themes. */
val DisabledInk: Color get() = TextMuted

/**
 * A disabled label on an ordinary surface — "Previous" on step 1 of TIPP, of
 * the 5-4-3-2-1 grounding sequence.
 *
 * WCAG 1.4.3 exempts inactive components from the contrast minimum, so this is
 * not a compliance fix. It is a legibility one: at the old alphas these
 * measured **1.94:1** and **2.33:1** in Dawn, which is dim enough to read as
 * *absent* rather than as a control you cannot use yet — and a step counter
 * whose back button seems to vanish is a worse answer than one that is plainly
 * there and plainly unavailable.
 *
 * 0.7 puts it at 3.07:1 on Dawn and 4.45:1 on Night while the ENABLED label
 * sits at ~16:1, so the gap that says "not yet" is still about fivefold. Going
 * further would be the opposite mistake: a disabled control that looks live
 * earns a tap and gives nothing back.
 */
val DisabledTextInk: Color get() = TextMuted.copy(alpha = 0.7f)

val DangerSoftInk: Color get() =
    if (AppTheme.isNight) Color(0xFFCFC3D6) else Color(0xFF542D34)

// ── Sound Mixer hero ────────────────────────────────────────────────────
// The mixer's hero follows the theme (owner call 2026-08-05) rather than being
// a constant-dark panel stranded on a light page. Both arms are now plum: the
// Night arm sits on the night elevation ladder, the Dawn arm on the accent and
// info washes, so the panel reads as the same object in both themes.
//
// These are hero-local roles, not general tokens: only MixerHeroCard and the
// waveform inside it may read them.
val MixerHeroTop: Color get() = if (AppTheme.isNight) NightPalette.nightMid else DawnPalette.accentSoft
val MixerHeroMid: Color get() = if (AppTheme.isNight) NightPalette.cardFill else DawnPalette.infoSoft
val MixerHeroBottom: Color get() = if (AppTheme.isNight) NightPalette.night else DawnPalette.night
/** Panel edge + the radial bloom behind the waveform. */
val MixerHeroEdge: Color get() = if (AppTheme.isNight) Color(0x55D9ACDE) else Color(0x4D5A2B5C)
/** Title ink — the theme's own --text on both sides. */
val MixerHeroInk: Color get() = TextPrimary
/** The session line under the title. */
val MixerHeroInkSoft: Color get() = TextSoft
/** Eyebrow ("Now mixing" / "Ready to mix") and the timer glyph. */
val MixerHeroEyebrow: Color get() = Cyan
val MixerHeroTimer: Color get() = Periwinkle
/** The specks scattered across the panel. */
val MixerHeroSpeck: Color get() = if (AppTheme.isNight) NightPalette.textPrimary else DawnPalette.periwinkle
/** Waveform bars, top and bottom of the vertical gradient. */
val MixerWaveTop: Color get() = Cyan
val MixerWaveBottom: Color get() = Periwinkle
/** Play/pause pill — the accent gradient in both themes, so the one control on
 * the panel keeps its identity; its label is [OnPrimary] on both. */
val MixerPlayTop: Color get() = Periwinkle
val MixerPlayBottom: Color get() = Accent2

// ── Sleep wind-down hero + the Toolkit's featured billboard ─────────────
// Same rule as the mixer: no screen has a dark rectangle stranded on ivory.
val SleepHeroTop: Color get() = if (AppTheme.isNight) NightPalette.nightMid else DawnPalette.accentSoft
val SleepHeroMid: Color get() = if (AppTheme.isNight) NightPalette.cardFill else DawnPalette.infoSoft
val SleepHeroBottom: Color get() = if (AppTheme.isNight) NightPalette.night else DawnPalette.night
val SleepHeroEdge: Color get() = if (AppTheme.isNight) Color(0x55D9ACDE) else Color(0x4D5A2B5C)
val SleepHeroInk: Color get() = TextPrimary
val SleepHeroInkSoft: Color get() = TextSoft
val SleepHeroMeta: Color get() = TextMuted
val SleepHeroEyebrow: Color get() = Cyan
val SleepHeroTimer: Color get() = Periwinkle
/** The star field and the shimmer sweep across the panel. */
val SleepHeroSpark: Color get() = if (AppTheme.isNight) NightPalette.textPrimary else DawnPalette.periwinkle
/** The moon medallion: its radial halo and its glyph. */
val SleepHeroMoonGlow: Color get() = if (AppTheme.isNight) Color(0x66D9ACDE) else Color(0x4D5A2B5C)
val SleepHeroMoon: Color get() = if (AppTheme.isNight) Color(0xFFE9CDEC) else Color(0xFF4A2350)

// The featured-billboard tokens (FeaturedScrim/Edge/Ink/Pill*) left with the
// billboard itself: the Toolkit density pass demoted its one placement to a
// plain exercise row, and a palette entry with no surface is just a claim.

// Component tokens.
/** PrimaryButton label — `--on-accent` on the accent pill, in both themes. */
val OnPrimary: Color get() = if (AppTheme.isNight) NightPalette.onPrimary else DawnPalette.onPrimary
val ChipSelectedFill: Color get() = if (AppTheme.isNight) NightPalette.chipSelectedFill else DawnPalette.chipSelectedFill
val ChipSelectedInk: Color get() = if (AppTheme.isNight) NightPalette.chipSelectedInk else DawnPalette.chipSelectedInk
val SwitchThumbOn: Color get() = if (AppTheme.isNight) NightPalette.switchThumbOn else DawnPalette.switchThumbOn
/** Brightest chrome text (SubPage titles, ContentRow titles) — `--text` in both themes. */
val TextBright: Color get() = if (AppTheme.isNight) NightPalette.textBright else DawnPalette.textBright
/** Bottom-nav selected-cell radial stops (accent wash tuned per theme). */
val NavSelectedHi: Color get() = if (AppTheme.isNight) NightPalette.navSelectedHi else DawnPalette.navSelectedHi
val NavSelectedLo: Color get() = if (AppTheme.isNight) NightPalette.navSelectedLo else DawnPalette.navSelectedLo

// Structural veils — the soft white-on-Night wells/tracks/hairlines that would
// vanish on ivory; they flip to soft ink on Dawn.
val Veil: Color get() = if (AppTheme.isNight) NightPalette.veil else DawnPalette.veil
val VeilSoft: Color get() = if (AppTheme.isNight) NightPalette.veilSoft else DawnPalette.veilSoft
val VeilWell: Color get() = if (AppTheme.isNight) NightPalette.veilWell else DawnPalette.veilWell
val VeilStrong: Color get() = if (AppTheme.isNight) NightPalette.veilStrong else DawnPalette.veilStrong
val VeilLine: Color get() = if (AppTheme.isNight) NightPalette.veilLine else DawnPalette.veilLine

/** Full-screen modal/tour scrim. It must darken content in both themes; deriving
 * it from [Night] made the Dawn scrim a translucent ivory wash. */
val ModalScrim: Color get() = Color.Black.copy(alpha = if (AppTheme.isNight) 0.72f else 0.46f)

/** Moving highlight used by skeleton loaders: light on Night, ink on Dawn. */
val ShimmerHighlight: Color
    get() = if (AppTheme.isNight) Color.White.copy(alpha = 0.14f) else Ink.copy(alpha = 0.10f)

// ── Theme-independent colors ────────────────────────────────────────────────
// Decorative accents: never used as text on themed surfaces (orb/lotus art,
// thumbnail gradients, aurora tints, title-glow shadows only). Re-toned to the
// plum family in the Light Dawn port so no indigo survives as stray art.
val PeriwinkleDeep = Color(0xFF5A2B5C)  // = --accent (light)
val PeriwinkleSoft = Color(0xFFD9ACDE)  // = --accent (night) — a light plum for dark art
val Iris = Color(0xFFC580B5)            // = --accent-2 (night)
val Violet = Color(0xFF8A4A78)          // = --accent-2 (light)
val Teal = Color(0xFF9CC4DC)            // = --info (night); lotus / breathe accent

// Cream/Ink stay constants: every Cream consumer is light text/fills on
// always-light-on-dark art (GradientHero panels, HeroCard photo scrims, game
// tiles) and every Ink consumer is dark ink on light/white art (breathe-orb
// count, celebration check, funnel pills) — grounds that do not change with the
// theme. Both re-toned onto the canonical neutrals.
val Cream = Color(0xFFFFFCF8)       // --surface-raised
val Ink = Color(0xFF211D20)         // --text

// Constants for text/scrims over always-dark art (photo heroes, gradient game
// tiles): these panels keep their night art in both themes, so their overlay
// colors must NOT follow the theme. Values = the Night ground and --text-secondary.
val ArtScrim = Color(0xFF171019)
val ArtTextSoft = Color(0xFFDED4E0)

// ── V3 journey hero (companion-first reference, owner-approved 2026-08-16) ──
// THEMED, per the reference's as-built Dawn block (complete.html §Dawn):
// the light face wears a warm peach→lilac pane with deep-plum ink
// (#FBE3D3→#E2CFEF / ink #3B1C3F — the reference's exact values, which sit on
// our own Dawn family); Night keeps the deep plum pane with pale ink. Ink
// pairs hold ≥7:1 on both faces.
val HeroPlumTop: Color get() = if (AppTheme.isNight) Color(0xFF3A2150) else Color(0xFFFBE3D3)
val HeroPlumBottom: Color get() = if (AppTheme.isNight) Color(0xFF251035) else Color(0xFFE2CFEF)
val HeroInk: Color get() = if (AppTheme.isNight) Color(0xFFF5EDFA) else Color(0xFF3B1C3F)
val HeroInkMuted: Color get() = if (AppTheme.isNight) Color(0xFFC9B4D9) else Color(0xFF7A5680)
val HeroPale: Color get() = if (AppTheme.isNight) Color(0xFFE5CDF2) else Color(0xFF5A2B5C)

// List-thumbnail gradient floors (UI chrome for content-row artwork). The tops
// reuse the brand accents (Periwinkle/Cyan/Warm/Iris); these are the darker
// gradient partners — the light-theme (deep) end of each tonal role.
val ThumbBlue = Color(0xFF315C7A)   // = --info (light), the info thumbnail floor
val ThumbRose = Color(0xFFA45161)   // = --warm (light)
val ThumbIndigo = Color(0xFF8A4A78) // = --accent-2 (light)

// W21 generative-artwork accents (ContentArt.kt). Constants, not themed
// getters: content art keeps its deep night base in BOTH themes (like the
// hero/game panels above), so its hues must not follow the theme. Values =
// the Night accents these families are named after.
val ArtPeriwinkle = Color(0xFFD9ACDE)  // = NightPalette.periwinkle
val ArtCyan = Color(0xFF9CC4DC)        // = NightPalette.cyan
val ArtWarm = Color(0xFFF29AB0)        // = NightPalette.warm

// ---------------------------------------------------------------------------
// Semantic roles (docs/REDESIGN_V2.md §2) — screens should prefer these over the
// raw palette constants above. They are aliases of the themed getters, so both
// themes flow through them. Every text role is contrast-gated ≥ 4.5:1 on its
// surfaces in both themes (ContrastTest).
// ---------------------------------------------------------------------------
val Surface: Color get() = CardFill             // --surface-raised: resting card fill
val SurfaceRaised: Color get() = ChipFill       // --surface-field: chips, glass-card top edge
val SurfaceField: Color get() = FieldFill       // --surface-field: text-field container
val Line: Color get() = LineStroke              // --line: hairline dividers/strokes
val TextSecondary: Color get() = TextSoft       // --text-secondary: supporting copy
val TextFaint: Color get() = TextMuted2         // --text-faint (≥4.5:1 everywhere)
/** `--accent-soft`: the accent's own wash (tints, selected-state washes). */
val AccentSoft: Color get() = if (AppTheme.isNight) NightPalette.accentSoft else DawnPalette.accentSoft

// Onboarding / Auth surface tokens. The funnel FOLLOWS the appearance choice
// (2026-08-03): each role maps onto the ordinary themed tokens or a plum
// derivative, so the funnel is the theme rather than a second palette.
val GratitudeCardFill: Color get() = if (AppTheme.isNight) NightPalette.accentSoft else Surface
val GratitudeAvatarFill: Color get() = if (AppTheme.isNight) NightPalette.lineStroke else SurfaceRaised
val GratitudeCaption: Color get() = TextMuted
val InfoCardFill: Color get() = Surface
val InfoCardStroke: Color get() = Line
val InfoCardHint: Color get() = TextMuted
val InfoCardDivider: Color get() = Line
val WelcomeGradientTop: Color get() = if (AppTheme.isNight) NightPalette.nightMid else DawnPalette.cardFill
val WelcomeGradientBottom: Color get() = Night
val WelcomeTitleText: Color get() = TextSoft
val WelcomeSubtitleText: Color get() = TextMuted
val WelcomeSecondaryText: Color get() = Periwinkle
val WelcomeOrbMid = Color(0xFFFAF5FB)
val WelcomeOrbEdge = Color(0xFFD9ACDE)
// The orb's outer halo — three soft plum washes that fade the glow into the
// funnel backdrop (inner → outer, then the wide ambient disc beneath it).
val WelcomeOrbHaloInner = Color(0x335A2B5C)
val WelcomeOrbHaloOuter = Color(0x228A4A78)
val WelcomeOrbHaloDisc = Color(0x184A2350)
/** The primary CTA is an **accent fill** in both themes (REDESIGN_V2 §2:
 * "a near-white pill is invisible on an ivory ground"). */
val PrimaryButtonFill: Color get() = Periwinkle
val PrimaryButtonInk: Color get() = OnPrimary
val PrimaryButtonDisabledFill: Color get() = ButtonDisabled
val ResetDoneFill: Color get() = SurfaceRaised
val FunnelHeaderTop: Color get() = if (AppTheme.isNight) NightPalette.nightMid else DawnPalette.cardFill
val FunnelHeaderBottom: Color get() = Night
val FunnelBodyText: Color get() = TextSecondary
val ProgressTrack: Color get() = Line
/**
 * The selected row in a pick list — Appearance, Language, Crisis region.
 *
 * It is [ChipSelectedFill] in BOTH themes because the label on it is
 * [ChipSelectedInk], which is the on-accent ink: in Night that ink (#241927)
 * is designed against the accent pill (#D9ACDE) and measures the 8.77:1 the
 * palette documents. Painting it on `accentSoft` (#3A2A3E) instead put dark
 * ink on a dark surface and measured **1.27:1** — the selected option was the
 * one you could not read, in the one theme most people use at night. Dawn was
 * never affected (10.61:1), which is why a light-theme review kept missing it.
 * Found by walking all 58 routes on a CPH2681, 2026-08-20.
 */
val PickRowSelectedFill: Color get() = ChipSelectedFill
val PickRowFill: Color get() = Surface
val PickRowStroke: Color get() = Line
val PickRowChevron: Color get() = TextMuted2
val PickCardStroke: Color get() = Line
val DotUnselectedFill: Color get() = ChipFill
val AuthEyebrow: Color get() = EyebrowMuted
val AuthFieldLabel: Color get() = TextSoft

// ── Constant brand marks (theme-independent) ────────────────────────────────
// Not palette roles: these are the fixed brand hues the Material colorScheme,
// the gradient tokens and PremiumFrames' pill are built from. They do NOT flip
// with the theme — a brand mark that changed colour between Night and Dawn
// would stop being a brand mark. Re-toned to plum/sage/info with the rest of
// the port; each is a FILL that carries [OnPrimary]-weight ink, so the mid
// tones (`--accent-2`, `--info`, `--ok` at their light-theme depth) are used
// rather than the pale night variants.
val BrandPrimary = Color(0xFF8A4A78)    // plum — = --accent-2 (light)
val BrandSecondary = Color(0xFF315C7A)  // deep info blue
val BrandAccent = Color(0xFF49634F)     // sage — = --ok (light)
val LavenderPillTop = Color(0xFF8A4A78)
val LavenderPillFloor = Color(0xFF5A2B5C)
