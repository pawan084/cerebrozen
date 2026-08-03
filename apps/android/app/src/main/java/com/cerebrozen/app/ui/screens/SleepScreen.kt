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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LibraryMusic
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.cerebrozen.app.R
import com.cerebrozen.app.audio.Player
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.NightMid
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

/** A human-sized duration for prose ("45m", "1h 50m") — no zero-hour prefix.
 * Pure twin of [spreadLabelText], for tests and non-composable callers. */
internal fun spreadLabel(min: Int, hourUnit: String = "h", minuteUnit: String = "m"): String =
    if (min < 60) "$min$minuteUnit" else minutesToLabel(min, hourUnit, minuteUnit)

/** Sleep: morning check-in + honest weekly summary + diary, with a CBT-I-informed
 * layer on top — the job is improving sleep night by night, not measuring it. */
@Composable
fun SleepScreen(onOpen: (String) -> Unit = {}) {
    var quality by remember { mutableIntStateOf(0) }
    var bed by remember { mutableIntStateOf(23 * 60) }
    var wake by remember { mutableIntStateOf(7 * 60) }
    var summary by remember { mutableStateOf<JSONObject?>(null) }
    var nights by remember { mutableStateOf(listOf<SleepNight>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Three states this screen used to collapse into one. A first load in
    // flight, a load that failed, and an account with no nights yet all drew
    // the same "start logging" empty state — so a user whose request had just
    // failed was told, in a friendly voice, that they had never logged a night.
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
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
        // this module's audit was about.
        val gotSummary = runCatching { summary = Api.sleepSummary() }.isSuccess
        val gotNights = runCatching { nights = parseNights(Api.sleepLogs()) }.isSuccess
        loadFailed = !(gotSummary && gotNights)
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Night.copy(alpha = 0.90f), NightMid.copy(alpha = 0.72f), Night.copy(alpha = 0.82f))),
            ),
    ) {
        SleepBackgroundGlow()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
        SleepPremiumHeader()
        // The hero title doubles as the ambient bed's now-playing title, so both
        // read from the same resource and stay consistent.
        val calmerNight = stringResource(R.string.sleep_hero_title)
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
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(stringResource(R.string.sleep_tonight_badge), style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
                val playCd = stringResource(R.string.sleep_play_cd)
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
                            scaleX = if (reduceMotion) 1f else playScale
                            scaleY = if (reduceMotion) 1f else playScale
                        }
                        .shadow(16.dp, RoundedCornerShape(50), ambientColor = Color(0x557A5CFF), spotColor = Color(0x557A5CFF))
                        .clip(RoundedCornerShape(50))
                        .background(Brush.linearGradient(listOf(Color(0xFF7A5CFF), Color(0xFF9C72FF))))
                        .clickable { Player.play(context, calmerNight, "sleep") }
                        .semantics { contentDescription = playCd }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.common_play_label), color = Color.White, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        SleepGlassCard {
            Text(stringResource(R.string.sleep_checkin_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(stringResource(R.string.sleep_checkin_subtitle), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            if (hcAvailable) {
                HealthConnectCard(onClick = {
                    scope.launch {
                        if (com.cerebrozen.app.health.HealthConnectSleep.hasPermission(context)) applyHealthConnect()
                        else hcLauncher.launch(com.cerebrozen.app.health.HealthConnectSleep.permissions)
                    }
                })
                // The prefill label and the consent-boundary hint (owner decision
                // 2026-07-13) are rendered INSIDE HealthConnectCard — repeating
                // them here would print both twice.
            }
            val logged = stringResource(R.string.sleep_logged)
            val logFailed = stringResource(R.string.sleep_log_failed)
            SleepGradientButton(
                text = if (busy) stringResource(R.string.common_one_moment) else stringResource(R.string.sleep_save_cta),
                enabled = quality > 0 && !busy,
            ) {
                busy = true; status = null
                scope.launch {
                    try {
                        Api.logSleep(LocalDate.now().toString(), hhmm(bed), hhmm(wake), quality)
                        Celebrations.trigger()
                        status = logged
                        quality = 0
                        reload()
                    } catch (e: Exception) {
                        status = e.message ?: logFailed
                    } finally {
                        busy = false
                    }
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
        }

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

        summary?.let { s ->
            SleepGlassCard {
                Text(stringResource(R.string.sleep_week_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
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
                    // W24: a small one-shot art illustration above the copy.
                    EmptyStateArt(kind = "sleep", size = 48.dp)
                    Text(
                        stringResource(R.string.sleep_week_empty),
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
                SleepGlassCard {
                    Text(stringResource(R.string.sleep_rhythm_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(
                        stringResource(R.string.sleep_rhythm_line, spreadLabelText(avgSleep), spreadLabelText(spread)),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                    Text(
                        stringResource(if (isVariedRhythm(spread)) R.string.sleep_rhythm_vary else R.string.sleep_rhythm_steady),
                        style = MaterialTheme.typography.bodyMedium, color = PeriwinkleSoft,
                    )
                }
            }
        }

        if (nights.size >= 2) NightsChart(nights)

        // Mix-your-own layered soundscape — lives in the Sounds hub's Mixer
        // section now (REDESIGN §3.4); this door opens it directly.
        SleepNavCard(
            icon = Icons.Outlined.LibraryMusic,
            title = stringResource(R.string.sleep_mixer_nav_title),
            subtitle = stringResource(R.string.sleep_mixer_nav_subtitle),
        ) { onOpen("sounds/mixer") }

        // Programs door — after the redesign cut Home's program tiles, this is
        // the unenrolled entry point (the Home banner only shows once enrolled),
        // and the sleep tab is where the flagship Sleep Reset journey belongs.
        SleepNavCard(
            icon = Icons.Outlined.AutoStories,
            title = stringResource(R.string.sleep_programs_nav_title),
            subtitle = stringResource(R.string.sleep_programs_nav_subtitle),
        ) { onOpen("programs") }

        SleepSectionHeader("♫", stringResource(R.string.sleep_sounds_header))
        NowPlayingBar(onOpenPlayer = { onOpen("player") })
        // metaLabel lambdas are not composable — capture the templates here.
        val minutesTemplate = stringResource(R.string.common_minutes)
        val storyMeta = stringResource(R.string.sleep_meta_story)
        val guideMeta = stringResource(R.string.sleep_meta_guide)
        ContentList(
            "sleep",
            { d -> if (d > 0) minutesTemplate.format(d) else storyMeta },
            onItemTap = { title -> Player.toggle(context, title, "sleep") },
            emptyText = stringResource(R.string.sleep_sounds_empty),
            emptyIcon = Icons.Outlined.LibraryMusic,
        )

        // CBT-I-informed wind-down guide (served `wind_down` content, read-only).
        SleepSectionHeader("☾", stringResource(R.string.sleep_winddown_header))
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

        // The guided version of the advice above: stimulus control is something
        // to remember at 1am, the ritual is something to follow.
        SleepNavCard(
            icon = Icons.Outlined.Bedtime,
            title = stringResource(R.string.sleep_ritual_nav_title),
            subtitle = stringResource(R.string.sleep_ritual_nav_subtitle),
        ) { onOpen("winddown") }

        // One citation for the section, not one under each card.
        WhyThisWorks(stringResource(R.string.sleep_cbti_why))

        if (nights.isNotEmpty()) {
            SleepGlassCard {
                Text(stringResource(R.string.sleep_diary_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                nights.take(7).forEach { n ->
                    Text(
                        stringResource(R.string.sleep_diary_line, n.date, minutesLabel(n.duration), n.quality),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                }
            }
        }
    }
}

}

@Composable
private fun SleepBackgroundGlow() {
    Canvas(Modifier.fillMaxSize()) {
        val upper = Offset(size.width * 0.15f, size.height * 0.12f)
        drawCircle(
            Brush.radialGradient(listOf(Color(0x357A5CFF), Color.Transparent), upper, size.width * 0.78f),
            radius = size.width * 0.78f,
            center = upper,
        )
        val lower = Offset(size.width, size.height * 0.62f)
        drawCircle(
            Brush.radialGradient(listOf(Color(0x245CCBFF), Color.Transparent), lower, size.width * 0.8f),
            radius = size.width * 0.8f,
            center = lower,
        )
    }
}

@Composable
private fun SleepPremiumHeader() {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "sleepMoon")
    val floatY by transition.animateFloat(-4f, 5f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "moonFloat")
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(stringResource(R.string.sleep_title), style = MaterialTheme.typography.displayLarge, color = TextPrimary)
            Text(stringResource(R.string.sleep_premium_subtitle), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        Box(
            Modifier
                .size(72.dp)
                .graphicsLayer { translationY = if (reduceMotion) 0f else floatY.dp.toPx() }
                .background(Brush.radialGradient(listOf(Color(0x557A5CFF), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.DarkMode, contentDescription = null, tint = Color(0xFFD7CCFF), modifier = Modifier.size(34.dp))
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
            .height(height.coerceAtLeast(250.dp))
            .shadow(28.dp, shape, ambientColor = Color(0x447A5CFF), spotColor = Color(0x337A5CFF))
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF30275E), Color(0xFF1D315D), Color(0xFF17213E))))
            .border(1.dp, Color(0x557A5CFF), shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(18) { i ->
                val x = ((i * 71) % 100) / 100f * size.width
                val y = ((i * 43) % 70) / 100f * size.height
                drawCircle(Color.White.copy(alpha = 0.16f + (i % 3) * 0.08f), radius = (1 + i % 2).dp.toPx(), center = Offset(x, y))
            }
            val start = if (reduceMotion) size.width * 0.4f else shimmer * size.width
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent), startX = start, endX = start + size.width * 0.55f),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 22.dp, end = 24.dp)
                .size(104.dp)
                .graphicsLayer { translationY = if (reduceMotion || !alive) 0f else moonY.dp.toPx() }
                .background(Brush.radialGradient(listOf(Color(0x665CCBFF), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.DarkMode, contentDescription = null, tint = Color(0xFFE7DEFF), modifier = Modifier.size(58.dp))
        }
        Column(
            Modifier.fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(0xFF5CCBFF))
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC3CBE0), modifier = Modifier.fillMaxWidth(0.68f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = Color(0xFFB18CFF), modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.sleep_estimated_duration), style = MaterialTheme.typography.labelMedium, color = Color(0xFFD6D9E8))
                }
            }
            content()
        }
    }
}

@Composable
private fun SleepGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
            .clip(shape)
            .background(CardFill)
            .border(1.dp, LineStroke, shape)
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
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(emojis.getOrElse(index) { "🙂" }, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
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
        Icon(Icons.Outlined.HealthAndSafety, contentDescription = null, tint = Color(0xFF5CCBFF), modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.sleep_hc_prefill), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(stringResource(R.string.sleep_hc_boundary_hint), style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 3)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Color(0xFF5CCBFF))
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
            .shadow(if (enabled) 20.dp else 0.dp, shape, ambientColor = Color(0x447A5CFF), spotColor = Color(0x447A5CFF))
            .clip(shape)
            .background(if (enabled) Brush.linearGradient(listOf(Color(0xFF7A5CFF), Color(0xFF9A6DFF))) else Brush.linearGradient(listOf(Color(0x334E5877), Color(0x334E5877))))
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (enabled) Color.White else Color(0xFF7D879E))
    }
}

