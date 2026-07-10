package com.cerebrozen.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.cerebrozen.app.audio.Player
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Warm
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Mirrors iOS `Dummy.moods` (cross-stack mood taxonomy). */
private data class MoodOption(val name: String, val note: String, val symbol: String, val intensity: Int)

private val MOODS = listOf(
    MoodOption("Good", "Clear", "sparkles", 2),
    MoodOption("Anxious", "Loud thoughts", "exclamationmark.triangle", 4),
    MoodOption("Low", "Heavy", "moon", 4),
    MoodOption("Tired", "Need rest", "drop", 3),
)

internal fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

private fun greeting(): String = greetingFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

/** A gentle celebration line on milestone days (mirrors iOS streak
 * celebrations — calm, never punitive). */
internal fun milestoneLine(streak: Int): String? =
    if (streak in setOf(3, 7, 14, 21, 30, 50, 100)) "🎉 $streak-day milestone — beautifully done" else null

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
    symbol.startsWith("wind") -> "games"
    symbol.startsWith("moon") || symbol == "bell" -> "sounds"
    symbol == "book" || symbol == "brain" -> "journal"
    symbol == "mic" || symbol.startsWith("person") || symbol == "heart" -> "talk"
    else -> null
}

/** Time-matched rail kind + heading (mirrors the iOS Home rails). */
internal fun railKindFor(hour: Int): Pair<String, String> = when {
    hour < 12 -> "meditation" to "For this morning"
    hour < 17 -> "soundscape" to "A midday reset"
    else -> "sleep" to "For tonight"
}

