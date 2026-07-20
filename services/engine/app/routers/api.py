"""HTTP surface: /v1/webhook (stream or non-stream) + /health.

One coaching endpoint, mode chosen by a query param:

    POST /v1/webhook?stream=true   -> SSE stream (status / token / done / error)
    POST /v1/webhook?stream=false  -> single JSON body (the final result, at once)

Both run the SAME turn via service.handle_webhook_stream; the non-stream body is
exactly the payload the streaming "done" event carries. The SSE event shape is
preserved so the Streamlit tester UI consumes it unchanged."""

from __future__ import annotations

import asyncio
import json
import logging
import threading
from typing import AsyncGenerator

from fastapi import APIRouter, Depends, HTTPException
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse, StreamingResponse

from app.auth import require_auth
from app.llm.greeting_generator import generate_greeting, generate_greeting_stream
from app.schemas import WebhookRequest
from app.service import get_service
from app.session import user_id_from_claims

logger = logging.getLogger("cerebrozen.webhook")
router = APIRouter()


def _safety_health() -> dict:
    """Never let /health fail because a safety module could not be imported — but do NOT
    report "fine" either. An unknown safety state is reported as unknown."""
    try:
        from app.safety.escalation import health as _h

        return _h()
    except Exception as exc:  # noqa: BLE001
        return {"error": str(exc), "crisis_escalation_armed": None,
                "crisis_classifier_enabled": None}


def _nudge_health() -> dict:
    """Whether the check-in nudge channel is armed. Unknown is reported as unknown."""
    try:
        from app.notifications import health as _h

        return _h()
    except Exception as exc:  # noqa: BLE001
        return {"error": str(exc), "nudge_delivery_armed": None}


def _storage_health() -> dict:
    """The operator's at-rest-encryption attestation (datastore-layer). The app can't
    verify the datastore is encrypted — it reports what was declared, and 'unknown' when
    nothing was, so a deployment can't be quietly assumed encrypted."""
    from app import config

    enc = config.DATASTORE_ENCRYPTED
    return {"encrypted": "true" if enc is True else "false" if enc is False else "unknown"}


@router.get("/health")
async def health() -> dict:
    # Surface the force-handoff test flag so the UI can show an indicator (the UI
    # is a separate process and can't read the server's env). `all` = every stage
    # auto-advances after one turn; otherwise the explicit stage list.
    from app import config

    stages = sorted(config.FORCE_HANDOFF_STAGES)
    # Prompt-registry health: version + degraded flag, so a silent S3 fallback or
    # a failed reload is visible to load balancers/dashboards, not just in logs.
    prompts_health: dict = {}
    try:
        from app.graph.runtime import get_registry
        reg = get_registry()
        prompts_health = {
            "version": reg.version,
            "source": reg.source,
            "degraded": reg.degraded,
            "validation_issues": reg.validation.get("issue_count", 0),
        }
    except Exception:  # noqa: BLE001 — health must answer even if prompts can't load
        prompts_health = {"degraded": True, "error": "registry unavailable"}
    return {
        "status": "degraded" if prompts_health.get("degraded") else "ok",
        # White-label: the UI reads its title/brand from here rather than hardcoding it,
        # so a new client is one env var (CEREBROZEN_BRAND_NAME), not a static-file edit.
        "brand": config.BRAND_NAME,
        "prompts": prompts_health,
        # Safety, surfaced. A crisis classifier that is quietly off, or an escalation with
        # nobody listening on the other end, is the worst failure mode this product has: the
        # deployment believes it has a safety net and does not. It has to be answerable from
        # the outside, in one request, without reading anybody's config.
        "safety": _safety_health(),
        # Nudge delivery, surfaced for the same reason: a check-in reminder channel
        # that is silently off looks identical to one that is working until nobody
        # gets reminded. One request must be able to tell them apart.
        "nudges": _nudge_health(),
        # At-rest encryption attestation (datastore-layer). "unknown" until an operator
        # declares it, so a deployment is never quietly assumed encrypted.
        "storage": _storage_health(),
        "force_handoff": {
            "enabled": bool(stages),
            "all": "__all__" in config.FORCE_HANDOFF_STAGES,
            "stages": [s for s in stages if s != "__all__"],
        },
    }


