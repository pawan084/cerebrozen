"""Cross-tenant reads and writes, attempted directly (WC-74).

ARCHITECTURE claims a cross-tenant read "is not a check somebody has to
remember but a request that cannot be expressed", because no `/org` route takes
an `org_id`. That is true of the *organisation*, and it is the right shape — but
four surfaces do accept an id for something INSIDE an organisation:

    DELETE /org/members/{membership_id}
    POST   /org/members          {group_id}
    POST   /org/members/import   {group_id}
    POST   /org/programmes       {group_id}

Each of those is a place where an administrator of one organisation can name a
row belonging to another, and each therefore needs a check that somebody DID
have to remember. Broken tenant isolation is the most common source of B2B
breaches (WC-75), and it fails silently: the attacker gets data, and the victim
gets no signal at all.

So this file is written as the attack rather than as the feature. Every route
above is called by an administrator of a different organisation, with a real id
belonging to the victim. A 404 is the expected answer everywhere — not 403,
which would confirm the row exists and turn a blocked read into an oracle for
enumerating another tenant's groups.
"""

from __future__ import annotations

import uuid

import pytest
from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.organization import (
    ROLE_BENEFITS_OWNER,
    STATUS_ACTIVE,
    EligibilityGroup,
    Organization,
    OrgAdmin,
    OrgMembership,
)
from app.models.user import User


async def _signup(client, prefix: str) -> tuple[str, str]:
    email = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": email, "password": "password123", "name": "T"}
    )
    assert r.status_code == 201, r.text
    return email, r.json()["access_token"]


async def _org_owned_by(db, email: str, *, name: str) -> Organization:
    org = Organization(name=f"{name} {uuid.uuid4().hex[:6]}", region="IN")
    db.add(org)
    await db.flush()
    user = await db.scalar(select(User).where(User.email == email))
    db.add(OrgAdmin(org_id=org.id, user_id=user.id, role=ROLE_BENEFITS_OWNER))
    await db.flush()
    return org


@pytest.fixture
async def two_orgs(client):
    """An attacker with their own organisation, and a victim with a populated one.

    The attacker is a legitimate org admin — that is the whole point. The
    interesting failure is not an outsider getting in, it is a customer reading
    another customer.
    """
    attacker_email, attacker_token = await _signup(client, "attacker")
    victim_email, _ = await _signup(client, "victim")
    member_email, _ = await _signup(client, "victim-member")

    async with SessionLocal() as db:
        await _org_owned_by(db, attacker_email, name="Attacker Co")
        victim_org = await _org_owned_by(db, victim_email, name="Victim Co")

        victim_group = EligibilityGroup(org_id=victim_org.id, name="Victim group")
        db.add(victim_group)
        await db.flush()

        member = await db.scalar(select(User).where(User.email == member_email))
        victim_membership = OrgMembership(
            org_id=victim_org.id,
            user_id=member.id,
            group_id=victim_group.id,
            status=STATUS_ACTIVE,
            external_ref="VICTIM-PAYROLL-001",
        )
        db.add(victim_membership)
        await db.flush()
        ids = {
            "group": str(victim_group.id),
            "membership": str(victim_membership.id),
            "org_name": victim_org.name,
            "attacker_token": attacker_token,
        }
        await db.commit()

    client.headers["Authorization"] = f"Bearer {attacker_token}"
    return ids


class TestNamingAnotherTenantsRow:
    @pytest.mark.asyncio
    async def test_ending_another_organisations_seat_is_refused(self, client, two_orgs):
        # The highest-impact write available: cancelling somebody else's
        # customer's sponsorship.
        r = await client.delete(f"/org/members/{two_orgs['membership']}")
        assert r.status_code == 404, r.text

    @pytest.mark.asyncio
    async def test_adding_a_member_into_another_organisations_group_is_refused(
        self, client, two_orgs
    ):
        # Would place the attacker's own member inside the victim's cohort —
        # and then the victim's group totals would count a stranger.
        new_email, _ = await _signup(client, "attacker-member")
        # _signup signed the client in as the new user. Put the attacker back,
        # or the request goes out unauthenticated and a 401 hides the answer.
        client.headers["Authorization"] = "Bearer " + two_orgs["attacker_token"]
        r = await client.post(
            "/org/members", json={"email": new_email, "group_id": two_orgs["group"]}
        )
        assert r.status_code == 404, r.text

    @pytest.mark.asyncio
    async def test_importing_into_another_organisations_group_is_refused(
        self, client, two_orgs
    ):
        r = await client.post(
            "/org/members/import",
            json={"csv": "email\nsomebody@test.app\n", "group_id": two_orgs["group"]},
        )
        assert r.status_code == 404, r.text

    @pytest.mark.asyncio
    async def test_sponsoring_a_programme_for_another_organisations_group_is_refused(
        self, client, two_orgs
    ):
        r = await client.post(
            "/org/programmes",
            json={"programme_slug": "sleep-reset", "group_id": two_orgs["group"]},
        )
        assert r.status_code == 404, r.text


class TestTheRefusalDoesNotBecomeAnOracle:
    @pytest.mark.asyncio
    async def test_a_real_foreign_id_is_indistinguishable_from_an_invented_one(
        self, client, two_orgs
    ):
        """404 for both, or the error code enumerates another tenant's rows.

        A 403 for a row that exists and a 404 for one that does not turns every
        refusal into a yes/no lookup: an attacker walks uuids until the status
        changes and learns the victim's group ids without ever reading one.
        """
        real = await client.delete(f"/org/members/{two_orgs['membership']}")
        invented = await client.delete(f"/org/members/{uuid.uuid4()}")
        assert real.status_code == invented.status_code == 404
        assert real.json() == invented.json(), "the bodies must not differ either"

    @pytest.mark.asyncio
    async def test_nothing_of_the_victim_appears_in_the_attackers_own_reads(
        self, client, two_orgs
    ):
        """The ordinary reads, checked for leakage rather than for status.

        Each of these is scoped by the signed-in admin's organisation, so the
        assertion is about CONTENT: the victim's payroll reference and group
        name must not appear anywhere in what the attacker can see.
        """
        for path in ("/org", "/org/summary", "/org/groups", "/org/groups/totals",
                     "/org/members", "/org/programmes", "/org/admins", "/org/audit"):
            r = await client.get(path)
            assert r.status_code == 200, f"{path}: {r.text}"
            body = r.text
            assert "VICTIM-PAYROLL-001" not in body, f"{path} leaked a foreign external_ref"
            assert "Victim group" not in body, f"{path} leaked a foreign group name"
            assert two_orgs["org_name"] not in body, f"{path} leaked a foreign org name"
