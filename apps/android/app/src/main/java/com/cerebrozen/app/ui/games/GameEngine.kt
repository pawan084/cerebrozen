package com.cerebrozen.app.ui.games

import kotlin.random.Random

/**
 * What the games were missing, and what this file is.
 *
 * The first version had 23 titles over 7 shared round-builders, and every one of
 * them derived its answer from `round % n`. That has three consequences worth
 * naming, because they are the reasons this rewrite exists:
 *
 * 1. **Nothing was random.** Round 3 of Color Tap was the same round 3 every
 *    time, for every user, forever. The second play was memorisation, not
 *    attention.
 * 2. **Nothing got harder.** Five rounds, identical difficulty, then a
 *    celebration. There was no reason to reach.
 * 3. **Twelve of the titles were the same game.** "Shape Shift" and "Rule
 *    Switch" were one function with a boolean; "Cloud Drift", "Zen Sand" and
 *    "Who Are You" were one screen with different words.
 *
 * So: fewer games, each with a mechanic of its own, a seeded generator so plays
 * differ, and a difficulty curve inside the session. All of it lives here as
 * pure functions — no Compose, no Android — because this is the part with
 * behaviour worth testing, and the screen is only how it looks.
 *
 * On scoring: the calm games are deliberately unscored. Turning "rest your
 * attention on a point" into a score is the gamification the redesign's
 * evidence review said to avoid (F5/F6) — a pressure loop stresses the users
 * who are least well.
 */

/** How hard a round is, derived from how far into the session it is. */
data class Level(val index: Int, val total: Int) {
    /** 0f at the first round, 1f at the last. */
    val progress: Float get() = if (total <= 1) 1f else index / (total - 1f)

    /** Milliseconds allowed before a timed round expires. Generous at the start
     * (2.4s), tight by the end (1.1s) — a curve, not a cliff. */
    val timeLimitMs: Long get() = (2400 - 1300 * progress).toLong()

    /** How many distractors a search/scan round shows. */
    val distractors: Int get() = 3 + (progress * 6).toInt()

    /** How long a sequence to remember. Span grows 3 → 6, the range where
     * working memory is worked without becoming a party trick. */
    val span: Int get() = 3 + (progress * 3).toInt()
}

/** One playable round. `answers` are what the screen renders; `correct` is the
 * index the player must pick. */
data class Round(
    val prompt: RoundPrompt,
    val options: List<RoundOption>,
    val correct: Int,
    /** Null for untimed rounds — the calm games and the memory recall step. */
    val timeLimitMs: Long?,
)

/** What the player is being asked, in a form the screen can render without
 * knowing which game it is. */
sealed interface RoundPrompt {
    /** "Tap the green one" — a coloured or worded instruction. */
    data class Instruction(val textKey: String, val argKey: String = "") : RoundPrompt
    /** Stroop: a colour word painted in a different colour. */
    data class Conflict(val wordKey: String, val inkKey: String) : RoundPrompt
    /** A sequence to watch, then reproduce. */
    data class Sequence(val cells: List<Int>) : RoundPrompt
    /** Go/no-go: act, or deliberately do not. */
    data class Signal(val go: Boolean) : RoundPrompt
    /** A thought to sort as helpful or unhelpful (the CBT-adjacent one). */
    data class Thought(val textKey: String, val helpful: Boolean) : RoundPrompt
}

data class RoundOption(val key: String, val colorKey: String = "", val cell: Int = -1)

/** The colour vocabulary the games share. Keys, not Colors — this file stays
 * free of Compose so it can be tested on the JVM. */
val COLOR_KEYS = listOf("green", "cyan", "purple", "amber", "rose")

/** Thought prompts for the one Resilience game kept. Deliberately mild and
 * generic: a sorting exercise, never an assessment of the user's own thoughts. */
val THOUGHT_KEYS = listOf(
    "thought_all_or_nothing" to false,
    "thought_one_step" to true,
    "thought_mind_reading" to false,
    "thought_evidence" to true,
    "thought_catastrophe" to false,
    "thought_ask_for_help" to true,
    "thought_should" to false,
    "thought_good_enough" to true,
)

/**
 * Build the round list for one session.
 *
 * Seeded so a test can assert the shape of a session and a player gets a
 * different one each time. [seed] comes from the clock at session start.
 */
