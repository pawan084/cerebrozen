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
import com.cerebrozen.app.audio.Chime
import com.cerebrozen.app.audio.MediaUrls
import com.cerebrozen.app.audio.Player
import com.cerebrozen.app.audio.Sfx
import com.cerebrozen.app.audio.SoundscapeMixer
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
internal fun ContentRow(
    title: String,
    subtitle: String,
    meta: String,
    premium: Boolean,
    playing: Boolean = false,
    kind: String = "",
    imageUrl: String = "",
    onTap: (() -> Unit)? = null,
    fav: Boolean? = null,
    onFav: (() -> Unit)? = null,
) {
    SectionCard(onClick = onTap) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // W21: designed generative art always; a real content photo (when the
            // backend serves one AND Coil loads it) simply covers the art, so a
            // blank or failing image_url never leaves a flat slab.
            val thumbShape = RoundedCornerShape(14.dp)
            Box(Modifier.size(54.dp).clip(thumbShape)) {
                ContentArt(title = title, kind = kind, modifier = Modifier.fillMaxSize())
                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageUrl, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(Modifier.matchParentSize().border(1.dp, Color.White.copy(alpha = 0.12f), thumbShape))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextBright,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (meta.isNotBlank() && !subtitle.contains(meta, ignoreCase = true)) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = Periwinkle,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (premium) Text(stringResource(R.string.common_premium_badge), style = MaterialTheme.typography.labelSmall, color = Warm)
                if (onFav != null && fav != null) {
                    // 48dp touch target with a visually 22dp icon (a11y minimum).
                    Box(
                        Modifier.size(48.dp).clickable { onFav() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (fav) stringResource(R.string.common_unfavourite_cd, title)
                            else stringResource(R.string.common_favourite_cd, title),
                            tint = Warm,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                if (onTap != null) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) stringResource(R.string.common_pause_cd, title)
                        else stringResource(R.string.common_play_cd, title),
                        tint = Cyan, modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

/** Load a content kind and render it as a list; shows honest empty/error states. */
@Composable
internal fun ContentList(
    kind: String,
    metaLabel: (Int) -> String,
    onItemTap: ((String) -> Unit)? = null,
    favs: Set<String>? = null,
    onFav: ((String) -> Unit)? = null,
    emptyText: String? = null,
    emptyIcon: ImageVector? = null,
) {
    var items by remember { mutableStateOf<JSONArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val loadFailed = stringResource(R.string.content_error_fallback)
    LaunchedEffect(kind) {
        runCatching { Api.content(kind) }
            .onSuccess { items = it }
            .onFailure { error = it.message ?: loadFailed }
    }
    // Register narration URLs as a side effect of loading, not during render — the
    // registry is shared mutable state and must not be written on every recomposition.
    LaunchedEffect(items) {
        val arr = items ?: return@LaunchedEffect
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            MediaUrls.register(c.optString("title"), MediaUrls.resolve(c.optString("audio_url"), BuildConfig.API_BASE_URL))
        }
    }
    when {
        error != null -> Text(error!!, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        items == null -> repeat(3) { ShimmerBox(Modifier.fillMaxWidth().height(72.dp)) }
        items!!.length() == 0 -> Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            emptyIcon?.let {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Periwinkle.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(it, contentDescription = null, tint = Periwinkle, modifier = Modifier.size(22.dp))
                }
            }
            Text(emptyText ?: stringResource(R.string.content_empty), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        else -> (0 until items!!.length()).forEach { i ->
            val c = items!!.getJSONObject(i)
            val title = c.optString("title")
            ContentRow(
                title, c.optString("subtitle"),
                metaLabel(c.optInt("duration_min")), c.optBoolean("premium"),
                playing = Player.nowPlaying == title && Player.isPlaying,
                kind = kind,
                imageUrl = c.optString("image_url"),
                onTap = onItemTap?.let { { it(title) } },
                fav = favs?.contains(title),
                onFav = onFav?.let { { it(title) } },
            )
        }
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


/** The one audio hub (REDESIGN §3.4): a Library of served content + favourites
 * and the 4-layer Mixer behind a two-pill switch. [startInMixer] lets the
 * `sounds/mixer` route (Sleep's "mix your own" door) open on the Mixer. */
@Composable
fun SoundsScreen(onBack: () -> Unit, onOpen: (String) -> Unit = {}, startInMixer: Boolean = false) {
    val context = LocalContext.current
    // W27 §2: the list a title comes from declares its kind — the aurora's tint signal.
    val playSoundscape: (String) -> Unit = { title -> Player.toggle(context, title, "soundscape") }
    val playSleep: (String) -> Unit = { title -> Player.toggle(context, title, "sleep") }
    var favs by remember { mutableStateOf(SleepFavs.all()) }
    val toggleFav: (String) -> Unit = { favs = SleepFavs.toggle(it) }
    var section by rememberSaveable { mutableStateOf(if (startInMixer) "mixer" else "library") }
    SubPage(
        if (section == "mixer") stringResource(R.string.mixer_eyebrow) else stringResource(R.string.sounds_eyebrow),
        if (section == "mixer") stringResource(R.string.mixer_title) else stringResource(R.string.sounds_title),
        onBack,
    ) {
        PremiumSoundSegment(
            mixerSelected = section == "mixer",
            onLibrary = { section = "library" },
            onMixer = { section = "mixer" },
        )
        if (section == "mixer") {
            MixerSection()
            return@SubPage
        }
        NowPlayingBar(onOpenPlayer = { onOpen("player") })
        SleepTimerPill()
        Text(stringResource(R.string.sounds_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        if (favs.isNotEmpty()) {
            Text(stringResource(R.string.sounds_favourites_header), style = MaterialTheme.typography.titleMedium, color = TextSoft)
            favs.sorted().forEach { title ->
                ContentRow(
                    title, "", stringResource(R.string.sounds_meta_favourite), false,
                    playing = Player.nowPlaying == title && Player.isPlaying,
                    kind = "soundscape",
                    onTap = { playSoundscape(title) }, fav = true, onFav = { toggleFav(title) },
                )
            }
        }
        // metaLabel lambdas are not composable — capture the templates here.
        val minutesTemplate = stringResource(R.string.common_minutes)
        val ambientMeta = stringResource(R.string.sounds_meta_ambient)
        val storyMeta = stringResource(R.string.sleep_meta_story)
        ContentList("soundscape", { d -> if (d > 0) minutesTemplate.format(d) else ambientMeta },
            onItemTap = playSoundscape, favs = favs, onFav = toggleFav)
        Text(stringResource(R.string.sounds_sleep_stories_header), style = MaterialTheme.typography.titleMedium, color = TextSoft)
        ContentList("sleep", { d -> if (d > 0) minutesTemplate.format(d) else storyMeta },
            onItemTap = playSleep, favs = favs, onFav = toggleFav)
        Text(stringResource(R.string.sounds_narration_note),
            style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

/** W10: a quiet status pill when a sleep timer is armed — the mixer's live
 * countdown when it has one, else the player's coarse timer. Status only,
 * nothing tappable; renders nothing when no fade-out is armed. */
@Composable
private fun PremiumSoundSegment(
    mixerSelected: Boolean,
    onLibrary: () -> Unit,
    onMixer: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Color(0xA61A2340))
            .border(1.dp, Color.White.copy(alpha = 0.1f), shape)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        PremiumSegmentItem(stringResource(R.string.sounds_section_library), !mixerSelected, Modifier.weight(1f), onLibrary)
        PremiumSegmentItem(stringResource(R.string.sounds_section_mixer), mixerSelected, Modifier.weight(1f), onMixer)
    }
}

@Composable
private fun PremiumSegmentItem(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fill by animateColorAsState(if (selected) Color(0xFF6550D7) else Color.Transparent, label = "segmentFill")
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val itemShape = RoundedCornerShape(23.dp)
    Box(
        modifier.pressScale(pressed, down = 0.97f).height(48.dp).clip(itemShape)
            .background(fill)
            .then(if (selected) Modifier.border(1.dp, Color(0x667A5CFF), itemShape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else Color(0xFFAEB9D0))
    }
}

@Composable
private fun SleepTimerPill() {
    val label = SoundscapeMixer.remainingText()?.let { stringResource(R.string.sounds_fading_out_in, it) }
        ?: Player.timerMinutes.takeIf { it > 0 }?.let { stringResource(R.string.sounds_sleep_timer_short, it) }
        ?: return
    Row(
        Modifier
            .clip(RoundedCornerShape(Radius.round))
            .background(CardFill)
            .border(1.dp, LineStroke, RoundedCornerShape(Radius.round))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

private fun layerIcon(symbol: String): ImageVector = when (symbol) {
    "rain" -> Icons.Outlined.Grain
    "ocean" -> Icons.Outlined.Waves
    "wind" -> Icons.Outlined.Air
    else -> Icons.Outlined.GraphicEq
}

/** Mix-your-own ambient soundscape — blend rain, ocean, wind and a soft drone,
 * each with its own volume, into a personal calm. Parity with the iOS sleep
 * player's mixer; the four loops play gaplessly and keep going while you use it.
 * Lives inside the Sounds hub (REDESIGN §3.4 — one audio surface). */
@Composable
private fun MixerSection() {
    val context = LocalContext.current
    val playing = SoundscapeMixer.isPlaying
    val matching = SoundscapeMixer.matchingPreset()
    val mixName = matching?.let { presetLabel(SoundscapeMixer.presets[it].key) }
        ?: stringResource(R.string.mixer_custom_mix)
    val session = SoundscapeMixer.remainingText()
        ?: SoundscapeMixer.timerMinutes.takeIf { it > 0 }?.let { stringResource(R.string.common_minutes, it) }
        ?: stringResource(R.string.mixer_open_ended)

    Text(stringResource(R.string.mixer_subtitle), style = MaterialTheme.typography.bodyLarge, color = Color(0xFFB7C2DA))
    MixerHeroCard(
        playing = playing,
        mixName = mixName,
        session = session,
        onToggle = { SoundscapeMixer.toggle(context) },
    )
    MasterVolumeCard(
        value = SoundscapeMixer.master,
        onValueChange = { SoundscapeMixer.setMasterVolume(context, it) },
    )

    Text(stringResource(R.string.mixer_presets), style = MaterialTheme.typography.titleMedium, color = Color.White)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SoundscapeMixer.presets.forEachIndexed { index, preset ->
            PremiumPresetPill(
                selected = matching == index,
                label = presetLabel(preset.key),
            ) { SoundscapeMixer.applyPreset(context, index) }
        }
    }

    Text(stringResource(R.string.mixer_layers), style = MaterialTheme.typography.titleMedium, color = Color.White)
    SoundscapeMixer.layers.forEachIndexed { index, layer ->
        val volume = SoundscapeMixer.volumes[index]
        MixerLayerCard(
            icon = layerIcon(layer.symbol),
            title = layer.name,
            description = layerDescription(layer.symbol),
            volume = volume,
            playing = playing && volume > 0.02f,
            onToggle = { SoundscapeMixer.toggleLayer(context, index) },
            onVolume = { SoundscapeMixer.setLayerVolume(context, index, it) },
        )
    }

    PremiumSleepTimerCard(context)
    PremiumBellCard()
    PremiumActivitySoundsCard()
    SoundscapeMixer.remainingText()?.let {
        Text(stringResource(R.string.mixer_fades_note, it), style = MaterialTheme.typography.labelMedium, color = Color(0xFFAEB9D0))
    }
}

@Composable
private fun MixerHeroCard(
    playing: Boolean,
    mixName: String,
    session: String,
    onToggle: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "mixerHero")
    val glow by transition.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "heroGlow")
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier.fillMaxWidth().height(260.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF30265F), Color(0xFF18375B), Color(0xFF131D35))))
            .border(1.dp, Color(0x557A5CFF), shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(16) { i ->
                val x = ((i * 67) % 100) / 100f * size.width
                val y = ((i * 41) % 80) / 100f * size.height
                drawCircle(Color.White.copy(alpha = 0.12f + (i % 3) * 0.07f), (1 + i % 2).dp.toPx(), Offset(x, y))
            }
            drawCircle(
                Brush.radialGradient(listOf(Color(0x557A5CFF), Color.Transparent), Offset(size.width * 0.78f, size.height * 0.32f), size.width * 0.45f),
                size.width * 0.45f,
                Offset(size.width * 0.78f, size.height * 0.32f),
            )
        }
        Column(
            Modifier.fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(R.string.mixer_now_mixing), style = MaterialTheme.typography.labelSmall, color = Color(0xFF64C9FF))
                Text(mixName, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, contentDescription = null, tint = Color(0xFFB18CFF), modifier = Modifier.size(16.dp))
                    Text(session, style = MaterialTheme.typography.labelMedium, color = Color(0xFFC5CEE0))
                }
            }
            MixerWaveform(active = playing)
            val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Row(
                Modifier.pressScale(pressed, down = 0.96f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7A5CFF), Color(0xFF9A70FF))))
                    .border(1.dp, Color.White.copy(alpha = if (reduceMotion) 0.18f else glow * 0.28f), RoundedCornerShape(26.dp))
                    .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
                    .padding(horizontal = 20.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                Text(
                    if (playing) stringResource(R.string.common_pause_label) else stringResource(R.string.common_play_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun MixerWaveform(active: Boolean, bars: Int = 17) {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "mixerWave")
    Row(
        Modifier.fillMaxWidth().height(38.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(bars) { i ->
            val wave by transition.animateFloat(
                5f,
                13f + (i % 5) * 4f,
                infiniteRepeatable(tween(650 + i * 35, delayMillis = i * 35), RepeatMode.Reverse),
                label = "mixBar$i",
            )
            Box(
                Modifier.size(3.dp, (if (!active || reduceMotion) 6f else wave).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF64C9FF), Color(0xFFB18CFF)))),
            )
        }
    }
}

@Composable
private fun MasterVolumeCard(value: Float, onValueChange: (Float) -> Unit) {
    MixerGlassCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MixerIconWell(Icons.AutoMirrored.Outlined.VolumeUp, active = true)
                Column {
                    Text(stringResource(R.string.mixer_master_volume), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(stringResource(R.string.mixer_all_layers), style = MaterialTheme.typography.bodySmall, color = Color(0xFFAEB9D0))
                }
            }
            Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium, color = Color(0xFF64C9FF))
        }
        PremiumMixerSlider(value, onValueChange, stringResource(R.string.mixer_master_volume))
    }
}

@Composable
private fun PremiumPresetPill(selected: Boolean, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier.pressScale(pressed, down = 0.94f).clip(shape)
            .background(if (selected) Brush.linearGradient(listOf(Color(0xFF6B52E5), Color(0xFF9670F4))) else Brush.linearGradient(listOf(Color(0xA61A2340), Color(0xA61A2340))))
            .border(1.dp, if (selected) Color(0x887A5CFF) else Color.White.copy(alpha = 0.1f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun MixerLayerCard(
    icon: ImageVector,
    title: String,
    description: String,
    volume: Float,
    playing: Boolean,
    onToggle: () -> Unit,
    onVolume: (Float) -> Unit,
) {
    val active = volume > 0.02f
    val border by animateColorAsState(if (active) Color(0x667A5CFF) else Color.White.copy(alpha = 0.09f), label = "layerBorder")
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xC91A2340), Color(0xA8262B4A))))
            .border(1.dp, border, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
            MixerIconWell(icon, active)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAEB9D0))
            }
            PremiumMixerSwitch(active, title, onToggle)
        }
        if (playing) MixerWaveform(active = true, bars = 12)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (active) stringResource(R.string.mixer_playing) else stringResource(R.string.common_off), style = MaterialTheme.typography.labelSmall, color = if (active) Color(0xFF4ADE80) else Color(0xFF8993AA))
            Text("${(volume * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = Color(0xFFB18CFF))
        }
        PremiumMixerSlider(volume, onVolume, title)
    }
}