@router.get("/health/status")
async def health_status() -> dict:
    """Sovereignty self-check: which EXTERNAL dependencies this engine instance actually
    reaches for. Posture, never content — safe without auth, and it mirrors the platform's
    /health/status. `sovereign_ready` = the LLM runs locally (mock or on-prem ollama) and
    no cloud-voice provider is wired, i.e. no coaching data leaves your infrastructure."""
    import os

    from app import config
    from app.auth.dependencies import auth_enabled

    provider = (os.environ.get("CEREBROZEN_LLM_PROVIDER") or "openai").strip().lower()
    llm_local = provider in ("mock", "ollama")
    voice_cloud = bool(config.LIVEKIT_URL)
    return {
        "service": "engine",
        "env": config.ENV,
        "llm_provider": provider,
        "llm_local": llm_local,
        "redis_external": bool(config.REDIS_URL),
        "mongo_configured": bool(config.MONGO_DB_URL),
        # POSTGRES_URL is read via the store seam, not exposed on config.
        "postgres_configured": bool(os.environ.get("POSTGRES_URL", "").strip()),
        "voice_cloud": voice_cloud,
        "auth_enabled": auth_enabled(),
        "sovereign_ready": all((llm_local, not voice_cloud)),
    }


@router.get("/v1/greeting")
async def greeting(stream: bool = True, claims: dict = Depends(require_auth)):
    """LLM-generate a short, varying home-screen greeting (app/llm/greeting_generator.py).

    Takes nothing from the caller but the Bearer token — `user_id` comes ONLY
    from the JWT. Not tied to a session; called when the user opens the app,
    before any session exists. Name comes from `username`; local time is
    resolved from `localTimeZone`, falling back to `country` (ISO code), then
    UTC; language falls back to "english" (not in the DB yet).

    `?stream=true` (default) streams it token-by-token as SSE (token / done /
    error); `?stream=false` returns `{"greeting": "..."}` as one JSON body.
    """
    user_id = user_id_from_claims(claims)
    if not user_id:
        raise HTTPException(status_code=400, detail="user_id not found in JWT.")

    if stream:
        return StreamingResponse(
            _greeting_event_stream(user_id),
            media_type="text/event-stream",
            headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
        )

    text = await run_in_threadpool(generate_greeting, user_id)
    return JSONResponse(content={"greeting": text})


async def _greeting_event_stream(user_id: str) -> AsyncGenerator[str, None]:
    queue: asyncio.Queue = asyncio.Queue()
    loop = asyncio.get_event_loop()

    def _put(event) -> None:
        loop.call_soon_threadsafe(queue.put_nowait, event)

    def _run() -> None:
        try:
            text = generate_greeting_stream(
                user_id, on_token=lambda t: _put({"type": "token", "text": t})
            )
            _put({"type": "done", "greeting": text})
        except Exception as exc:  # noqa: BLE001
            logger.exception("greeting.stream_error")
            _put({"type": "error", "detail": str(exc)})
        finally:
            _put(None)  # sentinel

    threading.Thread(target=_run, daemon=True).start()

    while True:
        event = await queue.get()
        if event is None:
            break
        yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"


@router.post("/v1/webhook", deprecated=True)
async def webhook(
    request: WebhookRequest,
    stream: bool = False,
    _claims: dict = Depends(require_auth),
):
    """DEPRECATED — use `POST /v1/sessions/start` then `POST /v1/sessions/{id}/turn`.

    Retained as a thin shim so legacy callers keep working during migration.
    `?stream=false` (default) returns the full result as one JSON body;
    `?stream=true` streams the reply token-by-token as SSE.
    """
    if stream:
        return StreamingResponse(
            _event_stream(request),
            media_type="text/event-stream",
            headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
        )

    # Non-stream: run the same turn in a worker thread (handle_webhook_stream is
    # blocking) so we don't stall the event loop, then return the authoritative
    # result — the same dict the streaming "done" event carries.
    try:
        result = await run_in_threadpool(get_service().handle_webhook_stream, request)
    except Exception as exc:  # noqa: BLE001
        logger.exception("webhook.error")
        return JSONResponse(status_code=500, content={"type": "error", "detail": str(exc)})
    return JSONResponse(content=result)


async def _event_stream(request: WebhookRequest) -> AsyncGenerator[str, None]:
    queue: asyncio.Queue = asyncio.Queue()
    loop = asyncio.get_event_loop()
    service = get_service()

    def _put(event) -> None:
        loop.call_soon_threadsafe(queue.put_nowait, event)

    def _run() -> None:
        try:
            result = service.handle_webhook_stream(
                request,
                on_status=lambda msg: _put({"type": "status", "msg": msg}),
                on_token=lambda text: _put({"type": "token", "text": text}),
            )
            _put({"type": "done", **result})
        except Exception as exc:  # noqa: BLE001
            logger.exception("webhook.stream_error")
            _put({"type": "error", "detail": str(exc)})
        finally:
            _put(None)  # sentinel

    threading.Thread(target=_run, daemon=True).start()

    while True:
        event = await queue.get()
        if event is None:
            break
        yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
