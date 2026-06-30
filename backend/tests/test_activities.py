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


async def test_chat_reply_includes_widget(auth_client):
    r = await auth_client.post("/chat/messages", json={"text": "I feel so anxious right now"})
    assert r.status_code == 201
    body = r.json()
    assert body["widget"]["widget_kind"] == "breathing"
    assert len(body["suggestions"]) >= 1
    assert all({"label", "action"} <= set(s) for s in body["suggestions"])
