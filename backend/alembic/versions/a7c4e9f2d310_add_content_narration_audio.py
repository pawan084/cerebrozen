"""add content narration audio (narrated-audio pipeline: script + generated MP3)

Revision ID: a7c4e9f2d310
Revises: 0b8e5d2f7a41
Create Date: 2026-07-07 21:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "a7c4e9f2d310"
down_revision: Union[str, None] = "0b8e5d2f7a41"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "content_items",
        sa.Column("narration_script", sa.Text(), nullable=False, server_default=""),
    )
    op.add_column(
        "content_items",
        sa.Column("audio_url", sa.String(length=1024), nullable=False, server_default=""),
    )
    op.add_column(
        "content_items",
        sa.Column("audio_generated_at", sa.DateTime(timezone=True), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("content_items", "audio_generated_at")
    op.drop_column("content_items", "audio_url")
    op.drop_column("content_items", "narration_script")
