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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerebrozen.app.R
import com.cerebrozen.app.audio.CloudVoice
import com.cerebrozen.app.audio.Player
import com.cerebrozen.app.audio.VoiceEngine
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.theme.Accent
import com.cerebrozen.app.ui.theme.BrandPrimary
import com.cerebrozen.app.ui.theme.BrandSecondary
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Danger
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Night
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.PeriwinkleDeep
import com.cerebrozen.app.ui.theme.Radius
import com.cerebrozen.app.ui.theme.Space
import com.cerebrozen.app.ui.theme.SurfaceRaised
import com.cerebrozen.app.ui.theme.TextFaint
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSecondary
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

internal data class Msg(val role: String, val text: String, val widget: ChatWidget? = null)

/** An inline activity the Oracle attached to a reply (cross-stack widget kinds). */
internal data class ChatWidget(val kind: String, val title: String, val description: String)

internal fun parseWidget(o: JSONObject?): ChatWidget? {
    val kind = o?.optString("widget_kind").orEmpty()
    if (kind.isBlank()) return null
    return ChatWidget(kind, o!!.optString("title"), o.optString("description"))
}

/** Where an inline activity lands on Android — every cross-stack widget kind
 * still has a native surface after the Toolkit consolidation. */
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

/** m:ss elapsed-session label — pure + testable. */
internal fun fmtSession(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

enum class TalkMode { Landing, Live, Chat }

/**
 * Talk — a voice companion first, a chat second.
 *
 * The Serene pass restructured the *frame* without touching the pipeline. The old
 * screen put the disclosure pill, the orb, the whole conversation, the SOS row and
 * the text composer inside ONE scrolling Column, then auto-scrolled that column to
 * `maxValue` on every new message. Three things fell out of that:
 *
 *  1. **The composer scrolled away.** It sat at the bottom of the page scroll, so
 *     `imePadding()` on the field could never dock it above the keyboard — you had
 *     to scroll to the bottom to type, while auto-scroll fought you.
 *  2. **The "always-visible" AI disclosure scrolled away too** — it is a regulatory
 *     affordance (NY companion-law floor) that was only visible at scroll offset 0.
 *  3. **The orb — the hero of the screen — scrolled off** the moment a conversation
 *     started, because auto-scroll jumped past it to the send button.
 *
 * Now: header, disclosure and the crisis banner are **pinned**; the thread is a
 * LazyColumn that owns the middle (so history is no longer silently capped at 12
 * messages); the composer is **docked** above the keyboard. The orb still leads the
 * thread — and when it does scroll out of view, the header's mic well takes over as
 * the voice affordance, so talking is never more than one tap away.
 */
@Composable
fun TalkScreen(
    onOpen: (String) -> Unit = {},
    mode: TalkMode = TalkMode.Landing,
    onBack: () -> Unit = {},
    onChat: () -> Unit = {},
) {
    var messages by remember { mutableStateOf(listOf<Msg>()) }
    // Draft survives rotation / process death so a half-typed message isn't lost.
    var draft by rememberSaveable { mutableStateOf("") }
    var chips by remember { mutableStateOf(listOf<String>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusFailed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // The thread owns its own scroll now — the page no longer scrolls as one piece.
    val chatList = rememberLazyListState()
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
    val journalEntryTitle = stringResource(R.string.talk_journal_entry_title)
    val savedStatus = stringResource(R.string.talk_saved_status)
    val saveFailed = stringResource(R.string.talk_save_failed)
    val voice = remember { VoiceEngine(context) }
    // Cloud voice (iOS-parity quality): Deepgram STT + ElevenLabs TTS via the
    // backend when the server has keys; the on-device engine stays fallback.
    val cloud = remember { CloudVoice(context) }
    var cloudVoice by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    // Immersive live session (ref LIVE VOICE SESSION overlay): opens on the
    // first voice turn, stays up across turns until End/Text.
    var voiceSession by remember { mutableStateOf(mode == TalkMode.Live) }
    var sessionMuted by rememberSaveable { mutableStateOf(false) }
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
    // Only bubbles that arrive AFTER the restored history animate in — the
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
        Haptics.success()   // a felt "reply's here" in voice mode
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
        busy = true; status = null; statusFailed = false
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
                statusFailed = true
            } finally {
                busy = false
            }
        }
    }

    // Now that send() exists, wire the post-speech resume: re-arm the mic for the
    // next turn while the voice session is still open (End/Text clears it).
    resumeTurn = {
        if (voiceSession && !sessionMuted) {
            if (cloudVoice) {
                if (!cloud.startRecording()) { status = micUnavailable; statusFailed = true }
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
                statusFailed = true
            } finally {
                busy = false
            }
        }
    }

    fun saveToJournal() {
        if (messages.isEmpty()) return
        scope.launch {
            runCatching { Api.createJournal(journalEntryTitle, talkTranscript(messages)) }
                .onSuccess { status = savedStatus; statusFailed = false }
                .onFailure { status = saveFailed; statusFailed = true }
        }
    }

    fun beginListening() {
        if (cloudVoice) {
            if (cloud.startRecording()) voiceSession = true
            else { status = micUnavailable; statusFailed = true }
        } else {
            voiceSession = true
            voice.startListening { t -> send(t, speak = true) }
        }
    }

    fun endSession() {
        if (cloud.recording) cloud.stopRecording()   // discard the open take
        cloud.stopPlayback()
        voice.cancelListening()
        sessionMuted = false
        voiceSession = false
    }

    /** Stop the cloud recording and run the full quality loop: STT → chat → TTS. */
    fun finishCloudTurn() {
        val bytes = cloud.stopRecording()
        if (bytes == null) { status = didntCatch; statusFailed = true; return }
        transcribing = true
        scope.launch {
            try {
                val transcript = Api.stt(bytes)
                if (transcript.isBlank()) { status = didntCatch; statusFailed = true }
                else send(transcript, speak = true)
            } catch (e: Exception) {
                status = e.message ?: transcribeFailed
                statusFailed = true
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
        else { status = micOff; statusFailed = true }
    }

    fun onOrbTap() {
        if (!voice.available && !cloudVoice) return
        Haptics.tap()
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

    // Follow the conversation as it grows — the newest turn stays in view. Scrolls
    // the THREAD, not the page, so the composer and the disclosure never move.
    LaunchedEffect(messages.size, streamText, busy) {
        val last = chatList.layoutInfo.totalItemsCount - 1
        if (last >= 0) chatList.animateScrollToItem(last)
    }

    val hasVoice = voice.available || cloudVoice
    val listening = voice.listening || cloud.recording
    val speaking = voice.speaking || cloud.speaking
    val thinking = transcribing || busy

    // Opening the live route starts the call flow immediately. The live surface is
    // already visible while Android asks for microphone permission, so the first
    // tap never appears to do nothing.
    LaunchedEffect(mode) {
        if (mode == TalkMode.Live && !listening && !speaking) {
            kotlinx.coroutines.delay(220)
            onOrbTap()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0D1424), Color(0xFF182447), Color(0xFF241A4A)),
                ),
            ),
    ) {
        TalkGlowBackdrop()
        when (mode) {
            TalkMode.Landing -> TalkLanding(
                hasVoice = hasVoice,
                onLive = { onOpen("talk/live") },
                onChat = { onOpen("talk/chat") },
                onDisclosure = { showDisclosure = true },
                onSos = { onOpen("toolkit") },
            )
            TalkMode.Live -> Unit
            TalkMode.Chat -> Column(Modifier.fillMaxSize()) {
            // ── Pinned chrome: identity + safety. Never scrolls. ──────────────
            Column(
                Modifier
                    .padding(horizontal = pageHorizontalPadding())
                    .padding(top = 28.dp),
                verticalArrangement = Arrangement.spacedBy(Space.item),
            ) {
                ChatHeader(onBack = onBack)

                // Honest offline truth for a connection-dependent surface — not
                // dismissible, and it points at what still works.
                if (Session.servedStale) {
                    InfoBanner(
                        icon = Icons.Outlined.CloudOff,
                        text = stringResource(R.string.talk_offline_banner),
                        actionLabel = stringResource(R.string.talk_offline_action),
                        onAction = { onOpen("toolkit") },
                    )
                }

                // Persistent AI disclosure. PINNED — it is a compliance affordance,
                // and in the old layout it lived inside the page scroll, so it was
                // only visible at scroll offset 0.
                DisclosurePill { showDisclosure = true }

                // Safety never scrolls away either.
                if (crisis) {
                    CrisisBanner { onOpen("crisis") }
                }
            }

            // ── The thread. Owns the middle; scrolls independently. ────────────
            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = chatList,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = pageHorizontalPadding(),
                    end = pageHorizontalPadding(),
                    top = Space.group,
                    bottom = if (messages.isEmpty()) Space.section else 92.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.item),
            ) {
                if (mode != TalkMode.Chat && hasVoice) {
                    item(key = "orb") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Space.group),
                        ) {
                            VoiceOrb(
                                listening = listening,
                                speaking = speaking,
                                onTap = { onOrbTap() },
                                thinking = thinking,
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
                            Text(
                                hint,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            // The live level, as a row of bars — the mock's waveform.
                            // Purely reactive to the mic; rests flat when idle.
                            VoiceLevelBars(
                                level = if (cloud.recording) cloudLevel else voice.level,
                                active = listening,
                            )
                        }
                    }
                }

                if (messages.isEmpty()) {
                    item(key = "empty") {
                        ChatEmptyCard()
                    }
                    if (starters.isNotEmpty()) {
                        item(key = "starters") {
                            Column(verticalArrangement = Arrangement.spacedBy(Space.group)) {
                                Text(
                                    stringResource(R.string.talk_starters_header),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Periwinkle,
                                )
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    starters.forEach { topic ->
                                        PremiumSuggestionPill(label = topic) { send(topic) }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "try-empty") { TryTogetherRow(onOpen) }
                } else {
                    // The full transcript. The old screen rendered `takeLast(12)`
                    // inside a verticalScroll — older turns were silently dropped
                    // with no way to reach them. A LazyColumn composes only what's
                    // on screen, so the whole history can stay.
                    item(key = "history-start") {
                        Text(
                            stringResource(R.string.talk_history_start),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextFaint,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    itemsIndexed(messages, key = { i, _ -> "msg-$i" }) { i, m ->
                        Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
                            ChatBubble(m, animate = i >= entranceFloor)
                            m.widget?.let { WidgetCard(it, onOpen) }
                        }
                    }
                    // Live reply: streamed tokens with a blinking caret, or a typing
                    // indicator while the companion is composing its answer.
                    if (streamText.isNotBlank()) {
                        item(key = "stream") { StreamingBubble(streamText) }
                    } else if (busy) {
                        item(key = "typing") { TypingDots() }
                    }
                    // Structured exercises as first-class offers (REDESIGN §3.3) —
                    // quiet, after the companion's latest reply, never while composing.
                    if (showTryTogether(messages.size, messages.lastOrNull()?.role, busy, streamText.isNotBlank())) {
                        item(key = "try") { TryTogetherRow(onOpen) }
                    }
                }

                // Confirm-before-write: the Oracle paused on a write tool (log_mood,
                // save_journal, …) — nothing happens without an explicit approval.
                confirmReq?.let { (_, summary) ->
                    item(key = "confirm") {
                        ConfirmCard(
                            summary = summary,
                            busy = busy,
                            onApprove = { resolveConfirm(true) },
                            onDecline = { resolveConfirm(false) },
                        )
                    }
                }

                if (chips.isNotEmpty()) {
                    item(key = "chips") {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            chips.forEach { label ->
                                PremiumSuggestionPill(label = label) { send(label) }
                            }
                        }
                    }
                }

                // Fast escape hatch when talking feels like too much (mirrors iOS).
                item(key = "sos") {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
                        SectionGap()
                        SosGlassCard { onOpen("toolkit") }
                    }
                }

                status?.let { line ->
                    item(key = "status") {
                        StatusLine(text = line, failed = statusFailed)
                    }
                }
            }

            // ── Docked composer. Always reachable; rides above the keyboard. ────
            if (messages.isNotEmpty()) {
                SaveToJournalAction(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = pageHorizontalPadding(), bottom = 12.dp),
                    onClick = { saveToJournal() },
                )
            }
            }

            Composer(
                draft = draft,
                onDraft = { draft = it },
                busy = busy,
                hasVoice = hasVoice,
                onSend = { send(draft) },
                onVoice = { onOpen("talk/live") },
            )
            }
        }

        if (showDisclosure) {
            AlertDialog(
                onDismissRequest = { showDisclosure = false },
                title = { Text(stringResource(R.string.talk_disclosure_dialog_title)) },
                text = { Text(stringResource(R.string.talk_disclosure_dialog_body)) },
                confirmButton = {
                    TextButton(onClick = { showDisclosure = false }) {
                        Text(stringResource(R.string.talk_disclosure_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDisclosure = false; onOpen("crisis") }) {
                        Text(stringResource(R.string.talk_disclosure_crisis))
                    }
                },
            )
        }

        // Ref LIVE VOICE SESSION: an immersive overlay that stays up across turns.
        if (mode == TalkMode.Live && voiceSession) {
            VoiceSessionOverlay(
                seconds = sessionSeconds,
                stateLabel = when {
                    transcribing -> stringResource(R.string.talk_hint_hearing)
                    busy -> stringResource(R.string.talk_hint_thinking)
                    speaking -> stringResource(R.string.talk_state_speaking_interrupt)
                    listening -> stringResource(R.string.talk_state_listening)
                    else -> stringResource(R.string.talk_state_orb)
                },
                listening = listening,
                speaking = speaking,
                thinking = thinking,
                level = if (cloud.recording) cloudLevel else voice.level,
                caption = streamText.ifBlank { messages.lastOrNull { it.role == "assistant" }?.text.orEmpty() },
                onOrb = { onOrbTap() },
                muted = sessionMuted,
                onMute = { muted ->
                    sessionMuted = muted
                    if (muted) {
                        if (cloud.recording) cloud.stopRecording()
                        voice.cancelListening()
                    } else {
                        onOrbTap()
                    }
                },
                onEnd = { endSession(); onBack() },
                // Returning to the text composer must tear down the live mic/recognizer
                // too — otherwise recording keeps running (and can still speak a reply)
                // after the user has left voice mode.
                onText = { endSession(); onChat() },
            )
        }
    }
}