@Composable
private fun SleepNavCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(pressed, down = 0.98f)
            .clip(shape)
            .background(CardFill)
            .border(1.dp, LineStroke, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0x557A5CFF), Color(0x335CCBFF)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Color(0xFFB18CFF))
    }
}

/** A live bar chart of the last few nights' durations. W10: the bars grow from
 * the baseline once on first composition (40ms stagger, 400ms each); Reduce
 * Motion renders them at full height immediately — static, never blank.
 * Internal so the Robolectric reduce-motion suite can render it directly. */
@Composable
internal fun NightsChart(nights: List<SleepNight>) {
    val recent = nights.take(7).reversed()
    val maxDur = (recent.maxOfOrNull { it.duration } ?: 1).coerceAtLeast(1)
    val avg = recent.map { it.duration }.average().toInt()
    val reduceMotion = rememberReduceMotion()
    SleepGlassCard {
        Text(stringResource(R.string.sleep_chart_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
        val chartCd = stringResource(R.string.sleep_chart_cd, recent.size, minutesLabel(avg))
        Row(
            Modifier.fillMaxWidth().height(120.dp)
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
                Box(
                    Modifier.weight(1f).fillMaxHeight(frac * grow.value).clip(RoundedCornerShape(6.dp))
                        .background(Brush.verticalGradient(listOf(Periwinkle, Cyan)))
                        .testTag("night-bar-$i"),
                )
            }
        }
        Text(stringResource(R.string.sleep_chart_footer, minutesLabel(avg), recent.size),
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
        Text(
            hhmm(minutes),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(52.dp),
        )
        TimeStep(R.string.sleep_plus_30, laterCd) { onChange(minutes + 30) }
    }
}

/** One −30m/+30m step: a 48dp touch target that stays one line. */
@Composable
private fun TimeStep(
    @androidx.annotation.StringRes labelRes: Int,
    contentDesc: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(52.dp, 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
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