fun buildSession(gameId: String, seed: Long, rounds: Int = 6): List<Round> {
    val random = Random(seed)
    return (0 until rounds).map { index ->
        buildRound(gameId, Level(index, rounds), random)
    }
}

fun buildRound(gameId: String, level: Level, random: Random): Round = when (gameId) {
    "color-tap" -> colorTap(level, random)
    "stroop-flow" -> stroop(level, random)
    "freeze-switch" -> freezeSwitch(level, random)
    "pattern-recall" -> patternRecall(level, random)
    "object-tray" -> objectTray(level, random)
    "path-memory" -> pathMemory(level, random)
    "rule-switch" -> ruleSwitch(level, random)
    "mirror-tap" -> mirrorTap(level, random)
    "thought-sort" -> thoughtSort(level, random)
    else -> calmStep(level)
}

// ── Focus ────────────────────────────────────────────────────────────────

/** Selective attention: pick the named colour from a growing field of others.
 * The naming is the load; the field size is the difficulty. */
private fun colorTap(level: Level, random: Random): Round {
    val palette = COLOR_KEYS.shuffled(random).take(minOf(3 + level.index / 2, COLOR_KEYS.size))
    val target = palette.random(random)
    val options = palette.map { RoundOption(key = it, colorKey = it) }.shuffled(random)
    return Round(
        prompt = RoundPrompt.Instruction("mg_colour_prompt", target),
        options = options,
        correct = options.indexOfFirst { it.key == target },
        timeLimitMs = level.timeLimitMs,
    )
}

/**
 * Inhibitory control: the word says one colour, the ink is another, and the ink
 * wins. Guaranteed conflicting — a round where word and ink agree is not a
 * Stroop round, and the old version could produce one.
 */
private fun stroop(level: Level, random: Random): Round {
    val ink = COLOR_KEYS.random(random)
    val word = (COLOR_KEYS - ink).random(random)
    val distractors = (COLOR_KEYS - ink - word).shuffled(random).take(1)
    val options = (listOf(ink, word) + distractors).shuffled(random).map { RoundOption(it, it) }
    return Round(
        prompt = RoundPrompt.Conflict(wordKey = word, inkKey = ink),
        options = options,
        correct = options.indexOfFirst { it.key == ink },
        timeLimitMs = level.timeLimitMs,
    )
}

/**
 * Go/no-go. Roughly a third are no-go, and which ones is random — the point of
 * the exercise is that the impulse to tap has to be checked, which cannot
 * happen if the pattern is learnable.
 */
private fun freezeSwitch(level: Level, random: Random): Round {
    val go = random.nextFloat() > 0.34f
    val options = listOf(RoundOption("mg_tap"), RoundOption("mg_wait"))
    return Round(
        prompt = RoundPrompt.Signal(go),
        options = options,
        correct = if (go) 0 else 1,
        // Tighter than the others: a go/no-go with all day to think is a quiz.
        timeLimitMs = (level.timeLimitMs * 0.75).toLong(),
    )
}

// ── Memory ───────────────────────────────────────────────────────────────

private const val GRID = 9

/** Working memory: watch a sequence of cells light, then repeat it. Span grows. */
private fun patternRecall(level: Level, random: Random): Round {
    val cells = (0 until level.span).map { random.nextInt(GRID) }
    return Round(
        prompt = RoundPrompt.Sequence(cells),
        options = (0 until GRID).map { RoundOption(key = "cell", cell = it) },
        correct = cells.first(),
        timeLimitMs = null,   // recall is not a race
    )
}

/** Visual memory: a tray of items, one of which was not there a moment ago. */
private fun objectTray(level: Level, random: Random): Round {
    val count = minOf(4 + level.index, GRID)
    val cells = (0 until GRID).shuffled(random).take(count)
    val added = cells.last()
    // Shuffled ONCE: computing the display order and the answer index from two
    // separate shuffles would point the answer at whatever happened to land
    // there — the round would mark a correct tap wrong.
    val shown = cells.shuffled(random)
    return Round(
        prompt = RoundPrompt.Sequence(cells.dropLast(1)),
        options = shown.map { RoundOption(key = "cell", cell = it) },
        correct = shown.indexOf(added),
        timeLimitMs = null,
    )
}

