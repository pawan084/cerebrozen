package com.cerebrozen.app.ui

import org.junit.Assert.assertFalse
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
    @Test
    fun sleepContextsStayNightIncludingEverythingSleepCanPush() {
        // The rule came from hardware: a sleep story at 22:46, one tap on the
        // now-playing bar, and a full-brightness player with the sleep timer
        // running. The set must cover every route the Sleep tab pushes, or the
        // theme flips mid-wind-down.
        listOf("sleep", "player", "sounds", "sounds/mixer", "winddown").forEach {
            assertTrue(it, it in SLEEP_CONTEXT_ROUTES)
        }
        // And only sleep contexts — Home in Dawn must stay Dawn.
        listOf("home", "talk", "journal", "you", "toolkit", "trends").forEach {
            assertFalse(it, it in SLEEP_CONTEXT_ROUTES)
        }
    }
}
