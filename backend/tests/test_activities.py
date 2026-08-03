"""Chat-driven activity routing (inline widgets + suggestion chips)."""
from app.services import activities


def test_route_breathing_for_anxiety():
    widget, suggestions = activities.route("I feel really anxious", "none")
    assert widget is not None and widget.widget_kind == "breathing"
    assert len(suggestions) >= 1


def test_route_grounding_for_overthinking():
    widget, _ = activities.route("I keep overthinking everything", "none")
    assert widget is not None and widget.widget_kind == "grounding"


def test_route_dbt_skill_for_intense_urge():
    widget, _ = activities.route("I have a strong urge and I'm so angry", "none")
    assert widget is not None and widget.widget_kind == "dbt_skill"


def test_route_one_good_thing_for_gratitude():
    widget, _ = activities.route("something good went well today, I feel grateful", "none")
    assert widget is not None and widget.widget_kind == "one_good_thing"


def test_new_widget_kinds_resolve():
    for kind in ("one_good_thing", "intention_set", "dbt_skill"):
        assert activities.widget_for(kind) is not None


def test_route_none_still_suggests_chips():
    widget, suggestions = activities.route("the weather is nice", "none")
    assert widget is None
    assert suggestions  # always offers a couple of quick replies


def test_crisis_risk_adds_urgent_suggestion():
    _, suggestions = activities.route("everything is too much", "crisis")
    assert any(s.action == "crisis" for s in suggestions)


def test_crisis_narrows_no_widget_two_chips():
    """Top-grade turns narrow, not widen: no activity card competes with the
    crisis card, and the chips reduce to support + breath (2026-08-04)."""
    widget, suggestions = activities.route("I want to vent, it's all too much", "crisis")
    assert widget is None
    assert [s.action for s in suggestions] == ["crisis", "breathing"]


def test_write_tools_stand_down_on_elevated_and_crisis():
    from app.agent.tools import write_suppressed

    assert write_suppressed("crisis")
    assert write_suppressed("elevated")
    assert not write_suppressed("none")
    assert not write_suppressed("low")


async def test_reply_prompt_carries_no_contradiction_card_hint(auth_client, monkeypatch):
    """The card is chosen BEFORE the reply and the model is told it exists, so
    it can't claim it "can't start" the exercise rendered right under it."""
    captured = {}

    async def fake_complete(system, transcript, max_tokens=200):
        captured["system"] = system
        return "Let's slow things down together."

    monkeypatch.setattr("app.api.routes.chat.ai.complete", fake_complete)
    r = await auth_client.post("/chat/messages", json={"text": "I feel really anxious"})
    assert r.status_code == 201
    assert "2-minute breathing" in captured["system"]
    assert "never" in captured["system"].lower()


async def test_chat_reply_includes_widget(auth_client):
    r = await auth_client.post("/chat/messages", json={"text": "I feel so anxious right now"})
    assert r.status_code == 201
    body = r.json()
    assert body["widget"]["widget_kind"] == "breathing"
    assert len(body["suggestions"]) >= 1
    assert all({"label", "action"} <= set(s) for s in body["suggestions"])
