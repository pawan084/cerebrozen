"""App Store Server Notifications V2 webhook.

Keeps subscription entitlement fresh over the whole lifecycle — renewals,
refunds, expiry — server-to-server, with no client involved. Apple POSTs a
signed payload; we verify it (JWS + certificate chain, same as a receipt) and
update the user identified by the transaction's ``appAccountToken`` (which the
app sets to the user's id at purchase time).

Authentication is the Apple signature itself; there is no bearer token. In
production the chain must be pinned to Apple's root (``appstore_root_cert_path``).
"""
import logging
import uuid

from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.models.user import User
from app.models.webhook_event import ProcessedWebhook
from app.services import appstore, stripe_billing

logger = logging.getLogger("cerebro.webhooks")
router = APIRouter(prefix="/webhooks", tags=["webhooks"])


class AppStoreNotification(BaseModel):
    signedPayload: str


@router.post("/appstore")
async def appstore_notifications(payload: AppStoreNotification, db: AsyncSession = Depends(get_db)):
    try:
        note = appstore.verify_notification(payload.signedPayload)
    except appstore.ReceiptError as exc:
        # Return 200 so Apple doesn't retry a permanently-bad payload; log it.
        logger.warning("Rejected App Store notification: %s", exc)
        return {"handled": False, "reason": str(exc)}

    txn = note.get("transaction") or {}
    token = txn.get("appAccountToken")
    if not token:
        return {"handled": False, "reason": "no appAccountToken"}
    try:
        user_id = uuid.UUID(str(token))
    except (ValueError, TypeError):
        return {"handled": False, "reason": "bad appAccountToken"}

    user = await db.get(User, user_id)
    if user is None:
        return {"handled": False, "reason": "unknown user"}

    tier, expires = appstore.tier_from_notification(note)
    user.subscription_tier = tier
    user.subscription_expires_at = expires
    await db.commit()
    logger.info("App Store %s → user %s tier=%s", note.get("notification_type"), user_id, tier)
    return {"handled": True, "tier": tier}


@router.post("/stripe")
async def stripe_webhook(request: Request, db: AsyncSession = Depends(get_db)):
    """Stripe subscription lifecycle → the same entitlement contract as the
    App Store webhook above. Authentication is the Stripe-Signature header
    (HMAC over the raw body); the user rides in as checkout
    client_reference_id / subscription metadata. Returns 200 on bad payloads
    so Stripe doesn't retry garbage forever."""
    payload = await request.body()
    signature = request.headers.get("stripe-signature", "")
    try:
        event = stripe_billing.verify_webhook(payload, signature)
    except stripe_billing.StripeError as exc:
        logger.warning("Rejected Stripe webhook: %s", exc)
        return {"handled": False, "reason": str(exc)}

    entitlement = stripe_billing.entitlement_from_event(event)
    if entitlement is None:
        return {"handled": False, "reason": "ignored event"}
    raw_user_id, tier, expires = entitlement
    customer_id = stripe_billing.customer_id_from_event(event)

    # Idempotency BEFORE any write. Stripe delivers at least once and retries on
    # any non-2xx or timeout, so a replayed `subscription.deleted` arriving after
    # a re-subscribe would silently downgrade a paying customer. The uniqueness
    # is the database's, not this function's — two concurrent deliveries race,
    # and exactly one may win.
    event_id = str(event.get("id") or "")
    if event_id:
        db.add(ProcessedWebhook(provider="stripe", event_id=event_id))
        try:
            await db.flush()
        except IntegrityError:
            await db.rollback()
            logger.info("Stripe webhook %s already processed — ignoring replay", event_id)
            return {"handled": False, "reason": "duplicate"}

    user = None
    try:
        user = await db.get(User, uuid.UUID(str(raw_user_id)))
    except (ValueError, TypeError):
        user = None
    if user is None and customer_id:
        # Subscriptions edited in the Stripe dashboard or the billing portal
        # arrive without our metadata; the stored customer id is how they map.
        user = await db.scalar(select(User).where(User.stripe_customer_id == customer_id))
    if user is None:
        await db.rollback()
        return {"handled": False, "reason": "unknown user"}

    # Remember the customer for events that carry no metadata, and for the
    # billing portal.
    if customer_id and user.stripe_customer_id != customer_id:
        user.stripe_customer_id = customer_id

    user.subscription_tier = tier
    # checkout.session.completed carries no period end — keep any prior expiry
    # until the subscription event lands moments later.
    if expires is not None or tier == "free":
        user.subscription_expires_at = expires
    await db.commit()
    logger.info("Stripe %s → user %s tier=%s", event.get("type"), user.id, tier)
    return {"handled": True, "tier": tier}