// ── Chrome ───────────────────────────────────────────────────────────────

@Composable
private fun TalkGlowBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val first = Offset(size.width * 0.12f, size.height * 0.18f)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0x337A5CFF), Color.Transparent), first, size.width * 0.72f),
            radius = size.width * 0.72f,
            center = first,
        )
        val second = Offset(size.width, size.height * 0.72f)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0x2464C9FF), Color.Transparent), second, size.width * 0.78f),
            radius = size.width * 0.78f,
            center = second,
        )
    }
}

@Composable
private fun SaveToJournalAction(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .shadow(18.dp, shape, ambientColor = Color(0x55000000), spotColor = Color(0x55000000))
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xE77A5CFF), Color(0xE7A06EFF))))
            .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.talk_save_journal_short), style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

/** Voice-first landing from the reference: one hero orb, a quiet text fallback,
 * and the fast SOS exit. The waveform panel is the explicit Chat affordance. */
@Composable
private fun TalkLanding(
    hasVoice: Boolean,
    onLive: () -> Unit,
    onChat: () -> Unit,
    onDisclosure: () -> Unit,
    onSos: () -> Unit,
) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = stringResource(
        when (hour) {
            in 5..11 -> R.string.talk_good_morning
            in 12..16 -> R.string.talk_good_afternoon
            else -> R.string.talk_good_evening
        },
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = pageHorizontalPadding(),
            end = pageHorizontalPadding(),
            top = 30.dp,
            bottom = 36.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(greeting.uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(0xFF64C9FF))
                Text(
                    stringResource(R.string.talk_title),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                )
                Text(
                    stringResource(R.string.talk_home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }
        item { DisclosurePill(onDisclosure) }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                VoiceOrb(
                    listening = false,
                    speaking = false,
                    thinking = false,
                    level = 0f,
                    onTap = onLive,
                )
                AmbientWaveform()
                PulsingTalkPrompt(onClick = onLive)
            }
        }
        item {
            val shape = RoundedCornerShape(28.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(18.dp, shape, ambientColor = Color(0x44000000), spotColor = Color(0x44000000))
                    .clip(shape)
                    .background(Color(0x8426304A))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.talk_open_chat_cd)) {
                        Haptics.tap(); onChat()
                    }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0x557A5CFF), Color(0x3364C9FF))))
                        .border(1.dp, Color(0x667A5CFF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = Color.White)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.talk_chat_cta), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(stringResource(R.string.talk_landing_body), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = Color(0xFFB18CFF))
            }
        }
        item {
            SosGlassCard(onClick = onSos)
        }
    }
}

