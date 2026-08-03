"""merge interventions/oracle-audit with the B2C tier-1 line

Two heads again, for the same reason as 8c27b8990a90 and one level worse: `main`
itself forked. e8a5b3d1c742 (intervention_recommendations, off d7f4a2c9e631
oracle_tool_calls, off c93f2b7a5e18 media_assets) was authored on the branch that
became local main, while 8c27b8990a90 had already merged c93f2b7a5e18 into the
line carrying safety_plans, context_memories, recommendations, goals/habits,
agent_actions and the stripe hardening. `prestart.py` runs `upgrade head` at boot
and refuses to pick between heads, so the API would not start.

Empty on purpose — the two branches touch disjoint tables:

  · this side  — oracle_tool_calls, intervention_recommendations
  · other side — safety_plans, context_memories, practice_catalog,
                 recommendations, goals/habits, agent_actions, webhook events,
                 users.stripe_customer_id

Note `recommendations` (other side, practice suggestions off pattern statements)
and `intervention_recommendations` (this side, rule-driven offers off logged
signals) are two different tables for two different features that happen to share
a word. Nothing here reconciles them; that is a product decision, not a migration.

Revision ID: f4b7c2e9a815
Revises: e8a5b3d1c742, 8c27b8990a90
Create Date: 2026-08-02 00:00:00.000000
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = 'f4b7c2e9a815'
down_revision: Union[str, None] = ('e8a5b3d1c742', '8c27b8990a90')
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    pass


def downgrade() -> None:
    pass
