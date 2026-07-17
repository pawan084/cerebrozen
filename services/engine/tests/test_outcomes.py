"""Coaching-outcomes progress read — counts and follow-through, never content.

Drives the aggregation against a seeded agentic doc (the real read path, agentic.load) and
the public shape via the endpoint. The load-bearing assertions: the maths is right, an empty
history is an honest "no data" not a fake zero-rate, and no action body reaches the output.
"""

from __future__ import annotations

import jwt
import pytest
from fastapi.testclient import TestClient

from app import config, outcomes
from app.main import create_app


@pytest.fixture
def client(monkeypatch):
    """The app with auth ENFORCED (the outcomes endpoint takes the user only from the
    token, so the dev bypass — which yields empty claims — makes it unreachable). Mirrors
    test_api_layer.authed_client."""
    monkeypatch.setenv("AUTH_DEV_BYPASS", "")
    monkeypatch.setattr(config, "ENV", "production")
    monkeypatch.setattr(config, "JWT_SECRET", "s3cret")
    return TestClient(create_app(), raise_server_exceptions=False)


def _auth(user_id: str) -> dict:
    """A signed token carrying a user — the endpoint takes the subject only from here."""
    raw = jwt.encode({"user": {"username": user_id}, "org_id": "default"},
                     "s3cret", algorithm=config.JWT_ALGORITHM)
    return {"Authorization": f"Bearer {raw}"}


def _seed(coll, user_id):
    coll.insert_one({
        "user_id": user_id,
        "sessions_completed": 3,
        "checkin_complete_sessions": ["s-done"],
        "actions": [
            # committed + its session's check-in was completed -> followed through
            {"status": "saved", "roi_metrics": ["Decision-making"], "session_id": "s-done",
             "full_text": "SECRET commitment body A"},
            # committed but session not checked in
            {"status": "saved", "roi_metrics": ["Decision-making"], "session_id": "s-open",
             "full_text": "SECRET commitment body B"},
            {"status": "active", "roi_metrics": ["Communication"], "session_id": "s-open"},
            {"status": "skipped", "roi_metrics": ["Communication"], "session_id": "s-open"},
            {"status": "deleted", "roi_metrics": ["Well-being"], "session_id": "s-open"},
        ],
    })


def test_empty_history_is_honest_about_having_no_data(agentic_coll):
    p = outcomes.progress("nobody-here")
    assert p["actions_total"] == 0
    assert p["sessions_completed"] == 0
    assert p["follow_through"]["rate"] is None  # None, not a fake 0.0
    assert p["development_areas"] == []


def test_the_aggregation_is_correct(agentic_coll):
    _seed(agentic_coll, "u-out")
    p = outcomes.progress("u-out")

    assert p["actions_total"] == 5
    assert p["sessions_completed"] == 3
    assert p["by_status"] == {"saved": 2, "active": 1, "skipped": 1, "deleted": 1}

    # deleted is excluded from Development Areas; alphabetical order
    areas = {a["area"]: a for a in p["development_areas"]}
    assert set(areas) == {"Communication", "Decision-making"}
    assert areas["Decision-making"] == {"area": "Decision-making", "actions": 2, "committed": 2}
    assert areas["Communication"] == {"area": "Communication", "actions": 2, "committed": 0}

    # 2 committed, 1 in a checked-in session -> 0.5
    assert p["follow_through"] == {"committed": 2, "checked_in": 1, "rate": 0.5}


def test_no_action_body_leaks_into_the_snapshot(agentic_coll):
    """Counts, never content — the seeded full_text must not appear anywhere in the output."""
    _seed(agentic_coll, "u-out")
    assert "SECRET" not in str(outcomes.progress("u-out"))


def test_legacy_single_roi_field_is_tolerated(agentic_coll):
    agentic_coll.insert_one({
        "user_id": "u-legacy",
        "actions": [{"status": "saved", "roi_metric": "Focus", "session_id": "s1"}],
    })
    p = outcomes.progress("u-legacy")
    assert p["development_areas"] == [{"area": "Focus", "actions": 1, "committed": 1}]


def test_endpoint_returns_the_callers_own_progress(client, agentic_coll):
    """No user_id parameter — the subject comes only from the token."""
    _seed(agentic_coll, "u-ep")
    r = client.get("/v1/outcomes", headers=_auth("u-ep"))
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["spec"] == outcomes.SPEC
    assert body["actions_total"] == 5
    assert body["follow_through"]["rate"] == 0.5


def test_endpoint_refuses_a_token_with_no_user(client):
    """A userless token has no subject to report on — 400, not someone else's data."""
    raw = jwt.encode({"org_id": "default"}, "s3cret", algorithm=config.JWT_ALGORITHM)
    r = client.get("/v1/outcomes", headers={"Authorization": f"Bearer {raw}"})
    assert r.status_code == 400
