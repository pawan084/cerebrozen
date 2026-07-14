"""Responses-API client + reasoning-effort resolver + user-text streamer.

Ported (near-verbatim) from the old repo's coaching_harness_excel.py so the new
graph nodes keep the SAME OpenAI Responses API behaviour the constitution
mandates (no ChatOpenAI). Nodes are plain functions that call this.
"""

from __future__ import annotations

import logging
import os
import re
import time
from dataclasses import dataclass
from typing import Any, Callable, Dict, List, Optional

from app import config, metrics
from app.llm.pricing import estimate_cost
from app.llm.resilience import (
    BreakerOpen,
    backoff_delays,
    candidate_models,
    get_breaker,
    is_retryable,
)

logger = logging.getLogger("cerebrozen.llm")


def _tracing_enabled() -> bool:
    """LangSmith tracing is on if either env flag is truthy."""
    for var in ("LANGSMITH_TRACING", "LANGCHAIN_TRACING_V2"):
        if os.environ.get(var, "").strip().lower() in ("true", "1", "yes"):
            return True
    return False

# All effort strings that exist across all model families (union).
VALID_REASONING_EFFORTS = {"none", "minimal", "low", "medium", "high", "xhigh"}
DEFAULT_REASONING_EFFORT = "medium"

# Per-model-family: which effort values the API actually accepts.
# gpt-5.4 / gpt-5.x point releases use a different set from gpt-5-mini / o-series.
_GPT5_POINT_EFFORTS   = frozenset({"none", "low", "medium", "high", "xhigh"})
_GPT5_MINI_EFFORTS    = frozenset({"minimal", "low", "medium", "high"})

# When a configured effort is absent from the model's valid set, map it to the
# nearest supported equivalent (rather than silently omitting it).
_EFFORT_COMPAT: Dict[str, str] = {
    "minimal": "low",   # gpt-5-mini "minimal" → gpt-5.x "low" (closest fast tier)
    "xhigh":   "high",  # gpt-5.x "xhigh" → gpt-5-mini "high"
}

# Per-stage reasoning effort (latency lever). Override per stage via env
# CEREBROZEN_REASONING_<STAGE>, e.g. CEREBROZEN_REASONING_CORE=medium.
def _effort(stage_env: str, default: str) -> str:
    return os.environ.get(f"CEREBROZEN_REASONING_{stage_env}", default).strip().lower()


STAGE_REASONING_EFFORT: Dict[str, str] = {
    "coaching_intake_agent": _effort("INTAKE", "minimal"),
    "challenge_context_agent": _effort("CHALLENGE", "minimal"),
    "repeat_user_checkin_agent": _effort("CHECKIN", "minimal"),
    # Standalone Action Check-In is a conversational agent like intake/challenge — it was
    # missing from this map, so it defaulted to "medium" (→ effort omitted → the model's
    # own default reasoning), causing ~24s/turn. Put it on the fast path. Tune via
    # CEREBROZEN_REASONING_ACTION_CHECKIN=low if it needs a bit more reasoning quality.
    "action_checkin_agent": _effort("ACTION_CHECKIN", "minimal"),
    # Closing feedback/mood-capture is a conversational, sticky multi-turn agent like
    # intake/challenge/checkin — it was missing from this map, so it defaulted to
    # "medium" (→ effort omitted → gpt-5-mini's own default reasoning), causing the
    # feedback-stage latency users reported. Put it on the fast path. Tune via
    # CEREBROZEN_REASONING_FEEDBACK=low if it needs a bit more reasoning quality.
    "feedback_mood_capture_agent": _effort("FEEDBACK", "minimal"),
    "core_coaching_agent": _effort("CORE", "low"),
    # Every remaining LLM stage is listed explicitly: a stage ABSENT from this map
    # falls to DEFAULT_REASONING_EFFORT ("medium" → effort omitted → the model's own
    # default reasoning), which is the ~24-32s/turn defect class measured on
    # action_checkin (31.9s → 4.8s once mapped). Coaching-grade agents get "low",
    # conversational/gate agents "minimal".
    "CH_coaching_agent": _effort("CH", "low"),
    "simulation_decision_agent": _effort("SIMULATION_DECISION", "minimal"),
    "role_play_agent": _effort("ROLE_PLAY", "low"),
    "SJT_simulation_agent": _effort("SJT", "low"),
    "learning_aid_agent": _effort("LEARNING_AID", "minimal"),
    "builder": _effort("BUILDER", "low"),
    "chat_title_agent": _effort("TITLE", "minimal"),
    "greeting_agent": _effort("GREETING", "minimal"),
}

