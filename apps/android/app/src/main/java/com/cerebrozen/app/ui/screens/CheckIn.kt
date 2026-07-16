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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.net.Api
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
        if (allowed == true) refresh()
    }

    if (allowed != true) return

    val done = checkedInToday(dates, today) || justSaved
    SectionCard {
        Text(
            progressLine(streak, done),
            style = MaterialTheme.typography.titleMedium, color = TextPrimary,
        )
        Spacer(Modifier.height(12.dp))
        if (!done) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MOOD_OPTIONS.forEach { option ->
                    MoodChip(option.label, busy = saving == option.label) {
                        saving = option.label
                        scope.launch {
                            runCatching { Api.checkIn(option.label, "", option.symbol, option.intensity) }
                                .onSuccess { justSaved = true; refresh() }
                            saving = null
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        WeekRing(weekPresence(dates, today))
    }
}

@Composable
private fun MoodChip(label: String, busy: Boolean, onClick: () -> Unit) {
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
                if (!busy) { Haptics.selection(); onClick() }
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

/** Seven dots. A filled one is a day they showed up; an empty one is just a day.
 *  No red, no cross, no "missed" — see the file header. */
@Composable
private fun WeekRing(week: List<Pair<String, Boolean>>) {
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
                            .border(1.dp, BrandPrimary.copy(alpha = 0.55f), CircleShape))
                    }
                    Box(Modifier.size(14.dp).clip(CircleShape)
                        .background(if (active) BrandPrimary else ChipFill)
                        .testTag("presence-dot-$i"))
                }
                Text(day, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}
