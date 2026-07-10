package com.cerebro.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebro.app.audio.CloudVoice
import com.cerebro.app.audio.VoiceEngine
import com.cerebro.app.net.Api
import com.cerebro.app.net.Session
import com.cerebro.app.ui.theme.CardFill
import com.cerebro.app.ui.theme.Cyan
import com.cerebro.app.ui.theme.Danger
import com.cerebro.app.ui.theme.LineStroke
import com.cerebro.app.ui.theme.Night
import com.cerebro.app.ui.theme.Periwinkle
import com.cerebro.app.ui.theme.TextMuted
import com.cerebro.app.ui.theme.TextMuted2
import com.cerebro.app.ui.theme.TextPrimary
import com.cerebro.app.ui.theme.TextSoft
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
fun TalkScreen(onOpen: (String) -> Unit = {}, onVoiceSessionChange: (Boolean) -> Unit = {}) {
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
    var resumeListenToken by remember { mutableStateOf(0) }
    // Immersive live session (ref LIVE VOICE SESSION overlay): opens on the
    // first voice turn, stays up across turns until End/Text.
    var voiceSession by remember { mutableStateOf(false) }
    var textFallback by remember { mutableStateOf(false) }
    var sessionSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(voiceSession) {
        sessionSeconds = 0
        while (voiceSession) {
            kotlinx.coroutines.delay(1_000)
            sessionSeconds++
        }
    }
    LaunchedEffect(voiceSession, textFallback) {
        onVoiceSessionChange(voiceSession || textFallback)
    }
    DisposableEffect(Unit) {
        onDispose { onVoiceSessionChange(false) }
    }
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
        val resumeLiveTurn = {
            if (voiceSession) resumeListenToken++
        }
        if (cloudVoice) {
            runCatching { cloud.play(Api.tts(text), onDone = resumeLiveTurn) }
                .onFailure { voice.speak(text, onDone = resumeLiveTurn) }
        } else {
            voice.speak(text, onDone = resumeLiveTurn)
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
            if (cloud.startRecording()) voiceSession = true
            else status = "Microphone unavailable right now."
        } else {
            voiceSession = true
            voice.startListening { t -> send(t, speak = true) }
        }
    }

    LaunchedEffect(resumeListenToken) {
        if (resumeListenToken > 0 && voiceSession) beginListening()
    }

    fun endSession() {
        if (cloud.recording) cloud.stopRecording()   // discard the open take
        cloud.stopPlayback()
        voice.stopListening()
        voiceSession = false
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
        if (!voice.available && !cloudVoice) {
            voiceSession = true
            status = "Microphone unavailable right now."
            return
        }
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

    TalkVoiceHome(
        listening = voice.listening || cloud.recording,
        speaking = voice.speaking || cloud.speaking,
        busy = busy || transcribing,
        status = status,
        onOrbTap = { onOrbTap() },
        onDetails = { showDisclosure = true },
        onMic = { onOrbTap() },
        onSos = { onOpen("breathing") },
        onGames = { onOpen("games") },
    )

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("About your AI companion") },
            text = {
                Text(
                    "It's AI, not a person. It isn't medical care and never diagnoses or prescribes. It isn't for emergencies.",
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

    if (voiceSession) {
        VoiceSessionOverlay(
            seconds = sessionSeconds,
            stateLabel = when {
                transcribing -> "Hearing you..."
                busy -> "Thinking..."
                cloud.speaking || voice.speaking -> "Speaking - tap the orb to interrupt"
                cloud.recording || voice.listening -> "Listening... tap the orb when you're done"
                else -> "Tap the orb to speak"
            },
            listening = cloud.recording || voice.listening,
            speaking = cloud.speaking || voice.speaking,
            caption = streamText.ifBlank { messages.lastOrNull { it.role == "assistant" }?.text.orEmpty() },
            onOrb = { onOrbTap() },
            onEnd = { endSession() },
            onText = {
                voiceSession = false
                textFallback = true
            },
        )
    }
    if (textFallback) {
        TextFallbackOverlay(
            messages = messages,
            draft = draft,
            busy = busy,
            onDraftChange = { draft = it },
            onSend = { send(draft) },
            onBack = { textFallback = false },
            onDisclosure = { showDisclosure = true },
            onChip = { send(it) },
        )
    }
    return

    Box(Modifier.fillMaxSize()) {
    Page("AI voice companion", "Talk it through", trailing = Icons.Outlined.Mic) {
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

        // Fast escape hatch when talking feels like too much (mirrors iOS).
        NavRow("Quick SOS reset", "Fast anxiety/stress reset — 2 minutes") { onOpen("games") }

        Text(if (voice.available) "Type instead" else "Type a message",
            style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        AppTextField(draft, { draft = it }, "Message")
        PrimaryButton(text = if (busy) "Thinking…" else "Send", enabled = !busy && draft.isNotBlank()) { send(draft) }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
    }

    // Ref LIVE VOICE SESSION: an immersive overlay that stays up across turns.
    if (voiceSession) {
        VoiceSessionOverlay(
            seconds = sessionSeconds,
            stateLabel = when {
                transcribing -> "Hearing you…"
                busy -> "Thinking…"
                cloud.speaking || voice.speaking -> "Speaking — tap the orb to interrupt"
                cloud.recording || voice.listening -> "Listening… tap the orb when you're done"
                else -> "Tap the orb to speak"
            },
            listening = cloud.recording || voice.listening,
            speaking = cloud.speaking || voice.speaking,
            caption = streamText.ifBlank { messages.lastOrNull { it.role == "assistant" }?.text.orEmpty() },
            onOrb = { onOrbTap() },
            onEnd = { endSession() },
            onText = { voiceSession = false },
        )
    }
    }
}

@Composable
private fun TalkVoiceHome(
    listening: Boolean,
    speaking: Boolean,
    busy: Boolean,
    status: String?,
    onOrbTap: () -> Unit,
    onDetails: () -> Unit,
    onMic: () -> Unit,
    onSos: () -> Unit,
    onGames: () -> Unit,
) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("AI VOICE COMPANION", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.35f))
                    Text("Your voice\ncompanion", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
                }
                Box(
                    Modifier
                        .padding(top = 38.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { onMic() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Mic, contentDescription = "Start live voice", tint = TextPrimary, modifier = Modifier.size(22.dp))
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.09f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .clickable { onDetails() }
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ⓘ", style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text("AI companion — not a therapist or\ncrisis service.", style = MaterialTheme.typography.bodyMedium, color = TextSoft, modifier = Modifier.weight(1f))
                Text("Details ›", style = MaterialTheme.typography.bodyMedium, color = Periwinkle)
            }

            Spacer(Modifier.height(4.dp))
            VoiceOrb(listening = listening, speaking = speaking || busy, onTap = onOrbTap)
            Text(
                when {
                    busy -> "Thinking..."
                    speaking -> "Speaking... tap to interrupt"
                    listening -> "Listening... tap when you're done"
                    else -> "Tap the orb to talk live"
                },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOrbTap() },
            )
            WaveformCard(active = listening || speaking || busy)
            Text(
                "Tap the orb to start a real conversation. I'll\nlisten, then talk it through with you.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(18.dp),
            )

            TalkActionRow("♨", "Quick SOS reset", "Fast anxiety / stress reset", onSos)
            TalkActionRow("⌘", "Calm games", "Playful resets for a busy mind", onGames)
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
            Spacer(Modifier.height(112.dp))
        }
    }
}