@Composable
private fun MixerGlassCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xC91A2340), Color(0xA8262B4A))))
            .border(1.dp, Color.White.copy(alpha = 0.1f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun MixerIconWell(icon: ImageVector, active: Boolean) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(19.dp))
            .background(if (active) Brush.linearGradient(listOf(Color(0x557A5CFF), Color(0x335CCBFF))) else Brush.linearGradient(listOf(Color(0x332D3651), Color(0x332D3651))))
            .border(1.dp, if (active) Color(0x557A5CFF) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(19.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Color(0xFFBFDFFF) else Color(0xFF818BA2), modifier = Modifier.size(25.dp))
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PremiumMixerSlider(value: Float, onValueChange: (Float) -> Unit, label: String) {
    val interactionSource = remember { MutableInteractionSource() }
    val dragging by interactionSource.collectIsDraggedAsState()
    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "mixerSliderThumbScale",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (dragging) 0.95f else 0.58f,
        animationSpec = tween(180),
        label = "mixerSliderGlow",
    )
    val gradientMotion = rememberInfiniteTransition(label = "mixerSliderGradient")
    val gradientPhase by gradientMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mixerSliderGradientPhase",
    )
    val percentage = (value.coerceIn(0f, 1f) * 100).roundToInt()
    val activeGradient = Brush.horizontalGradient(
        listOf(
            lerp(Color(0xFF7A5CFF), Color(0xFF9D7CFF), gradientPhase * 0.35f),
            lerp(Color(0xFF9D7CFF), Color(0xFF64C9FF), gradientPhase * 0.25f),
            lerp(Color(0xFF64C9FF), Color(0xFF7A5CFF), gradientPhase * 0.18f),
        ),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = null,
            tint = if (value > 0.02f) Color(0xFFB9C8FF) else Color(0xFF77829E),
            modifier = Modifier.size(20.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            interactionSource = interactionSource,
            modifier = Modifier
                .weight(1f)
                .height(72.dp)
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                },
            thumb = {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (dragging) {
                        Box(
                            modifier = Modifier
                                .offset(y = (-38).dp)
                                .shadow(10.dp, RoundedCornerShape(12.dp), clip = false)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xF02A2448))
                                .border(1.dp, Color(0x667A5CFF), RoundedCornerShape(12.dp))
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$percentage%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .scale(thumbScale)
                            .blur(7.dp)
                            .background(Color(0xB57A5CFF), CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .size(25.dp)
                            .scale(thumbScale)
                            .shadow(9.dp, CircleShape, clip = false)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, Color(0xFFDFD8FF), CircleShape),
                    )
                }
            },
            track = { sliderState ->
                val fraction = sliderState.value.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(Color(0x4D2A3455))
                            .border(1.dp, Color.White.copy(alpha = 0.07f), CircleShape),
                    )
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(18.dp)
                                .blur(8.dp)
                                .graphicsLayer { alpha = glowAlpha }
                                .background(activeGradient, CircleShape),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(activeGradient)
                                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
                        )
                    }
                }
            },
        )
        Text(
            text = "$percentage%",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFDCE5FF),
            textAlign = TextAlign.End,
            modifier = Modifier.size(width = 38.dp, height = 20.dp),
        )
    }
}