# Per-stage temperature. Unset = the provider default (1.0 for OpenAI).
#
# A ROUTING decision must not be creative. challenge_context picks the coaching path the
# whole session runs on, and at the default temperature it is unreliable — MEASURED with
# scripts/eval on the production stack: the same CH-shaped goal scored CH 3/6, CIM 2/6,
# and emitted NO path at all 1/6 (a silent fallback to CIM). Temperature 0 makes the
# decision reproducible.
#
# Reasoning models (gpt-5 / o-series) REJECT a temperature parameter, so it is only sent
# to models that accept it (see _temperature_for).
# MEASURED AND REVERTED — kept as an opt-in, defaulting OFF.
#
# I assumed the flaky routing was a sampling problem and set these to 0. It made CH routing
# WORSE (1/6 correct, down from 3/6): at temperature 0 the model's *true* answer for
# "build my competency ... over time" is CIM, 4/6. Randomness had been accidentally landing
# on CH some of the time and MASKING a prompt bug.
#
# So the root cause is challenge_context's path-deciphering logic, not sampling — a
# competency-over-time goal is textbook CH and the prompt scores it CIM. That is a
# prompt-team fix (docs/EVALS.md). Pinning temperature here would only make the wrong
# answer consistent.
#
# Set CEREBROZEN_TEMP_CHALLENGE=0 to opt in once the prompt is fixed.
STAGE_TEMPERATURE: Dict[str, float] = {
    k: float(v) for k, v in (
        ("challenge_context_agent", os.environ.get("CEREBROZEN_TEMP_CHALLENGE", "")),
        ("simulation_decision_agent", os.environ.get("CEREBROZEN_TEMP_SIMULATION", "")),
    ) if v.strip()
}


def _temperature_for(stage: str, model: str) -> Optional[float]:
    """Temperature to send, or None to omit. Never sent to a reasoning model — the
    Responses API errors rather than ignoring it."""
    if _supports_reasoning(model):
        return None
    return STAGE_TEMPERATURE.get(stage)


# Revert flag: set CEREBROZEN_STAGE_OPT=false to collapse all reasoning efforts to "low"
# (the pre-optimization baseline). Applies to reasoning only; model selection is
# always driven by the workbook Catalog.
STAGE_OPT = os.environ.get("CEREBROZEN_STAGE_OPT", "true").strip().lower() != "false"

ORIGINAL_REASONING_EFFORT: Dict[str, str] = {
    "coaching_intake_agent": "low",
    "challenge_context_agent": "low",
    "repeat_user_checkin_agent": "low",
    "action_checkin_agent": "low",
    "feedback_mood_capture_agent": "low",
    "core_coaching_agent": "low",
    "CH_coaching_agent": "low",
    "simulation_decision_agent": "low",
    "role_play_agent": "low",
    "SJT_simulation_agent": "low",
    "learning_aid_agent": "low",
    "builder": "low",
    "chat_title_agent": "low",
    "greeting_agent": "low",
}


def model_for(stage: str, catalog_model: Optional[str] = None) -> str:
    """Return the model for a stage.

    The workbook Catalog is the sole source of truth — ``catalog_model`` is the value
    read from the Catalog's ``model`` column by the call site.  Raises RuntimeError
    when no model is present so the caller fails loudly instead of silently using the
    wrong model (which would break the agent's JSON contract).

    Override: ``CEREBROZEN_MODEL_OVERRIDE`` (env), when set, forces one model id for every
    stage regardless of the Catalog — used to pin a known-good model (e.g. gpt-4o-mini)
    when the workbook lists placeholder ids like gpt-5.4.
    """
    import os as _os

    _override = _os.environ.get("CEREBROZEN_MODEL_OVERRIDE", "").strip()
    if _override:
        return _override
    if catalog_model:
        return catalog_model
    # Error was already logged by PromptRegistry.model_for(); raise so the node/builder
    # surfaces a clear failure rather than passing an empty string to the API.
    raise RuntimeError(
        f"No model configured in the workbook Catalog for stage {stage!r}. "
        "Add a non-blank 'model' value to the Catalog tab for this agent and reload."
    )


