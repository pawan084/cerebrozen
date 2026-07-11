package com.cerebrozen.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Iris
import com.cerebrozen.app.ui.theme.LineStroke
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
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
        LaunchedEffect(matched.size) {
            if (deck.isNotEmpty() && matched.size == deck.size) Celebrations.trigger()
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
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
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
    }
}

// ── Color breathing ──────────────────────────────────────────────────────
/** The four-beat cycle, 4s each. Colour + orb scale are both driven BY the
 * current phase index (not a free-running pulse), mirroring iOS ColorBreathing. */
internal val BREATH_PHASES = listOf("Breathe in", "Hold", "Breathe out", "Hold")

@Composable
fun ColorBreathingScreen(onBack: () -> Unit) {
    var phase by remember { mutableIntStateOf(0) }
    var breaths by remember { mutableIntStateOf(0) }
    val reduceMotion = rememberReduceMotion()

    // Functional pacer: advance the phase every 4s and fire a gentle phase haptic.
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            phase = (phase + 1) % 4
            if (phase == 0) breaths++            // a full cycle just completed
            Haptics.soft(if (phase == 0 || phase == 2) 0.5f else 0.3f)
        }
    }

    // Scale is a function of phase: expand on inhale, hold full, contract on
    // exhale, hold empty. Motion (not the pacing) honours Reduce Motion.
    val scale by animateFloatAsState(
        targetValue = if (phase == 0 || phase == 1) 1f else 0.6f,
        animationSpec = if (reduceMotion) snap() else tween(3800, easing = FastOutSlowInEasing),
        label = "breathScale",
    )
    val tint by animateColorAsState(
        targetValue = when (phase) { 0 -> Cyan; 1 -> Periwinkle; 2 -> Iris; else -> Warm },
        animationSpec = if (reduceMotion) snap() else tween(1400, easing = FastOutSlowInEasing),
        label = "breathTint",
    )

    SubPage("Follow the glow", "Color breathing", onBack) {
        Text("Let your breath follow the orb — in, hold, out, hold.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Box(
            Modifier.fillMaxWidth().height(360.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(220.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(tint.copy(alpha = 0.90f), tint.copy(alpha = 0.20f)),
                        ),
                    )
                    .border(1.dp, tint.copy(alpha = 0.55f), CircleShape)
                    .semantics { contentDescription = "Breathing orb — ${BREATH_PHASES[phase]}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(BREATH_PHASES[phase], style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
        }
        Text("$breaths calm ${if (breaths == 1) "breath" else "breaths"}",
            style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

// ── Sliding puzzle ───────────────────────────────────────────────────────
/** Solved board: 1..8 with the blank (0) last. */
internal val SOLVED_PUZZLE = listOf(1, 2, 3, 4, 5, 6, 7, 8, 0)

/** The 3×3 grid neighbours of a cell — the tiles that could slide into a blank. */
internal fun puzzleNeighbors(index: Int): List<Int> {
    val row = index / 3; val col = index % 3
    return buildList {
        if (row > 0) add(index - 3)
        if (row < 2) add(index + 3)
        if (col > 0) add(index - 1)
        if (col < 2) add(index + 1)
    }
}

/** A solvable board: a run of random valid moves back from solved (never the
 * solved state itself). Pure + seedable, so the reachability is testable. */
internal fun shufflePuzzle(steps: Int = 60, random: Random = Random.Default): List<Int> {
    var board = SOLVED_PUZZLE
    var blank = board.indexOf(0)
    repeat(steps) {
        val n = puzzleNeighbors(blank).random(random)
        board = board.toMutableList().also { it[blank] = it[n]; it[n] = 0 }
        blank = n
    }
    if (board == SOLVED_PUZZLE) {           // guard against the rare no-op shuffle
        val n = puzzleNeighbors(blank).first()
        board = board.toMutableList().also { it[blank] = it[n]; it[n] = 0 }
    }
    return board
}

@Composable
fun SlidingPuzzleScreen(onBack: () -> Unit) {
    var board by remember { mutableStateOf(shufflePuzzle()) }
    val solved = board == SOLVED_PUZZLE

    fun tap(i: Int) {
        if (solved) return
        val blank = board.indexOf(0)
        if (i in puzzleNeighbors(blank)) {
            Haptics.soft(0.4f)
            board = board.toMutableList().also { it[blank] = it[i]; it[i] = 0 }
        }
    }

    LaunchedEffect(solved) { if (solved) Celebrations.trigger() }

    SubPage("Order from the shuffle", "Sliding puzzle", onBack) {
        Text("Slide the tiles until 1 through 8 line up. No timer, no score.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            (0 until 3).forEach { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (0 until 3).forEach { c ->
                        val i = r * 3 + c
                        val v = board[i]
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (v == 0) Color.White.copy(alpha = 0.04f) else Periwinkle.copy(alpha = 0.30f))
                                .border(1.dp, if (v == 0) LineStroke else Periwinkle.copy(alpha = 0.70f), RoundedCornerShape(16.dp))
                                .clickable(enabled = v != 0) { tap(i) }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = if (v == 0) "Blank space" else "Tile $v"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (v != 0) {
                                Text("$v", style = MaterialTheme.typography.headlineSmall, color = TextSoft)
                            }
                        }
                    }
                }
            }
        }
        if (solved) {
            Text("Solved! Every tile home.", style = MaterialTheme.typography.titleMedium, color = Ok)
            PrimaryButton(text = "Play again", modifier = Modifier.fillMaxWidth()) { board = shufflePuzzle() }
        } else {
            PrimaryButton(text = "Shuffle", modifier = Modifier.fillMaxWidth()) { board = shufflePuzzle() }
        }
    }
}
