"""Consumer billing (B2C freemium): plan resolution, mock checkout, cancel, and
the enterprise/personal boundary. All keyless — the mock provider completes a
purchase in-process so the whole freemium loop is testable with no payment keys."""

from datetime import datetime, timedelta, timezone

import pytest

from app import config
from app.models import (
    PERSONAL_ORG_SLUG_PREFIX,
    SUB_STATUS_CANCELED,
    Org,
    Subscription,
    resolve_plan,
)


async def _signup(client, email="member@example.com"):
    r = await client.post(
        "/auth/signup",
        json={"email": email, "password": "hunter2hunter2", "name": "Member"},
    )
    assert r.status_code == 201, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


async def test_a_new_personal_account_is_free(client):
    h = await _signup(client)
    r = await client.get("/billing/me", headers=h)
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["plan"] == "free"
    assert body["status"] is None  # no subscription row yet
    ent = body["entitlements"]
    assert ent["voice"] is False and ent["insights"] is False
    assert ent["coach_daily_limit"] == 5 and ent["programs_limit"] == 1


async def test_checkout_upgrades_a_personal_account_to_plus(client):
    h = await _signup(client, "buyer@example.com")
    r = await client.post("/billing/checkout", json={"plan": "plus"}, headers=h)
    assert r.status_code == 201, r.text
    body = r.json()
    assert body["plan"] == "plus" and body["status"] == "active"
    assert body["provider"] == "mock" and body["current_period_end"] is not None
    ent = body["entitlements"]
    assert ent["voice"] and ent["insights"] and ent["soundscapes"]
    assert ent["coach_daily_limit"] is None and ent["programs_limit"] is None
    # And it persists on the next read.
    assert (await client.get("/billing/me", headers=h)).json()["plan"] == "plus"


async def test_checkout_rotates_the_token_to_carry_plus(client):
    """#3: purchase returns a FRESH token asserting plan=plus, so the engine's plan gate
    and free-cap see it immediately (no ≤15-min post-purchase lockout)."""
    from app.security import decode_access_token
    h = await _signup(client, "rotate-up@example.com")
    r = await client.post("/billing/checkout", json={"plan": "plus"}, headers=h)
    assert r.status_code == 201
    tok = r.json().get("access_token")
    assert tok and decode_access_token(tok)["plan"] == "plus"


async def test_cancel_rotates_the_token_to_carry_free(client):
    """#3 reverse: cancel returns a fresh token asserting plan=free, so a former Plus user
    doesn't keep premium for the token's remaining life."""
    from app.security import decode_access_token
    h = await _signup(client, "rotate-down@example.com")
    await client.post("/billing/checkout", json={"plan": "plus"}, headers=h)
    r = await client.post("/billing/cancel", headers=h)
    assert r.status_code == 200
    assert decode_access_token(r.json()["access_token"])["plan"] == "free"


async def test_checkout_rejects_a_second_active_subscription(client):
    """Don't start a second checkout while already subscribed — with a real provider that
    double-bills the same card. But re-checkout AFTER a cancel is fine."""
    h = await _signup(client, "again@example.com")
    assert (await client.post("/billing/checkout", json={"plan": "plus"}, headers=h)).status_code == 201
    r2 = await client.post("/billing/checkout", json={"plan": "plus", "interval": "monthly"}, headers=h)
    assert r2.status_code == 409  # already active
    # cancel drops it to free (mock ends the period now), so a fresh checkout is allowed
    assert (await client.post("/billing/cancel", headers=h)).status_code == 200
    assert (await client.post("/billing/checkout", json={"plan": "plus"}, headers=h)).status_code == 201


async def test_cancel_reverts_to_free(client):
    h = await _signup(client, "quitter@example.com")
    await client.post("/billing/checkout", json={"plan": "plus"}, headers=h)
    r = await client.post("/billing/cancel", headers=h)
    assert r.status_code == 200, r.text
    assert r.json()["plan"] == "free"
    assert (await client.get("/billing/me", headers=h)).json()["plan"] == "free"


async def test_cancel_without_a_subscription_is_404(client):
    h = await _signup(client, "nothing@example.com")
    assert (await client.post("/billing/cancel", headers=h)).status_code == 404


async def test_checkout_rejects_an_unknown_plan(client):
    h = await _signup(client, "confused@example.com")
    r = await client.post("/billing/checkout", json={"plan": "galaxy"}, headers=h)
    assert r.status_code == 400


