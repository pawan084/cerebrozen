"""Graph regression net: compiles, routes deterministically, safety interrupts,
one streamed reply per turn, and the stuck-stage watchdog always advances.

Runs fully offline (mock LLM, in-memory stores). Routing is a pure code predicate
over typed state, so most of this is exercised without any LLM at all.
"""
import pytest

from app.graph.build_graph import (
    STAGE_NODE,
    _after_stage,
    _coaching_route,
    _dispatch_stage,
    _node_for_stage,
    build_graph,
)
from app.graph.state import (
    STAGE_CH,
    STAGE_CHALLENGE,
    STAGE_CHECKIN,
    STAGE_CLOSE,
    STAGE_CORE,
    STAGE_DYNAMIC_ACTIONS,
    STAGE_FEEDBACK,
    STAGE_FINAL_ACTION_CHECK,
    STAGE_INTAKE,
    STAGE_LEARNING_AID,
    STAGE_PATTERN,
    STAGE_ROLEPLAY,
    STAGE_SIMULATION_DECISION,
    STAGE_SJT,
    next_stage,
)
from app.graph.tools import crisis_screen


def _handoff(stage: str, **extra) -> dict:
    """A text-free control handoff — the state shape that chains within one turn."""
    return {"stage": stage, "handoff_ready": True, "reply_text": "", **extra}


# ── structure ────────────────────────────────────────────────────────────────

def test_graph_compiles():
    g = build_graph()
    assert hasattr(g, "ainvoke")  # a compiled LangGraph
    nodes = set(g.get_graph().nodes)
    # Every routable node in the stage table is actually registered in the graph.
    for node in STAGE_NODE.values():
        if node != "__coaching_slot__":
            assert node in nodes, f"{node} is routable but not registered"
    assert {"core", "capability"} <= nodes  # the coaching slot's two destinations


def test_every_stage_resolves_to_a_node():
    """No stage may dead-end: the dispatch edge must map all of them."""
    for stage in STAGE_NODE:
        assert _node_for_stage({"stage": stage, "coaching_path": "CIM"}, stage) is not None


# ── deterministic routing ────────────────────────────────────────────────────

def test_coaching_path_routing():
    # CIM/CBT are unified under `core`; CH gets its own node.
    assert _coaching_route({"coaching_path": "CIM"}) == "core"
    assert _coaching_route({"coaching_path": "CBT"}) == "core"
    assert _coaching_route({"coaching_path": "CH"}) == "capability"
    assert _coaching_route({"coaching_path": "ch"}) == "capability"  # case-insensitive
    # No usable path → logged CIM fallback, never a crash.
    assert _coaching_route({}) == "core"
    assert _coaching_route({"coaching_path": "garbage"}) == "core"


def test_coaching_slot_dispatches_by_path():
    assert _dispatch_stage({"stage": STAGE_CORE, "coaching_path": "CIM"}) == "core"
    assert _dispatch_stage({"stage": STAGE_CORE, "coaching_path": "CH"}) == "capability"
    assert _dispatch_stage({"stage": STAGE_CH, "coaching_path": "CH"}) == "capability"


def test_ch_stage_chains_within_a_turn():
    """Regression: STAGE_CH was missing from the in-turn chain table, so a text-free
    CH handoff fell through to `end` instead of routing to the capability node."""
    assert _after_stage(_handoff(STAGE_CH, coaching_path="CH")) == "capability"


def test_feedback_is_gated_by_the_final_action_check():
    """No session may close without passing the action check — on both edges."""
    for route in (_dispatch_stage, _after_stage):
        state = _handoff(STAGE_FEEDBACK)
        assert route(state) == "final_action_check"
        assert route({**state, "final_action_check_done": True}) == "feedback"
        # A CH early exit (Save & Exit at a phase boundary) bypasses the check.
        assert route({**state, "ch_early_exit": True}) == "feedback"


def test_close_is_terminal():
    assert _dispatch_stage({"stage": STAGE_CLOSE}) == "session_complete"


def test_turn_ends_when_the_stage_replied():
    """A stage that produced user-facing text ends the turn — it never chains on,
    which is what keeps one streamed reply per turn."""
    assert _after_stage({"stage": STAGE_CORE, "handoff_ready": True, "reply_text": "hi"}) == "end"
    assert _after_stage({"stage": STAGE_CORE, "handoff_ready": False, "reply_text": ""}) == "end"


