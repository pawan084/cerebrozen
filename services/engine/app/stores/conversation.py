"""Per-session transcript store — the queryable chat record, one doc per session.

The LangGraph checkpointer persists graph *state* for resume, but it isn't a
human-readable log. This store keeps the chat transcript, now keyed by
`session_id` (a UUID minted at session start) so a user can have many sessions:

    {
      _id: "<session_id>", session_id, user_id,
      messages: [ {role: user|bot|system, text, message_num,
                   bot_name, agent_name, timestamp}, ... ],
      ended: bool, created_at, updated_at,
    }

There is no `bot_name` at the top level — the coaching path/agent isn't known
until a coaching agent runs mid-session, so `bot_name`/`agent_name` are stamped
per *bot* message (both carry the producing agent's name). A `user_id` index
supports listing a user's sessions (for a future resume feature).

Persistence is best-effort: every public function swallows errors and logs a
warning, so a Mongo hiccup never breaks a coaching turn.
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from app import config
from app.stores import mongo as _mongo_seam  # late-bound: get_client is THE
# patchable Mongo/Postgres seam; binding the function at import time would
# freeze whatever stood there when THIS module first loaded (see conftest).
from app.tenancy import current_org, scoped

logger = logging.getLogger("cerebrozen.conversation")

END_CONVERSATION_MARKER = "/endconversation"
RESTART_MARKER = "/restart"

_index_ready = False


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _write_landed(result: Any) -> bool:
    """Did an ``update_one(..., upsert=True)`` actually write anything?

    Backend-agnostic: real pymongo reports an insert via ``upserted_id`` (with
    both counts 0), while the pg shim reports it as ``modified_count=1``. A write
    the store REFUSED reports zero on all three. Anything we cannot interrogate is
    assumed to have landed — this guard exists to catch silent data loss, never to
    invent it.
    """
    upserted = getattr(result, "upserted_id", None)
    if upserted is not None:
        return True
    matched = getattr(result, "matched_count", None)
    modified = getattr(result, "modified_count", None)
    if matched is None and modified is None:
        return True
    return bool(matched) or bool(modified)


def _collection():
    """Return the user_conversations collection, or None if Mongo is unavailable."""
    client = _mongo_seam.get_client()
    if client is None:
        return None
    coll = client[config.MONGO_RASA_DB][config.MONGO_USER_CONVERSATIONS_COLLECTION]
    global _index_ready
    if not _index_ready:
        try:
            # Drop legacy indexes from the OLD per-(user+bot) schema. Those docs
            # carry a UNIQUE `sender_id` index; new docs have no `sender_id`, so
            # it would reject every new doc as a duplicate `{sender_id: null}`.
            existing = coll.index_information()
            for name in ("sender_id_1",):
                if name in existing:
                    coll.drop_index(name)
                    logger.info("conversation.legacy_index_dropped", extra={"index": name})
            # `_id` is a Mongo-assigned ObjectId; `session_id` is the business key.
            # Unique (partial, so legacy docs without `session_id` don't collide)
            # so one session maps to exactly one doc; `user_id` index lists a
            # user's sessions (future resume feature).
            # Compound with org_id: the business key is (org, session) — two
            # tenants can never contend on a session id, and the index serves
            # the org-scoped lookups.
            coll.create_index(
                [("org_id", 1), ("session_id", 1)],
                unique=True,
                partialFilterExpression={"session_id": {"$exists": True}},
                background=True,
            )
            coll.create_index([("org_id", 1), ("user_id", 1)], background=True)
        except Exception as exc:  # noqa: BLE001 — index is an optimisation, not required
            logger.warning("conversation.index_failed", extra={"error": str(exc)})
        _index_ready = True
    return coll


def has_prior_sessions(user_id: str, current_session_id: str = "") -> bool:
    """Whether this user has any EARLIER session on record.

    A reliable repeat-user signal: the transcript store is written every turn, so
    it survives even when the close-time builders never ran (a session that didn't
    close, or a builder that failed) — unlike the agentic store, which only fills
    at session close. The current session is excluded so a genuine first-timer's
    own session never counts as prior. Best-effort: a Mongo hiccup returns False
    (degrade to "fresh").
    """
    coll = _collection()
    if coll is None or not user_id:
        return False
    try:
        query: Dict[str, Any] = scoped({"user_id": user_id})
        if current_session_id:
            query["session_id"] = {"$ne": current_session_id}
        return coll.count_documents(query, limit=1) > 0
    except Exception as exc:  # noqa: BLE001
        logger.warning("conversation.prior_sessions_failed", extra={"error": str(exc)})
        return False


def has_completed_session(user_id: str, current_session_id: str = "") -> bool:
    """Whether this user has any EARLIER *completed* (ended) session on record.

    Stricter than :func:`has_prior_sessions`: only sessions marked ``ended`` count
    (an explicit /endconversation or the graph reaching the ``close`` stage — both
    set ``ended=True`` via ``record_turn``). A session the user abandoned before it
    closed is NOT a completion, so it does NOT make the user "repeat". The current
    session is excluded so a first-timer's own session never counts. Best-effort: a
    Mongo hiccup returns False (degrade to "fresh").
    """
    coll = _collection()
    if coll is None or not user_id:
        return False
    try:
        query: Dict[str, Any] = scoped({"user_id": user_id, "ended": True})
        if current_session_id:
            query["session_id"] = {"$ne": current_session_id}
        return coll.count_documents(query, limit=1) > 0
    except Exception as exc:  # noqa: BLE001
        logger.warning("conversation.completed_sessions_failed", extra={"error": str(exc)})
        return False


def get_prior_transcripts(
    user_id: str, current_session_id: str = "", max_chars: int = 0
) -> str:
    """Concatenated VERBATIM transcript of all the user's PRIOR sessions (excluding
    the current one), oldest → newest — the cross-session memory injected into a
    returning user's context. Bounded to `max_chars` (keeps the most recent content
    and trims the oldest) so context stays sane. Returns "" when there's no prior
    history. Best-effort; never raises.
    """
    coll = _collection()
    if coll is None or not user_id:
        return ""
    try:
        query: Dict[str, Any] = scoped({"user_id": user_id})
        if current_session_id:
            query["session_id"] = {"$ne": current_session_id}
        cursor = coll.find(
            query, {"messages": 1, "created_at": 1, "session_id": 1}
        ).sort("created_at", 1)
        blocks: List[str] = []
        for n, doc in enumerate(cursor, 1):
            msgs = doc.get("messages") or []
            when = str(doc.get("created_at") or "")[:10]
            lines = [f"--- Session {n} ({when}) ---"]
            for m in msgs:
                text = (m.get("text") or "").strip()
                if not text:
                    continue
                role = m.get("role")
                who = "User" if role == "user" else ("Coach" if role == "bot" else "System")
                lines.append(f"{who}: {text}")
            if len(lines) > 1:  # at least one real message
                blocks.append("\n".join(lines))
        if not blocks:
            return ""
        transcript = "\n\n".join(blocks)
        if max_chars and len(transcript) > max_chars:
            transcript = "[older history trimmed]\n\n" + transcript[-max_chars:]
        return transcript
    except Exception as exc:  # noqa: BLE001
        logger.warning("conversation.prior_transcripts_failed", extra={"error": str(exc)})
        return ""


def record_turn(
    *,
    session_id: str,
    user_id: str,
    user_message: str,
    bot_text: Optional[str],
    agent_name: str = "",
    ended: bool = False,
    active_phase: str = "",
    phase_buttons: Optional[List[Dict[str, Any]]] = None,
    hidden: bool = False,
) -> None:
    """Append one exchange (user message + bot reply) to this session's transcript.

    - `/restart`        → a system marker only (no bot reply recorded).
    - `/endconversation`→ records the exchange and marks the session ended.
    - anything else     → records the user message and the bot reply.

    `agent_name` is the agent that produced the bot reply (e.g.
    `core_coaching_agent`); it is stamped as BOTH `agent_name` and `bot_name` on
    the bot message. `ended=True` also marks the session ended (e.g. the graph
    reached close). `hidden=True` stamps the user message (only) so the
    `/history` read path can omit it from the rendered chat — used for the
    action-ack turn (`text` carrying e.g. "saved|skipped|saved") the UI sends
    after `actions/status` calls: the bot still sees and reacts to it, it's just
    not meant to appear as a chat bubble. Never raises.
    """
    coll = _collection()
    if coll is None or not session_id:
        return

    now = _now()
    stripped = (user_message or "").strip().lower()
    is_restart = stripped == RESTART_MARKER
    is_end = stripped == END_CONVERSATION_MARKER

    try:
        from app.request_context import request_id as _req_id
        rid = _req_id.get() or ""

        doc = coll.find_one(scoped({"session_id": session_id}), {"messages": 1})
        next_num = len(doc.get("messages", [])) if doc else 0

        new_messages: List[Dict[str, Any]] = []
        if is_restart:
            next_num += 1
            new_messages.append({
                "role": "system",
                "text": "User restarted the chat.",
                "message_num": next_num,
                "timestamp": now,
            })
        else:
            next_num += 1
            _user_msg: Dict[str, Any] = {
                "role": "user",
                "text": user_message,
                "message_num": next_num,
                "timestamp": now,
                "request_id": rid,
            }
            if hidden:
                _user_msg["hidden"] = True
            new_messages.append(_user_msg)
            if bot_text is not None:
                next_num += 1
                _bot_msg: Dict[str, Any] = {
                    "role": "bot",
                    "text": bot_text,
                    # bot_name == agent_name: the agent that sent this to the UI.
                    "bot_name": agent_name or "",
                    "agent_name": agent_name or "",
                    "buttons": [],  # quick-reply buttons (non-CH turns)
                    "message_num": next_num,
                    "timestamp": now,
                    "request_id": rid,
                }
                # CH phase tracking: save the active phase and any transition buttons
                # so session history can reconstruct which phase each message was in.
                if active_phase:
                    _bot_msg["active_phase"] = active_phase
                if phase_buttons:
                    _bot_msg["phase_buttons"] = phase_buttons
                new_messages.append(_bot_msg)

        set_fields: Dict[str, Any] = {
            "session_id": session_id,
            "user_id": user_id,
            "org_id": current_org(),
            "updated_at": now,
        }
        # A field can't be in both $set and $setOnInsert (Mongo error 40): only
        # pin ended=False on insert; when ending we set it in $set instead.
        set_on_insert: Dict[str, Any] = {"created_at": now}
        # `title` is no longer derived from the first user message here — the UI
        # calls POST /v1/sessions/{session_id}/title (app/llm/title_generator.py)
        # to generate and persist it explicitly. See set_session_title() below.
        if is_end or ended:
            set_fields["ended"] = True
        else:
            set_on_insert["ended"] = False

        # Upsert by the `session_id` field (NOT _id) so Mongo assigns an ObjectId
        # `_id`; `session_id` stays a normal, uniquely-indexed business key.
        result = coll.update_one(
            scoped({"session_id": session_id}),
            {
                "$set": set_fields,
                "$push": {"messages": {"$each": new_messages}},
                "$setOnInsert": set_on_insert,
            },
            upsert=True,
        )
        # An upsert that neither matched nor inserted means the write was DROPPED —
        # the row exists under a key we resolved but does not satisfy the whole
        # filter (a different org), so the store's cross-tenant guard refused it.
        # Silence here is how a transcript goes unpersisted with no error anywhere:
        # say so, loudly, rather than log "recorded" over a write that never landed.
        if not _write_landed(result):
            logger.error(
                "conversation.record_dropped",
                extra={"session_id": session_id, "user_id": user_id,
                       "org_id": current_org(),
                       "reason": "upsert matched no document and inserted none — "
                                 "an existing row for this session belongs to another tenant"},
            )
            return
        logger.info(
            "conversation.recorded",
            extra={"session_id": session_id, "user_id": user_id,
                   "added": len(new_messages), "ended": is_end or ended},
        )
    except Exception as exc:  # noqa: BLE001 — never let logging break a turn
        logger.warning(
            "conversation.record_failed",
            extra={"session_id": session_id, "user_id": user_id, "error": str(exc)},
        )


def get_session(session_id: str) -> Optional[Dict[str, Any]]:
    """Return the full transcript document for a session_id (for history/export)."""
    coll = _collection()
    if coll is None or not session_id:
        return None
    try:
        return coll.find_one(scoped({"session_id": session_id}))
    except Exception as exc:  # noqa: BLE001
        logger.warning("conversation.read_failed", extra={"error": str(exc)})
        return None


def get_latest_bot_message(session_id: str) -> Optional[Dict[str, Any]]:
    """Return one session's metadata + its last `role == "bot"` message.

    Unlike `get_session`, avoids fetching the full transcript: only a bounded
    trailing window (last 10 messages) is pulled via `$slice`, then filtered for
    the last bot message in Python. 10 is generous headroom over the largest
    real trailing run (a `/restart` system marker followed by a user+bot pair),
    so this is O(1) in conversation length rather than O(total messages).
    Returns ``None`` when the session doesn't exist or Mongo is unavailable.
    """
    coll = _collection()
    if coll is None or not session_id:
        return None
    try:
        pipeline = [
            {"$match": scoped({"session_id": session_id})},
            {"$project": {
                "_id": 0,
                "session_id": 1,
                "user_id": 1,
                "ended": 1,
                "total": {"$size": {"$ifNull": ["$messages", []]}},
                "tail": {"$slice": ["$messages", -10]},
            }},
        ]
        docs = list(coll.aggregate(pipeline))
        if not docs:
            return None
        doc = docs[0]
        tail = doc.pop("tail", []) or []
        bot_messages = [m for m in tail if m.get("role") == "bot"]
        doc["last_bot_message"] = bot_messages[-1] if bot_messages else None
        return doc
    except Exception as exc:  # noqa: BLE001
        logger.warning("conversation.read_latest_failed", extra={"error": str(exc)})
        return None


def list_sessions(
    user_id: str, limit: int = 50, offset: int = 0
) -> List[Dict[str, Any]]:
    """Return a user's sessions, most-recently-updated first (for the Recents list).

    Lean projection — no full `messages` array: just `title` (LLM-generated via
    set_session_title, see get_session_title), `ended`, timestamps, and a small
    head slice of messages as a title fallback for docs where title generation was
    never called. Returns [] when Mongo is unavailable.
    """
    coll = _collection()
    if coll is None or not user_id:
        return []
    try:
        cursor = (
            coll.find(
                scoped({"user_id": user_id}),
                {
                    "session_id": 1,
                    "title": 1,
                    "ended": 1,
                    "created_at": 1,
                    "updated_at": 1,
                    "messages": {"$slice": 4},  # fallback title only
                },
            )
            # _id tiebreak: two sessions updated in the same clock tick would
            # otherwise sort non-deterministically (ObjectId embeds insertion order).
            .sort([("updated_at", -1), ("_id", -1)])
            .skip(max(0, offset))
            .limit(max(1, limit))
        )
        return list(cursor)
    except Exception as exc:  # noqa: BLE001
        logger.warning("conversation.list_failed", extra={"error": str(exc)})
        return []


def pop_last_exchange(session_id: str) -> bool:
    """Remove the last user message and everything after it (its bot reply) from a
    session's transcript — used before recording an EDITED last exchange so the
    stored history matches the regenerated graph state. Returns True if it changed
    anything. Never raises.
    """
    coll = _collection()
    if coll is None or not session_id:
        return False
    try:
        doc = coll.find_one(scoped({"session_id": session_id}), {"messages": 1})
        msgs = (doc or {}).get("messages", [])
        last_user_idx = next(
            (i for i in range(len(msgs) - 1, -1, -1) if msgs[i].get("role") == "user"),
            None,
        )
        if last_user_idx is None:
            return False
        coll.update_one(
            scoped({"session_id": session_id}),
            {"$set": {"messages": msgs[:last_user_idx], "updated_at": _now()}},
        )
        logger.info(
            "conversation.popped_last_exchange",
            extra={"session_id": session_id, "removed": len(msgs) - last_user_idx},
        )
        return True
    except Exception as exc:  # noqa: BLE001
        logger.warning("conversation.pop_failed", extra={"error": str(exc)})
        return False


def set_session_title(session_id: str, user_id: str, title: str) -> bool:
    """Persist an LLM-generated `title` for a session.

    Called by ``POST /v1/sessions/{session_id}/title`` (app/llm/title_generator.py)
    once the title has been generated. Upserts by `session_id` — the title call may
    land before the first turn is recorded, so this must not depend on the doc
    already existing. Returns True on success. Never raises.
    """
    coll = _collection()
    title = (title or "").strip()
    if coll is None or not session_id or not title:
        return False
    try:
        now = _now()
        coll.update_one(
            scoped({"session_id": session_id}),
            {
                "$set": {"session_id": session_id, "user_id": user_id, "org_id": current_org(), "title": title, "updated_at": now},
                "$setOnInsert": {"created_at": now, "ended": False},
            },
            upsert=True,
        )
        logger.info(
            "conversation.title_set",
            extra={"session_id": session_id, "user_id": user_id},
        )
        return True
    except Exception as exc:  # noqa: BLE001
        logger.warning(
            "conversation.set_title_failed",
            extra={"session_id": session_id, "user_id": user_id, "error": str(exc)},
        )
        return False


def get_session_title(session_id: str) -> str:
    """Return the stored `title` for a session (LLM-generated via set_session_title),
    or ``''`` when absent or unavailable.

    Used by the agentic store to stamp `chat_title` on every new action so the
    Actions Screen can group/display actions by session name without a separate
    lookup. Best-effort; never raises.
    """
    coll = _collection()
    if coll is None or not session_id:
        return ""
    try:
        doc = coll.find_one(
            scoped({"session_id": session_id}),
            {"title": 1, "messages": {"$slice": 4}},
        )
        if not doc:
            return ""
        if doc.get("title"):
            return doc["title"]
        # Fallback: scan the small head-slice for the first user message text.
        # Handles legacy docs written before the `title` field was introduced.
        for msg in doc.get("messages", []):
            if msg.get("role") == "user" and (msg.get("text") or "").strip():
                return msg["text"].strip()
        return ""
    except Exception as exc:  # noqa: BLE001
        logger.warning("conversation.get_title_failed", extra={"error": str(exc)})
        return ""


def record_phase_selection(session_id: str, user_selection: str) -> bool:
    """Record the user's phase button selection on the most recent bot message.

    Called by POST /v1/sessions/{session_id}/phase-selection. Stores
    `phase_user_selection` on the last bot message so the conversation history
    captures which button was pressed (critical for Save & Exit, where no next
    turn arrives to carry the signal). Returns True on success, False otherwise.
    Never raises.
    """
    coll = _collection()
    if coll is None or not session_id or not user_selection:
        return False
    try:
        doc = coll.find_one(scoped({"session_id": session_id}), {"messages": 1})
        if not doc:
            return False
        msgs = doc.get("messages", [])
        last_bot_idx = next(
            (i for i in range(len(msgs) - 1, -1, -1) if msgs[i].get("role") == "bot"),
            None,
        )
        if last_bot_idx is None:
            return False
        coll.update_one(
            scoped({"session_id": session_id}),
            {
                "$set": {
                    f"messages.{last_bot_idx}.phase_user_selection": user_selection,
                    "updated_at": _now(),
                }
            },
        )
        logger.info(
            "conversation.phase_selection_recorded",
            extra={"session_id": session_id, "user_selection": user_selection},
        )
        return True
    except Exception as exc:  # noqa: BLE001
        logger.warning(
            "conversation.phase_selection_failed",
            extra={"session_id": session_id, "user_selection": user_selection, "error": str(exc)},
        )
        return False


def delete_session(session_id: str, user_id: str) -> bool:
    """Delete a session's conversation history. Scoped by BOTH `session_id` and
    `user_id` so a caller can never delete another user's session. Returns True
    only when a doc actually matched and was deleted. Never raises.
    """
    coll = _collection()
    if coll is None or not session_id or not user_id:
        return False
    try:
        result = coll.delete_one(scoped({"session_id": session_id, "user_id": user_id}))
        deleted = result.deleted_count > 0
        if deleted:
            logger.info(
                "conversation.deleted",
                extra={"session_id": session_id, "user_id": user_id},
            )
        return deleted
    except Exception as exc:  # noqa: BLE001
        logger.warning(
            "conversation.delete_failed",
            extra={"session_id": session_id, "user_id": user_id, "error": str(exc)},
        )
        return False


def latest_resumable_session(user_id: str) -> Optional[Dict[str, Any]]:
    """The user's most-recently-updated NON-ended session, or None.

    Powers the home-screen "Resume" pill: a session is resumable while it hasn't
    ended (`ended` false or absent). Returns None when the user has no session or
    none is resumable.
    """
    coll = _collection()
    if coll is None or not user_id:
        return None
    try:
        return coll.find_one(
            scoped({"user_id": user_id, "ended": {"$ne": True}}),
            {"session_id": 1, "title": 1, "created_at": 1, "updated_at": 1,
             "messages": {"$slice": 4}},
            sort=[("updated_at", -1)],
        )
    except Exception as exc:  # noqa: BLE001
        logger.warning("conversation.latest_resumable_failed", extra={"error": str(exc)})
        return None
