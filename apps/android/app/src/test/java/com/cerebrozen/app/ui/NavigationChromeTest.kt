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

    // The Sleep-stays-Night pin that lived here was RETIRED 2026-08-04 with
    // the rule itself — owner decision, recorded in docs/TODO.md: appearance
    // is global, changed on every client in the same commit.
}
