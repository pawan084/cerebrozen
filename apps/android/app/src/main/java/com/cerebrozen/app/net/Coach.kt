package com.cerebrozen.app.net

/* The coaching-engine client (services/engine, docs/COACHING_FLOW.md).
 *
 * One governed arc per session: `start` mints the session and runs the first
 * turn; each later message is one `turn`. Both stream SSE frames of the shape
 * `data: {"type": "status"|"node"|"token"|"done", ...}` — the platform-issued
 * bearer token is accepted as-is (shared HS512 secret, org_id claim).
 *
 * The engine identifies the user from the JWT `username` claim, so no user_id
 * is sent. `done` carries `session_id` (stored for the next turn) and any
 * action cards the session produced. */

import com.cerebrozen.app.BuildConfig
import org.json.JSONObject

object Coach {
    /** The active engine session, if any. Survives tab switches, not process death
     * — the engine's own resumable-session list covers reconnection later. */
    @Volatile var sessionId: String? = null
        private set

    data class DoneResult(val payload: JSONObject)

    /** Run one coaching turn (starting the session if needed), streaming tokens. */
    suspend fun turn(
        text: String,
        onStatus: (String) -> Unit = {},
        onToken: (String) -> Unit,
    ): DoneResult {
        val path = sessionId?.let { "/v1/sessions/$it/turn?stream=true" }
            ?: "/v1/sessions/start?stream=true"
        var done = JSONObject()
        Session.sse(path, JSONObject().put("text", text), base = BuildConfig.ENGINE_BASE_URL) { ev ->
            when (ev.optString("type")) {
                "token" -> onToken(ev.optString("text"))
                "status" -> onStatus(ev.optString("msg"))
                "done" -> {
                    done = ev
                    ev.optString("session_id").takeIf { it.isNotBlank() }?.let { sessionId = it }
                }
            }
        }
        return DoneResult(done)
    }

    /** End the current session client-side (the engine's commit gate governs
     * whether the SESSION may close; this only clears the local pointer). */
    fun reset() {
        sessionId = null
    }
}
