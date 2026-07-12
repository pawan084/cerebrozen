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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.theme.Accent
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

// E2's one-shot save bloom now lives in Common.kt as the shared [BloomRing]
// (W10) — Home and Journal arm the same calm ring.

/** E3: the presence card's 7-dot week ring — each dot fades/scales in with a
 * one-shot 40ms stagger on first composition (instant under Reduce Motion).
 * Extracted so the reduce-motion branch is testable off-device. */
@Composable
internal fun PresenceWeekRing(week: List<Pair<String, Boolean>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        week.forEachIndexed { i, (day, active) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(
                    Modifier
                        .popIn(i)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (active) Periwinkle else CardFill)
                        .border(1.dp, if (active) Periwinkle else LineStroke, CircleShape)
                        .testTag("presence-dot-$i"),
                )
                Text(day, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}

/** Time-matched rail kind + heading (mirrors the iOS Home rails). */
// i18n: pending — pure function, needs context plumbing (headings are user copy)
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
                    Text(
                        if (d > 0) stringResource(R.string.common_minutes, d) else stringResource(R.string.today_rail_ambient),
                        style = MaterialTheme.typography.labelSmall, color = TextMuted,
                    )
                }
            }
        }
    }
}

/** Today, de-densified (REDESIGN §3.1): greeting → mood check-in → plan hero →
 * one content rail → presence → recent check-ins. One quiet Toolkit row instead
 * of a tile grid. */
