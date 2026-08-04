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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.outlined.ArrowDownward
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.withLink
import com.cerebrozen.app.ui.theme.ButtonDisabled
import com.cerebrozen.app.ui.theme.Gradients
import com.cerebrozen.app.ui.theme.OnPrimary
import androidx.compose.material.icons.outlined.BookmarkBorder
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
import java.time.LocalDate

internal data class Msg(
    val role: String,
    val text: String,
    val widget: ChatWidget? = null,
    /** ISO created_at from the server; "" for bubbles minted locally this session. */
    val createdAt: String = "",
)

/** The day-separator label slot above message [i]: "Today", "Yesterday", or a
 * date — null when message [i] shares its calendar day with message [i-1].
 * Local bubbles (no stamp) inherit the previous day and never force a label.
 * Pure; the composable maps the sentinel values to localized strings. */
internal fun daySeparator(messages: List<Msg>, i: Int, today: LocalDate): String? {
    fun dayOf(iso: String): LocalDate? = runCatching {
        java.time.OffsetDateTime.parse(iso)
            .atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate()
    }.getOrNull()
    val day = dayOf(messages[i].createdAt) ?: return null
    val prevDay = (i - 1 downTo 0).firstNotNullOfOrNull { dayOf(messages[it].createdAt) }
    if (prevDay == day) return null
    return when (day) {
        today -> "TODAY"
        today.minusDays(1) -> "YESTERDAY"
        else -> day.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
    }
}

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
    // Straight to the exercise since it has its own screen (2026-08-03) —
    // the hub was a detour when the companion just suggested grounding.
    "grounding" -> "ground"
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
        Msg(m.getString("role"), m.getString("text"), createdAt = m.optString("created_at"))
    }

/** Client-side archive line: Talk had no way to start a new conversation, so
 * last month's thread greeted every open. Messages at/before the cleared stamp
 * stay on the server (the record stays honest) but stop rendering; local
 * bubbles (blank stamp) always show. Pure. */
internal fun visibleAfterClear(messages: List<Msg>, clearedIso: String?): List<Msg> {
    if (clearedIso.isNullOrBlank()) return messages
    val cut = runCatching { java.time.OffsetDateTime.parse(clearedIso) }.getOrNull() ?: return messages
    return messages.filter { m ->
        val t = runCatching { java.time.OffsetDateTime.parse(m.createdAt) }.getOrNull()
        t == null || t.isAfter(cut)
    }
}

/** "Try together" chips, ordered by the moment instead of a fixed list: words
 * that sound like spiralling put grounding first, the late evening puts
 * breathing first, and the default keeps the CBT reframe lead. Pure. */
