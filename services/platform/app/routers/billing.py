"""Consumer billing (B2C freemium).

Reads the current account's plan and entitlements, and runs checkout. Checkout is
provider-abstracted: with `config.BILLING_MOCK` (the default, keyless) a purchase
completes synchronously in-process; a real Stripe / Play Billing integration slots
in behind the same routes and activates on a webhook — the end state is identical.

Only PERSONAL accounts have a consumer plan to buy. A B2B seat is billed through
its organization's enterprise subscription and has nothing to purchase here."""

import uuid
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession
from starlette.concurrency import run_in_threadpool

from app import billing_providers, config
from app.db import get_session
from app.deps import current_user
from app.routers.auth import _issue_pair
from app.security import hash_opaque
from app.models import (
    ACTIVE_SUB_STATUSES,
    PLAN_PLUS,
    SUB_STATUS_ACTIVE,
    SUB_STATUS_CANCELED,
    Org,
    Subscription,
    User,
    entitlements_for,
    is_personal_org,
    resolve_plan,
)

router = APIRouter(prefix="/billing", tags=["billing"])

# Published consumer prices, in minor units (cents). The marketing site and the
# app read this so a price shows in exactly one place. Plus only, for now.
PRICES = {
    "plus": {"currency": "usd", "yearly": 5999, "monthly": 999},
}


class PlanOut(BaseModel):
    plan: str
    status: str | None = None
    provider: str | None = None
    current_period_end: datetime | None = None
    entitlements: dict


class CheckoutIn(BaseModel):
    plan: str = "plus"
    interval: str = "yearly"  # yearly | monthly


async def _org_and_sub(session: AsyncSession, user: User):
    org = await session.get(Org, user.org_id) if user.org_id else None
    sub = None
    if user.org_id:
        sub = (
            await session.execute(
                select(Subscription).where(Subscription.org_id == user.org_id)
            )
        ).scalar_one_or_none()
    return org, sub


def _plan_out(org, sub) -> PlanOut:
    plan = resolve_plan(org, sub)
    return PlanOut(
        plan=plan,
        status=sub.status if sub else None,
        provider=sub.provider if sub else None,
        current_period_end=sub.current_period_end if sub else None,
        entitlements=entitlements_for(plan),
    )


async def _plan_out_rotated(session: AsyncSession, user: User, org, sub) -> dict:
    """The plan PLUS a fresh token pair carrying the new plan claim — so the engine's
    plan gate (and the free coaching cap) see the change on the very next request instead
    of after the ≤15-min token TTL (the post-purchase lockout / post-cancel over-grant).
    Mirrors the consent flow's rotation. Other devices are NOT signed out — they pick up
    the new plan on their next refresh. The client adopts these tokens."""
    data = _plan_out(org, sub).model_dump(mode="json")
    pair = await _issue_pair(session, user)
    data["access_token"] = pair.access_token
    data["refresh_token"] = pair.refresh_token
    return data


@router.get("/prices")
async def prices():
    """Published consumer prices — the single source of truth the app and the
    marketing site read, so a price lives in exactly one place. Public (no auth)."""
    return PRICES


@router.get("/me", response_model=PlanOut)
async def my_plan(
    user: User = Depends(current_user), session: AsyncSession = Depends(get_session)
):
    org, sub = await _org_and_sub(session, user)
    return _plan_out(org, sub)


@router.post("/checkout", status_code=201)
async def checkout(
    body: CheckoutIn,
    user: User = Depends(current_user),
    session: AsyncSession = Depends(get_session),
):
    org, sub = await _org_and_sub(session, user)
    if org is None or not is_personal_org(org):
        raise HTTPException(409, "this account is billed through your organization")
    if body.plan != PLAN_PLUS:
        raise HTTPException(400, "unknown plan")
    if body.interval not in ("yearly", "monthly"):
        raise HTTPException(400, "interval must be 'yearly' or 'monthly'")
    # Don't start a second checkout for an already-active plan — with a real provider
    # that double-bills the same card (two live subscriptions).
    if sub is not None and sub.status in ACTIVE_SUB_STATUSES:
        raise HTTPException(409, "you already have an active CereBro Plus subscription")

    mode, checkout_url = billing_providers.begin_checkout(org, body.plan, body.interval)
    if mode == "redirect":
        # Hosted checkout (Stripe): the subscription activates on the webhook, so the
        # client redirects the user here and polls /billing/me on return.
        return {"checkout_url": checkout_url}

    # mode == "activate" (mock provider): a completed purchase, in-process.
    now = datetime.now(timezone.utc)
    period = timedelta(days=365 if body.interval == "yearly" else 30)
    if sub is None:
        sub = Subscription(org_id=org.id)
        session.add(sub)
    sub.plan = PLAN_PLUS
    sub.status = SUB_STATUS_ACTIVE
    sub.provider = "mock"
    sub.provider_ref = f"mock_{uuid.uuid4().hex[:12]}"
    sub.current_period_end = now + period
    sub.updated_at = now
    await session.commit()
    return await _plan_out_rotated(session, user, org, sub)


