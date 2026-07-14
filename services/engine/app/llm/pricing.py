"""Per-turn cost estimation (constitution: cost is measured, not estimated in prose).

A small pricing table in USD per 1M tokens, keyed by model. Cached input tokens
(prompt-cache hits) are priced separately and cheaper. Prices are best-effort and
env-overridable; an unknown model returns 0.0 (logged once) and never blocks a turn.

Override a single price with CEREBROZEN_PRICE_<MODEL>="<in>,<cached_in>,<out>" where
<MODEL> upper-cases the model id with non-alphanumerics turned to underscores,
e.g. CEREBROZEN_PRICE_GPT_5_MINI="0.25,0.025,2.00".
"""

from __future__ import annotations

import logging
import os
import re
from typing import Dict, Tuple

logger = logging.getLogger("cerebrozen.pricing")

# (input, cached_input, output) USD per 1,000,000 tokens. Best-effort defaults;
# adjust here or via env as OpenAI pricing changes.
# "gpt-5" acts as the family-level fallback for gpt-5.x point releases (e.g. gpt-5.4).
_DEFAULT_PRICES: Dict[str, Tuple[float, float, float]] = {
    "gpt-5":      (10.00, 2.50, 40.00),
    "gpt-5-mini": ( 0.25, 0.025, 2.00),
    "gpt-5-nano": ( 0.05, 0.005, 0.40),
    "gpt-4o-mini": (0.15, 0.075, 0.60),
}

_PER_MILLION = 1_000_000.0
_warned_unknown: set[str] = set()


def _env_key(model: str) -> str:
    return "CEREBROZEN_PRICE_" + re.sub(r"[^A-Za-z0-9]", "_", model).upper()


def _prices_for(model: str) -> Tuple[float, float, float] | None:
    """Resolve (input, cached, output) per-1M prices for a model, env first.

    Falls back to progressively shorter prefixes so point-release models (e.g.
    gpt-5.4) automatically resolve to the family entry (gpt-5) without needing
    an explicit table row for every version."""
    raw = os.environ.get(_env_key(model), "").strip()
    if raw:
        try:
            parts = [float(p) for p in raw.split(",")]
            if len(parts) == 3:
                return (parts[0], parts[1], parts[2])
        except ValueError:
            logger.warning("pricing.bad_env_override", extra={"model": model, "value": raw})
    if model in _DEFAULT_PRICES:
        return _DEFAULT_PRICES[model]
    # Prefix fallback: strip trailing .N or -tag segments one at a time.
    candidate = re.sub(r"[.\-][^.\-]+$", "", model)
    while candidate and candidate != model:
        if candidate in _DEFAULT_PRICES:
            return _DEFAULT_PRICES[candidate]
        nxt = re.sub(r"[.\-][^.\-]+$", "", candidate)
        if nxt == candidate:  # nothing left to strip -> stop (was an infinite loop)
            break
        candidate = nxt
    return None


def estimate_cost(
    model: str,
    prompt_tokens: int = 0,
    cached_tokens: int = 0,
    completion_tokens: int = 0,
) -> float:
    """USD cost estimate for one call. Cached tokens are billed at the cached rate
    and are a subset of prompt_tokens (so non-cached prompt = prompt - cached).
    Unknown model → 0.0 (warned once)."""
    prices = _prices_for(model)
    if prices is None:
        if model and model not in _warned_unknown:
            _warned_unknown.add(model)
            logger.warning("pricing.unknown_model", extra={"model": model})
        return 0.0
    in_price, cached_price, out_price = prices
    non_cached_prompt = max(0, prompt_tokens - cached_tokens)
    cost = (
        non_cached_prompt * in_price
        + cached_tokens * cached_price
        + completion_tokens * out_price
    ) / _PER_MILLION
    return round(cost, 6)
