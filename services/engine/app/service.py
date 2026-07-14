"""Service boundary — the strangler-fig seam.

Drives one coaching turn against the LangGraph engine, keyed by an explicit
opaque `session_id` (a UUID minted at session start — NOT `{user_id}.{bot_name}`,
since the coaching path isn't known until a coaching agent runs mid-session).

Three entry points:
  - `start_session` — mint (or adopt) a session_id and run the first turn.
  - `run_turn`      — run a subsequent turn on an existing session_id.
  - `handle_webhook_stream` — DEPRECATED shim for the legacy /v1/webhook contract.
"""

from __future__ import annotations

import logging
from typing import Any, Callable, Dict, Optional

from fastapi import HTTPException

from app import config

from app.graph.engine import CereBroZenEngine
from app.llm.title_generator import dispatch_title_generation
from app.schemas import SessionStartRequest, SessionTurnRequest, WebhookRequest
from app.selector import route_to_graph
from app.session import mint_session_id
from app.stores import conversation
from app.request_context import request_id as _ctx_request_id
from app.stores.redis_state import SessionBusyError, session_lock

logger = logging.getLogger("cerebrozen.service")

OnStatus = Optional[Callable[[str], None]]
OnToken = Optional[Callable[[str], None]]
# Structured per-node lifecycle events (flow view). See graph/engine.run_turn_stream.
OnNode = Optional[Callable[[Dict[str, Any]], None]]

_CLOSE_REPLY = "Thanks for the session — take care, and come back any time."

# Returned when a message arrives on a session that has ALREADY reached its
# terminal close. A finished session is finished: we reject it here (no graph
# run, no LLM) and the user starts a fresh session to continue.
_SESSION_CLOSED_REPLY = (
    "This session has ended. Start a new session whenever you'd like to keep going "
    "— I'll be right here."
)

# Returned when an Action Check-In is tapped but the given action_checkin_id does
# not resolve to a stored action for the user (typically an id-format mismatch:
# get_action keys on the 12-char stable_id `action_id`, so an ObjectId/UUID never
# matches). We refuse to run a hollow, context-less check-in and surface this.
_ACTION_CHECKIN_NOT_FOUND_REPLY = (
    "I couldn't find that action to check in on — it may have been removed, or the "
    "link is out of date. Please reopen the action from your list and try again."
)

# Returned when a second turn arrives while one is already in flight for the SAME
# session (per-session lock held past the wait window — Art. 8.4). The turn is
# rejected, never interleaved; the client retries once the current turn finishes.
_SESSION_BUSY_REPLY = (
    "I'm still working on your previous message — give me a moment and try again."
)


