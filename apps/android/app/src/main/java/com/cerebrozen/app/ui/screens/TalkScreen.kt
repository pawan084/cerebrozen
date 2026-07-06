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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.cerebrozen.app.audio.VoiceEngine
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal data class Msg(val role: String, val text: String)

internal fun parseChat(rows: JSONArray): List<Msg> =
    (0 until rows.length()).map { i ->
        val m = rows.getJSONObject(i)
        Msg(m.getString("role"), m.getString("text"))
    }

/** Talk: a real voice companion (on-device speech ↔ TTS over /chat) with a
 * text fallback. Same deterministic, safety-scanned pipeline as iOS/web. */
@Composable
fun TalkScreen() {
    var messages by remember { mutableStateOf(listOf<Msg>()) }
    var draft by remember { mutableStateOf("") }
    var chips by remember { mutableStateOf(listOf<String>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val voice = remember { VoiceEngine(context) }
    DisposableEffect(Unit) { onDispose { voice.dispose() } }

    LaunchedEffect(Unit) { runCatching { messages = parseChat(Api.chat()) } }

    fun send(text: String, speak: Boolean = false) {
        if (text.isBlank() || busy) return
        busy = true; status = null
        scope.launch {
            try {
                val reply: JSONObject = Api.sendChat(text.trim())
                val replyText = reply.getJSONObject("reply").getString("text")
                messages = messages +
                    Msg("user", reply.getJSONObject("user_message").getString("text")) +
                    Msg("assistant", replyText)
                chips = reply.optJSONArray("suggestions")?.let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it).getString("label") }
                } ?: emptyList()
                draft = ""
                if (speak) voice.speak(replyText)
            } catch (e: Exception) {
                status = e.message ?: "Couldn't send."
            } finally {
                busy = false
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.startListening { t -> send(t, speak = true) }
        else status = "Microphone access is off — you can still type below."
    }

    fun onOrbTap() {
        if (!voice.available) return
        if (voice.listening) { voice.stopListening(); return }
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) voice.startListening { t -> send(t, speak = true) }
        else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Page("AI voice companion", "Talk it through") {
        Text(
            "Supportive AI — not medical care. In an emergency, contact local emergency services.",
            style = MaterialTheme.typography.bodySmall, color = TextMuted,
        )

        if (voice.available) {
            VoiceOrb(listening = voice.listening, speaking = voice.speaking, onTap = { onOrbTap() })
            val hint = when {
                busy -> "Thinking…"
                voice.speaking -> "Speaking…"
                voice.listening -> "Listening… tap to stop"
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
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                messages.takeLast(12).forEach { m -> ChatBubble(m) }
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