@Composable
private fun PulsingTalkPrompt(onClick: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "talkPrompt")
    val alpha by transition.animateFloat(
        0.62f, 1f,
        infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "talkPromptAlpha",
    )
    Text(
        stringResource(R.string.talk_tap_start),
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = if (reduceMotion) 0.9f else alpha),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
private fun AmbientWaveform() {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "ambientWave")
    Row(
        Modifier.height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(15) { index ->
            val peak by transition.animateFloat(
                initialValue = 5f,
                targetValue = 10f + (index % 5) * 2.8f,
                animationSpec = infiniteRepeatable(
                    tween(750 + index * 35, delayMillis = index * 45),
                    RepeatMode.Reverse,
                ),
                label = "ambientBar$index",
            )
            Box(
                Modifier
                    .size(3.dp, (if (reduceMotion) 7f else peak).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF64C9FF), Color(0xFFB18CFF)))),
            )
        }
    }
}

@Composable
private fun SosGlassCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(22.dp, shape, ambientColor = Color(0x33FF6B81), spotColor = Color(0x33FF6B81))
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0x33FF6B81), Color(0x8226304A))))
            .border(1.dp, Color(0x66FF6B81), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color(0x33FF6B81)),
            contentAlignment = Alignment.Center,
        ) {
            Text("SOS", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFF9AAA))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.talk_sos_title), style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(stringResource(R.string.talk_sos_subtitle), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = Color(0xFFFF9AAA))
    }
}

