package com.cerebrozen.app.ui.screens

import org.json.JSONArray
import org.json.JSONObject
import com.cerebrozen.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CBT-I Phase-1 rhythm math (SleepScreen "Your rhythm" card): the wrap-around
 * duration/spread helpers are pure and these tests pin the midnight edge cases —
 * the bits most likely to silently drift on a refactor.
 */
class SleepInsightTest {

    private fun night(bed: String?, wake: String?) =
        SleepNight("2026-07-10", 0, 3, parseClockMinutes(bed), parseClockMinutes(wake))

    // ── Clock parsing ───────────────────────────────────────────────
    @Test
    fun parseClockMinutes_reads_hhmm_and_hhmmss_and_rejects_junk() {
        assertEquals(23 * 60 + 30, parseClockMinutes("23:30"))
        assertEquals(23 * 60 + 30, parseClockMinutes("23:30:00"))   // backend serializes seconds
        assertEquals(7 * 60 + 5, parseClockMinutes("7:05"))
        assertEquals(0, parseClockMinutes("00:00"))
        assertEquals(null, parseClockMinutes(null))
        assertEquals(null, parseClockMinutes(""))
        assertEquals(null, parseClockMinutes("bedtime"))
        assertEquals(null, parseClockMinutes("25:00"))   // out of range
        assertEquals(null, parseClockMinutes("12:75"))
    }

    // ── Average duration (bedtime→wake, past-midnight wrap) ─────────
    @Test
    fun averageSleepMinutes_wraps_a_bedtime_before_midnight() {
        assertEquals(450, averageSleepMinutes(listOf(night("23:30", "07:00"))))   // 7h30
    }

    @Test
    fun averageSleepMinutes_handles_a_bedtime_after_midnight() {
        assertEquals(450, averageSleepMinutes(listOf(night("00:30", "08:00"))))   // 7h30, no wrap
    }

    @Test
    fun averageSleepMinutes_averages_and_skips_logs_missing_times() {
        val logs = listOf(
            night("23:00", "07:00"),   // 480
            night("01:00", "07:00"),   // 360
            night(null, "07:00"),      // no bedtime → skipped
        )
        assertEquals(420, averageSleepMinutes(logs))
        assertEquals(null, averageSleepMinutes(emptyList()))
        assertEquals(null, averageSleepMinutes(listOf(night(null, null))))
    }

    // ── Night length preview (the fact the steppers are for) ────────
    @Test
    fun nightLengthMinutes_wraps_midnight_like_the_rhythm_math() {
        assertEquals(480, nightLengthMinutes(23 * 60, 7 * 60))     // 23:00 → 07:00
        assertEquals(450, nightLengthMinutes(30, 8 * 60))          // 00:30 → 08:00
        assertEquals(0, nightLengthMinutes(7 * 60, 7 * 60))        // degenerate, not negative
    }

    // ── Merged data card helpers (chart axis, human dates, bed window) ─
    @Test
    fun dayLetterFor_maps_dates_and_degrades_on_garbage() {
        assertEquals("S", dayLetterFor("2026-08-02"))   // Sunday
        assertEquals("M", dayLetterFor("2026-08-03"))
        assertEquals("·", dayLetterFor("not-a-date"))
    }

    @Test
    fun humanDate_reads_like_a_person_and_passes_garbage_through() {
        assertEquals("Sun 2 Aug", humanDate("2026-08-02"))
        assertEquals("bedtime", humanDate("bedtime"))
    }

    @Test
    fun bedtimeWindow_spans_midnight_without_splitting() {
        val logs = listOf(night("23:00", "07:00"), night("00:10", "08:00"))
        assertEquals(23 * 60 to 10, bedtimeWindow(logs))
        assertEquals(null, bedtimeWindow(listOf(night(null, "07:00"))))
    }

    // ── Time-aware lead block (morning check-in vs evening wind-down) ─
    @Test
    fun checkInLeadsAt_hands_over_to_winddown_at_five_pm_and_back_at_four_am() {
        assertEquals(false, checkInLeadsAt(3))    // 3am: help going down, not a survey
        assertEquals(true, checkInLeadsAt(4))
        assertEquals(true, checkInLeadsAt(9))
        assertEquals(true, checkInLeadsAt(16))
        assertEquals(false, checkInLeadsAt(17))   // evening: wind-down leads
        assertEquals(false, checkInLeadsAt(23))
        assertEquals(false, checkInLeadsAt(0))
    }

    // ── Bedtime spread (max−min, anchored so midnight doesn't split) ─
    @Test
    fun bedtimeSpreadMinutes_keeps_bedtimes_either_side_of_midnight_close() {
        val logs = listOf(night("23:30", "07:00"), night("00:30", "08:00"))
        assertEquals(60, bedtimeSpreadMinutes(logs))   // one hour apart, not 23
    }

    @Test
    fun bedtimeSpreadMinutes_spans_evening_to_smallhours() {
        val logs = listOf(night("22:00", "06:00"), night("23:00", "07:00"), night("00:30", "08:00"))
        assertEquals(150, bedtimeSpreadMinutes(logs))
        assertEquals(0, bedtimeSpreadMinutes(listOf(night("23:00", "07:00"))))
        assertEquals(null, bedtimeSpreadMinutes(listOf(night(null, "07:00"))))
        assertEquals(null, bedtimeSpreadMinutes(emptyList()))
    }

    // ── The principle line follows the data ─────────────────────────
    // rhythmPrinciple returns the string RESOURCE for the principle (so the copy
    // localizes); the 90-minute boundary is what's under test.
    @Test
    fun rhythmPrinciple_switches_on_the_90_minute_boundary() {
        // Both seams exist and must agree on the boundary: the screen branches on
        // the boolean, and rhythmPrinciple names the copy for the same threshold.
        assertEquals(true, isVariedRhythm(91))
        assertEquals(false, isVariedRhythm(90))
        assertEquals(false, isVariedRhythm(0))
        assertEquals(R.string.sleep_rhythm_vary, rhythmPrinciple(91))
        assertEquals(R.string.sleep_rhythm_steady, rhythmPrinciple(90))
        assertEquals(R.string.sleep_rhythm_steady, rhythmPrinciple(0))
    }

    @Test
    fun hoursMinutes_backs_the_localized_spread_labels() {
        // Display copy resolves via R.string.duration_m / duration_h_m at the
        // composable (spreadLabelText); the pure split stays pinned here.
        assertEquals(0 to 45, hoursMinutes(45))
        assertEquals(1 to 50, hoursMinutes(110))
        assertEquals(6 to 40, hoursMinutes(400))
    }

    // ── Parser carries the new time fields (and stays backwards-safe) ─
    @Test
    fun parseNights_reads_bedtime_and_wake_time_when_present() {
        val rows = JSONArray()
            .put(
                JSONObject().put("date", "2026-07-10").put("duration_min", 450).put("quality", 4)
                    .put("bedtime", "23:30:00").put("wake_time", "07:00:00"),
            )
            .put(JSONObject().put("date", "2026-07-11").put("quality", 3))   // no times at all
        val nights = parseNights(rows)
        assertEquals(23 * 60 + 30, nights[0].bedMin)
        assertEquals(7 * 60, nights[0].wakeMin)
        assertEquals(null, nights[1].bedMin)
        assertEquals(null, nights[1].wakeMin)
    }
}
