"""Billing provider seam (B2C).

`mock` (default, keyless) completes a purchase in-process so the whole freemium loop
runs with no payment keys. `stripe` creates a hosted Checkout Session and activates the
subscription on a signature-verified webhook. The Stripe SDK is imported lazily, so the
adapter is INERT — a clean 503 / ignored webhook — until both the SDK and the STRIPE_*
keys are present. `CEREBROZEN_BILLING_PROVIDER` selects; absent, it follows BILLING_MOCK.
"""

from __future__ import annotations

import os

from fastapi import HTTPException

from app import config

_UNAVAILABLE = HTTPException(503, "checkout is temporarily unavailable")


def provider_name() -> str:
    explicit = os.environ.get("CEREBROZEN_BILLING_PROVIDER", "").strip().lower()
    if explicit in ("mock", "stripe"):
        return explicit
    return "mock" if config.BILLING_MOCK else "none"


def begin_checkout(org, plan: str, interval: str) -> tuple[str, str | None]:
    """Return ``("activate", None)`` when the caller should activate the subscription
    in-process (mock), or ``("redirect", url)`` to send the user to a hosted checkout
    (stripe). Raises 503 when no provider is configured."""
    name = provider_name()
    if name == "mock":
        return ("activate", None)
    if name == "stripe":
        return ("redirect", _stripe_checkout_url(org, plan, interval))
    raise _UNAVAILABLE


def _stripe_checkout_url(org, plan: str, interval: str) -> str:
    key = os.environ.get("STRIPE_SECRET_KEY", "").strip()
    price_id = os.environ.get(f"STRIPE_PRICE_{interval.upper()}", "").strip()
    if not key or not price_id:
        raise _UNAVAILABLE
    try:
        import stripe
    except ImportError:  # SDK not installed → stay inert
        raise _UNAVAILABLE
    stripe.api_key = key
    base = config.APP_BASE_URL
    session = stripe.checkout.Session.create(
        mode="subscription",
        line_items=[{"price": price_id, "quantity": 1}],
        success_url=os.environ.get("STRIPE_SUCCESS_URL", "").strip() or f"{base}/billing/success",
        cancel_url=os.environ.get("STRIPE_CANCEL_URL", "").strip() or f"{base}/billing/cancelled",
        client_reference_id=org.id,           # so the webhook knows which org to activate
        metadata={"org_id": org.id, "plan": plan, "interval": interval},
    )
    return session.url


def parse_webhook(payload: bytes, sig_header: str) -> dict | None:
    """Verify a provider webhook signature and normalise it to
    ``{"type": "activate"|"cancel", "org_id": ...}`` — or None when it can't be verified,
    the SDK is absent, or the event isn't one we act on. Never raises."""
    secret = os.environ.get("STRIPE_WEBHOOK_SECRET", "").strip()
    if not secret:
        return None
    try:
        import stripe
    except ImportError:
        return None
    try:
        event = stripe.Webhook.construct_event(payload, sig_header, secret)
    except Exception:  # noqa: BLE001 — a bad signature is an ignored event, not a 500
        return None
    etype = event.get("type", "")
    obj = (event.get("data") or {}).get("object") or {}
    org_id = obj.get("client_reference_id") or (obj.get("metadata") or {}).get("org_id")
    if not org_id:
        return None
    if etype == "checkout.session.completed":
        # `subscription` is the Stripe subscription id — store it so we can later tell
        # Stripe to stop billing on cancel (otherwise the card keeps being charged).
        # `interval` (from checkout metadata) sets the grant period so a MONTHLY sub isn't
        # granted a year (which the period-end cancel grace would then hand out for free).
        return {
            "type": "activate",
            "org_id": org_id,
            "subscription_id": obj.get("subscription") or "",
            "interval": (obj.get("metadata") or {}).get("interval") or "yearly",
        }
    if etype == "customer.subscription.deleted":
        return {"type": "cancel", "org_id": org_id}
    return None


def cancel_subscription(provider: str, provider_ref: str) -> None:
    """Tell the payment provider to stop billing. Mock has no biller; Play cancellation is
    user-driven in the Play Store; Stripe with no key configured on our side has nothing to
    call — all three are genuine no-ops. A configured Stripe sub needs an API call, and if
    that call FAILS this RAISES: a swallowed failure showed the user 'canceled' while Stripe
    kept charging the card. The caller surfaces the error and leaves the sub active (truthful:
    it still is) rather than reconciling a cancel that never reached Stripe."""
    if provider != "stripe" or not provider_ref:
        return
    key = os.environ.get("STRIPE_SECRET_KEY", "").strip()
    if not key:
        return
    import stripe

    stripe.api_key = key
    # cancel_at_period_end matches our 'keep access until the period ends' semantics;
    # the subsequent customer.subscription.deleted webhook flips status when it lapses.
    stripe.Subscription.modify(provider_ref, cancel_at_period_end=True)


# ── Google Play Billing ──────────────────────────────────────────────────────
# Play is client-buys / server-verifies: the Android app completes the purchase via the
# Play Billing Library and sends the purchase token here; we verify it against the Google
# Play Developer API with a service-account credential, then activate. Inert (returns None
# → the endpoint 503s) until GOOGLE_PLAY_SERVICE_ACCOUNT_JSON + GOOGLE_PLAY_PACKAGE_NAME
# and the google client libraries are present.
def verify_play_purchase(purchase_token: str, product_id: str) -> dict | None:
    """Verify a Play subscription purchase token. Returns
    ``{"valid": bool, "expiry_ms": int|None}`` or None when Play isn't configured / the
    SDK is absent / the call fails. Never raises."""
    creds = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    package = os.environ.get("GOOGLE_PLAY_PACKAGE_NAME", "").strip()
    if not creds or not package:
        return None
    try:
        return _play_purchase_state(creds, package, product_id, purchase_token)
    except Exception:  # noqa: BLE001 — an unverifiable purchase is a 402, not a 500
        return None


def _play_purchase_state(  # pragma: no cover — real Google API call, exercised only in prod
    creds_json: str, package: str, product_id: str, token: str
) -> dict:
    """The actual Google Play Developer API call. Split out so tests stub it without the
    google client libraries installed (they aren't a dependency until Play is wired)."""
    import json as _json

    from google.oauth2 import service_account  # type: ignore
    from googleapiclient.discovery import build  # type: ignore

    scoped = service_account.Credentials.from_service_account_info(
        _json.loads(creds_json),
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    service = build("androidpublisher", "v3", credentials=scoped, cache_discovery=False)
    result = (
        service.purchases()
        .subscriptions()
        .get(packageName=package, subscriptionId=product_id, token=token)
        .execute()
    )
    expiry_ms = int(result.get("expiryTimeMillis") or 0) or None
    valid = result.get("paymentState") in (1, 2)  # 1 = received, 2 = free trial
    return {"valid": valid, "expiry_ms": expiry_ms}
