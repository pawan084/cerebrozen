package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.Accent
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Violet
import org.json.JSONObject

/**
 * Trends — the mood and sleep series, side by side, over a window the user picks.
 *
 * The rule this screen is built under comes from the 2026-07-31 insights audit,
 * which found a mood reading computed from no check-ins. A chart is that hazard
 * with a nicer surface: **a day with no data drawn as zero reads as "I felt
 * terrible"**, not "I didn't open the app". So gaps are gaps here — the line
 * breaks — and every summary number waits for the server's `enough_data`.
 *
 * The server decides what is knowable (`app/services/trends.py`); this screen
 * only decides how to say it.
 */

// ── Parsing (pure, unit-tested) ──────────────────────────────────────────

internal data class TrendPoint(val date: String, val value: Float, val label: String = "")

internal data class TrendSeries(
    val points: List<TrendPoint>,
    val enoughData: Boolean,
    val summary: Float?,
    val logged: Int,
)

internal data class TrendLink(
    val available: Boolean,
    val coefficient: Float?,
    val direction: String?,
    val reason: String?,
    val pairs: Int,
)

internal data class Trends(
    val days: Int,
    val mood: TrendSeries,
    val sleep: TrendSeries,
    val link: TrendLink,
) {
    /** Nothing to draw at all — the difference between "no data" and "not enough
     * to summarise". The first gets the empty state, the second gets a chart
     * with an honest caption. */
    val isEmpty: Boolean get() = mood.points.isEmpty() && sleep.points.isEmpty()
}

internal fun parseTrends(payload: JSONObject): Trends {
    fun series(key: String, valueKey: String, summaryKey: String, countKey: String): TrendSeries {
        val obj = payload.optJSONObject(key) ?: JSONObject()
        val arr = obj.optJSONArray("points")
        val points = (0 until (arr?.length() ?: 0)).map { i ->
            val p = arr!!.getJSONObject(i)
            TrendPoint(
                date = p.optString("date"),
                value = p.optDouble(valueKey, 0.0).toFloat(),
                label = p.optString("mood"),
            )
        }
        // `has` rather than `opt…(0)`: the server sends null until it has
        // enough, and a 0 average would be a number it explicitly refused to give.
        val summary = if (obj.isNull(summaryKey)) null else obj.optDouble(summaryKey).toFloat()
        return TrendSeries(points, obj.optBoolean("enough_data"), summary, obj.optInt(countKey))
    }

    val corr = payload.optJSONObject("correlation") ?: JSONObject()
    return Trends(
        days = payload.optInt("days", 30),
        mood = series("mood", "ease", "average_ease", "days_logged"),
        sleep = series("sleep", "duration_min", "avg_duration_min", "nights"),
        link = TrendLink(
            available = corr.optBoolean("available"),
            coefficient = if (corr.isNull("coefficient")) null else corr.optDouble("coefficient").toFloat(),
            direction = if (corr.isNull("direction")) null else corr.optString("direction"),
            reason = if (corr.isNull("reason")) null else corr.optString("reason"),
            pairs = corr.optInt("pairs"),
        ),
    )
}

/** Minutes → "6h 40m". Units come from resources at the call site — the
 * hardcoded "h"/"m" literals were the one unlocalizable duration (B13). */
internal fun durationLabel(minutes: Int, hourUnit: String = "h", minuteUnit: String = "m"): String =
    if (minutes >= 60) "${minutes / 60}$hourUnit ${minutes % 60}$minuteUnit" else "${minutes}$minuteUnit"

/**
 * Which empty-state line an empty Trends screen shows.
 *
 * "Check in and this fills in" is a lie when the real reason is that mood or
 * sleep history is switched OFF in Privacy & memory — the server honours the
 * consent flag and will never read those check-ins, however many there are.
 * Say the true reason and point at the switch.
 */
internal fun trendsEmptyBodyRes(moodAllowed: Boolean, sleepAllowed: Boolean): Int =
    if (moodAllowed && sleepAllowed) R.string.trends_empty_body
    else R.string.trends_empty_consent_off

/**
 * Which points are contiguous in time, so the chart can break the line across a
 * gap instead of drawing through it.
 *
 * Dates come back as ISO days with missing days simply absent. Joining point 3
 * to point 4 across a week's silence would draw a confident slope through days
 * the user never logged — a line the data does not support.
 */
