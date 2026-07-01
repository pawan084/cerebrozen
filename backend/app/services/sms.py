"""SMS delivery (Twilio) for trusted-contact phone/SMS notifications.

Sends via Twilio's REST API when configured; otherwise logs so the flow is fully
exercisable without an account. Under tests the last message is captured in
``sent_outbox``.
"""
from __future__ import annotations

import logging
import os

import httpx

from app.core.config import settings

logger = logging.getLogger("cerebro.sms")

# Test-only capture.
sent_outbox: list[dict] = []


def _configured() -> bool:
    return bool(settings.twilio_account_sid and settings.twilio_auth_token and settings.twilio_from)


async def send_sms(to: str, body: str) -> None:
    """Best-effort SMS send. Never raises (a failed SMS must not break the crisis
    escalation path)."""
    if os.getenv("TESTING") == "1":
        sent_outbox.append({"to": to, "body": body})
        return
    if not _configured():
        logger.info("SMS (no Twilio configured) → %s: %s", to, body)
        return
    url = f"https://api.twilio.com/2010-04-01/Accounts/{settings.twilio_account_sid}/Messages.json"
    try:
        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.post(
                url,
                data={"From": settings.twilio_from, "To": to, "Body": body},
                auth=(settings.twilio_account_sid, settings.twilio_auth_token),
            )
            if resp.status_code >= 400:
                logger.warning("Twilio rejected SMS to %s: %s", to, resp.text[:200])
    except Exception as exc:  # pragma: no cover - network path
        logger.warning("SMS send failed (%s): %s", to, exc)
