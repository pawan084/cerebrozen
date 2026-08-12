package com.cerebrozen.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.cerebrozen.app.R
import com.cerebrozen.app.audio.Player
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.Accent2
import com.cerebrozen.app.ui.theme.ButtonDisabled
import com.cerebrozen.app.ui.theme.CardShadow
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Ink
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.OnPrimary
import com.cerebrozen.app.ui.theme.SleepHeroMoon
import com.cerebrozen.app.ui.theme.SleepHeroMoonGlow
import com.cerebrozen.app.ui.theme.TextBright
import com.cerebrozen.app.ui.theme.VeilWell
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.NightMid
import com.cerebrozen.app.ui.theme.SleepHeroBottom
import com.cerebrozen.app.ui.theme.SleepHeroEdge
import com.cerebrozen.app.ui.theme.SleepHeroEyebrow
import com.cerebrozen.app.ui.theme.SleepHeroInk
import com.cerebrozen.app.ui.theme.SleepHeroInkSoft
import com.cerebrozen.app.ui.theme.SleepHeroMeta
import com.cerebrozen.app.ui.theme.SleepHeroMid
import com.cerebrozen.app.ui.theme.SleepHeroMoon
import com.cerebrozen.app.ui.theme.SleepHeroMoonGlow
import com.cerebrozen.app.ui.theme.SleepHeroSpark
import com.cerebrozen.app.ui.theme.SleepHeroTimer
import com.cerebrozen.app.ui.theme.SleepHeroTop
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleSoft
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Warm
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt
import com.cerebrozen.app.ui.theme.FieldFill

/** Pure h/m split; the localized "7h 30m" copy resolves via [minutesLabel]. */
internal fun hoursMinutes(total: Int): Pair<Int, Int> = total / 60 to total % 60

/** "7h 30m". The unit letters are the only localizable part, so they arrive as
 * parameters — the function stays pure and unit-testable, and the UI feeds it
 * `unit_hour_short` / `unit_minute_short`. */
internal fun minutesToLabel(total: Int, hourUnit: String = "h", minuteUnit: String = "m"): String =
    "%d%s %02d%s".format(total / 60, hourUnit, total % 60, minuteUnit)

/** Localized long-duration label ("7h 30m") — display sites only. The screen
 * uses this rather than [minutesToLabel]: it pulls the unit strings itself, so a
 * call site doesn't have to carry `hUnit`/`mUnit` locals down to every Text. */
@Composable
internal fun minutesLabel(total: Int): String {
    val (h, m) = hoursMinutes(total)
    return stringResource(R.string.duration_h_m, h, m)
}

/** A human-sized duration for prose ("45m", "1h 50m") — no zero-hour prefix. */
@Composable
internal fun spreadLabelText(min: Int): String =
    if (min < 60) stringResource(R.string.duration_m, min) else minutesLabel(min)

internal fun hhmm(minutes: Int): String {
    val m = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)
    return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
}

/** Bed→wake length with the midnight wrap (23:00→07:00 = 480). The one derived
 * fact the user wants while adjusting the steppers. Pure. */
internal fun nightLengthMinutes(bed: Int, wake: Int): Int = ((wake - bed) % 1440 + 1440) % 1440

/** Tap fires once; holding arms after 400ms and repeats ~7 steps/sec until
 * release. Walking 23:00 to 03:30 by nine deliberate taps was stepper
 * punishment. */
private fun Modifier.pressRepeat(
    scope: kotlinx.coroutines.CoroutineScope,
    step: () -> Unit,
): Modifier = this.pointerInput(Unit) {
    detectTapGestures(
        onTap = { step() },
        onPress = {
            val repeater = scope.launch {
                delay(400)
                while (true) {
                    step()
                    delay(140)
                }
            }
            tryAwaitRelease()
            repeater.cancel()
        },
    )
}

internal data class SleepNight(
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

internal fun parseNights(rows: JSONArray): List<SleepNight> =
    (0 until rows.length()).map { i ->
        val n = rows.getJSONObject(i)
        SleepNight(
            n.getString("date"), n.optInt("duration_min"), n.optInt("quality"),
            parseClockMinutes(n.optString("bedtime")),
            parseClockMinutes(n.optString("wake_time")),
        )
    }

// ── Rhythm math (CBT-I Phase 1, REDESIGN §3.2) — pure + unit-tested ─────

/** Average bedtime→wake duration in minutes across logs that carry both times,
 * wrapping past midnight (23:30→07:00 = 450, 00:30→08:00 = 450). Null when
 * no log qualifies. */
internal fun averageSleepMinutes(logs: List<SleepNight>): Int? {
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
internal fun bedtimeSpreadMinutes(logs: List<SleepNight>): Int? {
    val anchored = logs.mapNotNull { it.bedMin }.map { ((it - 720) % 1440 + 1440) % 1440 }
    if (anchored.isEmpty()) return null
    return anchored.max() - anchored.min()
}

/** The one gentle CBT-I principle the data supports — consistency over
 * duration. Pure boundary (>90 min = varied). */
internal fun isVariedRhythm(spreadMin: Int): Boolean = spreadMin > 90

/** The same boundary, as the string RESOURCE for the copy it selects. Kept
 * alongside [isVariedRhythm] so the branch and the words it chooses can't drift;
 * `SleepInsightTest` asserts both agree at 90/91. */
@androidx.annotation.StringRes
internal fun rhythmPrinciple(spreadMin: Int): Int =
    if (isVariedRhythm(spreadMin)) R.string.sleep_rhythm_vary else R.string.sleep_rhythm_steady

/** Which block leads the Sleep tab. Morning through afternoon (4:00–16:59) the
 * check-in about last night leads — that's when there's a night to report.
 * From 17:00, and overnight until 3:59, the wind-down hero leads: someone
 * opening Sleep at 1am wants help going down, not a survey. */
internal fun checkInLeadsAt(hour: Int): Boolean = hour in 4..16

/** A human-sized duration for prose ("45m", "1h 50m") — no zero-hour prefix.
 * Pure twin of [spreadLabelText], for tests and non-composable callers. */
internal fun spreadLabel(min: Int, hourUnit: String = "h", minuteUnit: String = "m"): String =
    if (min < 60) "$min$minuteUnit" else minutesToLabel(min, hourUnit, minuteUnit)

/** "2026-08-02" → the chart's weekday letter; "·" for anything unparseable. */
internal fun dayLetterFor(date: String): String = runCatching {
    listOf("M", "T", "W", "T", "F", "S", "S")[LocalDate.parse(date).dayOfWeek.value - 1]
}.getOrDefault("·")

/** "2026-08-02" → "Sat 2 Aug" — the diary used to print raw ISO wire dates. */
internal fun humanDate(date: String): String = runCatching {
    LocalDate.parse(date).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))
}.getOrDefault(date)

