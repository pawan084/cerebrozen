"""Per-user agentic context store — what the background builders WRITE and
`profile_read` (user_profile_retrieval) READS back next session.

One document per `user_id` in `cerebrozen.users_agentic_conversation_context`
(the same collection `read_user_context` reads). Fields:

    {
      user_id,
      actions:  [ {verb, action_body, full_text, expected_outcome, roi_metrics,
                   confidence, session_id, chat_title, ts}, ... ],   # accumulated, deduped
      insights: [ {insight_title, insight_body, trigger_quote, confidence,
                   session_id, ts}, ... ],
      user_context_model: { ...10-dimension model... },  # from user_context_builder
      sessions_completed: int,
      updated_at,
    }

All writes are best-effort and field-level ($set / $push) so concurrent builders
for the same user don't clobber each other. Never raises — a Mongo hiccup must not
surface anywhere (builders are silent + off the request path).
"""

from __future__ import annotations

import hashlib
import logging
from datetime import datetime, timezone
from typing import Any, Dict, List, Tuple

from app import config
from app.request_context import request_id as _ctx_request_id
from app.stores import mongo as _mongo_seam  # late-bound: get_client is THE
# patchable Mongo/Postgres seam; binding the function at import time would
# freeze whatever stood there when THIS module first loaded (see conftest).
from app.tenancy import current_org, scoped
from app.stores.conversation import get_session_title

logger = logging.getLogger("cerebrozen.agentic")

# Lifecycle status for a stored action.
# "active"  = newly generated, visible to the user (default for new actions).
# "saved"   = user explicitly confirmed the action card.
# "skipped" = user skipped the card in the current session; still visible to the
#             final-action carousel so the user can save one before closing.
# "deleted" = user dismissed/removed the card.
# Deleted actions stay in the doc (audit) but are filtered out of every read
# that feeds the UI or a prompt. Insights are always written as "active".
ACTION_STATUS_VALUES = {"active", "saved", "skipped", "deleted"}


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def stable_id(text: Any) -> str:
    """Deterministic 12-char id from an action full_text / insight title.

    Same normalisation as the read path (``_norm``: trim, lowercase, collapse
    whitespace) so the id stamped at write time matches the one the
    actions-insights endpoint and the turn payload expose — the UI keys on it and
    the save/delete API resolves an action by it."""
    return hashlib.sha1(_norm(text).encode("utf-8")).hexdigest()[:12]


def _collection():
    client = _mongo_seam.get_client()
    if client is None:
        return None
    return client[config.MONGO_BACKEND_DB][config.MONGO_AGENTIC_COLLECTION]


def load(user_id: str) -> Dict[str, Any]:
    """The user's agentic doc, or {} when absent/unavailable."""
    coll = _collection()
    if coll is None or not user_id:
        return {}
    try:
        doc = coll.find_one(scoped({"user_id": user_id})) or {}
        doc.pop("_id", None)
        return doc
    except Exception as exc:  # noqa: BLE001
        logger.warning("agentic.load_failed", extra={"error": str(exc)})
        return {}


def _norm(s: Any) -> str:
    return " ".join(str(s or "").lower().split())


