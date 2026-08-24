package com.cerebrozen.app.net

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The queue under a flapping network (WC-189).
 *
 * The offline queue's existing tests each pull one lever — one failure, one
 * drain, one verdict. Real networks on the metro don't fail once: they come
 * and go mid-drain, an attempt lands but its RESPONSE is lost, and the app is
 * relaunched between flaps. Each scenario here scripts one of those weathers
 * over the REAL queue and transport seam and asserts the only three things
 * that matter: nothing the user wrote is lost, nothing lands twice (the
 * idempotency key survives every retry), and order is their order.
 *
 * Also pins the [Outbox.scheduleSync] seam: every enqueue must ask for a
 * background drain, because the enqueue may be the last thing this process
 * ever does (WC-190).
 */
class OutboxFlappingTest {

    private class FakeStore : Session.Store {
        val map = mutableMapOf<String, String>()
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun keys() = map.keys.toSet()
    }

    private lateinit var store: FakeStore
    private val sent = mutableListOf<Pair<String, String?>>()   // (body, idempotency key)
    private var networkUp = true
    private var scheduled = 0

    private val tokens = """{"access_token":"a1","refresh_token":"r2"}"""

    @Before
    fun setUp() {
        store = FakeStore().apply { map["refresh_token"] = "r1" }
        sent.clear()
        networkUp = true
        scheduled = 0
        Session.resetForTest(store) { url, _, body, _, _, headers ->
            if (url.endsWith("/auth/refresh")) {
                200 to tokens
            } else {
                if (!networkUp) throw IOException("airplane mode")
                sent += (body ?: "") to headers["Idempotency-Key"]
                200 to """{"id":"server"}"""
            }
        }
        Outbox.clear()
        Outbox.scheduleSync = { scheduled++ }
    }

    @After
    fun tearDown() {
        Outbox.scheduleSync = {}
    }

    private fun mood(n: Int) = JSONObject().put("mood", "m$n")

    @Test
    fun `flap - queue - flap - drain loses nothing and keeps order`() = runTest {
        networkUp = false
        Outbox.send("/moods", mood(1))
        Outbox.send("/moods", mood(2))
        networkUp = true
        Outbox.send("/moods", mood(3))          // went straight through, ahead of the queue
        networkUp = false
        Outbox.send("/moods", mood(4))
        assertEquals(3, Outbox.count())

        // Two dead drains while the network is down cost nothing and drop nothing.
        repeat(2) { assertEquals(0, Outbox.drain().sent) }
        assertEquals(3, Outbox.count())

        networkUp = true
        val result = Outbox.drain()
        assertEquals(3, result.sent)
        assertEquals(0, result.remaining)
        // m3 sent live during the up-window; the queue then replays 1, 2, 4 in
        // the order they were written.
        assertEquals(listOf("m3", "m1", "m2", "m4"), sent.map { JSONObject(it.first).getString("mood") })
    }

    @Test
    fun `the network dying mid-drain keeps the tail, and the retry reuses keys`() = runTest {
        networkUp = false
        (1..3).forEach { Outbox.send("/moods", mood(it)) }
        val keysBefore = Outbox.pending().map { it.key }

        // Comes up for exactly one item, then dies again.
        var budget = 1
        Session.resetForTest(store) { url, _, body, _, _, headers ->
            if (url.endsWith("/auth/refresh")) {
                200 to tokens
            } else {
                if (budget <= 0) throw IOException("flapped mid-drain")
                budget--
                sent += (body ?: "") to headers["Idempotency-Key"]
                200 to """{"id":"server"}"""
            }
        }
        var r = Outbox.drain()
        assertEquals(1, r.sent)
        assertEquals(2, r.remaining)

        budget = 10
        r = Outbox.drain()
        assertEquals(2, r.sent)
        assertEquals(0, r.remaining)
        // The keys that eventually landed are the keys minted at enqueue — a
        // retry never re-keys, or the server-side replay guard means nothing.
        assertEquals(keysBefore, sent.map { it.second })
        assertEquals(listOf("m1", "m2", "m3"), sent.map { JSONObject(it.first).getString("mood") })
    }

    @Test
    fun `an attempt whose response was lost lands exactly once`() = runTest {
        networkUp = false
        Outbox.send("/moods", mood(1))
        // The write reaches the server but the response dies on the way back —
        // the client cannot tell this from "never arrived". The server answers
        // the replay 409 (key already used), which the queue counts as SENT.
        var arrived = 0
        Session.resetForTest(store) { url, _, body, _, _, headers ->
            if (url.endsWith("/auth/refresh")) {
                200 to tokens
            } else {
                arrived++
                if (arrived == 1) {
                    sent += (body ?: "") to headers["Idempotency-Key"]
                    throw IOException("response lost after the server committed")
                }
                409 to """{"detail":"replay"}"""
            }
        }
        assertEquals(0, Outbox.drain().sent)     // the lost-response attempt stops the drain
        assertEquals(1, Outbox.count())          // still queued: the client couldn't know

        val r = Outbox.drain()                   // replay meets 409 = already landed
        assertEquals(1, r.sent)
        assertEquals(0, r.remaining)
        assertEquals(1, sent.size)               // and the SERVER only ever committed once
    }

    @Test
    fun `a relaunch between flaps changes nothing - the queue is storage, not memory`() = runTest {
        networkUp = false
        Outbox.send("/moods", mood(1))
        val key = Outbox.pending().single().key

        // "Relaunch": Session re-inits over the same persisted store; nothing
        // in-memory survives except what was written down.
        Session.resetForTest(store) { url, _, body, _, _, headers ->
            if (url.endsWith("/auth/refresh")) 200 to tokens
            else { sent += (body ?: "") to headers["Idempotency-Key"]; 200 to """{"id":"s"}""" }
        }
        assertEquals(1, Outbox.count())
        val r = Outbox.drain()
        assertEquals(1, r.sent)
        assertEquals(key, sent.single().second)
    }

    @Test
    fun `every offline enqueue asks for a background drain`() = runTest {
        networkUp = false
        Outbox.send("/moods", mood(1))
        Outbox.send("/moods", mood(2))
        assertEquals(2, scheduled)
        networkUp = true
        assertNull(null)  // (structure) the live send below must NOT schedule:
        Outbox.send("/moods", mood(3))
        assertEquals(2, scheduled)
        assertTrue(Outbox.drain().sent >= 2)
    }
}
