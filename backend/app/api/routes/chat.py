from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.chat import ChatMessage
from app.models.user import User
from app.schemas.content_data import ChatOut, ChatReply, ChatSend
from app.services import activities, ai, crisis, safety, usage

router = APIRouter(prefix="/chat", tags=["chat"])

_HISTORY_LIMIT = 50

_CALM_GUIDE = (
    "You are CereBro, a warm, calm wellness companion. Reflect feelings, ask one "
    "gentle question at a time, and suggest small grounding steps. You are NOT a "
    "therapist and never diagnose or prescribe. Keep replies to 1–3 short sentences."
)
_SCIENTIFIC = (
    "You are CereBro, a clear, evidence-informed wellness companion. Offer structured, "
    "CBT-style reflections and one concrete next step. You are NOT a therapist and never "
    "diagnose or prescribe. Keep replies to 1–3 short sentences."
)

_FALLBACKS = [
    "That sounds heavy. What part of it feels loudest right now?",
    "Thank you for naming that. Want to calm the body first, or unpack the thought?",
    "I'm here with you. Could we try one slow breath together before we continue?",
]


def _fallback_reply(text: str) -> str:
    return _FALLBACKS[len(text) % len(_FALLBACKS)]


@router.get("", response_model=list[ChatOut])
async def history(user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    rows = await db.scalars(
        select(ChatMessage).where(ChatMessage.user_id == user.id).order_by(ChatMessage.created_at.asc()).limit(200)
    )
    return rows.all()


@router.post("/messages", response_model=ChatReply, status_code=201)
async def send_message(
    payload: ChatSend,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    await usage.enforce_quota(db, user)   # free-tier daily cap (429 when exceeded)
    risk = await safety.scan_and_record(
        db, user_id=user.id, source="chat", source_id=None, text=payload.text
    )
    user_msg = ChatMessage(user_id=user.id, role="user", text=payload.text, risk_level=risk)
    db.add(user_msg)
    await db.flush()

    # Build short context from recent history — but only if the user consents to
    # AI memory. With memory off we pass just the current turn (no long-term
    # recall), honoring the "control what CereBro remembers" promise server-side.
    memory_on = user.consent is None or user.consent.ai_memory
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

    system = _SCIENTIFIC if user.companion == "Scientific" else _CALM_GUIDE
    reply_text = await ai.complete(system, transcript, max_tokens=200) or _fallback_reply(payload.text)

    if risk == "crisis":
        # Locale-correct hotlines for the user's region (never a hardcoded country).
        reply_text = f"{reply_text}{crisis.reply_suffix(user.region)}"

    reply = ChatMessage(user_id=user.id, role="assistant", text=reply_text)
    db.add(reply)
    await db.commit()
    await db.refresh(user_msg)
    await db.refresh(reply)

    # Offer an inline activity (breathing, grounding, …) + quick-reply chips.
    widget, suggestions = activities.route(payload.text, risk)
    return ChatReply(user_message=user_msg, reply=reply, widget=widget, suggestions=suggestions)
