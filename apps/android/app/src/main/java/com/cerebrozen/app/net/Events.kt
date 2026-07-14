package com.cerebrozen.app.net

/* HR-analytics beats — the four whitelisted kinds the platform aggregates
 * under the k-anonymity floor (POST /events/coaching). KIND ONLY, never
 * content: what the user said, did, or committed to never leaves the coach.
 *
 * Fire-and-forget by design: analytics must never slow a turn, block a tap,
 * or surface an error. A lost beat is a rounding error; a janky session is a
 * product failure. */

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

object Events {
    const val SESSION_STARTED = "session_started"
    const val SESSION_COMPLETED = "session_completed"
    const val ACTION_SAVED = "action_saved"
    const val ACTION_COMPLETED = "action_completed"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Launch seam: tests swap in a synchronous runner so beats are assertable. */
    internal var launchIn: (suspend () -> Unit) -> Unit = { block ->
        scope.launch { block() }
    }

    fun report(kind: String) {
        launchIn {
            runCatching {
                Session.api("/events/coaching", "POST", JSONObject().put("kind", kind))
            }
        }
    }
}