internal fun contiguousRuns(dates: List<String>): List<IntRange> {
    if (dates.isEmpty()) return emptyList()
    val runs = mutableListOf<IntRange>()
    var start = 0
    for (i in 1 until dates.size) {
        val previous = runCatching { java.time.LocalDate.parse(dates[i - 1]) }.getOrNull()
        val current = runCatching { java.time.LocalDate.parse(dates[i]) }.getOrNull()
        val adjacent = previous != null && current != null &&
            java.time.temporal.ChronoUnit.DAYS.between(previous, current) == 1L
        if (!adjacent) {
            runs += start..(i - 1)
            start = i
        }
    }
    runs += start..(dates.size - 1)
    return runs
}

// ── Screen ───────────────────────────────────────────────────────────────

@Composable
internal fun TrendsScreen(onBack: () -> Unit) {
    var days by remember { mutableIntStateOf(30) }
    var trends by remember { mutableStateOf<Trends?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // Which empty-state copy is honest (see trendsEmptyBodyRes). Read once; a
    // failed consent read falls back to the default copy rather than blocking.
    var emptyBodyRes by remember { mutableIntStateOf(R.string.trends_empty_body) }

    var reloadKey by remember { mutableIntStateOf(0) }
    // V3: the per-mood tally for the window (counts, not a scale — see the
    // comment on MoodBreakdownCard). A failed read simply hides the card.
    var moodTally by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    val loadFailed = stringResource(R.string.trends_load_failed)
    LaunchedEffect(days, reloadKey) {
        loading = true
        error = null
        runCatching { parseTrends(Api.trends(days)) }
            .onSuccess { trends = it }
            .onFailure { error = it.userMessage(loadFailed) }
        moodTally = runCatching { moodCounts(Api.moods(), days, java.time.LocalDate.now()) }
            .getOrDefault(emptyList())
        loading = false
    }
    LaunchedEffect(Unit) {
        runCatching { Api.consent() }.onSuccess {
            emptyBodyRes = trendsEmptyBodyRes(
                moodAllowed = it.optBoolean("mood_history"),
                sleepAllowed = it.optBoolean("sleep_history"),
            )
        }
    }

    SubPage(
        stringResource(R.string.trends_eyebrow),
        stringResource(R.string.trends_title),
        onBack,
    ) {
        // Window picker. Three windows, not a slider: the question is "this
        // month or this season", and a continuous control invites reading
        // precision into a self-reported diary that does not have it.
        // B36: FlowRow — three labelled chips do not fit one 720px line at
        // large font scale (the exact failure GoalsScreen documents).
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                7 to R.string.trends_window_week,
                30 to R.string.trends_window_month,
                90 to R.string.trends_window_season,
            ).forEach { (window, labelRes) ->
                PickChip(
                    selected = days == window,
                    label = stringResource(labelRes),
                ) { days = window }
            }
        }

        // B91: switching windows used to render the OLD window's chart
        // unmarked under the newly selected chip until the fetch landed.
        if (loading && trends != null) {
            // Not search_loading ("Searching the library…") — nothing is being
            // searched. This fires when the Week/Month/3-months chip changes,
            // so it says that.
            Text(stringResource(R.string.trends_window_loading),
                style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        when {
            loading && trends == null -> {
                repeat(2) {
                    SectionCard {
                        ShimmerBox(Modifier.fillMaxWidth().height(18.dp))
                        ShimmerBox(Modifier.fillMaxWidth().height(120.dp))
                    }
                }
            }

            error != null && trends == null -> {
                SectionCard {
                    Text(
                        stringResource(R.string.trends_error_title),
                        style = MaterialTheme.typography.titleMedium, color = TextSoft,
                    )
                    Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    // A dead end on failure — the one screen about your own
                    // data had no way to ask again (audit H23).
                    TextButton(onClick = { reloadKey++ }) {
                        Text(stringResource(R.string.common_try_again), color = Periwinkle)
                    }
                }
            }

            trends?.isEmpty == true -> {
                SectionCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EmptyStateArt(kind = "insight", size = 52.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.trends_empty_title),
                                style = MaterialTheme.typography.titleMedium, color = TextSoft,
                            )
                            Text(
                                stringResource(emptyBodyRes),
                                style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                            )
                        }
                    }
                }
            }

            trends != null -> {
                val data = trends!!
                TrendCard(
                    title = stringResource(R.string.trends_mood_title),
                    series = data.mood,
                    accent = Periwinkle,
                    // Ease runs 1–5 by construction (services/trends.ease_score),
                    // so the axis is fixed: an auto-scaled axis would make a
                    // steady week look dramatic.
                    range = 1f to 5f,
                    summary = data.mood.summary?.let {
                        stringResource(R.string.trends_mood_summary, String.format("%.1f", it), data.mood.logged)
                    },
                    notEnough = stringResource(R.string.trends_mood_not_enough, data.mood.logged),
                )

                TrendCard(
                    title = stringResource(R.string.trends_sleep_title),
                    series = data.sleep,
                    accent = Violet,
                    range = null,   // duration has no natural ceiling; scale to the data
                    summary = data.sleep.summary?.let {
                        stringResource(R.string.trends_sleep_summary, durationLabel(it.toInt(), stringResource(R.string.unit_hour_short), stringResource(R.string.unit_minute_short)), data.sleep.logged)
                    },
                    notEnough = stringResource(R.string.trends_sleep_not_enough, data.sleep.logged),
                )

                // V3: which feelings actually showed up, as COUNTS.
                //
                // This is the resolution of the "mood sparkline" question the
                // V2 redesign left open (docs/TODO.md). A sparkline needs each
                // mood to have a height, and "Anxious" is not objectively
                // lower than "Tired" — inventing that order would be exactly
                // the scoring this product refuses. Counting is honest: it
                // says what happened without ranking how you felt. The ease
                // line above stays the server's own 1–5 measure, which is
                // gap-broken and gated on `enough_data`.
                MoodBreakdownCard(counts = moodTally, days = days)

                LinkCard(data.link)

                WhyThisWorks(stringResource(R.string.trends_why))
            }
        }
    }
}

