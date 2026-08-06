package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerebrozen.app.R
import com.cerebrozen.app.ui.BrandMark
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.Warm

/** Explore hub matched to the canonical mobile.html composition. Existing
 * destinations remain present; this only changes their visual hierarchy. */
@Composable
fun ExploreScreen(onOpen: (String) -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    Column(Modifier.fillMaxSize().background(Night).statusBarsPadding()) {
        ExploreTopBar(serif, onUrgent = { onOpen("crisis") })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp).padding(top = 14.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("EXPLORE", style = MaterialTheme.typography.labelSmall, color = Warm)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Find what fits\nthis moment.", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = serif, fontWeight = FontWeight.Normal,
                        fontSize = 40.sp, lineHeight = 38.sp,
                    ), color = TextPrimary,
                )
                Box(
                    Modifier.size(48.dp).background(Color(0xFFF3ECF3), CircleShape)
                        .clickable { onOpen("search") },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Search, "Search", tint = Periwinkle, modifier = Modifier.size(23.dp)) }
            }
            Text(
                "Find a suitable tool quickly by need,\nformat and time.",
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp), color = TextMuted,
            )
            ExploreHero { onOpen("breathe/reset") }
            Spacer(Modifier.height(15.dp))
            ExploreSectionTitle("Start by need", serif)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeedCard("○", "Calm now", "Breathing and\ngrounding", Modifier.weight(1f)) { onOpen("breathe/reset") }
                NeedCard("☾", "Sleep", "Tonight and CBT-I", Modifier.weight(1f)) { onOpen("sleep") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeedCard("⌁", "Thoughts", "Reframe and reflect", Modifier.weight(1f)) { onOpen("cbt") }
                NeedCard("♫", "Sound", "Audio and mixer", Modifier.weight(1f)) { onOpen("sounds") }
            }
            Spacer(Modifier.height(12.dp))
            ExploreSectionTitle("Keep exploring", serif)
            ExploreListCard(Icons.Outlined.Spa, "Mindful activities", "Unscored sensory experiences", Warm) { onOpen("toolkit") }
            ExploreListCard(Icons.Outlined.CalendarMonth, "Programmes", "Guided journeys with progress", Warm) { onOpen("programs") }
            ExploreListCard(Icons.Outlined.FavoriteBorder, "Favourites and downloads", "Saved and offline", Color(0xFF49634F)) { onOpen("sounds") }
            ExploreAccordion("Watch and learn") { onOpen("insightreel") }

            // Existing safety feature retained as an extra card below the
            // canonical reference content.
            ExploreListCard(
                Icons.Outlined.HealthAndSafety,
                stringResource(R.string.explore_support_title),
                stringResource(R.string.explore_support_subtitle),
                Danger,
            ) { onOpen("crisis") }
        }
    }
}

@Composable
private fun ExploreTopBar(serif: FontFamily, onUrgent: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(CardFill)
            .border(0.5.dp, LineStroke).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        BrandMark(size = 44.dp, showGlow = true)
        Column(Modifier.weight(1f)) {
            Text("Explore", style = MaterialTheme.typography.titleLarge.copy(fontFamily = serif), color = TextPrimary)
            Text("Tools for different moments", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Box(
            Modifier.size(46.dp).background(Danger.copy(alpha = .09f), CircleShape).clickable(onClick = onUrgent),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = Danger, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun ExploreHero(onClick: () -> Unit) {
    val shape = RoundedCornerShape(29.dp)
    Box(
        Modifier.fillMaxWidth().height(160.dp).background(
            Brush.linearGradient(listOf(Color(0xFFFFE2D6), Color(0xFFF0D8EF), Color(0xFFE1CDEA))), shape,
        ).clip(shape).clickable(onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0x25FFFFFF), radius = 105.dp.toPx(), center = Offset(size.width * .90f, size.height * .95f))
            drawCircle(Color(0x305A2B5C), radius = 88.dp.toPx(), center = Offset(size.width * .90f, size.height * .95f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawCircle(
                Brush.radialGradient(listOf(Color.White, Color(0xFFF0E6F8), Color(0xFFD5B8E1))),
                radius = 34.dp.toPx(), center = Offset(58.dp.toPx(), 60.dp.toPx()),
            )
        }
    }
}

@Composable
private fun ExploreSectionTitle(title: String, serif: FontFamily) = Text(
    title, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = serif, fontWeight = FontWeight.SemiBold),
    color = TextPrimary,
)

@Composable
private fun NeedCard(glyph: String, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 136.dp).background(Color(0xFFF4EDF6), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(15.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(38.dp).background(CardFill, CircleShape), contentAlignment = Alignment.Center) {
            Text(glyph, color = Periwinkle, style = MaterialTheme.typography.titleMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp), color = TextMuted)
        }
    }
}

@Composable
private fun ExploreListCard(icon: ImageVector, title: String, subtitle: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).background(CardFill, RoundedCornerShape(22.dp))
            .border(1.dp, LineStroke.copy(alpha = .55f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.size(42.dp).background(tint.copy(alpha = .10f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = Periwinkle, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ExploreAccordion(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).background(Color(0xFFF4EDF6), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Periwinkle)
        Text("+", style = MaterialTheme.typography.titleLarge, color = Periwinkle)
    }
}
