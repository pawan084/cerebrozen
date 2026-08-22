"""Mutants for the two modules where a false-passing test costs the most (WC-277).

A test suite tells you the code passes. It does not tell you the tests would
notice if the code were wrong — and on these two modules that difference is
measured in human harm and in money:

* ``services/safety.py`` decides whether an explicit self-harm phrase is seen.
* ``services/entitlements.py`` decides who has paid for what.

**Curated, not generated.** A generic AST mutator flips operators everywhere and
produces mostly equivalent mutants and hours of runtime. Every entry here is
instead a specific WRONG BEHAVIOUR someone could plausibly introduce, written in
prose — so this file doubles as a statement of what these modules must never do,
and a survivor names a real gap rather than an arithmetic curiosity.

Each mutant names the tests that ought to catch it. A survivor means either the
tests are thinner than they look, or the mutant is equivalent — and the second
is a claim to prove, not to assume (three of this repo's own "surviving mutants"
turned out to be badly built mutants; see docs/TODO.md, 2026-08-22).
"""

from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class Mutant:
    id: str
    #: The wrong behaviour this creates, in the terms a user would feel it.
    breaks: str
    #: File relative to the backend root.
    path: str
    old: str
    new: str
    #: Tests expected to fail. Kept narrow so a run is seconds, not minutes.
    caught_by: tuple[str, ...] = field(default=("tests/",))
    #: Set ONLY with a proof, when the mutant provably cannot change behaviour.
    #:
    #: An equivalent mutant is expected to survive, so the runner inverts the
    #: verdict for it: surviving is correct, and being CAUGHT is the failure —
    #: because that means the proof below stopped holding. It is a canary for
    #: the assumption, not an excuse for a gap. Deleting the entry instead
    #: would throw the canary away with it.
    equivalent: str = ""


SAFETY = "app/services/safety.py"
CRISIS = "app/services/crisis.py"
ENTITLEMENTS = "app/services/entitlements.py"
SAFETY_TESTS = ("tests/test_safety_floor.py", "tests/test_safety_reach.py")
CRISIS_TESTS = ("tests/test_crisis.py",)
ENTITLEMENT_TESTS = ("tests/test_entitlements.py",)

