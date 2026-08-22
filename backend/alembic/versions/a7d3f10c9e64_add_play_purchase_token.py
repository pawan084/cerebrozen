"""Add users.play_purchase_token (Google Play receipt verification, WC-15).

UNIQUE for the same reason `apple_original_transaction_id` is: a signed Play
purchase payload verifies forever and on any account, so the signature alone
cannot stop one purchase unlocking premium on a hundred logins. The uniqueness
constraint is what makes "first account to verify it owns it" a rule the
database enforces rather than a check somebody has to remember.

Revision ID: a7d3f10c9e64
Revises: b2d5e8a1c473
Create Date: 2026-08-22
"""

from alembic import op
import sqlalchemy as sa

revision = "a7d3f10c9e64"
down_revision = "b2d5e8a1c473"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("users", sa.Column("play_purchase_token", sa.String(length=255), nullable=True))
    op.create_index(
        "ix_users_play_purchase_token", "users", ["play_purchase_token"], unique=True
    )


def downgrade() -> None:
    op.drop_index("ix_users_play_purchase_token", table_name="users")
    op.drop_column("users", "play_purchase_token")
