package com.cerebro.app.ui.screens

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cerebro.app.net.Api
import com.cerebro.app.ui.theme.CardFill
import com.cerebro.app.ui.theme.Cyan
import com.cerebro.app.ui.theme.Ink
import com.cerebro.app.ui.theme.LineStroke
import com.cerebro.app.ui.theme.Periwinkle
import com.cerebro.app.ui.theme.TextMuted
import com.cerebro.app.ui.theme.TextPrimary
import com.cerebro.app.ui.theme.TextSoft
import com.cerebro.app.ui.theme.Warm
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

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

internal fun milestoneLine(streak: Int): String? =
    if (streak in setOf(3, 7, 14, 21, 30, 50, 100)) "🎉 $streak-day milestone — beautifully done" else null

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
        }.getOrDefault(".")
        letter to d.optBoolean("active")
    }
}

internal fun planStepRoute(symbol: String): String? = when {
    symbol.startsWith("wind") -> "games"
    symbol.startsWith("moon") || symbol == "bell" -> "sounds"
    symbol == "book" || symbol == "brain" -> "journal"
    symbol == "mic" || symbol.startsWith("person") || symbol == "heart" -> "talk"
    else -> null
}

internal fun railKindFor(hour: Int): Pair<String, String> = when {
    hour < 12 -> "meditation" to "For this morning"
    hour < 17 -> "soundscape" to "A midday reset"
    else -> "sleep" to "For tonight"
}

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
    var program by remember { mutableStateOf<JSONObject?>(null) }
    var showTour by remember { mutableStateOf(!TourState.isDone()) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    fun parseRecent(moods: JSONArray): List<String> =
        (0 until minOf(moods.length(), 5)).map { i ->
            val m = moods.getJSONObject(i)
            "${m.getString("mood")} - ${m.getString("note")}"
        }

    suspend fun reload() {
        runCatching { userName = Api.me().optString("name") }
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

    val displayName = userName.ifBlank { "Pawan" }.trim().substringBefore(" ")
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF5D50A0), Color(0xFF2A185A), Color(0xFF0B061E)),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = pageHorizontalPadding(), vertical = pageVerticalPadding()),
            verticalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "THIS AFTERNOON - REDUCE STRESS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.32f),
                    )
                    Text(
                        "Ease into it,\n$displayName",
                        style = MaterialTheme.typography.displaySmall,
                        color = TextPrimary,
                        maxLines = 2,
                    )
                }
                Box(
                    Modifier
                        .padding(top = 36.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { onOpen("search") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search the library", tint = TextSoft, modifier = Modifier.size(21.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickTile("Games", Icons.Outlined.SportsEsports, "games", onOpen, Modifier.weight(1f))
                QuickTile("Insights", Icons.Outlined.Insights, "insights", onOpen, Modifier.weight(1f))
                QuickTile("Programs", Icons.Outlined.CalendarMonth, "programs", onOpen, Modifier.weight(1f))
                QuickTile("Sounds", Icons.Outlined.GraphicEq, "sounds", onOpen, Modifier.weight(1f))
            }

            StressAlertCard(onOpen = { onOpen("breathing") })
            CheckInHeroCard(
                busy = busy,
                status = status,
                onClick = { onOpen("howyoufeel") },
            )
            StreakPreviewCard(streak = streak, best = best, week = week)
            InsightSummaryCard(onOpen = { onOpen("insights") })
            PlanSummaryCard(plan = plan, onOpen = { onOpen("plan") })
            ProgramSummaryCard(program = program, onOpen = { onOpen("programs") })
            ContentRail(onOpen)
            HomeActionCard("♡", "Check how you feel", "Personalize your next best action") { onOpen("howyoufeel") }
            HomeActionCard("☾", "How did you sleep?", "A 20-second morning check-in") { onOpen("morningcheckin") }
            if (moodCount >= 3 && BaselineStore.get() == null) {
                NavRow("Your starting point", "Two quick scales - see real change later") { onOpen("baseline") }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (showTour) {
            GuidedTourOverlay(onDone = { showTour = false })
        }
    }
}

@Composable
private fun StressAlertCard(onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.065f))))
            .border(1.dp, Warm.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Warm))
            Text("ELEVATED STRESS DETECTED", style = MaterialTheme.typography.labelSmall, color = Warm)
        }
        Text(
            "Your heart rate variability dipped - a good moment to reset.",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Text("From Apple Watch - a few minutes ago", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.White)
                    .clickable { onOpen() },
                contentAlignment = Alignment.Center,
            ) {
                Text("Start 2-min reset", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Ink)
            }
            Box(
                Modifier
                    .width(92.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Not now", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun CheckInHeroCard(busy: Boolean, status: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(298.dp)
            .shadow(16.dp, shape, ambientColor = Color(0x40000000), spotColor = Color(0x40000000))
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .clickable(enabled = !busy) { onClick() },
    ) {
        AsyncImage(model = HeroImg.calm, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x22F7D5B6), Color(0x88484657), Color(0xF2111021))),
            ),
        )
        Box(
            Modifier
                .padding(20.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.72f))
                .border(1.dp, Color.White.copy(alpha = 0.58f), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("START HERE", style = MaterialTheme.typography.labelSmall, color = Color.Black)
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(21.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("How are you, really?", style = MaterialTheme.typography.displaySmall, color = Color.Black)
            Text("A 20-second check-in shapes today's plan.", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Ink, modifier = Modifier.size(17.dp))
                        Text(if (busy) "One moment..." else "Check in", style = MaterialTheme.typography.titleMedium, color = Ink)
                    }
                }
                status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = TextSoft) }
            }
        }
    }
}

