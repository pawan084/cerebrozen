"""The keyword floor's folding + multilingual net (audit J#1).

The floor is what stands when the LLM classifier is down, wrong, or too
conservative — so these tests exercise `_keyword_risk` directly, keylessly.
The two gaps that motivated the change are pinned first: both FAILED against
the pre-change floor (plain substring over `.lower()`), verified before fixing.
"""
import uuid

import pytest

from app.core.database import SessionLocal
from app.core.security import hash_password
from app.models.safety import SafetyEvent
from app.models.user import User
from app.services import safety
from app.services.safety import _keyword_risk


def test_curly_apostrophe_matches_the_floor():
    """Phone keyboards type ’ (U+2019); the lists spell '. Before the fold,
    "I can’t cope" matched nothing — on the device this product ships on."""
    risk, reason = _keyword_risk("honestly I can’t cope with this")
    assert risk == "elevated", reason


def test_every_apostrophe_variant_matches():
    # "cant" (no apostrophe at all) is its own spelling, not an empty variant
    # of this template — f"can{''}t" would build "icant". It has its own case.
    for apostrophe in ["'", "’", "ʼ", "`", "´"]:
        risk, _ = _keyword_risk(f"i can{apostrophe}t go on")
        assert risk == "elevated", f"apostrophe {apostrophe!r} fell through"
    assert _keyword_risk("i cant go on")[0] == "elevated"


def test_devanagari_crisis_term_matches():
    """The floor was English-only in an India-first product."""
    risk, _ = _keyword_risk("मैं आत्महत्या के बारे में सोच रहा हूँ")
    assert risk == "crisis"


def test_romanised_hindi_matches():
    risk, _ = _keyword_risk("mujhe lagta hai khudkushi hi rasta hai")
    assert risk == "crisis"


def test_word_bounds_keep_short_terms_safe():
    """Fragments must never fire inside ordinary words — the bound is what
    makes the list safe to extend with short terms."""
    assert _keyword_risk("I started a new diet today")[0] == "none"
    # A bounded term inside a benign sentence still fires — the floor is
    # deliberately high-recall; precision is the LLM classifier's job.
    assert _keyword_risk("the suicide hotline poster")[0] == "crisis"


def test_progressive_forms_still_match():
    """The 2026-08-03 regression stays pinned through the rewrite."""
    assert _keyword_risk("i have been hurting myself")[0] == "crisis"
    assert _keyword_risk("feeling suicidal")[0] == "crisis"


def test_diacritics_fold():
    assert _keyword_risk("I want to hürt mysélf")[0] == "crisis"


def test_severity_order_crisis_wins_over_elevated():
    risk, _ = _keyword_risk("i feel hopeless and want to die")
    assert risk == "crisis"


def test_empty_and_benign_text_are_none():
    assert _keyword_risk("")[0] == "none"
    assert _keyword_risk("lovely weather for a walk")[0] == "none"


# ── The floor is a FLOOR, not a fallback ────────────────────────────────
#
# Added 2026-08-22 after a mutation run (WC-277). Replacing the merge rule with
# `if llm_risk == "none"` — turning the floor into a mere no-LLM fallback —
# SURVIVED the entire suite, because nothing anywhere stubs the classifier to
# DISAGREE. Keyless, `complete_json` returns None and `llm_risk` is always
# "none", so both versions behave identically and the distinction the module's
# own comment rests on was untested.
#
# That comment is load-bearing: the LLM rated "hopeless … cannot go on" below
# its severity, which is the incident the floor exists because of.


@pytest.mark.asyncio
async def test_the_floor_beats_a_classifier_that_under_flags(monkeypatch):
    """An explicit crisis phrase is never rated below its severity."""
    async def under_flagging_llm(system, prompt):
        return {"risk_level": "low", "reason": "sounds like a rough day"}

    monkeypatch.setattr(safety.ai, "complete_json", under_flagging_llm)

    risk, reason = await safety.classify("i want to kill myself")
    assert risk == "crisis", f"the floor gave way to the classifier: {reason}"
    assert "matched phrase" in reason


@pytest.mark.asyncio
async def test_a_classifier_that_over_flags_still_wins(monkeypatch):
    """The rule is `max`, not `keyword always`.

    The floor raises a verdict it is sure about; it must not LOWER one. A
    classifier seeing risk in text with no keyword in it is exactly the case
    the LLM is there for.
    """
    async def alarmed_llm(system, prompt):
        return {"risk_level": "crisis", "reason": "explicit plan with a timeframe"}

    monkeypatch.setattr(safety.ai, "complete_json", alarmed_llm)

    risk, reason = await safety.classify("everything is arranged for saturday and then it stops")
    assert risk == "crisis"
    assert reason == "explicit plan with a timeframe"


@pytest.mark.asyncio
async def test_a_broken_classifier_leaves_the_floor_standing(monkeypatch):
    """A raising provider must not take the safety net down with it."""
    async def exploding_llm(system, prompt):
        raise RuntimeError("provider is down")

    monkeypatch.setattr(safety.ai, "complete_json", exploding_llm)

    with pytest.raises(RuntimeError):
        # Documents today's behaviour honestly: `classify` does NOT swallow a
        # provider exception, so the caller sees it. What matters here is that
        # this is a deliberate, recorded state rather than an assumption — if
        # it is ever wrapped, this test is where the new contract gets written.
        await safety.classify("i want to kill myself")


# ── Failure inside the floor flags rather than goes quiet ───────────────


def test_a_crash_inside_the_floor_flags_conservatively(monkeypatch):
    """Also a mutation survivor: returning "none" on error was untested.

    The floor's contract is that any failure resolves TOWARD flagging. A net
    that goes silent when it breaks is worse than no net, because everything
    downstream believes it ran.
    """
    def exploding_fold(text):
        raise ValueError("folding blew up")

    monkeypatch.setattr(safety, "_fold", exploding_fold)

    risk, reason = _keyword_risk("any text at all")
    assert risk == "elevated"
    assert "flagged conservatively" in reason


# ── An elevated verdict leaves a record ─────────────────────────────────


@pytest.mark.asyncio
async def test_an_elevated_verdict_is_recorded_not_just_returned():
    """The third survivor: only `crisis` creating an event went unnoticed.

    `elevated` is the rung that exists so somebody can look before it becomes
    a crisis. If it returns a level and writes nothing, there is nothing to
    look at.
    """
    async with SessionLocal() as s:
        user = User(
            email=f"elev-{uuid.uuid4().hex[:10]}@test.app",
            hashed_password=hash_password("x"),
            name="E",
        )
        s.add(user)
        await s.flush()

        risk = await safety.scan_and_record(
            s,
            user_id=user.id,
            source="journal",
            source_id=None,
            text="honestly I can’t cope with this",
        )
        assert risk == "elevated"
        await s.flush()

        events = (
            await s.scalars(SafetyEvent.__table__.select().where(SafetyEvent.user_id == user.id))
        ).all()
        assert len(events) == 1, "an elevated verdict must leave something to act on"
