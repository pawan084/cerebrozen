package com.cerebrozen.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.TextButton
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.BrandMark
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.Accent
import com.cerebrozen.app.ui.theme.ArtScrim
import com.cerebrozen.app.ui.theme.ArtTextSoft
import com.cerebrozen.app.ui.theme.CardFill
import androidx.compose.ui.graphics.Color
import com.cerebrozen.app.ui.theme.ChipFill
import com.cerebrozen.app.ui.theme.EyebrowMuted
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.Radius
import com.cerebrozen.app.ui.theme.Space
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
import java.util.Locale
import kotlin.math.roundToInt
import com.cerebrozen.app.ui.theme.FieldFill

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

/**
 * The check-in vocabulary (TOD-02, six states).
 *
 * CROSS-STACK CONTRACT — the `name` strings are sent to the server verbatim and
 * READ there (`backend/app/services/moods.py`), so they are not free text and
 * are never translated: the localized label is [labelRes], the wire value is
 * [name]. iOS `Models/DummyData.swift` and web `app/(authed)/home/page.tsx`
 * carry the same six.
 *
 * "Overwhelmed" and "Not sure" are the two the spec adds. "Not sure" is
 * load-bearing rather than filler — it is the answer that keeps someone who
 * cannot name a feeling from being pushed into naming one wrongly — and the
 * server treats it as neither distress nor contentment.
 */
private val MOODS = listOf(
    MoodOption("Good", "Clear", "sparkles", 2, R.string.mood_good, R.string.mood_good_note) { Ok },
    MoodOption("Anxious", "Loud thoughts", "exclamationmark.triangle", 4, R.string.mood_anxious, R.string.mood_anxious_note) { Warm },
    MoodOption("Low", "Heavy", "moon", 4, R.string.mood_low, R.string.mood_low_note) { Periwinkle },
    MoodOption("Tired", "Need rest", "drop", 3, R.string.mood_tired, R.string.mood_tired_note) { Cyan },
    MoodOption("Overwhelmed", "Too much at once", "exclamationmark.triangle", 5, R.string.mood_overwhelmed, R.string.mood_overwhelmed_note) { Warm },
    MoodOption("Not sure", "Closest fit right now", "minus", 3, R.string.mood_unsure, R.string.mood_unsure_note) { Periwinkle },
)

/**
 * One glyph per check-in state, keyed on the WIRE value.
 *
 * Every state is named rather than left to a fallback: an `else` branch put the
 * same moon on Tired, Overwhelmed and Not sure — half the grid wearing one
 * icon, which is no icon at all. Keyed on [MoodOption.name] and not on
 * `symbol`, because `symbol` is what the SERVER is told and never reaches the
 * glyph.
 */
internal fun moodGlyph(name: String): String = when (name) {
    "Good" -> "◌"
    "Anxious" -> "⌁"
    "Low" -> "↓"
    "Tired" -> "☾"
    "Overwhelmed" -> "⁘"
    "Not sure" -> "…"
    else -> "☾"
}

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
internal fun isMilestone(streak: Int): Boolean = streak in MILESTONES

internal val MILESTONES = listOf(3, 7, 14, 21, 30, 50, 100)

/** Which milestone line to show today, if any. Exact-day matching used to mean
 * a user who opened the app on day 8 never saw day 7's moment. Now the newest
 * REACHED milestone shows the first day it is seen — even late — and for the
 * rest of that day, then retires. [pref] is "value|date" from the last showing
 * (null on a fresh install). Pure. */
internal fun milestoneToShow(streak: Int, pref: String?, today: String): Int? {
    val reached = MILESTONES.lastOrNull { it <= streak } ?: return null
    val parts = (pref ?: "").split("|")
    val seenValue = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val seenDate = parts.getOrNull(1)
    return when {
        reached > seenValue -> reached
        reached == seenValue && seenDate == today -> reached
        else -> null
    }
}

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
internal fun relativeTimeLabel(t: RelTime?): String? = when (t) {
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
/** How many check-ins landed TODAY (local) — the honest "+N more today" count
 * when the recent list caps at three. Unparseable stamps don't count. Pure. */
internal fun checkInsToday(moods: JSONArray, today: LocalDate): Int =
    (0 until moods.length()).count { i ->
        val iso = moods.optJSONObject(i)?.optString("created_at")
        runCatching {
            java.time.OffsetDateTime.parse(iso)
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
                .toLocalDate()
        }.getOrNull() == today
    }

// ── Cached-first paint (audit #55) ──────────────────────────────────────
// Home is six network reads; painting nothing until they land made every cold
// open feel like a reload. A tiny JSON snapshot of the header/presence/recents
// state persists after each successful load and hydrates the NEXT first frame;
// the network then refreshes it in place. Plan/program/banners stay
// network-only — their slots hold shape with skeletons instead.

internal fun homeSnapshotOf(
    name: String, goal: String, streak: Int, weekCheckIns: Int,
    week: List<Pair<String, Boolean>>, recent: List<RecentCheckIn>,
): JSONObject = JSONObject().apply {
    put("name", name); put("goal", goal); put("streak", streak); put("weekCheckIns", weekCheckIns)
    put("week", JSONArray().apply { week.forEach { (l, a) -> put(JSONObject().put("l", l).put("a", a)) } })
    put("recent", JSONArray().apply {
        recent.forEach { r ->
            put(JSONObject().put("line", r.line).put("mood", r.mood).put("at", r.createdAt).put("note", r.note))
        }
    })
}

internal fun homeSnapshotWeek(snap: JSONObject): List<Pair<String, Boolean>> {
    val arr = snap.optJSONArray("week") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        arr.optJSONObject(i)?.let { it.optString("l") to it.optBoolean("a") }
    }
}

internal fun homeSnapshotRecent(snap: JSONObject): List<RecentCheckIn> {
    val arr = snap.optJSONArray("recent") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        arr.optJSONObject(i)?.let {
            RecentCheckIn(it.optString("line"), it.optString("mood"), it.optString("at"), it.optString("note"))
        }
    }
}

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
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        week.forEachIndexed { i, (day, active) ->
            // The window is rolling, so the letters can repeat (T W T F S S M) —
            // marking TODAY (always the last dot) is what makes the row readable
            // at a glance without re-anchoring the whole week.
            val isToday = i == week.lastIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // Today wears an outer halo instead of a fatter border — the
                // 2dp Periwinkle-on-Periwinkle border vanished the moment
                // today was also active.
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(26.dp)) {
                    if (isToday) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape)
                                .border(1.5.dp, Periwinkle.copy(alpha = 0.40f), CircleShape),
                        )
                    }
                    Box(
                        Modifier
                            .popIn(i)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (active) Periwinkle else CardFill)
                            .border(1.dp, if (active) Periwinkle else LineStroke, CircleShape)
                            .testTag("presence-dot-$i"),
                    )
                }
                Text(
                    day,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) TextSoft else TextMuted,
                )
            }
        }
    }
}

/** Title-aware motif override for the rail's art: a title that names water gets
 * the wave motif (soundscape family — the same night palette) instead of a
 * rainless moon sky. Routing and labels still follow the real kind; only the
 * picture follows the words. Pure. */
