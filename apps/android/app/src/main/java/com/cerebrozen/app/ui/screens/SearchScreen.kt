package com.cerebro.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cerebro.app.BuildConfig
import com.cerebro.app.audio.MediaUrls
import com.cerebro.app.audio.Player
import com.cerebro.app.net.Api
import com.cerebro.app.ui.theme.Periwinkle
import com.cerebro.app.ui.theme.TextMuted
import com.cerebro.app.ui.theme.TextPrimary
import com.cerebro.app.ui.theme.TextSoft
import org.json.JSONObject

private val SEARCH_KINDS = listOf("soundscape", "sleep", "meditation", "program", "wind_down")

internal data class SearchItem(
    val title: String,
    val subtitle: String,
    val kind: String,
    val duration: Int,
    val imageUrl: String,
    val audioUrl: String = "",
)

internal fun filterCatalogue(items: List<SearchItem>, query: String): List<SearchItem> {
    val q = query.trim()
    if (q.length < 2) return emptyList()
    return items.filter {
        it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true) ||
            it.kind.contains(q, ignoreCase = true)
    }
}

@Composable
fun SearchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var pool by remember { mutableStateOf(listOf<SearchItem>()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val all = mutableListOf<SearchItem>()
        SEARCH_KINDS.forEach { kind ->
            runCatching {
                val arr = Api.content(kind)
                (0 until arr.length()).forEach { i ->
                    val c: JSONObject = arr.getJSONObject(i)
                    val audio = MediaUrls.resolve(c.optString("audio_url"), BuildConfig.API_BASE_URL)
                    MediaUrls.register(c.optString("title"), audio)
                    all += SearchItem(
                        c.optString("title"),
                        c.optString("subtitle"),
                        kind,
                        c.optInt("duration_min"),
                        c.optString("image_url"),
                        audio,
                    )
                }
            }
        }
        pool = all
    }

    val hits = filterCatalogue(pool, query)
    val suggested = if (query.trim().length >= 2) hits.take(20) else pool.take(3).ifEmpty { fallbackSuggestions }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF6D5EB0), Color(0xFF2D215F), Color(0xFF120821)),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = pageHorizontalPadding(), vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SearchHeader(onBack)
            SearchField(value = query, onValueChange = { query = it })
            RecentChips(onPick = { query = it })
            Text("SUGGESTED", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f))

            if (query.trim().length >= 2 && hits.isEmpty()) {
                Text("Nothing matches \"${query.trim()}\" - try a different word.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            } else {
                suggested.forEach { item ->
                    SuggestedRow(
                        item = item,
                        onTap = {
                            if (item.kind in playableKinds) {
                                Player.toggle(context, item.title)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.13f))
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("FIND YOUR CALM", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.34f))
            Text("Search", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.17f), RoundedCornerShape(15.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isBlank()) {
                Text("Search calm, sleep, focus...", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    fontWeight = FontWeight.Normal,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RecentChips(onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("RECENT", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchChip("Sleep story", onPick)
            SearchChip("Breathing", onPick)
            SearchChip("Gratitude", onPick)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchChip("Focus", onPick)
        }
    }
}

@Composable
private fun SearchChip(label: String, onPick: (String) -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.11f))
            .clickable { onPick(label) }
            .padding(horizontal = 15.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

@Composable
private fun SuggestedRow(item: SearchItem, onTap: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .clickable { onTap() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        SearchThumb(item)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = Color.Black, maxLines = 1)
            Text(rowSubtitle(item), style = MaterialTheme.typography.labelSmall, color = TextSoft, maxLines = 1)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SearchThumb(item: SearchItem) {
    val fallbackIcon = when (item.kind) {
        "sleep" -> Icons.Outlined.Bedtime
        "program" -> Icons.Outlined.CalendarMonth
        "wind_down" -> Icons.Outlined.SelfImprovement
        else -> Icons.Outlined.GraphicEq
    }
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(9.dp)).background(Periwinkle.copy(alpha = 0.24f)), contentAlignment = Alignment.Center) {
        if (item.imageUrl.isNotBlank()) {
            AsyncImage(model = item.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(fallbackIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

private val playableKinds = setOf("soundscape", "sleep", "meditation")

private fun rowSubtitle(item: SearchItem): String {
    val kind = when (item.kind) {
        "meditation" -> "Breathing"
        "wind_down" -> "Journal prompt"
        "soundscape" -> "Soundscape"
        else -> item.kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
    return if (item.duration > 0) "$kind - ${item.duration} min" else kind
}

private val fallbackSuggestions = listOf(
    SearchItem("Rain over quiet hills", "Sleep story", "sleep", 18, HeroImg.calm),
    SearchItem("3-minute breath", "Stress reset", "meditation", 3, HeroImg.mood),
    SearchItem("Release the day", "Journal prompt", "wind_down", 0, HeroImg.journal),
)