internal fun tryTogetherOrder(hour: Int, lastUserText: String?): List<String> {
    val t = lastUserText.orEmpty().lowercase()
    return when {
        listOf("anxi", "panic", "spiral", "overwhelm", "racing").any { it in t } ->
            listOf("ground", "breathe", "reframe")
        hour >= 20 || hour < 5 -> listOf("breathe", "ground", "reframe")
        else -> listOf("reframe", "breathe", "ground")
    }
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

// ── AI-disclosure cadence (NY companion-law floor: re-disclose every 3h) ──

/** Where the last full-sheet disclosure timestamp lives. A wall-clock stamp in
 * prefs, so it survives tab switches, backgrounding and process death — the
 * things a coroutine-scoped timer does not. */
internal const val DISCLOSURE_LAST_SHOWN_KEY = "talk_disclosure_last_shown_ms"
internal const val DISCLOSURE_INTERVAL_MS = 3L * 60 * 60 * 1000
/** How often the Talk tab re-checks the clock while it's on screen. */
internal const val DISCLOSURE_CHECK_INTERVAL_MS = 60L * 1000

/** Whether the disclosure sheet is due. Pure + unit-tested. A clock that ran
 * backwards (timezone/NTP correction) counts as due rather than silently
 * postponing the disclosure — safety copy errs toward showing. */
internal fun disclosureDue(lastShownMs: Long, nowMs: Long): Boolean =
    lastShownMs <= 0L || nowMs < lastShownMs || nowMs - lastShownMs >= DISCLOSURE_INTERVAL_MS

/** Whether the "Try together" exercise offers show mid-conversation (REDESIGN §3.3):
 * after the most recent assistant reply, once a real exchange exists, and never
 * while the companion is composing. Pure + unit-tested. */
internal fun showTryTogether(messageCount: Int, lastRole: String?, busy: Boolean, streaming: Boolean): Boolean =
    messageCount >= 2 && lastRole == "assistant" && !busy && !streaming

/** The last few turns as a journal body (mirrors iOS "Save to journal").
 * Prefixes arrive as parameters so the saved entry speaks the UI's language. */
internal fun talkTranscript(
    messages: List<Msg>,
    take: Int = 8,
    mePrefix: String = "Me: ",
    botPrefix: String = "CereBro: ",
): String =
    messages.takeLast(take).joinToString("\n\n") { m ->
        (if (m.role == "user") mePrefix else botPrefix) + m.text
    }

/** Character ranges in [text] that read as phone numbers (3+ consecutive
 * digits, optionally dash/space-grouped) — the crisis suffix ships "Tele-MANAS
 * mental health support (14416)" as plain text and nothing was tappable on the
 * one line that should be. Single digits and "4-7-8" never match. Pure. */
internal fun phoneSpans(text: String): List<IntRange> =
    Regex("""\d{3,}(?:[- ]\d{3,4})*""").findAll(text).map { it.range }.toList()

/** Light markdown neutralizer for LLM replies — a live model emits
 * **emphasis** and headings; bubbles rendered the asterisks literally. Pure. */
internal fun stripMarkdownLite(s: String): String = s
    .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
    .replace(Regex("""(?<![\w*])\*([^*\n]+)\*(?![\w*])"""), "$1")
    .replace(Regex("""^[ \t]*#{1,4}[ \t]+""", RegexOption.MULTILINE), "")

/** Display resource for a KNOWN server chip label — chips arrive as English
 * wire strings; the display localizes, the RAW label is what gets sent back
 * (the server's routing keywords are English). Null renders the label raw. */
@androidx.annotation.StringRes
internal fun chipLabelResFor(label: String): Int? = when (label) {
    "Urgent support" -> R.string.chip_urgent_support
    "Breathe with me" -> R.string.chip_breathe
    "Try grounding" -> R.string.chip_grounding
    "Write it down" -> R.string.chip_write
    "Check in" -> R.string.chip_checkin
    "One good thing" -> R.string.chip_one_good
    "Set an intention" -> R.string.chip_intention
    "Talk to a person" -> R.string.chip_human
    else -> null
}

/** Local wall-clock "HH:mm" for a bubble stamp, or null. Pure-ish (zone). */
internal fun clockLabel(iso: String?): String? = runCatching {
    java.time.OffsetDateTime.parse(iso)
        .atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}.getOrNull()

/** Which art family a widget card wears (the cards were bare text). Pure. */
internal fun widgetArtKind(kind: String): String = when (kind) {
    "breathing", "grounding", "dbt_skill" -> "meditation"
    "sleep_checkin" -> "sleep"
    else -> "program"
}

/** Talk: a real voice companion (on-device speech ↔ TTS over /chat) with a
 * text fallback. Same deterministic, safety-scanned pipeline as iOS/web. */
@Composable
fun TalkScreen(onOpen: (String) -> Unit = {}) {
    var messages by remember { mutableStateOf(listOf<Msg>()) }
    // Draft survives rotation / process death so a half-typed message isn't lost.
    var draft by rememberSaveable { mutableStateOf("") }
    // label → action pairs: the ACTION routes ("crisis" opens the crisis
    // screen, "human_support" the directory) — chips used to drop the action
    // at parse and send "Urgent support" back as chat text.
    var chips by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    // How many messages the thread window shows; "Show earlier" widens it —
    // takeLast(12) used to make older messages unreachable in-app.
    var windowSize by remember { mutableIntStateOf(12) }
    var status by remember { mutableStateOf<String?>(null) }
    // Set when the free daily cap is hit; rendered as its own calm card rather
    // than an error string. Never set by the IP rate limiter.
    var freeLimit by remember { mutableStateOf<Session.FreeLimitException?>(null) }
    var busy by remember { mutableStateOf(false) }
    // The message a failed send was carrying, so "Try sending again" can resend
    // it verbatim — the composer clears on send, so without this a network blip
    // meant retyping from memory.
    var failedText by remember { mutableStateOf<String?>(null) }
    // Auto-scroll the conversation to the newest reply / streaming tokens.
    val chatScroll = rememberScrollState()
    // Regulatory UX (mirrors iOS AIDisclosure): tappable always-visible pill +
    // a re-shown sheet every 3 h of continuous use (NY companion-law floor).
    var showDisclosure by remember { mutableStateOf(false) }
    // Sticky once a reply carries crisis risk — the affordance stays reachable.
    // Backed by a PREF, not just saveable state: a conversation containing a
    // disclosure kept losing its resources card on app restart. Cleared only
    // by starting a fresh conversation.
    var crisis by rememberSaveable {
        mutableStateOf(runCatching { Session.prefGet("talk_crisis_sticky") == "1" }.getOrDefault(false))
    }
    LaunchedEffect(crisis) {
        if (crisis) runCatching { Session.prefPut("talk_crisis_sticky", "1") }
    }
    // The clock is a PERSISTED timestamp, not this coroutine's lifetime: the tabs
    // navigate with saveState/restoreState, so Talk's composition is thrown away
    // and rebuilt on every tab switch — a composition-scoped 3h timer restarted
    // each time and, for anyone who switches tabs, would never have fired.
    LaunchedEffect(Unit) {
        while (true) {
            val last = runCatching { Session.prefGet(DISCLOSURE_LAST_SHOWN_KEY)?.toLongOrNull() }.getOrNull()
            val now = System.currentTimeMillis()
            when {
                // First ever Talk visit: start the clock. The funnel already
                // disclosed — don't interrupt the opening seconds to repeat it.
                last == null || last <= 0L ->
                    runCatching { Session.prefPut(DISCLOSURE_LAST_SHOWN_KEY, now.toString()) }
                disclosureDue(last, now) -> {
                    showDisclosure = true
                    runCatching { Session.prefPut(DISCLOSURE_LAST_SHOWN_KEY, now.toString()) }
                }
            }
            kotlinx.coroutines.delay(DISCLOSURE_CHECK_INTERVAL_MS)
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
    // Voice failures used to be silent — the orb simply stopped. Each one now
    // says something true, and always points at typing as the way through.
    val voiceNetwork = stringResource(R.string.talk_voice_network)
    val voiceBusy = stringResource(R.string.talk_voice_busy)
    val voiceFailed = stringResource(R.string.talk_voice_failed)
    val voiceNoSpeech = stringResource(R.string.talk_voice_no_speech)
    val voice = remember { VoiceEngine(context) }
    // Cloud voice (iOS-parity quality): Deepgram STT + ElevenLabs TTS via the
    // backend when the server has keys; the on-device engine stays fallback.
    val cloud = remember { CloudVoice(context) }
    var cloudVoice by remember { mutableStateOf(false) }
    // The device's TTS refused every language it was offered — say so once,
    // rather than letting the companion silently stop speaking back.
    val ttsUnavailable = stringResource(R.string.talk_tts_unavailable)
    LaunchedEffect(voice.ttsAvailable, cloudVoice) {
        if (!voice.ttsAvailable && !cloudVoice) status = ttsUnavailable
    }
    var transcribing by remember { mutableStateOf(false) }
    // Immersive live session (ref LIVE VOICE SESSION overlay): opens on the
    // first voice turn, stays up across turns until End/Text.
    var voiceSession by remember { mutableStateOf(false) }
    var sessionSeconds by remember { mutableStateOf(0) }
    // Live mic level for the cloud recording path (the on-device path uses voice.level).
    var cloudLevel by remember { mutableStateOf(0f) }
    // Duck whichever ambience is playing under the companion's voice — the
    // mixer used to keep full volume while CereBro spoke.
    LaunchedEffect(voice.speaking, cloud.speaking) {
        val speaking = voice.speaking || cloud.speaking
        if (Player.isPlaying) Player.duck(context, speaking)
        if (com.cerebrozen.app.audio.SoundscapeMixer.isPlaying) {
            com.cerebrozen.app.audio.SoundscapeMixer.duck(context, speaking)
        }
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
    // Presence + honesty lines: who is talking, and whether it remembers.
    var companionName by remember { mutableStateOf("") }
    var memoryOn by remember { mutableStateOf<Boolean?>(null) }
    // Today's check-in seeds a personal opener on the empty state.
    var todayMoodTalk by remember { mutableStateOf<String?>(null) }
    // The memory line follows the consent LIVE: toggling ai_memory in Privacy
    // and returning used to show the stale value until the next cold open.
    run {
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    scope.launch {
                        runCatching { memoryOn = Api.consent().optBoolean("ai_memory", false) }
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }
    // Agentic Oracle (SSE) when the server has it; deterministic /chat otherwise.
    var useOracle by remember { mutableStateOf(false) }
    var streamText by remember { mutableStateOf("") }
    var confirmReq by remember { mutableStateOf<Pair<String, String>?>(null) } // threadId → summary
    // W10: only bubbles that arrive AFTER the restored history animate in — the
    // transcript renders settled, new turns rise gently. Int.MAX_VALUE until the
    // history load resolves, so nothing animates prematurely.
    var entranceFloor by remember { mutableIntStateOf(Int.MAX_VALUE) }
    LaunchedEffect(Unit) {
        runCatching {
            messages = visibleAfterClear(
                parseChat(Api.chat()),
                runCatching { Session.prefGet("talk_clear_before") }.getOrNull(),
            )
        }
        entranceFloor = messages.size
        // Empty chat → grounded conversation starters (mirrors the iOS rail).
        if (messages.isEmpty()) runCatching { starters = parseStarters(Api.starters()) }
        runCatching { companionName = Api.me().optString("companion") }
        runCatching { memoryOn = Api.consent().optBoolean("ai_memory", false) }
        runCatching {
            val latest = Api.moods().optJSONObject(0)
            todayMoodTalk = latest?.takeIf { isToday(it.optString("created_at")) }
                ?.optString("mood")?.takeIf { it.isNotBlank() }
        }
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
            // Resume the mic only after playback actually completes. CloudVoice
            // reports setup/start failures so the keyless device TTS can speak
            // instead of silently treating a swallowed MediaPlayer error as success.
            val spoke = runCatching {
                cloud.play(Api.tts(text)) { scope.launch { resumeTurn() } }
            }.getOrDefault(false)
            if (!spoke) voice.speak(text) { scope.launch { resumeTurn() } }
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
        busy = true; status = null; failedText = null
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
                    val final = try {
                        consume("/oracle/messages", JSONObject().put("text", text.trim()))
                    } catch (e: Session.FreeLimitException) {
                        throw e
                    } catch (_: Exception) {
                        // The stream failed mid-flight: fall back to the
                        // deterministic path with the SAME words, so the typed
                        // message is never lost (reference send-path pattern —
                        // the retry chip is now the last resort, not the first).
                        val reply = Api.sendChat(text.trim())
                        val replyText = reply.getJSONObject("reply").getString("text")
                        messages = messages + Msg("assistant", replyText)
                        val suggestions = reply.optJSONArray("suggestions")
                        chips = suggestions?.let { arr ->
                            (0 until arr.length()).map {
                                val s = arr.getJSONObject(it)
                                s.getString("label") to s.optString("action")
                            }
                        } ?: emptyList()
                        if (hasCrisisSuggestion(suggestions)) crisis = true
                        replyText
                    }
                    if (speak) speakReply(final) else com.cerebrozen.app.ui.Haptics.tap()
                } else {
                    // Optimistic bubble: your words appear the moment you send
                    // — they used to vanish from the composer and appear
                    // NOWHERE for the whole round-trip.
                    messages = messages + Msg("user", text.trim())
                    val reply: JSONObject = Api.sendChat(text.trim())
                    val replyText = reply.getJSONObject("reply").getString("text")
                    messages = messages + Msg("assistant", replyText)
                    val suggestions = reply.optJSONArray("suggestions")
                    chips = suggestions?.let { arr ->
                        (0 until arr.length()).map {
                            val s = arr.getJSONObject(it)
                            s.getString("label") to s.optString("action")
                        }
                    } ?: emptyList()
                    if (hasCrisisSuggestion(suggestions)) crisis = true
                    // A felt "reply's here" for the text path too (the voice
                    // path already lands its success haptic in speakReply).
                    if (speak) speakReply(replyText) else com.cerebrozen.app.ui.Haptics.tap()
                }
            } catch (e: Session.FreeLimitException) {
                // The cap is a product state, not a failure — say what it is,
                // when it clears, and what still works. Never a bare error.
                freeLimit = e
            } catch (e: Exception) {
                status = e.userMessage(sendFailed)
                failedText = text.trim()
            } finally {
                busy = false
            }
        }
    }

    // One place that turns a VoiceError into copy (never a bare silence).
    fun onVoiceError(err: com.cerebrozen.app.audio.VoiceError) {
        status = when (err) {
            com.cerebrozen.app.audio.VoiceError.Permission -> micOff
            com.cerebrozen.app.audio.VoiceError.Network -> voiceNetwork
            com.cerebrozen.app.audio.VoiceError.NoSpeech -> voiceNoSpeech
            com.cerebrozen.app.audio.VoiceError.Busy -> voiceBusy
            com.cerebrozen.app.audio.VoiceError.Unavailable -> micUnavailable
            com.cerebrozen.app.audio.VoiceError.Other -> voiceFailed
        }
    }

    // Now that send() exists, wire the post-speech resume: re-arm the mic for the
    // next turn while the voice session is still open (End/Text clears it).
    resumeTurn = {
        if (voiceSession) {
            if (cloudVoice) {
                if (!cloud.startRecording()) status = micUnavailable
            } else {
                voice.startListening({ t -> send(t, speak = true) }, ::onVoiceError)
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
            voice.startListening({ t -> send(t, speak = true) }, ::onVoiceError)
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
        // A dead tap on an unavailable orb used to say nothing at all.
        if (!voice.available && !cloudVoice) { status = micUnavailable; return }
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

    // Follow the conversation only when the reader is already AT the bottom
    // and something actually grew. The old effect fired on entry (burying the
    // header) and on every streamed token (yanking anyone rereading history
    // back to the bottom mid-scroll).
    var followedSize by remember { mutableIntStateOf(-1) }
    LaunchedEffect(messages.size, streamText.isNotBlank(), busy) {
        val grew = messages.size > followedSize && followedSize >= 0
        val nearBottom = chatScroll.maxValue - chatScroll.value < 900
        if ((grew || streamText.isNotBlank()) && nearBottom && messages.isNotEmpty()) {
            chatScroll.animateScrollTo(chatScroll.maxValue)
        }
        followedSize = messages.size
    }

    Box(Modifier.fillMaxSize()) {
    Page(
        stringResource(R.string.talk_eyebrow),
        stringResource(R.string.talk_title),
        trailing = Icons.Outlined.Mic,
        accent = Cyan,
        scrollState = chatScroll,
        // The composer stays put while the transcript scrolls under it. It used
        // to be the last item of the scrolling body, so after any real
        // conversation you had to scroll to the bottom to type — and the
        // auto-scroll-on-new-reply, aimed at revealing the reply, went to the
        // page's maxValue, which was the composer.
        footer = {
            // The free-tier cap sits ABOVE the field, where the comment always
            // said it did: it was rendered after the Send button, so the one
            // explanation of why a message was refused was the last thing on a
            // scrolling page. Says the number and when it clears in LOCAL time.
            freeLimit?.let { limit ->
                SectionCard {
                    Text(stringResource(R.string.freelimit_title),
                        style = MaterialTheme.typography.titleMedium, color = TextSoft)
                    Text(
                        stringResource(R.string.freelimit_body, limit.limit, localResetTime(limit.resetsAtUtc)),
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                    )
                    TextButton(onClick = { freeLimit = null }) {
                        Text(stringResource(R.string.common_dismiss), color = Periwinkle)
                    }
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
            // A failed send keeps its words: one tap resends verbatim.
            failedText?.let { t ->
                PickChip(selected = false, label = stringResource(R.string.talk_retry)) { send(t) }
            }
            // Chat can't queue offline (a conversation needs its other half) —
            // say so where the typing happens, not only in the top banner.
            if (Session.servedStale) {
                Text(stringResource(R.string.talk_offline_compose),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                AppTextField(
                    // A guard before the server's limit, not a 422 after it.
                    draft, { draft = it.take(2000) },
                    // Placeholder-only: the floating outlined label notched the
                    // composer border like a form field, not a chat box.
                    label = "",
                    placeholderText = when {
                        messages.isNotEmpty() -> stringResource(R.string.talk_field_followup)
                        voice.available -> stringResource(R.string.talk_type_instead)
                        else -> stringResource(R.string.talk_field_label)
                    },
                    modifier = Modifier.weight(1f),
                    // The keyboard's action key SENDS (it inserted a newline);
                    // the field itself caps at four visible lines.
                    maxLines = 4,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = { if (draft.isNotBlank() && !busy) send(draft) },
                    ),
                )
                // Voice stays reachable with the keyboard open — the orb is
                // off-screen the moment the composer has focus.
                if ((voice.available || cloudVoice) && !Session.servedStale) {
                    val micCd = stringResource(R.string.talk_mic_composer_cd)
                    Box(
                        Modifier.size(52.dp).clip(CircleShape)
                            .background(CardFill)
                            .border(1.dp, LineStroke, CircleShape)
                            .clickable { onOrbTap() }
                            .semantics { contentDescription = micCd },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Mic, contentDescription = null,
                            tint = PeriwinkleDeep, modifier = Modifier.size(20.dp))
                    }
                }
                SendButton(
                    enabled = !busy && draft.isNotBlank() && !Session.servedStale,
                    busy = busy,
                ) { send(draft) }
            }
        },
    ) {
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

        // Presence + memory honesty + the fresh-start action, ONE header row
        // (reference PresenceHeader, on our tokens). The presence state tells
        // the truth about offline; on an empty thread only the persona shows
        // (the orb hint already narrates state there, and two labels saying
        // "thinking…" was the duplication).
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val personaRaw = companionName.ifBlank { "Calm Guide" }
            val persona = companionLabelRes(personaRaw)?.let { stringResource(it) } ?: personaRaw
            // The state rides along only when it says something ("thinking…",
            // "offline") — at rest the persona stands alone, so the crowded
            // header row never ellipsizes the interesting part.
            val stateRes = when {
                Session.servedStale -> R.string.talk_presence_offline
                transcribing -> R.string.talk_presence_hearing
                busy || streamText.isNotBlank() -> R.string.talk_presence_thinking
                voice.speaking || cloud.speaking -> R.string.talk_presence_speaking
                voice.listening || cloud.recording -> R.string.talk_presence_listening
                else -> null
            }
            Text(
                if (stateRes == null) persona
                else stringResource(R.string.talk_presence_line, persona, stringResource(stateRes)),
                style = MaterialTheme.typography.labelSmall, color = TextMuted,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpen("companion") }
                    .padding(vertical = 4.dp),
            )
            memoryOn?.let { on ->
                Text(
                    stringResource(if (on) R.string.talk_memory_on else R.string.talk_memory_off),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpen("privacy") }
                        .padding(vertical = 4.dp),
                )
            }
            if (messages.isNotEmpty()) {
                val freshMsg = stringResource(R.string.talk_fresh_started)
                Text(
                    stringResource(R.string.talk_start_fresh),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            runCatching { Session.prefPut("talk_clear_before", java.time.OffsetDateTime.now().toString()) }
                            runCatching { Session.prefPut("talk_crisis_sticky", "0") }
                            crisis = false
                            messages = emptyList()
                            chips = emptyList()
                            windowSize = 12
                            // Reassure: the view resets, the record stays.
                            status = freshMsg
                            scope.launch { runCatching { starters = parseStarters(Api.starters()) } }
                        }
                        .padding(vertical = 4.dp),
                )
            }
        }

        if (crisis) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Danger.copy(alpha = 0.14f))
                    .border(1.dp, Danger.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .clickable { onOpen("crisis") }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.talk_crisis_title),
                    style = MaterialTheme.typography.titleMedium, color = Danger)
                Text(stringResource(R.string.talk_crisis_subtitle),
                    style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                // One tap fewer on the worst path: dial directly (ACTION_DIAL
                // opens the dialler, never places the call itself). The number
                // follows the user's crisis region (CrisisDirectory) — the full
                // regional list stays one tap away on the card.
                Row {
                    val pillRegion by rememberCrisisRegion()
                    val pillLine = primaryCrisisLine(pillRegion)
                    val pillIsUrl = isSupportUrl(pillLine.target)
                    val callCd =
                        if (pillIsUrl) stringResource(R.string.crisis_open_cd, stringResource(pillLine.nameRes))
                        else stringResource(R.string.you_support_call_cd,
                            stringResource(pillLine.nameRes), pillLine.target)
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .border(1.dp, Danger.copy(alpha = 0.6f), RoundedCornerShape(50))
                            .clickable { openSupportTarget(context, pillLine.target) }
                            .semantics { contentDescription = callCd }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            if (pillIsUrl) stringResource(pillLine.nameRes)
                            else stringResource(R.string.talk_crisis_call, pillLine.target),
                            style = MaterialTheme.typography.labelLarge, color = Danger)
                    }
                }
            }
        }

        if (showDisclosure) {
            AlertDialog(
                onDismissRequest = { showDisclosure = false },
                title = { Text(stringResource(R.string.talk_disclosure_dialog_title)) },
                text = {
                    // The sheet is the single source of truth — including the
                    // live memory-consent fact, not just the static bullets.
                    val memoryLine = when (memoryOn) {
                        true -> "\n• " + stringResource(R.string.talk_disclosure_memory_on)
                        false -> "\n• " + stringResource(R.string.talk_disclosure_memory_off)
                        null -> ""
                    }
                    Text(stringResource(R.string.talk_disclosure_dialog_body) + memoryLine)
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
            // Full-size only while the orb is the point (no conversation yet, or
            // actively in a voice turn). Once a text conversation exists, the orb
            // was half the viewport pushing the transcript below the fold — it
            // compacts, and the invitation hint goes quiet with it.
            val voiceActive = voice.listening || cloud.recording ||
                voice.speaking || cloud.speaking || transcribing
            val compactOrb = messages.isNotEmpty() && !voiceActive
            VoiceOrb(
                listening = voice.listening || cloud.recording,
                speaking = voice.speaking || cloud.speaking,
                onTap = { onOrbTap() },
                thinking = transcribing || busy,
                level = if (cloud.recording) cloudLevel else voice.level,
                compact = compactOrb,
            )
            val hint = when {
                transcribing -> stringResource(R.string.talk_hint_hearing)
                busy -> stringResource(R.string.talk_hint_thinking)
                cloud.speaking -> stringResource(R.string.talk_hint_speaking_interrupt)
                voice.speaking -> stringResource(R.string.talk_hint_speaking)
                cloud.recording -> stringResource(R.string.talk_hint_listening_done)
                voice.listening -> stringResource(R.string.talk_hint_listening_stop)
                compactOrb -> null   // the conversation is the point now
                cloudVoice -> stringResource(R.string.talk_hint_orb_studio)
                else -> stringResource(R.string.talk_hint_orb)
            }
            hint?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = TextSoft,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        if (messages.isEmpty()) {
            SectionCard {
                // W24: a small one-shot art illustration above the copy.
                EmptyStateArt(kind = "talk")
                Text(stringResource(R.string.talk_empty_title), style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(stringResource(R.string.talk_empty_subtitle), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            if (starters.isNotEmpty() || todayMoodTalk != null) {
                Text(stringResource(R.string.talk_starters_header), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
                // Bleed to the screen edge so a clipped chip reads as "scrolls",
                // not "broken" (the ContentRail pattern).
                Row(
                    Modifier.bleed(pageHorizontalPadding()).horizontalScroll(rememberScrollState())
                        .padding(horizontal = pageHorizontalPadding()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Today's check-in seeds the first opener — the static
                    // topics never knew anything about the user's actual day.
                    todayMoodTalk?.let { m ->
                        val moodLabel = moodLabelResFor(m)?.let { stringResource(it) } ?: m
                        val opener = stringResource(R.string.talk_starter_mood, moodLabel.lowercase())
                        PickChip(selected = false, label = opener) { send(opener) }
                    }
                    starters.forEach { topic ->
                        PickChip(selected = false, label = topic) { send(topic) }
                    }
                }
            }
            TryTogetherRow(onOpen)
        } else {
            // Long-press reveals a bubble's time (and still copies it).
            var timeShownKey by remember { mutableStateOf<Int?>(null) }
            var openedWidgets by remember { mutableStateOf(setOf<Int>()) }
            val nowStamp = remember(messages.size) { java.time.OffsetDateTime.now() }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Keyed on the absolute index so the sliding window never
                // re-runs an old bubble's entrance (W10).
                val windowStart = (messages.size - windowSize).coerceAtLeast(0)
                val today = remember { LocalDate.now() }
                val visible = messages.takeLast(windowSize)
                // Older messages exist and are reachable now — takeLast(12)
                // used to make yesterday unreachable in-app.
                if (windowStart > 0) {
                    TextButton(
                        onClick = { windowSize += 24 },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(stringResource(R.string.talk_show_earlier),
                            style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    }
                }
                visible.forEachIndexed { i, m ->
                    key(windowStart + i) {
                        // Day separators, computed against the FULL list so the
                        // window's first message keeps its day context.
                        daySeparator(messages, windowStart + i, today)?.let { label ->
                            Text(
                                when (label) {
                                    "TODAY" -> stringResource(R.string.talk_day_today)
                                    "YESTERDAY" -> stringResource(R.string.talk_day_yesterday)
                                    else -> label
                                },
                                style = MaterialTheme.typography.labelSmall, color = TextMuted2,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            )
                        }
                        ChatBubble(
                            m, animate = windowStart + i >= entranceFloor,
                            onLongPress = {
                                timeShownKey = if (timeShownKey == windowStart + i) null else windowStart + i
                            },
                            // Grouping: the tail corner only on the last bubble
                            // of a same-role run.
                            tail = visible.getOrNull(i + 1)?.role != m.role,
                            // TalkBack hears the newest reply arrive.
                            announce = m.role == "assistant" && windowStart + i == messages.lastIndex,
                        )
                        if (timeShownKey == windowStart + i) {
                            val abs = clockLabel(m.createdAt)
                            val rel = relativeTimeLabel(relativeTime(m.createdAt, nowStamp))
                            listOfNotNull(abs, rel).takeIf { it.isNotEmpty() }?.let { partsList ->
                                Text(
                                    partsList.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall, color = TextMuted2,
                                    textAlign = if (m.role == "user") TextAlign.End else TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        // Widget cards: consecutive duplicates collapse (two
                        // anxious turns used to stack two identical breathing
                        // cards), and an opened card settles to its done form.
                        val prevKind = visible.getOrNull(i - 1)?.widget?.kind
                        m.widget?.takeIf { it.kind != prevKind }?.let { w ->
                            WidgetCard(
                                w, onOpen,
                                opened = (windowStart + i) in openedWidgets,
                                onOpened = { openedWidgets = openedWidgets + (windowStart + i) },
                            )
                        }
                    }
                }
                // Live reply: streamed tokens with a blinking caret, or a typing
                // indicator while the companion is composing its answer.
                if (streamText.isNotBlank()) {
                    StreamingBubble(streamText)
                } else if (busy) {
                    val typingCd = stringResource(R.string.talk_typing_cd)
                    Box(Modifier.semantics { contentDescription = typingCd }) { TypingDots() }
                }
                // Structured exercises as first-class offers (REDESIGN §3.3) —
                // quiet, after the companion's latest reply, never while composing.
                // Ordered by the moment: spiralling words put grounding first,
                // late evening puts breathing first.
                // One rail, one source at a time: when the server sent chips
                // they lead; Try-together fills the quiet turns (two stacked
                // chip rows from different sources read as chip soup).
                if (chips.isEmpty() &&
                    showTryTogether(messages.size, messages.lastOrNull()?.role, busy, streamText.isNotBlank())
                ) {
                    TryTogetherRow(
                        onOpen,
                        order = tryTogetherOrder(
                            java.time.LocalTime.now().hour,
                            messages.lastOrNull { it.role == "user" }?.text,
                        ),
                    )
                }
            }
            val journalEntryTitle = stringResource(R.string.talk_journal_entry_title)
            val savedStatus = stringResource(R.string.talk_saved_status)
            val saveFailed = stringResource(R.string.talk_save_failed)
            // The saved entry speaks the UI's language, not hardcoded English
            // (the transcript name strings predate this — iOS parity).
            val mePrefix = stringResource(R.string.talk_transcript_me) + ": "
            val botPrefix = stringResource(R.string.talk_transcript_bot) + ": "
            // The save row answers itself: once saved it flips to "Saved ·
            // View" until new messages arrive (then there's new content to save).
            var savedToJournal by remember(messages.size) { mutableStateOf(false) }
            // An outlined row with an icon, not bare periwinkle text: unstyled,
            // it sat between two sections reading as a heading — the same weight
            // and colour as the "Try together" / "Type instead" labels above and
            // below it — so the one way to keep a conversation looked like a
            // caption for the next thing.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        1.dp,
                        (if (savedToJournal) Cyan else Periwinkle).copy(alpha = 0.45f),
                        RoundedCornerShape(14.dp),
                    )
                    .clickable {
                        if (savedToJournal) {
                            onOpen("journal")
                        } else {
                            scope.launch {
                                runCatching {
                                    Api.createJournal(
                                        journalEntryTitle,
                                        talkTranscript(messages, mePrefix = mePrefix, botPrefix = botPrefix),
                                    )
                                }
                                    .onSuccess { status = savedStatus; savedToJournal = true }
                                    .onFailure { status = saveFailed }
                            }
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    tint = if (savedToJournal) Cyan else Periwinkle,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(
                        if (savedToJournal) R.string.talk_saved_view else R.string.talk_save_journal,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (savedToJournal) Cyan else Periwinkle,
                )
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
            Row(
                Modifier.bleed(pageHorizontalPadding()).horizontalScroll(rememberScrollState())
                    .padding(horizontal = pageHorizontalPadding())
                    // Dim while composing — a tap mid-generation is a no-op
                    // and the chips shouldn't pretend otherwise.
                    .graphicsLayer { alpha = if (busy) 0.5f else 1f },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { (rawLabel, action) ->
                    // Display localizes; the WIRE label is what routes/sends
                    // (the server's routing keywords are English).
                    val display = chipLabelResFor(rawLabel)?.let { stringResource(it) } ?: rawLabel
                    PickChip(selected = false, label = display) {
                        if (busy) return@PickChip
                        when (action) {
                            // Action chips NAVIGATE — "Urgent support" used to
                            // send itself back as chat text.
                            "crisis" -> onOpen("crisis")
                            "human_support" -> onOpen("humansupport")
                            "breathing" -> onOpen("breathe/box")
                            "grounding" -> onOpen("ground")
                            else -> send(rawLabel)
                        }
                    }
                }
            }
        }

        // Fast escape hatch when talking feels like too much (mirrors iOS) —
        // except while the crisis card is up: two urgent doors stacked compete,
        // and the crisis one must win. Mid-conversation it compacts to a chip:
        // the full card duplicated the Toolkit door in prime transcript space.
        if (!crisis) {
            if (messages.isEmpty()) {
                NavRow(stringResource(R.string.talk_sos_title), stringResource(R.string.talk_sos_subtitle)) { onOpen("toolkit") }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PickChip(selected = false, label = stringResource(R.string.talk_sos_title)) { onOpen("toolkit") }
                }
            }
        }

    }

    // Ref LIVE VOICE SESSION: an immersive overlay that stays up across turns.
    // A quiet top scrim so scrolled content fades under the system clock
    // (Home and Sleep already have it).
    val topInset = with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(this).toDp()
    }
    Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(topInset + 18.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Night.copy(alpha = 0.88f), Night.copy(alpha = 0f)),
                ),
            ),
    )

    // Reading history? One tap back to the newest reply — a long thread had no
    // way down but scrolling.
    val farFromLatest = messages.size > 6 && chatScroll.maxValue > 0 &&
        chatScroll.value < chatScroll.maxValue - 900
    if (farFromLatest && !voiceSession) {
        val jumpCd = stringResource(R.string.talk_jump_latest_cd)
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 168.dp)
                .size(42.dp).clip(CircleShape)
                .background(CardFill)
                .border(1.dp, LineStroke, CircleShape)
                .clickable { scope.launch { chatScroll.animateScrollTo(chatScroll.maxValue) } }
                .semantics { contentDescription = jumpCd },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ArrowDownward, contentDescription = null,
                tint = TextSoft, modifier = Modifier.size(18.dp))
        }
    }

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
            // Actually swallow taps aimed behind the overlay. A `clickable`
            // would do it too, but it publishes an unlabeled clickable node over
            // the whole background for TalkBack — a raw pointer filter doesn't.
            .pointerInput(Unit) { detectTapGestures { } }
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
/** The circular send control beside the composer.
 *
 * A round icon button rather than the full-width PrimaryButton pill the composer
 * used to stack beneath the field: pinned above the keyboard, a 56dp pill for
 * one word wastes the height the transcript needs, and "Send" belongs next to
 * what it sends. Disabled until there is something to send, so the control never
 * lies about being actionable.
 */
@Composable
private fun SendButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    // The description tells the truth about the disabled state.
    val sendCd = stringResource(if (enabled) R.string.common_send else R.string.talk_send_disabled_cd)
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (enabled) Gradients.primary else Brush.horizontalGradient(listOf(ButtonDisabled, ButtonDisabled)))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = sendCd },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            TypingDots()
        } else {
            Icon(
                Icons.AutoMirrored.Outlined.Send,
                contentDescription = null,
                tint = if (enabled) OnPrimary else TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun TryTogetherRow(
    onOpen: (String) -> Unit,
    order: List<String> = listOf("reframe", "breathe", "ground"),
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.talk_try_together), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
        Row(
            Modifier.bleed(pageHorizontalPadding()).horizontalScroll(rememberScrollState())
                .padding(horizontal = pageHorizontalPadding()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            order.forEach { kindKey ->
                when (kindKey) {
                    "reframe" -> PickChip(selected = false, label = stringResource(R.string.talk_chip_reframe)) { onOpen("cbt") }
                    "breathe" -> PickChip(selected = false, label = stringResource(R.string.talk_chip_breathe)) { onOpen("breathe/box") }
                    "ground" -> PickChip(selected = false, label = stringResource(R.string.talk_chip_ground)) { onOpen("ground") }
                }
            }
        }
    }
}

/** An Oracle-suggested inline activity: title/description + a native surface
 * when Android has one, else the honest iOS-only note (mirrors the web card). */
@Composable
private fun WidgetCard(
    w: ChatWidget,
    onOpen: (String) -> Unit,
    /** Once opened, the card settles into its done form instead of forever
     * saying "Open" as if nothing happened. */
    opened: Boolean = false,
    onOpened: (() -> Unit)? = null,
) {
    val route = widgetRoute(w.kind)
    if (opened && route != null) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Cyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .clickable { onOpen(route) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp))) {
                ContentArt(title = w.title, kind = widgetArtKind(w.kind), modifier = Modifier.fillMaxSize())
            }
            Text(
                stringResource(R.string.talk_widget_opened, w.title),
                style = MaterialTheme.typography.bodyMedium, color = Cyan,
            )
        }
        return
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardFill)
            .border(1.dp, LineStroke, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The card wears the exercise's art like every door in the app —
        // it was the one bare-text tile on the surface.
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))) {
            ContentArt(title = w.title, kind = widgetArtKind(w.kind), modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.talk_suggested_activity), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
            Text(w.title, style = MaterialTheme.typography.titleMedium, color = TextSoft)
            Text(w.description, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            if (route != null) {
                TextButton(onClick = { onOpened?.invoke(); onOpen(route) }) {
                    Text(stringResource(R.string.common_open), color = Cyan)
                }
            } else {
                Text(stringResource(R.string.talk_ios_only), style = MaterialTheme.typography.bodySmall, color = TextMuted2)
            }
        }
    }
}

