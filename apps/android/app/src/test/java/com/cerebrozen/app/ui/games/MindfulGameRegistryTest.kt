package com.cerebrozen.app.ui.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue after the 2026-08-01 rebuild.
 *
 * It used to assert 23 games. Twelve of those were the same seven functions
 * with different emoji, which is what this file now exists to prevent: the
 * mechanic test below fails the moment two games resolve to the same thing.
 */
class MindfulGameRegistryTest {

    @Test
    fun twelve_games_each_with_a_mechanic_of_its_own() {
        assertEquals(12, MindfulGameRegistry.games.size)
        assertEquals(
            "two games sharing a mechanic is a longer menu, not more to do",
            MindfulGameRegistry.games.size,
            MindfulGameRegistry.games.map { it.mechanic }.toSet().size,
        )
    }

    @Test
    fun every_game_has_the_strings_its_card_renders() {
        MindfulGameRegistry.games.forEach { game ->
            assertNotNull(MindfulGameRegistry.find(game.id))
            assertNotEquals(0, game.nameRes)
            assertNotEquals(0, game.descriptionRes)
            assertNotEquals(0, game.practiceRes)
            assertTrue("a card with no glyph draws an empty tile", game.glyph.isNotBlank())
        }
    }

    @Test
    fun every_category_still_has_something_in_it() {
        val covered = MindfulGameRegistry.games.map { it.category }.toSet()
        assertEquals(
            "an empty category renders as a heading with nothing under it",
            GameCategory.entries.toSet(), covered,
        )
    }

    @Test
    fun a_retired_id_lands_on_the_game_that_absorbed_it() {
        // A saved shortcut or an old deeplink must not open a blank screen
        // because the catalogue was tidied.
        assertEquals("color-tap", MindfulGameRegistry.find("sound-hunter")?.id)
        assertEquals("pattern-recall", MindfulGameRegistry.find("emotion-match")?.id)
        assertEquals("thought-sort", MindfulGameRegistry.find("growth-garden")?.id)
        assertEquals("still-point", MindfulGameRegistry.find("who-are-you")?.id)
    }

    @Test
    fun every_retirement_points_at_a_game_that_exists() {
        val live = MindfulGameRegistry.games.map { it.id }.toSet()
        MindfulGameRegistry.retired.forEach { (old, successor) ->
            assertTrue("$old redirects to $successor, which is not in the catalogue", successor in live)
            assertTrue("$old is retired and must not also be live", old !in live)
        }
    }

    @Test
    fun an_unknown_id_is_null_rather_than_a_guess() {
        assertEquals(null, MindfulGameRegistry.find("not-a-game"))
        assertEquals(null, MindfulGameRegistry.find(null))
    }
}
