"""one insight snapshot per user per week (register C53)

`snapshot_week` promised "idempotent per ISO week" with a SELECT-then-INSERT
and no constraint — the dispatcher runs in every worker, so two workers
ticking together could write two rows for the same week and `/insights/digest`
returned whichever sorted first. Deduplicate what that race left behind
(keeping the newest row per user+period), then let the database state the
invariant.

Revision ID: d7e2c9a4b816
Revises: c3f8a1d64b27
Create Date: 2026-08-05 12:00:00.000000
"""
from typing import Sequence, Union

from alembic import op

revision: str = "d7e2c9a4b816"
down_revision: Union[str, None] = "c3f8a1d64b27"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Keep the newest duplicate ((created_at, id) breaks ties deterministically).
    op.execute(
        """
        DELETE FROM insights a
        USING insights b
        WHERE a.user_id = b.user_id
          AND a.period = b.period
          AND (a.created_at, a.id) < (b.created_at, b.id)
        """
    )
    op.create_unique_constraint("uq_insights_user_period", "insights", ["user_id", "period"])


def downgrade() -> None:
    op.drop_constraint("uq_insights_user_period", "insights", type_="unique")
