"""Agent output-contract monitors.

The graph routes on structured fields the AGENTS emit. When a prompt edit stops
emitting one, routing doesn't break — it silently takes the fallback path forever.
These monitors turn that invisible drift into a metric. Each test below pins a
contract that has ALREADY failed in production at least once.
"""
from app.graph.contracts import check_handoff_contract, check_turn_contract, report
from app.graph.state import (
    STAGE_CH,
    STAGE_CHALLENGE,
    STAGE_CORE,
    STAGE_DYNAMIC_ACTIONS,
    STAGE_LEARNING_AID,
)


def test_challenge_must_emit_a_coaching_path():
    """Without it, every session silently falls back to CIM."""
    assert check_handoff_contract(STAGE_CHALLENGE, {}, {}) == ["challenge_no_coaching_path"]
    assert check_handoff_contract(STAGE_CHALLENGE, {"coaching_path": "CH"}, {}) == []
    # The camelCase spelling some prompt revisions emit is accepted too.
    assert check_handoff_contract(STAGE_CHALLENGE, {"coachingPath": "cbt"}, {}) == []
    # A garbage path is a violation, not a silent fallback.
    assert check_handoff_contract(STAGE_CHALLENGE, {"coaching_path": "??"}, {}) != []


def test_challenge_path_is_found_where_the_prompt_actually_puts_it():
    """The live prompt emits the path NESTED in context_update, not at the top level.
    A monitor that only checked the top level would cry wolf on every healthy handoff
    — which is exactly what it did until a real session caught it."""
    raw = {"agent_name": "challenge_context_agent",
           "context_update": {"session_goal": "be more direct", "coaching_path": "CH"}}
    assert check_handoff_contract(STAGE_CHALLENGE, raw, {}) == []
    # Emitted but EMPTY (its value before the agent has scored the path) is still a miss.
    empty = {"context_update": {"coaching_path": ""}}
    assert check_handoff_contract(STAGE_CHALLENGE, empty, {}) == ["challenge_no_coaching_path"]


def test_ch_must_emit_a_phase_milestone():
    """A 27-turn CH session emitted `awaiting_phase_transition` zero times, so the
    per-phase Action beat never fired ('no actions for Phase 1')."""
    assert check_handoff_contract(STAGE_CH, {}, {"ch_beats_fired": []}) == ["ch_no_phase_milestone"]
    assert check_handoff_contract(STAGE_CH, {}, {"ch_beats_fired": ["1"]}) == []


def test_learning_aid_must_deliver_before_it_commits():
    """The aid regressed to jumping straight to `commit` with content in hand."""
    violations = check_handoff_contract(
        STAGE_LEARNING_AID, {"current_step": "commit"}, {"learning_aid_progress": {}},
    )
    assert violations == ["learning_aid_commit_without_delivery"]
    # Delivered first → no violation.
    assert check_handoff_contract(
        STAGE_LEARNING_AID,
        {"current_step": "commit"},
        {"learning_aid_progress": {"delivered": True}},
    ) == []


def test_actions_gate_must_produce_cards():
    assert check_turn_contract(STAGE_DYNAMIC_ACTIONS, {}) == ["dynamic_actions_no_cards"]
    assert check_turn_contract(STAGE_DYNAMIC_ACTIONS, {"actions": [{"text": "do the thing"}]}) == []


def test_stages_without_a_contract_are_unaffected():
    assert check_turn_contract(STAGE_CORE, {}) == []
    assert check_handoff_contract(STAGE_CORE, {}, {}) == []


def test_report_is_advisory_and_never_raises():
    """A monitor must never break the turn it is watching."""
    report(STAGE_CHALLENGE, ["challenge_no_coaching_path"], {"session_id": "s", "user_id": "u"})
    report(STAGE_CHALLENGE, [], {})           # no violations → no-op
    report("weird_stage", ["made_up"], {})    # unknown stage/contract → still fine


# ── contract REPAIR (not just detection) ─────────────────────────────────────

def test_a_missing_coaching_path_is_repairable_not_just_reported():
    """The graph routes on `coaching_path`. Measured on the live stack, challenge_context
    hands off WITHOUT it roughly 1 turn in 6 — and the router then silently falls back to
    CIM, so the user gets the wrong methodology for the whole session while nothing errors.

    Detection alone does not help the user in that session. So this violation is in the
    REPAIR table: the handoff is not honoured, the agent is re-prompted once naming the
    missing field, and the routing decision is recovered (measured: 6/6 recovery).
    """
    from app.graph.nodes import _CONTRACT_REPAIR

    assert "challenge_no_coaching_path" in _CONTRACT_REPAIR, (
        "a missing coaching_path silently mis-routes the entire session — it must be "
        "repaired, not merely counted"
    )
    nudge = _CONTRACT_REPAIR["challenge_no_coaching_path"]
    # The nudge must name the field AND define the options, or the retry is a coin-flip.
    assert "coaching_path" in nudge
    for path in ("CIM", "CBT", "CH"):
        assert path in nudge


def test_only_routing_violations_are_repaired():
    """A repair costs an extra LLM call. It must buy CORRECTNESS (a silently wrong route),
    not tidiness — so contracts that merely indicate quality drift stay advisory."""
    from app.graph.nodes import _CONTRACT_REPAIR

    assert "dynamic_actions_no_cards" not in _CONTRACT_REPAIR
    assert "learning_aid_commit_without_delivery" not in _CONTRACT_REPAIR
