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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.TextButton
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.theme.Accent
import com.cerebrozen.app.ui.theme.CardFill
import androidx.compose.ui.graphics.Color
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.Warm
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.Calendar

/** Mirrors iOS `Dummy.moods` (cross-stack mood taxonomy).
 *
 * [name]/[note]/[symbol] are WIRE VALUES — they go to the backend and are
 * hand-duplicated across iOS/web (see CLAUDE.md), so they are never translated.
 * [labelRes]/[noteRes] are the display copy and localize freely. */
private data class MoodOption(
    val name: String,
    val note: String,
    val symbol: String,
    val intensity: Int,
    @androidx.annotation.StringRes val labelRes: Int,
    @androidx.annotation.StringRes val noteRes: Int,
    /** The tile's own hue. Four identically grey pills made the most important
     * interaction in the product read as a settings row; colour is the fastest
     * way to make a feeling look like a feeling. Every value is a themed token,
     * so both palettes stay contrast-gated. */
    val tint: @Composable () -> Color,
)

/** Process-lifetime flag: the Home settle-in animation plays once per app
 * session, not once per tab visit. */
private var homeIntroPlayed = false

private val MOODS = listOf(
    MoodOption("Good", "Clear", "sparkles", 2, R.string.mood_good, R.string.mood_good_note) { Ok },
    MoodOption("Anxious", "Loud thoughts", "exclamationmark.triangle", 4, R.string.mood_anxious, R.string.mood_anxious_note) { Warm },
    MoodOption("Low", "Heavy", "moon", 4, R.string.mood_low, R.string.mood_low_note) { Periwinkle },
    MoodOption("Tired", "Need rest", "drop", 3, R.string.mood_tired, R.string.mood_tired_note) { Cyan },
)

/** Which greeting the hour calls for. Returns the resource, not the copy, so
 * the decision stays a pure unit-testable function AND localizes. */
@androidx.annotation.StringRes
internal fun greetingFor(hour: Int): Int = when (hour) {
    in 5..11 -> R.string.today_greeting_morning
    in 12..16 -> R.string.today_greeting_afternoon
    else -> R.string.today_greeting_evening
}

@Composable
private fun greeting(): String =
    stringResource(greetingFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))

/** The goal eyebrow's framing rotates by day — focus / working-on / coming-back —
 * so day 40 doesn't read like day 1. Pure; the templates localize. */
@androidx.annotation.StringRes
internal fun eyebrowTemplateRes(dayOfYear: Int): Int = when (dayOfYear % 3) {
    0 -> R.string.today_eyebrow_goal
    1 -> R.string.today_eyebrow_goal_working
    else -> R.string.today_eyebrow_goal_return
}

/** Whether the greeting still threads the last check-in under it: anything from
 * today, plus yesterday's through the small hours (before 4am the day hasn't
 * really turned — a 11:58pm check-in shouldn't vanish at midnight sharp). Pure. */
internal fun showEarlierLine(t: RelTime?, hour: Int): Boolean = when (t) {
    null -> false
    RelTime.Yesterday -> hour < 4
    is RelTime.Days -> false
    else -> true
}

/** Whether [streak] is a milestone worth a gentle line — presence framing
 * (REDESIGN §3.6): counts showing up, never chains or misses. Pure; the copy
 * itself lives in `today_milestone` so it can localize. */
internal fun isMilestone(streak: Int): Boolean = streak in setOf(3, 7, 14, 21, 30, 50, 100)

/** The milestone line, or null on an ordinary day. */
@Composable
internal fun milestoneLine(streak: Int): String? =
    if (isMilestone(streak)) stringResource(R.string.today_milestone, streak) else null

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
    hour >= com.cerebrozen.app.ui.theme.WIND_DOWN_FROM_HOUR && "winddown" !in dismissed -> HomeBanner.WIND_DOWN
    enrolledInProgram -> HomeBanner.PROGRAM
    else -> HomeBanner.NONE
}

/** One "Recent check-ins" line: "Good · Clear", or just "Good" when there is no
 * note.
 *
 * Was `"${m.getString("mood")} · ${m.getString("note")}"`, which had two faults.
 * A note-less check-in rendered as "anxious · " — a dangling separator pointing
 * at nothing, seen on device. And `getString` THROWS on a null field, inside a
 * `runCatching` that swallows it, so one null note would have made the whole
 * section vanish rather than degrade. Pure. */
internal fun checkInLine(m: JSONObject): String {
    val mood = m.optString("mood").trim()
    // "From onboarding" was a debug-flavoured note this client wrote before
    // 2026-08-02; historic rows still carry it, and it reads like leaked
    // internals. Render those rows as note-less rather than reprinting it.
    val note = m.optString("note").trim().takeUnless { it.equals("From onboarding", ignoreCase = true) }.orEmpty()
    return if (note.isEmpty()) mood else "$mood · $note"
}

/** A recent check-in with everything its row renders: the line, the wire mood
 * name + note (for tint and display-copy lookup), and when it happened. */