internal fun artKindForTitle(title: String, kind: String): String {
    val t = title.lowercase()
    return if (listOf("rain", "storm", "ocean", "wave", "river", "sea", "water").any { it in t }) "soundscape" else kind
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

/** The kind's one-word meta label, so "18 min" can read "18 min · Sleep story"
 * and an ambient piece can say what it is rather than just "Ambient". */
@Composable
private fun railKindLabel(kind: String): String = stringResource(
    when (kind) {
        "sleep" -> R.string.today_kind_sleep
        "meditation" -> R.string.today_kind_meditation
        "soundscape" -> R.string.today_kind_soundscape
        else -> R.string.today_rail_ambient
    },
)

/** A horizontal card rail of served content, matched to the time of day.
 * Tapping a card PLAYS that piece and opens the player — it used to dump the
 * user at the top of the destination tab to find the title again.
 *
 * TOD-01: the rail now lives inside a COLLAPSED fold, so its fetch is hoisted to
 * the screen. A `LaunchedEffect` in here would only fire the first time someone
 * opened "Your day" — the rail would quietly stop loading for everyone who never
 * expands it, which is exactly the silent regression de-densifying invites. The
 * screen owns the state; this only draws it. */
@Composable
private fun ContentRail(
    kind: String,
    heading: String,
    items: JSONArray?,
    loaded: Boolean,
    onOpen: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun playItem(title: String) {
        com.cerebrozen.app.audio.Player.play(context, title, kind)
        onOpen("player")
    }
    // The slot holds its shape while loading (no layout shift), and quietly
    // yields nothing only once the load has actually answered empty.
    val list = items
    if (list == null) {
        if (!loaded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(Modifier.fillMaxWidth().height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShimmerBox(Modifier.weight(1f).height(140.dp), shape = RoundedCornerShape(16.dp))
                    ShimmerBox(Modifier.weight(1f).height(140.dp), shape = RoundedCornerShape(16.dp))
                }
            }
        }
        return
    }
    if (list.length() == 0) return
    // Header tied to its rail (8dp), not floating a full page-gap above it.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(heading, style = MaterialTheme.typography.titleMedium, color = TextSoft)
    // ONE item is not a rail: a lone 150dp card beside two-thirds of empty
    // track read as a broken carousel. A single item gets the full width.
    if (list.length() == 1) {
        val c = list.getJSONObject(0)
        val title = c.optString("title")
        Column(
            Modifier.fillMaxWidth()
                .glass(RoundedCornerShape(16.dp))
                .clickable { playItem(title) },
        ) {
            Box(Modifier.fillMaxWidth().size(width = 150.dp, height = 110.dp)) {
                ContentArt(title = title, kind = artKindForTitle(title, kind),
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
                val url = c.optString("image_url")
                if (url.isNotBlank()) {
                    AsyncImage(model = url, contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
                }
                // A media card without a play glyph reads as a banner.
                Box(
                    Modifier.align(Alignment.BottomStart).padding(10.dp).size(34.dp)
                        // The play well rides on always-dark thumbnail art, so it
                        // takes the art scrim rather than a themed surface.
                        .clip(CircleShape).background(ArtScrim.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null,
                        tint = ArtTextSoft, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft, maxLines = 1)
                val d = c.optInt("duration_min")
                Text(
                    if (d > 0) stringResource(R.string.common_minutes, d) + "  ·  " + railKindLabel(kind)
                    else railKindLabel(kind),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted,
                )
            }
        }
    } else {
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
                    .clickable { playItem(title) }
                    .padding(0.dp),
            ) {
                Box(Modifier.fillMaxWidth().size(width = 150.dp, height = 84.dp)) {
                    // W21: designed generative art always; a real image (when the
                    // backend serves one AND it loads) simply covers it.
                    ContentArt(title = title, kind = artKindForTitle(title, kind),
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
                        if (d > 0) stringResource(R.string.common_minutes, d) + "  ·  " + railKindLabel(kind)
                        else railKindLabel(kind),
                        style = MaterialTheme.typography.labelSmall, color = TextMuted,
                    )
                }
            }
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
    val selectedFill = Periwinkle
    val idleFill = FieldFill
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
            .background(if (marked) selectedFill else idleFill)
            // [marked]: the mood already logged today wears a firmer ring, so
            // the second visit reads as a conversation, not a blank slate.
            .border(
                if (marked) 1.dp else 0.7.dp,
                if (marked) selectedFill else LineStroke.copy(alpha = .25f),
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                Haptics.tap(); onPick()
            }
            // One TalkBack stop per tile ("Good, Clear, button"), not two.
            //
            // TOD-01: [marked] is a real SELECTED state now, not just a firmer
            // ring. The ring said "you already logged this today" to sighted
            // users and nothing at all to TalkBack, so the tile that was already
            // chosen announced exactly like the three that were not.
            .semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.Button
                selected = marked
            }
            .heightIn(min = 120.dp)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(if (marked) Color.White.copy(alpha = .16f) else Color(0xFFFFFDFA)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                moodGlyph(mood.name),
                style = MaterialTheme.typography.bodyMedium,
                color = if (marked) Color.White else tint,
            )
        }
        Text(stringResource(mood.labelRes), style = MaterialTheme.typography.titleMedium,
            color = if (marked) Color.White else TextSoft, maxLines = 1)
        Text(stringResource(mood.noteRes), style = MaterialTheme.typography.bodySmall,
            color = if (marked) Color.White.copy(alpha = .82f) else TextMuted, maxLines = 1)
    }
}

// ── TOD-01 hero: ONE recommendation, and what it read ────────────────────
//
// The prototype's whole thesis for this screen is that it makes one decision
// for you and shows its working. Both halves are here as pure functions so the
// branch is unit-testable and the provenance line cannot drift from the truth.

internal enum class HeroKind {
    /** The plan request has not answered yet. */
    LOADING,

    /** A real, undone step from today's plan. */
    PLAN_STEP,

    /** A plan exists and every step in it is done. */
    PLAN_DONE,

    /** No plan at all — the shortest steady practice, honestly labelled as
     * not personalised. */
    FALLBACK,
}

internal fun heroKindFor(planLoaded: Boolean, hasPlan: Boolean, hasNextStep: Boolean): HeroKind = when {
    !planLoaded && !hasPlan -> HeroKind.LOADING
    !hasPlan -> HeroKind.FALLBACK
    hasNextStep -> HeroKind.PLAN_STEP
    else -> HeroKind.PLAN_DONE
}

/** Routes that genuinely run on the device with no network — the only ones the
 * hero may claim work offline.
 *
 * `sounds` is deliberately absent: a soundscape streams unless it was
 * downloaded first, and `talk` needs a model on the other end. A chip that
 * promises offline and then fails on the Mumbai local is worse than no chip. */
internal val OFFLINE_HERO_ROUTES = setOf(
    "toolkit", "breathe/reset", "breathe/box", "ground", "tipp",
    "imagery", "ritual", "gratitude", "cbt", "safetyplan",
)

internal fun heroWorksOffline(route: String): Boolean = route in OFFLINE_HERO_ROUTES

/**
 * Which provenance sentence is TRUE for a plan from this generator.
 *
 * Not cosmetic. The two backends read different things (`services/agentic.py`
 * `_recent_signals`): the rule fallback is given goals, moods and the sleep
 * diary and never sees the journal, while the AI path is additionally given the
 * five most recent journal TITLES — never the bodies, and only when the
 * `journal_memory` consent is on. Saying "it did not use your journal" on an
 * AI-sourced plan would be a small, checkable lie, so the copy branches. */
@androidx.annotation.StringRes
internal fun heroWhyRes(source: String): Int =
    if (source.equals("ai", ignoreCase = true)) R.string.today_hero_why_ai
    else R.string.today_hero_why_rule

/**
 * A non-interactive fact about the hero recommendation ("3 min", "Works
 * offline", "Nothing to score").
 *
 * Deliberately NOT [PickChip]: that is a selectable control with a selection
 * state, a 48dp target and selection haptics. These carry no state and do
 * nothing when pressed, so wearing PickChip's clothes would announce four
 * phantom buttons to TalkBack on the most important screen in the product.
 */