CATALOGUE: list[Mutant] = [
    # ── Safety: the floor ────────────────────────────────────────────────
    Mutant(
        id="S1-floor-becomes-fallback",
        breaks=(
            "An under-flagging classifier silently overrides an explicit "
            "self-harm phrase. This is not hypothetical: the LLM rated "
            "'hopeless … cannot go on' below its severity, which is why the "
            "keyword net is a FLOOR and not a no-LLM fallback."
        ),
        path=SAFETY,
        old="    if _RANK[kw_risk] >= _RANK[llm_risk]:",
        new='    if llm_risk == "none":',
        caught_by=SAFETY_TESTS,
    ),
    Mutant(
        id="S2-floor-inverted",
        breaks="The LOWER of the two verdicts wins, so every disagreement resolves toward silence.",
        path=SAFETY,
        old="    if _RANK[kw_risk] >= _RANK[llm_risk]:",
        new="    if _RANK[kw_risk] <= _RANK[llm_risk]:",
        caught_by=SAFETY_TESTS,
    ),
    Mutant(
        id="S3-errors-fail-silent",
        breaks=(
            "A crash inside the keyword net resolves to 'none' instead of "
            "'elevated'. The floor's whole contract is that failure flags "
            "rather than goes quiet."
        ),
        path=SAFETY,
        old='        return "elevated", "keyword floor errored; flagged conservatively"',
        new='        return "none", "keyword floor errored"',
        caught_by=SAFETY_TESTS,
    ),
    Mutant(
        id="S4-non-latin-crisis-unseen",
        breaks=(
            "Crisis phrases written in Devanagari and other non-Latin scripts "
            "stop being matched — in a product whose consent notice ships in "
            "13 Indian languages."
        ),
        path=SAFETY,
        old="        for term in _CRISIS_TERMS_NON_LATIN:\n            if term in text:\n                return \"crisis\", f\"matched phrase: {term}\"\n",
        new="",
        caught_by=SAFETY_TESTS,
    ),
    Mutant(
        id="S5-folding-removed",
        breaks=(
            "Normalisation stops, so an apostrophe or a diacritic evades the "
            "net: \"can't go on\" is seen and \"cant go on\" is not."
        ),
        path=SAFETY,
        old="        folded = _fold(text)",
        new="        folded = text.lower()",
        caught_by=SAFETY_TESTS,
    ),
    Mutant(
        id="S6-elevated-never-recorded",
        breaks=(
            "Only 'crisis' creates a SafetyEvent, so everything the floor "
            "rated 'elevated' leaves no record for anyone to act on."
        ),
        path=SAFETY,
        old='    if risk_level in {"elevated", "crisis"}:',
        new='    if risk_level == "crisis":',
        caught_by=SAFETY_TESTS,
    ),
    # ── Entitlements: who paid for what ──────────────────────────────────
    Mutant(
        id="E1-sponsorship-ignored",
        breaks=(
            "A member whose employer pays is shown a paywall for something "
            "already bought for them."
        ),
        path=ENTITLEMENTS,
        old="    if await org_service.is_sponsored(db, user.id):\n        return Entitlement(tier=SPONSORED_TIER, sponsored=True)\n",
        new="",
        caught_by=ENTITLEMENT_TESTS,
    ),
    Mutant(
        id="E2-sponsored-flag-lost",
        breaks=(
            "Sponsored members are told they have a subscription they can "
            "cancel. They cannot — it is their employer's."
        ),
        path=ENTITLEMENTS,
        old="        return Entitlement(tier=SPONSORED_TIER, sponsored=True)",
        new="        return Entitlement(tier=SPONSORED_TIER, sponsored=False)",
        caught_by=ENTITLEMENT_TESTS,
    ),
    Mutant(
        id="E3-sponsorship-invents-a-tier",
        breaks=(
            "Sponsorship grants a fourth tier string no client branches on, so "
            "every client renders a paywall to someone entitled to skip it — "
            "the exact reason SPONSORED_TIER is an existing value."
        ),
        path=ENTITLEMENTS,
        old='SPONSORED_TIER = "premium"',
        new='SPONSORED_TIER = "sponsored"',
        caught_by=ENTITLEMENT_TESTS,
    ),
    Mutant(
        id="E4-anonymous-is-paid",
        breaks="Signed-out callers resolve to premium, so the paywall stops existing.",
        path=ENTITLEMENTS,
        old="    if user is None:\n        return FREE",
        new='    if user is None:\n        return Entitlement(tier="premium", sponsored=False)',
        caught_by=ENTITLEMENT_TESTS,
    ),
    Mutant(
        id="E5-paid-check-inverted",
        breaks="A paid tier is treated as unpaid and vice versa.",
        path=ENTITLEMENTS,
        old="    if stored in PAID_TIERS:",
        new="    if stored not in PAID_TIERS:",
        caught_by=ENTITLEMENT_TESTS,
    ),
    Mutant(
        id="E6-is-paid-always-true",
        breaks="Every gate that asks `is_paid` opens, for everyone.",
        path=ENTITLEMENTS,
        old="        return self.tier in PAID_TIERS",
        new="        return True",
        caught_by=ENTITLEMENT_TESTS,
    ),
    # ── Crisis: the right number, for the right country ──────────────────
    #
    # Everything in this module ends in somebody dialling something, which
    # makes it the highest-harm code in the repo. C1 reproduces a bug that has
    # actually happened here: a UK helpline reaching Indian users.
    Mutant(
        id="C1-unknown-region-gets-a-country",
        breaks=(
            "An unrecognised region falls back to ONE country's lines instead "
            "of the international default, so a user in an unlisted country is "
            "handed a helpline that does not answer where they are."
        ),
        path=CRISIS,
        old="    return _REGIONS.get(normalize_region(region), _DEFAULT)",
        new='    return _REGIONS.get(normalize_region(region), _REGIONS["GB"])',
        caught_by=CRISIS_TESTS,
    ),
    Mutant(
        id="C2-telemanas-loses-its-place",
        breaks=(
            "India stops leading with Tele-MANAS. The rule is Tele-MANAS-first "
            "on every crisis surface (REDESIGN §2.3) and all three clients "
            "already lead with it, so this is the server disagreeing with every "
            "screen the user can see."
        ),
        path=CRISIS,
        old='        {"name": "Tele-MANAS mental health support", "number": "14416"},',
        new='        {"name": "KIRAN mental health helpline", "number": "1800-599-0019"},',
        caught_by=CRISIS_TESTS,
    ),
    Mutant(
        id="C3-region-case-not-normalised",
        breaks=(
            "A lower-case region code from a device locale ('in') matches "
            "nothing, so Indian users silently get the international default "
            "instead of Tele-MANAS."
        ),
        path=CRISIS,
        old='    return (region or "").strip().upper()[:2]',
        new='    return (region or "").strip()[:2]',
        caught_by=CRISIS_TESTS,
    ),
    Mutant(
        id="C4-region-not-truncated",
        breaks=(
            "A full locale ('IN-MH', 'en-IN') would no longer resolve to its "
            "country — 'en-IN' truncates to 'EN', which is not India, so an "
            "Indian user would get the international default instead of "
            "Tele-MANAS."
        ),
        path=CRISIS,
        old='    return (region or "").strip().upper()[:2]',
        new='    return (region or "").strip().upper()',
        caught_by=CRISIS_TESTS,
        equivalent=(
            "Proven equivalent 2026-08-22, not assumed. No locale can reach "
            "this function: `schemas/user._known_region` REJECTS anything "
            "outside KNOWN_REGIONS (a 2-letter set plus ''), and all four "
            "callers — chat, work, journal (x2) and oracle — pass `user.region`, "
            "which went through that validator. `[:2]` is therefore defence in "
            "depth over an already-constrained value. If this mutant is ever "
            "CAUGHT, the validator has been loosened and the truncation has "
            "become load-bearing — which is exactly what this entry is here to "
            "notice."
        ),
    ),
    Mutant(
        id="C5-crisis-reply-names-no-number",
        breaks=(
            "The reply suffix renders no lines at all — the one message sent to "
            "somebody who has just disclosed danger contains no number to call."
        ),
        path=CRISIS,
        old="for line in lines[:2])",
        new="for line in lines[:0])",
        caught_by=CRISIS_TESTS,
    ),
    Mutant(
        id="C6-reply-ignores-the-region",
        breaks=(
            "The chat reply always names the international default, however well "
            "the rest of the app resolved the user's country."
        ),
        path=CRISIS,
        old="    lines = lines_for(region)",
        new="    lines = _DEFAULT",
        caught_by=CRISIS_TESTS,
    ),
    Mutant(
        id="C7-an-emergency-number-drifts",
        breaks="A country's emergency number is wrong — 911 becomes 999 in the US.",
        path=CRISIS,
        old='        {"name": "Emergency services", "number": "911"},\n        {"name": "988 Suicide & Crisis Lifeline", "number": "988"},',
        new='        {"name": "Emergency services", "number": "999"},\n        {"name": "988 Suicide & Crisis Lifeline", "number": "988"},',
        caught_by=CRISIS_TESTS,
    ),
]
