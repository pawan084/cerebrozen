package com.cerebrozen.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.audio.CloudVoice
import com.cerebrozen.app.audio.VoiceEngine
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal data class Msg(val role: String, val text: String, val widget: ChatWidget? = null)

/** An inline activity the Oracle attached to a reply (cross-stack widget kinds). */
internal data class ChatWidget(val kind: String, val title: String, val description: String)

internal fun parseWidget(o: JSONObject?): ChatWidget? {
    val kind = o?.optString("widget_kind").orEmpty()
    if (kind.isBlank()) return null
    return ChatWidget(kind, o!!.optString("title"), o.optString("description"))
}

/** Where an inline activity lands on Android — every cross-stack widget kind
 * now has a native surface (the tools round closed the last gaps). */
internal fun widgetRoute(kind: String): String? = when (kind) {
    "breathing", "grounding" -> "games"
    "mood_check" -> "home"
    "mini_journal", "journal" -> "journal"
    "sleep_checkin" -> "sleep"
    "one_good_thing" -> "onegoodthing"
    "intention_set" -> "intention"
    "dbt_skill" -> "tipp"
    else -> null
}

internal fun parseChat(rows: JSONArray): List<Msg> =
    (0 until rows.length()).map { i ->
        val m = rows.getJSONObject(i)
        Msg(m.getString("role"), m.getString("text"))
    }

/** The backend marks elevated/crisis replies with a `crisis` suggestion
 * action (services/activities.py) — that's the signal for the banner. */
internal fun hasCrisisSuggestion(suggestions: JSONArray?): Boolean {
    if (suggestions == null) return false
    return (0 until suggestions.length()).any {
        suggestions.optJSONObject(it)?.optString("action") == "crisis"
    }
}

/** `/assessment/topics` → tappable starter texts (mirrors the iOS rail). */
internal fun parseStarters(payload: JSONObject): List<String> =
    payload.optJSONArray("topics")?.let { arr ->
        (0 until arr.length()).mapNotNull {
            arr.optJSONObject(it)?.optString("topic")?.takeIf(String::isNotBlank)
        }
    } ?: emptyList()

/** The last few turns as a journal body (mirrors iOS "Save to journal"). */
internal fun talkTranscript(messages: List<Msg>, take: Int = 8): String =
    messages.takeLast(take).joinToString("\n\n") { m ->
        (if (m.role == "user") "Me: " else "CereBro: ") + m.text
    }

/** Talk: a real voice companion (on-device speech ↔ TTS over /chat) with a
 * text fallback. Same deterministic, safety-scanned pipeline as iOS/web. */
