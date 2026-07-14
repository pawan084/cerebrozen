"""LiveKit voice agent worker (Phase 10) — the cascade around the brain.

Run it as a separate process alongside the API:

    python -m app.voice.agent dev      # dev mode (auto-reload, verbose)
    python -m app.voice.agent start    # production mode

It connects to LiveKit (LIVEKIT_URL / LIVEKIT_API_KEY / LIVEKIT_API_SECRET), and
whenever a participant joins a room it runs:

    Deepgram STT  ->  the LangGraph brain (app.service)  ->  ElevenLabs TTS

The room name IS the `session_id`, so a voice turn drives the SAME brain/checkpoint
as text. Barge-in (user speech cancels the bot's playback) is handled by the
AgentSession's built-in VAD/turn-detection.

The brain is plugged in by overriding `Agent.llm_node` to yield the reply text
straight from `app.service` — no second LLM, no prompt duplication. The brain's
existing streaming (`on_token`, which already unwraps the JSON reply via
`UserTextStreamer`) feeds the TTS.
"""

from __future__ import annotations

import asyncio
import contextvars
import json
import logging
import os
import uuid
from typing import AsyncIterable

# Load env-dev.ps1 BEFORE importing app.config — config snapshots os.environ at
# import time, so the keys must be present first. This also runs in each spawned
# job subprocess (LiveKit uses spawn on Windows), which re-imports this module.
# Mirrors app/main.py's ordering.
from app.env_loader import load_local_env

load_local_env()

from livekit.agents import (  # noqa: E402
    Agent,
    AgentSession,
    JobContext,
    WorkerOptions,
    cli,
    llm,
)
from livekit.plugins import deepgram, elevenlabs  # noqa: E402
from livekit.plugins.elevenlabs import VoiceSettings  # noqa: E402

from app import config  # noqa: E402
from app.schemas import SessionStartRequest, SessionTurnRequest  # noqa: E402
from app.voice.ssm_config import read_voice_params  # noqa: E402

# Default connection options for our custom LLM stream (path varies by version).
try:
    from livekit.agents import DEFAULT_API_CONNECT_OPTIONS  # type: ignore  # noqa: E402
except Exception:  # noqa: BLE001
    try:
        from livekit.agents.types import DEFAULT_API_CONNECT_OPTIONS  # type: ignore  # noqa: E402
    except Exception:  # noqa: BLE001
        DEFAULT_API_CONNECT_OPTIONS = None  # type: ignore

logger = logging.getLogger("cerebrozen.voice.agent")


def _last_user_text(chat_ctx) -> str:
    """Pull the most recent user utterance from the LiveKit ChatContext (robust to
    the v1.x content shapes: a `.text_content` str, a plain str, or a list)."""
    items = getattr(chat_ctx, "items", None) or getattr(chat_ctx, "messages", None) or []
    for item in reversed(list(items)):
        if getattr(item, "role", None) != "user":
            continue
        tc = getattr(item, "text_content", None)
        if isinstance(tc, str) and tc.strip():
            return tc.strip()
        content = getattr(item, "content", None)
        if isinstance(content, str) and content.strip():
            return content.strip()
        if isinstance(content, list):
            parts = [c for c in content if isinstance(c, str)]
            if parts:
                return " ".join(parts).strip()
    return ""