@Composable
fun HowYouFeelScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    var picked by remember { mutableStateOf<MoodOption?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF6657AA), Color(0xFF2B1E5C), Color(0xFF120820)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = pageHorizontalPadding(), vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("MOOD, INTENSITY AND TRIGGER", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.42f))
                    Text("How you feel", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFA89D8B), Color(0xFF238487))))
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFD2A07C)), contentAlignment = Alignment.Center) {
                    Text("♥", style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
                Text(
                    "Name how you feel — we'll shape\nyour next step.",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }

            MOODS.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { mood ->
                        MoodChoiceCard(
                            mood = mood,
                            selected = picked?.name == mood.name,
                            modifier = Modifier.weight(1f),
                        ) {
                            picked = mood
                            status = null
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.085f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Next best action", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Text(
                    if (picked == null) "Pick how you feel and I'll suggest a gentle\nnext step."
                    else "${picked!!.name}: start with a gentle support step.",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = TextMuted) }
            }

            Spacer(Modifier.height(1.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (picked == null) Color.White.copy(alpha = 0.58f) else Color.White)
                    .clickable(enabled = picked != null && !busy) {
                        val mood = picked ?: return@clickable
                        busy = true
                        scope.launch {
                            runCatching { Api.checkIn(mood.name, mood.note, mood.symbol, mood.intensity) }
                                .onSuccess {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpen("talk")
                                }
                                .onFailure { status = it.message ?: "Couldn't check in." }
                            busy = false
                        }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("♩", style = MaterialTheme.typography.titleMedium, color = Color(0xFF2B214E))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (busy) "One moment..." else "Start gentle support",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF2B214E),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(112.dp))
        }
    }
}

@Composable
private fun MoodChoiceCard(mood: MoodOption, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .height(120.dp)
            .clip(shape)
            .background(
                if (selected) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.20f), Color(0xFF4C75A1).copy(alpha = 0.28f)))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.05f))),
            )
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.10f), shape)
            .clickable { onClick() }
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(moodIcon(mood.name), style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(mood.name, style = MaterialTheme.typography.headlineSmall, color = Color.Black)
            Text(mood.note, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    }
}

private fun moodIcon(name: String): String = when (name) {
    "Good" -> "✦"
    "Anxious" -> "△"
    "Low" -> "☾"
    else -> "●"
}

@Composable
private fun StreakPreviewCard(streak: Int, best: Int, week: List<Pair<String, Boolean>>) {
    val activeDays = if (week.isNotEmpty()) week.map { it.second }.takeLast(7) else listOf(false, false, false, false, true, true, true)
    val days = listOf("S", "S", "M", "T", "W", "T", "F")
    Row(
        Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.13f), Color(0xFF5C75A4).copy(alpha = 0.34f))))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFE2D7FF)), contentAlignment = Alignment.Center) {
            Text("◒", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF2B214E))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (streak > 0) "$streak-day streak" else "3-day streak", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(
                if (best > 0) "Best: $best days —\nbeautifully done" else "3-day milestone —\nbeautifully done",
                style = MaterialTheme.typography.bodyMedium,
                color = Periwinkle,
                maxLines = 2,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                activeDays.take(7).forEach { active -> StreakDot(active) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEach { day ->
                    Text(day, style = MaterialTheme.typography.labelSmall, color = TextPrimary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun StreakDot(active: Boolean) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (active) Periwinkle else Color.Transparent)
            .border(1.dp, if (active) Periwinkle else Color.White.copy(alpha = 0.30f), CircleShape),
    )
}

@Composable
private fun HomeActionCard(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.11f), Color.White.copy(alpha = 0.055f))))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Periwinkle.copy(alpha = 0.34f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted, maxLines = 1)
        }
        Text(">", style = MaterialTheme.typography.headlineSmall, color = TextMuted)
    }
}