/** The actual bedtime window across logs, as minutes-of-day (23:00 to 00:10 →
 * 1380 to 10), anchored at noon so midnight doesn't split it. More useful than
 * the bare spread: "your bedtimes landed 23:00–00:10" is actionable. Pure. */
internal fun bedtimeWindow(logs: List<SleepNight>): Pair<Int, Int>? {
    val anchored = logs.mapNotNull { it.bedMin }.map { ((it - 720) % 1440 + 1440) % 1440 }
    if (anchored.isEmpty()) return null
    return ((anchored.min() + 720) % 1440) to ((anchored.max() + 720) % 1440)
}

/** The diary's quiet milestone line — presence-framed status, not confetti.
 * Shows at the first night, and while the first full week forms. Pure. */
@androidx.annotation.StringRes
internal fun sleepMilestoneRes(count: Int): Int? = when {
    count == 1 -> R.string.sleep_milestone_first
    count in 7..9 -> R.string.sleep_milestone_week
    else -> null
}

/** Which glyph identifies a wind-down guide, by title keywords — sibling ring
 * art made four guides read as one card printed four times. Null = no badge. */
internal fun windDownGlyph(title: String): androidx.compose.ui.graphics.vector.ImageVector? {
    val t = title.lowercase()
    return when {
        listOf("bed").any { it in t } -> Icons.Outlined.Bedtime
        listOf("dim", "screen", "light").any { it in t } -> Icons.Outlined.LightMode
        listOf("wake", "clock", "steady").any { it in t } -> Icons.Outlined.Alarm
        listOf("slow", "breath", "body").any { it in t } -> Icons.Outlined.Spa
        else -> null
    }
}

// ── Cached-first paint (mirrors Home's snapshot) ────────────────────────
// The tab shimmer-then-popped on every visit; the last session's summary and
// nights now hydrate the first frame and the network refreshes in place.

internal fun sleepSnapshotOf(summary: JSONObject?, nights: List<SleepNight>): JSONObject =
    JSONObject().apply {
        summary?.let { put("summary", it) }
        put("nights", JSONArray().apply {
            nights.forEach { n ->
                put(
                    JSONObject().put("date", n.date).put("duration_min", n.duration)
                        .put("quality", n.quality)
                        .put("bedtime", n.bedMin?.let { hhmm(it) } ?: "")
                        .put("wake_time", n.wakeMin?.let { hhmm(it) } ?: ""),
                )
            }
        })
    }

internal fun sleepSnapshotNights(snap: JSONObject): List<SleepNight> =
    snap.optJSONArray("nights")?.let { parseNights(it) } ?: emptyList()