/**
 * Per-mood counts over the window, newest-heaviest first. Pure + unit-tested.
 *
 * Counting is the honest alternative to ranking: it needs no opinion about
 * whether "Anxious" sits above or below "Tired". Rows outside the window or
 * without a mood are skipped rather than bucketed into a default.
 */
internal fun moodCounts(
    moods: org.json.JSONArray,
    days: Int,
    today: java.time.LocalDate,
): List<Pair<String, Int>> {
    val cutoff = today.minusDays((days - 1).coerceAtLeast(0).toLong())
    val counts = LinkedHashMap<String, Int>()
    for (i in 0 until moods.length()) {
        val o = moods.optJSONObject(i) ?: continue
        val day = runCatching {
            java.time.OffsetDateTime.parse(o.optString("created_at"))
                .atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate()
        }.getOrNull() ?: continue
        if (day.isBefore(cutoff) || day.isAfter(today)) continue
        val mood = o.optString("mood").takeIf { it.isNotBlank() } ?: continue
        counts[mood] = (counts[mood] ?: 0) + 1
    }
    return counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
}

/**
 * Presence over the last 30 days, oldest first — one flag per day, true when a
 * check-in landed. Pure + unit-tested.
 *
 * Presence framing (design rule §2): this counts the days you showed up and
 * says nothing about the others. No streak, no percentage, no reset.
 */
internal fun presenceMonth(
    moods: org.json.JSONArray,
    today: java.time.LocalDate,
    days: Int = 30,
): List<Boolean> {
    val present = HashSet<java.time.LocalDate>()
    for (i in 0 until moods.length()) {
        val o = moods.optJSONObject(i) ?: continue
        runCatching {
            java.time.OffsetDateTime.parse(o.optString("created_at"))
                .atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate()
        }.getOrNull()?.let(present::add)
    }
    return ((days - 1) downTo 0).map { back -> today.minusDays(back.toLong()) in present }
}

/** The reference's moods-detail breakdown: one row per feeling, a bar whose
 * width is its share, and the count. No scale, no ordering claim. */
