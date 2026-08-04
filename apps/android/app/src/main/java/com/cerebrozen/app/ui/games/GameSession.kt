package com.cerebrozen.app.ui.games

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cerebrozen.app.R
import com.cerebrozen.app.audio.GameSound
import com.cerebrozen.app.ui.Haptics
import com.cerebrozen.app.ui.screens.AmbienceToggle
import com.cerebrozen.app.ui.screens.Celebrations
import com.cerebrozen.app.ui.screens.PrimaryButton
import com.cerebrozen.app.ui.screens.SectionCard
import com.cerebrozen.app.ui.screens.SubPage
import com.cerebrozen.app.ui.screens.ToolAmbienceEffect
import com.cerebrozen.app.ui.screens.rememberReduceMotion
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.Warm
import com.cerebrozen.app.ui.theme.CardFill
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One session of one game.
 *
 * [GameEngine] decides what the rounds are; this decides how they look and what
 * they sound like. Three things it does that the previous version did not:
 *
 * * **Seeds each session from the clock**, so a second play is a different game
 *   rather than a memory test of the first.
 * * **Runs the clock on timed rounds.** Letting it expire is a real outcome —
 *   it counts as a miss and moves on. A reaction game you can think about for a
 *   minute is not a reaction game.
 * * **Plays the game's own voice** ([GameSound]), so Focus games sound quick and
 *   bright while Calm games sound low and long.
 */
@Composable
fun MindfulGameScreen(gameId: String?, onBack: () -> Unit, onBackToGames: () -> Unit) {
    val game = MindfulGameRegistry.find(gameId) ?: return
    val gameName = stringResource(game.nameRes)
    val reduceMotion = rememberReduceMotion()
    val accent = gameAccent(game.category)
    val scored = isScored(game.id)
    val total = if (scored) 6 else 4

    // A new seed per session, keyed on a counter rather than Unit, so "Play
    // again" genuinely reshuffles instead of replaying the same six rounds.
    var sessionCount by remember(game.id) { mutableIntStateOf(0) }
    val rounds = remember(game.id, sessionCount) {
        buildSession(game.id, System.currentTimeMillis() + sessionCount, total)
    }
    var index by remember(game.id, sessionCount) { mutableIntStateOf(0) }
    var score by remember(game.id, sessionCount) { mutableIntStateOf(0) }
    val finished = index >= total

    LaunchedEffect(finished) {
        if (finished) {
            GameSound.complete(game.id)
            Celebrations.trigger()
        }
    }
    if (game.category == GameCategory.Calm) ToolAmbienceEffect(R.raw.wind)

    SubPage(stringResource(game.practiceRes), gameName, onBack) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameHeader(game, gameName, accent)
            Text(
                stringResource(game.descriptionRes),
                style = MaterialTheme.typography.bodyMedium, color = TextMuted,
                modifier = Modifier.fillMaxWidth(),
            )

            if (finished) {
                GameResultPanel(
                    game = game,
                    score = score,
                    total = total,
                    scored = scored,
                    reduceMotion = reduceMotion,
                    onPlayAgain = { sessionCount++ },
                    onBackToGames = onBackToGames,
                )
                return@Column
            }

            // Progress. Unscored games show steps only — a running score on
            // "rest your attention on a point" grades how well someone rested.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.mg_round, index + 1, total), color = TextMuted)
                if (scored) Text(stringResource(R.string.mg_score, score), color = TextMuted)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(total) { step ->
                    Box(
                        Modifier.weight(1f).height(7.dp).clip(CircleShape)
                            .background(
                                when {
                                    step < index -> Ok   // B62: token, was raw green
                                    step == index -> accent
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                                },
                            ),
                    )
                }
            }

            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    if (reduceMotion) {
                        androidx.compose.animation.EnterTransition.None togetherWith
                            androidx.compose.animation.ExitTransition.None
                    } else {
                        (fadeIn(tween(260)) togetherWith fadeOut(tween(160))).using(SizeTransform(clip = false))
                    }
                },
                label = "game-round",
            ) { activeIndex ->
                if (activeIndex >= rounds.size) return@AnimatedContent
                val round = rounds[activeIndex]
                var answered by remember(activeIndex, sessionCount) { mutableStateOf<Boolean?>(null) }
                val scope = rememberCoroutineScope()

                val settle: (Boolean) -> Unit = { correct ->
                    if (answered == null) {
                        answered = correct
                        // B69: Haptics.kt's vocabulary — selection says "yes"
                        // per round, warning says "no"; success() fires ONCE at
                        // session completion (Celebrations.trigger), matching
                        // PatternGlow. success-per-round made every game shout.
                        if (correct) { GameSound.correct(game.id); Haptics.selection() }
                        else { GameSound.wrong(game.id); Haptics.warning() }
                        scope.launch {
                            delay(if (reduceMotion) 220 else 420)
                            if (correct) score++
                            index++
                        }
                    }
                }

                Box(
                    Modifier.fillMaxWidth().height(410.dp)
                        .shadow(22.dp, RoundedCornerShape(30.dp), ambientColor = accent.copy(alpha = 0.22f), spotColor = accent.copy(alpha = 0.28f))
                        .clip(RoundedCornerShape(30.dp))
                        .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), CardFill, accent.copy(alpha = 0.07f))))
                        .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(30.dp))
                        .padding(22.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // The clock, where the game has one. Running out is a
                        // miss, not a pause — otherwise the timer is decoration.
                        round.timeLimitMs?.let { limit ->
                            RoundTimer(
                                key = activeIndex to sessionCount,
                                limitMs = limit,
                                accent = accent,
                                paused = answered != null,
                                onExpire = { settle(false) },
                            )
                        }
                        RoundBody(game, round, accent, reduceMotion, settle)
                    }
                    GameTapFeedback(accent, answered)
                }
            }
        }
    }
}

