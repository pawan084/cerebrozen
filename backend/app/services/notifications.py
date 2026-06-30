"""Push delivery.

Token-based APNs (HTTP/2) when credentials are configured; otherwise logs the
payload so the rest of the system runs unchanged in dev. Android/FCM would slot
in the same way behind :func:`send_push`.
"""
from __future__ import annotations

import logging
import time

import httpx
from jose import jwt

from app.core.config import settings
from app.models.nudge import Nudge
from app.models.user import User

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


async def send_push(user: User, nudge: Nudge) -> bool:
    if not user.push_token:
        logger.info("No push token for %s; skipping nudge %s", user.email, nudge.kind)
        return False

    if not settings.apns_enabled:
        # Dev fallback: log the would-be delivery.
        logger.info("PUSH(log) → %s: [%s] %s — %s", user.email, nudge.kind, nudge.title, nudge.body)
        return True

    auth = _apns_auth_token()
    if auth is None:
        return False

    host = "api.sandbox.push.apple.com" if settings.apns_use_sandbox else "api.push.apple.com"
    url = f"https://{host}/3/device/{user.push_token}"
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
        if resp.status_code == 200:
            return True
        logger.warning("APNs rejected nudge for %s: %s %s", user.email, resp.status_code, resp.text)
        return False
    except Exception as exc:  # pragma: no cover - network/creds dependent
        logger.error("APNs send failed: %s", exc)
        return False
