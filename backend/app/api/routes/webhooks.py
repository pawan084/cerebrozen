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

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.models.user import User
from app.services import appstore

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
