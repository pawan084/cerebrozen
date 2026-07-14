"""Conversation-history read path — the queryable transcript for ONE session.

Separate from the coaching turn path (``app/service.py``) and from the
transcript *writer* (``app/stores/conversation.py``): this module only reads and
shapes a single session's transcript for the frontend's resume / history UI.

Keyed by ``session_id`` (one transcript doc per session). The response contract
mirrors the OLD repo's ``get_history`` so the existing client renders it
unchanged — including the legacy ``converstation_status`` key spelling, which is
part of the wire contract and is preserved deliberately.
"""

from __future__ import annotations

import logging
from typing import Any, Dict, List, Optional

from app.schemas import HistoryRequest
from app.stores import agentic as _agentic
from app.stores import conversation
from app.stores.agentic import stable_id as _stable_id

logger = logging.getLogger("cerebrozen.history")


def _shape_turn_action(a: Dict[str, Any]) -> Dict[str, Any]:
    """Shape a stored action for the history payload — same shape as turn actions."""
    return {"id": a.get("action_id") or _stable_id(a.get("full_text")), **a}


def _load_turn_actions(user_id: str, session_id: str) -> Dict[str, List[Dict[str, Any]]]:
    """Load all actions for a session grouped by request_id.

    Returns every action regardless of status (active/saved/deleted) so the
    history view can show skipped actions alongside confirmed ones. An empty
    request_id means the action can't be tied to a specific turn and is skipped."""
    if not user_id or not session_id:
        return {}
    doc = _agentic.load(user_id)
    by_rid: Dict[str, List[Dict[str, Any]]] = {}
    for a in doc.get("actions", []):
        if not isinstance(a, dict) or not a.get("full_text"):
            continue
        if a.get("session_id") != session_id:
            continue
        rid = a.get("request_id") or ""
        if not rid:
            continue
        by_rid.setdefault(rid, []).append(_shape_turn_action(a))
    return by_rid


def _shape_message(
    msg: Dict[str, Any],
    total: int,
    turn_actions_by_rid: Optional[Dict[str, List[Dict[str, Any]]]] = None,
    force_disable_phase_buttons: bool = True,
) -> Dict[str, Any]:
    """Map one stored transcript message to the frontend's role-keyed envelope.

    ``force_disable_phase_buttons`` (default True, the ``/history`` behavior):
    always mark phase_buttons disabled — past turns in a full transcript replay
    are never actionable. Callers recovering the *live* current turn (e.g.
    ``/latest-response``) pass False so phase_buttons stay pressable, matching
    the text-mode `done` event, unless a selection was already recorded on this
    message (``phase_user_selection`` present) — in which case it's disabled
    either way, since the choice has genuinely already been made.
    """
    role = msg.get("role")
    if role == "user":
        return {"user": {
            "text": msg.get("text"),
            "message_num": msg.get("message_num"),
            "hidden": bool(msg.get("hidden", False)),
        }}
    if role == "system":
        return {"system": {"text": msg.get("text"), "message_num": msg.get("message_num")}}
    # Default to bot; include running total, producing agent, and any buttons.
    entry: Dict[str, Any] = {
        "text": msg.get("text"),
        "message_num": msg.get("message_num"),
        "tot_messages": total,
        "bot_name": msg.get("bot_name", ""),
        "agent_name": msg.get("agent_name", ""),
    }
    if msg.get("buttons"):
        entry["buttons"] = msg["buttons"]
    if msg.get("active_phase"):
        entry["active_phase"] = msg["active_phase"]
    if msg.get("phase_buttons"):
        already_selected = bool(msg.get("phase_user_selection"))
        mark_disabled = force_disable_phase_buttons or already_selected
        entry["phase_buttons"] = [
            ({**btn, "disabled": True} if mark_disabled else dict(btn))
            for btn in msg["phase_buttons"]
        ]
        # Also carry the selection that was recorded (if any) so the UI can
        # highlight which button was previously pressed.
        if already_selected:
            entry["phase_user_selection"] = msg["phase_user_selection"]
    # Attach all actions generated for this turn (all statuses) so history can
    # render skipped and confirmed cards alike.
    if turn_actions_by_rid is not None:
        rid = msg.get("request_id") or ""
        if rid and rid in turn_actions_by_rid:
            entry["actions"] = turn_actions_by_rid[rid]
    return {"bot": entry}


def _fallback_title(doc: Dict[str, Any]) -> str:
    """First user message's text — title fallback for legacy docs without a
    stored `title` (only the head slice of messages is projected for the list)."""
    for msg in doc.get("messages", []):
        if msg.get("role") == "user" and (msg.get("text") or "").strip():
            return msg["text"].strip()
    return ""


