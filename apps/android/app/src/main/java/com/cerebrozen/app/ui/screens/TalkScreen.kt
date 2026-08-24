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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
    /** Label resources for the Oracle tools this reply ran (WC-138): the
     * member-visible half of the audit trail, on the message it explains. */
    val tools: List<Int> = emptyList(),
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
 * its journaling practice, and grounding lives inline in the Toolkit. */
internal fun widgetRoute(kind: String): String? = when (kind) {
    "breathing" -> "breathing"
    // Straight to the exercise since it has its own screen (2026-08-03) —
    // the hub was a detour when the companion just suggested grounding.
    "grounding" -> "ground"
    "mood_check" -> "home"
    "mini_journal", "journal" -> "journal"
    "sleep_checkin" -> "sleep"
    // Both of these have their own prompt-led screen with its "why this works"
    // footer, and both were being sent to the bare Journal composer instead —
    // which made the two screens unreachable from anywhere in the app.
    "one_good_thing" -> "onegoodthing"
    "intention_set" -> "intention"
    "dbt_skill" -> "tipp"
    else -> null
}

/** V3-c: the deterministic next-best-action after a chat check-in — the same
 * one-per-behavior surfaces the tools tray offers, chosen by what the mood
 * says the body needs first (mirrors tryTogetherOrder's reading). Pure. */
/**
 * Whether a live voice session owns the screen right now.
 *
 * The tab pill is drawn by the app Scaffold, outside this screen, so an
 * overlay inside Talk cannot cover it — a full-screen call sat with three
 * tabs across its bottom edge, one tap from silently abandoning the session
 * (device walk 2026-08-16). Same shape as the IME rule the pill already
 * honours: the chrome yields to what the user is doing.
 */
object VoiceSessionState {
    var active by androidx.compose.runtime.mutableStateOf(false)
}

internal fun moodNbaKind(mood: String): String = when (mood.lowercase(java.util.Locale.ROOT)) {
    "anxious" -> "grounding"
    "overwhelmed" -> "breathing"
    "low" -> "one_good_thing"
    "tired" -> "sleep_checkin"
    "good" -> "intention_set"
    else -> "breathing"
}

/** V3-d: the middle rung's ear — concerning-but-not-red-flag language. The
 * words here are deliberately NOT the crisis vocabulary (the server's scan owns
 * that and outranks this); these are the heavy-day phrases that deserve a warm
 * pathway card without a full crisis takeover. Pure. */
internal fun soundsHeavy(text: String): Boolean {
    val t = text.lowercase(java.util.Locale.ROOT)
    return listOf(
        "can't cope", "cant cope", "hopeless", "no point", "give up",
        "worthless", "hate myself", "can't do this anymore", "cant do this anymore",
    ).any { it in t }
}

/** What the companion owes you when you open the conversation again.
 *
 * Proactive, but only about things that actually happened:
 *  - [ACTIVITY] you opened a suggested activity and came back — ask how it
 *    landed, because nothing else in the app ever asks,
 *  - [RETURN] it has been a while since the last exchange — offer to pick up
 *    or start somewhere new, rather than pretending no time passed,
 *  - [NONE] you were just here; silence is the polite answer.
 *
 * Deliberately NOT time-of-day driven: a companion that greets you every time
 * you glance at the tab is a nag, and the daily opener already owns mornings.
 * Pure + unit-tested.
 */
internal enum class FollowUp { ACTIVITY, RETURN, NONE }

