package com.cerebrozen.app.ui.screens

/* The daily check-in, and the calm progress beside it.
 *
 * Api.checkIn existed with exactly ONE caller: onboarding. The app asked how you were once,
 * at signup, and never again — while PRODUCT.md ships "check-ins, nudges, reminders" as a
 * v1 feature and Api.streak()/Api.moods() sat orphaned. This is the surface that was
 * missing, not new machinery.
 *
 * ## Why a week ring and a streak are not the thing we dropped
 *
 * PRODUCT.md drops gamification (coins, badges, leaderboards) and in the same breath asks
 * for "Progress, not gamification — streaks and journey progress are shown CALMLY". The
 * difference is what the number is FOR. A coin is a score you earn and can lose, aimed at
 * making you come back. A week ring is a mirror: it shows what happened, it does not
 * congratulate you, and a gap in it is information rather than failure. The platform's own
 * streak endpoint says the same thing — "A streak that breaks does not nag and is not
 * reported to anyone; it is a number the person can look at."
 *
 * So: no confetti, no "don't break your streak!", no red on an empty day. And nothing here
 * is ever reported to an employer — the ring is drawn from the person's own check-ins,
 * which live in the engine precisely so no HR query can reach them.
 *
 * The pure functions below hold the date arithmetic, so it is testable off-device.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.HomeCache
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.theme.BrandPrimary
import com.cerebrozen.app.ui.theme.ChipFill
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import org.json.JSONArray

/** How the person can answer. Deliberately small and plainly worded: a long list makes a
 *  daily question feel like a form, and the point is that it takes two seconds. */
internal data class MoodOption(val label: String, val symbol: String, val intensity: Int)

internal val MOOD_OPTIONS = listOf(
    MoodOption("Good", "sun", 1),
    MoodOption("Okay", "cloud", 2),
    MoodOption("Tense", "wind", 3),
    MoodOption("Rough", "rain", 4),
)

/** The dates the person checked in on, from the engine's mood list. Unparseable rows are
 *  skipped rather than fatal — one bad row must not blank the ring.
 *
 *  OffsetDateTime, not Instant: the engine writes `datetime.now(timezone.utc).isoformat()`,
 *  which emits `+00:00` — and `Instant.parse` wants a `Z`. Parsing with Instant would have
 *  thrown on every row and drawn an empty ring for someone who checks in daily, with no
 *  error anywhere. Local dates, not UTC: a 11pm check-in belongs to the day the person had,
 *  not the day UTC was having. */
internal fun checkInDates(moods: JSONArray, zone: ZoneId = ZoneId.systemDefault()): Set<LocalDate> {
    val out = mutableSetOf<LocalDate>()
    for (i in 0 until moods.length()) {
        val ts = moods.optJSONObject(i)?.optString("ts").orEmpty()
        if (ts.isBlank()) continue
        runCatching { OffsetDateTime.parse(ts).atZoneSameInstant(zone).toLocalDate() }
            .onSuccess { out.add(it) }
    }
    return out
}

/**
 * The last seven days, oldest first, each flagged with whether they checked in — TODAY is
 * always the last entry.
 *
 * Anchoring on today rather than on a calendar week is the calm choice: a Monday-start week
 * makes Sunday evening look like a wasted week, and this is a mirror, not a scorecard.
 */
internal fun weekPresence(
    dates: Set<LocalDate>,
    today: LocalDate,
    locale: Locale = Locale.getDefault(),
): List<Pair<String, Boolean>> {
    val fmt = DateTimeFormatter.ofPattern("EEEEE", locale) // single letter: M T W T F S S
    return (6 downTo 0).map { back ->
        val day = today.minusDays(back.toLong())
        fmt.format(day) to (day in dates)
    }
}

/** Have they already answered today? Asking twice in a day is nagging. */
internal fun checkedInToday(dates: Set<LocalDate>, today: LocalDate): Boolean = today in dates

/**
 * The last 30 days, oldest first — a density view beyond what the 7-dot week ring can show
 * (HOME_SPEC #17). Still a mirror, not a scorecard: no labels, no counts, just whether each
 * day happened. Shown only once a streak has actually grown past a week (see [CheckInCard]) —
 * a brand-new account has nothing here worth looking at yet.
 */
