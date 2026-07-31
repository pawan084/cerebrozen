"""add context_memories (addressable per-item AI memory + pattern suppression)

Revision ID: d7f2a5c9e814
Revises: c7a4e91b6d38
Create Date: 2026-07-30 12:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "d7f2a5c9e814"
down_revision: Union[str, None] = "c7a4e91b6d38"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "context_memories",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column(
            "user_id",
            sa.Uuid(),
            # Account deletion relies on the cascade — see users.delete_my_account.
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("body", sa.Text(), nullable=False),
        sa.Column("salience", sa.Float(), nullable=False, server_default="0.5"),
        sa.Column("source", sa.String(length=24), nullable=False, server_default="manual"),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("dismissed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
    )
    op.create_index("ix_context_memories_user_id", "context_memories", ["user_id"])
    op.create_index("ix_context_memories_source", "context_memories", ["source"])
    # Expiry is swept by a range scan, so it wants its own index.
    op.create_index("ix_context_memories_expires_at", "context_memories", ["expires_at"])


def downgrade() -> None:
    op.drop_index("ix_context_memories_expires_at", table_name="context_memories")
    op.drop_index("ix_context_memories_source", table_name="context_memories")
    op.drop_index("ix_context_memories_user_id", table_name="context_memories")
    op.drop_table("context_memories")
