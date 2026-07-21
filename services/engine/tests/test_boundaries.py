"""Coach, not companion — the guardrail and the per-turn disclosure.

A coaching product and a companion product are assembled from the same parts and are
regulated differently (CA SB243, NY GBL art. 47). The difference is not the marketing; it
is whether the thing says what it is when a person asks, and whether it simulates a
relationship when a person offers one. Both of those are mechanisms in code here, and this
file is what stops them being quietly edited away.

The load-bearing tests are the ones that assert the guardrail is present in EVERY composed
prompt and outside the editable workbook — a prompt author tuning for warmth is the
realistic way this regresses, not a developer deleting a safety file.
"""

from __future__ import annotations

import pytest

from app.graph import guardrails
from app.safety import boundaries


# ── detection: what counts as being mistaken for a person ────────────────────

@pytest.mark.parametrize(
    "kind,message",
    [
        ("human", "wait, are you a real person?"),
        ("human", "are you even real?"),          # the adverb form; missed by the first draft
        ("human", "is this even a real conversation"),
        ("human", "Are you human or a bot"),
        ("human", "is this a real human I'm talking to"),
        ("human", "am i talking to a machine right now"),
        ("human", "who am I really talking to here"),
        ("human", "do you actually feel anything"),
        ("clinical", "are you a therapist?"),
        ("clinical", "are you licensed"),
        ("clinical", "is this therapy"),
        ("clinical", "can you diagnose me"),
        ("clinical", "do i have depression"),
        ("attachment", "i love you"),
        ("attachment", "do you love me"),
        ("attachment", "are you my friend"),
        ("attachment", "will you be my girlfriend"),
        ("attachment", "you're my only friend honestly"),
        ("persistence", "will you always be here for me?"),
        ("persistence", "promise me you'll be here tomorrow"),
        ("persistence", "please never leave me"),
    ],
)
def test_a_message_that_mistakes_the_coach_for_a_person_is_caught(kind, message):
    assert kind in boundaries.detect(message), f"missed [{kind}]: {message!r}"


@pytest.mark.parametrize(
    "message",
    [
        "my manager is being unreasonable and I want to prepare for the 1:1",
        "I need you to help me plan the quarter",
        "can you help me think through whether to take the role",
        "I have a difficult conversation with Dana on Thursday",
        "the team is burned out and I don't know how to raise it",
        "I want to be a better listener",
    ],
)
def test_ordinary_coaching_talk_does_not_trip_it(message):
    """The cost of a false positive is one true sentence about what the coach is, inserted
    into a coaching turn — cheap, but not free: at a high enough rate it turns the
    disclosure into wallpaper, which is the failure this is meant to prevent."""
    assert boundaries.detect(message) == [], f"false positive on: {message!r}"


def test_the_strongest_disclosure_wins_when_a_message_trips_several():
    """"Are you a real therapist, do you love me" must not be answered with the mildest of
    the three. The further into attachment a user is, the more a hedged answer costs
    them."""
    kinds = boundaries.detect("are you a real therapist? do you love me?")
    assert kinds[0] == "attachment"
    assert set(kinds) == {"attachment", "clinical", "human"}


def test_detection_never_raises_on_junk():
    for junk in ("", None, "🙂" * 50, "\x00\x01"):
        assert boundaries.detect(junk) == [] or isinstance(boundaries.detect(junk), list)


def test_a_matcher_failure_costs_the_disclosure_not_the_turn(monkeypatch):
    """The opposite bias to crisis.py, deliberately. There, a broken matcher flags anyway —
    a false crisis reply is survivable and a missed one is not. Here, a broken matcher
    stays quiet: injecting "you are not a person" into an unrelated coaching turn because a
    regex blew up is a worse outcome than missing one disclosure, and the always-on
    guardrail is still in the prompt underneath."""

    class _Boom:
        def search(self, _text):
            raise RuntimeError("boom")

    monkeypatch.setattr(boundaries, "_COMPILED", (("human", _Boom()),))
    assert boundaries.detect("are you human") == []
    assert boundaries.block_for("are you human") is None


# ── the block: what the coach is required to say ─────────────────────────────

def test_no_block_for_an_ordinary_turn():
    assert boundaries.block_for("how do I give Dana harder feedback") is None


@pytest.mark.parametrize("kind", sorted(boundaries.LINES))
def test_every_line_says_the_word_AI_and_never_claims_to_be_a_person(kind):
    """The disclosure has to use the word the law and the user both understand. "Assistant",
    "system" and "service" all leave a reasonable person believing something false."""
    line = boundaries.LINES[kind]
    assert "AI" in line, f"[{kind}] disclosure never says it is an AI"
    lowered = line.lower()
    for forbidden in ("i'm human", "i am human", "i'm a person", "i am a person", "i'm licensed"):
        assert forbidden not in lowered, f"[{kind}] claims to be {forbidden!r}"


def test_the_block_forbids_the_four_claims_that_change_what_this_product_is():
    block = boundaries.block_for("are you human")
    assert block
    lowered = block.lower()
    for required in ("human", "licensed", "feelings", "always be available"):
        assert required in lowered, f"the block does not forbid claiming {required!r}"
    assert "in your own words" in lowered, "a canned reply is not the mechanism here"


def test_the_clinical_line_declines_care_without_closing_the_door():
    """"I'm not a therapist" alone reads as a refusal to someone who just disclosed
    something hard. The line has to decline the role and keep the conversation open."""
    line = boundaries.LINES["clinical"]
    assert "diagnose" in line and "licensed" in line
    assert "either way" in line or "next step" in line


# ── the always-on guardrail ──────────────────────────────────────────────────

def test_the_conduct_guardrail_is_in_every_composed_prompt():
    """Unconditional, on every stage. "Which stages need it" is exactly the judgement call
    that goes wrong quietly."""
    prompt = guardrails.build_system_prompt(
        environment_prompt="be helpful",
        node_prompt="You are the coaching intake agent.",
        coaching_path=None,
        user_context={},
    )
    assert "COACH, NOT COMPANION" in prompt
    assert "you are an ai" in prompt.lower()


@pytest.mark.parametrize("path", [None, "CIM", "CBT", "CH"])
def test_the_guardrail_survives_every_coaching_path(path):
    prompt = guardrails.build_system_prompt("env", "node", path, {})
    assert "COACH, NOT COMPANION" in prompt


def test_the_guardrail_is_code_not_workbook_content():
    """Rule 4's shape: a prompt author must not be able to edit or disable it. It is
    composed even when the workbook's environment prompt is empty — i.e. it does not
    travel with the editable text."""
    prompt = guardrails.build_system_prompt("", "node prompt", "CIM", {})
    assert "OPERATING CONSTRAINTS" not in prompt      # no workbook content at all
    assert "COACH, NOT COMPANION" in prompt           # and the conduct rules survive anyway


def test_the_guardrail_bans_relationship_simulation_specifically():
    """Not a vague "be professional" — the named behaviours, because a model given a vague
    instruction and a warmth-tuned prompt resolves the tension toward warmth."""
    lowered = guardrails.NON_COMPANION.lower()
    for banned in ("pet names", "romantic", "always be here for you", "dependence"):
        assert banned in lowered, f"the guardrail does not name {banned!r}"


def test_the_guardrail_does_not_ask_the_coach_to_be_cold():
    """An over-corrected guardrail gets tuned out or edited away, which is the same
    outcome as not having one."""
    lowered = guardrails.NON_COMPANION.lower()
    assert "warmth is welcome" in lowered