@Composable
private fun InsightSummaryCard(onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.06f))))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .clickable { onOpen() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Periwinkle.copy(alpha = 0.30f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Insights, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("This week: calmer evenings", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text("See what changed - weekly insights", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        Text(">", style = MaterialTheme.typography.titleMedium, color = TextMuted)
    }
}

@Composable
private fun PlanSummaryCard(plan: JSONObject?, onOpen: () -> Unit) {
    val focus = plan?.optString("focus")?.ifBlank { "Keep the calm" } ?: "Keep the calm"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.055f))))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .clickable { onOpen() }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("TODAY'S PLAN · ${focus.uppercase()}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.44f))
            Text(">", style = MaterialTheme.typography.titleMedium, color = TextMuted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StepBubble("1", active = true)
            Text("Gratitude", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("-", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            StepBubble("", active = false)
            Text("Pause", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextSoft)
            Text("-", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            StepBubble("", active = false)
            Text("Wind\ndown", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextSoft, maxLines = 2)
        }
    }
}

@Composable
private fun StepBubble(label: String, active: Boolean) {
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (active) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .border(1.dp, Color.White.copy(alpha = if (active) 0.42f else 0.28f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
        }
    }
}

@Composable
private fun ProgramSummaryCard(program: JSONObject?, onOpen: () -> Unit) {
    val day = program?.optInt("day")?.takeIf { it > 0 } ?: 3
    val days = program?.optInt("days")?.takeIf { it > 0 } ?: 7
    val title = program?.optString("title")?.ifBlank { "7 days to calmer sleep" } ?: "7 days to calmer sleep"
    Box(
        Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF5A8BA4), Color(0xFF2A246C), Color(0xFF1B1248))))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
            .clickable { onOpen() },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .clickable { onOpen() }
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.32f))
                    .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("PROGRAM - DAY $day OF $days", style = MaterialTheme.typography.labelSmall, color = Color.Black)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.Black)
            LinearProgressIndicator(
                progress = { (day.toFloat() / days.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(0.58f),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
        }
    }
}

@Composable
private fun ContentRail(onOpen: (String) -> Unit) {
    val (kind, _) = remember { railKindFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var items by remember { mutableStateOf<JSONArray?>(null) }
    LaunchedEffect(kind) { runCatching { items = Api.content(kind) } }
    val fallback = listOf(
        Triple("Rain over quiet hills", "Sleep story - 18 min", HeroImg.calm),
        Triple("3-minute breath", "Stress reset", HeroImg.mood),
    )
    Text("Winding down", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val list = items
        if (list != null && list.length() > 0) {
            (0 until list.length()).forEach { i ->
                val c = list.getJSONObject(i)
                RailCard(
                    title = c.optString("title"),
                    subtitle = c.optInt("duration_min").takeIf { it > 0 }?.let { "${c.optString("kind", "Sound")} - $it min" } ?: "Ambient",
                    imageUrl = c.optString("image_url"),
                    onClick = { onOpen("sounds") },
                )
            }
        } else {
            fallback.forEach { (title, subtitle, imageUrl) ->
                RailCard(title, subtitle, imageUrl) { onOpen("sounds") }
            }
        }
    }
}

@Composable
private fun RailCard(title: String, subtitle: String, imageUrl: String, onClick: () -> Unit) {
    Column(
        Modifier
            .width(198.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(122.dp).clip(RoundedCornerShape(14.dp)),
        )
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 2)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
private fun QuickTile(label: String, icon: ImageVector, route: String, onOpen: (String) -> Unit, modifier: Modifier) {
    Column(
        modifier
            .shadow(11.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF6654A8).copy(alpha = 0.72f), Color(0xFF40336F).copy(alpha = 0.86f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .clickable { onOpen(route) }
            .height(84.dp)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF8170D4), Color(0xFF5E50A8)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun Card(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.055f))))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}