def test_unknown_stage_never_invents_a_destination_mid_turn():
    # Entry: recover into the coaching slot (logged). Mid-turn: stop.
    assert _dispatch_stage({"stage": "bogus_stage"}) == "core"
    assert _after_stage(_handoff("bogus_stage")) == "end"


@pytest.mark.parametrize("stage,expected", [
    (STAGE_INTAKE, "intake"),
    (STAGE_CHALLENGE, "challenge"),
    (STAGE_DYNAMIC_ACTIONS, "dynamic_actions"),
    (STAGE_SIMULATION_DECISION, "simulation_decision"),
    (STAGE_ROLEPLAY, "role_play"),
    (STAGE_SJT, "sjt"),
    (STAGE_PATTERN, "pattern"),
    (STAGE_FINAL_ACTION_CHECK, "final_action_check"),
])
def test_stage_to_node_mapping(stage, expected):
    assert _dispatch_stage({"stage": stage}) == expected


def test_catalog_gated_stages_advance_when_disabled(monkeypatch):
    """A gated agent that's switched off must ADVANCE the session, not dead-end or
    re-run a completed stage."""
    import app.graph.build_graph as bg

    class _Reg:
        def is_enabled(self, stage):
            return False

    monkeypatch.setattr(bg, "get_registry", lambda: _Reg())
    assert _dispatch_stage({"stage": STAGE_CHECKIN}) == "challenge"
    # learning_aid off → the closing layer (via the action check), never straight to close.
    assert _dispatch_stage({"stage": STAGE_LEARNING_AID}) == "final_action_check"


def test_next_stage_only_feedback_reaches_close():
    """The closing layer is the sole legitimate path to a terminal close."""
    assert next_stage(STAGE_FEEDBACK) == STAGE_CLOSE
    for stage in (STAGE_CORE, STAGE_CH, STAGE_ROLEPLAY, STAGE_SJT, STAGE_LEARNING_AID):
        assert next_stage(stage) == STAGE_FEEDBACK
    # An unknown stage recovers into coaching rather than terminating the session.
    assert next_stage("bogus") == STAGE_CORE


# ── safety ───────────────────────────────────────────────────────────────────

def test_crisis_screen():
    assert crisis_screen("I want to kill myself") == "crisis"
    assert crisis_screen("thinking about suicide") == "crisis"
    assert crisis_screen("I want to nail this presentation") == "ok"
    assert crisis_screen("") == "ok"


def test_safety_interrupt_skips_coaching(start_session, user_id):
    result = start_session(user_id, "I want to end my life")
    assert result["active_node"] == "safe_response"
    assert result["safety_flag"] == "crisis"
    # safe_response is a non-LLM node: it sets the scripted reply directly rather
    # than streaming it, so the text arrives in the done payload, not as tokens.
    #
    # Assert the CONTRACT, not the country. This used to assert "988" — hardcoding the US
    # helpline into the test suite, so a client in India or the UK could not change it
    # without a failing test. The reply must point the user at real help; WHICH help is
    # config (CEREBROZEN_CRISIS_LINE).
    from app.llm.prompts import CRISIS_LINE

    reply = result["response_to_user"]
    assert CRISIS_LINE in reply, "the crisis reply must carry the configured helpline"
    assert "coach" in reply.lower()   # and be honest that this is beyond coaching
    assert not result.get("coaching_path")  # coaching never ran


# ── turn loop ────────────────────────────────────────────────────────────────

def test_normal_turn_streams_one_reply(start_session, user_id):
    result = start_session(user_id, "I keep avoiding a hard conversation with my manager")
    assert result.reply.strip()                       # tokens actually streamed
    assert result["active_node"] not in (None, "safe_response")
    assert result["session_id"]


def test_session_continues_on_the_same_thread(start_session, run_turn, user_id):
    first = start_session(user_id, "I want to work on delegation")
    sid = first["session_id"]
    second = run_turn(user_id, sid, "It's hard to let go of control")
    assert second.reply.strip()
    assert second["session_id"] == sid


# ── stuck-stage watchdog ─────────────────────────────────────────────────────

def test_watchdog_forces_advance_past_the_turn_cap():
    """A prompt that never signals completion must not pin the session forever
    (live incident: 78 consecutive turns on challenge_context)."""
    from app.graph.nodes import _stage_turn_cap

    cap = _stage_turn_cap(STAGE_CHALLENGE)
    assert cap < 78, "the cap must fire well before the 78-turn incident"
    # Every stage has a finite cap — including ones with no explicit override.
    assert _stage_turn_cap("some_unmapped_stage") > 0
