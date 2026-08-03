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
    # Every shipping client source is accepted — android was 422ing before
    # 2026-07-07 (the pattern predated the Android client).
    for source in ("ios", "web", "app", "android"):
        r = await client.post("/events", json={
            "anon_id": _anon(), "source": source,
            "events": [{"name": "paywall_view"}],
        })
        assert r.status_code == 202, source


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


def test_onboarding_step_vocabulary_is_a_cross_stack_contract():
    """iOS, Android and the web client all send these exact strings, and the
    admin funnel joins on them. A rename drops a bar silently instead of
    erroring, so the list is pinned here as well as in each client.

    Mirrors `apps/app/lib/analytics.ts::ONBOARDING_STEPS`.
    """
    from app.services.metrics import ONBOARDING_STEPS

    assert ONBOARDING_STEPS == [
        "welcome", "age_gate", "disclosure", "language", "state_check",
        "first_reset", "first_plan", "signup", "consent", "notifications",
    ]


async def test_web_source_is_accepted_and_kept(client):
    """`web`/`app` were valid in the schema but no client ever sent them, so the
    browser funnel was invisible. Pin that the app source round-trips."""
    r = await client.post("/events", json={
        "anon_id": "a" * 16,
        "source": "app",
        "events": [{"name": "onboarding_step", "step": "consent"},
                   {"name": "paywall_view"}],
    })
    assert r.status_code == 202
    assert r.json()["accepted"] == 2


async def test_events_ignore_a_bearer_token(client):
    """The endpoint takes no auth on purpose — events must not be joinable to a
    user even by accident. Sending a token must not change the outcome."""
    signup = await client.post("/auth/signup", json={
        "email": "ev-auth@test.app", "password": "password123", "name": "E"})
    client.headers["Authorization"] = f"Bearer {signup.json()['access_token']}"

    r = await client.post("/events", json={
        "anon_id": "b" * 16, "source": "app",
        "events": [{"name": "onboarding_done"}],
    })
    assert r.status_code == 202 and r.json()["accepted"] == 1
