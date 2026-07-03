import uuid
from datetime import date


async def test_metrics_overview_aggregates(admin_client):
    # The admin is a user too — its own activity feeds the aggregates.
    r = await admin_client.post("/moods", json={"mood": "Good", "note": "Clear", "intensity": 2})
    assert r.status_code == 201
    r = await admin_client.post(
        "/sleep",
        json={"date": date.today().isoformat(), "bedtime": "23:00:00", "wake_time": "07:00:00", "quality": 4},
    )
    assert r.status_code in (200, 201)

    r = await admin_client.get("/admin/metrics/overview")
    assert r.status_code == 200
    body = r.json()

    assert body["actives"]["dau"] >= 1
    assert body["actives"]["mau"] >= body["actives"]["dau"]
    assert body["signups"]["total"] >= 1
    for key in ("d1", "d7", "d30"):
        assert set(body["retention"][key]) == {"cohort", "retained", "rate"}
    assert body["engagement_7d"]["mood_logs"] >= 1
    assert body["engagement_7d"]["sleep_logs"] >= 1
    assert body["funnel"]["mood"] >= 1
    assert body["funnel"]["signups"] >= body["funnel"]["premium"]


async def test_admin_user_detail_is_metadata_only(admin_client):
    me = (await admin_client.get("/auth/me")).json()
    secret = "Deeply private journal body that support must never see"
    r = await admin_client.post(
        "/journal", json={"title": "Private thing", "body": secret, "tags": ["Work"]}
    )
    assert r.status_code == 201

    r = await admin_client.get(f"/admin/users/{me['id']}")
    assert r.status_code == 200
    body = r.json()
    assert body["user"]["email"] == me["email"]
    assert body["counts"]["journals"] >= 1
    assert body["counts"]["open_safety_events"] >= 0
    assert body["consent"] is not None
    assert body["last_active"] is not None
    # The support view carries counts, never content.
    assert secret not in r.text
    assert "Private thing" not in r.text


async def test_admin_user_detail_404(admin_client):
    r = await admin_client.get(f"/admin/users/{uuid.uuid4()}")
    assert r.status_code == 404