class CereBroZenBrainLLM(llm.LLM):
    """Plugs the LangGraph brain in as the pipeline's LLM. `AgentSession` runs the
    generation step only when an `llm` is configured, so the brain MUST be an
    `llm.LLM` (overriding `Agent.llm_node` alone is never invoked without one).

    Each generation drives one brain turn on this room's `session_id`, streamed
    token-by-token (the brain's `on_token` already unwraps the JSON reply via
    UserTextStreamer) to the TTS. The session-started flag lives here so the first
    utterance mints/adopts the session and later ones continue it."""

    def __init__(
        self,
        session_id: str,
        user_id: str,
        room=None,
        user_language: str = "",
        participant=None,
    ) -> None:
        super().__init__()
        self._session_id = session_id
        self._user_id = user_id
        self._started = False
        self._brain_in_flight = False
        self._room = room
        # Initial value from the token metadata at room join (see entrypoint()).
        # Re-read from the participant's live attributes every turn below (see
        # stream_brain) so a mid-call language switch — pushed via LiveKit
        # `localParticipant.setAttributes({user_language: ...})`, no
        # reconnect needed — takes effect on the NEXT turn. `participant.attributes`
        # is a live view the SDK keeps in sync with attribute-changed events, not a
        # snapshot frozen at join time.
        self._user_language = user_language
        self._participant = participant
        # The full turn result (buttons, phase detail, etc.) — only the token text
        # is read off the queue below, so the rest of the dict is stashed here for
        # the UI-events publish step (VOICE_UI_EVENTS_ENABLED) once the reply
        # finishes streaming.
        self._last_result: dict | None = None

    def chat(self, *, chat_ctx, tools=None, conn_options=None, **kwargs) -> "_CereBroZenLLMStream":
        return _CereBroZenLLMStream(
            self,
            chat_ctx=chat_ctx,
            tools=tools or [],
            conn_options=conn_options if conn_options is not None else DEFAULT_API_CONNECT_OPTIONS,
        )

    async def stream_brain(self, user_text: str) -> AsyncIterable[str]:
        """Run one blocking brain turn in a worker thread and async-yield the reply
        tokens as they stream (so TTS starts speaking before the turn finishes)."""
        from app.service import get_service

        loop = asyncio.get_running_loop()
        queue: asyncio.Queue = asyncio.Queue()
        streamed_any = False

        def on_token(text: str) -> None:
            nonlocal streamed_any
            streamed_any = True
            loop.call_soon_threadsafe(queue.put_nowait, text)

        def run() -> None:
            svc = get_service()
            # Live language override: pick up a mid-call `setAttributes` push
            # before building this turn's metadata, so switching the dropdown
            # while connected takes effect on the very next turn (no stop/start).
            if self._participant is not None:
                live_lang = (self._participant.attributes or {}).get("user_language", "").strip()
                if live_lang and live_lang != self._user_language:
                    logger.info(
                        "voice.language_live_override",
                        extra={
                            "session_id": self._session_id,
                            "user_id": self._user_id,
                            "previous": self._user_language,
                            "new": live_lang,
                        },
                    )
                    self._user_language = live_lang
            turn_metadata: dict = {"conversation_mode": "voice"}
            if self._user_language:
                turn_metadata["user_language"] = self._user_language
            try:
                if not self._started:
                    self._started = True
                    result = svc.start_session(
                        self._user_id,
                        SessionStartRequest(
                            session_id=self._session_id,
                            text=user_text,
                            metadata=turn_metadata,
                        ),
                        on_token=on_token,
                    )
                else:
                    result = svc.run_turn(
                        self._user_id,
                        self._session_id,
                        SessionTurnRequest(
                            text=user_text, metadata=turn_metadata
                        ),
                        on_token=on_token,
                    )
                # A handful of _run_turn short-circuits (session busy / already
                # closed / route-disabled) return response_to_user in the result
                # dict WITHOUT ever calling on_token — the text/HTTP caller reads
                # the return value directly, but voice has no other path to TTS.
                # Without this fallback those turns silently produce zero audio:
                # thinking -> listening, no speech, no error anywhere in the logs.
                if not streamed_any:
                    reply_text = (result or {}).get("response_to_user") or ""
                    if reply_text:
                        on_token(reply_text)
                self._last_result = result
            except Exception:  # noqa: BLE001 — surface as end-of-stream, never crash the room
                logger.exception("voice.brain_turn_failed")
            finally:
                loop.call_soon_threadsafe(queue.put_nowait, None)  # sentinel

        # run_in_executor does NOT copy the current contextvars.Context into the
        # worker thread (unlike asyncio.create_task), so request_id — set by the
        # caller via a bare ContextVar — would silently read back as "" for every
        # log/DB write made from inside run(). Copy the context explicitly so the
        # thread inherits the request_id/user_id/session_id already bound here.
        ctx = contextvars.copy_context()
        fut = loop.run_in_executor(None, ctx.run, run)
        while True:
            token = await queue.get()
            if token is None:
                break
            yield token
        await fut