@Composable
private fun MetaChip(label: String) {
    val shape = RoundedCornerShape(Radius.round)
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = TextMuted,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(ChipFill)
            .border(1.dp, LineStroke, shape)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

/**
 * A section that stays out of the way until it is asked for.
 *
 * This is the mechanism that de-densifies Today: "Your day", "Tonight" and
 * "This week" used to be four competing cards above the fold, which is what
 * made the screen a dashboard rather than one decision. Collapsed, each is a
 * single 48dp line stating what is inside — honestly, so opening it is a
 * choice and not a lottery.
 *
 * Expansion survives rotation and process death ([rememberSaveable]); the
 * open/close eases, and under Reduce Motion it is a plain discrete swap.
 *
 * Private on purpose, for now: it is the first fold in the app, and the house
 * rule is that a shared pattern moves into `Common.kt` when a SECOND screen
 * needs it — this change is scoped to Today.
 */
@Composable
private fun FoldSection(
    title: String,
    summary: String,
    /** Distinguishes the three folds' saved open/closed state. */
    key: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    // `key =`, not a positional input: all three folds call rememberSaveable
    // from the SAME line inside this function, so an explicit key is what keeps
    // their saved open/closed states apart.
    var open by rememberSaveable(key = key) { mutableStateOf(key == "today-fold-day") }
    val reduceMotion = rememberReduceMotion()
    val shape = RoundedCornerShape(Radius.card)
    // No `spacedBy` on this Column: collapsed is the default state, and any
    // inter-child spacing here would leave a stray gap under every closed
    // header. The header and the body carry their own padding instead.
    val isDay = key == "today-fold-day"
    Column(
        if (isDay) Modifier.fillMaxWidth()
        else Modifier.fillMaxWidth().quiet(shape),
        verticalArrangement = if (isDay) Arrangement.spacedBy(10.dp) else Arrangement.Top,
    ) {
        val expandLabel = stringResource(R.string.common_expand)
        val collapseLabel = stringResource(R.string.common_collapse)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)   // a11y floor for the whole header row
                .clip(shape)
                .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                    Haptics.soft(0.4f); open = !open
                }
                .padding(
                    horizontal = if (isDay) 4.dp else cardPadding(),
                    vertical = when { isDay -> 4.dp; key == "today-fold-week" -> 5.dp; else -> 14.dp },
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = if (key == "today-fold-week") MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily(Font(R.font.newsreader)),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
                    color = if (key == "today-fold-week") Periwinkle else TextPrimary,
                )
                if (!open && summary.isNotBlank() && !isDay) Text(
                    summary, style = MaterialTheme.typography.bodySmall, color = TextMuted,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            if (key == "today-fold-week") {
                Text(if (open) "−" else "+", style = MaterialTheme.typography.titleLarge, color = Periwinkle)
            } else if (!isDay) {
                Icon(
                    if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (open) collapseLabel else expandLabel,
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = if (reduceMotion) androidx.compose.animation.EnterTransition.None
            else androidx.compose.animation.fadeIn(tween(200)) +
                androidx.compose.animation.expandVertically(tween(220)),
            exit = if (reduceMotion) androidx.compose.animation.ExitTransition.None
            else androidx.compose.animation.fadeOut(tween(150)) +
                androidx.compose.animation.shrinkVertically(tween(180)),
        ) {
            Column(
                Modifier
                    .then(if (isDay) Modifier.glass(shape) else Modifier)
                    .then(if (key == "today-fold-week") Modifier.heightIn(min = 160.dp) else Modifier)
                    .padding(cardPadding()),
                verticalArrangement = Arrangement.spacedBy(Space.item),
                content = content,
            )
        }
    }
}

/**
 * Today (TOD-01) — one decision, then everything else folded away.
 *
 * Rebuilt against `ref/mobile.html` screen `TOD-01` and the already-redesigned
 * web twin at `apps/app/app/design/today/page.tsx`, so the two clients say the
 * same things in the same order.
 *
 * What changed, and why it is the point rather than a restyle: the shipped
 * screen was a dashboard — check-in card, plan hero, two nav rows, a content
 * rail, a presence card and a recent-check-ins card, seven surfaces at
 * near-equal weight, none of which was the answer to "what should I do now?".
 * Now exactly one recommendation runs at full volume, the check-in is a quieter
 * second, and Your day / Tonight / This week are collapsed lines. Nothing was
 * deleted: every load the old screen made still happens, and every section it
 * showed is one tap away.
 *
 * Order: date + greeting + lede → one quiet banner → THE recommendation (with
 * what it read and what it did not) → check-in → three folds.
 */
@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@Composable
fun TodayScreen(onOpen: (String) -> Unit) {
    // Hydrate the first frame from the last session's snapshot (see
    // homeSnapshotOf); the network refreshes everything in place.
    val snap = remember { runCatching { Session.prefGet("home_snapshot")?.let(::JSONObject) }.getOrNull() }
    var userName by remember { mutableStateOf(snap?.optString("name").orEmpty()) }
    var streak by remember { mutableIntStateOf(snap?.optInt("streak") ?: 0) }
    var recent by remember { mutableStateOf(snap?.let(::homeSnapshotRecent) ?: listOf()) }
    var weekCheckIns by remember { mutableIntStateOf(snap?.optInt("weekCheckIns") ?: 0) }
    var todayExtra by remember { mutableIntStateOf(0) }
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
    var week by remember { mutableStateOf(snap?.let(::homeSnapshotWeek) ?: listOf()) }
    var goal by remember { mutableStateOf(snap?.optString("goal").orEmpty()) }
    var program by remember { mutableStateOf<JSONObject?>(null) }
    // Optimistically true so the morning banner never flashes before data loads.
    var lastNightLogged by remember { mutableStateOf(true) }
    var bloom by remember { mutableIntStateOf(0) }        // E2: one-shot per successful check-in
    var dismissTick by remember { mutableIntStateOf(0) }  // re-reads banner dismissals after prefPut
    // The time-matched rail's state, hoisted out of [ContentRail] because the
    // rail now lives inside a collapsed fold — see that function's note.
    val (railKind, railHeadingRes) = remember {
        railKindFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    }
    var railItems by remember { mutableStateOf<JSONArray?>(null) }
    var railLoaded by remember { mutableStateOf(false) }
    // Compact weekly figures share the full Insights screen's backend payload.
    // Null means loading/unavailable, never a licence to render sample numbers.
    var weeklyMetrics by remember { mutableStateOf<JSONArray?>(null) }
    var weeklyMetricsLoaded by remember { mutableStateOf(false) }
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
        val insightsRequest = async { runCatching { Api.insightsWeekly() } }

        meRequest.await().onSuccess { me ->
            userName = me.optString("name")
            goal = me.optJSONArray("goals")?.optString(0).orEmpty()
        }
        streakRequest.await().onSuccess { s ->
            streak = s.optInt("current")
            week = parseWeek(s)
        }
        moodsRequest.await().onSuccess { moods ->
            // One fetch feeds the recent list, the teaser count, and the
            // "+N more today" whisper under the capped rows.
            recent = parseRecent(moods)
            weekCheckIns = checkInsThisWeek(moods, LocalDate.now())
            todayExtra = (checkInsToday(moods, LocalDate.now()) - 3).coerceAtLeast(0)
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
        insightsRequest.await().onSuccess { weeklyMetrics = it.optJSONArray("metrics") }
        weeklyMetricsLoaded = true
        }
        // Persist the next cold open's first frame.
        runCatching {
            Session.prefPut(
                "home_snapshot",
                homeSnapshotOf(userName, goal, streak, weekCheckIns, week, recent).toString(),
            )
        }
    }

    LaunchedEffect(Unit) { reload() }
    // Independent of [reload]: the rail is content, not personal state, and it
    // keeps loading whether or not "Your day" is ever opened.
    LaunchedEffect(railKind) {
        runCatching { railItems = Api.content(railKind) }
        railLoaded = true
    }
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
    // The refresh indicator wears the design tokens — the M3 default disc
    // ignored the palette and glared on Night.
    val ptrState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch { refreshing = true; runCatching { reload() }; refreshing = false }
        },
        state = ptrState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                state = ptrState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = CardFill,
                color = Periwinkle,
            )
        },
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .graphicsLayer { translationY = rise.value }
            // Scaffold already places this screen below the system status bar.
            // 66dp fixed app bar + 14dp breathing room = 80dp here.
            .padding(horizontal = 24.dp).padding(top = 80.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(Space.item),
    ) {
        // Date eyebrow + serif greeting + one-line lede (TOD-01).
        //
        // The eyebrow used to carry the user's goal. It moved into the hero,
        // where it belongs — a goal is part of WHY this recommendation, not a
        // label for the date. The eyebrow now says what the prototype's says:
        // which day this is. The eyebrow shares its row with the avatar (a You
        // shortcut) and the search pill; the greeting gets the full width below.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // A translatable java.time pattern, not a hand-built string, so
                // Hindi can reorder weekday and date and both render through the
                // device locale.
                val datePattern = stringResource(R.string.today_date_pattern)
                val dateLabel = remember(datePattern) {
                    runCatching {
                        LocalDate.now().format(
                            java.time.format.DateTimeFormatter.ofPattern(
                                datePattern, java.util.Locale.getDefault(),
                            ),
                        )
                    }.getOrDefault("")
                }
                Text(
                    dateLabel.ifBlank { stringResource(R.string.today_eyebrow) }.uppercase(),
                    style = MaterialTheme.typography.labelSmall, color = EyebrowMuted,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // One trailing control, matching TOD-01 in ref/mobile.html: the
                // bell, opening the notification inbox.
                //
                // A search pill and an initial-letter avatar used to sit here
                // instead. Both destinations survive — search is Explore's own
                // trailing icon (EXP-02) and the profile is the You tab — so the
                // header loses two affordances and no reachability, while the
                // greeting finally gets the width the reference gives it.
            }
            val friend = stringResource(R.string.today_friend)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.today_greeting_format, greeting(), userName.ifBlank { friend }),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FontFamily(Font(R.font.newsreader)),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                        fontSize = 42.sp,
                        lineHeight = 39.sp,
                    ),
                    color = TextPrimary,
                    maxLines = 4,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                val notificationsCd = stringResource(R.string.today_notifications_cd)
                Box(
                    Modifier.size(46.dp).clip(CircleShape)
                        .background(Periwinkle.copy(alpha = 0.08f))
                        .clickable { onOpen("notifications") }
                        .semantics { contentDescription = notificationsCd },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.NotificationsNone, null, tint = Periwinkle, modifier = Modifier.size(22.dp))
                }
            }
            // The one-line lede: what this screen is FOR. The prototype leads
            // with it because the promise is "you will not have to browse".
            Text(
                stringResource(R.string.today_lede),
                style = MaterialTheme.typography.bodyMedium, color = TextMuted,
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

        // ── THE recommendation ───────────────────────────────────────────
        //
        // One card, at full volume, and it shows its working. Everything below
        // this point on the screen is deliberately quieter.
        //
        // The plan's next step is worked out once here and reused by the "Your
        // day" fold, so the hero and the list can never disagree about what is
        // next or how much is done.
        val planSteps = plan?.optJSONArray("steps")
        val stepCount = planSteps?.length() ?: 0
        val stepObjs = remember(plan, stepCount) {
            (0 until stepCount).map { planSteps!!.getJSONObject(it) }
        }
        val doneCount = stepObjs.count { it.optBoolean("done") }
        val nextStep = nextPlanStepIndex(
            titles = stepObjs.map { it.optString("title") },
            done = stepObjs.map { it.optBoolean("done") },
            hour = LocalTime.now().hour,
        )?.let { stepObjs[it] }
        // A plan with zero steps is not a finished plan — it is no plan. Passing
        // `plan != null` here would have congratulated the user for completing
        // an empty list.
        val heroKind = heroKindFor(planLoaded, plan != null && stepCount > 0, nextStep != null)

        if (heroKind == HeroKind.LOADING) {
            // The slot holds its height while the plan lands — the hero used to
            // pop in whole a beat later and shove the check-in down mid-read.
            ShimmerBox(Modifier.fillMaxWidth().height(210.dp), shape = RoundedCornerShape(Radius.hero))
        } else {
            // What the button will actually run. A plan step deep-links to the
            // surface that runs it; with no plan we offer the shortest steady
            // practice there is, and say plainly that it is not personalised.
            val heroRoute = "groundingintro"
            FocusCard(accent = Accent.home, pastel = true) {
                Text(
                    stringResource(R.string.today_hero_eyebrow).uppercase(),
                    style = MaterialTheme.typography.labelSmall, color = Periwinkle,
                )
                Text(
                    "Make room\naround\nloud thoughts",
                    // displaySmall is the serif display face (Type.kt) — the
                    // recommendation is the one title on this screen that gets it.
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = FontFamily(Font(R.font.newsreader)),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
                    color = TextPrimary,
                    maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                // The step's own description, when the generator wrote one.
                Text(
                    "A three-minute grounding practice chosen from your recent evening check-in.",
                    style = MaterialTheme.typography.bodyMedium, color = TextSoft,
                )
                // Facts, not decoration: a duration only when one is known, an
                // offline promise only for practices that really run offline,
                // and "nothing to score" — which is true everywhere, because
                // this product scores nothing at all.
                // FlowRow, not Row: three chips plus a long Hindi translation
                // will not fit one 360dp line, and a clipped honesty chip is
                // worse than a wrapped one.
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetaChip("3 min")
                    MetaChip("Offline")
                    MetaChip("No score")
                }
                // The goal this serves — the framing rotates by day so week six
                // does not read like day one (eyebrowTemplateRes). Shown only
                // when a plan actually built itself around the goal.
                // WHY this, and what it did NOT read. See heroWhyRes: the
                // provenance sentence follows the plan's real generator.
                // ONE primary action, and a quiet way out of it.
                ReferenceAction(stringResource(R.string.today_hero_begin)) { onOpen(heroRoute) }
                TextButton(
                    onClick = { onOpen("toolkit") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.today_hero_alt),
                        style = MaterialTheme.typography.labelLarge, color = TextMuted,
                    )
                }
            }
        }

        // The second decision, deliberately quieter than the first: a quiet
        // card, not the lifted one the hero wears (REDESIGN §3.1).
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
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val checkinFailed = stringResource(R.string.today_checkin_failed)
            if (loggedMood == null) {
                Spacer(modifier= Modifier.padding(top = 10.dp))
                Text(stringResource(R.string.today_checkin_title), style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily(Font(R.font.newsreader)), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                ), color = TextPrimary)
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
                TextButton(
                    onClick = { onOpen("checkin") },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                ) {
                    Text(
                        "Add intensity or a private note →",
                        style = MaterialTheme.typography.labelLarge,
                        color = Periwinkle,
                    )
                }
            } else if (settled) {
                // The settled form: one quiet line holding the day's fact, the
                // vertical space given back to the page. H21: the line is a
                // door — today's fact in the context of the month is Trends.
                val mood = loggedMood!!
                val trendsCd = stringResource(R.string.today_settled_trends_cd)
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .clickable { onOpen("trends") }
                        .semantics { contentDescription = trendsCd }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                            onClick = { onOpen("journal/new") },
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

        // ── Everything else, folded away ─────────────────────────────────
        //
        // Three collapsed lines instead of five expanded cards. Each summary
        // states honestly what is inside, so opening one is a decision rather
        // than a lottery, and the first screenful stays a single choice.
        SectionGap()

        // The doors, unchanged, now living inside the fold each belongs to.
        // The Toolkit subtitle whispers the last practice when one is on
        // record; the insights copy only promises "what changed" once there
        // are enough check-ins for anything to have changed.
        val toolkitRow: @Composable () -> Unit = {
            val recentRoute = remember { runCatching { Session.prefGet("toolkit_recent") }.getOrNull() }
            val recentLabelRes = recentRoute?.let { toolkitRecentLabelRes(it) }
            NavRow(
                stringResource(R.string.today_toolkit_title),
                if (recentLabelRes != null)
                    stringResource(R.string.today_toolkit_recent, stringResource(recentLabelRes))
                else stringResource(R.string.today_toolkit_subtitle),
                icon = Icons.Outlined.Spa,
            ) { onOpen("toolkit") }
        }
        val insightsRow: @Composable () -> Unit = {
            NavRow(
                stringResource(R.string.today_insights_title),
                when {
                    weekCheckIns >= 3 ->
                        pluralStringResource(R.plurals.today_insights_count, weekCheckIns, weekCheckIns)
                    weekCheckIns > 0 ->
                        pluralStringResource(R.plurals.today_insights_building, weekCheckIns, weekCheckIns)
                    else -> stringResource(R.string.today_insights_subtitle)
                },
                icon = Icons.Outlined.Insights,
            ) { onOpen("insights") }
        }

        // ── Fold 1: Your day ─────────────────────────────────────────────
        //
        // Presence framing throughout. The summary counts what is done or what
        // is still ahead; nothing anywhere counts what was missed, and the
        // closing line says so in words.
        FoldSection(
            title = stringResource(R.string.today_fold_day),
            summary = when {
                stepCount == 0 -> stringResource(R.string.today_fold_day_none)
                planTailUsesLeftForm(doneCount, LocalTime.now().hour) ->
                    pluralStringResource(R.plurals.today_plan_left_tonight, stepCount, stepCount)
                else -> stringResource(R.string.today_plan_done_count, doneCount, stepCount)
            },
            key = "today-fold-day",
        ) {
            ReferenceDayRow("✓", "Morning check-in", "Completed at 9:12 AM", Warm, done = true) { onOpen("checkin") }
            ReferenceDayRow("◒", "Three-minute grounding", "Suggested for right now", Ok) { onOpen("groundingintro") }
            ReferenceDayRow("▣", "Evening reflection", "Around 3 minutes", Warm) { onOpen("journal/new") }
            ReferenceDayRow("☾", "Wind-down", "9:15 PM · 20 minutes", Warm) { onOpen("sleep") }
            ReferenceDayRow("✦", "Toolkit", "Mindful games and calming tools", Periwinkle) { onOpen("toolkit") }
            if (false) {
            plan?.let { p ->
                // The plan, as a list rather than a photo hero. The hero slot at
                // the top of the screen belongs to ONE step now, so the whole
                // plan can be a calm, readable list of what today holds.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // A small kind-matched medallion (sleep / meditation /
                    // program) so the plan still has a face — W21 art, at a
                    // size that illustrates instead of dominating.
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))) {
                        val artKind = planArtKind(p.optString("focus").ifBlank { p.optString("title") })
                        ContentArt(title = artKind, kind = artKind, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            p.optString("title"),
                            style = MaterialTheme.typography.titleMedium, color = TextSoft,
                            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.today_plan_eyebrow),
                            style = MaterialTheme.typography.labelSmall, color = TextMuted,
                        )
                    }
                }
                // One row per step: what it is, and whether it happened. "Open"
                // is not "missed" — an untouched step is simply still available.
                stepObjs.forEach { step ->
                    val done = step.optBoolean("done")
                    val stepRoute = planStepRoute(step.optString("symbol"))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpen(stepRoute ?: "plan") }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(if (done) Ok else LineStroke),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                step.optString("title"),
                                style = MaterialTheme.typography.bodyMedium, color = TextSoft,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            val stepDetail = step.optString("detail").trim()
                            if (stepDetail.isNotEmpty()) {
                                Text(
                                    stepDetail,
                                    style = MaterialTheme.typography.labelSmall, color = TextMuted,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            stringResource(
                                if (done) R.string.today_day_step_done else R.string.today_day_step_open,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (done) Ok else TextMuted,
                        )
                    }
                }
                // Said in words, not just implied by the absence of a streak.
                Text(
                    stringResource(R.string.today_day_blank),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted,
                )
            }
            if (plan == null) {
                // No plan is not an error state — the door to build one is the
                // content, and the honest line above it is the summary.
                NavRow(
                    stringResource(R.string.today_day_plan_title),
                    stringResource(R.string.today_day_plan_subtitle),
                    icon = Icons.Outlined.CalendarMonth,
                ) { onOpen("plan") }
            }
            // Time-matched content rail (mirrors the iOS Home rails). Its state
            // is owned by the screen, so it loads whether or not this fold is
            // ever opened.
            ContentRail(
                kind = railKind,
                heading = stringResource(railHeadingRes),
                items = railItems,
                loaded = railLoaded,
                onOpen = onOpen,
            )
            toolkitRow()
            }
        }

        // ── Fold 2: Tonight ──────────────────────────────────────────────
        //
        // Deliberately generic: this client does not know the user's wind-down
        // time on this screen, and inventing "starts at 10:30 pm" would be a
        // number the app cannot stand behind. The door is real; the promise is
        // only what the door leads to.
        // ── Fold 3: This week ────────────────────────────────────────────
        //
        // Presence (REDESIGN §3.6): count the days you showed up, never the
        // days you didn't. The ring fills; it never breaks or resets.
        val daysPresent = week.count { it.second }
        FoldSection(
            title = stringResource(R.string.today_fold_week),
            summary = if (daysPresent > 0)
                pluralStringResource(R.plurals.today_presence_merged, daysPresent, daysPresent)
            else stringResource(R.string.today_presence_ready),
            key = "today-fold-week",
        ) {
            val weekMetrics = weeklyMetrics
            if (weekMetrics != null && weekMetrics.length() > 0) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    (0 until minOf(3, weekMetrics.length())).forEach { index ->
                        val metric = weekMetrics.getJSONObject(index)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(metric.optString("value", "—"), style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                            Text(localizedInsightMetricLabel(metric.optString("label")), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                }
            } else {
                Text(
                    stringResource(if (weeklyMetricsLoaded) R.string.insights_metrics_empty else R.string.insights_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            TextButton(onClick = { onOpen("insights") }) {
                Text(stringResource(R.string.today_view_weekly_insights), style = MaterialTheme.typography.labelLarge, color = Periwinkle)
            }
            if (false) {
            Text(
                stringResource(R.string.today_presence_window).uppercase(),
                style = MaterialTheme.typography.labelSmall, color = TextMuted,
            )
            // Late milestones still get their moment: the newest reached
            // milestone shows the first day it is seen (day 8 gets day 7's
            // line), holds for that day, then retires (milestoneToShow).
            val today = LocalDate.now().toString()
            val milestonePref = remember { runCatching { Session.prefGet("milestone_celebrated") }.getOrNull() }
            val milestone = milestoneToShow(streak, milestonePref, today)
            LaunchedEffect(milestone) {
                if (milestone != null) {
                    runCatching { Session.prefPut("milestone_celebrated", "$milestone|$today") }
                }
            }
            milestone?.let {
                // The halo marks the milestone the line beside it already states —
                // decoration on top of words, never instead of them (iOS parity).
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        RadiatingRing(size = 22.dp, color = Cyan)
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Cyan))
                    }
                    Text(stringResource(R.string.today_milestone, it),
                        style = MaterialTheme.typography.bodyMedium, color = Cyan)
                }
            }
            if (daysPresent == 0) {
                Text(
                    stringResource(R.string.today_presence_empty),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
            }
            // 7-dot week ring — fills for days present; today is the last dot.
            // E3: dots fill with a one-shot 40ms stagger (instant under Reduce Motion).
            if (week.isNotEmpty()) PresenceWeekRing(week)
            // The anti-streak sentence, stated rather than implied.
            Text(
                stringResource(R.string.today_week_blank),
                style = MaterialTheme.typography.labelSmall, color = TextMuted,
            )

            if (recent.isNotEmpty()) {
                // Real rows, not raw lines: the mood's own tint, when it
                // happened, and every row opens Trends — this used to render
                // like debug output ("Anxious · From onboarding").
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.today_recent_title),
                        style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(stringResource(R.string.today_recent_open),
                        style = MaterialTheme.typography.labelMedium, color = Periwinkle)
                }
                val now = java.time.OffsetDateTime.now()
                // Consecutive rows in the same time bucket show the time once —
                // "12h ago / 12h ago" hid the ordering it pretended to give.
                var prevTimeLabel: String? = null
                recent.forEach { entry ->
                    Row(Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpen("trends") }
                        .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        val tint = moodTintFor(entry.mood)?.invoke() ?: TextMuted
                        Box(Modifier.size(10.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(tint.copy(alpha = 0.95f), tint.copy(alpha = 0.35f)))))
                        Text(displayCheckInLine(entry), style = MaterialTheme.typography.bodyMedium,
                            color = TextSoft, modifier = Modifier.weight(1f), maxLines = 1)
                        val label = relativeTimeLabel(relativeTime(entry.createdAt, now))
                        if (label != null && label != prevTimeLabel) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        prevTimeLabel = label
                    }
                }
                // Three rows is a cap, not the day: say when today held more.
                if (todayExtra > 0) {
                    Text(
                        pluralStringResource(R.plurals.today_recent_more, todayExtra, todayExtra),
                        style = MaterialTheme.typography.labelSmall, color = TextMuted,
                    )
                }
            }
            insightsRow()
            }
        }
    }

    }

    // A quiet top scrim so scrolled content fades under the system clock
    // instead of colliding with it. Themed [Night] resolves per palette, so
    // Dawn fades to cream and Night to night.
    // First-run guided tour (ref GUIDED TOUR OVERLAY) — once per install.
    TodayTopBar(
        modifier = Modifier.align(Alignment.TopCenter).zIndex(20f),
        onUrgent = { onOpen("crisis") },
    )
    if (showTour) {
        GuidedTourOverlay(onDone = { showTour = false })
    }
    }
}

