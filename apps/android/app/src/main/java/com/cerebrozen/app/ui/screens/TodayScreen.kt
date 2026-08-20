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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.HealthAndSafety
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
import com.cerebrozen.app.ui.theme.AccentSoft
import com.cerebrozen.app.ui.theme.HeroInk
import com.cerebrozen.app.ui.theme.HeroInkMuted
import com.cerebrozen.app.ui.theme.HeroPale
import com.cerebrozen.app.ui.theme.HeroPlumBottom
import com.cerebrozen.app.ui.theme.HeroPlumTop
import com.cerebrozen.app.ui.theme.OnPrimary
import com.cerebrozen.app.ui.theme.AppTheme
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.fillMaxHeight

/** Mirrors iOS `Dummy.moods` (cross-stack mood taxonomy).
 *
 * [name]/[note]/[symbol] are WIRE VALUES — they go to the backend and are
 * hand-duplicated across iOS/web (see CLAUDE.md), so they are never translated.
 * [labelRes]/[noteRes] are the display copy and localize freely. */
internal data class MoodOption(
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
// internal since V3-c: the chat opener asks the same six moods (one taxonomy
// source; the wire contract comment above applies to every caller).
internal val MOODS = listOf(
    MoodOption("Good", "Clear", "sparkles", 2, R.string.mood_good, R.string.mood_good_note) { Ok },
    MoodOption("Anxious", "Loud thoughts", "exclamationmark.triangle", 4, R.string.mood_anxious, R.string.mood_anxious_note) { Warm },
    MoodOption("Low", "Heavy", "moon", 4, R.string.mood_low, R.string.mood_low_note) { Periwinkle },
    MoodOption("Tired", "Need rest", "drop", 3, R.string.mood_tired, R.string.mood_tired_note) { Cyan },
    MoodOption("Overwhelmed", "Too much at once", "exclamationmark.triangle", 5, R.string.mood_overwhelmed, R.string.mood_overwhelmed_note) { Warm },
    MoodOption("Not sure", "Closest fit right now", "minus", 3, R.string.mood_unsure, R.string.mood_unsure_note) { Periwinkle },
)

// The per-state icon lives in Common.kt as `moodIcon` (audit K wave 2): the
// old text glyphs (◌ ⌁ ↓ ☾ ⁘ …) rendered as six near-identical dots at tile
// size, and onboarding's first check-in needs the same faces as this grid.

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

// V2-e: ContentRail + railKindLabel left with Today's rail (Explore/V2-f may
// revive the rail there; railKindFor/artKindForTitle stay — tested pure fns).

/**
 * One mood, as a tile you can hit without aiming.
 *
 * A soft orb in the mood's own colour over a tinted well — large enough to be
 * the thing you look at, rather than a pill in a row of four. Tapping it IS the
 * check-in; there is no second step.
 */
@Composable
private fun MoodTile(
    mood: MoodOption,
    enabled: Boolean,
    marked: Boolean = false,
    /** V2-b: inside THE CARD the six states sit 3-across at ~58dp — icon +
     * one word, no note line — so the ask stays smaller than the step it
     * produces (the old 2×2 grid at 120dp physically outweighed the hero). */
    compact: Boolean = false,
    onPick: () -> Unit,
) {
    val tint = mood.tint()
    val shape = RoundedCornerShape(if (compact) 14.dp else 18.dp)
    // Reference fidelity (Aira as-built moodrow): the chosen tile fills with
    // the MOOD's own colour, not the generic accent — the feeling keeps its
    // hue when picked. Ink is OnPrimary, the token built for accent fills
    // (near-white on Dawn's deep tints, dark ink on Night's pale ones).
    val selectedFill = tint
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
            .heightIn(min = if (compact) 58.dp else 120.dp)
            .padding(
                horizontal = if (compact) 6.dp else 16.dp,
                vertical = if (compact) 8.dp else 15.dp,
            ),
        horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 9.dp),
    ) {
        Box(
            Modifier.size(if (compact) 26.dp else 36.dp).clip(CircleShape)
                // The icon well wears the mood's own hue (14% wash) — the
                // approved prototype's emotion-by-colour language; a plain
                // CardFill circle read as six grey settings rows.
                .background(if (marked) OnPrimary.copy(alpha = .16f) else tint.copy(alpha = .14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                moodIcon(mood.name), contentDescription = null,
                tint = if (marked) OnPrimary else tint,
                modifier = Modifier.size(if (compact) 15.dp else 19.dp),
            )
        }
        Text(
            stringResource(mood.labelRes),
            // bodySmall in compact: labelMedium clipped "Overwhelmed" to
            // "Overwhelm" in a 720px-wide 3-across chip, and labelSmall made it
            // WIDER (it is the eyebrow role, +1.4 tracking). bodySmall is the
            // narrow tracking-free role that fits (device walk, 2026-08-15).
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
            color = if (marked) OnPrimary else TextSoft, maxLines = 1,
        )
        if (!compact) {
            Text(stringResource(mood.noteRes), style = MaterialTheme.typography.bodySmall,
                color = if (marked) OnPrimary.copy(alpha = .82f) else TextMuted, maxLines = 1)
        }
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

/**
 * Whether the care card may claim "picked with you, not for you"
 * (`home_care_provenance`).
 *
 * Only a plan the SERVER built from this account's own signals was picked with
 * anyone. The offline/no-plan fallback is the same three-minute grounding
 * practice for everyone — it names its own reason in the row itself, and must
 * not inherit a personalization claim it cannot support. The line used to sit
 * outside the branch and so was printed under the fallback too (CLAIMS_MAP §3).
 */
internal fun showsCarePlanProvenance(kind: HeroKind): Boolean =
    kind == HeroKind.PLAN_STEP || kind == HeroKind.PLAN_DONE

/** Routes that genuinely run on the device with no network — the only ones the
 * hero may claim work offline.
 *
 * `sounds` is deliberately absent: a soundscape streams unless it was
 * downloaded first, and `talk` needs a model on the other end. A chip that
 * promises offline and then fails on the Mumbai local is worse than no chip. */
internal val OFFLINE_HERO_ROUTES = setOf(
    "toolkit", "breathe/reset", "breathe/box", "ground", "tipp",
    "imagery", "ritual", "gratitude", "cbt", "safetyplan",
    // The intro screen for `ground` — the practice it starts runs offline, so
    // the fallback hero's offline chip stays true rather than disappearing.
    "groundingintro",
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

// V2-e: FoldSection left with the folds it folded.

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
    // V3-b: the last seven nights as (day-letter, height-fraction) for the
    // sleep card — same fetch that feeds the morning banner.
    var sleepBars by remember { mutableStateOf(listOf<Pair<String, Float?>>()) }
    var bloom by remember { mutableIntStateOf(0) }        // E2: one-shot per successful check-in
    // V2-b: the rail, the weekly-metrics tiles and the banner-dismissal tick
    // left with the folds they served — the rail is Explore's (V2-e), the
    // metrics are Insights' own, and Tonight is a permanent row, not a
    // dismissible banner.
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
            // One fetch feeds the recent list, the teaser count, and the
            // "+N more today" whisper under the capped rows.
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
            sleepBars = sleepBarsFrom(logs, LocalDate.now())
        }
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
    // V2-b: the first-run hint chip replaces the 4-stop tour modal.
    var showHint by remember { mutableStateOf(!TourState.isDone()) }
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

    // Hoisted so the fixed top bar can tell whether anything has scrolled
    // underneath it (see TodayTopBar's hairline).
    val homeScroll = rememberScrollState()
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
            .verticalScroll(homeScroll)
            .graphicsLayer { translationY = rise.value }
            // Scaffold already places this screen below the system status bar.
            // 66dp fixed app bar + 14dp breathing room = 80dp here.
            .padding(horizontal = 24.dp).padding(top = 80.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(Space.item),
    ) {
        // ── V3-b JOURNEY HERO ────────────────────────────────────────────
        //
        // The approved companion-first reference: one plum pane holding the
        // greeting, the presence sentence, the active program's day, a quiet
        // progress bar and Tonight's door. Plum in BOTH themes on purpose —
        // the one deliberately dark surface of the light world (Color.kt
        // HeroPlum*), exactly as the reference draws it.
        val hourNow = LocalTime.now().hour
        val daysPresent = week.count { it.second }
        val doneToday = stepsDoneToday(plan, LocalDate.now())
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(Radius.hero))
                .background(Brush.linearGradient(listOf(HeroPlumTop, HeroPlumBottom)))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(Space.item),
        ) {
            val friend = stringResource(R.string.today_friend)
            Text(
                stringResource(R.string.today_greeting_format, greeting(), userName.ifBlank { friend }),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily(Font(R.font.newsreader)),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    // The reference's as-built Dawn greets in serif ITALIC —
                    // the one flourish its light face allows itself. Night
                    // stays upright (italic on the dark pane read as slant).
                    fontStyle = if (AppTheme.isNight) FontStyle.Normal else FontStyle.Italic,
                    fontSize = 24.sp, lineHeight = 27.sp,
                ),
                color = HeroInk, maxLines = 2,
            )
            // V5: the hero notices what you actually just did (heroLineFor).
            val minutesSinceCheckIn = recent.firstOrNull()?.createdAt?.let {
                runCatching {
                    java.time.Duration.between(
                        java.time.OffsetDateTime.parse(it), java.time.OffsetDateTime.now(),
                    ).toMinutes()
                }.getOrNull()
            }
            val quietDaysHero = quietDaysSince(recent.firstOrNull()?.createdAt, java.time.OffsetDateTime.now())
            Text(
                when (
                    heroLineFor(
                        offline = Session.servedStale,
                        minutesSinceCheckIn = minutesSinceCheckIn,
                        stepsDoneToday = doneToday,
                        quietDays = quietDaysHero,
                        daysPresent = daysPresent,
                    )
                ) {
                    HeroLine.OFFLINE -> stringResource(R.string.today_banner_offline)
                    HeroLine.JUST_CHECKED_IN -> stringResource(R.string.home_hero_just_checked)
                    HeroLine.STEP_DONE -> stringResource(R.string.home_hero_step_done)
                    HeroLine.QUIET -> stringResource(R.string.home_hero_quiet)
                    HeroLine.WEEK -> stringResource(R.string.today_week_sentence, daysPresent)
                    HeroLine.EMPTY -> stringResource(R.string.today_presence_empty)
                },
                style = MaterialTheme.typography.bodyMedium, color = HeroInkMuted,
            )
            program?.let { prog ->
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "${prog.optInt("day")}",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = FontFamily(Font(R.font.newsreader)),
                            fontSize = 44.sp, lineHeight = 44.sp,
                        ),
                        color = HeroInk,
                    )
                    Column {
                        Text(stringResource(R.string.home_hero_day).uppercase(), style = MaterialTheme.typography.labelSmall, color = HeroInkMuted)
                        Text(prog.optString("title"), style = MaterialTheme.typography.bodyMedium, color = HeroInk, maxLines = 1)
                    }
                }
            }
            // Only show progress when a named programme gives it context.
            program?.let { p ->
                val days = p.optInt("days").coerceAtLeast(1)
                val fraction = p.optInt("day").coerceIn(0, days) / days.toFloat()
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)).background(HeroInk.copy(alpha = .16f))) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(HeroPale))
                }
            }
            val tonightCd = stringResource(R.string.today_tonight_title)
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HeroInk.copy(alpha = .10f))
                    .clickable { onOpen("sleep") }
                    .semantics { contentDescription = tonightCd }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = HeroPale, modifier = Modifier.size(16.dp))
                Text(
                    // Just the clock-aware sentence — prefixing the "Tonight"
                    // title made the pill read "Tonight · Tonight's wind-down"
                    // (device walk 2026-08-16).
                    when {
                        hourNow < 11 && !lastNightLogged -> stringResource(R.string.today_banner_sleep)
                        com.cerebrozen.app.ui.theme.isWindDownHour(hourNow) -> stringResource(R.string.today_tonight_wind)
                        else -> stringResource(R.string.today_tonight_ready)
                    },
                    style = MaterialTheme.typography.bodySmall, color = HeroInk,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = HeroPale, modifier = Modifier.size(16.dp))
            }
        }

        // First-run: one dismissible hint line (V2: no surprise modals).
        if (showHint) {
            InfoBanner(
                icon = Icons.Outlined.SelfImprovement,
                text = stringResource(R.string.today_hint),
                onDismiss = { TourState.markDone(); showHint = false },
            )
        }

        // Queued writes earn a surface whether or not reads are stale.
        //
        // This used to read `if (Session.servedStale) Outbox.count() else 0`,
        // which hid the banner — and its Send now — at exactly the moment it
        // becomes useful: the network came back. Walked on a CPH2681
        // 2026-08-20: a check-in logged with the backend unreachable said
        // "Kept on this device", the link was restored, the next read
        // succeeded, and the entry then went BOTH unsent and invisible — the
        // row reverted to the older server value with nothing on screen
        // admitting a write was pending. It was not lost (the drain at app
        // start sent it, POST /moods 201), but "not lost" is not the same as
        // "the person can see what is happening".
        val queuedWrites = com.cerebrozen.app.net.Outbox.count()
        if (queuedWrites > 0) {
            // Back online with a queue is a transient state, so try it once
            // rather than only offering a button. The banner stays until the
            // drain actually empties the queue.
            LaunchedEffect(Session.servedStale, queuedWrites) {
                if (!Session.servedStale) {
                    runCatching { com.cerebrozen.app.net.Outbox.drain() }
                    reload()
                }
            }
            InfoBanner(
                icon = Icons.Outlined.CloudOff,
                text = stringResource(
                    // The offline sentence explains the stale reads too, and
                    // would be a lie once reads are live again.
                    if (Session.servedStale) R.string.today_banner_offline_queued
                    else R.string.today_banner_queued_online,
                    queuedWrites,
                ),
                actionLabel = stringResource(R.string.today_banner_offline_send),
                onAction = { scope.launch { runCatching { com.cerebrozen.app.net.Outbox.drain() }; reload() } },
            )
        }

        // ── V3-b TODAY'S CARE ────────────────────────────────────────────
        //
        // The plan's next step is worked out by the same pure helpers the V2
        // hero used, but renders as the first care row instead of a full-volume
        // hero — chat is the flagship now; Home summarises. Three rows at most.
        val planSteps = plan?.optJSONArray("steps")
        val stepCount = planSteps?.length() ?: 0
        val stepObjs = remember(plan, stepCount) {
            (0 until stepCount).map { planSteps!!.getJSONObject(it) }
        }
        val doneCount = stepObjs.count { it.optBoolean("done") }
        val nextStep = nextPlanStepIndex(
            titles = stepObjs.map { it.optString("title") },
            done = stepObjs.map { it.optBoolean("done") },
            hour = hourNow,
        )?.let { stepObjs[it] }
        // An offline session already knows a live plan cannot arrive. Waiting
        // for the network timeout left a blank shimmer in the most important
        // Home card even though the local grounding fallback is ready now.
        val heroKind = heroKindFor(
            planLoaded = planLoaded || Session.servedStale,
            hasPlan = plan != null && stepCount > 0,
            hasNextStep = nextStep != null,
        )
        Column(
            Modifier.fillMaxWidth().quiet(RoundedCornerShape(Radius.card)).padding(cardPadding()),
            verticalArrangement = Arrangement.spacedBy(Space.item),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_care_eyebrow).uppercase(), style = MaterialTheme.typography.labelSmall, color = EyebrowMuted)
                Text(
                    stringResource(R.string.home_care_details),
                    style = MaterialTheme.typography.labelLarge, color = Periwinkle,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpen("plan") }.padding(horizontal = 4.dp),
                )
            }
            if (stepCount > 0) {
                // Reference signature component (Aira hydration ring): a small
                // arc that fills on entry, beside the honest count. Presence
                // framing still holds — it shows what IS done and never what
                // was missed, and nothing resets.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    ProgressRing(done = doneCount, total = stepCount)
                    Text(
                        stringResource(R.string.home_care_done, doneCount, stepCount),
                        style = MaterialTheme.typography.bodySmall, color = TextMuted,
                    )
                }
            }
            when (heroKind) {
                HeroKind.LOADING -> ShimmerBox(Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp))
                HeroKind.PLAN_STEP -> {
                    val step = nextStep!!
                    CareRow(
                        // The step's OWN icon (symbol first, then its title) —
                        // a calendar beside "Nature Walk" was the icon saying
                        // nothing at all.
                        icon = stepIcon(step.optString("symbol"), step.optString("title")),
                        title = step.optString("title"),
                        // The provenance sentence stays honest per generator —
                        // the AI path reads journal titles, the rule path never
                        // does (heroWhyRes).
                        sub = stringResource(heroWhyRes(plan?.optString("source").orEmpty())),
                        actionLabel = stringResource(R.string.today_hero_start),
                    ) { onOpen(planStepRoute(step.optString("symbol")) ?: "plan") }
                }
                HeroKind.PLAN_DONE -> {
                    Text(stringResource(R.string.today_hero_done_title), style = MaterialTheme.typography.titleSmall, color = TextSoft)
                    Text(stringResource(R.string.today_hero_done_why), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                else -> {
                    CareRow(
                        icon = Icons.Outlined.Spa,   // grounding — the fallback step
                        title = stringResource(R.string.today_hero_fallback_title),
                        sub = stringResource(R.string.today_hero_why_fallback),
                        actionLabel = stringResource(R.string.today_hero_begin),
                    ) { onOpen("groundingintro") }
                }
            }
            // The evening prompt invites WRITING; the row below is the door to
            // what you have already written. V3 dropped the Journal tab, and
            // without this the room was unreachable before 17:00 — a whole
            // feature behind a clock (found on the demo walk 2026-08-16).
            if (hourNow >= 17) {
                CareRow(
                    icon = Icons.Outlined.Edit,
                    title = stringResource(R.string.today_prompt_title),
                    sub = stringResource(R.string.today_prompt_sub),
                    actionLabel = null,
                ) { onOpen("journal/new") }
            } else {
                CareRow(
                    icon = Icons.Outlined.MenuBook,
                    title = stringResource(R.string.today_journal_title),
                    sub = stringResource(R.string.today_journal_sub),
                    actionLabel = null,
                ) { onOpen("journal") }
            }
            if (showsCarePlanProvenance(heroKind)) {
                Text(
                    stringResource(R.string.home_care_provenance),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }

        // ── V3-b HOW ARE YOU TODAY ───────────────────────────────────────
        //
        // The conversation is the primary check-in now; this card is the
        // ten-second path, writing the same six wire moods. The 2-across grid
        // is the device-proven shape (V2 walk: "Overwhelmed" clips at 3-across).
        var reAsk by remember { mutableStateOf(false) }
        val earlierTodayMood = recent.firstOrNull()?.takeIf { last ->
            val t = relativeTime(last.createdAt, java.time.OffsetDateTime.now())
            t != null && t !is RelTime.Yesterday && t !is RelTime.Days
        }?.mood
        val asking = reAsk || (loggedMood == null && earlierTodayMood == null)
        val checkinFailed = stringResource(R.string.today_checkin_failed)
        var settled by remember { mutableStateOf(false) }
        LaunchedEffect(loggedMood) {
            settled = false
            if (loggedMood != null) {
                kotlinx.coroutines.delay(8_000)
                settled = true
            }
        }
        Box {
        Column(
            Modifier.fillMaxWidth().quiet(RoundedCornerShape(Radius.card)).padding(cardPadding()),
            verticalArrangement = Arrangement.spacedBy(Space.item),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_mood_eyebrow).uppercase(), style = MaterialTheme.typography.labelSmall, color = EyebrowMuted)
                Text(
                    stringResource(R.string.home_mood_history),
                    style = MaterialTheme.typography.labelLarge, color = Periwinkle,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpen("trends") }.padding(horizontal = 4.dp),
                )
            }
            if (asking) {
                MOODS.chunked(2).forEachIndexed { row, pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEachIndexed { col, mood ->
                            Box(Modifier.weight(1f).appear(row * 2 + col, rise = 8f)) {
                                MoodTile(
                                    mood, enabled = !busy, compact = true,
                                    marked = mood.name.equals(earlierTodayMood, ignoreCase = true),
                                ) {
                                    if (busy) return@MoodTile
                                    busy = true; status = null
                                    // Say it back NOW, then send. `Outbox.send`
                                    // documents that "the caller shows the entry
                                    // optimistically either way" — but the card
                                    // only morphed once the socket had resolved,
                                    // so on a connection that hangs rather than
                                    // refuses, a tap looked ignored for as long
                                    // as the timeout took. Found by the e2e walk
                                    // failing on a CI emulator with no host to
                                    // reach, while the handset (instant
                                    // ECONNREFUSED) looked fine.
                                    Haptics.success()
                                    if (!reduceMotion) bloom++
                                    loggedId = null
                                    loggedQueued = false
                                    loggedMood = mood
                                    reAsk = false
                                    scope.launch {
                                        try {
                                            val row2 = Api.checkIn(mood.name, mood.note, mood.symbol, mood.intensity)
                                            loggedId = row2?.optString("id").orEmpty()
                                            // Only now can the line honestly say
                                            // "queued": before the attempt returns
                                            // nobody knows which it will be.
                                            loggedQueued = row2 == null
                                            reload()
                                        } catch (e: Exception) {
                                            // A guest's 401 is an account state, not a
                                            // failure (walk defect, 2026-08-15): the
                                            // answer still counts on this device, so the
                                            // acknowledgement above stands.
                                            if (!e.isGuestGate()) {
                                                // A real refusal — a 4xx `Outbox` rethrew
                                                // rather than queued. Take the
                                                // acknowledgement back rather than leave
                                                // "noted" over a check-in that is nowhere.
                                                loggedMood = null
                                                loggedId = null
                                                loggedQueued = false
                                                status = e.userMessage(checkinFailed)
                                            }
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
                ) {
                    Text(
                        stringResource(R.string.today_checkin_note),
                        style = MaterialTheme.typography.labelLarge,
                        color = Periwinkle,
                    )
                }
            } else {
                val mood = loggedMood
                if (mood != null) {
                    // Undoable for 8s, then settled into one quiet line.
                    val undoneMsg = stringResource(R.string.today_checkin_undone)
                    if (settled) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape)
                                    .background(Brush.radialGradient(listOf(mood.tint().copy(alpha = 0.95f), mood.tint().copy(alpha = 0.35f)))),
                            )
                            Text(
                                stringResource(R.string.today_checkin_settled, stringResource(mood.labelRes)),
                                style = MaterialTheme.typography.bodySmall, color = TextMuted,
                            )
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape)
                                    .background(Brush.radialGradient(listOf(mood.tint().copy(alpha = 0.85f), mood.tint().copy(alpha = 0.25f)))),
                            )
                            Text(
                                stringResource(R.string.today_checkin_logged, stringResource(mood.labelRes)) +
                                    (if (loggedQueued) " · " + stringResource(R.string.today_checkin_queued) else ""),
                                style = MaterialTheme.typography.bodySmall, color = TextMuted,
                                maxLines = 2,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                enabled = !busy,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
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
                                        status = undoneMsg
                                        busy = false
                                        reload()
                                    }
                                },
                            ) { Text(stringResource(R.string.today_checkin_undo), color = Periwinkle, maxLines = 1) }
                        }
                    }
                } else {
                    // An earlier check-in today, said back, with a re-ask door.
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            recent.firstOrNull()?.let { stringResource(R.string.today_earlier_line, displayCheckInLine(it)) }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall, color = TextMuted,
                            maxLines = 2,
                        )
                        TextButton(
                            modifier = Modifier.align(Alignment.End),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                            onClick = { reAsk = true },
                        ) { Text(stringResource(R.string.home_mood_again), color = Periwinkle, maxLines = 1) }
                    }
                }
            }
            if (week.isNotEmpty()) PresenceWeekRing(week)
        }
        if (bloom > 0) BloomRing(bloom, Accent.home, Modifier.matchParentSize())
        }

        AnimatedVisibility(visible = status != null) {
            Text(status.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }

        // V2-e: the reminders ask, in context — once, right after a check-in
        // lands, and only while reminders are off.
        val appContext = androidx.compose.ui.platform.LocalContext.current
        var reminderAskDone by remember {
            mutableStateOf(
                runCatching { Session.prefGet("reminder_prompted") == "true" }.getOrDefault(true) ||
                    appContext.getSharedPreferences("cerebro", android.content.Context.MODE_PRIVATE)
                        .getBoolean("reminder_on", false),
            )
        }
        if (loggedMood != null && !reminderAskDone) {
            InfoBanner(
                icon = Icons.Outlined.NotificationsNone,
                text = stringResource(R.string.today_reminder_ask),
                actionLabel = stringResource(R.string.today_reminder_ask_action),
                onAction = {
                    runCatching { Session.prefPut("reminder_prompted", "true") }
                    reminderAskDone = true
                    onOpen("reminders")
                },
                onDismiss = {
                    runCatching { Session.prefPut("reminder_prompted", "true") }
                    reminderAskDone = true
                },
            )
        }

        // ── V3-b YOUR SLEEP: seven bars and a sentence, one door ─────────
        val sleepCd = stringResource(R.string.home_sleep_eyebrow)
        Column(
            Modifier.fillMaxWidth().quiet(RoundedCornerShape(Radius.card))
                .clickable { onOpen("sleep") }
                .semantics { contentDescription = sleepCd }
                .padding(cardPadding()),
            verticalArrangement = Arrangement.spacedBy(Space.item),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_sleep_eyebrow).uppercase(), style = MaterialTheme.typography.labelSmall, color = EyebrowMuted)
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
            }
            val loggedNights = sleepBars.count { it.second != null }
            if (loggedNights == 0) {
                Text(stringResource(R.string.home_sleep_empty), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            } else {
                // Reference graph language (Aira hgraph): seven day-slots
                // always, past nights in the quiet wash, the NEWEST logged
                // night solid — one bar carries the weight, the rest are
                // context. A day with no night draws no bar at all.
                val newest = sleepBars.indexOfLast { it.second != null }
                Row(
                    Modifier.fillMaxWidth().height(64.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    sleepBars.forEachIndexed { bi, (label, frac) ->
                        val latest = bi == newest
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (frac != null) {
                                Box(
                                    // 62% of the column: at full width the bars
                                    // rendered as squares on a 7-slot week.
                                    Modifier.fillMaxWidth(0.62f).height((48 * frac).dp)
                                        // The week measures itself out, oldest
                                        // first — the height is the value.
                                        .grow(index = bi)
                                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                        // A themed wash, not AccentSoft: on Night
                                        // that token is a dark plum that vanished
                                        // against the card (device walk).
                                        .background(if (latest) Periwinkle else Periwinkle.copy(alpha = .30f)),
                                )
                            }
                            Text(
                                label, style = MaterialTheme.typography.labelSmall,
                                color = if (latest) Periwinkle else TextMuted, maxLines = 1,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.home_sleep_nights, loggedNights),
                    style = MaterialTheme.typography.bodySmall, color = TextMuted,
                )
            }
        }

        // ── V3-b quiet days: supportive re-engagement, never guilt ───────
        // (Aira "missed 3 days" pattern; presence framing rules apply — no
        // counters, nothing resets, and the door leads to the conversation.)
        val quietDays = quietDaysSince(recent.firstOrNull()?.createdAt, java.time.OffsetDateTime.now())
        if (quietDays != null && quietDays >= 3) {
            Column(
                Modifier.fillMaxWidth().quiet(RoundedCornerShape(Radius.card)).padding(cardPadding()),
                verticalArrangement = Arrangement.spacedBy(Space.item),
            ) {
                Text(stringResource(R.string.home_quiet_title), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(stringResource(R.string.home_quiet_body), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                PrimaryButton(stringResource(R.string.home_quiet_cta), modifier = Modifier.fillMaxWidth()) { onOpen("talk") }
            }
        }

        // What the companion remembers — one quiet door to Privacy & memory.
        TextButton(onClick = { onOpen("privacy") }, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.home_remembers),
                style = MaterialTheme.typography.labelLarge, color = TextMuted,
            )
        }

        // Guest: one quiet line, not a card — and never twice (V2).
        if (Session.guestMode) {
            TextButton(onClick = { onOpen("auth") }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.today_guest_line),
                    style = MaterialTheme.typography.labelLarge, color = TextMuted,
                )
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
        onSettings = { onOpen("you") },
        // The one cue that content continues above the fold. A fixed bar with
        // no edge leaves a scrolled page looking like the top of the page.
        scrolled = homeScroll.value > 8,
    )
    }
}

