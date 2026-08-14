package com.cerebrozen.app.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.cerebrozen.app.R
import com.cerebrozen.app.net.Session
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Premium has three states, and the one that matters is the one that shows no price.
 *
 * The screen used to show the same price list to everyone. A member whose employer
 * sponsors their seat was therefore invited to buy what they already have — and on
 * iOS, offered a cancel link for a subscription that does not exist. The fix reads
 * the server's `sponsored` flag, which is a different question from the tier: what
 * is unlocked, versus who paid for it.
 *
 * These render the real screen rather than testing the branch expression, because
 * the failure being guarded against is *visual* — a price appearing in front of
 * someone who cannot buy anything. The negative assertions are the point; the
 * positive ones only prove the right branch was taken.
 *
 * `Api.me()` is not reachable from a Robolectric JVM test, so every case here
 * exercises the offline path: the profile read fails, `runCatching` swallows it,
 * and the screen falls back to the entitlement `Session` remembered. That is
 * deliberate — it is exactly the path that would have demoted a sponsored member
 * back to a price list before the cache existed, and it is the state a member sees
 * on a train.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PremiumScreenStateTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val res = context.resources

    @Before
    fun setUp() {
        Session.init(context)
        // Each case seeds its own entitlement; start from nothing known so a
        // leftover value from a previous test cannot decide the branch.
        Session.signOut()
    }

    @Test
    fun `a sponsored member is never shown a price`() {
        Session.rememberEntitlement("premium", sponsored = true)

        compose.setContent { PremiumScreen(onBack = {}) }

        // The regression this whole change exists to prevent.
        compose.onNodeWithText(res.getString(R.string.premium_annual_price)).assertDoesNotExist()
        compose.onNodeWithText(res.getString(R.string.premium_monthly_price)).assertDoesNotExist()
        compose.onNodeWithText(res.getString(R.string.premium_intro)).assertDoesNotExist()
        // Anchored to the sponsored branch on purpose. Mutation-testing this file
        // (forcing the screen to ignore the `sponsored` flag) left the three
        // assertions above passing: a member cached as `premium` falls through to
        // the bought-elsewhere state, which also shows no price. Without this line
        // the test's name promises more than it checks — it would have caught
        // "sold to a premium member", not "sold to a sponsored member".
        compose.onNodeWithText(res.getString(R.string.premium_sponsored_intro)).assertIsDisplayed()
    }

    @Test
    fun `a sponsored member is told who pays and what they can see`() {
        Session.rememberEntitlement("premium", sponsored = true)

        compose.setContent { PremiumScreen(onBack = {}) }

        // Replacing the price list with silence would also pass the test above.
        // What belongs there is the question the screen starts raising the moment
        // an employer is involved.
        compose.onNodeWithText(res.getString(R.string.premium_sponsored_intro)).assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.premium_sponsored_seen_title)).assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.premium_sponsored_ends_title)).assertIsDisplayed()
    }

    @Test
    fun `a member who bought premium elsewhere is not sold it again`() {
        Session.rememberEntitlement("premium", sponsored = false)

        compose.setContent { PremiumScreen(onBack = {}) }

        compose.onNodeWithText(res.getString(R.string.premium_annual_price)).assertDoesNotExist()
        compose.onNodeWithText(res.getString(R.string.premium_active_intro)).assertIsDisplayed()
        // Android can neither sell nor cancel a subscription, so the one useful
        // thing this state can do is say where it can be changed.
        compose.onNodeWithText(res.getString(R.string.premium_active_manage_title)).assertIsDisplayed()
    }

    @Test
    fun `a free member still gets the paywall`() {
        // The widening must not have broken the case the screen was built for.
        Session.rememberEntitlement("free", sponsored = false)

        compose.setContent { PremiumScreen(onBack = {}) }

        compose.onNodeWithText(res.getString(R.string.premium_annual_price)).assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.premium_monthly_price)).assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.premium_sponsored_intro)).assertDoesNotExist()
    }

    @Test
    fun `an account with nothing remembered is treated as free, not as premium`() {
        // signOut() in setUp cleared the cache. The safe default for an unknown
        // entitlement is the paywall: showing "your organisation pays for this"
        // to someone whose organisation does not is the worse of the two errors.
        compose.setContent { PremiumScreen(onBack = {}) }

        compose.onNodeWithText(res.getString(R.string.premium_annual_price)).assertIsDisplayed()
        compose.onNodeWithText(res.getString(R.string.premium_sponsored_intro)).assertDoesNotExist()
    }
}