@Composable
private fun GameHeader(game: MindfulGame, gameName: String, accent: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(52.dp)
                    .shadow(16.dp, CircleShape, ambientColor = accent.copy(alpha = 0.45f), spotColor = accent.copy(alpha = 0.45f))
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.14f))))
                    .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    game.glyph, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { contentDescription = gameName },
                )
            }
            Column {
                Text(stringResource(game.category.titleRes), style = MaterialTheme.typography.labelMedium, color = accent)
                Text(stringResource(game.practiceRes), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
        if (game.category == GameCategory.Calm) AmbienceToggle()
    }
}

/**
 * The round clock.
 *
 * Drains left to right and turns amber in its last third — peripheral warning,
 * so the player never has to look away from the thing they are tracking.
 */
@Composable
private fun RoundTimer(
    key: Any,
    limitMs: Long,
    accent: Color,
    paused: Boolean,
    onExpire: () -> Unit,
) {
    var remaining by remember(key) { mutableStateOf(1f) }
    LaunchedEffect(key, paused) {
        if (paused) return@LaunchedEffect
        val step = 40L
        var elapsed = ((1f - remaining) * limitMs).toLong()
        while (elapsed < limitMs) {
            delay(step)
            elapsed += step
            remaining = (1f - elapsed / limitMs.toFloat()).coerceIn(0f, 1f)
        }
        onExpire()
    }
    val warning = remaining < 0.34f
    // B58: color was the ONLY expiry cue. The bar now carries range semantics
    // (like the mixer sliders) and ticks a soft haptic when time runs short.
    LaunchedEffect(warning) { if (warning) Haptics.soft() }
    val timerCd = stringResource(R.string.mg_timer_cd)
    Box(
        Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
            .semantics {
                contentDescription = timerCd
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(remaining, 0f..1f)
            },
    ) {
        Box(
            Modifier.fillMaxWidth(remaining).height(5.dp).clip(CircleShape)
                .background(if (warning) Warm else accent),   // B62: warning wears the warm token
        )
    }
}

// ── Round rendering, one branch per mechanic ─────────────────────────────

