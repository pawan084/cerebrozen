package com.cerebrozen.app.notify

import com.cerebrozen.app.net.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The delivered-notification log (TOD-06's evidence).
 *
 * Pure store logic on the Session seam — no Android, no notification manager.
 * What these pin is the behaviour the screen depends on being true: newest
 * first, a hard cap, dismissal by instant rather than index, and a corrupt
 * value degrading to "empty" instead of throwing on a screen the user opened
 * to answer "did my reminder fire".
 */
class NotificationLogTest {

    private class FakeStore(vararg init: Pair<String, String>) : Session.Store {
        val m = mutableMapOf(*init)
        override fun getString(key: String) = m[key]
        override fun putString(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
        override fun keys() = m.keys.toSet()
    }

    @Before
    fun setUp() {
        Session.resetForTest(FakeStore()) { _, _, _, _, _, _ -> 200 to "{}" }
    }

    @Test
    fun `records newest first`() {
        NotificationLog.record("First", "b1", "2026-08-06T09:00Z", "checkin")
        NotificationLog.record("Second", "b2", "2026-08-06T21:00Z", "winddown")

        val all = NotificationLog.all()
        assertEquals(listOf("Second", "First"), all.map { it.title })
    }

    @Test
    fun `blank title is not recorded`() {
        NotificationLog.record("   ", "body", "2026-08-06T09:00Z", "checkin")

        // A row with no title renders as an empty card and says nothing.
        assertTrue(NotificationLog.all().isEmpty())
    }

    @Test
    fun `caps the stored history`() {
        repeat(40) { i ->
            NotificationLog.record("N$i", "b", "2026-08-06T09:0${i % 10}:00Z", "checkin")
        }

        val all = NotificationLog.all()
        assertEquals(30, all.size)
        // The cap drops the OLDEST rows, so the most recent write survives.
        assertEquals("N39", all.first().title)
    }

    @Test
    fun `remove matches on instant not index`() {
        NotificationLog.record("A", "b", "2026-08-06T09:00Z", "checkin")
        NotificationLog.record("B", "b", "2026-08-06T10:00Z", "checkin")
        NotificationLog.record("C", "b", "2026-08-06T11:00Z", "checkin")

        val kept = NotificationLog.remove("2026-08-06T10:00Z")

        assertEquals(listOf("C", "A"), kept.map { it.title })
        assertEquals(listOf("C", "A"), NotificationLog.all().map { it.title })
    }

    @Test
    fun `corrupt value reads as empty rather than throwing`() {
        Session.resetForTest(FakeStore("notification_log" to "{not json")) { _, _, _, _, _, _ -> 200 to "{}" }

        assertTrue(NotificationLog.all().isEmpty())
    }

    @Test
    fun `clear empties the log`() {
        NotificationLog.record("A", "b", "2026-08-06T09:00Z", "checkin")
        NotificationLog.clear()

        assertTrue(NotificationLog.all().isEmpty())
    }

    @Test
    fun `route is null for kinds with nowhere to go`() {
        // A dead "Open" button is worse than no button, so the screen only draws
        // one when routeFor resolved to something.
        assertNull(NotificationLog.routeFor("unknown-kind"))
        assertEquals("sleep", NotificationLog.routeFor("winddown"))
        // "home", not "today". This assertion used to say "today" and so PINNED
        // a crash: navigate("today") matches no destination and throws.
        assertEquals("home", NotificationLog.routeFor("checkin"))
    }

    @Test
    fun `every route a nudge can open is a real destination`() {
        // The bug this replaces was a single wrong string, so asserting one
        // string would only pin the next one. The Today tab deliberately kept
        // the route `home` through the five-tab rename (Tab.Home in
        // CereBroApp.kt) — a nudge pointing at the LABEL rather than the route
        // is exactly how "today" got here.
        //
        // Hand-duplicated against the nav graph; navigation-compose cannot be
        // built in a JVM unit test, so this is the closest pin available.
        val graphRoutes = setOf(
            "home", "explore", "talk", "journal", "you",
            "sleep", "toolkit", "insights", "trends", "plan", "goals",
            "programs", "crisis", "safetyplan", "winddown", "notifications",
        )
        for (kind in listOf("checkin", "winddown", "sleep", "journal", "practice")) {
            val route = NotificationLog.routeFor(kind)
            assertNotNull("$kind should resolve somewhere", route)
            assertTrue(
                "routeFor(\"$kind\") = \"$route\" is not a route in the nav graph",
                route in graphRoutes,
            )
        }
    }

    @Test
    fun `round trip preserves the route`() {
        NotificationLog.record("A", "b", "2026-08-06T09:00Z", "winddown", route = "sleep")

        assertEquals("sleep", NotificationLog.all().single().route)
    }

    @Test
    fun `absent route round trips as null`() {
        NotificationLog.record("A", "b", "2026-08-06T09:00Z", "nudge", route = null)

        assertNull(NotificationLog.all().single().route)
    }
}
