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

from app.core.config import settings
from app.core.deps import get_current_user
from app.core.ratelimit import limiter
from app.models.user import User
from app.services import voice

router = APIRouter(prefix="/voice", tags=["voice"])

_MAX_AUDIO_BYTES = 10 * 1024 * 1024  # 10 MB — a generous cap for short clips.


class STTOut(BaseModel):
    transcript: str


class TTSIn(BaseModel):
    text: str = Field(min_length=1, max_length=5000)


@router.get("/status")
async def status(user: User = Depends(get_current_user)):
    """Tell the client which halves of the voice loop are available."""
    return {"stt": settings.stt_enabled, "tts": settings.tts_enabled}


@router.post("/stt", response_model=STTOut)
@limiter.limit("20/minute")   # provider-cost guard (one STT call per voice turn)
async def speech_to_text(
    request: Request,
    audio: UploadFile = File(...),
    user: User = Depends(get_current_user),
):
    if not settings.stt_enabled:
        raise HTTPException(status_code=503, detail="Speech-to-text is not configured")
    data = await audio.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty audio upload")
    if len(data) > _MAX_AUDIO_BYTES:
        raise HTTPException(status_code=413, detail="Audio too large")
    transcript = await voice.transcribe(data, content_type=audio.content_type or "audio/mpeg")
    if not transcript:
        raise HTTPException(status_code=422, detail="Could not transcribe audio")
    return STTOut(transcript=transcript)


@router.post(
    "/tts",
    responses={200: {"content": {"audio/mpeg": {}}}},
    response_class=Response,
)
@limiter.limit("60/minute")   # sentence-by-sentence TTS makes several calls per reply
async def text_to_speech(request: Request, payload: TTSIn, user: User = Depends(get_current_user)):
    if not settings.tts_enabled:
        raise HTTPException(status_code=503, detail="Text-to-speech is not configured")
    audio = await voice.synthesize(payload.text)
    if not audio:
        raise HTTPException(status_code=502, detail="Speech synthesis failed")
    return Response(content=audio, media_type="audio/mpeg")