@Composable
private fun PremiumMixerSwitch(checked: Boolean, label: String, onToggle: () -> Unit) {
    val thumbX by animateDpAsState(if (checked) 24.dp else 3.dp, label = "switchThumb")
    val track by animateColorAsState(if (checked) Color(0xFF7258EB) else Color(0xFF37415C), label = "switchTrack")
    Box(
        Modifier.size(52.dp, 31.dp).clip(CircleShape).background(track)
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(role = androidx.compose.ui.semantics.Role.Switch, onClickLabel = label, onClick = onToggle),
    ) {
        Box(
            Modifier.offset(x = thumbX, y = 3.dp).size(25.dp).clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun PremiumSleepTimerCard(context: android.content.Context) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val current = SoundscapeMixer.timerMinutes
    fun choose(target: Int) {
        repeat(5) {
            if (SoundscapeMixer.timerMinutes != target) SoundscapeMixer.cycleTimer(context)
        }
        expanded = false
    }
    MixerGlassCard {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MixerIconWell(Icons.Outlined.Bedtime, active = current > 0)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.common_sleep_timer), style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    if (current > 0) stringResource(R.string.common_minutes, current) else stringResource(R.string.common_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (current > 0) Color(0xFF64C9FF) else Color(0xFFAEB9D0),
                )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Color(0xFFB18CFF), modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 90f else 0f })
        }
        if (expanded) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 15, 30, 45, 60).forEach { minutes ->
                    PremiumPresetPill(
                        selected = current == minutes,
                        label = if (minutes == 0) stringResource(R.string.common_off) else stringResource(R.string.common_minutes, minutes),
                    ) { choose(minutes) }
                }
            }
        }
    }
}

