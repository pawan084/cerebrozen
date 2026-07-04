"""add users.email_nudges (opt-in email delivery for web-only users)

Revision ID: d41f6a8c2e95
Revises: c29d5f7e4b18
Create Date: 2026-07-04 18:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "d41f6a8c2e95"
down_revision: Union[str, None] = "c29d5f7e4b18"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("users", sa.Column("email_nudges", sa.Boolean(), server_default="false", nullable=False))


def downgrade() -> None:
    op.drop_column("users", "email_nudges")
