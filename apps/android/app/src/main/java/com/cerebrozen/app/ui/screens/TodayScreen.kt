@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cerebrozen.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.theme.Accent
import com.cerebrozen.app.ui.theme.ArtTextSoft
import com.cerebrozen.app.ui.theme.BrandAccent
import com.cerebrozen.app.ui.theme.BrandPrimary
import com.cerebrozen.app.ui.theme.BrandSecondary
import com.cerebrozen.app.ui.theme.ChipFill
import com.cerebrozen.app.ui.theme.ChipSelectedFill
import com.cerebrozen.app.ui.theme.ChipSelectedInk
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.Gradients
import com.cerebrozen.app.ui.theme.Line
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.Radius
import com.cerebrozen.app.ui.theme.Space
import com.cerebrozen.app.ui.theme.Surface
import com.cerebrozen.app.ui.theme.TextFaint
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSecondary
import com.cerebrozen.app.ui.theme.Warm
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.Calendar

/** Mirrors iOS `Dummy.moods` (cross-stack mood taxonomy). */
private data class MoodOption(val name: String, val note: String, val symbol: String, val intensity: Int)

// i18n: pending — mood names/notes are the cross-stack mood taxonomy persisted
// to the backend; needs a label/value split before display strings can localize.
private val MOODS = listOf(
    MoodOption("Good", "Clear", "sparkles", 2),
    MoodOption("Anxious", "Loud thoughts", "exclamationmark.triangle", 4),
    MoodOption("Low", "Heavy", "moon", 4),
    MoodOption("Tired", "Need rest", "drop", 3),
)

/**
 * Each mood gets a hue, and it is the SAME hue everywhere the mood appears — the
 * chip you tap, and the row it becomes in "Recent check-ins". That consistency is
 * what turns four colours into a language instead of decoration.
 *
 * These are the text-safe accent tokens (not the raw brand fills), because they
 * are drawn as small dots that must stay visible on both a white selected pill and
 * a dark unselected chip. Colour is never the only signal — the label is always
 * present (WCAG 1.4.1).
 */
internal fun moodAccent(mood: String): Color = when (mood) {
    "Good" -> Ok         // mint — settled
    "Anxious" -> Warm    // coral — activated
    "Low" -> Periwinkle  // lavender — heavy
    "Tired" -> Cyan      // sky — depleted
    else -> Periwinkle
}

// i18n: pending — pure function, needs context plumbing
internal fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

private fun greeting(): String = greetingFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

/** A gentle celebration line on milestone days — presence framing (REDESIGN
 * §3.6): counts showing up, never chains or misses. Calm, never punitive. */
// i18n: pending — pure function, needs context plumbing
internal fun milestoneLine(streak: Int): String? =
    if (streak in setOf(3, 7, 14, 21, 30, 50, 100)) "🎉 $streak days of showing up — beautifully done" else null

/** `/users/me/streak` week → (weekday letter, active) pairs for the dot ring. */
internal fun parseWeek(streak: JSONObject): List<Pair<String, Boolean>> {
    val arr = streak.optJSONArray("week") ?: return emptyList()
    val letters = listOf("S", "M", "T", "W", "T", "F", "S")
    return (0 until arr.length()).map { i ->
        val d = arr.getJSONObject(i)
        val date = d.optString("date")
        val letter = runCatching {
            val cal = Calendar.getInstance()
            val parts = date.split("-").map(String::toInt)
            cal.set(parts[0], parts[1] - 1, parts[2])
            letters[cal.get(Calendar.DAY_OF_WEEK) - 1]
        }.getOrDefault("·")
        letter to d.optBoolean("active")
    }
}

/** Plan-step symbol → the Android surface that runs it (same contract as the
 * Oracle widgetRoute + the web Home mapping). */
internal fun planStepRoute(symbol: String): String? = when {
    symbol.startsWith("wind") -> "toolkit"
    symbol.startsWith("moon") || symbol == "bell" -> "sounds"
    symbol == "book" || symbol == "brain" -> "journal"
    symbol == "mic" || symbol.startsWith("person") || symbol == "heart" -> "talk"
    else -> null
}

// ── Home banner slot (W9) ────────────────────────────────────────────────
// At most ONE quiet banner under the greeting, by priority: offline truth →
// morning sleep check-in → evening wind-down → program day strip.

internal enum class HomeBanner { OFFLINE, SLEEP_CHECKIN, WIND_DOWN, PROGRAM, NONE }

