package com.cerebrozen.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.audio.CloudVoice
import com.cerebrozen.app.audio.Player
import com.cerebrozen.app.audio.VoiceEngine
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleDeep
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
 * still has a native surface after the Toolkit consolidation: breathing keeps
 * its journaling practice, grounding lives inline in the Toolkit, and the
 * one-field tools became Journal quick-entry chips. */
internal fun widgetRoute(kind: String): String? = when (kind) {
    "breathing" -> "breathing"
    "grounding" -> "toolkit"
    "mood_check" -> "home"
    "mini_journal", "journal" -> "journal"
    "sleep_checkin" -> "sleep"
    "one_good_thing", "intention_set" -> "journal"
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

/** Whether the "Try together" exercise offers show mid-conversation (REDESIGN §3.3):
 * after the most recent assistant reply, once a real exchange exists, and never
 * while the companion is composing. Pure + unit-tested. */
internal fun showTryTogether(messageCount: Int, lastRole: String?, busy: Boolean, streaming: Boolean): Boolean =
    messageCount >= 2 && lastRole == "assistant" && !busy && !streaming

/** The last few turns as a journal body (mirrors iOS "Save to journal"). */
// i18n: pending — pure function, needs context plumbing ("Me: " / "CereBro: " prefixes).
internal fun talkTranscript(messages: List<Msg>, take: Int = 8): String =
    messages.takeLast(take).joinToString("\n\n") { m ->
        (if (m.role == "user") "Me: " else "CereBro: ") + m.text
    }

/** Talk: a real voice companion (on-device speech ↔ TTS over /chat) with a
 * text fallback. Same deterministic, safety-scanned pipeline as iOS/web. */
@Composable
fun TalkScreen(onOpen: (String) -> Unit = {}) {
    var messages by remember { mutableStateOf(listOf<Msg>()) }
    // Draft survives rotation / process death so a half-typed message isn't lost.
    var draft by rememberSaveable { mutableStateOf("") }
    var chips by remember { mutableStateOf(listOf<String>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Auto-scroll the conversation to the newest reply / streaming tokens.
    val chatScroll = rememberScrollState()
    // Regulatory UX (mirrors iOS AIDisclosure): tappable always-visible pill +
    // a re-shown sheet every 3 h of continuous use (NY companion-law floor).
    var showDisclosure by remember { mutableStateOf(false) }
    // Sticky once a reply carries crisis risk — the affordance stays reachable
    // (saved so a rotation can't drop the safety banner).
    var crisis by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3L * 60 * 60 * 1000)
            showDisclosure = true
        }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Copy used inside non-composable closures below — resolved once per composition.
    val errGeneric = stringResource(R.string.talk_error_generic)
    val confirmFallback = stringResource(R.string.talk_confirm_fallback)
    val sendFailed = stringResource(R.string.talk_send_failed)
    val micUnavailable = stringResource(R.string.talk_mic_unavailable)
    val didntCatch = stringResource(R.string.talk_didnt_catch)
    val transcribeFailed = stringResource(R.string.talk_transcribe_failed)
    val micOff = stringResource(R.string.talk_mic_off)
    val voice = remember { VoiceEngine(context) }
    // Cloud voice (iOS-parity quality): Deepgram STT + ElevenLabs TTS via the
    // backend when the server has keys; the on-device engine stays fallback.
    val cloud = remember { CloudVoice(context) }
    var cloudVoice by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    // Immersive live session (ref LIVE VOICE SESSION overlay): opens on the
    // first voice turn, stays up across turns until End/Text.
    var voiceSession by remember { mutableStateOf(false) }
    var sessionSeconds by remember { mutableStateOf(0) }
    // Live mic level for the cloud recording path (the on-device path uses voice.level).
    var cloudLevel by remember { mutableStateOf(0f) }
    // Duck the ambient bed under the companion's voice while it speaks.
    LaunchedEffect(voice.speaking, cloud.speaking) {
        if (Player.isPlaying) Player.duck(context, voice.speaking || cloud.speaking)
    }
    LaunchedEffect(voiceSession) {
        sessionSeconds = 0
        while (voiceSession) {
            kotlinx.coroutines.delay(1_000)
            sessionSeconds++
        }
    }
    DisposableEffect(Unit) { onDispose { voice.dispose(); cloud.dispose() } }

    var starters by remember { mutableStateOf(listOf<String>()) }
    // Agentic Oracle (SSE) when the server has it; deterministic /chat otherwise.
    var useOracle by remember { mutableStateOf(false) }
    var streamText by remember { mutableStateOf("") }
    var confirmReq by remember { mutableStateOf<Pair<String, String>?>(null) } // threadId → summary
    // W10: only bubbles that arrive AFTER the restored history animate in — the
    // transcript renders settled, new turns rise gently. Int.MAX_VALUE until the
    // history load resolves, so nothing animates prematurely.
    var entranceFloor by remember { mutableIntStateOf(Int.MAX_VALUE) }
    LaunchedEffect(Unit) {
        runCatching { messages = parseChat(Api.chat()) }
        entranceFloor = messages.size
        // Empty chat → grounded conversation starters (mirrors the iOS rail).
        if (messages.isEmpty()) runCatching { starters = parseStarters(Api.starters()) }
        useOracle = Api.oracleAvailable()
        cloudVoice = runCatching {
            val v = Api.voiceStatus()
            v.optBoolean("stt") && v.optBoolean("tts")
        }.getOrDefault(false)
    }

    // After a spoken reply, pick the mic back up so the conversation flows turn by
    // turn. Wired below once send() is in scope (these funcs are mutually
    // dependent); invoked on the Main-dispatched scope since VoiceEngine must be
    // driven from the main thread.
    var resumeTurn: () -> Unit = {}

    /** Speak a reply — studio voice when the server has TTS, else on-device — then
     * hand the turn back to the listener. */
    suspend fun speakReply(text: String) {
        if (text.isBlank()) { resumeTurn(); return }
        com.cerebrozen.app.ui.Haptics.success()   // a felt "reply's here" in voice mode
        if (cloudVoice) {
            val spoke = runCatching { cloud.play(Api.tts(text)) }.isSuccess
            if (spoke) resumeTurn()
            else voice.speak(text) { scope.launch { resumeTurn() } }
        } else {
            voice.speak(text) { scope.launch { resumeTurn() } }
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
                        ev.optString("summary").ifBlank { confirmFallback }
                    "done" -> {
                        val t = ev.optString("text").ifBlank { acc }.trim()
                        if (t.isNotEmpty() || widget != null) messages = messages + Msg("assistant", t, widget)
                        final = t; acc = ""; widget = null
                    }
                    "error" -> messages = messages + Msg(
                        "assistant",
                        ev.optString("detail").ifBlank { errGeneric },
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
        // Clear the composer up front so the sent text doesn't linger in the box
        // during streaming (and can't be wiped if the user starts a follow-up).
        draft = ""
        scope.launch {
            try {
                if (useOracle) {
                    // Agentic path: SSE tokens + inline widgets + confirm-before-write.
                    // The server persists both sides; thread defaults to the user id.
                    messages = messages + Msg("user", text.trim())
                    chips = emptyList()
                    val final = consume("/oracle/messages", JSONObject().put("text", text.trim()))
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
                    if (speak) speakReply(replyText)
                }
            } catch (e: Exception) {
                status = e.message ?: sendFailed
            } finally {
                busy = false
            }
        }
    }

    // Now that send() exists, wire the post-speech resume: re-arm the mic for the
    // next turn while the voice session is still open (End/Text clears it).
    resumeTurn = {
        if (voiceSession) {
            if (cloudVoice) {
                if (!cloud.startRecording()) status = micUnavailable
            } else {
                voice.startListening { t -> send(t, speak = true) }
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
                status = e.message ?: sendFailed
            } finally {
                busy = false
            }
        }
    }

    fun beginListening() {
        if (cloudVoice) {
            if (cloud.startRecording()) voiceSession = true
            else status = micUnavailable
        } else {
            voiceSession = true
            voice.startListening { t -> send(t, speak = true) }
        }
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
        if (bytes == null) { status = didntCatch; return }
        transcribing = true
        scope.launch {
            try {
                val transcript = Api.stt(bytes)
                if (transcript.isBlank()) status = didntCatch
                else send(transcript, speak = true)
            } catch (e: Exception) {
                status = e.message ?: transcribeFailed
            } finally {
                transcribing = false
            }
        }
    }

    // Natural turn-taking on the cloud path: poll the mic level while recording and
    // auto-finish the turn after ~1.5s of trailing silence (once speech was heard),
    // so the user doesn't have to tap to end. Also feeds the reactive orb.
    LaunchedEffect(cloud.recording) {
        if (!cloud.recording) { cloudLevel = 0f; return@LaunchedEffect }
        var silenceMs = 0
        var heardSpeech = false
        while (cloud.recording) {
            kotlinx.coroutines.delay(150)
            val amp = cloud.maxAmplitude()
            cloudLevel = (amp / 12_000f).coerceIn(0f, 1f)
            if (amp > 1_800) { heardSpeech = true; silenceMs = 0 } else if (heardSpeech) silenceMs += 150
            if (heardSpeech && silenceMs >= 1_500) { finishCloudTurn(); break }
        }
        cloudLevel = 0f
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginListening()
        else status = micOff
    }

    fun onOrbTap() {
        if (!voice.available && !cloudVoice) return
        com.cerebrozen.app.ui.Haptics.tap()
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

    // Follow the conversation as it grows — newest reply and streaming tokens stay
    // in view instead of appearing below the fold.
    LaunchedEffect(messages.size, streamText, busy) {
        chatScroll.animateScrollTo(chatScroll.maxValue)
    }

    Box(Modifier.fillMaxSize()) {
    Page(stringResource(R.string.talk_eyebrow), stringResource(R.string.talk_title), trailing = Icons.Outlined.Mic, accent = Cyan, scrollState = chatScroll) {
        // W10: honest offline truth for a connection-dependent surface — not
        // dismissible, and it points at what still works.
        if (Session.servedStale) {
            InfoBanner(
                icon = Icons.Outlined.CloudOff,
                text = stringResource(R.string.talk_offline_banner),
                actionLabel = stringResource(R.string.talk_offline_action),
                onAction = { onOpen("toolkit") },
            )
        }

        // Persistent AI disclosure — always visible, tap for the full points.
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .glass(RoundedCornerShape(12.dp))
                .clickable { showDisclosure = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.talk_disclosure_pill),
                style = MaterialTheme.typography.bodySmall, color = TextMuted,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.talk_disclosure_details), style = MaterialTheme.typography.bodySmall, color = Periwinkle)
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
                Text(stringResource(R.string.talk_crisis_title),
                    style = MaterialTheme.typography.titleMedium, color = Danger)
                Text(stringResource(R.string.talk_crisis_subtitle),
                    style = MaterialTheme.typography.bodyMedium, color = TextSoft)
            }
        }

        if (showDisclosure) {
            AlertDialog(
                onDismissRequest = { showDisclosure = false },
                title = { Text(stringResource(R.string.talk_disclosure_dialog_title)) },
                text = {
                    Text(stringResource(R.string.talk_disclosure_dialog_body))
                },
                confirmButton = {
                    TextButton(onClick = { showDisclosure = false }) { Text(stringResource(R.string.talk_disclosure_ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDisclosure = false; onOpen("crisis") }) {
                        Text(stringResource(R.string.talk_disclosure_crisis))
                    }
                },
            )
        }

        if (voice.available || cloudVoice) {
            VoiceOrb(
                listening = voice.listening || cloud.recording,
                speaking = voice.speaking || cloud.speaking,
                onTap = { onOrbTap() },
                thinking = transcribing || busy,
                level = if (cloud.recording) cloudLevel else voice.level,
            )
            val hint = when {
                transcribing -> stringResource(R.string.talk_hint_hearing)
                busy -> stringResource(R.string.talk_hint_thinking)
                cloud.speaking -> stringResource(R.string.talk_hint_speaking_interrupt)
                voice.speaking -> stringResource(R.string.talk_hint_speaking)
                cloud.recording -> stringResource(R.string.talk_hint_listening_done)
                voice.listening -> stringResource(R.string.talk_hint_listening_stop)
                cloudVoice -> stringResource(R.string.talk_hint_orb_studio)
                else -> stringResource(R.string.talk_hint_orb)
            }
            Text(hint, style = MaterialTheme.typography.titleMedium, color = TextSoft,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        if (messages.isEmpty()) {
            SectionCard {
                Text(stringResource(R.string.talk_empty_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(stringResource(R.string.talk_empty_subtitle), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            if (starters.isNotEmpty()) {
                Text(stringResource(R.string.talk_starters_header), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    starters.forEach { topic ->
                        PickChip(selected = false, label = topic) { send(topic) }
                    }
                }
            }
            TryTogetherRow(onOpen)
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Keyed on the absolute index so the sliding 12-message window
                // never re-runs an old bubble's entrance (W10).
                val windowStart = (messages.size - 12).coerceAtLeast(0)
                messages.takeLast(12).forEachIndexed { i, m ->
                    key(windowStart + i) {
                        ChatBubble(m, animate = windowStart + i >= entranceFloor)
                        m.widget?.let { WidgetCard(it, onOpen) }
                    }
                }
                // Live reply: streamed tokens with a blinking caret, or a typing
                // indicator while the companion is composing its answer.
                if (streamText.isNotBlank()) {
                    StreamingBubble(streamText)
                } else if (busy) {
                    TypingDots()
                }
                // Structured exercises as first-class offers (REDESIGN §3.3) —
                // quiet, after the companion's latest reply, never while composing.
                if (showTryTogether(messages.size, messages.lastOrNull()?.role, busy, streamText.isNotBlank())) {
                    TryTogetherRow(onOpen)
                }
            }
            val journalEntryTitle = stringResource(R.string.talk_journal_entry_title)
            val savedStatus = stringResource(R.string.talk_saved_status)
            val saveFailed = stringResource(R.string.talk_save_failed)
            TextButton(onClick = {
                scope.launch {
                    runCatching { Api.createJournal(journalEntryTitle, talkTranscript(messages)) }
                        .onSuccess { status = savedStatus }
                        .onFailure { status = saveFailed }
                }
            }) {
                Text(stringResource(R.string.talk_save_journal), color = Periwinkle)
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
                Text(stringResource(R.string.talk_confirm_header), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
                Text(summary, style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = { resolveConfirm(true) }) { Text(stringResource(R.string.talk_approve), color = Cyan) }
                    TextButton(enabled = !busy, onClick = { resolveConfirm(false) }) { Text(stringResource(R.string.talk_not_now), color = TextMuted) }
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
        NavRow(stringResource(R.string.talk_sos_title), stringResource(R.string.talk_sos_subtitle)) { onOpen("toolkit") }

        Text(if (voice.available) stringResource(R.string.talk_type_instead) else stringResource(R.string.talk_type_message),
            style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        AppTextField(draft, { draft = it }, stringResource(R.string.talk_field_label), modifier = Modifier.fillMaxWidth().imePadding())
        PrimaryButton(
            text = if (busy) stringResource(R.string.talk_hint_thinking) else stringResource(R.string.common_send),
            enabled = !busy && draft.isNotBlank(),
        ) { send(draft) }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
    }

    // Ref LIVE VOICE SESSION: an immersive overlay that stays up across turns.
    if (voiceSession) {
        VoiceSessionOverlay(
            seconds = sessionSeconds,
            stateLabel = when {
                transcribing -> stringResource(R.string.talk_hint_hearing)
                busy -> stringResource(R.string.talk_hint_thinking)
                cloud.speaking || voice.speaking -> stringResource(R.string.talk_state_speaking_interrupt)
                cloud.recording || voice.listening -> stringResource(R.string.talk_state_listening)
                else -> stringResource(R.string.talk_state_orb)
            },
            listening = cloud.recording || voice.listening,
            speaking = cloud.speaking || voice.speaking,
            thinking = transcribing || busy,
            level = if (cloud.recording) cloudLevel else voice.level,
            caption = streamText.ifBlank { messages.lastOrNull { it.role == "assistant" }?.text.orEmpty() },
            onOrb = { onOrbTap() },
            onEnd = { endSession() },
            // Returning to the text composer must tear down the live mic/recognizer
            // too — otherwise recording keeps running (and can still speak a reply)
            // after the user has left voice mode.
            onText = { endSession() },
        )
    }
    }
}

/** Full-screen live-session surface: elapsed time, the orb, the state label,
 * the latest words, and End / Text controls (ref LIVE VOICE SESSION). */
@Composable
private fun VoiceSessionOverlay(
    seconds: Int,
    stateLabel: String,
    listening: Boolean,
    speaking: Boolean,
    thinking: Boolean,
    level: Float,
    caption: String,
    onOrb: () -> Unit,
    onEnd: () -> Unit,
    onText: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .background(Night.copy(alpha = 0.97f))
            // Actually swallow taps aimed behind the overlay (a disabled
            // clickable does not intercept touches).
            .clickable(
                enabled = true,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {}
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.talk_live_session), style = MaterialTheme.typography.labelSmall, color = Cyan)
            Text(fmtSession(seconds), style = MaterialTheme.typography.titleMedium, color = TextSoft)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            VoiceOrb(listening = listening, speaking = speaking, onTap = onOrb, thinking = thinking, level = level)
            Text(stateLabel, style = MaterialTheme.typography.titleMedium, color = TextSoft, textAlign = TextAlign.Center)
            if (caption.isNotBlank()) {
                Text(
                    caption.take(180),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            CallControl(Icons.Filled.CallEnd, stringResource(R.string.talk_end), Danger, Danger.copy(alpha = 0.18f), onEnd)
            CallControl(Icons.Outlined.Keyboard, stringResource(R.string.talk_text), TextSoft, CardFill, onText)
        }
    }
}

/** A round live-call control — a circular glyph button with a label below
 * (End / Text), echoing the fork's call-session look on our tokens. */
@Composable
private fun CallControl(icon: ImageVector, label: String, tint: Color, bg: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(58.dp).clip(CircleShape).background(bg)
                .border(1.dp, tint.copy(alpha = 0.5f), CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

/** m:ss elapsed-session label — pure + testable. */
internal fun fmtSession(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** A quiet row of structured exercises the companion can do with you — CBT
 * reframe, paced breathing, grounding (the evidenced spine; chat is the glue). */
@Composable
private fun TryTogetherRow(onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.talk_try_together), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickChip(selected = false, label = stringResource(R.string.talk_chip_reframe)) { onOpen("cbt") }
            PickChip(selected = false, label = stringResource(R.string.talk_chip_breathe)) { onOpen("breathe/box") }
            PickChip(selected = false, label = stringResource(R.string.talk_chip_ground)) { onOpen("toolkit") }
        }
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
        Text(stringResource(R.string.talk_suggested_activity), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        Text(w.title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
        Text(w.description, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        val route = widgetRoute(w.kind)
        if (route != null) {
            TextButton(onClick = { onOpen(route) }) { Text(stringResource(R.string.common_open), color = Cyan) }
        } else {
            Text(stringResource(R.string.talk_ios_only), style = MaterialTheme.typography.bodySmall, color = TextMuted2)
        }
    }
}

/** One chat bubble. [animate] arms a one-shot 150ms rise+fade for bubbles that
 * arrive during this session; restored history renders settled (W10). The
 * Reduce-Motion branch lives inside [appear] (static, never blank). */
@Composable
private fun ChatBubble(m: Msg, animate: Boolean = false) {
    val user = m.role == "user"
    val entrance = if (animate) Modifier.appear(rise = 8f, durationMs = 150) else Modifier
    Row(
        Modifier.fillMaxWidth().then(entrance),
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
                color = TextSoft,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            )
        }
    }
}

/** The assistant reply as it streams: committed text plus a blinking caret so the
 * words feel typed in real time. */
@Composable
private fun StreamingBubble(text: String) {
    val reduceMotion = rememberReduceMotion()
    val t = rememberInfiniteTransition(label = "typing")
    val animatedCaret by t.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "caret",
    )
    val caret = if (reduceMotion) 1f else animatedCaret   // steady caret, no blink
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = CardFill,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 5.dp, bottomEnd = 18.dp),
            modifier = Modifier.widthIn(max = 320.dp).border(1.dp, LineStroke, RoundedCornerShape(18.dp)),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                Text("▍", style = MaterialTheme.typography.bodyMedium, color = Periwinkle.copy(alpha = caret))
            }
        }
    }
}

/** Three softly-pulsing dots — the companion is composing a reply (shown when we're
 * busy but not yet streaming tokens). */
@Composable
private fun TypingDots() {
    val reduceMotion = rememberReduceMotion()
    val t = rememberInfiniteTransition(label = "typingDots")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = CardFill,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 5.dp, bottomEnd = 18.dp),
            modifier = Modifier.border(1.dp, LineStroke, RoundedCornerShape(18.dp)),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(3) { i ->
                    val animatedAlpha by t.animateFloat(
                        initialValue = 0.25f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(600, delayMillis = i * 160), RepeatMode.Reverse),
                        label = "dot$i",
                    )
                    // Reduce Motion: hold the dots at a steady mid-opacity (no pulse).
                    val a = if (reduceMotion) 0.6f else animatedAlpha
                    Box(Modifier.size(7.dp).clip(CircleShape).background(TextMuted.copy(alpha = a)))
                }
            }
        }
    }
}

