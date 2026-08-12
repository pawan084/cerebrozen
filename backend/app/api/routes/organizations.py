"""`/org` — the organisation portal's API.

Every route here is scoped to the caller's OWN organisation by
`current_org_admin`, which resolves the organisation from the signed-in user
rather than from anything the client sends. There is no `org_id` path parameter
anywhere in this module: an identifier a client supplies is an identifier a
client can change, and cross-tenant reads are the failure mode this design has
to make structurally impossible rather than merely checked.

There is also no route that takes a member identifier. The nearest thing,
`DELETE /org/members/{membership_id}`, ends a *sponsorship* — it is keyed by the
membership row, returns no user data, and is the only member-shaped operation
an administrator has.
"""
from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.organization import (
    ROLES_CAN_WRITE,
    STATUS_ACTIVE,
    STATUS_ENDED,
    EligibilityGroup,
    Organization,
    OrgAdmin,
    OrgMembership,
    SponsoredProgramme,
)
from app.models.user import User
from app.schemas.organization import (
    GroupCreate,
    GroupOut,
    GroupTotalsOut,
    MembershipCreate,
    MembershipOut,
    OrgAdminOut,
    OrgOut,
    OrgSettingsUpdate,
    OrgSummaryOut,
    SponsorshipCreate,
    SponsorshipOut,
)
from app.services import organizations as org_service

router = APIRouter(prefix="/org", tags=["organizations"])


async def current_org_admin(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> tuple[OrgAdmin, Organization]:
    """The caller's org-admin record and organisation, or 403.

    Being a CereBro platform admin (`user.is_admin`) grants nothing here. The
    two are different jobs, and conflating them would let a support engineer
    read a customer's reporting surface by accident.
    """
    admin = await org_service.admin_for(db, user.id)
    if admin is None:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Not an administrator of any organisation",
        )
    org = await db.get(Organization, admin.org_id)
    if org is None or not org.is_active:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Organisation is not active")
    return admin, org


def _require_write(admin: OrgAdmin) -> None:
    if admin.role not in ROLES_CAN_WRITE:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="This role may read reports but not change eligibility or sponsorship",
        )


@router.get("", response_model=OrgOut)
async def read_org(ctx: tuple[OrgAdmin, Organization] = Depends(current_org_admin)) -> Organization:
    return ctx[1]


@router.patch("", response_model=OrgOut)
async def update_org(
    body: OrgSettingsUpdate,
    ctx: tuple[OrgAdmin, Organization] = Depends(current_org_admin),
    db: AsyncSession = Depends(get_db),
) -> Organization:
    admin, org = ctx
    _require_write(admin)
    data = body.model_dump(exclude_unset=True)
    if "reporting_threshold" in data:
        # Clamped, not rejected: an administrator asking for a smaller number
        # should be told the floor, not lose the rest of their edit.
        data["reporting_threshold"] = org_service.clamp_threshold(data["reporting_threshold"])
    for field, value in data.items():
        setattr(org, field, value)
    await db.commit()
    await db.refresh(org)
    return org


@router.get("/summary", response_model=OrgSummaryOut)
async def read_summary(
    ctx: tuple[OrgAdmin, Organization] = Depends(current_org_admin),
    db: AsyncSession = Depends(get_db),
) -> dict:
    return await org_service.org_summary(db, ctx[1])


@router.get("/groups", response_model=list[GroupOut])
async def list_groups(
    ctx: tuple[OrgAdmin, Organization] = Depends(current_org_admin),
    db: AsyncSession = Depends(get_db),
) -> list[EligibilityGroup]:
    rows = await db.scalars(
        select(EligibilityGroup)
        .where(EligibilityGroup.org_id == ctx[1].id)
        .order_by(EligibilityGroup.name)
    )
    return list(rows.all())


@router.post("/groups", response_model=GroupOut, status_code=status.HTTP_201_CREATED)
async def create_group(
    body: GroupCreate,
    ctx: tuple[OrgAdmin, Organization] = Depends(current_org_admin),
    db: AsyncSession = Depends(get_db),
) -> EligibilityGroup:
    admin, org = ctx
    _require_write(admin)
    group = EligibilityGroup(org_id=org.id, **body.model_dump())
    db.add(group)
    await db.commit()
    await db.refresh(group)
    return group


@router.get("/groups/totals", response_model=list[GroupTotalsOut])
async def group_totals(
    ctx: tuple[OrgAdmin, Organization] = Depends(current_org_admin),
    db: AsyncSession = Depends(get_db),
) -> list[dict]:
    """Per-group counts, suppressed below the organisation's threshold.

    A suppressed group is returned with null counts and `suppressed: true`
    rather than omitted, so a reader can tell "too small to report" from "no
    such group" — the portal's Outcomes screen shows the difference.
    """
    totals = await org_service.all_group_totals(db, ctx[1])
    return [t.__dict__ for t in totals]


