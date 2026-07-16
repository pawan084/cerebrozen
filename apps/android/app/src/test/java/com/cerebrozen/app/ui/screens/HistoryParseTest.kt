package com.cerebrozen.app.ui.screens

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Restoring the thread after a process death.
 *
 * The app kept the conversation in memory only, so Android killing the process mid-session
 * lost it — and a coaching session spans a commute. Api.chat() sat orphaned pointing at a
 * reference route that was never ported; the engine's own /v1/sessions/resumable +
 * /{id}/history are what actually exist.
 *
 * chat_history is a WIRE CONTRACT: a list of one-key objects, {"user": {...}} or
 * {"bot": {...}}, each carrying `text`. These pin the shape and the two things that would
 * quietly show someone the wrong conversation.
 */
class HistoryParseTest {

    private val real = JSONObject(
        """
        {"converstation_status":"mid","session_id":"s1","chat_history":[
          {"user":{"text":"I keep putting off a hard conversation.","message_num":1,"hidden":false}},
          {"bot":{"text":"What feels like the hardest part?","message_num":2,"bot_name":"coaching_intake_agent"}}
        ]}
        """,
    )

    @Test
    fun `it restores the thread in order`() {
        val got = parseHistory(real)
        assertEquals(2, got.size)
        assertEquals("you" to "I keep putting off a hard conversation.", got[0])
        assertEquals("coach" to "What feels like the hardest part?", got[1])
    }

    @Test
    fun `hidden messages are never replayed`() {
        // The engine's own plumbing — system nudges the person never saw. Replaying them
        // would show someone a conversation they did not have.
        val body = JSONObject(
            """{"chat_history":[
                 {"user":{"text":"visible","hidden":false}},
                 {"user":{"text":"a nudge the person never saw","hidden":true}},
                 {"bot":{"text":"reply"}}
               ]}""",
        )
        assertEquals(listOf("you" to "visible", "coach" to "reply"), parseHistory(body))
    }

    @Test
    fun `an empty or missing history is not an error`() {
        assertTrue(parseHistory(JSONObject("{}")).isEmpty())
        assertTrue(parseHistory(JSONObject("""{"chat_history":[]}""")).isEmpty())
    }

    @Test
    fun `malformed rows are skipped, not rendered`() {
        val body = JSONObject(
            """{"chat_history":[
                 {"user":{"text":"good"}},
                 {"neither":{"text":"unknown speaker"}},
                 {"bot":{"text":""}},
                 {"bot":{}},
                 "nope"
               ]}""",
        )
        assertEquals(listOf("you" to "good"), parseHistory(body))
    }

    @Test
    fun `whitespace-only text is not a message`() {
        val body = JSONObject("""{"chat_history":[{"user":{"text":"   "}},{"bot":{"text":"real"}}]}""")
        assertEquals(listOf("coach" to "real"), parseHistory(body))
    }

    @Test
    fun `the speaker mapping matches what the screen renders`() {
        // "user"/"bot" on the wire; "you"/"coach" in the UI. A mismatch would draw the
        // person's own words as the coach's.
        val got = parseHistory(real)
        assertTrue(got.all { it.first == "you" || it.first == "coach" })
    }
}
