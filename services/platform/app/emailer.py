"""Outbound email — invitation delivery.

Stdlib smtplib against the same SMTP_* variables the marketing site's demo
form uses, so one mailbox configuration serves both. Degrades without keys:
unconfigured SMTP means invitations are shared manually via the revealed
link — creation never depends on delivery, and a delivery failure is logged,
never raised into the request.
"""

import logging
import smtplib
from email.message import EmailMessage

from app import config

logger = logging.getLogger("cerebrozen.platform.email")


def configured() -> bool:
    return bool(config.SMTP_HOST and config.SMTP_USER and config.SMTP_PASS)


def _deliver(msg: EmailMessage) -> None:
    if config.SMTP_PORT == 465:
        with smtplib.SMTP_SSL(config.SMTP_HOST, config.SMTP_PORT, timeout=15) as s:
            s.login(config.SMTP_USER, config.SMTP_PASS)
            s.send_message(msg)
    else:
        with smtplib.SMTP(config.SMTP_HOST, config.SMTP_PORT, timeout=15) as s:
            s.starttls()
            s.login(config.SMTP_USER, config.SMTP_PASS)
            s.send_message(msg)


# The transport seam tests patch — everything above it is stdlib plumbing.
deliver = _deliver


def send_invitation(to: str, org_name: str, role: str, link: str) -> bool:
    """Send the accept link. True = handed to SMTP; False = not configured
    or delivery failed (the caller surfaces manual sharing either way)."""
    if not configured():
        logger.info("email.skipped_unconfigured to=%s", to)
        return False
    msg = EmailMessage()
    msg["From"] = f"CereBroZen <{config.SMTP_USER}>"
    msg["To"] = to
    msg["Subject"] = f"You're invited to {org_name} on CereBroZen"
    role_line = (
        "as an organization admin" if role == "org_admin" else "as a member"
    )
    msg.set_content(
        f"You've been invited to join {org_name} on CereBroZen {role_line}.\n\n"
        f"Accept your invitation (single-use; it holds your seat until it expires):\n\n"
        f"    {link}\n\n"
        f"If you weren't expecting this, you can ignore this email — nothing "
        f"is created until the invitation is accepted.\n"
    )
    try:
        deliver(msg)
        logger.info("email.invitation_sent to=%s org=%s", to, org_name)
        return True
    except Exception as exc:  # noqa: BLE001 — delivery must never break creation
        logger.warning("email.invitation_failed to=%s error=%s", to, exc)
        return False


def send_password_reset(to: str, link: str) -> bool:
    """Email a single-use reset link. Same best-effort contract as invitations:
    unconfigured SMTP or a delivery failure returns False and is logged, never raised
    (the request always answers 200 so account existence isn't revealed)."""
    if not configured():
        logger.info("email.reset_skipped_unconfigured to=%s", to)
        return False
    msg = EmailMessage()
    msg["From"] = f"CereBroZen <{config.SMTP_USER}>"
    msg["To"] = to
    msg["Subject"] = "Reset your CereBroZen password"
    msg.set_content(
        "We got a request to reset your CereBroZen password.\n\n"
        "Set a new one here (this link is single-use and expires soon):\n\n"
        f"    {link}\n\n"
        "If you didn't ask for this, you can ignore this email — your password "
        "hasn't changed.\n"
    )
    try:
        deliver(msg)
        logger.info("email.reset_sent to=%s", to)
        return True
    except Exception as exc:  # noqa: BLE001 — delivery must never break the flow
        logger.warning("email.reset_failed to=%s error=%s", to, exc)
        return False


def send_otp_code(to: str, code: str) -> bool:
    """Email a one-time sign-in code. Best-effort, same contract as the others."""
    if not configured():
        logger.info("email.otp_skipped_unconfigured to=%s", to)
        return False
    msg = EmailMessage()
    msg["From"] = f"CereBroZen <{config.SMTP_USER}>"
    msg["To"] = to
    msg["Subject"] = "Your CereBroZen sign-in code"
    msg.set_content(
        f"Your CereBroZen sign-in code is:\n\n    {code}\n\n"
        "It expires shortly and can be used once. If you didn't request it, you can "
        "ignore this email.\n"
    )
    try:
        deliver(msg)
        logger.info("email.otp_sent to=%s", to)
        return True
    except Exception as exc:  # noqa: BLE001 — delivery must never break the flow
        logger.warning("email.otp_failed to=%s error=%s", to, exc)
        return False