/** Pure priority resolver for the Home banner slot — no Android, no clock, so
 * the whole decision (priority order, time windows, per-day dismissals) is
 * unit-testable. [dismissed] carries the banner keys dismissed today
 * ("sleep", "winddown"); offline always wins; the program strip is status,
 * never dismissible. */
internal fun homeBannerPriority(
    offline: Boolean,
    hour: Int,
    lastNightLogged: Boolean,
    dismissed: Set<String>,
    enrolledInProgram: Boolean,
): HomeBanner = when {
    offline -> HomeBanner.OFFLINE
    hour < 11 && !lastNightLogged && "sleep" !in dismissed -> HomeBanner.SLEEP_CHECKIN
    hour >= 21 && "winddown" !in dismissed -> HomeBanner.WIND_DOWN
    enrolledInProgram -> HomeBanner.PROGRAM
    else -> HomeBanner.NONE
}

/** True when any sleep-log date covers "last night" — a log saved this morning
 * carries today's date; one saved before midnight carries yesterday's. Pure. */
internal fun hasLastNightLog(dates: List<String>, today: LocalDate): Boolean =
    dates.any { raw ->
        val d = runCatching { LocalDate.parse(raw) }.getOrNull()
        d == today || d == today.minusDays(1)
    }

/** Time-matched rail kind + heading (mirrors the iOS Home rails). */
// i18n: pending — pure function, needs context plumbing (headings are user copy)
internal fun railKindFor(hour: Int): Pair<String, String> = when {
    hour < 12 -> "meditation" to "For this morning"
    hour < 17 -> "soundscape" to "A midday reset"
    else -> "sleep" to "For tonight"
}

// ── Components ───────────────────────────────────────────────────────────

/**
 * The mood chip. Same geometry and cross-fade as the shared [PickChip], plus the
 * mood's own hue as a leading dot.
 *
 * The dot carries a hairline ring in the label colour. Without it the dot would
 * nearly vanish on the selected pill in both themes — the pill is white on Navy
 * (a mint dot on white is ~1.6:1) and ink on Warm White (a deep-green dot on ink
 * is ~2:1). The ring is what keeps it legible whichever way the chip inverts.
 */
@Composable
private fun MoodChip(isSelected: Boolean, mood: MoodOption, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.round)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        if (isSelected) ChipSelectedFill else ChipFill, tween(220), label = "moodBg",
    )
    val stroke by animateColorAsState(
        if (isSelected) ChipSelectedFill else LineStroke, tween(220), label = "moodBorder",
    )
    val fg by animateColorAsState(
        if (isSelected) ChipSelectedInk else TextSecondary, tween(220), label = "moodFg",
    )
    Row(
        Modifier
            .heightIn(min = 52.dp)     // a11y: comfortably past the 48dp floor
            .pressScale(pressed)
            .clip(shape)
            .background(bg)
            .border(1.dp, stroke, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
            ) { Haptics.selection(); onClick() }
            .semantics { this.selected = isSelected }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(moodAccent(mood.name))
                .border(1.dp, fg.copy(alpha = 0.40f), CircleShape),
        )
        Text(
            mood.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = fg,
            maxLines = 1,
        )
    }
}

/**
 * The plan's completion, as a bar instead of a sentence.
 *
 * `done` and `total` were already computed on Home — they were just concatenated
 * into "2 of 5 done" and thrown at the user as prose. The same two numbers make a
 * progress bar, which is read in one glance rather than parsed. No new data.
 *
 * This sits on the hero's constant-dark art scrim, so it uses the theme-independent
 * art constants; a themed token would resolve to ink on Warm White and disappear.
 */
@Composable
private fun PlanProgress(done: Int, total: Int, label: String) {
    if (total <= 0) return
    val reduceMotion = rememberReduceMotion()
    val target = done.toFloat() / total
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) tween(0) else tween(700, easing = FastOutSlowInEasing),
        label = "plan-progress",
    )
    Column(verticalArrangement = Arrangement.spacedBy(Space.tight)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.20f))
                // One node, one announcement: the bar is decorative because the
                // label below already states the same fact.
                .clearAndSetSemantics { },
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Gradients.calm),
            )
        }
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = ArtTextSoft)
        }
    }
}

/** E3: the presence card's 7-dot week ring — each dot fades/scales in with a
 * one-shot 40ms stagger on first composition (instant under Reduce Motion).
 * Extracted so the reduce-motion branch is testable off-device.
 *
 * The final dot is today; it wears an accent ring so "where am I in this week" is
 * answerable without counting. Dots grew 14dp → 16dp: at 14dp with 1dp strokes the
 * filled and hollow states were hard to tell apart at arm's length. */
