"""add subscription tier + compliance attestation timestamps

Revision ID: 4d3b9f6a2c81
Revises: 3c2a8e5f1b74
Create Date: 2026-07-01 11:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "4d3b9f6a2c81"
down_revision: Union[str, None] = "3c2a8e5f1b74"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column("subscription_tier", sa.String(length=20), server_default="free", nullable=False),
    )
    op.add_column("users", sa.Column("age_confirmed_at", sa.DateTime(timezone=True), nullable=True))
    op.add_column("users", sa.Column("ai_disclosure_ack_at", sa.DateTime(timezone=True), nullable=True))


def downgrade() -> None:
    op.drop_column("users", "ai_disclosure_ack_at")
    op.drop_column("users", "age_confirmed_at")
    op.drop_column("users", "subscription_tier")
