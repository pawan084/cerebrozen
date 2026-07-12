package com.cerebrozen.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cerebrozen.app.BuildConfig
import com.cerebrozen.app.R
import com.cerebrozen.app.audio.MediaUrls
import com.cerebrozen.app.audio.Player
import com.cerebrozen.app.audio.SoundscapeMixer
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.ArtTextSoft
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cream
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.TextBright
import com.cerebrozen.app.ui.theme.VeilWell
import com.cerebrozen.app.ui.theme.EyebrowMuted
import com.cerebrozen.app.ui.theme.Danger
import kotlinx.coroutines.launch
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleDeep
import com.cerebrozen.app.ui.theme.Radius
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.ThumbBlue
import com.cerebrozen.app.ui.theme.ThumbIndigo
import com.cerebrozen.app.ui.theme.ThumbRose
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
                    contentDescription = "Back",
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

private val THUMB_GRADIENTS = listOf(
    listOf(Periwinkle, PeriwinkleDeep),
    listOf(Cyan, ThumbBlue),
    listOf(Warm, ThumbRose),
    listOf(Iris, ThumbIndigo),
)

private fun thumbBrush(seed: String): Brush =
    Brush.linearGradient(THUMB_GRADIENTS[(seed.hashCode() and 0x7fffffff) % THUMB_GRADIENTS.size])

/** Teammate-look gradient hero: a soft vertical gradient panel with a glassy pill
 * eyebrow and overlaid title/subtitle. Pure chrome — content is passed in by the
 * caller, so it never fabricates copy. Built on our palette tokens only. */
@Composable
private fun GradientHero(
    eyebrow: String,
    title: String,
    subtitle: String = "",
    colors: List<Color> = listOf(Iris, PeriwinkleDeep),
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(colors))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(20.dp),
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
}

