"""add users.apple_sub (stable SIWA id — private-relay/no-email support)

Revision ID: 8c7f5d1b9e46
Revises: 7a6e3c9f5b24
Create Date: 2026-07-02 12:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "8c7f5d1b9e46"
down_revision: Union[str, None] = "7a6e3c9f5b24"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("users", sa.Column("apple_sub", sa.String(length=64), nullable=True))
    op.create_index("ix_users_apple_sub", "users", ["apple_sub"], unique=True)


def downgrade() -> None:
    op.drop_index("ix_users_apple_sub", table_name="users")
    op.drop_column("users", "apple_sub")
