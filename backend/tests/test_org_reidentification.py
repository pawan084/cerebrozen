"""Can an employer re-identify one person from the aggregates? (WC-73)

The classic attack on threshold-suppressed reporting is DIFFERENCING: read a
cohort, change it by one person, read it again, and the delta is that person.
A minimum cohort size does nothing against it, because both reads are above the
threshold.

Chasing it here produced a better answer than "the threshold holds", and these
tests exist to pin that answer rather than the threshold:

    **There is nothing behavioural in the aggregate to re-identify.**

Every number an organisation can read is a count of MEMBERSHIP ROWS whose status
the organisation itself set. `add_member` and the CSV import write
`STATUS_ACTIVE` directly; `end_membership` writes `STATUS_ENDED`. Nothing
anywhere transitions a membership because of something the member did — so the
"activated" figure is the employer's own bookkeeping reflected back, and
differencing it reveals only what the differencer already did.

That is a structural property, not a policy, and structural properties rot
silently. The last test is the one that matters: it proves the reporting surface
produces identical output for a member who uses the product constantly and one
who has never opened it. If somebody later adds a usage aggregate, that test
fails and this file explains why it mattered.
"""

from __future__ import annotations

import uuid

import pytest
from sqlalchemy import select

from app.core.database import SessionLocal, utcnow
from app.core.security import hash_password
from app.models.chat import ChatMessage
from app.models.journal import JournalEntry
from app.models.mood import MoodLog
from app.models.organization import (
    MIN_REPORTING_THRESHOLD,
    ROLE_BENEFITS_OWNER,
    STATUS_ACTIVE,
    EligibilityGroup,
    Organization,
    OrgAdmin,
    OrgMembership,
)
from app.models.user import User
from app.services import organizations as org_service


async def _org_with_members(db, *, name: str, size: int, group_name: str = "All"):
    org = Organization(name=name, region="IN")
    db.add(org)
    await db.flush()
    group = EligibilityGroup(org_id=org.id, name=group_name)
    db.add(group)
    await db.flush()
    members = []
    for i in range(size):
        u = User(email=f"{name[:4]}{i}-{uuid.uuid4().hex[:8]}@test.app", hashed_password="x")
        db.add(u)
        await db.flush()
        db.add(
            OrgMembership(
                org_id=org.id, user_id=u.id, group_id=group.id, status=STATUS_ACTIVE
            )
        )
        members.append(u)
    await db.flush()
    return org, group, members


class TestDifferencing:
    @pytest.mark.asyncio
    async def test_ending_one_seat_reveals_only_what_the_admin_just_did(self):
        """The attack, run end to end.

        An administrator reads a reportable group, ends one seat, and reads it
        again. The `activated` count falls by exactly one — and that one is the
        seat they ended, which they already knew. No fact about the member has
        crossed the boundary.
        """
        async with SessionLocal() as db:
            org, group, members = await _org_with_members(
                db, name="Differencing Co", size=MIN_REPORTING_THRESHOLD + 5
            )
            await db.commit()

            before = await org_service.group_totals(db, org, group)
            assert before.suppressed is False

            victim = members[0]
            row = await db.scalar(
                select(OrgMembership).where(OrgMembership.user_id == victim.id)
            )
            row.status = "ended"
            await db.commit()

            after = await org_service.group_totals(db, org, group)

        assert after.activated == before.activated - 1
        # Eligible does not move: the seat still exists, it is just no longer
        # sponsored. So the delta is exactly the admin's own action.
        assert after.eligible == before.eligible

    @pytest.mark.asyncio
    async def test_a_member_cannot_be_moved_between_groups(self, client):
        """The reassignment vector is not expressible.

        Slicing needs the same person counted in two different cohorts. There is
        no route that changes a membership's group, and `add_member` 409s on
        anyone who already has a seat — so an admin cannot build the two
        overlapping cohorts the attack requires.
        """
        email = f"reassign-{uuid.uuid4().hex[:10]}@test.app"
        r = await client.post(
            "/auth/signup", json={"email": email, "password": "password123", "name": "T"}
        )
        assert r.status_code == 201
        client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"

        member_email = f"member-{uuid.uuid4().hex[:10]}@test.app"
        r2 = await client.post(
            "/auth/signup",
            json={"email": member_email, "password": "password123", "name": "M"},
        )
        assert r2.status_code == 201

        async with SessionLocal() as db:
            org = Organization(name=f"Slice Co {uuid.uuid4().hex[:6]}", region="IN")
            db.add(org)
            await db.flush()
            admin_user = await db.scalar(select(User).where(User.email == email))
            db.add(OrgAdmin(org_id=org.id, user_id=admin_user.id, role=ROLE_BENEFITS_OWNER))
            a = EligibilityGroup(org_id=org.id, name="A")
            b = EligibilityGroup(org_id=org.id, name="B")
            db.add_all([a, b])
            await db.flush()
            a_id, b_id = str(a.id), str(b.id)
            await db.commit()

        first = await client.post(
            "/org/members", json={"email": member_email, "group_id": a_id}
        )
        assert first.status_code == 201

        # The same person into the other cohort — which is what slicing needs.
        second = await client.post(
            "/org/members", json={"email": member_email, "group_id": b_id}
        )
        assert second.status_code == 409, "a person must not be countable in two cohorts"


class TestThereIsNothingBehaviouralToReIdentify:
    """The structural guarantee, and the reason the attack has no target.

    Not a restatement of the threshold: this asserts that the reporting surface
    is *blind* to what members do, so there is no behavioural fact for any
    amount of cohort arithmetic to isolate.
    """

    @pytest.mark.asyncio
    async def test_reports_are_identical_whether_or_not_members_use_the_product(self):
        size = MIN_REPORTING_THRESHOLD + 2
        async with SessionLocal() as db:
            busy_org, busy_group, busy_members = await _org_with_members(
                db, name="Busy Co", size=size
            )
            quiet_org, quiet_group, _ = await _org_with_members(
                db, name="Quiet Co", size=size
            )
            # Every member of Busy Co uses the product heavily; nobody in Quiet
            # Co has ever opened it.
            for member in busy_members:
                db.add(MoodLog(user_id=member.id, mood="Good", intensity=3))
                db.add(JournalEntry(user_id=member.id, title="t", body="private words"))
                db.add(ChatMessage(user_id=member.id, role="user", text="private words"))
            await db.commit()

            busy = await org_service.group_totals(db, busy_org, busy_group)
            quiet = await org_service.group_totals(db, quiet_org, quiet_group)

        # Identical in every reported field. If a usage aggregate is ever added
        # to this surface, THIS is the test that fails — and the docstring above
        # is why that matters.
        assert busy.eligible == quiet.eligible
        assert busy.activated == quiet.activated
        assert busy.active == quiet.active
        assert busy.suppressed == quiet.suppressed

    @pytest.mark.asyncio
    async def test_the_summary_says_individual_reporting_does_not_exist(self):
        async with SessionLocal() as db:
            org, _group, _members = await _org_with_members(
                db, name="Stated Co", size=MIN_REPORTING_THRESHOLD
            )
            await db.commit()
            summary = await org_service.org_summary(db, org)

        # Stated in the payload rather than left to be inferred from an absence,
        # so a client reads the boundary instead of guessing at it.
        assert summary["individual_reporting_available"] is False
        assert summary["small_cell_suppression"] is True
        assert summary["reporting_threshold"] >= MIN_REPORTING_THRESHOLD
