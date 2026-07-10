package com.cerebro.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Psychology
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cerebro.app.BuildConfig
import com.cerebro.app.audio.MediaUrls
import com.cerebro.app.audio.Player
import com.cerebro.app.net.Api
import com.cerebro.app.ui.theme.CardFill
import com.cerebro.app.ui.theme.Cyan
import kotlinx.coroutines.launch
import com.cerebro.app.ui.theme.LineStroke
import com.cerebro.app.ui.theme.Periwinkle
import com.cerebro.app.ui.theme.TextMuted
import com.cerebro.app.ui.theme.TextPrimary
import com.cerebro.app.ui.theme.TextSoft
import com.cerebro.app.ui.theme.Warm
import kotlinx.coroutines.delay
import kotlin.random.Random
import org.json.JSONArray

/** Page frame for a pushed sub-screen: back affordance + eyebrow + serif title. */
@Composable
internal fun SubPage(eyebrow: String, title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val horizontalPadding = pageHorizontalPadding()
    val verticalPadding = pageVerticalPadding()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, LineStroke, RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.055f)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("‹ Back", color = TextMuted)
        }
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        content()
    }
}

private val THUMB_GRADIENTS = listOf(
    listOf(Color(0xFF8A7BF0), Color(0xFF5B52C9)),
    listOf(Color(0xFF8FE6EE), Color(0xFF5B8FD0)),
    listOf(Color(0xFFF0A48C), Color(0xFFB86B8F)),
    listOf(Color(0xFFA68BFF), Color(0xFF6F7BF7)),
)

private fun thumbBrush(seed: String): Brush =
    Brush.linearGradient(THUMB_GRADIENTS[(seed.hashCode() and 0x7fffffff) % THUMB_GRADIENTS.size])

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
    SectionCard {
        val mod = Modifier.fillMaxWidth().let { if (onTap != null) it.clickable { onTap() } else it }
        Row(mod, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (meta.isNotBlank() && !subtitle.contains(meta, ignoreCase = true)) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = Periwinkle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (premium) Text("PREMIUM", style = MaterialTheme.typography.labelSmall, color = Warm)
                if (onFav != null && fav != null) {
                    Icon(
                        if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (fav) "Unfavourite $title" else "Favourite $title",
                        tint = Warm,
                        modifier = Modifier.size(22.dp).clickable { onFav() },
                    )
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
fun InsightsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    var headline by remember { mutableStateOf("Your week") }
    var summary by remember { mutableStateOf("") }
    var metrics by remember { mutableStateOf<JSONArray?>(null) }
    LaunchedEffect(Unit) {
        runCatching { Api.insightsWeekly() }.onSuccess {
            headline = it.optString("headline", "Your week")
            summary = it.optString("summary")
            metrics = it.optJSONArray("metrics")
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF6657AA), Color(0xFF2B1E5C), Color(0xFF120820)),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = pageHorizontalPadding(), vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("WEEKLY REPORT", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.42f))
                    Text("Weekly Insights", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(198.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFB36AAA).copy(alpha = 0.72f), Color(0xFF2B1D59)),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                    .padding(20.dp),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.25f))
                        .border(1.dp, Color.White.copy(alpha = 0.34f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text("A PATTERN TO LOOK FOR", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
                Column(
                    Modifier.align(Alignment.BottomStart),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(headline.ifBlank { "Calmer evenings" }, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Text(
                        summary.ifBlank { "Stress eased on days you journaled before bed - see if that holds this week." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.055f))))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val rows = insightRows(metrics)
                rows.forEach { row ->
                    InsightMetricRow(row.label, row.value, row.progress)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Sleep consistency", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Periwinkle.copy(alpha = 0.22f))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text("↗ Improving", style = MaterialTheme.typography.labelSmall, color = TextSoft)
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { onOpen("patterns") }
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Psychology, contentDescription = null, tint = TextSoft, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Pattern dashboard", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                    Text("Transparent AI memory - edit or delete any of it", style = MaterialTheme.typography.labelSmall, color = TextSoft, maxLines = 2)
                }
                Text(">", style = MaterialTheme.typography.titleMedium, color = TextMuted)
            }
            Spacer(Modifier.height(112.dp))
        }
    }
}

private data class InsightMetric(val label: String, val value: String, val progress: Float)

