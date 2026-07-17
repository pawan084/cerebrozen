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


# ── the spec must not drift from the parser ──────────────────────────────────


def test_the_authoring_spec_names_the_key_the_parser_actually_reads():
    """PROMPTS_SPEC.md is what a prompt author writes against, so a wrong key there is a
    wrong key in every prompt written from it.

    It said `reply_text` until 2026-07-17. The parser has never read that: it reads
    `_USER_TEXT_KEYS`, so a prompt following the spec would have emitted valid JSON, parsed
    to an EMPTY reply, and produced a dead turn with no error — the exact failure EVALS.md
    records as having reached production. The trap is `parse_control`'s docstring, which
    names its RETURN TUPLE `(reply_text, handoff_ready, coaching_path)`; read the signature
    instead of `_USER_TEXT_KEYS` and `reply_text` looks authoritative.

    Pinned here rather than trusted, because the doc and the code have already disagreed
    once and nothing failed when they did.
    """
    from pathlib import Path

    from app.graph.tools import _USER_TEXT_KEYS

    spec = Path(__file__).resolve().parents[3] / "docs" / "PROMPTS_SPEC.md"
    text = spec.read_text(encoding="utf-8")

    assert "response_to_user" in text, "the spec no longer names a key the parser reads"
    assert "response_to_user" in _USER_TEXT_KEYS, "the parser stopped reading the spec's key"

    # `reply_text` may still be NAMED in the spec — it documents the trap — but never as
    # the thing to emit. Checked per PARAGRAPH, not per line: markdown wraps, so the
    # mention and its correction routinely land on different lines (my first version of
    # this test failed on exactly that).
    for para in text.split("\n\n"):
        if "reply_text" not in para:
            continue
        corrected = any(w in para for w in ("NOT", "wrong", "empty", "return tuple", "docstring"))
        assert corrected, (
            f"the spec names reply_text without correcting it — an author would emit it "
            f"and every reply would be empty:\n{para.strip()[:200]}"
        )


def test_the_shipping_prompts_emit_a_key_the_parser_reads():
    """The workbook is the real artifact — a prompt whose envelope names an unread key is a
    dead turn for that agent, and nothing in the suite would notice."""
    import json

    from app.graph.runtime import get_registry
    from app.graph.tools import _USER_TEXT_KEYS, parse_control

    reg = get_registry()
    stages = [s for s in reg.sizes() if reg.get(s)]
    assert stages, "no prompts loaded — this test would pass vacuously"

    offenders = []
    for stage in stages:
        body = reg.get(stage) or ""
        if "reply_text" in body and not any(k in body for k in _USER_TEXT_KEYS):
            offenders.append(stage)
    assert not offenders, (
        f"these prompts emit reply_text and no key the parser reads, so every reply is "
        f"empty: {offenders}"
    )

    # And prove the failure is real rather than theoretical.
    assert parse_control(json.dumps({"reply_text": "hi"}))[0] == ""
    assert parse_control(json.dumps({"response_to_user": "hi"}))[0] == "hi"
