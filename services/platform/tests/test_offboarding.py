"""Offboarding a leaver — the core B2B operation, and the roster was GET-only.

An org_admin could see somebody who had left and do nothing about them, while the seat
they no longer used stayed counted against the org. That is not a missing nicety: seats are
what the customer pays for, and `_seats_used` gates every invitation, so a company that
churns staff would slowly lose the ability to onboard anyone.

Two things these pin that are easy to get wrong:

  * **Deactivation is not deletion.** A leaver's coaching content is not the employer's to
    destroy. Their own erasure (`DELETE /users/me`) stays theirs.
  * **Tenancy.** An org_admin is a customer, not staff. Their reach ends at their own org,
    and a probe for another tenant's user must not answer differently from a probe for a
    user who does not exist.
"""

from datetime import datetime, timedelta, timezone

import pytest
from sqlalchemy import select

from app.db import SessionLocal
from app.models import Invitation, User
from app.security import new_opaque_token


async def _join(client, org_id: str, email: str, role: str = "user") -> dict:
    """Put a real member in an org through the invitation flow the product uses."""
    raw, token_hash = new_opaque_token()
    async with SessionLocal() as session:
        session.add(Invitation(
            org_id=org_id, email=email, role=role, token_hash=token_hash,
            expires_at=datetime.now(timezone.utc) + timedelta(days=1),
        ))
        await session.commit()
    r = await client.post(
        "/auth/accept-invitation",
        json={"token": raw, "name": "A Member", "password": "hunter2hunter2"},
    )
    assert r.status_code == 201, r.text
    return r.json()


async def _user_id(email: str) -> str:
    async with SessionLocal() as session:
        return (await session.execute(select(User).where(User.email == email))).scalar_one().id


@pytest.fixture
async def member(client, org_with_admin):
    """An org with its admin and one ordinary member who has signed in."""
    tokens = await _join(client, org_with_admin["org"]["id"], "leaver@acme.example")
    return {
        **org_with_admin,
        "member_tokens": tokens,
        "member_headers": {"Authorization": f"Bearer {tokens['access_token']}"},
        "member_id": await _user_id("leaver@acme.example"),
    }


# ── the operation ────────────────────────────────────────────────────────────


async def test_an_org_admin_can_offboard_a_leaver(client, member):
    r = await client.patch(
        f"/orgs/me/people/{member['member_id']}",
        json={"is_active": False},
        headers=member["admin_headers"],
    )
    assert r.status_code == 200, r.text
    assert r.json()["is_active"] is False


async def test_offboarding_frees_the_seat(client, member):
    """The reason this route exists. Seats are what the customer pays for and
    `_seats_used` gates invitations — a leaver who keeps a seat is a slow leak."""
    before = (await client.get("/orgs/me", headers=member["admin_headers"])).json()["seats_used"]
    r = await client.patch(
        f"/orgs/me/people/{member['member_id']}",
        json={"is_active": False},
        headers=member["admin_headers"],
    )
    assert r.json()["seats_used"] == before - 1
    after = (await client.get("/orgs/me", headers=member["admin_headers"])).json()["seats_used"]
    assert after == before - 1, "the org's own seat count disagrees with the patch response"


async def test_offboarding_actually_cuts_access(client, member):
    """The whole point: a leaver must not still be able to use the product. Access, refresh
    and login all die — anything less is offboarding theatre."""
    await client.patch(
        f"/orgs/me/people/{member['member_id']}",
        json={"is_active": False},
        headers=member["admin_headers"],
    )
    assert (await client.get("/users/me", headers=member["member_headers"])).status_code == 401
    r = await client.post("/auth/refresh", json={"refresh_token": member["member_tokens"]["refresh_token"]})
    assert r.status_code == 401, "a revoked leaver refreshed their way back in"
    r = await client.post("/auth/login", data={"username": "leaver@acme.example", "password": "hunter2hunter2"})
    assert r.status_code == 401


async def test_reactivating_brings_them_back(client, member):
    """People come back from leave. Offboarding is reversible; deletion is not, which is
    exactly why they are different buttons."""
    await client.patch(f"/orgs/me/people/{member['member_id']}", json={"is_active": False},
                       headers=member["admin_headers"])
    r = await client.patch(f"/orgs/me/people/{member['member_id']}", json={"is_active": True},
                           headers=member["admin_headers"])
    assert r.status_code == 200 and r.json()["is_active"] is True
    r = await client.post("/auth/login", data={"username": "leaver@acme.example", "password": "hunter2hunter2"})
    assert r.status_code == 200, "a reactivated member still cannot sign in"


async def test_the_roster_shows_the_new_status(client, member):
    await client.patch(f"/orgs/me/people/{member['member_id']}", json={"is_active": False},
                       headers=member["admin_headers"])
    people = (await client.get("/orgs/me/people", headers=member["admin_headers"])).json()
    row = next(p for p in people if p["id"] == member["member_id"])
    assert row["is_active"] is False


# ── deactivation is not deletion ─────────────────────────────────────────────


async def test_offboarding_does_not_destroy_the_person(client, member):
    """A leaver's record survives with their PII intact — an employer may end someone's
    access, not erase them. Erasure is the person's own (`DELETE /users/me`)."""
    await client.patch(f"/orgs/me/people/{member['member_id']}", json={"is_active": False},
                       headers=member["admin_headers"])
    async with SessionLocal() as session:
        row = (await session.execute(select(User).where(User.id == member["member_id"]))).scalar_one()
    assert row.email == "leaver@acme.example", "offboarding scrubbed the account like a deletion"
    assert row.name


