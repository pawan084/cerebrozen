package com.cerebrozen.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cerebrozen.app.BuildConfig
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.ArtScrim
import com.cerebrozen.app.ui.theme.ArtTextSoft
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cream
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.TextBright
import com.cerebrozen.app.ui.theme.VeilWell
import com.cerebrozen.app.ui.theme.EyebrowMuted
import com.cerebrozen.app.ui.theme.Danger
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleDeep
import com.cerebrozen.app.ui.theme.Radius
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Warm
import kotlinx.coroutines.delay
import kotlin.random.Random
import org.json.JSONArray

/** Page frame for a pushed sub-screen: back affordance + eyebrow + serif title. */
@Composable
internal fun SubPage(eyebrow: String, title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val rise = remember { Animatable(24f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) rise.snapTo(0f) else rise.animateTo(0f, tween(440, easing = FastOutSlowInEasing))
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .graphicsLayer { translationY = rise.value }
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(VeilWell)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.ArrowBackIosNew,
                    contentDescription = stringResource(R.string.common_back),
                    tint = TextBright,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.7.sp),
                    color = EyebrowMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 36.sp),
                    color = TextBright,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        content()
    }
}

/** Teammate-look gradient hero: a soft panel with a glassy pill eyebrow and
 * overlaid title/subtitle. Pass [artKind] to paint the W21 generative art for
 * that content kind instead of the plain vertical gradient (the enrolled
 * program hero does). Pure chrome — content is passed in by the caller, so it
 * never fabricates copy. Built on our palette tokens only. */
@Composable
internal fun GradientHero(
    eyebrow: String,
    title: String,
    subtitle: String = "",
    colors: List<Color> = listOf(Iris, PeriwinkleDeep),
    artKind: String? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.fillMaxWidth().clip(shape)) {
        if (artKind != null) {
            HeroArt(kind = artKind, title = title, modifier = Modifier.matchParentSize())
            // A soft floor scrim keeps the overlay text honest over any art.
            Box(Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, ArtScrim.copy(alpha = 0.45f)))))
        } else {
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(colors)))
        }
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.22f))
                    .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Cream)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Cream,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                // ArtTextSoft: the panel's gradient art stays dark in both themes,
                // so its overlay text must not follow the theme.
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ArtTextSoft)
            }
            content?.invoke(this)
        }
        // Hairline on top of the art (a border modifier would draw beneath it).
        Box(Modifier.matchParentSize().border(1.dp, Color.White.copy(alpha = 0.10f), shape))
    }
}

