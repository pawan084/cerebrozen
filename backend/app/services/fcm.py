"""Firebase Cloud Messaging (HTTP v1) delivery for Android nudges.

The Android app registers its FCM token via ``/users/me/devices``; due nudges
are posted to Google's v1 endpoint with a short-lived OAuth2 access token
minted from a service-account key. No `google-auth` dependency: the assertion
flow is ten lines of JWT that `python-jose` already covers, and the APNs sender
next door mints its token the same way.

Empty credentials = log-only, exactly like APNs and Web Push, so dev and CI run
unchanged with blank keys.
"""
from __future__ import annotations

import json
import logging
import time

import httpx
from jose import jwt

from app.core.config import settings
from app.models.nudge import Nudge

logger = logging.getLogger("cerebro.fcm")

_TOKEN_URL = "https://oauth2.googleapis.com/token"
_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
# Google issues 1-hour tokens; refresh a little early.
_TOKEN_TTL = 50 * 60

_token_cache: dict[str, tuple[str, float]] = {}

# What a send did, so the dispatcher can tell "try again later" from "this
# install is gone" without knowing anything about FCM's error taxonomy.
OK = "ok"
RETRY = "retry"
DEAD = "dead"


def _service_account() -> dict | None:
    try:
        with open(settings.fcm_credentials_path) as fh:
            return json.load(fh)
    except Exception as exc:  # pragma: no cover - depends on real creds on disk
        logger.error("Could not read FCM service account: %s", exc)
        return None


async def _access_token() -> str | None:
    cached = _token_cache.get("access")
    if cached and (time.time() - cached[1]) < _TOKEN_TTL:
        return cached[0]

    account = _service_account()
    if account is None:
        return None
    now = int(time.time())
    try:
        assertion = jwt.encode(
            {
                "iss": account["client_email"],
                "scope": _SCOPE,
                "aud": _TOKEN_URL,
                "iat": now,
                "exp": now + 3600,
            },
            account["private_key"],
            algorithm="RS256",
        )
    except Exception as exc:  # pragma: no cover - malformed key material
        logger.error("Could not sign the FCM assertion: %s", exc)
        return None

    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(
                _TOKEN_URL,
                data={
                    "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                    "assertion": assertion,
                },
            )
        if resp.status_code != 200:
            logger.error("FCM token exchange failed: %s %s", resp.status_code, resp.text)
            return None
        token = resp.json().get("access_token")
    except Exception as exc:  # pragma: no cover - network dependent
        logger.error("FCM token exchange error: %s", exc)
        return None

    if not token:
        return None
    _token_cache["access"] = (token, time.time())
    return token


def build_message(token: str, nudge: Nudge) -> dict:
    """The v1 message body. Split out so its shape is testable without network.

    Sent as a *data* message rather than a `notification` one: the app builds
    the notification itself, so the same payload lands identically whether the
    app is foreground, background or killed, and the deeplink survives. The
    Android block sets the channel the client created and a normal priority —
    a wellness nudge is never an alarm.
    """
    return {
        "message": {
            "token": token,
            "data": {
                "title": nudge.title,
                "body": nudge.body or "",
                "deeplink": nudge.deeplink or "",
                "kind": nudge.kind,
            },
            "android": {
                "priority": "normal",
                "notification": {"channel_id": "cerebro_nudges"},
            },
        }
    }


def classify(status_code: int, body: str) -> str:
    """Map an FCM response to [OK] / [RETRY] / [DEAD]. Pure, so the table is a test."""
    if status_code == 200:
        return OK
    # The install is gone (uninstalled, token rotated, wrong sender).
    if status_code == 404 or "UNREGISTERED" in body:
        return DEAD
    # INVALID_ARGUMENT is DEAD only when FCM blames the token. It is also what
    # a malformed *payload* returns — and DEAD stamps failed_at, so a payload
    # bug on our side would otherwise silently bury every registered install.
    if "INVALID_ARGUMENT" in body and "token" in body.lower():
        return DEAD
    return RETRY


async def send(token: str, nudge: Nudge) -> str:
    """Deliver one nudge to one Android install."""
    if not settings.fcm_enabled:
        logger.info("FCM(log) → %s: [%s] %s — %s", token[:12], nudge.kind, nudge.title, nudge.body)
        return OK

    access = await _access_token()
    if access is None:
        return RETRY

    url = f"https://fcm.googleapis.com/v1/projects/{settings.fcm_project_id}/messages:send"
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(
                url,
                json=build_message(token, nudge),
                headers={"Authorization": f"Bearer {access}"},
            )
    except Exception as exc:  # pragma: no cover - network dependent
        logger.error("FCM send failed: %s", exc)
        return RETRY

    verdict = classify(resp.status_code, resp.text)
    if verdict != OK:
        logger.warning("FCM %s for %s: %s %s", verdict, token[:12], resp.status_code, resp.text)
    return verdict
