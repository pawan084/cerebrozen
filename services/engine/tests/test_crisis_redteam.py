"""Crisis screen — ADVERSARIAL red team.

`test_crisis.py` proves the lexicon matches the words that are in it. That is a spelling
test, not a safety test. This file is the safety test: it attacks the screen with the way
people ACTUALLY disclose, which is almost never with the words in a keyword list.

The idea is stolen wholesale from a sibling project (Mira), whose proposal leads with
"13/13 crisis red-team scenarios caught, offline, with no model in the loop". That is a
credibility artifact this product does not have and needs — especially since the incumbent's
own security page promises enterprise buyers that a self-harm disclosure triggers an "SOS
Callout" to the employer's designated contact. **A promise that a disclosure reaches a human
is only as good as the thing that detects it.**

## This suite is designed to FAIL, and the failures are the point

A lexicon cannot understand euphemism, planning, or a sentence whose danger is entirely in
its implication. Anyone who tells you their keyword screen catches everything has not tried
to break it. So this file does two different things:

  1. **A hard floor.** Explicit disclosures MUST be caught, 100%, no excuses. If any of these
     regress the build fails.
  2. **A pinned scorecard on the implicit ones.** The current recall is recorded as a number.
     Improvements are welcome; a REGRESSION fails the build. The remaining misses are printed
     with a `-> MISSED` marker so nobody can look at a green suite and conclude the screen is
     safe.

The gap this measures is the argument for the second layer — an LLM classifier on the
messages the lexicon lets through, and a real escalation path. Both are in docs/TODO.md.
"""

import pytest

from app.graph.crisis import crisis_screen
# Scenarios live in one place so this build-gating suite and the buyer-facing report
# (scripts/redteam_report.py) score the identical set. See app/safety/redteam_scenarios.py.
from app.safety.redteam_scenarios import EXPLICIT, IMPLICIT, NOT_A_CRISIS


# ── TIER 1: explicit. Non-negotiable, must be 100%. ──────────────────────────


@pytest.mark.parametrize("lang,message", EXPLICIT, ids=[f"{l}:{m[:22]}" for l, m in EXPLICIT])
def test_an_explicit_disclosure_is_always_caught(lang, message):
    """The floor. A screen that misses one of these is not a screen."""
    assert crisis_screen(message) == "crisis", f"[{lang}] MISSED an explicit disclosure: {message!r}"


# ── TIER 2: how people actually talk. The red team proper. ───────────────────
#
# The IMPLICIT list (imported above) contains no lexicon keyword in its expected form.
# Every entry is a real disclosure shape — euphemism, passive ideation, planning,
# method-seeking, or a statement whose danger lives entirely in the implication. The
# per-kind breakdown lives in app/safety/redteam_scenarios.py.

# THE LEXICON ALONE. Measured, not estimated: 1 / 22.
#
# This file deliberately still tests the LEXICON BY ITSELF, and the number stays pinned at
# its real value — because the lexicon is the FLOOR. It is what runs in 1ms, for free, with
# no network, and it is the only thing that runs in an air-gapped deployment with no local
# model available. Knowing exactly how weak that floor is, to a number, is the point.
#
# The SHIPPING screen is two layers (`crisis.full_screen`): lexicon, then a classifier on
# everything it let through. Measured against this same red team, on the real provider:
#
#     lexicon alone .............  1 / 22
#     lexicon + classifier ...... 20 / 22      <- what a user actually gets
#     false positives ...........  1 / 7 ordinary workplace idiom
#
# The classifier is not tested here because this suite is offline by construction. Its
# WIRING, its fail-safe and its escalation are tested in tests/test_crisis_classifier.py
# against a fake provider; the 20/22 above is a measured result, recorded in docs/SAFETY.md.
#
# Do NOT "fix" the 1/22 below by adding these phrases to the lexicon.
#
# And the one it "catches" is an accident — "don't worry, I'm not going to hurt myself"
# matches on `hurt myself` DESPITE the negation. The screen got it right for the wrong
# reason, which means the true score is arguably zero.
#
# Read that again, because today's work makes it easy to misread. The crisis screen was
# extended from English-only to ~20 languages this morning. That was worth doing. But
# multilingual keyword matching is still KEYWORD MATCHING: it now fails to understand
# euphemism in twenty languages instead of one.
#
# Pinned so an improvement is celebrated and a regression fails the build. It is not a
# target, and it is emphatically not a pass mark.
PINNED_IMPLICIT_RECALL = 1


def _implicit_hits():
    return [(kind, msg) for kind, msg in IMPLICIT if crisis_screen(msg) == "crisis"]


def test_the_implicit_red_team_scorecard(capsys):
    """THE headline number, and it is not a good one.

    This test does not assert that the screen is safe. It asserts that we KNOW how unsafe it
    is, to a number, and that the number is not allowed to get worse without the build going
    red. Every `-> MISSED` line below is a real person the keyword screen would coach at.
    """
    hits = _implicit_hits()
    missed = [(k, m) for k, m in IMPLICIT if (k, m) not in hits]

    with capsys.disabled():
        print(f"\n\n  CRISIS RED TEAM — implicit disclosures: {len(hits)}/{len(IMPLICIT)} caught\n")
        for kind, msg in IMPLICIT:
            mark = "  caught" if (kind, msg) in hits else "  -> MISSED"
            print(f"  {mark:<11} [{kind:<12}] {msg}")
        print(
            f"\n  {len(missed)} of {len(IMPLICIT)} real disclosure shapes reach the coach as normal\n"
            "  conversation. A lexicon cannot fix this — it needs a classifier on the messages\n"
            "  the lexicon lets through, and an escalation path to a human. See docs/TODO.md.\n"
        )

    assert len(hits) >= PINNED_IMPLICIT_RECALL, (
        f"crisis recall REGRESSED: {len(hits)}/{len(IMPLICIT)}, was {PINNED_IMPLICIT_RECALL}. "
        "Someone made the screen worse."
    )


def test_we_have_not_quietly_started_claiming_the_red_team_passes():
    """A guard against our own marketing.

    The sibling project can honestly say "13/13 caught" because its safety layer is a
    classifier, not a word list. **We cannot say that, and this test exists so nobody
    accidentally does.** If the day comes that the lexicon catches everything here, it will
    be because someone added these exact strings to the lexicon — which is overfitting to
    the test, not safety.

    The bar to delete this test is a real second layer, not a bigger word list.
    """
    hits = _implicit_hits()
    assert len(hits) < len(IMPLICIT), (
        "The lexicon now catches every implicit case. That is almost certainly overfitting: "
        "someone added the red-team phrases to the word list. A keyword screen cannot "
        "understand euphemism. Build the classifier, then delete this test."
    )


# ── The precision side: a red team that makes the screen paranoid is also a failure ──
# NOT_A_CRISIS is imported from app/safety/redteam_scenarios.py.


@pytest.mark.parametrize("message", NOT_A_CRISIS)
def test_ordinary_workplace_idiom_is_not_a_crisis(message):
    """The other failure mode. A screen that fires on "my manager is killing me" gets
    switched off within a week — and a screen someone has switched off protects nobody.
    Recall is bought with precision, and the budget is not unlimited."""
    assert crisis_screen(message) == "ok", f"false positive on ordinary idiom: {message!r}"
