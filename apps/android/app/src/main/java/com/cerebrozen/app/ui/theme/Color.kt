package com.cerebrozen.app.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette — kept in sync with the iOS DesignSystem
// (apps/ios/CereBro/DesignSystem/Theme.swift) and the web tokens
// (design/tokens.css). 2026-07 warm refresh: night warmed to indigo, primary
// hue softened to warm lavender, plus coral + cyan warm accents.
val Night = Color(0xFF0E0C22)       // --night (primary background / splash)
val NightMid = Color(0xFF1A1440)    // --night-top (lifted gradient top)
val NightPurple = Color(0xFF161138) // warm deep mid
val Periwinkle = Color(0xFF8A7BF0)  // --lav (primary accent, the orb ring)
val PeriwinkleDeep = Color(0xFF5B52C9) // --lav-deep (orb core / hero gradient floor)
val PeriwinkleSoft = Color(0xFFCBB6FF) // --lav-soft (hero eyebrow, lifted lavender)
val Iris = Color(0xFFA68BFF)        // --lav-2
val Violet = Color(0xFFA68BFF)      // --lav-2 (aurora gradient partner)
val Cream = Color(0xFFECEEFB)       // --cream
val Ink = Color(0xFF1C1740)         // --ink

val TextPrimary = Color(0xFFF5F4FF) // --text
val TextSoft = Color(0xFFDFE0FF)    // --soft
val TextMuted = Color(0xFFB0A9E0)   // --muted
val TextMuted2 = Color(0xFF8F88C0)  // --muted-2
val Danger = Color(0xFFE08A9A)      // --danger

// Warm accents introduced by the refresh.
val Warm = Color(0xFFF0A48C)        // --warm (coral)
val Cyan = Color(0xFF8FE6EE)        // --cyan (breathing orb)
val Ok = Color(0xFF7EE0A8)          // --ok (success)

// Glass card fills.
val CardFill = Color(0x0DFFFFFF)   // white 5%  (--card)
val LineStroke = Color(0x1FFFFFFF) // white 12% (--line)

// List-thumbnail gradient floors (UI chrome for content-row artwork). The tops
// reuse the brand accents (Periwinkle/Cyan/Warm/Iris); these are the darker
// gradient partners that don't map to an existing palette token.
val ThumbBlue = Color(0xFF5B8FD0)   // cyan thumbnail floor
val ThumbRose = Color(0xFFB86B8F)   // coral thumbnail floor
val ThumbIndigo = Color(0xFF6F7BF7) // iris thumbnail floor
