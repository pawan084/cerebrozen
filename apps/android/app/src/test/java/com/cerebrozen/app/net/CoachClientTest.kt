package com.cerebrozen.app.net

/* The Coach engine client against the real SSE frame shapes the engine emits
 * (data: {"type": "status"|"node"|"token"|"done", ...} — verified against
 * services/engine/app/routers/sessions.py at wiring time). */

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CoachClientTest {

    /** A store holding a refresh token, so ensureAccess() can rotate instead of
     * throwing "Signed out" — the http fake answers /auth/refresh below. */
    private class SignedInStore : Session.Store {
        private val map = mutableMapOf<String, String>()
        override fun getString(key: String): String? = map[key] ?: "seed-refresh"
        override fun putString(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun keys(): Set<String> = map.keys
    }

    private val urls = mutableListOf<String>()

    private fun install(vararg frames: String) {
        Session.resetForTest(SignedInStore()) { _, _, _, _, _ ->
            200 to """{"access_token":"t","refresh_token":"r2"}"""
        }
        val queue = frames.toMutableList()
        Session.sseExec = { url, _, _ ->
            urls.add(url)
            200 to ByteArrayInputStream(queue.removeAt(0).toByteArray())
        }
    }

    @Before
    fun reset() {
        urls.clear()
        Coach.reset()
    }

    @Test
    fun first_turn_starts_a_session_and_later_turns_reuse_it() = runBlocking {
        install(
            "data: {\"type\":\"token\",\"text\":\"Hel\"}\n\n" +
                "data: {\"type\":\"token\",\"text\":\"lo.\"}\n\n" +
                "data: {\"type\":\"done\",\"session_id\":\"s-42\",\"reply\":\"Hello.\"}\n\n",
            "data: {\"type\":\"done\",\"session_id\":\"s-42\"}\n\n",
        )
        val streamed = StringBuilder()
        Coach.turn("hi") { streamed.append(it) }
        assertEquals("Hello.", streamed.toString())
        assertEquals("s-42", Coach.sessionId)
        Coach.turn("more") { }
        assertTrue(urls[0].endsWith("/v1/sessions/start?stream=true"))
        assertTrue(urls[1].endsWith("/v1/sessions/s-42/turn?stream=true"))
    }

    @Test
    fun status_frames_surface_and_done_payload_carries_actions() = runBlocking {
        install(
            "data: {\"type\":\"status\",\"msg\":\"Running: core_coaching_agent\"}\n\n" +
                "data: {\"type\":\"token\",\"text\":\"ok\"}\n\n" +
                "data: {\"type\":\"done\",\"session_id\":\"s-1\"," +
                "\"actions\":[{\"action_id\":\"a1\",\"full_text\":\"Book the 1:1\"}]}\n\n",
        )
        var status = ""
        val done = Coach.turn("x", onStatus = { status = it }) { }
        assertEquals("Running: core_coaching_agent", status)
        val actions = done.payload.getJSONArray("actions")
        assertEquals("Book the 1:1", actions.getJSONObject(0).getString("full_text"))
    }

    @Test
    fun node_stages_are_captured_for_the_grounded_marker() = runBlocking {
        install(
            "data: {\"type\":\"node\",\"stage\":\"challenge_context_agent\"}\n\n" +
                "data: {\"type\":\"node\",\"stage\":\"learning_aid\"}\n\n" +
                "data: {\"type\":\"token\",\"text\":\"here\"}\n\n" +
                "data: {\"type\":\"done\",\"session_id\":\"s-g\"}\n\n",
        )
        val done = Coach.turn("teach me") { }
        assertTrue("learning_aid" in done.stages)
        assertTrue("challenge_context_agent" in done.stages)
    }

    @Test
    fun reset_clears_the_session_pointer() = runBlocking {
        install("data: {\"type\":\"done\",\"session_id\":\"s-9\"}\n\n")
        Coach.turn("hi") { }
        assertEquals("s-9", Coach.sessionId)
        Coach.reset()
        assertNull(Coach.sessionId)
        return@runBlocking
    }
}
