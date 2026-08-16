package com.cerebrozen.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationChromeTest {
    @Test
    fun bottomBarOnlyAppearsOnRootTabsAndTheirContentRooms() {
        listOf("home", "sleep", "talk", "journal").forEach {
            assertTrue(it, shouldShowBottomBar(it))
        }
        // V3-a: the settings family is a full-screen push behind the gear now —
        // a pill under a settings room would light nothing.
        listOf("you", "reminders", "tools", "games", "mindfulgame/{gameId}", "guidedimagery", "player", "explore", null).forEach {
            assertFalse(it ?: "null", shouldShowBottomBar(it))
        }
    }

    @Test
    fun theTabsAreTheV3Three() {
        // V3-a, owner-approved 2026-08-16 (companion-first prototype): Home ·
        // Chat · Sleep. The conversation is the flagship; Journal became a chat
        // tool + a room doored from Home; You lives behind the top-bar gear.
        // Pinned by ROUTE because the labels are localized; the constants keep
        // the `home`/`talk` routes so deeplinks and saved state survive.
        assertEquals(listOf("home", "talk", "sleep"), Tab.entries.map { it.route })
    }

    @Test
    fun theAppOpensOnTheConversation() {
        // "Chat first" is the ruling, not a default: the middle tab is also the
        // start destination (CereBroApp startDestination = Tab.Talk.route). The
        // enum can't see the NavHost, so this pins the tab the shell falls back
        // to — Talk must exist and own the `talk` route for that line to hold.
        assertTrue("talk" in Tab.entries.map { it.route })
    }

    @Test
    fun exploreIsAPushedScreenNotATab() {
        assertFalse("explore no longer owns a tab", shouldShowBottomBar("explore"))
        assertFalse("explore" in Tab.entries.map { it.route })
        assertEquals("explore", routeForDeeplink("cerebro://explore"))
    }

    @Test
    fun youAndJournalSurviveAsRoutesNotTabs() {
        // The rooms didn't die with their tabs: `you` opens from the gear on
        // every tab root, `journal` from Home's care card and the chat tools
        // tray. Both must stay deeplink-reachable.
        assertFalse("you" in Tab.entries.map { it.route })
        assertFalse("journal" in Tab.entries.map { it.route })
        assertEquals("you", routeForDeeplink("cerebro://you"))
        assertEquals("journal", routeForDeeplink("cerebro://journal"))
        // Journal keeps the pill (a content room, reached from Home);
        // You does not (a settings room).
        assertTrue(shouldShowBottomBar("journal"))
        assertFalse(shouldShowBottomBar("you"))
    }

    @Test
    fun urgentSupportSurvivesTheTabChange() {
        // Crisis stays <= 2 taps from anywhere. The shield sits in the top bar
        // of every frame (V2-a), which no longer depends on any tab set.
        assertEquals("crisis", routeForDeeplink("cerebro://crisis"))
    }

    @Test
    fun theRetiredAliasesStayRetired() {
        assertFalse(shouldShowBottomBar("talk/live"))
        assertFalse(shouldShowBottomBar("talk/chat"))
        assertFalse(shouldShowBottomBar("dailyplan"))
    }

    @Test
    fun nudgeDeeplinksLandWhereTheyPromise() {
        // The server's nudge vocabulary (backend nudges.py + digest.py) — each
        // resolves to the surface the notification named, not Home.
        assertEquals("home", routeForDeeplink("cerebro://mood"))
        assertEquals("breathe/reset", routeForDeeplink("cerebro://breathe"))
        assertEquals("sleep", routeForDeeplink("cerebro://sleep"))
        assertEquals("insights", routeForDeeplink("cerebro://insights"))
        // Admin-authored links to known routes pass through…
        assertEquals("winddown", routeForDeeplink("cerebro://winddown"))
        assertEquals("journal/new", routeForDeeplink("cerebro://journal/new"))
        // …with trailing slashes and case tolerated.
        assertEquals("sleep", routeForDeeplink("cerebro://Sleep/"))
    }

    @Test
    fun deeplinksAreAnAllowlistNotAPassthrough() {
        assertNull(routeForDeeplink("cerebro://admin"))
        assertNull(routeForDeeplink("cerebro://mindfulgame/zen-sand"))
        assertNull(routeForDeeplink("https://cerebrozen.in/sleep"))
        assertNull(routeForDeeplink("garbage"))
        assertNull(routeForDeeplink(""))
        assertNull(routeForDeeplink(null))
    }

    @Test
    fun theNavPillGetsOutOfTheKeyboardsWay() {
        assertTrue("visible normally", navVisible("journal", imeOpen = false))
        assertFalse("hidden while typing", navVisible("journal", imeOpen = true))
    }

    @Test
    fun aPushedScreenStillHasNoNavWithTheKeyboardEitherWay() {
        assertFalse(navVisible("player", imeOpen = false))
        assertFalse(navVisible("player", imeOpen = true))
    }

    @Test
    fun aLiveVoiceSessionTakesTheWholeScreen() {
        // V4: the session overlay is drawn inside Talk and cannot cover the
        // Scaffold's pill, so a call had three tabs across its bottom edge —
        // one tap from silently abandoning it. The pill yields to a live
        // session exactly as it yields to the keyboard.
        assertTrue(navVisible("talk", imeOpen = false, voiceLive = false))
        assertFalse(navVisible("talk", imeOpen = false, voiceLive = true))
    }

    // The Sleep-stays-Night pin that lived here was RETIRED 2026-08-04 with
    // the rule itself — owner decision, recorded in docs/TODO.md: appearance
    // is global, changed on every client in the same commit.
}
