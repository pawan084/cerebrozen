package com.cerebrozen.app.ui.screens

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure screen-logic tests: the sleep time math (24h wrap, zero-padding), the
 * greeting buckets, and the JSON→model parsers. These are the bits most likely
 * to break silently on a schema tweak or an off-by-one.
 */
class ScreenLogicTest {

    // ── Sleep time math ─────────────────────────────────────────────
    @Test
    fun minutesToLabel_formats_hours_and_zero_padded_minutes() {
        assertEquals("7h 30m", minutesToLabel(450))
        assertEquals("8h 05m", minutesToLabel(485))
        assertEquals("0h 00m", minutesToLabel(0))
    }

    @Test
    fun hhmm_zero_pads_and_wraps_around_the_clock() {
        assertEquals("23:00", hhmm(23 * 60))
        assertEquals("07:05", hhmm(7 * 60 + 5))
        assertEquals("00:00", hhmm(24 * 60))   // exactly midnight wraps to 0
        assertEquals("23:30", hhmm(-30))        // −30m from midnight wraps back a day
    }

    // ── Greeting buckets ────────────────────────────────────────────
    @Test
    fun greeting_buckets_by_hour() {
        assertEquals("Good morning", greetingFor(5))
        assertEquals("Good morning", greetingFor(11))
        assertEquals("Good afternoon", greetingFor(12))
        assertEquals("Good afternoon", greetingFor(16))
        assertEquals("Good evening", greetingFor(17))
        assertEquals("Good evening", greetingFor(2))   // small hours
    }

    // ── Parsers (JSON → model) ──────────────────────────────────────
    @Test
    fun parseNights_maps_rows_and_defaults_missing_duration() {
        val rows = JSONArray()
            .put(JSONObject().put("date", "2026-07-04").put("duration_min", 445).put("quality", 4))
            .put(JSONObject().put("date", "2026-07-05").put("quality", 3))  // no duration_min
        val nights = parseNights(rows)
        assertEquals(2, nights.size)
        assertEquals(Night("2026-07-04", 445, 4), nights[0])
        assertEquals(0, nights[1].duration)   // optInt default
    }

    @Test
    fun parseChat_maps_role_and_text_in_order() {
        val rows = JSONArray()
            .put(JSONObject().put("role", "user").put("text", "hi"))
            .put(JSONObject().put("role", "assistant").put("text", "hello"))
        assertEquals(listOf(Msg("user", "hi"), Msg("assistant", "hello")), parseChat(rows))
    }

    @Test
    fun parseEntries_takes_date_prefix_and_defaults_risk() {
        val rows = JSONArray().put(
            JSONObject().put("title", "T").put("body", "B")
                .put("created_at", "2026-07-04T12:34:56Z"),   // no risk_level field
        )
        val entries = parseEntries(rows)
        assertEquals(1, entries.size)
        assertEquals("2026-07-04", entries[0].date)   // created_at.take(10)
        assertEquals("none", entries[0].risk)          // optString default
    }
}
