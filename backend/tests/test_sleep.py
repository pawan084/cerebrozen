from datetime import date, timedelta


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
