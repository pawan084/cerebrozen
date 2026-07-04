"""Web billing (Stripe Checkout) for the browser client (apps/app).

Code-complete but inert until STRIPE_* env vars are set — the endpoint 503s
cleanly so the web app can show an honest "not available yet" message. iOS
purchases stay on StoreKit (App Store rule 3.1.1); both paths converge on the
same ``subscription_tier`` contract.
"""
import logging

from fastapi import APIRouter, Depends, HTTPException, Request, status
from pydantic import BaseModel, Field

from app.core.config import settings
from app.core.deps import get_current_user
from app.core.ratelimit import limiter
from app.models.user import User
from app.services import stripe_billing

logger = logging.getLogger("cerebro.billing")
router = APIRouter(prefix="/billing", tags=["billing"])


class CheckoutBody(BaseModel):
    tier: str = Field(default="premium", pattern=r"^(premium|premium_human)$")
    annual: bool = False


@router.post("/checkout")
@limiter.limit("10/minute")
async def create_checkout(request: Request, payload: CheckoutBody,
                          user: User = Depends(get_current_user)):
    """Start a Stripe Checkout session; the browser redirects to the URL."""
    if not settings.stripe_enabled:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                            detail="Web billing isn't available yet — subscriptions live in the iOS app for now.")
    try:
        url = await stripe_billing.create_checkout_session(str(user.id), payload.tier, payload.annual)
    except stripe_billing.StripeError as exc:
        logger.warning("Checkout failed for %s: %s", user.id, exc)
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY,
                            detail="Couldn't start checkout. Try again shortly.")
    return {"url": url}
