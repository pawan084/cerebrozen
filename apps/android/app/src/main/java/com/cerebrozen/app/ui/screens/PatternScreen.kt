package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal data class Learned(val statement: String, val basis: String)

/** A row the user wrote or approved — unlike [Learned], it has an id, so it
 * can be edited and deleted individually. */
internal data class Remembered(val id: String, val body: String, val source: String)

internal fun parsePatterns(payload: JSONObject): List<Learned> {
    val arr = payload.optJSONArray("patterns") ?: return emptyList()
    return (0 until arr.length()).map { i ->
        val p = arr.getJSONObject(i)
        Learned(p.optString("statement"), p.optString("basis"))
    }
}

/** A practice suggested because of a mined pattern. [reason] is the pattern
 * statement verbatim — never dropped, because a suggestion with no visible
 * basis is what this screen exists to avoid. */
internal data class Suggested(
    val id: String,
    val title: String,
    val body: String,
    val reason: String,
)

internal fun parseRecommendations(arr: JSONArray): List<Suggested> =
    (0 until arr.length()).mapNotNull { i ->
        val r = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = r.optString("id")
        if (id.isBlank()) null
        else Suggested(id, r.optString("title"), r.optString("body"), r.optString("reason"))
    }

internal fun parseMemories(arr: JSONArray): List<Remembered> =
    (0 until arr.length()).mapNotNull { i ->
        val m = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = m.optString("id")
        if (id.isBlank()) null
        else Remembered(id, m.optString("body"), m.optString("source"))
    }

/** Ref PATTERN DASHBOARD: "everything CereBro has learned about you —
 * visible, editable, and yours to delete." Statements come from
 * /insights/patterns with their supporting counts; deletion is real. */
