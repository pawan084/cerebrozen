package com.cerebrozen.app.ui.screens

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `pacing` marker on the `done` frame — CHAT_SPEC §1.7.
 *
 * The engine sometimes changes register mid-session: after several not-coping messages it
 * stops treating the problem as an ordinary work problem and points at support outside this
 * app (`services/engine/app/safety/pacing.py`). That instruction goes into the **system
 * prompt**, so the reply comes back in the coach's own voice — on the wire, a support-route
 * turn is byte-indistinguishable from ordinary coaching prose.
 *
 * So the client is told, rather than left to guess from wording. This test exists because
 * guessing at the payload's shape is the documented way this screen has already failed once:
 * `replyFallback` read `reply`/`text`, keys the engine never sends, and every crisis takeover
 * rendered as "…" until a device found it. A key name IS the contract, so it gets a test —
 * one that fails loudly if the engine's vocabulary moves.
 */
class CoachPacingMarkerTest {

    @Test
    fun `the marker is read from the key the engine actually sends`() {
        // The engine's own field name (graph/engine.py). If this assertion is what broke,
        // the fix is a coordinated release, not a rename here — see the cross-stack
        // contract row in docs/ARCHITECTURE.md.
        val done = JSONObject().put("pacing", "distress_route")
        assertEquals("distress_route", pacingOf(done))
    }

    @Test
    fun `a distress route earns the support block`() {
        assertTrue(isSupportRoute(JSONObject().put("pacing", PACING_DISTRESS_ROUTE)))
    }

    @Test
    fun `a pause does not`() {
        // A long-session pause is a scheduling nudge, not a support route. Drawing the same
        // bordered card for both would make the card mean nothing — and the turn that
        // genuinely needed to look different would stop looking different.
        assertFalse(isSupportRoute(JSONObject().put("pacing", PACING_PAUSE)))
    }

    @Test
    fun `an ordinary turn draws nothing`() {
        assertFalse(isSupportRoute(JSONObject().put("pacing", "")))
        assertFalse(isSupportRoute(JSONObject()))
        assertFalse(isSupportRoute(null))
    }

    @Test
    fun `an older engine that has never heard of pacing is not an error`() {
        // Additive-first (docs/ENGINEERING.md §Cross-stack change protocol): the field is
        // absent from any engine older than 2026-07-21, and this client must keep working
        // against one — silently, as an ordinary turn.
        val legacy = JSONObject()
            .put("response_to_user", "and what would make that different?")
            .put("safety_flag", "ok")
        assertEquals("", pacingOf(legacy))
        assertFalse(isSupportRoute(legacy))
    }

    @Test
    fun `an unknown future kind does not draw a support block`() {
        // Fail closed on vocabulary we do not recognise: showing the support-route card for
        // a kind whose meaning we do not know is worse than showing nothing.
        assertFalse(isSupportRoute(JSONObject().put("pacing", "some_future_kind")))
    }

    // ── the repeated AI disclosure (CA SB243 / backlog #23) ──────────────────────────────

    @Test
    fun `a long session re-states that the coach is an AI`() {
        assertTrue(shouldRestateAiNote(JSONObject().put("pacing", PACING_PAUSE)))
    }

    @Test
    fun `an ordinary turn does not`() {
        // The statute wants the disclosure repeated, not constant. A line on every turn is
        // one people learn to skip — which is the opposite of disclosure.
        assertFalse(shouldRestateAiNote(JSONObject().put("pacing", "")))
        assertFalse(shouldRestateAiNote(JSONObject()))
        assertFalse(shouldRestateAiNote(null))
    }

    @Test
    fun `the distress route does not double as a disclosure beat`() {
        // Someone who has said three times that they are not coping is being pointed at
        // real support. Appending "by the way, I'm an AI" to that moment is the wrong beat
        // for it — the support-route card is what that turn gets.
        assertFalse(shouldRestateAiNote(JSONObject().put("pacing", PACING_DISTRESS_ROUTE)))
        assertTrue(isSupportRoute(JSONObject().put("pacing", PACING_DISTRESS_ROUTE)))
    }

    @Test
    fun `the two pacing kinds never both fire on one turn`() {
        // The engine returns ONE kind per turn (`block_for` — distress wins when both
        // qualify), so the client must never draw both treatments on the same reply.
        for (kind in listOf(PACING_PAUSE, PACING_DISTRESS_ROUTE, "")) {
            val p = JSONObject().put("pacing", kind)
            assertFalse("both treatments fired for '$kind'", isSupportRoute(p) && shouldRestateAiNote(p))
        }
    }

    @Test
    fun `a crisis turn is never also a support-route turn`() {
        // safety_node clears the marker on every turn including the crisis path, so a
        // takeover cannot arrive decorated as a pacing card (CHAT_SPEC §10.98). This pins
        // the client's half: even given both fields, the takeover reply is what renders.
        val takeover = JSONObject()
            .put("safety_flag", "crisis")
            .put("pacing", "")
            .put("response_to_user", "I'm really glad you told me…")
        assertFalse(isSupportRoute(takeover))
        assertEquals("I'm really glad you told me…", replyFallback(takeover))
    }
}
