package com.cerebrozen.app.net

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The offline write queue.
 *
 * Before it existed, a mood logged with no signal simply failed — the POST
 * threw, a toast said something went wrong, and the tap was gone. Reads already
 * had the encrypted response cache; writes, the half the user actually
 * authored, had nothing.
 */
class OutboxTest {

    private class FakeStore : Session.Store {
        val map = mutableMapOf<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun keys() = map.keys.toSet()
    }

    private lateinit var store: FakeStore
    private val sent = mutableListOf<Triple<String, String?, String?>>()

    private val tokens = """{"access_token":"a1","refresh_token":"r2"}"""

    /**
     * Install a transport that records requests and answers by rule.
     *
     * Token refresh always succeeds here: these tests are about the queue, and
     * a signed-out session would fail every write for an unrelated reason.
     */
    private fun transport(answer: (String) -> Pair<Int, String>) {
        Session.resetForTest(store) { url, _, body, _, _, headers ->
            if (url.endsWith("/auth/refresh")) {
                200 to tokens
            } else {
                sent += Triple(url, body, headers["Idempotency-Key"])
                answer(url)
            }
        }
    }

    @Before
    fun setUp() {
        store = FakeStore().apply { map["refresh_token"] = "r1" }
        sent.clear()
        transport { 200 to """{"id":"server-1"}""" }
        Outbox.clear()
    }

    private fun mood(name: String = "Good") = JSONObject().put("mood", name)

    // ── Sending ──────────────────────────────────────────────────────────
    @Test
    fun a_write_that_goes_through_is_not_queued() = runTest {
        val result = Outbox.send("/moods", mood())

        assertEquals("server-1", result?.optString("id"))
        assertEquals("nothing should be left waiting", 0, Outbox.count())
    }

    @Test
    fun every_send_carries_an_idempotency_key() = runTest {
        Outbox.send("/moods", mood())

        assertNotNull("the server cannot de-duplicate a retry without one", sent.last().third)
    }

    @Test
    fun a_network_failure_queues_the_write_instead_of_losing_it() = runTest {
        transport { throw java.io.IOException("no route to host") }

        val result = Outbox.send("/moods", mood("Tired"))

        assertNull("a queued write has no server response yet", result)
        assertEquals(1, Outbox.count())
        assertEquals("Tired", Outbox.pending().first().body.optString("mood"))
    }

    @Test
    fun a_server_error_queues_but_a_refusal_does_not() = runTest {
        transport { 503 to """{"detail":"down"}""" }
        Outbox.send("/moods", mood())
        assertEquals("a 5xx is worth retrying", 1, Outbox.count())

        Outbox.clear()
        transport { 400 to """{"detail":"bad body"}""" }
        val error = runCatching { Outbox.send("/moods", mood()) }.exceptionOrNull()

        assertTrue("a refusal must surface, not hide behind 'will sync'", error is Session.ApiException)
        assertEquals(0, Outbox.count())
    }

    // ── Draining ─────────────────────────────────────────────────────────
    @Test
    fun draining_an_empty_queue_costs_nothing() = runTest {
        val result = Outbox.drain()

        assertEquals(Outbox.DrainResult(0, 0, 0), result)
        assertTrue("an empty queue must not touch the network", sent.isEmpty())
    }

    @Test
    fun a_drain_sends_everything_and_empties_the_queue() = runTest {
        transport { throw java.io.IOException("offline") }
        Outbox.send("/moods", mood("Low"))
        Outbox.send("/journal", JSONObject().put("title", "Late"))
        assertEquals(2, Outbox.count())

        sent.clear()
        transport { 200 to """{"id":"x"}""" }
        val result = Outbox.drain()

        assertEquals(2, result.sent)
        assertEquals(0, Outbox.count())
        assertTrue(
            "order is the order the user wrote them",
            sent.first().first.endsWith("/moods") && sent.last().first.endsWith("/journal"),
        )
    }

    @Test
    fun a_retry_reuses_the_key_it_was_queued_with() = runTest {
        transport { throw java.io.IOException("offline") }
        Outbox.send("/moods", mood())
        val queuedKey = Outbox.pending().first().key

        sent.clear()
        transport { 200 to "{}" }
        Outbox.drain()

        assertEquals(
            "a key minted at send time would let a crashed retry create a second check-in",
            queuedKey, sent.single().third,
        )
    }

    @Test
    fun a_drain_that_is_still_offline_keeps_everything_in_order() = runTest {
        transport { throw java.io.IOException("offline") }
        Outbox.send("/moods", mood("A"))
        Outbox.send("/moods", mood("B"))

        val result = Outbox.drain()

        assertEquals(0, result.sent)
        assertEquals(2, result.remaining)
        assertEquals(listOf("A", "B"), Outbox.pending().map { it.body.optString("mood") })
    }

