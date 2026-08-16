package com.cerebrozen.app.ui.screens

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * V3 companion-first pins — the pure logic behind the chat opener's
 * next-best-action, the middle escalation rung, Home's sleep bars, and the
 * quiet-days re-engagement card.
 */
class CompanionFirstTest {

    // ── moodNbaKind: the deterministic step after a chat check-in ─────────

    @Test
    fun `every wire mood maps to a one-per-behavior surface`() {
        // The six wire moods (cross-stack taxonomy) each earn a widget kind
        // that widgetRoute() can route — an NBA card must never be a dead card.
        val kinds = listOf("Good", "Anxious", "Low", "Tired", "Overwhelmed", "Not sure")
            .map { moodNbaKind(it) }
        kinds.forEach { kind ->
            assertTrue("widgetRoute must know '$kind'", widgetRoute(kind) != null)
        }
        assertEquals("grounding", moodNbaKind("Anxious"))
        assertEquals("breathing", moodNbaKind("Overwhelmed"))
        assertEquals("one_good_thing", moodNbaKind("Low"))
        assertEquals("sleep_checkin", moodNbaKind("Tired"))
        assertEquals("intention_set", moodNbaKind("Good"))
    }

    @Test
    fun `an unknown mood still gets the safest default`() {
        assertEquals("breathing", moodNbaKind("whatever"))
        assertEquals("breathing", moodNbaKind(""))
    }

    // ── soundsHeavy: the middle rung's ear ────────────────────────────────

    @Test
    fun `heavy-day phrases trigger the concern rung`() {
        assertTrue(soundsHeavy("I feel hopeless about all of it"))
        assertTrue(soundsHeavy("i just CAN'T COPE today"))
        assertTrue(soundsHeavy("there's no point anymore"))
    }

    @Test
    fun `ordinary hard days do not`() {
        // The rung is for concerning language, not for every bad mood — a
        // card that fires on "tired" would train people to ignore it.
        assertFalse(soundsHeavy("so tired today"))
        assertFalse(soundsHeavy("work was stressful"))
        assertFalse(soundsHeavy("I slept badly"))
    }

    // ── sleepBarsFrom: Home's seven honest bars ───────────────────────────

    private fun log(date: String, bed: String, wake: String): JSONObject =
        JSONObject().put("date", date).put("bedtime", bed).put("wake_time", wake)

    @Test
    fun `an overnight sleep computes across midnight and lands on its own day`() {
        val today = LocalDate.parse("2026-08-16")
        val bars = sleepBarsFrom(
            JSONArray().put(log("2026-08-15", "23:00:00", "07:00:00")),
            today,
        )
        // Seven slots always — the week keeps its rhythm (reference hgraph).
        assertEquals(7, bars.size)
        // …and the night lands on yesterday, the second-to-last slot: 8h
        // against the 10h ceiling.
        assertEquals(0.8f, bars[5].second!!, 0.01f)
    }

    @Test
    fun `missing nights are null slots, never zero-height lies`() {
        val today = LocalDate.parse("2026-08-16")
        val bars = sleepBarsFrom(
            JSONArray()
                .put(log("2026-08-15", "23:00:00", "07:00:00"))
                .put(log("2026-08-13", "22:30:00", "06:30:00"))
                .put(log("2026-07-01", "23:00:00", "07:00:00")),   // outside the window
            today,
        )
        assertEquals(7, bars.size)
        // Two real nights; every other slot is null (drawn as nothing), and
        // none of them is 0f — a zero bar would read as "you slept nothing".
        assertEquals(2, bars.count { it.second != null })
        assertTrue(bars.none { it.second == 0f })
    }

    @Test
    fun `a malformed night is skipped rather than crashing the card`() {
        val today = LocalDate.parse("2026-08-16")
        val bars = sleepBarsFrom(
            JSONArray().put(JSONObject().put("date", "2026-08-15").put("bedtime", "??")),
            today,
        )
        assertTrue(bars.all { it.second == null })
    }

