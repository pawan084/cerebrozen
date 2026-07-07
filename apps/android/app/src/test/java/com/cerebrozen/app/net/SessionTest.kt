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

    @Test
    fun forgotPassword_posts_unauthenticated() = runTest {
        val store = FakeStore()
        var seenAuth: String? = "sentinel"
        Session.resetForTest(store) { url, method, body, _, auth ->
            assertTrue(url.endsWith("/auth/password/forgot"))
            assertEquals("POST", method)
            assertTrue(body!!.contains("e@x.com"))
            seenAuth = auth
            200 to """{"sent":true}"""
        }
        Session.forgotPassword("e@x.com")
        assertNull("reset request must carry no bearer token", seenAuth)
    }

    @Test
    fun analytics_event_posts_anonymously_with_source_and_step() = runTest {
        val store = FakeStore()
        var captured: String? = null
        var seenAuth: String? = "sentinel"
        Session.resetForTest(store) { url, method, body, _, auth ->
            assertTrue(url.endsWith("/events"))
            assertEquals("POST", method)
            captured = body; seenAuth = auth
            202 to """{"accepted":1}"""
        }
        Session.postEvent("anon-123", "onboarding_step", "welcome")
        assertNull("events are deliberately unauthenticated", seenAuth)
        assertTrue(captured!!.contains("\"anon_id\":\"anon-123\""))
        assertTrue(captured!!.contains("\"source\":\"android\""))
        assertTrue(captured!!.contains("\"name\":\"onboarding_step\""))
        assertTrue(captured!!.contains("\"step\":\"welcome\""))
    }

    @Test
    fun analytics_toggle_defaults_on_and_persists_via_store() = runTest {
        val store = FakeStore()
        Session.resetForTest(store) { _, _, _, _, _ -> 200 to "{}" }
        assertTrue("anonymous counts default on (opt-out, matches iOS)", Analytics.enabled)
        Analytics.enabled = false
        assertFalse(Analytics.enabled)
        assertEquals("false", store.getString("usage_stats_on"))
    }

    @Test
    fun funnelStepName_maps_android_steps_to_the_cross_stack_vocabulary() {
        assertEquals("welcome", funnelStepName("Welcome"))
        assertEquals("age_gate", funnelStepName("Age"))
        assertEquals("state_check", funnelStepName("State"))
        assertEquals("first_reset", funnelStepName("Reset"))
        assertEquals("notifications", funnelStepName("Notify"))
        assertEquals("signup", funnelStepName("SignUp"))
    }

    // ── Oracle SSE ──────────────────────────────────────────────────
    @Test
    fun parseSseLine_reads_data_frames_and_ignores_noise() {
        assertEquals("token", parseSseLine("""data: {"type":"token","text":"hi"}""")!!.getString("type"))
        assertNull(parseSseLine(""))                    // keep-alive blank
        assertNull(parseSseLine(": comment"))
        assertNull(parseSseLine("event: message"))
        assertNull(parseSseLine("data: not-json"))      // malformed → dropped, not thrown
    }

    @Test
    fun sse_streams_frames_in_order() = runTest {
        val store = FakeStore("refresh_token" to "r1")
        Session.resetForTest(store) { url, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) 200 to tokens else 200 to "{}"
        }
        val frames = """
            data: {"type":"token","text":"Hel"}

            data: {"type":"token","text":"lo"}

            data: {"type":"widget","widget":{"widget_kind":"breathing"}}

            data: {"type":"done","text":"Hello"}

        """.trimIndent()
        Session.sseExec = { url, body, auth ->
            assertTrue(url.endsWith("/oracle/messages"))
            assertTrue(body.contains("\"text\":\"hey\""))
            assertEquals("a1", auth)   // fresh-launch refresh ran first
            200 to frames.byteInputStream()
        }
        val seen = mutableListOf<String>()
        Session.sse("/oracle/messages", org.json.JSONObject().put("text", "hey")) { ev ->
            seen.add(ev.getString("type"))
        }
        assertEquals(listOf("token", "token", "widget", "done"), seen)
    }

    @Test
    fun sse_rotates_once_on_401_then_replays() = runTest {
        val store = FakeStore("refresh_token" to "r1")
        var refreshes = 0
        Session.resetForTest(store) { url, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) { refreshes++; 200 to tokens } else 200 to "{}"
        }
        var attempts = 0
        Session.sseExec = { _, _, _ ->
            attempts++
            if (attempts == 1) 401 to """{"detail":"stale"}""".byteInputStream()
            else 200 to "data: {\"type\":\"done\",\"text\":\"ok\"}\n\n".byteInputStream()
        }
        val seen = mutableListOf<String>()
        Session.sse("/oracle/messages", org.json.JSONObject().put("text", "x")) { seen.add(it.getString("type")) }
        assertEquals(listOf("done"), seen)
        assertEquals("one rotation retry", 2, attempts)
        assertEquals("fresh-launch + 401 retry", 2, refreshes)
    }

    // ── Cloud voice transport ───────────────────────────────────────
    @Test
    fun multipartBody_wraps_the_file_with_boundary_and_disposition() {
        val body = String(multipartBody("BX", "audio", "clip.m4a", "audio/mp4", "PAYLOAD".toByteArray()))
        assertTrue(body.startsWith("--BX\r\n"))
        assertTrue(body.contains("Content-Disposition: form-data; name=\"audio\"; filename=\"clip.m4a\""))
        assertTrue(body.contains("Content-Type: audio/mp4\r\n\r\nPAYLOAD"))
        assertTrue(body.endsWith("\r\n--BX--\r\n"))
    }

    @Test
    fun stt_uploads_multipart_and_returns_the_transcript() = runTest {
        val store = FakeStore("refresh_token" to "r1")
        Session.resetForTest(store) { url, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) 200 to tokens else 200 to "{}"
        }
        Session.binExec = { url, method, body, contentType, auth ->
            assertTrue(url.endsWith("/voice/stt"))
            assertEquals("POST", method)
            assertTrue(contentType!!.startsWith("multipart/form-data; boundary="))
            assertTrue(String(body!!).contains("filename=\"clip.m4a\""))
            assertEquals("a1", auth)
            200 to """{"transcript":"i feel calm"}""".toByteArray()
        }
        assertEquals("i feel calm", Api.stt("AUDIO".toByteArray()))
    }

    @Test
    fun tts_rotates_once_on_401_and_returns_bytes() = runTest {
        val store = FakeStore("refresh_token" to "r1")
        Session.resetForTest(store) { url, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) 200 to tokens else 200 to "{}"
        }
        var attempts = 0
        Session.binExec = { url, _, _, _, _ ->
            assertTrue(url.endsWith("/voice/tts"))
            attempts++
            if (attempts == 1) 401 to """{"detail":"stale"}""".toByteArray()
            else 200 to byteArrayOf(0x49, 0x44, 0x33)   // "ID3"
        }
        val mp3 = Api.tts("hello")
        assertEquals(3, mp3.size)
        assertEquals("one rotation retry", 2, attempts)
    }

    @Test
    fun sse_surfaces_the_error_detail_on_persistent_failure() = runTest {
        val store = FakeStore("refresh_token" to "r1")
        Session.resetForTest(store) { url, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) 200 to tokens else 200 to "{}"
        }
        Session.sseExec = { _, _, _ -> 429 to """{"detail":"Daily message limit reached"}""".byteInputStream() }
        try {
            Session.sse("/oracle/messages", org.json.JSONObject().put("text", "x")) {}
            throw AssertionError("expected ApiException")
        } catch (e: Session.ApiException) {
            assertEquals(429, e.code)
            assertEquals("Daily message limit reached", e.message)
        }
    }
}
