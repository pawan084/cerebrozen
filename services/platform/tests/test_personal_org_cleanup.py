"""Deleting a self-serve (B2C) personal account must also retire its org-of-one and
drop any subscription — no orphan tenancy or billing left behind. A B2B org, by
contrast, outlives any single member and must never be deactivated by a deletion."""

from sqlalchemy import select

from app.db import SessionLocal
from app.models import Org, Subscription
from app.security import decode_access_token

_PW = "hunter2hunter2"


async def test_deleting_a_personal_account_retires_its_solo_org_and_subscription(client):
    r = await client.post(
        "/auth/signup", json={"email": "bye@example.com", "password": _PW, "name": "Bye"}
    )
    tok = r.json()["access_token"]
    h = {"Authorization": f"Bearer {tok}"}
    org_id = decode_access_token(tok)["org_id"]
    # Give it a subscription so we can prove that's cleaned up too.
    assert (await client.post("/billing/checkout", json={"plan": "plus"}, headers=h)).status_code == 201

    d = await client.delete("/users/me", params={"confirm": True}, headers=h)
    assert d.status_code == 204, d.text

    async with SessionLocal() as s:
        org = (await s.execute(select(Org).where(Org.id == org_id))).scalar_one()
        assert org.is_active is False, "the personal org should be retired with its owner"
        sub = (
            await s.execute(select(Subscription).where(Subscription.org_id == org_id))
        ).scalar_one_or_none()
        assert sub is None, "the subscription should be dropped, not orphaned"


async def test_deleting_a_b2b_member_never_deactivates_the_org(client, org_with_admin):
    headers = org_with_admin["admin_headers"]
    org_id = org_with_admin["org"]["id"]
    d = await client.delete("/users/me", params={"confirm": True}, headers=headers)
    assert d.status_code == 204, d.text
    async with SessionLocal() as s:
        org = (await s.execute(select(Org).where(Org.id == org_id))).scalar_one()
        assert org.is_active is True, "a B2B org must outlive any single member"
