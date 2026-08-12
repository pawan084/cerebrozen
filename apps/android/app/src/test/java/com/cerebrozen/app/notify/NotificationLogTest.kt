package com.cerebrozen.app.notify

import com.cerebrozen.app.net.Session
import org.junit.Assert.assertEquals
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
        // Compared against Tab.Home.route, not the literal: this line used to read
        // "today", which is not a destination the graph has ever registered, so the
        // assertion pinned a crash rather than catching one. Tying it to the enum
        // means renaming the tab route fails here first.
        assertEquals(com.cerebrozen.app.ui.Tab.Home.route, NotificationLog.routeFor("checkin"))
    }

    @Test
    fun `every route a nudge can carry is one the app accepts from outside`() {
        // The inbox hands entry.route straight to navigate(), so anything this
        // map emits has to be a real destination. EXTERNAL_ROUTES is the set the
        // deeplink resolver already vets against — one list, checked twice,
        // rather than a second one here that would drift away from it.
        val kinds = listOf("checkin", "winddown", "sleep", "journal", "practice", "nudge", "")
        kinds.mapNotNull { NotificationLog.routeFor(it) }.forEach { route ->
            assertTrue(
                "nudge route '$route' is not a destination the app accepts",
                route in com.cerebrozen.app.ui.EXTERNAL_ROUTES,
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
