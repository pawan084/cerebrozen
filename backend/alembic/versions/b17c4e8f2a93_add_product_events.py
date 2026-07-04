"""add product_events (anonymous first-party analytics — never user-linked)

Revision ID: b17c4e8f2a93
Revises: af3e6b9c1d57
Create Date: 2026-07-04 14:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "b17c4e8f2a93"
down_revision: Union[str, None] = "af3e6b9c1d57"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "product_events",
        sa.Column("anon_id", sa.String(length=64), nullable=False),
        sa.Column("name", sa.String(length=60), nullable=False),
        sa.Column("source", sa.String(length=10), nullable=False),
        sa.Column("step", sa.String(length=60), nullable=False),
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(op.f("ix_product_events_anon_id"), "product_events", ["anon_id"], unique=False)
    op.create_index(op.f("ix_product_events_name"), "product_events", ["name"], unique=False)


def downgrade() -> None:
    op.drop_index(op.f("ix_product_events_name"), table_name="product_events")
    op.drop_index(op.f("ix_product_events_anon_id"), table_name="product_events")
    op.drop_table("product_events")