@Composable
private fun MoodBreakdownCard(counts: List<Pair<String, Int>>, days: Int) {
    if (counts.isEmpty()) return
    val total = counts.sumOf { it.second }
    val top = counts.first().second.coerceAtLeast(1)
    SectionCard {
        Text(stringResource(R.string.trends_moods_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
        counts.forEach { (wire, count) ->
            val label = moodLabelResFor(wire)?.let { stringResource(it) } ?: wire
            // Unknown wire values (a newer server taxonomy) render untinted
            // rather than crashing — the same rule moodTintFor already keeps.
            val hue = moodTintFor(wire)?.invoke() ?: Periwinkle
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(
                    moodIcon(wire), contentDescription = null,
                    tint = hue, modifier = Modifier.size(16.dp),
                )
                Text(
                    label, style = MaterialTheme.typography.bodyMedium, color = TextSoft,
                    maxLines = 1, modifier = Modifier.width(96.dp),
                )
                // The bar is a SHARE of the most-common feeling, so the row
                // reads as "this one, more often" — never as a score.
                androidx.compose.foundation.layout.Box(
                    Modifier.weight(1f).height(12.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .background(LineStroke.copy(alpha = .25f)),
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxWidth(count / top.toFloat())
                            .height(12.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                            .background(hue.copy(alpha = .75f)),
                    )
                }
                Text("$count", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            }
        }
        Text(
            stringResource(R.string.trends_moods_total, total, days),
            style = MaterialTheme.typography.bodySmall, color = TextMuted,
        )
    }
}

@Composable
private fun TrendCard(
    title: String,
    series: TrendSeries,
    accent: Color,
    range: Pair<Float, Float>?,
    summary: String?,
    notEnough: String,
) {
    SectionCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
        if (series.points.isEmpty()) {
            Text(notEnough, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            return@SectionCard
        }
        Sparkline(
            points = series.points,
            accent = accent,
            range = range,
            modifier = Modifier.fillMaxWidth().height(112.dp),
        )
        // The summary line only exists when the server said it could.
        Text(
            summary ?: notEnough,
            style = MaterialTheme.typography.bodyMedium,
            color = if (summary != null) TextPrimary else TextMuted,
        )
    }
}

/**
 * A line chart with holes in it.
 *
 * Deliberately unlabelled beyond the summary line under it: axis ticks on
 * self-reported diary data invite reading precision that is not there. The
 * shape is the message — "steadier than last month" — not the value at day 14.
 */
@Composable
private fun Sparkline(
    points: List<TrendPoint>,
    accent: Color,
    range: Pair<Float, Float>?,
    modifier: Modifier = Modifier,
) {
    val runs = remember(points) { contiguousRuns(points.map { it.date }) }
    val values = points.map { it.value }
    val low = range?.first ?: (values.minOrNull() ?: 0f)
    val high = range?.second ?: (values.maxOrNull() ?: 1f)
    val span = (high - low).takeIf { it > 0f } ?: 1f
    val axis = LineStroke
    val description = stringResource(
        R.string.trends_chart_a11y,
        points.size,
        points.firstOrNull()?.date.orEmpty(),
        points.lastOrNull()?.date.orEmpty(),
    )

    Canvas(modifier.semantics { contentDescription = description }) {
        val w = size.width
        val h = size.height
        // A faint baseline, so an all-flat series still reads as a chart.
        drawLine(axis, Offset(0f, h), Offset(w, h), strokeWidth = 1f)

        fun xFor(index: Int) =
            if (points.size == 1) w / 2f else w * index / (points.size - 1).toFloat()
        fun yFor(value: Float) = h - ((value - low) / span).coerceIn(0f, 1f) * (h * 0.86f) - h * 0.07f

        runs.forEach { run ->
            if (run.first == run.last) {
                // A lone day is a dot: drawing a line through one point would be
                // drawing a trend from a single check-in.
                drawCircle(accent, radius = 3.5f, center = Offset(xFor(run.first), yFor(values[run.first])))
                return@forEach
            }
            val path = Path()
            val fill = Path()
            run.forEachIndexed { offset, index ->
                val x = xFor(index)
                val y = yFor(values[index])
                if (offset == 0) { path.moveTo(x, y); fill.moveTo(x, h) ; fill.lineTo(x, y) }
                else { path.lineTo(x, y); fill.lineTo(x, y) }
            }
            fill.lineTo(xFor(run.last), h)
            fill.close()
            drawPath(
                fill,
                Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent)),
            )
            drawPath(path, accent, style = Stroke(width = 2.5f))
        }
        // The most recent day gets a dot — the one value people look for.
        drawCircle(accent, radius = 4f, center = Offset(xFor(points.lastIndex), yFor(values.last())))
    }
}

@Composable
private fun LinkCard(link: TrendLink) {
    SectionCard(quiet = true) {
        Text(
            stringResource(R.string.trends_link_title),
            style = MaterialTheme.typography.titleMedium, color = TextSoft,
        )
        val body = when {
            !link.available && link.reason == "no_variation" ->
                stringResource(R.string.trends_link_no_variation)
            !link.available -> stringResource(R.string.trends_link_needs_more, link.pairs)
            link.direction == "better_sleep_easier_days" -> stringResource(R.string.trends_link_positive, link.pairs)
            link.direction == "better_sleep_harder_days" -> stringResource(R.string.trends_link_negative, link.pairs)
            else -> stringResource(R.string.trends_link_none, link.pairs)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}