class _CereBroZenLLMStream(llm.LLMStream):
    """Bridges the brain's token stream into LiveKit ChatChunks on the event channel."""

    async def _run(self) -> None:
        brain: CereBroZenBrainLLM = self._llm  # type: ignore[assignment]
        # Mint the per-turn request_id FIRST, before any early return, so every
        # log line for this turn attempt — including the skip/no-op paths below —
        # carries user_id/session_id/request_id (standing log-fields rule). The
        # FastAPI middleware never runs in this process, so the ContextVar is set
        # manually here; conversation.record_turn reads it the same way.
        from app.request_context import request_id as _req_id
        turn_request_id = uuid.uuid4().hex
        _req_id.set(turn_request_id)

        user_text = _last_user_text(self._chat_ctx)
        if not user_text:
            # Previously a silent no-op: the agent would go thinking -> listening
            # with zero logs anywhere, indistinguishable from a genuinely empty
            # turn. Log it so a chat_ctx shape mismatch (e.g. an SDK upgrade
            # changing ChatMessage.content) is visible instead of looking like a
            # dropped reply with no cause.
            logger.warning(
                "voice.turn_skipped_no_user_text",
                extra={
                    "session_id": brain._session_id,
                    "user_id": brain._user_id,
                    "request_id": turn_request_id,
                },
            )
            return
        if brain._brain_in_flight:
            # WARNING (not INFO): a user turn is being dropped on the floor — this is
            # exactly the silent-failure signature QA hit (thinking -> listening, no
            # reply). Was invisible at INFO; needs to surface without a manual log dig.
            logger.warning(
                "voice.turn_skipped_in_flight",
                extra={
                    "session_id": brain._session_id,
                    "user_id": brain._user_id,
                    "text": user_text[:100],
                    "request_id": turn_request_id,
                },
            )
            return
        brain._brain_in_flight = True
        try:
            logger.info(
                "voice.user_turn",
                extra={
                    "session_id": brain._session_id,
                    "user_id": brain._user_id,
                    "text": user_text[:300],
                    "stt_language": config.VOICE_STT_LANGUAGE,
                    "user_language": brain._user_language,
                    "request_id": turn_request_id,
                },
            )
            chunk_id = str(uuid.uuid4())
            reply_parts: list[str] = []
            async for token in brain.stream_brain(user_text):
                if token:
                    reply_parts.append(token)
                    self._event_ch.send_nowait(
                        llm.ChatChunk(
                            id=chunk_id,
                            delta=llm.ChoiceDelta(role="assistant", content=token),
                        )
                    )
            # Log after all tokens sent to TTS — no TTS latency added.
            if reply_parts:
                reply = "".join(reply_parts)
                logger.info(
                    "voice.bot_reply",
                    extra={
                        "session_id": brain._session_id,
                        "user_id": brain._user_id,
                        "text": reply[:300],
                        "request_id": turn_request_id,
                    },
                )
            # UI events (Phase 10 follow-up, flag-gated): publish the turn's full
            # result dict over the room's reliable data channel — same shape as the
            # text-mode SSE `done` event (both come from the same service._run_turn
            # return value), including response_to_user for parity even though voice
            # already delivers that text via speech + the aligned transcript.
            # Published once the full reply is assembled, mirroring `done`'s timing.
            # A publish failure must never break the voice turn itself.
            if config.VOICE_UI_EVENTS_ENABLED and reply_parts and brain._room is not None:
                try:
                    ui_payload = dict(brain._last_result or {})
                    # Single-source card delivery: inline action cards are stripped
                    # from the voice channel — they're un-actionable in voice and the
                    # voice-rendered row later duplicated the text-mode copy after the
                    # auto-switch (QA 2026-07-11 duplicate-inlay-card report). The
                    # text client gets the cards from the turn payload / history;
                    # `stage` still signals the action beat for the auto-switch.
                    if not config.VOICE_INLINE_ACTIONS_ENABLED and ui_payload.get("actions"):
                        logger.info(
                            "voice.inline_actions_suppressed",
                            extra={
                                "session_id": brain._session_id,
                                "user_id": brain._user_id,
                                "request_id": turn_request_id,
                                "actions": len(ui_payload.get("actions") or []),
                            },
                        )
                        ui_payload["actions"] = []
                        # Ships only to accompany action cards — drop it with them.
                        ui_payload.pop("available_roi_metrics", None)
                    if ui_payload:
                        await brain._room.local_participant.publish_data(
                            json.dumps(ui_payload, ensure_ascii=False).encode("utf-8"),
                            reliable=True,
                            topic="cerebrozen.ui_event",
                        )
                        logger.info(
                            "voice.ui_event_published",
                            extra={
                                "session_id": brain._session_id,
                                "user_id": brain._user_id,
                                "request_id": turn_request_id,
                                "payload": ui_payload,
                            },
                        )
                except Exception:  # noqa: BLE001
                    logger.exception(
                        "voice.ui_event_publish_failed",
                        extra={
                            "session_id": brain._session_id,
                            "user_id": brain._user_id,
                            "request_id": turn_request_id,
                        },
                    )
        finally:
            brain._brain_in_flight = False