/** One chat bubble. [animate] arms a one-shot 150ms rise+fade for bubbles that
 * arrive during this session; restored history renders settled (W10). The
 * Reduce-Motion branch lives inside [appear] (static, never blank). */
@Composable
private fun ChatBubble(
    m: Msg,
    animate: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    /** The tail corner renders only on the LAST bubble of a same-role run. */
    tail: Boolean = true,
    /** TalkBack politely announces the newest assistant reply. */
    announce: Boolean = false,
) {
    val user = m.role == "user"
    val entrance = if (animate) Modifier.appear(rise = 8f, durationMs = 150) else Modifier
    // Long-press copies the bubble (quoting your own words into the journal is
    // the common case). Android 13+ shows the system clipboard chip, so the
    // haptic plus that chip is the whole feedback loop. [onLongPress] also
    // lets the caller reveal the bubble's time.
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    // A live model emits markdown; the crisis suffix carries dialable numbers.
    val display = stripMarkdownLite(m.text)
    val spans = if (user) emptyList() else phoneSpans(display)
    // Fraction of the screen, not a fixed 320dp: small phones keep a margin,
    // large ones don't get stubby bubbles.
    val maxBubble = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp * 0.80f).dp
    Row(
        Modifier.fillMaxWidth().then(entrance),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (user) Periwinkle.copy(alpha = 0.20f) else CardFill,
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (user || !tail) 18.dp else 5.dp,
                bottomEnd = if (!user || !tail) 18.dp else 5.dp,
            ),
            modifier = Modifier.widthIn(max = maxBubble)
                .border(
                    // The companion's bubbles wear a faint cyan edge — white-on-
                    // cream left the two voices distinguished by alignment alone.
                    1.dp, if (user) Periwinkle.copy(alpha = 0.35f) else Cyan.copy(alpha = 0.22f),
                    RoundedCornerShape(18.dp),
                )
                .pointerInput(m.text) {
                    detectTapGestures(onLongPress = {
                        com.cerebrozen.app.ui.Haptics.tap()
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(m.text))
                        onLongPress?.invoke()
                    })
                },
        ) {
            val annotated = androidx.compose.ui.text.buildAnnotatedString {
                var cursor = 0
                spans.forEach { r ->
                    append(display.substring(cursor, r.first))
                    val number = display.substring(r.first, r.last + 1)
                    withLink(
                        androidx.compose.ui.text.LinkAnnotation.Clickable(
                            tag = "tel",
                            styles = androidx.compose.ui.text.TextLinkStyles(
                                style = androidx.compose.ui.text.SpanStyle(
                                    color = Cyan,
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                ),
                            ),
                        ) {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_DIAL,
                                        android.net.Uri.parse("tel:" + number.filter(Char::isDigit)),
                                    ),
                                )
                            }
                        },
                    ) { append(number) }
                    cursor = r.last + 1
                }
                append(display.substring(cursor))
            }
            Text(
                annotated,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSoft,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                    .let { mod ->
                        if (announce) mod.semantics {
                            liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                        } else mod
                    },
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
    /** Mid-conversation resting size — the orb stays reachable without owning
     * half the viewport. Voice activity always restores the full stage. */
    compact: Boolean = false,
) {
    val stage = if (compact) 96.dp else 210.dp
    val orbSize = if (compact) 64.dp else 150.dp
    val haloSize = if (compact) 100.dp else 230.dp
    val highlight = if (compact) 24.dp else 56.dp
    val highlightOffset = if (compact) (-10).dp else (-24).dp
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

    Box(Modifier.fillMaxWidth().height(stage), contentAlignment = Alignment.Center) {
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
            Modifier.size(haloSize).scale(pulse)
                .background(Brush.radialGradient(listOf(core.copy(alpha = 0.28f), Color.Transparent))),
        )
        // Rotating conic shimmer ring while the companion is thinking.
        if (thinking && !reduceMotion) {
            Canvas(Modifier.size(if (compact) 80.dp else 178.dp).graphicsLayer { rotationZ = spin }) {
                drawCircle(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, core.copy(alpha = 0.1f), core, Color.Transparent)),
                    radius = size.minDimension / 2f - 3.dp.toPx(),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
        // The orb core, with an inner specular highlight (top-left light source).
        Box(
            Modifier.size(orbSize).scale(pulse).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White, core, PeriwinkleDeep)))
                .clickable(
                    onClickLabel = if (listening) stringResource(R.string.talk_orb_stop_cd)
                    else stringResource(R.string.talk_orb_talk_cd),
                ) { onTap() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(highlight).offset(x = highlightOffset, y = highlightOffset).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent))),
            )
            // The resting orb reads as decoration without a glyph: a quiet mic
            // says what tapping does (full-size resting state only — while
            // listening/speaking the motion itself is the signal).
            if (!listening && !speaking && !thinking) {
                Icon(
                    Icons.Outlined.Mic, contentDescription = null,
                    tint = PeriwinkleDeep.copy(alpha = 0.55f),
                    modifier = Modifier.size(if (compact) 22.dp else 34.dp),
                )
            }
        }
    }
}


/**
 * Render the server's UTC reset instant in the device's own timezone.
 *
 * Copy that just says "midnight" is wrong for most of the world: the quota
 * window is UTC, so in India it clears at 05:30 local. Falls back to the plain
 * phrase only if the timestamp can't be parsed.
 */
internal fun localResetTime(iso: String): String = runCatching {
    val instant = java.time.OffsetDateTime.parse(iso).toInstant()
    java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
        .withZone(java.time.ZoneId.systemDefault())
        .format(instant)
}.getOrDefault("00:00 UTC")
