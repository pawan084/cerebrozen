package com.cerebrozen.app.net

import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * First-party anonymous product analytics — mirrors iOS `Analytics.track`.
 *
 * Allowlisted event names only (backend routes/events.ALLOWED_EVENTS), a
 * random install id, and NO auth header — rows can never join accounts.
 * Opt-out via the "Anonymous usage stats" toggle in Privacy & memory
 * (default on: anonymous counts, never content — the documented product
 * decision behind the "no third-party trackers" promise).
 */
object Analytics {
    private const val ID_KEY = "anon_id"
    private const val OPT_KEY = "usage_stats_on"
    private val scope = CoroutineScope(Dispatchers.IO)

    var enabled: Boolean
        get() = Session.prefGet(OPT_KEY) != "false"
        set(value) { Session.prefPut(OPT_KEY, value.toString()) }

    private fun anonId(): String =
        Session.prefGet(ID_KEY) ?: UUID.randomUUID().toString().also { Session.prefPut(ID_KEY, it) }

    /** Fire-and-forget: never blocks a screen and never surfaces errors —
     * these are counts, not truth. */
    fun track(name: String, step: String? = null) {
        if (!enabled) return
        val id = anonId()
        scope.launch { runCatching { Session.postEvent(id, name, step) } }
    }
}

/** Android funnel step → the canonical cross-stack step vocabulary
 * (backend services/metrics.ONBOARDING_STEPS; mirrors iOS/web step names). */
internal fun funnelStepName(step: String): String = when (step) {
    "Welcome" -> "welcome"
    "Age" -> "age_gate"
    "Disclosure" -> "disclosure"
    "Language" -> "language"
    "State" -> "state_check"
    "Reset" -> "first_reset"
    "Consent" -> "consent"
    "Notify" -> "notifications"
    "SignUp" -> "signup"
    else -> step.lowercase()
}
