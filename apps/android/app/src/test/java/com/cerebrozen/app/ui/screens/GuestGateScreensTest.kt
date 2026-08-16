package com.cerebrozen.app.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import com.cerebrozen.app.net.Session
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Audit K's guest-state class, pinned at the two screens where it bit hardest.
 *
 * A guest's 401 is not a failure — the request was never going to succeed — so
 * a screen may answer it with a sign-in door and nothing else. The two shapes
 * this class forbids coming back:
 *
 *  - the Daily plan's **eternal loading**: `activePlan()` swallowed every
 *    failure into null, and null-without-error rendered "Loading your plan…"
 *    forever;
 *  - the safety plan's **editor with no floor**: seven text sections a guest
 *    could fill in, whose saves could never succeed — losing user writing is
 *    this app's worst defect class, so the gate must arrive BEFORE the fields.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GuestGateScreensTest {

    @get:Rule val compose = createComposeRule()

    @After fun resetGuestMode() { Session.guestMode = false }

    @Test
    fun plan_screen_shows_the_sign_in_door_not_a_spinner() {
        Session.guestMode = true
        compose.setContent { PlanScreen(onBack = {}, onOpen = {}) }
        compose.waitForIdle()

        val tree = compose.onRoot(useUnmergedTree = false).printToString()
        assertTrue("a guest must meet the sign-in door", tree.contains("Sign in"))
        assertFalse(
            "the eternal 'Loading your plan…' state must be gone for guests",
            tree.contains("Loading your plan"),
        )
    }

    @Test
    fun safety_plan_gates_the_editor_before_a_guest_can_write_into_it() {
        Session.guestMode = true
        compose.setContent { SafetyPlanScreen(onBack = {}, onOpen = {}) }
        compose.waitForIdle()

        val tree = compose.onRoot(useUnmergedTree = false).printToString()
        assertTrue("a guest must meet the sign-in door", tree.contains("Sign in"))
        // The first Stanley-Brown section's label doubles as its field label;
        // if it renders, the editor rendered.
        assertFalse(
            "no editable sections for a guest — writing that cannot be saved gets lost",
            tree.contains("Warning signs"),
        )
    }

    @Test
    fun signed_in_screens_still_render_their_normal_states() {
        // Not guest mode: the gate must NOT appear (the load will fail in this
        // JVM — no network — and that failure renders the retryable error path,
        // which is correct for a signed-in user).
        compose.setContent { SafetyPlanScreen(onBack = {}, onOpen = {}) }
        compose.waitForIdle()

        val tree = compose.onRoot(useUnmergedTree = false).printToString()
        assertTrue(
            "a signed-out-of-guest-mode user keeps the editor",
            tree.contains("Warning signs"),
        )
    }
}