/** Sleep: morning check-in + honest weekly summary + diary, with a CBT-I-informed
 * layer on top — the job is improving sleep night by night, not measuring it. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(onOpen: (String) -> Unit = {}, onBack: (() -> Unit)? = null) {
    var quality by remember { mutableIntStateOf(0) }
    var bed by remember { mutableIntStateOf(23 * 60) }
    var wake by remember { mutableIntStateOf(7 * 60) }
    // Hydrate the first frame from the last session's snapshot; the network
    // refreshes everything in place (sleepSnapshotOf).
    val snap = remember {
        runCatching { com.cerebrozen.app.net.Session.prefGet("sleep_snapshot")?.let(::JSONObject) }.getOrNull()
    }
    var summary by remember { mutableStateOf(snap?.optJSONObject("summary")) }
    var nights by remember { mutableStateOf(snap?.let(::sleepSnapshotNights) ?: listOf()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Three states this screen used to collapse into one. A first load in
    // flight, a load that failed, and an account with no nights yet all drew
    // the same "start logging" empty state — so a user whose request had just
    // failed was told, in a friendly voice, that they had never logged a night.
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    // The saved-night settle line ("Rested · 23:00–07:00"), until Edit reopens.
    var savedSummaryLine by remember { mutableStateOf<String?>(null) }
    // Which past night the form is editing (the backend upserts by date) —
    // null means "tonight/last night" as always.
    var editDate by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<SleepNight?>(null) }
    // Enrollment, so the Programs door can say where you are instead of
    // telling an enrolled user to start.
    var program by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Duration unit letters — the only localizable part of "7h 30m".
    val hUnit = stringResource(R.string.unit_hour_short)
    val mUnit = stringResource(R.string.unit_minute_short)

    // Optional last-night prefill from Health Connect (Android's HealthKit analogue).
    val hcAvailable = remember { com.cerebrozen.app.health.HealthConnectSleep.available(context) }
    val hcDone = stringResource(R.string.sleep_hc_done)
    val hcNone = stringResource(R.string.sleep_hc_none)
    fun applyHealthConnect() {
        scope.launch {
            com.cerebrozen.app.health.HealthConnectSleep.readLastNight(context)?.let { (b, w) ->
                bed = b; wake = w; status = hcDone
            } ?: run { status = hcNone }
        }
    }
    val hcLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        if (granted.containsAll(com.cerebrozen.app.health.HealthConnectSleep.permissions)) applyHealthConnect()
    }

    suspend fun reload() {
        // Either read failing means the week we would draw is incomplete, and
        // an incomplete week rendered as a whole one is the honesty problem
        // this module's audit was about. The reads are independent, so they
        // run together — serial awaits made the tab pay the sum of the trips.
        coroutineScope {
            val summaryReq = async { runCatching { Api.sleepSummary() } }
            val nightsReq = async { runCatching { Api.sleepLogs() } }
            val programReq = async { runCatching { Api.activeProgram() } }
            val gotSummary = summaryReq.await().onSuccess { summary = it }.isSuccess
            val gotNights = nightsReq.await().onSuccess { nights = parseNights(it) }.isSuccess
            programReq.await().onSuccess { program = it }
            loadFailed = !(gotSummary && gotNights)
            if (gotSummary && gotNights) {
                runCatching {
                    com.cerebrozen.app.net.Session.prefPut(
                        "sleep_snapshot", sleepSnapshotOf(summary, nights).toString(),
                    )
                }
            }
        }
        loading = false
    }

    pendingDelete?.let { night ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingDelete = null },
            title = { Text(stringResource(R.string.sleep_delete_title)) },
            text = { Text(stringResource(R.string.sleep_delete_body, humanDate(night.date))) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { Api.deleteSleep(night.date) }
                            .onSuccess {
                                nights = nights.filterNot { it.date == night.date }
                                if (editDate == night.date) editDate = null
                                pendingDelete = null
                                reload()
                            }
                            .onFailure { status = it.message ?: context.getString(R.string.common_save_failed) }
                        busy = false
                    }
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    LaunchedEffect(Unit) { reload() }

    val scrollState = rememberScrollState()
    var refreshing by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFFCF8), Color(0xFFF6F0EA)))),
    ) {
        SleepBackgroundGlow()
        // Pull-to-refresh, same themed indicator as Home — the tab is just as
        // server-backed and had no refresh gesture at all.
        val ptrState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; runCatching { reload() }; refreshing = false } },
            state = ptrState,
            modifier = Modifier.fillMaxSize().padding(top = 66.dp),
            indicator = {
                androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                    state = ptrState, isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = CardFill, color = Periwinkle,
                )
            },
        ) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                // Tablets and landscape read a centred column, not a wall.
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        // The hero title doubles as the ambient bed's now-playing title, so both
        // read from the same resource and stay consistent.
        val calmerNight = stringResource(R.string.sleep_hero_title)
        val heroBlock: @Composable () -> Unit = {
        PremiumWindDownHero(
            kind = "sleep",
            eyebrow = stringResource(R.string.sleep_hero_eyebrow),
            title = calmerNight,
            subtitle = stringResource(R.string.sleep_hero_subtitle),
            height = 220.dp,
            alive = true,   // W24: hero stars twinkle, imperceptibly slow
        ) {
            // A "TONIGHT" badge + a prominent play pill — the richer hero look, on
            // our tokens. Still just plays the ambient bed (no behaviour change).
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Plain eyebrow, not a chip: the bordered pill styling read as
                // a button and it was inert.
                Text(
                    stringResource(R.string.sleep_tonight_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = com.cerebrozen.app.ui.theme.ArtTextSoft,
                )
                val playCd = stringResource(R.string.sleep_play_cd)
                val heroPlaying = Player.nowPlaying == calmerNight && Player.isPlaying
                val reduceMotion = rememberReduceMotion()
                val playTransition = rememberInfiniteTransition(label = "sleepPlay")
                val playScale by playTransition.animateFloat(
                    0.98f, 1.03f,
                    infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                    label = "sleepPlayScale",
                )
                Box(
                    Modifier
                        .graphicsLayer {
                            // The pulse asks for the first tap; once playing it rests.
                            val s = if (reduceMotion || heroPlaying) 1f else playScale
                            scaleX = s
                            scaleY = s
                        }
                        .shadow(16.dp, RoundedCornerShape(50), ambientColor = Periwinkle.copy(alpha = 0.33f), spotColor = Periwinkle.copy(alpha = 0.33f))
                        .clip(RoundedCornerShape(50))
                        .background(Brush.linearGradient(listOf(Periwinkle, Accent2)))
                        .clickable { Player.toggle(context, calmerNight, "sleep") }
                        .semantics { contentDescription = playCd }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (heroPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            // The accent pill's own ink, so the label survives
                            // Night's pale plum fill as well as Dawn's deep one.
                            tint = OnPrimary, modifier = Modifier.size(18.dp),
                        )
                        Text(
                            stringResource(if (heroPlaying) R.string.sleep_hero_pause else R.string.common_play_label),
                            color = OnPrimary, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        }
        }
        val checkInBlock: @Composable () -> Unit = {
        SleepGlassCard {
            // After a save the card settles to one line holding the night's
            // fact, with the way back in beside it — the form used to spring
            // back blank as if nothing had happened.
            val settledLine = savedSummaryLine
            if (settledLine != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Cyan))
                    Text(
                        stringResource(R.string.sleep_checkin_settled, settledLine),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { savedSummaryLine = null }) {
                        Text(stringResource(R.string.sleep_checkin_edit), color = Cyan)
                    }
                }
            } else {
            // Evening framing: at 11pm "Morning check-in — how rested do you
            // feel?" is a morning question asked at night. Same card, honest
            // words for the hour (checkInLeadsAt is the tested boundary).
            val morning = checkInLeadsAt(LocalTime.now().hour)
            Text(
                stringResource(if (morning) R.string.sleep_checkin_title else R.string.sleep_checkin_title_evening),
                style = MaterialTheme.typography.titleMedium, color = TextSoft,
            )
            Text(stringResource(R.string.sleep_checkin_subtitle), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            // Health Connect prefill sits ABOVE the times it prefills — it used
            // to interrupt between the inputs and Save.
            if (hcAvailable) {
                HealthConnectCard(onClick = {
                    scope.launch {
                        if (com.cerebrozen.app.health.HealthConnectSleep.hasPermission(context)) applyHealthConnect()
                        else hcLauncher.launch(com.cerebrozen.app.health.HealthConnectSleep.permissions)
                    }
                })
            }
            // Edge-bled chip rail: the last chip is cut by the SCREEN, not the
            // card padding — the only reliable "there's more this way".
            Row(
                Modifier.bleed(20.dp).horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // W10: the quality chips rise in with the shared staggered entrance
                // (instant under Reduce Motion — handled inside appear).
                sleepWords().forEachIndexed { i, word ->
                    Box(Modifier.appear(i, rise = 10f)) {
                        SleepMoodChip(index = i, label = word, selected = quality == i + 1) { quality = i + 1 }
                    }
                }
            }
            TimeRow(stringResource(R.string.sleep_inbed_label), bed, { bed = it })
            TimeRow(stringResource(R.string.sleep_wake_label), wake, { wake = it })
            // The derived fact the steppers are FOR: how long that night was.
            Text(
                stringResource(R.string.sleep_duration_preview, minutesLabel(nightLengthMinutes(bed, wake))),
                style = MaterialTheme.typography.labelSmall, color = PeriwinkleSoft,
            )
            // Editing an older night (via the diary): say so, and offer the
            // way back to logging tonight.
            editDate?.let { d ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.sleep_editing_night, humanDate(d)),
                        style = MaterialTheme.typography.labelSmall, color = Cyan,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { editDate = null }) {
                        Text(stringResource(R.string.common_cancel), color = TextMuted)
                    }
                }
            }
            val logged = stringResource(R.string.sleep_logged)
            val updated = stringResource(R.string.sleep_logged_updated)
            val logFailed = stringResource(R.string.sleep_log_failed)
            val words = sleepWords()
            SleepGradientButton(
                text = if (busy) stringResource(R.string.common_one_moment) else stringResource(R.string.sleep_save_cta),
                enabled = quality > 0 && !busy,
            ) {
                busy = true; status = null
                val targetDate = editDate ?: LocalDate.now().toString()
                val hadEntry = nights.any { it.date == targetDate }
                val word = words.getOrNull(quality - 1).orEmpty()
                scope.launch {
                    try {
                        Api.logSleep(targetDate, hhmm(bed), hhmm(wake), quality)
                        // A rough night is data, not an achievement — celebrate
                        // only when the user says the night was at least okay.
                        if (quality >= 3) Celebrations.trigger()
                        // Say which of the two happened: the backend upserts by
                        // date, so re-saving a date EDITS that entry.
                        status = if (hadEntry) updated else logged
                        savedSummaryLine = "$word · ${hhmm(bed)}–${hhmm(wake)}"
                        quality = 0
                        editDate = null
                        reload()
                    } catch (e: Exception) {
                        status = e.userMessage(logFailed)
                    } finally {
                        busy = false
                    }
                }
            }
            // Say WHY the button sleeps instead of leaving it mutely grey.
            if (quality == 0 && !busy) {
                Text(
                    stringResource(R.string.sleep_save_hint),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted,
                )
            }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
        }
        }
        // Time-aware order: through the afternoon the check-in about last night
        // leads; evenings and the small hours the wind-down hero does. Decided
        // once when the tab composes ([checkInLeadsAt] is the tested boundary).
        val checkInFirst = remember { checkInLeadsAt(java.time.LocalTime.now().hour) }
        if (checkInFirst) {
            checkInBlock()
            heroBlock()
        } else {
            heroBlock()
            checkInBlock()
        }
        // The transport lives where play starts — right under the lead cards,
        // not buried beneath the nav doors. Renders nothing while idle.
        NowPlayingBar(onOpenPlayer = { onOpen("player") })

        // Loading: the shape of the card that is coming, not a spinner and not
        // an empty state that would be a claim about the user's history.
        if (loading && summary == null) {
            SectionCard {
                ShimmerBox(Modifier.fillMaxWidth().height(18.dp))
                ShimmerBox(Modifier.fillMaxWidth().height(52.dp))
            }
        }

        // Failed: say so, and offer the retry. Silence here reads as "no nights".
        if (!loading && loadFailed && summary == null) {
            SectionCard {
                Text(stringResource(R.string.sleep_week_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(
                    stringResource(R.string.sleep_load_failed),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
                TextButton(onClick = { scope.launch { loading = true; reload() } }) {
                    Text(stringResource(R.string.common_retry), color = Cyan)
                }
            }
        }

        // ── The merged "Your sleep" card ────────────────────────────────
        // Averages, chart, rhythm and diary were FOUR stacked cards doing one
        // job; they now read as one story: headline, picture, principle, log.
        summary?.let { s ->
            // H22: the data card is a door to Trends — the month-scale view of
            // exactly what this card summarizes was one unlinked tap away.
            SleepGlassCard(onClick = { onOpen("trends") }) {
                Text(stringResource(R.string.sleep_data_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                // A quiet status line for the diary's own milestones — the
                // first night, the first week forming. Never confetti.
                sleepMilestoneRes(nights.size)?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.labelSmall, color = Cyan)
                }
                if (s.optBoolean("enough_data")) {
                    Text(
                        stringResource(
                            R.string.sleep_week_summary,
                            minutesLabel(s.optInt("avg_duration_min")),
                            "%.1f".format(s.optDouble("avg_quality")),
                            s.optString("trend"),
                        ),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                } else {
                    // W24: a small one-shot art illustration above the copy —
                    // and a way to act on it, not just read it.
                    EmptyStateArt(kind = "sleep", size = 48.dp)
                    Text(
                        stringResource(R.string.sleep_week_empty),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                    TextButton(onClick = {
                        savedSummaryLine = null
                        scope.launch { scrollState.animateScrollTo(0) }
                    }) { Text(stringResource(R.string.sleep_log_last_night), color = Cyan) }
                }

                if (nights.size >= 2) NightsChart(nights)

                // Consistency insight (CBT-I Phase 1) — with the ACTUAL window
                // ("bedtimes landed 23:00–00:10"), which is actionable where
                // the bare spread number wasn't.
                val recent7 = nights.take(7)
                val avgSleep = averageSleepMinutes(recent7)
                val spread = bedtimeSpreadMinutes(recent7)
                if (recent7.size >= 3 && avgSleep != null && spread != null) {
                    Text(
                        stringResource(R.string.sleep_rhythm_line, spreadLabelText(avgSleep), spreadLabelText(spread)),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                    bedtimeWindow(recent7)?.let { (from, to) ->
                        Text(
                            stringResource(R.string.sleep_bed_window, hhmm(from), hhmm(to)),
                            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                        )
                    }
                    Text(
                        stringResource(if (isVariedRhythm(spread)) R.string.sleep_rhythm_vary else R.string.sleep_rhythm_steady),
                        style = MaterialTheme.typography.bodyMedium, color = PeriwinkleSoft,
                    )
                }

                // The diary, humanized and finally editable: tapping a night
                // prefills the form and Save UPSERTS that date (the backend
                // edits in place). Deleting a night still needs a backend
                // route — ledgered in docs/TODO.md.
                if (nights.isNotEmpty()) {
                    Text(stringResource(R.string.sleep_diary_title), style = MaterialTheme.typography.titleSmall, color = TextSoft)
                    Text(stringResource(R.string.sleep_diary_edit_hint), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    val words = sleepWords()
                    nights.take(7).forEach { n ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                listOfNotNull(humanDate(n.date), minutesLabel(n.duration), words.getOrNull(n.quality - 1))
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                            )
                            Row {
                                TextButton(onClick = {
                                    n.bedMin?.let { bed = it }
                                    n.wakeMin?.let { wake = it }
                                    quality = n.quality.coerceIn(0, 5)
                                    editDate = n.date
                                    savedSummaryLine = null
                                    status = null
                                    scope.launch { scrollState.animateScrollTo(0) }
                                }) { Text(stringResource(R.string.common_edit), color = Cyan) }
                                TextButton(onClick = { pendingDelete = n }) {
                                    Text(stringResource(R.string.common_delete), color = Warm)
                                }
                            }
                        }
                    }
                }
            }
        }

        // The two doors: mornings keep them here, up with the planning mind;
        // at night they step below the sounds — the thing you actually came
        // for at 11pm outranks navigation.
        val doorsBlock: @Composable () -> Unit = {
            SleepNavCard(
                icon = Icons.Outlined.LibraryMusic,
                title = stringResource(R.string.sleep_mixer_nav_title),
                subtitle = stringResource(R.string.sleep_mixer_nav_subtitle),
            ) { onOpen("sounds/mixer") }
            // State-aware Programs door: an enrolled user is told where they
            // ARE, not to start.
            val prog = program
            SleepNavCard(
                icon = Icons.Outlined.AutoStories,
                title = stringResource(R.string.sleep_programs_nav_title),
                subtitle = if (prog != null) {
                    stringResource(
                        R.string.sleep_programs_enrolled,
                        prog.optInt("day"), prog.optInt("days"), prog.optString("title"),
                    )
                } else stringResource(R.string.sleep_programs_nav_subtitle),
            ) { onOpen("programs") }
        }
        if (checkInFirst) doorsBlock()

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SleepSectionHeader("♫", stringResource(R.string.sleep_sounds_header))
            // The Sounds hub exists; this section used to dead-end at whatever
            // the API returned.
            TextButton(onClick = { onOpen("sounds") }) {
                Text(stringResource(R.string.sleep_sounds_all), color = Cyan)
            }
        }
        // While something plays at night, the auto-stop timer is the killer
        // feature — surfaced as a real row, not small text in the transport.
        if (Player.nowPlaying != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(CardFill).border(1.dp, LineStroke, RoundedCornerShape(18.dp))
                    .clickable { Player.cycleTimer(context) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.sleep_timer_row), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(
                    if (Player.timerMinutes > 0) stringResource(R.string.sleep_timer_min, Player.timerMinutes)
                    else stringResource(R.string.sleep_timer_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (Player.timerMinutes > 0) Cyan else TextMuted,
                )
            }
        }
        // metaLabel lambdas are not composable — capture the templates here.
        val minutesTemplate = stringResource(R.string.common_minutes)
        val storyMeta = stringResource(R.string.sleep_meta_story)
        val guideMeta = stringResource(R.string.sleep_meta_guide)
        ContentList(
            "sleep",
            { d -> if (d > 0) minutesTemplate.format(d) else storyMeta },
            // Same catalogue item, same behaviour as Home's rail: play it and
            // open the player. A second tap on the playing row pauses in place.
            onItemTap = { title ->
                if (Player.nowPlaying == title && Player.isPlaying) {
                    Player.toggle(context, title, "sleep")
                } else {
                    Player.play(context, title, "sleep")
                    onOpen("player")
                }
            },
            emptyText = stringResource(R.string.sleep_sounds_empty),
            emptyIcon = Icons.Outlined.LibraryMusic,
        )

        if (!checkInFirst) doorsBlock()

        // CBT-I-informed wind-down guide (served `wind_down` content, read-only).
        SleepSectionHeader("☾", stringResource(R.string.sleep_winddown_header))

        // Action leads the section: the guided ritual first, the reference
        // cards after — it used to trail four passive cards.
        SleepNavCard(
            icon = Icons.Outlined.Bedtime,
            title = stringResource(R.string.sleep_ritual_nav_title),
            subtitle = stringResource(R.string.sleep_ritual_nav_subtitle),
        ) { onOpen("winddown") }
        // The served guides, falling back to the bundled stimulus-control pair
        // (CBT-I Phase 1) when the catalogue is unreachable or empty — advice
        // worth having at 3am on a bad connection.
        //
        // These used to render UNCONDITIONALLY under the list, so online users
        // met "Bed is for sleep" twice within a screen: once as a served guide
        // ("Awake 20+ minutes? Get up, reset gently, return sleepy") and again
        // as a card ("If you're wide awake for 20+ minutes, get up, do something
        // quiet and dim, come back sleepy"), with the same CBT-I citation
        // printed under each. Same advice, twice, a few hundred pixels apart.
        //
        // No emptyText/emptyIcon here: with a fallback present the list resolves
        // to Fallback rather than Empty, so they would be dead arguments.
        ContentList(
            "wind_down",
            { d -> if (d > 0) minutesTemplate.format(d) else guideMeta },
            // Reference cards, honestly dressed: the muted meta stops "Guide"
            // reading as a link, and each guide wears its own identifying
            // glyph over the sibling ring art.
            metaColor = TextMuted,
            glyphFor = ::windDownGlyph,
            fallback = {
                SleepGlassCard {
                    Text(stringResource(R.string.sleep_bed_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(
                        stringResource(R.string.sleep_bed_body),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                }
                SleepGlassCard {
                    Text(stringResource(R.string.sleep_waketime_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(
                        stringResource(R.string.sleep_waketime_body),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                }
            },
        )

        // One citation for the section, not one under each card. The diary now
        // lives inside the merged "Your sleep" card above.
        WhyThisWorks(stringResource(R.string.sleep_cbti_why))
    }
    }

    // A quiet top scrim so scrolled content fades under the system clock
    // instead of colliding with it (Home got this in its own polish pass).
    val topInset = with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(this).toDp()
    }
    Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(topInset + 18.dp)
            .background(Brush.verticalGradient(listOf(Night.copy(alpha = 0.85f), Night.copy(alpha = 0f)))),
    )
    SleepFixedTopBar(
        modifier = Modifier.align(Alignment.TopCenter).zIndex(20f),
        onBack = onBack,
        onUrgent = { onOpen("crisis") },
    )
}

}

@Composable
private fun SleepBackgroundGlow() {
    Canvas(Modifier.fillMaxSize()) {
        val upper = Offset(size.width * 0.15f, size.height * 0.12f)
        drawCircle(
            Brush.radialGradient(listOf(Periwinkle.copy(alpha = 0.21f), Color.Transparent), upper, size.width * 0.78f),
            radius = size.width * 0.78f,
            center = upper,
        )
        val lower = Offset(size.width, size.height * 0.62f)
        drawCircle(
            Brush.radialGradient(listOf(Cyan.copy(alpha = 0.14f), Color.Transparent), lower, size.width * 0.8f),
            radius = size.width * 0.8f,
            center = lower,
        )
    }
}

@Composable
private fun SleepPremiumHeader(
    liveLine: String? = null,
    onMoonTap: (() -> Unit)? = null,
    /** Sleep stopped being a tab in the five-tab pass, so it needs the same
     * visible back door every other pushed screen has (SubPage's back well,
     * same 48dp target and same treatment). Null keeps the old tab-root
     * header for any caller that still hosts this as a root. */
    onBack: (() -> Unit)? = null,
) {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "sleepMoon")
    val floatY by transition.animateFloat(-4f, 5f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "moonFloat")
    val headerShape = RoundedCornerShape(23.dp)
    Row(
        Modifier.fillMaxWidth().shadow(7.dp, headerShape, ambientColor = Color.Black.copy(alpha = .05f))
            .clip(headerShape).background(CardFill.copy(alpha = .96f)).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(VeilWell)
                    .border(1.dp, LineStroke, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBackIos,
                    contentDescription = stringResource(R.string.common_back),
                    tint = TextBright,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                stringResource(R.string.sleep_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily(Font(R.font.newsreader)), fontWeight = FontWeight.Normal),
                color = TextPrimary,
            )
            // Once there is a week of data the subtitle carries the fact —
            // the cheapest place to prove the product works.
            Text(
                liveLine ?: stringResource(R.string.sleep_premium_subtitle),
                style = MaterialTheme.typography.bodySmall, color = TextMuted,
            )
            // The credential that both softens and strengthens the claim above.
            Text(
                stringResource(R.string.sleep_evidence_chip),
                style = MaterialTheme.typography.labelSmall, color = Color(0xFFB13D57),
            )
        }
        // The moon stopped being decoration: it opens the player mid-playback,
        // or a slow breathing session otherwise — a calm thing to reach for.
        val moonCd = stringResource(R.string.sleep_moon_cd)
        Box(
            Modifier
                .size(52.dp)
                .graphicsLayer { translationY = if (reduceMotion) 0f else floatY.dp.toPx() }
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(SleepHeroMoonGlow, Color.Transparent)))
                .let { m ->
                    if (onMoonTap != null) {
                        m.clickable(onClick = onMoonTap)
                            .semantics { contentDescription = moonCd }
                    } else m
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.DarkMode, contentDescription = null, tint = Color(0xFF6C2768), modifier = Modifier.size(27.dp))
        }
    }
}

