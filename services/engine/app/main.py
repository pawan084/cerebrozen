"""FastAPI app factory for the LangGraph CIM skeleton."""

from __future__ import annotations

# Load local env (env-dev.ps1) before any config-reading import, so running via
# `uvicorn app.main:app` also works without a sourced shell.
from app.env_loader import load_local_env

load_local_env()

import logging  # noqa: E402
import uuid  # noqa: E402

from fastapi import FastAPI, Request  # noqa: E402
from fastapi.middleware.cors import CORSMiddleware  # noqa: E402
from fastapi.responses import JSONResponse  # noqa: E402
from fastapi.staticfiles import StaticFiles  # noqa: E402

from app.auth import AuthorizationError  # noqa: E402
from app.config import CORS_ALLOW_ORIGINS  # noqa: E402
from app.observability import configure_logging  # noqa: E402
from app.routers.api import router
from app.routers.flow import router as flow_router
from app.routers.privacy import router as privacy_router
from app.routers.prompts import router as prompts_router
from app.routers.rag import router as rag_router
from app.routers.sessions import router as sessions_router
# Voice depends on the (heavy, optional) livekit stack. Import defensively so the
# app boots without livekit installed; the voice routes are simply not mounted.
try:
    from app.routers.voice import router as voice_router, voice_lab_router
except Exception as _voice_exc:  # noqa: BLE001
    voice_router = None
    voice_lab_router = None
    logging.getLogger("cerebrozen.main").warning(
        "voice.router_disabled", extra={"error": str(_voice_exc)}
    )
from app.service import get_service

logger = logging.getLogger("cerebrozen.main")


