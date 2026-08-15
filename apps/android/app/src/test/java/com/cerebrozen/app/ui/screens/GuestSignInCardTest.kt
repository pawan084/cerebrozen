package com.cerebrozen.app.ui.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
// `onNode` / `onAllNodes` are members of the test rule, not importable
// extensions like `onRoot` — importing them fails to resolve.
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guest mode's only door to an account.
 *
 * Guest mode shipped as a full app shell with **no way to sign in from inside
 * it** — the only route to the auth screen was to clear the session first. The
 * door added on 2026-08-15 arrived without a test, and `RouteReachabilityTest`
 * only proves the `auth` route has *a* caller, not that a guest can find one.
 *
 * Two things are worth pinning, and neither is the copy:
 *  - it navigates to `auth` and nowhere else — a door to the wrong room is worse
 *    than no door, because the person believes they have tried;
 *  - it is ONE control, announced as a button. The card is three Text nodes over
 *    a click target, and `SectionCard` sets no role and does not merge children,
 *    so without the semantics in `GuestSignInCard` a screen-reader user meets
 *    three loose labels and no button at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GuestSignInCardTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun it_navigates_to_the_auth_route() {
        val opened = mutableListOf<String>()
        compose.setContent { GuestSignInCard(onOpen = { opened += it }) }

        compose.onNode(hasClickAction()).performClick()
        // Settle before asserting, and before this class ends.
        //
        // `SectionCard` answers a press with `pressScale`, an underdamped spring
        // (dampingRatio 0.62). Ending the test mid-flight left that animation
        // scheduled, and Compose's test rule registers its clock as an Espresso
        // idling resource in a registry that is **process-global** — so the
        // unsettled clock outlived this class and every later Compose test in
        // the JVM failed with AppNotIdleException. Twelve of them, in three
        // classes that had nothing to do with this one, all of which passed in
        // isolation. Waiting here keeps the mess inside the test that makes it.
        compose.waitForIdle()

        assertEquals("the guest door must lead to auth, and only there", listOf("auth"), opened)
    }

    @Test
    fun it_is_announced_as_a_single_button() {
        compose.setContent { GuestSignInCard(onOpen = {}) }

        // Merged: the three Text nodes collapse into one node carrying the
        // click. If the merge regresses, the child labels reappear as separate
        // nodes and this finds more than one clickable.
        val clickable = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
        ).fetchSemanticsNodes()
        assertEquals("the card must expose exactly one click target", 1, clickable.size)

        val role = clickable.first().config.getOrNull(SemanticsProperties.Role)
        assertEquals("a screen reader must be told this is a button", Role.Button, role)
    }

    @Test
    fun its_label_says_what_the_control_does() {
        compose.setContent { GuestSignInCard(onOpen = {}) }
        // Not asserting the marketing copy — only that the ACTION reaches the
        // accessibility label, since "Want to keep your progress?" alone does
        // not tell a screen-reader user that activating this signs them in.
        val tree = compose.onRoot(useUnmergedTree = false).printToString()
        assertTrue("the announced label must name the action, not just the offer", tree.contains("Sign in"))
    }
}
