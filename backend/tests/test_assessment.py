"""Self-reflection assessment: taxonomy + conversation-topic generation.

These exercise the deterministic (key-free) path; CI runs with no LLM key so
``generate_topics`` always falls back to the curated seeds.
"""
import pytest

from app.services import assessment


def test_normalize_drops_unknown_selections():
    mots, goals = assessment.normalize_selection(["Focus", "Endurance"], ["Sleep better", "Bogus"])
    assert mots == ["Focus"]          # "Endurance" is not in the taxonomy
    assert goals == ["Sleep better"]  # "Bogus" dropped


def test_normalize_defaults_when_empty():
    mots, goals = assessment.normalize_selection([], [])
    assert mots == []
    assert goals == ["Reduce stress"]  # gentle default so generation has input


def test_fallback_count_and_contract():
    """The deterministic path (no LLM): exact count, ≤6 words, deduped."""
    topics = assessment._fallback_topics(["Focus"], ["Sleep better", "Stop overthinking"], 8)
    assert len(topics) == 8
    seen = set()
    for t in topics:
        assert 1 <= len(t.split()) <= 6
        assert t.lower() not in seen
        seen.add(t.lower())


def test_fallback_reflects_selection():
    topics = assessment._fallback_topics([], ["Sleep better"], 6)
    joined = " ".join(topics).lower()
    # Sleep-specific seeds should surface for a sleep-focused selection.
    assert "night" in joined or "bed" in joined


async def test_generate_topics_contract_any_source():
    """Holds whether the LLM is configured (``ai``) or not (``rule``)."""
    topics, source = await assessment.generate_topics(
        ["Focus"], ["Sleep better", "Stop overthinking"], count=8
    )
    assert source in {"ai", "rule"}
    assert len(topics) == 8
    seen = set()
    for t in topics:
        assert {"id", "topic"} <= set(t)
        assert 1 <= len(t["topic"].split()) <= 6   # ≤6-word, tappable
        assert t["topic"].lower() not in seen      # deduped
        seen.add(t["topic"].lower())


@pytest.mark.parametrize("count,expected", [(2, 4), (50, 12), (8, 8)])
async def test_count_is_clamped(count, expected, monkeypatch):
    # Force the deterministic path so the clamp is exercised independently of any
    # configured LLM (CI runs hermetically, with no key).
    async def no_ai(system, prompt, max_tokens=1024):
        return None

    monkeypatch.setattr(assessment.ai, "complete_json", no_ai)
    topics, source = await assessment.generate_topics(["Focus", "Calm"], ["Reduce stress"], count=count)
    assert source == "rule"
    assert len(topics) == expected


async def test_generate_topics_ai_branch(monkeypatch):
    """Cover the AI-result parsing path (≤6-word + dedup enforcement) deterministically."""
    async def fake_ai(system, prompt, max_tokens=1024):
        return {"topics": [
            {"id": 1, "topic": "Managing pressure before meetings"},
            {"id": 2, "topic": "Managing pressure before meetings"},      # dupe → dropped
            {"id": 3, "topic": "This topic has far too many words to keep"},  # >6 words → dropped
            {"id": 4, "topic": "Why I keep overthinking"},
        ]}

    monkeypatch.setattr(assessment.ai, "complete_json", fake_ai)
    topics, source = await assessment.generate_topics(["Focus"], ["Stop overthinking"], count=8)
    assert source == "ai"
    labels = [t["topic"] for t in topics]
    assert "Managing pressure before meetings" in labels
    assert "Why I keep overthinking" in labels
    assert labels.count("Managing pressure before meetings") == 1     # deduped
    assert all(len(t["topic"].split()) <= 6 for t in topics)          # long one filtered


async def test_topics_reflect_selected_motivations(monkeypatch):
    """The selected *motivations* actually shape the starters (and change them).

    This is the generation half of the onboarding→Talk-starters chain: whatever
    motivations the user picks during self-reflection must surface as grounded,
    distinct conversation starters. Forces the deterministic path (no LLM).
    """
    async def no_ai(system, prompt, max_tokens=1024):
        return None
    monkeypatch.setattr(assessment.ai, "complete_json", no_ai)

    async def starters(mots, goals):
        items, source = await assessment.generate_topics(mots, goals, count=6)
        assert source == "rule"
        return [t["topic"].lower() for t in items]

    calm = await starters(["Calm"], [])
    conf = await starters(["Confidence"], [])

    # Each motivation surfaces its own seeds…
    assert any("worry" in t or "tension" in t for t in calm), calm
    assert any("doubt" in t or "sell myself" in t for t in conf), conf
    # …and a different motivation produces a different starter set.
    assert set(calm) != set(conf)
    assert not any("doubt" in t for t in calm)          # no confidence leakage into calm

    # A full onboarding-style selection draws from EVERY chosen pool.
    mix = await starters(["Focus", "Calm", "Discipline"], ["Build confidence", "Sleep better"])
    assert any("focus" in t or "distraction" in t for t in mix)       # Focus
    assert any("worry" in t or "tension" in t for t in mix)           # Calm
    assert any("promise" in t or "follow-through" in t for t in mix)  # Discipline
    assert any("decision" in t or "win" in t for t in mix)            # Build confidence (goal)


async def test_topics_endpoint_honors_request_selection(auth_client, monkeypatch):
    """End-to-end through the API: the motivations in the request body drive the
    returned starters (the exact call the iOS `loadStarters` makes)."""
    async def no_ai(system, prompt, max_tokens=1024):
        return None
    monkeypatch.setattr(assessment.ai, "complete_json", no_ai)

    r = await auth_client.post(
        "/assessment/topics",
        json={"motivations": ["Confidence"], "goals": ["Build confidence"], "count": 6},
    )
    assert r.status_code == 200
    joined = " ".join(t["topic"].lower() for t in r.json()["topics"])
    assert "doubt" in joined or "sell myself" in joined or "decision" in joined or "win" in joined


async def test_structure_endpoint(auth_client):
    r = await auth_client.get("/assessment/structure")
    assert r.status_code == 200
    body = r.json()
    assert "Focus" in body["motivations"]
    assert "Reduce stress" in body["goals"]["Daily Rituals"]


async def test_topics_endpoint_with_explicit_selection(auth_client):
    r = await auth_client.post(
        "/assessment/topics",
        json={"motivations": ["Confidence"], "goals": ["Build confidence"], "count": 6},
    )
    assert r.status_code == 200
    body = r.json()
    assert len(body["topics"]) == 6
    assert body["source"] in {"ai", "rule"}
    assert all(len(t["topic"].split()) <= 6 for t in body["topics"])


async def test_topics_endpoint_falls_back_to_saved_selection(auth_client):
    # No body → uses the user's saved motivations/goals (empty → gentle default).
    r = await auth_client.post("/assessment/topics", json={})
    assert r.status_code == 200
    assert len(r.json()["topics"]) >= 4


async def test_ai_topics_anchor_primary_selection(monkeypatch):
    """Even with LLM topics, one topic must be the curated anchor for the
    user's first selection (their explicit choice is always visible)."""
    from app.services import ai, assessment

    async def fake_json(*a, **k):
        return {"topics": [{"id": i, "topic": f"Something the model wrote {i}"} for i in range(1, 9)]}

    monkeypatch.setattr(ai, "complete_json", fake_json)
    items, source = await assessment.generate_topics(["Confidence"], ["Build confidence"])
    assert source == "ai"
    texts = [t["topic"] for t in items]
    assert assessment._TOPIC_SEEDS["Confidence"][0] in texts
