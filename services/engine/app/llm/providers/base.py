"""LLM provider interface.

One thin contract over a text LLM so graph nodes don't bind to a single vendor.
The OpenAI Responses implementation (`OpenAIResponsesClient`) is the only one
shipped now — the constitution keeps the Responses API (Art. 5.1). Gemini /
Anthropic implementations are deferred to the Phase 2 model bake-off (new
provider deps need approval); until then the factory raises for them.

A provider exposes exactly the two calls a node makes — `generate` and
`generate_stream` — returning the existing `LLMResponse`, so swapping the
provider is a config change, not a node change.
"""

from __future__ import annotations

from typing import Any, Dict, List, Optional, Protocol, runtime_checkable

from app.llm.responses_client import LLMResponse


@runtime_checkable
class LLMProvider(Protocol):
    """The surface a graph node depends on. Implementations own their own
    timeout / retry / cascade / breaker semantics (Art. 10)."""

    def generate(
        self,
        system_prompt: str,
        user_prompt: str,
        model: str,
        reasoning_effort: Optional[str] = None,
        history: Optional[List[Dict[str, str]]] = None,
    ) -> LLMResponse:
        ...

    def generate_stream(
        self,
        system_prompt: str,
        user_prompt: str,
        model: str,
        on_token: Any = None,
        reasoning_effort: Optional[str] = None,
        history: Optional[List[Dict[str, str]]] = None,
    ) -> LLMResponse:
        ...