internal data class RecentCheckIn(
    val line: String,
    val mood: String,
    val createdAt: String,
    val note: String = "",
)

/** The mood's tile tint for a WIRE name, or null for names this build doesn't
 * know (a server value from a newer taxonomy renders untinted, never crashes). */
internal fun moodTintFor(name: String): (@Composable () -> Color)? =
    MOODS.firstOrNull { it.name.equals(name, ignoreCase = true) }?.tint

/** Display-copy resource for a WIRE mood name — the server stores taxonomy wire
 * values ("Good"), which happen to be English; rendering them raw would freeze
 * the Hindi UI's check-in rows in English. Null for unknown names (render raw). */
@androidx.annotation.StringRes
internal fun moodLabelResFor(name: String): Int? =
    MOODS.firstOrNull { it.name.equals(name, ignoreCase = true) }?.labelRes

/** Same for the note, matched only when it is the taxonomy's own preset note —
 * a note the user (or an older build) wrote freely renders verbatim. */
@androidx.annotation.StringRes
internal fun moodNoteResFor(name: String, note: String): Int? =
    MOODS.firstOrNull { it.name.equals(name, ignoreCase = true) && it.note.equals(note, ignoreCase = true) }?.noteRes

/** The localized row line: wire values mapped to display copy where the
 * taxonomy knows them, raw values passed through where it doesn't. */
@Composable
internal fun displayCheckInLine(entry: RecentCheckIn): String {
    val label = moodLabelResFor(entry.mood)?.let { stringResource(it) } ?: entry.mood
    val rawNote = entry.note.trim().takeUnless { it.equals("From onboarding", ignoreCase = true) }.orEmpty()
    val note = moodNoteResFor(entry.mood, rawNote)?.let { stringResource(it) } ?: rawNote
    return if (note.isBlank()) label else "$label · $note"
}

/** How long ago an ISO timestamp was, bucketed for a quiet label. Pure — the
 * composable maps buckets to localized strings. Null when the stamp is missing
 * or unparseable: an honest row shows no time rather than a wrong one. */
internal sealed interface RelTime {
    data object JustNow : RelTime
    data class Minutes(val m: Int) : RelTime
    data class Hours(val h: Int) : RelTime
    data object Yesterday : RelTime
    data class Days(val d: Int) : RelTime
}

internal fun relativeTime(iso: String?, now: java.time.OffsetDateTime): RelTime? {
    val then = runCatching { java.time.OffsetDateTime.parse(iso) }.getOrNull() ?: return null
    val mins = java.time.Duration.between(then, now).toMinutes()
    if (mins < 0) return null   // a future stamp is a clock bug, not "in 3 minutes"
    return when {
        mins < 2 -> RelTime.JustNow
        mins < 60 -> RelTime.Minutes(mins.toInt())
        mins < 24 * 60 -> RelTime.Hours((mins / 60).toInt())
        mins < 48 * 60 -> RelTime.Yesterday
        else -> RelTime.Days((mins / (24 * 60)).toInt())
    }
}

@Composable
private fun relativeTimeLabel(t: RelTime?): String? = when (t) {
    null -> null
    RelTime.JustNow -> stringResource(R.string.today_time_just_now)
    is RelTime.Minutes -> stringResource(R.string.today_time_minutes, t.m)
    is RelTime.Hours -> stringResource(R.string.today_time_hours, t.h)
    RelTime.Yesterday -> stringResource(R.string.today_time_yesterday)
    is RelTime.Days -> stringResource(R.string.today_time_days, t.d)
}

/** Which plan step to suggest right now: prefer an UNDONE step whose title
 * matches the current part of day (the generator names steps "Morning …",
 * "Midday …", "Evening …"), else the first undone. At 7 PM, "Next: Morning
 * Breathing Exercise" was the screen telling the truth in the least useful
 * order. Pure. */
internal fun nextPlanStepIndex(titles: List<String>, done: List<Boolean>, hour: Int): Int? {
    val keywords = when {
        hour < 12 -> listOf("morning")
        hour < 17 -> listOf("midday", "afternoon", "noon")
        else -> listOf("evening", "night", "wind")
    }
    val undone = titles.indices.filter { !(done.getOrNull(it) ?: false) }
    return undone.firstOrNull { i -> keywords.any { titles[i].contains(it, ignoreCase = true) } }
        ?: undone.firstOrNull()
}

/** The line under the plan title on Home: never an echo of the title.
 *
 * `title` and `focus` come back identical for rule-generated plans (the
 * generator names the plan after its focus goal), so rendering focus under the
 * title repeated it verbatim. Prefer the rationale — the "why this, today" line
 * — and fall back to focus only when it actually says something new. Pure. */
internal fun planSubtitle(plan: JSONObject): String {
    val title = plan.optString("title").trim()
    val rationale = plan.optString("rationale").trim()
    val focus = plan.optString("focus").trim()
    return when {
        rationale.isNotEmpty() && !rationale.equals(title, ignoreCase = true) -> rationale
        focus.isNotEmpty() && !focus.equals(title, ignoreCase = true) -> focus
        else -> ""
    }
}

