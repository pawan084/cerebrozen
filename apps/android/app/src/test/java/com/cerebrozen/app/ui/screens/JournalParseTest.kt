package com.cerebrozen.app.ui.screens

import java.time.ZoneId
import java.util.Locale
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The journal read view's pure logic.
 *
 * Api.createJournal has been firing from the breathing/reset tools all along, so people
 * have entries — Api.journal() simply had no caller. You could write a journal on this
 * phone and never see it again.
 */
class JournalParseTest {

    private fun rows(vararg json: String) = JSONArray(json.joinToString(",", "[", "]"))

    private fun entry(id: String, body: String, ts: String, title: String = "") =
        """{"id":"$id","title":"$title","body":"$body","ts":"$ts"}"""

    @Test
    fun `it reads the engine's entries`() {
        val got = parseJournal(rows(entry("j1", "my own words", "2026-07-14T09:00:00+00:00", "A title")))
        assertEquals(1, got.size)
        assertEquals("my own words", got[0].body)
        assertEquals("A title", got[0].title)
    }

    @Test
    fun `newest first`() {
        // The engine stores oldest-first and hands the list back unchanged. A history that
        // opens on something you wrote weeks ago is not a history anybody reads.
        val got = parseJournal(rows(
            entry("old", "first thing I wrote", "2026-07-01T09:00:00+00:00"),
            entry("new", "what I wrote today", "2026-07-14T09:00:00+00:00"),
        ))
        assertEquals(listOf("what I wrote today", "first thing I wrote"), got.map { it.body })
    }

    @Test
    fun `an empty body is not an entry`() {
        val got = parseJournal(rows(entry("j1", "", "2026-07-14T09:00:00+00:00"), entry("j2", "real", "2026-07-15T09:00:00+00:00")))
        assertEquals(listOf("real"), got.map { it.body })
    }

    @Test
    fun `malformed rows do not blank the history`() {
        assertTrue(parseJournal(JSONArray()).isEmpty())
        assertEquals(1, parseJournal(rows("null", """"nope"""", entry("j", "kept", "2026-07-14T09:00:00Z"))).size)
    }

    @Test
    fun `it accepts either id field the store might use`() {
        val got = parseJournal(JSONArray("""[{"entry_id":"e9","body":"b","ts":"2026-07-14T09:00:00Z"}]"""))
        assertEquals("e9", got[0].id)
    }

    // ── dates ────────────────────────────────────────────────────────────────

    @Test
    fun `it renders the engine's offset format`() {
        // Same trap as the check-in ring: the engine emits +00:00, which Instant.parse
        // rejects. A blank date on every entry, with no error.
        assertEquals("14 Jul 2026", entryDate("2026-07-14T09:00:00.123456+00:00", ZoneId.of("UTC"), Locale.UK))
    }

    @Test
    fun `a late entry belongs to the day the person had`() {
        // 19:30 UTC is 01:00 the next day in Kolkata — their day, not UTC's.
        assertEquals("15 Jul 2026", entryDate("2026-07-14T19:30:00+00:00", ZoneId.of("Asia/Kolkata"), Locale.UK))
    }

    @Test
    fun `an unparseable stamp renders blank rather than crashing`() {
        assertEquals("", entryDate("junk", ZoneId.of("UTC"), Locale.UK))
        assertEquals("", entryDate("", ZoneId.of("UTC"), Locale.UK))
    }
}
