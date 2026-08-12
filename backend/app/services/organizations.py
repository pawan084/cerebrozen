"""Organisation reporting — totals, thresholds and suppression.

Every number an organisation can see comes from here, and every one of them is
a COUNT over `org_memberships`. There is no function in this module that takes
a user id and returns something about that person, because there is no such
question an employer is allowed to ask.

Two rules, both enforced here rather than in the route layer, so a new endpoint
cannot forget them:

1. **Threshold.** A group smaller than the organisation's `reporting_threshold`
   reports `None`, not a rounded number and not a range. `Suppressed` is a
   distinguishable value so a reader knows something is missing rather than
   assuming the group had no activity — the portal's Outcomes screen says
   exactly this to administrators.
2. **Floor.** The threshold itself cannot be set below
   `MIN_REPORTING_THRESHOLD`. An organisation may be *more* cautious than the
   default and never less.
"""
from __future__ import annotations

import uuid
from dataclasses import dataclass

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import utcnow
from app.models.organization import (
    MIN_REPORTING_THRESHOLD,
    STATUS_ACTIVE,
    STATUS_ENDED,
    STATUS_INVITED,
    EligibilityGroup,
    Organization,
    OrgAdmin,
    OrgMembership,
)


def clamp_threshold(value: int | None) -> int:
    """The threshold an organisation actually gets.

    Requests below the floor are raised to it rather than rejected: an
    administrator who types 5 wants a smaller number, and the honest answer is
    "the smallest we allow is 20", not a 422 that loses their other edits.
    """
    if value is None:
        return MIN_REPORTING_THRESHOLD
    return max(int(value), MIN_REPORTING_THRESHOLD)


@dataclass(frozen=True)
class GroupTotals:
    """Counts for one eligibility group, already suppressed where required."""

    group_id: uuid.UUID | None
    name: str
    #: Eligible is never suppressed: it is the organisation's own eligibility
    #: list, which it supplied, so reporting it back reveals nothing new.
    eligible: int
    #: Activated/active describe member BEHAVIOUR, so both suppress.
    activated: int | None
    active: int | None
    suppressed: bool
    threshold: int


async def _count(db: AsyncSession, org_id: uuid.UUID, *, group_id: uuid.UUID | None, status: str | None) -> int:
    stmt = select(func.count()).select_from(OrgMembership).where(OrgMembership.org_id == org_id)
    if group_id is not None:
        stmt = stmt.where(OrgMembership.group_id == group_id)
    if status is not None:
        stmt = stmt.where(OrgMembership.status == status)
    return int(await db.scalar(stmt) or 0)


async def group_totals(
    db: AsyncSession, org: Organization, group: EligibilityGroup | None = None
) -> GroupTotals:
    """Totals for one group, or for the whole organisation when `group` is None."""
    threshold = clamp_threshold(org.reporting_threshold)
    gid = group.id if group is not None else None

    eligible = await _count(db, org.id, group_id=gid, status=None)
    activated = await _count(db, org.id, group_id=gid, status=STATUS_ACTIVE)

    # The suppression test is the size of the population the number describes,
    # not the size of the number. A group of 4 where all 4 activated must not
    # report "4" — that identifies all four.
    suppressed = eligible < threshold
    return GroupTotals(
        group_id=gid,
        name=group.name if group is not None else org.name,
        eligible=eligible,
        activated=None if suppressed else activated,
        active=None if suppressed else activated,
        suppressed=suppressed,
        threshold=threshold,
    )


async def all_group_totals(db: AsyncSession, org: Organization) -> list[GroupTotals]:
    groups = list(
        (
            await db.scalars(
                select(EligibilityGroup)
                .where(EligibilityGroup.org_id == org.id)
                .order_by(EligibilityGroup.name)
            )
        ).all()
    )
    return [await group_totals(db, org, g) for g in groups]


async def org_summary(db: AsyncSession, org: Organization) -> dict:
    """The dashboard numbers (portal DASH-01), all organisation-wide.

    Organisation-wide totals are not suppressed on their own account: an
    organisation knows how many people it employs. They are still counts, and
    there is no path from any of them to a person.
    """
    eligible = await _count(db, org.id, group_id=None, status=None)
    activated = await _count(db, org.id, group_id=None, status=STATUS_ACTIVE)
    invited = await _count(db, org.id, group_id=None, status=STATUS_INVITED)
    ended = await _count(db, org.id, group_id=None, status=STATUS_ENDED)
    threshold = clamp_threshold(org.reporting_threshold)
    return {
        "organisation": org.name,
        "region": org.region,
        "seats_licensed": org.seats_licensed,
        "eligible": eligible,
        "invited": invited,
        "activated": activated,
        "ended": ended,
        "reporting_threshold": threshold,
        "small_cell_suppression": org.small_cell_suppression,
        # Stated explicitly so a client cannot infer it from the absence of a
        # field: there is no per-member reporting to switch on.
        "individual_reporting_available": False,
    }


async def admin_for(db: AsyncSession, user_id: uuid.UUID) -> OrgAdmin | None:
    """The organisation this user administers, if any."""
    return await db.scalar(select(OrgAdmin).where(OrgAdmin.user_id == user_id))


async def active_membership(db: AsyncSession, user_id: uuid.UUID) -> OrgMembership | None:
    """The sponsorship covering this user today, if one is in force.

    Used for entitlement. Dates are inclusive, and a null date means open-ended
    in that direction — an organisation that has not set an end date has not
    ended anything.
    """
    today = utcnow().date()
    rows = list(
        (
            await db.scalars(
                select(OrgMembership).where(
                    OrgMembership.user_id == user_id,
                    OrgMembership.status == STATUS_ACTIVE,
                )
            )
        ).all()
    )
    for m in rows:
        if m.access_start is not None and m.access_start > today:
            continue
        if m.access_end is not None and m.access_end < today:
            continue
        return m
    return None


async def is_sponsored(db: AsyncSession, user_id: uuid.UUID) -> bool:
    """Whether an organisation is currently paying for this account.

    Entitlement only. Nothing about being sponsored changes what the member
    sees of their own data, or what anyone else sees of it.
    """
    membership = await active_membership(db, user_id)
    if membership is None:
        return False
    org = await db.get(Organization, membership.org_id)
    return bool(org and org.is_active and org.grants_premium)