/** Spatial memory: a path across the grid, replayed from its start. */
private fun pathMemory(level: Level, random: Random): Round {
    val start = random.nextInt(GRID)
    val path = mutableListOf(start)
    repeat(level.span - 1) {
        val last = path.last()
        val neighbours = neighboursOf(last).filterNot { it in path }
        path += (neighbours.ifEmpty { neighboursOf(last) }).random(random)
    }
    return Round(
        prompt = RoundPrompt.Sequence(path),
        options = (0 until GRID).map { RoundOption(key = "cell", cell = it) },
        correct = path.first(),
        timeLimitMs = null,
    )
}

/** Orthogonal neighbours in the 3×3 grid — a path has to be walkable. */
internal fun neighboursOf(cell: Int): List<Int> {
    val row = cell / 3
    val col = cell % 3
    return buildList {
        if (row > 0) add(cell - 3)
        if (row < 2) add(cell + 3)
        if (col > 0) add(cell - 1)
        if (col < 2) add(cell + 1)
    }
}

// ── Flexibility ──────────────────────────────────────────────────────────

/**
 * Task switching: the rule ("pick the larger" / "pick the smaller") flips
 * unpredictably. The cost being trained is the switch itself, so the rule must
 * sometimes repeat and sometimes not — alternating every round teaches the
 * alternation instead.
 */
private fun ruleSwitch(level: Level, random: Random): Round {
    val pickLarger = random.nextBoolean()
    val a = random.nextInt(1, 9)
    val b = ((a + random.nextInt(1, 8) - 1) % 9) + 1
    val values = listOf(a, if (b == a) (a % 9) + 1 else b)
    val winner = if (pickLarger) values.max() else values.min()
    val options = values.map { RoundOption(key = it.toString()) }
    return Round(
        prompt = RoundPrompt.Instruction("mg_rule_prompt", if (pickLarger) "mg_rule_large" else "mg_rule_small"),
        options = options,
        correct = options.indexOfFirst { it.key == winner.toString() },
        timeLimitMs = level.timeLimitMs,
    )
}

/** Bilateral coordination: follow the side that lights, at a quickening pace. */
private fun mirrorTap(level: Level, random: Random): Round {
    val left = random.nextBoolean()
    val options = listOf(RoundOption("mg_left"), RoundOption("mg_right"))
    return Round(
        prompt = RoundPrompt.Instruction("mg_follow_side", if (left) "mg_left" else "mg_right"),
        options = options,
        correct = if (left) 0 else 1,
        timeLimitMs = (level.timeLimitMs * 0.8).toLong(),
    )
}

// ── Resilience ───────────────────────────────────────────────────────────

/**
 * The one Resilience game kept, because it is the only one with a lineage worth
 * claiming: sorting a thought as a familiar unhelpful pattern or a workable
 * one is the noticing step of CBT reframing.
 *
 * The thoughts are generic and pre-written. Asking the user to sort their *own*
 * thoughts into right and wrong would be an assessment this app does not do.
 */
private fun thoughtSort(level: Level, random: Random): Round {
    val (key, helpful) = THOUGHT_KEYS.random(random)
    val options = listOf(RoundOption("mg_sort_unhelpful"), RoundOption("mg_sort_workable"))
    return Round(
        prompt = RoundPrompt.Thought(key, helpful),
        options = options,
        correct = if (helpful) 1 else 0,
        timeLimitMs = null,   // a reflective round is never a race
    )
}

// ── Calm (unscored) ──────────────────────────────────────────────────────

/** A calm step has no right answer — the "round" is only the pacing unit. */
private fun calmStep(level: Level): Round = Round(
    prompt = RoundPrompt.Instruction("mg_calm_step", ""),
    options = emptyList(),
    correct = -1,
    timeLimitMs = null,
)

/** Whether a game keeps score at all. The calm ones do not, on purpose. */
fun isScored(gameId: String): Boolean = gameId !in setOf("breathing-rhythm", "zen-sand", "still-point")

/**
 * The closing line for a session.
 *
 * Never a grade. "3/6" invites a user who is already having a hard day to read
 * it as a verdict on themselves; the band is deliberately coarse and the copy
 * deliberately warm.
 */
fun resultBand(score: Int, total: Int): String = when {
    total <= 0 -> "steady"
    score >= total -> "sharp"
    score >= total * 0.6 -> "steady"
    else -> "gentle"
}
