from fastapi import APIRouter, Depends, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.core.ratelimit import limiter
from app.models.chat import ChatMessage
from app.models.consent import consent_allows
from app.models.user import User
from app.schemas.content_data import ChatOut, ChatReply, ChatSend
from app.services import activities, ai, crisis, language, safety, usage

router = APIRouter(prefix="/chat", tags=["chat"])

_HISTORY_LIMIT = 50

# Both personas: never state hotline numbers — the model defaults to US lines
# (it told an India-region user to call 1-800-273-TALK on 2026-08-03), and the
# platform appends region-correct resources itself (crisis.reply_suffix).
_NO_HOTLINES = (
    " If the user mentions self-harm or suicide, respond with warmth and take it "
    "seriously, but do not name hotline numbers or crisis services — the platform "
    "attaches the correct local resources itself."
)
_CALM_GUIDE = (
    "You are CereBro, a warm, calm wellness companion. Reflect feelings, ask one "
    "gentle question at a time, and suggest small grounding steps. You are NOT a "
    "therapist and never diagnose or prescribe. Keep replies to 1–3 short sentences."
    + _NO_HOTLINES
)
_SCIENTIFIC = (
    "You are CereBro, a clear, evidence-informed wellness companion. Offer structured, "
    "CBT-style reflections and one concrete next step. You are NOT a therapist and never "
    "diagnose or prescribe. Keep replies to 1–3 short sentences."
    + _NO_HOTLINES
)

_FALLBACKS = [
    "That sounds heavy. What part of it feels loudest right now?",
    "Thank you for naming that. Want to calm the body first, or unpack the thought?",
    "I'm here with you. Could we try one slow breath together before we continue?",
    "I hear you. If you had to put it in one sentence, what's underneath it?",
    "That's a lot to carry. What would feel like even a small relief right now?",
    "Okay — we don't have to solve it. What does it feel like in your body?",
]


def _fallback_reply(text: str) -> str:
    # Deterministic but varied: keyless regulars were cycling three lines by
    # bare length; folding the first character spreads adjacent messages.
    seed = len(text) + (ord(text[0]) if text else 0)
    return _FALLBACKS[seed % len(_FALLBACKS)]


@router.get("", response_model=list[ChatOut])
async def history(
    limit: int = 200,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    # Newest N, returned oldest-first — every open used to pull up to 200
    # messages so the client could render twelve.
    rows = await db.scalars(
        select(ChatMessage)
        .where(ChatMessage.user_id == user.id)
        .order_by(ChatMessage.created_at.desc())
        .limit(max(1, min(limit, 200)))
    )
    return list(reversed(rows.all()))


@router.post("/messages", response_model=ChatReply, status_code=201)
@limiter.limit("30/minute")   # IP-level cap on top of the per-account daily quota
async def send_message(
    request: Request,
    payload: ChatSend,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    await usage.enforce_quota(db, user)   # free-tier daily cap (429 when exceeded)
    # The message is flushed first so the safety event can point at it
    # (register C71: `source_id=None` meant the admin queue could name the
    # risk but never the message it came from). Safety still never blocks —
    # the scan only classifies and records.
    user_msg = ChatMessage(user_id=user.id, role="user", text=payload.text)
    db.add(user_msg)
    await db.flush()
    risk = await safety.scan_and_record(
        db, user_id=user.id, source="chat", source_id=user_msg.id, text=payload.text
    )
    user_msg.risk_level = risk

    # Build short context from recent history — but only if the user consents to
    # AI memory. With memory off we pass just the current turn (no long-term
    # recall), honoring the "control what CereBro remembers" promise server-side.
    # consent_allows, not a hand-rolled check: this read the same flag with the
    # opposite default for a missing row, so "no recorded decision" silently
    # enabled long-term recall on the one surface the promise is about.
    memory_on = consent_allows(user, "ai_memory")
    if memory_on:
        recent = (
            await db.scalars(
                select(ChatMessage)
                .where(ChatMessage.user_id == user.id)
                .order_by(ChatMessage.created_at.desc())
                .limit(_HISTORY_LIMIT)
            )
        ).all()
        transcript = "\n".join(f"{m.role}: {m.text}" for m in reversed(recent))
    else:
        transcript = f"user: {payload.text}"

    # The activity card is chosen from the USER's message BEFORE the reply is
    # generated, and the model is told it exists — so it can never say "I can't
    # start an exercise" above a working exercise card (the reference app hit
    # exactly that contradiction and fixed it with this ordering + hint).
    widget, suggestions = activities.route(payload.text, risk)

    system = _SCIENTIFIC if user.companion == "Scientific" else _CALM_GUIDE
    # Onboarding asks which language the user wants; until 2026-07-30 only
    # starter-topic generation read the answer.
    system += language.for_user(user)
    if widget is not None:
        system += (
            f" A '{widget.title}' activity card appears directly under your reply; "
            "you may point at it in at most one short sentence, and you must never "
            "say you cannot start, play or run activities."
        )
    reply_text = await ai.complete(system, transcript, max_tokens=200) or _fallback_reply(payload.text)

    if risk == "crisis":
        # Locale-correct hotlines for the user's region (never a hardcoded country).
        reply_text = f"{reply_text}{crisis.reply_suffix(user.region)}"

    reply = ChatMessage(user_id=user.id, role="assistant", text=reply_text)
    db.add(reply)
    await db.commit()
    await db.refresh(user_msg)
    await db.refresh(reply)

    return ChatReply(user_message=user_msg, reply=reply, widget=widget, suggestions=suggestions)
