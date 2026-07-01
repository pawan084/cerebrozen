"""add subscription_expires_at

Revision ID: 5e4c1a7d3f92
Revises: 4d3b9f6a2c81
Create Date: 2026-07-01 12:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "5e4c1a7d3f92"
down_revision: Union[str, None] = "4d3b9f6a2c81"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("users", sa.Column("subscription_expires_at", sa.DateTime(timezone=True), nullable=True))


def downgrade() -> None:
    op.drop_column("users", "subscription_expires_at")
