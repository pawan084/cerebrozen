"""stripe hardening: customer id + webhook idempotency

Revision ID: c8f1b6d94e23
Revises: b5e9c1a3d742
Create Date: 2026-07-30 22:30:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "c8f1b6d94e23"
down_revision: Union[str, None] = "b5e9c1a3d742"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("users", sa.Column("stripe_customer_id", sa.String(length=64), nullable=True))
    op.create_index("ix_users_stripe_customer_id", "users", ["stripe_customer_id"])

    op.create_table(
        "processed_webhooks",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("provider", sa.String(length=24), nullable=False),
        sa.Column("event_id", sa.String(length=191), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
        # The idempotency guarantee is the DB's, not the application's — two
        # concurrent deliveries of the same event race, and only one may win.
        sa.UniqueConstraint("provider", "event_id", name="uq_webhook_provider_event"),
    )
    op.create_index("ix_processed_webhooks_provider", "processed_webhooks", ["provider"])
    op.create_index("ix_processed_webhooks_event_id", "processed_webhooks", ["event_id"])


def downgrade() -> None:
    op.drop_table("processed_webhooks")
    op.drop_index("ix_users_stripe_customer_id", table_name="users")
    op.drop_column("users", "stripe_customer_id")
