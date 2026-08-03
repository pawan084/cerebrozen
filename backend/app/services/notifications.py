"""Push delivery.

Token-based APNs (HTTP/2) when credentials are configured; otherwise logs the
payload so the rest of the system runs unchanged in dev. Android goes out over
FCM (:mod:`app.services.fcm`) on the same terms.

Two entry points, and the difference matters:

* :func:`send_push` — the original one-token APNs path, driven by the single
  ``User.push_token`` column. Kept because iOS still sets it.
* :func:`deliver` — fans a nudge out to every registered install in
  ``device_tokens`` (iOS *and* Android, a phone *and* a tablet), prunes the
  ones the provider reports as gone, and falls back to :func:`send_push` for
  accounts that only ever set the legacy column.
"""
from __future__ import annotations

import logging
import time

import httpx
from jose import jwt
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import utcnow
from app.models.device_token import DeviceToken
from app.models.nudge import Nudge
from app.models.user import User
from app.services import fcm

logger = logging.getLogger("cerebro.push")

# Apple recommends reusing the auth token for up to ~1 hour.
_token_cache: dict[str, tuple[str, float]] = {}
_TOKEN_TTL = 50 * 60


def _apns_auth_token() -> str | None:
    cached = _token_cache.get("jwt")
    if cached and (time.time() - cached[1]) < _TOKEN_TTL:
        return cached[0]
    try:
        with open(settings.apns_key_path) as fh:
            key = fh.read()
        token = jwt.encode(
            {"iss": settings.apns_team_id, "iat": int(time.time())},
            key,
            algorithm="ES256",
            headers={"kid": settings.apns_key_id},
        )
        _token_cache["jwt"] = (token, time.time())
        return token
    except Exception as exc:  # pragma: no cover - depends on real creds
        logger.error("Could not build APNs auth token: %s", exc)
        return None


async def send_apns(token: str, nudge: Nudge) -> str:
    """Deliver one nudge to one iOS install. Returns [fcm.OK] / [fcm.RETRY] /
    [fcm.DEAD] so both providers speak the same three words to the dispatcher.

    APNs answers 410 (and 400 BadDeviceToken) for installs that are gone — that
    is the signal to stop sending to this row, not to try again tomorrow.
    """
    if not settings.apns_enabled:
        # Dev fallback: log the would-be delivery.
        logger.info("PUSH(log) → %s: [%s] %s — %s", token[:12], nudge.kind, nudge.title, nudge.body)
        return fcm.OK

    auth = _apns_auth_token()
    if auth is None:
        return fcm.RETRY

    host = "api.sandbox.push.apple.com" if settings.apns_use_sandbox else "api.push.apple.com"
    url = f"https://{host}/3/device/{token}"
    payload = {
        "aps": {"alert": {"title": nudge.title, "body": nudge.body}, "sound": "default"},
        "deeplink": nudge.deeplink,
        "kind": nudge.kind,
    }
    headers = {
        "authorization": f"bearer {auth}",
        "apns-topic": settings.apns_bundle_id,
        "apns-push-type": "alert",
        "apns-priority": "10",
    }
    try:
        async with httpx.AsyncClient(http2=True, timeout=10) as client:
            resp = await client.post(url, json=payload, headers=headers)
    except Exception as exc:  # pragma: no cover - network/creds dependent
        logger.error("APNs send failed: %s", exc)
        return fcm.RETRY

    if resp.status_code == 200:
        return fcm.OK
    logger.warning("APNs rejected nudge for %s: %s %s", token[:12], resp.status_code, resp.text)
    if resp.status_code == 410 or "BadDeviceToken" in resp.text or "Unregistered" in resp.text:
        return fcm.DEAD
    return fcm.RETRY


async def send_push(user: User, nudge: Nudge) -> bool:
    """Legacy single-token path, driven by ``User.push_token`` (iOS sets it on
    sign-in). :func:`deliver` prefers the per-device table and only falls back
    here for accounts that never registered one."""
    if not user.push_token:
        logger.info("No push token for %s; skipping nudge %s", user.email, nudge.kind)
        return False
    return await send_apns(user.push_token, nudge) == fcm.OK


async def deliver(db: AsyncSession, user: User, nudge: Nudge) -> bool:
    """Fan one nudge out to every live install this user has registered.

    True when at least one install took it. False means "this user has no
    native device we can reach" — the caller then tries browser push, then
    email, rather than recording a delivery that never happened.

    Tokens the provider calls dead are stamped ``failed_at`` in the same
    transaction, so an uninstalled app stops costing a request every pass. A
    token that merely failed transiently is left alone and retried next time.
    """
    rows = (
        await db.scalars(
            select(DeviceToken).where(
                DeviceToken.user_id == user.id,
                DeviceToken.failed_at.is_(None),
            )
        )
    ).all()
    if not rows:
        return await send_push(user, nudge)

    delivered = False
    for row in rows:
        if row.platform == "android":
            verdict = await fcm.send(row.token, nudge)
        else:
            verdict = await send_apns(row.token, nudge)
        if verdict == fcm.OK:
            delivered = True
        elif verdict == fcm.DEAD:
            row.failed_at = utcnow()
    await db.flush()
    return delivered