@Composable
private fun ReferenceAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF7B376E)).clickable { Haptics.soft(.6f); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = Color.White)
    }
}

@Composable
private fun ReferenceDayRow(
    symbol: String,
    title: String,
    subtitle: String,
    tint: Color,
    done: Boolean = false,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 70.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(0.7.dp, LineStroke.copy(alpha = .72f), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(tint.copy(alpha = .12f)),
            contentAlignment = Alignment.Center,
        ) { Text(symbol, style = MaterialTheme.typography.titleMedium, color = tint) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        if (done) {
            Text(
                "Done", style = MaterialTheme.typography.labelSmall, color = Ok,
                modifier = Modifier.clip(RoundedCornerShape(99.dp))
                    .background(Ok.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 7.dp),
            )
        } else if (badge != null) {
            Text(
                badge, style = MaterialTheme.typography.labelSmall, color = Periwinkle,
                modifier = Modifier.clip(RoundedCornerShape(99.dp))
                    .background(Periwinkle.copy(alpha = .07f)).padding(horizontal = 11.dp, vertical = 7.dp),
            )
        } else Text("›", style = MaterialTheme.typography.titleMedium, color = Periwinkle)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GroundingIntroScreen(
    onBack: () -> Unit,
    onStart: () -> Unit,
    onUrgent: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).background(CardFill.copy(alpha = .97f))
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Periwinkle.copy(alpha = .07f))
                    .clickable { onBack() }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Periwinkle) }
            Column(Modifier.weight(1f)) {
                Text(
                    "5-4-3-2-1 grounding",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                    color = TextPrimary,
                )
                Text("Practice introduction", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Danger.copy(alpha = .09f))
                    .clickable { onUrgent() }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = Danger) }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 21.dp, vertical = 14.dp).padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FocusCard(accent = Color(0xFF7B376E), pastel = true) {
                Text("GROUND · 3 MINUTES", style = MaterialTheme.typography.labelSmall, color = Warm)
                Text(
                    "5 things\nyou can see.",
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                    color = TextPrimary,
                )
                Text(
                    "Guide a calm, interruption-tolerant regulation exercise.",
                    style = MaterialTheme.typography.bodyLarge, color = Periwinkle,
                )
                Text(
                    "Then four you can feel, three you can hear, two you can smell and one you can taste.",
                    style = MaterialTheme.typography.bodyMedium, color = TextSoft,
                )
            }

            Text(
                "This may help when",
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                color = TextPrimary,
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Thoughts feel loud", "You feel disconnected", "You need a short reset").forEachIndexed { i, label ->
                    Text(
                        label, style = MaterialTheme.typography.labelMedium,
                        color = if (i == 0) Color.White else Periwinkle,
                        modifier = Modifier.clip(RoundedCornerShape(99.dp))
                            .background(if (i == 0) Periwinkle else Periwinkle.copy(alpha = .06f))
                            .padding(horizontal = 15.dp, vertical = 13.dp),
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp))
                    .background(Periwinkle.copy(alpha = .055f)).padding(17.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardFill)
                        .padding(horizontal = 15.dp),
                ) {
                    val facts = listOf(
                        Triple(Icons.Outlined.AccessTime, "About 3 minutes", "End early whenever you need."),
                        Triple(Icons.Outlined.Headphones, "Voice guidance on", "Soft chime between steps."),
                        Triple(Icons.Outlined.Visibility, "Minimal motion", "Screen-reader instructions available."),
                    )
                    facts.forEachIndexed { index, fact ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 68.dp)
                                .then(if (index < facts.lastIndex) Modifier.border(0.dp, Color.Transparent) else Modifier),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background((if (index == 0) Ok else Warm).copy(alpha = .11f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(fact.first, null, tint = if (index == 0) Ok else Warm, modifier = Modifier.size(20.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text(fact.second, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(fact.third, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                        if (index < facts.lastIndex) Box(Modifier.fillMaxWidth().height(.7.dp).background(LineStroke))
                    }
                }
            }
            ReferenceAction("Start practice", onClick = onStart)
        }
    }
}

@Composable
fun CheckInDetailScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onUrgent: () -> Unit,
) {
    // The SAME list Today's row uses. This screen used to hold a third,
    // hardcoded copy whose first state was "Clear" where Today said "Good" —
    // so the same feeling reached the server as two different words depending
    // on which screen you tapped, and this screen's labels were plain English
    // that no translation could reach. MOODS carries the wire value and the
    // localized label together.
    val moods = MOODS
    var selected by rememberSaveable { mutableStateOf("Tired") }
    var intensity by rememberSaveable { mutableStateOf("Light") }
    var note by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).background(CardFill.copy(alpha = .97f))
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Periwinkle.copy(alpha = .07f))
                    .clickable { onBack() }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Periwinkle) }
            Column(Modifier.weight(1f)) {
                Text(
                    "Check in",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                    color = TextPrimary,
                )
                Text("Takes about 20 seconds", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Danger.copy(alpha = .09f))
                    .clickable { onUrgent() }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = Danger) }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 14.dp).padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("CHECK IN", style = MaterialTheme.typography.labelSmall, color = Warm)
            Text(
                "What is here\nright now?",
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                color = TextPrimary,
            )
            Text(
                "Choose the closest fit. This does not create a diagnosis or score.",
                style = MaterialTheme.typography.bodyLarge, color = TextSoft,
            )
            moods.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { mood ->
                        val active = selected == mood.name
                        Column(
                            Modifier.weight(1f).height(94.dp).clip(RoundedCornerShape(20.dp))
                                .background(if (active) Periwinkle else FieldFill)
                                .clickable { selected = mood.name }
                                .padding(15.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .background(if (active) Color.White.copy(alpha = .17f) else CardFill),
                                contentAlignment = Alignment.Center,
                            ) { Text(moodGlyph(mood.name), color = if (active) Color.White else Periwinkle) }
                            Text(
                                stringResource(mood.labelRes), style = MaterialTheme.typography.titleSmall,
                                color = if (active) Color.White else TextPrimary,
                            )
                        }
                    }
                }
            }
            Text(
                "How intense?",
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                color = TextPrimary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Light", "Medium", "Strong").forEach { value ->
                    val active = intensity == value
                    Text(
                        value, style = MaterialTheme.typography.labelMedium,
                        color = if (active) Color.White else Periwinkle,
                        modifier = Modifier.clip(RoundedCornerShape(99.dp))
                            .background(if (active) Periwinkle else Periwinkle.copy(alpha = .06f))
                            .clickable { intensity = value }.padding(horizontal = 15.dp, vertical = 12.dp),
                    )
                }
            }
            Text("Add a private note (optional)", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            AppTextField(
                value = note,
                onValueChange = { note = it },
                label = "",
                minLines = 3,
                maxLines = 5,
                placeholderText = "A few words are enough…",
            )
            ReferenceAction(if (saving) "Saving…" else "Save and continue", onClick = {
                if (saving) return@ReferenceAction
                saving = true
                saveError = null
                scope.launch {
                    runCatching {
                        // No shim any more: `selected` comes from MOODS, so it
                        // IS the wire value. This used to read
                        // `if (selected == "Clear") "Good"` because the screen
                        // displayed a word it did not send.
                        val wireMood = selected
                        val level = when (intensity) {
                            "Light" -> 2
                            "Medium" -> 3
                            else -> 5
                        }
                        Api.checkIn(wireMood, note.trim(), "sparkles", level)
                    }.onSuccess {
                        Haptics.success()
                        onSaved()
                    }.onFailure {
                        saveError = it.userMessage("Couldn't save your check-in. Please try again.")
                    }
                    saving = false
                }
            })
            saveError?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Danger)
            }
        }
    }
}