@Composable
private fun VoiceOrb(
    listening: Boolean,
    speaking: Boolean,
    onTap: () -> Unit,
    thinking: Boolean = false,
    level: Float = 0f,
) {
    val active = listening || speaking || thinking
    val reduceMotion = rememberReduceMotion()
    // Phase tint: thinking = iris, voice active = cyan, resting = lavender.
    val core = when {
        thinking -> Iris
        listening || speaking -> Cyan
        else -> Periwinkle
    }
    val t = rememberInfiniteTransition(label = "orb")
    val animatedPulse by t.animateFloat(
        initialValue = if (active) 0.9f else 0.82f,
        targetValue = if (active) 1.14f else 1.0f,
        animationSpec = infiniteRepeatable(tween(if (listening) 700 else 2600), RepeatMode.Reverse),
        label = "pulse",
    )
    // Ripples radiate while listening; a slow rotation drives the thinking ring.
    val ripple by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "ripple")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "spin")

    val basePulse = if (reduceMotion) 1f else animatedPulse
    // Mic-reactive swell on top of the breathing pulse (listening only).
    val pulse = if (reduceMotion || !listening) basePulse else basePulse + level * 0.16f

    Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
        // Expanding ripple rings while listening.
        if (listening && !reduceMotion) {
            for (i in 0 until 3) {
                val phase = (ripple + i / 3f) % 1f
                Box(
                    Modifier.size((150 + 120 * phase).dp).clip(CircleShape)
                        .border(1.5.dp, core.copy(alpha = (1f - phase) * 0.35f), CircleShape),
                )
            }
        }
        // Soft bloom halo behind the orb (mirrors the iOS radial glow).
        Box(
            Modifier.size(230.dp).scale(pulse)
                .background(Brush.radialGradient(listOf(core.copy(alpha = 0.28f), Color.Transparent))),
        )
        // Rotating conic shimmer ring while the companion is thinking.
        if (thinking && !reduceMotion) {
            Canvas(Modifier.size(178.dp).graphicsLayer { rotationZ = spin }) {
                drawCircle(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, core.copy(alpha = 0.1f), core, Color.Transparent)),
                    radius = size.minDimension / 2f - 3.dp.toPx(),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
        // The orb core, with an inner specular highlight (top-left light source).
        Box(
            Modifier.size(150.dp).scale(pulse).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White, core, PeriwinkleDeep)))
                .clickable(
                    onClickLabel = if (listening) stringResource(R.string.talk_orb_stop_cd)
                    else stringResource(R.string.talk_orb_talk_cd),
                ) { onTap() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(56.dp).offset(x = (-24).dp, y = (-24).dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent))),
            )
        }
    }
}
