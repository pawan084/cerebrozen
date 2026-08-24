"""Per-language display overrides for the content catalogue.

The 2026-08-24 Hindi device walk's one untouchable finding: every programme,
sound and wind-down title arrived from this table in English, inside otherwise
fully-Hindi chrome. Resources fixed the clients; this fixes the content.
Additive and nullable — untranslated rows serve exactly as before.

Revision ID: a1d4f7c2e9b6
Revises: e5c07d9a2b13
Create Date: 2026-08-25
"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects.postgresql import JSONB

revision: str = "a1d4f7c2e9b6"
down_revision: Union[str, None] = "e5c07d9a2b13"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("content_items", sa.Column("i18n", JSONB, nullable=True))


def downgrade() -> None:
    op.drop_column("content_items", "i18n")
