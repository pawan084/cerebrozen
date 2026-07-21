package com.cerebrozen.app.ui.screens

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The non-streaming reply path — i.e. the crisis takeover.
 *
 * Found on a physical device 2026-07-21, not by any test here: the engine detected a
 * disclosure, escalated it, and served the scripted helpline reply, and the app showed the
 * literal string "…". A crisis reply carries NO token frames (there is no model in that
 * path by design), so the whole message arrives in the `done` frame under
 * `response_to_user` — and the client was reading `reply` / `text`, which the engine has
 * never sent.
 *
 * Every other reply streams, so the fallback is only ever exercised by the one path that
 * must never fail. That is exactly why it needs its own test rather than a live turn.
 */
class CoachReplyFallbackTest {

    private val crisisReply =
        "I'm really glad you told me… please reach out immediately to your local emergency " +
            "number. I'm an AI coach — not a person, and not a crisis service."

    @Test
    fun `the engine's own key wins`() {
        val done = JSONObject().put("response_to_user", crisisReply)
        assertEquals(crisisReply, replyFallback(done))
    }

    @Test
    fun `a crisis takeover never renders as an ellipsis`() {
        // The exact shape the engine sends for a takeover: safety flag, no tokens.
        val done = JSONObject()
            .put("safety_flag", "crisis")
            .put("response_to_user", crisisReply)
        val text = replyFallback(done).ifBlank { "…" }
        assertEquals(crisisReply, text)
    }

    @Test
    fun `older keys still resolve`() {
        assertEquals("hi", replyFallback(JSONObject().put("reply", "hi")))
        assertEquals("hi", replyFallback(JSONObject().put("text", "hi")))
        assertEquals("hi", replyFallback(JSONObject().put("greeting", "hi")))
    }

    @Test
    fun `response_to_user is preferred over the legacy keys`() {
        val done = JSONObject().put("reply", "legacy").put("response_to_user", "current")
        assertEquals("current", replyFallback(done))
    }

    @Test
    fun `an empty or absent payload is blank, never a crash`() {
        assertEquals("", replyFallback(null))
        assertEquals("", replyFallback(JSONObject()))
        assertEquals("", replyFallback(JSONObject().put("response_to_user", "")))
    }
}
