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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Habits — and specifically the promise attached to marking one.
 *
 * The add is the least interesting part; it is goals with a second field. What
 * is worth pinning is the sentence on `Api.setHabitToday`: *"Idempotent
 * server-side and undoable — a mis-tap is never permanent."* That is a claim
 * about a control people press while distracted, on a screen whose whole
 * framing is "flexible, not a streak", and an undo that silently does nothing
 * would be a quiet betrayal of it — the count would drift up and the person
 * would have no way back.
 *
 * So: add through the UI, mark through the UI, un-mark through the UI, and ask
 * the server after each.
 *
 * **Throwaway account.** `Api` exposes no habit delete — the backend has
 * `DELETE /habits/{id}` but nothing on Android calls it — so a habit added to
 * the demo account could not be removed afterwards, and the next device walk
 * would screenshot it. Adding a client method purely to let a test clean up
 * would be changing the product to suit the test.
 *
 * Skips without a backend — see [BackendFixture].
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class HabitFlowE2ETest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val stamp = "e2e-${System.currentTimeMillis()}"

    @Before
    fun onAThrowawayAccount() {
        DeviceE2E.requireUnlocked(context)
        reduceMotionOverrideForTests = true
        DeviceE2E.resetToFirstRun(context)
        BackendFixture.signInOrSkip(context)
        DeviceE2E.resetToFirstRun(context)
        BackendFixture.asThrowaway("habit")
    }

    @After
    fun restore() {
        reduceMotionOverrideForTests = null
    }

    private fun launchGoals(): ActivityScenario<MainActivity> {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("walk_route", "goals")
        val scenario = ActivityScenario.launch<MainActivity>(intent)
        scenario.moveToState(Lifecycle.State.RESUMED)
        DeviceE2E.requireAppInForeground()
        return scenario
    }

    private fun habitNamed(title: String): JSONObject? {
        val all = BackendFixture.onServer { Api.habits() }
        return (0 until all.length()).map { all.getJSONObject(it) }
            .firstOrNull { it.optString("title") == title }
    }

    @Test
    fun a_habit_is_added_marked_and_un_marked_from_the_screen() {
        val title = "Habit $stamp"

        launchGoals().use {
            compose.requireText("A small thing to repeat…")
            compose.typeInto("A small thing to repeat…", title)
            compose.typeInto("After I…", "brush my teeth")
            // Two "Add" buttons live on this screen — goals above, habits
            // below. `tapWhenEnabled` picks the ENABLED one, and the goals
            // draft is empty, so filling only these fields disambiguates them
            // without a test-only tag on the button.
            compose.tapWhenEnabled("Add")
            compose.awaitFieldCleared(title)
        }

        val created = habitNamed(title)
        assertNotNull("the habit never reached /habits", created)
        assertEquals(
            "a brand-new habit should not already be done today",
            false,
            created!!.optBoolean("done_today", true),
        )

        // Mark it — through the button someone actually presses.
        launchGoals().use {
            compose.requireText(title)
            compose.tapWhenEnabled("Mark today")
            compose.requireText("Done today ✓")
        }
        assertTrue(
            "the screen said Done today but the server did not record it",
            habitNamed(title)!!.optBoolean("done_today", false),
        )

        // ...and un-mark it. This is the half the API comment promises and the
        // half nothing checked: "a mis-tap is never permanent".
        launchGoals().use {
            compose.requireText("Done today ✓")
            compose.tapWhenEnabled("Done today ✓")
            compose.requireText("Mark today")
        }
        assertEquals(
            "un-marking left the habit done on the server — the mis-tap WAS permanent",
            false,
            habitNamed(title)!!.optBoolean("done_today", true),
        )
    }

    @Test
    fun marking_the_same_day_twice_counts_once() {
        // The other half of that sentence: "idempotent server-side". A second
        // POST for the same day must not add a second day, or a distracted
        // double-tap quietly inflates what the person is shown about themselves.
        //
        // Asserted on `recent_days`, because `HabitOut` has no count and no
        // streak field on purpose — "the schema shouldn't be able to say 'you
        // broke it'". That absence is the thing being relied on here, so the
        // test reads the seven-day window rather than inventing a counter.
        val title = "Idempotent $stamp"
        BackendFixture.onServer { Api.addHabit(title, "after coffee") }
        val id = habitNamed(title)!!.getString("id")

        BackendFixture.onServer { Api.setHabitToday(id, true) }
        val once = habitNamed(title)!!.optJSONArray("recent_days")?.length() ?: -1
        BackendFixture.onServer { Api.setHabitToday(id, true) }
        val twice = habitNamed(title)!!.optJSONArray("recent_days")?.length() ?: -2

        assertTrue("recent_days never populated after marking today", once >= 1)
        assertEquals(
            "marking the same day twice added a second day — a double-tap inflated the window",
            once,
            twice,
        )
        assertTrue(
            "the habit is not done after marking it",
            habitNamed(title)!!.optBoolean("done_today", false),
        )
    }
}
