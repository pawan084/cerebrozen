"""Ollama provider — the fully-offline backend.

Implements the same `LLMProvider` surface as the OpenAI client, so swapping to it is a
config change (`CEREBROZEN_LLM_PROVIDER=ollama`), not a node change.

Three things had to be true for this to work at all, and all three were MEASURED against
a live Ollama (gemma4:8b, Q4_K_M) with the real 20,116-token core_coaching prompt:

1. **Prefix caching.** Our system prompt is ~20-27K tokens. Re-reading it every turn costs
   ~2.9s of prompt-eval — blowing the 1.5s TTFT budget on its own. Ollama's KV cache reuses
   an identical prefix: measured 2.97s cold → **0.08s warm**. This only works because the
   composed system prompt is now byte-identical across turns (see rag/placeholders.py:
   {Time} is hour-granular, not microsecond). If that regresses, this provider gets slow
   again — `tests/test_context_package.py` pins it.

2. **Schema-constrained decoding.** The graph ROUTES on fields the model emits
   (handoff_ready, coaching_path, …). Hoping a local 8B emits valid JSON is not a plan.
   Ollama's `format: <json-schema>` constrains decoding to the grammar, so the envelope is
   structurally guaranteed rather than merely requested — a stronger guarantee than the
   prompt-only approach we use with OpenAI.

3. **Thinking OFF.** gemma4 is a reasoning model: left alone it spent its entire output
   budget on hidden thinking tokens and returned an EMPTY response (`done_reason: length`,
   0 chars). `think=False` is mandatory, not a tuning preference.

Measured, warm, on the real prompt: prompt-eval 0.08s, generation ~113 tok/s, turn 2.7s.
"""

from __future__ import annotations

import logging
import os
import time
from typing import Any, Dict, List, Optional

import httpx

from app.llm.responses_client import LLMResponse

logger = logging.getLogger("cerebrozen.llm.ollama")

# The control envelope every conversational agent must emit. Constrains decoding, so a
# local model cannot silently break the graph's routing contract.
#
# WHAT `required` BUYS YOU — this is not a formality, it is the whole game:
#
#   coaching_path OPTIONAL → the 8B simply OMITTED it on every CH-shaped goal (measured,
#                            3/3 empty) → the router saw no path → silent CIM fallback →
#                            **the CH path becomes unreachable**. Exactly the failure mode
#                            the deterministic graph cannot detect.
#   coaching_path REQUIRED → the grammar forces the model to commit to one of the enum
#                            values, and it then chose CORRECTLY 5/5 on clear cases
#                            (3 CH goals, 2 CIM goals).
#
# So: any field the GRAPH ROUTES ON must be `required` here. Grammar-forcing a commitment
# is what turns "the model might mention a path" into "the model must pick one".
#
# Caveat kept honest: 5/5 on unambiguous cases is encouraging, not proof. The published
# benchmarks put value-accuracy for open models well below 100%, so this needs a golden
# eval set per stage before it is trusted in production (docs/TODO.md, eval harness).
_BASE_PROPS: Dict[str, Any] = {
    "response_to_user": {"type": "string"},
    "handoff_ready": {"type": "boolean"},
    "agent_complete": {"type": "boolean"},
    "current_step": {"type": "string"},
    "context_update": {"type": "object"},
    "variables_set": {"type": "object"},
}
_PATH_PROP = {"coaching_path": {"type": "string", "enum": ["CIM", "CBT", "CH"]}}

# Stage → the fields the ROUTER reads, which therefore must be forced.
_ROUTING_REQUIRED: Dict[str, List[str]] = {
    "challenge_context_agent": ["coaching_path"],   # the whole job of this agent
}


def control_schema(stage: str = "") -> Dict[str, Any]:
    """The JSON schema to constrain decoding for this stage."""
    required = ["response_to_user", "handoff_ready"] + _ROUTING_REQUIRED.get(stage, [])
    return {
        "type": "object",
        "properties": {**_BASE_PROPS, **_PATH_PROP},
        "required": required,
    }


