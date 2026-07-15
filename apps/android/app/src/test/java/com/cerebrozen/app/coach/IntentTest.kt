package com.cerebrozen.app.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Coach's inline-card intent mapping. A card is an OFFER, not a reflex — so the
 * important cases are as much "stays plain" as "surfaces a card", and every card must
 * open a route the NavHost actually registers.
 */
class IntentTest {

    @Test
    fun a_plain_conversational_turn_surfaces_no_card() {
        assertNull(detectIntent("I want to get better at running standups"))
        assertNull(detectIntent("My manager gave me some feedback today and I'm mulling it over"))
        assertNull(detectIntent(""))
    }

    @Test
    fun sleep_trouble_offers_the_wind_down_routine() {
        val s = detectIntent("I just can't sleep lately, up all night")
        assertEquals("sleep", s?.id)
        assertEquals("winddown", s?.route)
    }

    @Test
    fun a_racing_anxious_mind_offers_a_breathing_reset() {
        assertEquals("breathe", detectIntent("I'm so anxious about tomorrow")?.id)
        assertEquals("breathe/reset", detectIntent("feeling really overwhelmed and tense")?.route)
    }

    @Test
    fun spiralling_offers_grounding_and_overthinking_offers_a_reframe() {
        assertEquals("ground", detectIntent("my head is spiraling and I can't focus")?.id)
        assertEquals("cbt", detectIntent("I keep overthinking the worst case")?.route)
    }

    @Test
    fun ordinary_loneliness_offers_a_human_never_a_crisis_card() {
        val s = detectIntent("I feel so alone in this and need to talk to someone")
        assertEquals("human", s?.id)
        assertEquals("humansupport", s?.route)
    }

    @Test
    fun crisis_language_is_NOT_handled_here_left_to_the_engine_takeover() {
        // Safety is the engine's deterministic takeover, not a keyword card. These must
        // NOT produce a chat suggestion — otherwise a card could sit where the takeover
        // belongs.
        assertNull(detectIntent("I want to end it all"))
        assertNull(detectIntent("thinking about hurting myself"))
    }

    @Test
    fun the_coach_reply_is_a_weaker_signal_too() {
        // Nothing in the user's text, but the coach named a tool.
        assertEquals("breathe", detectIntent("not sure", coachText = "Let's try a slow breathing reset")?.id)
    }

    @Test
    fun first_match_wins_so_at_most_one_card_per_turn() {
        // Mentions both sleep and anxiety; the mapping is deterministic (sleep rule first),
        // never two cards.
        val s = detectIntent("I'm anxious AND I can't sleep")
        assertEquals("sleep", s?.id)
    }

    @Test
    fun every_card_opens_a_real_navhost_route() {
        // The routes these cards open must exist in CereBroApp's NavHost, or a tap crashes.
        val registered = setOf(
            "winddown", "breathe/reset", "toolkit", "cbt", "humansupport", "sleep", "sounds",
        )
        val samples = listOf(
            "can't sleep", "so anxious", "spiraling out", "overthinking everything", "i feel so alone",
        )
        val routes = samples.mapNotNull { detectIntent(it)?.route }
        assertTrue("some sample should match", routes.isNotEmpty())
        routes.forEach { assertTrue("route '$it' must be registered", it in registered) }
    }
}
