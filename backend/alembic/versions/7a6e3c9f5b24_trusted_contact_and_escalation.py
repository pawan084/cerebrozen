"""trusted contact + safety-event escalation

Revision ID: 7a6e3c9f5b24
Revises: 6f5d2b8e4a13
Create Date: 2026-07-01 14:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "7a6e3c9f5b24"
down_revision: Union[str, None] = "6f5d2b8e4a13"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "trusted_contacts",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("name", sa.String(length=120), nullable=False, server_default=""),
        sa.Column("method", sa.String(length=20), nullable=False, server_default="email"),
        sa.Column("value", sa.String(length=255), nullable=False, server_default=""),
        sa.Column("relationship", sa.String(length=60), nullable=False, server_default=""),
        sa.Column("notify_consent", sa.Boolean(), nullable=False, server_default="false"),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_trusted_contacts_user_id", "trusted_contacts", ["user_id"], unique=True)

    op.add_column("safety_events", sa.Column("escalated", sa.Boolean(), server_default="false", nullable=False))
    op.add_column("safety_events", sa.Column("escalated_at", sa.DateTime(timezone=True), nullable=True))


def downgrade() -> None:
    op.drop_column("safety_events", "escalated_at")
    op.drop_column("safety_events", "escalated")
    op.drop_index("ix_trusted_contacts_user_id", table_name="trusted_contacts")
    op.drop_table("trusted_contacts")
