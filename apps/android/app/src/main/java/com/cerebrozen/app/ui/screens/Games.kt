package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Warm
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// Five more calm games (iOS GamesHub parity): memory match, pattern glow,
// zen ripples, bubble wrap, gratitude garden. All gentle — no timers, no
// fail states that scold.

// ── Memory match ─────────────────────────────────────────────────────────
internal val MEMORY_EMOJIS = listOf("🌙", "🌊", "🍃", "☁️", "⭐", "🪷")

/** A shuffled deck of pairs — pure, so the pairing contract is testable. */
internal fun buildDeck(emojis: List<String>, random: Random = Random.Default): List<String> =
    (emojis + emojis).shuffled(random)

@Composable
fun MemoryMatchScreen(onBack: () -> Unit) {
    var deck by remember { mutableStateOf(buildDeck(MEMORY_EMOJIS)) }
    var faceUp by remember { mutableStateOf(listOf<Int>()) }
    var matched by remember { mutableStateOf(setOf<Int>()) }
    var moves by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    fun tap(i: Int) {
        if (i in matched || i in faceUp || faceUp.size == 2) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        faceUp = faceUp + i
        if (faceUp.size == 2) {
            moves++
            val (a, b) = faceUp
            if (deck[a] == deck[b]) {
                matched = matched + a + b
                faceUp = emptyList()
            } else {
                scope.launch { delay(700); faceUp = emptyList() }
            }
        }
    }

    SubPage("A gentle pairing", "Memory match", onBack) {
        Text("Find the pairs at your own pace — no clock.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            deck.indices.chunked(3).forEach { rowIdx ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowIdx.forEach { i ->
                        val shown = i in matched || i in faceUp
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    when {
                                        i in matched -> Ok.copy(alpha = 0.22f)
                                        shown -> Periwinkle.copy(alpha = 0.30f)
                                        else -> Color.White.copy(alpha = 0.075f)
                                    },
                                )
                                .border(1.dp, if (shown) Periwinkle.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                .clickable { tap(i) }
                                .semantics { role = Role.Button; contentDescription = "Memory card" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (shown) deck[i] else "✦",
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (shown) TextSoft else TextMuted)
                        }
                    }
                }
            }
        }
        if (matched.size == deck.size) {
            Text("All matched in $moves moves — nicely done.",
                style = MaterialTheme.typography.titleMedium, color = Ok)
            TextButton(onClick = {
                deck = buildDeck(MEMORY_EMOJIS); matched = emptySet(); faceUp = emptyList(); moves = 0
            }) { Text("Play again", color = Periwinkle) }
        } else {
            Text("$moves moves · ${matched.size / 2} of ${deck.size / 2} pairs",
                style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

// ── Pattern glow (gentle Simon) ──────────────────────────────────────────
@Composable
fun PatternGlowScreen(onBack: () -> Unit) {
    val pads = listOf(Periwinkle, Cyan, Warm, Ok)
    var sequence by remember { mutableStateOf(listOf(Random.nextInt(4))) }
    var lit by remember { mutableIntStateOf(-1) }
    var inputPos by remember { mutableIntStateOf(0) }
    var showing by remember { mutableStateOf(true) }
    var best by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("Watch the glow, then repeat it.") }
    val haptics = LocalHapticFeedback.current

    // Replays the sequence whenever it changes (or after a gentle reset).
    LaunchedEffect(sequence) {
        showing = true
        delay(600)
        sequence.forEach { pad ->
            lit = pad; delay(450); lit = -1; delay(180)
        }
        showing = false
        inputPos = 0
    }

    fun tap(pad: Int) {
        if (showing) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (pad == sequence[inputPos]) {
            inputPos++
            if (inputPos == sequence.size) {
                best = maxOf(best, sequence.size)
                note = "Round ${sequence.size} — lovely. One more glow."
                sequence = sequence + Random.nextInt(4)
            }
        } else {
            note = "Reached round ${sequence.size}. Fresh start, no rush."
            sequence = listOf(Random.nextInt(4))
        }
    }

    SubPage("Follow the light", "Pattern glow", onBack) {
        Text(note, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(0 to 1, 2 to 3).forEach { (l, r) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(l, r).forEach { pad ->
                        val active = lit == pad
                        Box(
                            Modifier.weight(1f).height(130.dp)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color.White.copy(alpha = if (active) 0.70f else 0.16f),
                                            pads[pad].copy(alpha = if (active) 0.88f else 0.26f),
                                        ),
                                    ),
                                )
                                .border(1.dp, pads[pad].copy(alpha = if (active) 0.85f else 0.45f), RoundedCornerShape(20.dp))
                                .clickable { tap(pad) }
                                .semantics { role = Role.Button; contentDescription = "Pad ${pad + 1}" },
                        )
                    }
                }
            }
        }
        Text(
            (if (showing) "Watching…" else "Your turn") + if (best > 0) "  ·  best round $best" else "",
            style = MaterialTheme.typography.labelSmall, color = TextMuted,
        )
    }
}

