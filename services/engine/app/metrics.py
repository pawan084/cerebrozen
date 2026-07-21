"""Prometheus metrics for LLM calls — scraped at /metrics by your existing
Prometheus, dashboarded/alerted in your existing Grafana.

Design notes:
  - Optional dependency: if `prometheus_client` isn't installed, every helper
    degrades to a no-op and `metrics_asgi_app()` returns None (so /metrics is
    simply not mounted). The app runs unchanged on a lean install.
  - Low-cardinality labels only: `stage` and `model`. `session_id` is NEVER a
    label (unbounded cardinality would blow up Prometheus) — it stays in the
    JSON logs and OTEL traces for per-session drill-down.
  - Mirrors the values already computed at the `openai.response` log site, so
    enabling metrics costs nothing extra on the hot path.
"""

from __future__ import annotations

import logging

logger = logging.getLogger("cerebrozen.metrics")

try:
    from prometheus_client import Counter, Histogram, make_asgi_app

    _ENABLED = True
except Exception:  # noqa: BLE001 — lib absent on lean installs → metrics off.
    _ENABLED = False


if _ENABLED:
    # Cumulative USD spent on completions, by coaching stage + model.
    LLM_COST = Counter(
        "cerebrozen_llm_cost_usd_total",
        "Cumulative OpenAI completion cost in USD.",
        ["stage", "model"],
    )
    # Token counts split by kind (prompt / cached / completion).
    LLM_TOKENS = Counter(
        "cerebrozen_llm_tokens_total",
        "Cumulative tokens by kind.",
        ["stage", "model", "kind"],
    )
    # Per-call wall latency. Buckets span fast routing calls to slow coaching turns.
    LLM_LATENCY = Histogram(
        "cerebrozen_llm_latency_seconds",
        "OpenAI call wall-clock latency in seconds.",
        ["stage", "model"],
        buckets=(0.25, 0.5, 1, 2, 4, 8, 15, 30, 60, 120),
    )
    # Call count (the _count of the histogram also gives this, but an explicit
    # counter keeps simple rate() queries obvious).
    LLM_CALLS = Counter(
        "cerebrozen_llm_calls_total",
        "Number of OpenAI completion calls.",
        ["stage", "model"],
    )
    # Agent output-contract violations (see app/graph/contracts.py): an agent
    # completed its stage without emitting a structured field the graph routes on
    # (coaching_path, CH phase milestone, action cards). The target is ~0 — a
    # non-zero rate means a prompt has drifted from its contract, which is
    # otherwise invisible because the graph's fallbacks keep the session running.
    CONTRACT_VIOLATIONS = Counter(
        "cerebrozen_agent_contract_violations_total",
        "Agent output-contract violations, by stage and contract.",
        ["stage", "contract"],
    )
    # Stages force-advanced by the stuck-stage watchdog (a prompt that never
    # signalled completion). Also target ~0.
    STAGE_WATCHDOG = Counter(
        "cerebrozen_stage_watchdog_total",
        "Stages force-advanced after exceeding their max-turn cap.",
        ["stage"],
    )
    # Requests rejected with 429 by app/ratelimit.py. A steady low rate is healthy
    # (a client retrying); a spike is either a runaway client or someone probing, and
    # either way it is the cheapest early warning of an LLM bill about to run away.
    RATE_LIMITED = Counter(
        "cerebrozen_rate_limited_total",
        "Requests rejected by the rate limiter, by bucket.",
        ["bucket"],
    )
    # Crisis takeovers fired, by detection LAYER (lexicon / classifier) and language.
    # Content-free by construction — the labels are low-cardinality signal, never a word
    # the person wrote (CLAUDE.md rule 5). This is the counted safety event the release
    # gate reads: a sudden change in the lexicon/classifier split, or a drop to zero, is a
    # regression in the one path that must never silently break.
    CRISIS_TRIGGERED = Counter(
        "cerebrozen_crisis_triggered_total",
        "Crisis takeovers fired, by detection layer and language.",
        ["detected_by", "lang"],
    )
    # Turns where the user treated the coach as a person / a relationship / a clinician and
    # the mandatory disclosure was injected. Label is the KIND only — never a word the
    # person wrote (rule 5). Read it as the companion-drift signal: a product whose
    # `attachment` count climbs is being used as something it is not sold as, which is a
    # design problem long before it is a compliance one.
    BOUNDARY_PROMPTED = Counter(
        "cerebrozen_boundary_prompted_total",
        "Mandatory coach-not-companion disclosures injected, by kind.",
        ["kind"],
    )
    # Session-pacing interventions: a long-session pause offer, or a support route after
    # repeated not-coping messages. Kind only — never a word the person wrote. `pause` is
    # a healthy number to see; a rising `distress_route` says the population using this is
    # carrying more than a coaching product is the right answer for.
    SESSION_PACING = Counter(
        "cerebrozen_session_pacing_total",
        "Session-pacing interventions injected, by kind.",
        ["kind"],
    )