    // ── quietDaysSince: the re-engagement card's honesty ──────────────────

    // ── followUpOwed: proactive, but only about things that happened ──────

    @Test
    fun `an activity you opened is asked about when you come back`() {
        // Nothing else in the app ever asks whether a suggestion helped.
        assertEquals(
            FollowUp.ACTIVITY,
            followUpOwed(pendingActivity = true, minutesSinceLastMessage = 2, hasConversation = true),
        )
        // It outranks the time-gap follow-up: the specific question beats the
        // general one.
        assertEquals(
            FollowUp.ACTIVITY,
            followUpOwed(pendingActivity = true, minutesSinceLastMessage = 9000, hasConversation = true),
        )
    }

    @Test
    fun `a long gap earns a welcome back, a short one earns silence`() {
        assertEquals(
            FollowUp.RETURN,
            followUpOwed(pendingActivity = false, minutesSinceLastMessage = 240, hasConversation = true),
        )
        // Glancing at the tab is not an event. A companion that greets you
        // every time you look at it is a nag.
        assertEquals(
            FollowUp.NONE,
            followUpOwed(pendingActivity = false, minutesSinceLastMessage = 5, hasConversation = true),
        )
    }

    @Test
    fun `an empty thread is the opener's job, not the follow-up's`() {
        // Otherwise the companion would greet you twice on a first run.
        assertEquals(
            FollowUp.NONE,
            followUpOwed(pendingActivity = false, minutesSinceLastMessage = null, hasConversation = false),
        )
        assertEquals(
            FollowUp.NONE,
            followUpOwed(pendingActivity = false, minutesSinceLastMessage = 9999, hasConversation = false),
        )
    }

    @Test
    fun `an unparseable timestamp keeps the companion quiet rather than guessing`() {
        assertEquals(
            FollowUp.NONE,
            followUpOwed(pendingActivity = false, minutesSinceLastMessage = null, hasConversation = true),
        )
    }

    // ── stepIcon: an icon must carry information, not decorate ────────────

    @Test
    fun `every backend step symbol gets its own meaningful icon`() {
        // The whole vocabulary from services/agentic.py _STEP_LIBRARY.
        val library = listOf("wind", "book", "moon.stars", "moon.zzz", "bell", "leaf",
            "brain", "sparkles", "target", "mic", "person.2", "heart")
        val icons = library.map { stepIcon(it) }
        // The vocabulary maps to at least 9 DISTINCT glyphs, so a plan never
        // reads as one shape repeated down the list.
        assertTrue("distinct icons: ${icons.distinct().size}", icons.distinct().size >= 9)
        // Both moons are the same bed glyph — they mean the same thing…
        assertEquals(stepIcon("moon.stars"), stepIcon("moon.zzz"))
        // …and things that mean different things look different.
        assertTrue(stepIcon("wind") != stepIcon("moon.zzz"))
        assertTrue(stepIcon("mic") != stepIcon("book"))
        assertTrue(stepIcon("heart") != stepIcon("target"))
        // `leaf` deliberately SHARES the neutral fallback glyph: the default is
        // "something gentle to do", which is exactly what a leaf step is.
        assertEquals(stepIcon("leaf"), stepIcon("totally.unknown", ""))
    }

    @Test
    fun `an unknown symbol falls back to the step's own words`() {
        // The regression this exists for: the AI plan generator emits titles in
        // plain words with unpredictable symbols, and Home drew a CALENDAR
        // beside "Nature Walk".
        assertEquals(stepIcon("figure.walk"), stepIcon("", "Nature Walk"))
        assertEquals(stepIcon("wind"), stepIcon("zzz-unknown", "Evening breathing"))
        assertEquals(stepIcon("moon.zzz"), stepIcon("", "Tonight's wind-down"))
        // "Reflective journal" is a WRITING step, so it takes the pencil rather
        // than the book the `book` symbol carries — the title is read for what
        // the step asks you to do, not for its noun.
        assertEquals(stepIcon("pencil"), stepIcon("", "Reflective journal"))
    }

