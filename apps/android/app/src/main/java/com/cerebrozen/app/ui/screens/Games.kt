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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
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
    // Templates for the tap() closure below (not a composable context).
    val noteStart = stringResource(R.string.patternglow_note_start)
    val noteSuccess = stringResource(R.string.patternglow_note_success)
    val noteReset = stringResource(R.string.patternglow_note_reset)
    var note by remember { mutableStateOf(noteStart) }
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
                note = noteSuccess.format(sequence.size)
                sequence = sequence + Random.nextInt(4); replay++
            }
        } else {
            note = noteReset.format(sequence.size)
            sequence = listOf(Random.nextInt(4)); replay++
        }
    }

    SubPage(stringResource(R.string.patternglow_eyebrow), stringResource(R.string.patternglow_title), onBack) {
        Text(note, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(0 to 1, 2 to 3).forEach { (l, r) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(l, r).forEach { pad ->
                        val active = lit == pad
                        val padCd = stringResource(R.string.patternglow_pad_cd, pad + 1)
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
                                .semantics { role = Role.Button; contentDescription = padCd },
                        )
                    }
                }
            }
        }
        Text(
            (if (showing) stringResource(R.string.patternglow_watching) else stringResource(R.string.patternglow_your_turn)) +
                if (best > 0) stringResource(R.string.patternglow_best_suffix, best) else "",
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

    SubPage(stringResource(R.string.zen_eyebrow), stringResource(R.string.zen_title), onBack) {
        Text(stringResource(R.string.zen_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        val canvasCd = stringResource(R.string.zen_canvas_cd)
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
                .semantics { contentDescription = canvasCd },
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

    SubPage(stringResource(R.string.gratitude_eyebrow), stringResource(R.string.gratitude_title), onBack) {
        Text(stringResource(R.string.gratitude_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        AppTextField(draft, { draft = it }, stringResource(R.string.gratitude_field_label), singleLine = true)
        PrimaryButton(text = stringResource(R.string.gratitude_plant_cta), enabled = draft.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            entries = Gratitude.add(draft)
            draft = ""
        }
        // The soil: every real entry becomes one flower at a deterministic spot.
        val soilCd = stringResource(R.string.gratitude_soil_cd, entries.size)
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(300.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.05f), Ok.copy(alpha = 0.16f)),
                    ),
                )
                .border(1.dp, VeilLine, RoundedCornerShape(22.dp))
                .semantics { contentDescription = soilCd },
        ) {
            if (entries.isEmpty()) {
                Text(stringResource(R.string.gratitude_empty),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp))
            }
            val flower = 34.dp
            entries.forEachIndexed { i, text ->
                val x = (maxWidth - flower) * plantFraction(i, 1)
                val y = (maxHeight - flower) * plantFraction(i, 2)
                val flowerCd = stringResource(R.string.gratitude_flower_cd, text)
                Box(
                    Modifier.offset(x = x, y = y).size(flower)
                        .appear(i)
                        .clip(CircleShape)
                        .background(Periwinkle.copy(alpha = 0.22f))
                        .semantics { contentDescription = flowerCd },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(flowerFor(i), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Text(
            if (entries.isEmpty()) stringResource(R.string.gratitude_first)
            else pluralStringResource(R.plurals.gratitude_flower_count, entries.size, entries.size),
            style = MaterialTheme.typography.labelSmall, color = TextMuted,
        )
        WhyThisWorks(stringResource(R.string.gratitude_why))
    }
}
