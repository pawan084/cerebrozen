"""Voice loop endpoints.

The iOS Talk companion orchestrates a full turn client-side:
    mic audio → POST /voice/stt (Deepgram) → POST /chat/messages (LLM)
              → POST /voice/tts (ElevenLabs) → play

Keeping STT and TTS as separate steps means the transcript flows through the
existing /chat pipeline (safety scan, history, persona) unchanged.
"""
from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile
from fastapi.responses import Response
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db
from app.core.deps import get_current_user
from app.core.ratelimit import account_limit, limiter
from app.models.user import User
from app.models.consent import consent_allows
from app.services import usage, verification, voice

router = APIRouter(prefix="/voice", tags=["voice"])

_MAX_AUDIO_BYTES = 10 * 1024 * 1024  # 10 MB — a generous cap for short clips.


class STTOut(BaseModel):
    transcript: str
    # Whether the uploaded audio was allowed to be retained (voice_storage
    # consent). Always False today - nothing persists it - and now the
    # ENFORCED answer rather than an unread flag (register C5).
    audio_retained: bool = False


class TTSIn(BaseModel):
    text: str = Field(min_length=1, max_length=5000)


@router.get("/status")
async def status(user: User = Depends(get_current_user)):
    """Tell the client which halves of the voice loop are available."""
    return {"stt": settings.stt_enabled, "tts": settings.tts_enabled}


@router.post("/stt", response_model=STTOut)
@limiter.limit("20/minute")   # provider-cost guard (one STT call per voice turn)
@account_limit("20/minute")   # …and the same ceiling per account (one STT call per voice turn)
async def speech_to_text(
    request: Request,
    audio: UploadFile = File(...),
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    await verification.require_verified_email(db, user, feature='voice')
    await usage.consume(db, user, "voice_stt")
    if not settings.stt_enabled:
        raise HTTPException(status_code=503, detail="Speech-to-text is not configured")
    # Read at most cap+1 bytes and reject on overflow — never buffer an
    # arbitrarily large upload just to measure it (register C79; the admin
    # media upload has always done it this way).
    data = await audio.read(_MAX_AUDIO_BYTES + 1)
    if not data:
        raise HTTPException(status_code=400, detail="Empty audio upload")
    if len(data) > _MAX_AUDIO_BYTES:
        raise HTTPException(status_code=413, detail="Audio too large")
    transcript = await voice.transcribe(data, content_type=audio.content_type or "audio/mpeg")
    if not transcript:
        raise HTTPException(status_code=422, detail="Could not transcribe audio")
    # Register C5 (finding 66): `voice_storage` was collected, exported and
    # shown in admin while being enforced at zero sites. This endpoint is the
    # only place raw audio exists server-side, so this is where the category
    # means something: without consent the bytes are dropped the moment the
    # transcript exists, and the response says so rather than leaving the
    # user to assume. (Nothing persists audio today either way - the flag now
    # documents and ENFORCES that, so a future retention path cannot quietly
    # inherit permission it was never given.)
    retained = consent_allows(user, "voice_storage")
    del data   # explicit: the audio does not outlive this request
    return STTOut(transcript=transcript, audio_retained=retained)


@router.post(
    "/tts",
    responses={200: {"content": {"audio/mpeg": {}}}},
    response_class=Response,
)
@limiter.limit("60/minute")   # sentence-by-sentence TTS makes several calls per reply
@account_limit("60/minute")   # …and the same ceiling per account (sentence-by-sentence TTS)
async def text_to_speech(
    request: Request,
    payload: TTSIn,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    await verification.require_verified_email(db, user, feature='voice')
    await usage.consume(db, user, "voice_tts")
    if not settings.tts_enabled:
        raise HTTPException(status_code=503, detail="Text-to-speech is not configured")
    # TTS bills the provider per call and voices chat replies, so it draws on
    # the same free-tier daily quota as the chat that produced the text
    # (register C77) — an IP limit alone left per-account cost unbounded.
    await usage.enforce_quota(db, user)
    audio = await voice.synthesize(payload.text)
    if not audio:
        raise HTTPException(status_code=502, detail="Speech synthesis failed")
    return Response(content=audio, media_type="audio/mpeg")
