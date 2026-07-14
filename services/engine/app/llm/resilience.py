"""Request-path resilience: retry classification, backoff, circuit breaker.

Constitution / tech-stack "Resilience": every request-path call is bounded —
timeout (set on the OpenAI client), retry+backoff, model fallback, circuit
breaker. A hung call never hangs a turn. This module holds the pure (network-free)
pieces so they're unit-testable; the client (`responses_client.py`) composes them.
"""

from __future__ import annotations

import logging
import random
import threading
import time
from typing import List

from app import config

logger = logging.getLogger("cerebrozen.resilience")


class BreakerOpen(Exception):
    """Raised when the circuit breaker is open — caller degrades to a safe reply
    instead of calling OpenAI."""


# --- retry classification ----------------------------------------------------

# Matched on the exception's class name (avoids importing openai's exception tree,
# which varies across SDK versions) plus a couple of stdlib network errors.
_RETRYABLE_NAMES = {
    "APITimeoutError",
    "APIConnectionError",
    "APIConnectionTimeoutError",
    "InternalServerError",
    "RateLimitError",
    "Timeout",
    "TimeoutError",
    "ConnectionError",
    "ReadTimeout",
    "ServiceUnavailableError",
}
# Never retry these — a retry can't help and may burn quota / leak auth failures.
_NON_RETRYABLE_NAMES = {
    "AuthenticationError",
    "PermissionDeniedError",
    "BadRequestError",
    "NotFoundError",
    "UnprocessableEntityError",
    "ConflictError",
}


def is_retryable(exc: BaseException) -> bool:
    """True if the error is transient and worth retrying. Rate limits and 5xx /
    timeouts / connection drops are retryable; auth and 4xx (except 429) are not.
    Also inspects an HTTP `status_code` attribute when present (5xx or 429)."""
    name = type(exc).__name__
    if name in _NON_RETRYABLE_NAMES:
        return False
    if name in _RETRYABLE_NAMES:
        return True
    status = getattr(exc, "status_code", None) or getattr(exc, "status", None)
    if isinstance(status, int):
        return status == 429 or 500 <= status < 600
    return False


# --- backoff -----------------------------------------------------------------


def backoff_delays(
    attempts: int,
    base: float | None = None,
    cap: float | None = None,
) -> List[float]:
    """Exponential backoff with full jitter, capped. Returns the delay (seconds)
    to sleep *before* each retry — so `attempts` retries yield `attempts` delays.
    Jitter (random in [0, computed]) spreads retries to avoid thundering herd."""
    base = config.LLM_BACKOFF_BASE_S if base is None else base
    cap = config.LLM_BACKOFF_MAX_S if cap is None else cap
    delays: List[float] = []
    for i in range(max(0, attempts)):
        ceiling = min(cap, base * (2 ** i))
        delays.append(round(random.uniform(0, ceiling), 3))
    return delays


# --- circuit breaker ---------------------------------------------------------


class CircuitBreaker:
    """Process-local breaker. Closed → calls flow. After `fail_threshold`
    consecutive failures it Opens (calls short-circuit) for `cooldown_s`, then
    Half-opens to let ONE probe through: success closes it, failure re-opens it.
    Thread-safe; one in-flight turn per session still shares the process breaker.
    """

    def __init__(self, fail_threshold: int | None = None, cooldown_s: float | None = None) -> None:
        self.fail_threshold = (
            config.BREAKER_FAIL_THRESHOLD if fail_threshold is None else fail_threshold
        )
        self.cooldown_s = config.BREAKER_COOLDOWN_S if cooldown_s is None else cooldown_s
        self._lock = threading.Lock()
        self._consecutive_failures = 0
        self._opened_at: float | None = None
        self._half_open = False

    @property
    def state(self) -> str:
        with self._lock:
            if self._opened_at is None:
                return "closed"
            return "half_open" if self._half_open else "open"

    def allow(self) -> bool:
        """Whether a call may proceed now. Transitions open→half_open after the
        cooldown elapses (allowing a single probe)."""
        with self._lock:
            if self._opened_at is None:
                return True
            if time.monotonic() - self._opened_at >= self.cooldown_s:
                # cooldown elapsed → allow a single probe
                self._half_open = True
                return True
            return False

    def record_success(self) -> None:
        with self._lock:
            was_open = self._opened_at is not None
            self._consecutive_failures = 0
            self._opened_at = None
            self._half_open = False
        if was_open:
            logger.info("breaker.closed")

    def record_failure(self) -> None:
        with self._lock:
            self._consecutive_failures += 1
            # A failed half-open probe (or crossing the threshold) opens the breaker.
            if self._half_open or self._consecutive_failures >= self.fail_threshold:
                self._opened_at = time.monotonic()
                self._half_open = False
                opened = True
            else:
                opened = False
            fails = self._consecutive_failures
        if opened:
            logger.warning("breaker.open", extra={"consecutive_failures": fails})


_breaker: CircuitBreaker | None = None
_breaker_lock = threading.Lock()


def get_breaker() -> CircuitBreaker:
    """The shared process-local breaker."""
    global _breaker
    if _breaker is None:
        with _breaker_lock:
            if _breaker is None:
                _breaker = CircuitBreaker()
    return _breaker


def candidate_models(requested: str) -> List[str]:
    """Models to try in order: the node's requested model first, then the cascade
    (deduped, requested removed from the tail)."""
    ordered = [requested] + [m for m in config.MODEL_CASCADE if m != requested]
    seen: set[str] = set()
    out: List[str] = []
    for m in ordered:
        if m and m not in seen:
            seen.add(m)
            out.append(m)
    return out
