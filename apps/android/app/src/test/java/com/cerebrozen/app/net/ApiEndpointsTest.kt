package com.cerebrozen.app.net

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Contract tests for the typed [Api] helpers and the [Session.api] edge paths
 * (honest 503 vs "Signed out", 5xx-vs-4xx stale-cache policy, auth flavors).
 * Every endpoint helper is exercised against a scripted transport so the
 * path/method/body it emits — the cross-stack contract — is pinned.
 */
class ApiEndpointsTest {

    private val tokens = """{"access_token":"a1","refresh_token":"r1"}"""

    private class FakeStore(vararg init: Pair<String, String>) : Session.Store {
        val m = mutableMapOf(*init)
        override fun getString(key: String) = m[key]
        override fun putString(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
        override fun keys() = m.keys.toSet()
    }

    private data class Req(val path: String, val method: String, val body: String?, val auth: String?)

    /** Signed-in session over a router keyed by "METHOD /path". */
    private fun script(routes: Map<String, String>, log: MutableList<Req> = mutableListOf()): MutableList<Req> {
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { url, method, body, _, auth ->
            val path = url.removePrefix(com.cerebrozen.app.BuildConfig.API_BASE_URL)
            log.add(Req(path, method, body, auth))
            if (path == "/auth/refresh") return@resetForTest 200 to tokens
            val hit = routes["$method $path"] ?: throw AssertionError("unscripted: $method $path")
            200 to hit
        }
        return log
    }

    // ── Auth flavors not covered elsewhere ─────────────────────────────

    @Test
    fun signUp_posts_json_and_signs_in() = runTest {
        val store = FakeStore()
        Session.resetForTest(store) { url, method, body, contentType, _ ->
            assertTrue(url.endsWith("/auth/signup"))
            assertEquals("POST", method)
            assertEquals("application/json", contentType)
            val json = JSONObject(body!!)
            assertEquals("e@x.com", json.getString("email"))
            assertEquals("Pia", json.getString("name"))
            200 to tokens
        }
        Session.signUp("e@x.com", "pw12345", "Pia")
        assertTrue(Session.signedIn)
        assertEquals("r1", store.getString("refresh_token"))
    }

    @Test
    fun otp_request_then_verify_signs_in() = runTest {
        val store = FakeStore()
        val seen = mutableListOf<String>()
        Session.resetForTest(store) { url, _, body, _, auth ->
            assertNull("OTP endpoints are unauthenticated", auth)
            seen.add(url.substringAfter("http://").substringAfter("/"))
            when {
                url.endsWith("/auth/otp/request") -> {
                    assertEquals("e@x.com", JSONObject(body!!).getString("email"))
                    200 to """{"sent":true}"""
                }
                url.endsWith("/auth/otp/verify") -> {
                    assertEquals("123456", JSONObject(body!!).getString("code"))
                    200 to tokens
                }
                else -> throw AssertionError(url)
            }
        }
        Session.otpRequest("e@x.com")
        assertFalse("requesting a code must not sign in", Session.signedIn)
        Session.otpVerify("e@x.com", "123456")
        assertTrue(Session.signedIn)
        assertEquals(2, seen.size)
    }

    @Test
    fun signInWithGoogle_exchanges_the_id_token() = runTest {
        val store = FakeStore()
        Session.resetForTest(store) { url, method, body, _, _ ->
            assertTrue(url.endsWith("/auth/google"))
            assertEquals("POST", method)
            val json = JSONObject(body!!)
            assertEquals("gid-token", json.getString("id_token"))
            assertEquals("Pia", json.getString("name"))
            200 to tokens
        }
        Session.signInWithGoogle("gid-token", "Pia")
        assertTrue(Session.signedIn)
    }

    // ── Session.api edge paths ─────────────────────────────────────────

    @Test
    fun write_while_signed_in_but_offline_reports_503_not_signed_out() = runTest {
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { _, _, _, _, _ ->
            throw IOException("offline")
        }
        try {
            Session.api("/moods", "POST", JSONObject().put("mood", "calm"))
            throw AssertionError("expected ApiException")
        } catch (e: Session.ApiException) {
            assertEquals(503, e.code)
            assertTrue("honest message, not 'Signed out'", e.message!!.contains("reach the server"))
        }
        assertTrue(Session.signedIn)
    }