def append_actions_insights(
    user_id: str,
    actions: List[Dict[str, Any]],
    insights: List[Dict[str, Any]],
    session_id: str = "",
    agent_name: str = "",
) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    """Merge new actions/insights into the user's doc, deduped against what's
    already stored (actions by full_text, insights by insight_title). Returns the
    lists of items actually added (each carrying its stamps), so the caller can
    surface freshly-generated actions in the turn payload.

    Each new item is stamped with the originating `session_id`, `agent_name`,
    and `request_id` (the correlation id of the turn that generated them). Actions
    get `status="active"` by default — visible immediately; the UI saves or deletes
    each card. Insights get `status="active"` unconditionally. Stable
    `action_id`/`insight_id` are stamped so the UI and save/delete API can key on
    them."""
    coll = _collection()
    if coll is None or not user_id or not (actions or insights):
        return [], []
    try:
        existing = coll.find_one(scoped({"user_id": user_id}), {"actions": 1, "insights": 1}) or {}
        # Dedup against user-confirmed ("saved") actions from ANY session, and against
        # ALL of THIS session's actions regardless of status. The session-scoped check
        # is load-bearing: later beats in the same session (post-simulation,
        # post-learning-aid, CH phase beats) re-extract from the same history and can
        # regenerate an earlier action word-for-word; since action_id is a content
        # hash, re-adding it would store the same id twice and re-ship a duplicate
        # card (QA 2026-07-11, QA-user-4 session <redacted>: id 071431a92b05 stored as
        # both deleted and active). Cross-session, non-saved actions still resurface.
        seen_a = {_norm(a.get("full_text")) for a in existing.get("actions", [])
                  if a.get("status") == "saved"
                  or (session_id and a.get("session_id") == session_id)}
        seen_i = {_norm(i.get("insight_title")) for i in existing.get("insights", [])}

        now = _now()
        # `session_date` (calendar day, YYYY-MM-DD) anchors the repeat-user check-in
        # 7-day window (see app/checkin_scheduler). Actions are committed DURING a
        # coaching session, so the write date IS the session date. Stored alongside
        # the full `ts`, which the scheduler falls back to for pre-existing actions.
        # Resolve the chat title for this session so the Actions Screen can
        # group/display actions by session name.  Looked up NOW (after
        # conversation.record_turn has already stored the title), so the
        # `user_conversations` doc is always present before we reach here.
        chat_title = get_session_title(session_id)
        rid = _ctx_request_id.get("")
        base_stamp = {
            "session_id": session_id,
            "agent_name": agent_name,
            "bot_name": agent_name,
            "ts": now,
            "session_date": now[:10],
            "chat_title": chat_title,
            "request_id": rid,
        }
        # Actions default to "active" — visible immediately; confirmed via save/delete.
        # Insights are always active; no confirmation step.
        action_stamp = {**base_stamp, "status": "active"}
        insight_stamp = {**base_stamp, "status": "active"}
        # The dedup key is added to `seen_*` as we go, so a duplicate WITHIN this single
        # payload is caught too — not just one that collides with what's already stored.
        # Without this, one extraction that emits the same action twice (the generator is
        # non-deterministic and re-reads the same history) writes the SAME action_id into
        # the doc twice: the UI ships two identical cards under one key, and the batch
        # save/delete endpoint (set_action_statuses, which maps action_id -> action) can
        # only reach the last of them — leaving the other stuck "active", so a deleted
        # action reappears next session as a committed one. Same failure the
        # session-scoped check above exists to prevent, one call earlier.
        new_actions = []
        for a in actions:
            key = _norm(a.get("full_text"))
            if not a.get("full_text") or key in seen_a:
                continue
            seen_a.add(key)
            new_actions.append(
                {**a, **action_stamp, "action_id": stable_id(a.get("full_text"))}
            )
        new_insights = []
        for i in insights:
            key = _norm(i.get("insight_title"))
            if not i.get("insight_title") or key in seen_i:
                continue
            seen_i.add(key)
            new_insights.append(
                {**i, **insight_stamp, "insight_id": stable_id(i.get("insight_title"))}
            )
        if not (new_actions or new_insights):
            return [], []

        push: Dict[str, Any] = {}
        if new_actions:
            push["actions"] = {"$each": new_actions}
        if new_insights:
            push["insights"] = {"$each": new_insights}

        coll.update_one(
            scoped({"user_id": user_id}),
            {"$set": {"user_id": user_id, "org_id": current_org(), "updated_at": now}, "$push": push},
            upsert=True,
        )
        logger.info(
            "agentic.actions_insights_saved",
            extra={"user_id": user_id, "added_actions": len(new_actions),
                   "added_insights": len(new_insights)},
        )
        return new_actions, new_insights
    except Exception as exc:  # noqa: BLE001
        logger.warning("agentic.append_failed", extra={"error": str(exc)})
        return [], []


