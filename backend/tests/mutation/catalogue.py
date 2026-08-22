"""Mutants for the code where a false-passing test costs the most (WC-277).

A test suite tells you the code passes. It does not tell you the tests would
notice if the code were wrong — and here that difference is measured in human
harm, in money, and in whose data somebody can read:

* ``services/safety.py`` decides whether an explicit self-harm phrase is seen.
* ``services/crisis.py`` decides which number a person is given.
* ``services/entitlements.py`` decides who has paid for what.
* ``models/consent.py`` and ``services/organizations.py`` decide what an
  employer may see.
* The **tenancy** checks (T1–T8) decide whether one account can reach another's
  rows. Those are the ones this file exists for most: every one of them is
  currently PRESENT, and a present check is invisible — deleting one changes no
  test's outcome unless a test calls the route as the wrong account.

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
CONSENT = "app/models/consent.py"
ORG = "app/services/organizations.py"
SAFETY_TESTS = ("tests/test_safety_floor.py", "tests/test_safety_reach.py")
CRISIS_TESTS = ("tests/test_crisis.py",)
ENTITLEMENT_TESTS = ("tests/test_entitlements.py",)
CONSENT_TESTS = ("tests/test_consent_enforced.py",)
ORG_TESTS = ("tests/test_org.py", "tests/test_org_roles.py")
JOURNAL = "app/api/routes/journal.py"
MOODS = "app/api/routes/moods.py"
PLANS = "app/api/routes/plans.py"
USERS = "app/api/routes/users.py"
INTERVENTIONS = "app/services/interventions.py"
ORG_ROUTES = "app/api/routes/organizations.py"
TENANCY_TESTS = ("tests/test_cross_user_access.py",)
ORG_TENANCY_TESTS = ("tests/test_org_tenant_isolation.py",)

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
    # ── Privacy: the two places a fail-open is a breach, not a bug ────────
    #
    # Safety and money get caught by somebody noticing. These two do not: a
    # consent gate that opens, or a suppression threshold that stops
    # suppressing, produces output that looks exactly like correct output. The
    # user never sees the difference — which is precisely why they are mutated.
    Mutant(
        id="P1-consent-fails-open-when-absent",
        breaks=(
            "A user with no consent row reads as having granted everything. "
            "Absence of a recorded decision is not a decision, and this is the "
            "one case where we know least about what the person wanted — it "
            "used to default permissive, which is the bug this guards."
        ),
        path=CONSENT,
        old="    return consent is not None and bool(getattr(consent, flag))",
        new="    return consent is None or bool(getattr(consent, flag))",
        caught_by=CONSENT_TESTS,
    ),
    Mutant(
        id="P2-consent-always-granted",
        breaks=(
            "Every consent check opens. Chat memory, journal titles, sleep "
            "history and voice storage are all read regardless of the switches "
            "the privacy screen shows — while that screen keeps showing them."
        ),
        path=CONSENT,
        old="    return consent is not None and bool(getattr(consent, flag))",
        new="    return True",
        caught_by=CONSENT_TESTS,
    ),
    Mutant(
        id="P3-suppression-off-by-one",
        breaks=(
            "A group EXACTLY at the threshold stops being suppressed. The "
            "boundary is the whole rule: at 20 with a threshold of 20, the "
            "number is reportable; at 19 it is not."
        ),
        path=ORG,
        old="    suppressed = eligible < threshold",
        new="    suppressed = eligible < threshold - 1",
        caught_by=ORG_TESTS,
    ),
    Mutant(
        id="P4-suppression-measures-the-wrong-population",
        breaks=(
            "Suppression tests the SIZE OF THE NUMBER instead of the size of "
            "the population it describes, so a large group with few activations "
            "is hidden behind 'too small to report'. An employer who bought 25 "
            "seats and has 3 users is told there is no data, when low usage IS "
            "the finding.\n\n"
            "            Worth stating precisely, because the first draft of "
            "this entry claimed the opposite — that it would report a group of "
            "4 and identify all four. It cannot: `activated <= eligible` "
            "always, so this mutation can only ever OVER-suppress and never "
            "leak. A false 'no data' is safe and wrong; the leak lives in P3."
        ),
        path=ORG,
        old="    suppressed = eligible < threshold",
        new="    suppressed = activated < threshold",
        caught_by=ORG_TESTS,
    ),
    Mutant(
        id="P5-suppressed-groups-still-report-counts",
        breaks=(
            "A group marked suppressed reports its real numbers anyway — the "
            "flag says 'hidden' while the payload carries the counts."
        ),
        path=ORG,
        old="        activated=None if suppressed else activated,\n        active=None if suppressed else activated,",
        new="        activated=activated,\n        active=activated,",
        caught_by=ORG_TESTS,
    ),
    Mutant(
        id="P6-threshold-floor-removed",
        breaks=(
            "An administrator can set the reporting threshold to 1, which is "
            "no suppression at all. The floor exists because the number is not "
            "the organisation's to choose downward."
        ),
        path=ORG,
        old="    return max(int(value), MIN_REPORTING_THRESHOLD)",
        new="    return int(value)",
        caught_by=ORG_TESTS,
    ),
    Mutant(
        id="C7-an-emergency-number-drifts",
        breaks="A country's emergency number is wrong — 911 becomes 999 in the US.",
        path=CRISIS,
        old='        {"name": "Emergency services", "number": "911"},\n        {"name": "988 Suicide & Crisis Lifeline", "number": "988"},',
        new='        {"name": "Emergency services", "number": "999"},\n        {"name": "988 Suicide & Crisis Lifeline", "number": "988"},',
        caught_by=CRISIS_TESTS,
    ),
    # ── Tenancy: one account reaching another's rows (WC-74) ─────────────
    #
    # Every one of these is a check that is currently PRESENT, which is exactly
    # why it is here: a present check is invisible, and deleting one changes no
    # test's outcome unless a test calls the route as the wrong account. All
    # eight were confirmed killed on 2026-08-22.
    Mutant(
        id="T1-journal-entry-ownership-dropped",
        breaks=(
            "Anyone can read and overwrite anyone else's journal entry by id. "
            "The most private prose in the product, addressable by a uuid."
        ),
        path=JOURNAL,
        old="    if entry is None or entry.user_id != user.id:",
        new="    if entry is None:",
        caught_by=TENANCY_TESTS,
    ),
    Mutant(
        id="T2-remembered-item-ownership-dropped",
        breaks=(
            "One account can rewrite what CereBro remembers about another — "
            "not read it, but change what the assistant believes about them."
        ),
        path=USERS,
        old="    if row is None or row.user_id != user.id:",
        new="    if row is None:",
        caught_by=TENANCY_TESTS,
    ),
    Mutant(
        id="T3-mood-scoping-dropped-from-the-query",
        breaks=(
            "Anyone can delete anyone else's mood log. Scoped inside the WHERE "
            "clause, so no `if` shows the check going missing."
        ),
        path=MOODS,
        old="        select(MoodLog).where(MoodLog.id == mood_id, MoodLog.user_id == user.id)",
        new="        select(MoodLog).where(MoodLog.id == mood_id)",
        caught_by=TENANCY_TESTS,
    ),
    Mutant(
        id="T4-plan-step-checks-the-step-not-the-plan",
        breaks=(
            "A plan step is owned indirectly: the step names a plan, and the "
            "plan names a user. Drop the second hop and anyone can tick off "
            "anyone's plan."
        ),
        path=PLANS,
        old="    if plan is None or plan.user_id != user.id:",
        new="    if plan is None:",
        caught_by=TENANCY_TESTS,
    ),
    Mutant(
        id="T5-open-offer-not-scoped-to-its-owner",
        breaks=(
            "GET /interventions/active serves another person's open offer — "
            "its reason prose and the state_snapshot numbers behind it. A read "
            "of somebody's inferred state, through a route every client polls."
        ),
        path=INTERVENTIONS,
        old=(
            "            InterventionRecommendation.user_id == user.id,\n"
            "            InterventionRecommendation.accepted_at.is_(None),"
        ),
        new="            InterventionRecommendation.accepted_at.is_(None),",
        caught_by=TENANCY_TESTS,
    ),
    Mutant(
        id="T6-cooldown-becomes-global",
        breaks=(
            "One person's recent offer silences that rule for EVERY user. No "
            "data leaks, so it never looks like a security bug — it looks like "
            "the feature quietly not working, with the blast radius growing as "
            "the user base does."
        ),
        path=INTERVENTIONS,
        old=(
            "                InterventionRecommendation.user_id == user.id,\n"
            "                InterventionRecommendation.rule_slug == rule.slug,"
        ),
        new="                InterventionRecommendation.rule_slug == rule.slug,",
        caught_by=TENANCY_TESTS,
    ),
    Mutant(
        id="T7-seat-can-be-ended-across-organisations",
        breaks=(
            "An administrator of one customer cancels another customer's "
            "sponsored seat by naming its id. Broken tenant isolation, and it "
            "fails silently: the victim gets no signal at all."
        ),
        path=ORG_ROUTES,
        old="    if membership is None or membership.org_id != org.id:",
        new="    if membership is None:",
        caught_by=ORG_TENANCY_TESTS,
    ),
    Mutant(
        id="T8-import-writes-into-another-orgs-group",
        breaks=(
            "A CSV import names an eligibility group belonging to a different "
            "organisation, placing strangers inside that customer's cohort — "
            "and their reported totals then count people who are not theirs."
        ),
        path=ORG_ROUTES,
        old="        if group is None or group.org_id != org.id:",
        new="        if group is None:",
        caught_by=ORG_TENANCY_TESTS,
    ),
]
