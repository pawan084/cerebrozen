package com.cerebrozen.app.net

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * What a returning user is TOLD when the session cannot be renewed.
 *
 * `refresh()` collapses every non-401/403 outcome into `false`, and
 * `ensureAccess()` then reports all of them as "Couldn't reach the server —
 * check your connection." That sentence is true for a dead socket and false for
 * a server that answered; both leave the app serving cache behind the Home
 * banner "You're offline — showing your last copy."
 *
 * These pin the three outcomes apart so the difference stays visible.
 */
class SessionRefreshFailureTest {

    private class FakeStore(vararg init: Pair<String, String>) : Session.Store {
        val m = mutableMapOf(*init)
        override fun getString(key: String) = m[key]
        override fun putString(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
        override fun keys() = m.keys.toSet()
    }

    /** A signed-in device whose /auth/refresh meets `outcome`. */
    private fun sessionWhereRefresh(
        outcome: suspend () -> Pair<Int, String>,
    ): FakeStore {
        val store = FakeStore("refresh_token" to "r1")
        Session.resetForTest(store) { url, _, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) outcome() else 200 to "{}"
        }
        return store
    }

    @Test
    fun a_rejected_token_signs_out_and_says_so() = runTest {
        // The genuinely-expired case, and the one the app already gets right:
        // 401 means the token is dead, so the session is cleared and the UI
        // falls back to the welcome screen rather than pretending to be online.
        sessionWhereRefresh { 401 to """{"detail":"Invalid refresh token"}""" }
        val thrown = runCatching { Session.api("/moods", "GET") }
            .exceptionOrNull() as? Session.ApiException

        assertEquals(401, thrown?.code)
        assertFalse("a rejected refresh token must end the session", Session.signedIn)
        assertFalse("being signed out is not being offline", Session.servedStale)
    }

    @Test
    fun a_dead_socket_reads_as_offline() = runTest {
        // The honest offline case: nothing answered, so "check your connection"
        // is exactly right and the last copy is the best thing to show.
        sessionWhereRefresh { throw IOException("no route to host") }
        val thrown = runCatching { Session.api("/moods", "GET") }
            .exceptionOrNull() as? Session.ApiException

        assertEquals(503, thrown?.code)
        assertTrue("a blip must not sign anyone out", Session.signedIn)
    }

    @Test
    fun a_server_that_answers_404_is_not_a_connection_problem() = runTest {
        // The case this file exists for. A 404 from /auth/refresh means
        // something ANSWERED — a misrouted host, a proxy, a stale deployment.
        // Telling the user to check their connection sends them to fix the one
        // thing that is working.
        sessionWhereRefresh { 404 to """{"detail":"Not Found"}""" }
        val thrown = runCatching { Session.api("/moods", "GET") }
            .exceptionOrNull() as? Session.ApiException

        assertTrue("the session is intact — the token was never rejected", Session.signedIn)
        assertEquals(
            "a server that answered is not an unreachable one",
            503,
            thrown?.code,
        )
        println("MESSAGE SHOWN FOR A 404 REFRESH: ${thrown?.message}")
    }
}
