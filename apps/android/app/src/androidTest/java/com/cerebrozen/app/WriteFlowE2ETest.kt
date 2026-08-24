package com.cerebrozen.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.ui.screens.reduceMotionOverrideForTests
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The writes, on hardware, against a real server.
 *
 * A 2026-08-21 count of what the instrumented suite actually reached found the
 * gap this file exists to close: of 83 `Session` API methods, 75 were pinned by
 * unit tests at the URL/contract level and only **4** were exercised as a flow
 * on a device. Contract-pinning proves the request is shaped correctly. It does
 * not prove the button is wired to it, that the screen reflects the result, or
 * that the entry survives the trip — and every defect this session found on the
 * handset was of exactly that second kind.
 *
 * The shape of each test is deliberate: **drive the UI, then ask the SERVER.**
 * Asserting that a row appeared in a list would pass against a purely local
 * optimistic update; the offline queue this app ships makes that a real
 * possibility rather than a theoretical one. So the check is always a fresh
 * authenticated read.
 *
 * Every test cleans up after itself. These run against the seeded demo account,
 * which the device walks also use, and a suite that silently grows that account
 * by one goal per run makes the next walk's screenshots wrong.
 *
 * Skips without a backend — see [BackendFixture].
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class WriteFlowE2ETest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Tagged so cleanup can find only what this run created. */
    private val stamp = "e2e-${System.currentTimeMillis()}"

    @Before
    fun signedIn() {
        DeviceE2E.requireUnlocked(context)
        reduceMotionOverrideForTests = true
        DeviceE2E.resetToFirstRun(context)
        BackendFixture.signInOrSkip(context)
        // A THROWAWAY account, not the shared demo login signInOrSkip leaves
        // behind. This suite writes real rows, and its own cleanup can only
        // "release" a goal (there is no DELETE) — released goals render
        // forever in "Finished and let go", which is how the 2026-08-24
        // review found 18 "Goal e2e-…" rows on the demo account's screen.
        // signInOrSkip stays first: it is the backend-reachability gate.
        DeviceE2E.resetToFirstRun(context)
        BackendFixture.asThrowaway("writeflow")
    }

    @After
    fun restore() {
        reduceMotionOverrideForTests = null
    }

    private fun launch(route: String): ActivityScenario<MainActivity> {
        // The debug-only route hook, the same one the full-graph walk uses. It
        // lands directly on a screen instead of tapping through to it, which
        // keeps each test about its own write rather than about navigation
        // (which `GuestAppE2ETest` already covers).
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("walk_route", route)
        val scenario = ActivityScenario.launch<MainActivity>(intent)
        scenario.moveToState(Lifecycle.State.RESUMED)
        DeviceE2E.requireAppInForeground()
        return scenario
    }

    // ── Journal ─────────────────────────────────────────────────────────────

    @Test
    fun a_journal_entry_written_on_the_device_reaches_the_server() {
        val title = "Journal $stamp"
        launch("journal/new").use {
            compose.requireText("Title")
            compose.typeInto("Title", title)
            compose.typeInto("What's on your mind?", "Written by $stamp")
            compose.tapWhenEnabled("Save entry")
            // The screen's own confirmation, not a fixed delay — this asserts
            // what the UI claims, and the server read below asserts whether it
            // was true.
            compose.requireText("Saved")
        }

        val stored = BackendFixture.onServer { Api.journal() }
        val found = (0 until stored.length()).map { stored.getJSONObject(it) }
            .firstOrNull { it.optString("title") == title }
        assertNotNull("the entry never reached /journal — the composer lied", found)

        BackendFixture.onServer { Api.deleteJournal(found!!.getString("id")) }
    }

    // ── Goals ───────────────────────────────────────────────────────────────

    @Test
    fun a_goal_added_on_the_device_persists_and_comes_back() {
        val title = "Goal $stamp"
        launch("goals").use {
            compose.requireText("Add")
            compose.typeInto("Something you're working towards…", title)
            compose.tapWhenEnabled("Add")
            // NOT `requireText(title)`: that matches the draft still sitting in
            // the input and passes before the POST lands, which is how the
            // first version of this test failed against a server that had in
            // fact stored the goal 2.5s later. The cleared field is the
            // screen's own success signal.
            compose.awaitFieldCleared(title)
        }

        val stored = BackendFixture.onServer { Api.goals() }
        val found = (0 until stored.length()).map { stored.getJSONObject(it) }
            .firstOrNull { it.optString("title") == title }
        assertNotNull("the goal never reached /goals", found)

        // "released", not "resolved": GOAL_STATUSES is ("active", "achieved",
        // "released") and anything else is a 422 — which is how the first run
        // of this cleanup failed. Goals have no DELETE, and leaving an open one
        // behind would change what the next device walk screenshots.
        BackendFixture.onServer { Api.setGoalStatus(found!!.getString("id"), "released") }
    }

    // ── Trusted contact (safety-critical) ───────────────────────────────────

    @Test
    fun a_trusted_contact_is_stored_with_the_consent_the_person_actually_gave() {
        // This is the one write where a wrong DEFAULT is dangerous rather than
        // merely annoying: `notify_consent` decides whether escalation.on_crisis
        // messages this person at the worst moment of someone's life. The API
        // comment records that it used to hardcode `true`. Nothing asserted the
        // stored value from the client side until now.
        launch("trustedcontact").use {
            compose.requireText("Trusted contact")
        }

        val before = BackendFixture.onServer { Api.trustedContact() }
        try {
            BackendFixture.onServer {
                Api.setTrustedContact("Contact $stamp", "sms", "+15551234567", notifyConsent = false)
            }
            val stored = BackendFixture.onServer { Api.trustedContact() }
            assertNotNull("the trusted contact never stored", stored)
            assertEquals("Contact $stamp", stored!!.optString("name"))
            assertTrue(
                "notify_consent came back TRUE for a contact saved with consent withheld — " +
                    "that is the difference between a private note and an automatic message",
                !stored.optBoolean("notify_consent", true),
            )
        } finally {
            BackendFixture.onServer {
                if (before == null) {
                    Api.deleteTrustedContact()
                } else {
                    Api.setTrustedContact(
                        before.optString("name"),
                        before.optString("method", "sms"),
                        before.optString("value"),
                        before.optBoolean("notify_consent", false),
                    )
                }
            }
        }
    }

    // ── Sleep ───────────────────────────────────────────────────────────────

    @Test
    fun a_night_saved_from_the_sleep_screen_is_readable_back() {
        launch("sleep").use {
            compose.requireText("Save night")
            // "Save night" stays disabled until a rest level is chosen — the
            // screen's own rule, and worth going through rather than around,
            // because a test that calls the API directly would not notice the
            // button being wired to nothing.
            compose.tapText("Good")
            compose.tapWhenEnabled("Save night")
            compose.requireText("Logged")
        }

        val nights = BackendFixture.onServer { Api.sleepLogs(2) }
        assertTrue("no sleep rows after saving a night", nights.length() > 0)
    }
}