    @Test
    fun one_stuck_item_stops_the_drain_rather_than_reordering_the_day() = runTest {
        transport { throw java.io.IOException("offline") }
        Outbox.send("/moods", mood("first"))
        Outbox.send("/journal", JSONObject().put("title", "second"))

        // The first item still fails; the second would succeed — but sending it
        // now would put the evening's entry before the morning's.
        transport { url -> if (url.endsWith("/moods")) throw java.io.IOException("still") else 200 to "{}" }
        val result = Outbox.drain()

        assertEquals(0, result.sent)
        assertEquals(2, result.remaining)
    }

    @Test
    fun a_refused_item_is_dropped_so_the_queue_can_move_on() = runTest {
        transport { throw java.io.IOException("offline") }
        Outbox.send("/moods", mood("bad"))
        Outbox.send("/moods", mood("good"))

        var call = 0
        transport {
            call++
            if (call == 1) 422 to """{"detail":"unprocessable"}""" else 200 to "{}"
        }
        val result = Outbox.drain()

        assertEquals(1, result.sent)
        assertEquals("a permanently refused write must not block the queue forever", 1, result.dropped)
        assertEquals(0, Outbox.count())
    }

    @Test
    fun a_conflict_counts_as_sent_because_the_write_already_landed() = runTest {
        transport { throw java.io.IOException("offline") }
        Outbox.send("/moods", mood())

        transport { 409 to """{"detail":"Idempotency-Key was already used"}""" }
        val result = Outbox.drain()

        assertEquals("409 is the replay working, not a failure", 1, result.sent)
        assertEquals(0, result.dropped)
        assertEquals(0, Outbox.count())
    }

    // ── Undo ─────────────────────────────────────────────────────────────
    @Test
    fun undo_pulls_a_queued_write_back_out() = runTest {
        transport { throw java.io.IOException("offline") }
        Outbox.send("/moods", mood("mistake"))

        assertTrue(Outbox.dropLast("/moods"))
        assertEquals(
            "an un-undoable offline mis-tap would sync the mistake back later",
            0, Outbox.count(),
        )
    }

    @Test
    fun undo_takes_the_most_recent_and_only_from_that_path() = runTest {
        transport { throw java.io.IOException("offline") }
        Outbox.send("/moods", mood("keep"))
        Outbox.send("/journal", JSONObject().put("title", "keep too"))
        Outbox.send("/moods", mood("undo me"))

        assertTrue(Outbox.dropLast("/moods"))
        assertEquals(listOf("keep", "keep too"), Outbox.pending().map {
            it.body.optString("mood").ifBlank { it.body.optString("title") }
        })
    }

    @Test
    fun undo_with_nothing_queued_says_so() = runTest {
        assertFalse(Outbox.dropLast("/moods"))
    }

    // ── Durability ───────────────────────────────────────────────────────
    @Test
    fun the_queue_survives_a_process_restart() = runTest {
        transport { throw java.io.IOException("offline") }
        Outbox.send("/moods", mood("written on the metro"))
        val persisted = store.map.toMap()

        // A new process: fresh state, same storage.
        store = FakeStore().apply { map.putAll(persisted) }
        transport { 200 to "{}" }

        assertEquals(1, Outbox.count())
        assertEquals("written on the metro", Outbox.pending().first().body.optString("mood"))
    }

    @Test
    fun a_corrupt_queue_is_treated_as_empty_rather_than_crashing() = runTest {
        store.map["outbox"] = "not json at all"

        assertEquals(emptyList<Outbox.Item>(), Outbox.pending())
    }

    @Test
    fun malformed_entries_are_skipped_not_replayed_blindly() = runTest {
        store.map["outbox"] = """[{"path":"/moods"},{"path":"/moods","body":{"mood":"ok"},"key":"k1"}]"""

        val pending = Outbox.pending()

        assertEquals("an item with no body has nothing to send", 1, pending.size)
        assertEquals("k1", pending.first().key)
    }

    @Test
    fun the_queue_is_bounded() = runTest {
        transport { throw java.io.IOException("offline") }
        repeat(Outbox.MAX_ITEMS + 5) { Outbox.send("/moods", mood("m$it")) }

        assertEquals(Outbox.MAX_ITEMS, Outbox.count())
        assertEquals(
            "trimming keeps the newest — the ones the user can still see",
            "m${Outbox.MAX_ITEMS + 4}", Outbox.pending().last().body.optString("mood"),
        )
    }

    @Test
    fun retryable_codes() {
        assertTrue(Outbox.retryable(500))
        assertTrue(Outbox.retryable(503))
        assertTrue(Outbox.retryable(408))
        assertTrue("a throttled write should wait, not vanish", Outbox.retryable(429))
        assertFalse(Outbox.retryable(400))
        assertFalse(Outbox.retryable(404))
        assertFalse("409 means it already landed", Outbox.retryable(409))
    }
}
