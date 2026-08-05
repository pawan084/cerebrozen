import asyncio
import logging
import os
import time
import uuid
from contextlib import asynccontextmanager, suppress
from pathlib import Path

from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy import text
from starlette.middleware.trustedhost import TrustedHostMiddleware
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.middleware import SlowAPIMiddleware

from app.api.router import api_router
from app.core.config import settings
from app.core.database import SessionLocal
from app.core.ratelimit import limiter
from app.services import media
from app.services import digest as digest_service
from app.services import idempotency as idempotency_service
from app.services import nudges as nudges_service

__version__ = "0.1.0"

logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger("cerebro.main")


async def _nudge_dispatcher() -> None:
    """Periodic nudge delivery. Safe with multiple workers: dispatch_due claims
    due rows with FOR UPDATE SKIP LOCKED, so each nudge is sent exactly once."""
    interval = settings.nudge_dispatch_interval_minutes * 60
    # The weekly digest rides the same loop rather than adding a second timer.
    # `schedule_digest` is idempotent per ISO week and per pending nudge, so
    # running the pass on every tick is cheap and self-correcting — a restart
    # or a missed tick can't skip or duplicate a week.
    while True:
        await asyncio.sleep(interval)
        try:
            async with SessionLocal() as db:
                queued = await digest_service.run_weekly_pass(db)
            if queued:
                logger.info("Weekly digest: %d queued", queued)
        except Exception:  # noqa: BLE001 - keep the loop alive
            logger.exception("Weekly digest pass failed")
        try:
            async with SessionLocal() as db:
                sent = await nudges_service.dispatch_due(db)
            if sent:
                logger.info("Nudge dispatcher: %d sent", sent)
        except Exception:  # noqa: BLE001 - keep the loop alive
            logger.exception("Nudge dispatch pass failed")
        # Offline-queue replay records age out here rather than on a timer of
        # their own — the pass is idempotent and cheap, and one loop is one
        # thing to reason about when a worker misbehaves.
        try:
            async with SessionLocal() as db:
                purged = await idempotency_service.purge_expired(db)
            if purged:
                logger.info("Idempotency purge: %d expired", purged)
        except Exception:  # noqa: BLE001 - keep the loop alive
            logger.exception("Idempotency purge failed")


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Warm the Oracle graph (and its Postgres checkpointer) BEFORE serving
    # traffic: langgraph's setup() issues CREATE INDEX CONCURRENTLY, which
    # waits on every open transaction — at startup none exist, but at
    # first-request time an idle-in-transaction pool connection can block it
    # indefinitely (found via a hung first /oracle/messages on a fresh DB).
    if settings.oracle_available and os.getenv("TESTING") != "1":
        try:
            from app.agent.graph import get_graph

            await get_graph()
        except Exception:  # noqa: BLE001 — degraded is fine; never fatal at boot
            logger.exception("Oracle graph warmup failed; it will retry lazily")
    dispatcher = None
    if settings.nudge_dispatch_interval_minutes > 0 and os.getenv("TESTING") != "1":
        dispatcher = asyncio.create_task(_nudge_dispatcher())
    yield
    if dispatcher is not None:
        dispatcher.cancel()
        with suppress(asyncio.CancelledError):
            await dispatcher


app = FastAPI(
    title="CereBro API",
    version=__version__,
    description="Backend for the CereBro mental-wellness app: auth, user data, and "
    "proactive AI (agentic plans, nudges, insights, safety, voice).",
    lifespan=lifespan,
    docs_url=None if settings.is_production else "/docs",
    redoc_url=None if settings.is_production else "/redoc",
    openapi_url=None if settings.is_production else "/openapi.json",
)

if settings.is_production:
    app.add_middleware(TrustedHostMiddleware, allowed_hosts=settings.trusted_hosts)

