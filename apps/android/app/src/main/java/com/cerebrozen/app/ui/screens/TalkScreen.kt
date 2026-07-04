package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class Msg(val role: String, val text: String)

private fun parseChat(rows: JSONArray): List<Msg> =
    (0 until rows.length()).map { i ->
        val m = rows.getJSONObject(i)
        Msg(m.getString("role"), m.getString("text"))
    }

/** Text chat against /chat — the deterministic-fallback pipeline the iOS and
 * web clients share (safety-scanned; AI boundary stated up front). */
@Composable
fun TalkScreen() {
    var messages by remember { mutableStateOf(listOf<Msg>()) }
    var draft by remember { mutableStateOf("") }
    var chips by remember { mutableStateOf(listOf<String>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { runCatching { messages = parseChat(Api.chat()) } }

    fun send(text: String) {
        if (text.isBlank() || busy) return
        busy = true; status = null
        scope.launch {
            try {
                val reply: JSONObject = Api.sendChat(text.trim())
                messages = messages +
                    Msg("user", reply.getJSONObject("user_message").getString("text")) +
                    Msg("assistant", reply.getJSONObject("reply").getString("text"))
                chips = reply.optJSONArray("suggestions")?.let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it).getString("label") }
                } ?: emptyList()
                draft = ""
            } catch (e: Exception) {
                status = e.message ?: "Couldn't send."
            } finally {
                busy = false
            }
        }
    }

    Page("A companion, not a therapist", "Talk it through") {
        Text(
            "Supportive AI — not medical care. In an emergency, contact local emergency services.",
            style = MaterialTheme.typography.bodySmall, color = TextMuted,
        )

        SectionCard {
            if (messages.isEmpty()) {
                Text("What's on your mind?", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text("Say anything — small worries welcome.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            messages.takeLast(12).forEach { m ->
                Text(
                    if (m.role == "user") "You" else "CereBro",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (m.role == "user") Periwinkle else TextSoft,
                )
                Text(m.text, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
        }

        if (chips.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { label ->
                    FilterChip(selected = false, onClick = { send(label) }, label = { Text(label) })
                }
            }
        }

        OutlinedTextField(
            value = draft, onValueChange = { draft = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(enabled = !busy && draft.isNotBlank(), onClick = { send(draft) }) {
            Text(if (busy) "Thinking…" else "Send")
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
    }
}
