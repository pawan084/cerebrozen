import asyncio
import logging
import os
from contextlib import asynccontextmanager, suppress
from pathlib import Path

from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.middleware import SlowAPIMiddleware

from app.api.router import api_router
from app.core.config import settings
from app.core.database import SessionLocal
from app.core.ratelimit import limiter
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
    while True:
        await asyncio.sleep(interval)
        try:
            async with SessionLocal() as db:
                sent = await nudges_service.dispatch_due(db)
            if sent:
                logger.info("Nudge dispatcher: %d sent", sent)
        except Exception:  # noqa: BLE001 - keep the loop alive
            logger.exception("Nudge dispatch pass failed")


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
)

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
async def security_headers(request: Request, call_next) -> Response:
    """Baseline hardening headers on every response."""
    resp = await call_next(request)
    resp.headers.setdefault("X-Content-Type-Options", "nosniff")
    resp.headers.setdefault("X-Frame-Options", "DENY")
    resp.headers.setdefault("Referrer-Policy", "no-referrer")
    return resp


app.include_router(api_router)

# Media bytes (narration MP3s, catalogue assets) — public like /content;
# StaticFiles serves Range/ETag so native players can stream and seek.
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