@Composable
private fun SleepFixedTopBar(modifier: Modifier = Modifier, onBack: (() -> Unit)?, onUrgent: () -> Unit) {
    Row(
        modifier.fillMaxWidth().height(66.dp).background(CardFill.copy(alpha = .96f)).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(FieldFill).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBackIos, stringResource(R.string.common_back), tint = Color(0xFF6E376B), modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                stringResource(R.string.sleep_title), maxLines = 1,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily(Font(R.font.newsreader))),
                color = TextPrimary,
            )
            Text(
                stringResource(R.string.sleep_premium_subtitle), maxLines = 1,
                style = MaterialTheme.typography.bodySmall, color = TextMuted,
            )
        }
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(com.cerebrozen.app.ui.theme.Danger.copy(alpha = .09f)).clickable(onClick = onUrgent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.WarningAmber, "Urgent support", tint = com.cerebrozen.app.ui.theme.Danger, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun PremiumWindDownHero(
    kind: String,
    eyebrow: String,
    title: String,
    subtitle: String,
    height: Dp,
    alive: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "windDownHero")
    val moonY by transition.animateFloat(-3f, 5f, infiniteRepeatable(tween(2800), RepeatMode.Reverse), label = "heroMoon")
    val shimmer by transition.animateFloat(-1f, 2f, infiniteRepeatable(tween(4200)), label = "heroShimmer")
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier
            .fillMaxWidth()
            // Respect the caller's height — the old 250dp floor silently grew
            // every hero and ate half a 720px viewport.
            .height(height)
            .shadow(28.dp, shape, ambientColor = Periwinkle.copy(alpha = 0.27f), spotColor = Periwinkle.copy(alpha = 0.2f))
            .clip(shape)
            // Owner call 2026-08-05: this hero follows the theme. The paint
            // comes from the SleepHero* roles in Color.kt — Night keeps the
            // exact values it shipped with, Dawn gets a light dusk panel.
            .background(Brush.linearGradient(listOf(SleepHeroTop, SleepHeroMid, SleepHeroBottom)))
            .border(1.dp, SleepHeroEdge, shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(18) { i ->
                val x = ((i * 71) % 100) / 100f * size.width
                val y = ((i * 43) % 70) / 100f * size.height
                drawCircle(SleepHeroSpark.copy(alpha = 0.16f + (i % 3) * 0.08f), radius = (1 + i % 2).dp.toPx(), center = Offset(x, y))
            }
            val start = if (reduceMotion) size.width * 0.4f else shimmer * size.width
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, SleepHeroSpark.copy(alpha = 0.07f), Color.Transparent), startX = start, endX = start + size.width * 0.55f),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 22.dp, end = 24.dp)
                .size(104.dp)
                .graphicsLayer { translationY = if (reduceMotion || !alive) 0f else moonY.dp.toPx() }
                .background(Brush.radialGradient(listOf(SleepHeroMoonGlow, Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.DarkMode, contentDescription = null, tint = SleepHeroMoon, modifier = Modifier.size(58.dp))
        }
        Column(
            Modifier.fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = SleepHeroEyebrow)
                Text(title, style = MaterialTheme.typography.headlineSmall, color = SleepHeroInk)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SleepHeroInkSoft, modifier = Modifier.fillMaxWidth(0.68f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = SleepHeroTimer, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.sleep_estimated_duration), style = MaterialTheme.typography.labelMedium, color = SleepHeroMeta)
                }
            }
            content()
        }
    }
}