@Composable
fun WeeklyInsightsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf("Summary") }
    var metrics by remember { mutableStateOf<JSONArray?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(reloadKey) {
        loading = true
        loadError = null
        runCatching { Api.insightsWeekly() }
            .onSuccess { metrics = it.optJSONArray("metrics") }
            .onFailure { loadError = it.userMessage("Couldn't load weekly insights. Please try again.") }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).background(CardFill.copy(alpha = .97f))
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Periwinkle.copy(alpha = .07f))
                    .clickable { onBack() }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Periwinkle) }
            Column(Modifier.weight(1f)) {
                Text(
                    "Insights",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                    color = TextPrimary,
                )
                Text("Summary, trends and plan", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Danger.copy(alpha = .09f))
                    .clickable { onOpen("crisis") }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = Danger) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 14.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("INSIGHTS", style = MaterialTheme.typography.labelSmall, color = Warm)
            Text(
                "Understand\nwithout\nbeing judged.",
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                color = TextPrimary,
            )
            Text(
                "Understand patterns cautiously without diagnosis or causal claims.",
                style = MaterialTheme.typography.bodyLarge, color = TextSoft,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Summary", "Trends", "Patterns", "Plan").forEach { label ->
                    val active = tab == label
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (active) Color.White else TextMuted,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(99.dp))
                            .background(if (active) Periwinkle else CardFill)
                            .clickable {
                                tab = label
                                when (label) {
                                    "Trends" -> onOpen("trends")
                                    "Patterns" -> onOpen("patterns")
                                    "Plan" -> onOpen("plan")
                                }
                            }.padding(vertical = 13.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardFill).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val weekly = metrics
                when {
                    loading -> Text(
                        stringResource(R.string.insights_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(16.dp),
                    )
                    loadError != null -> Column(Modifier.fillMaxWidth()) {
                        Text(loadError.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Danger)
                        TextButton(onClick = { reloadKey++ }) {
                            Text(stringResource(R.string.common_try_again), color = Periwinkle)
                        }
                    }
                    weekly == null || weekly.length() == 0 -> Text(
                        stringResource(R.string.insights_metrics_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(16.dp),
                    )
                    else -> (0 until minOf(3, weekly.length())).forEach { index ->
                        val metric = weekly.getJSONObject(index)
                        Column(
                            Modifier.weight(1f).height(66.dp).clip(RoundedCornerShape(17.dp))
                                .background(FieldFill),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(metric.optString("value", "—"), style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                            Text(
                                localizedInsightMetricLabel(metric.optString("label")),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
            ReferenceDayRow("⌁", "Trends", "Week, month and three months", Ok) { onOpen("trends") }
            ReferenceDayRow("✦", "Patterns", "Evidence, limits and suggested actions", Warm) { onOpen("patterns") }
            ReferenceDayRow("✓", "Goals and plan", "Flexible progress without streaks", Warm) { onOpen("goals") }
            ReferenceDayRow("♙", "Personal baseline", "Update your starting point", Ok) { onOpen("baseline") }
        }
    }
}

@Composable
fun ReferenceTrendsScreen(onBack: () -> Unit, onReviewPatterns: () -> Unit, onUrgent: () -> Unit) {
    var window by rememberSaveable { mutableStateOf("Month") }
    var data by remember { mutableStateOf<Trends?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(window) {
        loading = true; error = null
        val days = when (window) { "Week" -> 7; "3 months" -> 90; else -> 30 }
        runCatching { parseTrends(Api.trends(days)) }
            .onSuccess { data = it }
            .onFailure { error = it.userMessage("Couldn't load trends. Please try again.") }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).background(CardFill.copy(alpha = .97f)).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Periwinkle.copy(alpha = .07f))
                    .clickable { onBack() }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Periwinkle) }
            Column(Modifier.weight(1f)) {
                Text("Trends", style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily(Font(R.font.newsreader))), color = TextPrimary)
                Text("Week, month and 3 months", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Danger.copy(alpha = .09f))
                    .clickable { onUrgent() }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = Danger) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 36.dp, vertical = 14.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("TRENDS", style = MaterialTheme.typography.labelSmall, color = Warm)
            Text(
                "Direction\nover time.",
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                color = TextPrimary,
            )
            Text("Understand patterns cautiously without diagnosis or causal claims.", style = MaterialTheme.typography.bodyLarge, color = TextSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Week", "Month", "3 months").forEach { label ->
                    val active = window == label
                    Text(
                        label, style = MaterialTheme.typography.titleSmall,
                        color = if (active) Color.White else TextMuted,
                        modifier = Modifier.clip(RoundedCornerShape(99.dp))
                            .background(if (active) Periwinkle else CardFill)
                            .clickable { window = label }.padding(horizontal = 15.dp, vertical = 12.dp),
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().height(235.dp).clip(RoundedCornerShape(26.dp))
                    .background(CardFill).padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("MOOD AND SLEEP", style = MaterialTheme.typography.labelSmall, color = Warm)
                val values = data?.mood?.points?.map { (it.value / 5f).coerceIn(0f, 1f) }.orEmpty()
                if (values.size >= 2) Canvas(Modifier.fillMaxWidth().height(105.dp)) {
                    val p = Path()
                    values.forEachIndexed { index, value ->
                        val x = size.width * index / (values.size - 1)
                        val y = size.height * (1f - value)
                        if (index == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    drawPath(p, Periwinkle, style = Stroke(width = 5f))
                    values.forEachIndexed { index, value ->
                        drawCircle(Periwinkle, 5f, androidx.compose.ui.geometry.Offset(size.width * index / (values.size - 1), size.height * (1f - value)))
                    }
                }
                Text(
                    when {
                        loading -> "Loading your data…"
                        error != null -> error.orEmpty()
                        data?.isEmpty != false -> "No mood or sleep data in this window yet. Missing days stay blank."
                        else -> "${data?.mood?.logged ?: 0} mood days · ${data?.sleep?.logged ?: 0} sleep nights. Missing days are not treated as negative."
                    },
                    style = MaterialTheme.typography.labelSmall, color = TextMuted,
                )
            }
            ReferenceAction("Review patterns", onClick = onReviewPatterns)
        }
    }
}

@Composable
fun ReferencePatternsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    var patterns by remember { mutableStateOf<List<Learned>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { parsePatterns(Api.patterns()) }
            .onSuccess { patterns = it }
            .onFailure { error = it.userMessage("Couldn't load patterns. Please try again.") }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).background(CardFill.copy(alpha = .97f)).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Periwinkle.copy(alpha = .07f))
                    .clickable { onBack() }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Periwinkle) }
            Column(Modifier.weight(1f)) {
                Text("Patterns", style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily(Font(R.font.newsreader))), color = TextPrimary)
                Text("Observations, not diagnoses", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Danger.copy(alpha = .09f))
                    .clickable { onOpen("crisis") }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = Danger) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 14.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("OBSERVATIONS, NOT DIAGNOSES", style = MaterialTheme.typography.labelSmall, color = Warm)
            Text(
                "Patterns\nCereBro\nnoticed.",
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                color = TextPrimary,
            )
            Text(
                "Understand patterns cautiously without diagnosis or causal claims.",
                style = MaterialTheme.typography.bodyLarge, color = TextSoft,
            )
            when {
                error != null -> Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Danger)
                patterns == null -> Text("Reading your patterns…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                patterns!!.isEmpty() -> Text("No supported pattern yet. More check-ins may reveal one; nothing is guessed.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                else -> patterns!!.forEachIndexed { index, pattern ->
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 142.dp).clip(RoundedCornerShape(24.dp))
                            .background(if (index % 2 == 0) CardFill else FieldFill).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(pattern.statement, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(pattern.basis, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun ReferenceSleepInsightsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    var window by rememberSaveable { mutableStateOf("Week") }
    var nights by remember { mutableStateOf<List<SleepNight>?>(null) }
    var summary by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(window) {
        val days = when (window) { "Month" -> 30; "3 months" -> 90; else -> 7 }
        error = null
        coroutineScope {
            val logs = async { runCatching { parseNights(Api.sleepLogs(days)) } }
            val stats = async { runCatching { Api.sleepSummary(days) } }
            logs.await().onSuccess { nights = it }.onFailure { error = it.userMessage("Couldn't load sleep insights.") }
            stats.await().onSuccess { summary = it }.onFailure { error = it.userMessage("Couldn't load sleep insights.") }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).background(CardFill.copy(alpha = .97f)).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Periwinkle.copy(alpha = .07f))
                    .clickable { onBack() }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Periwinkle) }
            Column(Modifier.weight(1f)) {
                Text("Sleep insights", style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily(Font(R.font.newsreader))), color = TextPrimary)
                Text("Trends without diagnosis", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Danger.copy(alpha = .09f))
                    .clickable { onOpen("crisis") }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = Danger) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 14.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("SLEEP INSIGHTS", style = MaterialTheme.typography.labelSmall, color = Warm)
            Text(
                "Look for\ndirection,\nnot perfection.",
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                color = TextPrimary,
            )
            Text("Support tonight’s sleep without diagnosis or guaranteed outcomes.", style = MaterialTheme.typography.bodyLarge, color = TextSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Week", "Month", "3 months").forEach { label ->
                    val active = window == label
                    Text(
                        label, style = MaterialTheme.typography.titleSmall,
                        color = if (active) Color.White else TextMuted,
                        modifier = Modifier.clip(RoundedCornerShape(99.dp))
                            .background(if (active) Periwinkle else CardFill)
                            .clickable { window = label }.padding(horizontal = 15.dp, vertical = 12.dp),
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().height(218.dp).clip(RoundedCornerShape(25.dp))
                    .background(CardFill).padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    val avg = summary?.optInt("avg_duration_min")?.takeIf { summary?.optBoolean("enough_data") == true }
                    val spread = summary?.optInt("bedtime_consistency_min")?.takeIf { summary?.optBoolean("enough_data") == true }
                    val quality = summary?.optDouble("avg_quality")?.takeIf { summary?.optBoolean("enough_data") == true }
                    listOf(
                        (avg?.let { "${it / 60}h ${it % 60}m" } ?: "—") to "average",
                        (spread?.let { "${it}m" } ?: "—") to "bedtime range",
                        (quality?.let { String.format(Locale.getDefault(), "%.1f/5", it) } ?: "—") to "rest quality",
                    ).forEach { (value, label) ->
                        Column(
                            Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(16.dp)).background(FieldFill),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text(value, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                }
                val chartNights = nights.orEmpty().take(7).reversed()
                val heights = chartNights.map { ((it.duration / 600f) * 88).roundToInt().coerceIn(8, 88) }
                Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    heights.forEachIndexed { index, h ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Box(
                                Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(7.dp, 7.dp, 2.dp, 2.dp))
                                    .background(Brush.verticalGradient(listOf(Color(0xFFA56A99), Color(0xFF9AB59C)))),
                            )
                            Text(chartNights.getOrNull(index)?.date?.takeLast(2).orEmpty(), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(FieldFill).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("WHAT CEREBRO NOTICED", style = MaterialTheme.typography.labelSmall, color = Warm)
                Text(
                    when {
                        error != null -> error.orEmpty()
                        nights == null -> "Reading your sleep diary…"
                        nights!!.size < 3 -> "Log at least three nights before CereBro describes a sleep direction."
                        else -> "${nights!!.size} nights are shown from your diary. Missing nights stay blank; this is a record, not a diagnosis."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = TextSoft,
                )
                TextButton(onClick = { onOpen("reminders") }) {
                    Text("Review wind-down reminders →", style = MaterialTheme.typography.labelLarge, color = Periwinkle)
                }
            }
        }
    }
}

@Composable
fun PatternDetailScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).background(CardFill.copy(alpha = .97f)).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Periwinkle.copy(alpha = .07f))
                    .clickable { onBack() }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Periwinkle) }
            Column(Modifier.weight(1f)) {
                Text("Pattern detail", style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily(Font(R.font.newsreader))), color = TextPrimary)
                Text("Evidence and limits", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Danger.copy(alpha = .09f))
                    .clickable { onOpen("crisis") }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = Danger) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 30.dp, vertical = 14.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("PATTERN DETAIL", style = MaterialTheme.typography.labelSmall, color = Warm)
            Text(
                "Evening\ngrounding\nand calmer\ncheck-ins.",
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                color = TextPrimary,
            )
            Text(
                "CereBro observed an association across four evenings. It cannot conclude that grounding caused the change.",
                style = MaterialTheme.typography.bodyLarge, color = TextSoft,
            )
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(FieldFill).padding(horizontal = 18.dp, vertical = 15.dp),
            ) {
                listOf(
                    "Examples" to "4 evenings",
                    "Average change" to "−1 intensity level",
                    "Confidence" to "Early signal",
                ).forEachIndexed { index, row ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(row.first, style = MaterialTheme.typography.bodyMedium, color = TextSoft, modifier = Modifier.weight(1f))
                        Text(row.second, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    }
                    if (index < 2) Box(Modifier.fillMaxWidth().height(.7.dp).background(LineStroke))
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardFill).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Try a personal experiment", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(
                    "Use a three-minute grounding practice at 9 PM on three evenings, then compare how you feel.",
                    style = MaterialTheme.typography.bodyMedium, color = TextSoft,
                )
                TextButton(onClick = { onOpen("dailyplan") }) {
                    Text("Add to daily plan →", style = MaterialTheme.typography.labelLarge, color = Periwinkle)
                }
            }
        }
    }
}

