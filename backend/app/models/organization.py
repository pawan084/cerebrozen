"""B2B2C: an organisation that pays for member access, and nothing more.

The whole design constraint is one sentence from the portal it backs: *an
organisation can see totals and never a person*. Everything here is shaped by
that, so a few absences are deliberate and should stay absent:

* There is **no** `manager_dashboards` flag, and no per-member activity table.
  `apps/portal` tells administrators that individual reporting is "not a feature
  that exists in a disabled state" — a column called `manager_dashboards` would
  make that sentence false the moment someone flipped it in psql.
* `OrgMembership` links an organisation to a user for *entitlement*, and carries
  no wellbeing field. It answers "is this person sponsored, between these
  dates" and nothing else.
* Nothing in this module references `MoodLog`, `JournalEntry`, `ChatMessage`,
  `SleepLog` or `SafetyPlan`. That is not an oversight; `tests/test_org.py`
  asserts it, so an import here would fail the suite.

Reporting aggregates live in `services/organizations.py`, which applies the
organisation's own threshold before returning any count.
"""
from __future__ import annotations

import uuid
from datetime import date

from sqlalchemy import Boolean, Date, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base

# Least-privilege roles, mirroring apps/portal ROL-01. No role reaches wellbeing
# content — the difference between them is which *administrative* surface they
# may use, which is why the boundary is enforced by the absence of read paths
# rather than by this list.
ROLE_BENEFITS_OWNER = "benefits_owner"
ROLE_PROGRAMME_ADMIN = "programme_admin"
ROLE_ANALYST = "analyst"
ROLE_PRIVACY_REVIEWER = "privacy_reviewer"

ORG_ROLES: frozenset[str] = frozenset(
    {ROLE_BENEFITS_OWNER, ROLE_PROGRAMME_ADMIN, ROLE_ANALYST, ROLE_PRIVACY_REVIEWER}
)

#: Roles allowed to change eligibility, sponsorship and settings. An analyst may
#: read aggregates and nothing else.
ROLES_CAN_WRITE: frozenset[str] = frozenset({ROLE_BENEFITS_OWNER, ROLE_PROGRAMME_ADMIN})

# Membership lifecycle. "ended" is kept rather than deleted so a seat count can
# be explained at renewal; it grants nothing.
STATUS_INVITED = "invited"
STATUS_ACTIVE = "active"
STATUS_ENDED = "ended"

MEMBERSHIP_STATUSES: frozenset[str] = frozenset({STATUS_INVITED, STATUS_ACTIVE, STATUS_ENDED})

#: Floor for `reporting_threshold`. Below this, a "group total" starts to
#: describe individuals — which is the one thing this model exists to prevent.
#: Settable upward per organisation, never downward.
MIN_REPORTING_THRESHOLD = 20


class Organization(Base):
    """A paying organisation. Owns eligibility and reporting settings only."""

    __tablename__ = "organizations"

    name: Mapped[str] = mapped_column(String(160), index=True)
    legal_entity: Mapped[str] = mapped_column(String(200), default="", server_default="")
    #: ISO-3166 alpha-2. Drives which crisis resources sponsored members see.
    region: Mapped[str] = mapped_column(String(8), default="IN", server_default="IN")
    primary_contact_email: Mapped[str] = mapped_column(String(255), default="", server_default="")
    privacy_contact_email: Mapped[str] = mapped_column(String(255), default="", server_default="")

    #: Minimum group size before any aggregate is reported. Never below
    #: MIN_REPORTING_THRESHOLD — enforced in the service, not just the schema,
    #: because a direct DB write should not be able to weaken it silently.
    reporting_threshold: Mapped[int] = mapped_column(
        Integer, default=MIN_REPORTING_THRESHOLD, server_default=str(MIN_REPORTING_THRESHOLD)
    )
    #: Suppress dimension combinations that would isolate individuals.
    small_cell_suppression: Mapped[bool] = mapped_column(Boolean, default=True, server_default="true")
    #: How long generated aggregate reports are retained, in months.
    retention_months: Mapped[int] = mapped_column(Integer, default=24, server_default="24")

    seats_licensed: Mapped[int] = mapped_column(Integer, default=0, server_default="0")
    contract_start: Mapped[date | None] = mapped_column(Date, nullable=True)
    contract_end: Mapped[date | None] = mapped_column(Date, nullable=True)
    #: Whether sponsorship grants premium entitlement while a membership is active.
    grants_premium: Mapped[bool] = mapped_column(Boolean, default=True, server_default="true")

    is_active: Mapped[bool] = mapped_column(Boolean, default=True, server_default="true")