class CoachingService:
    def __init__(self) -> None:
        self.engine = CereBroZenEngine()

    # -- public entry points --------------------------------------------------

    def start_session(
        self,
        user_id: str,
        request: SessionStartRequest,
        on_status: OnStatus = None,
        on_token: OnToken = None,
        on_node: OnNode = None,
    ) -> Dict[str, Any]:
        """Mint (or adopt) a session_id and run the first turn."""
        incoming_session_id = (request.session_id or "").strip()
        session_id = incoming_session_id or mint_session_id()
        # Stamp the freshly-minted session_id into the request context so all
        # downstream log records (safety node, LLM call, etc.) carry it without
        # each logger needing to pass it explicitly.
        from app.request_context import ctx_session_id
        ctx_session_id.set(session_id)
        # is_new distinguishes a genuinely fresh conversation from a caller-supplied
        # session_id with no Mongo history behind it yet — e.g. every voice
        # reconnect re-enters here (a fresh LiveKit room means a fresh
        # CereBroZenBrainLLM with _started=False), so start_session runs again on
        # what should be an EXISTING session_id. If Mongo has no record of it,
        # the caller's session_id continuity broke upstream before reaching us.
        is_new = not incoming_session_id or conversation.get_session(session_id) is None
        logger.info(
            "session.start",
            extra={
                "user_id": user_id,
                "session_id": session_id,
                "is_new": is_new,
                "request_id": _ctx_request_id.get(""),
            },
        )
        # Fire the title-generation LLM call now, in parallel with the coaching
        # agent's own call below — off the request path, so it adds zero latency
        # to the streamed reply. Only for a genuinely new session: a reconnect/
        # resume re-entering start_session on an EXISTING session_id must not
        # regenerate the title from whatever message it's re-entering with.
        # An Action Check-In tap is titled from the ACTION body in _run_turn —
        # dispatching a second title here from the placeholder user message
        # ("hi") races it and can win, mislabeling the saved chat (live incident
        # 2026-07-06: check-in chats titled "Getting started"). Skip it.
        if is_new and not (request.metadata or {}).get("action_checkin_id"):
            dispatch_title_generation(user_id, session_id, request.user_text())
        return self._run_turn(
            user_id=user_id,
            session_id=session_id,
            raw=request.raw_message(),
            user_message=request.user_text(),
            metadata=request.metadata,
            on_status=on_status,
            on_token=on_token,
            on_node=on_node,
        )

    def run_turn(
        self,
        user_id: str,
        session_id: str,
        request: SessionTurnRequest,
        on_status: OnStatus = None,
        on_token: OnToken = None,
        on_node: OnNode = None,
    ) -> Dict[str, Any]:
        """Run a subsequent turn on an existing session."""
        return self._run_turn(
            user_id=user_id,
            session_id=session_id,
            raw=request.raw_message(),
            user_message=request.user_text(),
            metadata=request.metadata,
            hidden=request.hidden,
            on_status=on_status,
            on_token=on_token,
            on_node=on_node,
        )

    def assert_editable(self, user_id: str, session_id: str) -> None:
        """Synchronous pre-check for an edit (raises HTTP 4xx before any streaming).

        404 — unknown session or not the caller's; 409 — ended (read-only);
        400 — no user message to edit.
        """
        doc = conversation.get_session(session_id)
        if not doc or (doc.get("user_id") and doc.get("user_id") != user_id):
            raise HTTPException(status_code=404, detail="Session not found.")
        if doc.get("ended"):
            raise HTTPException(
                status_code=409, detail="Session has ended and cannot be edited."
            )
        if not any(m.get("role") == "user" for m in doc.get("messages", [])):
            raise HTTPException(status_code=400, detail="No user message to edit.")

    def edit_last_message(
        self,
        user_id: str,
        session_id: str,
        request: SessionTurnRequest,
        on_status: OnStatus = None,
        on_token: OnToken = None,
        on_node: OnNode = None,
    ) -> Dict[str, Any]:
        """Replace the last user message and regenerate the reply (LangGraph
        time-travel fork), then sync the transcript. Call `assert_editable` first.
        """
        edited = request.user_text() or request.raw_message()
        fork = self.engine.find_edit_fork(session_id)
        if fork is None:
            raise HTTPException(status_code=400, detail="No turn to edit.")
        fork_ckpt, is_first = fork

        logger.info("session.edit", extra={"user_id": user_id, "session_id": session_id})
        result = self.engine.run_turn_stream(
            user_id=user_id,
            session_id=session_id,
            bot_name="generative-bot",
            user_message=edited,
            user_language=(request.metadata or {}).get("user_language"),
            session_continued=(request.metadata or {}).get("session_continued", ""),
            conversation_mode=(request.metadata or {}).get("conversation_mode", "text"),
            on_status=on_status,
            on_token=on_token,
            on_node=on_node,
            from_checkpoint_id=fork_ckpt,
            is_first_turn_override=is_first,
        )
        result["served_by"] = "graph"
        result["edited"] = True

        # Transcript sync: drop the old last user+bot pair, append the edited pair
        # (regenerated reply) so history/resume match the forked graph state.
        conversation.pop_last_exchange(session_id)
        conversation.record_turn(
            session_id=session_id,
            user_id=user_id,
            user_message=edited,
            bot_text=result.get("response_to_user", ""),
            agent_name=result.get("active_node", ""),
            ended=result.get("stage") == "close",
            active_phase=result.get("active_phase", ""),
            phase_buttons=result.get("phase_buttons") or [],
            hidden=request.hidden,
        )
        result["title"] = conversation.get_session_title(session_id)
        return result

    # -- core turn ------------------------------------------------------------

    def _run_turn(
        self,
        *,
        user_id: str,
        session_id: str,
        raw: str,
        user_message: str,
        metadata: Optional[Dict[str, Any]],
        on_status: OnStatus,
        on_token: OnToken,
        on_node: OnNode = None,
        hidden: bool = False,
    ) -> Dict[str, Any]:
        # Strangler-fig gate: serve with the graph, or return the disabled reply.
        use_graph, reason = route_to_graph(user_id, metadata)
        # Standalone Action Check-In: the UI taps "Action Check-In" on a card and passes
        # its `action_checkin_id` in metadata (first turn only). Resolve the tapped action's
        # full_text/expected_outcome into the agent's input params; this entry always runs
        # the graph (its own mini-session), independent of the rollout gate.
        checkin_action = None
        checkin_action_id = (metadata or {}).get("action_checkin_id")
        if checkin_action_id:
            use_graph, reason = True, "action_checkin"
            from app.stores import agentic
            act = agentic.get_action(user_id, str(checkin_action_id)) or {}
            if not act:
                # The tapped id resolved to no stored action for this user. Do NOT
                # run a hollow, context-less check-in (blank {action_item}/
                # {action_outcome}) — short-circuit and surface the error so a
                # bad/mismatched id fails loudly instead of producing an empty
                # reflection. Root cause is typically an id-format mismatch:
                # get_action keys on the 12-char stable_id `action_id`, so a
                # 24-char ObjectId / UUID sent as action_checkin_id never matches.
                logger.warning(
                    "action_checkin.action_not_found",
                    extra={
                        "user_id": user_id,
                        "session_id": session_id,
                        "action_checkin_id": str(checkin_action_id),
                    },
                )
                return {
                    "session_id": session_id,
                    "response_to_user": _ACTION_CHECKIN_NOT_FOUND_REPLY,
                    "handoff_ready": True,
                    "stage": "close",
                    "served_by": "action_checkin_not_found",
                    "route_reason": "action_checkin_action_not_found",
                    "prompt_tokens": 0,
                    "completion_tokens": 0,
                }
            checkin_action = {
                "action_item": act.get("full_text", ""),
                "action_outcome": act.get("expected_outcome", ""),
            }
            # An Action Check-In is its OWN mini-session. If the tap arrives attached to
            # a session_id that already has turns (UI reused the open chat's / a stale
            # session id), the engine would SKIP the stage seed (it seeds only on a first
            # turn) and the graph would resume THAT session's checkpointed stage — live
            # incident: coaching intake started instead of the check-in, and the check-in
            # got recorded into another chat's transcript. Re-mint a fresh session_id so
            # the check-in always runs isolated; the engine stamps the session_id it is
            # given into the response payload, so the UI receives (and continues on) the
            # new id. Exception: when the reused session IS an in-flight action check-in
            # (UI re-sent the id mid-arc), continue it — no re-mint, no re-title.
            starting_checkin = True
            if not self.engine.is_first_turn(session_id):
                from app.graph.state import STAGE_ACTION_CHECKIN
                try:
                    _stage_now = (self.engine.session_state(session_id) or {}).get("stage", "")
                except Exception:  # noqa: BLE001 — a state probe must never kill the turn
                    _stage_now = ""
                if _stage_now == STAGE_ACTION_CHECKIN:
                    starting_checkin = False  # mid-arc continuation of this same check-in
                else:
                    from app.request_context import ctx_session_id
                    _old_session_id = session_id
                    session_id = mint_session_id()
                    ctx_session_id.set(session_id)  # correlate downstream logs to the new id
                    logger.warning(
                        "action_checkin.session_reused_reminted",
                        extra={
                            "user_id": user_id,
                            "old_session_id": _old_session_id,
                            "session_id": session_id,
                            "old_stage": _stage_now,
                            "action_checkin_id": str(checkin_action_id),
                        },
                    )
            # An Action Check-In carries no user message, so start_session's title
            # dispatch (from user_text) no-ops. Title from the action body instead —
            # off the request path, same background executor. Only when this call
            # actually STARTS the check-in (never re-titles a mid-arc turn).
            if starting_checkin and checkin_action["action_item"]:
                dispatch_title_generation(
                    user_id, session_id, checkin_action["action_item"]
                )
        if not use_graph:
            logger.info(
                "route.disabled",
                extra={"user_id": user_id, "session_id": session_id, "reason": reason},
            )
            return {
                "session_id": session_id,
                "response_to_user": config.GRAPH_DISABLED_MESSAGE,
                "handoff_ready": False,
                "stage": "disabled",
                "served_by": "disabled",
                "route_reason": reason,
                "prompt_tokens": 0,
                "completion_tokens": 0,
            }

        if raw.lower() == conversation.END_CONVERSATION_MARKER:
            return self._close_session(user_id, session_id, raw)

        # A finished session stays finished: once it has reached terminal close,
        # any further message in the SAME session is rejected here — no graph run,
        # no LLM. A true restart mints a NEW session_id (fresh checkpointer thread),
        # so this never blocks continuing the conversation, only re-opening a closed
        # one. /restart is allowed through (it isn't a coaching turn).
        if raw.lower() != conversation.RESTART_MARKER:
            existing = conversation.get_session(session_id)
            if existing and existing.get("ended"):
                logger.info(
                    "turn.rejected_closed",
                    extra={"user_id": user_id, "session_id": session_id},
                )
                return {
                    "session_id": session_id,
                    "response_to_user": _SESSION_CLOSED_REPLY,
                    "handoff_ready": True,
                    "stage": "close",
                    "served_by": "closed",
                    "route_reason": "session_already_closed",
                    "prompt_tokens": 0,
                    "completion_tokens": 0,
                }

        # Log the client-supplied language at turn entry to trace it through the pipeline.
        logger.info(
            "turn.start",
            extra={
                "user_id": user_id,
                "session_id": session_id,
                "route_reason": reason,
                "user_message": user_message[:300],
                "user_language_from_request": (metadata or {}).get("user_language"),
                "request_id": _ctx_request_id.get(""),
            },
        )
        # CH phase button the user pressed this turn ("continue_to_phase_2",
        # "continue_to_phase_3", "save_and_exit") or "" when none pressed.
        session_continued = (metadata or {}).get("session_continued", "")
        if session_continued:
            # Stamp the selection onto the prior phase-completion message BEFORE
            # running the turn, so a "Continue" press only needs this ONE call
            # (turn) instead of two — the client no longer has to call
            # POST /phase-selection separately for Continue buttons. ("Save & Exit"
            # still needs its own direct call: there is no turn to attach it to.)
            # Best-effort: record_phase_selection never raises.
            conversation.record_phase_selection(session_id, session_continued)

        # Serialize turns on this session (Art. 8.4): a second concurrent turn waits,
        # then is cleanly rejected rather than racing the checkpoint. Degrades open if
        # Redis is unavailable.
        try:
            with session_lock(session_id):
                result = self.engine.run_turn_stream(
                    user_id=user_id,
                    session_id=session_id,
                    bot_name="generative-bot",  # internal placeholder; not the recorded agent
                    user_message=user_message,
                    user_language=(metadata or {}).get("user_language"),
                    session_continued=session_continued,
                    conversation_mode=(metadata or {}).get("conversation_mode", "text"),
                    checkin_action=checkin_action,
                    on_status=on_status,
                    on_node=on_node,
                    on_token=on_token,
                )
                result["served_by"] = "graph"

                # Persist the turn to the per-session transcript (best-effort, off the
                # streamed reply — tokens already reached the client by now). The bot
                # message is stamped with the producing agent (active_node) as agent_name.
                ended = result.get("stage") == "close"
                conversation.record_turn(
                    session_id=session_id,
                    user_id=user_id,
                    user_message=user_message,
                    bot_text=result.get("response_to_user", ""),
                    agent_name=result.get("active_node", ""),
                    ended=ended,
                    active_phase=result.get("active_phase", ""),
                    phase_buttons=result.get("phase_buttons") or [],
                    hidden=hidden,
                )
                result["ended"] = ended
                result["title"] = conversation.get_session_title(session_id)
        except SessionBusyError:
            logger.info(
                "turn.rejected_busy",
                extra={"user_id": user_id, "session_id": session_id},
            )
            return {
                "session_id": session_id,
                "response_to_user": _SESSION_BUSY_REPLY,
                "handoff_ready": False,
                "stage": "busy",
                "served_by": "busy",
                "route_reason": "session_in_flight",
                "prompt_tokens": 0,
                "completion_tokens": 0,
            }

        logger.info(
            "turn.done",
            extra={
                "session_id": session_id,
                "stage": result.get("stage"),
                "active_node": result.get("active_node"),
                "handoff_ready": result.get("handoff_ready"),
                "coaching_path": result.get("coaching_path"),
                "safety_flag": result.get("safety_flag"),
                "actions_count": len(result.get("actions") or []),
                "prompt_tokens": result.get("prompt_tokens"),
                "completion_tokens": result.get("completion_tokens"),
                "cost_usd": result.get("cost_usd"),
                "served_by": result.get("served_by"),
                "response_to_user": (result.get("response_to_user") or "")[:300],
                "response_json": result,
            },
        )
        return result

    def _close_session(self, user_id: str, session_id: str, raw: str) -> Dict[str, Any]:
        """Handle an explicit /endconversation: record the close + fire the
        session-close builders off-path — the User Context Model and the cumulative
        Pattern Intelligence Model (pattern_agent `background_write`, full-session
        scan). The live user-facing mirror runs in the foreground after simulation."""
        conversation.record_turn(
            session_id=session_id,
            user_id=user_id,
            user_message=raw,
            bot_text=_CLOSE_REPLY,
            agent_name="session_complete",
            ended=True,
        )
        try:
            from app.graph.builders import (
                dispatch_context_builder,
                dispatch_pattern_write,
            )

            st = self.engine.session_state(session_id)
            history = st.get("history", [])
            dispatch_context_builder(user_id, session_id, history, st.get("user_context", {}))
            dispatch_pattern_write(user_id, session_id, history)
        except Exception:  # noqa: BLE001 — background capture is best-effort
            logger.warning("service.session_close_builders_dispatch_failed", exc_info=True)
        return {
            "session_id": session_id,
            "response_to_user": _CLOSE_REPLY,
            "handoff_ready": True,
            "stage": "close",
            "served_by": "graph",
            "prompt_tokens": 0,
            "completion_tokens": 0,
            "title": conversation.get_session_title(session_id),
        }

    # -- deprecated legacy webhook -------------------------------------------

    def handle_webhook_stream(
        self,
        request: WebhookRequest,
        on_status: OnStatus = None,
        on_token: OnToken = None,
        on_node: OnNode = None,
    ) -> Dict[str, Any]:
        """DEPRECATED — legacy /v1/webhook shim. Prefer /v1/sessions/*.

        Adopts the caller's `session_id` when present, else mints one (so a
        webhook caller no longer gets the old stable `{user_id}.{bot_name}` id).
        """
        user_id = (request.sender or "").strip()
        session_id = (request.session_id or "").strip() or mint_session_id()
        return self._run_turn(
            user_id=user_id,
            session_id=session_id,
            raw=request.raw_message(),
            user_message=request.user_text() or request.raw_message(),
            metadata=request.metadata,
            on_status=on_status,
            on_token=on_token,
            on_node=on_node,
        )


_service: Optional[CoachingService] = None


def get_service() -> CoachingService:
    global _service
    if _service is None:
        _service = CoachingService()
    return _service
