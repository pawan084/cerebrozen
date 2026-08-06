package com.cerebrozen.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.ui.semantics.role
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WbSunny
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import com.cerebrozen.app.R
import com.cerebrozen.app.audio.Chime
import com.cerebrozen.app.audio.MediaUrls
import com.cerebrozen.app.audio.Player
import com.cerebrozen.app.audio.Sfx
import com.cerebrozen.app.audio.SoundscapeMixer
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.Accent2
import com.cerebrozen.app.ui.theme.Amber
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
import com.cerebrozen.app.ui.theme.NightMid
import com.cerebrozen.app.ui.theme.FeaturedEdge
import com.cerebrozen.app.ui.theme.FeaturedInk
import com.cerebrozen.app.ui.theme.FeaturedInkSoft
import com.cerebrozen.app.ui.theme.FeaturedPillEdge
import com.cerebrozen.app.ui.theme.FeaturedPillFill
import com.cerebrozen.app.ui.theme.FeaturedPillInk
import com.cerebrozen.app.ui.theme.FeaturedScrim
import com.cerebrozen.app.ui.theme.MixerHeroBottom
import com.cerebrozen.app.ui.theme.MixerHeroEdge
import com.cerebrozen.app.ui.theme.MixerHeroEyebrow
import com.cerebrozen.app.ui.theme.MixerHeroInk
import com.cerebrozen.app.ui.theme.MixerHeroInkSoft
import com.cerebrozen.app.ui.theme.MixerHeroMid
import com.cerebrozen.app.ui.theme.MixerHeroSpeck
import com.cerebrozen.app.ui.theme.MixerHeroTimer
import com.cerebrozen.app.ui.theme.MixerHeroTop
import com.cerebrozen.app.ui.theme.MixerPlayBottom
import com.cerebrozen.app.ui.theme.MixerPlayTop
import com.cerebrozen.app.ui.theme.MixerWaveBottom
import com.cerebrozen.app.ui.theme.MixerWaveTop
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.OnPrimary
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleDeep
import com.cerebrozen.app.ui.theme.Radius
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Warm
import kotlinx.coroutines.delay
import kotlin.random.Random
import org.json.JSONArray

