"""safety event resolution audit trail

Closing a crisis flag used to flip a single boolean, so a resolved row could not
answer "who decided this was handled, when, and why". These three columns make
the review queue an audit trail rather than a checklist.

Revision ID: c7a4e91b6d38
Revises: b8e6d1a4f527
Create Date: 2026-07-30

"""

from __future__ import annotations

import sqlalchemy as sa
from alembic import op

revision = "c7a4e91b6d38"
down_revision = "b8e6d1a4f527"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("safety_events", sa.Column("resolved_by", sa.Uuid(), nullable=True))
    op.add_column(
        "safety_events", sa.Column("resolved_at", sa.DateTime(timezone=True), nullable=True)
    )
    op.add_column(
        "safety_events",
        sa.Column("resolution_note", sa.String(length=500), nullable=False, server_default=""),
    )
    op.create_index(
        op.f("ix_safety_events_resolved_by"), "safety_events", ["resolved_by"], unique=False
    )
    # SET NULL, not CASCADE: if the reviewing admin's account is later deleted we
    # lose who resolved it, but we must not lose the safety event itself.
    op.create_foreign_key(
        "fk_safety_events_resolved_by_users",
        "safety_events",
        "users",
        ["resolved_by"],
        ["id"],
        ondelete="SET NULL",
    )


def downgrade() -> None:
    op.drop_constraint("fk_safety_events_resolved_by_users", "safety_events", type_="foreignkey")
    op.drop_index(op.f("ix_safety_events_resolved_by"), table_name="safety_events")
    op.drop_column("safety_events", "resolution_note")
    op.drop_column("safety_events", "resolved_at")
    op.drop_column("safety_events", "resolved_by")
