"""Stripe web billing — the browser twin of services/appstore.py.

Same entitlement contract: a verified event maps a price id to
``subscription_tier`` (+ expiry) on the user. Plain REST over httpx and
manual webhook-signature verification (Stripe's t=/v1= HMAC-SHA256 scheme) —
no SDK dependency. Everything degrades when unconfigured: checkout raises,
the webhook rejects, and the App Store flow is untouched.
"""
from __future__ import annotations

import hashlib
import hmac
import json
import logging
from datetime import datetime, timezone

import httpx

from app.core.config import settings

logger = logging.getLogger("cerebro.stripe")

_API = "https://api.stripe.com/v1"
_SIG_TOLERANCE_SECONDS = 300


class StripeError(Exception):
    """Configuration, transport, or verification failure."""


def _price_map() -> dict[str, str]:
    return {
        price: tier
        for price, tier in (
            (settings.stripe_price_premium_monthly, "premium"),
            (settings.stripe_price_premium_annual, "premium"),
            (settings.stripe_price_premium_human_monthly, "premium_human"),
            (settings.stripe_price_premium_human_annual, "premium_human"),
        )
        if price
    }


def price_for(tier: str, annual: bool) -> str:
    price = {
        ("premium", False): settings.stripe_price_premium_monthly,
        ("premium", True): settings.stripe_price_premium_annual,
        ("premium_human", False): settings.stripe_price_premium_human_monthly,
        ("premium_human", True): settings.stripe_price_premium_human_annual,
    }.get((tier, annual), "")
    if not price:
        raise StripeError(f"no Stripe price configured for {tier} ({'annual' if annual else 'monthly'})")
    return price


async def create_checkout_session(user_id: str, tier: str, annual: bool) -> str:
    """Create a subscription Checkout Session and return its redirect URL.
    The user id rides along as client_reference_id AND subscription metadata,
    so every later webhook event can be mapped back without a customer store."""
    if not settings.stripe_enabled:
        raise StripeError("Stripe is not configured")
    data = {
        "mode": "subscription",
        "line_items[0][price]": price_for(tier, annual),
        "line_items[0][quantity]": "1",
        "client_reference_id": user_id,
        "success_url": f"{settings.stripe_return_url}?billing=success",
        "cancel_url": f"{settings.stripe_return_url}?billing=cancelled",
        "metadata[user_id]": user_id,
        "metadata[tier]": tier,
        "subscription_data[metadata][user_id]": user_id,
    }
    try:
        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.post(f"{_API}/checkout/sessions", data=data,
                                     auth=(settings.stripe_secret_key, ""))
    except httpx.HTTPError as exc:  # pragma: no cover - network path
        raise StripeError(f"Stripe unreachable: {exc}") from exc
    if resp.status_code != 200:
        logger.warning("Stripe checkout creation failed (%s): %s", resp.status_code, resp.text[:300])
        raise StripeError("checkout session creation failed")
    url = resp.json().get("url")
    if not url:
        raise StripeError("checkout session had no url")
    return url


def verify_webhook(payload: bytes, sig_header: str) -> dict:
    """Verify a Stripe-Signature header (t=...,v1=...) and return the event.
    HMAC-SHA256 over "{t}.{payload}" with the webhook signing secret; the
    timestamp must be within tolerance to blunt replay."""
    if not settings.stripe_webhook_secret:
        raise StripeError("webhook secret not configured")
    parts = dict(
        kv.split("=", 1) for kv in sig_header.split(",") if "=" in kv
    )
    timestamp, signature = parts.get("t"), parts.get("v1")
    if not timestamp or not signature:
        raise StripeError("malformed signature header")
    expected = hmac.new(settings.stripe_webhook_secret.encode(),
                        f"{timestamp}.{payload.decode()}".encode(),
                        hashlib.sha256).hexdigest()
    if not hmac.compare_digest(expected, signature):
        raise StripeError("signature mismatch")
    now = datetime.now(timezone.utc).timestamp()
    if abs(now - float(timestamp)) > _SIG_TOLERANCE_SECONDS:
        raise StripeError("stale signature")
    try:
        return json.loads(payload)
    except ValueError as exc:
        raise StripeError("payload is not JSON") from exc


def entitlement_from_event(event: dict) -> tuple[str, str, datetime | None] | None:
    """(user_id, tier, expires_at) from a subscription-relevant event, or None
    for event types we deliberately ignore."""
    kind = event.get("type", "")
    obj = (event.get("data") or {}).get("object") or {}
    metadata = obj.get("metadata") or {}

    if kind == "checkout.session.completed":
        user_id = obj.get("client_reference_id") or metadata.get("user_id") or ""
        tier = metadata.get("tier") or "premium"
        return (user_id, tier, None) if user_id else None

    if kind in {"customer.subscription.created", "customer.subscription.updated",
                "customer.subscription.deleted"}:
        user_id = metadata.get("user_id") or ""
        if not user_id:
            return None
        period_end = obj.get("current_period_end")
        expires = (datetime.fromtimestamp(period_end, tz=timezone.utc)
                   if isinstance(period_end, (int, float)) else None)
        status = obj.get("status", "")
        if kind == "customer.subscription.deleted" or status in {"canceled", "unpaid", "incomplete_expired"}:
            return (user_id, "free", expires)
        items = ((obj.get("items") or {}).get("data") or [{}])
        price_id = ((items[0] or {}).get("price") or {}).get("id", "")
        tier = _price_map().get(price_id, "premium")
        return (user_id, tier, expires)

    return None
