from datetime import date, datetime, time, timedelta, timezone


def _iso(days_ago: int) -> str:
    return (date.today() - timedelta(days=days_ago)).isoformat()


async def test_sleep_upsert_and_list(auth_client):
    payload = {"date": _iso(0), "bedtime": "23:30:00", "wake_time": "07:00:00", "quality": 2}
    r = await auth_client.post("/sleep", json=payload)
    assert r.status_code == 201
    body = r.json()
    assert body["duration_min"] == 450  # crosses midnight
    assert body["source"] == "manual"

    # Re-submitting the same date edits the entry instead of duplicating it.
    r = await auth_client.post("/sleep", json={**payload, "quality": 4, "awakenings": 1})
    assert r.status_code == 200
    assert r.json()["quality"] == 4

    r = await auth_client.get("/sleep")
    assert r.status_code == 200
    entries = [e for e in r.json() if e["date"] == payload["date"]]
    assert len(entries) == 1 and entries[0]["awakenings"] == 1


async def test_sleep_after_midnight_duration(auth_client):
    r = await auth_client.post(
        "/sleep", json={"date": _iso(0), "bedtime": "01:00:00", "wake_time": "08:30:00"}
    )
    assert r.json()["duration_min"] == 450  # same-day bedtime


async def test_sleep_list_range_filter(auth_client):
    for days_ago in (0, 1, 5):
        await auth_client.post(
            "/sleep", json={"date": _iso(days_ago), "bedtime": "23:00:00", "wake_time": "07:00:00"}
        )
    r = await auth_client.get("/sleep", params={"start": _iso(1), "end": _iso(0)})
    assert r.status_code == 200
    assert {e["date"] for e in r.json()} == {_iso(0), _iso(1)}


async def test_sleep_validation(auth_client):
    base = {"date": _iso(0), "bedtime": "23:00:00", "wake_time": "07:00:00"}
    assert (await auth_client.post("/sleep", json={**base, "quality": 6})).status_code == 422
    assert (await auth_client.post("/sleep", json={**base, "source": "watch"})).status_code == 422


async def test_sleep_summary_not_enough_data(auth_client):
    await auth_client.post(
        "/sleep", json={"date": _iso(0), "bedtime": "23:00:00", "wake_time": "07:00:00"}
    )
    r = await auth_client.get("/sleep/summary")
    assert r.status_code == 200
    body = r.json()
    assert body["enough_data"] is False
    assert body["trend"] == "not_enough_data"
    assert body["nights"] == 1


async def test_sleep_summary_aggregates(auth_client):
    # Four recent nights, quality rising from 2s to 4s → "improving".
    for days_ago, quality in ((3, 2), (2, 2), (1, 4), (0, 4)):
        r = await auth_client.post(
            "/sleep",
            json={
                "date": _iso(days_ago),
                "bedtime": "23:30:00",
                "wake_time": "07:00:00",
                "quality": quality,
            },
        )
        assert r.status_code in (200, 201)

    r = await auth_client.get("/sleep/summary")
    body = r.json()
    assert body["enough_data"] is True
    assert body["nights"] == 4
    assert body["avg_duration_min"] == 450
    assert body["avg_quality"] == 3.0
    assert body["bedtime_consistency_min"] == 0  # identical bedtimes
    assert body["trend"] == "improving"


async def test_sleep_requires_auth(client):
    assert (await client.get("/sleep")).status_code == 401
    assert (await client.get("/sleep/summary")).status_code == 401


async def test_sleep_metric_in_weekly_insights(auth_client):
    r = await auth_client.get("/insights/weekly")
    by_label = {m["label"]: m for m in r.json()["metrics"]}
    assert by_label["Sleep"]["value"] == "No diary yet"

    for days_ago in (0, 1):
        await auth_client.post(
            "/sleep", json={"date": _iso(days_ago), "bedtime": "23:00:00", "wake_time": "06:30:00", "quality": 3}
        )
    r = await auth_client.get("/insights/weekly")
    by_label = {m["label"]: m for m in r.json()["metrics"]}
    assert by_label["Sleep"]["value"] == "7h 30m avg"


