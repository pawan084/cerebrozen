"""add practice_catalog + recommendations (patterns become actionable)

Revision ID: f93c2e7a5b48
Revises: e8a3b6d1f927
Create Date: 2026-07-30 14:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "f93c2e7a5b48"
down_revision: Union[str, None] = "e8a3b6d1f927"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "practice_catalog",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("slug", sa.String(length=64), nullable=False, unique=True),
        sa.Column("title", sa.String(length=160), nullable=False),
        sa.Column("body", sa.Text(), nullable=False, server_default=""),
        sa.Column("action", sa.String(length=64), nullable=False, server_default=""),
        sa.Column("active", sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
        ),
    )
    op.create_index("ix_practice_catalog_slug", "practice_catalog", ["slug"], unique=True)
    op.create_index("ix_practice_catalog_active", "practice_catalog", ["active"])

    op.create_table(
        "recommendations",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column(
            "user_id",
            sa.Uuid(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("practice_slug", sa.String(length=64), nullable=False),
        sa.Column("reason", sa.Text(), nullable=False, server_default=""),
        sa.Column("status", sa.String(length=16), nullable=False, server_default="pending"),
        sa.Column("resolved_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()
        ),
    )
    op.create_index("ix_recommendations_user_id", "recommendations", ["user_id"])
    op.create_index("ix_recommendations_practice_slug", "recommendations", ["practice_slug"])
    op.create_index("ix_recommendations_status", "recommendations", ["status"])


def downgrade() -> None:
    op.drop_index("ix_recommendations_status", table_name="recommendations")
    op.drop_index("ix_recommendations_practice_slug", table_name="recommendations")
    op.drop_index("ix_recommendations_user_id", table_name="recommendations")
    op.drop_table("recommendations")
    op.drop_index("ix_practice_catalog_active", table_name="practice_catalog")
    op.drop_index("ix_practice_catalog_slug", table_name="practice_catalog")
    op.drop_table("practice_catalog")
