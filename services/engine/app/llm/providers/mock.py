"""Keyless mock LLM provider.

Lets the full reference graph run end-to-end WITHOUT an OPENAI_API_KEY (local
demo / CI smoke). Selected automatically by the provider factory when the OpenAI
provider is configured but no key is present, or explicitly via
CEREBROZEN_LLM_PROVIDER=mock.

It honours the same contract as OpenAIResponsesClient (`generate` /
`generate_stream` → LLMResponse) and returns a JSON control envelope with a
user-facing text field, so the nodes' UserTextStreamer extracts a real reply and
the control fields (handoff_ready, coaching_path, …) parse cleanly. Deterministic,
no network — every stage produces a short canned coaching-style line.
"""

from __future__ import annotations

import json
import logging
import time
from typing import Any, Dict, List, Optional

from app.llm.responses_client import LLMResponse

logger = logging.getLogger("cerebrozen.llm.mock")

_REPLY = (
    "[mock] Thanks for sharing that — I hear this matters to you. "
    "What feels like the hardest part right now?"
)


def _envelope(stage: str) -> Dict[str, Any]:
    # Provide every user-facing key the nodes look for (they read whichever their
    # own schema uses) plus benign control fields so routing stays deterministic.
    return {
        "response_to_user": _REPLY,
        "next_question": _REPLY,
        "clarifying_question": _REPLY,
        "message": _REPLY,
        "response": _REPLY,
        "handoff_ready": False,
        "coaching_path": "CIM",
    }


class MockLLMClient:
    """Deterministic, offline stand-in for OpenAIResponsesClient."""

    def _run(self, stage: str, on_token: Any = None) -> LLMResponse:
        payload = json.dumps(_envelope(stage), ensure_ascii=False)
        if on_token:
            # Stream the JSON in small chunks so the UserTextStreamer forwards the
            # user-facing text to the client token-by-token, exactly like the real
            # streaming path.
            for i in range(0, len(payload), 16):
                on_token(payload[i : i + 16])
                time.sleep(0.005)
        return LLMResponse(
            text=payload,
            prompt_tokens=42,
            completion_tokens=len(payload) // 4,
            total_tokens=42 + len(payload) // 4,
            model_latency_ms=5.0,
            cached_tokens=0,
            cost_usd=0.0,
            model="mock",
        )

    def generate(
        self,
        system_prompt: str,
        user_prompt: str,
        model: str,
        reasoning_effort: Optional[str] = None,
        history: Optional[List[Dict[str, str]]] = None,
        stage: str = "",
        session_id: str = "",
        user_id: str = "",
    ) -> LLMResponse:
        logger.info("mock.generate", extra={"stage": stage, "model": model})
        return self._run(stage)

    def generate_stream(
        self,
        system_prompt: str,
        user_prompt: str,
        model: str,
        on_token: Any = None,
        reasoning_effort: Optional[str] = None,
        history: Optional[List[Dict[str, str]]] = None,
        stage: str = "",
        session_id: str = "",
        user_id: str = "",
        json_output: bool = False,
    ) -> LLMResponse:
        logger.info("mock.generate_stream", extra={"stage": stage, "model": model})
        return self._run(stage, on_token=on_token)