def _maybe_bust_cache(system_prompt: str) -> str:
    """Stamp the system prompt with a cache-control header.

    OpenAI's Responses API caches identical input prefixes automatically — a shared
    static prefix means responses may be served from a stale cache slot for up to
    5–10 minutes after a prompt edit or workbook reload.

    - Caching ENABLED (CEREBROZEN_LLM_PROMPT_CACHE=true): prepend the workbook's
      content-hash version. The prefix stays identical across requests (full
      cache hits, big TTFT/cost win) and changes the moment a prompt edit is
      reloaded — the reload itself busts the cache, no nonce needed.
    - Caching DISABLED (the default): prepend a per-request UUID nonce, which
      makes every prompt distinct and guarantees a 0% cache hit rate."""
    if config.LLM_PROMPT_CACHE_ENABLED:
        from app.llm.prompts import current_workbook_version
        version = current_workbook_version()
        return f"<!-- prompt-rev:{version} -->\n{system_prompt}" if version else system_prompt
    import uuid
    return f"<!-- {uuid.uuid4().hex} -->\n{system_prompt}"


def _cache_key(stage: str) -> str:
    """Cache-affinity key for OpenAI's prompt cache.

    Caching only pays if the request LANDS on a machine that already holds the prefix.
    Without a key, OpenAI routes freely and hits are sporadic — measured on a live
    6-turn session with a byte-identical 16.5K-token system prompt: only 1 turn in 6
    hit the cache (the other five re-paid for the whole prompt).

    The key is `stage:workbook-version`, deliberately NOT per-user or per-session:
      - every user of the same agent shares the SAME system prompt, so they should share
        the same cache entry — a per-user key would fragment the cache and throw the
        cross-user reuse away;
      - the workbook version busts it automatically when a prompt is edited, so a stale
        prefix can never be served after a reload.
    """
    from app.llm.prompts import current_workbook_version

    return f"{stage or 'agent'}:{current_workbook_version() or 'dev'}"


def _supports_reasoning(model: str) -> bool:
    m = (model or "").lower()
    return m.startswith(("gpt-5", "o1", "o3", "o4"))


def _valid_efforts_for_model(model: str) -> frozenset:
    """Effort values accepted by this specific model family."""
    m = (model or "").lower()
    # gpt-5.x point releases (gpt-5.4, gpt-5.5, …) differ from gpt-5-mini / o-series.
    if "gpt-5." in m:
        return _GPT5_POINT_EFFORTS
    return _GPT5_MINI_EFFORTS


def reasoning_effort_for(stage: str, model: str) -> Optional[str]:
    """Effort to send for this stage/model, or None to omit.

    Translates effort strings that are valid for one model family but not another
    ('minimal' → 'low' for gpt-5.x, 'xhigh' → 'high' for gpt-5-mini) so changing
    the workbook model column doesn't also require updating CEREBROZEN_REASONING_* vars.

    With CEREBROZEN_STAGE_OPT=false uses the original 'low' efforts instead of the
    optimized 'minimal' hot-path efforts."""
    if not _supports_reasoning(model):
        return None
    table = STAGE_REASONING_EFFORT if STAGE_OPT else ORIGINAL_REASONING_EFFORT
    effort = (table.get(stage, DEFAULT_REASONING_EFFORT) or "").strip().lower()
    valid = _valid_efforts_for_model(model)
    if effort not in valid:
        effort = _EFFORT_COMPAT.get(effort, "")
    if not effort or effort == "medium" or effort not in valid:
        return None
    return effort


@dataclass
class LLMResponse:
    text: str
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0
    model_latency_ms: float = 0.0
    cached_tokens: int = 0
    cost_usd: float = 0.0
    model: str = ""


