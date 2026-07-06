package com.cerebrozen.app.ui.screens

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.audio.Player
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Warm
import kotlinx.coroutines.delay
import org.json.JSONArray

/** Page frame for a pushed sub-screen: back affordance + eyebrow + serif title. */
@Composable
internal fun SubPage(eyebrow: String, title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
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

@Composable
internal fun ContentRow(
    title: String,
    subtitle: String,
    meta: String,
    premium: Boolean,
    playing: Boolean = false,
    onTap: (() -> Unit)? = null,
) {
    SectionCard {
        val inner = Modifier.fillMaxWidth().let { if (onTap != null) it.clickable { onTap() } else it }
        Column(inner, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (premium) Text("PREMIUM", style = MaterialTheme.typography.labelSmall, color = Warm)
                    if (onTap != null) Text(if (playing) "❚❚" else "▶", style = MaterialTheme.typography.titleMedium, color = Cyan)
                }
            }
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        }
    }
}

/** Load a content kind and render it as a list; shows honest empty/error states. */
@Composable
internal fun ContentList(kind: String, metaLabel: (Int) -> String, onItemTap: ((String) -> Unit)? = null) {
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
            ContentRow(
                title, c.optString("subtitle"),
                metaLabel(c.optInt("duration_min")), c.optBoolean("premium"),
                playing = Player.nowPlaying == title && Player.isPlaying,
                onTap = onItemTap?.let { { it(title) } },
            )
        }
    }
}

@Composable
fun InsightsScreen(onBack: () -> Unit) {
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
    SubPage("Insights · this week", headline, onBack) {
        if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        SectionCard {
            val m = metrics
            if (m == null || m.length() == 0) {
                Text("Log a few days and honest patterns appear here — no guesses.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            } else {
                (0 until m.length()).forEach { i ->
                    val row = m.getJSONObject(i)
                    val p = row.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.optString("label"), style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                            Text(row.optString("value"), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(CardFill)) {
                            Box(Modifier.fillMaxWidth(p).height(8.dp).clip(RoundedCornerShape(99.dp))
                                .background(Brush.horizontalGradient(listOf(Periwinkle, Cyan))))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        Text("First-party — computed on your own data, never sold or shared.",
            style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
fun ProgramsScreen(onBack: () -> Unit) = SubPage("Guided journeys", "Programs", onBack) {
    Text("Multi-day paths to a calmer baseline. Start any time; go at your pace.",
        style = MaterialTheme.typography.bodyMedium, color = TextSoft)
    ContentList("program", { d -> if (d > 0) "$d min a day" else "A few minutes a day" })
}

@Composable
fun SoundsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val play: (String) -> Unit = { title -> Player.toggle(context, title) }
    SubPage("Sound library", "Sounds", onBack) {
        NowPlayingBar()
        Text("Soundscapes and sleep stories to slow a racing mind.",
            style = MaterialTheme.typography.bodyMedium, color = TextSoft)
        ContentList("soundscape", { d -> if (d > 0) "$d min" else "Continuous ambient" }, onItemTap = play)
        Text("Sleep stories", style = MaterialTheme.typography.titleMedium, color = TextSoft)
        ContentList("sleep", { d -> if (d > 0) "$d min" else "Sleep story" }, onItemTap = play)
        Text("Tap to play a calming ambient bed — narrated stories arrive with the content pipeline.",
            style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

/** A compact transport shown whenever something is playing. */
@Composable
internal fun NowPlayingBar() {
    val context = LocalContext.current
    val title = Player.nowPlaying ?: return
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("NOW PLAYING · AMBIENT BED", style = MaterialTheme.typography.labelSmall, color = Cyan)
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
            }
            TextButton(onClick = { if (Player.isPlaying) Player.pause() else Player.toggle(context, title) }) {
                Text(if (Player.isPlaying) "Pause" else "Play", color = Periwinkle)
            }
        }
    }
}

@Composable
fun GamesScreen(onBack: () -> Unit) = SubPage("A tiny reset", "Calm games", onBack) {
    Text("Box breathing — inhale, hold, exhale, hold. Follow the orb for a minute.",
        style = MaterialTheme.typography.bodyMedium, color = TextSoft)
    BoxBreathing()
    ContentRow("Bubble pop", "Pop to release tension", "Coming soon", false)
    ContentRow("Color drift", "Let your eyes soften", "Coming soon", false)
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
