import hashlib
import uuid
from datetime import timezone

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import delete, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db, utcnow
from app.core.deps import get_current_user
from app.models.chat import ChatMessage
from app.models.agent_action import AgentAction
from app.models.consent import Consent, consent_allows
from app.models.deletion_ledger import DeletionLedger
from app.models.device_token import DeviceToken
from app.models.habit import Goal
from app.models.insight import Insight
from app.models.memory import EDITABLE_SOURCES, ContextMemory
from app.models.recommendation import Recommendation
from app.models.safety_plan import SafetyPlan
from app.models.journal import JournalEntry
from app.models.mood import MoodLog
from app.models.nudge import Nudge
from app.models.plan import Plan
from app.models.sleep import SleepLog
from app.models.user import User
from app.schemas.content_data import (
    ChatOut,
    InsightOut,
    JournalOut,
    MemoryCreate,
    MemoryOut,
    MemoryUpdate,
    MoodOut,
    NudgeOut,
    AgentActionOut,
    GoalOut,
    PatternSuppress,
    SafetyPlanOut,
    SleepLogOut,
)
from app.schemas.plan import PlanOut
from app.models.trusted_contact import TrustedContact
from app.models.web_push import WebPushSubscription
from app.schemas.user import (
    AttestBody,
    ConsentSchema,
    ConsentUpdate,
    DeviceTokenIn,
    DeviceTokenOut,
    PushStatusOut,
    PushTokenUpdate,
    SubscriptionVerify,
    TrustedContactIn,
    TrustedContactOut,
    UserOut,
    UserUpdate,
    WebPushStatusOut,
    WebPushSubscriptionIn,
    WebPushSubscriptionOut,
)
from app.core.config import settings
from app.services import appstore, metrics

router = APIRouter(prefix="/users", tags=["users"])


