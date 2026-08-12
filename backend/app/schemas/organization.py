"""Wire shapes for the organisation portal.

Every response here is administrative or aggregate. If a field ever appears in
this file that names one person's behaviour, the model behind it has gone wrong.
"""
from __future__ import annotations

import uuid
from datetime import date

from pydantic import BaseModel, ConfigDict, EmailStr, Field

from app.models.organization import MIN_REPORTING_THRESHOLD


class OrgOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    legal_entity: str
    region: str
    primary_contact_email: str
    privacy_contact_email: str
    reporting_threshold: int
    small_cell_suppression: bool
    retention_months: int
    seats_licensed: int
    contract_start: date | None
    contract_end: date | None
    grants_premium: bool
    is_active: bool


class OrgSettingsUpdate(BaseModel):
    """What an administrator may change about their own organisation.

    Note what is absent: `grants_premium`, `seats_licensed` and the contract
    dates are commercial terms, not portal settings — the portal says so on
    BIL-02 — and there is no field here that could enable individual reporting,
    because no such capability exists to enable.
    """

    legal_entity: str | None = Field(default=None, max_length=200)
    primary_contact_email: EmailStr | None = None
    privacy_contact_email: EmailStr | None = None
    #: Values below the floor are raised to it rather than rejected. See
    #: services/organizations.clamp_threshold.
    reporting_threshold: int | None = Field(default=None, ge=1, le=10_000)
    small_cell_suppression: bool | None = None
    retention_months: int | None = Field(default=None, ge=1, le=120)


class GroupCreate(BaseModel):
    name: str = Field(min_length=1, max_length=160)
    rule: str = Field(default="", max_length=400)
    source: str = Field(default="manual", max_length=20)
    region: str = Field(default="IN", max_length=8)


class GroupOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    rule: str
    source: str
    region: str
    is_active: bool


class GroupTotalsOut(BaseModel):
    """Counts for one group. `activated`/`active` are null when suppressed."""

    group_id: uuid.UUID | None
    name: str
    eligible: int
    activated: int | None
    active: int | None
    suppressed: bool
    threshold: int


class MembershipCreate(BaseModel):
    """Add one eligible person.

    `email` identifies an existing CereBro account or reserves entitlement for
    one. Nothing about the person's wellbeing is accepted here, and the importer
    rejects unknown fields rather than ignoring them.
    """

    model_config = ConfigDict(extra="forbid")

    email: EmailStr
    group_id: uuid.UUID | None = None
    external_ref: str = Field(default="", max_length=120)
    access_start: date | None = None
    access_end: date | None = None


class MembershipOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    org_id: uuid.UUID
    group_id: uuid.UUID | None
    external_ref: str
    status: str
    access_start: date | None
    access_end: date | None


class SponsorshipCreate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    programme_slug: str = Field(min_length=1, max_length=80)
    group_id: uuid.UUID | None = None
    starts_on: date | None = None
    ends_on: date | None = None


class SponsorshipOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    org_id: uuid.UUID
    group_id: uuid.UUID | None
    programme_slug: str
    starts_on: date | None
    ends_on: date | None
    is_active: bool


class OrgSummaryOut(BaseModel):
    organisation: str
    region: str
    seats_licensed: int
    eligible: int
    invited: int
    activated: int
    ended: int
    reporting_threshold: int = Field(ge=MIN_REPORTING_THRESHOLD)
    small_cell_suppression: bool
    #: Always false. Present so a client reads the boundary rather than infers it.
    individual_reporting_available: bool


class OrgProvision(BaseModel):
    """Platform-admin onboarding of a new customer.

    Deliberately on `/admin`, not `/org`: creating an organisation and naming
    its first administrator is CereBro staff work. An organisation cannot
    create itself, and — the point of the split — an org admin cannot create
    another organisation or promote themselves into one.
    """

    model_config = ConfigDict(extra="forbid")

    name: str = Field(min_length=1, max_length=160)
    #: The account that becomes the first Benefits owner. Must already exist:
    #: provisioning does not create user accounts, for the same reason
    #: `POST /org/members` does not.
    admin_email: EmailStr
    legal_entity: str = Field(default="", max_length=200)
    region: str = Field(default="IN", max_length=8)
    seats_licensed: int = Field(default=0, ge=0, le=1_000_000)
    contract_start: date | None = None
    contract_end: date | None = None


class OrgAdminOut(BaseModel):
    """One administrator of this organisation.

    Identity IS returned here, unlike `MembershipOut`, and the difference is the
    point: an administrator is a named officer of the organisation, and a
    quarterly access review is meaningless without knowing who is being
    attested. A member is not, which is why seats carry no name at all.

    What is still absent: nothing about this person as a CereBro *user*. Whether
    an administrator also keeps a journal is not the organisation's business
    merely because they hold an admin role.
    """

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    email: str
    name: str
    role: str
    attested_on: date | None
