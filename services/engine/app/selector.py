"""Strangler-fig selector — decides whether the new graph serves a request.

Everything runs on the new system; this is the reversible safety valve, not a
router to a legacy backend. Precedence (first match wins):
  1. per-request override   (metadata.use_graph: true/false)
  2. blocklist              (force OFF for these user_ids)
  3. allowlist              (force ON for these user_ids)
  4. global flag + % rollout
Deterministic per user_id, so a given user always lands the same way.
"""

from __future__ import annotations

import hashlib
from typing import Any, Dict, Optional, Tuple

from app import config


def _bucket(user_id: str) -> int:
    """Stable 0–99 bucket for a user (deterministic % rollout)."""
    digest = hashlib.md5((user_id or "").encode("utf-8")).hexdigest()
    return int(digest, 16) % 100


def route_to_graph(
    user_id: str, metadata: Optional[Dict[str, Any]] = None
) -> Tuple[bool, str]:
    """Return (serve_with_graph, reason)."""
    md = metadata or {}

    if "use_graph" in md:
        return bool(md["use_graph"]), "request_override"

    if user_id and user_id in config.GRAPH_BLOCKLIST:
        return False, "blocklist"

    if user_id and user_id in config.GRAPH_ALLOWLIST:
        return True, "allowlist"

    if config.GRAPH_ENABLED:
        if config.GRAPH_PERCENT >= 100 or _bucket(user_id) < config.GRAPH_PERCENT:
            return True, "enabled"
        return False, "percent_excluded"

    # Globally disabled — still allow a ramp-up cohort via percentage.
    if config.GRAPH_PERCENT > 0 and _bucket(user_id) < config.GRAPH_PERCENT:
        return True, "percent_rampup"
    return False, "disabled"
