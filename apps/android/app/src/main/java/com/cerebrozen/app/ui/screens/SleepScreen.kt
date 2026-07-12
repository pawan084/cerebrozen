package com.cerebrozen.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.audio.Player
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.Cream
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Ink
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleSoft
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

private val QUALITY_WORDS = listOf("Rough", "Poor", "Okay", "Good", "Rested")

internal fun minutesToLabel(total: Int): String = "%dh %02dm".format(total / 60, total % 60)

internal fun hhmm(minutes: Int): String {
    val m = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)
    return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
}

internal data class Night(
    val date: String,
    val duration: Int,
    val quality: Int,
    /** Bedtime / wake time as minutes since midnight — null when the log has none. */
    val bedMin: Int? = null,
    val wakeMin: Int? = null,
)

/** "HH:MM" or "HH:MM:SS" → minutes since midnight; null for anything unparseable. */
internal fun parseClockMinutes(s: String?): Int? {
    if (s.isNullOrBlank()) return null
    val parts = s.split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

internal fun parseNights(rows: JSONArray): List<Night> =
    (0 until rows.length()).map { i ->
        val n = rows.getJSONObject(i)
        Night(
            n.getString("date"), n.optInt("duration_min"), n.optInt("quality"),
            parseClockMinutes(n.optString("bedtime")),
            parseClockMinutes(n.optString("wake_time")),
        )
    }

// ── Rhythm math (CBT-I Phase 1, REDESIGN §3.2) — pure + unit-tested ─────

/** Average bedtime→wake duration in minutes across logs that carry both times,
 * wrapping past midnight (23:30→07:00 = 450, 00:30→08:00 = 450). Null when
 * no log qualifies. */
internal fun averageSleepMinutes(logs: List<Night>): Int? {
    val durations = logs.mapNotNull { n ->
        val bed = n.bedMin ?: return@mapNotNull null
        val wake = n.wakeMin ?: return@mapNotNull null
        ((wake - bed) % 1440 + 1440) % 1440
    }
    if (durations.isEmpty()) return null
    return durations.average().roundToInt()
}

/** Bedtime spread (max − min) in minutes, anchored at noon so bedtimes either
 * side of midnight stay close (23:30 vs 00:30 → 60, not 23 hours). Null when
 * no log carries a bedtime. */
internal fun bedtimeSpreadMinutes(logs: List<Night>): Int? {
    val anchored = logs.mapNotNull { it.bedMin }.map { ((it - 720) % 1440 + 1440) % 1440 }
    if (anchored.isEmpty()) return null
    return anchored.max() - anchored.min()
}

/** The one gentle CBT-I principle the data supports — consistency over duration. */
internal fun rhythmPrinciple(spreadMin: Int): String =
    if (spreadMin > 90) {
        "A steadier bedtime — even an imperfect one — does more for sleep than extra hours."
    } else {
        "Your bedtime is steady — that consistency is the strongest thing you're doing for your sleep."
    }

/** A human-sized duration for prose ("45m", "1h 50m") — no zero-hour prefix. */
internal fun spreadLabel(min: Int): String = if (min < 60) "${min}m" else minutesToLabel(min)

private const val CBT_I_WHY =
    "From CBT-I (cognitive behavioural therapy for insomnia) — the best-evidenced " +
        "approach in sleep apps (Lancet Digital Health, 2025)."

/** Sleep: morning check-in + honest weekly summary + diary, with a CBT-I-informed
 * layer on top — the job is improving sleep night by night, not measuring it. */
@Composable
fun SleepScreen(onOpen: (String) -> Unit = {}) {
    var quality by remember { mutableIntStateOf(0) }
    var bed by remember { mutableIntStateOf(23 * 60) }
    var wake by remember { mutableIntStateOf(7 * 60) }
    var summary by remember { mutableStateOf<JSONObject?>(null) }
    var nights by remember { mutableStateOf(listOf<Night>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Optional last-night prefill from Health Connect (Android's HealthKit analogue).
    val hcAvailable = remember { com.cerebrozen.app.health.HealthConnectSleep.available(context) }
    fun applyHealthConnect() {
        scope.launch {
            com.cerebrozen.app.health.HealthConnectSleep.readLastNight(context)?.let { (b, w) ->
                bed = b; wake = w; status = "Pre-filled from last night's sleep."
            } ?: run { status = "No recent sleep found in Health Connect." }
        }
    }
    val hcLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        if (granted.containsAll(com.cerebrozen.app.health.HealthConnectSleep.permissions)) applyHealthConnect()
    }

    suspend fun reload() {
        runCatching { summary = Api.sleepSummary() }
        runCatching { nights = parseNights(Api.sleepLogs()) }
    }

    LaunchedEffect(Unit) { reload() }

    Page("Improve your sleep, night by night", "Sleep", trailing = Icons.Outlined.DarkMode, accent = com.cerebrozen.app.ui.theme.Violet) {
        HeroCard(
            imageUrl = HeroImg.sleep,
            eyebrow = "Wind down",
            title = "A calmer night",
            subtitle = "A slower evening makes for a softer morning.",
            height = 220.dp,
        ) {
            // A "TONIGHT" badge + a prominent play pill — the richer hero look, on
            // our tokens. Still just plays the ambient bed (no behaviour change).
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text("TONIGHT", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Cream)
                        .clickable { Player.play(context, "A calmer night") }
                        .semantics { contentDescription = "Play the ambient bed" }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null,
                            tint = Ink, modifier = Modifier.size(18.dp))
                        Text("Play", color = Ink, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        SectionCard {
            Text("Morning check-in", style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text("How rested do you feel?", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // W10: the quality chips rise in with the shared staggered entrance
                // (instant under Reduce Motion — handled inside appear).
                QUALITY_WORDS.forEachIndexed { i, word ->
                    Box(Modifier.appear(i, rise = 10f)) {
                        PickChip(selected = quality == i + 1, label = word) { quality = i + 1 }
                    }
                }
            }
            TimeRow("In bed around", bed, { bed = it })
            TimeRow("Woke up around", wake, { wake = it })
            if (hcAvailable) {
                TextButton(onClick = {
                    scope.launch {
                        if (com.cerebrozen.app.health.HealthConnectSleep.hasPermission(context)) applyHealthConnect()
                        else hcLauncher.launch(com.cerebrozen.app.health.HealthConnectSleep.permissions)
                    }
                }) { Text("Pre-fill from Health Connect", color = Cyan) }
            }
            PrimaryButton(
                text = if (busy) "One moment…" else "Save night",
                enabled = quality > 0 && !busy,
            ) {
                busy = true; status = null
                scope.launch {
                    try {
                        Api.logSleep(LocalDate.now().toString(), hhmm(bed), hhmm(wake), quality)
                        Celebrations.trigger()
                        status = "Logged — one honest night at a time."
                        quality = 0
                        reload()
                    } catch (e: Exception) {
                        status = e.message ?: "Couldn't log."
                    } finally {
                        busy = false
                    }
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
        }

        summary?.let { s ->
            SectionCard {
                Text("This week", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                if (s.optBoolean("enough_data")) {
                    Text(
                        "${minutesToLabel(s.optInt("avg_duration_min"))} avg · feeling ${"%.1f".format(s.optDouble("avg_quality"))}/5 · trend ${s.optString("trend")}",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                } else {
                    Text(
                        "Log a few more nights and honest averages appear here — no guesses.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                }
            }
        }

        // Consistency insight (CBT-I Phase 1): what the last week's rhythm says,
        // with one gentle principle — consistency beats duration. Client-side only.
        run {
            val recent = nights.take(7)
            val avgSleep = averageSleepMinutes(recent)
            val spread = bedtimeSpreadMinutes(recent)
            if (recent.size >= 3 && avgSleep != null && spread != null) {
                SectionCard {
                    Text("Your rhythm", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(
                        "You averaged ${spreadLabel(avgSleep)}. Your bedtime varied by about ${spreadLabel(spread)} this week.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                    Text(rhythmPrinciple(spread), style = MaterialTheme.typography.bodyMedium, color = PeriwinkleSoft)
                }
            }
        }

        if (nights.size >= 2) NightsChart(nights)

        // Mix-your-own layered soundscape — lives in the Sounds hub's Mixer
        // section now (REDESIGN §3.4); this door opens it directly.
        NavRow("Mix your own soundscape", "Blend rain, ocean, wind & a soft drone") { onOpen("sounds/mixer") }

        SleepSectionHeader("♫", "Sounds for sleep")
        NowPlayingBar(onOpenPlayer = { onOpen("player") })
        ContentList("sleep", { d -> if (d > 0) "$d min" else "Sleep story" },
            onItemTap = { title -> Player.toggle(context, title) })

        // CBT-I-informed wind-down guide (served `wind_down` content, read-only).
        SleepSectionHeader("☾", "Wind-down guide")
        ContentList("wind_down", { d -> if (d > 0) "$d min" else "Guide" })

        // Stimulus-control micro-education (CBT-I Phase 1) — two small, steady
        // ideas, each with an honest provenance footer.
        SectionCard {
            Text("Bed is for sleep", style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(
                "If you're wide awake for 20+ minutes, get up, do something quiet and dim, come back sleepy.",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            )
        }
        WhyThisWorks(CBT_I_WHY)
        SectionCard {
            Text("Same wake time, even after a short night", style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(
                "Your body clock anchors on when you get up.",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            )
        }
        WhyThisWorks(CBT_I_WHY)

        if (nights.isNotEmpty()) {
            SectionCard {
                Text("Diary", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                nights.take(7).forEach { n ->
                    Text(
                        "${n.date} · ${minutesToLabel(n.duration)} · felt ${n.quality}/5",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                }
            }
        }
    }
}

/** A live bar chart of the last few nights' durations. W10: the bars grow from
 * the baseline once on first composition (40ms stagger, 400ms each); Reduce
 * Motion renders them at full height immediately — static, never blank.
 * Internal so the Robolectric reduce-motion suite can render it directly. */
@Composable
internal fun NightsChart(nights: List<Night>) {
    val recent = nights.take(7).reversed()
    val maxDur = (recent.maxOfOrNull { it.duration } ?: 1).coerceAtLeast(1)
    val avg = recent.map { it.duration }.average().toInt()
    val reduceMotion = rememberReduceMotion()
    SectionCard {
        Text("Last 7 nights", style = MaterialTheme.typography.titleMedium, color = TextSoft)
        Row(
            Modifier.fillMaxWidth().height(120.dp)
                .semantics {
                    contentDescription =
                        "Sleep durations, last ${recent.size} nights, average ${minutesToLabel(avg)}"
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            recent.forEachIndexed { i, n ->
                val frac = (n.duration.toFloat() / maxDur).coerceIn(0.08f, 1f)
                // Starts at full height under Reduce Motion so the first frame is
                // already the resting chart (no clock ever advances it).
                val grow = remember { Animatable(if (reduceMotion) 1f else 0f) }
                LaunchedEffect(reduceMotion) {
                    if (reduceMotion) { grow.snapTo(1f); return@LaunchedEffect }
                    kotlinx.coroutines.delay(i * 40L)
                    grow.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                }
                Box(
                    Modifier.weight(1f).fillMaxHeight(frac * grow.value).clip(RoundedCornerShape(6.dp))
                        .background(Brush.verticalGradient(listOf(Periwinkle, Cyan)))
                        .testTag("night-bar-$i"),
                )
            }
        }
        Text("avg ${minutesToLabel(avg)} · ${recent.size} nights",
            style = MaterialTheme.typography.labelSmall, color = Periwinkle)
    }
}

/** A richer standalone section header — a soft lavender glyph leading a title —
 * mirroring the redesign's sub-section headers, on our tokens. */
@Composable
private fun SleepSectionHeader(glyph: String, title: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Periwinkle.copy(alpha = 0.20f))
                .border(1.dp, LineStroke, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, style = MaterialTheme.typography.titleMedium, color = PeriwinkleSoft)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextSoft)
    }
}

@Composable
private fun TimeRow(label: String, minutes: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        TextButton(
            onClick = { onChange(minutes - 30) },
            modifier = Modifier.semantics { contentDescription = "Set $label 30 minutes earlier" },
        ) { Text("−30m") }
        Text(hhmm(minutes), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        TextButton(
            onClick = { onChange(minutes + 30) },
            modifier = Modifier.semantics { contentDescription = "Set $label 30 minutes later" },
        ) { Text("+30m") }
    }
}
