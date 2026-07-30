"""add safety_plans (user-authored Stanley-Brown plan, versioned)

Revision ID: e8a3b6d1f927
Revises: d7f2a5c9e814
Create Date: 2026-07-30 12:30:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "e8a3b6d1f927"
down_revision: Union[str, None] = "d7f2a5c9e814"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

_SECTIONS = (
    "warning_signs",
    "internal_coping",
    "social_distractors",
    "social_support",
    "professionals",
    "means_safety",
    "notes",
)


def upgrade() -> None:
    op.create_table(
        "safety_plans",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column(
            "user_id",
            sa.Uuid(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("version", sa.Integer(), nullable=False, server_default="1"),
        *[
            sa.Column(name, sa.Text(), nullable=False, server_default="")
            for name in _SECTIONS
        ],
        # NULL = the live plan. Superseded versions are archived, not deleted.
        sa.Column("archived_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
    )
    op.create_index("ix_safety_plans_user_id", "safety_plans", ["user_id"])
    op.create_index("ix_safety_plans_archived_at", "safety_plans", ["archived_at"])


def downgrade() -> None:
    op.drop_index("ix_safety_plans_archived_at", table_name="safety_plans")
    op.drop_index("ix_safety_plans_user_id", table_name="safety_plans")
    op.drop_table("safety_plans")