@Composable
private fun PremiumBellCard() {
    var bellOn by remember { mutableStateOf(Chime.timerBellEnabled) }
    MixerGlassCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MixerIconWell(Icons.Outlined.NotificationsNone, bellOn)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.sounds_timer_bell), style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(stringResource(R.string.mixer_bell_description), style = MaterialTheme.typography.bodySmall, color = Color(0xFFAEB9D0))
            }
            PremiumMixerSwitch(bellOn, stringResource(R.string.sounds_timer_bell)) {
                bellOn = !bellOn
                Chime.timerBellEnabled = bellOn
            }
        }
    }
}

/** The Toolkit activity sounds (pattern pads, ripples, the gratitude bloom).
 * Default on — a silent game reads as broken — but sensory sensitivity is common
 * in the people this app is for, so it stays one switch away, next to the bell. */
@Composable
private fun PremiumActivitySoundsCard() {
    var soundsOn by remember { mutableStateOf(Sfx.enabled) }
    MixerGlassCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MixerIconWell(Icons.Outlined.MusicNote, soundsOn)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.sounds_activity), style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(stringResource(R.string.mixer_activity_description), style = MaterialTheme.typography.bodySmall, color = Color(0xFFAEB9D0))
            }
            PremiumMixerSwitch(soundsOn, stringResource(R.string.sounds_activity)) {
                soundsOn = !soundsOn
                Sfx.enabled = soundsOn
            }
        }
    }
}

@Composable
private fun layerDescription(symbol: String): String = when (symbol) {
    "rain" -> stringResource(R.string.mixer_rain_description)
    "ocean" -> stringResource(R.string.mixer_ocean_description)
    "wind" -> stringResource(R.string.mixer_wind_description)
    else -> stringResource(R.string.mixer_drone_description)
}

