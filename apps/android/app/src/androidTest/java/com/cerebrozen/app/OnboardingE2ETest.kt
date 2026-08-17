package com.cerebrozen.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.screens.reduceMotionOverrideForTests
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First run, end to end, on hardware: a fresh install to a usable app.
 *
 * This is the flow every other flow depends on and the only one a new user
 * cannot skip, and until now nothing tested it outside a person's hands.
 * `ScreenLogicTest` pins the funnel's pure rules on the JVM — the step order,
 * `defaultConsent()` — but a rule can be right while the screen that renders it
 * is unreachable, mis-wired or gated behind a control that never enables. The
 * first device walk of this funnel found exactly that class of defect: a guest's
 * mood tap that errored out and never advanced.
 *
 * The consent assertion is the one to keep if only one survives. "Private by
 * default — nothing remembered unless you allow it" is a claim this product
 * makes on its landing page, in its privacy notice and to a regulator under
 * DPDP §6, and it is exactly the kind of promise that a UI can quietly break
 * (a switch defaulting on, a card pre-selecting "recommended") while every unit
 * test still passes. Here it is read off the rendered switches.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class OnboardingE2ETest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun freshInstall() {
        DeviceE2E.requireUnlocked(context)
        reduceMotionOverrideForTests = true
        DeviceE2E.resetToFirstRun(context)
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

    @Test
    fun a_first_run_walks_the_funnel_and_lands_in_the_app() {
        launch().use {
            // 0 — Welcome. The promise is a next step, not a setup marathon.
            compose.tapText(s(R.string.ob_welcome_cta))

            // 1 — Disclosure. Crisis support is reachable HERE, before anyone
            // has accepted terms or confirmed an age: the product's rule is
            // that safety never waits on consent (design §1).
            compose.requireText(s(R.string.ob_disclosure_title))
            compose.requireText(s(R.string.ob_urgent_support))
            // The age gate is the affirmative tap on this step, and it gates
            // Continue — the copy says the confirmation happens at the button
            // precisely because a pre-ticked attestation is not one.
            compose.requireText(s(R.string.ob_under_18))
            // Continue is dead until the attestation is made — the gate itself
            // is the compliance surface, so assert it before satisfying it.
            compose.onNode(hasText(s(R.string.ob_disclosure_cta)) and hasClickAction())
                .assertIsNotEnabled()
            compose.tapText(s(R.string.ob_age_confirm))
            compose.tapExactText(s(R.string.ob_disclosure_cta))

            // 2 — Consent. Every switch off, every time.
            compose.requireText(s(R.string.ob_consent_title))
            val switches = compose.onAllNodes(isToggleable()).fetchSemanticsNodes()
            assertEquals(
                "the consent step must render all six DPDP categories — a category with no " +
                    "switch is one the user never saw and cannot have consented to",
                6, switches.size,
            )
            repeat(switches.size) { compose.onAllNodes(isToggleable())[it].assertIsOff() }
            compose.tapExactText(s(R.string.common_continue))

            // 3 — State check. A pick is required to continue, and it only
            // shapes the first suggestion — nothing here is scored.
            compose.requireText(s(R.string.ob_state_title))
            compose.tapText(s(R.string.ob_state_opt_overthinking))
            compose.tapExactText(s(R.string.common_continue))

            // 4 — Guest. "Continue as guest" does it: no interstitial.
            compose.requireText(s(R.string.ob_guest_title))
            compose.tapText(s(R.string.ob_guest_continue))

            // Landed: the companion's composer is the app's front door since the
            // chat-first redesign, so its placeholder is the honest landmark.
            compose.requireText(s(R.string.talk_field_followup))
            assertTrue("guest mode was never recorded, so the funnel would run again", Session.guestMode)
        }
    }
}
