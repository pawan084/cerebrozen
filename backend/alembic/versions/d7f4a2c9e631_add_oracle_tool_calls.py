"""add oracle_tool_calls (agent audit trail + pending confirmations)

The Oracle's write tools edit a user's own records behind an interrupt()
confirmation; nothing recorded which ran, which were approved, or which were
still stuck. Stores argument NAMES only — never their values — so the trail
never becomes a second, un-consented copy of journal or mood content.

Revision ID: d7f4a2c9e631
Revises: c93f2b7a5e18
Create Date: 2026-07-28
"""
import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "d7f4a2c9e631"
down_revision = "c93f2b7a5e18"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "oracle_tool_calls",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("thread_id", sa.String(length=120), nullable=False),
        sa.Column("tool", sa.String(length=80), nullable=False),
        sa.Column("risk_tier", sa.String(length=16), nullable=False, server_default="read"),
        sa.Column("decision", sa.String(length=16), nullable=False, server_default="auto"),
        sa.Column("arg_keys", postgresql.JSONB(), nullable=False, server_default="[]"),
        sa.Column("resolved_at", sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
    )
    op.create_index("ix_oracle_tool_calls_user_id", "oracle_tool_calls", ["user_id"])
    op.create_index("ix_oracle_tool_calls_thread_id", "oracle_tool_calls", ["thread_id"])
    op.create_index("ix_oracle_tool_calls_tool", "oracle_tool_calls", ["tool"])
    op.create_index("ix_oracle_tool_calls_decision", "oracle_tool_calls", ["decision"])


def downgrade() -> None:
    op.drop_index("ix_oracle_tool_calls_decision", table_name="oracle_tool_calls")
    op.drop_index("ix_oracle_tool_calls_tool", table_name="oracle_tool_calls")
    op.drop_index("ix_oracle_tool_calls_thread_id", table_name="oracle_tool_calls")
    op.drop_index("ix_oracle_tool_calls_user_id", table_name="oracle_tool_calls")
    op.drop_table("oracle_tool_calls")
