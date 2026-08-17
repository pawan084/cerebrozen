package com.cerebrozen.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.screens.reduceMotionOverrideForTests
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app as a guest, on hardware — the state most first users are actually in.
 *
 * A guest has no account, so every server call returns a 401 by design. That is
 * the interesting part: the product's rule is that a guest still gets a working
 * app, and the failure mode it must never have is treating "no account" as "an
 * error happened". These walks need no backend, which is why they can assert
 * that at all.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class GuestAppE2ETest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun asAGuest() {
        DeviceE2E.requireUnlocked(context)
        reduceMotionOverrideForTests = true
        DeviceE2E.resetToFirstRun(context)
        // Straight into the shell: the funnel has its own walk
        // (`OnboardingE2ETest`), and repeating it here would test it twice and
        // this twice as slowly.
        Session.continueAsGuest(context)
    }

    @After
    fun restoreMotion() {
        reduceMotionOverrideForTests = null
    }

    private fun launch(): ActivityScenario<MainActivity> =
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        ).also {
            it.moveToState(Lifecycle.State.RESUMED)
            DeviceE2E.requireAppInForeground()
        }

    private fun s(id: Int): String = context.getString(id)

    /**
     * The regression guard for the worst defect the first device walk found: a
     * guest tapped a mood, the server refused the row (401, by design), the
     * exception escaped, and the card never became the step. The answer was
     * lost and the screen just sat there. `isGuestGate()` now treats that 401
     * as what it is — an account state, not a failure — and the check-in still
     * counts on the device.
     *
     * Only a device can catch this: the JVM tests supply a fake API, so the
     * very 401 that broke it never happens there.
     */
    @Test
    fun a_guests_check_in_is_answered_not_errored() {
        launch().use {
            compose.tapText(s(R.string.tab_home))
            // The card's eyebrow is rendered uppercase, so match what is drawn.
            compose.requireText(s(R.string.home_mood_eyebrow).uppercase())
            compose.tapText(s(R.string.mood_anxious))
            // "Anxious — noted": the answer is said back. Before the fix this
            // never appeared, and nothing said why.
            compose.requireText(
                context.getString(R.string.today_checkin_logged, s(R.string.mood_anxious)),
            )
        }
    }

    /**
     * The nav rule this pass rewrote, read off the rendered chrome rather than
     * off the pure function. `NavigationChromeTest` pins `shouldShowBottomBar`,
     * which is the rule; this pins that the rule reaches the screen — the tab
     * pill belongs to roots, and a pushed room owns a Back button instead. On
     * glass this is how You/Settings was caught with neither.
     */
    @Test
    fun a_pushed_room_trades_the_tab_pill_for_a_back_button() {
        launch().use {
            // A root: all three tabs are on screen.
            compose.tapText(s(R.string.tab_home))
            compose.requireText(s(R.string.tab_sleep))
            compose.requireText(s(R.string.tab_talk))

            // The Toolkit is a pushed room, reached the way the app offers it:
            // the chat's tools tray, through the All-tools door this pass added.
            compose.tapText(s(R.string.tab_talk))
            compose.tapText(s(R.string.talk_tools_cd))
            compose.tapText(s(R.string.talk_all_tools))

            compose.requireText(s(R.string.toolkit_title))
            assertTrue(
                "a pushed room must own a Back button — dropping the tab pill is only " +
                    "safe because of it",
                compose.awaitText("Back", timeoutMs = 5_000),
            )
            assertFalse(
                "the tab pill is still drawn over a pushed room, so a Back button and a " +
                    "lit tab now disagree about where the user is",
                compose.awaitText(s(R.string.tab_sleep), timeoutMs = 2_000),
            )
        }
    }
}