private fun insightRows(metrics: JSONArray?): List<InsightMetric> {
    if (metrics == null || metrics.length() == 0) {
        return listOf(
            InsightMetric("Calm sessions", "4", 0.40f),
            InsightMetric("Journal entries", "3", 0.50f),
        )
    }
    return (0 until minOf(metrics.length(), 2)).map { i ->
        val row = metrics.getJSONObject(i)
        InsightMetric(
            row.optString("label").ifBlank { if (i == 0) "Calm sessions" else "Journal entries" },
            row.optString("value").ifBlank { if (i == 0) "4" else "3" },
            row.optDouble("progress", if (i == 0) 0.40 else 0.50).toFloat().coerceIn(0f, 1f),
        )
    }
}

@Composable
private fun InsightMetricRow(label: String, value: String, progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.18f))) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(5.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Periwinkle),
            )
        }
    }
}

@Composable
fun ProgramsScreen(onBack: () -> Unit) {
    // Real enrollment (ref "PROGRAM · DAY X OF Y"): one journey at a time,
    // the day counts itself from the start date — nothing to fail.
    var rows by remember { mutableStateOf(listOf<Triple<String, String, String>>()) } // id, title, subtitle
    var active by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        runCatching { active = Api.activeProgram() }
        runCatching {
            val arr = Api.content("program")
            rows = (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                Triple(c.optString("id"), c.optString("title"), c.optString("subtitle"))
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF6657AA), Color(0xFF2B1E5C), Color(0xFF120820)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = pageHorizontalPadding(), vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("MULTI-DAY JOURNEYS", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.42f))
                    Text("Programs", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
                }
            }

            ProgramHero(active)
            Text("Start something new", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)

            val displayRows = rows.ifEmpty {
                listOf(
                    Triple("fallback-anxiety", "Taming anxiety", "10 days - CBT-based tools"),
                    Triple("fallback-morning", "Morning momentum", "5 days - start the day steady"),
                    Triple("fallback-kindness", "Kindness to self", "7 days - quiet the inner critic"),
                )
            }
            displayRows.take(6).forEachIndexed { index, (id, title, subtitle) ->
                ProgramStartRow(title, subtitle.ifBlank { "A few minutes a day" }, index) {
                    scope.launch {
                        if (!id.startsWith("fallback")) {
                            runCatching { Api.enrollProgram(id) }
                                .onSuccess { status = "Enrolled - day 1 starts now." }
                                .onFailure { status = it.message ?: "Couldn't enroll." }
                            refresh()
                        }
                    }
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
            Spacer(Modifier.height(112.dp))
        }
    }
}

@Composable
private fun ProgramHero(active: org.json.JSONObject?) {
    val day = active?.optInt("day")?.takeIf { it > 0 } ?: 3
    val days = active?.optInt("days")?.takeIf { it > 0 } ?: 7
    val title = active?.optString("title")?.ifBlank { "7 days to calmer sleep" } ?: "7 days to calmer sleep"
    Box(
        Modifier
            .fillMaxWidth()
            .height(178.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF5A8BA4), Color(0xFF2A246C), Color(0xFF1B1248))))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(20.dp),
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.28f))
                .border(1.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text("IN PROGRESS", style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Text("Day $day · tonight: “Loosening the jaw”", style = MaterialTheme.typography.bodyMedium, color = TextSoft)
            Box(Modifier.fillMaxWidth(0.68f).height(5.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth((day.toFloat() / days.coerceAtLeast(1)).coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(99.dp)).background(Color.White))
            }
        }
    }
}

@Composable
private fun ProgramStartRow(title: String, subtitle: String, index: Int, onStart: () -> Unit) {
    val colors = listOf(Color(0xFFA85F75), Color(0xFFCFAE50), Color(0xFFA3639C))
    Row(
        Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), colors[index % colors.size], Color(0xFF251841)))),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSoft)
        }
        Box(
            Modifier.clip(RoundedCornerShape(22.dp)).background(Color.White).clickable { onStart() }.padding(horizontal = 17.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Start", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF2B214E))
        }
    }
}

