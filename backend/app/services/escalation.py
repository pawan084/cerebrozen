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
    """Alert ops and (if consented) notify the trusted contact for a crisis event."""
    # 1) Operational alert — never silent.
    logger.warning("CRISIS safety event %s for user %s (%s)", event.id, user_id, event.reason)
    if settings.ops_alert_email:
        await email.send_email(
            settings.ops_alert_email,
            "[CereBro] Crisis safety event",
            f"A crisis-level event was flagged for user {user_id}.\nReason: {event.reason}\n"
            "Review it in the admin safety queue.",
        )

    # 2) Trusted-contact notification (consent-gated).
    contact = await db.scalar(select(TrustedContact).where(TrustedContact.user_id == user_id))
    if contact is None or not contact.notify_consent or not contact.value:
        return

    user = await db.get(User, user_id)
    display = (user.name if user and user.name else "Someone you care about")
    body = (
        f"{display} may be going through a hard moment and listed you as a trusted "
        "contact in CereBro. Please consider reaching out to them. If they may be in "
        "immediate danger, contact local emergency services."
    )
    if contact.method == "email":
        await email.send_email(contact.value, "A wellbeing check-in from CereBro", body)
    else:  # sms | phone
        await sms.send_sms(contact.value, body)

    event.escalated = True
    event.escalated_at = utcnow()
