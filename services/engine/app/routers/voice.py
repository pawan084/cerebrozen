"""Voice transport endpoints (Phase 10) — mint LiveKit room tokens.

Voice shares the coaching brain with text: the LiveKit room is named after the
`session_id`, the agent worker (`app/voice/agent.py`) joins that room and runs
STT -> the LangGraph brain -> TTS on the SAME `session_id`, so a user can move
between typing and talking within one session/checkpoint.

    POST /v1/sessions/{session_id}/voice/token  -> { url, token, room, identity }

The client (test UI / Flutter / web) uses `url` + `token` with the LiveKit SDK to
join the room, publish its mic, and play the agent's audio back.
"""

from __future__ import annotations

import datetime
import json
import logging
import uuid

import os

import requests as _requests
from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app import config
from app.auth import require_auth
from app.schemas import SessionStartRequest
from app.session import resolve_user_id
from app.voice.ssm_config import read_voice_params, write_voice_params

logger = logging.getLogger("cerebrozen.voice")
router = APIRouter(prefix="/v1/sessions", tags=["voice"])

# ── Voice Lab (param tuning console) ─────────────────────────────────────────
voice_lab_router = APIRouter(prefix="/v1/voice", tags=["voice-lab"])


class VoiceConfigSaveRequest(BaseModel):
    voice_id: str = ""
    model: str = ""
    stability: str = ""
    similarity_boost: str = ""
    style: str = ""
    speed: str = ""
    use_speaker_boost: str = ""


@voice_lab_router.get("/config")
def get_voice_config(claims: dict = Depends(require_auth)):
    """Return the current live voice config — SSM values when available,
    otherwise config.py defaults. Used by the Voice Lab to pre-fill sliders."""
    env = os.environ.get("ENV", "dev")
    ssm = read_voice_params(env)
    defaults = {
        "CEREBROZEN_VOICE_TTS_VOICE_ID": config.VOICE_TTS_VOICE_ID,
        "CEREBROZEN_VOICE_TTS_MODEL":    config.VOICE_TTS_MODEL,
        "CEREBROZEN_VOICE_STABILITY":    str(config.VOICE_TTS_STABILITY),
        "CEREBROZEN_VOICE_SIMILARITY":   str(config.VOICE_TTS_SIMILARITY_BOOST),
        "CEREBROZEN_VOICE_STYLE":        str(config.VOICE_TTS_STYLE),
        "CEREBROZEN_VOICE_SPEED":        str(config.VOICE_TTS_SPEED),
        "CEREBROZEN_VOICE_SPEAKER_BOOST": str(config.VOICE_TTS_SPEAKER_BOOST).lower(),
    }
    merged = {**defaults, **ssm}
    return {
        "source": "ssm" if ssm else "defaults",
        "env": env,
        "ssm_configured": bool(ssm),
        "params": {
            "voice_id":        merged.get("CEREBROZEN_VOICE_TTS_VOICE_ID", ""),
            "model":           merged.get("CEREBROZEN_VOICE_TTS_MODEL", ""),
            "stability":       merged.get("CEREBROZEN_VOICE_STABILITY", "0.5"),
            "similarity_boost":merged.get("CEREBROZEN_VOICE_SIMILARITY", "0.75"),
            "style":           merged.get("CEREBROZEN_VOICE_STYLE", "0.0"),
            "speed":           merged.get("CEREBROZEN_VOICE_SPEED", "1.0"),
            "use_speaker_boost":merged.get("CEREBROZEN_VOICE_SPEAKER_BOOST", "true"),
        },
    }