@Composable
private fun SleepGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(23.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = .06f))
            .clip(shape)
            .background(CardFill)
            .border(.5.dp, LineStroke.copy(alpha = .65f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun SleepMoodChip(index: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    val emojis = listOf("😴", "😟", "😐", "🙂", "😊")
    val shape = RoundedCornerShape(24.dp)
    val fill by animateColorAsState(if (selected) Periwinkle.copy(alpha = 0.22f) else CardFill, label = "moodFill")
    val stroke by animateColorAsState(if (selected) Periwinkle else LineStroke, label = "moodStroke")
    Column(
        Modifier
            .height(78.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, stroke, shape)
            .clickable { com.cerebrozen.app.ui.Haptics.tap(); onClick() }
            // One TalkBack stop per chip ("Rested, button"), not emoji + word.
            .semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.Button
            }
            .padding(horizontal = 15.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(emojis.getOrElse(index) { "🙂" }, style = MaterialTheme.typography.titleLarge)
        // One line, never wrapped: the chip widens to fit ("Rested" used to
        // wrap inside the fixed-height chip and clip to "Reste…").
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary,
            maxLines = 1, softWrap = false)
    }
}

@Composable
private fun HealthConnectCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardFill)
            .border(1.dp, LineStroke, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.HealthAndSafety, contentDescription = null, tint = Cyan, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.sleep_hc_prefill), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            // Unclamped: the consent boundary is an owner decision — cutting it
            // mid-sentence ("nothing is saved until…") undermined it.
            Text(stringResource(R.string.sleep_hc_boundary_hint), style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Cyan)
    }
}

