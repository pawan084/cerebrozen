"""merge the two heads left by add_media_assets and the stripe-hardening branch

Revision ID: d2b7f9c41a63
Revises: c8f1b6d94e23, c93f2b7a5e18
Create Date: 2026-08-01 10:00:00.000000

Both branches descend from ``b8e6d1a4f527`` (content day guides): the safety /
recommendations / stripe line, and the media-assets line. Two heads make
``alembic upgrade head`` fail outright ("Multiple head revisions are present"),
and ``prestart.py`` catches that failure and falls back to ``create_all``.

``create_all`` only ever CREATEs missing tables — it never ALTERs an existing
one. So on any database that already had the schema, every migration after the
branch point silently stopped applying while the boot log showed one warning.
This merge is empty by design: it exists to give the graph a single head again
so the real migrations either run or fail loudly.
"""
from typing import Sequence, Union

revision: str = "d2b7f9c41a63"
down_revision: Union[str, Sequence[str], None] = ("c8f1b6d94e23", "c93f2b7a5e18")
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """No-op: a merge point carries no schema change of its own."""


def downgrade() -> None:
    """No-op."""