@router.get("/admins", response_model=list[OrgAdminOut])
async def list_admins(
    ctx: tuple[OrgAdmin, Organization] = Depends(current_org_admin),
    db: AsyncSession = Depends(get_db),
) -> list[dict]:
    """Who administers this organisation, for the quarterly access review.

    Readable by any role including analyst: knowing who can see the reports is
    part of the governance story, not a privileged action. Changing the list is
    not exposed here at all — adding or removing an administrator goes through
    provisioning, so a compromised analyst session cannot grant itself company.
    """
    org = ctx[1]
    rows = list(
        (
            await db.scalars(
                select(OrgAdmin).where(OrgAdmin.org_id == org.id).order_by(OrgAdmin.role)
            )
        ).all()
    )
    out: list[dict] = []
    for row in rows:
        user = await db.get(User, row.user_id)
        # A deleted account leaves its admin row behind briefly; skip rather
        # than render a nameless entry in an access review.
        if user is None:
            continue
        out.append(
            {
                "id": row.id,
                "email": user.email,
                "name": user.name or "",
                "role": row.role,
                "attested_on": row.attested_on,
            }
        )
    return out


@router.get("/members", response_model=list[MembershipOut])
async def list_members(
    ctx: tuple[OrgAdmin, Organization] = Depends(current_org_admin),
    db: AsyncSession = Depends(get_db),
) -> list[OrgMembership]:
    """Sponsorship rows — entitlement, never activity.

    `MembershipOut` carries no user id, no email and no name: an administrator
    manages seats by their own `external_ref`, and does not need a roster of who
    holds a CereBro account to do it.
    """
    rows = await db.scalars(
        select(OrgMembership)
        .where(OrgMembership.org_id == ctx[1].id)
        .order_by(OrgMembership.external_ref)
    )
    return list(rows.all())


@router.post("/members", response_model=MembershipOut, status_code=status.HTTP_201_CREATED)
async def add_member(
    body: MembershipCreate,
    ctx: tuple[OrgAdmin, Organization] = Depends(current_org_admin),
    db: AsyncSession = Depends(get_db),
) -> OrgMembership:
    """Grant sponsorship to the account with this email.

    The email is used to find or reserve an entitlement and is not stored on the
    membership row. If no account exists yet the request is accepted and the row
    is held against `external_ref` — creating a user account here would create
    an account the person did not ask for.
    """
    admin, org = ctx
    _require_write(admin)

    if body.group_id is not None:
        group = await db.get(EligibilityGroup, body.group_id)
        if group is None or group.org_id != org.id:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Group not found")

    user = await db.scalar(select(User).where(User.email == str(body.email).lower()))
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_202_ACCEPTED,
            detail="No CereBro account for that address yet — invite it and add the seat once they sign up",
        )

    existing = await db.scalar(
        select(OrgMembership).where(
            OrgMembership.org_id == org.id, OrgMembership.user_id == user.id
        )
    )
    if existing is not None:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Already a sponsored member")

    membership = OrgMembership(
        org_id=org.id,
        user_id=user.id,
        group_id=body.group_id,
        external_ref=body.external_ref,
        status=STATUS_ACTIVE,
        access_start=body.access_start,
        access_end=body.access_end,
    )
    db.add(membership)
    await db.commit()
    await db.refresh(membership)
    return membership


@router.delete("/members/{membership_id}", response_model=MembershipOut)
async def end_membership(
    membership_id: uuid.UUID,
    ctx: tuple[OrgAdmin, Organization] = Depends(current_org_admin),
    db: AsyncSession = Depends(get_db),
) -> OrgMembership:
    """End a sponsorship. The account and its history are untouched.

    Marked `ended` rather than deleted so a seat count can be explained at
    renewal. The person keeps their account, their data and their safety tools;
    they lose the organisation-funded entitlement and nothing else.
    """
    admin, org = ctx
    _require_write(admin)
    membership = await db.get(OrgMembership, membership_id)
    if membership is None or membership.org_id != org.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Membership not found")
    membership.status = STATUS_ENDED
    await db.commit()
    await db.refresh(membership)
    return membership


@router.get("/programmes", response_model=list[SponsorshipOut])
async def list_programmes(
    ctx: tuple[OrgAdmin, Organization] = Depends(current_org_admin),
    db: AsyncSession = Depends(get_db),
) -> list[SponsoredProgramme]:
    rows = await db.scalars(
        select(SponsoredProgramme)
        .where(SponsoredProgramme.org_id == ctx[1].id)
        .order_by(SponsoredProgramme.programme_slug)
    )
    return list(rows.all())


@router.post("/programmes", response_model=SponsorshipOut, status_code=status.HTTP_201_CREATED)
async def sponsor_programme(
    body: SponsorshipCreate,
    ctx: tuple[OrgAdmin, Organization] = Depends(current_org_admin),
    db: AsyncSession = Depends(get_db),
) -> SponsoredProgramme:
    """Fund a programme for a group. It makes the programme available; it does
    not enrol anyone, and no completion is reported back."""
    admin, org = ctx
    _require_write(admin)
    if body.group_id is not None:
        group = await db.get(EligibilityGroup, body.group_id)
        if group is None or group.org_id != org.id:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Group not found")
    sponsorship = SponsoredProgramme(org_id=org.id, **body.model_dump())
    db.add(sponsorship)
    await db.commit()
    await db.refresh(sponsorship)
    return sponsorship