/** Page frame for a pushed sub-screen: back affordance + eyebrow + serif title. */
@Composable
internal fun SubPage(
    eyebrow: String,
    title: String,
    onBack: () -> Unit,
    /** Callers with switchable panes pass their own state so a pane change can
     * reset to top (the Sounds hub's Library↔Mixer flip kept mid-scroll). */
    scrollState: androidx.compose.foundation.ScrollState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val rise = remember { Animatable(24f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) rise.snapTo(0f) else rise.animateTo(0f, tween(440, easing = FastOutSlowInEasing))
    }
    // Same fix as Page: inset the scrolling VIEWPORT, not the content, so
    // scrolled text cannot pass behind the status bar. Top padding drops from
    // 22dp to 4dp so every pushed screen's header stays where it was.
    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding()
            .verticalScroll(scrollState ?: rememberScrollState())
            .graphicsLayer { translationY = rise.value }
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 22.dp),
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
                    .border(1.dp, LineStroke, CircleShape)
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
private fun GradientHero(
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
    /** Reference rows (no onTap) pass a muted color so their meta stops
     * dressing like a link — "Guide" in periwinkle read as tappable. */
    metaColor: Color = Periwinkle,
    /** A small identifying glyph over the thumb corner — four wind-down
     * guides with sibling ring art read as one card printed four times. */
    glyph: ImageVector? = null,
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
                if (glyph != null) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(3.dp).size(20.dp)
                            // Always-dark thumbnail art underneath, so this well
                            // takes the art scrim, not a themed surface.
                            .clip(CircleShape).background(ArtScrim.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(glyph, contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(12.dp))
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextBright,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (meta.isNotBlank() && !subtitle.contains(meta, ignoreCase = true)) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = metaColor,
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
/** What a [ContentList] shows: served rows, shimmer, or — when the catalogue
 * gives nothing and the caller supplied one — its own offline copy. */
internal enum class ContentListState { Loading, Items, Empty, Error, Fallback }

/**
 * Pure: the branch [ContentList] takes. Extracted so the rule that matters is a
 * test rather than a rendering.
 *
 * Loading must NEVER resolve to Fallback — a caller's offline copy flashing for
 * one frame before the real list arrives would be worse than the shimmer, and it
 * is the mistake the ordering here prevents.
 */
internal fun contentListState(
    error: String?,
    items: JSONArray?,
    hasFallback: Boolean,
): ContentListState = when {
    error != null -> if (hasFallback) ContentListState.Fallback else ContentListState.Error
    items == null -> ContentListState.Loading
    items.length() == 0 -> if (hasFallback) ContentListState.Fallback else ContentListState.Empty
    else -> ContentListState.Items
}

@Composable
internal fun ContentList(
    kind: String,
    metaLabel: (Int) -> String,
    onItemTap: ((String) -> Unit)? = null,
    favs: Set<String>? = null,
    onFav: ((String) -> Unit)? = null,
    emptyText: String? = null,
    emptyIcon: ImageVector? = null,
    /** Shown INSTEAD of the empty/error line when the catalogue gives nothing.
     *
     * For sections whose advice is worth having with no network at all — the
     * wind-down guidance is the case that prompted it, since 3am and a bad
     * connection arrive together. Without this the caller's only option was to
     * render its offline copy unconditionally, which is what Sleep did: two of
     * the four served guides were repeated verbatim in substance a few hundred
     * pixels below the list. */
    fallback: (@Composable () -> Unit)? = null,
    metaColor: Color = Periwinkle,
    glyphFor: ((String) -> ImageVector?)? = null,
) {
    var items by remember(kind) { mutableStateOf<JSONArray?>(null) }
    var error by remember(kind) { mutableStateOf<String?>(null) }   // B35: keyed like the fetch
    var reloadKey by remember(kind) { mutableStateOf(0) }
    val loadFailed = stringResource(R.string.content_error_fallback)
    LaunchedEffect(kind, reloadKey) {
        error = null
        runCatching { Api.content(kind) }
            .onSuccess { items = it }
            .onFailure { error = it.userMessage(loadFailed) }
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
    when (contentListState(error, items, hasFallback = fallback != null)) {
        ContentListState.Fallback -> fallback!!.invoke()
        ContentListState.Error -> Column {
            Text(error!!, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            // Every catalogue section was a dead end on failure (audit B26).
            TextButton(onClick = { reloadKey++ }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(stringResource(R.string.common_try_again), color = Periwinkle)
            }
        }
        ContentListState.Loading -> repeat(3) { ShimmerBox(Modifier.fillMaxWidth().height(72.dp)) }
        // The empty state keeps its icon well and caller-supplied line — the
        // state machine decides WHICH state renders, not how it looks.
        ContentListState.Empty -> Row(
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
        ContentListState.Items -> (0 until items!!.length()).forEach { i ->
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
                metaColor = metaColor,
                glyph = glyphFor?.invoke(title),
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
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(reloadKey) {
        loading = true; error = null
        runCatching { Api.insightsWeekly() }
            .onSuccess {
                headline = it.optString("headline", defaultHeadline)
                summary = it.optString("summary")
                metrics = it.optJSONArray("metrics")
            }
            .onFailure { error = it.userMessage(loadFailed) }
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
            TextButton(onClick = { reloadKey++ }) {
                Text(stringResource(R.string.common_try_again), color = Periwinkle)
            }
            return@SubPage
        }
        // Real weekly read in a gradient hero — only when the backend returned one.
        if (summary.isNotBlank()) {
            GradientHero(eyebrow = stringResource(R.string.insights_hero_eyebrow), title = summary)
        }
        // The honest "before" — renders only when a real baseline was saved;
        // otherwise the invitation lives here (REDESIGN §2.2: baseline is the
        // Insights starting point, not a Home row).
        // Clamped to the scales' 1..5 — BaselineStore returns whatever int
        // parses from the pref, and an out-of-range value indexed straight
        // into stressWords()[stress - 1]: a corrupt pref crashed Insights
        // with IndexOutOfBounds (audit B16).
        val baseline = BaselineStore.get()
        if (baseline != null && baseline.first in 1..5 && baseline.second in 1..5) {
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
                    androidx.compose.runtime.key(row.optString("label", i.toString())) {
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
                    }   // key(label) — B92: identity keying for the fill state
                }
            }
        }
        // The offline insight reel had zero inbound links (audit A9) — quiet
        // local cards, exactly the read-only pause this screen already sells.
        NavRow(stringResource(R.string.oir_title), stringResource(R.string.oir_subtitle)) {
            onOpen("insightreel")
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

/** Every day of a program as (title, body), in order — the journey path's input.
 *
 * Additive like `today_guide`: a server that does not send `guides`, or a
 * program with no day structure, yields an empty list and the caller falls back
 * to the single today-only card. Pure. */
internal fun parseDayGuides(program: org.json.JSONObject?): List<Pair<String, String>> {
    val arr = program?.optJSONArray("guides") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val g = arr.optJSONObject(i) ?: return@mapNotNull null
        val title = g.optString("title").trim()
        val body = g.optString("body").trim()
        if (title.isEmpty() && body.isEmpty()) null else title to body
    }
}

@Composable
fun ProgramsScreen(onBack: () -> Unit, onOpen: (String) -> Unit = {}) {
    // Real enrollment (ref "PROGRAM · DAY X OF Y"): one journey at a time,
    // the day counts itself from the start date — nothing to fail.
    var rows by remember { mutableStateOf(listOf<Triple<String, String, String>>()) } // id, title, subtitle
    var active by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // Whether we actually KNOW there is no journey, or merely failed to ask.
    //
    // The enrollment read was a bare runCatching with no failure branch: if it
    // threw while the catalogue read succeeded, `active` stayed null and the
    // screen drew "Start something new" — so a user on day 5 of 7 was shown the
    // sign-up list with nothing to say their journey still existed. Third time
    // this shape has come up (the safety plan and the consent switches were the
    // other two): a failed read rendering as a confident empty state.
    var activeUnknown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val loadFailed = stringResource(R.string.programs_error_fallback)

    suspend fun refresh() {
        error = null
        runCatching { active = Api.activeProgram() }
            .onSuccess { activeUnknown = false }
            .onFailure { active = null; activeUnknown = true }
        runCatching {
            val arr = Api.content("program")
            rows = (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                Triple(c.optString("id"), c.optString("title"), c.optString("subtitle"))
            }
        }.onFailure { error = it.userMessage(loadFailed) }
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }

    SubPage(stringResource(R.string.programs_eyebrow), stringResource(R.string.programs_title), onBack) {
        Text(stringResource(R.string.programs_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        // Credibility line (REDESIGN §2.4) — honest provenance, no overclaim.
        Text(stringResource(R.string.programs_evidence),
            style = MaterialTheme.typography.bodySmall, color = TextMuted)

        if (loading) {
            Text(stringResource(R.string.programs_loading), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            return@SubPage
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            TextButton(onClick = { scope.launch { loading = true; refresh() } }) {
                Text(stringResource(R.string.common_try_again), color = Periwinkle)
            }
            return@SubPage
        }

        if (activeUnknown) {
            SectionCard {
                Text(stringResource(R.string.programs_active_unknown_title),
                    style = MaterialTheme.typography.titleMedium, color = Danger)
                Text(stringResource(R.string.programs_active_unknown_body),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                TextButton(onClick = { scope.launch { refresh() } }) {
                    Text(stringResource(R.string.common_try_again), color = Periwinkle)
                }
            }
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
                val leaveFailed = stringResource(R.string.programs_error_fallback)
                TextButton(onClick = {
                    scope.launch {
                        // B88: failure used to be swallowed — indistinguishable
                        // from a mis-tap.
                        runCatching { Api.leaveProgram() }
                            .onFailure { status = it.userMessage(leaveFailed) }
                        refresh()
                    }
                }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(stringResource(R.string.programs_leave), color = Cream.copy(alpha = 0.85f))
                }
            }
            // The journey path: every day of the program at once, today marked,
            // nothing gated. Replaces the single today-only guide card, which
            // made a "7-day wind-down" seven surprises — you could read the day
            // you were on and nothing else.
            val guides = parseDayGuides(p)
            if (guides.isNotEmpty()) {
                SectionCard {
                    Text(stringResource(R.string.programs_path_header),
                        style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(stringResource(R.string.programs_path_sub),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    JourneyPath(guides = guides, currentDay = day)
                }
            } else {
                // Older server, or a program with no day structure: the current
                // day's guide alone, exactly as before.
                parseTodayGuide(p)?.let { (guideTitle, guideBody) ->
                    SectionCard {
                        Text(stringResource(R.string.programs_guide_header),
                            style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(guideTitle, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                        Text(guideBody, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                }
            }
        }

        if (rows.isNotEmpty()) {
            Text(stringResource(R.string.programs_start_new_header), style = MaterialTheme.typography.titleMedium, color = TextSoft)
        }
        val enrolledStatus = stringResource(R.string.programs_enrolled_status)
        val enrollFailed = stringResource(R.string.programs_enroll_error)
        rows.forEach { (id, title, subtitle) ->
            // B87: match by content id — two programs sharing a title used to
            // BOTH render as enrolled. Title stays only as a legacy fallback.
            val activeContentId = active?.optString("content_id").orEmpty()
            val isActive =
                if (activeContentId.isNotBlank()) activeContentId == id
                else active?.optString("title") == title
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
                                        .onFailure { status = it.userMessage(enrollFailed) }
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

        // The complete offline education courses shipped route-registered with
        // zero inbound links (audit A5/A6) — whole CBT-I/MBCT journeys with
        // their own per-module progress, unreachable. The journeys hub is
        // their natural home, and they need neither account nor network.
        Text(stringResource(R.string.programs_offline_header),
            style = MaterialTheme.typography.titleMedium, color = TextSoft)
        NavRow(stringResource(R.string.ocbti_title),
            stringResource(R.string.programs_offline_cbti_sub)) { onOpen("cbti") }
        NavRow(stringResource(R.string.ombct_title),
            stringResource(R.string.programs_offline_mbct_sub)) { onOpen("mbct") }
    }
}

/** The one audio hub (REDESIGN §3.4): a Library of served content + favourites
 * and the 4-layer Mixer behind a two-pill switch. [startInMixer] lets the
 * `sounds/mixer` route (Sleep's "mix your own" door) open on the Mixer. */
@Composable
fun SoundsScreen(onBack: () -> Unit, onOpen: (String) -> Unit = {}, startInMixer: Boolean = false) {
    val context = LocalContext.current
    // Sleep-story TITLES, so a favourite plays back with its REAL kind: the
    // favourites section hardcoded "soundscape" and a favourited story played
    // with the wrong kind and the wrong aurora tint (W27 §2).
    var sleepTitles by remember { mutableStateOf(setOf<String>()) }
    // Narration truth for the footnote: only claim narration is coming when
    // this deployment can actually speak.
    var ttsAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        SoundscapeMixer.ensureLoaded()
        runCatching {
            val arr = Api.content("sleep")
            sleepTitles = (0 until arr.length()).mapNotNull {
                arr.optJSONObject(it)?.optString("title")?.takeIf(String::isNotBlank)
            }.toSet()
        }
        runCatching {
            val v = Api.voiceStatus()
            ttsAvailable = v.optBoolean("tts")
        }
    }
    fun kindOf(title: String): String = if (title in sleepTitles) "sleep" else "soundscape"
    // W27 §2: the list a title comes from declares its kind — the aurora's tint
    // signal. Playing also remembers (the recents chip) and opens the player
    // like the Sleep tab's rows (same item, same behavior, at last).
    val playAs: (String, String) -> Unit = { title: String, kind: String ->
        if (Player.nowPlaying == title && Player.isPlaying) {
            Player.toggle(context, title, kind)
        } else {
            runCatching { com.cerebrozen.app.net.Session.prefPut("sounds_recent", title) }
            Player.play(context, title, kind)
            onOpen("player")
        }
    }
    var favs by remember { mutableStateOf(SleepFavs.all()) }
    val toggleFav: (String) -> Unit = { com.cerebrozen.app.ui.Haptics.tap(); favs = SleepFavs.toggle(it) }
    var section by rememberSaveable { mutableStateOf(if (startInMixer) "mixer" else "library") }
    // Each pane opens at its top — the flip used to keep mid-scroll position.
    val paneScroll = remember(section) { androidx.compose.foundation.ScrollState(0) }
    // Back from the Mixer visits the Library once before leaving the hub, so a
    // mixer deep-link (Sleep's door) still discovers the other pane.
    androidx.activity.compose.BackHandler(enabled = section == "mixer") { section = "library" }
    SubPage(
        if (section == "mixer") stringResource(R.string.mixer_eyebrow) else stringResource(R.string.sounds_eyebrow),
        if (section == "mixer") stringResource(R.string.mixer_title) else stringResource(R.string.sounds_title),
        onBack,
        scrollState = paneScroll,
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
        // Offline: the served lists will be stale or empty, but the Mixer is
        // bundled — point across the pane instead of failing quietly.
        if (com.cerebrozen.app.net.Session.servedStale) {
            InfoBanner(
                icon = Icons.Outlined.CloudOff,
                text = stringResource(R.string.sounds_offline_banner),
                actionLabel = stringResource(R.string.sounds_offline_action),
                onAction = { section = "mixer" },
            )
        }
        NowPlayingBar(onOpenPlayer = { onOpen("player") })
        SleepTimerPill()
        // Pick up where you left off — the audio hub finally remembers the
        // one thing its users repeat nightly.
        run {
            val recent: String? = remember {
                runCatching { com.cerebrozen.app.net.Session.prefGet("sounds_recent") }.getOrNull()
            }
            if (Player.nowPlaying == null && !recent.isNullOrBlank()) {
                PickChip(
                    selected = false,
                    label = stringResource(R.string.sounds_recent_chip, recent),
                    announceSelection = false,   // B56: an action, not a choice
                ) { playAs(recent, kindOf(recent)) }
            }
        }
        Text(stringResource(R.string.sounds_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        if (favs.isNotEmpty()) {
            Text(stringResource(R.string.sounds_favourites_header), style = MaterialTheme.typography.titleMedium, color = TextSoft)
            favs.sorted().forEach { title ->
                val kind = kindOf(title)
                ContentRow(
                    title, "",
                    // The row keeps its true meta — "Favourite" hid what a thing was.
                    if (kind == "sleep") stringResource(R.string.sleep_meta_story)
                    else stringResource(R.string.sounds_meta_ambient),
                    false,
                    playing = Player.nowPlaying == title && Player.isPlaying,
                    kind = kind,
                    onTap = { playAs(title, kind) }, fav = true, onFav = { toggleFav(title) },
                )
            }
        }
        // metaLabel lambdas are not composable — capture the templates here.
        val minutesTemplate = stringResource(R.string.common_minutes)
        val ambientMeta = stringResource(R.string.sounds_meta_ambient)
        val storyMeta = stringResource(R.string.sleep_meta_story)
        // The first list gets its header like the two sections after it.
        Text(stringResource(R.string.sounds_soundscapes_header), style = MaterialTheme.typography.titleMedium, color = TextSoft)
        ContentList("soundscape", { d -> if (d > 0) minutesTemplate.format(d) else ambientMeta },
            onItemTap = { playAs(it, "soundscape") }, favs = favs, onFav = toggleFav)
        Text(stringResource(R.string.sounds_sleep_stories_header), style = MaterialTheme.typography.titleMedium, color = TextSoft)
        ContentList("sleep", { d -> if (d > 0) minutesTemplate.format(d) else storyMeta },
            onItemTap = { playAs(it, "sleep") }, favs = favs, onFav = toggleFav)
        // Only promise narration where the deployment can actually speak.
        if (!ttsAvailable) {
            Text(stringResource(R.string.sounds_narration_note),
                style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
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
            .background(CardFill)
            .border(1.dp, LineStroke, shape)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        PremiumSegmentItem(stringResource(R.string.sounds_section_library), !mixerSelected, Modifier.weight(1f), onLibrary)
        PremiumSegmentItem(stringResource(R.string.sounds_section_mixer), mixerSelected, Modifier.weight(1f), onMixer)
    }
}

@Composable
private fun PremiumSegmentItem(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // Brand token, not raw hex (the merge converted the pane below; the pill
    // above it was missed), and real Tab semantics so TalkBack announces the
    // selected pane instead of two anonymous buttons.
    val fill by animateColorAsState(
        if (selected) com.cerebrozen.app.ui.theme.BrandPrimary else Color.Transparent,
        label = "segmentFill",
    )
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val itemShape = RoundedCornerShape(23.dp)
    Box(
        modifier.pressScale(pressed, down = 0.97f).height(48.dp).clip(itemShape)
            .background(fill)
            .then(if (selected) Modifier.border(1.dp, Periwinkle.copy(alpha = 0.5f), itemShape) else Modifier)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = androidx.compose.ui.semantics.Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else TextMuted)
    }
}

@Composable
private fun SleepTimerPill() {
    val context = LocalContext.current
    val mixerOwns = SoundscapeMixer.remainingText() != null || SoundscapeMixer.timerMinutes > 0
    val label = SoundscapeMixer.remainingText()?.let { stringResource(R.string.sounds_fading_out_in, it) }
        ?: Player.timerMinutes.takeIf { it > 0 }?.let { stringResource(R.string.sounds_sleep_timer_short, it) }
        ?: return
    val cycleCd = stringResource(R.string.sounds_timer_cycle_cd)
    Row(
        Modifier
            // B47: a ~26dp-tall tappable was well under the 48dp floor the
            // same file enforces on the favourite button.
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Radius.round))
            .background(CardFill)
            .border(1.dp, LineStroke, RoundedCornerShape(Radius.round))
            // The pill looked tappable and wasn't — now a tap cycles the timer
            // of whichever engine owns it (one Sleep timer, wherever you are).
            .clickable {
                if (mixerOwns) SoundscapeMixer.cycleTimer(context) else Player.cycleTimer(context)
            }
            .semantics { contentDescription = cycleCd }
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
    LaunchedEffect(Unit) { SoundscapeMixer.ensureLoaded() }
    val playing = SoundscapeMixer.isPlaying
    val matching = SoundscapeMixer.matchingPreset()
    // A non-preset blend is named by its loudest layer ("Mostly rain") —
    // "Custom mix" told the user nothing.
    val mixName = matching?.let { presetLabel(SoundscapeMixer.presets[it].key) }
        ?: SoundscapeMixer.dominantLayerRes()?.let { stringResource(R.string.mixer_mostly, stringResource(it)) }
        ?: stringResource(R.string.mixer_custom_mix)
    val session = SoundscapeMixer.remainingText()?.let { stringResource(R.string.sounds_fading_out_in, it) }
        ?: SoundscapeMixer.timerMinutes.takeIf { it > 0 }
            ?.let { stringResource(R.string.mixer_fades_after, it) }
        ?: stringResource(R.string.mixer_open_ended)

    Text(stringResource(R.string.mixer_subtitle), style = MaterialTheme.typography.bodyLarge, color = TextMuted)
    MixerHeroCard(
        playing = playing,
        mixName = mixName,
        session = session,
        onToggle = { com.cerebrozen.app.ui.Haptics.tap(); SoundscapeMixer.toggle(context) },
    )
    MasterVolumeCard(
        value = SoundscapeMixer.master,
        onValueChange = { SoundscapeMixer.setMasterVolume(context, it) },
    )
    // Honesty hints: a lit mix at master-zero is silence; a tuned blend while
    // stopped is silence too. Say which, quietly.
    if (playing && SoundscapeMixer.master < 0.02f) {
        Text(stringResource(R.string.mixer_muted_hint), style = MaterialTheme.typography.labelSmall, color = TextMuted)
    } else if (!playing) {
        Text(stringResource(R.string.mixer_press_play_hint), style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }

    Text(stringResource(R.string.mixer_presets), style = MaterialTheme.typography.titleMedium, color = TextSoft)
    // Edge-bled like every other rail, so a clipped pill reads as "scrolls".
    Row(
        Modifier.bleed(pageHorizontalPadding()).horizontalScroll(rememberScrollState())
            .padding(horizontal = pageHorizontalPadding()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SoundscapeMixer.presets.forEachIndexed { index, preset ->
            PremiumPresetPill(
                selected = matching == index,
                label = presetLabel(preset.key),
            ) {
                // A preset tap is one-tap-to-calm: apply, make sure it's
                // audible, and PLAY — applying silent numbers was homework.
                com.cerebrozen.app.ui.Haptics.tap()
                SoundscapeMixer.applyPreset(context, index)
                if (SoundscapeMixer.master < 0.5f) SoundscapeMixer.setMasterVolume(context, 0.7f)
                if (!SoundscapeMixer.isPlaying) SoundscapeMixer.play(context)
            }
        }
    }

    Text(stringResource(R.string.mixer_layers), style = MaterialTheme.typography.titleMedium, color = TextSoft)
    SoundscapeMixer.layers.forEachIndexed { index, layer ->
        val volume = SoundscapeMixer.volumes[index]
        MixerLayerCard(
            icon = layerIcon(layer.symbol),
            title = stringResource(layer.nameRes),
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
    // (The remaining-time note that used to sit here duplicated the hero's
    // session line — one source of truth per pane.)
}

@Composable
private fun MixerHeroCard(
    playing: Boolean,
    mixName: String,
    session: String,
    onToggle: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    // B24: the clock idles under Reduce Motion, not just the read.
    val glow = restingFloat(reduceMotion, still = 0.18f / 0.28f, initial = 0.45f, target = 0.9f,
        spec = infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "heroGlow")
    val shape = RoundedCornerShape(32.dp)
    // A third of a small screen was hero before any control — scale with the
    // viewport (260dp on tall phones, tighter on 720px-class devices).
    val heroHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.28f).dp
        .coerceIn(190.dp, 260.dp)
    Box(
        Modifier.fillMaxWidth().height(heroHeight).clip(shape)
            .background(Brush.linearGradient(listOf(MixerHeroTop, MixerHeroMid, MixerHeroBottom)))
            .border(1.dp, MixerHeroEdge, shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(16) { i ->
                val x = ((i * 67) % 100) / 100f * size.width
                val y = ((i * 41) % 80) / 100f * size.height
                drawCircle(MixerHeroSpeck.copy(alpha = 0.12f + (i % 3) * 0.07f), (1 + i % 2).dp.toPx(), Offset(x, y))
            }
            drawCircle(
                Brush.radialGradient(listOf(MixerHeroEdge, Color.Transparent), Offset(size.width * 0.78f, size.height * 0.32f), size.width * 0.45f),
                size.width * 0.45f,
                Offset(size.width * 0.78f, size.height * 0.32f),
            )
        }
        Column(
            Modifier.fillMaxSize().padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Owner call 2026-08-05: this hero FOLLOWS the theme. It used to
            // be hardcoded deep-night hexes that survived Dawn; the paint now
            // comes from the MixerHero* roles in Color.kt, which keep the exact
            // Night values and add a light Dawn set.
            val heroSummary = stringResource(R.string.mixer_hero_cd, mixName, session)
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = heroSummary },
            ) {
                Text(
                    // The eyebrow tells the truth when stopped.
                    stringResource(if (playing) R.string.mixer_now_mixing else R.string.mixer_paused_eyebrow),
                    style = MaterialTheme.typography.labelSmall, color = MixerHeroEyebrow,
                )
                Text(mixName, style = MaterialTheme.typography.headlineSmall, color = MixerHeroInk)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, contentDescription = null, tint = MixerHeroTimer, modifier = Modifier.size(16.dp))
                    Text(session, style = MaterialTheme.typography.labelMedium, color = MixerHeroInkSoft)
                }
            }
            MixerWaveform(active = playing)
            val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val toggleCd = stringResource(
                if (playing) R.string.mixer_pause_cd else R.string.mixer_play_cd,
            )
            Row(
                Modifier.pressScale(pressed, down = 0.96f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(MixerPlayTop, MixerPlayBottom)))
                    .border(1.dp, Color.White.copy(alpha = glow * 0.28f), RoundedCornerShape(26.dp))
                    .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
                    .semantics {
                        role = androidx.compose.ui.semantics.Role.Button
                        contentDescription = toggleCd
                    }
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
    // The static branch renders WITHOUT an infinite transition — up to five
    // waveforms per pane used to keep their clocks ticking while drawing
    // fixed-height bars.
    if (!active || reduceMotion) {
        Row(
            Modifier.fillMaxWidth().height(38.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(bars) {
                Box(
                    Modifier.size(3.dp, 6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.verticalGradient(listOf(MixerWaveTop, MixerWaveBottom))),
                )
            }
        }
        return
    }
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
                Modifier.size(3.dp, wave.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(MixerWaveTop, MixerWaveBottom))),
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
                    Text(stringResource(R.string.mixer_master_volume), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(stringResource(R.string.mixer_all_layers), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium, color = Cyan)
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
            .background(if (selected) Brush.linearGradient(listOf(Periwinkle, Accent2)) else Brush.linearGradient(listOf(CardFill, CardFill)))
            .border(1.dp, if (selected) Periwinkle.copy(alpha = 0.53f) else LineStroke, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 12.dp),
    ) {
        // Selected = an accent pill, so its label is the accent's own ink; a
        // fixed white would vanish on Night's pale plum fill.
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) OnPrimary else TextPrimary)
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
    val border by animateColorAsState(if (active) Periwinkle.copy(alpha = 0.55f) else LineStroke, label = "layerBorder")
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(CardFill)
            .border(1.dp, border, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
            MixerIconWell(icon, active)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(description, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            PremiumMixerSwitch(active, title, onToggle)
        }
        if (playing) MixerWaveform(active = true, bars = 12)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                // "Playing" only when the MIX is playing — an audible layer on
                // a stopped mix used to claim it was playing.
                when {
                    playing -> stringResource(R.string.mixer_playing)
                    active -> stringResource(R.string.common_on)
                    else -> stringResource(R.string.common_off)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (playing) Cyan else TextMuted,
            )
            Text("${(volume * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = Periwinkle)
        }
        PremiumMixerSlider(volume, onVolume, title)
    }
}

@Composable
private fun MixerGlassCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(CardFill)
            .border(1.dp, LineStroke, shape)
            // Expanding content (the timer's chips) eases instead of popping.
            .animateContentSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun MixerIconWell(icon: ImageVector, active: Boolean) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(19.dp))
            .background(if (active) Periwinkle.copy(alpha = 0.18f) else LineStroke.copy(alpha = 0.35f))
            .border(1.dp, if (active) Periwinkle.copy(alpha = 0.45f) else LineStroke, RoundedCornerShape(19.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Periwinkle else TextMuted, modifier = Modifier.size(25.dp))
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
    // B23: five of these render on the Mixer pane, each formerly running an
    // endless clock for a barely-perceptible gradient lerp even while idle.
    val gradientPhase = restingFloat(rememberReduceMotion(), still = 0f, initial = 0f, target = 1f,
        spec = infiniteRepeatable(tween(2_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mixerSliderGradientPhase")
    val percentage = (value.coerceIn(0f, 1f) * 100).roundToInt()
    val activeGradient = Brush.horizontalGradient(
        listOf(
            lerp(Periwinkle, Accent2, gradientPhase * 0.35f),
            lerp(Accent2, Cyan, gradientPhase * 0.25f),
            lerp(Cyan, Periwinkle, gradientPhase * 0.18f),
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
            tint = if (value > 0.02f) Periwinkle else TextMuted,
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
                                .background(CardFill)
                                .border(1.dp, Periwinkle.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$percentage%",
                                style = MaterialTheme.typography.labelSmall,
                                // Follows the bubble's Surface fill: white ink
                                // would disappear on Dawn's paper tooltip.
                                color = TextPrimary,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .scale(thumbScale)
                            .blur(7.dp)
                            .background(Periwinkle.copy(alpha = 0.71f), CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .size(25.dp)
                            .scale(thumbScale)
                            .shadow(9.dp, CircleShape, clip = false)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, LineStroke, CircleShape),
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
                            .background(LineStroke.copy(alpha = 0.55f))
                            .border(1.dp, LineStroke, CircleShape),
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
            color = TextSoft,
            textAlign = TextAlign.End,
            modifier = Modifier.size(width = 38.dp, height = 20.dp),
        )
    }
}

@Composable
private fun PremiumMixerSwitch(checked: Boolean, label: String, onToggle: () -> Unit) {
    val thumbX by animateDpAsState(if (checked) 24.dp else 3.dp, label = "switchThumb")
    val track by animateColorAsState(
        if (checked) com.cerebrozen.app.ui.theme.BrandPrimary else LineStroke,
        label = "switchTrack",
    )
    // B46: the visual is 52x31 but the TARGET meets the 48dp floor, and
    // toggleable() carries the on/off state TalkBack could never hear from a
    // bare clickable(role = Switch).
    Box(
        Modifier.sizeIn(minWidth = 52.dp, minHeight = 48.dp)
            .toggleable(
                value = checked,
                role = androidx.compose.ui.semantics.Role.Switch,
                onValueChange = { onToggle() },
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(52.dp, 31.dp).clip(CircleShape).background(track)
                .border(1.dp, if (checked) Periwinkle.copy(alpha = 0.55f) else TextMuted.copy(alpha = 0.35f), CircleShape),
        ) {
            Box(
                Modifier.offset(x = thumbX, y = 3.dp).size(25.dp).clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun PremiumSleepTimerCard(context: android.content.Context) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val current = SoundscapeMixer.timerMinutes
    fun choose(target: Int) {
        // B33: one intent straight to the target — the blind cycle loop reset
        // the service's fade state up to four times per pick.
        SoundscapeMixer.setTimer(context, target)
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
                Text(stringResource(R.string.common_sleep_timer), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    if (current > 0) stringResource(R.string.common_minutes, current) else stringResource(R.string.common_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (current > 0) Cyan else TextMuted,
                )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Periwinkle, modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 90f else 0f })
        }
        if (expanded) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundscapeMixer.TIMER_CYCLE.forEach { minutes ->
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
                Text(stringResource(R.string.sounds_timer_bell), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(stringResource(R.string.mixer_bell_description), style = MaterialTheme.typography.bodySmall, color = TextMuted)
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
                Text(stringResource(R.string.sounds_activity), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(stringResource(R.string.mixer_activity_description), style = MaterialTheme.typography.bodySmall, color = TextMuted)
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


/** Localized label for a mixer preset's stable key. */
@Composable
private fun presetLabel(key: String): String = when (key) {
    "just_rain" -> stringResource(R.string.mixer_preset_just_rain)
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
fun PlayerScreen(onBack: () -> Unit, onOpen: (String) -> Unit = {}) {
    val context = LocalContext.current
    val title = Player.nowPlaying
    val reduceMotion = rememberReduceMotion()
    val playing = title != null && Player.isPlaying
    SubPage(stringResource(R.string.player_eyebrow), title ?: stringResource(R.string.player_nothing), onBack) {
        // H8: nothing playing used to be an inert page — the library is the
        // obvious way forward.
        if (title == null) {
            PrimaryButton(
                text = stringResource(R.string.player_browse_sounds),
                modifier = Modifier.fillMaxWidth(),
            ) { onOpen("sounds") }
            return@SubPage
        }
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

/** The display label for a remembered Toolkit route, or null for routes that
 * should never appear as "recent" (unknown/retired ones simply don't render —
 * a stale pref can't crash the hub). Pure + unit-tested. */
internal fun toolkitRecentLabelRes(route: String): Int? = when (route) {
    "ground" -> R.string.toolkit_ground_title
    "zenripples" -> R.string.toolkit_zen_title
    "games" -> R.string.mg_title
    "bubblepop" -> R.string.toolkit_bubble_title
    "breathe/box" -> R.string.toolkit_box_title
    "breathe/reset" -> R.string.toolkit_reset_title
    "cbt" -> R.string.toolkit_cbt_title
    "tipp" -> R.string.toolkit_tipp_title
    "imagery" -> R.string.toolkit_imagery_title
    "ritual" -> R.string.toolkit_ritual_title
    "gratitude" -> R.string.toolkit_gratitude_title
    "patternglow" -> R.string.toolkit_pattern_title
    "sounds" -> R.string.toolkit_sounds_title
    else -> null
}

@Composable
fun ToolkitScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    // B25: no ambient clock at all when motion is reduced.
    val glowY = restingFloat(reduceMotion, still = 0f, initial = -0.08f, target = 0.14f,
        spec = infiniteRepeatable(tween(7_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "toolkitGlowY")
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Night.copy(alpha = 0.90f), NightMid.copy(alpha = 0.72f), Night.copy(alpha = 0.82f))),
        ),
    ) {
        ToolkitAmbientLayer(glowY)
        // Every exercise door records itself so the hub can offer "pick up
        // where you left off" next visit (support/crisis is deliberately not
        // a "recent" — it is not a practice).
        val openTool: (String) -> Unit = remember(onOpen) {
            { route: String -> runCatching { com.cerebrozen.app.net.Session.prefPut("toolkit_recent", route) }; onOpen(route) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ToolkitHeroHeader(onBack)
            // Returning users mostly come back for one tool — the chip
            // shortcuts the scroll hunt.
            val recentRoute = remember { runCatching { com.cerebrozen.app.net.Session.prefGet("toolkit_recent") }.getOrNull() }
            val recentLabel = recentRoute?.let { toolkitRecentLabelRes(it) }
            if (recentRoute != null && recentLabel != null) {
                PickChip(
                    selected = false,
                    label = stringResource(R.string.toolkit_recent_chip, stringResource(recentLabel)),
                    announceSelection = false,   // B56
                ) { onOpen(recentRoute) }
            }
            // Section/tool accents are the tonal roles, not a private palette:
            // each one is drawn as an ICON TINT on the card fill, so it has to
            // clear 4.5:1 in both themes the way Ok/Cyan/Amber/Warm/Accent do.
            ToolkitSectionHeader(stringResource(R.string.toolkit_header_ground), stringResource(R.string.toolkit_ground_description), Icons.Outlined.LocalFlorist, Ok)
            // The 5-4-3-2-1 practice moved to its own screen (a guided exercise
            // ran INLINE here, restarting silently mid-scroll every visit).
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_ground_title), stringResource(R.string.toolkit_grounding_intro),
                stringResource(R.string.toolkit_duration_3), stringResource(R.string.toolkit_level_guided),
                Icons.Outlined.Grain, Ok, 0,
            ) { openTool("ground") }
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_zen_title), stringResource(R.string.toolkit_zen_subtitle),
                stringResource(R.string.toolkit_duration_open), stringResource(R.string.toolkit_level_gentle),
                Icons.Outlined.Waves, Cyan, 1,
            ) { openTool("zenripples") }
            FeaturedGameCard(stringResource(R.string.toolkit_bubble_title), stringResource(R.string.toolkit_bubble_subtitle)) { openTool("bubblepop") }
            // The door to the twelve offline games. It was orphaned for a day:
            // the only onOpen("games") lived in the retired legacy Toolkit, so
            // a whole shipped hub (engine, registry, tests) was unreachable
            // from the UI — caught walking the emulator, not by any gate.
            ToolkitExerciseCard(
                stringResource(R.string.mg_title), stringResource(R.string.mg_subtitle),
                stringResource(R.string.toolkit_duration_open), stringResource(R.string.toolkit_level_easy),
                Icons.Outlined.SportsEsports, Ok, 2,
            ) { openTool("games") }

            ToolkitSectionHeader(stringResource(R.string.toolkit_header_breathe), stringResource(R.string.toolkit_breathe_description), Icons.Outlined.Air, Cyan)
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_box_title), stringResource(R.string.toolkit_box_subtitle),
                stringResource(R.string.toolkit_duration_3), stringResource(R.string.toolkit_level_guided),
                Icons.Outlined.Air, Cyan, 3,
            ) { openTool("breathe/box") }
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_reset_title), stringResource(R.string.toolkit_reset_subtitle),
                stringResource(R.string.toolkit_duration_2), stringResource(R.string.toolkit_level_easy),
                Icons.Outlined.SelfImprovement,
                // B60: this was 0xFF7A5CFF — a NEAR-BrandPrimary purple subtly
                // disagreeing with the brand purple on the same screen.
                com.cerebrozen.app.ui.theme.BrandPrimary, 4,
            ) { openTool("breathe/reset") }

            ToolkitSectionHeader(stringResource(R.string.toolkit_header_reframe), stringResource(R.string.toolkit_reframe_description), Icons.Outlined.Psychology, Periwinkle)
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_cbt_title), stringResource(R.string.toolkit_cbt_subtitle),
                stringResource(R.string.toolkit_duration_5), stringResource(R.string.toolkit_level_guided),
                Icons.Outlined.Psychology, Periwinkle, 5,
            ) { openTool("cbt") }
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_tipp_title), stringResource(R.string.toolkit_tipp_subtitle),
                stringResource(R.string.toolkit_duration_3), stringResource(R.string.toolkit_level_guided),
                Icons.Outlined.Spa, Amber, 6,
            ) { openTool("tipp") }

            ToolkitSectionHeader(stringResource(R.string.toolkit_header_settle), stringResource(R.string.toolkit_settle_description), Icons.Outlined.Bedtime, Accent2)
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_imagery_title), stringResource(R.string.toolkit_imagery_subtitle),
                stringResource(R.string.toolkit_duration_2), stringResource(R.string.toolkit_level_guided),
                Icons.Outlined.Bedtime, Accent2, 7,
            ) { openTool("imagery") }
            // The standalone body scan shipped route-registered but door-less
            // (audit A7) — only the wind-down ritual's embedded step existed.
            ToolkitExerciseCard(
                stringResource(R.string.obs_title), stringResource(R.string.toolkit_bodyscan_subtitle),
                stringResource(R.string.toolkit_duration_3), stringResource(R.string.toolkit_level_gentle),
                Icons.Outlined.Spa, Cyan, 7,
            ) { openTool("bodyscan") }
            // The builder is a door, not a section of its own: it only
            // sequences the tools above, and putting it first would suggest
            // setup comes before use.
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_ritual_title), stringResource(R.string.toolkit_ritual_subtitle),
                stringResource(R.string.toolkit_duration_open), stringResource(R.string.toolkit_level_guided),
                Icons.Outlined.AutoAwesome, Accent2, 7,
            ) { openTool("ritual") }
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_gratitude_title), stringResource(R.string.toolkit_gratitude_subtitle),
                stringResource(R.string.toolkit_duration_3), stringResource(R.string.toolkit_level_gentle),
                Icons.Outlined.LocalFlorist, Ok, 7,
            ) { openTool("gratitude") }
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_pattern_title), stringResource(R.string.toolkit_pattern_subtitle),
                stringResource(R.string.toolkit_duration_2), stringResource(R.string.toolkit_level_easy),
                Icons.Outlined.AutoAwesome, Periwinkle, 8,
            ) { openTool("patternglow") }
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_sounds_title), stringResource(R.string.toolkit_sounds_subtitle),
                stringResource(R.string.toolkit_duration_open), stringResource(R.string.toolkit_level_gentle),
                Icons.Outlined.GraphicEq, Cyan, 9,
            ) { openTool("sounds") }
            // Region-aware subtitle: the card names the user's actual crisis
            // line (CrisisDirectory), not a hardcoded India number.
            val toolkitSupportLine = primaryCrisisLine(rememberCrisisRegion().value)
            ToolkitExerciseCard(
                stringResource(R.string.toolkit_support_title),
                if (isSupportUrl(toolkitSupportLine.target)) stringResource(toolkitSupportLine.nameRes)
                else stringResource(R.string.you_support_line,
                    stringResource(toolkitSupportLine.nameRes), toolkitSupportLine.target),
                stringResource(R.string.toolkit_duration_1), stringResource(R.string.toolkit_level_guided),
                Icons.Outlined.HealthAndSafety, Warm, 10, true,
            ) { onOpen("crisis") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BoxScope.ToolkitAmbientLayer(motion: Float) {
    Canvas(Modifier.matchParentSize()) {
        drawCircle(
            brush = Brush.radialGradient(listOf(Periwinkle.copy(alpha = 0.2f), Color.Transparent)),
            radius = size.minDimension * 0.62f,
            center = Offset(size.width * 0.78f, size.height * (0.12f + motion)),
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Cyan.copy(alpha = 0.12f), Color.Transparent)),
            radius = size.minDimension * 0.48f,
            center = Offset(size.width * 0.08f, size.height * 0.58f),
        )
        listOf(0.13f to 0.09f, 0.87f to 0.18f, 0.72f to 0.38f, 0.18f to 0.74f).forEachIndexed { index, point ->
            drawCircle(
                color = if (index % 2 == 0) Cyan.copy(alpha = 0.27f) else Periwinkle.copy(alpha = 0.27f),
                radius = 2.2.dp.toPx(),
                center = Offset(size.width * point.first, size.height * (point.second + motion * 0.25f)),
            )
        }
    }
}

@Composable
private fun ToolkitHeroHeader(onBack: () -> Unit) {
    val backLabel = stringResource(R.string.common_back)
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(VeilWell)
                .border(1.dp, LineStroke, CircleShape)
                .clickable(onClickLabel = backLabel, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = backLabel, tint = TextPrimary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.toolkit_eyebrow).uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.7.sp), color = EyebrowMuted)
            Text(stringResource(R.string.toolkit_title), style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text(stringResource(R.string.toolkit_intro), style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 2)
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
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.09f))
                .border(1.dp, LineStroke, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Text(description, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(1.dp).background(LineStroke))
        }
    }
}

@Composable
private fun ToolkitExerciseCard(
    title: String,
    subtitle: String,
    duration: String,
    /** "Guided" / "Gentle" / "Easy". Register B58: this and a `category` badge
     * were passed by every call site and rendered by neither, so the metadata
     * silently vanished. The level now shows beside the duration; the category
     * badge is gone because it only ever repeated the section header the card
     * already sits under. */
    difficulty: String,
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
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().graphicsLayer {
            alpha = reveal.value
            translationY = (1f - reveal.value) * 18.dp.toPx()
        }.pressScale(pressed, down = 0.975f).clip(shape)
            .background(CardFill)
            .border(1.dp, if (emphasis) accent.copy(alpha = 0.40f) else LineStroke, shape)
            .clickable(interactionSource = interaction, indication = null, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = title, onClick = onOpen)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = 0.10f))
                .border(1.dp, LineStroke, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (difficulty.isBlank()) duration else "$duration · $difficulty",
                style = MaterialTheme.typography.labelSmall, color = TextMuted2,
            )
        }
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(VeilWell)
                .border(1.dp, LineStroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FeaturedGameCard(title: String, subtitle: String, onOpen: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    // B26: same gate-the-clock fix as the hero and the hub ambient.
    val drift = restingFloat(reduceMotion, still = 4f, initial = -7f, target = 8f,
        spec = infiniteRepeatable(tween(2_900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bubbleDrift")
    val pulse = restingFloat(reduceMotion, still = 1f, initial = 0.92f, target = 1.08f,
        spec = infiniteRepeatable(tween(2_100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bubblePulse")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(32.dp)
    val playCd = stringResource(R.string.common_play_cd, title)
    Box(
        Modifier.fillMaxWidth().height(236.dp).pressScale(pressed, down = 0.98f).clip(shape)
            .contentArtBackground(title, kind = "game")
            // Settle the art's bright top so the Cream title/badge keep their
            // contrast (same constant-dark treatment as the hero scrims).
            // Owner call 2026-08-05: the billboard follows the theme. The
            // generative art underneath is unchanged; only the scrim over it
            // flips — Night sinks the art, Dawn lifts it to a pastel wash so
            // ink text reads on top.
            .background(FeaturedScrim.copy(alpha = 0.18f))
            .background(Brush.verticalGradient(listOf(Color.Transparent, FeaturedScrim.copy(alpha = 0.50f))))
            .border(1.dp, FeaturedEdge, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .semantics { contentDescription = playCd }
            .padding(22.dp),
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(FeaturedPillFill)
                .border(1.dp, FeaturedPillEdge, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(stringResource(R.string.toolkit_featured), style = MaterialTheme.typography.labelSmall, color = FeaturedPillInk)
        }
        // A couple of drifting bubbles as quiet ornamentation.
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = (-10).dp, y = drift.dp)
                .size(76.dp).scale(pulse).clip(CircleShape)
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
            Text(title, style = MaterialTheme.typography.headlineSmall, color = FeaturedInk)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = FeaturedInkSoft, modifier = Modifier.fillMaxWidth(0.72f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.height(44.dp).clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Periwinkle, Accent2)))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.toolkit_begin), style = MaterialTheme.typography.labelMedium, color = OnPrimary)
                    }
                }
                Text(stringResource(R.string.toolkit_duration_2), style = MaterialTheme.typography.labelMedium, color = FeaturedInkSoft)
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
    fun freshBubbles(): List<Bubble> = (0 until 7).map { i ->
        Bubble(
            nextId++, Random.nextFloat() * 0.66f + 0.06f,
            0.08f + i * 0.115f, (54..90).random(), hues[Random.nextInt(hues.size)],
        )
    }
    // Reduce Motion is a contract: no spawn loop, no drift loop. The field
    // still gets one static set of bubbles to pop — static, never blank.
    // B10: "never blank" now includes AFTER the seventh pop — the spawn loop
    // exited under RM, so an emptied pool stayed empty until the small Reset.
    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(reduceMotion, bubbles.isEmpty()) {
        if (reduceMotion && bubbles.isEmpty()) bubbles = freshBubbles()
    }
    // Spawn near the bottom…
    LaunchedEffect(reduceMotion) {
        if (bubbles.isEmpty()) bubbles = freshBubbles()
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            delay(650)
            if (bubbles.size < 7) {
                bubbles = bubbles + Bubble(
                    nextId++,
                    Random.nextFloat() * 0.66f + 0.06f,
                    Random.nextFloat() * 0.16f + 0.80f,
                    (54..90).random(),
                    hues[Random.nextInt(hues.size)],
                )
            }
        }
    }
    // …and drift them gently upward, popping any that float off the top.
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        // B31: 15Hz is indistinguishable for a slow drift and skips entirely
        // while the pool is empty — the 25Hz loop rebuilt and re-filtered the
        // whole list every 40ms even with nothing to move.
        while (true) {
            delay(66)
            if (bubbles.isNotEmpty()) {
                bubbles = bubbles.map { it.copy(y = it.y - 0.008f) }.filter { it.y > -0.15f }
            }
        }
    }
    SubPage(stringResource(R.string.bubblepop_eyebrow), stringResource(R.string.bubblepop_title), onBack) {
        ToolAmbienceEffect(R.raw.ocean)
        SectionCard(quiet = true) {
            Text(stringResource(R.string.bubblepop_intro),
                style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        // A quiet score panel + reset — a gentle sense of progress, easily cleared.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$score", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text(stringResource(R.string.bubblepop_popped), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                AmbienceToggle()
                TextButton(onClick = { bubbles = freshBubbles(); score = 0 }) {
                    Text(stringResource(R.string.common_reset), color = Cyan)
                }
            }
        }
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(430.dp).clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(
                    listOf(Cyan.copy(alpha = 0.12f), Periwinkle.copy(alpha = 0.09f), CardFill),
                )).border(1.dp, Cyan.copy(alpha = 0.22f), RoundedCornerShape(28.dp)),
        ) {
            val w = maxWidth
            val h = maxHeight
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(Cyan.copy(alpha = 0.14f), Color.Transparent)),
                    radius = size.minDimension * 0.62f,
                    center = Offset(size.width * 0.48f, size.height * 0.48f),
                )
            }
            val popCd = stringResource(R.string.bubble_pop_cd)
            bubbles.forEach { b ->
                Box(
                    Modifier.offset(x = w * b.x, y = h * b.y).size(b.size.dp)
                        .shadow(12.dp, CircleShape, clip = false,
                            ambientColor = b.hue.copy(alpha = 0.42f), spotColor = b.hue.copy(alpha = 0.42f))
                        .clip(CircleShape)
                        .background(Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.96f), b.hue.copy(alpha = 0.78f), b.hue),
                            center = Offset(0.34f, 0.28f),
                        )).border(1.dp, Color.White.copy(alpha = 0.52f), CircleShape)
                        // B54: the featured Toolkit activity was invisible
                        // to screen readers — anonymous clickable Boxes.
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            com.cerebrozen.app.ui.Haptics.soft()
                            bubbles = bubbles.filterNot { it.id == b.id }; score++
                        }
                        .semantics { contentDescription = popCd },
                ) {
                    Box(
                        Modifier.align(Alignment.TopStart)
                            .offset((b.size * 0.20f).dp, (b.size * 0.16f).dp)
                            .size((b.size * 0.18f).dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.48f)),
                    )
                }
            }
        }
    }
}

