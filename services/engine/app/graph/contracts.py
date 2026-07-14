"""Agent output-contract monitors.

The graph is deterministic, but every routing decision it makes is driven by a
structured signal the AGENT emits (coaching_path, phase milestones, action
cards). So the graph is only as reliable as those signals — and a prompt edit can
silently stop emitting one. That failure mode is invisible: routing keeps
"working", it just takes the fallback path forever. Live examples:

  - a 27-turn CH session emitted `awaiting_phase_transition` ZERO times, so the
    per-phase Action beat never fired ("no actions for Phase 1");
  - challenge_context handed off with no `coaching_path`, so every session
    silently fell back to CIM;
  - learning_aid skipped its delivery arc straight to `commit`.

Each is a CONTRACT violation: the agent completed its stage without emitting the
field the graph depends on. This module states those contracts explicitly and
checks them on every turn, so a prompt that stops emitting a milestone shows up
as a violation-rate metric (and an alert) instead of a bug report weeks later.

Advisory by design: a violation is logged + counted, never raised. The graph's
fallbacks still run — we want the signal, not a new failure mode.
"""

from __future__ import annotations

import logging
from typing import Any, Dict, List

from app.graph.state import (
    STAGE_CH,
    STAGE_CHALLENGE,
    STAGE_DYNAMIC_ACTIONS,
    STAGE_LEARNING_AID,
)
from app.metrics import record_contract_violation
from app.request_context import request_id as _req_id_ctx

logger = logging.getLogger("cerebrozen.contracts")

_VALID_PATHS = ("CIM", "CBT", "CH")


def _pick_path(raw: Dict[str, Any]) -> str:
    """The coaching path, looked up exactly where the router looks for it.

    Mirrors tools.parse_control._pick_path: snake_case or camelCase, top level or
    nested in `context_update` (which is where the live challenge_context prompt
    actually emits it). Returns "" when no usable path is present."""
    def _one(d: Any) -> str:
        if isinstance(d, dict):
            return str(d.get("coaching_path") or d.get("coachingPath") or "").strip().upper()
        return ""
    path = _one(raw) or _one(raw.get("context_update"))
    return path if path in _VALID_PATHS else ""


def check_handoff_contract(
    stage: str,
    raw: Dict[str, Any],
    state: Dict[str, Any],
) -> List[str]:
    """Check the contract a stage must satisfy AT HANDOFF (its stage is complete).

    `raw` is the agent's parsed JSON envelope for this turn. Returns the list of
    violated contract names (empty when the agent held up its end)."""
    violations: List[str] = []

    if stage == STAGE_CHALLENGE:
        # challenge_context's ONE job is to decide the coaching path. Handing off
        # without it means every downstream route is a silent CIM fallback.
        #
        # Look wherever the ROUTER looks (tools.parse_control._pick_path): snake or
        # camel case, top level or nested in context_update — the live prompt emits
        # `context_update.coaching_path`. A monitor that checked fewer places than the
        # router would cry wolf on every healthy handoff.
        if not _pick_path(raw):
            violations.append("challenge_no_coaching_path")

    if stage == STAGE_CH:
        # The CH arc is 3 phases; phases 1 and 2 each close with a milestone that
        # fires the per-phase Action beat. Reaching the final handoff having never
        # emitted one means the phase milestones were never sent.
        if not (state.get("ch_beats_fired") or []):
            violations.append("ch_no_phase_milestone")

    if stage == STAGE_LEARNING_AID:
        # The aid must actually be DELIVERED before the commit step — a prompt
        # regression made it jump straight to `commit` with content in hand.
        progress = state.get("learning_aid_progress") or {}
        step = str(raw.get("current_step") or progress.get("current_step") or "").strip().lower()
        delivered = bool(progress.get("delivered") or progress.get("grasp"))
        if step == "commit" and not delivered:
            violations.append("learning_aid_commit_without_delivery")

    return violations


def check_turn_contract(stage: str, raw: Dict[str, Any]) -> List[str]:
    """Check the per-TURN contract (things that must hold on every turn of a stage)."""
    violations: List[str] = []
    if stage == STAGE_DYNAMIC_ACTIONS:
        # The actions gate exists to produce action cards. An invocation that
        # yields none is the "session closed with zero actions" bug at its source.
        actions = raw.get("actions") or raw.get("action_cards") or []
        if not actions:
            violations.append("dynamic_actions_no_cards")
    return violations


def report(stage: str, violations: List[str], state: Dict[str, Any]) -> None:
    """Log + count contract violations. Never raises."""
    if not violations:
        return
    for name in violations:
        try:
            record_contract_violation(stage=stage, contract=name)
        except Exception:  # noqa: BLE001 — a metric must never break a turn
            pass
    logger.warning(
        "agent.contract_violation",
        extra={
            "stage": stage,
            "violations": violations,
            "session_id": state.get("session_id", ""),
            "user_id": state.get("user_id", ""),
            "request_id": _req_id_ctx.get(""),
        },
    )
