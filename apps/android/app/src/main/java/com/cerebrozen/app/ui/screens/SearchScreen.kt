package com.cerebrozen.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cerebrozen.app.BuildConfig
import com.cerebrozen.app.R
import com.cerebrozen.app.audio.MediaUrls
import com.cerebrozen.app.audio.Player
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import org.json.JSONObject

// The whole served catalogue as the search pool (ref SEARCH route; mirrors the
// iOS SearchView). Playable kinds start the ambient bed; programs are listed
// with their kind label.

private val SEARCH_KINDS = listOf("soundscape", "sleep", "meditation", "program", "wind_down")

internal data class SearchItem(val title: String, val subtitle: String, val kind: String, val duration: Int, val imageUrl: String, val audioUrl: String = "")

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
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val all = mutableListOf<SearchItem>()
        var failures = 0
        SEARCH_KINDS.forEach { kind ->
            runCatching {
                val arr = Api.content(kind)
                (0 until arr.length()).forEach { i ->
                    val c: JSONObject = arr.getJSONObject(i)
                    val audio = MediaUrls.resolve(c.optString("audio_url"), BuildConfig.API_BASE_URL)
                    MediaUrls.register(c.optString("title"), audio)
                    all += SearchItem(
                        c.optString("title"), c.optString("subtitle"), kind,
                        c.optInt("duration_min"), c.optString("image_url"), audio,
                    )
                }
            }.onFailure { failures++ }
        }
        pool = all
        loadError = failures == SEARCH_KINDS.size
        loading = false
    }

    PremiumSubPage(stringResource(R.string.search_eyebrow), stringResource(R.string.search_title), onBack) {
        AppTextField(
            query, { query = it }, stringResource(R.string.search_field_label), singleLine = true,
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.search_clear_cd), tint = TextMuted)
                    }
                }
            } else null,
        )
        val hits = filterCatalogue(pool, query)
        when {
            query.trim().length < 2 -> PremiumStateCard(
                icon = Icons.Outlined.Search,
                message = stringResource(R.string.search_hint),
                accent = Periwinkle,
            )
            loading -> PremiumStateCard(
                icon = Icons.Outlined.AutoAwesome,
                message = stringResource(R.string.search_loading),
            )
            loadError -> PremiumStateCard(
                icon = Icons.Outlined.CloudOff,
                message = stringResource(R.string.search_error),
                accent = com.cerebrozen.app.ui.theme.Danger,
            )
            hits.isEmpty() -> PremiumStateCard(
                icon = Icons.Outlined.Search,
                message = stringResource(R.string.search_no_match, query.trim()),
                accent = Periwinkle,
            )
            else -> {
                Text(stringResource(R.string.search_results), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
                hits.take(20).forEach { item ->
                    val playable = item.kind in listOf("soundscape", "sleep", "meditation")
                    ContentRow(
                        item.title, item.subtitle,
                        // The kind is a backend content-kind value, shown as-is.
                        (if (item.duration > 0) stringResource(R.string.common_minutes, item.duration) + " · " else "") +
                            item.kind.replace('_', ' '),
                        false,
                        playing = Player.nowPlaying == item.title && Player.isPlaying,
                        kind = item.kind,   // W21: the kind picks the art family/motif
                        imageUrl = item.imageUrl,
                        onTap = if (playable) ({ Player.toggle(context, item.title, item.kind) }) else null,
                    )
                }
                if (hits.size > 20) {
                    Text(stringResource(R.string.search_refine),
                        style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }
}
