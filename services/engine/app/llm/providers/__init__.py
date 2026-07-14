"""Provider factory — picks the configured LLM provider (one shared instance).

`CEREBROZEN_LLM_PROVIDER` selects the backend; default `openai` (the Responses-API
client). `gemini` / `anthropic` are reserved for the Phase 2 bake-off and raise
until their implementations + deps are approved. This is the single swap point
the model bake-off flips later — graph nodes call `get_provider()` (via
`runtime.get_client`) and never name a vendor.
"""

from __future__ import annotations

import logging
import os
from typing import Optional

from app.llm.providers.base import LLMProvider

logger = logging.getLogger("cerebrozen.llm.providers")

_provider: Optional[LLMProvider] = None


def get_provider() -> LLMProvider:
    """Return the configured provider as a process-wide singleton."""
    global _provider
    if _provider is None:
        name = os.environ.get("CEREBROZEN_LLM_PROVIDER", "openai").strip().lower()
        # Keyless local/CI fallback: if the OpenAI provider is configured but no
        # API key is present, transparently use the offline mock so the full graph
        # still runs (streams tokens + a terminal event) instead of erroring on
        # every turn. Setting CEREBROZEN_LLM_PROVIDER=mock forces it explicitly.
        _has_key = bool(
            (os.environ.get("OPENAI_API_KEY") or "").strip()
            or (os.environ.get("OPENAI_ADMIN_KEY") or "").strip()
        )
        if name == "mock" or (
            name in ("openai", "responses", "openai_responses") and not _has_key
        ):
            from app.llm.providers.mock import MockLLMClient

            _provider = MockLLMClient()
            logger.warning(
                "llm.provider_mock",
                extra={"reason": "explicit" if name == "mock" else "no_api_key"},
            )
            return _provider
        if name in ("openai", "responses", "openai_responses"):
            from app.llm.responses_client import OpenAIResponsesClient

            _provider = OpenAIResponsesClient()
        elif name == "ollama":
            # Fully-offline backend (no internet, no API key). See providers/ollama.py —
            # viable only because the system-prompt prefix is stable (KV cache hit) and
            # the control envelope is schema-constrained.
            from app.llm.providers.ollama import OllamaClient

            _provider = OllamaClient()
        elif name == "gemini":
            raise NotImplementedError(
                "Gemini provider is deferred to the Phase 2 model bake-off "
                "(needs google-genai dep + eval approval)."
            )
        elif name == "anthropic":
            raise NotImplementedError(
                "Anthropic provider is deferred to the Phase 2 model bake-off "
                "(needs anthropic dep + eval approval)."
            )
        else:
            raise ValueError(f"Unknown CEREBROZEN_LLM_PROVIDER: {name!r}")
        logger.info("llm.provider", extra={"provider": name})
    return _provider


def reset_provider() -> None:
    """Drop the cached provider (tests / provider switch)."""
    global _provider
    _provider = None
