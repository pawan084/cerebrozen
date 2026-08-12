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
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.audio.MediaCatalog
import com.cerebrozen.app.audio.Sfx
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft
import com.cerebrozen.app.ui.theme.Veil
import com.cerebrozen.app.ui.theme.VeilSoft
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
    // Saveable: the "Best: N" claim must not reset mid-session on a theme
    // switch (B45); the sequence itself replays fresh by design.
    var best by rememberSaveable { mutableIntStateOf(0) }
    // Templates for the tap() closure below (not a composable context).
    val noteStart = stringResource(R.string.patternglow_note_start)
    val noteSuccess = stringResource(R.string.patternglow_note_success)
    val noteReset = stringResource(R.string.patternglow_note_reset)
    var note by remember { mutableStateOf(noteStart) }

    // Replays the sequence on first show and after every change (success or reset).
    // The pad's own tone sounds with its glow, so the demonstration is heard as well
    // as seen — which is the whole point of a Simon board, and what makes the game
    // playable at all with your eyes closed.
    LaunchedEffect(replay) {
        showing = true
        delay(600)
        sequence.forEach { pad ->
            lit = pad; Sfx.playPad(pad); delay(450); lit = -1; delay(180)
        }
        showing = false
        inputPos = 0
    }

    fun tap(pad: Int) {
        if (showing) return
        // Every pad answers, right or wrong — the pads are a pentatonic set, so no
        // tap can sound like a mistake (see SfxTones). The HAPTIC is deliberately
        // not fired here: right and wrong get different pulses below, and a
        // generic one on every tap would say "registered" before "correct".
        Sfx.playPad(pad)
        if (pad == sequence[inputPos]) {
            // A right tap ticks; completing the round earns the one success
            // pulse; a wrong tap gets the softer warning — never the same
            // feedback for "yes" and "no" (the haptic vocabulary, Haptics.kt).
            Haptics.selection()
            inputPos++
            if (inputPos == sequence.size) {
                best = maxOf(best, sequence.size)
                note = noteSuccess.format(sequence.size)
                Sfx.playActivity(MediaCatalog.Keys.GAME_PATTERN_SUCCESS)
                Haptics.success()
                sequence = sequence + Random.nextInt(4); replay++
            }
        } else {
            Haptics.warning()
            note = noteReset.format(sequence.size)
            // A soft low settle, quieter than the pad that preceded it — never a
            // buzzer. Missing a pad is a reset, not a failure.
            Sfx.playActivity(MediaCatalog.Keys.GAME_PATTERN_RESET)
            sequence = listOf(Random.nextInt(4)); replay++
        }
    }

    PremiumSubPage(stringResource(R.string.patternglow_eyebrow), stringResource(R.string.patternglow_title), onBack) {
        ToolAmbienceEffect(R.raw.drone)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(if (showing) Cyan.copy(alpha = 0.08f) else Periwinkle.copy(alpha = 0.09f))
                .border(1.dp, if (showing) Cyan.copy(alpha = 0.22f) else Periwinkle.copy(alpha = 0.24f), RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(9.dp).clip(CircleShape)
                    .background(if (showing) Cyan else Periwinkle))
                Text(
                    if (showing) stringResource(R.string.patternglow_watching)
                    else stringResource(R.string.patternglow_your_turn),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            }
            Text(note, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Text(
                stringResource(R.string.patternglow_progress, inputPos, sequence.size),
                style = MaterialTheme.typography.labelSmall,
                color = if (showing) TextMuted else Periwinkle,
            )
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(CardFill, Periwinkle.copy(alpha = 0.07f))))
                .border(1.dp, Periwinkle.copy(alpha = 0.18f), RoundedCornerShape(26.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(0 to 1, 2 to 3).forEach { (l, r) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(l, r).forEach { pad ->
                        val active = lit == pad
                        val padCd = stringResource(R.string.patternglow_pad_cd, pad + 1)
                        Box(
                            Modifier.weight(1f).height(112.dp)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color.White.copy(alpha = if (active) 0.92f else 0.22f),
                                            pads[pad].copy(alpha = if (active) 0.92f else 0.20f),
                                        ),
                                    ),
                                )
                                .border(1.dp, pads[pad].copy(alpha = if (active) 0.85f else 0.45f), RoundedCornerShape(20.dp))
                                .clickable(enabled = !showing) { tap(pad) }
                                // B55: during the watch phase taps are ignored —
                                // the pads now SAY they're disabled instead of
                                // silently swallowing activation.
                                .semantics {
                                    role = Role.Button
                                    contentDescription = padCd
                                    if (showing) disabled()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (pad + 1).toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (active) TextPrimary else pads[pad],
                            )
                        }
                    }
                }
            }
        }
        // Was `patternglow_best_suffix` — a string written to be APPENDED to
        // another line ("  ·  best round N") but rendered standalone, so
        // .trim() left a dangling bullet and a double space on screen.
        if (best > 0) Text(
            stringResource(R.string.patternglow_best, best),
            style = MaterialTheme.typography.bodySmall, color = TextMuted,
        )
        // H17: the screen had no finish control at all — the round loops by
        // design, so Done is the only honest ending.
        PrimaryButton(text = stringResource(R.string.common_done), modifier = Modifier.fillMaxWidth()) {
            onBack()
        }
    }
}