@Composable
private fun LegacyMixerSectionUnused() {
    val context = LocalContext.current
    val playing = SoundscapeMixer.isPlaying
    Text(stringResource(R.string.mixer_intro),
        style = MaterialTheme.typography.bodyMedium, color = TextMuted)

    PrimaryButton(
        text = if (playing) stringResource(R.string.common_pause_label) else stringResource(R.string.common_play_label),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SoundscapeMixer.toggle(context)
    }

    SectionCard {
        Text(stringResource(R.string.mixer_master_volume), style = MaterialTheme.typography.titleMedium, color = TextSoft)
        Slider(
            value = SoundscapeMixer.master,
            onValueChange = { SoundscapeMixer.setMasterVolume(context, it) },
            valueRange = 0f..1f,
            colors = mixSliderColors(),
        )
    }

    // W27 §3 (Calm study): named one-tap starting blends over the four layers.
    // Selection is derived by vector match, so nudging any slider honestly
    // deselects the chip; the sliders below remain the power path.
    val matching = SoundscapeMixer.matchingPreset()
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SoundscapeMixer.presets.forEachIndexed { i, preset ->
            PickChip(selected = matching == i, label = presetLabel(preset.key)) {
                SoundscapeMixer.applyPreset(context, i)
            }
        }
    }

    SoundscapeMixer.layers.forEachIndexed { i, layer ->
        val vol = SoundscapeMixer.volumes[i]
        val on = vol > 0.02f
        // W10: the card's hairline warms with the layer's volume — LineStroke at
        // rest, easing toward the accent as the layer rises. A pure state mapping
        // (no animation loop), so Reduce Motion needs no branch.
        val layerShape = RoundedCornerShape(Radius.card)
        val borderTint = lerp(LineStroke, Periwinkle.copy(alpha = 0.7f), vol.coerceIn(0f, 1f))
        Column(
            Modifier.fillMaxWidth()
                .glass(layerShape)
                .border(1.dp, borderTint, layerShape)
                .padding(cardPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Tap the tinted well to toggle the layer on/off.
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(
                            if (on) Brush.verticalGradient(listOf(Periwinkle.copy(alpha = 0.34f), Periwinkle.copy(alpha = 0.14f)))
                            else Brush.verticalGradient(listOf(CardFill, CardFill)),
                        )
                        .border(1.dp, if (on) Periwinkle.copy(alpha = 0.4f) else LineStroke, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(layerIcon(layer.symbol), contentDescription = null,
                        tint = if (on) Periwinkle else TextMuted, modifier = Modifier.size(20.dp))
                }
                Text(layer.name, style = MaterialTheme.typography.titleMedium,
                    color = if (on) TextPrimary else TextMuted, modifier = Modifier.weight(1f))
                TextButton(onClick = { SoundscapeMixer.toggleLayer(context, i) }) {
                    Text(
                        if (on) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                        color = if (on) Cyan else TextMuted,
                    )
                }
            }
            Slider(
                value = vol,
                onValueChange = { SoundscapeMixer.setLayerVolume(context, i, it) },
                valueRange = 0f..1f,
                colors = mixSliderColors(),
            )
        }
    }

    // Sleep auto-stop: off → 15 → 30 → 45 → 60, fades out then stops.
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.common_sleep_timer), style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        }
        TextButton(onClick = { SoundscapeMixer.cycleTimer(context) }) {
            Text(
                if (SoundscapeMixer.timerMinutes > 0) stringResource(R.string.common_minutes, SoundscapeMixer.timerMinutes)
                else stringResource(R.string.common_off),
                color = if (SoundscapeMixer.timerMinutes > 0) Cyan else TextMuted,
            )
        }
    }
    TimerBellRow()
    SoundscapeMixer.remainingText()?.let {
        Text(stringResource(R.string.mixer_fades_note, it),
            style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

/** Localized label for a mixer preset's stable key. */
@Composable
private fun presetLabel(key: String): String = when (key) {
    "monsoon_night" -> stringResource(R.string.mixer_preset_monsoon)
    "shoreline" -> stringResource(R.string.mixer_preset_shoreline)
    else -> stringResource(R.string.mixer_preset_still_air)
}

/** W27 §5: the session-end bell toggle, surfaced next to each sleep-timer
 * control (default on) — when a timer completes, the fade ends with one soft
 * chime, then silence. */
@Composable
private fun TimerBellRow() {
    var bellOn by remember { mutableStateOf(Chime.timerBellEnabled) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.sounds_timer_bell), style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        AppSwitch(checked = bellOn, onCheckedChange = { bellOn = it; Chime.timerBellEnabled = it })
    }
}

@Composable
private fun mixSliderColors() = SliderDefaults.colors(
    thumbColor = Periwinkle,
    activeTrackColor = Periwinkle,
    inactiveTrackColor = CardFill,
)

/** Full-screen player for the ambient bed: art, transport, sleep timer,
 * volume (mirrors the iOS sleep player; mixing arrives with real tracks). */
@Composable
fun PlayerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val title = Player.nowPlaying
    val reduceMotion = rememberReduceMotion()
    val playing = title != null && Player.isPlaying
    SubPage(stringResource(R.string.player_eyebrow), title ?: stringResource(R.string.player_nothing), onBack) {
        // Centered art + transport (teammate player look), our tokens throughout.
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val artShape = RoundedCornerShape(26.dp)
            // Slow "breathing" scale on the centered artwork (~5s); steady under Reduce Motion.
            val artScale = if (reduceMotion) 1f else {
                val breathe = rememberInfiniteTransition(label = "art-breathe")
                val s by breathe.animateFloat(
                    initialValue = 1f, targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "art-scale",
                )
                s
            }
            Box(
                Modifier.fillMaxWidth(0.72f).height(240.dp).clip(artShape)
                    .border(1.dp, LineStroke, artShape),
                contentAlignment = Alignment.Center,
            ) {
                // Blurred backdrop: an oversized, soft copy of the same artwork so the
                // centered art floats over a diffuse version of itself. Modifier.blur is
                // API 31+ and degrades gracefully (no-op) on older releases. W21: the
                // art is generative per track — no network, never a dead image.
                ContentArt(
                    title = title.orEmpty(), kind = "soundscape",
                    modifier = Modifier.matchParentSize().scale(1.4f).blur(28.dp),
                    alive = true,   // W24: the blurred waves drift on the 22s loop
                )
                // Scrim to settle the backdrop into the night palette.
                Box(Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(Night.copy(alpha = 0.35f), Night.copy(alpha = 0.72f)))))
                // The centered, breathing artwork floating above the blur — also
                // alive: the blur+scrim mute the backdrop's drift to nothing, so
                // the crisp center carries the visible (still whisper-slow) motion.
                ContentArt(
                    title = title.orEmpty(), kind = "soundscape",
                    motifScale = 1.35f,
                    modifier = Modifier.fillMaxWidth(0.62f).height(168.dp)
                        .scale(artScale).clip(RoundedCornerShape(20.dp))
                        .border(1.dp, LineStroke, RoundedCornerShape(20.dp)),
                    alive = true,
                )
                // Legibility scrim beneath the base overlay.
                Box(Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
                // W27 §6 (Calm study): the fake-reactive 7-bar equalizer is gone —
                // one slow-breathing dot says "playing" honestly instead.
                BreathingDot(
                    playing = playing,
                    dotSize = 12.dp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
            }
            if (title == null) {
                Text(stringResource(R.string.player_empty_hint),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    textAlign = TextAlign.Center)
            } else {
                Text(
                    if (MediaUrls.urlFor(title).isBlank())
                        stringResource(R.string.player_ambient_note)
                    else stringResource(R.string.player_narrated_note),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted,
                    textAlign = TextAlign.Center,
                )
                PrimaryButton(
                    text = if (Player.isPlaying) stringResource(R.string.common_pause_label) else stringResource(R.string.common_play_label),
                    modifier = Modifier.fillMaxWidth(0.62f),
                ) {
                    if (Player.isPlaying) Player.pause(context) else Player.toggle(context, title)
                }
            }
        }
        if (title != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.common_sleep_timer), style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                TextButton(onClick = { Player.cycleTimer(context) }) {
                    Text(
                        if (Player.timerMinutes > 0) stringResource(R.string.common_minutes, Player.timerMinutes)
                        else stringResource(R.string.common_off),
                        color = if (Player.timerMinutes > 0) Cyan else TextMuted,
                    )
                }
            }
            TimerBellRow()
            Text(stringResource(R.string.common_volume), style = MaterialTheme.typography.bodyMedium, color = TextSoft)
            val volumeCd = stringResource(R.string.common_volume)
            Slider(
                value = Player.volume,
                onValueChange = { Player.setVolume(context, it) },
                valueRange = 0f..1f,
                modifier = Modifier.semantics { contentDescription = volumeCd },
            )
            Text(stringResource(R.string.player_fade_note),
                style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

/** W27 §6 (Calm study): the honest "playing" signal — a single small dot that
 * breathes ±15% on a slow ~4s cycle while audio actually plays. It replaces
 * the fake-reactive EqBars (bars implied a waveform readout that never
 * existed — the one element Calm would cut). Paused and Reduce Motion hold a
 * static mid-size dot (static, never blank). Purely ornamental — never a
 * level, position, or progress meter. */
@Composable
internal fun BreathingDot(playing: Boolean, dotSize: Dp = 10.dp, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val scale = if (playing && !reduceMotion) {
        val breathe = rememberInfiniteTransition(label = "now-playing-dot")
        val s by breathe.animateFloat(
            initialValue = 0.85f, targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                tween(2_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "dot-scale",
        )
        s
    } else 1f
    // The outer box reserves the max-scale footprint so the breathing never
    // nudges neighbouring layout.
    Box(
        modifier.size(dotSize * 1.15f).testTag("now-playing-dot"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(dotSize)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(if (playing) Cyan else Cyan.copy(alpha = 0.6f)),
        )
    }
}

/** A compact transport shown whenever something is playing. Tapping the title
 * opens the full player when a route callback is provided. */
@Composable
internal fun NowPlayingBar(onOpenPlayer: (() -> Unit)? = null) {
    val context = LocalContext.current
    val title = Player.nowPlaying ?: return
    val label = if (MediaUrls.urlFor(title).isBlank()) stringResource(R.string.nowplaying_label_ambient)
    else stringResource(R.string.nowplaying_label_narration)
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(
                // Weighted so a long title truncates instead of squeezing the
                // transport actions into vertical wraps ("Pa-us-e").
                (if (onOpenPlayer != null) Modifier.clickable { onOpenPlayer() } else Modifier).weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BreathingDot(playing = Player.isPlaying)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = Cyan, maxLines = 1)
                    Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sleep auto-stop: off → 15 → 30 → 45 → 60 min, fades then stops.
                TextButton(onClick = { Player.cycleTimer(context) }) {
                    Text(
                        if (Player.timerMinutes > 0) stringResource(R.string.nowplaying_timer_on, Player.timerMinutes)
                        else stringResource(R.string.nowplaying_timer_off),
                        color = if (Player.timerMinutes > 0) Cyan else TextMuted,
                        maxLines = 1, softWrap = false,
                    )
                }
                TextButton(onClick = { if (Player.isPlaying) Player.pause(context) else Player.toggle(context, title) }) {
                    Text(
                        if (Player.isPlaying) stringResource(R.string.common_pause_label) else stringResource(R.string.common_play_label),
                        color = Periwinkle,
                        maxLines = 1, softWrap = false,
                    )
                }
            }
        }
    }
}