internal fun followUpOwed(
    pendingActivity: Boolean,
    minutesSinceLastMessage: Long?,
    hasConversation: Boolean,
): FollowUp = when {
    pendingActivity -> FollowUp.ACTIVITY
    !hasConversation -> FollowUp.NONE          // the opener handles an empty thread
    minutesSinceLastMessage == null -> FollowUp.NONE
    minutesSinceLastMessage >= 180 -> FollowUp.RETURN
    else -> FollowUp.NONE
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
    // A live model occasionally omits the space after a sentence period
    // ("…further.Writing down…", seen in a stored transcript, 2026-08-24
    // review). Display-only healing, deliberately narrow: lowercase-period-
    // Uppercase only, so decimals (2.5), acronyms (U.S.), versions and URLs
    // never match.
    .replace(Regex("""(?<=[a-z])\.(?=[A-Z])"""), ". ")

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
    /** The last send failed because a guest has no account — offer the door, not a retry. */
    var signInBlocked by remember { mutableStateOf(false) }
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
    // Mirror it out so the app Scaffold can drop the tab pill (see
    // VoiceSessionState) — and always clear it when Talk leaves composition,
    // or the tabs would stay hidden after a tab switch mid-session.
    LaunchedEffect(voiceSession) { VoiceSessionState.active = voiceSession }
    DisposableEffect(Unit) { onDispose { VoiceSessionState.active = false } }
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
    // ── V3-c: the companion speaks first (owner-approved prototype) ──────
    // Deterministic opener, no LLM: morning asks about sleep and logs the
    // night from chat; then the mood ask, whose answer earns a next-best-
    // action card (rendered by the existing WidgetCard machinery). Stage is
    // saveable so a tab hop never re-asks a question already answered.
    var openerStage by rememberSaveable { mutableStateOf("idle") } // idle|sleep|mood|done
    var openerArmed by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    // V5: set when a suggested activity is opened FROM the chat, so the
    // companion can ask about it on the way back. A pref, not composition
    // state — leaving for a breathing screen tears this screen down.
    var awaitingActivity by remember {
        mutableStateOf(runCatching { Session.prefGet("talk_activity_pending") == "1" }.getOrDefault(false))
    }
    // Quick replies under the newest companion message — a chat should never
    // hand you a blank composer as the only way forward.
    var quickReplies by remember { mutableStateOf(listOf<String>()) }
    var tipDismissed by remember {
        mutableStateOf(runCatching { Session.prefGet("talk_tip_tools") == "1" }.getOrDefault(false))
    }
    // V3-d: reply controls show only under a reply the user actually asked for
    // (never under opener bubbles), and the concern card shows once per thread.
    var lastWasSend by remember { mutableStateOf(false) }
    var concernPending by rememberSaveable { mutableStateOf(false) }
    var concernDismissed by rememberSaveable { mutableStateOf(false) }
    val hourTalk = remember { java.time.LocalTime.now().hour }
    val opHello = stringResource(
        when {
            hourTalk in 5..11 -> R.string.talk_op_hello_morning
            hourTalk in 12..16 -> R.string.talk_op_hello_day
            else -> R.string.talk_op_hello_evening
        },
    )
    val fuActivity = stringResource(R.string.talk_fu_activity)
    val fuBack = stringResource(R.string.talk_fu_back)
    val fuHelped = stringResource(R.string.talk_fu_helped)
    val fuNotReally = stringResource(R.string.talk_fu_notreally)
    val fuAckHelped = stringResource(R.string.talk_fu_ack_helped)
    val fuAckNot = stringResource(R.string.talk_fu_ack_notreally)
    val qrMore = stringResource(R.string.talk_qr_more)
    val qrWhy = stringResource(R.string.talk_qr_why)
    val qrNotNow = stringResource(R.string.talk_qr_notnow)
    val qrWhyAnswer = stringResource(R.string.talk_qr_why_answer)
    val qrNotNowAnswer = stringResource(R.string.talk_qr_notnow_answer)
    val journalEntryTitle = stringResource(R.string.talk_journal_entry_title)
    val savedStatus = stringResource(R.string.talk_saved_status)
    val saveFailed = stringResource(R.string.talk_save_failed)
    val transcriptMe = stringResource(R.string.talk_transcript_me) + ": "
    val transcriptBot = stringResource(R.string.talk_transcript_bot) + ": "
    val freshMsg = stringResource(R.string.talk_fresh_started)
    val opSleepQ = stringResource(R.string.talk_op_sleep_q)
    val opSleepDone = stringResource(R.string.talk_op_sleep_done)
    val opSleepGuest = stringResource(R.string.talk_op_sleep_guest)
    val opSleepFailed = stringResource(R.string.talk_op_sleep_failed)
    val opMoodQ = stringResource(R.string.talk_op_mood_q)
    val opMoodAck = stringResource(R.string.talk_op_mood_ack)
    val opMoodAckGood = stringResource(R.string.talk_op_mood_ack_good)
    val opGuestNote = stringResource(R.string.talk_op_guest_note)
    val opEvening = stringResource(R.string.talk_op_evening)
    val nbaTitles = mapOf(
        "grounding" to stringResource(R.string.talk_nba_ground),
        "breathing" to stringResource(R.string.talk_nba_breathe),
        "one_good_thing" to stringResource(R.string.talk_nba_onegood),
        "intention_set" to stringResource(R.string.talk_nba_intention),
        "sleep_checkin" to stringResource(R.string.talk_nba_sleep),
    )
    // The description IS the practice's one-line provenance (the copy-dieted
    // why-strings) — the card explains itself the way every tool here does.
    val nbaDescs = mapOf(
        "grounding" to stringResource(R.string.ground_why),
        "breathing" to stringResource(R.string.breathe_why),
        "one_good_thing" to stringResource(R.string.onegood_why),
        "intention_set" to stringResource(R.string.intention_why),
        "sleep_checkin" to stringResource(R.string.sleep_cbti_why),
    )
    var streamText by remember { mutableStateOf("") }
    // The label of the tool the agent is running RIGHT NOW — the answer to why
    // the stream is quiet. Cleared by the first token: once text is flowing the
    // stall this line explains is over (WC-139).
    var toolActivity by remember { mutableStateOf<Int?>(null) }
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
        // V3-c: on an empty thread the companion opens the day. The opener
        // bubbles are LOCAL (the server keeps the mood/sleep rows, not the
        // pleasantries) and they animate in past the settled-history floor.
        if (messages.isEmpty()) {
            val lastNightLogged = runCatching {
                val logs = Api.sleepLogs()
                hasLastNightLog(
                    (0 until logs.length()).map { logs.getJSONObject(it).optString("date") },
                    LocalDate.now(),
                )
            }.getOrDefault(true)
            if (openerStage == "idle") {
                openerStage = when {
                    hourTalk in 5..11 && !lastNightLogged -> "sleep"
                    todayMoodTalk == null -> "mood"
                    else -> "done"
                }
            }
            val greet = mutableListOf(Msg("assistant", opHello))
            when {
                openerStage == "sleep" -> greet += Msg("assistant", opSleepQ)
                openerStage == "mood" -> greet += Msg("assistant", opMoodQ)
                openerStage == "done" && hourTalk >= 17 -> greet += Msg(
                    "assistant", opEvening,
                    widget = ChatWidget(
                        "sleep_checkin",
                        nbaTitles["sleep_checkin"].orEmpty(),
                        nbaDescs["sleep_checkin"].orEmpty(),
                    ),
                )
            }
            messages = greet + messages
            openerArmed = true
        }
        // V5: a returning visit gets a follow-up instead of silence.
        val lastStamp = messages.lastOrNull { it.createdAt.isNotBlank() }?.createdAt
        val minutesSince = lastStamp?.let {
            runCatching {
                java.time.Duration.between(
                    java.time.OffsetDateTime.parse(it), java.time.OffsetDateTime.now(),
                ).toMinutes()
            }.getOrNull()
        }
        when (followUpOwed(awaitingActivity, minutesSince, messages.isNotEmpty())) {
            FollowUp.ACTIVITY -> {
                messages = messages + Msg("assistant", fuActivity)
                quickReplies = listOf(fuHelped, fuNotReally)
                awaitingActivity = false
                runCatching { Session.prefPut("talk_activity_pending", "0") }
            }
            FollowUp.RETURN -> {
                messages = messages + Msg("assistant", fuBack)
                quickReplies = listOf(qrMore, qrNotNow)
            }
            FollowUp.NONE -> Unit
        }
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
        val toolsUsed = mutableListOf<Int>()
        try {
            Session.sse(path, body) { ev ->
                when (ev.optString("type")) {
                    "token" -> { acc += ev.optString("text"); streamText = acc; toolActivity = null }
                    "tool" -> {
                        val res = oracleToolLabelRes(ev.optString("tool"))
                        if (res !in toolsUsed) toolsUsed += res
                        toolActivity = res
                    }
                    "widget" -> widget = parseWidget(ev.optJSONObject("widget"))
                    "crisis" -> crisis = true
                    "tool_confirm" -> confirmReq = ev.optString("thread_id") to
                        ev.optString("summary").ifBlank { confirmFallback }
                    "done" -> {
                        val t = ev.optString("text").ifBlank { acc }.trim()
                        if (t.isNotEmpty() || widget != null) {
                            messages = messages + Msg("assistant", t, widget, tools = toolsUsed.toList())
                        }
                        final = t; acc = ""; widget = null; toolsUsed.clear()
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
            toolActivity = null
        }
        return final
    }

    fun send(text: String, speak: Boolean = false, echo: Boolean = true) {
        if (text.isBlank() || busy) return
        busy = true; status = null; failedText = null; signInBlocked = false
        // Clear the composer up front so the sent text doesn't linger in the box
        // during streaming (and can't be wiped if the user starts a follow-up).
        draft = ""
        scope.launch {
            try {
                if (useOracle) {
                    // Agentic path: SSE tokens + inline widgets + confirm-before-write.
                    // The server persists both sides; thread defaults to the user id.
                    if (echo) messages = messages + Msg("user", text.trim())
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
                    if (echo) messages = messages + Msg("user", text.trim())
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
                // A guest's 401 is not a transient failure and retrying can never
                // clear it — `Session.ensureAccess` refuses the call outright,
                // by design, because a guest never signed in. Offering "Try
                // sending again" beneath a message that says "sign in" invites
                // someone to tap a button that cannot work; found on a device
                // walk 2026-08-15. The door is offered instead, which exists now
                // that guest mode has an `auth` route to send people to.
                val guestBlocked = Session.guestMode &&
                    e is Session.ApiException && e.code == 401
                failedText = if (guestBlocked) null else text.trim()
                signInBlocked = guestBlocked
            } finally {
                busy = false
                lastWasSend = true
                // V3-d middle rung: heavy-day language earns the warm pathway
                // card — unless the server's crisis scan already answered with
                // the full banner, which outranks it.
                if (!crisis && !concernDismissed && soundsHeavy(text)) concernPending = true
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

    /** Keep the conversation as a journal entry (was the save row). */
    fun saveThreadToJournal() {
        if (messages.isEmpty()) return
        scope.launch {
            runCatching {
                Api.createJournal(
                    journalEntryTitle,
                    talkTranscript(messages, mePrefix = transcriptMe, botPrefix = transcriptBot),
                )
            }
                .onSuccess { status = savedStatus }
                .onFailure { status = saveFailed }
        }
    }

    /** Start a new conversation. The VIEW resets; the record stays on the
     * server, which is what the confirmation says. */
    fun startFresh() {
        runCatching { Session.prefPut("talk_clear_before", java.time.OffsetDateTime.now().toString()) }
        runCatching { Session.prefPut("talk_crisis_sticky", "0") }
        crisis = false
        messages = emptyList()
        chips = emptyList()
        windowSize = 12
        openerStage = "idle"
        status = freshMsg
        scope.launch { runCatching { starters = parseStarters(Api.starters()) } }
    }

    /** A quick reply. The canned ones are answered on-device — asking the
     * server "why that?" would spend a network turn to get a vaguer version of
     * the honest answer we can already give. Anything else is a real message. */
    fun onQuickReply(label: String) {
        quickReplies = emptyList()
        messages = messages + Msg("user", label)
        val canned = when (label) {
            fuHelped -> fuAckHelped
            fuNotReally -> fuAckNot
            qrWhy -> qrWhyAnswer
            qrNotNow -> qrNotNowAnswer
            else -> null
        }
        if (canned != null) {
            messages = messages + Msg("assistant", canned)
            com.cerebrozen.app.ui.Haptics.tap()
        } else {
            send(label, echo = false)
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
        // V3-f device walk: the GraphicEq identity glyph gave its slot back —
        // with the V3 gear + shield beside it, three wells ellipsized the
        // title to "How are yo…". Identity lost to legibility, correctly.
        accent = Cyan,
        scrollState = chatScroll,
        // V2-a: the shield in the same pixels here too — Talk relied on the
        // sticky in-thread crisis card, so its top bar was the only tab root
        // without the door.
        onUrgent = { onOpen("crisis") },
        // V3-a: You left the tab pill; the gear is its door on every tab root.
        onSettings = { onOpen("you") },
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
            // …unless the only thing that would fix it is an account, in which
            // case offer that instead of a retry that cannot succeed.
            if (signInBlocked) {
                PickChip(
                    selected = false,
                    label = stringResource(R.string.guest_sign_in_action),
                ) { onOpen("auth") }
            }
            // V4: the one persistent AI disclosure, in the slot the user's eyes
            // are already in. Tapping it opens the full sheet.
            Text(
                stringResource(R.string.talk_disclosure_pill),
                style = MaterialTheme.typography.bodySmall, color = TextMuted2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showDisclosure = true }
                    .padding(vertical = 4.dp),
            )
            // The persistent banner already explains the offline state and
            // points to Toolkit. Repeating it here made the composer feel like
            // an error panel and put the same message on screen three times.
            // The same honesty for a guest, BEFORE they type (audit I#13): the
            // composer was fully enabled for an account state in which every
            // send is refused by design — the truth arrived only after the
            // failed attempt. One quiet line, in the slot the offline truth
            // already uses.
            if (Session.guestMode && !Session.signedIn) {
                Text(stringResource(R.string.talk_guest_compose),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            // V4: ONE pill, controls inside it (the reference's inputpill).
            //
            // The row used to be [＋ 44][field][mic 44][send 52] with three
            // 8dp gaps — 164dp of chrome on a 360dp screen, which left the
            // field too narrow to fit its own placeholder: "Say what's on your
            // mind…" wrapped onto two lines inside a chat composer. Mic and
            // send moved INSIDE the field's trailing slot, so the pill spans
            // the row and the text has somewhere to go.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // V3-c: the ＋ tray — the tools inside the conversation (the
                // Aira pattern). Browsing the Practices hub becomes the
                // exception, not the path.
                val toolsCd = stringResource(R.string.talk_tools_cd)
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(CardFill)
                        .border(1.dp, LineStroke, CircleShape)
                        .clickable { showTools = true }
                        .semantics { contentDescription = toolsCd },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", style = MaterialTheme.typography.titleLarge, color = PeriwinkleDeep)
                }
                val micCd = stringResource(R.string.talk_mic_composer_cd)
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
                    trailingIcon = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Voice stays reachable with the keyboard open —
                            // the orb is off-screen once the composer has focus.
                            if ((voice.available || cloudVoice) && !Session.servedStale) {
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape)
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
                                compact = true,
                            ) { send(draft) }
                        }
                    },
                )
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

        // V4: the disclosure CARD is gone from the top of the thread. The rule
        // (design §8) asks for a persistent disclosure plus the periodic sheet,
        // and this screen had three copies of it: this card, the line above the
        // composer, and the sheet. The composer line is the persistent one —
        // it sits where you type, it is always on screen, and it is tappable
        // for the full points. One disclosure, still always visible.

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
                transcribing -> R.string.talk_presence_hearing
                busy || streamText.isNotBlank() -> R.string.talk_presence_thinking
                voice.speaking || cloud.speaking -> R.string.talk_presence_speaking
                voice.listening || cloud.recording -> R.string.talk_presence_listening
                else -> null
            }
            Text(
                if (stateRes == null) persona
                else stringResource(R.string.talk_presence_line, persona, stringResource(stateRes)),
                style = MaterialTheme.typography.bodySmall, color = TextMuted,
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
            // V5: "Start fresh" left this row for the ＋ tray, where it lives
            // beside "Save to journal" as the other thing you do TO a
            // conversation. Four links running edge to edge was the last piece
            // of chrome above the thread, and this one had a home already.
        }

        // V5: one dismissible line, once ever — the ＋ tray holds eight tools
        // and nothing on screen said so.
        if (!tipDismissed && messages.isNotEmpty()) {
            InfoBanner(
                icon = Icons.Outlined.Spa,
                text = stringResource(R.string.talk_tip_tools),
                onDismiss = {
                    tipDismissed = true
                    runCatching { Session.prefPut("talk_tip_tools", "1") }
                },
            )
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

        // V3-d: the middle escalation rung — between a normal reply and the
        // full crisis takeover. Warm, one real pathway, dismissible, and never
        // rendered while the crisis banner (which outranks it) is up.
        if (concernPending && !concernDismissed && !crisis) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardFill)
                    .border(1.dp, com.cerebrozen.app.ui.theme.Warm.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.talk_concern_title),
                    style = MaterialTheme.typography.titleMedium, color = TextSoft)
                Text(stringResource(R.string.talk_concern_body),
                    style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val rungRegion by rememberCrisisRegion()
                    val rungLine = primaryCrisisLine(rungRegion)
                    val rungIsUrl = isSupportUrl(rungLine.target)
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .border(1.dp, com.cerebrozen.app.ui.theme.Warm.copy(alpha = 0.6f), RoundedCornerShape(50))
                            .clickable { openSupportTarget(context, rungLine.target) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            if (rungIsUrl) stringResource(rungLine.nameRes)
                            else stringResource(R.string.talk_crisis_call, rungLine.target),
                            style = MaterialTheme.typography.labelLarge, color = TextSoft,
                        )
                    }
                    TextButton(onClick = { concernPending = false; concernDismissed = true }) {
                        Text(stringResource(R.string.talk_concern_keep), color = TextMuted)
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
                    Button(onClick = { showDisclosure = false }) {
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

        val voiceActive = voice.listening || cloud.recording ||
            voice.speaking || cloud.speaking || transcribing
        // V4: the orb belongs to the moments where it IS the interaction — an
        // opening screen with nothing to read, or a live turn. Above a real
        // conversation it was a small disconnected circle floating over the
        // first bubble, decoration where the mic in the composer already
        // carries the affordance.
        if ((voice.available || cloudVoice) && (messages.isEmpty() || voiceActive)) {
            VoiceOrb(
                listening = voice.listening || cloud.recording,
                speaking = voice.speaking || cloud.speaking,
                onTap = { onOrbTap() },
                thinking = transcribing || busy,
                level = if (cloud.recording) cloudLevel else voice.level,
                // It only renders when it IS the interaction, so it is never
                // the half-size version any more.
                compact = false,
            )
            val hint = when {
                transcribing -> stringResource(R.string.talk_hint_hearing)
                busy -> stringResource(R.string.talk_hint_thinking)
                cloud.speaking -> stringResource(R.string.talk_hint_speaking_interrupt)
                voice.speaking -> stringResource(R.string.talk_hint_speaking)
                cloud.recording -> stringResource(R.string.talk_hint_listening_done)
                voice.listening -> stringResource(R.string.talk_hint_listening_stop)
                // The orb now only renders on an empty screen or in a live
                // turn, so there is no quiet-hint case left to suppress.
                cloudVoice -> stringResource(R.string.talk_hint_orb_studio)
                else -> stringResource(R.string.talk_hint_orb)
            }
            hint?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = TextSoft,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        if (messages.isEmpty()) {
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
                                onOpened = {
                                    openedWidgets = openedWidgets + (windowStart + i)
                                    // Ask how it went when they come back.
                                    awaitingActivity = true
                                    runCatching { Session.prefPut("talk_activity_pending", "1") }
                                },
                            )
                        }
                    }
                }
                // V3-d: every asked-for reply can be re-rolled or flagged as
                // unhelpful — no reply pretends to be final. "Ask again" resends
                // the same words without repeating your bubble; "didn't help"
                // answers honestly and surfaces the structured offers.
                if (lastWasSend && messages.lastOrNull()?.role == "assistant" && !busy && streamText.isBlank()) {
                    val lastUserText = messages.lastOrNull { it.role == "user" }?.text
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (lastUserText != null) {
                            Text(
                                stringResource(R.string.talk_regen),
                                style = MaterialTheme.typography.labelMedium, color = Periwinkle,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .clickable { send(lastUserText, echo = false) }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                            )
                        }
                        val didntHelpAck = stringResource(R.string.talk_didnthelp_ack)
                        Text(
                            stringResource(R.string.talk_didnt_help),
                            style = MaterialTheme.typography.labelMedium, color = TextMuted,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    messages = messages + Msg("assistant", didntHelpAck)
                                    lastWasSend = false
                                }
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                        )
                    }
                }
                // Live reply: streamed tokens with a blinking caret, or a typing
                // indicator while the companion is composing its answer.
                if (streamText.isNotBlank()) {
                    StreamingBubble(streamText)
                } else if (busy) {
                    val typingCd = stringResource(R.string.talk_typing_cd)
                    Row(
                        Modifier.semantics { contentDescription = typingCd },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TypingDots()
                        // The agent announced a tool and its answer hasn't started
                        // streaming: name the work instead of leaving dots to carry
                        // an unexplained stall (WC-139). Deliberately quiet type —
                        // this is a status line, not a message.
                        toolActivity?.let { res ->
                            Text(
                                stringResource(R.string.oracle_tool_running, stringResource(res)),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                            )
                        }
                    }
                }
                // Structured exercises as first-class offers (REDESIGN §3.3) —
                // quiet, after the companion's latest reply, never while composing.
                // Ordered by the moment: spiralling words put grounding first,
                // late evening puts breathing first.
                // One rail, one source at a time: when the server sent chips
                // they lead; Try-together fills the quiet turns (two stacked
                // chip rows from different sources read as chip soup).
                // V5: quick replies — one tap to keep going, so the composer is
                // never the only way forward. They clear the moment one is used.
                if (quickReplies.isNotEmpty() && !busy && streamText.isBlank()) {
                    Row(
                        Modifier.bleed(pageHorizontalPadding()).horizontalScroll(rememberScrollState())
                            .padding(horizontal = pageHorizontalPadding()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        quickReplies.forEach { label ->
                            PickChip(selected = false, label = label) { onQuickReply(label) }
                        }
                    }
                }
                // V3-c: the opener's answer chips — sleep words first thing in
                // the morning, then the six wire moods. Answers append real
                // bubbles; the rows retire the moment their question is done.
                if (openerArmed && openerStage == "sleep") {
                    val words = sleepWords()
                    Row(
                        Modifier.bleed(pageHorizontalPadding()).horizontalScroll(rememberScrollState())
                            .padding(horizontal = pageHorizontalPadding()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        words.forEachIndexed { qi, word ->
                            PickChip(selected = false, label = word) {
                                if (busy) return@PickChip
                                messages = messages + Msg("user", word)
                                scope.launch {
                                    val saved = runCatching {
                                        Api.logSleep(LocalDate.now().toString(), "23:00", "07:00", qi + 1)
                                    }
                                    messages = messages + Msg(
                                        "assistant",
                                        when {
                                            saved.isSuccess -> opSleepDone
                                            saved.exceptionOrNull()?.isGuestGate() == true -> opSleepGuest
                                            else -> opSleepFailed
                                        },
                                    )
                                    if (todayMoodTalk == null) {
                                        messages = messages + Msg("assistant", opMoodQ)
                                        openerStage = "mood"
                                    } else {
                                        openerStage = "done"
                                    }
                                }
                            }
                        }
                    }
                }
                if (openerArmed && openerStage == "mood") {
                    Row(
                        Modifier.bleed(pageHorizontalPadding()).horizontalScroll(rememberScrollState())
                            .padding(horizontal = pageHorizontalPadding()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MOODS.forEach { mood ->
                            val moodLabel = stringResource(mood.labelRes)
                            PickChip(selected = false, label = moodLabel) {
                                if (busy) return@PickChip
                                messages = messages + Msg("user", moodLabel)
                                scope.launch {
                                    // A guest's answer still earns the card (it
                                    // is computed on-device); only the ROW needs
                                    // an account — the same ruling as Home.
                                    val gate = runCatching {
                                        Api.checkIn(mood.name, mood.note, mood.symbol, mood.intensity)
                                    }.exceptionOrNull()
                                    val kind = moodNbaKind(mood.name)
                                    messages = messages + Msg(
                                        "assistant",
                                        (if (mood.name == "Good") opMoodAckGood else opMoodAck) +
                                            (if (gate != null && gate.isGuestGate()) "\n" + opGuestNote else ""),
                                        widget = ChatWidget(kind, nbaTitles[kind].orEmpty(), nbaDescs[kind].orEmpty()),
                                    )
                                    openerStage = "done"
                                    com.cerebrozen.app.ui.Haptics.success()
                                }
                            }
                        }
                    }
                }
                // One rail, one source at a time (the rule the server-chip
                // branch already followed): quick replies answer the question
                // just asked, so the generic offers stand down while they are
                // up — two stacked chip rows read as chip soup.
                if (chips.isEmpty() && quickReplies.isEmpty() &&
                    openerStage !in setOf("sleep", "mood") &&
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
                            // Same surface as the Oracle's breathing WIDGET —
                            // the identical server suggestion used to open two
                            // different screens depending on transport (A24).
                            "breathing" -> onOpen("breathing")
                            "grounding" -> onOpen("ground")
                            else -> send(rawLabel)
                        }
                    }
                }
            }
        }

    }

    // ── V3-c tools tray: a bottom sheet without the experimental API ─────
    if (showTools) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { showTools = false }
                .zIndex(24f),
        )
        Column(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(CardFill)
                .padding(18.dp)
                .zIndex(25f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.talk_tools_eyebrow).uppercase(), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
            Text(stringResource(R.string.talk_tools_title), style = MaterialTheme.typography.titleLarge, color = TextSoft)
            // Reference tool tiles: a circular accent-mist icon well above the
            // label (the as-built Dawn rounds every well to a circle).
            data class TrayTool(val icon: ImageVector, val titleRes: Int, val subRes: Int, val route: String)
            // V4: two THREAD actions live here now, where "what can this
            // conversation do" is already the question — they used to be a
            // full-width bordered row and a floating chip stacked under the
            // transcript, competing with the companion's own offers.
            val threadTools = buildList {
                if (messages.isNotEmpty()) {
                    add(TrayTool(Icons.Outlined.BookmarkBorder, R.string.talk_save_journal, R.string.talk_tool_save_sub, "@save"))
                    add(TrayTool(Icons.Outlined.Refresh, R.string.talk_start_fresh, R.string.talk_tool_fresh_sub, "@fresh"))
                }
            }
            val wellbeingTools = listOf(
                TrayTool(Icons.Outlined.Favorite, R.string.talk_tool_checkin, R.string.talk_tool_checkin_sub, "checkin"),
                TrayTool(Icons.Outlined.SelfImprovement, R.string.talk_tool_breathe, R.string.talk_tool_breathe_sub, "breathe/reset"),
                TrayTool(Icons.Outlined.Spa, R.string.talk_tool_ground, R.string.talk_tool_ground_sub, "ground"),
                TrayTool(Icons.Outlined.Edit, R.string.talk_tool_journal, R.string.talk_tool_journal_sub, "journal/new"),
            )
            // Four choices is enough to scan. In an active thread its two
            // thread actions lead, followed by the two quickest practices;
            // everything else stays reachable through one explicit door.
            val tools = (threadTools + wellbeingTools).take(4)
            tools.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { tool ->
                        Column(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.dp, LineStroke, RoundedCornerShape(18.dp))
                                .clickable {
                                    showTools = false
                                    when (tool.route) {
                                        "" -> onOrbTap()
                                        "@save" -> saveThreadToJournal()
                                        "@fresh" -> startFresh()
                                        else -> onOpen(tool.route)
                                    }
                                }
                                .padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(com.cerebrozen.app.ui.theme.AccentSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(tool.icon, contentDescription = null, tint = Periwinkle, modifier = Modifier.size(17.dp))
                            }
                            Text(stringResource(tool.titleRes), style = MaterialTheme.typography.titleSmall, color = TextSoft)
                            Text(stringResource(tool.subRes), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .clickable { showTools = false; onOpen("toolkit") }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Extension, contentDescription = null, tint = Periwinkle, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.talk_all_tools), style = MaterialTheme.typography.titleSmall, color = TextSoft)
                    Text(stringResource(R.string.talk_all_tools_sub), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }

    // Ref LIVE VOICE SESSION: an immersive overlay that stays up across turns.
    //
    // The status-bar scrim that used to sit here is gone. It never covered the
    // status bar: this Box is laid out inside the Scaffold's content, which
    // already excludes that inset, so its top edge is the top edge of the APP
    // BAR — and `Night` is a theme-aware token, meaning in Dawn it painted 88%
    // cream straight over the bar's title, gear, shield and brand mark, fading
    // all four together.
    //
    // Measured on glass, Dawn: the Chat title read 2.97:1 against its own
    // background, under the 3:1 floor for large text, while the identical bar on
    // Home read 16.28:1. Shrinking the scrim to the inset height only halved it
    // (4.57:1); removing it entirely puts the title at exactly Home's ink,
    // (33,29,32). Nothing is lost — `Page` applies `statusBarsPadding`, so
    // content never reaches the status bar, and the bar itself has been opaque
    // since the density pass.

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
            // On-device recognition streams partials; the cloud path transcribes
            // the whole take afterwards, so this is empty there and the screen
            // falls back to the companion's words.
            heard = voice.partial,
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

/**
 * A live waveform — five bars breathing with the mic.
 *
 * The reference's listening screen puts this under the transcript, and it is
 * doing real work: it is the only element that distinguishes "the mic is open
 * and hearing you" from "the mic is open and hearing nothing", which is the
 * question a person stares at a listening screen asking. Driven by the real
 * amplitude, never a decorative loop — under Reduce Motion the bars hold a
 * static resting shape rather than vanishing.
 */
@Composable
private fun VoiceWaveform(level: Float, active: Boolean, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    // Each bar reacts at its own weight so the row reads as a voice rather than
    // five identical sliders.
    val weights = listOf(0.55f, 0.85f, 1f, 0.8f, 0.5f)
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        weights.forEachIndexed { i, w ->
            val target = if (!active || reduceMotion) 0.34f else (0.22f + level * w).coerceIn(0.15f, 1f)
            val h by androidx.compose.animation.core.animateFloatAsState(
                targetValue = target,
                animationSpec = androidx.compose.animation.core.tween(if (reduceMotion) 0 else 160),
                label = "wave-$i",
            )
            Box(
                Modifier
                    .width(4.dp)
                    .height((34 * h).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (active) Cyan else TextMuted2),
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
    /** What the recognizer is hearing right now, shown back while you speak. */
    heard: String,
    onOrb: () -> Unit,
    onEnd: () -> Unit,
    onText: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            // Fully opaque: at 0.97 the transcript ghosted through and the
            // live screen read as a dialog over the chat rather than as the
            // place you now are (device walk 2026-08-16).
            .background(Night)
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            VoiceOrb(listening = listening, speaking = speaking, onTap = onOrb, thinking = thinking, level = level)
            Text(stateLabel, style = MaterialTheme.typography.titleMedium, color = TextSoft, textAlign = TextAlign.Center)
            // The waveform belongs to LISTENING; while the companion speaks or
            // thinks, the orb already carries the state and a second animation
            // would just be noise.
            VoiceWaveform(level = level, active = listening)
            when {
                // Your own words, as they are recognised — in the display serif
                // and quoted, so it reads as speech rather than as a reply.
                listening && heard.isNotBlank() -> Text(
                    "“" + heard.take(160) + "”",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily(
                            androidx.compose.ui.text.font.Font(R.font.newsreader),
                        ),
                    ),
                    color = TextSoft, textAlign = TextAlign.Center,
                )
                // Otherwise the companion's latest words.
                caption.isNotBlank() -> Text(
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
private fun TryTogetherRow(
    onOpen: (String) -> Unit,
    order: List<String> = listOf("reframe", "breathe", "ground"),
) {
    // The label used to sit INLINE with the chips (audit I#9: as a two-row unit
    // the scroll fold landed between heading and children, stranding a heading
    // over nothing). V4 removes the visible label instead of moving it back:
    // it cost ~90dp of a 328dp row, which pushed the third offer off the right
    // edge on every 360dp phone, and the chips are now single verbs that name
    // themselves. The meaning survives for screen readers as a group label —
    // where it never competed for width in the first place.
    val offersCd = stringResource(R.string.talk_try_together)
    Row(
        Modifier.bleed(pageHorizontalPadding()).horizontalScroll(rememberScrollState())
            .padding(horizontal = pageHorizontalPadding())
            .semantics { contentDescription = offersCd },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
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
    // Reference fidelity (Aira NBA card, as-built Dawn): an accent-mist pane,
    // a solid 40dp icon badge, the serif title, and ONE full-width primary
    // pill — the suggested step is the loudest thing in the thread, exactly
    // once. The old form was a quiet bordered row with a text link.
    // V5 volume pass: this is an OFFER inside a conversation, so it stops
    // shouting. The badge and title stay (identity and what it is), but the
    // call to action is a wrap-width pill rather than a full-bleed deep-plum
    // bar — full width read as the screen's primary action when the primary
    // action is talking, and it sat directly under the companion's own words.
    // The card also aligns to the bubbles' left edge instead of the page.
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(com.cerebrozen.app.ui.theme.AccentSoft)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(Periwinkle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    widgetIcon(w.kind), contentDescription = null,
                    tint = OnPrimary, modifier = Modifier.size(16.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.talk_suggested_activity), style = MaterialTheme.typography.labelSmall, color = Periwinkle)
                Text(
                    w.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily(
                            androidx.compose.ui.text.font.Font(R.font.newsreader),
                        ),
                    ),
                    color = com.cerebrozen.app.ui.theme.TextPrimary,
                )
            }
        }
        Text(w.description, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        if (route != null) {
            PrimaryButton(
                stringResource(R.string.common_open),
                modifier = Modifier.padding(top = 2.dp),
            ) { onOpened?.invoke(); onOpen(route) }
        } else {
            Text(stringResource(R.string.talk_ios_only), style = MaterialTheme.typography.bodySmall, color = TextMuted2)
        }
    }
}

/** The icon a widget kind's badge wears (reference NBA badge language). */
internal fun widgetIcon(kind: String): ImageVector = when (kind) {
    "breathing" -> Icons.Outlined.SelfImprovement
    "grounding" -> Icons.Outlined.Spa
    "one_good_thing" -> Icons.Outlined.LightMode
    "intention_set" -> Icons.Outlined.Flag
    "sleep_checkin" -> Icons.Outlined.Bedtime
    "mini_journal", "journal" -> Icons.Outlined.Edit
    "dbt_skill" -> Icons.Outlined.Bolt
    else -> Icons.Outlined.ChatBubbleOutline
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
        verticalAlignment = Alignment.Bottom,
    ) {
        // V5: the companion's face beside the LAST bubble of each of its runs
        // (the reference's avatar). On a long thread the two voices were told
        // apart by alignment and fill alone; a run now has an owner. Only the
        // tail carries it, so a three-bubble answer doesn't stamp three faces
        // down the margin — the rest keep the width via a matching spacer.
        if (!user) {
            if (tail) {
                // The real brand mark, not a tinted disc: a two-colour circle
                // read as a broken image at 26dp. This is the same orb the top
                // bar and the splash draw, so the face beside a reply is
                // recognisably the app's own.
                com.cerebrozen.app.ui.BrandMark(size = 26.dp, showGlow = false)
            } else {
                Spacer(Modifier.size(26.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
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
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Text(
                    annotated,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSoft,
                    modifier = Modifier
                        .let { mod ->
                            if (announce) mod.semantics {
                                liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                            } else mod
                        },
                )
                // WC-138, the read half: which tools produced this reply, on the
                // reply itself. The write half already has the confirm card and
                // GET /oracle/actions; reads were invisible. Quiet on purpose —
                // provenance, not decoration.
                if (!user && m.tools.isNotEmpty()) {
                    // A for-loop, not map{}: stringResource is @Composable and
                    // lambdas passed to stdlib functions are not composable scope.
                    val labels = ArrayList<String>(m.tools.size)
                    for (id in m.tools) labels += stringResource(id)
                    Text(
                        stringResource(R.string.oracle_tool_used, labels.joinToString(" · ")),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
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
    val orbCd = stringResource(
        if (listening) R.string.talk_orb_stop_cd else R.string.talk_orb_talk_cd,
    )
    val stage = if (compact) 96.dp else 150.dp
    val orbSize = if (compact) 64.dp else 112.dp
    val haloSize = if (compact) 100.dp else 170.dp
    val highlight = if (compact) 24.dp else 40.dp
    val highlightOffset = if (compact) (-10).dp else (-17).dp
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
                .semantics { contentDescription = orbCd }
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
