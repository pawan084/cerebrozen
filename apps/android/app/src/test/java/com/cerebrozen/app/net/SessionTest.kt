package com.cerebrozen.app.net

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for the auth/session logic — the network-blip-shouldn't-sign-out
 * fix, the offline read-cache, token rotation, and sign-out clearing. Uses the
 * Store + http seams so nothing touches Android or the network.
 */
class SessionTest {

    private val tokens = """{"access_token":"a1","refresh_token":"r1"}"""

    private class FakeStore(vararg init: Pair<String, String>) : Session.Store {
        val m = mutableMapOf(*init)
        override fun getString(key: String) = m[key]
        override fun putString(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
        override fun keys() = m.keys.toSet()
    }

    @Test
    fun signIn_stores_tokens_and_flips_signedIn() = runTest {
        val store = FakeStore()
        Session.resetForTest(store) { url, _, _, _, _ ->
            assertTrue(url.endsWith("/auth/login"))
            200 to tokens
        }
        Session.signIn("e@x.com", "pw")
        assertTrue(Session.signedIn)
        assertEquals("r1", store.getString("refresh_token"))
    }

    @Test
    fun get_is_cached_and_served_when_offline() = runTest {
        val store = FakeStore("refresh_token" to "r1")
        var online = true
        Session.resetForTest(store) { url, _, _, _, _ ->
            if (!online) throw IOException("offline")
            when {
                url.endsWith("/auth/refresh") -> 200 to tokens
                url.endsWith("/me") -> 200 to """{"ok":1}"""
                else -> 200 to "{}"
            }
        }
        assertEquals("""{"ok":1}""", Session.api("/me"))   // online → caches
        online = false
        assertEquals("""{"ok":1}""", Session.api("/me"))   // offline → served from cache
        assertTrue("a network failure must not sign the user out", Session.signedIn)
    }

    @Test
    fun network_blip_during_refresh_does_not_sign_out() = runTest {
        val store = FakeStore("refresh_token" to "r1")
        Session.resetForTest(store) { url, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) throw IOException("offline") else 200 to "{}"
        }
        runCatching { Session.api("/x") }   // cold start, refresh fails on network
        assertTrue("offline refresh must keep the session", Session.signedIn)
        assertEquals("r1", store.getString("refresh_token"))
    }

    @Test
    fun expired_refresh_401_signs_out() = runTest {
        val store = FakeStore("refresh_token" to "r1")
        Session.resetForTest(store) { url, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) 401 to """{"detail":"expired"}""" else 200 to "{}"
        }
        runCatching { Session.api("/x") }
        assertFalse("a rejected refresh must end the session", Session.signedIn)
    }

    @Test
    fun expired_access_401_rotates_then_retries() = runTest {
        val store = FakeStore("refresh_token" to "r1")
        var meCalls = 0
        Session.resetForTest(store) { url, _, _, _, _ ->
            when {
                url.endsWith("/auth/refresh") -> 200 to tokens
                url.endsWith("/me") -> {
                    meCalls++
                    if (meCalls == 1) 401 to """{"detail":"stale"}""" else 200 to """{"ok":1}"""
                }
                else -> 200 to "{}"
            }
        }
        // First /me 401s → one rotation → retry succeeds.
        assertEquals("""{"ok":1}""", Session.api("/me"))
        assertEquals("the 401 must trigger exactly one retry", 2, meCalls)
        assertTrue(Session.signedIn)
    }

    @Test
    fun signOut_clears_refresh_and_cache() = runTest {
        val store = FakeStore("refresh_token" to "r1", "cache:/me" to """{"ok":1}""")
        Session.resetForTest(store) { _, _, _, _, _ -> 200 to "{}" }
        Session.signOut()
        assertFalse(Session.signedIn)
        assertNull(store.getString("refresh_token"))
        assertNull("offline cache must be wiped on sign-out (privacy)", store.getString("cache:/me"))
    }
}
