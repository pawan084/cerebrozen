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
import org.json.JSONObject

internal data class Learned(val statement: String, val basis: String)

internal fun parsePatterns(payload: JSONObject): List<Learned> {
    val arr = payload.optJSONArray("patterns") ?: return emptyList()
    return (0 until arr.length()).map { i ->
        val p = arr.getJSONObject(i)
        Learned(p.optString("statement"), p.optString("basis"))
    }
}

/** Ref PATTERN DASHBOARD: "everything CereBro has learned about you —
 * visible, editable, and yours to delete." Statements come from
 * /insights/patterns with their supporting counts; deletion is real. */
@Composable
fun PatternScreen(onBack: () -> Unit) {
    var learned by remember { mutableStateOf<List<Learned>?>(null) }
    var patternsError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var confirming by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val loadFailed = stringResource(R.string.patterns_error_fallback)

    LaunchedEffect(reload) {
        patternsError = null
        learned = null
        runCatching { parsePatterns(Api.patterns()) }
            .onSuccess { learned = it }
            .onFailure { patternsError = it.message ?: loadFailed }
    }

    // Two-tap delete: fall back out of the armed state if left untouched.
    LaunchedEffect(confirming) {
        if (confirming) { delay(4000); confirming = false }
    }

    PremiumSubPage(stringResource(R.string.patterns_eyebrow), stringResource(R.string.patterns_title), onBack) {
        Text(
            stringResource(R.string.patterns_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
        )
        SectionCard {
            Text(stringResource(R.string.patterns_remembers_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
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
                    MemoryRow(i, p.statement, p.basis)
                }
            }
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
                                status = clearedTemplate.format(it.optInt("chat_messages"), it.optInt("insights"))
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
 * count [basis]. No edit affordance — memory here is honest and read-only; deletion
 * lives in its own section. */
@Composable
private fun MemoryRow(index: Int, statement: String, basis: String) {
    val shape = RoundedCornerShape(13.dp)
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
            .padding(horizontal = 16.dp, vertical = 11.dp),
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
    }
}
