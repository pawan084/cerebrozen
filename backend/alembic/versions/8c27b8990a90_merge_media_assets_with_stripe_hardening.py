"""merge media assets with stripe hardening

Two heads existed: c93f2b7a5e18 (media_assets) was authored off b8e6d1a4f527 on a
branch cut from an older main, while c7a4e91b6d38 had already claimed that parent
and the line continued to c8f1b6d94e23. `prestart.py` runs `upgrade head` at boot,
which refuses to pick between heads, so the API could not start.

Empty on purpose — the two branches touch disjoint tables (media_assets +
content_items.video_url on one side, subscription/stripe columns on the other), so
joining them needs no data or schema work. Generated with `alembic merge`, not by
hand: c7a4e91b6d38 declares down_revision without type annotations and hand-rolled
head detection misses it (see CLAUDE.md).

Revision ID: 8c27b8990a90
Revises: c8f1b6d94e23, c93f2b7a5e18
Create Date: 2026-07-31 08:50:11.976134
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = '8c27b8990a90'
down_revision: Union[str, None] = ('c8f1b6d94e23', 'c93f2b7a5e18')
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    pass


def downgrade() -> None:
    pass
