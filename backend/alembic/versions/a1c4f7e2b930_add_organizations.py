"""add organisations, admins, eligibility groups, memberships and sponsorships

The B2B2C model behind apps/portal. Note what is NOT created: there is no table
recording what a sponsored member did. Membership is an entitlement row, and the
only numbers an organisation can read are counts over it.

Revision ID: a1c4f7e2b930
Revises: d7e2c9a4b816
Create Date: 2026-08-12 21:40:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "a1c4f7e2b930"
down_revision: Union[str, None] = "d7e2c9a4b816"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

# Mirrors MIN_REPORTING_THRESHOLD in app/models/organization.py. Duplicated
# because a migration must not import application code that may move.
_MIN_THRESHOLD = "20"


def upgrade() -> None:
    op.create_table(
        "organizations",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("name", sa.String(length=160), nullable=False),
        sa.Column("legal_entity", sa.String(length=200), nullable=False, server_default=""),
        sa.Column("region", sa.String(length=8), nullable=False, server_default="IN"),
        sa.Column("primary_contact_email", sa.String(length=255), nullable=False, server_default=""),
        sa.Column("privacy_contact_email", sa.String(length=255), nullable=False, server_default=""),
        sa.Column("reporting_threshold", sa.Integer(), nullable=False, server_default=_MIN_THRESHOLD),
        sa.Column("small_cell_suppression", sa.Boolean(), nullable=False, server_default="true"),
        sa.Column("retention_months", sa.Integer(), nullable=False, server_default="24"),
        sa.Column("seats_licensed", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("contract_start", sa.Date(), nullable=True),
        sa.Column("contract_end", sa.Date(), nullable=True),
        sa.Column("grants_premium", sa.Boolean(), nullable=False, server_default="true"),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default="true"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_organizations_name", "organizations", ["name"])

    op.create_table(
        "org_admins",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("org_id", sa.Uuid(), sa.ForeignKey("organizations.id", ondelete="CASCADE"), nullable=False),
        sa.Column("user_id", sa.Uuid(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("role", sa.String(length=32), nullable=False, server_default="analyst"),
        sa.Column("attested_on", sa.Date(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.UniqueConstraint("org_id", "user_id", name="uq_org_admin"),
    )
    op.create_index("ix_org_admins_org_id", "org_admins", ["org_id"])
    op.create_index("ix_org_admins_user_id", "org_admins", ["user_id"])

    op.create_table(
        "eligibility_groups",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("org_id", sa.Uuid(), sa.ForeignKey("organizations.id", ondelete="CASCADE"), nullable=False),
        sa.Column("name", sa.String(length=160), nullable=False),
        sa.Column("rule", sa.String(length=400), nullable=False, server_default=""),
        sa.Column("source", sa.String(length=20), nullable=False, server_default="manual"),
        sa.Column("region", sa.String(length=8), nullable=False, server_default="IN"),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default="true"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.UniqueConstraint("org_id", "name", name="uq_org_group_name"),
    )
    op.create_index("ix_eligibility_groups_org_id", "eligibility_groups", ["org_id"])

    op.create_table(
        "org_memberships",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("org_id", sa.Uuid(), sa.ForeignKey("organizations.id", ondelete="CASCADE"), nullable=False),
        sa.Column("user_id", sa.Uuid(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column(
            "group_id",
            sa.Uuid(),
            sa.ForeignKey("eligibility_groups.id", ondelete="SET NULL"),
            nullable=True,
        ),
        sa.Column("external_ref", sa.String(length=120), nullable=False, server_default=""),
        sa.Column("status", sa.String(length=20), nullable=False, server_default="invited"),
        sa.Column("access_start", sa.Date(), nullable=True),
        sa.Column("access_end", sa.Date(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.UniqueConstraint("org_id", "user_id", name="uq_org_membership"),
    )
    op.create_index("ix_org_memberships_org_id", "org_memberships", ["org_id"])
    op.create_index("ix_org_memberships_user_id", "org_memberships", ["user_id"])
    op.create_index("ix_org_memberships_group_id", "org_memberships", ["group_id"])

    op.create_table(
        "sponsored_programmes",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("org_id", sa.Uuid(), sa.ForeignKey("organizations.id", ondelete="CASCADE"), nullable=False),
        sa.Column(
            "group_id",
            sa.Uuid(),
            sa.ForeignKey("eligibility_groups.id", ondelete="SET NULL"),
            nullable=True,
        ),
        sa.Column("programme_slug", sa.String(length=80), nullable=False),
        sa.Column("starts_on", sa.Date(), nullable=True),
        sa.Column("ends_on", sa.Date(), nullable=True),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default="true"),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_sponsored_programmes_org_id", "sponsored_programmes", ["org_id"])
    op.create_index("ix_sponsored_programmes_group_id", "sponsored_programmes", ["group_id"])
    op.create_index("ix_sponsored_programmes_slug", "sponsored_programmes", ["programme_slug"])


def downgrade() -> None:
    op.drop_table("sponsored_programmes")
    op.drop_table("org_memberships")
    op.drop_table("eligibility_groups")
    op.drop_table("org_admins")
    op.drop_table("organizations")
