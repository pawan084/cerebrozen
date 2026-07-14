"""Unified, parallel placeholder resolver — the replacement for the root
`placeholder_replacement.py`.

A prompt's placeholders are the contract between the (business-edited) prompt and
the data layer. This one resolver handles BOTH kinds in a single pass:

  - RAG placeholders  → tokens bound (in the registry) to an extraction. Their
    PRESENCE in the rendered prompt is what triggers retrieval — no message
    counting, no app-side trigger. Each fires `extract(extract_id, params)`.
  - Context placeholders → everything else ({userName}, {userPosition}, …),
    resolved from the turn context dict.

Both kinds are resolved CONCURRENTLY (RAG calls do embedding + maybe a cheap LLM,
so they dominate latency). The whole step runs BEFORE the node's single streamed
call, so the constitution's "one streamed call per turn" holds.

Defensive: an unresolved/failed token is left as-is (RAG) or dropped to "" only
when the extraction explicitly returns null with a null_text. Resolution never
raises into the caller.
"""

from __future__ import annotations

import contextvars
import logging
import re
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Any, Dict, Optional

from app.rag.extractors import extract
from app.rag.registry import get_registry
from app.request_context import ctx_session_id as _sid_ctx
from app.request_context import request_id as _req_id_ctx

logger = logging.getLogger("cerebrozen.rag")

# Same token grammar as the old resolver: {Word}, {with_underscores}, {a&b}, {a-b}.
# "." is included so dot-path nested-variable tokens resolve — e.g.
# {coaching_style_context.selected_style}, which mirrors the Mongo dot-path
# nesting written by app/stores/dynamic_vars.py (see _resolve_context below).
PLACEHOLDER_RE = re.compile(r"\{[A-Za-z0-9_.&\-]+\}")

_MISSING = object()

_MAX_WORKERS = 8