def set_action_status(
    user_id: str,
    action_id: str,
    status: str,
    roi_metrics: object = None,
    full_text: object = None,
    action_body: object = None,
    expected_outcome: object = None,
) -> bool:
    """Flag one stored action as "saved"/"skipped"/"deleted"/"active" and,
    when supplied, apply the card's inline edits (``roi_metrics`` Development-Area
    re-tag as a list, plus ``full_text`` / ``action_body`` / ``expected_outcome``
    text edits).

    Resolves the action by its stored `action_id`, falling back to the stable id
    recomputed from `full_text` so actions written before ids were stamped still
    match. The action KEEPS its original `action_id` even when `full_text` changes,
    so the UI's card key and any later save/delete still resolve it. A "deleted"
    action is kept in the doc but filtered out of every read that feeds the UI or a
    prompt. roi_metrics is canonicalised to the catalogue casing. Blank text edits
    are ignored (a save with no edits just sets status). Returns True when an action
    was found and updated."""
    coll = _collection()
    if coll is None or not user_id or not action_id or status not in ACTION_STATUS_VALUES:
        return False
    from app.roi_metrics import canonical_roi_metrics

    roi = canonical_roi_metrics(roi_metrics)
    # Only non-empty text edits overwrite the stored value.
    edits = {
        k: str(v).strip()
        for k, v in (("full_text", full_text), ("action_body", action_body),
                     ("expected_outcome", expected_outcome))
        if v is not None and str(v).strip()
    }
    try:
        doc = coll.find_one(scoped({"user_id": user_id}), {"actions": 1})
        if not doc:
            return False
        actions = doc.get("actions", []) or []
        changed = False
        for a in actions:
            aid = a.get("action_id") or stable_id(a.get("full_text"))
            if aid == action_id:
                a["status"] = status
                a["action_id"] = aid  # pin it so an edited full_text can't shift it
                if roi is not None:
                    a["roi_metrics"] = roi
                    a.pop("roi_metric", None)  # remove legacy single-value field
                a.update(edits)
                changed = True
        if not changed:
            return False
        coll.update_one(
            scoped({"user_id": user_id}),
            {"$set": {"actions": actions, "updated_at": _now()}},
        )
        logger.info(
            "agentic.action_status_set",
            extra={"user_id": user_id, "action_id": action_id, "status": status,
                   "roi_metrics": roi, "edited_fields": sorted(edits)},
        )
        return True
    except Exception as exc:  # noqa: BLE001
        logger.warning("agentic.action_status_failed", extra={"error": str(exc)})
        return False


def set_action_statuses(
    user_id: str,
    updates: List[Dict[str, Any]],
) -> List[Dict[str, Any]]:
    """Batch form of ``set_action_status`` — apply several status/edit updates in one
    read + one write instead of one Mongo round trip per action.

    ``updates`` is a list of ``{action_id, status, roi_metrics?, full_text?,
    action_body?, expected_outcome?}`` dicts (same per-action shape/semantics as
    ``set_action_status``'s params). Returns one result dict per input update, in
    order: ``{"action_id": ..., "ok": bool, "roi_metrics": [...] | None}`` — ``ok``
    is False when the doc/collection is unavailable or that action_id wasn't found;
    other updates in the same call still apply."""
    from app.roi_metrics import canonical_roi_metrics

    results = [{"action_id": u.get("action_id"), "ok": False, "roi_metrics": None} for u in updates]
    coll = _collection()
    if coll is None or not user_id or not updates:
        return results
    try:
        doc = coll.find_one(scoped({"user_id": user_id}), {"actions": 1})
        if not doc:
            return results
        actions = doc.get("actions", []) or []
        by_id: Dict[str, Dict[str, Any]] = {}
        for a in actions:
            aid = a.get("action_id") or stable_id(a.get("full_text"))
            a["action_id"] = aid
            by_id[aid] = a

        changed = False
        for result, update in zip(results, updates):
            action_id = update.get("action_id")
            status = update.get("status")
            a = by_id.get(action_id)
            if not action_id or status not in ACTION_STATUS_VALUES or a is None:
                continue
            roi = canonical_roi_metrics(update.get("roi_metrics"))
            edits = {
                k: str(v).strip()
                for k, v in (
                    ("full_text", update.get("full_text")),
                    ("action_body", update.get("action_body")),
                    ("expected_outcome", update.get("expected_outcome")),
                )
                if v is not None and str(v).strip()
            }
            a["status"] = status
            if roi is not None:
                a["roi_metrics"] = roi
                a.pop("roi_metric", None)  # remove legacy single-value field
            a.update(edits)
            result["ok"] = True
            result["roi_metrics"] = roi
            changed = True

        if not changed:
            return results
        coll.update_one(
            scoped({"user_id": user_id}),
            {"$set": {"actions": actions, "updated_at": _now()}},
        )
        logger.info(
            "agentic.action_statuses_set",
            extra={"user_id": user_id, "count": len(updates),
                   "ok_count": sum(1 for r in results if r["ok"])},
        )
        return results
    except Exception as exc:  # noqa: BLE001
        logger.warning("agentic.action_statuses_failed", extra={"error": str(exc)})
        return [{"action_id": u.get("action_id"), "ok": False, "roi_metrics": None} for u in updates]