async def test_sleep_mood_link_appears_when_supported(auth_client):
    # Two short (5h) and two rested (8.5h) nights this week…
    for days_ago, wake in ((1, "04:00:00"), (2, "04:00:00"), (3, "07:30:00"), (4, "07:30:00")):
        await auth_client.post(
            "/sleep", json={"date": _iso(days_ago), "bedtime": "23:00:00", "wake_time": wake, "quality": 3}
        )
    # …with rougher moods on the short mornings (created_at backdated directly —
    # the API stamps server time, so this pairs moods with their wake dates).
    import uuid

    from app.core.database import SessionLocal
    from app.models.mood import MoodLog

    uid = uuid.UUID((await auth_client.get("/auth/me")).json()["id"])
    async with SessionLocal() as s:
        for days_ago, intensity in ((1, 5), (2, 4), (3, 2), (4, 2)):
            d = date.today() - timedelta(days=days_ago)
            s.add(MoodLog(user_id=uid, mood="Anxious", intensity=intensity,
                          created_at=datetime.combine(d, time(10, 0), tzinfo=timezone.utc)))
        await s.commit()

    r = await auth_client.get("/insights/weekly")
    assert "7+ hours" in r.json()["summary"]


async def test_wind_down_nudge_follows_diary(auth_client):
    # One night isn't a pattern — no wind_down nudge yet.
    await auth_client.post(
        "/sleep", json={"date": _iso(1), "bedtime": "23:15:00", "wake_time": "07:00:00"}
    )
    kinds = [n["kind"] for n in (await auth_client.get("/nudges")).json()]
    assert "wind_down" not in kinds

    # A second morning creates it; a third exercises the update-in-place path.
    for days_ago in (0, 2):
        await auth_client.post(
            "/sleep", json={"date": _iso(days_ago), "bedtime": "23:45:00", "wake_time": "07:00:00"}
        )
    nudges = (await auth_client.get("/nudges")).json()
    wind_downs = [n for n in nudges if n["kind"] == "wind_down"]
    assert len(wind_downs) == 1
    assert "23:" in wind_downs[0]["body"]


async def test_plan_leans_wind_down_after_short_sleep(auth_client, monkeypatch):
    # Force the deterministic fallback planner (dev containers may carry live keys).
    async def _no_ai(*args, **kwargs):
        return None

    monkeypatch.setattr("app.services.ai.complete_json", _no_ai)
    for days_ago in (0, 1):
        await auth_client.post(
            "/sleep", json={"date": _iso(days_ago), "bedtime": "01:30:00", "wake_time": "06:00:00", "quality": 2}
        )
    r = await auth_client.post("/plans/generate")
    plan = r.json()
    assert plan["steps"][0]["title"] == "Tonight's wind-down"
    assert "wind-down" in plan["rationale"]


async def test_chat_routes_sleep_checkin_widget(auth_client):
    r = await auth_client.post(
        "/chat/messages", json={"text": "I couldn't sleep last night, up all night tossing."}
    )
    assert r.status_code == 201
    widget = r.json()["widget"]
    assert widget and widget["widget_kind"] == "sleep_checkin"


async def test_wind_down_content_kind(admin_client):
    # The wind-down guide ships as ordinary catalogue items (kind wind_down) so
    # the admin CMS can author it and iOS serves it with a local fallback.
    r = await admin_client.post(
        "/admin/content",
        json={"title": "Slow the body first (test)", "subtitle": "Two minutes of soft breathing",
              "kind": "wind_down", "symbol": "wind"},
    )
    assert r.status_code == 201

    r = await admin_client.get("/content", params={"kind": "wind_down"})
    assert r.status_code == 200
    assert any(c["title"] == "Slow the body first (test)" for c in r.json())
