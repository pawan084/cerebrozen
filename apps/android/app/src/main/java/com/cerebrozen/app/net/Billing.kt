package com.cerebrozen.app.net

/**
 * Play Billing rules (WC-10) — the half that is decisions rather than SDK.
 *
 * The paywall has shown prices and no buy button since audit H1, deliberately:
 * a permanently-disabled "Start free trial" walked people into a screen where
 * nothing could be bought. This is the client that makes the button real, and
 * `services/playstore.py` is the server that refuses to take its word for it.
 *
 * Nothing here imports the Billing SDK. Every SDK call lives behind [Store],
 * so the rules below — which is to say, the rules about somebody's money — run
 * on the JVM in CI instead of only on a device with Play Services.
 *
 * ## The rule that matters most: acknowledge only what the server honoured
 *
 * Play **auto-refunds any purchase not acknowledged within three days**. That
 * turns acknowledgement into a decision with two ways to get it wrong:
 *
 * * Acknowledge too eagerly — before the server accepts it — and a purchase the
 *   backend refuses is kept anyway. The user paid and got nothing, and the
 *   automatic refund that should have rescued them has been suppressed.
 * * Never acknowledge on a *transient* failure and a paying customer is
 *   refunded because their train went into a tunnel. Their money comes back,
 *   which sounds harmless, but they also silently lose the thing they bought.
 *
 * So a rejection and an outage are treated as different things — see
 * [Verification] — and only a definitive server acceptance acknowledges.
 */
object Billing {

    /**
     * The four products, hand-duplicated with `services/playstore.py`
     * `_PRODUCT_TIERS`, `services/appstore.py`, and the App Store ids
     * (ARCHITECTURE's cross-stack contract table). The SAME ids on both stores
     * so a subscriber who switches phones keeps the tier they paid for.
     */
    val PRODUCTS = listOf(
        "com.cerebrozen.premium.monthly",
        "com.cerebrozen.premium.annual",
        "com.cerebrozen.premiumhuman.monthly",
        "com.cerebrozen.premiumhuman.annual",
    )

    /**
     * One purchase, in the only shape the server can check.
     *
     * [originalJson] and [signature] are passed through **byte for byte**. A
     * re-serialised payload — even one that parses to the same object — has a
     * different byte sequence and its signature no longer verifies, which is
     * why this carries the raw string rather than a parsed model.
     */
    data class Purchase(
        val originalJson: String,
        val signature: String,
        val purchaseToken: String,
        val productId: String,
        val isAcknowledged: Boolean,
        val isPending: Boolean,
    )

    /** What the server said about a purchase. */
    enum class Verification {
        /** The server accepted it and set the tier. Safe to acknowledge. */
        ACCEPTED,

        /** A definitive no — forged, someone else's, or already claimed. Do NOT
         *  acknowledge: letting Play refund is the right end for a purchase
         *  this account will never be given. */
        REJECTED,

        /** No answer: offline, a 5xx, a timeout. Decides nothing, so it must
         *  not acknowledge AND must not give up — the purchase is left for the
         *  next reconcile, which runs on every launch. */
        UNAVAILABLE,
    }

    /** The SDK seam. Implemented by BillingBridge over Play's client. */
    interface Store {
        /** Purchases Play believes this user owns, on this device, right now. */
        suspend fun purchases(): List<Purchase>

        /** Tell Play the entitlement was delivered. Returns false if it failed. */
        suspend fun acknowledge(purchaseToken: String): Boolean
    }

    /** What one reconcile pass did — the numbers the paywall and logs read. */
    data class Report(
        val entitled: Int = 0,
        val rejected: Int = 0,
        val deferred: Int = 0,
        val pending: Int = 0,
        val acknowledged: Int = 0,
    ) {
        /** True when something is worth retrying later rather than forgetting. */
        val needsRetry: Boolean get() = deferred > 0
    }

    /**
     * Verify every purchase Play reports, and acknowledge exactly the ones the
     * server honoured.
     *
     * Runs on launch and after a purchase completes — the same pass either way,
     * which is what makes "restore purchases" not a separate feature with its
     * own bugs. Idempotent by construction: an already-acknowledged purchase is
     * still re-verified (that is how a reinstall or a new device gets its tier
     * back) but is not acknowledged twice.
     */
    suspend fun reconcile(
        purchases: List<Purchase>,
        verify: suspend (Purchase) -> Verification,
        acknowledge: suspend (String) -> Boolean,
    ): Report {
        var report = Report()
        for (purchase in purchases) {
            // Money has not moved yet — a pending purchase is somebody at a
            // kiosk halfway through paying. Verifying it would ask the server
            // to grant a tier for a sale that may never complete, and
            // acknowledging it is not even legal.
            if (purchase.isPending) {
                report = report.copy(pending = report.pending + 1)
                continue
            }
            when (verify(purchase)) {
                Verification.ACCEPTED -> {
                    report = report.copy(entitled = report.entitled + 1)
                    if (!purchase.isAcknowledged) {
                        if (acknowledge(purchase.purchaseToken)) {
                            report = report.copy(acknowledged = report.acknowledged + 1)
                        } else {
                            // Acknowledgement itself can fail. The entitlement
                            // is already granted, so this is not a user-facing
                            // failure — but it must be retried before Play's
                            // three-day window closes and refunds a live
                            // subscription out from under them.
                            report = report.copy(deferred = report.deferred + 1)
                        }
                    }
                }
                Verification.REJECTED -> report = report.copy(rejected = report.rejected + 1)
                Verification.UNAVAILABLE -> report = report.copy(deferred = report.deferred + 1)
            }
        }
        return report
    }

    /**
     * Map an API failure to a verdict.
     *
     * The distinction this draws is the whole reason [Verification] has three
     * values: 4xx is the server having looked and said no, and anything else is
     * the server not having answered. Treating an outage as a rejection would
     * abandon a real purchase; treating a rejection as an outage would retry a
     * forged one forever.
     */
    fun verdictFor(error: Throwable?): Verification = when {
        error == null -> Verification.ACCEPTED
        error is Session.ApiException && error.code in 400..499 -> Verification.REJECTED
        else -> Verification.UNAVAILABLE
    }
}