async def test_a_b2b_seat_is_enterprise_and_cannot_self_checkout(client, org_with_admin):
    h = org_with_admin["admin_headers"]
    me = await client.get("/billing/me", headers=h)
    assert me.status_code == 200 and me.json()["plan"] == "enterprise"
    # Enterprise seats get the full product for free (billed via the org).
    assert me.json()["entitlements"]["voice"] is True
    r = await client.post("/billing/checkout", json={"plan": "plus"}, headers=h)
    assert r.status_code == 409  # "billed through your organization"


async def test_billing_requires_auth(client):
    assert (await client.get("/billing/me")).status_code == 401


async def test_checkout_is_unavailable_without_the_mock_provider(client, monkeypatch):
    monkeypatch.setattr(config, "BILLING_MOCK", False)
    h = await _signup(client, "nokeys@example.com")
    r = await client.post("/billing/checkout", json={"plan": "plus"}, headers=h)
    assert r.status_code == 503


async def test_prices_endpoint_is_public_and_lists_plus(client):
    r = await client.get("/billing/prices")  # no auth header
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["plus"]["yearly"] == 5999 and body["plus"]["monthly"] == 999
    assert body["plus"]["currency"] == "usd"


async def test_jwt_plan_claim_reflects_free_then_plus(client):
    """The engine enforces entitlements from the signed `plan` claim, so it must
    track the subscription across a re-auth."""
    from app.security import decode_access_token
    r = await client.post(
        "/auth/signup",
        json={"email": "claim@example.com", "password": "hunter2hunter2", "name": "C"},
    )
    tok = r.json()["access_token"]
    assert decode_access_token(tok)["plan"] == "free"
    h = {"Authorization": f"Bearer {tok}"}
    assert (await client.post("/billing/checkout", json={"plan": "plus"}, headers=h)).status_code == 201
    # A freshly minted token (re-login) reflects the new subscription.
    r2 = await client.post(
        "/auth/login", data={"username": "claim@example.com", "password": "hunter2hunter2"}
    )
    assert decode_access_token(r2.json()["access_token"])["plan"] == "plus"


async def test_jwt_plan_claim_is_enterprise_for_a_b2b_seat(client, org_with_admin):
    from app.security import decode_access_token
    tok = org_with_admin["admin_tokens"]["access_token"]
    assert decode_access_token(tok)["plan"] == "enterprise"


async def test_provider_ref_is_globally_unique_backstopping_replay(client):
    """#4: the partial unique index makes a purchase token un-replayable even under a
    concurrent double-submit that races past the app's SELECT check — two subs can't hold
    the same non-empty provider_ref."""
    from sqlalchemy.exc import IntegrityError

    from app.db import SessionLocal

    async with SessionLocal() as s:
        s.add(Subscription(org_id="o1", provider="play", provider_ref="tok_dup"))
        s.add(Subscription(org_id="o2", provider="play", provider_ref="tok_dup"))
        with pytest.raises(IntegrityError):
            await s.commit()


async def test_free_orgs_share_the_empty_provider_ref(client):
    """The uniqueness is PARTIAL — the many free orgs (empty ref) must not collide."""
    from app.db import SessionLocal

    async with SessionLocal() as s:
        s.add(Subscription(org_id="f1", provider="mock", provider_ref=""))
        s.add(Subscription(org_id="f2", provider="mock", provider_ref=""))
        await s.commit()  # no raise → partial index exempts empty refs


def test_a_cancelled_subscription_keeps_access_until_period_end():
    """Cancel = 'won't renew', not 'cut off now': a cancelled sub still grants its
    plan while its paid period is unexpired, and drops to free once it passes."""
    org = Org(name="Solo", slug=PERSONAL_ORG_SLUG_PREFIX + "abc", seats_total=1)
    now = datetime(2026, 7, 19, tzinfo=timezone.utc)
    still_in_period = Subscription(
        org_id="o", plan="plus", status=SUB_STATUS_CANCELED,
        current_period_end=now + timedelta(days=10),
    )
    lapsed = Subscription(
        org_id="o", plan="plus", status=SUB_STATUS_CANCELED,
        current_period_end=now - timedelta(days=1),
    )
    assert resolve_plan(org, still_in_period, at=now) == "plus"
    assert resolve_plan(org, lapsed, at=now) == "free"