@voice_lab_router.post("/config")
def save_voice_config(request: VoiceConfigSaveRequest, claims: dict = Depends(require_auth)):
    """Write voice params to SSM Parameter Store. Takes effect on the next
    voice session start — no restart required."""
    env = os.environ.get("ENV", "dev")
    payload: dict[str, str] = {}
    if request.voice_id:        payload["CEREBROZEN_VOICE_TTS_VOICE_ID"] = request.voice_id
    if request.model:           payload["CEREBROZEN_VOICE_TTS_MODEL"]    = request.model
    if request.stability:       payload["CEREBROZEN_VOICE_STABILITY"]    = request.stability
    if request.similarity_boost:payload["CEREBROZEN_VOICE_SIMILARITY"]   = request.similarity_boost
    if request.style:           payload["CEREBROZEN_VOICE_STYLE"]        = request.style
    if request.speed:           payload["CEREBROZEN_VOICE_SPEED"]        = request.speed
    if request.use_speaker_boost:payload["CEREBROZEN_VOICE_SPEAKER_BOOST"] = request.use_speaker_boost
    if not payload:
        raise HTTPException(status_code=400, detail="No params provided.")
    try:
        written = write_voice_params(env, payload)
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=503, detail=f"SSM write failed: {exc}") from exc
    return {
        "saved": written,
        "env": env,
        "message": "Saved to Parameter Store — takes effect on the next voice session (no restart needed).",
    }


class VoicePreviewRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=1000)
    voice_id: str
    model: str = "eleven_multilingual_v2"
    stability: float = 0.5
    similarity_boost: float = 0.75
    style: float = 0.0
    speed: float = 1.0
    use_speaker_boost: bool = True


@voice_lab_router.get("/voices")
def list_voices(claims: dict = Depends(require_auth)):
    """Return the voice list configured via CEREBROZEN_VOICE_AVAILABLE_IDS."""
    return {
        "voices": config.VOICE_AVAILABLE_IDS,
        "current": {
            "voice_id": config.VOICE_TTS_VOICE_ID,
            "model": config.VOICE_TTS_MODEL,
            "stability": config.VOICE_TTS_STABILITY,
            "similarity_boost": config.VOICE_TTS_SIMILARITY_BOOST,
            "style": config.VOICE_TTS_STYLE,
            "speed": config.VOICE_TTS_SPEED,
            "use_speaker_boost": config.VOICE_TTS_SPEAKER_BOOST,
        },
    }


@voice_lab_router.post("/preview")
def preview_voice(request: VoicePreviewRequest, claims: dict = Depends(require_auth)):
    """Proxy a TTS synthesis request to ElevenLabs and stream back the MP3.
    The ElevenLabs API key stays server-side."""
    if not config.ELEVEN_API_KEY:
        raise HTTPException(status_code=503, detail="ELEVENLABS_API_KEY not configured.")
    # eleven_v3 only supports stability; the other VoiceSettings params are not
    # recognised and will cause a 400 if sent.
    voice_settings: dict = {"stability": request.stability}
    if request.model != "eleven_v3":
        voice_settings["similarity_boost"] = request.similarity_boost
        voice_settings["style"] = request.style
        voice_settings["speed"] = request.speed
        voice_settings["use_speaker_boost"] = request.use_speaker_boost

    payload = {
        "text": request.text,
        "model_id": request.model,
        "voice_settings": voice_settings,
    }
    r = _requests.post(
        f"https://api.elevenlabs.io/v1/text-to-speech/{request.voice_id}",
        headers={"xi-api-key": config.ELEVEN_API_KEY, "Accept": "audio/mpeg"},
        json=payload,
        stream=True,
        timeout=30,
    )
    if not r.ok:
        raise HTTPException(status_code=r.status_code,
                            detail=f"ElevenLabs error {r.status_code}: {r.text[:300]}")
    logger.info("voice.lab_preview", extra={
        "voice_id": request.voice_id, "model": request.model,
        "stability": request.stability, "similarity_boost": request.similarity_boost,
        "style": request.style, "speed": request.speed,
    })

    def _stream():
        for chunk in r.iter_content(chunk_size=4096):
            if chunk:
                yield chunk

    return StreamingResponse(_stream(), media_type="audio/mpeg")