@Composable
private fun ChatHeader(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, Color.White.copy(alpha = 0.13f), CircleShape)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.talk_back)) { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(19.dp))
            }
            Text(
                stringResource(R.string.talk_chat_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
        Text(
            stringResource(R.string.talk_chat_eyebrow).uppercase(),
            modifier = Modifier.padding(start = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.4.sp),
            color = Color(0xFF64C9FF),
        )
    }
}

/** The always-visible AI disclosure. Tap for the full points. */
@Composable
private fun DisclosurePill(onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.field)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .shadow(14.dp, shape, ambientColor = Color(0x28000000), spotColor = Color(0x28000000))
            .clip(shape)
            .background(Color(0x6626304A))
            .border(1.dp, Color.White.copy(alpha = 0.11f), shape)
            .clickable(role = Role.Button) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.talk_disclosure_pill),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.talk_disclosure_details) + " ›",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFB18CFF),
        )
    }
}

/** The crisis affordance — calm, warm, and impossible to miss, but never alarming. */
@Composable
private fun CrisisBanner(onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.card)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Danger.copy(alpha = 0.12f))
            .border(1.dp, Danger.copy(alpha = 0.42f), shape)
            .clickable(role = Role.Button) { onClick() }
            .padding(cardPadding()),
        verticalArrangement = Arrangement.spacedBy(Space.tight),
    ) {
        Text(
            stringResource(R.string.talk_crisis_title),
            style = MaterialTheme.typography.titleMedium,
            color = Danger,
        )
        Text(
            stringResource(R.string.talk_crisis_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

/**
 * The docked composer. A field and a circular send button, riding above the
 * keyboard and the nav pill.
 *
 * Previously this was a labelled field plus a full-width [PrimaryButton] at the
 * very bottom of the page scroll. Two problems: a full-width primary button gave
 * *typing* the same visual weight as the check-in CTA on Home (in a voice-first
 * screen, typing is the fallback), and being inside the scroll meant it could not
 * dock. Now it is chrome: quiet until you have something to send.
 */
@Composable
private fun Composer(
    draft: String,
    onDraft: (String) -> Unit,
    busy: Boolean,
    hasVoice: Boolean,
    onSend: () -> Unit,
    onVoice: () -> Unit,
) {
    val canSend = draft.isNotBlank() && !busy
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0xF20D1424)),
                ),
            )
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = pageHorizontalPadding(), vertical = 12.dp),
    ) {
        val shape = RoundedCornerShape(30.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(22.dp, shape, ambientColor = Color(0x66000000), spotColor = Color(0x55000000))
                .clip(shape)
                .background(Color(0xE626304A))
                .border(1.dp, Color.White.copy(alpha = 0.13f), shape)
                .animateContentSize()
                .padding(start = 18.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val placeholder = if (hasVoice) {
                stringResource(R.string.talk_type_instead)
            } else {
                stringResource(R.string.talk_type_message)
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraft,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 124.dp)
                    .padding(vertical = 11.dp),
                enabled = !busy,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                cursorBrush = Brush.verticalGradient(listOf(Color(0xFF64C9FF), Color(0xFFB18CFF))),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                maxLines = 5,
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (draft.isEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = TextFaint)
                        }
                        inner()
                    }
                },
            )
            ComposerAction(
                enabled = if (draft.isBlank()) hasVoice else canSend,
                active = canSend,
                icon = if (draft.isBlank()) Icons.Outlined.Mic else Icons.AutoMirrored.Filled.Send,
                label = if (draft.isBlank()) stringResource(R.string.talk_orb_talk_cd) else stringResource(R.string.talk_send_cd),
                onClick = {
                    if (draft.isBlank()) onVoice() else onSend()
                },
            )
        }
    }
}

