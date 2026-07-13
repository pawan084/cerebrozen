package com.cerebrozen.app.ui.screens

import com.cerebrozen.app.net.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the post-signup personalization race: Session.signUp
 * flips signedIn, the root composition swaps away from Onboarding, and the
 * screen's rememberCoroutineScope() is cancelled mid-chain — which used to
 * kill the consent/profile/first-check-in writes before they reached the
 * network. [signUpThenPersonalize] shields them with NonCancellable.
 */
class AuthFlowTest {

    private val tokens = """{"access_token":"a1","refresh_token":"r1"}"""

    private class FakeStore : Session.Store {
        val m = mutableMapOf<String, String>()
        override fun getString(key: String) = m[key]
        override fun putString(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
        override fun keys() = m.keys.toSet()
    }

    @Test
    fun post_signup_writes_survive_the_signedIn_composition_swap() = runTest {
        val calls = mutableListOf<String>()
        Session.resetForTest(FakeStore()) { url, _, _, _, _ ->
            // The real transport suspends (withContext(Dispatchers.IO)), which is
            // where a cancelled caller dies on-device — model that suspension
            // point with yield() so cancellation is actually observed here.
            yield()
            calls.add(url)
            200 to if (url.endsWith("/auth/signup")) tokens else "{}"
        }
        // Stand-in for rememberCoroutineScope(): the scope Compose cancels when
        // the Onboarding composition is disposed.
        val scope = CoroutineScope(coroutineContext + Job())
        val job = scope.launch {
            signUpThenPersonalize(
                signUp = {
                    Session.signUp("e@x.com", "pw", "N")
                    // signedIn just flipped → root recomposes → screen disposed.
                    // Cancel at the earliest possible moment, like Compose does.
                    scope.cancel()
                },
                personalize = {
                    // Same best-effort shape as Onboarding's onAccountCreated.
                    runCatching { Session.api("/users/me/attest", "POST", JSONObject().put("adult", true)) }
                    runCatching { Session.api("/users/me/consent", "PATCH", JSONObject()) }
                    runCatching { Session.api("/moods", "POST", JSONObject().put("mood", "Anxious")) }
                },
            )
        }
        job.join()
        assertTrue(Session.signedIn)
        assertTrue("signup must land", calls.any { it.endsWith("/auth/signup") })
        assertTrue("attest must reach the network despite cancellation", calls.any { it.endsWith("/users/me/attest") })
        assertTrue("consent must reach the network despite cancellation", calls.any { it.endsWith("/users/me/consent") })
        assertTrue("first check-in must reach the network despite cancellation", calls.any { it.endsWith("/moods") })
    }
}