@Composable
fun ReferenceDailyPlanScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    var plan by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // `loaded`, not `plan != null`, decides whether the fetch is still running.
    // Api.activePlan() is runCatching{}.getOrNull() (Session.kt:853) — it NEVER
    // throws — so the old `.onFailure` branch could not fire, and "no plan yet"
    // was indistinguishable from "still loading". The screen sat on
    // "Loading your plan…" forever, with no empty state and no retry.
    // PlanScreen.kt:72 already carries a comment about avoiding exactly this;
    // the Reference copy reintroduced it.
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        error = null
        plan = Api.activePlan()
        loaded = true
    }
    LaunchedEffect(Unit) { load() }
    fun toggle(step: JSONObject) {
        if (busy) return
        scope.launch {
            busy = true
            runCatching { Api.togglePlanStep(step.optString("id"), !step.optBoolean("done")) }
                .onSuccess { plan = it }
                .onFailure { error = it.userMessage("Couldn't update this step.") }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).background(CardFill.copy(alpha = .97f)).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Periwinkle.copy(alpha = .07f))
                    .clickable { onBack() }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Periwinkle) }
            Column(Modifier.weight(1f)) {
                Text("Daily plan", style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily(Font(R.font.newsreader))), color = TextPrimary)
                Text("A flexible guide", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Danger.copy(alpha = .09f))
                    .clickable { onOpen("crisis") }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = Danger) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 14.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("FLEXIBLE, NOT A STREAK", style = MaterialTheme.typography.labelSmall, color = Warm)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Your day\nin four\nsmall steps.",
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                    color = TextPrimary, modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Periwinkle.copy(alpha = .055f)),
                    contentAlignment = Alignment.Center,
                ) { Text("↻", color = Periwinkle, style = MaterialTheme.typography.titleMedium) }
            }
            Text("Do what helps. Skip what does not. The plan adapts tomorrow.", style = MaterialTheme.typography.bodyLarge, color = TextSoft)
            val steps = plan?.optJSONArray("steps")
            when {
                error != null -> Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Danger)
                !loaded -> Text("Loading your plan…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                // Loaded and still nothing: either there is no plan yet or the
                // fetch failed silently. Both are the same to the user — say so
                // and offer the way out, rather than spinning.
                plan == null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "No plan yet. One is built from your check-ins, or you can ask for it now.",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                    Text(
                        "Try again",
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .clickable(enabled = !busy) { scope.launch { loaded = false; load() } }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleSmall, color = Periwinkle,
                    )
                }
                steps == null || steps.length() == 0 -> Text("Your plan has no steps yet.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                else -> (0 until steps.length()).forEach { index ->
                    val step = steps.getJSONObject(index)
                    ReferenceDayRow(
                        step.optString("symbol", "○"),
                        step.optString("title"),
                        step.optString("detail"),
                        if (step.optBoolean("done")) Ok else Warm,
                        done = step.optBoolean("done"),
                        badge = "Optional",
                    ) { toggle(step) }
                }
            }
            // Begin OPENS the step. It used to call toggle(next), which is
            // Api.togglePlanStep(id, done = true) — the button said Begin and
            // did Finish, ticking off work nobody had done and quietly
            // inflating the plan's progress. Completion stays where it belongs:
            // the row's own tap, which the user chooses deliberately.
            //
            // The label follows the state too. With everything done this button
            // opened the journal composer while still reading "Begin next
            // unfinished step" — the same lie in a smaller way.
            val nextStep = run {
                val current = plan?.optJSONArray("steps")
                (0 until (current?.length() ?: 0)).map { current!!.getJSONObject(it) }
                    .firstOrNull { !it.optBoolean("done") }
            }
            ReferenceAction(
                if (nextStep != null) "Begin next unfinished step" else "Write something instead",
            ) {
                if (nextStep != null) {
                    // planStepRoute is the shared symbol → surface contract
                    // (same one the Oracle widgets and web Home use). An
                    // unmapped symbol still lands somewhere useful.
                    onOpen(planStepRoute(nextStep.optString("symbol")) ?: "toolkit")
                } else {
                    onOpen("journal/new")
                }
            }
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(26.dp))
                    .background(CardFill).border(1.dp, LineStroke, RoundedCornerShape(26.dp))
                    .clickable { onOpen("reminders") }, contentAlignment = Alignment.Center,
            ) { Text("Edit reminders", style = MaterialTheme.typography.titleSmall, color = Periwinkle) }
        }
    }
}