class HistoryService:
    """Reads + pages ONE session's transcript into the frontend history payload."""

    def list_sessions(
        self, user_id: str, limit: int = 50, offset: int = 0
    ) -> Dict[str, Any]:
        """Return a user's sessions for the Recents list, newest first.

        Each entry: ``{session_id, title, resumable, ended, created_at, updated_at}``
        — ``resumable`` is simply ``not ended`` (an ended session can't be
        continued; the UI disables its input box). ``title`` is the first user
        message. Returns ``{user_id, count, sessions}``.
        """
        docs = conversation.list_sessions(user_id, limit=limit, offset=offset)
        sessions = []
        for d in docs:
            ended = bool(d.get("ended"))
            sessions.append({
                "session_id": d.get("session_id"),
                "title": d.get("title") or _fallback_title(d),
                "resumable": not ended,
                "ended": ended,
                "created_at": d.get("created_at"),
                "updated_at": d.get("updated_at"),
            })
        logger.info(
            "history.sessions_listed",
            extra={"user_id": user_id, "count": len(sessions), "offset": offset},
        )
        return {"user_id": user_id, "count": len(sessions), "sessions": sessions}

    def latest_resumable(self, user_id: str) -> Dict[str, Any]:
        """Home-screen check: does the user have a resumable session?

        Returns ``{user_id, resumable, session_id, title, updated_at}``. When the
        user has no session (or none is resumable), ``resumable`` is ``False`` and
        ``session_id`` is ``None`` — the UI then shows no Resume pill. Otherwise
        ``resumable`` is ``True`` with the most-recent resumable session's id.
        """
        doc = conversation.latest_resumable_session(user_id)
        if not doc:
            return {
                "user_id": user_id,
                "resumable": False,
                "session_id": None,
                "title": None,
                "updated_at": None,
            }
        return {
            "user_id": user_id,
            "resumable": True,
            "session_id": doc.get("session_id"),
            "title": doc.get("title") or _fallback_title(doc),
            "updated_at": doc.get("updated_at"),
        }

    def get_latest_response(self, session_id: str, user_id: str = "") -> Dict[str, Any]:
        """Return the most recent bot message in one session's transcript.

        Powers the voice barge-in recovery flow: when the user interrupts
        CereBroZen mid-speech, the client has no reliable page number to
        re-fetch from ``/history`` — the turn's ``cerebrozen.ui_event`` data-channel
        message (which carries the message's position) may not have been
        published yet, or may have been dropped, at the exact moment TTS was cut
        off. This endpoint sidesteps that by always returning whichever bot
        message is last, regardless of position.

        Returns ``{session_id, message}`` where ``message`` carries ``text``,
        ``message_num``, ``bot_name`` plus ``buttons`` / ``phase_buttons`` /
        ``actions`` whenever the stored message has them — same fields, same
        *live* (pressable) button state as the text-mode turn response, not the
        disabled replay ``/history`` shows. ``None`` when the session doesn't
        exist, isn't owned by ``user_id``, or has no bot message yet.
        """
        doc = conversation.get_latest_bot_message(session_id)
        if not doc or (user_id and doc.get("user_id") and doc.get("user_id") != user_id):
            return {"session_id": session_id, "message": None}

        last = doc.get("last_bot_message")
        if not last:
            return {"session_id": session_id, "message": None}

        effective_uid = user_id or (doc.get("user_id") or "")
        turn_actions_by_rid = _load_turn_actions(effective_uid, session_id)
        shaped = _shape_message(
            last, doc.get("total", 0), turn_actions_by_rid, force_disable_phase_buttons=False
        )

        logger.info(
            "history.latest_response_served",
            extra={"session_id": session_id, "message_num": last.get("message_num")},
        )
        return {"session_id": session_id, "message": shaped.get("bot")}

    def get_history(self, request: HistoryRequest, user_id: str = "") -> Dict[str, Any]:
        """Return ``{converstation_status, session_id, chat_history, total_messages,
        has_more}`` for a session — the FULL stored transcript, unpaginated.

        - No transcript for the session → ``{"converstation_status": "new", "chat_history": []}``.
        - Otherwise → every message in the session, tagged ``"mid"`` (or ``"ended"``
          when the session is closed), fetched as stored in Mongo. ``request.page``
          / ``request.take`` are still accepted for backward compatibility with
          existing callers but no longer slice the result — the UI renders the
          whole transcript. ``has_more`` is always ``False``; ``total_messages`` is
          the session's full message count.

        ``user_id``, when supplied, is enforced as an ownership check — a session
        belonging to another user reads as ``new`` rather than leaking the transcript.
        """
        doc = conversation.get_session(request.session_id)
        if not doc or (user_id and doc.get("user_id") and doc.get("user_id") != user_id):
            return {"converstation_status": "new", "chat_history": []}

        messages: List[Dict[str, Any]] = doc.get("messages", [])
        total = len(messages)

        # Load all turn-level actions for this session (grouped by request_id) so
        # bot messages in the page can carry the actions generated on that turn,
        # irrespective of their save/delete status.
        effective_uid = user_id or (doc.get("user_id") or "")
        turn_actions_by_rid = _load_turn_actions(effective_uid, request.session_id)

        chat_history = [_shape_message(msg, total, turn_actions_by_rid) for msg in messages]

        logger.info(
            "history.served",
            extra={
                "session_id": request.session_id,
                "total": total,
                "returned": len(chat_history),
            },
        )
        return {
            "converstation_status": "ended" if doc.get("ended") else "mid",
            "session_id": request.session_id,
            "chat_history": chat_history,
            "total_messages": total,
            "has_more": False,
        }


_service: Optional[HistoryService] = None


def get_history_service() -> HistoryService:
    global _service
    if _service is None:
        _service = HistoryService()
    return _service