@Composable
internal fun PresenceWeekRing(week: List<Pair<String, Boolean>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        week.forEachIndexed { i, (day, active) ->
            val isToday = i == week.lastIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    Modifier.size(22.dp).popIn(i),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isToday) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .border(1.dp, BrandPrimary.copy(alpha = 0.55f), CircleShape),
                        )
                    }
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (active) BrandPrimary else Surface)
                            .border(1.dp, if (active) BrandPrimary else LineStroke, CircleShape)
                            .testTag("presence-dot-$i"),
                    )
                }
                Text(
                    day,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    color = if (isToday) TextSecondary else TextFaint,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

/** One past check-in: the mood's hue, its name, and the note it carried. Replaces
 * three identical grey lines of "Mood · Note" — the same text, but now the eye can
 * tell the entries apart before reading any of them. */
@Composable
private fun RecentRow(mood: String, note: String, showDivider: Boolean) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(moodAccent(mood)),
            )
            Text(
                mood,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
            )
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (showDivider) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        }
    }
}

/** The rail's loading state. The rail used to render *nothing* until content
 * arrived, so a cold start showed a hollow Home that then popped full — the
 * skeleton holds the space it is about to fill. [ShimmerBox] already existed in
 * Common.kt and was simply never used here. */
@Composable
private fun RailSkeleton() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Column(
                Modifier.width(160.dp),
                verticalArrangement = Arrangement.spacedBy(Space.tight),
            ) {
                ShimmerBox(
                    Modifier.fillMaxWidth().height(96.dp),
                    RoundedCornerShape(Radius.card),
                )
                ShimmerBox(Modifier.fillMaxWidth(0.75f).height(13.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.4f).height(11.dp))
            }
        }
    }
}

/** A horizontal card rail of served content, matched to the time of day. */
@Composable
private fun ContentRail(onOpen: (String) -> Unit) {
    val (kind, heading) = remember { railKindFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    val route = if (kind == "sleep") "sleep" else "sounds"
    var items by remember { mutableStateOf<JSONArray?>(null) }
    LaunchedEffect(kind) { runCatching { items = Api.content(kind) } }

    val list = items
    // Loaded and genuinely empty → the rail stays out of the way entirely
    // (unchanged behaviour). Still loading → hold the space with a skeleton.
    if (list != null && list.length() == 0) return

    Column(verticalArrangement = Arrangement.spacedBy(Space.group)) {
        Text(heading, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        if (list == null) {
            RailSkeleton()
            return@Column
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            (0 until list.length()).forEach { i ->
                val c = list.getJSONObject(i)
                val title = c.optString("title")
                val minutes = c.optInt("duration_min")
                val meta = if (minutes > 0) {
                    stringResource(R.string.common_minutes, minutes)
                } else {
                    stringResource(R.string.today_rail_ambient)
                }
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Column(
                    Modifier
                        .width(160.dp)
                        .pressScale(pressed, down = 0.97f)
                        .glass(RoundedCornerShape(Radius.card))
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                        ) { Haptics.soft(0.4f); onOpen(route) }
                        // The card speaks once: "Deep Rest, 12 min" — not three
                        // separate nodes the user has to swipe through.
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$title, $meta"
                        },
                ) {
                    Box(Modifier.fillMaxWidth().height(96.dp)) {
                        // Designed generative art always; a real image (when the
                        // backend serves one AND it loads) simply covers it.
                        ContentArt(
                            title = title, kind = kind,
                            modifier = Modifier.fillMaxSize()
                                .clip(RoundedCornerShape(Radius.card)),
                        )
                        val url = c.optString("image_url")
                        if (url.isNotBlank()) {
                            AsyncImage(
                                model = url, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                                    .clip(RoundedCornerShape(Radius.card)),
                            )
                        }
                    }
                    Column(
                        Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = TextFaint)
                    }
                }
            }
        }
    }
}

/**
 * Today — de-densified around one daily action (REDESIGN §3.1):
 * greeting → check-in → plan → one content rail → toolkit → presence → recent.
 *
 * The Serene pass changed how this reads, not what it does. Every API call, banner
 * rule, route and pure function is untouched; what changed is that the primary
 * action now *looks* primary (a [FocusCard], not the same glass card as the
 * read-only history below it), all four moods are reachable without a hidden
 * horizontal scroll, the plan's progress is a bar rather than a sentence, and a
 * failed check-in no longer looks identical to a successful one.
 */
