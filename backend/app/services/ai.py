"""Thin async wrapper around the configured LLM provider.

The provider is chosen at runtime (see ``settings.ai_provider``): OpenAI when
``OPENAI_API_KEY`` is set, else Anthropic when ``ANTHROPIC_API_KEY`` is set.
Every call degrades gracefully: when no provider is configured (or the call
fails), helpers return ``None`` and the callers fall back to deterministic
local logic. This keeps the whole backend runnable offline.
"""
from __future__ import annotations

import json
import logging
from typing import Any

from app.core.config import settings

logger = logging.getLogger("cerebro.ai")

_anthropic = None
_openai = None


def _get_anthropic():
    global _anthropic
    if _anthropic is None:
        try:
            from anthropic import AsyncAnthropic

            _anthropic = AsyncAnthropic(api_key=settings.anthropic_api_key)
        except Exception as exc:  # pragma: no cover - import/config guard
            logger.warning("Anthropic client unavailable: %s", exc)
            return None
    return _anthropic


def _get_openai():
    global _openai
    if _openai is None:
        try:
            from openai import AsyncOpenAI

            _openai = AsyncOpenAI(api_key=settings.openai_api_key)
        except Exception as exc:  # pragma: no cover - import/config guard
            logger.warning("OpenAI client unavailable: %s", exc)
            return None
    return _openai


async def _complete_openai(system: str, prompt: str, max_tokens: int) -> str | None:
    client = _get_openai()
    if client is None:
        return None
    resp = await client.chat.completions.create(
        model=settings.openai_model,
        max_tokens=max_tokens,
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": prompt},
        ],
    )
    return (resp.choices[0].message.content or "").strip() or None


async def _complete_anthropic(system: str, prompt: str, max_tokens: int) -> str | None:
    client = _get_anthropic()
    if client is None:
        return None
    msg = await client.messages.create(
        model=settings.ai_model,
        max_tokens=max_tokens,
        system=system,
        messages=[{"role": "user", "content": prompt}],
    )
    parts = [block.text for block in msg.content if getattr(block, "type", "") == "text"]
    return "\n".join(parts).strip() or None


async def complete(system: str, prompt: str, max_tokens: int = 1024) -> str | None:
    """Return the model's text response, or None if AI is disabled/failed."""
    provider = settings.ai_provider
    if provider == "none":
        return None
    try:
        if provider == "openai":
            return await _complete_openai(system, prompt, max_tokens)
        return await _complete_anthropic(system, prompt, max_tokens)
    except Exception as exc:
        logger.warning("AI completion failed (%s), using fallback: %s", provider, exc)
        return None


async def complete_json(system: str, prompt: str, max_tokens: int = 1024) -> Any | None:
    """Like :func:`complete` but parses a JSON object/array from the response."""
    text = await complete(system + "\n\nRespond with ONLY valid JSON, no prose.", prompt, max_tokens)
    if not text:
        return None
    # Tolerate code fences / surrounding text.
    start = min((i for i in (text.find("{"), text.find("[")) if i != -1), default=-1)
    if start == -1:
        return None
    end = max(text.rfind("}"), text.rfind("]"))
    try:
        return json.loads(text[start : end + 1])
    except json.JSONDecodeError:
        logger.warning("Could not parse AI JSON response")
        return None
