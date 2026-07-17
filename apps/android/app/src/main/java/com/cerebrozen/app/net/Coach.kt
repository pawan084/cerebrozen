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
    /** The active engine session, if any.
     *
     * Memory only, and deliberately so: the engine already knows which session is live
     * (`GET /v1/sessions/resumable`), and asking it is both authoritative and survives a
     * reinstall — a locally cached id can be stale (the session may have ended on another
     * device) in a way the server's answer never is. CoachScreen resumes through
     * [adopt] on launch, which is what "reconnection later" meant here. */
    @Volatile var sessionId: String? = null
        private set

    /** Re-enter a session the engine says is still open, so a process death does not lose
     *  the thread. Only for resuming: a NEW session id arrives on the `done` frame. */
    fun adopt(id: String) {
        if (id.isNotBlank()) sessionId = id
    }

    data class DoneResult(
        val payload: JSONObject,
        /** Engine stages that ran this turn (from SSE `node` events) — e.g.
         * "learning_aid" present means retrieved, reviewed material was served,
         * which the UI marks with the grounded line. */
        val stages: Set<String> = emptySet(),
    )

    /** Run one coaching turn (starting the session if needed), streaming tokens. */
    suspend fun turn(
        text: String,
        onStatus: (String) -> Unit = {},
        onToken: (String) -> Unit,
    ): DoneResult {
        val starting = sessionId == null
        val path = sessionId?.let { "/v1/sessions/$it/turn?stream=true" }
            ?: "/v1/sessions/start?stream=true"
        var done = JSONObject()
        val stages = mutableSetOf<String>()
        // local_hour: the coach greets by time of day, and this phone is the only party
        // that knows the hour here — there is no timezone on the account, and `region` is
        // multi-zone for US/CA/AU/EU. Without it the engine has nothing and the coach
        // guesses ("Good evening" at 9am). Sent per turn, not stored, so it stays right
        // across a flight. An hour, never a location.
        val body = JSONObject()
            .put("text", text)
            .put("local_hour", java.time.LocalTime.now().hour)
        Session.sse(path, body, base = BuildConfig.ENGINE_BASE_URL) { ev ->
            when (ev.optString("type")) {
                "token" -> onToken(ev.optString("text"))
                "status" -> onStatus(ev.optString("msg"))
                "node" -> ev.optString("stage").takeIf { it.isNotBlank() }?.let { stages.add(it) }
                "done" -> {
                    done = ev
                    ev.optString("session_id").takeIf { it.isNotBlank() }?.let { sessionId = it }
                }
            }
        }
        // HR-analytics beats (kind only — docs/SECURITY.md): a session that
        // began, and a session the commit gate allowed to close.
        if (starting && sessionId != null) Events.report(Events.SESSION_STARTED)
        if (done.optString("stage") == "close") Events.report(Events.SESSION_COMPLETED)
        return DoneResult(done, stages)
    }

    /** End the current session client-side (the engine's commit gate governs
     * whether the SESSION may close; this only clears the local pointer). */
    fun reset() {
        sessionId = null
    }
}