/** A horizontal card rail of served content, matched to the time of day. */
@Composable
private fun ContentRail(onOpen: (String) -> Unit) {
    val (kind, heading) = remember { railKindFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    val route = if (kind == "sleep") "sleep" else "sounds"
    var items by remember { mutableStateOf<JSONArray?>(null) }
    LaunchedEffect(kind) { runCatching { items = Api.content(kind) } }
    val list = items ?: return
    if (list.length() == 0) return
    Text(heading, style = MaterialTheme.typography.titleMedium, color = TextSoft)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        (0 until list.length()).forEach { i ->
            val c = list.getJSONObject(i)
            val title = c.optString("title")
            Column(
                Modifier.width(150.dp)
                    .glass(RoundedCornerShape(16.dp))
                    .clickable { onOpen(route) }
                    .padding(0.dp),
            ) {
                Box(Modifier.fillMaxWidth().size(width = 150.dp, height = 84.dp)) {
                    val url = c.optString("image_url")
                    if (url.isNotBlank()) {
                        AsyncImage(model = url, contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
                    } else {
                        Box(Modifier.fillMaxSize().background(Periwinkle.copy(alpha = 0.25f)))
                    }
                }
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, color = TextSoft, maxLines = 1)
                    val d = c.optInt("duration_min")
                    Text(if (d > 0) "$d min" else "Ambient", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }
}

/** Today: quick-access grid + live mood check-in + streak + recent check-ins. */
@Composable
fun TodayScreen(onOpen: (String) -> Unit) {
    var userName by remember { mutableStateOf("") }
    var streak by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var recent by remember { mutableStateOf(listOf<String>()) }
    var plan by remember { mutableStateOf<JSONObject?>(null) }
    var picked by remember { mutableStateOf<MoodOption?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var moodCount by remember { mutableIntStateOf(0) }
    var week by remember { mutableStateOf(listOf<Pair<String, Boolean>>()) }
    var goal by remember { mutableStateOf("") }
    var program by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()

    fun parseRecent(moods: JSONArray): List<String> =
        (0 until minOf(moods.length(), 5)).map { i ->
            val m = moods.getJSONObject(i)
            "${m.getString("mood")} · ${m.getString("note")}"
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
            best = s.optInt("best")
            week = parseWeek(s)
        }
        runCatching { val m = Api.moods(); moodCount = m.length(); recent = parseRecent(m) }
        runCatching { plan = Api.activePlan() }
        runCatching { program = Api.activeProgram() }
    }

    LaunchedEffect(Unit) { reload() }
    var showTour by remember { mutableStateOf(!TourState.isDone()) }

    // A gentle settle-in as the screen arrives (complements the NavHost cross-fade).
    val reduceMotion = rememberReduceMotion()
    val rise = remember { Animatable(26f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) rise.snapTo(0f) else rise.animateTo(0f, tween(460, easing = FastOutSlowInEasing))
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .graphicsLayer { translationY = rise.value }
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Goal-aware eyebrow + serif greeting (mirrors iOS DailyFocus header),
        // with the working search affordance top-right (ref SEARCH route).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    (if (goal.isBlank()) "Today" else "Today · $goal").uppercase(),
                    style = MaterialTheme.typography.labelSmall, color = Periwinkle,
                )
                Text(
                    "${greeting()}, ${userName.ifBlank { "friend" }}",
                    style = MaterialTheme.typography.displaySmall,
                    color = TextPrimary,
                )
            }
            Box(
                Modifier.padding(top = 6.dp).size(48.dp)
                    .clip(CircleShape)
                    .background(CardFill)
                    .border(1.dp, LineStroke, CircleShape)
                    .clickable { onOpen("search") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Search, contentDescription = "Search the library",
                    tint = TextSoft, modifier = Modifier.size(19.dp))
            }
        }

        // Home leads with the goal-aware next action (mirrors iOS DailyFocus);
        // tapping deep-links to the surface that runs the next undone step.
        plan?.let { p ->
            val steps = p.optJSONArray("steps")
            val total = steps?.length() ?: 0
            val done = (0 until total).count { steps!!.getJSONObject(it).optBoolean("done") }
            val next = (0 until total).map { steps!!.getJSONObject(it) }
                .firstOrNull { !it.optBoolean("done") }
            HeroCard(
                imageUrl = HeroImg.calm,
                eyebrow = "Today's plan",
                title = p.optString("title"),
                subtitle = p.optString("focus"),
                height = 190.dp,
                onClick = { onOpen("plan") },   // full plan route (ref/iOS parity)
            ) {
                val tail = buildString {
                    if (next != null) append("Next: ${next.optString("title")}")
                    if (total > 0) { if (isNotEmpty()) append("  ·  "); append("$done of $total done") }
                }
                if (tail.isNotBlank()) Text(tail, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
            }
        }

        // Active multi-day journey (ref "PROGRAM · DAY 3 OF 7" card).
        program?.let { prog ->
            SectionCard(onClick = { onOpen("programs") }) {
                Text("PROGRAM · DAY ${prog.optInt("day")} OF ${prog.optInt("days")}",
                    style = MaterialTheme.typography.labelSmall, color = Cyan)
                Text(prog.optString("title"), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                LinearProgressIndicator(
                    progress = {
                        (prog.optInt("day").toFloat() / prog.optInt("days").coerceAtLeast(1)).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = Periwinkle,
                    trackColor = CardFill,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            QuickTile("Games", Icons.Outlined.SportsEsports, "games", onOpen, Modifier.weight(1f), Periwinkle, index = 0)
            QuickTile("Insights", Icons.Outlined.Insights, "insights", onOpen, Modifier.weight(1f), Cyan, index = 1)
            QuickTile("Programs", Icons.Outlined.CalendarMonth, "programs", onOpen, Modifier.weight(1f), Warm, index = 2)
            QuickTile("Sounds", Icons.Outlined.GraphicEq, "sounds", onOpen, Modifier.weight(1f), Iris, index = 3)
        }

        SectionCard {
            Text("How are you, really?", style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text("A 20-second check-in shapes today's plan.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MOODS.forEach { mood ->
                    PickChip(selected = picked == mood, label = mood.name) { picked = mood }
                }
            }
            PrimaryButton(
                text = if (busy) "One moment…" else "Check in",
                enabled = picked != null && !busy,
            ) {
                val mood = picked ?: return@PrimaryButton
                busy = true; status = null
                scope.launch {
                    try {
                        Api.checkIn(mood.name, mood.note, mood.symbol, mood.intensity)
                        Celebrations.trigger()
                        status = "Checked in — noted gently."
                        picked = null
                        reload()
                    } catch (e: Exception) {
                        status = e.message ?: "Couldn't check in."
                    } finally {
                        busy = false
                    }
                }
            }
            // The confirmation eases in rather than popping — a small, calm reward.
            AnimatedVisibility(visible = status != null) {
                Text(status.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
        }

        // Time-matched content rail (mirrors the iOS Home rails).
        ContentRail(onOpen)

        NavRow("Tools", "Small resets & reframes — 2 minutes each") { onOpen("tools") }

        // Morning sleep check-in nudge (mirrors the iOS Home sleep row).
        if (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) < 12) {
            NavRow("How was last night?", "A 20-second sleep check-in") { onOpen("sleep") }
        }

        SectionCard {
            Text(
                if (streak > 0) "$streak-day streak" else "Your streak starts today",
                style = MaterialTheme.typography.titleMedium, color = TextSoft,
            )
            milestoneLine(streak)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Cyan)
            }
            Text(
                if (best > 0) "Best: $best days · show up once a day, no pressure."
                else "Show up once a day — gentle, no pressure.",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            )
            // 7-dot week ring (server streak week; today is the last dot).
            if (week.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    week.forEachIndexed { i, (day, active) ->
                        Column(
                            Modifier.appear(i, rise = 8f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Box(
                                Modifier.size(14.dp).clip(CircleShape)
                                    .background(if (active) Periwinkle else CardFill)
                                    .border(1.dp, if (active) Periwinkle else LineStroke, CircleShape),
                            )
                            Text(day, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                }
            }
        }

        // Contextual baseline (mirrors iOS): offered once a few REAL check-ins
        // exist and only until it's saved — deliberately not an onboarding step.
        if (moodCount >= 3 && BaselineStore.get() == null) {
            NavRow(
                "Your starting point",
                "Two quick scales — see real change later",
            ) { onOpen("baseline") }
        }

        if (recent.isNotEmpty()) {
            SectionCard {
                Text("Recent check-ins", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                recent.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
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

@Composable
private fun QuickTile(
    label: String,
    icon: ImageVector,
    route: String,
    onOpen: (String) -> Unit,
    modifier: Modifier,
    accent: Color,
    index: Int = 0,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier
            .appear(index)
            .pressScale(pressed)
            .glass(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null) { onOpen(route) }
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Gradient icon chip — a small tinted well behind each glyph (fork look),
        // one accent hue per tile.
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.14f))))
                .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSoft, textAlign = TextAlign.Center)
    }
}
