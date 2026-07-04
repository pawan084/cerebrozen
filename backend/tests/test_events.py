"""Anonymous first-party product events: POST /events + admin onboarding funnel."""
import uuid


def _anon() -> str:
    return uuid.uuid4().hex


async def test_events_accepted_and_unknown_names_dropped(client):
    r = await client.post("/events", json={
        "anon_id": _anon(),
        "source": "ios",
        "events": [
            {"name": "onboarding_step", "step": "welcome"},
            {"name": "onboarding_done"},
            {"name": "totally_made_up"},          # dropped, not an error
        ],
    })
    assert r.status_code == 202
    assert r.json()["accepted"] == 2


async def test_events_take_no_identity(client):
    # A bearer token on the request is ignored — the endpoint has no auth
    # dependency, so events can't be joined to an account even by accident.
    r = await client.post("/events",
                          headers={"Authorization": "Bearer not-even-checked"},
                          json={"anon_id": _anon(),
                                "events": [{"name": "paywall_view"}]})
    assert r.status_code == 202 and r.json()["accepted"] == 1


async def test_events_validation(client):
    # Oversized batch → 422 (cap is 20).
    r = await client.post("/events", json={
        "anon_id": _anon(),
        "events": [{"name": "paywall_view"}] * 21,
    })
    assert r.status_code == 422
    # anon_id too short and bad source both refuse.
    assert (await client.post("/events", json={"anon_id": "x", "events": []})).status_code == 422
    assert (await client.post("/events", json={
        "anon_id": _anon(), "source": "carrier-pigeon", "events": []})).status_code == 422


async def test_onboarding_funnel_counts_unique_installs(admin_client):
    a, b = _anon(), _anon()
    for anon in (a, b):
        for step in ("welcome", "age_gate"):
            await admin_client.post("/events", json={
                "anon_id": anon,
                "events": [{"name": "onboarding_step", "step": step}]})
    # One install repeats a step (must not double-count) and completes + sees the paywall.
    await admin_client.post("/events", json={
        "anon_id": a,
        "events": [{"name": "onboarding_step", "step": "welcome"},
                   {"name": "onboarding_done"},
                   {"name": "paywall_view"},
                   {"name": "paywall_cta", "step": "com.cerebrozen.premium.monthly"}]})

    r = await admin_client.get("/admin/metrics/funnel?days=7")
    assert r.status_code == 200
    body = r.json()
    steps = {s["step"]: s["installs"] for s in body["steps"]}
    # Both installs counted once each (the repeat "welcome" dedupes), so the
    # age_gate count can't trail welcome by more than other tests' traffic.
    assert steps["welcome"] >= 2 and steps["age_gate"] >= 2
    assert body["completed"] >= 1
    assert body["paywall_views"] >= 1 and body["paywall_taps"] >= 1
    # The canonical step order is preserved for the funnel chart.
    assert [s["step"] for s in body["steps"]][:3] == ["welcome", "age_gate", "disclosure"]


async def test_funnel_requires_admin(auth_client):
    assert (await auth_client.get("/admin/metrics/funnel")).status_code == 403
