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


async def test_nudge_authoring_broadcast_and_targeted(admin_client):
    me = (await admin_client.get("/auth/me")).json()

    # Broadcast reaches every active user (at least this admin).
    r = await admin_client.post(
        "/admin/nudges",
        json={"title": "Evening wind-down live", "body": "The new sleep guide is waiting."},
    )
    assert r.status_code == 201
    assert r.json()["created"] >= 1

    # Targeted at one account.
    r = await admin_client.post(
        "/admin/nudges",
        json={"title": "Just for you", "body": "A gentle check-in.", "user_id": me["id"]},
    )
    assert r.status_code == 201
    assert r.json()["created"] == 1

    # Unknown target → 404; nothing created.
    r = await admin_client.post(
        "/admin/nudges",
        json={"title": "x", "body": "y", "user_id": str(uuid.uuid4())},
    )
    assert r.status_code == 404

    rows = (await admin_client.get("/admin/nudges", params={"kind": "announcement"})).json()
    titles = [n["title"] for n in rows]
    assert "Evening wind-down live" in titles and "Just for you" in titles
    assert all(n["kind"] == "announcement" for n in rows)


async def test_streak_endpoint_mirrors_ios_rules(auth_client):
    r = await auth_client.get("/users/me/streak")
    assert r.status_code == 200
    assert r.json()["current"] == 0
    assert len(r.json()["week"]) == 7

    # Activity today and two days ago: today counts, yesterday is the one
    # grace day (uncounted), the run ends at the day-3 gap → streak 2.
    import uuid as _uuid
    from datetime import datetime, time, timedelta, timezone

    from app.core.database import SessionLocal
    from app.models.mood import MoodLog

    uid = _uuid.UUID((await auth_client.get("/auth/me")).json()["id"])
    async with SessionLocal() as s:
        for days_ago in (0, 2):
            d = date.today() - timedelta(days=days_ago)
            s.add(MoodLog(user_id=uid, mood="Good", intensity=2,
                          created_at=datetime.combine(d, time(10, 0), tzinfo=timezone.utc)))
        await s.commit()

    body = (await auth_client.get("/users/me/streak")).json()
    assert body["current"] == 2
    assert body["best"] >= 1
    assert body["week"][-1]["active"] is True   # today
    assert body["week"][-2]["active"] is False  # the grace day
