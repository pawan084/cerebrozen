"""Grandfather every account that predates the email-verification gate.

Revision ID: b2e9f47c1a08
Revises: c4e9b28d17fa

`email_verified` has existed since the auth-hardening revision with
``server_default="false"``, and until this release signup sent nothing to
confirm — the only way to set it was `POST /auth/verify/request`, an endpoint a
signed-in user had to know existed and ask for. So in production essentially
every password-signup account carries False, not because anyone failed a check
but because there was never a check to fail.

Deploying the gate without this would, on contact, drop every existing free user
from `FREE_DAILY_MESSAGES` to `UNVERIFIED_DAILY_MESSAGES` and 403 them out of
voice, plan generation, goal decomposition, assessment and the Oracle. No client
renders the `email_unverified` code yet either, so they would meet generic
failures with no route to fix them. A feature aimed at new bot signups would
have degraded the entire existing user base.

**Why a separate column rather than backfilling `email_verified = true`.**
Setting that flag would be the one-line version, and it would be a lie: the flag
means "this address was confirmed", and confirming these is exactly what never
happened. Anything later trusting it — a password reset path, a compliance
answer about which addresses are reachable — would inherit the lie. This column
says the true thing instead: this account predates the requirement, so the
requirement is not applied to it.

New rows get False, so the gate applies in full from this release onward, which
is the entire point of shipping it.
"""

from alembic import op
import sqlalchemy as sa

revision = "b2e9f47c1a08"
down_revision = "c4e9b28d17fa"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column(
            "verification_grandfathered",
            sa.Boolean(),
            server_default="false",
            nullable=False,
        ),
    )
    # Every row that exists at this moment predates the gate. Rows created after
    # this statement keep the column default of false — which is why the UPDATE
    # is unconditional and still only ever touches the old accounts.
    op.execute("UPDATE users SET verification_grandfathered = true")


def downgrade() -> None:
    op.drop_column("users", "verification_grandfathered")