/** From 17:00 an untouched plan stops reading as "0 of 3 done" — presence
 * framing counts what's ahead, not the zero. Pure boundary. */
internal fun planTailUsesLeftForm(done: Int, hour: Int): Boolean = done == 0 && hour >= 17

/** The hero art follows the plan's FOCUS: a sleep plan wears the moon, a calm /
 * stress / breath plan the meditation rings, anything else the program day
 * dots. Every plan used to wear the same purple regardless. Pure. */
internal fun planArtKind(focus: String): String {
    val f = focus.lowercase()
    return when {
        listOf("sleep", "night", "bed", "rest").any { it in f } -> "sleep"
        listOf("stress", "calm", "breath", "anxi", "mindful").any { it in f } -> "meditation"
        else -> "program"
    }
}

/** True when any sleep-log date covers "last night" — a log saved this morning
 * carries today's date; one saved before midnight carries yesterday's. Pure. */
internal fun hasLastNightLog(dates: List<String>, today: LocalDate): Boolean =
    dates.any { raw ->
        val d = runCatching { LocalDate.parse(raw) }.getOrNull()
        d == today || d == today.minusDays(1)
    }

/**
 * How many check-ins landed in the last seven local days — the honest number
 * behind the Insights teaser (web states the same count).
 *
 * Seven days *including today*, so the window matches the presence ring beside
 * it rather than quietly counting an eighth day. Rows whose timestamp will not
 * parse are not counted: a teaser that inflates the number is worse than one
 * that says nothing. Pure.
 */
internal fun checkInsThisWeek(moods: JSONArray, today: LocalDate): Int {
    val cutoff = today.minusDays(6)
    return (0 until moods.length()).count { i ->
        val iso = moods.optJSONObject(i)?.optString("created_at")
        val day = runCatching {
            java.time.OffsetDateTime.parse(iso)
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
                .toLocalDate()
        }.getOrNull()
        day != null && !day.isBefore(cutoff) && !day.isAfter(today)
    }
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
            // The window is rolling, so the letters can repeat (T W T F S S M) —
            // marking TODAY (always the last dot) is what makes the row readable
            // at a glance without re-anchoring the whole week.
            val isToday = i == week.lastIndex
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
                        .border(
                            if (isToday) 2.dp else 1.dp,
                            when {
                                active -> Periwinkle
                                isToday -> TextMuted
                                else -> LineStroke
                            },
                            CircleShape,
                        )
                        .testTag("presence-dot-$i"),
                )
                Text(
                    day,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) TextSoft else TextMuted,
                )
            }
        }
    }
}

/** Time-matched rail kind + heading (mirrors the iOS Home rails). The kind is a
 * backend content-kind WIRE VALUE; the heading is a resource id, so the pairing
 * stays a pure unit-testable function and the copy still localizes. */
internal fun railKindFor(hour: Int): Pair<String, Int> = when {
    // The small hours belong to the night before, not to the morning after.
    // Seen on device at 00:09: the theme had gone Night for wind-down while this
    // rail offered "For this morning · Body scan" — a 10-minute meditation, to
    // someone still awake past midnight.
    com.cerebrozen.app.ui.theme.isWindDownHour(hour) -> "sleep" to R.string.today_rail_tonight
    hour < 12 -> "meditation" to R.string.today_rail_morning
    hour < 17 -> "soundscape" to R.string.today_rail_midday
    else -> "sleep" to R.string.today_rail_tonight
}