@router.post("/{session_id}/voice/token")
def voice_token(
    session_id: str,
    request: SessionStartRequest,
    claims: dict = Depends(require_auth),
):
    """Mint a LiveKit access token for `session_id`'s room.

    `user_id` comes from the body or the JWT (same as the text endpoints). The
    client generates a fresh `session_id` (UUID) for a new voice session, or passes
    an existing one to continue it by voice. 503 if LiveKit isn't configured."""
    if not (config.LIVEKIT_URL and config.LIVEKIT_API_KEY and config.LIVEKIT_API_SECRET):
        raise HTTPException(
            status_code=503,
            detail="Voice is not configured: set LIVEKIT_URL / LIVEKIT_API_KEY / "
            "LIVEKIT_API_SECRET (and DEEPGRAM_API_KEY / ELEVENLABS_API_KEY for the worker).",
        )
    user_id = resolve_user_id(request.user_id, claims)
    if not user_id:
        raise HTTPException(status_code=400, detail="user_id required (body or JWT).")
    if not session_id.strip():
        raise HTTPException(status_code=400, detail="session_id required in the path.")

    # Fresh room name per connect: LiveKit only dispatches an agent when a room is
    # FIRST created, so reconnecting to an existing room leaves you with no agent.
    # A unique room each time guarantees dispatch. The persistent BRAIN session_id
    # rides in the token metadata so the worker resumes the SAME conversation/
    # checkpoint across Stop→Start (the room is just LiveKit transport).
    room = f"{session_id}--{uuid.uuid4().hex[:8]}"

    # Same contract as the text endpoints (metadata.user_language — see
    # API_INTEGRATION.md "Language" note): rides in the token metadata so the
    # worker (app/voice/agent.py) can read it back at room join and thread it
    # into every brain turn, same as session_id above. Room-lifetime setting,
    # matching the environment prompt's "fixed session setting" language rule —
    # to change it, Stop and Start voice again with the new selection.
    user_language = (request.metadata or {}).get("user_language") or ""
    token_metadata: dict = {"session_id": session_id}
    if user_language:
        token_metadata["user_language"] = user_language

    # Lazy import: keep the (heavy) livekit dep off the import path of envs that
    # don't run voice. The package is installed with the voice extras.
    #
    # Guarded, because the unguarded version defeated the 503 above. That guard exists to
    # say "voice is not configured here" — and it fired correctly when the env vars were
    # MISSING. But with the env vars SET and the SDK absent (it is not in requirements.txt),
    # this import raised ModuleNotFoundError and the caller got a bare 500. That is the
    # exact misconfiguration the guard was written for, and it was the one shape it missed:
    # a deploy that believes it has voice, and does not.
    try:
        from livekit import api
    except ModuleNotFoundError as exc:
        logger.error("voice.sdk_missing", extra={"error": str(exc)})
        raise HTTPException(
            status_code=503,
            detail=(
                "Voice is configured (LIVEKIT_URL/API_KEY/API_SECRET are set) but the "
                "LiveKit SDK is not installed in this image. Install the voice extras."
            ),
        ) from exc

    token = (
        api.AccessToken(config.LIVEKIT_API_KEY, config.LIVEKIT_API_SECRET)
        .with_identity(user_id)
        .with_name(user_id)
        .with_metadata(json.dumps(token_metadata))
        .with_ttl(datetime.timedelta(seconds=config.VOICE_TOKEN_TTL_S))
        .with_grants(
            api.VideoGrants(
                room_join=True,
                room=room,
                can_publish=True,
                can_subscribe=True,
                can_publish_data=True,
                # Required for the client to call localParticipant.setAttributes()
                # (mid-call language switch — see testui pushLiveLanguageIfConnected()
                # and app/voice/agent.py's live attribute re-read). Without this grant
                # LiveKit's server rejects the update; the client-side call fails
                # silently unless the caller explicitly awaits and catches it.
                can_update_own_metadata=True,
            )
        )
        .to_jwt()
    )
    logger.info(
        "voice.token_minted",
        extra={
            "user_id": user_id,
            "session_id": session_id,
            "room": room,
            "user_language": user_language,
        },
    )
    return {
        "url": config.LIVEKIT_URL,
        "token": token,
        "room": room,
        "identity": user_id,
        "session_id": session_id,
    }