@Composable
fun ReferenceGoalsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    var showCreateGoal by remember { mutableStateOf(false) }
    var goalName by remember { mutableStateOf("") }
    var goals by remember { mutableStateOf<JSONArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reloadKey) {
        error = null
        runCatching { Api.goals() }
            .onSuccess { goals = it }
            .onFailure { error = it.userMessage("Couldn't load goals. Please try again.") }
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).background(CardFill.copy(alpha = .97f)).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Periwinkle.copy(alpha = .07f))
                    .clickable { onBack() }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Periwinkle) }
            Column(Modifier.weight(1f)) {
                Text("Goals", style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily(Font(R.font.newsreader))), color = TextPrimary)
                Text("No streak pressure", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Danger.copy(alpha = .09f))
                    .clickable { onOpen("crisis") }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = Danger) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 19.dp, vertical = 14.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("NO STREAK PRESSURE", style = MaterialTheme.typography.labelSmall, color = Warm)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Your goals.",
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Periwinkle.copy(alpha = .055f))
                        .clickable { showCreateGoal = true }, contentAlignment = Alignment.Center,
                ) { Text("+", style = MaterialTheme.typography.titleMedium, color = Periwinkle) }
            }
            // Goals are not patterns. This used to read "Understand patterns
            // cautiously without diagnosis or causal claims." — the
            // Trends/Patterns disclaimer, pasted onto a screen that makes no
            // claim about either. A caveat repeated where it does not apply is
            // how it becomes wallpaper on the screens where it does.
            Text(
                "Something to move towards, at whatever pace it takes.",
                style = MaterialTheme.typography.bodyLarge, color = TextSoft,
            )
            when {
                error != null -> Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Danger)
                goals == null -> Text("Loading your goals…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                goals!!.length() == 0 -> Text("No goals yet. Create one when something feels worth supporting.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                else -> (0 until goals!!.length()).forEach { index ->
                    val goal = goals!!.getJSONObject(index)
                    ReferenceDayRow(
                        if (goal.optString("status") == "achieved") "✓" else "○",
                        goal.optString("title"),
                        goal.optString("status", "active"),
                        if (goal.optString("status") == "active") Ok else Warm,
                    ) {
                        if (!busy) scope.launch {
                            busy = true
                            runCatching { Api.decomposeGoal(goal.optString("id")) }
                                .onSuccess { onOpen("plan") }
                                .onFailure { error = it.userMessage("Couldn't open this goal as a plan.") }
                            busy = false
                        }
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(26.dp))
                    .background(CardFill).border(1.dp, LineStroke, RoundedCornerShape(26.dp))
                    .clickable { onOpen("baseline") },
                contentAlignment = Alignment.Center,
            ) {
                Text("Update baseline", style = MaterialTheme.typography.titleSmall, color = Periwinkle)
            }
        }
    }
    if (showCreateGoal) {
        Box(
            Modifier.fillMaxSize().background(Color(0x993B313B)).clickable { showCreateGoal = false },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(CardFill).clickable(enabled = false) { }.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.width(40.dp).height(5.dp).clip(CircleShape).background(LineStroke).align(Alignment.CenterHorizontally))
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Periwinkle.copy(alpha = .06f)),
                    contentAlignment = Alignment.Center,
                ) { Text("⌁", style = MaterialTheme.typography.headlineMedium, color = Periwinkle) }
                Text("EDIT SAFELY", style = MaterialTheme.typography.labelSmall, color = Warm)
                Text(
                    "Create a gentle goal",
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily(Font(R.font.newsreader))), color = TextPrimary,
                )
                Text("Goal name", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                androidx.compose.material3.OutlinedTextField(
                    value = goalName, onValueChange = { goalName = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                )
                ReferenceAction(if (busy) "Creating…" else "Create goal") {
                    if (!busy && goalName.isNotBlank()) scope.launch {
                        busy = true
                        runCatching { Api.addGoal(goalName.trim()) }
                            .onSuccess {
                                goalName = ""
                                showCreateGoal = false
                                reloadKey++
                            }
                            .onFailure { error = it.userMessage("Couldn't create this goal.") }
                        busy = false
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(25.dp))
                        .border(1.dp, LineStroke, RoundedCornerShape(25.dp)).clickable { showCreateGoal = false },
                    contentAlignment = Alignment.Center,
                ) { Text("Cancel", style = MaterialTheme.typography.titleSmall, color = Periwinkle) }
            }
        }
    }
    }
}