/** Small-caps section eyebrow inside the Toolkit hub. */
@Composable
private fun ToolkitHeader(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.7.sp),
        color = EyebrowMuted,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** The one activities hub (REDESIGN §2.2: `games` + `tools` merged). Sections
 * are jobs, not categories — Ground · Breathe · Reframe · Settle — with the
 * evidence-lineage tool (CBT reframe) leading its section. */
@Composable
private fun LegacyToolkitScreenUnused(onOpen: (String) -> Unit, onBack: () -> Unit) =
    SubPage(stringResource(R.string.toolkit_eyebrow), stringResource(R.string.toolkit_title), onBack) {
    Text(stringResource(R.string.toolkit_intro),
        style = MaterialTheme.typography.bodyMedium, color = TextSoft)

    ToolkitHeader(stringResource(R.string.toolkit_header_ground))
    Text(stringResource(R.string.toolkit_grounding_intro),
        style = MaterialTheme.typography.bodyMedium, color = TextSoft)
    Grounding()
    NavRow(stringResource(R.string.toolkit_zen_title), stringResource(R.string.toolkit_zen_subtitle),
        icon = Icons.Outlined.Waves) { onOpen("zenripples") }
    FeaturedGameCard(
        title = stringResource(R.string.toolkit_bubble_title),
        subtitle = stringResource(R.string.toolkit_bubble_subtitle),
        onOpen = { onOpen("bubblepop") },
    )

    ToolkitHeader(stringResource(R.string.toolkit_header_breathe))
    NavRow(stringResource(R.string.toolkit_box_title), stringResource(R.string.toolkit_box_subtitle),
        icon = Icons.Outlined.Air) { onOpen("breathe/box") }
    NavRow(stringResource(R.string.toolkit_reset_title), stringResource(R.string.toolkit_reset_subtitle),
        icon = Icons.Outlined.SelfImprovement) { onOpen("breathe/reset") }

    ToolkitHeader(stringResource(R.string.toolkit_header_reframe))
    NavRow(stringResource(R.string.toolkit_cbt_title), stringResource(R.string.toolkit_cbt_subtitle),
        icon = Icons.Outlined.Psychology) { onOpen("cbt") }
    NavRow(stringResource(R.string.toolkit_tipp_title), stringResource(R.string.toolkit_tipp_subtitle),
        icon = Icons.Outlined.Spa) { onOpen("tipp") }

    ToolkitHeader(stringResource(R.string.toolkit_header_settle))
    NavRow(stringResource(R.string.toolkit_gratitude_title), stringResource(R.string.toolkit_gratitude_subtitle),
        icon = Icons.Outlined.LocalFlorist) { onOpen("gratitude") }
    NavRow(stringResource(R.string.toolkit_pattern_title), stringResource(R.string.toolkit_pattern_subtitle),
        icon = Icons.Outlined.AutoAwesome) { onOpen("patternglow") }
    NavRow(stringResource(R.string.toolkit_sounds_title), stringResource(R.string.toolkit_sounds_subtitle),
        icon = Icons.Outlined.GraphicEq) { onOpen("sounds") }

    // The quiet, always-there door (REDESIGN §2.3) — support belongs in the
    // toolkit too, two taps from anywhere.
    NavRow(stringResource(R.string.toolkit_support_title), stringResource(R.string.crisis_telemanas_line),
        icon = Icons.Outlined.HealthAndSafety) { onOpen("crisis") }
}

/** Headline game tile — W21 generative hero art with floating orbs, tappable to
 * open the game. Chrome only; the copy is passed in. Built on palette tokens. */