class OpenAIResponsesClient:
    def __init__(self) -> None:
        from openai import OpenAI

        from app.config import OPENAI_TIMEOUT

        # Per-request timeout caps pathological hangs (a 341s CIM call broke the UI).
        # max_retries=0: our resilience layer (retry + cascade + breaker) owns
        # retries, so the SDK must not silently retry on top of us.
        client = OpenAI(timeout=OPENAI_TIMEOUT, max_retries=0)
        # When LangSmith tracing is on, wrap the OpenAI client so each Responses
        # call shows as an LLM span nested under the current graph-node span.
        # Off → no wrapper, zero overhead. The nodes still use the raw Responses
        # API either way (constitution: keep the Responses API, no ChatOpenAI).
        if _tracing_enabled():
            try:
                from langsmith.wrappers import wrap_openai

                client = wrap_openai(client)
                logger.info("langsmith.wrap_openai", extra={"enabled": True})
            except Exception as exc:  # noqa: BLE001
                logger.warning("langsmith.wrap_failed", extra={"error": str(exc)})
        self.client = client

    @staticmethod
    def _build_input(
        system_prompt: str, user_prompt: str, history: Optional[List[Dict[str, str]]]
    ) -> List[Dict[str, str]]:
        msgs: List[Dict[str, str]] = [{"role": "system", "content": system_prompt}]
        for turn in history or []:
            role = turn.get("role")
            content = turn.get("content")
            if role in ("user", "assistant") and content:
                msgs.append({"role": role, "content": content})
        msgs.append({"role": "user", "content": user_prompt})
        return msgs

    def _generate_once(
        self,
        system_prompt: str,
        user_prompt: str,
        model: str,
        reasoning_effort: Optional[str],
        history: Optional[List[Dict[str, str]]],
        stage: str = "",
        session_id: str = "",
        user_id: str = "",
    ) -> LLMResponse:
        t0 = time.perf_counter()
        built_input = self._build_input(_maybe_bust_cache(system_prompt), user_prompt, history)
        kwargs: Dict[str, Any] = {"model": model, "input": built_input}
        if reasoning_effort:
            kwargs["reasoning"] = {"effort": reasoning_effort}
        _temp = _temperature_for(stage, m if (m := model) else model)
        if _temp is not None:
            kwargs["temperature"] = _temp
        if config.LLM_PROMPT_CACHE_ENABLED:
            kwargs["prompt_cache_key"] = _cache_key(stage)
        if config.OPENAI_MAX_OUTPUT_TOKENS > 0:
            kwargs["max_output_tokens"] = config.OPENAI_MAX_OUTPUT_TOKENS
        if config.CEREBROZEN_LLM_LOG_CONTENT:
            _lim = config.CEREBROZEN_LLM_LOG_CONTENT_CHARS
            _sys = built_input[0]["content"] if built_input else ""
            _usr = built_input[-1]["content"] if len(built_input) > 1 else ""
            logger.info(
                "llm.request",
                extra={
                    "stage": stage, "session_id": session_id, "model": model,
                    "reasoning_effort": reasoning_effort, "stream": False,
                    "history_turns": max(0, len(built_input) - 2),
                    "system_prompt": _sys if not _lim else (_sys[:_lim] + ("…" if len(_sys) > _lim else "")),
                    "user_message": _usr if not _lim else (_usr[:_lim] + ("…" if len(_usr) > _lim else "")),
                },
            )
        response = self.client.responses.create(**kwargs)
        latency_ms = (time.perf_counter() - t0) * 1000
        usage = getattr(response, "usage", None)
        prompt_tokens = getattr(usage, "input_tokens", 0) if usage else 0
        completion_tokens = getattr(usage, "output_tokens", 0) if usage else 0
        total_tokens = getattr(usage, "total_tokens", 0) if usage else 0
        details = getattr(usage, "input_tokens_details", None) if usage else None
        cached_tokens = getattr(details, "cached_tokens", 0) if details else 0
        cost = estimate_cost(model, prompt_tokens, cached_tokens, completion_tokens)
        _resp_text = response.output_text or ""
        _resp_extra: Dict[str, Any] = {
            "stage": stage, "user_id": user_id, "session_id": session_id,
            "model": model, "stream": False,
            "latency_ms": round(latency_ms, 1),
            "prompt_tokens": prompt_tokens,
            "cached_tokens": cached_tokens,  # >0 = prompt-cache hit
            "completion_tokens": completion_tokens,
            "total_tokens": total_tokens,
            "cost_usd": cost,
        }
        if config.CEREBROZEN_LLM_LOG_CONTENT:
            _lim = config.CEREBROZEN_LLM_LOG_CONTENT_CHARS
            _resp_extra["response_text"] = _resp_text if not _lim else (_resp_text[:_lim] + ("…" if len(_resp_text) > _lim else ""))
            _resp_extra["response_chars"] = len(_resp_text)
        logger.info("openai.response", extra=_resp_extra)
        metrics.record_llm(
            stage=stage, model=model, latency_ms=latency_ms,
            prompt_tokens=prompt_tokens, cached_tokens=cached_tokens,
            completion_tokens=completion_tokens, cost_usd=cost,
        )
        return LLMResponse(
            text=_resp_text,
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
            total_tokens=total_tokens,
            model_latency_ms=latency_ms,
            cached_tokens=cached_tokens,
            cost_usd=cost,
            model=model,
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
        """Non-streaming call, bounded by retry + model cascade + circuit breaker."""
        return self._resilient_call(
            lambda m: self._generate_once(
                system_prompt, user_prompt, m, reasoning_effort, history, stage, session_id, user_id
            ),
            requested_model=model,
            can_retry=lambda: True,  # nothing streamed yet — always safe to retry
        )

    def _generate_stream_once(
        self,
        system_prompt: str,
        user_prompt: str,
        model: str,
        on_token: Any,
        reasoning_effort: Optional[str],
        history: Optional[List[Dict[str, str]]],
        stage: str = "",
        session_id: str = "",
        user_id: str = "",
        json_output: bool = False,
    ) -> LLMResponse:
        t0 = time.perf_counter()
        full_text = ""
        prompt_tokens = completion_tokens = total_tokens = cached_tokens = 0
        built_input = self._build_input(_maybe_bust_cache(system_prompt), user_prompt, history)
        stream_kwargs: Dict[str, Any] = {"model": model, "input": built_input}
        if reasoning_effort:
            stream_kwargs["reasoning"] = {"effort": reasoning_effort}
        _temp = _temperature_for(stage, model)
        if _temp is not None:
            stream_kwargs["temperature"] = _temp
        if config.LLM_PROMPT_CACHE_ENABLED:
            stream_kwargs["prompt_cache_key"] = _cache_key(stage)
        # Structured-output enforcement: force the whole response to be a valid JSON
        # object (Responses API `text.format`). Agents like CH emit a JSON envelope but
        # frequently DROP it for plain prose on long/narrative turns, which loses every
        # control field (phase/current_step/milestone) and silently breaks phase routing.
        # json_object mode makes prose impossible — the model can only return JSON — so
        # the control fields are always present to parse. Opt-in per stage (off by default).
        #
        # It is `json_object` and NOT strict `json_schema`, deliberately. Upgrading looks
        # like the obvious fix for challenge_context omitting `coaching_path` (~1 handoff
        # in 6) — it is not, and the API says so:
        #
        #   400 invalid_json_schema: In context=('properties', 'context_update'),
        #   'additionalProperties' is required to be supplied and to be false.
        #
        # `context_update` is open-ended by design: agents write session_goal,
        # presenting_issue_summary, coaching_path and any of the ~30 capture-registry
        # variables into it. Strict mode requires every object to enumerate its keys and
        # forbid the rest — and because decoding is grammar-constrained, a key we forgot to
        # enumerate becomes a key the model CANNOT emit. That is not an error anyone would
        # see; it is coaching context silently vanishing on every turn. The missing routing
        # field is instead repaired in-turn (_CONTRACT_REPAIR in graph/nodes.py, 6/6
        # recovery). See docs/EVALS.md.
        if json_output:
            stream_kwargs["text"] = {"format": {"type": "json_object"}}
        if config.OPENAI_MAX_OUTPUT_TOKENS > 0:
            stream_kwargs["max_output_tokens"] = config.OPENAI_MAX_OUTPUT_TOKENS
        if config.CEREBROZEN_LLM_LOG_CONTENT:
            _lim = config.CEREBROZEN_LLM_LOG_CONTENT_CHARS
            _sys = built_input[0]["content"] if built_input else ""
            _usr = built_input[-1]["content"] if len(built_input) > 1 else ""
            logger.info(
                "llm.request",
                extra={
                    "stage": stage, "session_id": session_id, "model": model,
                    "reasoning_effort": reasoning_effort, "stream": True,
                    "history_turns": max(0, len(built_input) - 2),
                    "system_prompt": _sys if not _lim else (_sys[:_lim] + ("…" if len(_sys) > _lim else "")),
                    "user_message": _usr if not _lim else (_usr[:_lim] + ("…" if len(_usr) > _lim else "")),
                },
            )

        # Hard wall-clock deadline for the WHOLE streamed generation. httpx's
        # OPENAI_TIMEOUT only bounds the gap BETWEEN chunks, so a generation that
        # keeps drip-feeding tokens can run forever (live incident 2026-07-06:
        # an action_checkin stream never completed and never errored). Raising here
        # surfaces through the normal resilience/worker error path — loud, bounded.
        _deadline_s = config.OPENAI_STREAM_DEADLINE_S
        with self.client.responses.stream(**stream_kwargs) as stream:
            for event in stream:
                if _deadline_s > 0 and (time.perf_counter() - t0) > _deadline_s:
                    raise TimeoutError(
                        f"streamed generation exceeded OPENAI_STREAM_DEADLINE_S="
                        f"{_deadline_s:.0f}s (stage={stage!r}, chars_so_far={len(full_text)})"
                    )
                if getattr(event, "type", None) == "response.output_text.delta":
                    text = getattr(event, "delta", "")
                    if text:
                        full_text += text
                        if on_token:
                            on_token(text)
            final = stream.get_final_response()
            usage = getattr(final, "usage", None)
            if usage:
                prompt_tokens = getattr(usage, "input_tokens", 0)
                completion_tokens = getattr(usage, "output_tokens", 0)
                total_tokens = getattr(usage, "total_tokens", 0)
                details = getattr(usage, "input_tokens_details", None)
                cached_tokens = getattr(details, "cached_tokens", 0) if details else 0

        latency_ms = (time.perf_counter() - t0) * 1000
        cost = estimate_cost(model, prompt_tokens, cached_tokens, completion_tokens)
        _stream_extra: Dict[str, Any] = {
            "stage": stage, "user_id": user_id, "session_id": session_id,
            "model": model, "stream": True,
            "latency_ms": round(latency_ms, 1),
            "prompt_tokens": prompt_tokens,
            "cached_tokens": cached_tokens,  # >0 = prompt-cache hit
            "completion_tokens": completion_tokens,
            "total_tokens": total_tokens,
            "cost_usd": cost,
        }
        if config.CEREBROZEN_LLM_LOG_CONTENT:
            _lim = config.CEREBROZEN_LLM_LOG_CONTENT_CHARS
            _stream_extra["response_text"] = full_text if not _lim else (full_text[:_lim] + ("…" if len(full_text) > _lim else ""))
            _stream_extra["response_chars"] = len(full_text)
        logger.info("openai.response", extra=_stream_extra)
        metrics.record_llm(
            stage=stage, model=model, latency_ms=latency_ms,
            prompt_tokens=prompt_tokens, cached_tokens=cached_tokens,
            completion_tokens=completion_tokens, cost_usd=cost,
        )
        return LLMResponse(
            text=full_text,
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
            total_tokens=total_tokens,
            model_latency_ms=latency_ms,
            cached_tokens=cached_tokens,
            cost_usd=cost,
            model=model,
        )

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
        """Stream text deltas via on_token; bounded by retry + cascade + breaker.

        `json_output=True` forces the response to a valid JSON object (structured-output
        enforcement — see _generate_stream_once). Opt-in; off by default.

        Streaming safety rule: a retry or model fallback may only happen BEFORE the
        first token reaches the user. Once we've streamed text, a mid-stream failure
        raises (no duplicated/garbled output)."""
        emitted = {"n": 0}

        def sink(text: str) -> None:
            emitted["n"] += len(text)
            if on_token:
                on_token(text)

        return self._resilient_call(
            lambda m: self._generate_stream_once(
                system_prompt, user_prompt, m, sink, reasoning_effort, history, stage, session_id,
                user_id, json_output=json_output,
            ),
            requested_model=model,
            can_retry=lambda: emitted["n"] == 0,
        )

    # --- resilience driver ---------------------------------------------------

    def _resilient_call(
        self,
        attempt_fn: Callable[[str], LLMResponse],
        requested_model: str,
        can_retry: Callable[[], bool],
    ) -> LLMResponse:
        """Run attempt_fn(model) with retry + model cascade + circuit breaker.

        `can_retry()` lets the streaming path veto a retry once tokens have been
        emitted. The breaker short-circuits to BreakerOpen when OpenAI is
        persistently down so a turn degrades to a safe reply instead of hanging."""
        breaker = get_breaker()
        if not breaker.allow():
            logger.warning("breaker.short_circuit", extra={"model": requested_model})
            raise BreakerOpen("circuit breaker open")

        last_exc: Optional[BaseException] = None
        for model in candidate_models(requested_model):
            delays = backoff_delays(config.LLM_MAX_RETRIES)
            for attempt in range(config.LLM_MAX_RETRIES + 1):
                try:
                    resp = attempt_fn(model)
                    breaker.record_success()
                    return resp
                except Exception as exc:  # noqa: BLE001
                    last_exc = exc
                    # Already streamed to the user → cannot safely retry/fallback.
                    if not can_retry():
                        breaker.record_failure()
                        logger.error(
                            "openai.fail_midstream",
                            extra={"model": model, "error": str(exc)},
                        )
                        raise
                    if not is_retryable(exc):
                        breaker.record_failure()
                        logger.error(
                            "openai.non_retryable",
                            extra={"model": model, "error": str(exc)},
                        )
                        raise
                    if attempt < config.LLM_MAX_RETRIES:
                        delay = delays[attempt]
                        logger.warning(
                            "openai.retry",
                            extra={
                                "model": model,
                                "attempt": attempt + 1,
                                "delay_s": delay,
                                "error": str(exc),
                            },
                        )
                        time.sleep(delay)
                        continue
                    # retries exhausted for this model → fall back to next in cascade
                    logger.warning(
                        "openai.cascade", extra={"from_model": model, "error": str(exc)}
                    )
                    break
        breaker.record_failure()
        logger.error(
            "openai.exhausted",
            extra={"model": requested_model, "error": str(last_exc)},
        )
        if last_exc:
            raise last_exc
        raise RuntimeError("LLM call failed with no exception captured")


# Keys whose value is shown to the user (priority order).
_USER_TEXT_KEY_RE = re.compile(
    r'"(?:response_to_user|next_question|clarifying_question|message|response)"\s*:\s*"'
)

# Markdown code fence prefix the LLM sometimes wraps its JSON output in.
# When detected, the streamer skips past the fence before looking for the JSON key
# instead of immediately going into raw mode (which leaks ```json to the UI).
_FENCE_PREFIX_RE = re.compile(r'^```(?:json)?\s*', re.IGNORECASE)

# Marks a fence opening on its own line, appearing partway through an otherwise
# plain-text ("raw" mode) response. Some models answer with prose first and then
# append a trailing ```json control envelope instead of wrapping everything in one
# JSON object (see _FENCE_PREFIX_RE above for the fence-at-the-very-start case).
# Without this, raw mode just forwards every later delta verbatim and leaks the
# fence marker + control JSON straight into the user-visible chat bubble.
_RAW_FENCE_TRIGGER = "\n```"

# Same failure as _RAW_FENCE_TRIGGER but for a BARE (un-fenced) trailing JSON
# envelope: prose reply followed directly by `{"...": ...}` with no ```json fence.
# The fence trigger above doesn't catch it, so raw mode used to stream the whole
# control JSON into the chat bubble (live incidents: action_checkin final/handoff turn
# and the mid-checkin turns where the model wrote the real message as prose then
# appended the envelope — user saw backend code flash on screen).
#
# Match the START OF ANY JSON OBJECT FIELD (`{ "key":`) — NOT only known control keys.
# The earlier control-key-only version leaked whenever the envelope led with a DATA
# field (e.g. `{"action_item": ...}` on the action_checkin turns), because the control
# key was no longer the first key. A brace immediately followed by a quoted key and a
# colon is unambiguously a JSON object start, essentially never natural prose (an
# incidental `{plan}` has no quoted-key-colon, so it is preserved).
_RAW_CONTROL_JSON_RE = re.compile(r'\{\s*"\w+"\s*:')
# Viable PARTIAL prefix of the above sitting at the end of the buffer (envelope split
# across a chunk boundary, e.g. "...today.\n{" then `"action_item": ...`). Held back
# until the next delta confirms (suppress) or denies (flush as prose) it, so a
# half-arrived envelope never leaks. Anchored to end-of-text and permissive enough to
# hold a lone `{`, `{"`, `{"key`, `{"key"`, or `{"key" :` mid-arrival.
_RAW_CONTROL_PREFIX_RE = re.compile(r'\{\s*"?\w*"?\s*:?\s*$')


def _longest_suffix_prefix_len(text: str, pattern: str) -> int:
    """Length of the longest suffix of ``text`` that is a proper prefix of ``pattern``."""
    for length in range(min(len(text), len(pattern) - 1), 0, -1):
        if text.endswith(pattern[:length]):
            return length
    return 0


class UserTextStreamer:
    """Incrementally extract the user-facing text field from a streaming JSON
    agent response and forward its characters to ``sink`` as they arrive.

    Nodes emit JSON like ``{"next_question": "<reply>", "handoff_ready": false}``.
    This watches the streamed text, locates the first user-facing key, and emits
    only that string value's characters (JSON-unescaped). If the key never
    appears, nothing is emitted. The authoritative reply is always the final
    parsed payload. Ported verbatim from the old _UserTextStreamer.
    """

    def __init__(self, sink) -> None:
        self._sink = sink
        self._buf = ""
        self._state = "search"  # search -> stream -> done  |  search -> raw -> done
        self._j = 0
        self._esc = False
        self._raw_tail = ""  # short unflushed tail held back in raw mode, in case
        # it's the start of a "\n```" fence the model appended after its prose reply

    def feed(self, delta: str) -> None:
        if self._state == "done" or not delta:
            return
        self._buf += delta
        if self._state == "search":
            stripped = self._buf.lstrip()
            if not stripped:
                return  # only whitespace so far — keep waiting
            # Detect markdown code fence (```json...```) wrapping the JSON output.
            # Without this, the first "`" character causes the streamer to go into
            # raw mode and emit the fence markers verbatim to the UI.
            fence_m = _FENCE_PREFIX_RE.match(stripped)
            if fence_m:
                after_fence = stripped[fence_m.end():]
                if not after_fence:
                    return  # fence header arrived but JSON content hasn't yet
                if not after_fence.lstrip().startswith("{"):
                    # Code fence around non-JSON — treat as plain text
                    logger.warning(
                        "streamer.raw_mode",
                        extra={"reason": "code_fence_non_json", "prefix": stripped[:80]},
                    )
                    self._state = "raw"
                    self._emit_raw(self._buf)
                    return
                # Code fence around JSON — fall through to key search below.
                # _USER_TEXT_KEY_RE matches within the full buffer; the key sits
                # inside the JSON after the fence so the match position is correct.
            elif not stripped.startswith("{"):
                # Plain-text response (no JSON envelope) — stream the whole buffer
                # immediately and forward subsequent deltas directly.
                logger.warning(
                    "streamer.raw_mode",
                    extra={"reason": "non_json", "prefix": stripped[:80]},
                )
                self._state = "raw"
                self._emit_raw(self._buf)
                return
            m = _USER_TEXT_KEY_RE.search(self._buf)
            if not m:
                return
            self._j = m.end()
            self._state = "stream"
        if self._state == "stream":
            self._drain()
        elif self._state == "raw":
            self._emit_raw(delta)

    def _emit_raw(self, delta: str) -> None:
        """Forward raw-mode text to the sink, but stop the instant the model switches
        from its prose reply into a trailing JSON control block — whether it's fenced
        (``\\n```json``) or a BARE ``{"agent": ...}`` envelope. Neither must ever reach
        the user (Round-2 bug: mid-stream ```json leak; action_checkin final-turn bug:
        bare control JSON flashed on screen)."""
        text = self._raw_tail + delta
        # A confirmed trigger anywhere in the buffer — emit the prose before it, then
        # stop for good. Take the EARLIEST of the fence / bare-envelope positions.
        fence_idx = text.find(_RAW_FENCE_TRIGGER)
        ctrl_m = _RAW_CONTROL_JSON_RE.search(text)
        ctrl_idx = ctrl_m.start() if ctrl_m else -1
        hits = [i for i in (fence_idx, ctrl_idx) if i != -1]
        if hits:
            cut = min(hits)
            safe = text[:cut].rstrip()  # drop the whitespace separating prose from JSON
            if safe and self._sink:
                self._sink(safe)
            self._state = "done"
            self._raw_tail = ""
            reason = "fence" if cut == fence_idx else "control_json"
            logger.warning("streamer.raw_control_suppressed",
                           extra={"reason": reason, "prefix": text[:80]})
            return
        # Nothing confirmed yet — hold back any tail that could still BECOME a fence or a
        # bare control envelope on the next delta; flush the rest as prose.
        keep = _longest_suffix_prefix_len(text, _RAW_FENCE_TRIGGER)
        pm = _RAW_CONTROL_PREFIX_RE.search(text)
        if pm is not None:
            keep = max(keep, len(text) - pm.start())
        flush_len = len(text) - keep
        if flush_len > 0 and self._sink:
            self._sink(text[:flush_len])
        self._raw_tail = text[flush_len:]

    def _drain(self) -> None:
        buf = self._buf
        n = len(buf)
        j = self._j
        out: List[str] = []
        while j < n:
            ch = buf[j]
            if self._esc:
                if ch == "u":
                    if j + 5 > n:
                        break
                    try:
                        out.append(chr(int(buf[j + 1 : j + 5], 16)))
                    except ValueError:
                        out.append(ch)
                    j += 5
                    self._esc = False
                    continue
                out.append({"n": "\n", "t": "\t", "r": "\r", "b": "\b", "f": "\f"}.get(ch, ch))
                self._esc = False
                j += 1
                continue
            if ch == "\\":
                self._esc = True
                j += 1
                continue
            if ch == '"':
                self._state = "done"
                j += 1
                break
            out.append(ch)
            j += 1
        self._j = j
        if out and self._sink:
            self._sink("".join(out))
