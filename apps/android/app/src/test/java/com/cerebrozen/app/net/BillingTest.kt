package com.cerebrozen.app.net

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Consumer-plan (B2C freemium) logic on [Session]: parsing /billing/me into
 * observable plan + entitlements, the checkout/cancel contract, the fail-safe
 * default (unknown → locked), and sign-out clearing. Pure JVM via the Store +
 * http seams — no Android, no network.
 */
class BillingTest {

    private val tokens = """{"access_token":"a1","refresh_token":"r1"}"""

    private class FakeStore(vararg init: Pair<String, String>) : Session.Store {
        val m = mutableMapOf(*init)
        override fun getString(key: String) = m[key]
        override fun putString(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
        override fun keys() = m.keys.toSet()
    }

    private val freeBody =
        """{"plan":"free","status":null,"entitlements":{"voice":false,"sleep":false,"coach_daily_limit":5,"programs_limit":1}}"""
    private val plusBody =
        """{"plan":"plus","status":"active","entitlements":{"voice":true,"sleep":true,"insights":true,"coach_daily_limit":null,"programs_limit":null}}"""

    @Test
    fun refreshBilling_free_keeps_everything_locked() = runTest {
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { url, _, _, _, _ ->
            when {
                url.endsWith("/auth/refresh") -> 200 to tokens
                url.endsWith("/billing/me") -> 200 to freeBody
                else -> 200 to "{}"
            }
        }
        Session.refreshBilling()
        assertEquals("free", Session.plan)
        assertFalse(Session.isPlus)
        assertFalse(Session.entitled("voice"))
        assertFalse(Session.entitled("sleep"))
    }

    @Test
    fun refreshBilling_plus_unlocks_features() = runTest {
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { url, _, _, _, _ ->
            when {
                url.endsWith("/auth/refresh") -> 200 to tokens
                url.endsWith("/billing/me") -> 200 to plusBody
                else -> 200 to "{}"
            }
        }
        Session.refreshBilling()
        assertEquals("plus", Session.plan)
        assertTrue(Session.isPlus)
        assertTrue(Session.entitled("voice"))
        assertTrue(Session.entitled("insights"))
    }

    @Test
    fun startPlus_posts_checkout_body_and_unlocks() = runTest {
        var method = ""; var path = ""; var body: String? = null
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { url, m, b, _, _ ->
            when {
                url.endsWith("/auth/refresh") -> 200 to tokens
                url.endsWith("/billing/checkout") -> { method = m; path = "/billing/checkout"; body = b; 200 to plusBody }
                else -> 200 to "{}"
            }
        }
        Session.startPlus("yearly")
        assertEquals("POST", method)
        assertEquals("/billing/checkout", path)
        val j = JSONObject(body!!)
        assertEquals("plus", j.getString("plan"))
        assertEquals("yearly", j.getString("interval"))
        assertTrue(Session.isPlus)
    }

    @Test
    fun cancelPlus_posts_cancel_and_reverts_to_free() = runTest {
        var method = ""
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { url, m, _, _, _ ->
            when {
                url.endsWith("/auth/refresh") -> 200 to tokens
                url.endsWith("/billing/cancel") -> { method = m; 200 to freeBody }
                else -> 200 to "{}"
            }
        }
        Session.cancelPlus()
        assertEquals("POST", method)
        assertEquals("free", Session.plan)
        assertFalse(Session.isPlus)
    }

    @Test
    fun signOut_clears_plan_and_entitlements() = runTest {
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { url, _, _, _, _ ->
            when {
                url.endsWith("/auth/refresh") -> 200 to tokens
                url.endsWith("/billing/me") -> 200 to plusBody
                else -> 200 to "{}"
            }
        }
        Session.refreshBilling()
        assertTrue(Session.isPlus)
        Session.signOut()
        assertEquals("free", Session.plan)
        assertFalse(Session.entitled("voice"))
    }
}