    @Test
    fun api_without_any_session_reports_signed_out() = runTest {
        Session.resetForTest(FakeStore()) { _, _, _, _, _ -> 200 to "{}" }
        try {
            Session.api("/moods")
            throw AssertionError("expected ApiException")
        } catch (e: Session.ApiException) {
            assertEquals(401, e.code)
            assertEquals("Signed out", e.message)
        }
    }

    @Test
    fun get_serves_stale_on_5xx_but_never_on_4xx() = runTest {
        var status = 200
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { url, _, _, _, _ ->
            when {
                url.endsWith("/auth/refresh") -> 200 to tokens
                else -> status to if (status == 200) """{"ok":1}""" else """{"detail":"boom"}"""
            }
        }
        assertEquals("""{"ok":1}""", Session.api("/me"))   // primes the cache
        status = 500
        assertEquals("a 5xx must fall back to the last copy", """{"ok":1}""", Session.api("/me"))
        assertTrue(Session.servedStale)
        status = 404
        try {
            Session.api("/me")
            throw AssertionError("a 4xx is a real answer — must not be masked by stale data")
        } catch (e: Session.ApiException) {
            assertEquals(404, e.code)
            assertEquals("boom", e.message)
        }
    }

    @Test
    fun non_json_error_body_falls_back_to_a_generic_detail() = runTest {
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { url, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) 200 to tokens else 502 to "<html>bad gateway</html>"
        }
        try {
            Session.api("/x", "POST", JSONObject())
            throw AssertionError("expected ApiException")
        } catch (e: Session.ApiException) {
            assertEquals(502, e.code)
            assertEquals("Request failed (502)", e.message)
        }
    }

    // ── The typed endpoint helpers (paths/methods/bodies pinned) ─────────

    @Test
    fun read_helpers_hit_their_documented_paths() = runTest {
        val log = script(
            mapOf(
                "GET /users/me" to """{"email":"e@x.com"}""",
                "GET /users/me/streak" to """{"current":3}""",
                "GET /moods" to """[{"mood":"calm"}]""",
                "GET /journal" to "[]",
                "GET /sleep" to "[]",
                "GET /sleep/summary" to """{"avg_hours":7.5}""",
                "GET /chat" to "[]",
                "GET /content?kind=meditation" to "[]",
                "GET /insights/weekly" to """{"summary":"ok"}""",
                "GET /insights/patterns" to """{"patterns":[]}""",
                "GET /users/me/consent" to """{"share_data":false}""",
                "GET /users/me/export" to """{"everything":true}""",
                "GET /voice/status" to """{"stt":false,"tts":false}""",
            ),
        )
        assertEquals("e@x.com", Api.me().getString("email"))
        assertEquals(3, Api.streak().getInt("current"))
        assertEquals(1, Api.moods().length())
        assertEquals(0, Api.journal().length())
        assertEquals(0, Api.sleepLogs().length())
        assertEquals(7.5, Api.sleepSummary().getDouble("avg_hours"), 0.0)
        assertEquals(0, Api.chat().length())
        assertEquals(0, Api.content("meditation").length())
        assertEquals("ok", Api.insightsWeekly().getString("summary"))
        assertEquals(0, Api.patterns().getJSONArray("patterns").length())
        assertFalse(Api.consent().getBoolean("share_data"))
        assertTrue(Api.exportData().contains("everything"))
        assertFalse(Api.voiceStatus().getBoolean("stt"))
        assertTrue("every read used GET + a bearer token",
            log.filter { it.path != "/auth/refresh" }.all { it.method == "GET" && it.auth == "a1" })
    }

    @Test
    fun write_helpers_post_the_documented_payloads() = runTest {
        val log = script(
            mapOf(
                "POST /moods" to """{"id":"m1"}""",
                "POST /journal" to """{"id":"j1"}""",
                "POST /sleep" to """{"id":"s1"}""",
                "POST /chat/messages" to """{"reply":"hi"}""",
                "POST /assessment/topics" to """{"topics":[]}""",
                "POST /users/me/attest" to """{"ok":true}""",
            ),
        )
        Api.checkIn("calm", "note", "sun.max", 3)
        Api.createJournal("Title", "Body")
        Api.logSleep("2026-07-11", "23:00", "07:00", 4)
        Api.sendChat("hello")
        Api.starters()
        Api.attest()

        val byPath = log.filter { it.path != "/auth/refresh" }.associateBy { it.path }
        JSONObject(byPath.getValue("/moods").body!!).let {
            assertEquals("calm", it.getString("mood"))
            assertEquals(3, it.getInt("intensity"))
        }
        JSONObject(byPath.getValue("/journal").body!!).let {
            assertEquals("Title", it.getString("title"))
            assertEquals("book", it.getString("symbol"))
        }
        JSONObject(byPath.getValue("/sleep").body!!).let {
            assertEquals("23:00", it.getString("bedtime"))
            assertEquals(0, it.getInt("awakenings"))
        }
        assertEquals("hello", JSONObject(byPath.getValue("/chat/messages").body!!).getString("text"))
        assertTrue(JSONObject(byPath.getValue("/users/me/attest").body!!).getBoolean("adult"))
    }