class PlaceholderResolver:
    """Find every placeholder in a prompt and replace it — RAG tokens via the
    extraction layer, the rest via context lookup — in parallel."""

    def __init__(
        self,
        context: Optional[Dict[str, Any]] = None,
        *,
        user_context: Optional[Dict[str, Any]] = None,
        rag_enabled: bool = True,
    ) -> None:
        # `context` carries both the user/profile fields (for context placeholders)
        # and the RAG query params (org_id, user_message, conversation, level, role,
        # skill, user_id, session_id). `user_context` is accepted as a back-compat
        # alias for the old call sites.
        self.context: Dict[str, Any] = {**(user_context or {}), **(context or {})}
        self.rag_enabled = rag_enabled
        self._registry = get_registry()

    # --- public API ----------------------------------------------------------

    def resolve_text(self, text: str) -> str:
        if not text:
            return text
        tokens = self._unique_tokens(text)
        if not tokens:
            return text

        resolved = self._resolve_all(tokens)
        blanked: list[str] = []

        def _sub(match: "re.Match[str]") -> str:
            token = match.group(0)
            if token in resolved:
                return resolved[token]
            # Unresolved. A registered RAG token whose extraction errored is left
            # as-is (existing behaviour — re-tried next turn). Any OTHER unresolved
            # token is an unprovided CONTEXT placeholder, e.g. {user_name} with no
            # value source — blank it so a raw {token} can never reach the model or
            # leak to the user.
            name = token.strip("{}").strip()
            if self.rag_enabled and self._registry.by_token(name) is not None:
                return token  # RAG extraction failed this turn — leave for next turn
            blanked.append(token)
            return ""

        result = PLACEHOLDER_RE.sub(_sub, text)
        if blanked:
            logger.info(
                "placeholder.blanked_unresolved",
                extra={
                    "user_id": self.context.get("user_id", ""),
                    "session_id": _sid_ctx.get(""),
                    "request_id": _req_id_ctx.get(""),
                    "tokens": blanked,
                    "count": len(blanked),
                },
            )
        return result

    # --- resolution ----------------------------------------------------------

    def _unique_tokens(self, text: str) -> list[str]:
        seen, ordered = set(), []
        for tok in PLACEHOLDER_RE.findall(text):
            if tok not in seen:
                seen.add(tok)
                ordered.append(tok)
        return ordered

    def _resolve_all(self, tokens: list[str]) -> Dict[str, str]:
        """Resolve tokens concurrently. RAG tokens (embedding + maybe LLM) run in a
        thread pool; context tokens are cheap and resolved inline."""
        out: Dict[str, str] = {}
        rag_tokens: list[str] = []

        for tok in tokens:
            name = tok.strip("{}").strip()
            if self.rag_enabled and self._registry.by_token(name) is not None:
                rag_tokens.append(tok)
            else:
                value = self._resolve_context(name)
                if value is not None:
                    out[tok] = value

        if not rag_tokens:
            return out

        # Attribute the whole RAG invocation to the calling agent + capture the
        # input snapshot (the per-extraction lines add query/candidates/output).
        logger.info(
            "rag.invocation",
            extra={
                "invoking_agent": self.context.get("invoking_agent", ""),
                "tokens": rag_tokens,
                "user_id": self.context.get("user_id", ""),
                "session_id": self.context.get("session_id", ""),
                "request_id": _req_id_ctx.get(""),
                "org_id": self.context.get("org_id", ""),
                "user_message": str(self.context.get("user_message", ""))[:240],
                "has_conversation": bool(self.context.get("conversation")),
            },
        )

        # ThreadPoolExecutor workers do NOT inherit the calling thread's ContextVars
        # (request_id / ctx_user_id / ctx_session_id) by default — copy_context() +
        # ctx.run() propagates them explicitly, so every rag.extract/rag.retrieved
        # log emitted inside a worker still carries the right request_id.
        # IMPORTANT: a fresh copy PER TASK, not one shared Context — Context.run()
        # is not reentrant and raises RuntimeError if the same Context object is
        # entered concurrently from more than one thread (hits with >1 RAG token/prompt).
        with ThreadPoolExecutor(max_workers=min(_MAX_WORKERS, len(rag_tokens))) as pool:
            future_to_tok = {
                pool.submit(contextvars.copy_context().run, self._resolve_rag, tok): tok
                for tok in rag_tokens
            }
            for fut in as_completed(future_to_tok):
                tok = future_to_tok[fut]
                try:
                    value = fut.result()
                except Exception as exc:  # noqa: BLE001
                    logger.warning("rag.placeholder_failed", extra={"token": tok, "error": str(exc)})
                    value = None
                if value is not None:
                    out[tok] = value
        return out

    def _resolve_rag(self, token: str) -> Optional[str]:
        """Run the bound extraction and return its formatted text block.

        - resolved → the formatted block (may be "").
        - null     → the extraction's null_text ("" leaves the token cleared).
        - error    → None, so the token is left as-is in the prompt.
        """
        name = token.strip("{}").strip()
        ex = self._registry.by_token(name)
        if ex is None:
            return None
        result = extract(ex.extract_id, self.context)
        if result.status == "error":
            return None
        return result.formatted  # "" for a null with no null_text → token cleared

    def _resolve_context(self, name: str) -> Optional[str]:
        """Look up a non-RAG placeholder from the turn context (case-insensitive,
        a couple of sensible defaults). Returns None to leave the token untouched."""
        ctx = self.context
        if name in ctx and ctx[name] is not None:
            return _stringify(ctx[name])
        lower = name.lower()
        for key, val in ctx.items():
            if isinstance(key, str) and key.lower() == lower and val is not None:
                return _stringify(val)
        if "." in name:
            # Dot-path token, e.g. {coaching_style_context.selected_style}. The
            # context stores this as a nested dict (Mongo dot-path writes
            # deserialize that way — see dynamic_vars.py), so a flat lookup on
            # the full dotted name above always misses; walk it segment by segment.
            nested = self._resolve_dotted(name)
            if nested is not None:
                return _stringify(nested)
        if name == "userName":
            return _stringify(ctx.get("name") or ctx.get("username") or "")
        if name == "Time":
            from datetime import datetime, timezone

            # HOUR granularity, deliberately — not microseconds.
            #
            # {Time} sits ~130 tokens into a system prompt that runs to ~27,000 tokens.
            # With microsecond precision it changed on EVERY turn, so the prompt prefix was
            # unique every time and the LLM prompt cache could never hit: ~0% cached, and
            # ~21,000 tokens re-encoded per turn (measured). Rounding to the hour makes the
            # whole instruction body byte-identical across a session — the same fix that
            # makes a local/offline model (Ollama KV prefix reuse) viable at all.
            #
            # Nothing needs sub-hour resolution: the prompts use {Time} for time-of-day
            # awareness ("good morning"), not for timing.
            return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:00Z")
        return None

    def _resolve_dotted(self, name: str) -> Any:
        """Walk a dotted token name through nested dicts in the context
        (case-insensitive per segment). Returns None if any segment is missing
        or the path bottoms out on a non-dict before the last segment."""
        node: Any = self.context
        for part in name.split("."):
            if not isinstance(node, dict):
                return None
            if part in node:
                node = node[part]
                continue
            lower_part = part.lower()
            match = _MISSING
            for key, val in node.items():
                if isinstance(key, str) and key.lower() == lower_part:
                    match = val
                    break
            if match is _MISSING:
                return None
            node = match
        return node


def _stringify(value: Any) -> str:
    if isinstance(value, str):
        return value
    if isinstance(value, (list, tuple)):
        return ", ".join(str(v) for v in value)
    if isinstance(value, dict):
        return "; ".join(f"{k}: {v}" for k, v in value.items())
    return str(value)
