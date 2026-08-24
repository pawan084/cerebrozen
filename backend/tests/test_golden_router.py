"""Golden regression for the deterministic router (WC-128, hermetic layer).

`activities.route` is the product's floor: it is what every user gets when the
model is down, what free-tier turns lean on, and the fallback the Android
client replays a failed Oracle stream through. Its existing tests spot-check
one phrasing per rule; this table is the regression net — the high-traffic
utterances, each pinned to (widget kind, chip actions) as data.

Two properties make this a GOLDEN table rather than more spot tests:

* It is declarative. A routing change shows up as a table diff in review —
  "'exhausted' now routes to sleep_checkin, was mood" — not as a test rename.
* It fails whole. One assert over all rows reports every drifted row at once,
  because a keyword edit rarely moves exactly one utterance.

The judged layer (tests/evals/, DeepEval, RUN_LLM_TESTS=1) evaluates what the
MODEL says; this layer pins what the PRODUCT does with no model at all —
the half of "agentic" that must survive a dead provider (WC-155).
"""
from app.services import activities

# (utterance, risk) -> (expected widget kind or None, expected chip actions)
# Chip actions, not labels: labels are copy and may be reworded; actions are
# the cross-stack contract the clients switch on.
GOLDEN: list[tuple[str, str, str | None, list[str]]] = [
    # Baseline captured 2026-08-24 by running route() itself — a golden table
    # encodes VERIFIED behaviour, and this router's behaviour is verified by
    # its own 12 spot tests plus the 943-test suite around it. (The first
    # draft of this table was hand-guessed and drifted in five rows —
    # `journal` for `mini_journal`, `mood_checkin` for `mood_check` — which is
    # this table's own lesson: goldens are captured, never remembered.)
    # ── breathing: anxiety vocabulary ──────────────────────────────────────
    ("I'm so anxious right now", "none", "breathing", ["grounding", "journal"]),
    ("feeling really stressed about work", "none", "breathing", ["grounding", "journal"]),
    ("I'm freaking out", "none", "breathing", ["grounding", "journal"]),
    ("my chest is tense and I can't breathe", "none", "breathing", ["grounding", "journal"]),
    # ── grounding: rumination vocabulary ───────────────────────────────────
    ("I keep overthinking everything", "none", "grounding", ["breathing", "journal"]),
    ("my head is spiraling", "none", "grounding", ["breathing", "journal"]),
    ("I feel dissociated, not present at all", "none", "grounding", ["breathing", "journal"]),
    # ── sleep check-in ─────────────────────────────────────────────────────
    ("I barely slept last night", "none", "sleep_checkin", ["breathing", "journal"]),
    ("insomnia again", "none", "sleep_checkin", ["breathing", "journal"]),
    ("woke up a lot, tossing all night", "none", "sleep_checkin", ["breathing", "journal"]),
    # ── journal ────────────────────────────────────────────────────────────
    ("I just need to vent", "none", "mini_journal", ["breathing", "mood_check"]),
    ("can I write this down somewhere", "none", "mini_journal", ["breathing", "mood_check"]),
    # ── dbt skill: intensity vocabulary ────────────────────────────────────
    ("I'm so angry I want to scream", "none", "dbt_skill", ["breathing", "grounding"]),
    ("fighting an urge right now", "none", "dbt_skill", ["breathing", "grounding"]),
    # ── one good thing ─────────────────────────────────────────────────────
    ("actually today went well", "none", "one_good_thing", ["intention_set", "mood_check"]),
    ("feeling grateful this evening", "none", "one_good_thing", ["intention_set", "mood_check"]),
    # ── intention ──────────────────────────────────────────────────────────
    ("I want a plan for the day tomorrow", "none", "intention_set", ["one_good_thing", "breathing"]),
    # ── mood ───────────────────────────────────────────────────────────────
    ("just feeling low and empty", "none", "mood_check", ["breathing", "journal"]),
    ("I'm sad and lonely tonight", "none", "mood_check", ["breathing", "journal"]),
    # ── no match: the two universal doors ──────────────────────────────────
    ("what's the weather like", "none", None, ["breathing", "mood_check"]),
    ("tell me about the app", "none", None, ["breathing", "mood_check"]),
    # ── elevated risk: urgent leads, the human path beside it, and the cap
    #    of 3 displaces the widget's usual complements ─────────────────────
    ("I'm anxious and it's getting scary", "elevated", "breathing", ["crisis", "human_support", "grounding"]),
    ("everything is too much, I feel empty", "elevated", "mood_check", ["crisis", "human_support", "breathing"]),
    # ── crisis: routing yields entirely — no widget, exactly two chips ─────
    ("I want to end it", "crisis", None, ["crisis", "breathing"]),
    ("I can't do this anymore, goodbye", "crisis", None, ["crisis", "breathing"]),
]


def test_the_golden_table_holds():
    drifted: list[str] = []
    for text, risk, want_widget, want_actions in GOLDEN:
        widget, suggestions = activities.route(text, risk)
        got_widget = widget.widget_kind if widget else None
        got_actions = [s.action for s in suggestions]
        if got_widget != want_widget or got_actions != want_actions:
            drifted.append(
                f"  {text!r} (risk={risk}): widget {want_widget}->{got_widget}, "
                f"chips {want_actions}->{got_actions}"
            )
    assert not drifted, (
        "the deterministic router drifted from its goldens — if intentional, "
        "update the table so the change is a reviewed diff:\n" + "\n".join(drifted)
    )


def test_crisis_never_gets_a_widget_regardless_of_text():
    # Property over the whole table's vocabulary: even the strongest widget
    # keyword must not surface an activity card on a crisis turn.
    for text, _risk, _w, _a in GOLDEN:
        widget, suggestions = activities.route(text, "crisis")
        assert widget is None, f"crisis turn grew a widget for {text!r}"
        assert [s.action for s in suggestions] == ["crisis", "breathing"]


def test_suggestions_never_exceed_three():
    for text, risk, _w, _a in GOLDEN:
        _widget, suggestions = activities.route(text, risk)
        assert len(suggestions) <= 3, f"{text!r} (risk={risk}) grew {len(suggestions)} chips"