    @Test
    fun programs_plans_and_account_helpers_use_the_right_verbs() = runTest {
        val log = script(
            mapOf(
                "GET /programs/active" to """{"program":{"content_id":"c1","day":2}}""",
                "POST /programs/enroll" to """{"program":{"content_id":"c1"}}""",
                "DELETE /programs/active" to "{}",
                "PATCH /plans/steps/st1" to """{"steps":[]}""",
                "POST /plans/generate" to """{"steps":[]}""",
                "GET /plans/active" to """{"steps":[]}""",
                "PATCH /users/me" to """{"name":"Pia"}""",
                "PATCH /users/me/consent" to """{"share_data":true}""",
                "DELETE /users/me/memory" to """{"cleared":true}""",
                "DELETE /users/me" to "{}",
            ),
        )
        assertEquals(2, Api.activeProgram()!!.getInt("day"))
        Api.enrollProgram("c1")
        Api.leaveProgram()
        assertNotNull(Api.togglePlanStep("st1", true))
        Api.regeneratePlan()
        assertNotNull(Api.activePlan())
        assertEquals("Pia", Api.updateProfile(JSONObject().put("name", "Pia")).getString("name"))
        assertTrue(Api.updateConsent(JSONObject().put("share_data", true)).getBoolean("share_data"))
        assertTrue(Api.deleteMemory().getBoolean("cleared"))
        Api.deleteAccount()

        fun saw(method: String, path: String) = log.any { it.method == method && it.path == path }
        assertTrue("activeProgram reads GET", saw("GET", "/programs/active"))
        assertTrue("leaveProgram is a DELETE on the same path", saw("DELETE", "/programs/active"))
        assertTrue(saw("PATCH", "/plans/steps/st1"))
        assertTrue(saw("PATCH", "/users/me"))
        assertTrue(saw("DELETE", "/users/me/memory"))
        assertTrue(saw("DELETE", "/users/me"))
        assertTrue(JSONObject(log.first { it.path == "/plans/steps/st1" }.body!!).getBoolean("done"))
    }

    @Test
    fun activeProgram_is_null_when_none_and_activePlan_swallows_failure() = runTest {
        script(mapOf("GET /programs/active" to """{"program":null}"""))
        assertNull(Api.activeProgram())
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { url, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) 200 to tokens else 404 to """{"detail":"no plan"}"""
        }
        assertNull("activePlan degrades to null instead of throwing", Api.activePlan())
    }

    @Test
    fun trustedContact_maps_blank_and_null_bodies_to_null() = runTest {
        var body = "null"
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { url, method, reqBody, _, _ ->
            when {
                url.endsWith("/auth/refresh") -> 200 to tokens
                method == "PUT" -> {
                    val json = JSONObject(reqBody!!)
                    assertEquals("Mum", json.getString("name"))
                    assertEquals("sms", json.getString("method"))
                    assertTrue(json.getBoolean("notify_consent"))
                    200 to """{"name":"Mum"}"""
                }
                else -> 200 to body
            }
        }
        assertNull(Api.trustedContact())
        assertEquals("Mum", Api.setTrustedContact("Mum", "sms", "+65 8123").getString("name"))
        // A blank body would JSON-crash without the isBlank guard.
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { url, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) 200 to tokens else 200 to ""
        }
        assertNull(Api.trustedContact())
    }

    @Test
    fun oracleAvailable_reads_the_flag_and_defaults_false_on_failure() = runTest {
        script(mapOf("GET /oracle/status" to """{"available":true}"""))
        assertTrue(Api.oracleAvailable())
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { url, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) 200 to tokens else throw IOException("offline")
        }
        assertFalse("no network → Oracle treated as unavailable", Api.oracleAvailable())
    }
}