@router.post("/cancel")
async def cancel(
    user: User = Depends(current_user), session: AsyncSession = Depends(get_session)
):
    org, sub = await _org_and_sub(session, user)
    if sub is None or sub.status not in ACTIVE_SUB_STATUSES:
        raise HTTPException(404, "no active subscription")
    # Tell the payment provider to stop billing (Stripe API call; mock/play are no-ops).
    # Off the event loop since it does network I/O when Stripe is live. If it FAILS we stop
    # here: marking the sub canceled locally while Stripe keeps charging the card would show
    # the user a cancel that never happened. Leave it active (it still is) and let them retry.
    try:
        await run_in_threadpool(
            billing_providers.cancel_subscription, sub.provider, sub.provider_ref
        )
    except Exception:  # noqa: BLE001 — any provider-side failure means the cancel didn't land
        raise HTTPException(502, "could not reach the payment provider — please try again")
    # Cancel = "won't renew". A real provider keeps the paid plan until
    # current_period_end, then a webhook flips status (resolve_plan honours that
    # window). The mock provider has no billing cycle to wait on, so it ends the
    # period immediately — the account drops to free right away.
    sub.status = SUB_STATUS_CANCELED
    if config.BILLING_MOCK:
        sub.current_period_end = datetime.now(timezone.utc)
    sub.updated_at = datetime.now(timezone.utc)
    await session.commit()
    return await _plan_out_rotated(session, user, org, sub)


class PlayVerifyIn(BaseModel):
    purchase_token: str
    product_id: str


@router.post("/play/verify", status_code=201)
async def play_verify(
    body: PlayVerifyIn,
    user: User = Depends(current_user),
    session: AsyncSession = Depends(get_session),
):
    """Verify a Google Play purchase the app just completed, and activate Plus. Play is
    client-buys/server-verifies: the app sends the purchase token, we confirm it with
    Google, then flip the subscription on. 503 until Play is configured; 402 if Google
    says the purchase isn't valid."""
    org, sub = await _org_and_sub(session, user)
    if org is None or not is_personal_org(org):
        raise HTTPException(409, "this account is billed through your organization")
    state = billing_providers.verify_play_purchase(body.purchase_token, body.product_id)
    if state is None:
        raise HTTPException(503, "Play billing is not configured")
    if not state.get("valid"):
        raise HTTPException(402, "this purchase could not be verified")
    # Bind the purchase token to ONE account: a single real purchase yields a token that
    # Google keeps answering "valid" for, so without this any number of accounts could
    # replay it to get Plus. Store its hash and reject if another org already holds it.
    token_ref = hash_opaque(body.purchase_token)
    clash = (
        await session.execute(
            select(Subscription).where(
                Subscription.provider_ref == token_ref, Subscription.org_id != org.id
            )
        )
    ).scalar_one_or_none()
    if clash is not None:
        raise HTTPException(409, "this purchase is already linked to another account")
    now = datetime.now(timezone.utc)
    if sub is None:
        sub = Subscription(org_id=org.id)
        session.add(sub)
    sub.plan = PLAN_PLUS
    sub.status = SUB_STATUS_ACTIVE
    sub.provider = "play"
    sub.provider_ref = token_ref
    expiry_ms = state.get("expiry_ms")
    sub.current_period_end = (
        datetime.fromtimestamp(expiry_ms / 1000, tz=timezone.utc)
        if expiry_ms
        else now + timedelta(days=365)
    )
    sub.updated_at = now
    try:
        await session.commit()
    except IntegrityError:  # pragma: no cover — only under a real concurrent double-submit
        # Lost the race: another account committed this exact purchase token first (the SELECT
        # above is TOCTOU — two concurrent requests can both pass it). The partial unique index
        # on provider_ref is the backstop; turn its violation into the same clean 409 the
        # sequential path returns, never a 500.
        await session.rollback()
        raise HTTPException(409, "this purchase is already linked to another account")
    return await _plan_out_rotated(session, user, org, sub)


@router.post("/webhook")
async def webhook(request: Request, session: AsyncSession = Depends(get_session)):
    """Provider callback (Stripe). The signature is verified in billing_providers;
    unverifiable or irrelevant events are IGNORED with 200 rather than errored, so a
    misconfigured, replayed, or hostile hook can never take billing down. This is where
    a hosted-checkout subscription actually goes active."""
    event = billing_providers.parse_webhook(
        await request.body(), request.headers.get("stripe-signature", "")
    )
    if event is None:
        return {"ok": True}
    org_id = event["org_id"]
    sub = (
        await session.execute(select(Subscription).where(Subscription.org_id == org_id))
    ).scalar_one_or_none()
    now = datetime.now(timezone.utc)
    if event["type"] == "activate":
        if sub is None:
            sub = Subscription(org_id=org_id)
            session.add(sub)
        sub.plan = PLAN_PLUS
        sub.status = SUB_STATUS_ACTIVE
        sub.provider = "stripe"
        sub.provider_ref = event.get("subscription_id") or sub.provider_ref or ""
        # Period from the interval, not a constant — so a monthly sub isn't granted a year.
        # (A customer.subscription.updated handler would refine this from Stripe's real
        # period end; until then this bounds the cancel-grace correctly.)
        period_days = 30 if event.get("interval") == "monthly" else 365
        sub.current_period_end = now + timedelta(days=period_days)
        sub.updated_at = now
        await session.commit()
    elif event["type"] == "cancel" and sub is not None:
        # `subscription.deleted` means the plan has ENDED (not "will end at period
        # close" — that's a different event we don't act on), so access stops now.
        sub.status = SUB_STATUS_CANCELED
        sub.current_period_end = now
        sub.updated_at = now
        await session.commit()
    return {"ok": True}