@Composable
private fun WaveformCard(active: Boolean, boxed: Boolean = true) {
    val t = rememberInfiniteTransition(label = "waveform")
    val pulse by t.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "waveform-pulse",
    )
    val shell = if (boxed) {
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(13.dp))
    } else {
        Modifier
            .fillMaxWidth()
            .height(40.dp)
    }
    Row(
        shell,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val base = listOf(9, 16, 24, 13, 31, 36, 25, 35, 21, 12)
        base.forEachIndexed { index, h ->
            val animated = if (active) {
                (h * (0.62f + ((pulse + index * 0.13f) % 1f) * 0.55f)).toInt().coerceIn(8, 40)
            } else {
                h
            }
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .width(4.dp)
                    .height(animated.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.72f)),
            )
        }
    }
}

@Composable
private fun TalkActionRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Periwinkle.copy(alpha = 0.32f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = TextMuted)
    }
}

@Composable
private fun VoiceSessionOverlay(
    seconds: Int,
    stateLabel: String,
    listening: Boolean,
    speaking: Boolean,
    caption: String,
    onOrb: () -> Unit,
    onEnd: () -> Unit,
    onText: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF6657AA), Color(0xFF2B1E5C), Color(0xFF153852))))
            .clickable(enabled = false) {},
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 32.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF73D4B5)))
                    Text("LIVE SESSION", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.45f))
                }
                Text(fmtSession(seconds), style = MaterialTheme.typography.displaySmall, color = TextPrimary)
            }

            Spacer(Modifier.height(124.dp))
            VoiceOrb(listening = listening, speaking = speaking, onTap = onOrb)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF73D4B5).copy(alpha = 0.75f)))
                Text(
                    if (speaking) "Speaking" else "Listening",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSoft,
                )
            }
            WaveformCard(active = listening || speaking, boxed = false)
            Spacer(Modifier.height(14.dp))
            Text(
                caption.take(120).ifBlank { "Let's take this slow together — I'm\nright here." },
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveSessionButton("♩", "Mute", Color.White.copy(alpha = 0.08f), TextPrimary, onOrb)
                LiveSessionButton("☎", "End", Color(0xFFE95D78), Color.White, onEnd)
                LiveSessionButton("⌨", "Text", Color.White.copy(alpha = 0.08f), TextPrimary, onText)
            }
            Spacer(Modifier.height(34.dp))
        }
    }
}

