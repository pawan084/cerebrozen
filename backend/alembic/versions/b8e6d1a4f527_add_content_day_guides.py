"""add content day guides (per-day program structure: [{"title","body"}] per day)

Revision ID: b8e6d1a4f527
Revises: a7c4e9f2d310
Create Date: 2026-07-12 10:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision: str = "b8e6d1a4f527"
down_revision: Union[str, None] = "a7c4e9f2d310"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "content_items",
        sa.Column("day_guides", postgresql.JSONB(), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("content_items", "day_guides")
