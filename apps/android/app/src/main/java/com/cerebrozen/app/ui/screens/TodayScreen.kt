package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.CardFill
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

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
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
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    fun parseRecent(moods: JSONArray): List<String> =
        (0 until minOf(moods.length(), 5)).map { i ->
            val m = moods.getJSONObject(i)
            "${m.getString("mood")} · ${m.getString("note")}"
        }

    suspend fun reload() {
        runCatching { userName = Api.me().optString("name") }
        runCatching {
            val s = Api.streak()
            streak = s.optInt("current")
            best = s.optInt("best")
        }
        runCatching { recent = parseRecent(Api.moods()) }
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
        Text("TODAY", style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        Text(
            "${greeting()},\n${userName.ifBlank { "friend" }}",
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
        )

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
                    FilterChip(
                        selected = picked == mood,
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); picked = mood },
                        label = { Text(mood.name) },
                    )
                }
            }
            Button(
                enabled = picked != null && !busy,
                onClick = {
                    val mood = picked ?: return@Button
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
                },
            ) { Text(if (busy) "One moment…" else "Check in") }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
        }

        plan?.let { p ->
            val steps = p.optJSONArray("steps")
            val total = steps?.length() ?: 0
            val done = (0 until total).count { steps!!.getJSONObject(it).optBoolean("done") }
            val nextStep = (0 until total).map { steps!!.getJSONObject(it) }
                .firstOrNull { !it.optBoolean("done") }?.optString("title")
            Card {
                Text("TODAY'S PLAN", style = MaterialTheme.typography.labelSmall, color = Periwinkle)
                Text(p.optString("title"), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(p.optString("focus"), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                if (nextStep != null) Text("Next: $nextStep", style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                if (total > 0) Text("$done of $total done", style = MaterialTheme.typography.labelSmall, color = Periwinkle)
            }
        }

        Card {
            Text(
                if (streak > 0) "$streak-day streak" else "Your streak starts today",
                style = MaterialTheme.typography.titleMedium, color = TextSoft,
            )
            Text(
                if (best > 0) "Best: $best days · show up once a day, no pressure."
                else "Show up once a day — gentle, no pressure.",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            )
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
    Surface(
        color = CardFill,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .border(1.dp, LineStroke, RoundedCornerShape(16.dp))
            .clickable { onOpen(route) },
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Periwinkle, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSoft, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        color = CardFill,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, LineStroke, RoundedCornerShape(20.dp)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}
