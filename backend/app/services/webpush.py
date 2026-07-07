"""Web Push (VAPID) delivery for browser nudges.

Browsers register a ``PushSubscription`` via ``/users/me/push-subscriptions``;
due nudges are encrypted end-to-end (RFC 8291, via pywebpush) and posted to
each browser's push service. The VAPID keypair is self-generated — no
third-party account. Empty keys = log-only, so the rest of the system runs
unchanged in dev/CI (mirrors :mod:`app.services.notifications` for APNs).
"""
from __future__ import annotations

import asyncio
import json
import logging

from pywebpush import WebPushException, webpush
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.models.nudge import Nudge
from app.models.user import User
from app.models.web_push import WebPushSubscription

logger = logging.getLogger("cerebro.webpush")


async def send_web_push(db: AsyncSession, user: User, nudge: Nudge) -> bool:
    """Deliver a nudge to every browser the user subscribed. True if at least
    one delivery succeeded (or would have, in the keyless log-only mode).

    Push services answer 404/410 for subscriptions the browser has dropped —
    those rows are pruned in place so dead endpoints never accumulate.
    """
    subs = (
        await db.scalars(
            select(WebPushSubscription).where(WebPushSubscription.user_id == user.id)
        )
    ).all()
    if not subs:
        return False

    if not settings.webpush_enabled:
        # Dev fallback: log the would-be delivery (same contract as APNs).
        logger.info("WEBPUSH(log) → %s: [%s] %s — %s", user.email, nudge.kind, nudge.title, nudge.body)
        return True

    payload = json.dumps(
        {"title": nudge.title, "body": nudge.body, "deeplink": nudge.deeplink, "kind": nudge.kind}
    )
    delivered = False
    for sub in subs:
        try:
            # pywebpush is synchronous (requests) — hop to a thread so the
            # dispatch loop never blocks the event loop on a slow push service.
            await asyncio.to_thread(
                webpush,
                subscription_info={
                    "endpoint": sub.endpoint,
                    "keys": {"p256dh": sub.p256dh, "auth": sub.auth},
                },
                data=payload,
                vapid_private_key=settings.vapid_private_key,
                vapid_claims={"sub": settings.vapid_subject},
            )
            delivered = True
        except WebPushException as exc:
            code = getattr(exc.response, "status_code", None)
            if code in (404, 410):
                # The browser unsubscribed/expired — drop the dead endpoint.
                await db.delete(sub)
                logger.info("Pruned dead web push endpoint for %s (%s)", user.email, code)
            else:
                logger.warning("Web push rejected for %s: %s", user.email, exc)
        except Exception as exc:  # pragma: no cover - network dependent
            logger.error("Web push send failed for %s: %s", user.email, exc)
    return delivered
