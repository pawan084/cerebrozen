package com.cerebrozen.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.VeilLine
import com.cerebrozen.app.ui.theme.Warm
import kotlinx.coroutines.delay
import kotlin.random.Random

// The Toolkit's sensory activities (REDESIGN §2.2): pattern glow (the single
// attention anchor), zen ripples (sensory grounding) and gratitude garden
// (gratitude practice with real persistence). All gentle — no timers, no fail
// states that scold. The casual games (memory match, sliding puzzle, bubble
// wrap, colour breathing) were retired in the consolidation pass.

// ── Pattern glow (gentle Simon) ──────────────────────────────────────────
@Composable
fun PatternGlowScreen(onBack: () -> Unit) {
    val pads = listOf(Periwinkle, Cyan, Warm, Ok)
    var sequence by remember { mutableStateOf(listOf(Random.nextInt(4))) }
    // Monotonic replay trigger: keying the replay effect on `sequence` misses a
    // reset when Random yields the same single pad (the new list is structurally
    // equal), so the glow wouldn't re-play and inputPos wouldn't reset. Bump this
    // on every sequence change instead.
    var replay by remember { mutableIntStateOf(0) }
    var lit by remember { mutableIntStateOf(-1) }
    var inputPos by remember { mutableIntStateOf(0) }
    var showing by remember { mutableStateOf(true) }
    var best by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("Watch the glow, then repeat it.") }
    val haptics = LocalHapticFeedback.current

    // Replays the sequence on first show and after every change (success or reset).
    LaunchedEffect(replay) {
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
                sequence = sequence + Random.nextInt(4); replay++
            }
        } else {
            note = "Reached round ${sequence.size}. Fresh start, no rush."
            sequence = listOf(Random.nextInt(4)); replay++
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
    // Pump frames only while a ripple is still animating (they live ~3s); when the
    // water is still the loop exits, so we don't recompose the Canvas every frame
    // forever. A new tap changes `ripples` and relaunches the effect.
    LaunchedEffect(ripples) {
        while (ripples.any { (System.nanoTime() - it.born) < 3_000_000_000L }) {
            withFrameNanos { now = it }
        }
    }

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
                .border(1.dp, VeilLine, RoundedCornerShape(22.dp))
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

// ── Gratitude garden ─────────────────────────────────────────────────────
internal val FLOWERS = listOf("🌸", "🌼", "🌷", "🌻", "💮", "🪻")

/** Deterministic flower per entry index — testable, stable across launches. */
internal fun flowerFor(index: Int): String = FLOWERS[index % FLOWERS.size]

/** Deterministic 0..1 planting fraction per entry — a stable scatter so a saved
 * flower lands in the same spot on every launch. Pure, so it's unit-testable. */
internal fun plantFraction(index: Int, salt: Int): Float =
    ((index * 73 + salt * 149 + 31) % 100) / 100f

@Composable
fun GratitudeGardenScreen(onBack: () -> Unit) {
    var entries by remember { mutableStateOf(Gratitude.all()) }
    var draft by remember { mutableStateOf("") }

    SubPage("Grow something good", "Gratitude garden", onBack) {
        Text("Name one small thing you're thankful for — a flower is planted for each.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        AppTextField(draft, { draft = it }, "One good thing…", singleLine = true)
        PrimaryButton(text = "Plant it", enabled = draft.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            entries = Gratitude.add(draft)
            draft = ""
        }
        // The soil: every real entry becomes one flower at a deterministic spot.
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(300.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.05f), Ok.copy(alpha = 0.16f)),
                    ),
                )
                .border(1.dp, VeilLine, RoundedCornerShape(22.dp))
                .semantics { contentDescription = "Gratitude garden soil with ${entries.size} flowers" },
        ) {
            if (entries.isEmpty()) {
                Text("Your garden is waiting for its first flower.",
                    style = MaterialTheme.typography.labelSmall, color = TextMuted, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp))
            }
            val flower = 34.dp
            entries.forEachIndexed { i, text ->
                val x = (maxWidth - flower) * plantFraction(i, 1)
                val y = (maxHeight - flower) * plantFraction(i, 2)
                Box(
                    Modifier.offset(x = x, y = y).size(flower)
                        .appear(i)
                        .clip(CircleShape)
                        .background(Periwinkle.copy(alpha = 0.22f))
                        .semantics { contentDescription = "Flower: $text" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(flowerFor(i), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Text(
            if (entries.isEmpty()) "Plant one small good thing."
            else "${entries.size} ${if (entries.size == 1) "flower" else "flowers"} and counting.",
            style = MaterialTheme.typography.labelSmall, color = TextMuted,
        )
        WhyThisWorks(
            "Noting what you're grateful for is a studied positive-psychology practice " +
                "linked to improved mood over time.",
        )
    }
}
