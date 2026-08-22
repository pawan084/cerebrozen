"""Add nudges.attempts and nudges.next_attempt_at (WC-24 durability).

Two gaps the multi-worker proof left open, both recorded in TODO on 2026-08-22:

* a transiently failed delivery was terminal — one FCM blip and the nudge was
  marked `failed` forever, so the user simply never got it;
* a nudge scheduled while every instance was down went out whenever the
  dispatcher next ticked, however much later that was.

`scheduled_for` keeps meaning "when this was MEANT to arrive" — it has to, or
lateness cannot be measured once a retry moves the clock. `next_attempt_at` is
the new "when to try next", null until something needs retrying.

Revision ID: c4e9b28d17fa
Revises: a7d3f10c9e64
Create Date: 2026-08-22
"""

from alembic import op
import sqlalchemy as sa

revision = "c4e9b28d17fa"
down_revision = "a7d3f10c9e64"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "nudges",
        sa.Column("attempts", sa.Integer(), nullable=False, server_default="0"),
    )
    op.add_column(
        "nudges",
        sa.Column("next_attempt_at", sa.DateTime(timezone=True), nullable=True),
    )
    # The dispatcher's due-query reads whichever of the two is in play, so it
    # is indexed the way it is filtered.
    op.create_index("ix_nudges_next_attempt_at", "nudges", ["next_attempt_at"])


def downgrade() -> None:
    op.drop_index("ix_nudges_next_attempt_at", table_name="nudges")
    op.drop_column("nudges", "next_attempt_at")
    op.drop_column("nudges", "attempts")
