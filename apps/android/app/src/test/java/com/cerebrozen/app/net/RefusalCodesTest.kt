package com.cerebrozen.app.net

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The refusals the server sends, told apart by CODE rather than by status.
 *
 * Three different 429s now mean three different things — the free-tier cap, a
 * daily abuse ceiling, and slowapi's "slow down" — and one of the wrong answers
 * is manipulative: offering an upgrade for a ceiling that is identical on every
 * tier is selling a fix that is not for sale.
 *
 * The queue tests are the ones that were load-bearing. None of these exceptions
 * is an [Session.ApiException], so before [Session.RefusalException] existed
 * they fell through [Outbox]'s catch-all — the branch meaning "no connectivity
 * at all" — and the write was queued. A refusal would then be retried on every
 * drain, forever, while the person was told it was saved and would sync. That
 * was already true of the free-tier cap before today.
 */
class RefusalCodesTest {

    private class FakeStore(vararg seed: Pair<String, String>) : Session.Store {
        val map = mutableMapOf(*seed)
        override fun getString(key: String) = map[key]
        override fun putString(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
        override fun keys() = map.keys.toSet()
    }

    private val tokens =
        """{"access_token":"a1","refresh_token":"r1","token_type":"bearer"}"""

    private fun respondingWith(status: Int, body: String) {
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { url, _, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) 200 to tokens else status to body
        }
    }

    // ── Parsed by code, never by status ───────────────────────────────────

    @Test
    fun aDailyCeilingArrivesTypedAndCarriesItsFeature() = runTest {
        respondingWith(
            429,
            """{"detail":{"code":"daily_ceiling","feature":"voice_tts",""" +
                """"message":"You've hit the daily limit for this.","limit":2000,""" +
                """"resets_at":"2026-08-24T00:00:00Z"}}""",
        )
        try {
            Session.api("/voice/tts", "POST", JSONObject().put("text", "hi"))
            throw AssertionError("expected DailyCeilingException")
        } catch (e: Session.DailyCeilingException) {
            assertEquals("voice_tts", e.feature)
            assertEquals(2000, e.limit)
            assertEquals("2026-08-24T00:00:00Z", e.resetsAtUtc)
        }
    }

    @Test
    fun aDailyCeilingIsNotTheFreeCap() = runTest {
        // Same status, opposite remedy: the cap means "upgrade and this goes
        // away", the ceiling means "come back tomorrow". A screen that caught
        // the wrong one would offer to sell a fix that does not exist.
        respondingWith(
            429,
            """{"detail":{"code":"daily_ceiling","feature":"oracle_turn","limit":500}}""",
        )
        try {
            Session.api("/oracle/messages", "POST", JSONObject().put("text", "hi"))
            throw AssertionError("expected DailyCeilingException")
        } catch (e: Session.DailyCeilingException) {
            assertFalse(e is Session.FreeLimitException)
        }
    }

    @Test
    fun anUnconfirmedAddressArrivesTypedAndNamesTheFeature() = runTest {
        respondingWith(
            403,
            """{"detail":{"code":"email_unverified","feature":"voice",""" +
                """"message":"Confirm your email address to use this."}}""",
        )
        try {
            Session.api("/voice/tts", "POST", JSONObject().put("text", "hi"))
            throw AssertionError("expected VerificationRequiredException")
        } catch (e: Session.VerificationRequiredException) {
            // Named so a screen can say WHICH thing is waiting rather than
            // showing a generic wall.
            assertEquals("voice", e.feature)
            assertTrue(e.message!!.contains("Confirm your email"))
        }
    }

    @Test
    fun anOrdinaryThrottleStaysAPlainApiException() = runTest {
        // slowapi's key is `error`, with no `detail`, and it means "slow down".
        respondingWith(429, """{"error":"Rate limit exceeded: 10 per 1 minute"}""")
        try {
            Session.api("/auth/signup", "POST", JSONObject())
            throw AssertionError("expected ApiException")
        } catch (e: Session.ApiException) {
            assertEquals(429, e.code)
            assertTrue(e.message!!.contains("Rate limit exceeded"))
        }
    }

    @Test
    fun aPlain403IsNotMistakenForAVerificationWall() = runTest {
        // Only the code makes it one. A consent-gated 403 answers differently
        // and must not offer to resend a verification email.
        respondingWith(403, """{"detail":"AI memory is switched off in your privacy settings."}""")
        try {
            Session.api("/users/me/memory/x", "PATCH", JSONObject())
            throw AssertionError("expected ApiException")
        } catch (e: Session.ApiException) {
            assertEquals(403, e.code)
        }
    }

    // ── The queue must not keep a decision ────────────────────────────────

    @Test
    fun everyRefusalSharesTheBaseTheQueueChecks() {
        // Outbox branches on `Session.RefusalException`, so a future refusal
        // type that forgets to extend it silently becomes retryable-forever
        // again. This is the assertion that makes the base load-bearing rather
        // than decorative.
        val free = Session.FreeLimitException("m", 50, 50, "2026-08-24T00:00:00Z")
        val ceiling = Session.DailyCeilingException("m", "voice_tts", 2000, "2026-08-24T00:00:00Z")
        val verify = Session.VerificationRequiredException("m", "voice")

        assertTrue(free is Session.RefusalException)
        assertTrue(ceiling is Session.RefusalException)
        assertTrue(verify is Session.RefusalException)
        // And a transport failure is NOT one — that is the case the queue exists
        // for, and treating it as a refusal would drop somebody's write.
        assertFalse(Session.ApiException(503, "down") is Session.RefusalException)
    }

    @Test
    fun aRefusedWriteIsNotQueued() = runTest {
        // `send` used to reach its catch-all — "no connectivity at all" — and
        // enqueue the item, so the person was told it was saved and would sync
        // while the server had already decided otherwise.
        val store = FakeStore("refresh_token" to "r1")
        Session.resetForTest(store) { url, _, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) 200 to tokens
            else 403 to """{"detail":{"code":"email_unverified","feature":"plans"}}"""
        }
        Outbox.clear()

        try {
            Outbox.send("/moods", JSONObject().put("mood", "Good"))
            throw AssertionError("expected the refusal to reach the caller")
        } catch (e: Session.VerificationRequiredException) {
            // The caller sees it, which is the point.
        }
        assertEquals(0, Outbox.pending().size)
    }

    @Test
    fun aGenuineOutageIsStillQueued() = runTest {
        // The other half: the queue must still do its job. Without this the
        // test above could be satisfied by never queueing anything.
        val store = FakeStore("refresh_token" to "r1")
        Session.resetForTest(store) { url, _, _, _, _, _ ->
            if (url.endsWith("/auth/refresh")) 200 to tokens
            else throw java.net.UnknownHostException("no dns")
        }
        Outbox.clear()

        Outbox.send("/moods", JSONObject().put("mood", "Good"))
        assertEquals(1, Outbox.pending().size)
    }
}
