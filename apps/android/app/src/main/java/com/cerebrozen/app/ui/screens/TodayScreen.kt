package com.cerebrozen.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.cerebrozen.app.audio.Player
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardFill)
                    .border(1.dp, LineStroke, RoundedCornerShape(16.dp))
                    .clickable { onOpen("sounds") }
                    .padding(0.dp),
            ) {
                Box(Modifier.fillMaxWidth().size(width = 150.dp, height = 84.dp)) {
                    val url = c.optString("image_url")
                    if (url.isNotBlank()) {
                        AsyncImage(model = url, contentDescription = null,
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
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

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
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Goal-aware eyebrow + serif greeting (mirrors iOS DailyFocus header).
        Text(
            (if (goal.isBlank()) "Today" else "Today · $goal").uppercase(),
            style = MaterialTheme.typography.labelSmall, color = Periwinkle,
        )
        Text(
            "${greeting()}, ${userName.ifBlank { "friend" }}",
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
        )

        // Home leads with the goal-aware next action (mirrors iOS DailyFocus);
        // tapping deep-links to the surface that runs the next undone step.
        plan?.let { p ->
            val steps = p.optJSONArray("steps")
            val total = steps?.length() ?: 0
            val done = (0 until total).count { steps!!.getJSONObject(it).optBoolean("done") }
            val next = (0 until total).map { steps!!.getJSONObject(it) }
                .firstOrNull { !it.optBoolean("done") }
            val nextRoute = next?.optString("symbol")?.let { planStepRoute(it) }
            HeroCard(
                imageUrl = HeroImg.calm,
                eyebrow = "Today's plan",
                title = p.optString("title"),
                subtitle = p.optString("focus"),
                height = 190.dp,
                onClick = nextRoute?.let { { onOpen(it) } },
            ) {
                val tail = buildString {
                    if (next != null) append("Next: ${next.optString("title")}")
                    if (total > 0) { if (isNotEmpty()) append("  ·  "); append("$done of $total done") }
                }
                if (tail.isNotBlank()) Text(tail, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            QuickTile("Games", Icons.Outlined.SportsEsports, "games", onOpen, Modifier.weight(1f))
            QuickTile("Insights", Icons.Outlined.Insights, "insights", onOpen, Modifier.weight(1f))
            QuickTile("Programs", Icons.Outlined.CalendarMonth, "programs", onOpen, Modifier.weight(1f))
            QuickTile("Sounds", Icons.Outlined.GraphicEq, "sounds", onOpen, Modifier.weight(1f))
        }

        Card {
            Text("How are you, really?", style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text("A 20-second check-in shapes today's plan.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MOODS.forEach { mood ->
                    PickChip(selected = picked == mood, label = mood.name) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); picked = mood
                    }
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
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
        }

        // Time-matched content rail (mirrors the iOS Home rails).
        ContentRail(onOpen)

        NavRow("Tools", "Small resets & reframes — 2 minutes each") { onOpen("tools") }

        // Morning sleep check-in nudge (mirrors the iOS Home sleep row).
        if (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) < 12) {
            NavRow("How was last night?", "A 20-second sleep check-in") { onOpen("sleep") }
        }

        Card {
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
                    week.forEach { (day, active) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
            Card {
                Text("Recent check-ins", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                recent.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun QuickTile(label: String, icon: ImageVector, route: String, onOpen: (String) -> Unit, modifier: Modifier) {
    Column(
        modifier
            .glass(RoundedCornerShape(16.dp))
            .clickable { onOpen(route) }
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Periwinkle, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSoft, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}
