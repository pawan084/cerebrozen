"""Per-account daily ceilings for the calls that cost money.

Revision ID: d3f81b57c920
Revises: b2e9f47c1a08

See app/models/daily_usage.py for why this exists. Briefly: the per-minute rate
limits bound a burst and not a day, so plan generation allowed 14,400 calls per
account per day and TTS allowed 86,400, and `services/usage.py` — the only daily
cap in the product — covers chat alone.

The unique constraint is not hygiene. `services/usage.consume` increments with a
single `INSERT … ON CONFLICT DO UPDATE … RETURNING`, and the constraint is what
makes concurrent requests land on one row instead of racing a read-then-write.
"""

from alembic import op
import sqlalchemy as sa

revision = "d3f81b57c920"
down_revision = "b2e9f47c1a08"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "daily_usage",
        sa.Column("id", sa.Uuid(), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column(
            "user_id",
            sa.Uuid(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("feature", sa.String(length=40), nullable=False),
        sa.Column("day", sa.Date(), nullable=False),
        sa.Column("count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.UniqueConstraint(
            "user_id", "feature", "day", name="uq_daily_usage_account_feature_day"
        ),
    )
    op.create_index("ix_daily_usage_user_id", "daily_usage", ["user_id"])
    op.create_index("ix_daily_usage_feature", "daily_usage", ["feature"])
    op.create_index("ix_daily_usage_day", "daily_usage", ["day"])


def downgrade() -> None:
    op.drop_index("ix_daily_usage_day", table_name="daily_usage")
    op.drop_index("ix_daily_usage_feature", table_name="daily_usage")
    op.drop_index("ix_daily_usage_user_id", table_name="daily_usage")
    op.drop_table("daily_usage")