internal fun monthPresence(dates: Set<LocalDate>, today: LocalDate): List<Boolean> =
    (29 downTo 0).map { back -> today.minusDays(back.toLong()) in dates }

/**
 * A quiet warmth shift at real milestones (7/30/100 days) — never a badge, never a
 * congratulation, just the SAME dot reading a little richer once the streak has actually
 * earned it (HOME_SPEC #18). Below 7 this is plain [BrandPrimary]; it never appears anywhere
 * gamified — no counter resets it, no copy calls attention to the shift itself.
 */
internal fun milestoneTint(streakDays: Int, base: Color, gold: Color): Color = when {
    streakDays >= 100 -> androidx.compose.ui.graphics.lerp(base, gold, 0.85f)
    streakDays >= 30 -> androidx.compose.ui.graphics.lerp(base, gold, 0.55f)
    streakDays >= 7 -> androidx.compose.ui.graphics.lerp(base, gold, 0.28f)
    else -> base
}

/**
 * What to say next to the ring. Never a scold, never a streak-loss warning.
 *
 * A streak is only mentioned once it is worth mentioning (2+), because "1 day streak" is
 * noise dressed as an achievement — and it is never mentioned as something at risk.
 */
internal fun progressLine(streakDays: Int, checkedIn: Boolean): String = when {
    streakDays >= 2 && checkedIn -> "$streakDays days in a row."
    streakDays >= 2 -> "$streakDays days in a row so far."
    checkedIn -> "Noted — thanks for checking in."
    else -> "How's today going?"
}


// ── the surface ──────────────────────────────────────────────────────────────

/**
 * The check-in card: a question, four chips, and a week of dots.
 *
 * Consent-gated, and gated the way onboarding already words it — "asking at all when the
 * answer was no is the app not listening". If they declined mood history the whole card is
 * absent, not disabled: a greyed-out control is still a nag about a choice they made.
 */
@Composable
internal fun CheckInCard() {
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    var dates by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var streak by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf<String?>(null) }
    var justSaved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    suspend fun refresh() {
        runCatching { Api.moods() }.onSuccess { dates = checkInDates(it) }
        runCatching { Api.streak() }.onSuccess { streak = it.optInt("current") }
    }

    LaunchedEffect(Unit) {
        // Absence is not refusal: a token minted before the claim existed, or a profile
        // fetch that failed, must not silently hide the feature. Only an explicit false does.
        allowed = runCatching { Api.consent().optBoolean("mood_history", true) }.getOrDefault(true)
        // The keyed effect below already seeds `dates`/`streak` from HomeCache the moment it
        // has a value; only fetch directly here when the cache came up genuinely empty (a
        // cold path — warmBoot() timed out, or this card mounted before it finished).
        if (allowed == true && HomeCache.moods == null && HomeCache.streak == null) refresh()
    }
    // HomeCache is warmed once during the splash (Session.warmBoot) and re-warmed by Today's
    // pull-to-refresh — this keeps the card in sync with BOTH without a manual callback: any
    // update to the shared cache re-fires here and recomposes the card.
    LaunchedEffect(HomeCache.moods, HomeCache.streak) {
        HomeCache.moods?.let { dates = checkInDates(it) }
        HomeCache.streak?.let { streak = it }
    }

    if (allowed == null) { CheckInSkeleton(); return }
    if (allowed != true) return

    val done = checkedInToday(dates, today) || justSaved
    // A quiet warmth shift at real milestones — the SAME dot, a little richer once the
    // streak has actually earned it (HOME_SPEC #18). Never a badge, never named as such.
    val tint = milestoneTint(streak, BrandPrimary, MilestoneGold)
    SectionCard {
        Text(
            progressLine(streak, done),
            style = MaterialTheme.typography.titleMedium, color = TextPrimary,
        )
        Spacer(Modifier.height(12.dp))
        if (!done) {
            val moodScroll = rememberScrollState()
            Box {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(moodScroll),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MOOD_OPTIONS.forEach { option ->
                        MoodChip(option.label, option.intensity, busy = saving == option.label) {
                            saving = option.label
                            scope.launch {
                                runCatching { Api.checkIn(option.label, "", option.symbol, option.intensity) }
                                    .onSuccess { justSaved = true; refresh(); HomeCache.markCheckedInToday() }
                                saving = null
                            }
                        }
                    }
                }
                // A hint that there's a chip off-screen (HOME_SPEC #20) — "Rough" used to sit
                // past the fold with nothing telling you it was there.
                if (moodScroll.canScrollForward) {
                    Box(
                        Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(28.dp)
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, ScrollFadeScrim))),
                    )
                }
                if (moodScroll.canScrollBackward) {
                    Box(
                        Modifier.align(Alignment.CenterStart).fillMaxHeight().width(28.dp)
                            .background(Brush.horizontalGradient(listOf(ScrollFadeScrim, Color.Transparent))),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        WeekRing(weekPresence(dates, today), tint)
        // Beyond a week, the 7-dot ring can't show the shape of a longer streak — a quiet
        // density strip, no labels, no counts (HOME_SPEC #17). Held back until it's actually
        // meaningful; a brand-new account has nothing here worth looking at yet.
        if (streak >= 8) {
            Spacer(Modifier.height(10.dp))
            MonthDensity(monthPresence(dates, today), tint)
        }
    }
}

