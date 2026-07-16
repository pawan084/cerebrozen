package com.cerebrozen.app.ui.screens

/* The Coach tab: a live coaching session against the engine's governed arc.
 * Tokens stream into the last coach bubble; action cards from `done` land in
 * ActionsStore (the Actions tab + the coach's 7-day check-in close the loop). */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.cerebrozen.app.audio.VoiceEngine
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Coach
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.theme.BrandPrimary
import com.cerebrozen.app.ui.theme.OnPrimary
import com.cerebrozen.app.ui.theme.Accent
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.ChipFill
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.cerebrozen.app.coach.Suggestion
import com.cerebrozen.app.coach.detectIntent
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.Warm
import org.json.JSONObject
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.cerebrozen.app.R

// A chat entry is either text (a bubble) or a CARD the coach surfaced in line —
// an engine-suggested commitment, or an intent-matched tool (see coach/Intent.kt).
private sealed interface ChatCard
private data class SuggestChatCard(val s: Suggestion) : ChatCard
private data class ActionChatCard(val body: String) : ChatCard

/**
 * The engine's transcript, turned back into the thread.
 *
 * `chat_history` is a list of one-key objects: `{"user": {...}}` or `{"bot": {...}}`, each
 * with a `text`. Pure and separate from the composable because it is a WIRE CONTRACT — and
 * because the app previously kept the thread in memory only, so a process death lost the
 * conversation an employee was mid-way through. A coaching session spans a commute.
 *
 * `hidden` messages are the engine's own plumbing (system nudges the person never saw);
 * replaying them would show someone a conversation they did not have.
 */
internal fun parseHistory(body: JSONObject): List<Pair<String, String>> {
    val rows = body.optJSONArray("chat_history") ?: return emptyList()
    val out = mutableListOf<Pair<String, String>>()
    for (i in 0 until rows.length()) {
        val row = rows.optJSONObject(i) ?: continue
        val who = when {
            row.has("user") -> "you"
            row.has("bot") -> "coach"
            else -> continue
        }
        val entry = row.optJSONObject(if (who == "you") "user" else "bot") ?: continue
        if (entry.optBoolean("hidden", false)) continue
        val text = entry.optString("text").trim()
        if (text.isNotEmpty()) out.add(who to text)
    }
    return out
}

private data class CoachMsg(
    val who: String,
    var text: String = "",
    val grounded: Boolean = false,
    val card: ChatCard? = null,
)

/** Pre-first-token typing indicator (Mira reference): three quiet pulsing
 * dots. Reduce Motion shows a static ellipsis. */
@Composable
private fun TypingDots() {
    val reduceMotion = rememberReduceMotion()
    if (reduceMotion) {
        Text("…", style = MaterialTheme.typography.bodyLarge, color = TextMuted)
        return
    }
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(600, delayMillis = i * 180),
                    androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier.size(7.dp)
                    .background(TextMuted.copy(alpha = alpha), androidx.compose.foundation.shape.CircleShape),
            )
        }
    }
}

// Suggestion.icon / .accent are string keys (Intent.kt is pure Kotlin, no Compose) —
// resolved to real icons and theme colours here.
private fun iconFor(key: String): ImageVector = when (key) {
    "bedtime" -> Icons.Outlined.Bedtime
    "air" -> Icons.Outlined.Air
    "spa" -> Icons.Outlined.Spa
    "psychology" -> Icons.Outlined.Psychology
    "diversity" -> Icons.Outlined.Diversity3
    else -> Icons.Outlined.AutoAwesome
}

private fun accentFor(key: String): Color = when (key) {
    "peri" -> Periwinkle
    "warm" -> Warm
    else -> Cyan
}