/** A horizontal card rail of served content, matched to the time of day. */
@Composable
private fun ContentRail(onOpen: (String) -> Unit) {
    val (kind, headingRes) = remember { railKindFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    val heading = stringResource(headingRes)
    val route = if (kind == "sleep") "sleep" else "sounds"
    var items by remember { mutableStateOf<JSONArray?>(null) }
    LaunchedEffect(kind) { runCatching { items = Api.content(kind) } }
    val list = items ?: return
    if (list.length() == 0) return
    Text(heading, style = MaterialTheme.typography.titleMedium, color = TextSoft)
    // ONE item is not a rail: a lone 150dp card beside two-thirds of empty
    // track read as a broken carousel. A single item gets the full width.
    if (list.length() == 1) {
        val c = list.getJSONObject(0)
        val title = c.optString("title")
        Column(
            Modifier.fillMaxWidth()
                .glass(RoundedCornerShape(16.dp))
                .clickable { onOpen(route) },
        ) {
            Box(Modifier.fillMaxWidth().size(width = 150.dp, height = 110.dp)) {
                ContentArt(title = title, kind = kind,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
                val url = c.optString("image_url")
                if (url.isNotBlank()) {
                    AsyncImage(model = url, contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft, maxLines = 1)
                val d = c.optInt("duration_min")
                Text(
                    if (d > 0) stringResource(R.string.common_minutes, d) else stringResource(R.string.today_rail_ambient),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted,
                )
            }
        }
        return
    }
    // Edge to edge, with the page inset re-applied inside: the first card still
    // lines up with everything above it, and the last one is cut by the screen
    // rather than stopping neatly short — which is the only reliable way a row
    // says "there is more this way".
    Row(
        Modifier.bleed(24.dp).horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
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
                    // W21: designed generative art always; a real image (when the
                    // backend serves one AND it loads) simply covers it.
                    ContentArt(title = title, kind = kind,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
                    val url = c.optString("image_url")
                    if (url.isNotBlank()) {
                        AsyncImage(model = url, contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
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

/**
 * One mood, as a tile you can hit without aiming.
 *
 * A soft orb in the mood's own colour over a tinted well — large enough to be
 * the thing you look at, rather than a pill in a row of four. Tapping it IS the
 * check-in; there is no second step.
 */
@Composable
private fun MoodTile(mood: MoodOption, enabled: Boolean, marked: Boolean = false, onPick: () -> Unit) {
    val tint = mood.tint()
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .pressScale(pressed, down = 0.96f)
            // While a check-in is in flight the tiles are disabled — say so
            // (they used to look tappable and silently ignore the tap).
            .graphicsLayer { alpha = if (enabled) 1f else 0.55f }
            .clip(shape)
            .background(tint.copy(alpha = 0.14f))
            // [marked]: the mood already logged today wears a firmer ring, so
            // the second visit reads as a conversation, not a blank slate.
            .border(if (marked) 2.dp else 1.dp, tint.copy(alpha = if (marked) 0.70f else 0.35f), shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                Haptics.tap(); onPick()
            }
            // One TalkBack stop per tile ("Good, Clear, button"), not two.
            .semantics(mergeDescendants = true) { role = androidx.compose.ui.semantics.Role.Button }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(tint.copy(alpha = 0.95f), tint.copy(alpha = 0.30f)))),
        )
        Text(stringResource(mood.labelRes), style = MaterialTheme.typography.titleMedium, color = TextSoft, maxLines = 1)
        Text(stringResource(mood.noteRes), style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1)
    }
}

/** Today, de-densified (REDESIGN §3.1): greeting → mood check-in → plan hero →
 * one content rail → presence → recent check-ins. One quiet Toolkit row instead
 * of a tile grid. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(onOpen: (String) -> Unit) {
    var userName by remember { mutableStateOf("") }
    var streak by remember { mutableIntStateOf(0) }
    var recent by remember { mutableStateOf(listOf<RecentCheckIn>()) }
    var weekCheckIns by remember { mutableIntStateOf(0) }
    var plan by remember { mutableStateOf<JSONObject?>(null) }
    var planLoaded by remember { mutableStateOf(false) }
    // What was just logged, and its row id, so the tap can be taken back.
    var loggedMood by remember { mutableStateOf<MoodOption?>(null) }
    var loggedId by remember { mutableStateOf<String?>(null) }
    // True when the check-in is sitting in the offline queue rather than on the
    // server: the confirmation is the same, but Undo has to pull it back out of
    // the queue instead of deleting a row that does not exist yet.
    var loggedQueued by remember { mutableStateOf(false) }
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

    fun parseRecent(moods: JSONArray): List<RecentCheckIn> =
        (0 until minOf(moods.length(), 3)).map { i ->
            val m = moods.getJSONObject(i)
            RecentCheckIn(checkInLine(m), m.optString("mood"), m.optString("created_at"), m.optString("note"))
        }

    suspend fun reload() {
        // These endpoints are independent. Running them serially made Home take
        // the sum of six network round trips and caused cards to pop in one by
        // one. Fetch together, then publish each result as soon as this batch is
        // ready; total wait is now the slowest request, not all requests added.
        coroutineScope {
        val meRequest = async { runCatching { Api.me() } }
        val streakRequest = async { runCatching { Api.streak() } }
        val moodsRequest = async { runCatching { Api.moods() } }
        val planRequest = async { runCatching { Api.activePlan() } }
        val programRequest = async { runCatching { Api.activeProgram() } }
        val sleepRequest = async { runCatching { Api.sleepLogs() } }

        meRequest.await().onSuccess { me ->
            userName = me.optString("name")
            goal = me.optJSONArray("goals")?.optString(0).orEmpty()
        }
        streakRequest.await().onSuccess { s ->
            streak = s.optInt("current")
            week = parseWeek(s)
        }
        moodsRequest.await().onSuccess { moods ->
            // One fetch feeds both the recent-check-ins list and the teaser count.
            recent = parseRecent(moods)
            weekCheckIns = checkInsThisWeek(moods, LocalDate.now())
        }
        planRequest.await().onSuccess { plan = it }
        planLoaded = true
        programRequest.await().onSuccess { program = it }
        // One extra GET (cached like every read) so the morning banner knows
        // whether last night is already logged — B2.
        sleepRequest.await().onSuccess { logs ->
            lastNightLogged = hasLastNightLog(
                (0 until logs.length()).map { logs.getJSONObject(it).optString("date") },
                LocalDate.now(),
            )
        }
        }
    }

    LaunchedEffect(Unit) { reload() }
    var showTour by remember { mutableStateOf(!TourState.isDone()) }
    // Pull-to-refresh: a server-backed dashboard the user could not refresh
    // meant "kill the app" was the refresh gesture.
    var refreshing by remember { mutableStateOf(false) }

    // A gentle settle-in as the screen arrives — ONCE per app session. It used
    // to replay on every tab return, which made coming back from Sleep feel
    // like a reload rather than a return.
    val reduceMotion = rememberReduceMotion()
    val rise = remember { Animatable(if (homeIntroPlayed) 0f else 26f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion || homeIntroPlayed) rise.snapTo(0f)
        else rise.animateTo(0f, tween(460, easing = FastOutSlowInEasing))
        homeIntroPlayed = true
    }

    Box(Modifier.fillMaxSize()) {
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch { refreshing = true; runCatching { reload() }; refreshing = false }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .graphicsLayer { translationY = rise.value }
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Goal-aware eyebrow + serif greeting (mirrors iOS DailyFocus header).
        // The eyebrow shares its row with the avatar (a You shortcut) and the
        // search pill; the greeting gets the full width below and ellipsizes —
        // it used to fight a search circle and wrap long names against it.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (if (goal.isBlank()) stringResource(R.string.today_eyebrow)
                    else stringResource(eyebrowTemplateRes(LocalDate.now().dayOfYear), goal)).uppercase(),
                    style = MaterialTheme.typography.labelSmall, color = Periwinkle,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // The search pill says what it searches, which a bare magnifier
                // never did; the avatar makes the person greeted tappable.
                Row(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(CardFill)
                        .border(1.dp, LineStroke, RoundedCornerShape(50))
                        .clickable { onOpen("search") }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.today_search_cd),
                        tint = TextSoft, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.today_search_hint),
                        style = MaterialTheme.typography.labelMedium, color = TextMuted, maxLines = 1)
                }
                val profileCd = stringResource(R.string.today_profile_cd)
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(Periwinkle.copy(alpha = 0.16f))
                        .border(1.dp, Periwinkle.copy(alpha = 0.35f), CircleShape)
                        .clickable { onOpen("you") }
                        .semantics { contentDescription = profileCd },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        userName.trim().firstOrNull()?.uppercase() ?: "·",
                        style = MaterialTheme.typography.titleSmall, color = Periwinkle,
                    )
                }
            }
            val friend = stringResource(R.string.today_friend)
            Text(
                stringResource(R.string.today_greeting_format, greeting(), userName.ifBlank { friend }),
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            // One quiet thread of continuity: if there is a check-in from today
            // (or yesterday's, through the small hours), say it back — the
            // greeting stops being generic the moment the app knows something.
            recent.firstOrNull()?.let { last ->
                val t = relativeTime(last.createdAt, java.time.OffsetDateTime.now())
                if (showEarlierLine(t, LocalTime.now().hour) && last.line.isNotBlank()) {
                    Text(
                        stringResource(R.string.today_earlier_line, displayCheckInLine(last)),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                }
            }
        }

        // The one quiet banner slot (W9): at most one, by honest priority.
        val today = LocalDate.now().toString()
        // Hoisted so a recomposition of Today doesn't hand every banner a fresh
        // lambda identity (which defeats the banners' own skipping).
        val openSleep = remember(onOpen) { { onOpen("sleep") } }
        val openMixer = remember(onOpen) { { onOpen("sounds/mixer") } }
        val openPrograms = remember(onOpen) { { onOpen("programs") } }
        val dismissSleep: () -> Unit = remember(today) {
            { Session.prefPut("sleepBannerDismissed", today); dismissTick++ }
        }
        val dismissWindDown: () -> Unit = remember(today) {
            { Session.prefPut("windDownBannerDismissed", today); dismissTick++ }
        }
        val dismissed = remember(dismissTick, today) {
            buildSet {
                if (Session.prefGet("sleepBannerDismissed") == today) add("sleep")
                if (Session.prefGet("windDownBannerDismissed") == today) add("winddown")
            }
        }
        val banner = homeBannerPriority(
            offline = Session.servedStale,
            hour = LocalTime.now().hour,
            lastNightLogged = lastNightLogged,
            dismissed = dismissed,
            enrolledInProgram = program != null,
        )
        // Banners ease in and out instead of popping — dismissal used to remove
        // the row frame-to-frame while everything else on the page eases.
        // [lastBanner] holds the outgoing content through the exit animation.
        var lastBanner by remember { mutableStateOf(banner) }
        if (banner != HomeBanner.NONE) lastBanner = banner
        androidx.compose.animation.AnimatedVisibility(
            visible = banner != HomeBanner.NONE,
            enter = if (reduceMotion) androidx.compose.animation.EnterTransition.None
            else androidx.compose.animation.fadeIn(tween(220)) + androidx.compose.animation.expandVertically(tween(220)),
            exit = if (reduceMotion) androidx.compose.animation.ExitTransition.None
            else androidx.compose.animation.fadeOut(tween(180)) + androidx.compose.animation.shrinkVertically(tween(180)),
        ) {
        when (lastBanner) {
            HomeBanner.OFFLINE -> {
                // Two different offline facts, and conflating them is what the
                // banner used to do. "You're seeing the last copy" is about
                // reads; "3 things you wrote are waiting" is about the user's
                // own writing, which is the one they will worry about — so
                // that one also gets the way to act on it: a retry that
                // drains the outbox now instead of waiting for the next write.
                val waiting = com.cerebrozen.app.net.Outbox.count()
                InfoBanner(
                    icon = Icons.Outlined.CloudOff,
                    text = if (waiting > 0) {
                        stringResource(R.string.today_banner_offline_queued, waiting)
                    } else {
                        stringResource(R.string.today_banner_offline)
                    },
                    actionLabel = if (waiting > 0) stringResource(R.string.today_banner_offline_send) else null,
                    onAction = if (waiting > 0) {
                        { scope.launch { runCatching { com.cerebrozen.app.net.Outbox.drain() }; reload() } }
                    } else null,
                )
            }
            HomeBanner.SLEEP_CHECKIN -> InfoBanner(
                icon = Icons.Outlined.LightMode,
                text = stringResource(R.string.today_banner_sleep),
                actionLabel = stringResource(R.string.today_banner_sleep_action),
                // Sleep's tab is time-aware since 2026-08-03: through the
                // morning the check-in card IS the top of that tab, so this
                // lands the user directly on it.
                onAction = openSleep,
                onDismiss = dismissSleep,
            )
            HomeBanner.WIND_DOWN -> InfoBanner(
                icon = Icons.Outlined.Bedtime,
                text = stringResource(R.string.today_banner_winddown),
                actionLabel = stringResource(R.string.common_open),
                onAction = openMixer,
                onDismiss = dismissWindDown,
                // The action opens the MIXER, so the medallion wears the wave
                // motif (soundscape family), not the generic sleep moon.
                artKind = "soundscape",
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
                    onAction = openPrograms,
                    artKind = "program",   // W21: journey status → art medallion
                )
            }
            HomeBanner.NONE -> {}
        }
        }

        // The primary daily action leads (REDESIGN §3.1): the 1-tap check-in.
        // After ~8s the confirmation settles into one quiet line — it used to
        // hold the full confirmation row (and its Undo) forever.
        var settled by remember { mutableStateOf(false) }
        LaunchedEffect(loggedMood) {
            settled = false
            if (loggedMood != null) {
                kotlinx.coroutines.delay(8_000)
                settled = true
            }
        }
        // The mood already logged today, so its tile can wear the "earlier" ring.
        val earlierTodayMood = recent.firstOrNull()?.takeIf { last ->
            val t = relativeTime(last.createdAt, java.time.OffsetDateTime.now())
            t != null && t !is RelTime.Yesterday && t !is RelTime.Days
        }?.mood
        Box {
        SectionCard {
            val checkinFailed = stringResource(R.string.today_checkin_failed)
            if (loggedMood == null) {
                Text(stringResource(R.string.today_checkin_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(stringResource(R.string.today_checkin_subtitle), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                // A 2x2 grid of tinted tiles, and ONE tap logs it.
                //
                // This was four grey pills in a horizontalScroll behind a
                // separate "Check in" button: the fourth mood was clipped off
                // the right edge on a 720px screen, the product's most important
                // interaction took two taps, and the code comment above it had
                // called it "the 1-tap check-in" the whole time. It is one tap
                // now, and undoable — the same trade taken for Goals and
                // Programs, because a confirm on a feeling is friction in the
                // wrong place while a mis-tap needs to cost nothing.
                MOODS.chunked(2).forEachIndexed { row, pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEachIndexed { col, mood ->
                            Box(Modifier.weight(1f).appear(row * 2 + col, rise = 10f)) {
                                MoodTile(
                                    mood, enabled = !busy,
                                    marked = mood.name.equals(earlierTodayMood, ignoreCase = true),
                                ) {
                                    // Two fast taps on different tiles both
                                    // dispatch before recomposition disables
                                    // them; the guard makes the second a no-op.
                                    if (busy) return@MoodTile
                                    busy = true; status = null
                                    scope.launch {
                                        try {
                                            // Null = no signal; the check-in is
                                            // queued and will send itself later.
                                            // The tap still counts, so the
                                            // confirmation is the same — only
                                            // the undo path differs (there is
                                            // no server row to delete yet).
                                            val row2 = Api.checkIn(mood.name, mood.note, mood.symbol, mood.intensity)
                                            Haptics.success()
                                            if (!reduceMotion) bloom++
                                            loggedId = row2?.optString("id").orEmpty()
                                            loggedQueued = row2 == null
                                            loggedMood = mood
                                            reload()
                                        } catch (e: Exception) {
                                            status = e.userMessage(checkinFailed)
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (settled) {
                // The settled form: one quiet line holding the day's fact, the
                // vertical space given back to the page.
                val mood = loggedMood!!
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(12.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(mood.tint().copy(alpha = 0.95f), mood.tint().copy(alpha = 0.35f)))),
                    )
                    Text(
                        stringResource(R.string.today_checkin_settled, stringResource(mood.labelRes)),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                }
            } else {
                // The confirmation IS the moment — the mood said back in its own
                // colour, with the way out beside it.
                val mood = loggedMood!!
                val undoneMsg = stringResource(R.string.today_checkin_undone)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(mood.tint().copy(alpha = 0.85f), mood.tint().copy(alpha = 0.25f)))),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.today_checkin_logged, stringResource(mood.labelRes)),
                            style = MaterialTheme.typography.titleMedium, color = TextSoft)
                        // Say which of the two happened. "Saved" when it is on
                        // the server, and the truth when it is not — a check-in
                        // that silently waits for signal is still saved, but
                        // claiming it synced would be a small lie the user can
                        // catch by opening the app on another device.
                        Text(
                            if (loggedQueued) stringResource(R.string.today_checkin_queued)
                            else stringResource(mood.noteRes),
                            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                        )
                        // The bridge for the days one tap isn't enough: a word
                        // wants a page, and the page is one tap away.
                        TextButton(
                            onClick = { onOpen("journal") },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) {
                            Text(stringResource(R.string.today_checkin_say_more),
                                style = MaterialTheme.typography.labelLarge, color = Periwinkle)
                        }
                    }
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            val id = loggedId
                            val queued = loggedQueued
                            busy = true
                            scope.launch {
                                if (queued) {
                                    com.cerebrozen.app.net.Outbox.dropLast("/moods")
                                } else if (!id.isNullOrBlank()) {
                                    runCatching { Api.deleteMood(id) }
                                }
                                loggedMood = null; loggedId = null; loggedQueued = false
                                // Close the loop: the tap back is confirmed too.
                                status = undoneMsg
                                busy = false
                                reload()
                            }
                        },
                    ) { Text(stringResource(R.string.today_checkin_undo), color = Periwinkle, maxLines = 1) }
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

        // The plan slot holds its height while loading — the hero used to pop
        // in whole a beat later, shoving the rail down mid-read.
        if (!planLoaded && plan == null) {
            ShimmerBox(Modifier.fillMaxWidth().height(190.dp), shape = RoundedCornerShape(20.dp))
        }

        // The goal-aware next action (mirrors iOS DailyFocus); tapping
        // deep-links to the full plan.
        plan?.let { p ->
            val steps = p.optJSONArray("steps")
            val total = steps?.length() ?: 0
            val done = (0 until total).count { steps!!.getJSONObject(it).optBoolean("done") }
            // Time-aware: at 7 PM the card suggests the evening step, not the
            // morning one it happens to list first (nextPlanStepIndex).
            val stepObjs = (0 until total).map { steps!!.getJSONObject(it) }
            val next = nextPlanStepIndex(
                titles = stepObjs.map { it.optString("title") },
                done = stepObjs.map { it.optBoolean("done") },
                hour = LocalTime.now().hour,
            )?.let { stepObjs[it] }
            // The named next step deep-links to the surface that runs it; the
            // card body still opens the full plan.
            val nextRoute = next?.optString("symbol")?.let { planStepRoute(it) }
            HeroCard(
                kind = planArtKind(p.optString("focus").ifBlank { p.optString("title") }),
                eyebrow = stringResource(R.string.today_plan_eyebrow),
                title = p.optString("title"),
                subtitleMaxLines = 1,
                // The generator sets title = the focus goal, so "focus" was the
                // SAME string and the card printed "Sleep before midnight" twice,
                // one line under the other. `rationale` is the field worth the
                // space — it says why today's plan looks like this — and it was
                // being fetched and thrown away.
                subtitle = planSubtitle(p),
                height = 190.dp,
                alive = true,   // W24: a slow glow pass walks the day dots
                onClick = { onOpen("plan") },   // full plan route (ref/iOS parity)
            ) {
                val nextLabel = next?.let { stringResource(R.string.today_plan_next, it.optString("title")) }
                // From 17:00 an untouched plan says what's ahead, not the zero
                // ("2 steps still open tonight" instead of "0 of 3 done") —
                // presence framing, REDESIGN §3.6.
                val doneLabel = when {
                    total == 0 -> null
                    planTailUsesLeftForm(done, LocalTime.now().hour) ->
                        pluralStringResource(R.plurals.today_plan_left_tonight, total, total)
                    else -> stringResource(R.string.today_plan_done_count, done, total)
                }
                val tail = buildString {
                    if (nextLabel != null) append(nextLabel)
                    if (doneLabel != null) { if (isNotEmpty()) append("  ·  "); append(doneLabel) }
                }
                // This sits on the hero's constant-dark photo scrim, so use the art-text
                // constant — themed TextSoft resolves to ink on Dawn and vanishes here.
                // The chip runs the next step where one exists (START), and only
                // falls back to opening the plan (OPEN) when everything is done.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (tail.isNotBlank()) {
                        Text(tail, style = MaterialTheme.typography.bodyMedium,
                            color = com.cerebrozen.app.ui.theme.ArtTextSoft,
                            modifier = Modifier.weight(1f, fill = false)
                                .let { m ->
                                    if (nextRoute != null) m.clickable { onOpen(nextRoute) } else m
                                })
                    }
                    Box(
                        Modifier.padding(start = 10.dp)
                            .clip(CircleShape)
                            .border(1.dp, com.cerebrozen.app.ui.theme.ArtTextSoft.copy(alpha = 0.5f), CircleShape)
                            .clickable { onOpen(nextRoute ?: "plan") }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(
                            stringResource(if (nextRoute != null) R.string.today_plan_start else R.string.common_open).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = com.cerebrozen.app.ui.theme.ArtTextSoft,
                        )
                    }
                }
                // A 6dp glance of progress under the words — one segment per step.
                if (total > 0) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        repeat(total) { i ->
                            Box(
                                Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                                    .background(
                                        com.cerebrozen.app.ui.theme.ArtTextSoft.copy(
                                            alpha = if (i < done) 0.95f else 0.25f,
                                        ),
                                    ),
                            )
                        }
                    }
                }
            }
        }

        // An active journey now lives in the banner slot under the greeting
        // (W9 B4) — the full surface stays in `programs`.

        // Time-matched content rail (mirrors the iOS Home rails).
        ContentRail(onOpen)

        NavRow(stringResource(R.string.today_toolkit_title), stringResource(R.string.today_toolkit_subtitle)) { onOpen("toolkit") }

        // Weekly-insights teaser (iOS/web parity). Insights was reachable only
        // from You, so the one screen that answers "did any of this help?" was
        // two taps off the main surface. The subtitle carries the real count
        // when there is one and claims nothing when there isn't.
        NavRow(
            stringResource(R.string.today_insights_title),
            if (weekCheckIns > 0)
                pluralStringResource(R.plurals.today_insights_count, weekCheckIns, weekCheckIns)
            else stringResource(R.string.today_insights_subtitle),
        ) { onOpen("insights") }

        // Presence (REDESIGN §3.6): count the days you showed up, never the
        // days you didn't. The ring fills; it never breaks or resets.
        //
        // Quiet from here down. The check-in and the plan hero are what Home is
        // for; presence and past check-ins are what you read afterwards, and
        // giving all four the same lifted card made none of them lead. The
        // header binds the two quiet cards into one readable group.
        Text(
            stringResource(R.string.today_week_section),
            style = MaterialTheme.typography.titleMedium, color = TextSoft,
            modifier = Modifier.padding(top = 6.dp),
        )
        SectionCard(quiet = true) {
            val daysPresent = week.count { it.second }
            Text(
                if (daysPresent > 0 || streak > 0) stringResource(R.string.today_presence_title)
                else stringResource(R.string.today_presence_ready),
                style = MaterialTheme.typography.titleMedium, color = TextSoft,
            )
            milestoneLine(streak)?.let {
                // The halo marks the milestone the line beside it already states —
                // decoration on top of words, never instead of them (iOS parity).
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        RadiatingRing(size = 22.dp, color = Cyan)
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Cyan))
                    }
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Cyan)
                }
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
            // Real rows, not raw lines: the mood's own tint, when it happened,
            // and the whole card opens Trends — this used to render like debug
            // output ("Anxious · From onboarding") with no time and no tap.
            SectionCard(quiet = true, onClick = { onOpen("trends") }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.today_recent_title),
                        style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(stringResource(R.string.today_recent_open),
                        style = MaterialTheme.typography.labelMedium, color = Periwinkle)
                }
                val now = java.time.OffsetDateTime.now()
                recent.forEach { entry ->
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        val tint = moodTintFor(entry.mood)?.invoke() ?: TextMuted
                        Box(Modifier.size(10.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(tint.copy(alpha = 0.95f), tint.copy(alpha = 0.35f)))))
                        Text(entry.line, style = MaterialTheme.typography.bodyMedium,
                            color = TextSoft, modifier = Modifier.weight(1f), maxLines = 1)
                        relativeTimeLabel(relativeTime(entry.createdAt, now))?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                }
            }
        }
    }

    }

    // A quiet top scrim so scrolled content fades under the system clock
    // instead of colliding with it. Themed [Night] resolves per palette, so
    // Dawn fades to cream and Night to night.
    val topInset = with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(this).toDp()
    }
    Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(topInset + 18.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        com.cerebrozen.app.ui.theme.Night.copy(alpha = 0.88f),
                        com.cerebrozen.app.ui.theme.Night.copy(alpha = 0f),
                    ),
                ),
            ),
    )

    // First-run guided tour (ref GUIDED TOUR OVERLAY) — once per install.
    if (showTour) {
        GuidedTourOverlay(onDone = { showTour = false })
    }
    }
}
