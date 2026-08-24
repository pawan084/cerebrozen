package com.cerebrozen.app.ui.screens

import com.cerebrozen.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The tool-name → label contract ([oracleToolLabelRes]).
 *
 * The names are wire values from `backend/app/agent/tools.py` `TOOLS`,
 * hand-duplicated like every cross-stack contract here. Two failure modes:
 * a backend rename silently landing every frame in the generic bucket, and —
 * the one that leaks — an unknown name reaching the screen as snake_case.
 */
class OracleToolsTest {

    @Test
    fun `every backend tool has its own label`() {
        assertEquals(R.string.oracle_tool_weekly_insights, oracleToolLabelRes("get_weekly_insights"))
        assertEquals(R.string.oracle_tool_suggest_activity, oracleToolLabelRes("suggest_activity"))
        assertEquals(R.string.oracle_tool_log_mood, oracleToolLabelRes("log_mood"))
        assertEquals(R.string.oracle_tool_save_journal, oracleToolLabelRes("save_journal"))
        assertEquals(R.string.oracle_tool_log_sleep, oracleToolLabelRes("log_sleep"))
    }

    @Test
    fun `an unknown tool degrades to the generic label, never to its raw name`() {
        assertEquals(R.string.oracle_tool_generic, oracleToolLabelRes("brand_new_tool"))
        assertEquals(R.string.oracle_tool_generic, oracleToolLabelRes(""))
    }
}

/** The display-only period healing in [stripMarkdownLite] (2026-08-24 review). */
class SentenceSpacingTest {
    @Test
    fun `a missing space after a sentence period is healed`() {
        org.junit.Assert.assertEquals(
            "explore these feelings further. Writing down your thoughts",
            stripMarkdownLite("explore these feelings further.Writing down your thoughts"),
        )
    }

    @Test
    fun `decimals acronyms and urls are untouched`() {
        for (s in listOf("rate it 2.5 out of 5", "the U.S. average", "see cerebrozen.in/support", "v1.2.Final")) {
            // v1.2.Final: digit before the period — no heal, by design.
            org.junit.Assert.assertEquals(s, stripMarkdownLite(s))
        }
    }
}
