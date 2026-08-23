"""Record what a crisis escalation actually did, not what it attempted.

Revision ID: e5c07d9a2b13
Revises: d3f81b57c920

`safety_events.escalated` was set unconditionally after the trusted-contact
notification, and both senders swallow their own failures by design — a Twilio
rejection must not 500 the message that triggered the scan. So a contact who was
never reached (no SMS configured, or Twilio answered 400) was recorded exactly
like one who was, and the flag reads the same either way.

`escalation_note` carries the outcome in short tokens — `ops_alerted`,
`contact_notify_failed`, `contact_not_consented`, `no_contact` — so the admin
queue can show a reviewer that nobody was reached instead of leaving them to
assume somebody was.

Existing rows get `""`: they predate the record, and inventing an outcome for
them would be the same mistake in the other direction.
"""

from alembic import op
import sqlalchemy as sa

revision = "e5c07d9a2b13"
down_revision = "d3f81b57c920"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "safety_events",
        sa.Column(
            "escalation_note",
            sa.String(length=120),
            nullable=False,
            server_default="",
        ),
    )


def downgrade() -> None:
    op.drop_column("safety_events", "escalation_note")
