"""merge the device-tokens line with the interventions line

Two heads, third occurrence of the same pattern (see 8c27b8990a90 and
f4b7c2e9a815): e4c8a1d69b25 (device_tokens + idempotency_records) was authored
on a branch that had produced its own duplicate merge of the media/stripe
parents (d2b7f9c41a63 — same parent pair 8c27b8990a90 already joined, harmless
but a sign the branch never saw main), while main's line had continued to
f4b7c2e9a815. `prestart.py` runs `upgrade head` at boot and refuses to pick
between heads, so the API would not start.

Empty on purpose — the branches touch disjoint tables:

  · this side  — device_tokens, idempotency_records
  · other side — oracle_tool_calls, intervention_recommendations (and the
                 whole B2C tier-1 set below it)

Revision ID: a9d3e7f2c481
Revises: e4c8a1d69b25, f4b7c2e9a815
Create Date: 2026-08-03 00:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = 'a9d3e7f2c481'
down_revision: Union[str, None] = ('e4c8a1d69b25', 'f4b7c2e9a815')
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    pass


def downgrade() -> None:
    pass