@router.get("/me/streak")
async def my_streak(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    """Server-computed "mindful days" streak (same rules as the iOS local one —
    cross-stack contract; see services/metrics.user_streak)."""
    return await metrics.user_streak(db, user.id)


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
    payload: AttestBody | None = None,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Record the compliance attestations from onboarding: 18+ age confirmation
    and acknowledgement of the AI disclosure. Idempotent — stamps the first time
    each is confirmed and leaves earlier timestamps untouched. An optional
    client-recorded confirmation time is honored only when it's in the past
    (the tap can predate the first connect; the server clock caps it)."""
    now = utcnow()
    if user.age_confirmed_at is None:
        client = payload.age_confirmed_at if payload else None
        if client is not None and client.tzinfo is None:
            client = client.replace(tzinfo=timezone.utc)
        user.age_confirmed_at = min(client, now) if client else now
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

    # Register C2 — the receipt must belong to THIS account. Two independent
    # checks, because either alone has a hole:
    #  1. appAccountToken: the app stamps the purchase with the buyer's user
    #     id. A verified transaction carrying someone else's token is someone
    #     else's purchase, however valid the signature.
    account_token = str(payload_data.get("appAccountToken") or "").strip().lower()
    if account_token and account_token != str(user.id).lower():
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="This receipt was purchased for a different account.",
        )
    #  2. originalTransactionId uniqueness: without it, one signed transaction
    #     (which carries no token on older clients) granted premium on
    #     unlimited accounts. First verifier owns the subscription.
    original_txn = str(payload_data.get("originalTransactionId") or "").strip()
    if original_txn:
        owner = await db.scalar(
            select(User).where(
                User.apple_original_transaction_id == original_txn,
                User.id != user.id,
            )
        )
        if owner is not None:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="This subscription is already linked to another account.",
            )
        user.apple_original_transaction_id = original_txn

    tier, expires = appstore.tier_for(payload_data)
    user.subscription_tier = tier
    user.subscription_expires_at = expires
    await db.commit()
    await db.refresh(user)
    return user


@router.get("/me/trusted-contact", response_model=TrustedContactOut | None)
async def get_trusted_contact(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    return await db.scalar(select(TrustedContact).where(TrustedContact.user_id == user.id))


@router.put("/me/trusted-contact", response_model=TrustedContactOut)
async def upsert_trusted_contact(
    payload: TrustedContactIn,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Save the person to notify if a crisis is detected (one per user). Automatic
    escalation only fires when ``notify_consent`` is true."""
    contact = await db.scalar(select(TrustedContact).where(TrustedContact.user_id == user.id))
    if contact is None:
        contact = TrustedContact(user_id=user.id)
        db.add(contact)
    for field, value in payload.model_dump().items():
        setattr(contact, field, value)
    await db.commit()
    await db.refresh(contact)
    return contact


@router.delete("/me/trusted-contact", status_code=status.HTTP_204_NO_CONTENT)
async def delete_trusted_contact(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    contact = await db.scalar(select(TrustedContact).where(TrustedContact.user_id == user.id))
    if contact is not None:
        await db.delete(contact)
        await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


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
        # Portability: a table the user can see in-app must appear here too.
        "memory": await rows(ContextMemory, MemoryOut, ContextMemory.created_at),
        # Every version, not just the live one — the history is the record.
        "safety_plans": await rows(SafetyPlan, SafetyPlanOut, SafetyPlan.version),
        "goals": await rows(Goal, GoalOut, Goal.created_at),
        "agent_actions": await rows(AgentAction, AgentActionOut, AgentAction.created_at),
        "sleep": await rows(SleepLog, SleepLogOut, SleepLog.date),
        "push_subscriptions": await rows(
            WebPushSubscription, WebPushSubscriptionOut, WebPushSubscription.created_at
        ),
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

    DPDP Rule 8(3): a minimal DeletionLedger row (hashed email, account age —
    no content) survives in the same transaction for the 12-month log-retention
    obligation, then gets purged by ops.
    """
    db.add(DeletionLedger(
        email_hash=hashlib.sha256(user.email.lower().encode()).hexdigest(),
        account_created_at=user.created_at,
    ))
    await db.execute(delete(User).where(User.id == user.id))
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.delete("/me/memory")
async def delete_my_memory(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Wipe what the AI remembers about the user: conversation history,
    computed insights, and the Oracle's durable thread state. Journals, moods
    and the sleep diary are the user's own content with their own controls —
    deliberately untouched here (the Pattern Dashboard's "Delete all memory").
    """
    chat = await db.execute(delete(ChatMessage).where(ChatMessage.user_id == user.id))
    ins = await db.execute(delete(Insight).where(Insight.user_id == user.id))
    # Per-item memories are exactly "what the AI remembers", so a wipe that
    # left them would make this button a lie on all three clients.
    mem = await db.execute(
        delete(ContextMemory).where(ContextMemory.user_id == user.id)
    )
    # The agent's record of what it proposed writing to you is part of "what
    # the AI remembers" — leaving it would make the button a half-truth.
    acts = await db.execute(
        delete(AgentAction).where(AgentAction.user_id == user.id)
    )
    # LangGraph checkpoint tables exist once the Postgres saver initialised;
    # thread ids embed the user id (str(user.id) / "web-<id>" / client prefixes).
    from sqlalchemy import text as sql_text
    threads_cleared = False
    for table in ("checkpoint_writes", "checkpoint_blobs", "checkpoints"):
        exists = await db.scalar(sql_text(f"SELECT to_regclass('{table}')"))
        if exists:
            await db.execute(
                sql_text(f"DELETE FROM {table} WHERE thread_id LIKE :tid"),  # noqa: S608 - table name from a fixed tuple
                {"tid": f"%{user.id}%"},
            )
            threads_cleared = True
    await db.commit()
    return {
        "chat_messages": chat.rowcount or 0,
        "insights": ins.rowcount or 0,
        "memories": mem.rowcount or 0,
        "agent_actions": acts.rowcount or 0,
        "oracle_threads_cleared": threads_cleared,
    }


# ── Per-item memory ─────────────────────────────────────────────────────
# The Pattern Dashboard has always told the user "you can edit or delete any of
# it". Until these routes existed the only control was the all-or-nothing wipe
# above, because mined patterns are computed on the fly and had no row to point
# at. See app/models/memory.py for why inferences are still never persisted.

def _memory_allowed(user: User) -> None:
    """Memory is the `ai_memory` consent category. Switched off, the store is
    not merely hidden — it must not be read or written."""
    if not consent_allows(user, "ai_memory"):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="AI memory is switched off in your privacy settings.",
        )


async def _owned_memory(db: AsyncSession, user: User, memory_id: uuid.UUID) -> ContextMemory:
    row = await db.get(ContextMemory, memory_id)
    # 404 rather than 403 on someone else's row: whether an id exists is not
    # something another account should be able to probe.
    if row is None or row.user_id != user.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not found")
    return row


@router.get("/me/memory", response_model=list[MemoryOut])
async def list_my_memory(
    include_dismissed: bool = False,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Everything remembered about this user, newest first.

    Suppression tombstones are internal bookkeeping, never shown as memories.
    Expired rows are filtered on read so a lapsed item stops being recalled
    even before any sweep removes it.
    """
    stmt = select(ContextMemory).where(
        ContextMemory.user_id == user.id,
        ContextMemory.source != "suppressed_pattern",
    )
    if not include_dismissed:
        stmt = stmt.where(ContextMemory.dismissed_at.is_(None))
    rows = (await db.scalars(stmt.order_by(ContextMemory.created_at.desc()))).all()
    now = utcnow()
    return [r for r in rows if r.expires_at is None or r.expires_at > now]


@router.post("/me/memory", response_model=MemoryOut, status_code=status.HTTP_201_CREATED)
async def create_my_memory(
    payload: MemoryCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Add something to remember. `source` is clamped to the user-authored
    kinds — nothing can post itself in as an inference."""
    _memory_allowed(user)
    source = payload.source if payload.source in EDITABLE_SOURCES else "manual"
    row = ContextMemory(
        user_id=user.id, body=payload.body.strip(),
        source=source, salience=payload.salience,
    )
    db.add(row)
    await db.commit()
    await db.refresh(row)
    return row


@router.patch("/me/memory/{memory_id}", response_model=MemoryOut)
async def update_my_memory(
    memory_id: uuid.UUID,
    payload: MemoryUpdate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    _memory_allowed(user)
    row = await _owned_memory(db, user, memory_id)
    if payload.body is not None:
        # A suppression tombstone is not the user's prose; rewriting its body
        # would silently re-point which pattern is hidden.
        if row.source not in EDITABLE_SOURCES:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="This entry can be removed but not rewritten.",
            )
        row.body = payload.body.strip()
    if payload.salience is not None:
        row.salience = payload.salience
    if payload.dismissed is not None:
        row.dismissed_at = utcnow() if payload.dismissed else None
    row.updated_at = utcnow()
    await db.commit()
    await db.refresh(row)
    return row


@router.delete("/me/memory/{memory_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_one_memory(
    memory_id: uuid.UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Delete one remembered item. Deliberately NOT consent-gated: switching
    `ai_memory` off must never trap data the user is trying to remove."""
    row = await _owned_memory(db, user, memory_id)
    await db.delete(row)
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/me/memory/suppress-pattern", status_code=status.HTTP_204_NO_CONTENT)
async def suppress_pattern(
    payload: PatternSuppress,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Stop showing one computed pattern.

    Patterns are derived per request and have no id, so the statement itself is
    the key. Writing a tombstone (rather than persisting the pattern) keeps the
    rule intact that the product never stores its own guesses as user facts.
    Idempotent — suppressing twice is not an error.

    Deliberately NOT behind `_memory_allowed`: that gate protects *remembering*,
    and a suppression is the opposite — it narrows what the AI may use. Patterns
    are computed from mood/journal/sleep data, so they render for users who
    granted those categories but never ai_memory; gating Hide on ai_memory would
    403 exactly the person trying to reduce what we hold. Same principle as
    deletion ("consent off must never trap data"), one step earlier.
    """
    statement = payload.statement.strip()
    existing = await db.scalar(
        select(ContextMemory).where(
            ContextMemory.user_id == user.id,
            ContextMemory.source == "suppressed_pattern",
            ContextMemory.body == statement,
        )
    )
    if existing is None:
        db.add(ContextMemory(
            user_id=user.id, body=statement,
            source="suppressed_pattern", salience=0.0, dismissed_at=utcnow(),
        ))

    # Retract any PENDING suggestion that was derived from this pattern.
    #
    # The client tells the user "hide one and it stops being shown or used", and
    # `compute_patterns` honours the tombstone so no NEW recommendation is ever
    # seeded from it. But one seeded earlier keeps its `reason` — the pattern
    # statement, verbatim — and the dashboard renders it as "Because: …". Seen on
    # a device: the pattern vanished from the top of the screen and went on
    # justifying a live suggestion halfway down it.
    #
    # Only pending ones. A practice the user already accepted is theirs now, and
    # withdrawing it because they tidied away the observation behind it would be
    # taking something they chose.
    pending = (
        await db.scalars(
            select(Recommendation).where(
                Recommendation.user_id == user.id,
                Recommendation.reason == statement,
                Recommendation.status == "pending",
            )
        )
    ).all()
    for row in pending:
        row.status = "dismissed"
        row.resolved_at = utcnow()

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


@router.get("/me/devices", response_model=PushStatusOut)
async def device_push_status(
    # Closed to the platforms that exist (register C30) — anything else used
    # to be answered with the APNs flag, so `?platform=windows` reported iOS
    # delivery status.
    platform: str = Query("android", pattern="^(ios|android)$"),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Whether push can actually be delivered to this platform, plus the number
    of live installs. The client asks before offering a notifications toggle —
    a switch that silently does nothing is worse than an honest explanation."""
    # Scoped to the platform being asked about: `enabled` answers for ONE
    # provider, so the install count must too — an iPhone-only account asking
    # about Android would otherwise read "delivery off, 1 device registered".
    rows = (
        await db.scalars(
            select(DeviceToken).where(
                DeviceToken.user_id == user.id,
                DeviceToken.platform == platform,
                DeviceToken.failed_at.is_(None),
            )
        )
    ).all()
    enabled = settings.fcm_enabled if platform == "android" else settings.apns_enabled
    return PushStatusOut(enabled=enabled, platform=platform, devices=len(rows))


@router.post("/me/devices", response_model=DeviceTokenOut, status_code=201)
async def register_device(
    payload: DeviceTokenIn,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Register (or refresh) this install's push token.

    Called on every cold start, not just once: FCM and APNs both rotate tokens
    silently, and a token that rotated without us hearing about it is an install
    that stops getting nudges with nothing in any log to say so. Re-registering
    is therefore cheap and idempotent — the row is adopted, `last_seen_at` moves
    forward, and a token previously marked dead is revived (a reinstall reuses
    neither the token nor the problem, but a transient failure can be cleared).
    """
    row = await db.scalar(select(DeviceToken).where(DeviceToken.token == payload.token))
    if row is None:
        row = DeviceToken(token=payload.token)
        db.add(row)
    row.user_id = user.id
    row.platform = payload.platform
    row.app_version = payload.app_version
    row.last_seen_at = utcnow()
    row.failed_at = None
    try:
        await db.commit()
    except IntegrityError:
        # Register C52: two concurrent cold-start registrations of the same
        # token both found no row; the loser's unique-violation 500'd. Adopt
        # the row the winner created instead.
        await db.rollback()
        row = await db.scalar(select(DeviceToken).where(DeviceToken.token == payload.token))
        if row is None:  # deleted in the same instant — genuinely gone
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Try again")
        row.user_id = user.id
        row.platform = payload.platform
        row.app_version = payload.app_version
        row.last_seen_at = utcnow()
        row.failed_at = None
        await db.commit()
    await db.refresh(row)
    return row


@router.delete("/me/devices", status_code=status.HTTP_204_NO_CONTENT)
async def unregister_device(
    token: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Drop this install's token — sign-out, or the user turning notifications
    off. Scoped to the caller, so one account cannot unregister another's."""
    row = await db.scalar(
        select(DeviceToken).where(
            DeviceToken.token == token, DeviceToken.user_id == user.id
        )
    )
    if row is not None:
        await db.delete(row)
        await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/me/push-subscriptions", response_model=WebPushStatusOut)
async def web_push_status(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Whether the server can deliver Web Push (VAPID configured) + the public
    application server key the browser subscribes with. `enabled: false` lets
    the web client degrade honestly instead of offering a dead toggle."""
    count = len(
        (await db.scalars(select(WebPushSubscription).where(WebPushSubscription.user_id == user.id))).all()
    )
    return WebPushStatusOut(
        enabled=settings.webpush_enabled,
        public_key=settings.vapid_public_key,
        subscriptions=count,
    )


@router.post("/me/push-subscriptions", response_model=WebPushSubscriptionOut)
async def register_web_push(
    payload: WebPushSubscriptionIn,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Register (or refresh) this browser's push subscription. Endpoints are
    globally unique, so an endpoint registered from a different account adopts
    the row — a shared browser only notifies whoever subscribed last."""
    sub = await db.scalar(
        select(WebPushSubscription).where(WebPushSubscription.endpoint == payload.endpoint)
    )
    if sub is None:
        sub = WebPushSubscription(endpoint=payload.endpoint)
        db.add(sub)
    sub.user_id = user.id
    sub.p256dh = payload.p256dh
    sub.auth = payload.auth
    try:
        await db.commit()
    except IntegrityError:
        # Register C52: concurrent subscribes of the same endpoint — adopt
        # the winner's row rather than 500ing on the unique violation.
        await db.rollback()
        sub = await db.scalar(
            select(WebPushSubscription).where(WebPushSubscription.endpoint == payload.endpoint)
        )
        if sub is None:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Try again")
        sub.user_id = user.id
        sub.p256dh = payload.p256dh
        sub.auth = payload.auth
        await db.commit()
    await db.refresh(sub)
    return sub


@router.delete("/me/push-subscriptions", status_code=status.HTTP_204_NO_CONTENT)
async def unregister_web_push(
    endpoint: str,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    sub = await db.scalar(
        select(WebPushSubscription).where(
            WebPushSubscription.endpoint == endpoint,
            WebPushSubscription.user_id == user.id,
        )
    )
    if sub is not None:
        await db.delete(sub)
        await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