def get_action(user_id: str, action_id: str) -> object:
    """Return one stored action dict by its ``action_id`` (falling back to the stable
    id recomputed from full_text, mirroring set_action_status' resolution), or None.
    Used by the standalone action_checkin_agent to resolve the tapped card's
    ``full_text`` / ``expected_outcome`` into its input parameters. Deleted actions are
    still returned (the check-in is explicitly about one action the user chose)."""
    coll = _collection()
    if coll is None or not user_id or not action_id:
        return None
    try:
        doc = coll.find_one(scoped({"user_id": user_id}), {"actions": 1})
        for a in (doc or {}).get("actions", []) or []:
            if not isinstance(a, dict):
                continue
            aid = a.get("action_id") or stable_id(a.get("full_text"))
            if aid == action_id:
                return a
        return None
    except Exception as exc:  # noqa: BLE001
        logger.warning("agentic.get_action_failed", extra={"error": str(exc)})
        return None


def mark_checkin_complete(user_id: str, session_ids: List[str]) -> bool:
    """Permanently record that the repeat-user check-in has closed the loop on the
    given prior-session batches (BRD R3 + idempotency NFR). Uses ``$addToSet`` so
    re-running the check-in for the same session (e.g. a harness retry) is a no-op
    and the same actions are never surfaced for check-in again — the scheduler
    reads ``checkin_complete_sessions`` and excludes these forever. Best-effort;
    never raises."""
    coll = _collection()
    ids = [s for s in (session_ids or []) if s]
    if coll is None or not user_id or not ids:
        return False
    try:
        coll.update_one(
            scoped({"user_id": user_id}),
            {"$set": {"user_id": user_id, "org_id": current_org(), "updated_at": _now()},
             "$addToSet": {"checkin_complete_sessions": {"$each": ids}}},
            upsert=True,
        )
        logger.info("agentic.checkin_complete", extra={"user_id": user_id, "sessions": ids})
        return True
    except Exception as exc:  # noqa: BLE001
        logger.warning("agentic.checkin_complete_failed", extra={"error": str(exc)})
        return False


def save_ic_profile(user_id: str, ic_profile: str) -> bool:
    """Persist pattern_agent's cumulative Pattern Intelligence Model (the IC
    profile) as serialized JSON, from a `background_write` invocation. Read back
    next session via read_user_context, which exposes it as the prompt's
    `{ic_profile}` (and legacy `{prev_pattern_table}`/`{pattern}`)."""
    coll = _collection()
    if coll is None or not user_id or not (ic_profile and ic_profile.strip()):
        return False
    try:
        coll.update_one(
            scoped({"user_id": user_id}),
            {"$set": {"user_id": user_id, "ic_profile": ic_profile, "updated_at": _now()}},
            upsert=True,
        )
        logger.info("agentic.ic_profile_saved", extra=scoped({"user_id": user_id}))
        return True
    except Exception as exc:  # noqa: BLE001
        logger.warning("agentic.ic_profile_save_failed", extra={"error": str(exc)})
        return False


def save_pattern_mirror(user_id: str, mirror: str) -> bool:
    """Persist the latest `in_session` mirror block (the one surfaced to the user),
    for record/continuity. The cumulative table is written by save_ic_profile."""
    coll = _collection()
    if coll is None or not user_id or not (mirror and mirror.strip()):
        return False
    try:
        coll.update_one(
            scoped({"user_id": user_id}),
            {"$set": {"user_id": user_id, "pattern_mirror": mirror, "updated_at": _now()}},
            upsert=True,
        )
        logger.info("agentic.pattern_mirror_saved", extra=scoped({"user_id": user_id}))
        return True
    except Exception as exc:  # noqa: BLE001
        logger.warning("agentic.pattern_mirror_save_failed", extra={"error": str(exc)})
        return False


