"""Lazy singletons shared by graph nodes (LLM client + prompt registry).

Kept out of nodes.py so the engine can trigger a prompt reload (edit-without-
redeploy) without reaching into node internals.
"""

from __future__ import annotations

from typing import Optional

from app.llm.prompts import PromptRegistry
from app.llm.providers import get_provider
from app.llm.providers.base import LLMProvider

_registry: Optional[PromptRegistry] = None


def get_client() -> LLMProvider:
    """The LLM provider nodes call. Delegates to the provider factory so the
    backend is a config choice (`CEREBROZEN_LLM_PROVIDER`), not wired into nodes."""
    return get_provider()


def get_registry() -> PromptRegistry:
    global _registry
    if _registry is None:
        _registry = PromptRegistry()
    return _registry


def reload_prompts() -> None:
    get_registry().reload()
