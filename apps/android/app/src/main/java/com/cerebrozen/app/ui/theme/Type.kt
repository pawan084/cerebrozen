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

// Brand serif: Newsreader (variable font), matching the iOS/web display type.
// Body stays on the system sans; only headings use the serif.
private val Newsreader = FontFamily(
    Font(
        R.font.newsreader, weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.newsreader, weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.newsreader, weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(430)),
    ),
)

val Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = Newsreader,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Newsreader,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    // The small-caps EYEBROW: "WIND DOWN", "TONIGHT", "TODAY'S PLAN". Two to four
    // words, above a heading. It is not a body style — 1.6sp of tracking on bold
    // 11sp with no lineHeight is close to unreadable over three lines, and it had
    // drifted onto ten paragraphs, among them the Health Connect consent boundary
    // and TIPP's "if the urge to hurt yourself is present" line. Paragraphs use
    // [bodySmall]; see the 2026-07-31 Sleep audit.
    labelSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
    ),
)