@Composable
fun SoundsScreen(onBack: () -> Unit, onOpen: (String) -> Unit = {}) {
    var items by remember { mutableStateOf<List<SoundTile>>(emptyList()) }
    var selectedChip by remember { mutableStateOf("All") }

    LaunchedEffect(Unit) {
        val all = mutableListOf<SoundTile>()
        listOf("sleep", "soundscape").forEach { kind ->
            runCatching {
                val arr = Api.content(kind)
                (0 until arr.length()).forEach { i ->
                    val c = arr.getJSONObject(i)
                    val title = c.optString("title")
                    MediaUrls.register(title, MediaUrls.resolve(c.optString("audio_url"), BuildConfig.API_BASE_URL))
                    all += SoundTile(
                        title = title,
                        subtitle = if (kind == "sleep") "Sleep story" else "Soundscape",
                        duration = c.optInt("duration_min"),
                        imageUrl = c.optString("image_url").ifBlank { soundImageFor(title, kind, i) },
                        kind = kind,
                    )
                }
            }
        }
        items = all.ifEmpty { fallbackSounds }
        SoundLibraryState.items = items
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF6657AA), Color(0xFF2B1E5C), Color(0xFF120820)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = pageHorizontalPadding(), vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("STORIES AND SOUNDSCAPES", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.42f))
                    Text("Sound Library", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SoundChip("All", selected = selectedChip == "All") { selectedChip = "All" }
                SoundChip("Sleep stories", selected = selectedChip == "Sleep stories") { selectedChip = "Sleep stories" }
                SoundChip("Soundscapes", selected = selectedChip == "Soundscapes") { selectedChip = "Soundscapes" }
                SoundChip("Focus", selected = selectedChip == "Focus") { selectedChip = "Focus" }
            }

            val visibleItems = items.filter {
                selectedChip == "All" ||
                    (selectedChip == "Sleep stories" && it.kind == "sleep") ||
                    (selectedChip == "Soundscapes" && it.kind == "soundscape") ||
                    (selectedChip == "Focus" && it.kind == "focus")
            }.ifEmpty { items }

            SoundLibraryState.items = visibleItems.ifEmpty { fallbackSounds }
            visibleItems.take(8).chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { item ->
                        SoundTileCard(item, modifier = Modifier.weight(1f)) {
                            SoundLibraryState.items = visibleItems
                            Player.setState(item.title, false)
                            onOpen("player")
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(112.dp))
        }
    }
}

private data class SoundTile(
    val title: String,
    val subtitle: String,
    val duration: Int,
    val imageUrl: String,
    val kind: String,
)

@Composable
private fun SoundChip(label: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.10f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (selected) Color(0xFF2B214E) else TextPrimary)
    }
}

@Composable
private fun SoundTileCard(item: SoundTile, modifier: Modifier = Modifier, onPlay: () -> Unit) {
    Column(modifier.clickable { onPlay() }, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(124.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(thumbBrush(item.title)),
        ) {
            AsyncImage(model = item.imageUrl.ifBlank { soundImageFor(item.title, item.kind, 0) }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF2B214E),
                    modifier = Modifier.size(20.dp).clickable { onPlay() },
                )
            }
        }
        Text(item.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val duration = if (item.duration > 0) " · ${item.duration} min" else if (item.kind == "soundscape") " · 8 hr" else ""
        Text("${item.subtitle}$duration", style = MaterialTheme.typography.labelSmall, color = TextSoft, maxLines = 1)
    }
}

private val fallbackSounds = listOf(
    SoundTile("Rain over quiet hills", "Sleep story", 18, HeroImg.calm, "sleep"),
    SoundTile("Deep delta drift", "Soundscape", 0, HeroImg.sleep, "soundscape"),
    SoundTile("The lighthouse keeper", "Sleep story", 25, HeroImg.journal, "sleep"),
    SoundTile("Warm rain on canvas", "Soundscape", 0, HeroImg.mood, "soundscape"),
)

private fun soundImageFor(title: String, kind: String, index: Int): String = when {
    title.contains("rain", ignoreCase = true) -> HeroImg.calm
    title.contains("delta", ignoreCase = true) -> HeroImg.sleep
    title.contains("lighthouse", ignoreCase = true) -> HeroImg.journal
    title.contains("warm", ignoreCase = true) -> HeroImg.mood
    kind == "sleep" -> if (index % 2 == 0) HeroImg.calm else HeroImg.sleep
    else -> if (index % 2 == 0) HeroImg.mood else HeroImg.sleep
}

private object SoundLibraryState {
    var items by mutableStateOf(fallbackSounds)
    var favorites by mutableStateOf(setOf<String>())

    fun current(title: String): SoundTile = items.firstOrNull { it.title == title }
        ?: fallbackSounds.firstOrNull { it.title == title }
        ?: fallbackSounds.first()

    fun adjacent(title: String, step: Int): SoundTile {
        val list = items.ifEmpty { fallbackSounds }
        val index = list.indexOfFirst { it.title == title }.takeIf { it >= 0 } ?: 0
        val next = (index + step + list.size) % list.size
        return list[next]
    }

