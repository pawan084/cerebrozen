package com.cerebrozen.app.ui.screens

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Today's assembly rules (HOME_SPEC #31): which doors appear, in what order, and with what
 * copy. `buildDoors`/`presenceLines` are pure — no Compose, no coroutine — specifically so a
 * refactor that silently drops a door or breaks the evening Wind-down gate fails a test
 * instead of only being noticed by a person scrolling Today.
 */
class CoachHomeTest {

    private fun program(day: Int, days: Int, title: String) =
        JSONObject().put("day", day).put("days", days).put("title", title)

    // ── which doors exist ────────────────────────────────────────────────────

    @Test
    fun `every door key appears exactly once, daytime, no program`() {
        val doors = buildDoors(hour = 14, open = 0, activeProgram = null)
        val keys = doors.map { it.key }
        assertEquals(listOf("actions", "journeys", "toolkit", "sleep", "human").sorted(), keys.sorted())
        assertEquals("no duplicate doors", keys.size, keys.toSet().size)
    }

    @Test
    fun `wind-down only exists in the evening-through-early-morning window`() {
        assertFalse("3pm is not the window", buildDoors(15, 0, null).any { it.key == "winddown" })
        assertTrue("8pm is the window", buildDoors(20, 0, null).any { it.key == "winddown" })
        assertTrue("11pm is the window", buildDoors(23, 0, null).any { it.key == "winddown" })
        assertTrue("2am is still the window", buildDoors(2, 0, null).any { it.key == "winddown" })
        assertFalse("3am is past the window", buildDoors(3, 0, null).any { it.key == "winddown" })
    }

    // ── ordering (HOME_SPEC #1, #3, #4) ──────────────────────────────────────

    @Test
    fun `wind-down is promoted near the top in the evening, not merely appended`() {
        val doors = buildDoors(hour = 21, open = 0, activeProgram = null)
        // "Promoted" means it outranks the everyday doors — not merely present at the end.
        val windDownIndex = doors.indexOfFirst { it.key == "winddown" }
        val toolkitIndex = doors.indexOfFirst { it.key == "toolkit" }
        val sleepIndex = doors.indexOfFirst { it.key == "sleep" }
        assertTrue(windDownIndex < toolkitIndex)
        assertTrue(windDownIndex < sleepIndex)
    }

    @Test
    fun `commitments rises with how many are open`() {
        val zero = buildDoors(14, 0, null).first { it.key == "actions" }.priority
        val one = buildDoors(14, 1, null).first { it.key == "actions" }.priority
        val many = buildDoors(14, 5, null).first { it.key == "actions" }.priority
        assertTrue(one > zero)
        assertTrue(many > one)
    }

    @Test
    fun `need a human rises only once several commitments are open, never by urgency styling`() {
        val calm = buildDoors(14, 0, null).first { it.key == "human" }
        val busy = buildDoors(14, 3, null).first { it.key == "human" }
        assertTrue(busy.priority > calm.priority)
        // Ordering signal only — the copy itself never changes tone or adds urgency language.
        assertEquals(calm.desc, busy.desc)
    }

    @Test
    fun `equal-priority doors keep a stable, deliberate relative order`() {
        val a = buildDoors(14, 0, null).map { it.key }
        val b = buildDoors(14, 0, null).map { it.key }
        assertEquals("re-sorting identical input must not reshuffle it", a, b)
    }

    @Test
    fun `every door gets its OWN accent — no two share a color`() {
        // Device-verified regression (2026-07-20): the original HOME_SPEC #26 fix blended
        // `Periwinkle` and `Violet`, both of which resolve to CORAL-family hues in this app's
        // actual night palette (Color.kt — their English names are misleading holdovers), so
        // Wind-down rendered visually identical to Journeys on a real screen despite the pure
        // color VALUES differing in isolation. This asserts on the ACTUAL resolved theme
        // colors so a future palette change can't quietly reintroduce the same collision.
        val doors = buildDoors(hour = 22, open = 3, activeProgram = program(3, 7, "Better Sleep"))
        val accents = doors.map { it.accent }
        assertEquals(
            "two doors are rendering the identical accent color: $doors",
            accents.size, accents.toSet().size,
        )
    }

    // ── state-aware copy (HOME_SPEC #2, #5) ──────────────────────────────────

    @Test
    fun `journeys stays generic with no active program`() {
        val journeys = buildDoors(14, 0, null).first { it.key == "journeys" }
        assertEquals("Journeys", journeys.title)
        assertFalse(journeys.desc.contains("Day"))
    }

    @Test
    fun `journeys carries day X of Y and outranks the generic doors once a program is active`() {
        val withProgram = buildDoors(14, 0, program(3, 7, "Better Sleep")).first { it.key == "journeys" }
        val without = buildDoors(14, 0, null).first { it.key == "journeys" }
        assertEquals("Continue your journey", withProgram.title)
        assertEquals("Day 3 of 7 — Better Sleep", withProgram.desc)
        assertTrue(withProgram.priority > without.priority)
    }

    @Test
    fun `a malformed program payload falls back to the generic door rather than blank copy`() {
        val doors = buildDoors(14, 0, JSONObject()) // day/days/title all missing
        val journeys = doors.first { it.key == "journeys" }
        assertEquals("Journeys", journeys.title)
        assertFalse(journeys.desc.isBlank())
    }

    @Test
    fun `commitments copy is exact for zero, one, and many`() {
        assertEquals(
            "Nothing open — your next session ends with one concrete step.",
            buildDoors(14, 0, null).first { it.key == "actions" }.desc,
        )
        assertEquals("1 open commitment waiting on you.", buildDoors(14, 1, null).first { it.key == "actions" }.desc)
        assertEquals("3 open commitments waiting on you.", buildDoors(14, 3, null).first { it.key == "actions" }.desc)
    }

    // ── the resumable-session hint (HOME_SPEC #25) ───────────────────────────

    @Test
    fun `a resumable session is only mentioned when nothing else is more pressing`() {
        val (_, say) = presenceLines("Nova", openActions = 0, hour = 14, resumable = true)
        assertEquals("Pick up where you left off, whenever you're ready.", say)
    }

    @Test
    fun `an open commitment still outranks a resumable session`() {
        val (_, say) = presenceLines("Nova", openActions = 1, hour = 14, resumable = true)
        assertTrue("a concrete promised step matters more than a conversation", say.contains("commitment"))
    }

    @Test
    fun `resumable is invisible when absent — no regression to the original copy`() {
        val (_, say) = presenceLines("Nova", openActions = 0, hour = 14, resumable = false)
        assertEquals("What's the moment in front of you? Two minutes of prep changes how it goes.", say)
    }
}
