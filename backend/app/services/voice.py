"""Voice I/O: speech-to-text (Deepgram) and text-to-speech (ElevenLabs).

Both halves degrade gracefully — when the matching key is unset, ``transcribe``
returns ``None`` and ``synthesize`` returns ``None`` so callers can respond with
a clear 503 instead of crashing. Keys live only in the environment.
"""
from __future__ import annotations

import logging

import httpx

from app.core.config import settings

logger = logging.getLogger("cerebro.voice")

_DEEPGRAM_URL = "https://api.deepgram.com/v1/listen"
_ELEVENLABS_URL = "https://api.elevenlabs.io/v1/text-to-speech"


async def transcribe(audio: bytes, content_type: str = "audio/mpeg") -> str | None:
    """Transcribe raw audio bytes to text via Deepgram. None if disabled/failed."""
    if not settings.stt_enabled:
        logger.info("STT disabled (no Deepgram key); skipping transcription")
        return None
    params = {"model": settings.deepgram_model, "smart_format": "true", "punctuate": "true"}
    headers = {
        "Authorization": f"Token {settings.deepgram_api_key}",
        "Content-Type": content_type or "audio/mpeg",
    }
    try:
        async with httpx.AsyncClient(timeout=30) as client:
            resp = await client.post(_DEEPGRAM_URL, params=params, headers=headers, content=audio)
            resp.raise_for_status()
            data = resp.json()
        alt = data["results"]["channels"][0]["alternatives"][0]
        return (alt.get("transcript") or "").strip() or None
    except Exception as exc:
        logger.warning("Deepgram transcription failed: %s", exc)
        return None


async def synthesize(text: str, timeout: float = 30.0) -> bytes | None:
    """Synthesize speech (MP3 bytes) from text via ElevenLabs. None if disabled/failed.

    The default timeout suits short chat replies; long-form narration (admin
    content generation) passes a larger budget.
    """
    if not settings.tts_enabled:
        logger.info("TTS disabled (no ElevenLabs key); skipping synthesis")
        return None
    url = f"{_ELEVENLABS_URL}/{settings.elevenlabs_voice_id}"
    headers = {"xi-api-key": settings.elevenlabs_api_key, "accept": "audio/mpeg"}
    body = {
        "text": text,
        "model_id": settings.elevenlabs_model,
        "voice_settings": {"stability": 0.5, "similarity_boost": 0.75},
    }
    params = {"output_format": "mp3_44100_128"}
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.post(url, params=params, headers=headers, json=body)
            resp.raise_for_status()
            return resp.content
    except Exception as exc:
        logger.warning("ElevenLabs synthesis failed: %s", exc)
        return None