@Composable
private fun TextFallbackOverlay(
    messages: List<Msg>,
    draft: String,
    busy: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    onDisclosure: () -> Unit,
    onChip: (String) -> Unit,
) {
    val displayMessages = messages.ifEmpty {
        listOf(
            Msg("user", "I keep overthinking about tomorrow's meeting."),
            Msg("assistant", "Let's slow it down. What thought is repeating?"),
            Msg("user", "I feel like I'll fail."),
            Msg("assistant", "That's a heavy prediction. Want to reframe it, or calm the body first?"),
        )
    }
    val chatScroll = rememberScrollState()

    LaunchedEffect(displayMessages.size) {
        kotlinx.coroutines.delay(80)
        chatScroll.animateScrollTo(chatScroll.maxValue)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF6758AB), Color(0xFF21164F), Color(0xFF110A27), Color(0xFF32163A)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("<", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                }
                Column {
                    Text("TEXT FALLBACK", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.35f))
                    Text("AI Chat", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(13.dp))
                    .clickable { onDisclosure() }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("i", style = MaterialTheme.typography.labelLarge, color = TextSoft)
                Text("AI companion — not a therapist or crisis service.", style = MaterialTheme.typography.bodyMedium, color = TextSoft)
            }

            Spacer(Modifier.height(18.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(chatScroll),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                displayMessages.forEach { msg ->
                    TextFallbackBubble(msg)
                }

                Spacer(Modifier.height(2.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Reframe it", "2-min reset", "Save to journal").forEach { chip ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable { onChip(chip) }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                        ) {
                            Text(chip, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    placeholder = { Text("Type a message...", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Color.White.copy(alpha = 0.18f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                        focusedContainerColor = Color.White.copy(alpha = 0.06f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                        cursorColor = TextPrimary,
                    ),
                )
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(enabled = draft.isNotBlank() && !busy) { onSend() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↑", style = MaterialTheme.typography.headlineSmall, color = Night)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TextFallbackBubble(msg: Msg) {
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = 290.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 19.dp,
                        topEnd = 19.dp,
                        bottomStart = if (isUser) 19.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 19.dp,
                    ),
                )
                .background(
                    if (isUser) Color(0xFF685CC2).copy(alpha = 0.86f)
                    else Color(0xFF566995).copy(alpha = 0.58f),
                )
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            Text(msg.text, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        }
    }
}

@Composable
private fun LiveSessionButton(icon: String, label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(if (label == "End") 76.dp else 62.dp)
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            CallControlGlyph(
                icon = when (label) {
                    "End" -> CallControlIcon.Phone
                    "Text" -> CallControlIcon.Keyboard
                    else -> CallControlIcon.Mic
                },
                color = fg,
                modifier = Modifier.size(if (label == "End") 32.dp else 25.dp),
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSoft)
    }
}

private enum class CallControlIcon { Mic, Phone, Keyboard }

@Composable
private fun CallControlGlyph(icon: CallControlIcon, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        when (icon) {
            CallControlIcon.Mic -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.36f, h * 0.12f),
                    size = Size(w * 0.28f, h * 0.48f),
                    cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
                )
                drawLine(color, Offset(w * 0.5f, h * 0.58f), Offset(w * 0.5f, h * 0.82f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.34f, h * 0.82f), Offset(w * 0.66f, h * 0.82f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                drawArc(
                    color = color,
                    startAngle = 28f,
                    sweepAngle = 124f,
                    useCenter = false,
                    topLeft = Offset(w * 0.20f, h * 0.28f),
                    size = Size(w * 0.60f, h * 0.48f),
                    style = stroke,
                )
            }
            CallControlIcon.Phone -> {
                val handset = Path().apply {
                    moveTo(w * 0.20f, h * 0.52f)
                    cubicTo(w * 0.30f, h * 0.32f, w * 0.70f, h * 0.32f, w * 0.80f, h * 0.52f)
                    lineTo(w * 0.68f, h * 0.67f)
                    cubicTo(w * 0.63f, h * 0.59f, w * 0.57f, h * 0.55f, w * 0.50f, h * 0.55f)
                    cubicTo(w * 0.43f, h * 0.55f, w * 0.37f, h * 0.59f, w * 0.32f, h * 0.67f)
                    close()
                }
                drawPath(handset, color)
            }
            CallControlIcon.Keyboard -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.16f, h * 0.28f),
                    size = Size(w * 0.68f, h * 0.46f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = Stroke(width = w * 0.075f),
                )
                val keyW = w * 0.055f
                val keyH = h * 0.055f
                listOf(
                    Offset(w * 0.30f, h * 0.42f),
                    Offset(w * 0.42f, h * 0.42f),
                    Offset(w * 0.54f, h * 0.42f),
                    Offset(w * 0.66f, h * 0.42f),
                    Offset(w * 0.34f, h * 0.56f),
                    Offset(w * 0.46f, h * 0.56f),
                    Offset(w * 0.58f, h * 0.56f),
                ).forEach { topLeft ->
                    drawRoundRect(color, topLeft, Size(keyW, keyH), CornerRadius(keyW * 0.35f, keyH * 0.35f))
                }
            }
        }
    }
}