def _base_url() -> str:
    return os.environ.get("OLLAMA_HOST", "http://localhost:11434").rstrip("/")


def _num_ctx() -> int:
    """Context window. Ollama DEFAULTS TO 4096 and silently truncates above it — which
    would lop the head off a 20K-token system prompt and leave the model reading the
    middle of its own instructions. Must be set explicitly, and must exceed the worst-case
    prompt (CH_coaching + environment ≈ 26.7K tokens) plus history and output."""
    return int(os.environ.get("CEREBROZEN_OLLAMA_NUM_CTX", "32768"))


class OllamaClient:
    """Offline LLM provider. Same two calls the graph nodes depend on."""

    def __init__(self) -> None:
        self.model_default = os.environ.get("CEREBROZEN_OLLAMA_MODEL", "gemma4:latest")
        self.timeout = float(os.environ.get("CEREBROZEN_OLLAMA_TIMEOUT", "300"))

    # -- internals ------------------------------------------------------------

    def _messages(self, system_prompt: str, user_prompt: str,
                  history: Optional[List[Dict[str, str]]]) -> List[Dict[str, str]]:
        # System prompt FIRST and unchanged — it is the cacheable prefix. Anything volatile
        # placed ahead of it (or inside it) destroys the KV-cache hit for every turn.
        msgs = [{"role": "system", "content": system_prompt}]
        for m in (history or []):
            role = "assistant" if m.get("role") in ("assistant", "bot") else "user"
            if m.get("content"):
                msgs.append({"role": role, "content": m["content"]})
        msgs.append({"role": "user", "content": user_prompt})
        return msgs

    def _call(self, system_prompt: str, user_prompt: str, model: str,
              history: Optional[List[Dict[str, str]]], on_token: Any,
              structured: bool, stage: str) -> LLMResponse:
        body: Dict[str, Any] = {
            "model": model or self.model_default,
            "messages": self._messages(system_prompt, user_prompt, history),
            "stream": bool(on_token),
            # Reasoning models otherwise spend the whole budget thinking and return "".
            "think": False,
            "options": {
                "num_ctx": _num_ctx(),
                "temperature": float(os.environ.get("CEREBROZEN_OLLAMA_TEMPERATURE", "0.3")),
                "num_predict": int(os.environ.get("CEREBROZEN_OLLAMA_NUM_PREDICT", "800")),
            },
        }
        if structured:
            body["format"] = control_schema(stage)

        t0 = time.perf_counter()
        text_parts: List[str] = []
        prompt_tokens = completion_tokens = 0
        prompt_eval_ms = 0.0

        with httpx.Client(timeout=self.timeout) as client:
            if on_token:
                with client.stream("POST", f"{_base_url()}/api/chat", json=body) as r:
                    r.raise_for_status()
                    for line in r.iter_lines():
                        if not line:
                            continue
                        try:
                            chunk = __import__("json").loads(line)
                        except Exception:  # noqa: BLE001
                            continue
                        piece = (chunk.get("message") or {}).get("content") or ""
                        if piece:
                            text_parts.append(piece)
                            on_token(piece)
                        if chunk.get("done"):
                            prompt_tokens = chunk.get("prompt_eval_count", 0) or 0
                            completion_tokens = chunk.get("eval_count", 0) or 0
                            prompt_eval_ms = (chunk.get("prompt_eval_duration", 0) or 0) / 1e6
            else:
                r = client.post(f"{_base_url()}/api/chat", json=body)
                r.raise_for_status()
                d = r.json()
                text_parts.append((d.get("message") or {}).get("content") or "")
                prompt_tokens = d.get("prompt_eval_count", 0) or 0
                completion_tokens = d.get("eval_count", 0) or 0
                prompt_eval_ms = (d.get("prompt_eval_duration", 0) or 0) / 1e6

        text = "".join(text_parts)
        latency_ms = (time.perf_counter() - t0) * 1000

        # prompt_eval_duration is the tell for whether the KV prefix cache hit. A warm hit
        # on a 20K-token prompt is ~80ms; a miss is ~3,000ms. Worth watching in prod.
        logger.info(
            "ollama.call",
            extra={
                "stage": stage, "model": body["model"],
                "prompt_tokens": prompt_tokens, "completion_tokens": completion_tokens,
                "prompt_eval_ms": round(prompt_eval_ms, 1),
                "prefix_cache": "hit" if prompt_eval_ms < 500 and prompt_tokens > 2000 else "miss",
                "latency_ms": round(latency_ms, 1),
                "empty_reply": not text.strip(),
            },
        )
        return LLMResponse(
            text=text,
            prompt_tokens=prompt_tokens,
            cached_tokens=0,
            completion_tokens=completion_tokens,
            total_tokens=prompt_tokens + completion_tokens,
            cost_usd=0.0,                      # self-hosted: no per-token cost
            model=body["model"],
            model_latency_ms=latency_ms,
        )

    def prewarm(self, system_prompt: str, model: str = "") -> None:
        """Load a system prompt into the KV cache WITHOUT producing a reply.

        Why this exists: Ollama caches ONE prompt prefix at a time (measured — calling a
        second agent evicts the first). Our prompts are 20-27K tokens, so a cold prefix
        costs 7-10s of prompt-eval:

            stage transition, no prewarm : user waits 10.6s
            stage transition, prewarmed  : user waits  1.8s   ← same model, same prompt

        The graph is DETERMINISTIC, so the moment a stage hands off we know exactly which
        agent runs next. We spend that 10s in the background while the user is reading the
        last reply and typing the next one — instead of making them watch a spinner.

        Fire-and-forget: a failed prewarm just means the next turn is cold, which is
        exactly where we started. It must never raise into a turn.
        """
        try:
            with httpx.Client(timeout=self.timeout) as client:
                r = client.post(f"{_base_url()}/api/chat", json={
                    "model": model or self.model_default,
                    "messages": [{"role": "system", "content": system_prompt},
                                 {"role": "user", "content": "."}],
                    "stream": False,
                    "think": False,
                    "options": {"num_ctx": _num_ctx(), "num_predict": 1},
                })
                # Without this an HTTP error (404 model-not-pulled, 500 OOM) still logs
                # "ollama.prewarmed": the prefix was NOT cached, every stage transition
                # stays cold (10.6s vs 1.8s), and the log says it worked.
                r.raise_for_status()
            logger.info("ollama.prewarmed", extra={"prompt_chars": len(system_prompt)})
        except Exception as exc:  # noqa: BLE001
            logger.warning("ollama.prewarm_failed", extra={"error": str(exc)})

    # -- the LLMProvider surface ---------------------------------------------

    def generate(self, system_prompt: str, user_prompt: str, model: str,
                 reasoning_effort: Optional[str] = None,
                 history: Optional[List[Dict[str, str]]] = None,
                 **kwargs: Any) -> LLMResponse:
        # reasoning_effort is an OpenAI concept with no Ollama equivalent — ignored, not
        # emulated. The local latency levers are num_predict and model size instead.
        return self._call(system_prompt, user_prompt, model, history, None,
                          structured=True, stage=kwargs.get("stage", ""))

    def generate_stream(self, system_prompt: str, user_prompt: str, model: str,
                        on_token: Any = None, reasoning_effort: Optional[str] = None,
                        history: Optional[List[Dict[str, str]]] = None,
                        **kwargs: Any) -> LLMResponse:
        # NOTE: schema-constrained decoding and token streaming are both on. The stream
        # emits raw JSON, which the node's UserTextStreamer already knows how to unwrap
        # (it does the same for the OpenAI control envelope).
        return self._call(system_prompt, user_prompt, model, history, on_token,
                          structured=True, stage=kwargs.get("stage", ""))
