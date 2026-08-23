"""Crisis escalation — turns a detected crisis into duty-of-care action.

On a crisis-level SafetyEvent this:
  1. Emits an operational alert (log WARNING, + email to ops if configured) so the
     admin queue isn't purely pull-only.
  2. Notifies the user's trusted contact **iff** one exists and the user consented
     (consent is a hard gate), then marks the event escalated.

Notifications go out via the email service (SMTP when configured, else logged),
so the whole path is exercisable without external providers.
"""
from __future__ import annotations

import logging
import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import utcnow
from app.models.safety import SafetyEvent
from app.models.trusted_contact import TrustedContact
from app.models.user import User
from app.services import email, sms

logger = logging.getLogger("cerebro.escalation")


async def on_crisis(db: AsyncSession, *, user_id: uuid.UUID, event: SafetyEvent) -> None:
    """Alert ops and (if consented) notify the trusted contact for a crisis event.

    **Records what actually happened, not what was attempted.** Both senders
    swallow their own failures by design — a Twilio rejection must not 500 the
    message that triggered the scan — and `event.escalated` used to be set
    unconditionally afterwards. So a contact who was never reached, because the
    deployment had no SMS configured or Twilio returned a 400, was recorded as
    escalated. That is a false record in the one place where being wrong costs
    most, and it is invisible: the flag reads the same either way.

    `escalation_note` carries the outcome in short machine-readable tokens, so
    the admin queue can show a reviewer that the contact was NOT reached rather
    than leaving them to assume somebody was.
    """
    notes: list[str] = []

    # 1) Operational alert — never silent.
    logger.warning("CRISIS safety event %s for user %s (%s)", event.id, user_id, event.reason)
    if settings.ops_alert_email:
        sent = await email.send_email(
            settings.ops_alert_email,
            "[CereBro] Crisis safety event",
            f"A crisis-level event was flagged for user {user_id}.\nReason: {event.reason}\n"
            "Review it in the admin safety queue.",
        )
        notes.append("ops_alerted" if sent else "ops_alert_failed")
    else:
        # Worth recording rather than inferring from an absence: nobody was
        # told, and the reason is configuration rather than a delivery failure.
        notes.append("ops_alert_unconfigured")

    # 2) Trusted-contact notification (consent-gated).
    contact = await db.scalar(select(TrustedContact).where(TrustedContact.user_id == user_id))
    if contact is None or not contact.value:
        notes.append("no_contact")
    elif not contact.notify_consent:
        # Not a failure. The user chose this, and the choice is the feature.
        notes.append("contact_not_consented")
    else:
        user = await db.get(User, user_id)
        display = (user.name if user and user.name else "Someone you care about")
        body = (
            f"{display} may be going through a hard moment and listed you as a trusted "
            "contact in CereBro. Please consider reaching out to them. If they may be in "
            "immediate danger, contact local emergency services."
        )
        if contact.method == "email":
            reached = await email.send_email(
                contact.value, "A wellbeing check-in from CereBro", body
            )
        else:  # sms | phone
            reached = await sms.send_sms(contact.value, body)

        if reached:
            event.escalated = True
            event.escalated_at = utcnow()
            notes.append("contact_notified")
        else:
            # The consent was given, the attempt was made, and it did not land.
            # This is the case that most needs a human to see it.
            notes.append("contact_notify_failed")
            logger.warning(
                "CRISIS contact notification FAILED for event %s (method=%s)",
                event.id, contact.method,
            )

    event.escalation_note = ",".join(notes)[:120]