@Composable
fun ToolkitScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val ambient = rememberInfiniteTransition(label = "toolkitAmbient")
    val glowY by ambient.animateFloat(
        initialValue = -0.08f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(tween(7_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "toolkitGlowY",
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0D1424), Color(0xFF182447), Color(0xFF241A4A))),
        ),
    ) {
        ToolkitAmbientLayer(if (reduceMotion) 0f else glowY)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ToolkitHeroHeader(onBack)
            ToolkitSectionHeader(stringResource(R.string.toolkit_header_ground), stringResource(R.string.toolkit_ground_description), Icons.Outlined.LocalFlorist, Color(0xFF4ADE80))
            Grounding()
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_zen_title), stringResource(R.string.toolkit_zen_subtitle),
                stringResource(R.string.toolkit_duration_open), stringResource(R.string.toolkit_level_gentle),
                stringResource(R.string.toolkit_badge_ground), Icons.Outlined.Waves, Color(0xFF64C9FF), 1,
            ) { onOpen("zenripples") }
            FeaturedGameCard(stringResource(R.string.toolkit_bubble_title), stringResource(R.string.toolkit_bubble_subtitle)) { onOpen("bubblepop") }

            ToolkitSectionHeader(stringResource(R.string.toolkit_header_breathe), stringResource(R.string.toolkit_breathe_description), Icons.Outlined.Air, Color(0xFF64C9FF))
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_box_title), stringResource(R.string.toolkit_box_subtitle),
                stringResource(R.string.toolkit_duration_3), stringResource(R.string.toolkit_level_guided),
                stringResource(R.string.toolkit_badge_breathe), Icons.Outlined.Air, Color(0xFF64C9FF), 3,
            ) { onOpen("breathe/box") }
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_reset_title), stringResource(R.string.toolkit_reset_subtitle),
                stringResource(R.string.toolkit_duration_2), stringResource(R.string.toolkit_level_easy),
                stringResource(R.string.toolkit_badge_breathe), Icons.Outlined.SelfImprovement, Color(0xFF7A5CFF), 4,
            ) { onOpen("breathe/reset") }

            ToolkitSectionHeader(stringResource(R.string.toolkit_header_reframe), stringResource(R.string.toolkit_reframe_description), Icons.Outlined.Psychology, Color(0xFFB18CFF))
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_cbt_title), stringResource(R.string.toolkit_cbt_subtitle),
                stringResource(R.string.toolkit_duration_5), stringResource(R.string.toolkit_level_guided),
                stringResource(R.string.toolkit_badge_reframe), Icons.Outlined.Psychology, Color(0xFFB18CFF), 5,
            ) { onOpen("cbt") }
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_tipp_title), stringResource(R.string.toolkit_tipp_subtitle),
                stringResource(R.string.toolkit_duration_3), stringResource(R.string.toolkit_level_guided),
                stringResource(R.string.toolkit_badge_reframe), Icons.Outlined.Spa, Color(0xFFFFD166), 6,
            ) { onOpen("tipp") }

            ToolkitSectionHeader(stringResource(R.string.toolkit_header_settle), stringResource(R.string.toolkit_settle_description), Icons.Outlined.Bedtime, Color(0xFF9D7CFF))
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_gratitude_title), stringResource(R.string.toolkit_gratitude_subtitle),
                stringResource(R.string.toolkit_duration_3), stringResource(R.string.toolkit_level_gentle),
                stringResource(R.string.toolkit_badge_settle), Icons.Outlined.LocalFlorist, Color(0xFF4ADE80), 7,
            ) { onOpen("gratitude") }
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_pattern_title), stringResource(R.string.toolkit_pattern_subtitle),
                stringResource(R.string.toolkit_duration_2), stringResource(R.string.toolkit_level_easy),
                stringResource(R.string.toolkit_badge_settle), Icons.Outlined.AutoAwesome, Color(0xFFB18CFF), 8,
            ) { onOpen("patternglow") }
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_sounds_title), stringResource(R.string.toolkit_sounds_subtitle),
                stringResource(R.string.toolkit_duration_open), stringResource(R.string.toolkit_level_gentle),
                stringResource(R.string.toolkit_badge_settle), Icons.Outlined.GraphicEq, Color(0xFF64C9FF), 9,
            ) { onOpen("sounds") }
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_support_title), stringResource(R.string.crisis_telemanas_line),
                stringResource(R.string.toolkit_duration_1), stringResource(R.string.toolkit_level_guided),
                stringResource(R.string.toolkit_badge_support), Icons.Outlined.HealthAndSafety, Color(0xFFFF6B81), 10, true,
            ) { onOpen("crisis") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BoxScope.ToolkitAmbientLayer(motion: Float) {
    Canvas(Modifier.matchParentSize()) {
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0x337A5CFF), Color.Transparent)),
            radius = size.minDimension * 0.62f,
            center = Offset(size.width * 0.78f, size.height * (0.12f + motion)),
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0x1F64C9FF), Color.Transparent)),
            radius = size.minDimension * 0.48f,
            center = Offset(size.width * 0.08f, size.height * 0.58f),
        )
        listOf(0.13f to 0.09f, 0.87f to 0.18f, 0.72f to 0.38f, 0.18f to 0.74f).forEachIndexed { index, point ->
            drawCircle(
                color = if (index % 2 == 0) Color(0x4464C9FF) else Color(0x44B18CFF),
                radius = 2.2.dp.toPx(),
                center = Offset(size.width * point.first, size.height * (point.second + motion * 0.25f)),
            )
        }
    }
}

@Composable
private fun ToolkitHeroHeader(onBack: () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    val backLabel = stringResource(R.string.common_back)
    Box(
        Modifier.fillMaxWidth().height(204.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xDD31275F), Color(0xCC1A3155), Color(0xBB182447))))
            .border(1.dp, Color.White.copy(alpha = 0.13f), shape)
            .padding(20.dp),
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 28.dp, y = (-42).dp).size(150.dp)
                .blur(28.dp).background(Color(0x557A5CFF), CircleShape),
        )
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                .clickable(onClickLabel = backLabel, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = backLabel, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Icon(Icons.Outlined.Spa, contentDescription = null, tint = Color(0xFFBFDFFF), modifier = Modifier.align(Alignment.TopEnd).size(52.dp))
        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(stringResource(R.string.toolkit_eyebrow).uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.7.sp), color = Color(0xFFB9C8FF))
            Text(stringResource(R.string.toolkit_title), style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 40.sp), color = Color.White)
            Text(stringResource(R.string.toolkit_intro), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD2D9EB))
        }
    }
}

@Composable
private fun ToolkitSectionHeader(label: String, description: String, icon: ImageVector, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(17.dp)).background(accent.copy(alpha = 0.13f))
                .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAEB9D0))
            Box(
                Modifier.fillMaxWidth().padding(top = 4.dp).height(1.dp)
                    .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.7f), Color.Transparent))),
            )
        }
    }
}

@Composable
private fun ToolkitExerciseCard(
    title: String,
    subtitle: String,
    duration: String,
    difficulty: String,
    category: String,
    icon: ImageVector,
    accent: Color,
    revealIndex: Int,
    emphasis: Boolean = false,
    onOpen: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val reveal = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) reveal.snapTo(1f) else {
            delay((revealIndex * 45L).coerceAtMost(360L))
            reveal.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier.fillMaxWidth().graphicsLayer {
            alpha = reveal.value
            translationY = (1f - reveal.value) * 18.dp.toPx()
        }.pressScale(pressed, down = 0.975f).clip(shape)
            .background(
                Brush.linearGradient(
                    if (emphasis) listOf(Color(0xD9442346), Color(0xCC2A274D))
                    else listOf(Color(0xCC1A2340), Color(0xA8262B4A)),
                ),
            )
            .border(1.dp, accent.copy(alpha = if (emphasis) 0.46f else 0.25f), shape)
            .clickable(interactionSource = interaction, indication = null, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = title, onClick = onOpen)
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.09f))))
                .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(27.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolkitBadge(category, accent)
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8C2D9), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("•  $duration", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD4DCF0))
                Text("•  $difficulty", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD4DCF0))
            }
        }
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.13f))
                .border(1.dp, accent.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ToolkitBadge(label: String, accent: Color) {
    Box(
        Modifier.clip(CircleShape).background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.24f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.9.sp), color = accent)
    }
}

