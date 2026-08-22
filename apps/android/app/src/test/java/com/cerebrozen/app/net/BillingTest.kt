package com.cerebrozen.app.net

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The rules about somebody's money (WC-10).
 *
 * Play auto-refunds any purchase not acknowledged within three days, so
 * acknowledgement is a decision with two opposite failure modes — keeping a
 * purchase the server refused, and losing one it accepted. These pin both, plus
 * the difference between "the server said no" and "the server did not answer",
 * which is the distinction the whole design turns on.
 *
 * No Billing SDK is involved: `Billing` talks to a `Store` seam, so the rules
 * run in CI rather than only on a handset with Play Services.
 */
class BillingTest {

    private fun purchase(
        token: String = "tok-1",
        product: String = "com.cerebrozen.premium.monthly",
        acknowledged: Boolean = false,
        pending: Boolean = false,
    ) = Billing.Purchase(
        originalJson = """{"productId":"$product","purchaseToken":"$token"}""",
        signature = "c2ln",
        purchaseToken = token,
        productId = product,
        isAcknowledged = acknowledged,
        isPending = pending,
    )

    // ── Acknowledge exactly what the server honoured ─────────────────────

    @Test
    fun `an accepted purchase is acknowledged`() = runTest {
        val acked = mutableListOf<String>()
        val report = Billing.reconcile(
            listOf(purchase()),
            verify = { Billing.Verification.ACCEPTED },
            acknowledge = { acked.add(it); true },
        )
        assertEquals(listOf("tok-1"), acked)
        assertEquals(1, report.entitled)
        assertEquals(1, report.acknowledged)
    }

    @Test
    fun `a rejected purchase is never acknowledged`() = runTest {
        // Not acknowledging is what lets Play refund it. Keeping a purchase the
        // server will not honour would take the money and give nothing back.
        val acked = mutableListOf<String>()
        val report = Billing.reconcile(
            listOf(purchase()),
            verify = { Billing.Verification.REJECTED },
            acknowledge = { acked.add(it); true },
        )
        assertTrue("a refused purchase must stay unacknowledged", acked.isEmpty())
        assertEquals(1, report.rejected)
        assertEquals(0, report.entitled)
    }

    @Test
    fun `an unreachable server neither acknowledges nor gives up`() = runTest {
        // The tunnel case. Abandoning here refunds a paying customer three days
        // later and silently takes away what they bought.
        val acked = mutableListOf<String>()
        val report = Billing.reconcile(
            listOf(purchase()),
            verify = { Billing.Verification.UNAVAILABLE },
            acknowledge = { acked.add(it); true },
        )
        assertTrue(acked.isEmpty())
        assertEquals(1, report.deferred)
        assertTrue("it must come back to this one", report.needsRetry)
    }

    @Test
    fun `a pending purchase is left completely alone`() = runTest {
        // Somebody at a kiosk halfway through paying. Verifying would ask the
        // server to grant a tier for a sale that may never complete.
        var verified = 0
        val acked = mutableListOf<String>()
        val report = Billing.reconcile(
            listOf(purchase(pending = true)),
            verify = { verified++; Billing.Verification.ACCEPTED },
            acknowledge = { acked.add(it); true },
        )
        assertEquals(0, verified)
        assertTrue(acked.isEmpty())
        assertEquals(1, report.pending)
        assertFalse("a pending sale is not a failure to retry", report.needsRetry)
    }

    // ── Idempotence: restore is the same pass, not a second feature ──────

    @Test
    fun `an already-acknowledged purchase is re-verified but not re-acknowledged`() = runTest {
        // This IS "restore purchases": a reinstall or a new device gets its
        // tier back by verifying again, and acknowledging twice is both
        // pointless and an error Play reports.
        var verified = 0
        val acked = mutableListOf<String>()
        val report = Billing.reconcile(
            listOf(purchase(acknowledged = true)),
            verify = { verified++; Billing.Verification.ACCEPTED },
            acknowledge = { acked.add(it); true },
        )
        assertEquals("the tier still has to be restored", 1, verified)
        assertTrue(acked.isEmpty())
        assertEquals(1, report.entitled)
        assertEquals(0, report.acknowledged)
    }

    @Test
    fun `a failed acknowledgement is retried rather than forgotten`() = runTest {
        // The entitlement is already granted, so this is not user-facing — but
        // Play's three-day window is still running, and an unacknowledged live
        // subscription gets refunded out from under the subscriber.
        val report = Billing.reconcile(
            listOf(purchase()),
            verify = { Billing.Verification.ACCEPTED },
            acknowledge = { false },
        )
        assertEquals(1, report.entitled)
        assertEquals(0, report.acknowledged)
        assertTrue(report.needsRetry)
    }

    @Test
    fun `several purchases are each judged on their own`() = runTest {
        val acked = mutableListOf<String>()
        val verdicts = mapOf(
            "good" to Billing.Verification.ACCEPTED,
            "forged" to Billing.Verification.REJECTED,
            "offline" to Billing.Verification.UNAVAILABLE,
        )
        val report = Billing.reconcile(
            listOf(purchase("good"), purchase("forged"), purchase("offline"), purchase("later", pending = true)),
            verify = { verdicts.getValue(it.purchaseToken) },
            acknowledge = { acked.add(it); true },
        )
        assertEquals(listOf("good"), acked)
        assertEquals(1, report.entitled)
        assertEquals(1, report.rejected)
        assertEquals(1, report.deferred)
        assertEquals(1, report.pending)
    }

