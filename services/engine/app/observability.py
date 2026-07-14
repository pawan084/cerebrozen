"""Minimal structured JSON logging to logs/cerebrozen.log.

A trimmed version of the old repo's observability layer — enough for the
Streamlit UI's token/latency tiles and log viewer to work, without the
Prometheus / middleware machinery (out of scope for Phase 1).
"""

from __future__ import annotations

import json
import logging
import logging.handlers
from datetime import datetime, timezone
from pathlib import Path

from app.config import REPO_ROOT

LOG_DIR = REPO_ROOT / "logs"
LOG_FILE = LOG_DIR / "cerebrozen.log"

_STANDARD = set(
    logging.LogRecord("", 0, "", 0, "", (), None).__dict__.keys()
) | {"message", "asctime", "taskName"}

# Correlation fields — placed first in every JSON record for easy grep/query.
_CORRELATION = ("request_id", "user_id", "session_id")


class RequestContextFilter(logging.Filter):
    """Stamp every log record with the current request's correlation IDs.

    Reads from ContextVars set by the HTTP middleware and the session router.
    When the vars are empty (startup, health checks) nothing is added, so those
    logs stay clean.  When a record already carries user_id / session_id from
    an explicit `extra={}` that value is kept; only missing / empty fields are
    filled from context.
    """

    def filter(self, record: logging.LogRecord) -> bool:
        from app.request_context import ctx_session_id, ctx_user_id, request_id

        rid = request_id.get()
        if rid and not getattr(record, "request_id", ""):
            record.request_id = rid  # type: ignore[attr-defined]

        if not getattr(record, "user_id", ""):
            uid = ctx_user_id.get()
            if uid:
                record.user_id = uid  # type: ignore[attr-defined]

        if not getattr(record, "session_id", ""):
            sid = ctx_session_id.get()
            if sid:
                record.session_id = sid  # type: ignore[attr-defined]

        return True


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: dict = {
            "ts": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "event": record.getMessage(),
        }
        # Correlation fields always appear right after `event`, present only when set.
        for field in _CORRELATION:
            v = getattr(record, field, "")
            if v:
                payload[field] = v
        # Remaining custom fields (skip correlation — already added above).
        _skip = _STANDARD | set(_CORRELATION)
        for key, value in record.__dict__.items():
            if key in _skip or key.startswith("_"):
                continue
            try:
                json.dumps(value)
                payload[key] = value
            except (TypeError, ValueError):
                payload[key] = str(value)
        if record.exc_info:
            payload["exc"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=False)


def configure_logging(level: int = logging.INFO) -> None:
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    root = logging.getLogger()
    if any(getattr(h, "_cerebrozen", False) for h in root.handlers):
        return
    root.setLevel(level)

    # Quiet noisy third-party loggers so the console + log file stay readable. Under
    # the LiveKit `dev` CLI (which sets root to DEBUG) pymongo's awaited heartbeats
    # otherwise flood the logs every ~10s and bury the events that matter.
    for noisy in ("pymongo", "httpx", "httpcore", "urllib3", "websockets", "openai", "asyncio"):
        logging.getLogger(noisy).setLevel(logging.WARNING)
    # OTEL retry warnings are very spammy when the collector is unreachable (503 loop).
    # Suppress to ERROR so transient retries don't bury real log events.
    logging.getLogger("opentelemetry.exporter.otlp").setLevel(logging.ERROR)

    # Attach the context filter to the HANDLERS, not the root logger.
    # root.addFilter() only fires for records logged directly on root; records
    # from child loggers (cerebrozen.nodes, cerebrozen.trace, …) propagate via
    # callHandlers() which skips root.handle() entirely, so root filters never run.
    # Handler filters fire for every record that reaches the handler, regardless
    # of which logger emitted it.
    _ctx_filter = RequestContextFilter()

    file_handler = logging.handlers.TimedRotatingFileHandler(
        LOG_FILE, when="midnight", backupCount=7, encoding="utf-8"
    )
    file_handler.setFormatter(JsonFormatter())
    file_handler.addFilter(_ctx_filter)
    file_handler._cerebrozen = True  # type: ignore[attr-defined]
    root.addHandler(file_handler)

    console = logging.StreamHandler()
    console.setFormatter(JsonFormatter())
    console.addFilter(_ctx_filter)
    console._cerebrozen = True  # type: ignore[attr-defined]
    root.addHandler(console)