def save_intake_vars(user_id: str, variables: Dict[str, Any]) -> bool:
    """Persist the coaching_intake_agent's flat `variables_set` (Table 1 intake
    vars: userRoleContext, coachingHistory, coachingNeeds, coaching_style_preference,
    coachability_score, coachabilityDetail, userMotivations, the ci_* coachability
    dimensions, ...) under `intake_vars` so they survive across sessions. Merged
    field-level ($set per key) so a later session only overwrites the keys it
    re-captured. `read_user_context` reads these back and flattens them into
    user_context next session.

    The intake agent's structured output echoes the FULL variables_set schema every
    turn — most keys null except whichever question was just answered. For
    once_in_lifetime vars (per variable_capture_registry.xlsx) that echo would wipe
    an already-captured value with null on the very next turn, so those keys are
    skipped here whenever intake_vars already holds a non-empty value for them —
    same set-once guarantee as app/stores/dynamic_vars.py."""
    coll = _collection()
    if coll is None or not user_id or not variables:
        return False
    try:
        from app.stores.variable_capture_registry import VariableCaptureRegistry

        reg = VariableCaptureRegistry.get()
        once_keys = [k for k in variables if reg.is_once_in_lifetime(k)]

        existing_once: Dict[str, Any] = {}
        if once_keys:
            projection = {f"intake_vars.{k}": 1 for k in once_keys}
            projection["_id"] = 0
            existing_doc = coll.find_one(scoped({"user_id": user_id}), projection) or {}
            existing_intake_vars = existing_doc.get("intake_vars") or {}
            existing_once = {
                k: existing_intake_vars[k]
                for k in once_keys
                if k in existing_intake_vars and existing_intake_vars[k] not in (None, "", [], {})
            }

        # $set each key under intake_vars.<key> so a partial re-capture doesn't wipe
        # previously stored fields (whole-object $set would clobber them).
        fields: Dict[str, Any] = {"user_id": user_id, "updated_at": _now()}
        written_keys: list = []
        skipped_keys: list = []
        for key, val in variables.items():
            if key in existing_once:
                skipped_keys.append(key)
                continue
            fields[f"intake_vars.{key}"] = val
            written_keys.append(key)

        if not written_keys:
            logger.info(
                "agentic.intake_vars_no_write",
                extra={"user_id": user_id, "skipped_keys": skipped_keys},
            )
            return True

        coll.update_one(scoped({"user_id": user_id}), {"$set": fields}, upsert=True)
        logger.info(
            "agentic.intake_vars_saved",
            extra={"user_id": user_id, "keys": sorted(written_keys), "skipped_keys": skipped_keys},
        )
        return True
    except Exception as exc:  # noqa: BLE001
        logger.warning("agentic.intake_vars_save_failed", extra={"error": str(exc)})
        return False


_MOOD_FIELDS = {
    "mapped_emotions", "positive_emotions", "negative_emotions",
    "positive_exploration", "negative_exploration", "mood_capture_complete",
    "responses",
}

# (thumbnail_url filename, Valence) — canonical 22 emotions from feedback_mood_capture_agent_LangGraph.md
# Filenames are Strapi-hashed asset names taken from domain.yml of ai-specialized-generative-bot
_EMOTION_META: dict = {
    "Happy":        ("Happy_3fe029a86d.svg",        "Positive"),
    "Peaceful":     ("Peaceful_88070221dc.svg",      "Positive"),
    "Optimistic":   ("Optimistic_60051113a4.svg",    "Positive"),
    "Joyful":       ("Joyful_94ab9ad93b.svg",        "Positive"),
    "Curious":      ("Curious_d365c77901.svg",       "Positive"),
    "Creative":     ("Creative_4a40f1d409.svg",      "Positive"),
    "Loving":       ("Loving_c2cc0c10c3.svg",        "Positive"),
    "Confident":    ("Confident_861158dfd9.svg",     "Positive"),
    "Inspired":     ("Inspired_2d206033e6.svg",      "Positive"),
    "Surprised":    ("Surprised_abb354b5f2.svg",     "Positive"),
    "Proud":        ("Proud_c2a50c6a3b.svg",         "Positive"),
    "Sad":          ("Sad_49ac14c53a.svg",           "Negative"),
    "Angry":        ("Angry_4595c7b27d.svg",         "Negative"),
    "Fearful":      ("Fearful_b02a682f55.svg",       "Negative"),
    "Anxious":      ("Anxious_16f094923c.svg",       "Negative"),
    "Frustrated":   ("Frustrated_0708bfcede.svg",    "Negative"),
    "Disappointed": ("Disappointed_265c66b492.svg",  "Negative"),
    "Insecure":     ("Insecure_25767f5678.svg",      "Negative"),
    "Stressed":     ("Stressed_7da6e247e2.svg",      "Negative"),
    "Bored":        ("Bored_fde590f007.svg",         "Negative"),
    "Confused":     ("Confused_f12357624b.svg",      "Negative"),
    "Tired":        ("Tired_2182331ace.svg",         "Negative"),
}


