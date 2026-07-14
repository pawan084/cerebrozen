"""Execution tracing — verbose, env-gated per-agent I/O logging.

A single helper, `io(event, **fields)`, writes a structured record to the
`cerebrozen.trace` logger so you can follow a turn end-to-end: what profile_read
extracted, what went INTO each agent (resolved system prompt, user message,
history) and what came OUT (raw model text, parsed reply/handoff/path, tokens,
cost), plus the background builders' input/output.

Design notes:
  - OFF by default (config.TRACE_IO). When off, `io()` returns immediately, so
    there is zero formatting/serialization cost on the hot path.
  - Pure logging: no LLM calls, no network — so enabling it does not change turn
    latency, only log volume (and possible PII in the logs).
  - Long fields (big prompts, transcripts) are clipped to config.TRACE_CHARS
    (0 = no clipping) so a trace line stays readable.
  - Reserved LogRecord attribute names (msg, args, name, module, …) must not be
    used as field keys; the callers avoid them.
"""

from __future__ import annotations

import json
import logging
from typing import Any

from app import config

logger = logging.getLogger("cerebrozen.trace")


def enabled() -> bool:
    return config.TRACE_IO


def _clip(value: Any) -> Any:
    """Truncate long strings (and large list/dict payloads, serialized) so a
    single trace line stays bounded. TRACE_CHARS <= 0 disables clipping."""
    limit = config.TRACE_CHARS
    if limit <= 0:
        return value
    if isinstance(value, str):
        if len(value) <= limit:
            return value
        return value[:limit] + f"…[+{len(value) - limit} chars]"
    if isinstance(value, (list, dict)):
        serialized = json.dumps(value, ensure_ascii=False, default=str)
        if len(serialized) <= limit:
            return value
        return serialized[:limit] + f"…[+{len(serialized) - limit} chars]"
    return value


def io(event: str, **fields: Any) -> None:
    """Emit one trace record. No-op unless tracing is enabled."""
    if not config.TRACE_IO:
        return
    logger.info(event, extra={k: _clip(v) for k, v in fields.items()})
