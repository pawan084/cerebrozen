package com.cerebrozen.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.screens.reduceMotionOverrideForTests
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Turn memory off and it forgets" — the toggle, from the switch inwards.
 *
 * `tests/test_memory.py::test_consent_off_blocks_reads_and_writes_but_never_deletion`
 * proves the SERVER honours `ai_memory`: with it off a write is 403, an edit is
 * 403, the listing still reads, and DELETE always works, because switching a
 * category off must never trap data someone wants gone. What nothing checked is
 * whether the switch on the privacy screen is connected to any of that. This
 * repo has shipped a control wired to nothing before, and a consent toggle is
 * the worst possible place for it: the failure is silent, and the person
 * believes they have withdrawn something they have not.
 *
 * So this drives the actual `AppSwitch` and then asks the server what it
 * believes, rather than calling `updateConsent` directly — calling the API
 * would test the API, which is already tested.
 *
 * **Runs on a throwaway account.** Consent state is rendered on the check-in
 * screen ("here is exactly which of them are switched on for you right now"),
 * so flipping it on the demo account would change what a device walk
 * screenshots — the lesson the programme test taught the hard way.
 *
 * Skips without a backend — see [BackendFixture].
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ConsentFlowE2ETest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun onAThrowawayAccount() {
        DeviceE2E.requireUnlocked(context)
        reduceMotionOverrideForTests = true
        DeviceE2E.resetToFirstRun(context)
        BackendFixture.signInOrSkip(context)
        DeviceE2E.resetToFirstRun(context)
        BackendFixture.asThrowaway("consent")
    }

    @After
    fun restore() {
        reduceMotionOverrideForTests = null
    }

    private fun launchPrivacy(): ActivityScenario<MainActivity> {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("walk_route", "privacy")
        val scenario = ActivityScenario.launch<MainActivity>(intent)
        scenario.moveToState(Lifecycle.State.RESUMED)
        DeviceE2E.requireAppInForeground()
        compose.requireText("What CereBro remembers")
        return scenario
    }

    /** The `AppSwitch` for a consent category, found by the label it carries. */
    private fun switchFor(label: String) =
        isToggleable() and (hasContentDescription(label) or hasText(label, substring = true))

    @Test
    fun a_fresh_account_has_granted_nothing() {
        // The default the product claims: all six off until asked. A toggle
        // test that started from an unknown state could pass by accident.
        val consent = BackendFixture.onServer { Api.consent() }
        val granted = CONSENT_CATEGORIES.filter { consent.optBoolean(it, false) }
        assertTrue(
            "a brand-new account already consents to $granted — the default must be nothing",
            granted.isEmpty(),
        )
    }

    @Test
    fun the_switch_is_wired_to_the_server_in_both_directions() {
        launchPrivacy().use {
            compose.turnOn(switchFor("AI memory"))
            assertTrue(
                "the switch read On but the server did not record the grant",
                BackendFixture.onServer { Api.consent() }.optBoolean("ai_memory", false),
            )

            compose.turnOff(switchFor("AI memory"))
            assertFalse(
                "the switch read Off but the server still believes consent was given — " +
                    "the person thinks they withdrew it and they did not",
                BackendFixture.onServer { Api.consent() }.optBoolean("ai_memory", false),
            )
        }
    }

    @Test
    fun with_memory_off_writing_is_refused_but_removal_is_not() {
        // The half of the claim that protects the person rather than the
        // product: consent off must block new memory WITHOUT trapping what is
        // already there. Server-side this is
        // test_consent_off_blocks_reads_and_writes_but_never_deletion; here it
        // is reached through the switch a person actually touches.
        // Grant FIRST, then write, then withdraw. The first version of this
        // test wrote before granting and failed with the server's own
        // "AI memory is switched off in your privacy settings" — because a
        // fresh account consents to nothing, which is the product's documented
        // default and worth having been reminded of by the gate itself.
        launchPrivacy().use { compose.turnOn(switchFor("AI memory")) }
        val id = BackendFixture.onServer { Api.addMemory("Before the toggle").getString("id") }

        launchPrivacy().use { compose.turnOff(switchFor("AI memory")) }
        assertFalse(
            "consent did not actually go off",
            BackendFixture.onServer { Api.consent() }.optBoolean("ai_memory", false),
        )

        val wroteAnyway = runCatching {
            BackendFixture.onServer { Api.addMemory("After the toggle") }
        }.isSuccess
        assertFalse("a new memory was accepted with ai_memory off", wroteAnyway)

        // ...and the existing row must still be removable.
        BackendFixture.onServer { Api.deleteOneMemory(id) }
        val left = BackendFixture.onServer { Api.memories() }
        assertFalse(
            "deletion was refused with consent off — that traps data someone asked to remove",
            (0 until left.length()).any { left.getJSONObject(it).optString("id") == id },
        )
    }

    private companion object {
        /** `ui/screens/ConsentNotice.kt` CONSENT_KEY_ORDER, duplicated here on
         *  purpose: if that list changes, this test should notice. */
        val CONSENT_CATEGORIES = listOf(
            "mood_history", "ai_memory", "journal_memory",
            "sleep_history", "voice_storage", "model_training",
        )
    }
}