def _build_moods_list(mapped_emotions: list) -> list:
    base_url = config.STRAPI_MEDIA_URL
    result = []
    for e in mapped_emotions:
        filename, valence = _EMOTION_META.get(e, ("", "Unknown"))
        result.append({
            "name": e,
            "thumbnail_url": f"{base_url}/{filename}" if base_url and filename else "",
            "polarity": valence,
        })
    return result


def save_mood_capture(user_id: str, session_id: str, mood_capture: Dict[str, Any]) -> bool:
    """Append one mood record to the `moods` array in the agentic context doc.
    One entry per session; only _MOOD_FIELDS are persisted.

    Refuses to write anything when `CEREBROZEN_EMOTION_CAPTURE=false` (implied by
    `CEREBROZEN_REGULATED_WORKPLACE=true`). This is emotion inference about a person in an
    employment context, which the EU AI Act treats as a prohibited practice — so a tenant
    that must not do it needs the guarantee to live in CODE, not in a promise that the
    agent won't be asked. The store is the last gate before the disk, which makes it the
    right place for the guarantee: whatever the prompt does upstream, nothing is persisted.
    """
    if not config.EMOTION_CAPTURE_ENABLED:
        logger.info(
            "agentic.mood_capture_disabled",
            extra={"reason": "emotion inference is off for this tenant (regulated workplace)"},
        )
        return False
    coll = _collection()
    if coll is None or not user_id or not mood_capture:
        return False
    try:
        chat_title = get_session_title(session_id)
        now = _now()
        _moods_list = _build_moods_list(mood_capture.get("mapped_emotions", []))
        _flat_responses = mood_capture.get("responses", [])
        _nested_responses = [
            {"moods": _moods_list, "responses": _flat_responses}
        ] if (_moods_list or _flat_responses) else []
        entry = {
            "session_id": session_id,
            "chat_title": chat_title,
            "ts": now,
            "date": datetime.now(timezone.utc).strftime("%d/%m/%Y"),
            **{k: mood_capture[k] for k in _MOOD_FIELDS if k in mood_capture and k != "responses"},
            "responses": _nested_responses,
        }
        coll.update_one(
            scoped({"user_id": user_id}),
            {
                "$set": {"user_id": user_id, "updated_at": now},
                "$push": {"moods": entry},
            },
            upsert=True,
        )
        rid = _ctx_request_id.get("")
        logger.info("agentic.mood_capture_saved",
                    extra={"user_id": user_id, "session_id": session_id, "request_id": rid})
        return True
    except Exception as exc:  # noqa: BLE001
        logger.warning("agentic.mood_capture_save_failed",
                       extra={"user_id": user_id, "error": str(exc),
                              "request_id": _ctx_request_id.get("")})
        return False


def save_user_context_model(user_id: str, model: Dict[str, Any]) -> bool:
    """Persist the User Context Model from user_context_builder + bump
    sessions_completed + stamp last_session_at (the definitive session-close
    timestamp, distinct from updated_at which is written on any agentic update).
    Returns True on a successful write."""
    coll = _collection()
    if coll is None or not user_id or not model:
        return False
    try:
        now = _now()
        coll.update_one(
            scoped({"user_id": user_id}),
            {
                "$set": {
                    "user_id": user_id,
                    "user_context_model": model,
                    "updated_at": now,
                    "last_session_at": now,   # explicit session-close timestamp
                },
                "$inc": {"sessions_completed": 1},
            },
            upsert=True,
        )
        logger.info(
            "agentic.context_model_saved",
            extra={"user_id": user_id, "last_session_at": now},
        )
        return True
    except Exception as exc:  # noqa: BLE001
        logger.warning("agentic.context_save_failed", extra={"error": str(exc)})
        return False
