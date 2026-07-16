package com.cerebrozen.app.ui.screens

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Pattern dashboard's pure helpers (PatternScreen.kt). The composable itself is
 * outside the gated scope like every screen, but the parse IS logic and it guards the one
 * thing this surface must never do: render a claim about a person with no basis under it.
 *
 * The screen was orphaned by the B2C strip — Api.patterns()/deleteMemory() sat unused,
 * pointing at reference routes that were never ported. These pin the contract now that the
 * engine actually serves it.
 */
class PatternParseTest {

    private fun body(json: String) = JSONObject(json)

    // ── parsePatterns ────────────────────────────────────────────────────────

    @Test
    fun `it reads statements and their basis`() {
        val got = parsePatterns(body("""
            {"patterns":[{"statement":"Mornings tend to be your hardest time of day.",
                          "basis":"6 of your 11 difficult check-ins landed there"}]}
        """))
        assertEquals(1, got.size)
        assertEquals("Mornings tend to be your hardest time of day.", got[0].statement)
        assertEquals("6 of your 11 difficult check-ins landed there", got[0].basis)
    }

    @Test
    fun `a claim with no basis is dropped, never rendered`() {
        // THE rule. Unattributed, "Mornings are your hardest time" is a horoscope, and the
        // person has no way to judge whether the coach knows something or is guessing.
        val got = parsePatterns(body("""{"patterns":[{"statement":"You seem stressed.","basis":""}]}"""))
        assertTrue(got.isEmpty())
    }

    @Test
    fun `a basis with no claim is dropped too`() {
        assertEquals(0, parsePatterns(body("""{"patterns":[{"statement":"  ","basis":"3 of 4"}]}""")).size)
    }

    @Test
    fun `missing or malformed rows do not blank the screen`() {
        assertEquals(0, parsePatterns(body("{}")).size)
        assertEquals(0, parsePatterns(body("""{"patterns":[]}""")).size)
        assertEquals(0, parsePatterns(body("""{"patterns":["nope", null]}""")).size)
    }

    @Test
    fun `one bad row does not lose the good ones`() {
        val got = parsePatterns(body("""
            {"patterns":[{"statement":"ok","basis":"2 of 3"},
                         {"statement":"no basis","basis":""},
                         {"statement":"also ok","basis":"4 of 5"}]}
        """))
        assertEquals(listOf("ok", "also ok"), got.map { it.statement })
    }

    // ── sourceLabels: the honest complement to the delete button ─────────────

    @Test
    fun `it names only the categories that were read`() {
        val got = sourceLabels(body("""
            {"sources":{"mood_history":true,"journal_memory":false,"sleep_history":true}}
        """))
        assertEquals(listOf("check-ins", "sleep"), got)
    }

    @Test
    fun `declining everything names nothing`() {
        val got = sourceLabels(body("""
            {"sources":{"mood_history":false,"journal_memory":false,"sleep_history":false}}
        """))
        assertTrue(got.isEmpty())
    }

    @Test
    fun `a response without sources says nothing rather than guessing`() {
        assertTrue(sourceLabels(body("{}")).isEmpty())
    }

    // ── deletedCounts: storage vocabulary -> a person's words ────────────────

    @Test
    fun `it folds the engine's locations into messages and insights`() {
        // The copy says "%1$d messages and %2$d insights"; the engine reports per-store
        // counts. Map rather than reword — the string is already translated.
        val (messages, insights) = deletedCounts(body("""
            {"deleted":{"transcripts":2,"checkpoints":2,"checkpoint_writes":1,
                        "agentic_context":1,"dynamic_vars":1}}
        """))
        assertEquals(5, messages)
        assertEquals(2, insights)
    }

    @Test
    fun `a report with nothing deleted reads as zero, not a crash`() {
        assertEquals(0 to 0, deletedCounts(body("{}")))
        assertEquals(0 to 0, deletedCounts(body("""{"deleted":{}}""")))
    }

    @Test
    fun `the wellness store is never counted as something the coach forgot`() {
        // It is not in the wipe at all (erasure._MEMORY_LABELS), so even if a future
        // report mentioned it, it must not be reported to the person as forgotten.
        val (messages, insights) = deletedCounts(body("""{"deleted":{"wellness":9}}"""))
        assertEquals(0, messages)
        assertEquals(0, insights)
    }
}
