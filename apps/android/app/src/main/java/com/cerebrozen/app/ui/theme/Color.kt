package com.cerebrozen.app.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette — kept in sync with the iOS DesignSystem
// (apps/ios/CereBro/DesignSystem/Theme.swift) and the web tokens
// (design/tokens.css). 2026-07 warm refresh: night warmed to indigo, primary
// hue softened to warm lavender, plus coral + cyan warm accents.
val Night = Color(0xFF100D2B)       // reference gradient floor
val NightMid = Color(0xFF3A3372)    // reference gradient top
val NightPurple = Color(0xFF29254D) // fields and secondary surfaces
val Periwinkle = Color(0xFF8B78F2)  // switches, focus and icon accent
val PeriwinkleDeep = Color(0xFF5545AD)
val PeriwinkleSoft = Color(0xFFC5BDF3)
val Iris = Color(0xFF9A87F5)
val Violet = Color(0xFF7665D4)
val Cream = Color(0xFFECEEFB)       // --cream
val Ink = Color(0xFF1C1740)         // --ink

val TextPrimary = Color(0xFFF5F4FF) // --text
val TextSoft = Color(0xFFE1DEEE)    // --soft
val TextMuted = Color(0xFFC0BBD4)   // --muted
val TextMuted2 = Color(0xFF928CAC)  // --muted-2
val Danger = Color(0xFFE08A9A)      // --danger

// Warm accents introduced by the refresh.
val Warm = Color(0xFFF0A48C)        // --warm (coral)
val Cyan = Color(0xFF8FE6EE)        // --cyan (breathing orb)
val Teal = Color(0xFF6FE0E6)        // --teal (lotus / breathe accent, matches iOS)
val Ok = Color(0xFF7EE0A8)          // --ok (success)

// Glass card fills.
val CardFill = Color(0xFF302C55)
val LineStroke = Color(0xFF514B76)

// Floating bottom-nav pill — a lifted lavender-indigo capsule over a dark scrim.
val NavPillTop = Color(0xFF413A70)
val NavPillBottom = Color(0xFF28234D)
val NavScrim = Color(0xFF100D2B)

// List-thumbnail gradient floors (UI chrome for content-row artwork). The tops
// reuse the brand accents (Periwinkle/Cyan/Warm/Iris); these are the darker
// gradient partners that don't map to an existing palette token.
val ThumbBlue = Color(0xFF5B8FD0)   // cyan thumbnail floor
val ThumbRose = Color(0xFFB86B8F)   // coral thumbnail floor
val ThumbIndigo = Color(0xFF6F7BF7) // iris thumbnail floor