@Composable
private fun RoundBody(
    game: MindfulGame,
    round: Round,
    accent: Color,
    reduceMotion: Boolean,
    onAnswer: (Boolean) -> Unit,
) {
    when (game.mechanic) {
        GameMechanic.ColorTap -> ColorTapBody(round, onAnswer)
        GameMechanic.Stroop -> StroopBody(round, onAnswer)
        GameMechanic.GoNoGo -> GoNoGoBody(round, onAnswer)
        GameMechanic.SequenceRecall, GameMechanic.PathRecall ->
            SequenceBody(game.id, round, accent, reduceMotion, onAnswer)
        GameMechanic.ChangeSpotting -> ChangeSpottingBody(game.id, round, accent, reduceMotion, onAnswer)
        GameMechanic.RuleSwitch -> RuleSwitchBody(round, accent, onAnswer)
        GameMechanic.BilateralTap -> BilateralBody(round, accent, onAnswer)
        GameMechanic.ThoughtSort -> ThoughtSortBody(round, onAnswer)
        GameMechanic.BreathPace -> BreathPaceBody(game.id, accent, reduceMotion, onAnswer)
        GameMechanic.SandDraw -> SandDrawBody(game.id, accent, onAnswer)
        GameMechanic.StillPoint -> StillPointBody(game.id, accent, reduceMotion, onAnswer)
    }
}

/** The shared colour vocabulary, resolved once. */
private fun colorFor(key: String): Color = when (key) {
    "green" -> Color(0xFF34D399)
    "cyan" -> Color(0xFF22D3EE)
    "purple" -> Color(0xFFA78BFA)
    "amber" -> Color(0xFFF59E0B)
    else -> Color(0xFFFB7185)   // rose
}

@Composable
private fun colorName(key: String): String = stringResource(
    when (key) {
        "green" -> R.string.mg_colour_green
        "cyan" -> R.string.mg_colour_cyan
        "purple" -> R.string.mg_colour_purple
        "amber" -> R.string.mg_colour_amber
        else -> R.string.mg_colour_rose
    },
)