@Composable
private fun FeaturedGameCard(title: String, subtitle: String, onOpen: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val motion = rememberInfiniteTransition(label = "bubblePreview")
    val drift by motion.animateFloat(
        initialValue = -7f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(2_900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bubbleDrift",
    )
    val pulse by motion.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2_100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bubblePulse",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(32.dp)
    val playCd = stringResource(R.string.common_play_cd, title)
    Box(
        Modifier.fillMaxWidth().height(236.dp).pressScale(pressed, down = 0.98f).clip(shape)
            .contentArtBackground(title, kind = "game")
            // Settle the art's bright top so the Cream title/badge keep their
            // contrast (same constant-dark treatment as the hero scrims).
            .background(ArtScrim.copy(alpha = 0.18f))
            .background(Brush.verticalGradient(listOf(Color.Transparent, ArtScrim.copy(alpha = 0.50f))))
            .border(1.dp, Color(0x667A5CFF), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .semantics { contentDescription = playCd }
            .padding(22.dp),
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.22f))
                .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(stringResource(R.string.toolkit_featured), style = MaterialTheme.typography.labelSmall, color = Cream)
        }
        // A couple of drifting bubbles as quiet ornamentation.
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = (-10).dp, y = (if (reduceMotion) 4f else drift).dp)
                .size(76.dp).scale(if (reduceMotion) 1f else pulse).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Periwinkle))),
        )
        Box(
            Modifier.align(Alignment.CenterEnd).offset(x = (-70).dp, y = (if (reduceMotion) 0f else -drift).dp)
                .size(38.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Cyan))),
        )
        Column(
            Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            // Constant-dark gradient art — overlay text must not follow the theme.
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD5DCF0), modifier = Modifier.fillMaxWidth(0.72f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.height(44.dp).clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF7A5CFF), Color(0xFF64C9FF))))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.toolkit_begin), style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
                Text(stringResource(R.string.toolkit_duration_2), style = MaterialTheme.typography.labelMedium, color = Color(0xFFD5DCF0))
            }
        }
    }
}

private data class Bubble(val id: Long, val x: Float, val y: Float, val size: Int, val hue: Color)

/** A calm bubble-pop field — tap the drifting bubbles to pop them. */
@Composable
fun BubblePopScreen(onBack: () -> Unit) {
    var bubbles by remember { mutableStateOf(listOf<Bubble>()) }
    var score by remember { mutableIntStateOf(0) }
    var nextId by remember { mutableLongStateOf(0L) }
    val hues = listOf(Periwinkle, Cyan, Warm)
    val haptics = LocalHapticFeedback.current
    // Spawn near the bottom…
    LaunchedEffect(Unit) {
        while (true) {
            delay(650)
            if (bubbles.size < 7) {
                bubbles = bubbles + Bubble(
                    nextId++,
                    Random.nextFloat() * 0.78f + 0.04f,
                    Random.nextFloat() * 0.16f + 0.80f,
                    (52..96).random(),
                    hues[Random.nextInt(hues.size)],
                )
            }
        }
    }
    // …and drift them gently upward, popping any that float off the top.
    LaunchedEffect(Unit) {
        while (true) {
            delay(40)
            bubbles = bubbles.map { it.copy(y = it.y - 0.005f) }.filter { it.y > -0.15f }
        }
    }
    SubPage(stringResource(R.string.bubblepop_eyebrow), stringResource(R.string.bubblepop_title), onBack) {
        ToolAmbienceEffect(R.raw.ocean)
        Text(stringResource(R.string.bubblepop_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        AmbienceToggle()
        // A quiet score panel + reset — a gentle sense of progress, easily cleared.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.bubblepop_popped), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
                Text("$score", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
            }
            TextButton(onClick = { bubbles = emptyList(); score = 0 }) {
                Text(stringResource(R.string.common_reset), color = Cyan)
            }
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(440.dp).clip(RoundedCornerShape(20.dp))
                .background(CardFill).border(1.dp, LineStroke, RoundedCornerShape(20.dp)),
        ) {
            val w = maxWidth
            val h = maxHeight
            bubbles.forEach { b ->
                Box(
                    Modifier.offset(x = w * b.x, y = h * b.y).size(b.size.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.92f), b.hue)))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            bubbles = bubbles.filterNot { it.id == b.id }; score++
                        },
                )
            }
        }
    }
}

/** The 5-4-3-2-1 grounding steps, resolved from resources in composition. */
@Composable
private fun groundSteps(): List<Pair<String, String>> = listOf(
    stringResource(R.string.ground_step1_title) to stringResource(R.string.ground_step1_hint),
    stringResource(R.string.ground_step2_title) to stringResource(R.string.ground_step2_hint),
    stringResource(R.string.ground_step3_title) to stringResource(R.string.ground_step3_hint),
    stringResource(R.string.ground_step4_title) to stringResource(R.string.ground_step4_hint),
    stringResource(R.string.ground_step5_title) to stringResource(R.string.ground_step5_hint),
)

/** The iOS 5-4-3-2-1 sensory grounding tool as a gentle stepper — inlined at the
 * top of the Toolkit so grounding is zero taps away. */
@Composable
private fun Grounding() {
    var step by remember { mutableIntStateOf(0) }
    val steps = groundSteps()
    val last = step == steps.lastIndex
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xCC183C3B), Color(0xC41A2944))))
            .border(1.dp, Color(0x554ADE80), shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ToolkitBadge(stringResource(R.string.toolkit_badge_ground), Color(0xFF4ADE80))
            Text(
                "•  ${stringResource(R.string.toolkit_duration_3)}   •  ${stringResource(R.string.toolkit_level_guided)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFD4DCF0),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x224ADE80))
                    .border(1.dp, Color(0x444ADE80), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Grain, contentDescription = null, tint = Color(0xFF78E6A1), modifier = Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.toolkit_ground_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(stringResource(R.string.toolkit_grounding_intro), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8C2D9))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.indices.forEach { index ->
                Box(
                    Modifier.weight(1f).height(5.dp).clip(CircleShape)
                        .background(if (index <= step) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.10f)),
                )
            }
        }
        Text(stringResource(R.string.ground_counter), style = MaterialTheme.typography.labelSmall, color = Color(0xFF78E6A1))
        Text(steps[step].first, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Text(steps[step].second, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC4CDE0))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.height(48.dp).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(Color(0xFF4BAE83), Color(0xFF64C9FF))))
                    .clickable { step = if (last) 0 else step + 1 }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (last) stringResource(R.string.ground_start_over) else stringResource(R.string.common_next),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
            if (step > 0) TextButton(onClick = { step -= 1 }) {
                Text(stringResource(R.string.common_back), color = Color(0xFFB8C2D9))
            }
        }
    }
}