@Composable
private fun ComposerAction(
    enabled: Boolean,
    active: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .pressScale(pressed, down = 0.88f)
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (active) Brush.linearGradient(listOf(Color(0xFF7A5CFF), Color(0xFFB18CFF)))
                else Brush.linearGradient(listOf(Color(0x334F5C86), Color(0x334F5C86))),
            )
            .border(1.dp, Color.White.copy(alpha = if (active) 0.2f else 0.1f), CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
            ) { Haptics.soft(0.6f); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Color.White else TextFaint, modifier = Modifier.size(21.dp))
    }
}

/** Success and failure must not look alike — colour, icon and haptic all differ. */
@Composable
private fun StatusLine(text: String, failed: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (failed) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = if (failed) Danger else Ok,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (failed) Danger else Ok,
        )
    }
}

/** The Oracle paused on a write tool — nothing happens without explicit approval. */
@Composable
private fun ConfirmCard(summary: String, busy: Boolean, onApprove: () -> Unit, onDecline: () -> Unit) {
    val shape = RoundedCornerShape(Radius.card)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardFill)
            .border(1.dp, Periwinkle.copy(alpha = 0.5f), shape)
            .padding(cardPadding()),
        verticalArrangement = Arrangement.spacedBy(Space.tight),
    ) {
        Text(
            stringResource(R.string.talk_confirm_header),
            style = MaterialTheme.typography.labelSmall,
            color = Periwinkle,
        )
        Text(summary, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !busy, onClick = onApprove) {
                Text(stringResource(R.string.talk_approve), color = Cyan)
            }
            TextButton(enabled = !busy, onClick = onDecline) {
                Text(stringResource(R.string.talk_not_now), color = TextMuted)
            }
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
    muted: Boolean,
    onMute: (Boolean) -> Unit,
    onEnd: () -> Unit,
    onText: () -> Unit,
) {
    val shortStatus = stringResource(
        when {
            thinking -> R.string.talk_status_thinking
            speaking -> R.string.talk_status_speaking
            listening -> R.string.talk_status_listening
            else -> R.string.talk_status_ready
        },
    )
    Column(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF241A4A), Color(0xFF182447), Color(0xFF0D1424)),
                ),
            )
            // Actually swallow taps aimed behind the overlay (a disabled
            // clickable does not intercept touches).
            .clickable(
                enabled = true,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {}
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("●  " + stringResource(R.string.talk_live_session), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(fmtSession(seconds), style = MaterialTheme.typography.displayLarge, color = TextPrimary)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            VoiceOrb(listening = listening, speaking = speaking, onTap = onOrb, thinking = thinking, level = level)
            Text(shortStatus, style = MaterialTheme.typography.titleMedium, color = Color.White)
            SessionWaveform(level = level, active = listening || speaking)
            LiveResponseCard(caption.ifBlank { stateLabel })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CallControl(
                Icons.Outlined.Mic,
                stringResource(if (muted) R.string.talk_unmute else R.string.talk_mute),
                TextPrimary,
                CardFill,
                onClick = { onMute(!muted) },
            )
            CallControl(Icons.Filled.CallEnd, stringResource(R.string.talk_end), Color.White, Danger, onEnd, prominent = true)
            CallControl(Icons.Outlined.Keyboard, stringResource(R.string.talk_text), TextSoft, CardFill, onText)
        }
    }
}