/** V2-a: the hero CTA is the house pill again. The old hand-rolled plum box
 * (raw hex, no Role.Button, no haptic parity) was the most important button in
 * the app and the only one off-system. Kept as a named wrapper so call sites
 * read the same until V2-b reworks Today. */
@Composable
private fun ReferenceAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    PrimaryButton(text = text, modifier = modifier.fillMaxWidth(), onClick = onClick)

/** V2-b: one quick-help door — icon in a tinted well + one word, ≥56dp. Four
 * of these make the "Quick helps" row: direct doors to the practices a
 * distressed user reaches for, with no browsing in between. */
@Composable
private fun QuickHelp(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(FieldFill)
            .border(0.7.dp, LineStroke.copy(alpha = .25f), shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button) { Haptics.soft(0.4f); onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(AccentSoft.copy(alpha = .6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Periwinkle, modifier = Modifier.size(16.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSoft, maxLines = 1)
    }
}

@Composable
private fun ReferenceDayRow(
    symbol: androidx.compose.ui.graphics.vector.ImageVector,
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
        ) { Icon(symbol, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp)) }
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
        CereBroTopBar(
            title = stringResource(R.string.groundingintro_title),
            subtitle = stringResource(R.string.groundingintro_subtitle),
            onBack = onBack,
            onUrgent = { onUrgent() },
        )

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 21.dp, vertical = 14.dp).padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // The accent is the shadow tint and the orb, nothing that carries text.
            // This was the only call site in the app that overrode it, with a
            // plum a shade off the BrandPrimary default (7B376E vs 8A4A78) —
            // so the override was a literal buying nothing.
            FocusCard(pastel = true) {
                Text("GROUND · 3 MINUTES", style = MaterialTheme.typography.labelSmall, color = Warm)
                Text(
                    "5 things\nyou can see.",
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                    color = TextPrimary,
                )
                Text(
                    // Not "interruption-tolerant regulation exercise" — clinical
                    // vocabulary on a first-use surface (audit K).
                    "A calm, guided steadying practice. Pause or stop any time.",
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
                        color = if (i == 0) OnPrimary else Periwinkle,
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
                    // These are read as facts about the practice one tap away, so
                    // they have to be true of it. "Voice guidance on · Soft chime
                    // between steps" was true of neither: GroundingScreen has no
                    // TextToSpeech, no chime and no sound of any kind — it is a
                    // silent, text-paced exercise. Stating it as ON also implied a
                    // setting the user could find and change, and there isn't one.
                    // Replaced with what the practice actually offers rather than
                    // deleted, because the row's job is to set expectations before
                    // someone commits three minutes.
                    val facts = listOf(
                        Triple(Icons.Outlined.AccessTime, "About 3 minutes", "End early whenever you need."),
                        Triple(Icons.Outlined.Headphones, "Reads at your pace", "You move each step on yourself."),
                        Triple(Icons.Outlined.Visibility, "Minimal motion", "Plain text, works with a screen reader."),
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
        CereBroTopBar(
            title = stringResource(R.string.checkindetail_title),
            subtitle = stringResource(R.string.checkindetail_subtitle),
            onBack = onBack,
            onUrgent = { onUrgent() },
        )

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
                            ) {
                                Icon(moodIcon(mood.name), contentDescription = null,
                                    // `Color.White` on a Periwinkle fill measured 1.93:1 in Night (the token
                                    // is #D9ACDE there — a LIGHT pink) against 10.85:1 in Dawn, so the
                                    // selected option was the one you could not read, in the theme most
                                    // people use at night. OnPrimary is the ink this fill was designed for:
                                    // 8.77:1 Night, 10.61:1 Dawn.
                                    tint = if (active) OnPrimary else Periwinkle,
                                    modifier = Modifier.size(18.dp))
                            }
                            Text(
                                stringResource(mood.labelRes), style = MaterialTheme.typography.titleSmall,
                                color = if (active) OnPrimary else TextPrimary,
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
                        color = if (active) OnPrimary else Periwinkle,
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
    var metrics by remember { mutableStateOf<JSONArray?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var guestGated by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(reloadKey) {
        loading = true
        loadError = null
        runCatching { Api.insightsWeekly() }
            .onSuccess { metrics = it.optJSONArray("metrics") }
            .onFailure {
                guestGated = it.isGuestGate()
                loadError = it.userMessage("Couldn't load weekly insights. Please try again.")
            }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        CereBroTopBar(
            title = stringResource(R.string.weeklyinsights_title),
            subtitle = stringResource(R.string.weeklyinsights_subtitle),
            onBack = onBack,
            onUrgent = { onOpen("crisis") },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 14.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.insights_eyebrow), style = MaterialTheme.typography.labelSmall, color = Warm)
            Text(
                stringResource(R.string.insights_heading),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                color = TextPrimary,
            )
            Text(
                stringResource(R.string.insights_heading_sub),
                style = MaterialTheme.typography.bodyMedium, color = TextSoft,
            )
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
                    // The guest 401 is not retryable — insights are computed
                    // from an account's check-ins (audit K guest-state class).
                    guestGated -> GuestSignInCard(onOpen = onOpen, modifier = Modifier.fillMaxWidth())
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
                        val value = metric.optString("value", "—")
                        Column(
                            Modifier.weight(1f).height(78.dp).clip(RoundedCornerShape(17.dp))
                                .background(FieldFill).padding(horizontal = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            // The server sends both "8" and "7h 41m avg" into the
                            // same ~55dp-wide tile. One type size cannot serve
                            // both: at headlineSmall the long form broke its line
                            // and spilled out of the tile AND the card. The long
                            // form steps down instead of overflowing.
                            Text(
                                value,
                                style = if (value.length > 5) MaterialTheme.typography.titleSmall
                                        else MaterialTheme.typography.headlineSmall,
                                color = TextPrimary,
                                maxLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
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
            ReferenceDayRow(
                Icons.Outlined.ShowChart, stringResource(R.string.insights_row_trends),
                stringResource(R.string.insights_row_trends_sub), Ok,
            ) { onOpen("trends") }
            ReferenceDayRow(
                Icons.Outlined.Insights, stringResource(R.string.insights_row_patterns),
                stringResource(R.string.insights_row_patterns_sub), Warm,
            ) { onOpen("patterns") }
            ReferenceDayRow(
                Icons.Outlined.Flag, stringResource(R.string.insights_row_goals),
                stringResource(R.string.insights_row_goals_sub), Warm,
            ) { onOpen("goals") }
            ReferenceDayRow(
                Icons.Outlined.SelfImprovement, stringResource(R.string.insights_row_baseline),
                stringResource(R.string.insights_row_baseline_sub), Ok,
            ) { onOpen("baseline") }
        }
    }
}

// ReferenceSleepInsightsScreen is the one Reference screen kept: patterns
// and trends now route to the real PatternScreen/TrendsScreen, but this
// has no real twin — it is the week/month/3-month view, and it is linked
// from the Sleep rhythm line. Its sibling mocks are gone.
@Composable
fun ReferenceSleepInsightsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    // V2-f: `window` holds a locale-free id; the chips render localized labels.
    var window by rememberSaveable { mutableStateOf("week") }
    var nights by remember { mutableStateOf<List<SleepNight>?>(null) }
    var summary by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val siLoadFailed = stringResource(R.string.si_load_failed)
    LaunchedEffect(window) {
        val days = when (window) { "month" -> 30; "3m" -> 90; else -> 7 }
        error = null
        coroutineScope {
            // Api.sleepLogs takes a ROW LIMIT, not a day count — it builds
            // "/sleep?limit=N" — while Api.sleepSummary(days) is day-based. So
            // passing `days` to both made the chart and the stat tiles describe
            // different windows: seven nights logged across two months rendered
            // under "Week". The rows are still capped (a week cannot hold more
            // than `days` nights) but the window is now decided by the date.
            val cutoff = java.time.LocalDate.now().minusDays(days.toLong() - 1)
            val logs = async {
                runCatching {
                    parseNights(Api.sleepLogs(days)).filter { night ->
                        // A row with an unparseable date is kept rather than
                        // silently dropped from the user's own history.
                        runCatching { !java.time.LocalDate.parse(night.date).isBefore(cutoff) }
                            .getOrDefault(true)
                    }
                }
            }
            val stats = async { runCatching { Api.sleepSummary(days) } }
            logs.await().onSuccess { nights = it }.onFailure { error = it.userMessage(siLoadFailed) }
            stats.await().onSuccess { summary = it }.onFailure { error = it.userMessage(siLoadFailed) }
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
            ) { Icon(Icons.Outlined.ArrowBack, stringResource(R.string.common_back), tint = Periwinkle) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.si_title), style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily(Font(R.font.newsreader))), color = TextPrimary)
                Text(stringResource(R.string.si_subtitle), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Danger.copy(alpha = .09f))
                    .clickable { onOpen("crisis") }, contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.HealthAndSafety, stringResource(R.string.common_urgent_support), tint = Danger) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 14.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.si_eyebrow).uppercase(), style = MaterialTheme.typography.labelSmall, color = Warm)
            Text(
                stringResource(R.string.si_hero),
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                color = TextPrimary,
            )
            Text(stringResource(R.string.si_lede), style = MaterialTheme.typography.bodyLarge, color = TextSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("week" to R.string.si_win_week, "month" to R.string.si_win_month, "3m" to R.string.si_win_3m).forEach { (id, labelRes) ->
                    val active = window == id
                    Text(
                        stringResource(labelRes), style = MaterialTheme.typography.titleSmall,
                        color = if (active) OnPrimary else TextMuted,
                        modifier = Modifier.clip(RoundedCornerShape(99.dp))
                            .background(if (active) Periwinkle else CardFill)
                            .clickable { window = id }.padding(horizontal = 15.dp, vertical = 12.dp),
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
                        (avg?.let { "${it / 60}h ${it % 60}m" } ?: "—") to stringResource(R.string.si_stat_avg),
                        (spread?.let { "${it}m" } ?: "—") to stringResource(R.string.si_stat_bedtime),
                        (quality?.let { String.format(Locale.getDefault(), "%.1f/5", it) } ?: "—") to stringResource(R.string.si_stat_quality),
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
                                    .background(Brush.verticalGradient(listOf(Cyan, Periwinkle))),
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
                Text(stringResource(R.string.si_noticed).uppercase(), style = MaterialTheme.typography.labelSmall, color = Warm)
                Text(
                    when {
                        error != null -> error.orEmpty()
                        nights == null -> stringResource(R.string.si_reading)
                        nights!!.size < 3 -> stringResource(R.string.si_need_three)
                        else -> stringResource(R.string.si_shown, nights!!.size)
                    },
                    style = MaterialTheme.typography.bodyMedium, color = TextSoft,
                )
                TextButton(onClick = { onOpen("reminders") }) {
                    Text(stringResource(R.string.si_reminders_link), style = MaterialTheme.typography.labelLarge, color = Periwinkle)
                }
            }
        }
    }
}


