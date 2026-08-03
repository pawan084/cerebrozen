package com.cerebrozen.app.ui.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The three defects the rebuild was for, each pinned by a test:
 *
 * 1. nothing was random — round 3 was the same round 3 forever;
 * 2. nothing got harder — five identical rounds, then a celebration;
 * 3. a Stroop round could be generated with no conflict in it, which is not a
 *    Stroop round.
 */
class GameEngineTest {

    private val scoredGames = MindfulGameRegistry.games.filter { isScored(it.id) }.map { it.id }

    // ── Variety ──────────────────────────────────────────────────────────

    @Test
    fun two_sessions_of_the_same_game_are_not_the_same_session() {
        val monday = buildSession("color-tap", seed = 1, rounds = 6)
        val tuesday = buildSession("color-tap", seed = 2, rounds = 6)

        assertNotEquals(
            "a second play that repeats the first is a memory test, not attention",
            monday.map { it.correct }, tuesday.map { it.correct },
        )
    }

    @Test
    fun the_same_seed_replays_exactly() {
        // Not a user-facing promise — it is what makes every other test here
        // possible, and what lets a bug report be reproduced.
        assertEquals(
            buildSession("stroop-flow", seed = 99).map { it.correct },
            buildSession("stroop-flow", seed = 99).map { it.correct },
        )
    }

    @Test
    fun the_answer_is_not_always_in_the_same_place() {
        val positions = (1..40L).map { seed -> buildSession("color-tap", seed, rounds = 1).first().correct }

        assertTrue(
            "an answer that never moves can be learned without looking at the prompt",
            positions.toSet().size > 1,
        )
    }

    // ── Difficulty ───────────────────────────────────────────────────────

    @Test
    fun timed_rounds_get_tighter_as_the_session_goes_on() {
        val session = buildSession("color-tap", seed = 7, rounds = 6)
        val limits = session.mapNotNull { it.timeLimitMs }

        assertEquals(6, limits.size)
        assertTrue("the last round must be tighter than the first", limits.last() < limits.first())
        assertTrue("but never so tight it is unfair", limits.last() >= 900)
    }

    @Test
    fun the_memory_span_grows_within_a_session() {
        val session = buildSession("pattern-recall", seed = 11, rounds = 6)
        val spans = session.map { (it.prompt as RoundPrompt.Sequence).cells.size }

        assertEquals(3, spans.first())
        assertTrue("a span that never grows is the same round six times", spans.last() > spans.first())
    }

    @Test
    fun the_field_of_choices_widens() {
        val session = buildSession("color-tap", seed = 3, rounds = 6)

        assertTrue(session.last().options.size > session.first().options.size)
    }

    @Test
    fun level_curve() {
        assertEquals(0f, Level(0, 6).progress, 0.001f)
        assertEquals(1f, Level(5, 6).progress, 0.001f)
        // A single-round session is the end of its own curve, not a divide by zero.
        assertEquals(1f, Level(0, 1).progress, 0.001f)
    }

    // ── Each mechanic's own rule ─────────────────────────────────────────

    @Test
    fun a_stroop_round_always_conflicts() {
        repeat(60) { seed ->
            val prompt = buildSession("stroop-flow", seed.toLong(), rounds = 1).first().prompt
            val conflict = prompt as RoundPrompt.Conflict
            assertNotEquals(
                "a word painted in its own colour is not a Stroop round",
                conflict.wordKey, conflict.inkKey,
            )
        }
    }

    @Test
    fun the_stroop_answer_is_the_ink_not_the_word() {
        val round = buildSession("stroop-flow", seed = 5, rounds = 1).first()
        val conflict = round.prompt as RoundPrompt.Conflict

        assertEquals(conflict.inkKey, round.options[round.correct].colorKey)
    }

    @Test
    fun go_no_go_is_mostly_go_but_never_predictable() {
        val signals = (1..80L).map { (buildSession("freeze-switch", it, rounds = 1).first().prompt as RoundPrompt.Signal).go }

        assertTrue("a no-go that never comes trains nothing", signals.any { !it })
        assertTrue("and one that always comes is not a no-go", signals.any { it })
        val goShare = signals.count { it } / signals.size.toFloat()
        assertTrue("no-go should stay the exception, around a third", goShare in 0.5f..0.85f)
    }