// ── Zen ripples ──────────────────────────────────────────────────────────
private data class Ripple(val at: Offset, val born: Long)

@Composable
fun ZenRipplesScreen(onBack: () -> Unit) {
    var ripples by remember { mutableStateOf(listOf<Ripple>()) }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { withFrameNanos { now = it } } }

    SubPage("Still water", "Zen ripples", onBack) {
        Text("Tap anywhere on the water. Watch each ripple widen and let go.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Box(
            Modifier.fillMaxWidth().height(400.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.radialGradient(
                        listOf(Cyan.copy(alpha = 0.33f), Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.025f)),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        ripples = (ripples + Ripple(offset, System.nanoTime())).takeLast(12)
                    }
                }
                .semantics { contentDescription = "Ripple canvas" },
        ) {
            Canvas(Modifier.matchParentSize()) {
                ripples.forEach { r ->
                    val age = (now - r.born) / 1_000_000_000f
                    if (age in 0f..3f) {
                        val alpha = (1f - age / 3f) * 0.5f
                        drawCircle(Cyan.copy(alpha = alpha), radius = 30f + age * 220f, center = r.at, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                        drawCircle(Periwinkle.copy(alpha = alpha * 0.6f), radius = 10f + age * 140f, center = r.at, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                    }
                }
            }
        }
    }
}

// ── Bubble wrap ──────────────────────────────────────────────────────────
@Composable
fun BubbleWrapScreen(onBack: () -> Unit) {
    val cols = 6
    val rows = 8
    var popped by remember { mutableStateOf(setOf<Int>()) }
    val haptics = LocalHapticFeedback.current

    SubPage("Pop at will", "Bubble wrap", onBack) {
        Text("A fresh sheet, endlessly poppable. Very serious stress research.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            (0 until rows).forEach { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0 until cols).forEach { c ->
                        val i = r * cols + c
                        val isPopped = i in popped
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(if (isPopped) CardFill else Periwinkle.copy(alpha = 0.32f))
                                .border(1.dp, if (isPopped) LineStroke else Periwinkle.copy(alpha = 0.70f), CircleShape)
                                .clickable(enabled = !isPopped) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    popped = popped + i
                                }
                                .semantics { role = Role.Button; contentDescription = "Bubble" },
                        )
                    }
                }
            }
        }
        if (popped.size == cols * rows) {
            TextButton(onClick = { popped = emptySet() }) { Text("Fresh sheet", color = Periwinkle) }
        } else {
            Text("${popped.size} of ${cols * rows} popped",
                style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

// ── Gratitude garden ─────────────────────────────────────────────────────
internal val FLOWERS = listOf("🌸", "🌼", "🌷", "🌻", "💮", "🪻")

/** Deterministic flower per entry index — testable, stable across launches. */
internal fun flowerFor(index: Int): String = FLOWERS[index % FLOWERS.size]

@Composable
fun GratitudeGardenScreen(onBack: () -> Unit) {
    var entries by remember { mutableStateOf(Gratitude.all()) }
    var draft by remember { mutableStateOf("") }

    SubPage("Grow something good", "Gratitude garden", onBack) {
        Text("Name one small thing you're thankful for — a flower grows for each.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        AppTextField(draft, { draft = it }, "One good thing…", singleLine = true)
        PrimaryButton(text = "Plant it", enabled = draft.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            entries = Gratitude.add(draft)
            draft = ""
        }
        if (entries.isEmpty()) {
            Text("Your garden is waiting for its first flower.",
                style = MaterialTheme.typography.labelSmall, color = TextMuted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        } else {
            SectionCard {
                entries.asReversed().forEachIndexed { revIdx, text ->
                    val i = entries.size - 1 - revIdx
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(Periwinkle.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(flowerFor(i), style = MaterialTheme.typography.titleLarge)
                        }
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSoft, modifier = Modifier.weight(1f))
                    }
                }
            }
            Text("${entries.size} ${if (entries.size == 1) "flower" else "flowers"} and counting.",
                style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}
