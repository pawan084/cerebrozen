"""add media assets catalogue + content scene video

The media catalogue keys every sound/video a client can play. An empty `url` is a
valid row: it means "no server asset yet" and the client uses its bundled or
synthesized fallback, so the catalogue can ship ahead of the assets.

Revision ID: c93f2b7a5e18
Revises: b8e6d1a4f527
Create Date: 2026-07-13 10:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision: str = "c93f2b7a5e18"
down_revision: Union[str, None] = "b8e6d1a4f527"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "media_assets",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("key", sa.String(length=120), nullable=False),
        sa.Column("kind", sa.String(length=40), nullable=False),
        sa.Column("title", sa.String(length=160), nullable=False, server_default=""),
        sa.Column("url", sa.String(length=1024), nullable=False, server_default=""),
        sa.Column("mime", sa.String(length=80), nullable=False, server_default=""),
        sa.Column("duration_ms", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("loop", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("published", sa.Boolean(), nullable=False, server_default=sa.true()),
    )
    op.create_index(op.f("ix_media_assets_key"), "media_assets", ["key"], unique=True)
    op.create_index(op.f("ix_media_assets_kind"), "media_assets", ["kind"])
    op.create_index(op.f("ix_media_assets_published"), "media_assets", ["published"])

    op.add_column(
        "content_items",
        sa.Column("video_url", sa.String(length=1024), nullable=False, server_default=""),
    )


def downgrade() -> None:
    op.drop_column("content_items", "video_url")
    op.drop_index(op.f("ix_media_assets_published"), table_name="media_assets")
    op.drop_index(op.f("ix_media_assets_kind"), table_name="media_assets")
    op.drop_index(op.f("ix_media_assets_key"), table_name="media_assets")
    op.drop_table("media_assets")