@Composable
fun InsightsScreen(onBack: () -> Unit, onOpen: (String) -> Unit = {}) {
    val defaultHeadline = stringResource(R.string.insights_default_headline)
    val loadFailed = stringResource(R.string.insights_error_fallback)
    var headline by remember { mutableStateOf(defaultHeadline) }
    var summary by remember { mutableStateOf("") }
    var metrics by remember { mutableStateOf<JSONArray?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { Api.insightsWeekly() }
            .onSuccess {
                headline = it.optString("headline", defaultHeadline)
                summary = it.optString("summary")
                metrics = it.optJSONArray("metrics")
            }
            .onFailure { error = it.message ?: loadFailed }
        loading = false
    }
    SubPage(stringResource(R.string.insights_eyebrow), headline, onBack) {
        if (loading) {
            InsightsLoadingState(stringResource(R.string.insights_loading))
            return@SubPage
        }
        error?.let {
            InsightsMessageCard(
                icon = Icons.Outlined.ErrorOutline,
                title = stringResource(R.string.insights_error_title),
                message = it,
                isError = true,
            )
            return@SubPage
        }
        // Real weekly read in a gradient hero — only when the backend returned one.
        if (summary.isNotBlank()) {
            GradientHero(eyebrow = stringResource(R.string.insights_hero_eyebrow), title = summary)
        }
        // The honest "before" — renders only when a real baseline was saved;
        // otherwise the invitation lives here (REDESIGN §2.2: baseline is the
        // Insights starting point, not a Home row).
        val baseline = BaselineStore.get()
        if (baseline != null) {
            val (stress, sleep, date) = baseline
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InsightsIconWell(Icons.Outlined.Flag, Periwinkle)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.insights_baseline_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSoft,
                        )
                        Text(
                            stringResource(
                                R.string.insights_baseline_summary,
                                stressWords()[stress - 1].lowercase(), stress,
                                sleepWords()[sleep - 1].lowercase(), sleep,
                            ) + if (date.isNotBlank()) stringResource(R.string.insights_baseline_recorded, date) else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                        )
                    }
                }
            }
        } else {
            NavRow(
                stringResource(R.string.insights_baseline_nav_title),
                stringResource(R.string.insights_baseline_nav_subtitle),
                icon = Icons.Outlined.Flag,
            ) { onOpen("baseline") }
        }
        SectionCard {
            val m = metrics
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.QueryStats,
                    contentDescription = null,
                    tint = Periwinkle,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(R.string.insights_metrics_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSoft,
                )
            }
            HorizontalDivider(color = LineStroke.copy(alpha = 0.72f))
            if (m == null || m.length() == 0) {
                InsightsMessageContent(
                    icon = Icons.Outlined.QueryStats,
                    title = stringResource(R.string.insights_metrics_empty_title),
                    message = stringResource(R.string.insights_metrics_empty),
                )
            } else {
                // W10: each metric fills 0→value once (60ms stagger, 400ms);
                // Reduce Motion renders the final fill immediately.
                val reduceMotion = rememberReduceMotion()
                (0 until m.length()).forEach { i ->
                    val row = m.getJSONObject(i)
                    val p = row.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f)
                    val fill = remember { Animatable(if (reduceMotion) 1f else 0f) }
                    LaunchedEffect(reduceMotion) {
                        if (reduceMotion) { fill.snapTo(1f); return@LaunchedEffect }
                        delay(i * 60L)
                        fill.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                    }
                    Column(
                        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                            progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f)
                        },
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                row.optString("label"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                row.optString("value"),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                textAlign = TextAlign.End,
                            )
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(Radius.round)).background(VeilWell)) {
                            Box(Modifier.fillMaxWidth(p * fill.value).height(8.dp).clip(RoundedCornerShape(Radius.round))
                                .background(Brush.horizontalGradient(listOf(Periwinkle, Cyan))))
                        }
                    }
                    if (i < m.length() - 1) {
                        HorizontalDivider(color = LineStroke.copy(alpha = 0.48f))
                    }
                }
            }
        }
        Text(stringResource(R.string.insights_privacy_footer),
            style = MaterialTheme.typography.bodySmall, color = TextMuted,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

/** Presentation-only loading frame that preserves the final layout and avoids a
 * disruptive text-to-content jump while the weekly payload is fetched. */
@Composable
private fun InsightsLoadingState(label: String) {
    Column(
        Modifier.fillMaxWidth().semantics {
            contentDescription = label
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ShimmerBox(Modifier.fillMaxWidth().height(152.dp), RoundedCornerShape(Radius.hero))
        ShimmerBox(Modifier.fillMaxWidth().height(88.dp), RoundedCornerShape(Radius.card))
        SectionCard {
            ShimmerBox(Modifier.fillMaxWidth(0.42f).height(18.dp), RoundedCornerShape(8.dp))
            repeat(3) {
                ShimmerBox(Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(12.dp))
            }
        }
    }
}

/** Shared visual language for an honest Insights error state. */
@Composable
private fun InsightsMessageCard(
    icon: ImageVector,
    title: String,
    message: String,
    isError: Boolean = false,
) {
    SectionCard {
        InsightsMessageContent(icon, title, message, isError)
    }
}

@Composable
private fun InsightsMessageContent(
    icon: ImageVector,
    title: String,
    message: String,
    isError: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            if (isError) liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InsightsIconWell(icon, if (isError) Danger else Cyan)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    }
}

@Composable
private fun InsightsIconWell(icon: ImageVector, tint: Color) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.size(48.dp).clip(shape)
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.28f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** W15 per-day programs: today's `{title, body}` guide from the
 * /programs/active payload — null when the program has no day guides (the
 * field is additive; older servers simply omit it) or both fields are blank. */
internal fun parseTodayGuide(program: org.json.JSONObject?): Pair<String, String>? {
    val g = program?.optJSONObject("today_guide") ?: return null
    val title = g.optString("title").trim()
    val body = g.optString("body").trim()
    if (title.isEmpty() && body.isEmpty()) return null
    return title to body
}

@Composable
fun ProgramsScreen(onBack: () -> Unit) {
    // Real enrollment (ref "PROGRAM · DAY X OF Y"): one journey at a time,
    // the day counts itself from the start date — nothing to fail.
    var rows by remember { mutableStateOf(listOf<Triple<String, String, String>>()) } // id, title, subtitle
    var active by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val loadFailed = stringResource(R.string.programs_error_fallback)

    suspend fun refresh() {
        error = null
        runCatching { active = Api.activeProgram() }
        runCatching {
            val arr = Api.content("program")
            rows = (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                Triple(c.optString("id"), c.optString("title"), c.optString("subtitle"))
            }
        }.onFailure { error = it.message ?: loadFailed }
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }

    SubPage(stringResource(R.string.programs_eyebrow), stringResource(R.string.programs_title), onBack) {
        Text(stringResource(R.string.programs_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        // Credibility line (REDESIGN §2.4) — honest provenance, no overclaim.
        Text(stringResource(R.string.programs_evidence),
            style = MaterialTheme.typography.labelSmall, color = TextMuted)

        if (loading) {
            Text(stringResource(R.string.programs_loading), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            return@SubPage
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            return@SubPage
        }

        active?.let { p ->
            val day = p.optInt("day")
            val days = p.optInt("days")
            GradientHero(
                eyebrow = stringResource(R.string.programs_day_eyebrow, day, days),
                title = p.optString("title"),
                subtitle = if (p.optBoolean("completed"))
                    stringResource(R.string.programs_completed_subtitle)
                else stringResource(R.string.programs_active_subtitle),
                artKind = "program",   // W21: journey art (day-dot motif)
            ) {
                if (days > 0) {
                    val prog = (day.toFloat() / days).coerceIn(0f, 1f)
                    // W10: the day-progress fills 0→value once on arrival;
                    // Reduce Motion renders the final fill immediately.
                    val reduceMotion = rememberReduceMotion()
                    val fill = remember { Animatable(if (reduceMotion) 1f else 0f) }
                    LaunchedEffect(reduceMotion) {
                        if (reduceMotion) fill.snapTo(1f)
                        else fill.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.22f))) {
                        Box(Modifier.fillMaxWidth(prog * fill.value).height(6.dp).clip(RoundedCornerShape(99.dp))
                            .background(Cream))
                    }
                }
                TextButton(onClick = {
                    scope.launch { runCatching { Api.leaveProgram() }; refresh() }
                }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(stringResource(R.string.programs_leave), color = Cream.copy(alpha = 0.85f))
                }
            }
            // W15: the current day's guide, when the program carries one —
            // the journey card stops being day-blind.
            parseTodayGuide(p)?.let { (guideTitle, guideBody) ->
                SectionCard {
                    Text(stringResource(R.string.programs_guide_header),
                        style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(guideTitle, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(guideBody, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                }
            }
        }

        if (rows.isNotEmpty()) {
            Text(stringResource(R.string.programs_start_new_header), style = MaterialTheme.typography.titleMedium, color = TextSoft)
        }
        val enrolledStatus = stringResource(R.string.programs_enrolled_status)
        val enrollFailed = stringResource(R.string.programs_enroll_error)
        rows.forEach { (id, title, subtitle) ->
            val isActive = active?.optString("title") == title
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))) {
                        // W21: per-title program art instead of a flat gradient chip.
                        ContentArt(title = title, kind = "program", modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                        Text(subtitle.ifBlank { stringResource(R.string.programs_default_subtitle) },
                            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        if (!isActive) {
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { Api.enrollProgram(id) }
                                        .onSuccess { status = enrolledStatus }
                                        .onFailure { status = it.message ?: enrollFailed }
                                    refresh()
                                }
                            }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text(stringResource(R.string.programs_start), color = Periwinkle)
                            }
                        }
                    }
                }
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
    }
}