class OrgAdmin(Base):
    """A user who administers an organisation. Not a member of it."""

    __tablename__ = "org_admins"
    __table_args__ = (UniqueConstraint("org_id", "user_id", name="uq_org_admin"),)

    org_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("organizations.id", ondelete="CASCADE"), index=True
    )
    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    role: Mapped[str] = mapped_column(String(32), default=ROLE_ANALYST, server_default=ROLE_ANALYST)
    #: Quarterly access review (portal ROL-02): an unattested admin loses access
    #: rather than being grandfathered.
    attested_on: Mapped[date | None] = mapped_column(Date, nullable=True)


class EligibilityGroup(Base):
    """A named, reporting-safe group of eligible people.

    "Cohort" in the portal. It describes *eligibility* — region, worker status,
    benefit — never anything a member did.
    """

    __tablename__ = "eligibility_groups"
    __table_args__ = (UniqueConstraint("org_id", "name", name="uq_org_group_name"),)

    org_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("organizations.id", ondelete="CASCADE"), index=True
    )
    name: Mapped[str] = mapped_column(String(160))
    rule: Mapped[str] = mapped_column(String(400), default="", server_default="")
    #: "csv" | "hris" | "api" | "manual"
    source: Mapped[str] = mapped_column(String(20), default="manual", server_default="manual")
    region: Mapped[str] = mapped_column(String(8), default="IN", server_default="IN")
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, server_default="true")


class OrgMembership(Base):
    """One sponsored person, for entitlement purposes only.

    Deliberately thin. It records that an organisation pays for this account
    between these dates — not what the account is used for. There is no
    `last_active`, no `sessions`, no `programme_progress`: each of those would
    be an individual behavioural record held by an employer, which is the exact
    thing the product promises does not exist.
    """

    __tablename__ = "org_memberships"
    __table_args__ = (UniqueConstraint("org_id", "user_id", name="uq_org_membership"),)

    org_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("organizations.id", ondelete="CASCADE"), index=True
    )
    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    group_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("eligibility_groups.id", ondelete="SET NULL"), nullable=True, index=True
    )
    #: The organisation's own identifier for this person (payroll/HRIS key).
    #: Stored so an eligibility sync can reconcile without email matching.
    external_ref: Mapped[str] = mapped_column(String(120), default="", server_default="")
    status: Mapped[str] = mapped_column(String(20), default=STATUS_INVITED, server_default=STATUS_INVITED)
    access_start: Mapped[date | None] = mapped_column(Date, nullable=True)
    access_end: Mapped[date | None] = mapped_column(Date, nullable=True)


class SponsoredProgramme(Base):
    """An organisation funding a programme for a group, between dates.

    Sponsorship makes a programme *available*. It never enrols anyone: taking it
    up stays the member's decision, and no completion is reported back.
    """

    __tablename__ = "sponsored_programmes"

    org_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("organizations.id", ondelete="CASCADE"), index=True
    )
    group_id: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("eligibility_groups.id", ondelete="SET NULL"), nullable=True, index=True
    )
    #: Matches the catalogue slug in services/programs.py.
    programme_slug: Mapped[str] = mapped_column(String(80), index=True)
    starts_on: Mapped[date | None] = mapped_column(Date, nullable=True)
    ends_on: Mapped[date | None] = mapped_column(Date, nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, server_default="true")
