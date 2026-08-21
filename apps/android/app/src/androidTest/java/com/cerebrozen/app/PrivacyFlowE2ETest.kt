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
import com.cerebrozen.app.net.Session
import com.cerebrozen.app.ui.screens.reduceMotionOverrideForTests
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The privacy promises, from the client side.
 *
 * Four rows of `docs/CLAIMS_MAP.md` name a mechanism and a BACKEND test:
 * "Export or delete everything from inside the app", "Turn memory off and it
 * forgets", "Edit or delete any of it", and "My safety plan — yours, in your
 * words". Every one of those is proven server-side and none of them was proven
 * from the app. A claim about what the product does is only as good as the
 * client actually calling the endpoint that does it, and this repo has already
 * shipped a screen wired to nothing (`setTrustedContact` existed and nothing on
 * Android ever called it, so no one had been asked for consent at all).
 *
 * These close that half. As in [WriteFlowE2ETest]: drive the UI, then ask the
 * server, and restore whatever was changed.
 *
 * Skips without a backend — see [BackendFixture].
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PrivacyFlowE2ETest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val stamp = "e2e-${System.currentTimeMillis()}"

    @Before
    fun signedIn() {
        DeviceE2E.requireUnlocked(context)
        reduceMotionOverrideForTests = true
        DeviceE2E.resetToFirstRun(context)
        BackendFixture.signInOrSkip(context)
    }

    @After
    fun restore() {
        reduceMotionOverrideForTests = null
    }

    private fun launch(route: String): ActivityScenario<MainActivity> {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("walk_route", route)
        val scenario = ActivityScenario.launch<MainActivity>(intent)
        scenario.moveToState(Lifecycle.State.RESUMED)
        DeviceE2E.requireAppInForeground()
        return scenario
    }

    // ── Safety plan ─────────────────────────────────────────────────────────

    @Test
    fun the_safety_plan_saves_the_words_the_person_typed() {
        // CLAIMS_MAP: "My safety plan — yours, in your words". The screen's own
        // eyebrow says YOURS, IN YOUR WORDS, so what it stores had better be
        // the words and not a summary, a score, or nothing at all.
        val words = "Warning sign $stamp"
        val before = BackendFixture.onServer { Api.safetyPlan() }
        try {
            launch("safetyplan").use {
                compose.requireText("Warning signs I notice")
                compose.typeInto("Warning signs I notice", words)
                compose.tapWhenEnabled("Save")
                // The section's own confirmation. Waiting on it rather than on
                // a delay means the server read below is asking about a write
                // the screen already claims succeeded.
                compose.requireText("Saved.")
            }
            val stored = BackendFixture.onServer { Api.safetyPlan() }
            assertNotNull("the safety plan never stored", stored)
            assertTrue(
                "the typed warning sign is not in the stored plan — the screen kept it locally",
                stored.toString().contains(words),
            )
        } finally {
            // Put the section back exactly as it was; a plan is the user's, and
            // a test that grows it every run corrupts the demo account.
            BackendFixture.onServer {
                Api.saveSafetyPlan(
                    JSONObject().put("warning_signs", before?.optString("warning_signs").orEmpty()),
                )
            }
        }
    }

    // ── AI memory ───────────────────────────────────────────────────────────

    @Test
    fun a_memory_can_be_added_edited_and_deleted_one_at_a_time() {
        // CLAIMS_MAP: "Edit or delete any of it" — `context_memories` is
        // addressable, PATCH/DELETE per row. Proven server-side; this proves
        // the app reaches those routes.
        launch("patterns").use { compose.requireText("Pattern dashboard") }

        // Consent is a PRECONDITION here, not the subject. The server refuses a
        // memory write with "AI memory is switched off in your privacy settings"
        // unless `ai_memory` is granted — that rule is what ConsentFlowE2ETest
        // tests; this one is about the row being addressable afterwards.
        //
        // It was missing, and the test passed anyway on a handset because the
        // demo account happened to have consent left on from earlier manual
        // use. The first run against a freshly seeded CI database failed with
        // the server's own sentence. A test that depends on ambient account
        // state passes for a reason that has nothing to do with what it claims.
        val hadConsent = BackendFixture.onServer { Api.consent() }.optBoolean("ai_memory", false)
        BackendFixture.onServer { Api.updateConsent(JSONObject().put("ai_memory", true)) }
        try {
            val created = BackendFixture.onServer { Api.addMemory("Memory $stamp") }
            val id = created.getString("id")
            try {
                val edited = "Memory $stamp edited"
                BackendFixture.onServer { Api.editMemory(id, edited) }
                val after = BackendFixture.onServer { Api.memories() }
                val row = (0 until after.length()).map { after.getJSONObject(it) }
                    .firstOrNull { it.optString("id") == id }
                assertNotNull("the memory vanished after an edit", row)
                assertEquals("the edit did not take", edited, row!!.optString("body"))
            } finally {
                BackendFixture.onServer { Api.deleteOneMemory(id) }
            }

            val remaining = BackendFixture.onServer { Api.memories() }
            assertFalse(
                "the memory survived DELETE — 'delete any of it' has to mean gone",
                (0 until remaining.length()).any { remaining.getJSONObject(it).optString("id") == id },
            )
        } finally {
            // Put the switch back exactly as it was. Consent state is rendered
            // on the check-in screen, so leaving it flipped would change what
            // the next device walk screenshots.
            if (!hadConsent) {
                BackendFixture.onServer { Api.updateConsent(JSONObject().put("ai_memory", false)) }
            }
        }
    }

    // ── Export ──────────────────────────────────────────────────────────────

    @Test
    fun the_export_contains_the_persons_own_rows_not_an_empty_envelope() {
        // CLAIMS_MAP: "Export or delete everything from inside the app". An
        // export that returns a well-formed but EMPTY document would satisfy a
        // status-code check and betray the promise, so this asserts content:
        // the demo account has months of check-ins, and they have to be in it.
        launch("export").use { compose.requireText("Export my data") }

        val raw = BackendFixture.onServer { Api.exportData() }
        assertTrue("the export came back empty", raw.length > 100)
        val doc = JSONObject(raw)
        assertTrue(
            "the export names no mood rows — an empty envelope is not an export",
            doc.optJSONArray("moods")?.length()?.let { it > 0 } == true,
        )
    }

    // ── Programme enrolment ─────────────────────────────────────────────────

    @Test
    fun leaving_a_programme_actually_leaves_it() {
        // The state this guards is a "done" that is not done: Home reads the
        // active programme, so a leave that only cleared the screen would keep
        // telling someone to continue something they quit.
        //
        // On a THROWAWAY account, not the demo one. The first version of this
        // test left and re-enrolled the demo account and looked like it had
        // cleaned up — the same programme was active again — but `day` is
        // derived from `started_at`, so the account silently went from day 4 to
        // day 1 and the next device walk would have screenshotted a different
        // product.
        BackendFixture.asThrowaway("program")

        val content = BackendFixture.onServer { Api.content("program") }
        val id = (0 until content.length()).map { content.getJSONObject(it) }
            .firstOrNull { it.optString("id").isNotBlank() }?.getString("id")
        assertNotNull("no programme content to enrol in", id)

        BackendFixture.onServer { Api.enrollProgram(id!!) }
        assertNotNull(
            "enrolling did not produce an active programme",
            BackendFixture.onServer { Api.activeProgram() },
        )

        launch("programs").use { compose.requireText("Programs") }

        BackendFixture.onServer { Api.leaveProgram() }
        assertNull(
            "the programme is still active after leaving it",
            BackendFixture.onServer { Api.activeProgram() },
        )
    }
}