    fun toggleFavorite(title: String) {
        favorites = if (title in favorites) favorites - title else favorites + title
    }
}

private fun soundDurationSeconds(durationMin: Int): Int {
    val minutes = if (durationMin > 0) durationMin else 8 * 60
    return minutes * 60
}

private fun formatElapsed(progress: Float, durationMin: Int): String {
    val seconds = (soundDurationSeconds(durationMin) * progress.coerceIn(0f, 1f)).toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private fun formatRemaining(progress: Float, durationMin: Int): String {
    val total = soundDurationSeconds(durationMin)
    val elapsed = (total * progress.coerceIn(0f, 1f)).toInt()
    val left = (total - elapsed).coerceAtLeast(0)
    return "-${left / 60}:${(left % 60).toString().padStart(2, '0')}"
}

@Composable
private fun PlayerTimeline(progress: Float, onProgressChange: (Float) -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(18.dp),
    ) {
        val density = LocalDensity.current
        val timelineWidth = maxWidth
        val widthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val clamped = progress.coerceIn(0f, 1f)
        val updateFromX: (Float) -> Unit = { x -> onProgressChange((x / widthPx).coerceIn(0f, 1f)) }

        Box(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .pointerInput(widthPx) {
                    detectTapGestures { offset -> updateFromX(offset.x) }
                }
                .pointerInput(widthPx) {
                    detectDragGestures(
                        onDragStart = { offset -> updateFromX(offset.x) },
                        onDrag = { change, _ -> updateFromX(change.position.x) },
                    )
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(clamped)
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (timelineWidth - 13.dp) * clamped)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
fun PlayerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val title = Player.nowPlaying ?: "Rain over quiet hills"
    val current = SoundLibraryState.current(title)
    var progress by remember(title) { mutableStateOf(0.34f) }

    LaunchedEffect(title) {
        if (!Player.isPlaying || Player.nowPlaying != title) Player.play(context, title)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF6657AA), Color(0xFF2B1E5C), Color(0xFF120820)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = pageHorizontalPadding(), vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Text(
                    "NOW PLAYING",
                    modifier = Modifier.weight(1f).padding(end = 44.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.42f),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(30.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.64f)
                    .height(216.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(thumbBrush(title)),
            ) {
                AsyncImage(
                    model = current.imageUrl.ifBlank { soundImageFor(current.title, current.kind, 0) },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(title, style = MaterialTheme.typography.displaySmall, color = TextPrimary, textAlign = TextAlign.Center)
            Text("${current.subtitle} · Naomi", style = MaterialTheme.typography.bodyMedium, color = TextSoft)

            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth()) {
                PlayerTimeline(progress = progress, onProgressChange = { progress = it })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatElapsed(progress, current.duration), style = MaterialTheme.typography.labelSmall, color = TextSoft)
                    Text(formatRemaining(progress, current.duration), style = MaterialTheme.typography.labelSmall, color = TextSoft)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(0.56f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable {
                            val previous = SoundLibraryState.adjacent(title, -1)
                            progress = 0f
                            Player.play(context, previous.title)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("<<", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                }
                Box(
                    Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { if (Player.isPlaying) Player.pause(context) else Player.play(context, title) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (Player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF2B214E),
                        modifier = Modifier.size(30.dp),
                    )
                }
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable {
                            val next = SoundLibraryState.adjacent(title, 1)
                            progress = 0f
                            Player.play(context, next.title)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(">>", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                }
            }

            Spacer(Modifier.height(28.dp))
            Row(
                Modifier.fillMaxWidth(0.38f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "+",
                    modifier = Modifier.clip(CircleShape).clickable { Player.cycleTimer(context) }.padding(8.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextSoft,
                )
                Text(
                    "♪",
                    modifier = Modifier.clip(CircleShape).clickable { Player.setVolume(context, if (Player.volume > 0.5f) 0.35f else 1f) }.padding(8.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextSoft,
                )
                Icon(
                    if (title in SoundLibraryState.favorites) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (title in SoundLibraryState.favorites) Warm else TextSoft,
                    modifier = Modifier.size(30.dp).clip(CircleShape).clickable { SoundLibraryState.toggleFavorite(title) }.padding(3.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(108.dp))
        }
    }
}

@Composable
private fun LegacyPlayerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val title = Player.nowPlaying
    SubPage("Now playing", title ?: "Nothing playing", onBack) {
        Box(
            Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(22.dp)),
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
                style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        } else {
            Text(
                if (MediaUrls.urlFor(title).isBlank())
                    "Continuous ambient bed · this title has no narration yet."
                else "Narrated track · streams from your library.",
                style = MaterialTheme.typography.labelSmall, color = TextMuted,
            )
            PrimaryButton(
                text = if (Player.isPlaying) "Pause" else "Play",
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (Player.isPlaying) Player.pause(context) else Player.toggle(context, title)
            }
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
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).let { if (onOpenPlayer != null) it.clickable { onOpenPlayer() } else it },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("NOW PLAYING · AMBIENT BED", style = MaterialTheme.typography.labelSmall, color = Cyan)
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
fun GamesScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF6657AA), Color(0xFF2B1E5C), Color(0xFF120820)),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = pageHorizontalPadding(), vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("PLAYFUL RESETS", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.38f))
                    Text("Calm Games", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
                }
            }

            FeaturedGameCard { onOpen("bubblepop") }
            CalmGameRow("Bubble wrap", "A fresh sheet, endlessly poppable", Color(0xFF3F725F), "bubblewrap", onOpen)
            CalmGameRow("Memory match", "Find the pairs, no clock", Color(0xFF65529B), "memorymatch", onOpen)
            CalmGameRow("Pattern glow", "Watch the light, repeat it", Color(0xFF5C4AA0), "patternglow", onOpen)
            CalmGameRow("Zen ripples", "Tap the water, let it widen", Color(0xFF3E6F86), "zenripples", onOpen)
            CalmGameRow("Gratitude garden", "A flower for every thank-you", Color(0xFF6A548B), "gratitude", onOpen)
            Spacer(Modifier.height(112.dp))
        }
    }
}

@Composable
private fun FeaturedGameCard(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF5043A5), Color(0xFF2E2866))))
            .clickable { onClick() }
            .padding(26.dp),
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.25f))
                .border(1.dp, Color.White.copy(alpha = 0.34f), RoundedCornerShape(50))
                .padding(horizontal = 15.dp, vertical = 6.dp),
        ) {
            Text("PLAYABLE", style = MaterialTheme.typography.labelSmall, color = Color.Black)
        }

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-14).dp, y = (-10).dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White, Color(0xFFB5A7FF), Color(0xFF6D5DDB)))),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = 24.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White, Color(0xFFFFA8AA), Color(0xFFD56E8A)))),
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-44).dp, y = 0.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White, Color(0xFF9EE8E9), Color(0xFF65A7C3)))),
        )

        Column(
            Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text("Bubble Pop", style = MaterialTheme.typography.headlineSmall, color = Color.Black)
            Text(
                "Pop gently - no timer, no losing. Just breathe and tap.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
        }
    }
}