def record_rate_limited(*, bucket: str) -> None:
    """Count one 429. No-op when Prometheus is absent."""
    if _ENABLED:
        RATE_LIMITED.labels(bucket or "unknown").inc()


def record_crisis(*, detected_by: str, lang: str) -> None:
    """Count one crisis takeover, by detection layer + language. Content-free: never called
    with anything the person wrote. No-op when Prometheus is absent."""
    if _ENABLED:
        CRISIS_TRIGGERED.labels(detected_by or "unknown", lang or "unknown").inc()


def record_pacing(*, kind: str) -> None:
    """Count one session-pacing intervention (pause offer / support route), by kind.
    Content-free: never called with anything the person wrote. No-op without Prometheus."""
    if _ENABLED:
        SESSION_PACING.labels(kind or "unknown").inc()


def record_boundary(*, kind: str) -> None:
    """Count one mandatory coach-not-companion disclosure, by kind. Content-free: never
    called with anything the person wrote. No-op when Prometheus is absent."""
    if _ENABLED:
        BOUNDARY_PROMPTED.labels(kind or "unknown").inc()


def record_contract_violation(*, stage: str, contract: str) -> None:
    """Count one agent output-contract violation. No-op when Prometheus is absent."""
    if _ENABLED:
        CONTRACT_VIOLATIONS.labels(stage or "unknown", contract or "unknown").inc()


def record_stage_watchdog(*, stage: str) -> None:
    """Count one watchdog-forced stage advance. No-op when Prometheus is absent."""
    if _ENABLED:
        STAGE_WATCHDOG.labels(stage or "unknown").inc()


def record_llm(
    *,
    stage: str,
    model: str,
    latency_ms: float,
    prompt_tokens: int,
    cached_tokens: int,
    completion_tokens: int,
    cost_usd: float,
) -> None:
    """Record one completed LLM call to Prometheus (/metrics) and, when OTEL is on,
    to the OTLP meter (→ ADOT collector → Prometheus). Both are no-ops when absent.
    Pure in-memory/async — adds no user-facing latency."""
    stage = stage or "unknown"
    model = model or "unknown"
    if _ENABLED:
        LLM_CALLS.labels(stage, model).inc()
        LLM_COST.labels(stage, model).inc(cost_usd or 0.0)
        LLM_LATENCY.labels(stage, model).observe((latency_ms or 0.0) / 1000.0)
        LLM_TOKENS.labels(stage, model, "prompt").inc(prompt_tokens or 0)
        LLM_TOKENS.labels(stage, model, "cached").inc(cached_tokens or 0)
        LLM_TOKENS.labels(stage, model, "completion").inc(completion_tokens or 0)
    _record_llm_otel(
        stage, model, latency_ms, prompt_tokens, cached_tokens, completion_tokens, cost_usd
    )


# --- OTEL meter (lazy; → ADOT collector → Prometheus) -----------------------
_otel = {"init": False, "calls": None, "cost": None, "latency": None, "tokens": None}


def _otel_instruments():
    """Lazily build OTEL instruments after the meter provider is configured.
    Returns the dict, or None if OTEL/metrics are unavailable."""
    if _otel["init"]:
        return _otel if _otel["calls"] is not None else None
    _otel["init"] = True
    try:
        from app import config

        if not config.OTEL_METRICS_ENABLED:
            return None
        from opentelemetry import metrics as ot

        meter = ot.get_meter("cerebrozen.llm")
        _otel["calls"] = meter.create_counter("cerebrozen_llm_calls", description="LLM calls")
        _otel["cost"] = meter.create_counter("cerebrozen_llm_cost_usd", description="LLM cost USD")
        _otel["latency"] = meter.create_histogram(
            "cerebrozen_llm_latency_seconds", unit="s", description="LLM call latency"
        )
        _otel["tokens"] = meter.create_counter("cerebrozen_llm_tokens", description="LLM tokens")
        return _otel
    except Exception:  # noqa: BLE001 — OTEL absent/misconfigured → skip silently.
        return None


def _record_llm_otel(stage, model, latency_ms, prompt_tokens, cached_tokens, completion_tokens, cost_usd) -> None:
    inst = _otel_instruments()
    if inst is None:
        return
    attrs = {"stage": stage, "model": model}
    try:
        inst["calls"].add(1, attrs)
        inst["cost"].add(cost_usd or 0.0, attrs)
        inst["latency"].record((latency_ms or 0.0) / 1000.0, attrs)
        inst["tokens"].add(prompt_tokens or 0, {**attrs, "kind": "prompt"})
        inst["tokens"].add(cached_tokens or 0, {**attrs, "kind": "cached"})
        inst["tokens"].add(completion_tokens or 0, {**attrs, "kind": "completion"})
    except Exception:  # noqa: BLE001 — never let metrics raise on the call path.
        pass


def metrics_asgi_app():
    """ASGI app for /metrics, or None when prometheus_client isn't installed."""
    if not _ENABLED:
        logger.info("metrics.disabled", extra={"reason": "prometheus_client not installed"})
        return None
    return make_asgi_app()