/** Delegates to [CereBroTopBar] — see the note there on the nine that existed. */
@Composable
private fun ProgressRing(done: Int, total: Int, size: androidx.compose.ui.unit.Dp = 38.dp) {
    // The arc animates from empty on first composition (Reduce Motion snaps to
    // the value — a ring that never draws would be a blank hole, and this
    // carries the count beside it either way).
    val target = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val reduceMotion = rememberReduceMotion()
    val swept = remember { Animatable(0f) }
    LaunchedEffect(target, reduceMotion) {
        if (reduceMotion) swept.snapTo(target) else swept.animateTo(target, tween(900, easing = FastOutSlowInEasing))
    }
    val track = AccentSoft
    val arc = Periwinkle
    Canvas(Modifier.size(size)) {
        val stroke = Stroke(width = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val inset = stroke.width / 2
        drawArc(
            color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(this.size.width - stroke.width, this.size.height - stroke.width),
            style = stroke,
        )
        if (swept.value > 0f) {
            drawArc(
                color = arc, startAngle = -90f, sweepAngle = 360f * swept.value, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(this.size.width - stroke.width, this.size.height - stroke.width),
                style = stroke,
            )
        }
    }
}

@Composable
private fun CareRow(
    icon: ImageVector,
    title: String,
    sub: String,
    actionLabel: String?,
    onClick: () -> Unit,
) {
    // V3-b: one row of the Today's-care card — icon well, title + provenance,
    // then either a Start pill or a quiet chevron. The whole row is the target.
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            // Circular accent-mist well — the reference's as-built icon
            // language (its Dawn pass rounded every icon well to a circle).
            Modifier.size(38.dp).clip(CircleShape).background(AccentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = Periwinkle, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            if (actionLabel != null) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = Periwinkle,
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(99.dp))
                        .background(Periwinkle.copy(alpha = .10f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        if (actionLabel == null) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

/** V3-b: the last seven days as (day-letter, height-fraction) bars for Home's
 * sleep card. A night's height is its duration against a 10-hour ceiling.
 *
 * All SEVEN days are returned, oldest first, so the week keeps its rhythm and
 * two logged nights don't stretch to half the card each (reference hgraph).
 * A day with no night carries a **null** fraction — drawn as an empty slot,
 * never a zero-height bar that would read as "you slept nothing": missing
 * stays missing, a record and not a diagnosis. */
internal fun sleepBarsFrom(logs: JSONArray, today: LocalDate): List<Pair<String, Float?>> {
    val byDate = HashMap<String, Float>()
    for (i in 0 until logs.length()) {
        val o = logs.optJSONObject(i) ?: continue
        val mins = runCatching {
            val b = java.time.LocalTime.parse(o.optString("bedtime").take(5))
            val w = java.time.LocalTime.parse(o.optString("wake_time").take(5))
            ((w.toSecondOfDay() - b.toSecondOfDay() + 86_400) % 86_400) / 60
        }.getOrNull() ?: continue
        if (mins > 0) byDate[o.optString("date")] = (mins / 600f).coerceIn(0.12f, 1f)
    }
    return (6 downTo 0).map { back ->
        val d = today.minusDays(back.toLong())
        d.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()) to
            byDate[d.toString()]
    }
}

/**
 * What Home's hero says under the greeting.
 *
 * It used to say one thing forever — the week's presence count — whether you
 * had checked in ten minutes ago or not opened the app in a fortnight. A line
 * that never changes stops being read, and a companion that doesn't notice you
 * just did something isn't much of a companion.
 *
 * Ordered by what the person most recently DID, because that is the thing they
 * know to be true and will judge the app against:
 *  1. offline — the honest state always wins,
 *  2. a check-in in the last ~90 minutes — say it landed,
 *  3. a plan step finished today — say that instead of a weekly average,
 *  4. several quiet days — name it kindly and ask nothing,
 *  5. otherwise the week's presence, or the honest empty line.
 *
 * Presence framing throughout: it counts what happened, never what didn't.
 * Pure + unit-tested.
 */
internal enum class HeroLine { OFFLINE, JUST_CHECKED_IN, STEP_DONE, QUIET, WEEK, EMPTY }

/** How many plan steps were finished TODAY — `done` alone would keep saying
 * "one step done today" about something ticked last Tuesday. A step without a
 * usable `done_at` is not counted rather than assumed to be today's. Pure. */
internal fun stepsDoneToday(plan: JSONObject?, today: LocalDate): Int {
    val steps = plan?.optJSONArray("steps") ?: return 0
    return (0 until steps.length()).count { i ->
        val step = steps.optJSONObject(i) ?: return@count false
        if (!step.optBoolean("done")) return@count false
        runCatching {
            java.time.OffsetDateTime.parse(step.optString("done_at"))
                .atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate() == today
        }.getOrDefault(false)
    }
}

internal fun heroLineFor(
    offline: Boolean,
    minutesSinceCheckIn: Long?,
    stepsDoneToday: Int,
    quietDays: Int?,
    daysPresent: Int,
): HeroLine = when {
    offline -> HeroLine.OFFLINE
    minutesSinceCheckIn != null && minutesSinceCheckIn <= 90 -> HeroLine.JUST_CHECKED_IN
    stepsDoneToday > 0 -> HeroLine.STEP_DONE
    quietDays != null && quietDays >= 3 -> HeroLine.QUIET
    daysPresent > 0 -> HeroLine.WEEK
    else -> HeroLine.EMPTY
}

/** Whole days since the newest check-in; null when there has never been one
 * (a first day is not a "quiet" day) or the timestamp doesn't parse. */
internal fun quietDaysSince(createdAt: String?, now: java.time.OffsetDateTime): Int? =
    createdAt?.let {
        runCatching {
            java.time.Duration.between(java.time.OffsetDateTime.parse(it), now).toDays().toInt()
        }.getOrNull()
    }

@Composable
private fun TodayTopBar(
    modifier: Modifier = Modifier,
    onUrgent: () -> Unit,
    onSettings: () -> Unit,
    scrolled: Boolean = false,
) = CereBroTopBar(
    title = stringResource(R.string.tab_home),
    subtitle = stringResource(R.string.topbar_today_subtitle),
    modifier = modifier,
    onUrgent = onUrgent,
    onSettings = onSettings,
    scrolled = scrolled,
)