@Composable
internal fun ContentRow(
    title: String,
    subtitle: String,
    meta: String,
    premium: Boolean,
    playing: Boolean = false,
    icon: ImageVector = Icons.Outlined.GraphicEq,
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
            // Real content photo (iOS AsyncImage) over a gradient fallback + symbol.
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(thumbBrush(title))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(24.dp))
                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageUrl, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                }
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
                if (premium) Text("PREMIUM", style = MaterialTheme.typography.labelSmall, color = Warm)
                if (onFav != null && fav != null) {
                    // 48dp touch target with a visually 22dp icon (a11y minimum).
                    Box(
                        Modifier.size(48.dp).clickable { onFav() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (fav) "Unfavourite $title" else "Favourite $title",
                            tint = Warm,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                if (onTap != null) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause $title" else "Play $title",
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
    icon: ImageVector = Icons.Outlined.GraphicEq,
    onItemTap: ((String) -> Unit)? = null,
    favs: Set<String>? = null,
    onFav: ((String) -> Unit)? = null,
) {
    var items by remember { mutableStateOf<JSONArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(kind) {
        runCatching { Api.content(kind) }
            .onSuccess { items = it }
            .onFailure { error = it.message ?: "Couldn't load." }
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
        items!!.length() == 0 -> Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        else -> (0 until items!!.length()).forEach { i ->
            val c = items!!.getJSONObject(i)
            val title = c.optString("title")
            ContentRow(
                title, c.optString("subtitle"),
                metaLabel(c.optInt("duration_min")), c.optBoolean("premium"),
                playing = Player.nowPlaying == title && Player.isPlaying,
                icon = icon,
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
    var headline by remember { mutableStateOf("Your week") }
    var summary by remember { mutableStateOf("") }
    var metrics by remember { mutableStateOf<JSONArray?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { Api.insightsWeekly() }
            .onSuccess {
                headline = it.optString("headline", "Your week")
                summary = it.optString("summary")
                metrics = it.optJSONArray("metrics")
            }
            .onFailure { error = it.message ?: "Couldn't load your week — try again shortly." }
        loading = false
    }
    SubPage("Insights · this week", headline, onBack) {
        if (loading) {
            Text("Reading your week…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            return@SubPage
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            return@SubPage
        }
        // Real weekly read in a gradient hero — only when the backend returned one.
        if (summary.isNotBlank()) {
            GradientHero(eyebrow = "This week", title = summary)
        }
        // The honest "before" — renders only when a real baseline was saved;
        // otherwise the invitation lives here (REDESIGN §2.2: baseline is the
        // Insights starting point, not a Home row).
        val baseline = BaselineStore.get()
        if (baseline != null) {
            val (stress, sleep, date) = baseline
            SectionCard {
                Text("Your starting point", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(
                    "Stress ${STRESS_WORDS[stress - 1].lowercase()} ($stress/5) · sleep ${SLEEP_WORDS[sleep - 1].lowercase()} ($sleep/5)" +
                        if (date.isNotBlank()) " · recorded $date" else "",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
            }
        } else {
            NavRow("Set your starting point", "Two quick scales — see real change later") { onOpen("baseline") }
        }
        SectionCard {
            val m = metrics
            if (m == null || m.length() == 0) {
                Text("Log a few days and honest patterns appear here — no guesses.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.optString("label"), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(row.optString("value"), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(CardFill)) {
                            Box(Modifier.fillMaxWidth(p * fill.value).height(8.dp).clip(RoundedCornerShape(99.dp))
                                .background(Brush.horizontalGradient(listOf(Periwinkle, Cyan))))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        Text("First-party — computed on your own data, never sold or shared.",
            style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
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

    suspend fun refresh() {
        error = null
        runCatching { active = Api.activeProgram() }
        runCatching {
            val arr = Api.content("program")
            rows = (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                Triple(c.optString("id"), c.optString("title"), c.optString("subtitle"))
            }
        }.onFailure { error = it.message ?: "Couldn't load journeys — try again shortly." }
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }

    SubPage("Guided journeys", "Programs", onBack) {
        Text("Multi-day paths to a calmer baseline. Start any time; go at your pace.",
            style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        // Credibility line (REDESIGN §2.4) — honest provenance, no overclaim.
        Text("Programs are evidence-informed — built on CBT and sleep-science techniques.",
            style = MaterialTheme.typography.labelSmall, color = TextMuted)

        if (loading) {
            Text("Loading journeys…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
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
                eyebrow = "Program · Day $day of $days",
                title = p.optString("title"),
                subtitle = if (p.optBoolean("completed"))
                    "Complete — beautifully done. Start another whenever you like."
                else "Showing up is the whole assignment today.",
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
                    Text("Leave this journey", color = Cream.copy(alpha = 0.85f))
                }
            }
        }

        if (rows.isNotEmpty()) {
            Text("Start something new", style = MaterialTheme.typography.titleMedium, color = TextSoft)
        }
        rows.forEach { (id, title, subtitle) ->
            val isActive = active?.optString("title") == title
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(thumbBrush(title)),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                        Text(subtitle.ifBlank { "A few minutes a day" },
                            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        if (!isActive) {
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { Api.enrollProgram(id) }
                                        .onSuccess { status = "Enrolled — day 1 starts now." }
                                        .onFailure { status = it.message ?: "Couldn't enroll." }
                                    refresh()
                                }
                            }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text("Start this journey", color = Periwinkle)
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
    val play: (String) -> Unit = { title -> Player.toggle(context, title) }
    var favs by remember { mutableStateOf(SleepFavs.all()) }
    val toggleFav: (String) -> Unit = { favs = SleepFavs.toggle(it) }
    var section by rememberSaveable { mutableStateOf(if (startInMixer) "mixer" else "library") }
    SubPage("Sound library", "Sounds", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickChip(selected = section == "library", label = "Library") { section = "library" }
            PickChip(selected = section == "mixer", label = "Mixer") { section = "mixer" }
        }
        if (section == "mixer") {
            MixerSection()
            return@SubPage
        }
        NowPlayingBar(onOpenPlayer = { onOpen("player") })
        SleepTimerPill()
        Text("Soundscapes and sleep stories to slow a racing mind.",
            style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        if (favs.isNotEmpty()) {
            Text("Favourites", style = MaterialTheme.typography.titleMedium, color = TextSoft)
            favs.sorted().forEach { title ->
                ContentRow(
                    title, "", "Favourite", false,
                    playing = Player.nowPlaying == title && Player.isPlaying,
                    onTap = { play(title) }, fav = true, onFav = { toggleFav(title) },
                )
            }
        }
        ContentList("soundscape", { d -> if (d > 0) "$d min" else "Continuous ambient" },
            icon = Icons.Outlined.GraphicEq, onItemTap = play, favs = favs, onFav = toggleFav)
        Text("Sleep stories", style = MaterialTheme.typography.titleMedium, color = TextSoft)
        ContentList("sleep", { d -> if (d > 0) "$d min" else "Sleep story" },
            icon = Icons.Outlined.Bedtime, onItemTap = play, favs = favs, onFav = toggleFav)
        Text("Titles with narration play their own audio; the rest play a calming ambient bed.",
            style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

/** W10: a quiet status pill when a sleep timer is armed — the mixer's live
 * countdown when it has one, else the player's coarse timer. Status only,
 * nothing tappable; renders nothing when no fade-out is armed. */
@Composable
private fun SleepTimerPill() {
    val label = SoundscapeMixer.remainingText()?.let { "Fading out in $it" }
        ?: Player.timerMinutes.takeIf { it > 0 }?.let { "Sleep timer: ${it}m" }
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
    Text("Blend rain, ocean, wind and a soft drone into your own calm — nudge each one to taste.",
        style = MaterialTheme.typography.bodyMedium, color = TextMuted)

    PrimaryButton(text = if (playing) "Pause" else "Play", modifier = Modifier.fillMaxWidth()) {
        SoundscapeMixer.toggle(context)
    }

    SectionCard {
        Text("Master volume", style = MaterialTheme.typography.titleMedium, color = TextSoft)
        Slider(
            value = SoundscapeMixer.master,
            onValueChange = { SoundscapeMixer.setMasterVolume(context, it) },
            valueRange = 0f..1f,
            colors = mixSliderColors(),
        )
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
                    Text(if (on) "On" else "Off", color = if (on) Cyan else TextMuted)
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
            Text("Sleep timer", style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        }
        TextButton(onClick = { SoundscapeMixer.cycleTimer(context) }) {
            Text(
                if (SoundscapeMixer.timerMinutes > 0) "${SoundscapeMixer.timerMinutes} min" else "Off",
                color = if (SoundscapeMixer.timerMinutes > 0) Cyan else TextMuted,
            )
        }
    }
    SoundscapeMixer.remainingText()?.let {
        Text("Fades out in $it — drift off, it stops itself.",
            style = MaterialTheme.typography.labelSmall, color = TextMuted)
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
    SubPage("Now playing", title ?: "Nothing playing", onBack) {
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
                // API 31+ and degrades gracefully (no-op) on older releases.
                AsyncImage(
                    model = HeroImg.sleep, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().scale(1.4f).blur(28.dp),
                )
                // Scrim to settle the backdrop into the night palette.
                Box(Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(Night.copy(alpha = 0.35f), Night.copy(alpha = 0.72f)))))
                // The centered, breathing artwork floating above the blur.
                AsyncImage(
                    model = HeroImg.sleep, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(0.62f).height(168.dp)
                        .scale(artScale).clip(RoundedCornerShape(20.dp))
                        .border(1.dp, LineStroke, RoundedCornerShape(20.dp)),
                )
                // Legibility scrim beneath the base overlay.
                Box(Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
                // Decorative equalizer visualizer — a row of 7 bars that animate ONLY while
                // playing and stay still under Reduce Motion. Purely ornamental: this is not
                // elapsed time, track position, or any progress readout.
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (playing && !reduceMotion) {
                        val eq = rememberInfiniteTransition(label = "eq")
                        repeat(7) { i ->
                            val h by eq.animateFloat(
                                initialValue = 6f,
                                targetValue = (18 + (i % 4) * 7).toFloat(),
                                animationSpec = infiniteRepeatable(
                                    tween(360 + i * 70, easing = FastOutSlowInEasing),
                                    RepeatMode.Reverse),
                                label = "eq-bar-$i",
                            )
                            Box(Modifier.size(width = 5.dp, height = h.dp).clip(RoundedCornerShape(3.dp))
                                .background(Brush.verticalGradient(listOf(Cyan, Periwinkle))))
                        }
                    } else {
                        listOf(10, 16, 12, 20, 12, 16, 10).forEach { hv ->
                            Box(Modifier.size(width = 5.dp, height = hv.dp).clip(RoundedCornerShape(3.dp))
                                .background(Cyan.copy(alpha = 0.5f)))
                        }
                    }
                }
            }
            if (title == null) {
                Text("Pick a soundscape or story from Sounds and it plays here.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    textAlign = TextAlign.Center)
            } else {
                Text(
                    if (MediaUrls.urlFor(title).isBlank())
                        "Continuous ambient bed · this title has no narration yet."
                    else "Narrated track · streams from your library.",
                    style = MaterialTheme.typography.labelSmall, color = TextMuted,
                    textAlign = TextAlign.Center,
                )
                PrimaryButton(
                    text = if (Player.isPlaying) "Pause" else "Play",
                    modifier = Modifier.fillMaxWidth(0.62f),
                ) {
                    if (Player.isPlaying) Player.pause(context) else Player.toggle(context, title)
                }
            }
        }
        if (title != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Sleep timer", style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                TextButton(onClick = { Player.cycleTimer(context) }) {
                    Text(
                        if (Player.timerMinutes > 0) "${Player.timerMinutes} min" else "Off",
                        color = if (Player.timerMinutes > 0) Cyan else TextMuted,
                    )
                }
            }
            Text("Volume", style = MaterialTheme.typography.bodyMedium, color = TextSoft)
            Slider(
                value = Player.volume,
                onValueChange = { Player.setVolume(context, it) },
                valueRange = 0f..1f,
                modifier = Modifier.semantics { contentDescription = "Volume" },
            )
            Text("Fades out ~10 seconds before the timer ends — sleep through it.",
                style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

/** E5: three tiny equalizer bars beside the now-playing title — alive only while
 * audio actually plays; paused and Reduce Motion hold calm mid-height bars
 * (static, never blank). Purely ornamental — never a level or progress meter. */
@Composable
private fun EqBars(playing: Boolean) {
    val reduceMotion = rememberReduceMotion()
    Row(
        Modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (playing && !reduceMotion) {
            val eq = rememberInfiniteTransition(label = "now-playing-eq")
            repeat(3) { i ->
                // Slightly different periods keep the three bars out of phase.
                val h by eq.animateFloat(
                    initialValue = 4f, targetValue = 12f,
                    animationSpec = infiniteRepeatable(
                        tween(700 + i * 90, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "eq-bar-$i",
                )
                Box(Modifier.size(width = 2.dp, height = h.dp)
                    .clip(RoundedCornerShape(1.dp)).background(Cyan))
            }
        } else {
            repeat(3) {
                Box(Modifier.size(width = 2.dp, height = 8.dp)
                    .clip(RoundedCornerShape(1.dp)).background(Cyan.copy(alpha = 0.6f)))
            }
        }
    }
}

/** A compact transport shown whenever something is playing. Tapping the title
 * opens the full player when a route callback is provided. */
@Composable
internal fun NowPlayingBar(onOpenPlayer: (() -> Unit)? = null) {
    val context = LocalContext.current
    val title = Player.nowPlaying ?: return
    val label = if (MediaUrls.urlFor(title).isBlank()) "NOW PLAYING · AMBIENT BED" else "NOW PLAYING · NARRATION"
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(
                if (onOpenPlayer != null) Modifier.clickable { onOpenPlayer() } else Modifier,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EqBars(playing = Player.isPlaying)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = Cyan)
                    Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sleep auto-stop: off → 15 → 30 → 45 → 60 min, fades then stops.
                TextButton(onClick = { Player.cycleTimer(context) }) {
                    Text(
                        if (Player.timerMinutes > 0) "Timer ${Player.timerMinutes}m" else "Timer off",
                        color = if (Player.timerMinutes > 0) Cyan else TextMuted,
                    )
                }
                TextButton(onClick = { if (Player.isPlaying) Player.pause(context) else Player.toggle(context, title) }) {
                    Text(if (Player.isPlaying) "Pause" else "Play", color = Periwinkle)
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
fun ToolkitScreen(onOpen: (String) -> Unit, onBack: () -> Unit) = SubPage("Small ways to steady", "Toolkit", onBack) {
    Text("Pick what the moment needs — ground, breathe, reframe, settle.",
        style = MaterialTheme.typography.bodyMedium, color = TextSoft)

    ToolkitHeader("Ground")
    Text("5-4-3-2-1 grounding — come back to the present through your senses.",
        style = MaterialTheme.typography.bodyMedium, color = TextSoft)
    Grounding()
    NavRow("Zen ripples", "Tap the water, let it widen", icon = Icons.Outlined.Waves) { onOpen("zenripples") }
    FeaturedGameCard(
        title = "Bubble pop",
        subtitle = "Pop gently — no timer, no losing. Just breathe and tap.",
        onOpen = { onOpen("bubblepop") },
    )

    ToolkitHeader("Breathe")
    NavRow("Box breathing", "In, hold, out, hold — four counts each", icon = Icons.Outlined.Air) { onOpen("breathe/box") }
    NavRow("Two-minute reset", "A gentle in and out, nothing to hold", icon = Icons.Outlined.SelfImprovement) { onOpen("breathe/reset") }

    ToolkitHeader("Reframe")
    NavRow("Untangle a thought", "CBT reframe — from stuck to balanced", icon = Icons.Outlined.Psychology) { onOpen("cbt") }
    NavRow("TIPP skill", "DBT reset for very intense moments", icon = Icons.Outlined.Spa) { onOpen("tipp") }

    ToolkitHeader("Settle")
    NavRow("Gratitude garden", "A flower for every thank-you", icon = Icons.Outlined.LocalFlorist) { onOpen("gratitude") }
    NavRow("Pattern glow", "Watch the light, repeat it", icon = Icons.Outlined.AutoAwesome) { onOpen("patternglow") }
    NavRow("Sounds", "Soundscapes and sleep stories", icon = Icons.Outlined.GraphicEq) { onOpen("sounds") }

    // The quiet, always-there door (REDESIGN §2.3) — support belongs in the
    // toolkit too, two taps from anywhere.
    NavRow("Need support right now?", "Tele-MANAS 14416 · real people, 24/7",
        icon = Icons.Outlined.HealthAndSafety) { onOpen("crisis") }
}

/** Headline game tile — a gradient hero with floating orbs, tappable to open the
 * game. Chrome only; the copy is passed in. Built on palette tokens. */
@Composable
private fun FeaturedGameCard(title: String, subtitle: String, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.fillMaxWidth().height(168.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(PeriwinkleDeep, Iris)))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .clickable { onOpen() }
            .semantics { contentDescription = "Play $title" }
            .padding(24.dp),
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.22f))
                .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text("PLAYABLE", style = MaterialTheme.typography.labelSmall, color = Cream)
        }
        // A couple of drifting bubbles as quiet ornamentation.
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 4.dp).size(40.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Periwinkle))),
        )
        Box(
            Modifier.align(Alignment.CenterEnd).offset(x = (-8).dp, y = (-4).dp).size(24.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Cyan))),
        )
        Column(
            Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Cream)
            // Constant-dark gradient art — overlay text must not follow the theme.
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ArtTextSoft)
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
    SubPage("A tiny reset", "Bubble pop", onBack) {
        ToolAmbienceEffect(R.raw.ocean)
        Text("Pop them slowly — no rush, no score to chase.",
            style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        AmbienceToggle()
        // A quiet score panel + reset — a gentle sense of progress, easily cleared.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("POPPED", style = MaterialTheme.typography.labelSmall, color = Periwinkle)
                Text("$score", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
            }
            TextButton(onClick = { bubbles = emptyList(); score = 0 }) {
                Text("Reset", color = Cyan)
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

private val GROUND_STEPS = listOf(
    "5 things you can see" to "Look around — name five, slowly.",
    "4 things you can feel" to "Textures, temperature, your feet on the floor.",
    "3 things you can hear" to "Near, then far.",
    "2 things you can smell" to "Or two scents you like.",
    "1 thing you can taste" to "Or one slow, full breath.",
)

/** The iOS 5-4-3-2-1 sensory grounding tool as a gentle stepper — inlined at the
 * top of the Toolkit so grounding is zero taps away. */
@Composable
private fun Grounding() {
    var step by remember { mutableIntStateOf(0) }
    val last = step == GROUND_STEPS.lastIndex
    SectionCard {
        Text("5 · 4 · 3 · 2 · 1", style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        Text(GROUND_STEPS[step].first, style = MaterialTheme.typography.titleMedium, color = TextSoft)
        Text(GROUND_STEPS[step].second, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { step = if (last) 0 else step + 1 }) {
                Text(if (last) "Start over" else "Next")
            }
            if (step > 0) TextButton(onClick = { step -= 1 }) { Text("Back", color = TextMuted) }
        }
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
    val desc = if (isUrl) "Open $title" else "Call $title $detail"
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

@Composable
fun CrisisScreen(onBack: () -> Unit) {
    var contact by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { Api.trustedContact() }.onSuccess { tc ->
            contact = tc?.let { "${it.optString("name")} · ${it.optString("value")}" }
        }
    }
    // Static (offline-safe) directory — Tele-MANAS leads every crisis surface
    // (REDESIGN §2.3), then emergency services, then an international finder.
    val lines = listOf(
        "Tele-MANAS — real people, 24/7" to "14416",
        "Tele-MANAS on WhatsApp" to "wa.me/9114416",
        "Emergency services" to "112",
        "KIRAN mental-health helpline" to "1800-599-0019",
        "Find a helpline" to "findahelpline.com",
    )
    SubPage("You're not alone", "Urgent support", onBack) {
        GradientHero(
            eyebrow = "You deserve support",
            title = "If you're in immediate danger, please reach out now.",
            colors = listOf(Warm, Danger),
        )
        lines.forEach { (name, number) ->
            SupportLinkRow(name, number, number)
        }
        SectionCard {
            Text("Trusted contact", style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(contact ?: "Not set — add one in Settings so CereBro can reach them in a crisis (with your consent).",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        Text("Wellness support, not emergency care.",
            style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}
