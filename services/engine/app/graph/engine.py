"""Engine wrapper around the compiled graph.

Owns the per-turn run: derives is_first_turn from the checkpointer, threads the
SSE status/token callbacks into the graph, and maps the final state onto the
response shape the existing /v1/stream contract expects.
"""

from __future__ import annotations

import logging
from typing import Any, Callable, Dict, Optional

from app.graph.build_graph import build_graph
from app.tenancy import thread_id_for
from app.graph.state import STAGE_CORE, STAGE_PATTERN
from app.roi_metrics import get_roi_metrics
from app.tracing_otel import get_tracer

logger = logging.getLogger("cerebrozen.engine")


class CereBroZenEngine:
    def __init__(self) -> None:
        self.graph = build_graph()

    def _thread(self, session_id: str, on_token=None, on_status=None, on_node=None) -> Dict[str, Any]:
        # thread_id_for(), not an f-string: erasure has to find these exact rows, and when
        # each side built the key itself they silently disagreed (app/tenancy.py).
        cfg: Dict[str, Any] = {"configurable": {"thread_id": thread_id_for(session_id)}}
        if on_token is not None:
            cfg["configurable"]["on_token"] = on_token
        if on_status is not None:
            cfg["configurable"]["on_status"] = on_status
        if on_node is not None:
            # Structured per-node lifecycle events (flow view). Nodes emit `start`;
            # this engine emits `end` from each node's state delta.
            cfg["configurable"]["on_node"] = on_node
        return cfg

    def is_first_turn(self, session_id: str) -> bool:
        # Fast path: a Redis "seen" marker means this session already ran a turn, so
        # skip the remote-Mongo get_state probe. No marker (or Redis down) → fall back
        # to the authoritative checkpoint read, so this never misjudges a first turn.
        try:
            from app.stores.redis_state import is_session_seen

            if is_session_seen(session_id):
                return False
        except Exception:  # noqa: BLE001 — fall back to get_state on any cache issue
            pass
        snapshot = self.graph.get_state(self._thread(session_id))
        return not (snapshot and snapshot.values)

    def session_state(self, session_id: str) -> Dict[str, Any]:
        """The checkpointed state values for a session (for the session-close
        context builder, which needs the full transcript + user_context)."""
        snapshot = self.graph.get_state(self._thread(session_id))
        return (snapshot.values if snapshot else {}) or {}

    def find_edit_fork(self, session_id: str) -> Optional["tuple[str, bool]"]:
        """Locate the checkpoint to re-run the LAST turn from with an edited message.

        Returns ``(fork_checkpoint_id, is_first_turn)`` or ``None`` when there is no
        turn to edit. The fork point is the checkpoint at the END of the previous
        turn — i.e. the snapshot just older than the most-recent ``source=="input"``
        checkpoint (each turn's ``stream`` writes exactly one ``input`` checkpoint).
        When the last turn IS the first turn, fork from that input checkpoint itself
        (its history is empty) and re-run as a fresh first turn.
        """
        snaps = list(self.graph.get_state_history(self._thread(session_id)))
        input_idxs = [
            i for i, s in enumerate(snaps) if (s.metadata or {}).get("source") == "input"
        ]
        if not input_idxs:
            return None
        last_input = input_idxs[0]  # newest-first → [0] is the last turn's input
        if last_input + 1 < len(snaps):
            return snaps[last_input + 1].config["configurable"]["checkpoint_id"], False
        return snaps[last_input].config["configurable"]["checkpoint_id"], True

    def run_turn_stream(
        self,
        *,
        user_id: str,
        session_id: str,
        bot_name: str,
        user_message: str,
        user_language: Optional[str] = None,
        session_continued: Optional[str] = None,
        conversation_mode: Optional[str] = None,
        checkin_action: Optional[Dict[str, Any]] = None,
        on_status: Optional[Callable[[str], None]] = None,
        on_token: Optional[Callable[[str], None]] = None,
        on_node: Optional[Callable[[Dict[str, Any]], None]] = None,
        from_checkpoint_id: Optional[str] = None,
        is_first_turn_override: Optional[bool] = None,
    ) -> Dict[str, Any]:
        """Run one turn. When ``from_checkpoint_id`` is given the turn FORKS from
        that checkpoint (time-travel) instead of the thread tip — used to edit and
        regenerate the last message; ``is_first_turn_override`` then sets the flag."""
        first = (
            is_first_turn_override
            if is_first_turn_override is not None
            else self.is_first_turn(session_id)
        )
        # Pass on_status into config so each node announces ITSELF when it starts
        # (accurate "Running: <agent>"), instead of the engine announcing the
        # previous node after it finished.
        cfg = self._thread(session_id, on_token, on_status, on_node)
        # Fork from a past checkpoint (edit/regenerate) when asked; the new branch
        # becomes the thread tip, so later turns continue on the corrected history.
        if from_checkpoint_id:
            cfg["configurable"]["checkpoint_id"] = from_checkpoint_id
        # Trace metadata for LangSmith (no-op when tracing is off): groups every
        # node span of this turn under one named run, filterable by session.
        cfg["run_name"] = f"cerebrozen-{'edit' if from_checkpoint_id else 'turn'}:{session_id}"
        cfg["tags"] = ["cerebrozen", "cim"]
        cfg["metadata"] = {
            "session_id": session_id,
            "user_id": user_id,
            "is_first_turn": first,
        }

        graph_input: Dict[str, Any] = {
            "user_id": user_id,
            "session_id": session_id,
            "bot_name": bot_name,
            "user_message": user_message,
            "is_first_turn": first,
            "user_language": user_language or "",
            # CH phase button press from the UI (e.g. "continue_to_phase_2"); "" otherwise.
            # profile_read_node injects this into user_context as {session_continued} every turn.
            "session_continued": session_continued or "",
            # "voice" | "text" — profile_read_node injects this into user_context as
            # {conversation_mode} every turn.
            "conversation_mode": conversation_mode or "text",
        }

        # Standalone action check-in: on the FIRST turn, seed the entry stage + the tapped
        # action's fields so profile_read (which only sets `stage` when unset) leaves the
        # check-in stage in place and dispatch routes straight to action_checkin_node. The
        # fields persist across the sticky arc via the checkpointer.
        if first and checkin_action:
            from app.graph.state import STAGE_ACTION_CHECKIN
            graph_input["stage"] = STAGE_ACTION_CHECKIN
            graph_input["checkin_action"] = checkin_action

        # Drive the graph to completion; nodes emit their own status on start.
        # Watch the per-node update stream for the closing layer completing this
        # turn (feedback hands off → terminal close), so the session-close builders
        # fire exactly once on a NATURAL end too (not only on /endconversation).
        closed_naturally = False
        checkin_completed = False
        # The pattern_agent's in_session mirror is now surfaced by the `pattern` graph
        # node (its own reflect-beat turn right after simulation), not here in the engine.
        fresh_captured: Dict[str, Any] = {}
        _dynamic_var_capture_count: int = 0  # for post-stream logging only
        _dynamic_var_stages: list = []
        _node_call_seq: int = 0  # sequential counter across all node updates this turn
        from app.graph.builders import dispatch_dynamic_vars as _dispatch_dyn_vars
        # One OTLP span per turn (no-op when OTEL off; async export → no added
        # latency). Node/LLM spans nest under it for an X-Ray turn waterfall.
        final_values: Dict[str, Any] = {}
        # The stage this turn ENTERS on. Compared with the stage it ends on to decide
        # whether the next agent's prompt prefix needs prewarming (see below).
        try:
            entry_stage = (self.graph.get_state(self._thread(session_id)).values or {}).get("stage", "")
        except Exception:  # noqa: BLE001
            entry_stage = ""
        with get_tracer().start_as_current_span("cerebrozen.turn") as _turn_span:
            _turn_span.set_attribute("cerebrozen.session_id", session_id)
            _turn_span.set_attribute("cerebrozen.user_id", user_id or "")
            _turn_span.set_attribute("cerebrozen.is_first_turn", bool(first))
            # Stream BOTH per-node updates (closing/checkin/builder detection + fresh
            # captures) AND full state values — the last `values` chunk is the final
            # state, captured here instead of paying a second remote-Mongo get_state.
            for mode, chunk in self.graph.stream(
                graph_input, cfg, stream_mode=["updates", "values"]
            ):
                if mode == "values":
                    if isinstance(chunk, dict):
                        final_values = chunk
                    continue
                for node_name, delta in (chunk or {}).items():
                    if not isinstance(delta, dict):
                        continue
                    _node_call_seq += 1
                    # Structured node-completed event for the flow view. The delta is
                    # the node's own state write, so it already carries everything the
                    # UI wants: which branch the routing took (stage), whether the node
                    # handed off, and what the call cost.
                    if on_node:
                        try:
                            on_node({
                                "phase": "end",
                                "node": node_name,
                                "seq": _node_call_seq,
                                "stage": delta.get("stage") or "",
                                "active_node": delta.get("active_node") or "",
                                "handoff_ready": bool(delta.get("handoff_ready")),
                                "coaching_path": delta.get("coaching_path") or "",
                                "prompt_tokens": delta.get("prompt_tokens") or 0,
                                "completion_tokens": delta.get("completion_tokens") or 0,
                                "cost_usd": delta.get("cost_usd") or 0.0,
                                "replied": bool((delta.get("reply_text") or "").strip()),
                            })
                        except Exception:  # noqa: BLE001 — telemetry never breaks a turn
                            pass
                    # A session closes naturally two ways: the feedback layer finishing
                    # (the normal full-journey close), or a CH Phase-1/2 "Save & Exit"
                    # (capability node → direct close; business rule 2026-07-10: exit goes
                    # straight to Home with no feedback ritual). Both must fire the
                    # session-close builders. `session_complete` deltas are excluded — that
                    # node re-emits stage=close on every post-close ping and would re-run
                    # the builders each time.
                    if (
                        node_name in ("feedback", "capability")
                        and delta.get("handoff_ready")
                        and delta.get("stage") == "close"
                    ):
                        closed_naturally = True
                    # Repeat-user check-in handing off = it closed the due batches this
                    # turn. Mark them complete once (BRD R3), using the code-computed ids.
                    if node_name == "checkin" and delta.get("handoff_ready"):
                        checkin_completed = True
                    # Any node emitting captured_variables → persist immediately so
                    # variables land in agentic_session_dynamic_variables as soon as
                    # the LLM response arrives, not after the full turn completes.
                    if delta.get("captured_variables"):
                        _vars = dict(delta["captured_variables"])
                        fresh_captured = delta["captured_variables"]
                        try:
                            _dispatch_dyn_vars(
                                user_id=user_id,
                                session_id=session_id,
                                variables=_vars,
                                stage=node_name,
                                turn_seq=_node_call_seq,
                            )
                            _dynamic_var_capture_count += 1
                            _dynamic_var_stages.append(node_name)
                        except Exception:  # noqa: BLE001
                            logger.warning("engine.dynamic_vars_dispatch_failed", exc_info=True)

        # Prefer the final state accumulated from the stream (no extra round-trip).
        # Fall back to get_state only if the stream yielded no values (defensive) —
        # the fresh thread cfg (no checkpoint_id) returns a forked edit's new branch.
        final = final_values or (self.graph.get_state(self._thread(session_id)).values or {})

        # KV-cache prewarm (offline/Ollama backend only; a no-op on OpenAI).
        #
        # ONLY when the stage actually CHANGED. The local model caches ONE prompt prefix
        # at a time — prewarming a different agent mid-stage would EVICT the prefix the
        # user is about to reuse and make their next turn SLOWER. Because the graph is
        # deterministic, a stage change tells us precisely which agent runs next, so we
        # can pay its 10s cold-read in the background while the user reads and types.
        try:
            _next_stage = final.get("stage") or ""
            if _next_stage and _next_stage != (entry_stage or ""):
                from app.graph.builders import dispatch_prewarm

                dispatch_prewarm(
                    _next_stage,
                    final.get("user_context") or {},
                    final.get("coaching_path") or "",
                )
        except Exception:  # noqa: BLE001 — prewarm is best-effort, never a turn failure
            logger.warning("engine.prewarm_dispatch_failed", exc_info=True)

        # Mark the session seen so the NEXT turn's is_first_turn skips its get_state.
        try:
            from app.stores.redis_state import mark_session_seen

            mark_session_seen(session_id)
        except Exception:  # noqa: BLE001 — fast-path marker only
            pass

        # Action/insight cards are generated by dynamic_actions_insights_node (graph node,
        # foreground, in-turn). The node stores shaped cards in state["generated_actions"]
        # / state["generated_insights"] on its first invocation; cleared to [] on its
        # second (handoff-only) invocation so stale cards are never re-sent. The
        # /actions-insights endpoint also serves both independently of the inline payload.
        generated_actions: list = final.get("generated_actions") or []
        generated_insights: list = final.get("generated_insights") or []

        # Repeat-user check-in finished → permanently mark the closed batches off-path
        # (BRD R3 + idempotency) so the same actions never resurface for check-in.
        if checkin_completed:
            try:
                from app.graph.builders import dispatch_checkin_complete

                session_ids = (final.get("user_context") or {}).get("checkinSessionIds") or []
                dispatch_checkin_complete(user_id, session_ids)
            except Exception:  # noqa: BLE001 — must not affect a turn
                logger.warning("engine.checkin_complete_dispatch_failed", exc_info=True)

        # Persist freshly-captured intake variables_set to the legacy agentic store
        # off-path (backward-compat: other readers that query intake_vars still work).
        if fresh_captured:
            try:
                from app.graph.builders import dispatch_intake_vars

                dispatch_intake_vars(user_id, fresh_captured)
            except Exception:  # noqa: BLE001 — background capture must not affect a turn
                logger.warning("engine.intake_vars_dispatch_failed", exc_info=True)

        # Log a summary of dynamic vars dispatched this turn (dispatched inline during
        # the stream — see the capture block above).
        if _dynamic_var_capture_count:
            from app.request_context import request_id as _req_id_ctx

            logger.info(
                "engine.dynamic_vars_dispatched",
                extra={
                    "user_id": user_id,
                    "session_id": session_id,
                    "request_id": _req_id_ctx.get(""),
                    "capture_count": _dynamic_var_capture_count,
                    "stages": _dynamic_var_stages,
                },
            )

        # Natural session end (closing layer fired EndOfConversation): build + persist
        # the User Context Model AND the cumulative Pattern Intelligence Model
        # (pattern_agent `background_write`) off-path, same as /endconversation. This
        # runs post-session so the cumulative scan sees the FULL session; the live
        # user-facing mirror already ran in the foreground after simulation (above).
        if closed_naturally:
            try:
                from app.graph.builders import (
                    dispatch_context_builder,
                    dispatch_pattern_write,
                )

                history = final.get("history", [])
                dispatch_context_builder(user_id, session_id, history, final.get("user_context", {}))
                dispatch_pattern_write(user_id, session_id, history)
                logger.info("engine.natural_close_builders", extra={"session_id": session_id})
            except Exception:  # noqa: BLE001 — background capture must not affect a turn
                logger.warning("engine.natural_close_dispatch_failed", exc_info=True)

        # The in-session pattern mirror is now surfaced by the `pattern` graph node as
        # its OWN user-facing turn (spec: reflect beat right after simulation), so it
        # arrives as a normal reply — no post-hoc folding onto another node's reply.
        _reply = final.get("reply_text", "") or ""
        # `pattern_mirror` is retained for telemetry / back-compat: it's the mirror text,
        # non-empty ONLY on the turn the pattern node was the terminal (surfacing) stage.
        _pattern_mirror = _reply if final.get("active_node") == STAGE_PATTERN else ""
        result: Dict[str, Any] = {
            "session_id": session_id,
            "response_to_user": _reply,
            "handoff_ready": final.get("handoff_ready", False),
            "coaching_path": final.get("coaching_path"),
            "stage": final.get("stage", STAGE_CORE),
            "active_node": final.get("active_node", ""),
            "safety_flag": final.get("safety_flag", "ok"),
            "is_first_turn": first,
            # Actions/insights generated THIS turn ship inline so clients render cards
            # straight from the `done` payload; [] on other turns.
            "actions": generated_actions,
            "insights": generated_insights,
            # The pattern mirror surfaced this turn — non-empty only when the `pattern`
            # node was the terminal stage (it delivers the mirror as response_to_user).
            # Retained for telemetry / back-compat.
            "pattern_mirror": _pattern_mirror,
            # CH phase tracking: active_phase is the current step name the CH agent
            # is on (e.g. "phase_1", "phase_1_complete"); phase_buttons are the
            # transition buttons to render when a phase completes ([] otherwise).
            "active_phase": final.get("active_phase", ""),
            "phase_buttons": final.get("phase_buttons") or [],
            "prompt_tokens": final.get("prompt_tokens", 0),
            "completion_tokens": final.get("completion_tokens", 0),
            "cost_usd": final.get("cost_usd", 0.0),
        }
        # Ship the Development-Area catalogue only when there are cards to render it for.
        if generated_actions:
            result["available_roi_metrics"] = get_roi_metrics()
        return result
