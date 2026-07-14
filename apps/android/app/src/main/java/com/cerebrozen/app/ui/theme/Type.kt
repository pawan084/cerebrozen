@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.cerebrozen.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cerebrozen.app.R

/**
 * Nunito — a rounded, humanist sans (variable weight axis 200–1000).
 *
 * Chosen over Quicksand because its taller x-height and open apertures hold up in
 * long body copy, which this app has a lot of (journal entries, chat, consent
 * notices, crisis instructions) — Quicksand's geometric roundness reads beautifully
 * at 32sp and gets tiring at 14sp. Nunito is soft without costing legibility.
 *
 * One family for everything. The previous scale mixed a display serif (Newsreader)
 * with a system sans for body, which meant headings and body shared no vertical
 * rhythm and no letterform DNA. A single rounded family, differentiated by weight
 * and size, reads calmer and more coherent — and is what Calm/Headspace/Finch do.
 */
private val Nunito = FontFamily(
    Font(R.font.nunito, weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    Font(R.font.nunito, weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.nunito, weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.nunito, weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.nunito, weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))),
)

/**
 * The scale. Headings are large and welcoming with negative tracking (big rounded
 * type needs tightening or it reads loose); body is generously leaded (1.5×) for
 * comfortable reading in an anxious moment. Every size is in `sp`, so the whole
 * app scales with the user's system font-size preference.
 */
val Typography = Typography(
    // Hero greeting — "Good evening, Priya". The warmest moment in the app.
    displayLarge = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.8).sp,
    ),
    // Page titles.
    displayMedium = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp,
    ),
    // Card / hero titles.
    headlineMedium = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp,
    ),
    // Section headings.
    titleLarge = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.Bold,
        fontSize = 18.sp, lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    // Body — 1.5× leading, the accessibility sweet spot for sustained reading.
    bodyLarge = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
    ),
    // Controls.
    labelLarge = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    // Small-caps eyebrows. 1.4sp tracking (was 1.6) — Nunito's rounder counters
    // need less air than the old serif did.
    labelSmall = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.4.sp,
    ),
)
