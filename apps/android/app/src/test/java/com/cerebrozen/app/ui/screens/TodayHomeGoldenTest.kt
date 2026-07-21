package com.cerebrozen.app.ui.screens

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.cerebrozen.app.net.HomeCache
import com.cerebrozen.app.net.Session
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A structural "golden" render of Today for a signed-in, mid-streak account (HOME_SPEC #32) —
 * the one screen every session starts on had no regression net at all. This doesn't replace
 * [CoachHomeTest]'s pure-function ordering/copy locks; it proves the ASSEMBLED screen — real
 * Session state, real HomeCache, the actual composable tree — renders the greeting, the
 * check-in, and the door stack together without crashing, which the pure tests alone can't see
 * (they caught the ordering bug; this catches the "does it even compose" class of regression).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodayHomeGoldenTest {

    @get:Rule val compose = createComposeRule()

    private val store = object : Session.Store {
        val m = mutableMapOf("refresh_token" to "r1")
        override fun getString(key: String) = m[key]
        override fun putString(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
        override fun keys() = m.keys.toSet()
    }

    // HomeCache is a process-wide singleton — cleared both before AND after each test, since
    // Gradle's test executor doesn't guarantee this class's methods are the only ones to have
    // touched it in this JVM (any other suite that warms HomeCache and doesn't clean up would
    // otherwise leak state into whichever test happens to run next).
    @Before
    fun setUp() {
        HomeCache.clear()
    }

    @After
    fun tearDown() {
        HomeCache.clear()
    }

    @Test
    fun a_signed_in_mid_streak_account_renders_the_whole_screen_together() {
        // Reduce Motion: the splash/stagger entrances resolve to their final frame
        // immediately, so this test asserts the SETTLED screen, not a mid-animation frame.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)

        Session.resetForTest(store) { url, _, _, _, _ ->
            when {
                url.endsWith("/auth/refresh") -> 200 to """{"access_token":"a1","refresh_token":"r1"}"""
                url.endsWith("/users/me") -> 200 to """{"name":"Nova"}"""
                url.endsWith("/v1/wellness/moods") -> 200 to """[{"ts":"2026-07-20T09:00:00+00:00"}]"""
                url.endsWith("/users/me/streak") -> 200 to """{"current":9}"""
                url.endsWith("/users/me/consent") -> 200 to """{"mood_history":true}"""
                url.endsWith("/programs/active") -> 200 to """{"program":null}"""
                url.endsWith("/v1/sessions/resumable") -> 200 to """{"resumable":false}"""
                else -> 200 to "{}"
            }
        }

        compose.setContent { TodayHome(onOpen = {}) }
        compose.waitForIdle()

        // The greeting carries the warmed name (HOME_SPEC #11 — never a permanent greeting
        // with nothing after it). Which GREETING word appears (morning/afternoon/evening) is
        // hour-of-day-dependent logic already locked precisely by CoachHomeTest; this only
        // proves the assembled screen actually used the warmed name at all.
        compose.onNodeWithText("Nova", substring = true).assertExists()
        // The check-in card reflects the warmed streak, not "How's today going?" — proof
        // CheckInCard actually picked up HomeCache rather than racing its own cold fetch.
        compose.onNodeWithText("9 days in a row", substring = true).assertExists()
        // At least one door rendered — the assembled stack, not an empty Page.
        compose.onNodeWithText("Commitments").assertExists()
    }

    @Test
    fun a_brand_new_signed_out_looking_account_still_renders_without_crashing() {
        // No name, no moods, no streak — every one of Home's warm() calls came back empty.
        // The screen must degrade to its friendly defaults, never a blank page or a crash.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)

        Session.resetForTest(store) { url, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) 200 to """{"access_token":"a1","refresh_token":"r1"}"""
            else 404 to """{"detail":"nope"}"""
        }

        compose.setContent { TodayHome(onOpen = {}) }
        compose.waitForIdle()

        compose.onNodeWithText("Couldn't load Today").assertExists()
        compose.onNodeWithText("Commitments").assertExists()
    }
}