@Composable
fun TalkScreen(onOpen: (String) -> Unit = {}) {
    var messages by remember { mutableStateOf(listOf<Msg>()) }
    var draft by remember { mutableStateOf("") }
    var chips by remember { mutableStateOf(listOf<String>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Regulatory UX (mirrors iOS AIDisclosure): tappable always-visible pill +
    // a re-shown sheet every 3 h of continuous use (NY companion-law floor).
    var showDisclosure by remember { mutableStateOf(false) }
    // Sticky once a reply carries crisis risk — the affordance stays reachable.
    var crisis by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3L * 60 * 60 * 1000)
            showDisclosure = true
        }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val voice = remember { VoiceEngine(context) }
    // Cloud voice (iOS-parity quality): Deepgram STT + ElevenLabs TTS via the
    // backend when the server has keys; the on-device engine stays fallback.
    val cloud = remember { CloudVoice(context) }
    var cloudVoice by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { voice.dispose(); cloud.dispose() } }

    var starters by remember { mutableStateOf(listOf<String>()) }
    // Agentic Oracle (SSE) when the server has it; deterministic /chat otherwise.
    var useOracle by remember { mutableStateOf(false) }
    var streamText by remember { mutableStateOf("") }
    var confirmReq by remember { mutableStateOf<Pair<String, String>?>(null) } // threadId → summary
    LaunchedEffect(Unit) {
        runCatching { messages = parseChat(Api.chat()) }
        // Empty chat → grounded conversation starters (mirrors the iOS rail).
        if (messages.isEmpty()) runCatching { starters = parseStarters(Api.starters()) }
        useOracle = Api.oracleAvailable()
        cloudVoice = runCatching {
            val v = Api.voiceStatus()
            v.optBoolean("stt") && v.optBoolean("tts")
        }.getOrDefault(false)
    }

    /** Speak a reply — studio voice when the server has TTS, else on-device. */
    suspend fun speakReply(text: String) {
        if (text.isBlank()) return
        if (cloudVoice) {
            runCatching { cloud.play(Api.tts(text)) }.onFailure { voice.speak(text) }
        } else {
            voice.speak(text)
        }
    }

    /** Consume one Oracle SSE stream, mutating the chat state per frame.
     * Returns the final assistant text (for the voice path to speak). */
    suspend fun consume(path: String, body: JSONObject): String {
        var acc = ""
        var widget: ChatWidget? = null
        var final = ""
        try {
            Session.sse(path, body) { ev ->
                when (ev.optString("type")) {
                    "token" -> { acc += ev.optString("text"); streamText = acc }
                    "widget" -> widget = parseWidget(ev.optJSONObject("widget"))
                    "crisis" -> crisis = true
                    "tool_confirm" -> confirmReq = ev.optString("thread_id") to
                        ev.optString("summary").ifBlank { "Approve this action?" }
                    "done" -> {
                        val t = ev.optString("text").ifBlank { acc }.trim()
                        if (t.isNotEmpty() || widget != null) messages = messages + Msg("assistant", t, widget)
                        final = t; acc = ""; widget = null
                    }
                    "error" -> messages = messages + Msg(
                        "assistant",
                        ev.optString("detail").ifBlank { "Something went wrong — please try again." },
                    )
                }
            }
        } finally {
            // Stream may end paused on a confirm — keep the card, drop the bubble.
            streamText = ""
        }
        return final
    }

    fun send(text: String, speak: Boolean = false) {
        if (text.isBlank() || busy) return
        busy = true; status = null
        scope.launch {
            try {
                if (useOracle) {
                    // Agentic path: SSE tokens + inline widgets + confirm-before-write.
                    // The server persists both sides; thread defaults to the user id.
                    messages = messages + Msg("user", text.trim())
                    chips = emptyList()
                    val final = consume("/oracle/messages", JSONObject().put("text", text.trim()))
                    draft = ""
                    if (speak) speakReply(final)
                } else {
                    val reply: JSONObject = Api.sendChat(text.trim())
                    val replyText = reply.getJSONObject("reply").getString("text")
                    messages = messages +
                        Msg("user", reply.getJSONObject("user_message").getString("text")) +
                        Msg("assistant", replyText)
                    val suggestions = reply.optJSONArray("suggestions")
                    chips = suggestions?.let { arr ->
                        (0 until arr.length()).map { arr.getJSONObject(it).getString("label") }
                    } ?: emptyList()
                    if (hasCrisisSuggestion(suggestions)) crisis = true
                    draft = ""
                    if (speak) speakReply(replyText)
                }
            } catch (e: Exception) {
                status = e.message ?: "Couldn't send."
            } finally {
                busy = false
            }
        }
    }

    fun resolveConfirm(approved: Boolean) {
        val req = confirmReq ?: return
        confirmReq = null
        busy = true
        scope.launch {
            try {
                consume("/oracle/confirm", JSONObject().put("thread_id", req.first).put("approved", approved))
            } catch (e: Exception) {
                status = e.message ?: "Couldn't send."
            } finally {
                busy = false
            }
        }
    }

    fun beginListening() {
        if (cloudVoice) {
            if (!cloud.startRecording()) status = "Microphone unavailable right now."
        } else {
            voice.startListening { t -> send(t, speak = true) }
        }
    }

    /** Stop the cloud recording and run the full quality loop: STT → chat → TTS. */
    fun finishCloudTurn() {
        val bytes = cloud.stopRecording()
        if (bytes == null) { status = "Didn't catch that — try again."; return }
        transcribing = true
        scope.launch {
            try {
                val transcript = Api.stt(bytes)
                if (transcript.isBlank()) status = "Didn't catch that — try again."
                else send(transcript, speak = true)
            } catch (e: Exception) {
                status = e.message ?: "Couldn't transcribe — you can type below."
            } finally {
                transcribing = false
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginListening()
        else status = "Microphone access is off — you can still type below."
    }

    fun onOrbTap() {
        if (!voice.available && !cloudVoice) return
        when {
            cloud.speaking -> cloud.stopPlayback()          // tap-to-interrupt
            cloud.recording -> finishCloudTurn()
            voice.listening -> voice.stopListening()
            else -> {
                val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) beginListening() else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Page("AI voice companion", "Talk it through") {
        // Persistent AI disclosure — always visible, tap for the full points.
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardFill)
                .border(1.dp, LineStroke, RoundedCornerShape(12.dp))
                .clickable { showDisclosure = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "AI companion — not a therapist or crisis service",
                style = MaterialTheme.typography.bodySmall, color = TextMuted,
                modifier = Modifier.weight(1f),
            )
            Text("Details", style = MaterialTheme.typography.bodySmall, color = Periwinkle)
        }

        if (crisis) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Danger.copy(alpha = 0.14f))
                    .border(1.dp, Danger.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .clickable { onOpen("crisis") }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("You matter. Support is available right now.",
                    style = MaterialTheme.typography.titleMedium, color = Danger)
                Text("Tap for crisis resources — real people, 24/7.",
                    style = MaterialTheme.typography.bodyMedium, color = TextSoft)
            }
        }

        if (showDisclosure) {
            AlertDialog(
                onDismissRequest = { showDisclosure = false },
                title = { Text("About your AI companion") },
                text = {
                    Text(
                        "• It's AI, not a person — replies are generated.\n" +
                        "• It isn't medical care and never diagnoses or prescribes.\n" +
                        "• It isn't for emergencies — in one, contact local services.\n" +
                        "• Conversations are reviewed for safety signals only.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showDisclosure = false }) { Text("Got it") }
                },
                dismissButton = {
                    TextButton(onClick = { showDisclosure = false; onOpen("crisis") }) {
                        Text("Get crisis support")
                    }
                },
            )
        }

        if (voice.available || cloudVoice) {
            VoiceOrb(
                listening = voice.listening || cloud.recording,
                speaking = voice.speaking || cloud.speaking,
                onTap = { onOrbTap() },
            )
            val hint = when {
                transcribing -> "Hearing you…"
                busy -> "Thinking…"
                cloud.speaking -> "Speaking… tap to interrupt"
                voice.speaking -> "Speaking…"
                cloud.recording -> "Listening… tap when you're done"
                voice.listening -> "Listening… tap to stop"
                cloudVoice -> "Tap the orb — studio-quality voice"
                else -> "Tap the orb to talk live"
            }
            Text(hint, style = MaterialTheme.typography.titleMedium, color = TextSoft,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        if (messages.isEmpty()) {
            SectionCard {
                Text("What's on your mind?", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text("Speak or type — small worries welcome.", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            if (starters.isNotEmpty()) {
                Text("Or start from where you are", style = MaterialTheme.typography.labelSmall, color = Periwinkle)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    starters.forEach { topic ->
                        PickChip(selected = false, label = topic) { send(topic) }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                messages.takeLast(12).forEach { m ->
                    ChatBubble(m)
                    m.widget?.let { WidgetCard(it, onOpen) }
                }
                if (streamText.isNotBlank()) {
                    ChatBubble(Msg("assistant", "$streamText ▍"))
                }
            }
            TextButton(onClick = {
                scope.launch {
                    runCatching { Api.createJournal("Talk reflection", talkTranscript(messages)) }
                        .onSuccess { status = "Saved to your journal." }
                        .onFailure { status = "Couldn't save — try again." }
                }
            }) {
                Text("Save this conversation to my journal", color = Periwinkle)
            }
        }

        // Confirm-before-write: the Oracle paused on a write tool (log_mood,
        // save_journal, …) — nothing happens without an explicit approval.
        confirmReq?.let { (_, summary) ->
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardFill)
                    .border(1.dp, Periwinkle.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("The companion wants to act", style = MaterialTheme.typography.labelSmall, color = Periwinkle)
                Text(summary, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = { resolveConfirm(true) }) { Text("Approve", color = Cyan) }
                    TextButton(enabled = !busy, onClick = { resolveConfirm(false) }) { Text("Not now", color = TextMuted) }
                }
            }
        }

        if (chips.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { label ->
                    PickChip(selected = false, label = label) { send(label) }
                }
            }
        }

        Text(if (voice.available) "Type instead" else "Type a message",
            style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        AppTextField(draft, { draft = it }, "Message")
        PrimaryButton(text = if (busy) "Thinking…" else "Send", enabled = !busy && draft.isNotBlank()) { send(draft) }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
    }
}

/** An Oracle-suggested inline activity: title/description + a native surface
 * when Android has one, else the honest iOS-only note (mirrors the web card). */
@Composable
private fun WidgetCard(w: ChatWidget, onOpen: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardFill)
            .border(1.dp, LineStroke, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Suggested activity", style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        Text(w.title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
        Text(w.description, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        val route = widgetRoute(w.kind)
        if (route != null) {
            TextButton(onClick = { onOpen(route) }) { Text("Open", color = Cyan) }
        } else {
            Text("This one lives in the iOS app.", style = MaterialTheme.typography.bodySmall, color = TextMuted2)
        }
    }
}

@Composable
private fun ChatBubble(m: Msg) {
    val user = m.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (user) Periwinkle.copy(alpha = 0.20f) else CardFill,
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (user) 18.dp else 5.dp,
                bottomEnd = if (user) 5.dp else 18.dp,
            ),
            modifier = Modifier.widthIn(max = 320.dp)
                .border(
                    1.dp, if (user) Periwinkle.copy(alpha = 0.35f) else LineStroke,
                    RoundedCornerShape(18.dp),
                ),
        ) {
            Text(
                m.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (user) TextSoft else TextMuted,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun VoiceOrb(listening: Boolean, speaking: Boolean, onTap: () -> Unit) {
    val active = listening || speaking
    val t = rememberInfiniteTransition(label = "orb")
    val pulse by t.animateFloat(
        initialValue = if (active) 0.9f else 0.82f,
        targetValue = if (active) 1.18f else 1.0f,
        animationSpec = infiniteRepeatable(tween(if (listening) 700 else 2600), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(150.dp).scale(pulse).clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White, if (listening) Cyan else Periwinkle, Color(0xFF5B52C9)),
                    ),
                )
                .clickable(onClickLabel = if (listening) "Stop listening" else "Talk to CereBro") { onTap() },
        )
    }
}
