package com.cerebrozen.app.net

/* The HR-analytics beats: fired on the right transitions, kind-only, and
 * never able to break the flow that emitted them. */

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.cerebrozen.app.ui.screens.ActionsStore

class EventsTest {

    private class SignedInStore : Session.Store {
        private val map = mutableMapOf<String, String>()
        override fun getString(key: String): String? = map[key] ?: "seed-refresh"
        override fun putString(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun keys(): Set<String> = map.keys
    }

    private val posts = mutableListOf<Pair<String, String>>()  // url -> body

    @Before
    fun reset() {
        posts.clear()
        Coach.reset()
        Events.launchIn = { block -> runBlocking { block() } }  // synchronous in tests
        Session.resetForTest(SignedInStore()) { url, method, body, _, _ ->
            if (method == "POST" && url.endsWith("/events/coaching")) {
                posts.add(url to (body ?: ""))
            }
            200 to """{"access_token":"t","refresh_token":"r2"}"""
        }
    }

    private fun kinds() = posts.map {
        Regex("\"kind\":\"(\\w+)\"").find(it.second)!!.groupValues[1]
    }

    @Test
    fun a_first_turn_reports_session_started_and_a_close_reports_completed() = runBlocking {
        val frames = mutableListOf(
            "data: {\"type\":\"done\",\"session_id\":\"s-1\"}\n\n",
            "data: {\"type\":\"done\",\"session_id\":\"s-1\",\"stage\":\"close\"}\n\n",
        )
        Session.sseExec = { _, _, _ -> 200 to ByteArrayInputStream(frames.removeAt(0).toByteArray()) }

        Coach.turn("hi") { }
        assertEquals(listOf(Events.SESSION_STARTED), kinds())

        Coach.turn("bye") { }  // later turn: no second 'started'; close fires 'completed'
        assertEquals(listOf(Events.SESSION_STARTED, Events.SESSION_COMPLETED), kinds())
    }

    @Test
    fun saving_and_completing_an_action_each_report_one_beat() {
        ActionsStore.add("evt-a1", "Book the 1:1")
        assertEquals(listOf(Events.ACTION_SAVED), kinds())
        ActionsStore.setStatus("evt-a1", "done")
        assertEquals(listOf(Events.ACTION_SAVED, Events.ACTION_COMPLETED), kinds())
        // Reopening is not a completion.
        ActionsStore.setStatus("evt-a1", "active")
        assertEquals(2, posts.size)
    }

    @Test
    fun a_failing_beat_never_breaks_the_caller() {
        Session.resetForTest(SignedInStore()) { _, _, _, _, _ -> throw java.io.IOException("down") }
        Events.launchIn = { block -> runBlocking { block() } }
        ActionsStore.add("evt-a2", "Still lands locally")  // must not throw
        assertTrue(ActionsStore.items.any { it.id == "evt-a2" })
    }
}