@Composable
fun TodayScreen(onOpen: (String) -> Unit) {
    var userName by remember { mutableStateOf("") }
    var streak by remember { mutableIntStateOf(0) }
    var recent by remember { mutableStateOf(listOf<String>()) }
    var plan by remember { mutableStateOf<JSONObject?>(null) }
    var picked by remember { mutableStateOf<MoodOption?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var week by remember { mutableStateOf(listOf<Pair<String, Boolean>>()) }
    var goal by remember { mutableStateOf("") }
    var program by remember { mutableStateOf<JSONObject?>(null) }
    // Optimistically true so the morning banner never flashes before data loads.
    var lastNightLogged by remember { mutableStateOf(true) }
    var bloom by remember { mutableIntStateOf(0) }        // E2: one-shot per successful check-in
    var dismissTick by remember { mutableIntStateOf(0) }  // re-reads banner dismissals after prefPut
    val scope = rememberCoroutineScope()

    fun parseRecent(moods: JSONArray): List<String> =
        (0 until minOf(moods.length(), 3)).map { i ->
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
                    (if (goal.isBlank()) stringResource(R.string.today_eyebrow)
                    else stringResource(R.string.today_eyebrow_goal, goal)).uppercase(),
                    style = MaterialTheme.typography.labelSmall, color = Periwinkle,
                )
                val friend = stringResource(R.string.today_friend)
                Text(
                    stringResource(R.string.today_greeting_format, greeting(), userName.ifBlank { friend }),
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
                Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.today_search_cd),
                    tint = TextSoft, modifier = Modifier.size(19.dp))
            }
        }

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
            )
            HomeBanner.PROGRAM -> program?.let { prog ->
                // B4: the day strip is status, not a nudge — never dismissible.
                InfoBanner(
                    icon = Icons.Outlined.CalendarMonth,
                    text = stringResource(
                        R.string.today_banner_program,
                        prog.optInt("day"), prog.optInt("days"), prog.optString("title"),
                    ),
                    actionLabel = stringResource(R.string.common_open),
                    onAction = { onOpen("programs") },
                )
            }
            HomeBanner.NONE -> {}
        }

        // The primary daily action leads (REDESIGN §3.1): the 1-tap check-in.
        Box {
        SectionCard {
            Text(stringResource(R.string.today_checkin_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(stringResource(R.string.today_checkin_subtitle), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // E7: the mood chips rise in with the shared staggered entrance.
                MOODS.forEachIndexed { i, mood ->
                    Box(Modifier.appear(i, rise = 10f)) {
                        PickChip(selected = picked == mood, label = mood.name) { picked = mood }
                    }
                }
            }
            val checkedIn = stringResource(R.string.today_checkin_done)
            val checkinFailed = stringResource(R.string.today_checkin_failed)
            PrimaryButton(
                text = if (busy) stringResource(R.string.common_one_moment) else stringResource(R.string.today_checkin_cta),
                enabled = picked != null && !busy,
            ) {
                val mood = picked ?: return@PrimaryButton
                busy = true; status = null
                scope.launch {
                    try {
                        Api.checkIn(mood.name, mood.note, mood.symbol, mood.intensity)
                        // E2: a small inline bloom + the success pulse — a calm
                        // daily reward, not the full-screen celebration.
                        Haptics.success()
                        if (!reduceMotion) bloom++
                        status = checkedIn
                        picked = null
                        reload()
                    } catch (e: Exception) {
                        status = e.message ?: checkinFailed
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
        // E2: the one-shot bloom rides over the card; Reduce Motion never arms it.
        if (bloom > 0) BloomRing(bloom, Accent.home, Modifier.matchParentSize())
        }

        // The goal-aware next action (mirrors iOS DailyFocus); tapping
        // deep-links to the full plan.
        plan?.let { p ->
            val steps = p.optJSONArray("steps")
            val total = steps?.length() ?: 0
            val done = (0 until total).count { steps!!.getJSONObject(it).optBoolean("done") }
            val next = (0 until total).map { steps!!.getJSONObject(it) }
                .firstOrNull { !it.optBoolean("done") }
            HeroCard(
                imageUrl = HeroImg.calm,
                eyebrow = stringResource(R.string.today_plan_eyebrow),
                title = p.optString("title"),
                subtitle = p.optString("focus"),
                height = 190.dp,
                onClick = { onOpen("plan") },   // full plan route (ref/iOS parity)
            ) {
                val nextLabel = next?.let { stringResource(R.string.today_plan_next, it.optString("title")) }
                val doneLabel = if (total > 0) stringResource(R.string.today_plan_done_count, done, total) else null
                val tail = buildString {
                    if (nextLabel != null) append(nextLabel)
                    if (doneLabel != null) { if (isNotEmpty()) append("  ·  "); append(doneLabel) }
                }
                // This sits on the hero's constant-dark photo scrim, so use the art-text
                // constant — themed TextSoft resolves to ink on Dawn and vanishes here.
                if (tail.isNotBlank()) Text(tail, style = MaterialTheme.typography.bodyMedium, color = com.cerebrozen.app.ui.theme.ArtTextSoft)
            }
        }

        // An active journey now lives in the banner slot under the greeting
        // (W9 B4) — the full surface stays in `programs`.

        // Time-matched content rail (mirrors the iOS Home rails).
        ContentRail(onOpen)

        NavRow(stringResource(R.string.today_toolkit_title), stringResource(R.string.today_toolkit_subtitle)) { onOpen("toolkit") }

        // Presence (REDESIGN §3.6): count the days you showed up, never the
        // days you didn't. The ring fills; it never breaks or resets.
        SectionCard {
            val daysPresent = week.count { it.second }
            Text(
                if (daysPresent > 0 || streak > 0) stringResource(R.string.today_presence_title)
                else stringResource(R.string.today_presence_ready),
                style = MaterialTheme.typography.titleMedium, color = TextSoft,
            )
            milestoneLine(streak)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Cyan)
            }
            Text(
                if (daysPresent > 0)
                    pluralStringResource(R.plurals.today_presence_days, daysPresent, daysPresent)
                else stringResource(R.string.today_presence_empty),
                style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            )
            // 7-dot week ring — fills for days present; today is the last dot.
            // E3: dots fill with a one-shot 40ms stagger (instant under Reduce Motion).
            if (week.isNotEmpty()) PresenceWeekRing(week)
        }

        if (recent.isNotEmpty()) {
            SectionCard {
                Text(stringResource(R.string.today_recent_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
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
