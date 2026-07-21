"""Session pacing — the long-session pause and the distress support route (#27, #28).

Both are interventions that a product optimising for engagement would never ship, which is
why they are in code rather than in the prompt workbook: they are the mechanism behind
"we don't want you here longer than you need to be", and that claim has to be inspectable.

The sharpest tests here are the negative ones. It is easy to build a pause reminder that
nags and a distress route that reads as the coach trying to get rid of you — both then get
ignored, and an intervention nobody reads is an intervention that does not exist.
"""

from __future__ import annotations

import pytest

from app.safety import pacing


def _history(user_messages: list[str]) -> list[dict]:
    """A transcript with each user message followed by a coach reply."""
    out: list[dict] = []
    for m in user_messages:
        out.append({"role": "user", "content": m})
        out.append({"role": "assistant", "content": "and what would make that different?"})
    return out


# ── the long-session pause (#27) ─────────────────────────────────────────────

def test_a_short_session_is_left_alone():
    block, kind = pacing.block_for(_history(["how do I prep for the review?"]), "and after that?")
    assert block is None and kind == ""


def test_a_long_session_is_offered_a_break():
    history = _history([f"turn {i}" for i in range(pacing.LONG_SESSION_TURNS - 1)])
    block, kind = pacing.block_for(history, "and one more thing")
    assert kind == "pause"
    assert "offer a natural pause" in block
    assert "nothing lost" in block


def test_the_pause_is_an_offer_not_an_ending():
    """The failure mode: a session that cuts someone off mid-thought because a counter hit
    twenty. The block must keep the choice with the user."""
    history = _history([f"turn {i}" for i in range(pacing.LONG_SESSION_TURNS - 1)])
    block, _ = pacing.block_for(history, "keep going")
    assert "never a refusal" in block
    assert "keep going" in block


def test_the_pause_does_not_repeat_every_turn_after_the_threshold():
    """A reminder on every turn is nagging, and nagging is how an intervention becomes
    invisible. It fires on the crossing, then once per further LONG_SESSION_TURNS."""
    fired = []
    for turns_so_far in range(pacing.LONG_SESSION_TURNS, pacing.LONG_SESSION_TURNS * 2 + 2):
        history = _history([f"turn {i}" for i in range(turns_so_far - 1)])
        _, kind = pacing.block_for(history, "next")
        if kind == "pause":
            fired.append(turns_so_far)
    assert fired == [pacing.LONG_SESSION_TURNS, pacing.LONG_SESSION_TURNS * 2]


# ── the distress support route (#28) ─────────────────────────────────────────

@pytest.mark.parametrize(
    "message",
    [
        "I can't cope with any of it right now",
        "I feel like I'm falling apart",
        "I had a panic attack before the standup",
        "I'm barely holding it together",
        "honestly I can't take this anymore",
        "it all feels hopeless",
    ],
)
def test_the_language_of_not_coping_is_recognised(message):
    assert pacing.is_distress(message), f"missed distress: {message!r}"


@pytest.mark.parametrize(
    "message",
    [
        "I'm exhausted after this quarter",
        "the team is burned out and I don't know how to raise it",
        "I feel overwhelmed by the backlog",
        "this project is a disaster",
        "I'm stressed about the review on Friday",
    ],
)
def test_ordinary_work_stress_is_not_treated_as_distress(message):
    """The load-bearing negative test. "Burned out", "exhausted" and "overwhelmed" are what
    a normal Tuesday sounds like in a workplace coaching product — a screen that fires on
    them fires on everyone, and then it is noise nobody acts on."""
    assert not pacing.is_distress(message), f"false positive on ordinary work stress: {message!r}"


def test_one_hard_message_does_not_trigger_a_route():
    """Someone saying once that they are struggling is a coaching moment, not a hand-off.
    Redirecting on the first sentence is its own way of not listening."""
    _, kind = pacing.block_for(_history(["the quarter went badly"]), "I can't cope with this")
    assert kind == ""


def test_repeated_not_coping_routes_to_real_support():
    history = _history(["I can't cope with the workload", "I'm falling apart honestly"])
    block, kind = pacing.block_for(history, "I can't take this anymore")
    assert kind == "distress_route"
    assert "EAP" in block and "doctor" in block


def test_the_distress_route_is_not_a_crisis_takeover():
    """Over-escalating "I'm falling apart" to an emergency line is its own harm: it tells
    someone their difficulty is an emergency, and it spends the crisis affordance on a
    moment that was not one. The crisis path owns that; this must stay out of its way."""
    history = _history(["I can't cope", "I'm falling apart"])
    block, _ = pacing.block_for(history, "I can't stop crying")
    assert "NOT a crisis takeover" in block
    assert "do not imply they are in danger" in block


def test_the_distress_route_never_ends_the_session():
    history = _history(["I can't cope", "I'm falling apart"])
    block, _ = pacing.block_for(history, "I'm drowning in it")
    assert "do not end the session over this" in block
    assert "accept either" in block, "the user must be allowed to keep going"


def test_the_route_does_not_repeat_on_every_later_message():
    """Offering an exit every single turn reads as the coach trying to get rid of you."""
    fired = []
    hard = ["I can't cope", "I'm falling apart", "I can't take this anymore",
            "I'm drowning", "I can't stop crying", "it's hopeless"]
    for i in range(1, len(hard)):
        _, kind = pacing.block_for(_history(hard[:i]), hard[i])
        fired.append(kind == "distress_route")
    assert fired == [False, True, False, False, True], f"unexpected cadence: {fired}"


def test_distress_outranks_the_pause_when_both_fire():
    """A long session is a scheduling observation; not coping is about the person."""
    history = _history(["I can't cope", "I'm falling apart"]
                       + [f"turn {i}" for i in range(pacing.LONG_SESSION_TURNS)])
    block, kind = pacing.block_for(history, "I can't take this anymore")
    assert kind == "distress_route"
    assert "SUPPORT ROUTE" in block


# ── it must never break a turn ───────────────────────────────────────────────

def test_junk_history_is_survivable():
    for junk in (None, [], [{}, {"role": "user"}], [{"role": None, "content": None}]):
        block, kind = pacing.block_for(junk, "hello")
        assert block is None and kind == ""


def test_a_matcher_failure_costs_the_intervention_not_the_turn(monkeypatch):
    class _Boom:
        def search(self, _text):
            raise RuntimeError("boom")

    monkeypatch.setattr(pacing, "_DISTRESS_RE", _Boom())
    assert pacing.is_distress("I can't cope") is False
    assert pacing.block_for(_history(["I can't cope"]), "I can't cope")[1] == ""