@Composable
private fun LiveResponseCard(text: String) {
    val shape = RoundedCornerShape(26.dp)
    Text(
        text.take(180),
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
            .clip(shape)
            .background(Color(0x662A3450))
            .border(1.dp, Color.White.copy(alpha = 0.1f), shape)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

@Composable
private fun SessionWaveform(level: Float, active: Boolean) {
    val reduceMotion = rememberReduceMotion()
    Row(
        Modifier.height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(11) { index ->
            val centre = 5f
            val falloff = 1f - kotlin.math.abs(index - centre) / 8f
            val target = if (!active || reduceMotion) 5f else 7f + 24f * level.coerceIn(0.15f, 1f) * falloff
            val height by androidx.compose.animation.core.animateFloatAsState(target, tween(90), label = "sessionBar$index")
            Box(
                Modifier
                    .size(4.dp, height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Periwinkle),
            )
        }
    }
}

/** A round live-call control — a circular glyph button with a label below
 * (End / Text), echoing the fork's call-session look on our tokens. */
@Composable
private fun CallControl(
    icon: ImageVector,
    label: String,
    tint: Color,
    bg: Color,
    onClick: () -> Unit,
    prominent: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.tight),
    ) {
        Box(
            Modifier
                .pressScale(pressed, down = 0.88f)
                .size(if (prominent) 72.dp else 62.dp)
                .shadow(if (prominent) 22.dp else 12.dp, CircleShape, ambientColor = bg.copy(alpha = 0.4f), spotColor = bg.copy(alpha = 0.4f))
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, tint.copy(alpha = 0.4f), CircleShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

/**
 * The live mic level as a row of bars — the waveform in the reference design.
 *
 * It is honest: the bars are driven by the actual mic amplitude, so they rest flat
 * when nothing is being said. A decorative always-dancing waveform would imply the
 * app is hearing you when it isn't, which is exactly the wrong signal on a screen
 * about being listened to. Reduce Motion collapses it to a steady, quiet baseline.
 */
@Composable
private fun VoiceLevelBars(level: Float, active: Boolean, bars: Int = 13) {
    val reduceMotion = rememberReduceMotion()
    val shape = RoundedCornerShape(Radius.field)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(shape)
            .background(SurfaceRaised)
            .border(1.dp, LineStroke, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(bars) { i ->
            // A soft bell across the row, so the centre bars ride highest — the
            // shape a voice actually makes, rather than a flat block.
            val centre = (bars - 1) / 2f
            val falloff = 1f - (kotlin.math.abs(i - centre) / (centre + 1f)) * 0.65f
            val h = if (!active || reduceMotion) {
                4f
            } else {
                4f + (level.coerceIn(0f, 1f) * 26f * falloff)
            }
            val animated by androidx.compose.animation.core.animateFloatAsState(
                targetValue = h,
                animationSpec = tween(90),
                label = "bar$i",
            )
            Box(
                Modifier
                    .size(width = 3.dp, height = animated.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (active) Brush.verticalGradient(listOf(BrandSecondary, BrandPrimary)) else Brush.linearGradient(listOf(TextFaint, TextFaint))),
            )
        }
    }
}

/** A quiet row of structured exercises the companion can do with you — CBT
 * reframe, paced breathing, grounding (the evidenced spine; chat is the glue). */
@Composable
private fun ChatEmptyCard() {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
            .clip(shape)
            .background(Color(0x7026304A))
            .border(1.dp, Color.White.copy(alpha = 0.11f), shape)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0x667A5CFF), Color(0x4464C9FF)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = Color.White)
        }
        Text(stringResource(R.string.talk_empty_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(stringResource(R.string.talk_empty_subtitle), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun TryTogetherRow(onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.group)) {
        Text(
            stringResource(R.string.talk_try_together),
            style = MaterialTheme.typography.labelSmall,
            color = Periwinkle,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PremiumSuggestionPill(label = stringResource(R.string.talk_chip_reframe)) { onOpen("cbt") }
            PremiumSuggestionPill(label = stringResource(R.string.talk_chip_breathe)) { onOpen("breathe/box") }
            PremiumSuggestionPill(label = stringResource(R.string.talk_chip_ground)) { onOpen("toolkit") }
        }
    }
}

@Composable
private fun PremiumSuggestionPill(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = Modifier
            .clip(shape)
            .background(Color(0x742F3655))
            .border(1.dp, Color(0x557A5CFF), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    )
}

/** An Oracle-suggested inline activity: title/description + a native surface
 * when Android has one, else the honest iOS-only note (mirrors the web card). */
@Composable
private fun WidgetCard(w: ChatWidget, onOpen: (String) -> Unit) {
    val shape = RoundedCornerShape(Radius.card)
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(CardFill)
            .border(1.dp, LineStroke, shape)
            .padding(cardPadding()),
        verticalArrangement = Arrangement.spacedBy(Space.tight),
    ) {
        Text(
            stringResource(R.string.talk_suggested_activity),
            style = MaterialTheme.typography.labelSmall,
            color = Periwinkle,
        )
        Text(w.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(w.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        val route = widgetRoute(w.kind)
        if (route != null) {
            TextButton(onClick = { onOpen(route) }) {
                Text(stringResource(R.string.common_open), color = Cyan)
            }
        } else {
            Text(
                stringResource(R.string.talk_ios_only),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted2,
            )
        }
    }
}

/**
 * One chat bubble. [animate] arms a one-shot 150ms rise+fade for bubbles that
 * arrive during this session; restored history renders settled.
 *
 * Max width is now a fraction of the screen rather than a hardcoded 320dp — on a
 * small phone that constant was wider than the content column, so user bubbles
 * never actually looked right-aligned.
 */
@Composable
private fun ChatBubble(m: Msg, animate: Boolean = false) {
    val user = m.role == "user"
    val entrance = if (animate) Modifier.appear(rise = 8f, durationMs = 150) else Modifier
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
    Row(
        Modifier.fillMaxWidth().then(entrance),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
    ) {
        val shape = RoundedCornerShape(
            topStart = 20.dp, topEnd = 20.dp,
            bottomStart = if (user) 20.dp else 6.dp,
            bottomEnd = if (user) 6.dp else 20.dp,
        )
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .shadow(12.dp, shape, ambientColor = Color(0x28000000), spotColor = Color(0x28000000))
                .clip(shape)
                .background(
                    if (user) Brush.linearGradient(listOf(Color(0xFF6950E8), Color(0xFF8C64F2)))
                    else Brush.linearGradient(listOf(Color(0xC42A3450), Color(0xB8232B44))),
                )
                .border(1.dp, Color.White.copy(alpha = if (user) 0.13f else 0.1f), shape),
        ) {
            Text(
                m.text,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 17.dp, vertical = 14.dp),
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
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = Color(0xC42A3450),
            shape = shape,
            modifier = Modifier.widthIn(max = maxWidth).border(1.dp, Color.White.copy(alpha = 0.1f), shape),
        ) {
            Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
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
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = Color(0xC42A3450),
            shape = shape,
            modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), shape),
        ) {
            Row(
                Modifier.padding(horizontal = 17.dp, vertical = 15.dp),
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
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Periwinkle.copy(alpha = a)))
                }
            }
        }
    }
}

