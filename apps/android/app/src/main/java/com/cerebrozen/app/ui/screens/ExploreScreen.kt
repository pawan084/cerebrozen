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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.SelfImprovement
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
import com.cerebrozen.app.ui.theme.AccentSoft
import com.cerebrozen.app.ui.theme.FieldFill
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.ExploreHeroEnd
import com.cerebrozen.app.ui.theme.ExploreHeroOrbCore
import com.cerebrozen.app.ui.theme.ExploreHeroOrbEdge
import com.cerebrozen.app.ui.theme.ExploreHeroStart

/** Explore hub matched to the canonical mobile.html composition. Existing
 * destinations remain present; this only changes their visual hierarchy. */
@Composable
fun ExploreScreen(onOpen: (String) -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    Column(Modifier.fillMaxSize().background(Night)) {
        ExploreTopBar(onUrgent = { onOpen("crisis") })
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
                    Modifier.size(48.dp).background(FieldFill, CircleShape)
                        .clickable { onOpen("search") },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Search, "Search", tint = Periwinkle, modifier = Modifier.size(23.dp)) }
            }
            Text(
                "Find a suitable tool quickly by need,\nformat and time.",
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp), color = TextMuted,
            )
            // Explore is a discovery surface: open the Breath Loops picker and
            // let the user choose/start a pattern. The direct `breathe/reset`
            // route intentionally starts immediately for explicit reset links.
            ExploreHero { onOpen("breathe/box") }
            Spacer(Modifier.height(15.dp))
            ExploreSectionTitle("Start by need", serif)
            // height(IntrinsicSize.Max) + fillMaxHeight in NeedCard: the four
            // cards were vertically misaligned (audit I#5) — the two-line
            // subtitle grew one card past the 136dp floor and the SpaceBetween
            // anchoring dragged its title ~34px above its neighbour's. Row two
            // aligned only because both subtitles happened to fit one line, so
            // the bug was invisible until someone edited copy.
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeedCard(Icons.Outlined.SelfImprovement, "Calm now", "Breathing and\ngrounding", Modifier.weight(1f)) { onOpen("practice-library") }
                NeedCard(Icons.Outlined.Bedtime, "Sleep", "Tonight and CBT-I", Modifier.weight(1f)) { onOpen("sleep") }
            }
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeedCard(Icons.Outlined.Psychology, "Thoughts", "Reframe and reflect", Modifier.weight(1f)) { onOpen("cbt") }
                NeedCard(Icons.Outlined.MusicNote, "Sound", "Audio and mixer", Modifier.weight(1f)) { onOpen("sounds") }
            }
            Spacer(Modifier.height(12.dp))
            ExploreSectionTitle("Keep exploring", serif)
            ExploreListCard(Icons.Outlined.Spa, "Mindful activities", "Unscored sensory experiences", Warm) { onOpen("toolkit") }
            ExploreListCard(Icons.Outlined.CalendarMonth, "Programmes", "Guided journeys with progress", Warm) { onOpen("programs") }
            // Was "Favourites and downloads · Saved and offline" — three problems in
            // one row. No client implements downloads (the documented reason
            // "available offline"/"download for offline" are banned phrases), there
            // is no favourites screen to open, and it routed to "sounds", the same
            // destination as the "Sound · Audio and mixer" card two rows above. The
            // claims gate missed it because it matches literal phrases and this was
            // the same promise in different words. Deleted rather than reworded: a
            // second row to one destination is not worth honest copy.
            // A list row like its two siblings (audit I#8): this was a tinted
            // "accordion" with a "+" that never expanded anything — it
            // navigates, exactly like the rows above it, and now looks like it.
            ExploreListCard(
                Icons.Outlined.PlayCircleOutline,
                stringResource(R.string.explore_watch_title),
                stringResource(R.string.explore_watch_subtitle),
                Warm,
            ) { onOpen("insightreel") }

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

/** Delegates to [CereBroTopBar] — see the note there on the nine that existed. */
@Composable
private fun ExploreTopBar(onUrgent: () -> Unit) = CereBroTopBar(
    title = stringResource(R.string.tab_explore),
    subtitle = stringResource(R.string.topbar_explore_subtitle),
    onUrgent = onUrgent,
)

@Composable
private fun ExploreHero(onClick: () -> Unit) {
    val shape = RoundedCornerShape(29.dp)
    // Words on the door (audit I#7). This was the single largest element above
    // the fold on a screen promising "find a tool quickly" — and it carried no
    // information at all, because it is actually a TAPPABLE door to box
    // breathing that nothing labelled: an invisible control dressed as
    // decoration, with no accessibility semantics either. The pixels now say
    // what the tap does, which is cheaper than the height was.
    Box(
        Modifier.fillMaxWidth().height(160.dp).background(
            Brush.linearGradient(listOf(ExploreHeroStart, AccentSoft, ExploreHeroEnd)), shape,
        ).clip(shape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0x25FFFFFF), radius = 105.dp.toPx(), center = Offset(size.width * .90f, size.height * .95f))
            drawCircle(Color(0x305A2B5C), radius = 88.dp.toPx(), center = Offset(size.width * .90f, size.height * .95f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawCircle(
                Brush.radialGradient(listOf(ExploreHeroOrbCore, FieldFill, ExploreHeroOrbEdge)),
                radius = 34.dp.toPx(), center = Offset(58.dp.toPx(), 60.dp.toPx()),
            )
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 18.dp, end = 110.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                stringResource(R.string.explore_hero_eyebrow).uppercase(),
                style = MaterialTheme.typography.labelSmall, color = Cyan,
            )
            Text(
                stringResource(R.string.explore_hero_title),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary,
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
private fun NeedCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().heightIn(min = 136.dp).background(FieldFill, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(15.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(38.dp).background(CardFill, CircleShape), contentAlignment = Alignment.Center) {
            // Real icons, not text glyphs (audit I#6): "○" and "⌁" rendered as
            // placeholder marks beside a confident crescent and note — and
            // beside the filled Material icons of the list rows below.
            Icon(icon, contentDescription = null, tint = Periwinkle, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            // minLines 2 = a reserved subtitle slot, so sibling titles stay
            // level whether the copy wraps or not (audit I#5).
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp), color = TextMuted, minLines = 2)
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