private data class QuickAccessItem(
    val title: String,
    val subtitle: String,
    val route: String,
    val icon: ImageVector,
    val accent: Color,
)

@Composable
private fun QuickAccessSection(onOpen: (String) -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val glowMotion = rememberInfiniteTransition(label = "quickAccessGlow")
    val animatedGlow by glowMotion.animateFloat(
        initialValue = 0.32f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "quickAccessGlowAlpha",
    )
    val animatedIconOffset by glowMotion.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "quickAccessIconFloat",
    )
    val glow = if (reduceMotion) 0.44f else animatedGlow
    val iconOffset = if (reduceMotion) 0f else animatedIconOffset
    val items = listOf(
        QuickAccessItem(
            stringResource(R.string.today_quick_games_title),
            stringResource(R.string.today_quick_games_description),
            "games",
            Icons.Outlined.SportsEsports,
            Color(0xFFB18CFF),
        ),
        QuickAccessItem(
            stringResource(R.string.today_quick_insights_title),
            stringResource(R.string.today_quick_insights_description),
            "insights",
            Icons.Outlined.Insights,
            Color(0xFF64C9FF),
        ),
        QuickAccessItem(
            stringResource(R.string.today_quick_programs_title),
            stringResource(R.string.today_quick_programs_description),
            "programs",
            Icons.Outlined.CalendarMonth,
            Color(0xFF7A5CFF),
        ),
        QuickAccessItem(
            stringResource(R.string.today_quick_sounds_title),
            stringResource(R.string.today_quick_sounds_description),
            "sounds",
            Icons.Outlined.Headphones,
            Color(0xFF64C9FF),
        ),
    )

    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.today_quick_access_title),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
            Text(
                stringResource(R.string.today_quick_access_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items.chunked(2).forEachIndexed { rowIndex, rowItems ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    rowItems.forEachIndexed { columnIndex, item ->
                        QuickAccessCard(
                            item = item,
                            glowAlpha = glow,
                            iconOffset = iconOffset,
                            modifier = Modifier.weight(1f).height(156.dp)
                                .appear(rowIndex * 2 + columnIndex, rise = 14f),
                        ) {
                            Haptics.soft(0.35f)
                            onOpen(item.route)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAccessCard(
    item: QuickAccessItem,
    glowAlpha: Float,
    iconOffset: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "quickAccessScale",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) 1.dp else 5.dp,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "quickAccessElevation",
    )
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xE61A2340), Color(0xC923294B))))
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(item.accent.copy(alpha = glowAlpha), Color.White.copy(alpha = 0.10f), Color(0xFF64C9FF).copy(alpha = glowAlpha * 0.65f)),
                ),
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = item.title,
                onClick = onClick,
            )
            .padding(14.dp),
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).size(60.dp).blur(18.dp)
                .background(item.accent.copy(alpha = glowAlpha * 0.34f), CircleShape),
        )
        Box(
            Modifier.align(Alignment.CenterEnd).size(5.dp).clip(CircleShape)
                .background(Color(0x8864C9FF)),
        )
        Column(
            Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier.size(50.dp).graphicsLayer { translationY = iconOffset }
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(item.accent.copy(alpha = 0.30f), Color(0x3364C9FF))))
                    .border(1.dp, item.accent.copy(alpha = 0.44f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(27.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = Color(0xFFB8C2D9),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun TodayScreen(onOpen: (String) -> Unit) {
    var userName by remember { mutableStateOf("") }
    var streak by remember { mutableIntStateOf(0) }
    var recent by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var plan by remember { mutableStateOf<JSONObject?>(null) }
    var picked by remember { mutableStateOf<MoodOption?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }   // success and error must not look alike
    var busy by remember { mutableStateOf(false) }
    var week by remember { mutableStateOf(listOf<Pair<String, Boolean>>()) }
    var goal by remember { mutableStateOf("") }
    var program by remember { mutableStateOf<JSONObject?>(null) }
    // Optimistically true so the morning banner never flashes before data loads.
    var lastNightLogged by remember { mutableStateOf(true) }
    var bloom by remember { mutableIntStateOf(0) }        // one-shot per successful check-in
    var dismissTick by remember { mutableIntStateOf(0) }  // re-reads banner dismissals after prefPut
    val scope = rememberCoroutineScope()

    fun parseRecent(moods: JSONArray): List<Pair<String, String>> =
        (0 until minOf(moods.length(), 3)).map { i ->
            val m = moods.getJSONObject(i)
            m.getString("mood") to m.getString("note")
        }

    suspend fun reload() {
        runCatching {
            val me = Api.me()
            userName = me.optString("name")
            goal = me.optJSONArray("goals")?.optString(0).orEmpty()
        }
        runCatching {
            val s = Api.streak()
            streak = s.optInt("current")
            week = parseWeek(s)
        }
        runCatching { recent = parseRecent(Api.moods()) }
        runCatching { plan = Api.activePlan() }
        runCatching { program = Api.activeProgram() }
        // One extra GET (cached like every read) so the morning banner knows
        // whether last night is already logged — B2.
        runCatching {
            val logs = Api.sleepLogs()
            lastNightLogged = hasLastNightLog(
                (0 until logs.length()).map { logs.getJSONObject(it).optString("date") },
                LocalDate.now(),
            )
        }
    }

    LaunchedEffect(Unit) { reload() }
    var showTour by remember { mutableStateOf(!TourState.isDone()) }
    val reduceMotion = rememberReduceMotion()

    val friend = stringResource(R.string.today_friend)
    val checkedIn = stringResource(R.string.today_checkin_done)
    val checkinFailed = stringResource(R.string.today_checkin_failed)

    Box(Modifier.fillMaxSize()) {
        Page(
            eyebrow = if (goal.isBlank()) {
                stringResource(R.string.today_eyebrow)
            } else {
                stringResource(R.string.today_eyebrow_goal, goal)
            },
            title = stringResource(
                R.string.today_greeting_format, greeting(), userName.ifBlank { friend },
            ),
            trailing = Icons.Outlined.Search,
            accent = Accent.home,
            eyebrowColor = Periwinkle,
            trailingLabel = stringResource(R.string.today_search_cd),
            onTrailingClick = { onOpen("search") },
        ) {
            // The one quiet banner slot (W9): at most one, by honest priority.
            val today = LocalDate.now().toString()
            val dismissed = remember(dismissTick, today) {
                buildSet {
                    if (Session.prefGet("sleepBannerDismissed") == today) add("sleep")
                    if (Session.prefGet("windDownBannerDismissed") == today) add("winddown")
                }
            }
            when (
                homeBannerPriority(
                    offline = Session.servedStale,
                    hour = LocalTime.now().hour,
                    lastNightLogged = lastNightLogged,
                    dismissed = dismissed,
                    enrolledInProgram = program != null,
                )
            ) {
                HomeBanner.OFFLINE -> InfoBanner(
                    icon = Icons.Outlined.CloudOff,
                    text = stringResource(R.string.today_banner_offline),
                )
                HomeBanner.SLEEP_CHECKIN -> InfoBanner(
                    icon = Icons.Outlined.LightMode,
                    text = stringResource(R.string.today_banner_sleep),
                    actionLabel = stringResource(R.string.today_banner_sleep_action),
                    onAction = { onOpen("sleep") },
                    onDismiss = { Session.prefPut("sleepBannerDismissed", today); dismissTick++ },
                )
                HomeBanner.WIND_DOWN -> InfoBanner(
                    icon = Icons.Outlined.Bedtime,
                    text = stringResource(R.string.today_banner_winddown),
                    actionLabel = stringResource(R.string.today_banner_winddown_action),
                    onAction = { onOpen("sounds/mixer") },
                    onDismiss = { Session.prefPut("windDownBannerDismissed", today); dismissTick++ },
                    artKind = "sleep",
                )
                HomeBanner.PROGRAM -> program?.let { prog ->
                    // The day strip is status, not a nudge — never dismissible.
                    InfoBanner(
                        icon = Icons.Outlined.CalendarMonth,
                        text = stringResource(
                            R.string.today_banner_program,
                            prog.optInt("day"), prog.optInt("days"), prog.optString("title"),
                        ),
                        actionLabel = stringResource(R.string.common_open),
                        onAction = { onOpen("programs") },
                        artKind = "program",
                    )
                }
                HomeBanner.NONE -> {}
            }

            QuickAccessSection(onOpen)
            SectionGap()

            // ── The primary daily action. One FocusCard; nothing else competes. ──
            Box {
                FocusCard(accent = BrandPrimary) {
                    Text(
                        stringResource(R.string.today_checkin_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                    )
                    Text(
                        stringResource(R.string.today_checkin_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    // A 2×2 grid, not a horizontal scroll. Four chips at ~110dp each
                    // overflowed a 360dp phone, so "Tired" sat off-screen behind an
                    // invisible scroll — a quarter of the mood vocabulary, unreachable.
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        maxItemsInEachRow = 2,
                    ) {
                        MOODS.forEachIndexed { i, mood ->
                            Box(Modifier.weight(1f).appear(i, rise = 10f)) {
                                MoodChip(isSelected = picked == mood, mood = mood) {
                                    picked = mood
                                }
                            }
                        }
                    }
                    PrimaryButton(
                        text = if (busy) {
                            stringResource(R.string.common_one_moment)
                        } else {
                            stringResource(R.string.today_checkin_cta)
                        },
                        enabled = picked != null && !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val mood = picked ?: return@PrimaryButton
                        busy = true; status = null; failed = false
                        scope.launch {
                            try {
                                Api.checkIn(mood.name, mood.note, mood.symbol, mood.intensity)
                                Haptics.success()
                                if (!reduceMotion) bloom++
                                status = checkedIn
                                failed = false
                                picked = null
                                reload()
                            } catch (e: Exception) {
                                status = e.message ?: checkinFailed
                                failed = true
                                Haptics.warning()
                            } finally {
                                busy = false
                            }
                        }
                    }
                    // Success and failure used to render in the same muted grey — a
                    // failed check-in was indistinguishable from a saved one. Now the
                    // colour, the icon AND the haptic differ (never colour alone).
                    AnimatedVisibility(visible = status != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (failed) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = if (failed) Danger else Ok,
                                modifier = Modifier.size(17.dp),
                            )
                            Text(
                                status.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (failed) Danger else Ok,
                            )
                        }
                    }
                }
                // The one-shot bloom rides over the card; Reduce Motion never arms it.
                if (bloom > 0) BloomRing(bloom, BrandAccent, Modifier.matchParentSize())
            }

            SectionGap()

            // The goal-aware next action; tapping deep-links to the full plan.
            plan?.let { p ->
                val steps = p.optJSONArray("steps")
                val total = steps?.length() ?: 0
                val done = (0 until total).count { steps!!.getJSONObject(it).optBoolean("done") }
                val next = (0 until total).map { steps!!.getJSONObject(it) }
                    .firstOrNull { !it.optBoolean("done") }
                HeroCard(
                    kind = "program",
                    eyebrow = stringResource(R.string.today_plan_eyebrow),
                    title = p.optString("title"),
                    subtitle = p.optString("focus"),
                    height = 210.dp,
                    alive = true,
                    onClick = { onOpen("plan") },
                ) {
                    val nextLabel = next?.let {
                        stringResource(R.string.today_plan_next, it.optString("title"))
                    }
                    val doneLabel = if (total > 0) {
                        stringResource(R.string.today_plan_done_count, done, total)
                    } else {
                        ""
                    }
                    PlanProgress(done = done, total = total, label = doneLabel)
                    if (nextLabel != null) {
                        Text(
                            nextLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ArtTextSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                SectionGap()
            }

            // Time-matched content rail (mirrors the iOS Home rails).
            ContentRail(onOpen)

            SectionGap()

            NavRow(
                stringResource(R.string.today_toolkit_title),
                stringResource(R.string.today_toolkit_subtitle),
            ) { onOpen("toolkit") }

            SectionGap()

            // Presence (REDESIGN §3.6): count the days you showed up, never the
            // days you didn't. The ring fills; it never breaks or resets.
            SectionCard {
                val daysPresent = week.count { it.second }
                Text(
                    if (daysPresent > 0 || streak > 0) {
                        stringResource(R.string.today_presence_title)
                    } else {
                        stringResource(R.string.today_presence_ready)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                milestoneLine(streak)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Ok)
                }
                Text(
                    if (daysPresent > 0) {
                        pluralStringResource(R.plurals.today_presence_days, daysPresent, daysPresent)
                    } else {
                        stringResource(R.string.today_presence_empty)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                if (week.isNotEmpty()) PresenceWeekRing(week)
            }

            if (recent.isNotEmpty()) {
                SectionCard {
                    Text(
                        stringResource(R.string.today_recent_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    recent.forEachIndexed { i, (mood, note) ->
                        RecentRow(mood = mood, note = note, showDivider = i < recent.lastIndex)
                    }
                }
            }
        }

        // First-run guided tour (ref GUIDED TOUR OVERLAY) — once per install.
        if (showTour) {
            GuidedTourOverlay(onDone = { showTour = false })
        }
    }
}
