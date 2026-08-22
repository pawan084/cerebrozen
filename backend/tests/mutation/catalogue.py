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


SAFETY = "app/services/safety.py"
ENTITLEMENTS = "app/services/entitlements.py"
SAFETY_TESTS = ("tests/test_safety_floor.py", "tests/test_safety_reach.py")
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
]
