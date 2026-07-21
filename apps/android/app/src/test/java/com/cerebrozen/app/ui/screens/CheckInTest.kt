package com.cerebrozen.app.ui.screens

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The daily check-in's date arithmetic and its copy.
 *
 * Api.checkIn had exactly one caller — onboarding — so the app asked how you were at signup
 * and never again. These pin the two things that would fail silently: the timestamp parse
 * (the engine emits `+00:00`, which Instant.parse rejects — an empty ring for a daily user,
 * with no error anywhere), and the copy, which must stay a mirror rather than a scorecard.
 */
class CheckInTest {

    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 16) // a Thursday

    private fun moods(vararg ts: String) =
        JSONArray(ts.joinToString(",", "[", "]") { """{"ts":"$it","mood":"Okay"}""" })

    // ── the parse ────────────────────────────────────────────────────────────

    @Test
    fun `it parses the offset format the engine actually writes`() {
        // datetime.now(timezone.utc).isoformat() -> "+00:00", NOT "Z". Instant.parse would
        // have thrown on every row here and drawn an empty ring.
        val got = checkInDates(moods("2026-07-16T09:30:00.123456+00:00"), utc)
        assertEquals(setOf(LocalDate.of(2026, 7, 16)), got)
    }

    @Test
    fun `it also parses a Z timestamp`() {
        assertEquals(setOf(LocalDate.of(2026, 7, 16)), checkInDates(moods("2026-07-16T09:30:00Z"), utc))
    }

    @Test
    fun `one unparseable row does not blank the ring`() {
        val got = checkInDates(moods("2026-07-16T09:30:00+00:00", "junk", "", "2026-07-15T08:00:00Z"), utc)
        assertEquals(setOf(LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 15)), got)
    }

    @Test
    fun `an empty list is not an error`() {
        assertTrue(checkInDates(JSONArray(), utc).isEmpty())
    }

    @Test
    fun `a late check-in belongs to the day the person had, not the day UTC had`() {
        // 23:30 in Kolkata on the 16th is 18:00 UTC on the 16th — but the point is the
        // conversion happens in THEIR zone, so an 01:00 local check-in isn't yesterday.
        val kolkata = ZoneId.of("Asia/Kolkata")
        val got = checkInDates(moods("2026-07-16T19:30:00+00:00"), kolkata) // 01:00 on the 17th local
        assertEquals(setOf(LocalDate.of(2026, 7, 17)), got)
    }

    // ── the ring ─────────────────────────────────────────────────────────────

    @Test
    fun `the week is seven days ending today`() {
        val week = weekPresence(emptySet(), today, Locale.UK)
        assertEquals(7, week.size)
        assertEquals(7, week.map { it.first }.size)
    }

    @Test
    fun `today is always the last entry`() {
        // Anchored on today, not on a calendar week: a Monday-start week makes Sunday
        // evening look like a wasted week, and this is a mirror, not a scorecard.
        val week = weekPresence(setOf(today), today, Locale.UK)
        assertTrue("today must be the last dot and marked present", week.last().second)
        assertFalse(week.dropLast(1).any { it.second })
    }

    @Test
    fun `it marks only the days that have a check-in`() {
        val week = weekPresence(setOf(today, today.minusDays(2)), today, Locale.UK)
        assertEquals(listOf(false, false, false, false, true, false, true), week.map { it.second })
    }

    @Test
    fun `a check-in older than the window does not light a dot`() {
        val week = weekPresence(setOf(today.minusDays(40)), today, Locale.UK)
        assertFalse(week.any { it.second })
    }

    @Test
    fun `checkedInToday is exactly that`() {
        assertTrue(checkedInToday(setOf(today), today))
        assertFalse(checkedInToday(setOf(today.minusDays(1)), today))
        assertFalse(checkedInToday(emptySet(), today))
    }

    // ── the copy: progress, not gamification ─────────────────────────────────

    @Test
    fun `a one-day streak is not announced`() {
        // "1 day streak" is noise dressed as an achievement.
        assertEquals("Noted — thanks for checking in.", progressLine(1, checkedIn = true))
    }

    @Test
    fun `a real streak is stated plainly`() {
        assertEquals("5 days in a row.", progressLine(5, checkedIn = true))
    }

    @Test
    fun `it asks rather than scolds when they have not checked in`() {
        assertEquals("How's today going?", progressLine(0, checkedIn = false))
    }

    @Test
    fun `it never warns about losing a streak`() {
        /* PRODUCT.md: "streaks and journey progress are shown CALMLY", and the platform's
           own streak endpoint says a broken streak "does not nag". A coin is a score you
           can lose; a ring is a mirror. */
        val lines = listOf(
            progressLine(0, false), progressLine(1, false), progressLine(5, false),
            progressLine(0, true), progressLine(1, true), progressLine(9, true),
        )
        for (line in lines) {
            for (bad in listOf("don't break", "lose", "keep it up", "!", "missed", "failed")) {
                assertFalse("copy nags: \"$line\"", line.lowercase().contains(bad))
            }
        }
    }

    @Test
    fun `the mood options stay short enough to answer in two seconds`() {
        assertTrue(MOOD_OPTIONS.size in 3..5)
        assertTrue(MOOD_OPTIONS.all { it.label.isNotBlank() && it.symbol.isNotBlank() })
        assertEquals(MOOD_OPTIONS.size, MOOD_OPTIONS.map { it.label }.toSet().size)
    }

    // ── the month density strip (HOME_SPEC #17) ──────────────────────────────

    @Test
    fun `the month is thirty days ending today`() {
        val month = monthPresence(emptySet(), today)
        assertEquals(30, month.size)
    }

    @Test
    fun `today is always the last entry in the month too`() {
        val month = monthPresence(setOf(today), today)
        assertTrue(month.last())
        assertFalse(month.dropLast(1).any { it })
    }

    @Test
    fun `a check-in older than thirty days does not light a tick`() {
        assertFalse(monthPresence(setOf(today.minusDays(45)), today).any { it })
    }

    // ── the quiet milestone tint (HOME_SPEC #18) ─────────────────────────────

    private val base = androidx.compose.ui.graphics.Color(0xFFF56B6B)
    private val gold = androidx.compose.ui.graphics.Color(0xFFE0B341)

    @Test
    fun `below a week the tint is plain, unshifted`() {
        assertEquals(base, milestoneTint(0, base, gold))
        assertEquals(base, milestoneTint(6, base, gold))
    }

    @Test
    fun `each milestone shifts warmer than the one before it, never past gold`() {
        val week = milestoneTint(7, base, gold)
        val month = milestoneTint(30, base, gold)
        val hundred = milestoneTint(100, base, gold)
        assertTrue("7 days must shift at all", week != base)
        // Closer to gold means a smaller distance from gold's own channels.
        fun distanceToGold(c: androidx.compose.ui.graphics.Color) =
            kotlin.math.abs(c.red - gold.red) + kotlin.math.abs(c.green - gold.green) + kotlin.math.abs(c.blue - gold.blue)
        assertTrue("30 days is warmer than 7", distanceToGold(month) < distanceToGold(week))
        assertTrue("100 days is warmer than 30", distanceToGold(hundred) < distanceToGold(month))
    }
}
