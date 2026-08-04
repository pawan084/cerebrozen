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
        listOf("tools", "games", "mindfulgame/{gameId}", "guidedimagery", "player", null).forEach {
            assertFalse(it ?: "null", shouldShowBottomBar(it))
        }
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