// ── Zen ripples ──────────────────────────────────────────────────────────
private data class Ripple(val at: Offset, val born: Long)

/** Where a tap sits in the pool, as 0 (bottom) → 1 (top) — the brightness of the
 * drop it makes. Pure, so the mapping is unit-testable; a zero-height canvas (the
 * first frame, before layout) resolves to the middle rather than dividing by zero. */
internal fun rippleBrightness(y: Float, height: Float): Float {
    if (height <= 0f) return 0.5f
    return (1f - (y / height)).coerceIn(0f, 1f)
}

@Composable
fun ZenRipplesScreen(onBack: () -> Unit) {
    var ripples by remember { mutableStateOf(listOf<Ripple>()) }
    var now by remember { mutableLongStateOf(0L) }
    var rippleCount by rememberSaveable { mutableIntStateOf(0) }
    var waterSoundEnabled by rememberSaveable { mutableStateOf(true) }
    // Pump frames only while a ripple is still animating (they live ~3s); when the
    // water is still the loop exits, so we don't recompose the Canvas every frame
    // forever. A new tap changes `ripples` and relaunches the effect.
    // Reduce Motion: no frame pump. Each tap still paints its ring — the water
    // simply holds still instead of animating outward (static, never blank).
    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(ripples, reduceMotion) {
        if (reduceMotion) {
            now = System.nanoTime()
            return@LaunchedEffect
        }
        while (ripples.any { (System.nanoTime() - it.born) < 3_000_000_000L }) {
            withFrameNanos { now = it }
        }
    }

    PremiumSubPage(stringResource(R.string.zen_eyebrow), stringResource(R.string.zen_title), onBack) {
        ToolAmbienceEffect(R.raw.ocean)
        Text(stringResource(R.string.zen_intro),
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        SectionCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.zen_ripple_count, rippleCount), style = MaterialTheme.typography.titleMedium, color = Cyan)
                    Text(stringResource(R.string.zen_water_sound), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                AppSwitch(waterSoundEnabled, { waterSoundEnabled = it }, label = stringResource(R.string.zen_water_sound))
                if (rippleCount > 0) TextButton(onClick = { ripples = emptyList(); rippleCount = 0 }) {
                    Text(stringResource(R.string.common_reset), color = Periwinkle)
                }
            }
        }
        val canvasCd = stringResource(R.string.zen_canvas_cd)
        Box(
            Modifier.fillMaxWidth().height(420.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.radialGradient(
                        listOf(Cyan.copy(alpha = 0.24f), Periwinkle.copy(alpha = 0.10f), CardFill),
                    ),
                )
                .border(1.dp, LineStroke, RoundedCornerShape(26.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        ripples = (ripples + Ripple(offset, System.nanoTime())).takeLast(12)
                        rippleCount++
                        Haptics.soft(0.35f)
                        // One audio vocabulary: the pre-warmed Sfx engine, not a
                        // per-tap AudioTrack. Pitch tracks where you touched —
                        // higher up the pool rings brighter, so a run of taps
                        // plays as a little phrase instead of twelve identical
                        // plinks. The switch above lets a silent room stay silent.
                        if (waterSoundEnabled) Sfx.playRipple(rippleBrightness(offset.y, size.height.toFloat()))
                    }
                }
                .semantics { contentDescription = canvasCd },
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Periwinkle.copy(alpha = 0.035f), radius = size.minDimension * 0.48f, center = center)
                drawCircle(Cyan.copy(alpha = 0.065f), radius = size.minDimension * 0.32f, center = center)
                ripples.forEach { r ->
                    val age = (now - r.born) / 1_000_000_000f
                    if (age in 0f..3f) {
                        val alpha = (1f - age / 3f) * 0.5f
                        drawCircle(Cyan.copy(alpha = alpha), radius = 30f + age * 220f, center = r.at, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                        drawCircle(Periwinkle.copy(alpha = alpha * 0.6f), radius = 10f + age * 140f, center = r.at, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                    }
                }
            }
            if (ripples.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◎", style = MaterialTheme.typography.displayMedium, color = Cyan.copy(alpha = 0.72f))
                    Text(stringResource(R.string.zen_tap_hint), style = MaterialTheme.typography.titleMedium, color = TextPrimary.copy(alpha = 0.78f))
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
    // B41: per-axis slopes (73 vs 37, both coprime with 100) — one shared
    // slope meant y - x was constant, so the whole garden grew on a single
    // diagonal and later flowers hid earlier ones.
    ((index * (if (salt == 1) 73 else 37) + salt * 149 + 31) % 100) / 100f

@Composable
fun GratitudeGardenScreen(onBack: () -> Unit) {
    var entries by remember { mutableStateOf(Gratitude.all()) }
    var draft by remember { mutableStateOf("") }

    PremiumSubPage(stringResource(R.string.gratitude_eyebrow), stringResource(R.string.gratitude_title), onBack) {
        ToolAmbienceEffect(R.raw.rain)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Periwinkle.copy(alpha = 0.055f))
                .border(1.dp, Periwinkle.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        Text(stringResource(R.string.gratitude_intro),
            style = MaterialTheme.typography.bodySmall, color = TextSoft)
        AppTextField(draft, { draft = it }, label = "", singleLine = true,
            placeholderText = stringResource(R.string.gratitude_field_label))
        PrimaryButton(text = stringResource(R.string.gratitude_plant_cta), enabled = draft.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            entries = Gratitude.add(draft)
            draft = ""
            // Something opening — the sound of the flower landing.
            Sfx.playActivity(MediaCatalog.Keys.GAME_BLOOM)
        }
        }
        // The soil: every real entry becomes one flower at a deterministic spot.
        val soilCd = stringResource(R.string.gratitude_soil_cd, entries.size)
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(270.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Cyan.copy(alpha = 0.10f), Ok.copy(alpha = 0.13f), CardFill),
                    ),
                )
                .border(1.dp, Ok.copy(alpha = 0.24f), RoundedCornerShape(26.dp))
                .semantics { contentDescription = soilCd },
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(Ok.copy(alpha = 0.16f), Color.Transparent)),
                    radius = size.minDimension * 0.72f,
                    center = Offset(size.width * 0.50f, size.height * 0.90f),
                )
                drawLine(
                    color = Ok.copy(alpha = 0.14f),
                    start = Offset(0f, size.height * 0.78f),
                    end = Offset(size.width, size.height * 0.78f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            if (entries.isEmpty()) {
                Text(stringResource(R.string.gratitude_empty),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp))
            }
            val flower = 44.dp
            entries.forEachIndexed { i, text ->
                val x = (maxWidth - flower) * plantFraction(i, 1)
                val y = (maxHeight - flower) * plantFraction(i, 2)
                val flowerCd = stringResource(R.string.gratitude_flower_cd, text)
                Box(
                    Modifier.offset(x = x, y = y).size(flower)
                        .appear(i)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.88f), Periwinkle.copy(alpha = 0.24f)),
                        ))
                        .border(1.dp, Periwinkle.copy(alpha = 0.18f), CircleShape)
                        .semantics { contentDescription = flowerCd },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(flowerFor(i), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Text(
            if (entries.isEmpty()) stringResource(R.string.gratitude_first)
            else pluralStringResource(R.plurals.gratitude_flower_count, entries.size, entries.size),
            style = MaterialTheme.typography.bodySmall, color = TextMuted,
        )
        WhyThisWorks(stringResource(R.string.gratitude_why))
    }
}
