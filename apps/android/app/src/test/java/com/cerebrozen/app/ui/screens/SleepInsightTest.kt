package com.cerebrozen.app.ui.screens

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CBT-I Phase-1 rhythm math (SleepScreen "Your rhythm" card): the wrap-around
 * duration/spread helpers are pure and these tests pin the midnight edge cases —
 * the bits most likely to silently drift on a refactor.
 */
class SleepInsightTest {

    private fun night(bed: String?, wake: String?) =
        Night("2026-07-10", 0, 3, parseClockMinutes(bed), parseClockMinutes(wake))

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
    @Test
    fun rhythmPrinciple_switches_on_the_90_minute_boundary() {
        assertEquals(true, rhythmPrinciple(91).startsWith("A steadier bedtime"))
        assertEquals(true, rhythmPrinciple(90).startsWith("Your bedtime is steady"))
        assertEquals(true, rhythmPrinciple(0).startsWith("Your bedtime is steady"))
    }

    @Test
    fun spreadLabel_uses_plain_minutes_under_an_hour() {
        assertEquals("45m", spreadLabel(45))
        assertEquals("1h 50m", spreadLabel(110))
        assertEquals("6h 40m", spreadLabel(400))
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
