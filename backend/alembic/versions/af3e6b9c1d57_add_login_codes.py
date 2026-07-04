"""add login_codes (email one-time sign-in codes — passwordless auth)

Revision ID: af3e6b9c1d57
Revises: 9e8d4f7c2b65
Create Date: 2026-07-04 12:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "af3e6b9c1d57"
down_revision: Union[str, None] = "9e8d4f7c2b65"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "login_codes",
        sa.Column("email", sa.String(length=255), nullable=False),
        sa.Column("code_hash", sa.String(length=64), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("attempts", sa.Integer(), server_default="0", nullable=False),
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(op.f("ix_login_codes_email"), "login_codes", ["email"], unique=True)


def downgrade() -> None:
    op.drop_index(op.f("ix_login_codes_email"), table_name="login_codes")
    op.drop_table("login_codes")
