from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db, utcnow
from app.core.deps import get_current_user
from app.models.chat import ChatMessage
from app.models.consent import Consent
from app.models.insight import Insight
from app.models.journal import JournalEntry
from app.models.mood import MoodLog
from app.models.nudge import Nudge
from app.models.plan import Plan
from app.models.user import User
from app.schemas.content_data import (
    ChatOut,
    InsightOut,
    JournalOut,
    MoodOut,
    NudgeOut,
)
from app.schemas.plan import PlanOut
from app.schemas.user import (
    ConsentSchema,
    ConsentUpdate,
    PushTokenUpdate,
    SubscriptionVerify,
    UserOut,
    UserUpdate,
)
from app.services import appstore

router = APIRouter(prefix="/users", tags=["users"])


@router.get("/me", response_model=UserOut)
async def get_me(user: User = Depends(get_current_user)):
    return user


@router.patch("/me", response_model=UserOut)
async def update_me(
    payload: UserUpdate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    for field, value in payload.model_dump(exclude_unset=True).items():
        setattr(user, field, value)
    await db.commit()
    await db.refresh(user)
    return user


@router.post("/me/attest", response_model=UserOut)
async def attest(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Record the compliance attestations from onboarding: 18+ age confirmation
    and acknowledgement of the AI disclosure. Idempotent — stamps the first time
    each is confirmed and leaves earlier timestamps untouched."""
    now = utcnow()
    if user.age_confirmed_at is None:
        user.age_confirmed_at = now
    if user.ai_disclosure_ack_at is None:
        user.ai_disclosure_ack_at = now
    await db.commit()
    await db.refresh(user)
    return user


@router.post("/me/subscription/verify", response_model=UserOut)
async def verify_subscription(
    payload: SubscriptionVerify,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Verify a StoreKit 2 signed transaction and set the authoritative tier.

    The server — not the client — decides entitlement: a bad/expired/revoked
    receipt resolves to ``free``. This is what unlocks the usage quota.
    """
    try:
        payload_data = appstore.verify_transaction(payload.signed_transaction)
    except appstore.ReceiptError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Invalid receipt: {exc}")
    tier, expires = appstore.tier_for(payload_data)
    user.subscription_tier = tier
    user.subscription_expires_at = expires
    await db.commit()
    await db.refresh(user)
    return user


@router.get("/me/consent", response_model=ConsentSchema)
async def get_consent(user: User = Depends(get_current_user)):
    return user.consent or Consent()


@router.patch("/me/consent", response_model=ConsentSchema)
async def update_consent(
    payload: ConsentUpdate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if user.consent is None:
        user.consent = Consent()
    for field, value in payload.model_dump(exclude_unset=True).items():
        setattr(user.consent, field, value)
    await db.commit()
    await db.refresh(user)
    return user.consent


@router.get("/me/export")
async def export_my_data(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Return a complete copy of the user's data (GDPR-style portability).

    Everything is fetched fresh and serialized through the public schemas, so
    the export mirrors exactly what the API would return for each resource.
    """
    async def rows(model, schema, order=None):
        stmt = select(model).where(model.user_id == user.id)
        if order is not None:
            stmt = stmt.order_by(order)
        result = (await db.scalars(stmt)).all()
        return [schema.model_validate(r).model_dump(mode="json") for r in result]

    return {
        "exported_at": utcnow().isoformat(),
        "profile": UserOut.model_validate(user).model_dump(mode="json"),
        "moods": await rows(MoodLog, MoodOut, MoodLog.created_at),
        "journal": await rows(JournalEntry, JournalOut, JournalEntry.created_at),
        "chat": await rows(ChatMessage, ChatOut, ChatMessage.created_at),
        "plans": await rows(Plan, PlanOut, Plan.created_at),
        "nudges": await rows(Nudge, NudgeOut, Nudge.scheduled_for),
        "insights": await rows(Insight, InsightOut),
    }


@router.delete("/me", status_code=status.HTTP_204_NO_CONTENT)
async def delete_my_account(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Permanently delete the account and ALL associated data.

    Every user-scoped table declares ``ondelete="CASCADE"``, so a single delete
    on the user row cascades in Postgres to moods, journal, chat, plans (+steps),
    consent, safety events, nudges, and insights. Required by App Store 5.1.1(v).
    """
    await db.execute(delete(User).where(User.id == user.id))
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.put("/me/push-token", response_model=UserOut)
async def set_push_token(
    payload: PushTokenUpdate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    user.push_token = payload.push_token
    await db.commit()
    await db.refresh(user)
    return user
