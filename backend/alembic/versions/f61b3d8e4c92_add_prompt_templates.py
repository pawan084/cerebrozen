"""add prompt_templates (versioned LLM prompt registry; admin-editable)

Revision ID: f61b3d8e4c92
Revises: e52a9c7d3b81
Create Date: 2026-07-07 16:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision: str = "f61b3d8e4c92"
down_revision: Union[str, None] = "e52a9c7d3b81"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "prompt_templates",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("name", sa.String(length=80), nullable=False),
        sa.Column("version", sa.Integer(), nullable=False),
        sa.Column("template", sa.Text(), nullable=False),
        sa.Column("notes", sa.String(length=255), nullable=False, server_default=""),
        sa.Column("active", sa.Boolean(), nullable=False, server_default="true"),
        sa.UniqueConstraint("name", "version", name="uq_prompt_name_version"),
    )
    op.create_index("ix_prompt_templates_name", "prompt_templates", ["name"])


def downgrade() -> None:
    op.drop_index("ix_prompt_templates_name", table_name="prompt_templates")
    op.drop_table("prompt_templates")
