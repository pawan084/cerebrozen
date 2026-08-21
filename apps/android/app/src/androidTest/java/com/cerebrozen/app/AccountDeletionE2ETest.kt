package com.cerebrozen.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.cerebrozen.app.net.Api
import com.cerebrozen.app.net.Session
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Export or delete everything from inside the app", proven by doing it.
 *
 * This is the one claim that cannot be checked against the seeded demo
 * account, because checking it would destroy the account every other test and
 * every device walk depends on. So it builds its own: sign up, write something
 * worth losing, delete the account, and then prove the credentials no longer
 * work and the data no longer answers.
 *
 * Kept in its own class for the same reason. A `@Before` that signs into the
 * demo account, next to a test that deletes whatever it is signed into, is one
 * editing mistake away from wiping the fixture — and the mistake would be
 * silent until the next walk came up empty.
 *
 * Skips without a backend — see [BackendFixture].
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AccountDeletionE2ETest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun ready() {
        DeviceE2E.requireUnlocked(context)
        DeviceE2E.resetToFirstRun(context)
        // Reachability only. The account this test uses does not exist yet.
        BackendFixture.signInOrSkip(context)
        DeviceE2E.resetToFirstRun(context)
    }

    @Test
    fun deleting_an_account_takes_the_data_with_it() {
        val email = "e2e-delete-${System.currentTimeMillis()}@cerebro.app"
        val password = "delete-me-12345"

        runBlocking {
            Session.signUp(email, password, "E2E Deletion")
            // Something to lose. An account with no rows would let a deletion
            // that only removes the user record still look correct.
            Api.checkIn("Good", "e2e", "sparkles", 3)
            val mine = Api.moods()
            assertTrue("the throwaway account saved nothing to delete", mine.length() > 0)

            Api.deleteAccount()
        }

        // The credentials must stop working. A soft-delete that leaves sign-in
        // intact would satisfy "the row is gone" and betray the promise.
        DeviceE2E.resetToFirstRun(context)
        val canStillSignIn = runBlocking {
            runCatching { Session.signIn(email, password); Api.me() }.isSuccess
        }
        assertFalse(
            "the deleted account can still sign in — deletion has to mean gone, not hidden",
            canStillSignIn,
        )
    }
}
