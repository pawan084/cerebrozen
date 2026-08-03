"""add intervention_recommendations (recommend-with-a-rationale engine)

Rules live in code (services/interventions.RULES), so there is no rules table —
only the per-user offers they produce, each carrying the plain-language reason
and the counts behind it, frozen at fire time.

Revision ID: e8a5b3d1c742
Revises: d7f4a2c9e631
Create Date: 2026-07-28
"""
import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision = "e8a5b3d1c742"
down_revision = "d7f4a2c9e631"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "intervention_recommendations",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("rule_slug", sa.String(length=80), nullable=False),
        sa.Column("tier", sa.String(length=20), nullable=False, server_default="self_support"),
        sa.Column("reason", sa.String(length=300), nullable=False, server_default=""),
        sa.Column("action_kind", sa.String(length=40), nullable=False, server_default="widget"),
        sa.Column("action_target", sa.String(length=120), nullable=False, server_default=""),
        sa.Column("state_snapshot", postgresql.JSONB(), nullable=False, server_default="{}"),
        sa.Column("accepted_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("dismissed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("completed_at", sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
    )
    op.create_index(
        "ix_intervention_recommendations_user_id", "intervention_recommendations", ["user_id"]
    )
    op.create_index(
        "ix_intervention_recommendations_rule_slug", "intervention_recommendations", ["rule_slug"]
    )


def downgrade() -> None:
    op.drop_index("ix_intervention_recommendations_rule_slug", table_name="intervention_recommendations")
    op.drop_index("ix_intervention_recommendations_user_id", table_name="intervention_recommendations")
    op.drop_table("intervention_recommendations")