@Composable
private fun ColorTapBody(round: Round, onAnswer: (Boolean) -> Unit) {
    val prompt = round.prompt as? RoundPrompt.Instruction ?: return
    Text(stringResource(R.string.mg_colour_prompt, colorName(prompt.argKey)), color = TextMuted)
    // Wraps rather than overflowing: the field grows to five as the session goes on.
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        round.options.forEachIndexed { i, option ->
            val label = colorName(option.colorKey)
            Box(
                Modifier.size(78.dp).clip(CircleShape)
                    .background(colorFor(option.colorKey))
                    .clickable { Haptics.tap(); onAnswer(i == round.correct) }
                    .semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun StroopBody(round: Round, onAnswer: (Boolean) -> Unit) {
    val prompt = round.prompt as? RoundPrompt.Conflict ?: return
    Text(stringResource(R.string.mg_ink_prompt), color = TextMuted)
    // The word is a colour name; the ink is a different colour. The ink wins.
    Text(
        colorName(prompt.wordKey).uppercase(),
        style = MaterialTheme.typography.displayMedium,
        color = colorFor(prompt.inkKey),
        fontWeight = FontWeight.Black,
    )
    round.options.forEachIndexed { i, option ->
        OutlinedButton(
            onClick = { Haptics.selection(); onAnswer(i == round.correct) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(colorName(option.colorKey), color = colorFor(option.colorKey)) }
    }
}

@Composable
private fun GoNoGoBody(round: Round, onAnswer: (Boolean) -> Unit) {
    val prompt = round.prompt as? RoundPrompt.Signal ?: return
    val cue = if (prompt.go) Color(0xFF34D399) else Color(0xFFF87171)
    Box(
        Modifier.size(168.dp).clip(CircleShape).background(cue.copy(alpha = 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(if (prompt.go) R.string.mg_go else R.string.mg_freeze),
            color = cue, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black,
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        round.options.forEachIndexed { i, option ->
            OutlinedButton(
                onClick = { Haptics.tap(); onAnswer(i == round.correct) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(if (option.key == "mg_tap") R.string.mg_tap else R.string.mg_wait))
            }
        }
    }
}

/**
 * Watch, then repeat.
 *
 * The cells light in order with the game's tick under each, then the grid goes
 * quiet and the player retraces. Under Reduce Motion the sequence still plays —
 * it is the content, not decoration — but the lights step rather than pulse.
 */
@Composable
private fun SequenceBody(
    gameId: String,
    round: Round,
    accent: Color,
    reduceMotion: Boolean,
    onAnswer: (Boolean) -> Unit,
) {
    val prompt = round.prompt as? RoundPrompt.Sequence ?: return
    var showing by remember(prompt) { mutableIntStateOf(-1) }
    var playing by remember(prompt) { mutableStateOf(true) }
    // How far into the sequence the player has correctly retraced. The round
    // used to settle on ONE tap of the first cell — the engine grows span 3→6
    // for "working memory", and a six-item sequence was scored by remembering
    // item one (audit B7). Now every cell must land, in order.
    var progress by remember(prompt) { mutableIntStateOf(0) }

    LaunchedEffect(prompt) {
        delay(350)
        prompt.cells.forEachIndexed { i, _ ->
            showing = i
            GameSound.tick(gameId)
            delay(if (reduceMotion) 420 else 560)
            showing = -1
            delay(140)
        }
        playing = false
    }

    Text(
        stringResource(if (playing) R.string.mg_watch_sequence else R.string.mg_repeat_sequence),
        color = TextMuted,
    )
    if (!playing && prompt.cells.size > 1) {
        Text(
            stringResource(R.string.mg_sequence_progress, progress, prompt.cells.size),
            color = TextMuted,
        )
    }
    Grid(
        highlighted = when {
            playing && showing >= 0 -> setOf(prompt.cells[showing])
            // The retraced prefix stays lit — the player sees their own steps.
            !playing && progress > 0 -> prompt.cells.take(progress).toSet()
            else -> emptySet()
        },
        accent = accent,
        enabled = !playing,
    ) { cell ->
        if (cell == prompt.cells[progress]) {
            progress += 1
            if (progress == prompt.cells.size) onAnswer(true)
        } else {
            onAnswer(false)
        }
    }
}

/** A tray of items, one of which was not there a moment ago. */
@Composable
private fun ChangeSpottingBody(
    gameId: String,
    round: Round,
    accent: Color,
    reduceMotion: Boolean,
    onAnswer: (Boolean) -> Unit,
) {
    val prompt = round.prompt as? RoundPrompt.Sequence ?: return
    var studying by remember(prompt) { mutableStateOf(true) }
    LaunchedEffect(prompt) {
        delay(if (reduceMotion) 1400 else 1900)
        GameSound.tick(gameId)
        studying = false
    }
    Text(
        stringResource(if (studying) R.string.mg_study_tray else R.string.mg_find_new),
        color = TextMuted,
    )
    val shown = if (studying) prompt.cells.toSet() else round.options.map { it.cell }.toSet()
    Grid(highlighted = shown, accent = accent, enabled = !studying, filledOnly = true) { cell ->
        onAnswer(round.options.getOrNull(round.correct)?.cell == cell)
    }
}

/** The 3×3 board both memory games share. */
@Composable
private fun Grid(
    highlighted: Set<Int>,
    accent: Color,
    enabled: Boolean,
    filledOnly: Boolean = false,
    onTap: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (0 until 9).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { cell ->
                    val lit = cell in highlighted
                    val visible = !filledOnly || lit
                    val scale by animateFloatAsState(if (lit) 1.06f else 1f, tween(180), label = "cell")
                    // B51: anonymous clickable Boxes made the memory games
                    // unusable with TalkBack — every cell now has a name,
                    // position and role (contrast PatternGlow's labelled pads).
                    val cellCd = stringResource(R.string.mg_cell_cd, cell + 1)
                    Box(
                        Modifier.size(64.dp).scale(scale).clip(RoundedCornerShape(18.dp))
                            .background(
                                when {
                                    lit -> accent.copy(alpha = 0.85f)
                                    visible -> accent.copy(alpha = 0.14f)
                                    else -> Color.Transparent
                                },
                            )
                            .then(
                                if (enabled && visible) {
                                    Modifier.clickable { Haptics.soft(); onTap(cell) }
                                        .semantics {
                                            role = androidx.compose.ui.semantics.Role.Button
                                            contentDescription = cellCd
                                        }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleSwitchBody(round: Round, accent: Color, onAnswer: (Boolean) -> Unit) {
    val prompt = round.prompt as? RoundPrompt.Instruction ?: return
    val rule = stringResource(
        if (prompt.argKey == "mg_rule_large") R.string.mg_rule_large else R.string.mg_rule_small,
    )
    Text(stringResource(R.string.mg_rule_prompt, rule), color = TextMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        round.options.forEachIndexed { i, option ->
            OutlinedButton(
                onClick = { Haptics.selection(); onAnswer(i == round.correct) },
                modifier = Modifier.size(112.dp),
            ) { Text(option.key, style = MaterialTheme.typography.headlineMedium, color = accent) }
        }
    }
}

@Composable
private fun BilateralBody(round: Round, accent: Color, onAnswer: (Boolean) -> Unit) {
    val prompt = round.prompt as? RoundPrompt.Instruction ?: return
    Text(stringResource(R.string.mg_follow_side), color = TextMuted)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        round.options.forEachIndexed { i, option ->
            val active = option.key == prompt.argKey
            val label = stringResource(if (option.key == "mg_left") R.string.mg_left else R.string.mg_right)
            Box(
                Modifier.weight(1f).height(150.dp).clip(RoundedCornerShape(28.dp))
                    .background(if (active) accent.copy(alpha = 0.42f) else accent.copy(alpha = 0.10f))
                    .clickable { Haptics.soft(); onAnswer(i == round.correct) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) { Text(label, color = TextPrimary, style = MaterialTheme.typography.titleLarge) }
        }
    }
}

/**
 * Sort a written thought.
 *
 * The thoughts are pre-written and generic on purpose. Asking someone to sort
 * their *own* thoughts into right and wrong would be an assessment, and this
 * app does not assess.
 */
@Composable
private fun ThoughtSortBody(round: Round, onAnswer: (Boolean) -> Unit) {
    val prompt = round.prompt as? RoundPrompt.Thought ?: return
    Text(stringResource(R.string.mg_sort_prompt), color = TextMuted)
    SectionCard(quiet = true) {
        Text(
            stringResource(thoughtRes(prompt.textKey)),
            style = MaterialTheme.typography.titleMedium, color = TextPrimary,
        )
    }
    round.options.forEachIndexed { i, option ->
        OutlinedButton(
            onClick = { Haptics.selection(); onAnswer(i == round.correct) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (option.key == "mg_sort_unhelpful") R.string.mg_sort_unhelpful
                    else R.string.mg_sort_workable,
                ),
            )
        }
    }
}

private fun thoughtRes(key: String): Int = when (key) {
    "thought_all_or_nothing" -> R.string.mg_thought_all_or_nothing
    "thought_one_step" -> R.string.mg_thought_one_step
    "thought_mind_reading" -> R.string.mg_thought_mind_reading
    "thought_evidence" -> R.string.mg_thought_evidence
    "thought_catastrophe" -> R.string.mg_thought_catastrophe
    "thought_ask_for_help" -> R.string.mg_thought_ask_for_help
    "thought_should" -> R.string.mg_thought_should
    else -> R.string.mg_thought_good_enough
}

// ── Calm: unscored, self-paced ───────────────────────────────────────────

/** Paced breathing. The ring is the instruction; there is nothing to get right. */
@Composable
private fun BreathPaceBody(
    gameId: String,
    accent: Color,
    reduceMotion: Boolean,
    onAnswer: (Boolean) -> Unit,
) {
    var inhaling by remember { mutableStateOf(true) }
    val size by animateFloatAsState(
        if (inhaling) 1f else 0.62f,
        tween(if (reduceMotion) 0 else 4000, easing = FastOutSlowInEasing),
        label = "breath",
    )
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            inhaling = !inhaling
            GameSound.tick(gameId)
        }
    }
    Text(
        stringResource(if (inhaling) R.string.mg_breathe_in else R.string.mg_breathe_out),
        style = MaterialTheme.typography.titleLarge, color = TextPrimary,
    )
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size((190 * size).dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.08f)))),
        )
    }
    // Advancing is the user's call, not a timer's: a calm exercise that hurries
    // you along is not calm.
    PrimaryButton(stringResource(R.string.mg_done_step), modifier = Modifier.fillMaxWidth()) { onAnswer(true) }
}

/** Sensory drawing: tap to lay grains, no target, no score. */
@Composable
private fun SandDrawBody(gameId: String, accent: Color, onAnswer: (Boolean) -> Unit) {
    var grains by remember { mutableStateOf(listOf<Int>()) }
    Text(stringResource(R.string.mg_draw_sand), color = TextMuted)
    Grid(highlighted = grains.toSet(), accent = accent, enabled = true) { cell ->
        GameSound.tick(gameId)
        grains = if (cell in grains) grains - cell else grains + cell
    }
    PrimaryButton(stringResource(R.string.mg_done_step), modifier = Modifier.fillMaxWidth()) { onAnswer(true) }
}

/** Hold attention on one point. The point breathes very slowly; nothing is scored. */
@Composable
private fun StillPointBody(
    gameId: String,
    accent: Color,
    reduceMotion: Boolean,
    onAnswer: (Boolean) -> Unit,
) {
    var wide by remember { mutableStateOf(false) }
    val pulse by animateFloatAsState(
        if (wide) 1f else 0.8f,
        tween(if (reduceMotion) 0 else 5200, easing = FastOutSlowInEasing),
        label = "still",
    )
    LaunchedEffect(Unit) {
        while (true) {
            delay(5200)
            wide = !wide
            GameSound.tick(gameId)
        }
    }
    Text(stringResource(R.string.mg_still_prompt), color = TextMuted)
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size((180 * pulse).dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.40f), Color.Transparent))),
        )
        Box(Modifier.size(16.dp).clip(CircleShape).background(accent))
    }
    PrimaryButton(stringResource(R.string.mg_done_step), modifier = Modifier.fillMaxWidth()) { onAnswer(true) }
}