# Rate limiting (auth endpoints opt in via @limiter.limit).
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)
app.add_middleware(SlowAPIMiddleware)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def request_context(request: Request, call_next) -> Response:
    """Attach a safe correlation id and record one structured access event."""
    supplied = request.headers.get("X-Request-ID", "")
    request_id = (
        supplied
        if 0 < len(supplied) <= 128 and supplied.isascii()
        else uuid.uuid4().hex
    )
    request.state.request_id = request_id
    started = time.perf_counter()
    try:
        response = await call_next(request)
    except Exception:  # noqa: BLE001 - convert unknown failures at the app boundary
        logger.exception(
            "request_failed method=%s path=%s request_id=%s",
            request.method,
            request.url.path,
            request_id,
        )
        response = JSONResponse(
            status_code=500,
            content={"detail": "Internal server error", "request_id": request_id},
        )
    elapsed_ms = (time.perf_counter() - started) * 1000
    response.headers["X-Request-ID"] = request_id
    response.headers["Server-Timing"] = f"app;dur={elapsed_ms:.2f}"
    logger.info(
        "request_complete method=%s path=%s status=%d duration_ms=%.2f request_id=%s",
        request.method,
        request.url.path,
        response.status_code,
        elapsed_ms,
        request_id,
    )
    return response


@app.middleware("http")
async def security_headers(request: Request, call_next) -> Response:
    """Baseline hardening headers on every response."""
    resp = await call_next(request)
    resp.headers.setdefault("X-Content-Type-Options", "nosniff")
    resp.headers.setdefault("X-Frame-Options", "DENY")
    resp.headers.setdefault("Referrer-Policy", "no-referrer")
    return resp


@app.middleware("http")
async def media_guard(request: Request, call_next) -> Response:
    """Require a signed grant for narration audio.

    The files are served by a StaticFiles mount, which knows how to do Range and
    ETag but nothing about who is asking — so the check happens here, in front of
    it. Without this, a premium sleep story's MP3 was fetchable by anyone who
    knew (or guessed) its URL, with no entitlement check anywhere in the path.

    Grants are minted per item in ``services.media.playback_url`` and carried as
    ``?t=``, because the players that fetch these files cannot send headers.
    """
    path = request.url.path
    if path.startswith("/media/narration/") and request.method in {"GET", "HEAD"}:
        if not media.token_authorizes(request.query_params.get("t"), path):
            # 404, not 403: whether a given narration exists is itself not
            # something an unauthorized caller should be able to probe.
            return JSONResponse({"detail": "Not found"}, status_code=404)
    return await call_next(request)


app.include_router(api_router)

# Media bytes (narration MP3s, catalogue assets). StaticFiles serves Range/ETag
# so native players can stream and seek; `media_guard` above gates access to
# premium narration (the mount itself cannot ask "is this person allowed?").
#
# ORDER MATTERS: this mount must stay *below* include_router. The media router
# owns GET /media/catalog under the same prefix, and Starlette matches routes in
# registration order — mounting first would make the mount swallow /media/catalog
# and look for a file called "catalog" on disk. test_media_catalog.py locks this.
Path(settings.media_root).mkdir(parents=True, exist_ok=True)
app.mount("/media", StaticFiles(directory=settings.media_root), name="media")


@app.get("/health", tags=["meta"])
async def health():
    return {"status": "ok", "version": __version__, "ai_enabled": settings.ai_enabled}


@app.get("/ready", tags=["meta"])
async def ready():
    """Report readiness only after PostgreSQL accepts a trivial query."""
    try:
        async with SessionLocal() as db:
            await db.execute(text("SELECT 1"))
    except Exception:  # noqa: BLE001 - dependency details must not leak publicly
        return JSONResponse(
            status_code=503, content={"status": "not_ready", "database": "unavailable"}
        )
    return {"status": "ready", "database": "ok"}


# Versioned operational aliases for new integrations. Legacy routes remain the
# canonical client contract and are intentionally untouched.
app.add_api_route(
    "/api/v1/health", health, methods=["GET"], tags=["meta"], include_in_schema=False
)
app.add_api_route(
    "/api/v1/ready", ready, methods=["GET"], tags=["meta"], include_in_schema=False
)
