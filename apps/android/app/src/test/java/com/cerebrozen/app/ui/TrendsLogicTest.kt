package com.cerebrozen.app.ui

import com.cerebrozen.app.R
import com.cerebrozen.app.ui.screens.contiguousRuns
import com.cerebrozen.app.ui.screens.durationLabel
import com.cerebrozen.app.ui.screens.parseTrends
import com.cerebrozen.app.ui.screens.trendsEmptyBodyRes
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Trends screen's pure logic — the honesty rules from the 2026-07-31
 * insights audit, pinned. TrendsScreen.kt has claimed "(pure, unit-tested)"
 * above these functions since it shipped; as of 2026-08-03 this file makes
 * that true.
 */
class TrendsLogicTest {

    // ── parseTrends: absent is absent, never zero ────────────────────────

    @Test
    fun `a null summary stays null - the server refused a number and 0 is not it`() {
        val payload = JSONObject(
            """{"days":30,
                "mood":{"points":[{"date":"2026-08-01","ease":3.0}],
                        "enough_data":false,"average_ease":null,"days_logged":1},
                "sleep":{"points":[],"enough_data":false,"avg_duration_min":null,"nights":0},
                "correlation":{"available":false,"coefficient":null,"direction":null,
                               "reason":"needs_more","pairs":2}}""",
        )
        val trends = parseTrends(payload)
        assertNull(trends.mood.summary)
        assertNull(trends.sleep.summary)
        assertFalse(trends.mood.enoughData)
        assertEquals(1, trends.mood.points.size)
        assertFalse(trends.link.available)
        assertEquals(2, trends.link.pairs)
    }

    @Test
    fun `empty means both series empty - one lone series still draws`() {
        val empty = parseTrends(JSONObject("""{"days":7}"""))
        assertTrue(empty.isEmpty)

        val oneSeries = parseTrends(
            JSONObject(
                """{"days":7,
                    "mood":{"points":[{"date":"2026-08-01","ease":4.0,"mood":"Good"}],
                            "enough_data":false,"average_ease":null,"days_logged":1}}""",
            ),
        )
        assertFalse(oneSeries.isEmpty)
        assertEquals("Good", oneSeries.mood.points[0].label)
    }

    @Test
    fun `a real summary comes through with its count`() {
        val trends = parseTrends(
            JSONObject(
                """{"days":30,
                    "mood":{"points":[{"date":"2026-08-01","ease":3.0}],
                            "enough_data":true,"average_ease":3.4,"days_logged":9}}""",
            ),
        )
        assertEquals(3.4f, trends.mood.summary!!, 0.001f)
        assertTrue(trends.mood.enoughData)
        assertEquals(9, trends.mood.logged)
    }

    // ── contiguousRuns: gaps break the line ──────────────────────────────

    @Test
    fun `a gap splits the line - no confident slope through silent days`() {
        val runs = contiguousRuns(
            listOf("2026-08-01", "2026-08-02", "2026-08-05", "2026-08-06"),
        )
        assertEquals(listOf(0..1, 2..3), runs)
    }

    @Test
    fun `contiguous days stay one run and a single day is a lone dot`() {
        assertEquals(
            listOf(0..2),
            contiguousRuns(listOf("2026-08-01", "2026-08-02", "2026-08-03")),
        )
        assertEquals(listOf(0..0), contiguousRuns(listOf("2026-08-01")))
        assertEquals(emptyList<IntRange>(), contiguousRuns(emptyList()))
    }

    // A chart that breaks its line is honest; a chart that LOOKS broken is not
    // read as honest. The caption and the dotted bridge both key off "is there
    // more than one run", so that condition is what these pin.

    @Test
    fun `an unbroken line has nothing to explain`() {
        // No gap note, no dotted segment: one run means the line never stops,
        // and a caption about absent days would be noise on a full week.
        val runs = contiguousRuns(
            listOf("2026-08-01", "2026-08-02", "2026-08-03", "2026-08-04"),
        )
        assertEquals(1, runs.size)
        assertTrue("an unbroken series needs no gap caption", runs.size <= 1)
    }

    @Test
    fun `every gap gets exactly one bridge`() {
        // The dotted connectors are drawn from runs.zipWithNext(), so N runs
        // must produce N-1 bridges — one per hole, never one past the end.
        val runs = contiguousRuns(
            listOf("2026-08-01", "2026-08-04", "2026-08-05", "2026-08-09"),
        )
        assertEquals(listOf(0..0, 1..2, 3..3), runs)
        assertEquals("three runs leave two holes to bridge", 2, runs.zipWithNext().size)
    }

    @Test
    fun `a bridge joins the last logged day to the next one, never across itself`() {
        // Each connector spans the real endpoints either side of the silence,
        // so it cannot imply a reading on a day nobody logged.
        val runs = contiguousRuns(listOf("2026-08-01", "2026-08-02", "2026-08-06"))
        val bridges = runs.zipWithNext().map { (before, after) -> before.last to after.first }
        assertEquals(listOf(1 to 2), bridges)
    }

    @Test
    fun `an unparseable date breaks the run rather than bridging it`() {
        val runs = contiguousRuns(listOf("2026-08-01", "not-a-date", "2026-08-03"))
        assertEquals(listOf(0..0, 1..1, 2..2), runs)
    }

    // ── durationLabel ────────────────────────────────────────────────────

    @Test
    fun `durations read as hours and minutes past the hour`() {
        assertEquals("6h 40m", durationLabel(400))
        assertEquals("1h 0m", durationLabel(60))
        assertEquals("45m", durationLabel(45))
    }

    // ── trendsEmptyBodyRes: the empty state tells the true reason ────────

    @Test
    fun `with both histories allowed the empty state invites a check-in`() {
        assertEquals(R.string.trends_empty_body, trendsEmptyBodyRes(moodAllowed = true, sleepAllowed = true))
    }

    @Test
    fun `with either history off the empty state names the consent switch`() {
        assertEquals(R.string.trends_empty_consent_off, trendsEmptyBodyRes(moodAllowed = false, sleepAllowed = true))
        assertEquals(R.string.trends_empty_consent_off, trendsEmptyBodyRes(moodAllowed = true, sleepAllowed = false))
        assertEquals(R.string.trends_empty_consent_off, trendsEmptyBodyRes(moodAllowed = false, sleepAllowed = false))
    }
}