@Composable
private fun CalmGameRow(title: String, subtitle: String, iconColor: Color, route: String, onOpen: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.055f)),
                ),
            )
            .clickable { onOpen(route) }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.SportsEsports, contentDescription = null, tint = TextSoft, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
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
    val hues = listOf(Color(0xFFD9C28D), Color(0xFFFF9DB0), Color(0xFF9D8CFF))
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
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF6657AA), Color(0xFF2B1E5C), Color(0xFF120820)),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = pageHorizontalPadding(), vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("BUBBLE POP", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.38f))
                        Text("Pop & breathe", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$score", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
                    Text("POPPED", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.66f))
                }
            }

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(414.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.045f)),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.11f), RoundedCornerShape(22.dp)),
            ) {
                val w = maxWidth
                val h = maxHeight
                bubbles.forEach { b ->
                    Box(
                        Modifier
                            .offset(x = w * b.x, y = h * b.y)
                            .size(b.size.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color.White.copy(alpha = 0.96f), b.hue, b.hue.copy(alpha = 0.78f)),
                                ),
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                bubbles = bubbles.filterNot { it.id == b.id }
                                score++
                            },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "Tap a bubble as you breathe out.\nThere's no way to lose.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSoft,
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(24.dp))
                        .clickable {
                            bubbles = emptyList()
                            score = 0
                        }
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Reset", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
            }
            Spacer(Modifier.height(112.dp))
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
        SectionCard {
            Text("If you're in immediate danger, please reach out now — you deserve support.",
                style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        }
        lines.forEach { (name, number) ->
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(number, style = MaterialTheme.typography.titleMedium, color = Cyan)
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
