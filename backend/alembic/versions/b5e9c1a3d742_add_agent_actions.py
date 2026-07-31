"""add agent_actions (audit trail for Oracle write proposals + decisions)

Revision ID: b5e9c1a3d742
Revises: a1d4e8b2c637
Create Date: 2026-07-30 21:30:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "b5e9c1a3d742"
down_revision: Union[str, None] = "a1d4e8b2c637"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "agent_actions",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("user_id", sa.Uuid(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("thread_id", sa.String(length=120), nullable=False),
        sa.Column("tool", sa.String(length=64), nullable=False),
        sa.Column("summary", sa.Text(), nullable=False, server_default=""),
        sa.Column("status", sa.String(length=16), nullable=False, server_default="proposed"),
        sa.Column("decided_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )
    op.create_index("ix_agent_actions_user_id", "agent_actions", ["user_id"])
    op.create_index("ix_agent_actions_thread_id", "agent_actions", ["thread_id"])
    op.create_index("ix_agent_actions_tool", "agent_actions", ["tool"])
    op.create_index("ix_agent_actions_status", "agent_actions", ["status"])


def downgrade() -> None:
    for name in ("status", "tool", "thread_id", "user_id"):
        op.drop_index(f"ix_agent_actions_{name}", table_name="agent_actions")
    op.drop_table("agent_actions")
