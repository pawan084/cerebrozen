"""#25: every crisis takeover is counted as a CONTENT-FREE safety event for the release-gate
metrics — labelled only by detection layer (lexicon / classifier) and language, never a word
the person wrote (CLAUDE.md rule 5). The counter is the cheapest signal that the one path
that must never silently break is still firing."""

import pytest

from app import metrics
from app.graph import nodes


def test_record_crisis_increments_the_counter():
    if not metrics._ENABLED:
        pytest.skip("prometheus_client not installed → metrics are a no-op")
    before = metrics.CRISIS_TRIGGERED.labels("lexicon", "en")._value.get()
    metrics.record_crisis(detected_by="lexicon", lang="en")
    after = metrics.CRISIS_TRIGGERED.labels("lexicon", "en")._value.get()
    assert after == before + 1


def test_record_crisis_never_raises_on_blank_labels():
    metrics.record_crisis(detected_by="", lang="")  # unknown/unknown, must not raise


def test_a_crisis_takeover_is_counted_by_layer_and_language(monkeypatch):
    """The takeover fires the counter with the detection layer + language — and nothing else,
    so a person's disclosure can never reach the metric."""
    seen = []
    monkeypatch.setattr(metrics, "record_crisis", lambda **kw: seen.append(kw))
    monkeypatch.setattr("app.safety.escalation.escalate", lambda **kw: True)

    out = nodes.safety_node({"user_message": "i want to end my life"}, {})

    assert out["safety_flag"] == "crisis"
    assert seen == [{"detected_by": "lexicon", "lang": "en"}], "crisis uncounted, or content leaked"


def test_an_ordinary_message_fires_no_crisis_metric(monkeypatch):
    seen = []
    monkeypatch.setattr(metrics, "record_crisis", lambda **kw: seen.append(kw))

    out = nodes.safety_node({"user_message": "i had a rough day at work"}, {})

    assert out["safety_flag"] != "crisis"
    assert seen == [], "a non-crisis turn must not fire the crisis counter"