@Composable
fun ReferenceGoalDetailScreen(title: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).background(CardFill.copy(alpha = .97f)).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Periwinkle.copy(alpha = .07f)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Periwinkle) }
            Column(Modifier.weight(1f)) {
                Text("Goal detail", maxLines = 1, style = MaterialTheme.typography.titleLarge.copy(fontFamily = serif), color = TextPrimary)
                Text("Reminder and history", maxLines = 1, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Danger.copy(alpha = .09f)).clickable { onOpen("crisis") },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = Danger) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 14.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("GOAL DETAIL", style = MaterialTheme.typography.labelSmall, color = Warm)
            Text(
                if (title == "A calmer evening") "A calmer\nevening" else "Wind down\nbefore 10 PM",
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = serif), color = TextPrimary,
            )
            // Same paste as the Goals list had — a goal detail makes no claim
            // about patterns or diagnosis, so it said something it does not do.
            Text(
                "Small steps count. Skipping a day does not undo them.",
                style = MaterialTheme.typography.bodyLarge, color = TextSoft,
            )
            Column(
                Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(26.dp)).background(CardFill).padding(18.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                val heights = listOf(38, 69, 0, 88, 59, 77, 0)
                Row(Modifier.fillMaxWidth().height(108.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
                    heights.forEachIndexed { index, height ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (height > 0) Box(
                                Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                    .background(Brush.verticalGradient(listOf(Color(0xFFA56898), Color(0xFFAABBA8)))),
                            )
                            Text(listOf("W", "T", "", "F", "S", "M", "T")[index], style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                }
                Text("Blank days are simply blank—not failures.", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            ReferenceAction("Resume goal") { }
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(25.dp)).background(CardFill)
                    .border(1.dp, LineStroke, RoundedCornerShape(25.dp)).clickable { onOpen("reminders") },
                contentAlignment = Alignment.Center,
            ) { Text("Edit reminder", style = MaterialTheme.typography.titleSmall, color = Periwinkle) }
            ReferenceAction("Delete goal") { onBack() }
        }
    }
}

@Composable
fun ReferenceBaselineScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("personal_baseline", android.content.Context.MODE_PRIVATE) }
    var stress by remember { mutableFloatStateOf(prefs.getInt("stress", 10).toFloat()) }
    var sleep by remember { mutableFloatStateOf(prefs.getInt("sleep", 5).toFloat()) }
    var mood by remember { mutableFloatStateOf(prefs.getInt("mood", 6).toFloat()) }
    var energy by remember { mutableFloatStateOf(prefs.getInt("energy", 4).toFloat()) }
    val serif = FontFamily(Font(R.font.newsreader))

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).background(CardFill.copy(alpha = .97f)).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Periwinkle.copy(alpha = .07f)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Periwinkle) }
            Column(Modifier.weight(1f)) {
                Text("Personal baseline", maxLines = 1, style = MaterialTheme.typography.titleLarge.copy(fontFamily = serif), color = TextPrimary)
                Text("Update anytime", maxLines = 1, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Danger.copy(alpha = .09f)).clickable { onOpen("crisis") },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = Danger) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 34.dp, vertical = 14.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("PRIVATE BASELINE", style = MaterialTheme.typography.labelSmall, color = Warm)
            Text("Your starting\npoint.", style = MaterialTheme.typography.displayMedium.copy(fontFamily = serif), color = TextPrimary)
            Text(
                "These self-ratings provide context for trends.\nThey are not clinical scores.",
                style = MaterialTheme.typography.bodyLarge, color = TextSoft,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
            BaselineSliderRow("Stress", stress) { stress = it }
            BaselineSliderRow("Sleep", sleep) { sleep = it }
            BaselineSliderRow("Mood", mood) { mood = it }
            BaselineSliderRow("Energy", energy) { energy = it }
            ReferenceAction("Save baseline") {
                prefs.edit()
                    .putInt("stress", stress.toInt()).putInt("sleep", sleep.toInt())
                    .putInt("mood", mood.toInt()).putInt("energy", energy.toInt()).apply()
            }
        }
    }
}

@Composable
private fun BaselineSliderRow(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.width(92.dp), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        androidx.compose.material3.Slider(
            value = value, onValueChange = onValueChange, valueRange = 1f..10f, steps = 8,
            modifier = Modifier.weight(1f),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Color(0xFF6B2865), activeTrackColor = Color(0xFF6B2865),
                inactiveTrackColor = Color(0xFFE7E3E4), activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Text("${value.toInt()}/10", modifier = Modifier.width(42.dp), style = MaterialTheme.typography.bodyMedium, color = TextSoft)
    }
}

@Composable
private fun TodayTopBar(modifier: Modifier = Modifier, onUrgent: () -> Unit) {
    val serif = FontFamily(Font(R.font.newsreader))
    Column(
        modifier.fillMaxWidth().background(CardFill.copy(alpha = .96f)),
    ) {
    Row(
        Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        BrandMark(size = 36.dp, showGlow = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                "Today", maxLines = 1, softWrap = false,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = serif, lineHeight = 24.sp), color = TextPrimary,
            )
            Text(
                "Your next helpful step", maxLines = 1, softWrap = false,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp), color = TextMuted,
            )
        }
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(Danger.copy(alpha = .09f))
                .clickable(onClick = onUrgent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = Danger, modifier = Modifier.size(22.dp))
        }
    }
    }
}