/** The 5-4-3-2-1 grounding steps, resolved from resources in composition. */
@Composable
internal fun groundSteps(): List<Pair<String, String>> = listOf(
    stringResource(R.string.ground_step1_title) to stringResource(R.string.ground_step1_hint),
    stringResource(R.string.ground_step2_title) to stringResource(R.string.ground_step2_hint),
    stringResource(R.string.ground_step3_title) to stringResource(R.string.ground_step3_hint),
    stringResource(R.string.ground_step4_title) to stringResource(R.string.ground_step4_hint),
    stringResource(R.string.ground_step5_title) to stringResource(R.string.ground_step5_hint),
)

/** The 5-4-3-2-1 grounding practice on its own screen. It used to run INLINE in
 * the Toolkit hub — a guided three-minute exercise sandwiched mid-scroll between
 * a section header and the next card, restarting silently every visit. A focused
 * screen gives it what an exercise needs: room, a completion moment, and its
 * evidence attached rather than floating after it. */
@Composable
fun GroundingScreen(onBack: () -> Unit) {
    // Saveable: a theme switch or process death mid-exercise must not
    // restart 5-4-3-2-1 at step 1 (audit B1) — losing your place is the
    // worst moment for it, as the wind-down ritual already documents.
    var step by rememberSaveable { mutableIntStateOf(0) }
    var done by rememberSaveable { mutableStateOf(false) }
    val steps = groundSteps()
    val last = step == steps.lastIndex
    PremiumSubPage(
        stringResource(R.string.toolkit_badge_ground),
        stringResource(R.string.toolkit_ground_title),
        onBack = onBack,
    ) {
        if (done) {
            SectionCard {
                Text(stringResource(R.string.ground_done_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(stringResource(R.string.ground_done_body), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                // H14: completion offered only "Start again" — finishing is the
                // likelier intent, so Done leads.
                PrimaryButton(text = stringResource(R.string.common_done), modifier = Modifier.fillMaxWidth()) {
                    onBack()
                }
                TextButton(onClick = { step = 0; done = false }) {
                    Text(stringResource(R.string.ground_start_again), color = Periwinkle)
                }
            }
        } else {
            SectionCard {
                Text(stringResource(R.string.toolkit_grounding_intro), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.indices.forEach { index ->
                        Box(
                            Modifier.weight(1f).height(5.dp).clip(CircleShape)
                                .background(if (index <= step) Ok else LineStroke),
                        )
                    }
                }
                // B61: theme tokens, not raw hex — the counter now follows
                // Night/Dawn like everything else on the screen.
                Text(stringResource(R.string.ground_counter), style = MaterialTheme.typography.labelSmall, color = Ok)
                Text(steps[step].first, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(steps[step].second, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    // B67: the bespoke gradient Box (no Role.Button, no haptic)
                    // becomes the app's own PrimaryButton like every other
                    // primary action.
                    PrimaryButton(
                        text = if (last) stringResource(R.string.common_done) else stringResource(R.string.common_next),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (last) {
                            done = true
                            Celebrations.trigger()
                        } else {
                            step += 1
                        }
                    }
                    if (step > 0) TextButton(onClick = { step -= 1 }) {
                        Text(stringResource(R.string.common_back), color = TextSoft)
                    }
                }
            }
        }
        // The evidence travels WITH the practice (REDESIGN F9) — it used to
        // float after the inline card as a separate row on the hub.
        WhyThisWorks(stringResource(R.string.ground_why))
    }
}

/** True when a support target is a link rather than a dialable number — any
 * letter means URL (phone numbers are digits, dashes and spaces). Shared by the
 * crisis and human-support directories. */
internal fun isSupportUrl(target: String): Boolean = target.any { it.isLetter() }

/** Open a support target: phone numbers open the dialer (never auto-call), URLs
 * open the browser/WhatsApp. Failures are swallowed — a missing handler must
 * never crash a support surface. */
internal fun openSupportTarget(ctx: android.content.Context, target: String) {
    val intent = if (isSupportUrl(target)) {
        Intent(Intent.ACTION_VIEW, Uri.parse(if (target.startsWith("http")) target else "https://$target"))
    } else {
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$target"))
    }
    runCatching { ctx.startActivity(intent) }
}

/** A tappable support line — title, a cyan detail line, and a call/open glyph.
 * The whole card is one accessible target (used by Crisis + Human support). */
@Composable
internal fun SupportLinkRow(title: String, detail: String, target: String) {
    val ctx = LocalContext.current
    val isUrl = isSupportUrl(target)
    val desc = if (isUrl) stringResource(R.string.crisis_open_cd, title)
    else stringResource(R.string.crisis_call_cd, title, detail)
    SectionCard(onClick = { openSupportTarget(ctx, target) }) {
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = desc },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = Cyan)
            }
            Icon(
                if (isUrl) Icons.AutoMirrored.Outlined.OpenInNew else Icons.Outlined.Call,
                contentDescription = null, tint = Cyan, modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Compact high-priority action used by the urgent-support directory. */
@Composable
private fun CrisisSupportRow(title: String, detail: String, target: String, primary: Boolean = false) {
    val ctx = LocalContext.current
    val isUrl = isSupportUrl(target)
    val desc = if (isUrl) stringResource(R.string.crisis_open_cd, title)
    else stringResource(R.string.crisis_call_cd, title, detail)
    val accent = if (primary) Warm else Cyan
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(accent.copy(alpha = if (primary) 0.10f else 0.045f))
            .border(1.dp, accent.copy(alpha = if (primary) 0.30f else 0.16f), shape)
            .clickable { openSupportTarget(ctx, target) }
            .semantics { contentDescription = desc }
            .padding(horizontal = 15.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = accent)
        }
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isUrl) Icons.AutoMirrored.Outlined.OpenInNew else Icons.Outlined.Call,
                contentDescription = null, tint = accent, modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
fun CrisisScreen(onBack: () -> Unit, onOpen: (String) -> Unit = {}) {
    var contact by remember { mutableStateOf<String?>(null) }
    // A failed read must not render as "add one" — on this surface a false
    // empty state tells someone their person isn't there. Unknown says unknown.
    var contactUnknown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { Api.trustedContact() }
            .onSuccess { tc ->
                contactUnknown = false
                contact = tc?.let { "${it.optString("name")} · ${it.optString("value")}" }
            }
            .onFailure { contactUnknown = true }
    }
    // Region-aware (offline-safe) directory — CrisisDirectory mirrors backend
    // crisis.py; the You → Crisis region setting finally governs the numbers
    // this surface shows. Tele-MANAS leads in India (REDESIGN §2.3); elsewhere
    // the backend's emergency-first order holds. Targets are dial/URL contracts
    // and stay literal. The findahelpline finder is appended for every region
    // as the universal escape hatch (the default region already carries it).
    // W25 (CTA audit): no WhatsApp row — no official Tele-MANAS WhatsApp exists.
    val region by rememberCrisisRegion()
    val regional = crisisLinesFor(region)
    val lines = (
        if (regional.any { isSupportUrl(it.target) }) regional
        else regional + CrisisLine(R.string.crisis_line_find_helpline, "findahelpline.com")
    ).map { stringResource(it.nameRes) to it.target }
    SubPage(stringResource(R.string.crisis_eyebrow), stringResource(R.string.crisis_title), onBack) {
        val heroShape = RoundedCornerShape(24.dp)
        Column(
            Modifier.fillMaxWidth().clip(heroShape)
                .background(Brush.verticalGradient(
                    listOf(Warm.copy(alpha = 0.16f), Danger.copy(alpha = 0.09f)),
                ))
                .border(1.dp, Warm.copy(alpha = 0.32f), heroShape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.crisis_hero_eyebrow).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.3.sp),
                color = Warm,
            )
            Text(
                stringResource(R.string.crisis_hero_title),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
        }
        lines.forEachIndexed { index, (name, number) ->
            CrisisSupportRow(name, number, number, primary = index == 0)
        }
        // The region setting, visible where it acts — a wrong-country list is
        // one tap from correct instead of buried under You → Settings.
        NavRow(
            stringResource(R.string.crisis_region_showing, stringResource(regionLabelRes(region))),
            stringResource(R.string.crisis_region_change),
            icon = Icons.Outlined.Public,
        ) { onOpen("crisisregion") }
        // For the person who cannot make a call right now: the crisis-specific
        // grounding practice (offline), built for exactly this screen.
        NavRow(
            stringResource(R.string.crisis_ground_title),
            stringResource(R.string.crisis_ground_sub),
            icon = Icons.Outlined.SelfImprovement,
        ) { onOpen("crisisgrounding") }
        // A door, not a notice. It used to be an inert card telling the user to
        // "add one in Settings" — where no such setting existed on Android.
        NavRow(
            stringResource(R.string.crisis_trusted_contact_title),
            contact ?: stringResource(
                if (contactUnknown) R.string.crisis_trusted_contact_unknown
                else R.string.crisis_trusted_contact_empty,
            ),
            icon = Icons.Outlined.PersonAddAlt,
        ) { onOpen("trustedcontact") }
        // The plan written for this moment, reachable in this moment — not only
        // via the calm-state path through You.
        NavRow(
            stringResource(R.string.crisis_safety_plan_title),
            stringResource(R.string.crisis_safety_plan_sub),
            icon = Icons.Outlined.FactCheck,
        ) { onOpen("safetyplan") }
        Text(stringResource(R.string.common_wellness_footer),
            style = MaterialTheme.typography.bodySmall, color = TextMuted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
