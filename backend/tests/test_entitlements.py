"""Sponsorship has to actually grant something — and only what it should.

`services.organizations.is_sponsored` was correct and unused for a day: an
organisation could pay for a seat and the member got a database row and nothing
else. These tests pin the wiring in `services/entitlements`, and — more
importantly — the four things it must NOT do:

* never write the granted tier back onto the user (a lapsed contract would
  leave premium behind forever, paid for by nobody),
* never report a purchase as sponsored, because the two differ in whether the
  member can cancel it,
* never keep granting after the sponsorship ends,
* never tell CereBro staff a customer's sponsorship is a purchase.
"""
import uuid
from datetime import timedelta

from sqlalchemy import select, update

from app.core.database import SessionLocal, utcnow
from app.models.organization import (
    STATUS_ACTIVE,
    STATUS_ENDED,
    Organization,
    OrgMembership,
)
from app.models.user import User
from app.services import entitlements


async def _signup(client, prefix: str) -> str:
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": addr, "password": "password123", "name": "E"}
    )
    assert r.status_code == 201, r.text
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return addr


async def _sponsor(email: str, *, grants_premium: bool = True, **membership) -> uuid.UUID:
    """Put this address on a paid seat. Returns the membership id.

    Built directly rather than through `/org/members` so the dates and flags a
    test cares about are visible in the test itself.
    """
    async with SessionLocal() as db:
        org = Organization(name=f"Sponsor {uuid.uuid4().hex[:6]}", seats_licensed=50,
                           grants_premium=grants_premium)
        db.add(org)
        await db.flush()
        user_id = await db.scalar(select(User.id).where(User.email == email))
        m = OrgMembership(org_id=org.id, user_id=user_id,
                          status=membership.pop("status", STATUS_ACTIVE), **membership)
        db.add(m)
        await db.commit()
        return m.id


async def _stored_tier(email: str) -> str:
    async with SessionLocal() as db:
        return await db.scalar(select(User.subscription_tier).where(User.email == email))


# ---------------------------------------------------------------- the grant


async def test_sponsorship_lifts_the_free_chat_cap(client, monkeypatch):
    """The gate that made sponsorship worthless: `usage.enforce_quota` read the
    stored column, so a sponsored member hit the free cap their employer had
    paid to remove."""
    from app.core.config import settings

    monkeypatch.setattr(settings, "free_daily_messages", 1)
    email = await _signup(client, "sponsored")

    assert (await client.post("/chat/messages", json={"text": "one"})).status_code == 201
    capped = await client.post("/chat/messages", json={"text": "two"})
    assert capped.status_code == 429

    await _sponsor(email)

    for _ in range(3):
        assert (await client.post("/chat/messages", json={"text": "more"})).status_code == 201


async def test_the_stored_tier_is_never_written_by_sponsorship(client, monkeypatch):
    """The whole reason entitlement is computed instead of stored.

    If a grant were persisted, the row would outlive the contract that paid for
    it and nothing would ever take it back.
    """
    from app.core.config import settings

    monkeypatch.setattr(settings, "free_daily_messages", 1)
    email = await _signup(client, "notwritten")
    await _sponsor(email)

    for _ in range(2):
        assert (await client.post("/chat/messages", json={"text": "hi"})).status_code == 201
    assert (await client.get("/users/me")).json()["subscription_tier"] == "premium"

    assert await _stored_tier(email) == "free"


async def test_me_reports_the_tier_the_server_will_enforce(client):
    """A client shown "free" while the backend allows premium would render a
    paywall to someone entitled to walk past it."""
    email = await _signup(client, "reported")
    assert (await client.get("/users/me")).json()["subscription_tier"] == "free"

    await _sponsor(email)

    for path in ("/users/me", "/auth/me"):
        body = (await client.get(path)).json()
        assert body["subscription_tier"] == "premium", path
        # ...and says where it came from, so no client offers a cancel link for
        # a subscription the member cannot cancel.
        assert body["sponsored"] is True, path