# ── tenancy: an org_admin is a customer, not staff ───────────────────────────


async def test_an_org_admin_cannot_touch_another_tenants_employee(client, member, internal):
    headers, _ = internal
    r = await client.post("/orgs", json={"name": "Other Co", "slug": "other", "seats_total": 3},
                          headers=headers)
    other_org = r.json()["id"]
    await _join(client, other_org, "them@other.example")
    victim = await _user_id("them@other.example")

    r = await client.patch(f"/orgs/me/people/{victim}", json={"is_active": False},
                           headers=member["admin_headers"])
    assert r.status_code == 404, "one tenant's admin reached another tenant's employee"

    async with SessionLocal() as session:
        row = (await session.execute(select(User).where(User.id == victim))).scalar_one()
    assert row.is_active is True, "the other tenant's employee was actually deactivated"


async def test_an_unknown_person_is_a_404_not_a_403(client, member):
    """Same answer as another tenant's user, on purpose: a different status would let an
    org_admin enumerate which user ids exist elsewhere."""
    r = await client.patch("/orgs/me/people/does-not-exist", json={"is_active": False},
                           headers=member["admin_headers"])
    assert r.status_code == 404


async def test_a_member_cannot_offboard_anyone(client, member):
    r = await client.patch(f"/orgs/me/people/{member['member_id']}", json={"is_active": False},
                           headers=member["member_headers"])
    assert r.status_code == 403


async def test_an_unauthenticated_caller_cannot_offboard(client, member):
    r = await client.patch(f"/orgs/me/people/{member['member_id']}", json={"is_active": False})
    assert r.status_code == 401


# ── the self-lockout ─────────────────────────────────────────────────────────


async def test_an_admin_cannot_deactivate_themselves(client, member):
    """With one org_admin this locks the whole tenant out of its own console, with no
    self-service way back."""
    me = await _user_id("hr@acme.example")
    r = await client.patch(f"/orgs/me/people/{me}", json={"is_active": False},
                           headers=member["admin_headers"])
    assert r.status_code == 400
    assert "your own" in r.json()["detail"]


async def test_an_admin_may_still_reactivate_themselves_in_principle(client, member):
    """The guard is only about locking yourself OUT — a no-op reactivation must not 400,
    or the rule would be about the subject rather than the risk."""
    me = await _user_id("hr@acme.example")
    r = await client.patch(f"/orgs/me/people/{me}", json={"is_active": True},
                           headers=member["admin_headers"])
    assert r.status_code == 200


# ── the shape of the patch ───────────────────────────────────────────────────


async def test_the_patch_accepts_nothing_but_status(client, member):
    """A roster row is identity + status, and status is the only thing an employer gets to
    change here — not someone's name, not their role."""
    r = await client.patch(
        f"/orgs/me/people/{member['member_id']}",
        json={"is_active": False, "name": "Renamed By HR", "role": "org_admin"},
        headers=member["admin_headers"],
    )
    assert r.status_code == 200
    assert r.json()["name"] == "A Member", "HR renamed an employee through the status patch"
    assert r.json()["role"] == "user", "HR changed an employee's role through the status patch"


async def test_is_active_is_required(client, member):
    r = await client.patch(f"/orgs/me/people/{member['member_id']}", json={},
                           headers=member["admin_headers"])
    assert r.status_code == 422


# ── tombstones are not people ────────────────────────────────────────────────
#
# A deletion scrubs the row in place and leaves a tombstone so foreign keys stay valid. It
# is not a person, and it was showing up in the roster with a "reactivate" button next to
# it — which would have set is_active=True and burned a seat forever on an account with no
# usable password. Found by clicking through the roster after an erasure.


@pytest.fixture
async def deleted_member(client, member):
    """A member who has erased their own account."""
    await client.delete("/users/me?confirm=true", headers=member["member_headers"])
    return member


async def test_a_deleted_account_disappears_from_the_roster(client, deleted_member):
    people = (await client.get("/orgs/me/people", headers=deleted_member["admin_headers"])).json()
    assert deleted_member["member_id"] not in [p["id"] for p in people]
    assert not any(p["email"].endswith("@deleted.invalid") for p in people), "a tombstone is listed as a person"


async def test_a_tombstone_cannot_be_reactivated(client, deleted_member):
    """The bug this guards: reactivating a scrubbed row consumes a seat that nobody can
    ever use, because delete_me set the password to a sentinel nothing verifies against."""
    r = await client.patch(
        f"/orgs/me/people/{deleted_member['member_id']}",
        json={"is_active": True},
        headers=deleted_member["admin_headers"],
    )
    assert r.status_code == 404


async def test_a_deletion_frees_the_seat_and_it_stays_freed(client, deleted_member):
    org = (await client.get("/orgs/me", headers=deleted_member["admin_headers"])).json()
    before = org["seats_used"]
    # Even if someone tries, the seat must not come back.
    await client.patch(f"/orgs/me/people/{deleted_member['member_id']}", json={"is_active": True},
                       headers=deleted_member["admin_headers"])
    after = (await client.get("/orgs/me", headers=deleted_member["admin_headers"])).json()["seats_used"]
    assert after == before


async def test_the_tombstone_marker_is_written_and_read_from_one_place():
    """Two spellings of "@deleted.invalid" would silently un-hide every tombstone."""
    from app.models import DELETED_EMAIL_DOMAIN, User, is_tombstone

    assert is_tombstone(User(email=f"deleted-abc{DELETED_EMAIL_DOMAIN}"))
    assert not is_tombstone(User(email="real@acme.example"))
    assert not is_tombstone(User(email=""))