/** An intent-matched tool the coach offers in line — tap anywhere to open it. */
@Composable
private fun SuggestionCardView(s: Suggestion, onOpen: (String) -> Unit) {
    val accent = accentFor(s.accent)
    Row(
        Modifier.fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
            .background(CardFill)
            .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
            .clickable { onOpen(s.route) }
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape)
                .background(accent.copy(alpha = 0.14f))
                .border(1.dp, accent.copy(alpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconFor(s.icon), contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(s.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(s.subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Text(
            s.cta,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            modifier = Modifier.clip(RoundedCornerShape(999.dp))
                .background(accent.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** A commitment the session captured this turn — mirrored in line, tap to manage it. */
@Composable
private fun ActionCardView(body: String, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
            .background(CardFill)
            .border(1.dp, Ok.copy(alpha = 0.35f), RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
            .clickable { onOpen() }
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Ok, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Saved to your Actions", style = MaterialTheme.typography.labelMedium, color = Ok)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        Text("View", style = MaterialTheme.typography.labelMedium, color = Accent.talk)
    }
}

/**
 * The standing AI disclosure.
 *
 * Every string here already shipped (`talk_disclosure_*`) — the screen that rendered them
 * was deleted in the B2C strip and nothing replaced it, so the app disclosed once, during
 * onboarding, and never again. An employee is being asked to talk candidly inside software
 * their employer pays for; "this is AI, it is not a clinician, and it is not for
 * emergencies" belongs where they are typing, not in a screen they saw once on day one.
 *
 * The dialog carries a route to crisis support, because the moment someone reads "not for
 * emergencies" is exactly the moment they might need the thing it is not.
 */
@Composable
private fun DisclosurePill(onOpen: (String) -> Unit) {
    var showing by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = pageHorizontalPadding())
            // 44dp: a disclosure nobody can tap is decoration.
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(ChipFill)
            .clickable(role = Role.Button) { showing = true }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.talk_disclosure_pill),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.talk_disclosure_details),
            style = MaterialTheme.typography.labelSmall,
            color = Cyan,
        )
    }
    if (showing) {
        AlertDialog(
            onDismissRequest = { showing = false },
            title = { Text(stringResource(R.string.talk_disclosure_dialog_title)) },
            text = { Text(stringResource(R.string.talk_disclosure_dialog_body)) },
            confirmButton = {
                TextButton(onClick = { showing = false }) {
                    Text(stringResource(R.string.talk_disclosure_ok))
                }
            },
            // Reading "it isn't for emergencies" is exactly when someone might need one.
            dismissButton = {
                TextButton(onClick = { showing = false; onOpen("crisis") }) {
                    Text(stringResource(R.string.talk_disclosure_crisis))
                }
            },
        )
    }
}

@Composable
fun CoachScreen(onOpen: (String) -> Unit) {
    val messages = remember { mutableStateListOf<CoachMsg>() }
    // A tool card is an offer, not a nag — show each intent at most once a session.
    val shownIntents = remember { mutableSetOf<String>() }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    /* Pick the thread back up after a process death.
     *
     * The engine is asked, not a local cache: it knows which session is still open, its
     * answer survives a reinstall, and a cached id can be stale in a way the server's
     * answer never is (the session may have ended on another device). Best-effort by
     * design — a failure here leaves an empty composer, which is the old behaviour, not a
     * broken screen. */
    LaunchedEffect(Unit) {
        if (messages.isNotEmpty() || Coach.sessionId != null) return@LaunchedEffect
        runCatching {
            val open = Api.resumableSession()
            if (!open.optBoolean("resumable")) return@runCatching
            val id = open.optString("session_id").takeIf { it.isNotBlank() } ?: return@runCatching
            val restored = parseHistory(Api.sessionHistory(id))
            if (restored.isEmpty()) return@runCatching
            Coach.adopt(id)
            restored.forEach { (who, text) -> messages.add(CoachMsg(who = who, text = text)) }
        }
    }

    // On-device voice (keyless): mic dictates into the composer; replies can
    // be read aloud. The cloud-quality loop stays the tracked v2 item.
    val voiceCtx = LocalContext.current
    val voice = remember { VoiceEngine(voiceCtx) }
    DisposableEffect(Unit) { onDispose { voice.dispose() } }
    var speakReplies by remember { mutableStateOf(Session.prefGet("coach_speak_replies") == "1") }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) voice.startListening { draft = it } }
    fun micTap() {
        if (voice.listening) { voice.stopListening(); return }
        if (voiceCtx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voice.startListening { draft = it }
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || busy) return
        draft = ""
        busy = true
        statusLine = ""
        messages.add(CoachMsg("you", text))
        val reply = CoachMsg("coach", "")
        messages.add(reply)
        val replyIndex = messages.size - 1
        scope.launch {
            try {
                val done = Coach.turn(
                    text,
                    onStatus = { statusLine = it },
                ) { chunk ->
                    // Streaming: replace (not mutate) so Compose sees the change.
                    messages[replyIndex] = messages[replyIndex].copy(
                        text = messages[replyIndex].text + chunk,
                    )
                }
                statusLine = ""
                // The grounded line: shown only when the engine actually served
                // retrieved, reviewed material this turn (learning_aid stage ran).
                if ("learning_aid" in done.stages) {
                    messages[replyIndex] = messages[replyIndex].copy(grounded = true)
                }
                if (speakReplies && messages[replyIndex].text.isNotBlank()) {
                    voice.speak(messages[replyIndex].text)
                }
                // The commit gate's product half: cards the session saved land
                // on the Actions tab and the Today count immediately.
                done.payload.optJSONArray("actions")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val a = arr.optJSONObject(i) ?: continue
                        val cardText = a.optString("full_text")
                            .ifBlank { a.optString("action_body") }
                            .ifBlank { a.optString("text") }
                        val id = a.optString("action_id").ifBlank { cardText.hashCode().toString() }
                        if (cardText.isNotBlank()) {
                            ActionsStore.add(id, cardText)
                            // Mirror the saved commitment in line, so the gate is visible
                            // in the conversation, not only on the Actions tab.
                            messages.add(CoachMsg("coach", card = ActionChatCard(cardText)))
                        }
                    }
                }
                if (messages[replyIndex].text.isBlank()) {
                    val fallback = done.payload.optString("reply")
                        .ifBlank { done.payload.optString("text") }
                    messages[replyIndex] = messages[replyIndex].copy(
                        text = fallback.ifBlank { "…" },
                    )
                }
            } catch (e: Exception) {
                messages[replyIndex] = messages[replyIndex].copy(
                    text = "The coach is unreachable right now — your message wasn't lost on our side. Try again in a moment.",
                )
            } finally {
                busy = false
                // Intent → inline tool card. When the turn clearly maps to a tool the app
                // already has (sleep, a racing mind, an overthought fear…), offer it one tap
                // away — at most once per intent per session. Runs even when the coach was
                // unreachable, since that's exactly when the offline tools matter most.
                // Crisis is never a card here; that stays the engine's deterministic takeover.
                detectIntent(text, messages.getOrNull(replyIndex)?.text ?: "")?.let { s ->
                    if (shownIntents.add(s.id)) {
                        messages.add(CoachMsg("coach", card = SuggestChatCard(s)))
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.padding(horizontal = pageHorizontalPadding()).padding(top = 28.dp)) {
            PageHeader(eyebrow = "Your coach", title = "Coach", accent = Accent.talk)
        }
        // The transparency chip (Mira reference): what the coach is carrying
        // for you, from on-device state — tap to manage it on the Actions tab.
        val openActions = ActionsStore.openCount()
        Row(
            Modifier.padding(horizontal = pageHorizontalPadding()).padding(top = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardFill)
                .clickable { onOpen("actions") }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (openActions > 0)
                    "Your coach remembers · $openActions open commitment" + (if (openActions > 1) "s" else "")
                else "Your coach remembers what you commit to — sessions stay private to you",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                modifier = Modifier.weight(1f),
            )
            Text("View", style = MaterialTheme.typography.labelMedium, color = Accent.talk)
        }
        if (voice.available) {
            Row(
                Modifier.padding(horizontal = pageHorizontalPadding()).padding(top = 6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable {
                        speakReplies = !speakReplies
                        Session.prefPut("coach_speak_replies", if (speakReplies) "1" else "0")
                        if (!speakReplies) voice.dispose()
                    }
                    .background(if (speakReplies) ChipFill else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    if (speakReplies) "Replies aloud · on" else "Replies aloud · off",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (speakReplies) TextPrimary else TextMuted,
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth()
                .padding(horizontal = pageHorizontalPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    SectionCard {
                        Text("What's in front of you?", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            "A conversation you're postponing, a decision that's stalling, a moment you want to get right — start there. Every session ends with one concrete step.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                        )
                    }
                }
            }
            items(messages) { m ->
                val card = m.card
                if (card != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        when (card) {
                            is SuggestChatCard -> SuggestionCardView(card.s) { onOpen(it) }
                            is ActionChatCard -> ActionCardView(card.body) { onOpen("actions") }
                        }
                    }
                    return@items
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (m.who == "you") Arrangement.End else Arrangement.Start,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.86f)
                            .background(
                                if (m.who == "you") ChipFill else CardFill,
                                RoundedCornerShape(
                                    16.dp, 16.dp,
                                    if (m.who == "you") 4.dp else 16.dp,
                                    if (m.who == "you") 16.dp else 4.dp,
                                ),
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (m.text.isBlank() && busy) {
                                TypingDots()
                            } else {
                                Text(
                                    m.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary,
                                )
                            }
                            if (m.grounded) {
                                Text(
                                    "Grounded in reviewed material — guidance, not diagnosis",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Ok,
                                )
                            }
                        }
                    }
                }
            }
            if (statusLine.isNotBlank()) {
                item {
                    Text(statusLine, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
        // Directly above the composer: a standing disclosure, where they are typing.
        DisclosurePill(onOpen)
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = pageHorizontalPadding(), vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (voice.available) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(999.dp))
                        .background(if (voice.listening) BrandPrimary else ChipFill)
                        .clickable { micTap() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (voice.listening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                        contentDescription = if (voice.listening) "Stop listening" else "Dictate",
                        tint = if (voice.listening) OnPrimary else TextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Talk it through…", color = TextMuted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
            )
            PrimaryButton(if (busy) "…" else "Send", enabled = !busy && draft.isNotBlank()) { send() }
        }
    }
}