    @Test
    fun `a step with nothing to go on still gets a gentle icon, never a crash`() {
        assertNotNull(stepIcon("", ""))
        assertNotNull(stepIcon("totally.unknown.symbol", "asdfgh"))
    }

    // ── moodCounts / presenceMonth / journal pills — the V3 deferred items ──

    private fun checkIn(mood: String, at: String): JSONObject =
        JSONObject().put("mood", mood).put("created_at", at)

    @Test
    fun `moods are counted, never scored`() {
        val today = LocalDate.parse("2026-08-16")
        val counts = moodCounts(
            JSONArray()
                .put(checkIn("Anxious", "2026-08-16T09:00:00Z"))
                .put(checkIn("Anxious", "2026-08-15T09:00:00Z"))
                .put(checkIn("Tired", "2026-08-14T09:00:00Z"))
                .put(checkIn("Good", "2026-06-01T09:00:00Z")),   // outside the window
            days = 30, today = today,
        )
        // Heaviest first, and the out-of-window row is not counted.
        assertEquals(listOf("Anxious" to 2, "Tired" to 1), counts)
        // No ordering claim between moods is encoded anywhere: the only
        // number attached to a feeling is how often it appeared.
        assertTrue(counts.all { it.second > 0 })
    }

    @Test
    fun `a check-in with no mood or a broken stamp is skipped, not bucketed`() {
        val today = LocalDate.parse("2026-08-16")
        val counts = moodCounts(
            JSONArray()
                .put(checkIn("", "2026-08-16T09:00:00Z"))
                .put(checkIn("Low", "not-a-date"))
                .put(checkIn("Low", "2026-08-16T09:00:00Z")),
            days = 7, today = today,
        )
        assertEquals(listOf("Low" to 1), counts)
    }

    @Test
    fun `presence month counts days shown up and nothing else`() {
        val today = LocalDate.parse("2026-08-16")
        val month = presenceMonth(
            JSONArray()
                .put(checkIn("Good", "2026-08-16T09:00:00Z"))
                .put(checkIn("Good", "2026-08-16T20:00:00Z"))   // same day, twice
                .put(checkIn("Low", "2026-08-10T09:00:00Z")),
            today,
        )
        assertEquals(30, month.size)
        // Two distinct days, not three check-ins — presence, not volume.
        assertEquals(2, month.count { it })
        // Today is the last cell.
        assertTrue(month.last())
    }

    @Test
    fun `a journal mood pill shows only what the writer chose`() {
        // The tag round-trips through the existing `tags` field…
        assertEquals("calm", journalMoodTag(listOf("mood:calm")))
        assertNotNull(journalMoodLabelRes("calm"))
        // …an unknown or hand-written tag renders no pill rather than raw text…
        assertNull(journalMoodTag(listOf("mood:whatever")))
        assertNull(journalMoodTag(listOf("holiday")))
        assertNull(journalMoodLabelRes(null))
        // …and an entry with no feeling chosen simply has none.
        assertNull(journalMoodTag(emptyList()))
    }

    @Test
    fun `a first day is not a quiet day`() {
        assertNull(quietDaysSince(null, OffsetDateTime.parse("2026-08-16T10:00:00Z")))
        assertNull(quietDaysSince("garbage", OffsetDateTime.parse("2026-08-16T10:00:00Z")))
    }

    @Test
    fun `days count whole and honest`() {
        val now = OffsetDateTime.parse("2026-08-16T10:00:00Z")
        assertEquals(0, quietDaysSince("2026-08-16T08:00:00Z", now))
        assertEquals(3, quietDaysSince("2026-08-13T09:00:00Z", now))
    }
}
