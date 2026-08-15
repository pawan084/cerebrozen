"""The keyword floor's folding + multilingual net (audit J#1).

The floor is what stands when the LLM classifier is down, wrong, or too
conservative — so these tests exercise `_keyword_risk` directly, keylessly.
The two gaps that motivated the change are pinned first: both FAILED against
the pre-change floor (plain substring over `.lower()`), verified before fixing.
"""
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