@Composable
fun PatternScreen(onBack: () -> Unit) {
    var learned by remember { mutableStateOf<List<Learned>?>(null) }
    var hiddenCount by remember { mutableIntStateOf(0) }
    var remembered by remember { mutableStateOf<List<Remembered>?>(null) }
    var suggestions by remember { mutableStateOf<List<Suggested>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var patternsError by remember { mutableStateOf<String?>(null) }
    var memoryError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var confirming by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val loadFailed = stringResource(R.string.patterns_error_fallback)
    val memoryOff = stringResource(R.string.patterns_memory_off)

    LaunchedEffect(reload) {
        patternsError = null
        learned = null
        runCatching { Api.patterns() }
            .onSuccess {
                learned = parsePatterns(it)
                hiddenCount = it.optInt("suppressed")
            }
            .onFailure { patternsError = it.message ?: loadFailed }
        runCatching { parseMemories(Api.memories()) }
            .onSuccess { remembered = it }
            .onFailure { remembered = emptyList() }
        runCatching { parseRecommendations(Api.recommendations()) }
            .onSuccess { suggestions = it }
            .onFailure { suggestions = emptyList() }
    }

    // Two-tap delete: fall back out of the armed state if left untouched.
    LaunchedEffect(confirming) {
        if (confirming) { delay(4000); confirming = false }
    }

    SubPage(stringResource(R.string.patterns_eyebrow), stringResource(R.string.patterns_title), onBack) {
        Text(
            stringResource(R.string.patterns_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
        )
        // Computed patterns: hideable, never editable — there is no row to edit.
        SectionCard {
            Text(stringResource(R.string.patterns_noticed_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(stringResource(R.string.patterns_noticed_body),
                style = MaterialTheme.typography.bodySmall, color = TextMuted)
            when {
                patternsError != null -> {
                    Text(patternsError!!, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    TextButton(onClick = { reload++ }) { Text(stringResource(R.string.common_try_again), color = Periwinkle) }
                }
                learned == null -> Text(stringResource(R.string.patterns_loading),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                learned!!.isEmpty() -> Text(
                    stringResource(R.string.patterns_empty),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
                else -> learned!!.forEachIndexed { i, p ->
                    MemoryRow(i, p.statement, p.basis) {
                        scope.launch {
                            runCatching { Api.suppressPattern(p.statement) }
                                .onSuccess { reload++ }
                                .onFailure { memoryError = it.message ?: memoryOff }
                        }
                    }
                }
            }
            if (hiddenCount > 0) {
                Text(stringResource(R.string.patterns_hidden_count, hiddenCount),
                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }

        // Suggestions sit directly under the patterns that produced them, so
        // the basis is on the same screen as the advice.
        if (suggestions.isNotEmpty()) {
            SectionCard {
                Text(stringResource(R.string.recs_title),
                    style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(stringResource(R.string.recs_body),
                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
                suggestions.forEach { rec ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(rec.title, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(rec.body, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        Text(stringResource(R.string.recs_because, rec.reason),
                            style = MaterialTheme.typography.bodySmall, color = Cyan)
                        Row(
                            Modifier.heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { Api.resolveRecommendation(rec.id, true) }
                                    reload++
                                }
                            }) { Text(stringResource(R.string.recs_accept), color = Periwinkle) }
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { Api.resolveRecommendation(rec.id, false) }
                                    reload++
                                }
                            }) { Text(stringResource(R.string.recs_dismiss), color = TextMuted) }
                        }
                    }
                }
            }
        }

        // The user's own notes: real rows, so real edit + delete.
        SectionCard {
            Text(stringResource(R.string.patterns_saved_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(stringResource(R.string.patterns_saved_body),
                style = MaterialTheme.typography.bodySmall, color = TextMuted)
            memoryError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Danger)
            }
            val sourceLabels = mapOf(
                "manual" to stringResource(R.string.patterns_source_manual),
                "confirmed" to stringResource(R.string.patterns_source_confirmed),
                "onboarding" to stringResource(R.string.patterns_source_onboarding),
            )
            when {
                remembered == null -> Text(stringResource(R.string.patterns_loading),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                remembered!!.isEmpty() -> Text(stringResource(R.string.patterns_saved_empty),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                else -> remembered!!.forEach { m ->
                    if (editingId == m.id) {
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.patterns_edit)) },
                            singleLine = false,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                val body = editText.trim()
                                if (body.isEmpty()) return@TextButton
                                scope.launch {
                                    runCatching { Api.editMemory(m.id, body) }
                                        .onSuccess { editingId = null; reload++ }
                                        .onFailure { memoryError = it.message ?: memoryOff }
                                }
                            }) { Text(stringResource(R.string.patterns_save), color = Periwinkle) }
                            TextButton(onClick = { editingId = null }) {
                                Text(stringResource(R.string.patterns_cancel), color = TextMuted)
                            }
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.body, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Text(sourceLabels[m.source] ?: m.source,
                                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                            TextButton(onClick = { editingId = m.id; editText = m.body }) {
                                Text(stringResource(R.string.patterns_edit), color = Periwinkle)
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { Api.deleteOneMemory(m.id) }
                                        .onSuccess { reload++ }
                                        .onFailure { memoryError = it.message ?: memoryOff }
                                }
                            }) { Text(stringResource(R.string.patterns_delete_one), color = Danger) }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.patterns_add_hint)) },
                singleLine = true,
            )
            TextButton(
                enabled = draft.isNotBlank(),
                onClick = {
                    val body = draft.trim()
                    scope.launch {
                        runCatching { Api.addMemory(body) }
                            .onSuccess { draft = ""; memoryError = null; reload++ }
                            .onFailure { memoryError = it.message ?: memoryOff }
                    }
                },
            ) { Text(stringResource(R.string.patterns_add), color = Periwinkle) }
        }
        SectionCard {
            Text(stringResource(R.string.patterns_delete_title), style = MaterialTheme.typography.titleMedium, color = Danger)
            Text(
                stringResource(R.string.patterns_delete_body),
                style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            )
            val clearedTemplate = stringResource(R.string.patterns_cleared)
            val deleteFailed = stringResource(R.string.patterns_delete_failed)
            TextButton(
                modifier = Modifier.fillMaxWidth().then(
                    if (confirming) Modifier.background(Danger.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    else Modifier,
                ),
                onClick = {
                    if (!confirming) { confirming = true; return@TextButton }
                    scope.launch {
                        runCatching { Api.deleteMemory() }
                            .onSuccess {
                                confirming = false
                                learned = emptyList()
                                remembered = emptyList()
                                status = clearedTemplate.format(it.optInt("chat_messages"), it.optInt("insights"), it.optInt("memories"))
                            }
                            .onFailure { status = it.message ?: deleteFailed }
                    }
                },
            ) {
                Text(
                    if (confirming) stringResource(R.string.patterns_confirm) else stringResource(R.string.patterns_delete_title),
                    color = Danger,
                )
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
        }
    }
}

/** One learned statement, rendered as a soft lavender gradient row (teammate look,
 * rebuilt on our tokens). Shows the real statement and, when present, its supporting
 * count [basis]. Still no EDIT affordance — a pattern is computed per request and has
 * no row to rewrite — but it can be hidden, which is the honest equivalent.
 *
 * The hide control is the point. This composable used to ACCEPT [onHide], the caller
 * used to pass a working one wired to `POST /users/me/memory/suppress-pattern`, and
 * the body rendered nothing that could ever call it — while the section above
 * promised "Hide one and it stops being shown or used". A capability the backend
 * supported, the client had wired, the copy advertised, and the user could not
 * reach. It stayed invisible because the row only renders when patterns exist, and
 * they need weeks of check-ins to appear.
 *
 * Two taps, because it does not come back: the tombstone's source is not in
 * `EDITABLE_SOURCES`, so the server refuses to delete it and the only undo really
 * is clearing all memory — exactly what the copy says.
 */
@Composable
private fun MemoryRow(index: Int, statement: String, basis: String, onHide: () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    var armed by remember(statement) { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(4000); armed = false } }
    val hideCd = stringResource(
        if (armed) R.string.patterns_hide_confirm_cd else R.string.patterns_hide_cd,
        statement,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)   // a11y: >= 48dp target
            .appear(index)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(Periwinkle.copy(alpha = 0.18f), Iris.copy(alpha = 0.10f)),
                ),
            )
            .border(1.dp, LineStroke, shape)
            .padding(start = 16.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                statement,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            if (basis.isNotBlank()) {
                Text(basis, style = MaterialTheme.typography.bodySmall, color = Cyan)
            }
        }
        TextButton(
            onClick = { if (armed) onHide() else armed = true },
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = hideCd },
        ) {
            Text(
                stringResource(if (armed) R.string.patterns_hide_confirm else R.string.patterns_hide),
                style = MaterialTheme.typography.bodySmall,
                color = if (armed) Danger else TextMuted,
            )
        }
    }
}