// ── Feedback + result ────────────────────────────────────────────────────

@Composable
private fun GameTapFeedback(accent: Color, result: Boolean?) {
    AnimatedVisibility(
        visible = result != null,
        enter = fadeIn(tween(90)) + scaleIn(initialScale = 0.72f, animationSpec = spring(dampingRatio = 0.68f)),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 1.08f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = accent.copy(alpha = 0.35f), spotColor = accent.copy(alpha = 0.35f))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.32f), Color(0xF20A1020))))
                    .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 28.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    if (result == false) "↺" else "✓",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (result == false) Warm else accent,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    stringResource(
                        if (result == false) R.string.mg_feedback_continue else R.string.mg_feedback_good,
                    ),
                    color = Color.White, style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * The closing panel.
 *
 * Scored games get a band, not a grade — see [resultBand]. Unscored ones get no
 * number at all, because counting calm defeats it.
 */
@Composable
private fun GameResultPanel(
    game: MindfulGame,
    score: Int,
    total: Int,
    scored: Boolean,
    reduceMotion: Boolean,
    onPlayAgain: () -> Unit,
    onBackToGames: () -> Unit,
) {
    var entered by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) { entered = true }
    val scale by animateFloatAsState(if (entered) 1f else 0.82f, tween(520, easing = FastOutSlowInEasing), label = "result-scale")
    val alpha by animateFloatAsState(if (entered) 1f else 0f, tween(420), label = "result-alpha")

    Column(
        Modifier.fillMaxWidth().scale(if (reduceMotion) 1f else scale).alpha(if (reduceMotion) 1f else alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(132.dp).clip(CircleShape).background(Ok.copy(alpha = 0.14f)))
            Box(Modifier.size(94.dp).clip(CircleShape).background(Ok), contentAlignment = Alignment.Center) {
                Text("✓", style = MaterialTheme.typography.displayMedium, color = Color(0xFF08251B), fontWeight = FontWeight.Black)
            }
        }
        Text(stringResource(R.string.mg_result_eyebrow).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.mg_result_title), style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        SectionCard {
            if (scored) {
                Text(
                    stringResource(
                        when (resultBand(score, total)) {
                            "sharp" -> R.string.mg_band_sharp
                            "steady" -> R.string.mg_band_steady
                            else -> R.string.mg_band_gentle
                        },
                    ),
                    style = MaterialTheme.typography.headlineSmall, color = TextPrimary,
                )
                Text(stringResource(R.string.mg_result_score, score, total), color = TextMuted)
            } else {
                Text(stringResource(R.string.mg_band_rested), style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            }
            Text(stringResource(R.string.mg_result_message), color = TextMuted)
        }
        PrimaryButton(stringResource(R.string.mg_play_again), modifier = Modifier.fillMaxWidth(), onClick = onPlayAgain)
        TextButton(onClick = onBackToGames) { Text(stringResource(R.string.mg_back_games)) }
    }
}