/** Full-screen live-session surface: elapsed time, the orb, the state label,
 * the latest words, and End / Text controls (ref LIVE VOICE SESSION). */
@Composable
private fun LegacyVoiceSessionOverlay(
    seconds: Int,
    stateLabel: String,
    listening: Boolean,
    speaking: Boolean,
    caption: String,
    onOrb: () -> Unit,
    onEnd: () -> Unit,
    onText: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .background(Night.copy(alpha = 0.97f))
            .clickable(enabled = false) {}   // swallow taps aimed behind the overlay
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("LIVE SESSION", style = MaterialTheme.typography.labelSmall, color = Cyan)
            Text(fmtSession(seconds), style = MaterialTheme.typography.titleMedium, color = TextSoft)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            VoiceOrb(listening = listening, speaking = speaking, onTap = onOrb)
            Text(stateLabel, style = MaterialTheme.typography.titleMedium, color = TextSoft, textAlign = TextAlign.Center)
            if (caption.isNotBlank()) {
                Text(
                    caption.take(180),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TextButton(onClick = onEnd) { Text("End", color = Danger) }
            TextButton(onClick = onText) { Text("Text instead", color = TextMuted) }
        }
    }
}

/** m:ss elapsed-session label — pure + testable. */
internal fun fmtSession(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

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
        // Soft bloom halo behind the orb (mirrors the iOS radial glow).
        Box(
            Modifier.size(230.dp).scale(pulse)
                .background(
                    Brush.radialGradient(
                        listOf(
                            (if (listening) Cyan else Periwinkle).copy(alpha = 0.28f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
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