async def entrypoint(ctx: JobContext) -> None:
    await ctx.connect()
    participant = await ctx.wait_for_participant()
    user_id = (participant.identity or "voice_user").strip()
    # The BRAIN session_id rides in the token metadata so a fresh LiveKit room still
    # resumes the SAME conversation/checkpoint across Stop→Start. Fall back to the
    # room name (minus the per-connect suffix) if metadata is missing.
    session_id = ctx.room.name.split("--", 1)[0]
    session_id_source = "room_name"
    # user_language rides in the same token metadata as session_id (set by
    # app/routers/voice.py from metadata.user_language on the /voice/token
    # request) — read once here, at room join, and fixed for the room's
    # lifetime (matches the environment prompt's "fixed session setting" rule).
    user_language = ""
    try:
        if participant.metadata:
            meta = json.loads(participant.metadata)
            if meta.get("session_id"):
                session_id = meta["session_id"]
                session_id_source = "metadata"
            user_language = (meta.get("user_language") or "").strip()
    except Exception:  # noqa: BLE001 — fall back to the room-derived id
        session_id_source = "room_name_fallback_parse_error"
    logger.info(
        "voice.session_start",
        extra={
            "session_id": session_id,
            "room": ctx.room.name,
            "user_id": user_id,
            "session_id_source": session_id_source,
            "stt_model": config.VOICE_STT_MODEL,
            "stt_language": config.VOICE_STT_LANGUAGE,
            "user_language": user_language,
        },
    )

    # Build the cascade: Deepgram STT + ElevenLabs TTS. VAD/turn-detection (and thus
    # barge-in) are AgentSession defaults — no explicit vad needed. Keys are passed
    # EXPLICITLY (resolved from config OR os.environ, accepting either ElevenLabs
    # spelling) so the plugins never depend on a specific env-var name being set.
    deepgram_key = config.DEEPGRAM_API_KEY or os.environ.get("DEEPGRAM_API_KEY", "")
    eleven_key = (
        config.ELEVEN_API_KEY
        or os.environ.get("ELEVENLABS_API_KEY")
        or os.environ.get("ELEVEN_API_KEY")
        or ""
    )
    if not eleven_key:
        logger.error("voice.missing_eleven_key — set ELEVENLABS_API_KEY in env-dev.ps1")
    if not deepgram_key:
        logger.error("voice.missing_deepgram_key — set DEEPGRAM_API_KEY in env-dev.ps1")

    # Higher endpointing_ms = Deepgram waits for a real pause before finalizing,
    # instead of fragmenting one utterance into multiple/late finals (which were
    # cancelling CereBroZen's reply → "no response").
    stt_kwargs = {"model": config.VOICE_STT_MODEL, "endpointing_ms": config.VOICE_STT_ENDPOINTING_MS}
    # Multilingual input: without a language, Deepgram defaults to English and
    # drops/mis-transcribes Hindi. "multi" (nova-3) captures English + Hindi and
    # code-switching between them. Empty value keeps the plugin default.
    if config.VOICE_STT_LANGUAGE:
        stt_kwargs["language"] = config.VOICE_STT_LANGUAGE
    if deepgram_key:
        stt_kwargs["api_key"] = deepgram_key

    # Hot-load voice params from SSM so the boss can change them in the Voice Lab
    # without restarting the worker. Falls back to config.py / env var defaults
    # when SSM is unavailable (local dev) or a param is not yet set.
    env = os.environ.get("ENV", "dev")
    ssm = await asyncio.to_thread(read_voice_params, env)

    def _ssm_float(key: str, default: float) -> float:
        try:
            return float(ssm[key]) if key in ssm else default
        except ValueError:
            return default

    tts_voice_id = ssm.get("CEREBROZEN_VOICE_TTS_VOICE_ID") or config.VOICE_TTS_VOICE_ID
    tts_model    = ssm.get("CEREBROZEN_VOICE_TTS_MODEL")    or config.VOICE_TTS_MODEL
    stability    = _ssm_float("CEREBROZEN_VOICE_STABILITY",  config.VOICE_TTS_STABILITY)
    similarity   = _ssm_float("CEREBROZEN_VOICE_SIMILARITY", config.VOICE_TTS_SIMILARITY_BOOST)
    style        = _ssm_float("CEREBROZEN_VOICE_STYLE",      config.VOICE_TTS_STYLE)
    speed        = _ssm_float("CEREBROZEN_VOICE_SPEED",      config.VOICE_TTS_SPEED)
    speaker_boost = (ssm.get("CEREBROZEN_VOICE_SPEAKER_BOOST", "").lower() == "true"
                     if "CEREBROZEN_VOICE_SPEAKER_BOOST" in ssm
                     else config.VOICE_TTS_SPEAKER_BOOST)

    logger.info(
        "voice.tts_params",
        extra={
            "session_id": session_id,
            "user_id": user_id,
            "source": "ssm" if ssm else "config",
            "model": tts_model, "voice_id": tts_voice_id,
            "stability": stability, "similarity": similarity,
            "style": style, "speed": speed,
        },
    )

    # TTS provider: ElevenLabs by default; Deepgram Aura is an opt-in fallback
    # (CEREBROZEN_VOICE_TTS_PROVIDER=deepgram) for when the ElevenLabs quota is spent.
    if config.VOICE_TTS_PROVIDER == "deepgram":
        tts = deepgram.TTS(model=config.VOICE_DEEPGRAM_TTS_MODEL, api_key=deepgram_key or None)
    else:
        # eleven_v3 only uses stability; similarity_boost is required by the VoiceSettings
        # dataclass so we must pass it, but the v3 API ignores it (no 400).
        # style/speed/use_speaker_boost are optional fields (NOT_GIVEN default) so safe to omit.
        if tts_model == "eleven_v3":
            voice_settings = VoiceSettings(
                stability=stability,
                similarity_boost=similarity,
            )
        else:
            voice_settings = VoiceSettings(
                stability=stability,
                similarity_boost=similarity,
                style=style,
                speed=speed,
                use_speaker_boost=speaker_boost,
            )
        tts_kwargs: dict = {
            "model": tts_model,
            "voice_settings": voice_settings,
        }
        if tts_voice_id:
            tts_kwargs["voice_id"] = tts_voice_id
        if eleven_key:
            tts_kwargs["api_key"] = eleven_key
        tts = elevenlabs.TTS(**tts_kwargs)

    session = AgentSession(
        stt=deepgram.STT(**stt_kwargs),
        llm=CereBroZenBrainLLM(
            session_id, user_id, room=ctx.room, user_language=user_language, participant=participant
        ),  # the brain IS the LLM
        tts=tts,
        # turn_handling replaces the flat endpointing/interruption kwargs (deprecated,
        # removed in v2.0) — structured here so all turn-taking behavior for the brain
        # lives in one place.
        turn_handling={
            # Force plain VAD-based end-of-turn instead of AgentSession's default
            # hosted semantic turn-detector (inference.TurnDetector()). That model
            # predicts end-of-turn from transcript content and, once its (often
            # pre-computed) prediction is confident, resolves the endpointing wait
            # near-instantly — so a short, grammatically-complete-sounding phrase
            # ("What's happening?") can commit the turn with ~0 silence gap even
            # though the endpointing delay below says to wait ~3s. With turn
            # detection pinned to "vad", the delays below are honored as literal
            # silence-duration floors/ceilings again.
            "turn_detection": "vad",
            "endpointing": {
                # Wait a bit longer after you stop before ending the turn, so a
                # brief pause mid-thought doesn't split into two transcripts.
                "min_delay": config.VOICE_MIN_ENDPOINTING_DELAY,
                "max_delay": config.VOICE_MAX_ENDPOINTING_DELAY,
            },
            "interruption": {
                # Barge-in toggle: when False (default), speaking while CereBroZen talks
                # does NOT cut him off — he finishes, then listens. Re-enable via
                # CEREBROZEN_VOICE_BARGE_IN=true.
                "enabled": config.VOICE_BARGE_IN,
                # Don't let a stray/late STT final cut CereBroZen off: require a real
                # interruption, and resume his reply if the "interruption" was false.
                "min_words": config.VOICE_MIN_INTERRUPTION_WORDS,
                "resume_false_interruption": True,
                "false_interruption_timeout": config.VOICE_FALSE_INTERRUPTION_TIMEOUT,
            },
            # The brain is NOT a cheap local model: it's a ~5s, stateful LangGraph
            # turn with real LLM cost and Mongo/RAG side effects. Preemptive
            # generation (SDK default: enabled) fires it speculatively on an
            # unconfirmed turn boundary, then cancels the consuming stream if the
            # guess was wrong. Because the actual brain call runs in a detached
            # thread that can't be cancelled, a superseded attempt still completes
            # and writes to Mongo — but its reply never reaches TTS. That's the
            # "thinking -> listening, no reply" failure QA hit. Disabled: the brain
            # only ever runs once a turn is truly confirmed.
            "preemptive_generation": {"enabled": False},
        },
        # Align CereBroZen's published transcript with the spoken audio word-by-word,
        # so the UI can render his reply in real time as he speaks it.
        use_tts_aligned_transcript=True,
    )
    # The Agent's instructions are unused (the real persona lives in the brain's
    # workbook prompts); the session's llm (our brain) produces every reply.
    await session.start(
        agent=Agent(instructions="You are CereBroZen, a warm, incisive voice coach."),
        room=ctx.room,
    )


if __name__ == "__main__":
    # Route the worker's logs to logs/cerebrozen.log too (same config as the API), so
    # voice.session_start / voice.user_turn / the brain's turn events are captured in
    # the file — not just this terminal — for debugging.
    try:
        from app.observability import configure_logging

        configure_logging()
    except Exception:  # noqa: BLE001 — never block the worker on logging setup
        pass
    cli.run_app(WorkerOptions(entrypoint_fnc=entrypoint))
