package com.cerebrozen.app.net

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * The DPDP telemetry posture (owner decision 2026-07-13): Analytics.track is
 * SILENT until the consent unlock, then governed by the opt-out toggle, and
 * always anonymous (random install id, no auth). Uses the Session store/http
 * seams — no Android, no network.
 */
class AnalyticsTest {

    private class FakeStore(vararg init: Pair<String, String>) : Session.Store {
        val m = mutableMapOf(*init)
        override fun getString(key: String) = m[key]
        override fun putString(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
        override fun keys() = m.keys.toSet()
    }

    /** Collects /events bodies; counts down once per post. */
    private class EventSink {
        val bodies = CopyOnWriteArrayList<String>()
        var latch = CountDownLatch(1)
        fun install(store: FakeStore) {
            Session.resetForTest(store) { url, _, body, _, auth ->
                assertTrue(url.endsWith("/events"))
                assertNull("events must never carry a bearer token", auth)
                bodies.add(body!!)
                latch.countDown()
                202 to """{"accepted":1}"""
            }
        }
        fun await() = latch.await(5, TimeUnit.SECONDS)
        /** Short window for the MUST-NOT-fire cases (a fire would land ~instantly). */
        fun awaitNone() = latch.await(300, TimeUnit.MILLISECONDS)
    }

    @Test
    fun track_is_silent_before_the_consent_unlock() {
        val sink = EventSink()
        sink.install(FakeStore())
        assertFalse("fresh install starts locked", Analytics.unlocked)
        Analytics.track("app_open")
        assertFalse("no telemetry before consent (DPDP)", sink.awaitNone())
        assertTrue(sink.bodies.isEmpty())
    }

    @Test
    fun unlock_then_track_posts_the_event_with_a_stable_anon_id() {
        val store = FakeStore()
        val sink = EventSink()
        sink.install(store)
        Analytics.unlock()
        assertTrue(Analytics.unlocked)
        assertEquals("true", store.getString("analytics_unlocked"))

        Analytics.track("onboarding_step", "welcome")
        assertTrue("event must reach /events", sink.await())
        val first = JSONObject(sink.bodies[0])
        val anonId = first.getString("anon_id")
        assertTrue(anonId.isNotBlank())
        assertEquals("android", first.getString("source"))
        val event = first.getJSONArray("events").getJSONObject(0)
        assertEquals("onboarding_step", event.getString("name"))
        assertEquals("welcome", event.getString("step"))
        assertEquals("the anon id must persist for the install", anonId, store.getString("anon_id"))

        sink.latch = CountDownLatch(1)
        Analytics.track("app_open")
        assertTrue(sink.await())
        assertEquals("same id across events — one install, one row key",
            anonId, JSONObject(sink.bodies[1]).getString("anon_id"))
    }

    @Test
    fun opt_out_gates_tracking_even_when_unlocked() {
        val store = FakeStore("analytics_unlocked" to "true", "usage_stats_on" to "false")
        val sink = EventSink()
        sink.install(store)
        assertFalse(Analytics.enabled)
        Analytics.track("app_open")
        assertFalse("opted out → nothing may be sent", sink.awaitNone())

        Analytics.enabled = true
        sink.latch = CountDownLatch(1)
        Analytics.track("app_open")
        assertTrue("re-enabling resumes anonymous counts", sink.await())
    }

    @Test
    fun unknown_funnel_steps_fall_back_to_lowercase() {
        // Known steps are pinned in SessionTest; this is the unknown-step fallback.
        assertEquals("somenewstep", funnelStepName("SomeNewStep"))
    }

    @Test
    fun track_swallows_transport_failures() {
        val store = FakeStore("analytics_unlocked" to "true")
        val latch = CountDownLatch(1)
        Session.resetForTest(store) { _, _, _, _, _ ->
            latch.countDown()
            throw java.io.IOException("offline")
        }
        Analytics.track("app_open")   // must not throw or surface anything
        assertTrue(latch.await(5, TimeUnit.SECONDS))
    }
}
