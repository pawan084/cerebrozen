package com.cerebrozen.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationChromeTest {
    @Test
    fun bottomBarOnlyAppearsOnRootTabs() {
        listOf("home", "explore", "talk", "journal", "you").forEach {
            assertTrue(it, shouldShowBottomBar(it))
        }
        listOf("tools", "games", "mindfulgame/{gameId}", "guidedimagery", "player", null).forEach {
            assertFalse(it ?: "null", shouldShowBottomBar(it))
        }
    }

    @Test
    fun theTabsAreTheSpecsFive() {
        // docs/REDESIGN_V2.md §3.1 + owner ruling §6.1: Today · Explore · Talk ·
        // Journal · You. Pinned by ROUTE because the labels are localized; the
        // Today tab deliberately keeps the `home` route so deeplinks, saved
        // back-stack state and the nudge vocabulary all survived the rename.
        assertEquals(listOf("home", "explore", "talk", "journal", "you"), Tab.entries.map { it.route })
    }

    @Test
    fun sleepIsAPushedScreenNotATab() {
        // Sleep left the tab bar (owner ruling §6.1) and is reached from
        // Explore. It must NOT light the pill: a tab bar showing on a route no
        // tab owns leaves five unlit tabs and no sense of where you are.
        assertFalse("sleep no longer owns a tab", shouldShowBottomBar("sleep"))
        assertFalse("sleep" in Tab.entries.map { it.route })
        // …but it is still a real destination, so everything that pointed at it
        // — nudges, plan steps, Today's entry points — keeps working.
        assertEquals("sleep", routeForDeeplink("cerebro://sleep"))
    }

    @Test
    fun urgentSupportSurvivesTheTabChange() {
        // The safety rule is that crisis stays <= 2 taps from anywhere. It never
        // hung off the Sleep tab — it hangs off the You tab's Support card and,
        // since the five-tab pass, Explore's support door. Both of those tabs
        // are one tap from every tab route, so the path is unchanged in length.
        assertTrue("You is still a tab", "you" in Tab.entries.map { it.route })
        assertTrue("Explore is a tab", "explore" in Tab.entries.map { it.route })
        listOf("home", "explore", "talk", "journal", "you").forEach {
            assertTrue("the pill (and so the You tab) is reachable from $it", shouldShowBottomBar(it))
        }
        // And the crisis screen stays a first-class deeplink target.
        assertEquals("crisis", routeForDeeplink("cerebro://crisis"))
    }

    @Test
    fun theTalkAliasesKeepTheirTabChrome() {
        // talk/live + talk/chat render the Talk tab; without the pill a stale
        // entry point showed Talk chrome-less (audit A12).
        assertTrue(shouldShowBottomBar("talk/live"))
        assertTrue(shouldShowBottomBar("talk/chat"))
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
        // A notification must never navigate to an arbitrary graph node —
        // unknown or malformed URIs fall back to a normal Home launch.
        assertNull(routeForDeeplink("cerebro://admin"))
        assertNull(routeForDeeplink("cerebro://mindfulgame/zen-sand"))
        assertNull(routeForDeeplink("https://cerebrozen.in/sleep"))
        assertNull(routeForDeeplink("garbage"))
        assertNull(routeForDeeplink(""))
        assertNull(routeForDeeplink(null))
    }

    @Test
    fun theNavPillGetsOutOfTheKeyboardsWay() {
        // It reserved its slot whether or not it drew anything, so with the IME
        // up there was a dead lavender band above the keyboard on every screen
        // you can type on — showing tabs that dismiss the keyboard if tapped.
        assertTrue("visible normally", navVisible("journal", imeOpen = false))
        assertFalse("hidden while typing", navVisible("journal", imeOpen = true))
    }

    @Test
    fun aPushedScreenStillHasNoNavWithTheKeyboardEitherWay() {
        assertFalse(navVisible("player", imeOpen = false))
        assertFalse(navVisible("player", imeOpen = true))
    }

    // The Sleep-stays-Night pin that lived here was RETIRED 2026-08-04 with
    // the rule itself — owner decision, recorded in docs/TODO.md: appearance
    // is global, changed on every client in the same commit.
}
