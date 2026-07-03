"""add sleep_logs (sleep-diary morning check-ins — docs/SLEEP_TRACKING.md)

Revision ID: 9e8d4f7c2b65
Revises: 8c7f5d1b9e46
Create Date: 2026-07-03 12:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "9e8d4f7c2b65"
down_revision: Union[str, None] = "8c7f5d1b9e46"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "sleep_logs",
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("date", sa.Date(), nullable=False),
        sa.Column("bedtime", sa.Time(), nullable=False),
        sa.Column("wake_time", sa.Time(), nullable=False),
        sa.Column("quality", sa.Integer(), nullable=False),
        sa.Column("awakenings", sa.Integer(), nullable=False),
        sa.Column("source", sa.String(length=20), nullable=False),
        sa.Column("note", sa.String(length=255), nullable=False),
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("user_id", "date", name="uq_sleep_logs_user_date"),
    )
    op.create_index(op.f("ix_sleep_logs_user_id"), "sleep_logs", ["user_id"], unique=False)


def downgrade() -> None:
    op.drop_index(op.f("ix_sleep_logs_user_id"), table_name="sleep_logs")
    op.drop_table("sleep_logs")
