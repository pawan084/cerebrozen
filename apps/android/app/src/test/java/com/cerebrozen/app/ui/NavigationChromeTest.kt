package com.cerebrozen.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationChromeTest {
    @Test
    fun bottomBarOnlyAppearsOnRootTabs() {
        listOf("home", "sleep", "talk", "journal", "you").forEach {
            assertTrue(it, shouldShowBottomBar(it))
        }
        listOf("tools", "games", "mindfulgame/{gameId}", "guidedimagery", "player", "explore", null).forEach {
            assertFalse(it ?: "null", shouldShowBottomBar(it))
        }
    }

    @Test
    fun theTabsAreTheV2Five() {
        // REDESIGN_V2.md §2, owner-approved 2026-08-15: Today · Sleep · Talk ·
        // Journal · You. This REVERSES the Light-Dawn §6.1 ruling (archived at
        // docs/REDESIGN_V2_2026-08-06-lightdawn.md) deliberately: Sleep is the
        // only bias-robust evidence domain and gets its permanent door back;
        // Explore's slot retires and the library lives behind Today's
        // "more options →". Pinned by ROUTE because the labels are localized;
        // Today keeps the `home` route so deeplinks and saved state survive.
        assertEquals(listOf("home", "sleep", "talk", "journal", "you"), Tab.entries.map { it.route })
    }

    @Test
    fun exploreIsAPushedScreenNotATab() {
        // The mirror of the ruling above: `explore` must not light the pill —
        // a tab bar showing on a route no tab owns leaves five unlit tabs and
        // no sense of where you are. The route survives for deeplinks.
        assertFalse("explore no longer owns a tab", shouldShowBottomBar("explore"))
        assertFalse("explore" in Tab.entries.map { it.route })
        assertEquals("explore", routeForDeeplink("cerebro://explore"))
    }

    @Test
    fun urgentSupportSurvivesTheTabChange() {
        // The safety rule is that crisis stays <= 2 taps from anywhere. Since
        // V2-a the shield sits in the top bar of every frame, and the You tab
        // keeps its Support card — one tap from every tab route.
        assertTrue("You is still a tab", "you" in Tab.entries.map { it.route })
        listOf("home", "sleep", "talk", "journal", "you").forEach {
            assertTrue("the pill (and so the You tab) is reachable from $it", shouldShowBottomBar(it))
        }
        // And the crisis screen stays a first-class deeplink target.
        assertEquals("crisis", routeForDeeplink("cerebro://crisis"))
    }

    @Test
    fun theRetiredAliasesStayRetired() {
        // V2-e: talk/live, talk/chat and dailyplan left the graph — a stale
        // entry point must not resurrect chrome for routes that don't exist.
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
