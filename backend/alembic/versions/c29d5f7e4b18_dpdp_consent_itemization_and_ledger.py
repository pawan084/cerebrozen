"""DPDP: itemized consent categories (journal, sleep) + Rule 8(3) deletion ledger

Revision ID: c29d5f7e4b18
Revises: b17c4e8f2a93
Create Date: 2026-07-04 16:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "c29d5f7e4b18"
down_revision: Union[str, None] = "b17c4e8f2a93"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Existing accounts consented under the broader ai_memory umbrella —
    # the new granular flags start allowed and are switchable in-app.
    op.add_column("consents", sa.Column("journal_memory", sa.Boolean(), server_default="true", nullable=False))
    op.add_column("consents", sa.Column("sleep_history", sa.Boolean(), server_default="true", nullable=False))
    op.create_table(
        "deletion_ledger",
        sa.Column("email_hash", sa.String(length=64), nullable=False),
        sa.Column("account_created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("reason", sa.String(length=30), nullable=False),
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(op.f("ix_deletion_ledger_email_hash"), "deletion_ledger", ["email_hash"], unique=False)


def downgrade() -> None:
    op.drop_index(op.f("ix_deletion_ledger_email_hash"), table_name="deletion_ledger")
    op.drop_table("deletion_ledger")
    op.drop_column("consents", "sleep_history")
    op.drop_column("consents", "journal_memory")
