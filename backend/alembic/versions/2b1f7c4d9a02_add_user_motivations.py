"""add user motivations (self-reflection assessment)

Revision ID: 2b1f7c4d9a02
Revises: 11990476bc18
Create Date: 2026-06-29 10:15:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision: str = "2b1f7c4d9a02"
down_revision: Union[str, None] = "11990476bc18"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column(
            "motivations",
            postgresql.JSONB(astext_type=sa.Text()),
            server_default=sa.text("'[]'::jsonb"),
            nullable=False,
        ),
    )


def downgrade() -> None:
    op.drop_column("users", "motivations")