/**
 * The orb. The hero of this screen, and the thing the whole tab is about.
 *
 * Grown 150dp → 176dp core (210dp → 250dp frame) so it reads as the subject rather
 * than an icon, matching the reference. All state logic is unchanged: the tint
 * carries the phase (thinking = iris, voice active = sky, resting = lavender),
 * ripples radiate while listening, a conic shimmer turns while thinking, and the
 * swell is driven by real mic amplitude.
 */
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
    // Phase tint: thinking = iris, voice active = sky, resting = brand lavender.
    val core = when {
        thinking -> Iris
        listening || speaking -> BrandSecondary
        else -> BrandPrimary
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
    val floatY by t.animateFloat(
        -5f, 5f,
        infiniteRepeatable(tween(2400), RepeatMode.Reverse),
        label = "orbFloat",
    )

    val basePulse = if (reduceMotion) 1f else animatedPulse
    // Mic-reactive swell on top of the breathing pulse (listening only).
    val pulse = if (reduceMotion || !listening) basePulse else basePulse + level * 0.16f

    Box(
        Modifier
            .fillMaxWidth()
            .height(250.dp)
            .graphicsLayer { translationY = if (reduceMotion) 0f else floatY.dp.toPx() },
        contentAlignment = Alignment.Center,
    ) {
        if (!listening) {
            Box(Modifier.size(214.dp).clip(CircleShape).border(1.dp, core.copy(alpha = 0.14f), CircleShape))
            Box(Modifier.size(242.dp).clip(CircleShape).border(1.dp, core.copy(alpha = 0.07f), CircleShape))
        }
        // Expanding ripple rings while listening.
        if (listening && !reduceMotion) {
            for (i in 0 until 3) {
                val phase = (ripple + i / 3f) % 1f
                Box(
                    Modifier.size((176 + 130 * phase).dp).clip(CircleShape)
                        .border(1.5.dp, core.copy(alpha = (1f - phase) * 0.35f), CircleShape),
                )
            }
        }
        // Soft bloom halo behind the orb.
        Box(
            Modifier.size(268.dp).scale(pulse)
                .background(Brush.radialGradient(listOf(core.copy(alpha = 0.30f), Color.Transparent))),
        )
        // Rotating conic shimmer ring while the companion is thinking.
        if (thinking && !reduceMotion) {
            Canvas(Modifier.size(208.dp).graphicsLayer { rotationZ = spin }) {
                drawCircle(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, core.copy(alpha = 0.1f), core, Color.Transparent)),
                    radius = size.minDimension / 2f - 3.dp.toPx(),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
        // The orb core, with an inner specular highlight (top-left light source).
        Box(
            Modifier.size(176.dp).scale(pulse).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White, core, PeriwinkleDeep)))
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (listening) {
                        stringResource(R.string.talk_orb_stop_cd)
                    } else {
                        stringResource(R.string.talk_orb_talk_cd)
                    },
                ) { onTap() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(64.dp).offset(x = (-28).dp, y = (-28).dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent))),
            )
        }
    }
}