async def test_a_purchase_is_never_reported_as_sponsored(client):
    """Someone can be both. The distinction that matters is who may cancel."""
    email = await _signup(client, "bothpaid")
    await _sponsor(email)
    async with SessionLocal() as db:
        await db.execute(
            update(User).where(User.email == email).values(subscription_tier="premium_human")
        )
        await db.commit()

    body = (await client.get("/users/me")).json()
    assert body["subscription_tier"] == "premium_human"  # their own, not downgraded
    assert body["sponsored"] is False


# ---------------------------------------------------------------- the limits


async def test_the_cap_returns_when_the_sponsorship_ends(client, monkeypatch):
    from app.core.config import settings

    monkeypatch.setattr(settings, "free_daily_messages", 1)
    email = await _signup(client, "lapsing")
    membership_id = await _sponsor(email)
    assert (await client.post("/chat/messages", json={"text": "one"})).status_code == 201
    assert (await client.post("/chat/messages", json={"text": "two"})).status_code == 201

    async with SessionLocal() as db:
        await db.execute(
            update(OrgMembership).where(OrgMembership.id == membership_id)
            .values(status=STATUS_ENDED)
        )
        await db.commit()

    assert (await client.post("/chat/messages", json={"text": "three"})).status_code == 429
    body = (await client.get("/users/me")).json()
    assert body["subscription_tier"] == "free" and body["sponsored"] is False


async def test_an_access_window_in_the_future_grants_nothing_yet(client):
    email = await _signup(client, "future")
    today = utcnow().date()
    await _sponsor(email, access_start=today + timedelta(days=7))

    assert (await client.get("/users/me")).json()["subscription_tier"] == "free"


async def test_an_organisation_that_does_not_grant_premium_grants_nothing(client):
    """`grants_premium` is a real switch, not decoration: an organisation can
    sponsor eligibility (crisis resources, programmes) without buying premium."""
    email = await _signup(client, "nopremium")
    await _sponsor(email, grants_premium=False)

    body = (await client.get("/users/me")).json()
    assert body["subscription_tier"] == "free" and body["sponsored"] is False


async def test_an_invited_seat_is_not_an_active_one(client):
    email = await _signup(client, "invited")
    await _sponsor(email, status="invited")

    assert (await client.get("/users/me")).json()["subscription_tier"] == "free"


# ---------------------------------------------------------------- boundaries


async def test_the_staff_listing_shows_what_the_account_bought(admin_client):
    """`/admin/users` deliberately reports the stored column. Support answering
    a billing question needs the purchase, not the employer's grant — and
    resolving it there would be a query per row."""
    # `admin_client` IS `client` with a staff token, so signing the member up
    # takes the header over; put the staff token back before reading the list.
    staff = admin_client.headers["Authorization"]
    member = await _signup(admin_client, "seen-by-staff")
    await _sponsor(member)
    admin_client.headers["Authorization"] = staff

    rows = (await admin_client.get(f"/admin/users?q={member}")).json()
    row = next(r for r in rows if r["email"] == member)
    assert row["subscription_tier"] == "free"
    assert row["sponsored"] is False


async def test_premium_narration_follows_the_resolved_tier():
    """The media gate takes a tier rather than a user for exactly this reason:
    it cannot read the stored column and miss a sponsored member."""
    from app.services import media

    class _Item:
        premium = True

    assert media.is_entitled(_Item(), "free") is False
    assert media.is_entitled(_Item(), entitlements.SPONSORED_TIER) is True

    class _Free:
        premium = False

    assert media.is_entitled(_Free(), "free") is True


async def test_an_anonymous_caller_resolves_to_free():
    """The catalogue serves signed-out browsers, and must not query for them."""
    async with SessionLocal() as db:
        ent = await entitlements.resolve(db, None)
    assert ent.tier == "free" and ent.sponsored is False and ent.is_paid is False