@Composable
private fun SleepGradientButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(pressed, down = 0.97f)
            .shadow(if (enabled) 20.dp else 0.dp, shape, ambientColor = Periwinkle.copy(alpha = 0.27f), spotColor = Periwinkle.copy(alpha = 0.27f))
            .clip(shape)
            .background(if (enabled) Brush.linearGradient(listOf(Periwinkle, Accent2)) else Brush.linearGradient(listOf(ButtonDisabled, ButtonDisabled)))
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Exactly PrimaryButton's pairing: the accent ink on the live pill,
        // [Ink] on the disabled fill, in both themes.
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (enabled) OnPrimary else Ink)
    }
}

@Composable
private fun SleepNavCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(23.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(pressed, down = 0.98f)
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = .06f))
            .clip(shape)
            .background(CardFill)
            .border(.5.dp, LineStroke.copy(alpha = .65f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Periwinkle.copy(alpha = 0.33f), Cyan.copy(alpha = 0.2f)))),
            contentAlignment = Alignment.Center,
        ) {
            // A wash, not a fill: the glyph is body ink, not on-accent ink.
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Periwinkle)
    }
}

/** A live bar chart of the last few nights' durations, drawn INSIDE the
 * merged "Your sleep" card. Duration is height; QUALITY is the tint (bright
 * cyan-led for rested nights, dim for rough ones) — the chart used to spend
 * its ink on one number. Day letters sit under the bars (an axis at last),
 * and tapping a bar answers with the night's facts. W10: the bars grow from
 * the baseline once on first composition (40ms stagger, 400ms each); Reduce
 * Motion renders them at full height immediately — static, never blank.
 * Internal so the Robolectric reduce-motion suite can render it directly. */
