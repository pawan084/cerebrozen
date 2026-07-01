"""Transactional email (verification, password reset).

Sends via SMTP when configured; otherwise logs the message so the flow is fully
exercisable in dev without a provider. Under tests the last message is captured
in ``sent_outbox`` so flows can be asserted without real delivery.
"""
from __future__ import annotations

import asyncio
import logging
import os
import smtplib
from email.message import EmailMessage

from app.core.config import settings

logger = logging.getLogger("cerebro.email")

# Test-only capture of sent messages (list of dicts).
sent_outbox: list[dict] = []


def _smtp_send(to: str, subject: str, body: str) -> None:
    msg = EmailMessage()
    msg["From"] = settings.smtp_from or settings.smtp_user
    msg["To"] = to
    msg["Subject"] = subject
    msg.set_content(body)
    with smtplib.SMTP(settings.smtp_host, settings.smtp_port, timeout=15) as server:
        if settings.smtp_tls:
            server.starttls()
        if settings.smtp_user:
            server.login(settings.smtp_user, settings.smtp_password)
        server.send_message(msg)


async def send_email(to: str, subject: str, body: str) -> None:
    """Best-effort send. Never raises to the caller (a failed email must not
    500 the auth flow)."""
    if os.getenv("TESTING") == "1":
        sent_outbox.append({"to": to, "subject": subject, "body": body})
        return
    if not settings.smtp_host:
        logger.info("EMAIL (no SMTP configured) → %s | %s\n%s", to, subject, body)
        return
    try:
        await asyncio.to_thread(_smtp_send, to, subject, body)
    except Exception as exc:  # pragma: no cover - network path
        logger.warning("Email send failed (%s → %s): %s", subject, to, exc)
