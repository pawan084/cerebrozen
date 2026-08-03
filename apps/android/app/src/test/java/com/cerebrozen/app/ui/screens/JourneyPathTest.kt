package com.cerebrozen.app.ui.screens

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The journey path's two pure inputs. The rules under test are product rules,
 * not geometry trivia: nothing is ever locked, and a day that has passed is
 * never called "done".
 */
class JourneyPathTest {

    @Test
    fun aDayIsPassedTodayOrAhead_andNeverLocked() {
        assertEquals(DayState.PASSED, dayState(day = 1, currentDay = 3))
        assertEquals(DayState.TODAY, dayState(day = 3, currentDay = 3))
        assertEquals(DayState.AHEAD, dayState(day = 7, currentDay = 3))
        // The whole enum, so a future "LOCKED" cannot be added quietly: gating a
        // coping practice behind progress is the thing this deliberately lacks.
        assertEquals(
            listOf(DayState.PASSED, DayState.TODAY, DayState.AHEAD),
            DayState.entries.toList(),
        )
    }

    @Test
    fun everyDayOfTheProgramGetsAState_includingBeforeDayOneAndPastTheEnd() {
        // Day counts clamp server-side, but the path must not throw if they drift.
        assertEquals(DayState.AHEAD, dayState(day = 1, currentDay = 0))
        assertEquals(DayState.PASSED, dayState(day = 7, currentDay = 99))
    }

    @Test
    fun theSerpentineMeandersRatherThanZigZagging() {
        val biases = (0 until 8).map { nodeBias(it) }
        assertEquals(listOf(0f, 0.62f, 0f, -0.62f, 0f, 0.62f, 0f, -0.62f), biases)
        assertTrue("nodes must stay inside the card", biases.all { it in -1f..1f })
    }

    @Test
    fun guidesParseInOrderAndSkipEmptyDays() {
        val program = JSONObject(
            """{"day":2,"days":3,"guides":[
                 {"title":"Settle","body":"Ten slow breaths."},
                 {"title":"","body":""},
                 {"title":"Wind down","body":"Lights low."}]}"""
        )
        val guides = parseDayGuides(program)
        assertEquals(2, guides.size)
        assertEquals("Settle" to "Ten slow breaths.", guides[0])
        assertEquals("Wind down" to "Lights low.", guides[1])
    }

    @Test
    fun anOlderServerWithoutGuidesFallsBackCleanly() {
        // The field is additive: no `guides` must mean "use the today-only card",
        // never a crash and never an empty path where content should be.
        assertEquals(emptyList<Pair<String, String>>(), parseDayGuides(null))
        assertEquals(
            emptyList<Pair<String, String>>(),
            parseDayGuides(JSONObject("""{"day":1,"days":7,"today_guide":{"title":"A","body":"B"}}""")),
        )
    }
}
