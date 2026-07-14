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
import androidx.compose.runtime.Composable
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
import com.cerebrozen.app.net.Coach
import com.cerebrozen.app.ui.theme.Accent
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.ChipFill
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

private data class CoachMsg(val who: String, var text: String, val grounded: Boolean = false)

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

@Composable
fun CoachScreen(onOpen: (String) -> Unit) {
    val messages = remember { mutableStateListOf<CoachMsg>() }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

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
                // The commit gate's product half: cards the session saved land
                // on the Actions tab and the Today count immediately.
                done.payload.optJSONArray("actions")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val a = arr.optJSONObject(i) ?: continue
                        val cardText = a.optString("full_text")
                            .ifBlank { a.optString("action_body") }
                            .ifBlank { a.optString("text") }
                        val id = a.optString("action_id").ifBlank { cardText.hashCode().toString() }
                        if (cardText.isNotBlank()) ActionsStore.add(id, cardText)
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
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = pageHorizontalPadding(), vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
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
