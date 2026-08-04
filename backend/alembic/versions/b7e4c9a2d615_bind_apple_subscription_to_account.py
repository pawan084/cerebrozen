"""bind the Apple subscription to one account (register C2)

One signed StoreKit transaction used to grant premium on unlimited
accounts — nothing tied the receipt to the buyer. The unique
original-transaction id is that tie: the first account to verify a
subscription owns it.

Revision ID: b7e4c9a2d615
Revises: a9d3e7f2c481
Create Date: 2026-08-04 22:30:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "b7e4c9a2d615"
down_revision: Union[str, None] = "a9d3e7f2c481"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column("apple_original_transaction_id", sa.String(length=64), nullable=True),
    )
    op.create_index(
        "ix_users_apple_original_transaction_id",
        "users",
        ["apple_original_transaction_id"],
        unique=True,
    )


def downgrade() -> None:
    op.drop_index("ix_users_apple_original_transaction_id", table_name="users")
    op.drop_column("users", "apple_original_transaction_id")
