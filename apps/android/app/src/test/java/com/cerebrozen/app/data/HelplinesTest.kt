package com.cerebrozen.app.data

import com.cerebrozen.app.net.Session
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The client half of the crisis-helpline contract.
 *
 * The bug these guard against: the app hardcoded one country's numbers into the crisis
 * screens and showed them to everyone, while Settings offered a region picker whose answer
 * nothing read. A user in the UK picked "GB" and still saw India's Tele-MANAS.
 *
 * So the properties pinned here are about what happens when things go WRONG — because the
 * reason the numbers were inlined in the first place was that a crisis screen must render
 * offline. It still must, and it must do so without naming a country we haven't confirmed.
 */
class HelplinesTest {

    private fun rows(vararg json: String) = JSONArray("[${json.joinToString(",")}]")

    private val good = """{"name":"Samaritans","detail":"24/7","target":"116123","kind":"tel"}"""

    // ── the floor names no country ───────────────────────────────────────────

    @Test
    fun `the offline fallback never names a country`() {
        // The whole bug in one assertion. If someone adds "the useful India numbers" back
        // to NEUTRAL, this fails — NEUTRAL is shown when we do NOT know the region, and a
        // confident guess there is exactly what hands a person a dead number.
        val targets = Helplines.NEUTRAL.map { it.target }
        assertEquals(listOf("https://findahelpline.com"), targets)
    }

    @Test
    fun `the offline fallback is never empty`() {
        assertTrue("a crisis screen with no rows is the worst outcome", Helplines.NEUTRAL.isNotEmpty())
        assertTrue(Helplines.NEUTRAL.all { it.target.isNotBlank() })
    }

    @Test
    fun `the offline fallback matches what the engine serves for an unknown region`() {
        // Mirrors app/safety/helplines.py::_INTERNATIONAL. Two copies of one list is a
        // trap; this is the seam that catches them drifting apart.
        val n = Helplines.NEUTRAL.single()
        assertEquals("https://findahelpline.com", n.target)
        assertEquals("url", n.kind)
        assertTrue(n.isUrl)
    }

    // ── parsing is defensive: a bad row must not become a dead tap ────────────

    @Test
    fun `it parses the engine's rows`() {
        val parsed = Helplines.parse(rows(good))
        assertEquals(1, parsed.size)
        assertEquals("Samaritans", parsed[0].name)
        assertEquals("116123", parsed[0].target)
        assertFalse(parsed[0].isUrl)
    }

    @Test
    fun `it drops rows with no target rather than render a dead tap`() {
        val parsed = Helplines.parse(rows(good, """{"name":"X","detail":"y","target":"","kind":"tel"}"""))
        assertEquals(1, parsed.size)
    }

    @Test
    fun `it drops rows with an unknown kind rather than guess how to act`() {
        // kind decides dial-vs-open. Guessing wrong on a crisis screen is not recoverable
        // by the user, so an unrecognised kind is dropped, not defaulted.
        val parsed = Helplines.parse(rows(good, """{"name":"X","detail":"y","target":"123","kind":"smoke-signal"}"""))
        assertEquals(1, parsed.size)
    }

    @Test
    fun `it survives junk without throwing`() {
        assertEquals(0, Helplines.parse(JSONArray()).size)
        assertEquals(0, Helplines.parse(rows(""""not-an-object"""")).size)
        assertEquals(0, Helplines.parse(rows("null")).size)
    }

    @Test
    fun `isUrl follows kind, not the shape of the target`() {
        val u = Helpline("n", "d", "https://x.example", "url")
        val t = Helpline("n", "d", "988", "tel")
        assertTrue(u.isUrl)
        assertFalse(t.isUrl)
    }

    // ── load(): every failure path lands on a dialable screen ────────────────

    private class FakeStore(vararg init: Pair<String, String>) : Session.Store {
        val m = mutableMapOf(*init)
        override fun getString(key: String) = m[key]
        override fun putString(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
        override fun keys() = m.keys.toSet()
    }

    /** Signed-in session whose transport answers /v1/safety/helplines with [body]
     *  (or throws [boom]). Records the URLs asked for. */
    private fun transport(body: String? = null, boom: Exception? = null): MutableList<String> {
        val seen = mutableListOf<String>()
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { url, _, _, _, _ ->
            seen.add(url)
            if (url.endsWith("/auth/refresh")) return@resetForTest 200 to """{"access_token":"a1","refresh_token":"r1"}"""
            boom?.let { throw it }
            200 to (body ?: "")
        }
        return seen
    }

    @Test
    fun `load returns the engine's rows for the region`() = runTest {
        transport("""{"helplines":[$good]}""")
        val rows = Helplines.load("GB")
        assertEquals(1, rows.size)
        assertEquals("116123", rows[0].target)
    }

    @Test
    fun `load asks the engine for the caller's region`() = runTest {
        val seen = transport("""{"helplines":[$good]}""")
        Helplines.load("GB")
        assertTrue("region must reach the engine: $seen", seen.any { it.contains("/v1/safety/helplines?region=GB") })
    }

    @Test
    fun `load url-encodes a hostile region rather than building a broken url`() = runTest {
        val seen = transport("""{"helplines":[$good]}""")
        Helplines.load("a b&c=d")
        assertTrue("must be encoded: $seen", seen.any { it.contains("region=a+b%26c%3Dd") })
    }

    @Test
    fun `load falls back to NEUTRAL when the network is down`() = runTest {
        // THE point of this file. Offline is why the numbers were hardcoded originally;
        // it must now degrade to a country-neutral finder, not to a blank screen.
        transport(boom = IOException("airplane mode"))
        assertEquals(Helplines.NEUTRAL, Helplines.load("GB"))
    }

    @Test
    fun `load falls back to NEUTRAL on a malformed body`() = runTest {
        transport("not json at all")
        assertEquals(Helplines.NEUTRAL, Helplines.load("GB"))
    }

    @Test
    fun `load falls back to NEUTRAL when the engine returns an empty list`() = runTest {
        // A 200 with nothing in it would otherwise render an empty crisis screen — the
        // worst outcome available, and it would look like a working request.
        transport("""{"helplines":[]}""")
        assertEquals(Helplines.NEUTRAL, Helplines.load("GB"))
    }

    @Test
    fun `load falls back to NEUTRAL when every row is malformed`() = runTest {
        transport("""{"helplines":[{"name":"x","detail":"y","target":"","kind":"tel"}]}""")
        assertEquals(Helplines.NEUTRAL, Helplines.load("GB"))
    }

    @Test
    fun `load never throws`() = runTest {
        transport(boom = RuntimeException("anything at all"))
        assertTrue(Helplines.load("").isNotEmpty())
    }
}