def create_app() -> FastAPI:
    configure_logging()
    # OTLP traces/metrics → ADOT collector → X-Ray/Prometheus (no-op when OTEL off).
    from app.tracing_otel import configure_tracing

    configure_tracing()
    app = FastAPI(title="CereBroZen LangGraph", version="0.1.0")

    # Browser frontends call /v1/webhook
    # cross-origin, so the preflight needs Access-Control-Allow-Origin. Origins
    # are env-driven; "*" can't be combined with credentials, so only allow
    # credentials when explicit origins are configured.
    _allow_all = CORS_ALLOW_ORIGINS == ["*"]
    app.add_middleware(
        CORSMiddleware,
        allow_origins=CORS_ALLOW_ORIGINS,
        allow_credentials=not _allow_all,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Mint a request_id for every user-facing request so all log records within
    # a single turn share the same correlation ID.  System endpoints (health,
    # metrics) are excluded — their logs intentionally carry no request context.
    _EXCLUDED_PREFIXES = ("/health", "/metrics")

    @app.middleware("http")
    async def _request_id_middleware(request: Request, call_next):
        if any(request.url.path.startswith(p) for p in _EXCLUDED_PREFIXES):
            return await call_next(request)
        from app.request_context import ctx_session_id, ctx_user_id, request_id
        # Set without try/finally: for SSE streaming, BaseHTTPMiddleware runs the
        # finally block before the response body is consumed, which would reset
        # the vars before the worker thread starts.  Each asyncio task owns an
        # isolated context copy, so there is no cross-request leakage without reset.
        request_id.set(uuid.uuid4().hex)
        ctx_user_id.set("")
        ctx_session_id.set("")

        # --- Access log: emit one structured log per HTTP request so CloudWatch
        # Logs Insights can power the "API Hit Count" Grafana panel. Uses the
        # FastAPI route TEMPLATE (e.g. /v1/sessions/{session_id}/turn) so
        # parameterised paths group correctly; falls back to the raw URL path.
        import time as _time

        _t0 = _time.perf_counter()
        response = await call_next(request)
        _elapsed_ms = round((_time.perf_counter() - _t0) * 1000, 1)

        # Resolve route template: FastAPI stores the matched route on the scope.
        _route = getattr(request.scope.get("route"), "path", None) or request.url.path
        _service_name = f"{request.method}{_route}"

        logger.info(
            "http.request",
            extra={
                "method": request.method,
                "route": _route,
                "service_name": _service_name,
                "status_code": response.status_code,
                "response_time_ms": _elapsed_ms,
            },
        )
        return response

    app.include_router(router)
    app.include_router(prompts_router)
    app.include_router(rag_router)
    app.include_router(sessions_router)
    app.include_router(flow_router)
    app.include_router(privacy_router)
    if voice_router is not None:
        app.include_router(voice_router)
    if voice_lab_router is not None:
        app.include_router(voice_lab_router)

    # Browser chat UI — static HTML served at "/" (app/static/index.html). The
    # HTML itself is public; API calls from it run through the auth dev-bypass.
    import os as _os
    from fastapi.responses import HTMLResponse

    _static_dir = _os.path.join(_os.path.dirname(__file__), "static")
    _index_path = _os.path.join(_static_dir, "index.html")

    def _serve(name: str, fallback: str) -> HTMLResponse:
        try:
            with open(_os.path.join(_static_dir, name), "r", encoding="utf-8") as _f:
                return HTMLResponse(_f.read())
        except FileNotFoundError:
            return HTMLResponse(fallback)

    @app.get("/", response_class=HTMLResponse)
    async def _root() -> HTMLResponse:
        # The unified workbench shell (Chat / Flows / Prompts / Console).
        return _serve("workbench.html", "<h1>CereBroZen Workbench</h1><p>UI not found.</p>")

    @app.get("/chat", response_class=HTMLResponse)
    async def _chat_ui() -> HTMLResponse:
        return _serve("index.html", "<h1>CereBroZen Chat</h1><p>UI not found.</p>")

    @app.get("/prompts", response_class=HTMLResponse)
    async def _prompts_ui() -> HTMLResponse:
        _p = _os.path.join(_static_dir, "prompts.html")
        try:
            with open(_p, "r", encoding="utf-8") as _f:
                return HTMLResponse(_f.read())
        except FileNotFoundError:
            return HTMLResponse("<h1>Prompt Registry</h1><p>UI not found.</p>")

    @app.get("/flow", response_class=HTMLResponse)
    async def _flow_ui() -> HTMLResponse:
        _p = _os.path.join(_static_dir, "flow.html")
        try:
            with open(_p, "r", encoding="utf-8") as _f:
                return HTMLResponse(_f.read())
        except FileNotFoundError:
            return HTMLResponse("<h1>Agent Flow</h1><p>UI not found.</p>")

    if _os.path.isdir(_static_dir):
        app.mount("/static", StaticFiles(directory=_static_dir, html=True), name="static")

    # Test UI — static HTML (testui/index.html) served at /testui.
    # JWT is required for all API calls from the UI; the HTML itself is public.
    _testui_dir = _os.path.join(_os.path.dirname(_os.path.dirname(__file__)), "testui")
    if _os.path.isdir(_testui_dir):
        app.mount("/testui", StaticFiles(directory=_testui_dir, html=True), name="testui")

    # Prometheus scrape target (no-op if prometheus_client isn't installed).
    from app.metrics import metrics_asgi_app

    _metrics = metrics_asgi_app()
    if _metrics is not None:
        app.mount("/metrics", _metrics)

    # Map auth failures to 401 {"message": ...} (matches the other services).
    @app.exception_handler(AuthorizationError)
    async def _auth_error(_request: Request, exc: AuthorizationError) -> JSONResponse:
        return JSONResponse(status_code=401, content={"message": exc.message})

    @app.on_event("startup")
    def _warm() -> None:
        # Build the graph, then eagerly warm the prompt registry (the S3 download
        # in s3 mode) and the LLM client so the FIRST turn pays no cold-start cost
        # and the prompt source is confirmed in the boot logs. Warming is best-
        # effort: a hiccup falls back to lazy init on the first turn rather than
        # failing startup.
        from app.graph.runtime import get_client, get_registry

        get_service()
        try:
            get_registry()  # loads workbook (s3 download in s3 mode)
            get_client()    # constructs + wraps the OpenAI client
        except Exception:  # noqa: BLE001
            logger.exception("warm.failed")
        # RAG vector store: connect to the (S3) index + incrementally ingest any
        # new/changed docs (or full reindex if RAG_REINDEX). Best-effort — a RAG
        # hiccup must never block the service from starting.
        try:
            from app.config import RAG_REINDEX
            from app.rag.startup import run_startup

            run_startup(RAG_REINDEX)
        except Exception:  # noqa: BLE001
            logger.exception("rag.startup_failed")
        logger.info("app.ready")

    return app


app = create_app()
