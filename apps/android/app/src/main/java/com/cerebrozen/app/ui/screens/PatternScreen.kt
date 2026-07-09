package com.cerebro.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cerebro.app.net.Api
import com.cerebro.app.ui.theme.Cyan
import com.cerebro.app.ui.theme.Danger
import com.cerebro.app.ui.theme.TextMuted
import com.cerebro.app.ui.theme.TextSoft
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
    var confirming by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { runCatching { learned = parsePatterns(Api.patterns()) } }

    SubPage("Transparent AI memory", "Pattern dashboard", onBack) {
        Text(
            "Everything CereBro has learned about you — visible, honest, and yours to delete.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted,
        )
        SectionCard {
            Text("What CereBro remembers", style = MaterialTheme.typography.titleMedium, color = TextSoft)
            when {
                learned == null -> Text("Looking at your data…",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                learned!!.isEmpty() -> Text(
                    "Nothing yet. Patterns only appear once a few weeks of real check-ins support them — no guesses, ever.",
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                )
                else -> learned!!.forEach { p ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("· ${p.statement}", style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                        if (p.basis.isNotBlank()) {
                            Text("   ${p.basis}", style = MaterialTheme.typography.bodySmall, color = Cyan)
                        }
                    }
                }
            }
        }
        SectionCard {
            Text("Delete all memory", style = MaterialTheme.typography.titleMedium, color = Danger)
            Text(
                "Removes chat history, computed insights and the companion's thread memory — it starts fresh. " +
                    "Your journal, check-ins and sleep diary stay: they're your content, with their own controls.",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted,
            )
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (!confirming) { confirming = true; return@TextButton }
                    scope.launch {
                        runCatching { Api.deleteMemory() }
                            .onSuccess {
                                confirming = false
                                learned = emptyList()
                                status = "Memory cleared — ${it.optInt("chat_messages")} messages and " +
                                    "${it.optInt("insights")} insights forgotten."
                            }
                            .onFailure { status = it.message ?: "Couldn't delete — try again." }
                    }
                },
            ) {
                Text(if (confirming) "Tap again to confirm" else "Delete all memory", color = Danger)
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
        }
    }
}