@Composable
internal fun NightsChart(nights: List<SleepNight>) {
    val recent = nights.take(7).reversed()
    val maxDur = (recent.maxOfOrNull { it.duration } ?: 1).coerceAtLeast(1)
    val avg = recent.map { it.duration }.average().toInt()
    val reduceMotion = rememberReduceMotion()
    var selected by remember { mutableStateOf<SleepNight?>(null) }
    val words = sleepWords()
    val chartCd = stringResource(R.string.sleep_chart_cd, recent.size, minutesLabel(avg))
    Row(
        Modifier.fillMaxWidth()
            .semantics { contentDescription = chartCd },
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
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // Fixed 120dp track (the W10 reduce-motion pin measures it).
                Box(Modifier.height(120.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(frac * grow.value)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    n.quality >= 4 -> Brush.verticalGradient(listOf(Cyan, Periwinkle))
                                    n.quality == 3 -> Brush.verticalGradient(listOf(Periwinkle, Periwinkle.copy(alpha = 0.75f)))
                                    else -> Brush.verticalGradient(
                                        listOf(Periwinkle.copy(alpha = 0.45f), Periwinkle.copy(alpha = 0.28f)),
                                    )
                                },
                            )
                            .clickable { selected = if (selected == n) null else n }
                            .testTag("night-bar-$i"),
                    )
                }
                Text(dayLetterFor(n.date), style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
    selected?.let { n ->
        Text(
            listOfNotNull(
                humanDate(n.date), minutesLabel(n.duration),
                words.getOrNull(n.quality - 1),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = TextSoft,
        )
    }
    Text(stringResource(R.string.sleep_chart_footer, minutesLabel(avg), recent.size),
        style = MaterialTheme.typography.labelSmall, color = Periwinkle)
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

/** "In bed around   −30m  23:00  +30m" — label left, a fixed stepper right.
 *
 * The label takes the slack and the stepper is a fixed width, so the row cannot
 * overflow and the two rows line up with each other. It used to be a bare Row of
 * five children with no weights: at 720px the longer "Woke up around" pushed the
 * last button off the end and Compose broke "+30m" across two lines, one glyph
 * on the second. Seen on device.
 */
@Composable
private fun TimeRow(label: String, minutes: Int, onChange: (Int) -> Unit) {
    val earlierCd = stringResource(R.string.sleep_time_earlier_cd, label)
    val laterCd = stringResource(R.string.sleep_time_later_cd, label)
    val pickCd = stringResource(R.string.sleep_time_pick_cd, label)
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSoft,
            modifier = Modifier.weight(1f),
        )
        TimeStep(R.string.sleep_minus_30, earlierCd) { onChange(minutes - 30) }
        // ±30m covers the common case; tapping the time itself opens the system
        // picker for people whose night didn't land on a half hour. The pill
        // fill is what says "tappable" — colored bare text didn't.
        Text(
            hhmm(minutes),
            style = MaterialTheme.typography.bodyMedium,
            color = PeriwinkleSoft,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, LineStroke, RoundedCornerShape(10.dp))
                .clickable {
                    android.app.TimePickerDialog(
                        context,
                        { _, h, m -> onChange(h * 60 + m) },
                        minutes / 60, minutes % 60, true,
                    ).show()
                }
                .semantics { contentDescription = pickCd }
                .width(60.dp)
                .padding(vertical = 12.dp),
        )
        TimeStep(R.string.sleep_plus_30, laterCd) { onChange(minutes + 30) }
    }
}

/** One −30m/+30m step: a 48dp touch target that stays one line. Holding it
 * repeats (400ms arm, then ~7 steps/sec) — walking 23:00 to 03:30 by nine
 * deliberate taps was stepper punishment. */
@Composable
private fun TimeStep(
    @androidx.annotation.StringRes labelRes: Int,
    contentDesc: String,
    onClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val current = androidx.compose.runtime.rememberUpdatedState(onClick)
    Box(
        Modifier
            .size(52.dp, 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .pressRepeat(scope) { current.value() }
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = Periwinkle,
            maxLines = 1,
            softWrap = false,
        )
    }
}
