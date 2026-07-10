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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cerebrozen.app.BuildConfig
import com.cerebrozen.app.R
import com.cerebrozen.app.audio.MediaUrls
import com.cerebrozen.app.audio.Player
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cream
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Danger
import kotlinx.coroutines.launch
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleDeep
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
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("‹ Back", color = TextMuted)
        }
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        Text(title, style = MaterialTheme.typography.displaySmall, color = TextPrimary)
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
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // Real content photo (iOS AsyncImage) over a gradient fallback + symbol.
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)).background(thumbBrush(title)),
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
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft,
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
    when {
        error != null -> Text(error!!, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        items == null -> Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        items!!.length() == 0 -> Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        else -> (0 until items!!.length()).forEach { i ->
            val c = items!!.getJSONObject(i)
            val title = c.optString("title")
            MediaUrls.register(title, MediaUrls.resolve(c.optString("audio_url"), BuildConfig.API_BASE_URL))
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
fun InsightsScreen(onBack: () -> Unit) {
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
        // The honest "before" — renders only when a real baseline was saved.
        BaselineStore.get()?.let { (stress, sleep, date) ->
            SectionCard {
                Text("Your starting point", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(
                    "Stress ${STRESS_WORDS[stress - 1].lowercase()} ($stress/5) · sleep ${SLEEP_WORDS[sleep - 1].lowercase()} ($sleep/5)" +
                        if (date.isNotBlank()) " · recorded $date" else "",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
            }
        }
        SectionCard {
            val m = metrics
            if (m == null || m.length() == 0) {
                Text("Log a few days and honest patterns appear here — no guesses.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            } else {
                (0 until m.length()).forEach { i ->
                    val row = m.getJSONObject(i)
                    val p = row.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.optString("label"), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(row.optString("value"), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(CardFill)) {
                            Box(Modifier.fillMaxWidth(p).height(8.dp).clip(RoundedCornerShape(99.dp))
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
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.22f))) {
                        Box(Modifier.fillMaxWidth(prog).height(6.dp).clip(RoundedCornerShape(99.dp))
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

@Composable
fun SoundsScreen(onBack: () -> Unit, onOpen: (String) -> Unit = {}) {
    val context = LocalContext.current
    val play: (String) -> Unit = { title -> Player.toggle(context, title) }
    var favs by remember { mutableStateOf(SleepFavs.all()) }
    val toggleFav: (String) -> Unit = { favs = SleepFavs.toggle(it) }
    SubPage("Sound library", "Sounds", onBack) {
        NowPlayingBar(onOpenPlayer = { onOpen("player") })
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

/** Full-screen player for the ambient bed: art, transport, sleep timer,
 * volume (mirrors the iOS sleep player; mixing arrives with real tracks). */
@Composable
fun PlayerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val title = Player.nowPlaying
    SubPage("Now playing", title ?: "Nothing playing", onBack) {
        // Centered art + transport (teammate player look), our tokens throughout.
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.fillMaxWidth(0.72f).height(240.dp).clip(RoundedCornerShape(26.dp))
                    .border(1.dp, LineStroke, RoundedCornerShape(26.dp)),
            ) {
                AsyncImage(
                    model = HeroImg.sleep, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
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
            Column(
                if (onOpenPlayer != null) Modifier.clickable { onOpenPlayer() } else Modifier,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Cyan)
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
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

@Composable
fun GamesScreen(onOpen: (String) -> Unit, onBack: () -> Unit) = SubPage("A tiny reset", "Calm games", onBack) {
    Text("Box breathing — inhale, hold, exhale, hold. Follow the orb for a minute.",
        style = MaterialTheme.typography.bodyMedium, color = TextSoft)
    BoxBreathing()
    Text("5-4-3-2-1 grounding — come back to the present through your senses.",
        style = MaterialTheme.typography.bodyMedium, color = TextSoft)
    Grounding()
    FeaturedGameCard(
        title = "Bubble pop",
        subtitle = "Pop gently — no timer, no losing. Just breathe and tap.",
        onOpen = { onOpen("bubblepop") },
    )
    ContentRow("Bubble wrap", "A fresh sheet, endlessly poppable", "Play", false,
        icon = Icons.Outlined.SportsEsports, onTap = { onOpen("bubblewrap") })
    ContentRow("Memory match", "Find the pairs, no clock", "Play", false,
        icon = Icons.Outlined.SportsEsports, onTap = { onOpen("memorymatch") })
    ContentRow("Pattern glow", "Watch the light, repeat it", "Play", false,
        icon = Icons.Outlined.SportsEsports, onTap = { onOpen("patternglow") })
    ContentRow("Zen ripples", "Tap the water, let it widen", "Play", false,
        icon = Icons.Outlined.SportsEsports, onTap = { onOpen("zenripples") })
    ContentRow("Gratitude garden", "A flower for every thank-you", "Play", false,
        icon = Icons.Outlined.SportsEsports, onTap = { onOpen("gratitude") })
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
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
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

/** The iOS 5-4-3-2-1 sensory grounding tool as a gentle stepper. */
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

/** A live box-breathing guide (4-4-4-4) with a phase-synced orb. */
@Composable
private fun BoxBreathing() {
    val phases = listOf("Breathe in", "Hold", "Breathe out", "Hold")
    var phase by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(4000); phase = (phase + 1) % phases.size }
    }
    val transition = rememberInfiniteTransition(label = "box")
    val scale by transition.animateFloat(
        0.8f, 1.15f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "s",
    )
    SectionCard {
        Text(phases[phase], style = MaterialTheme.typography.titleMedium,
            color = TextSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(130.dp).scale(scale).background(
                Brush.radialGradient(listOf(Color.White, Cyan, Periwinkle)), CircleShape))
        }
    }
}

@Composable
fun CrisisScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var contact by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { Api.trustedContact() }.onSuccess { tc ->
            contact = tc?.let { "${it.optString("name")} · ${it.optString("value")}" }
        }
    }
    // Static (offline-safe) directory — India first, then an international finder.
    val lines = listOf(
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
            // A letter in the value means it's the helpline-finder URL, not a phone number.
            val isUrl = number.any { it.isLetter() }
            val desc = if (isUrl) "Open helpline finder" else "Call $name $number"
            SectionCard(onClick = {
                val intent = if (isUrl) {
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://$number"))
                } else {
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                }
                runCatching { ctx.startActivity(intent) }
            }) {
                Row(
                    Modifier.fillMaxWidth().semantics { contentDescription = desc },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                        Text(number, style = MaterialTheme.typography.bodyMedium, color = Cyan)
                    }
                    Icon(
                        if (isUrl) Icons.AutoMirrored.Outlined.OpenInNew else Icons.Outlined.Call,
                        contentDescription = null, tint = Cyan, modifier = Modifier.size(22.dp),
                    )
                }
            }
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