private val MilestoneGold = Color(0xFFE0B341)
private val ScrollFadeScrim = Color(0xFF0B0E24).copy(alpha = 0.55f)

/** Placeholder shape for [CheckInCard] while the consent check is in flight — the card used
 *  to render NOTHING until it resolved, so the page visibly grew once data landed. Mirrors
 *  the card's real layout (a title line, four chip-shaped fills, seven day-dots) so there is
 *  no jump when the shimmer is replaced by the real content. */
@Composable
private fun CheckInSkeleton() {
    SectionCard {
        ShimmerBox(Modifier.fillMaxWidth(0.55f).height(20.dp), RoundedCornerShape(6.dp))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { ShimmerBox(Modifier.size(width = 72.dp, height = 52.dp), RoundedCornerShape(999.dp)) }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(7) { ShimmerBox(Modifier.size(22.dp), CircleShape) }
        }
    }
}

/** [intensity] (1=Good..4=Rough) softens the tap for heavier moods — HOME_SPEC #19: every
 *  option used to fire the identical tick regardless of which feeling was tapped. */
@Composable
private fun MoodChip(label: String, intensity: Int, busy: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            // 52dp: comfortably past the 48dp accessibility floor.
            .heightIn(min = 52.dp)
            .pressScale(pressed, down = 0.97f)
            .clip(RoundedCornerShape(999.dp))
            .background(ChipFill)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                if (!busy) {
                    Haptics.soft(1f - (intensity - 1) * 0.18f)
                    onClick()
                }
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

/** Seven dots. A filled one is a day they showed up; an empty one is just a day.
 *  No red, no cross, no "missed" — see the file header. [tint] carries the quiet
 *  milestone warmth (HOME_SPEC #18); plain [BrandPrimary] below a 7-day streak. */
@Composable
private fun WeekRing(week: List<Pair<String, Boolean>>, tint: Color = BrandPrimary) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        week.forEachIndexed { i, (day, active) ->
            val isToday = i == week.lastIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    if (isToday) {
                        Box(Modifier.size(22.dp).clip(CircleShape)
                            .border(1.dp, tint.copy(alpha = 0.55f), CircleShape))
                    }
                    Box(Modifier.size(14.dp).clip(CircleShape)
                        .background(if (active) tint else ChipFill)
                        .testTag("presence-dot-$i"))
                }
                Text(day, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}

/** A 30-tick density strip beyond the week ring — no labels, no counts, just whether each
 *  day happened (HOME_SPEC #17). Compact enough to sit under the week ring without
 *  competing with it for attention. */
@Composable
private fun MonthDensity(month: List<Boolean>, tint: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        month.forEach { active ->
            Box(
                Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (active) tint.copy(alpha = 0.85f) else ChipFill),
            )
        }
    }
}
