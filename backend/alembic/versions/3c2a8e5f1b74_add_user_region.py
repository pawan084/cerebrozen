"""add user region (locale-aware crisis resources)

Revision ID: 3c2a8e5f1b74
Revises: 2b1f7c4d9a02
Create Date: 2026-07-01 09:30:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "3c2a8e5f1b74"
down_revision: Union[str, None] = "2b1f7c4d9a02"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column("region", sa.String(length=8), server_default="", nullable=False),
    )


def downgrade() -> None:
    op.drop_column("users", "region")
