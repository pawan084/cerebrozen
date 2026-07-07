"""add program_enrollments (multi-day journey tracking — ref DAY X OF Y card)

Revision ID: 0b8e5d2f7a41
Revises: f61b3d8e4c92
Create Date: 2026-07-07 18:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision: str = "0b8e5d2f7a41"
down_revision: Union[str, None] = "f61b3d8e4c92"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "program_enrollments",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "content_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("content_items.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("title", sa.String(length=160), nullable=False),
        sa.Column("days", sa.Integer(), nullable=False, server_default="7"),
        sa.Column("started_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("active", sa.Boolean(), nullable=False, server_default="true"),
    )
    op.create_index("ix_program_enrollments_user_id", "program_enrollments", ["user_id"])


def downgrade() -> None:
    op.drop_index("ix_program_enrollments_user_id", table_name="program_enrollments")
    op.drop_table("program_enrollments")