    @Test
    fun a_remembered_path_is_walkable() {
        repeat(30) { seed ->
            val cells = (buildSession("path-memory", seed.toLong(), rounds = 4).last().prompt as RoundPrompt.Sequence).cells
            cells.zipWithNext { a, b ->
                assertTrue(
                    "a path that jumps across the grid is not a path ($a → $b)",
                    b in neighboursOf(a),
                )
            }
        }
    }

    @Test
    fun neighbours_respect_the_edges() {
        assertEquals(setOf(1, 3), neighboursOf(0).toSet())          // top-left corner
        assertEquals(setOf(1, 3, 5, 7), neighboursOf(4).toSet())    // centre
        assertEquals(setOf(5, 7), neighboursOf(8).toSet())          // bottom-right
    }

    @Test
    fun change_spotting_points_at_the_item_that_was_added() {
        repeat(30) { seed ->
            val round = buildSession("object-tray", seed.toLong(), rounds = 3).first()
            val shownBefore = (round.prompt as RoundPrompt.Sequence).cells
            val answer = round.options[round.correct].cell

            assertFalse(
                "the answer must be the NEW item, not one that was already there",
                answer in shownBefore,
            )
        }
    }

    @Test
    fun rule_switch_offers_two_different_numbers_and_the_rule_decides() {
        repeat(40) { seed ->
            val round = buildSession("rule-switch", seed.toLong(), rounds = 2).first()
            val values = round.options.map { it.key.toInt() }
            assertNotEquals("two identical numbers have no larger or smaller", values[0], values[1])

            val prompt = round.prompt as RoundPrompt.Instruction
            val expected = if (prompt.argKey == "mg_rule_large") values.max() else values.min()
            assertEquals(expected, round.options[round.correct].key.toInt())
        }
    }

    @Test
    fun the_rule_does_not_simply_alternate() {
        val rules = (1..40L).map { (buildSession("rule-switch", it, rounds = 1).first().prompt as RoundPrompt.Instruction).argKey }

        assertTrue("alternating every round teaches the alternation, not the switch",
            rules.zipWithNext().any { (a, b) -> a == b })
        assertTrue("and it does still switch", rules.toSet().size == 2)
    }

    @Test
    fun a_sorting_round_is_never_a_race() {
        buildSession("thought-sort", seed = 4).forEach {
            assertNull("a reflective round with a clock is a quiz", it.timeLimitMs)
        }
    }

    @Test
    fun a_recall_round_is_never_a_race_either() {
        buildSession("pattern-recall", seed = 4).forEach { assertNull(it.timeLimitMs) }
    }

    // ── Scoring, and the deliberate absence of it ─────────────────────────

    @Test
    fun calm_games_are_not_scored() {
        listOf("breathing-rhythm", "zen-sand", "still-point").forEach {
            assertFalse("scoring calm is the pressure loop the redesign ruled out", isScored(it))
        }
    }

    @Test
    fun every_other_game_is_scored() {
        assertEquals(9, scoredGames.size)
        scoredGames.forEach { assertTrue(isScored(it)) }
    }

    @Test
    fun the_result_is_a_band_not_a_grade() {
        assertEquals("sharp", resultBand(6, 6))
        assertEquals("steady", resultBand(4, 6))
        assertEquals("gentle", resultBand(1, 6))
        // Zero rounds is a session that never started, not a failed one.
        assertEquals("steady", resultBand(0, 0))
    }

    // ── Every scored game produces playable rounds ───────────────────────

    @Test
    fun every_scored_game_has_a_reachable_correct_answer() {
        scoredGames.forEach { id ->
            buildSession(id, seed = 21).forEachIndexed { i, round ->
                assertTrue(
                    "$id round $i has ${round.options.size} options and answer ${round.correct}",
                    round.correct in round.options.indices,
                )
            }
        }
    }

    @Test
    fun unscored_games_ask_nothing() {
        listOf("breathing-rhythm", "zen-sand", "still-point").forEach { id ->
            buildSession(id, seed = 21).forEach { round ->
                assertTrue("a calm round with options implies a right answer", round.options.isEmpty())
                assertEquals(-1, round.correct)
            }
        }
    }

    @Test
    fun an_unknown_game_falls_back_to_a_calm_step_rather_than_crashing() {
        val round = buildRound("not-a-game", Level(0, 4), Random(1))

        assertEquals(-1, round.correct)
        assertTrue(round.options.isEmpty())
    }
}