    @Test
    fun `nothing to reconcile is not an error`() = runTest {
        val report = Billing.reconcile(emptyList(), { Billing.Verification.ACCEPTED }, { true })
        assertEquals(Billing.Report(), report)
        assertFalse(report.needsRetry)
    }

    // ── "Said no" vs "did not answer" ────────────────────────────────────

    @Test
    fun `a 4xx is the server having looked and refused`() {
        // 400 forged, 403 someone else's account, 409 already claimed — all
        // final, and all pointless to retry.
        for (code in listOf(400, 403, 409, 422)) {
            assertEquals(
                "HTTP $code should be final",
                Billing.Verification.REJECTED,
                Billing.verdictFor(Session.ApiException(code, "no")),
            )
        }
    }

    @Test
    fun `a 5xx or a dead socket is the server not having answered`() {
        assertEquals(
            Billing.Verification.UNAVAILABLE,
            Billing.verdictFor(Session.ApiException(503, "later")),
        )
        assertEquals(
            Billing.Verification.UNAVAILABLE,
            Billing.verdictFor(IOException("no route to host")),
        )
    }

    @Test
    fun `no error is an acceptance`() {
        assertEquals(Billing.Verification.ACCEPTED, Billing.verdictFor(null))
    }

    // ── The cross-stack contract ─────────────────────────────────────────

    @Test
    fun `the product ids match the ones the server grants tiers for`() {
        // Hand-duplicated with services/playstore.py and services/appstore.py.
        // A drift here sells a product the backend maps to `free`, which is a
        // charge with nothing delivered.
        assertEquals(
            listOf(
                "com.cerebrozen.premium.monthly",
                "com.cerebrozen.premium.annual",
                "com.cerebrozen.premiumhuman.monthly",
                "com.cerebrozen.premiumhuman.annual",
            ),
            Billing.PRODUCTS,
        )
    }

    @Test
    fun `the signed bytes are carried through untouched`() {
        // The signature is over these exact bytes. Re-serialising the JSON,
        // even into an identical object, breaks verification server-side.
        val raw = """{"productId":"com.cerebrozen.premium.annual",  "purchaseToken":"t"}"""
        val p = Billing.Purchase(raw, "sig", "t", "com.cerebrozen.premium.annual", false, false)
        assertEquals(raw, p.originalJson)
    }

    // ── The request the server actually receives ─────────────────────────

    @Test
    fun `the purchase reaches the server byte for byte`() = runTest {
        // The signature is over these exact bytes. If the client re-serialised
        // the JSON — reordering keys, dropping the spacing Play emitted — the
        // payload would still parse to the same object and would no longer
        // verify. backend/tests/test_playstore.py asserts the same contract
        // from the other side; this is the half that could break it.
        val raw = """{"productId":"com.cerebrozen.premium.annual",  "purchaseToken":"t-9"}"""
        var seenPath: String? = null
        var seenMethod: String? = null
        var seenBody: String? = null
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { url, method, body, _, _, _ ->
            when {
                url.endsWith("/auth/refresh") -> 200 to """{"access_token":"a1","refresh_token":"r1"}"""
                else -> {
                    seenPath = url
                    seenMethod = method
                    seenBody = body
                    200 to """{"subscription_tier":"premium"}"""
                }
            }
        }

        val out = Api.verifyPlayPurchase(raw, "c2lnbmF0dXJl")

        assertTrue(seenPath!!.endsWith("/users/me/subscription/verify-play"))
        assertEquals("POST", seenMethod)
        val sent = org.json.JSONObject(seenBody!!)
        assertEquals("the signed bytes must not be rewritten", raw, sent.getString("purchase_payload"))
        assertEquals("c2lnbmF0dXJl", sent.getString("purchase_signature"))
        assertEquals("premium", out.getString("subscription_tier"))
    }

    @Test
    fun `a refused purchase surfaces as the api error the verdict reads`() = runTest {
        // The 4xx path end to end: what the server says becomes REJECTED, which
        // is what stops reconcile acknowledging it.
        Session.resetForTest(FakeStore("refresh_token" to "r1")) { url, _, _, _, _, _ ->
            when {
                url.endsWith("/auth/refresh") -> 200 to """{"access_token":"a1","refresh_token":"r1"}"""
                else -> 409 to """{"detail":"This subscription is already linked to another account."}"""
            }
        }
        val error = runCatching { Api.verifyPlayPurchase("{}", "sig") }.exceptionOrNull()
        assertEquals(Billing.Verification.REJECTED, Billing.verdictFor(error))
    }

    private class FakeStore(vararg init: Pair<String, String>) : Session.Store {
        val m = mutableMapOf(*init)
        override fun getString(key: String) = m[key]
        override fun putString(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
        override fun keys() = m.keys.toSet()
    }
}
