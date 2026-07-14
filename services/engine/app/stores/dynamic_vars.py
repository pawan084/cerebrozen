"""Persistent store for dynamic session variables captured during coaching.

Collection: cerebrozen.agentic_session_dynamic_variables
Document key: user_id (one document per user; latest values win per variable).

Variable classification (update_frequency) is driven at runtime from:
  business_requirements/variable_capture_registry.xlsx

  once_in_lifetime  – never overwritten once a non-empty value exists
  every_session     – always overwritten with the latest captured value
  only_on_shift     – same as every_session in code; agent controls emission

The registry is a lazy singleton (VariableCaptureRegistry). Changing the sheet
and calling VariableCaptureRegistry.reload() (or restarting the app) picks up
the new classification without any code change.

Variables NOT in the registry are still captured with every_session behaviour.
Variables with capture_enabled=FALSE in the sheet are silently skipped.

Provenance
  Each written variable gets a _provenance.<flat_key> sub-document that records
  which session, which agent stage, the sequential node-call number within the
  turn (turn_seq), the request_id of the originating API call, and a UTC
  timestamp. The timestamp matches the CloudWatch structured-log timestamp, so
  searching logs for session_id + stage + generated_at pinpoints the exact
  turn that produced the value.

MongoDB path convention
  Variable names that contain "." are written using MongoDB dot-path syntax,
  which creates the natural nested document:
    "coaching_style_context.selected_style" → {coaching_style_context: {selected_style: …}}
  The provenance key for such a variable flattens the dot to "__":
    _provenance.coaching_style_context__selected_style

Reading
  read_dynamic_vars() returns a flat dict of all stored fields (minus internal
  bookkeeping keys) ready to be merged into user_context at profile_read time.
  Nested objects (e.g. coaching_style_context) arrive as Python dicts; the
  PlaceholderResolver stringifies them as "key: val; key: val" for prompt use.
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any, Dict, Optional

from app import config
from app.stores import mongo as _mongo_seam  # late-bound: get_client is THE
# patchable Mongo/Postgres seam; binding the function at import time would
# freeze whatever stood there when THIS module first loaded (see conftest).
from app.tenancy import current_org, scoped
from app.stores.variable_capture_registry import VariableCaptureRegistry

logger = logging.getLogger("cerebrozen.dynamic_vars")

# Internal bookkeeping keys that must never leak into user_context on read.
_INTERNAL_KEYS = frozenset({"_id", "user_id", "_provenance", "updated_at"})


def _registry() -> VariableCaptureRegistry:
    return VariableCaptureRegistry.get()


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _collection():
    client = _mongo_seam.get_client()
    if client is None:
        return None
    return client[config.MONGO_BACKEND_DB][config.MONGO_DYNAMIC_VARS_COLLECTION]


# ---------------------------------------------------------------------------
# Write
# ---------------------------------------------------------------------------


def save_session_dynamic_vars(
    user_id: str,
    session_id: str,
    variables: Dict[str, Any],
    stage: str = "",
    turn_seq: int = 0,
    request_id: str = "",
) -> bool:
    """Persist a batch of captured variables to agentic_session_dynamic_variables.

    For ONCE_IN_LIFETIME_VARS: reads the current document first and skips any
    key that is already set to a non-empty value (set-once guarantee).

    For all other variables: always overwrites with the latest captured value.

    Variable names containing "." are written as MongoDB nested paths, which
    creates the nested document structure automatically (e.g.
    "coaching_style_context.selected_style" → {coaching_style_context:
    {selected_style: …}}).

    Each written variable gets a provenance entry under _provenance.<flat_key>
    that records session_id, stage, turn_seq, request_id, and generated_at so
    CloudWatch queries can trace the exact turn that produced the value.

    Returns True on a successful write, False on any error (never raises).
    """
    if not user_id or not variables:
        return False

    # Filter disabled vars first — if nothing survives, it's a clean no-op
    # regardless of Mongo availability (avoids a pointless connection attempt).
    reg = _registry()
    variables = {k: v for k, v in variables.items() if reg.is_capture_enabled(k)}
    if not variables:
        return True

    coll = _collection()
    if coll is None:
        return False

    try:
        now = _now()

        # --- single pre-read covering both write guards ----------------------
        # once_in_lifetime: skip if a non-empty value is already stored (ever).
        # every_session:    skip if the stored provenance session_id matches the
        #                   current one — meaning this session already wrote it.
        #                   Overwrite only when the session_id has changed (new
        #                   session), keeping the latest session's value current.
        once_in_payload = [k for k in variables if reg.is_once_in_lifetime(k)]
        every_session_in_payload = [k for k in variables if not reg.is_once_in_lifetime(k)]

        projection: Dict[str, Any] = {"_id": 0}
        for k in once_in_payload:
            projection[k] = 1
        for k in every_session_in_payload:
            projection[f"_provenance.{k.replace('.', '__')}.session_id"] = 1

        existing_once: Dict[str, Any] = {}
        existing_session_ids: Dict[str, str] = {}  # key → session_id that last wrote it

        if once_in_payload or every_session_in_payload:
            existing_doc = coll.find_one(scoped({"user_id": user_id}), projection) or {}

            existing_once = {
                k: existing_doc[k]
                for k in once_in_payload
                if k in existing_doc and existing_doc[k] not in (None, "", [], {})
            }

            prov_data = existing_doc.get("_provenance", {})
            for k in every_session_in_payload:
                stored_sid = (prov_data.get(k.replace(".", "__")) or {}).get("session_id")
                if stored_sid:
                    existing_session_ids[k] = stored_sid

        # --- build the $set payload ------------------------------------------
        set_fields: Dict[str, Any] = {
            "user_id": user_id,
            "org_id": current_org(),
            "updated_at": now,
        }
        written_keys: list[str] = []
        skipped_keys: list[str] = []

        for raw_key, val in variables.items():
            # once_in_lifetime: never overwrite once a non-empty value exists.
            if reg.is_once_in_lifetime(raw_key) and raw_key in existing_once:
                skipped_keys.append(raw_key)
                continue

            # every_session: only overwrite when the session has changed.
            if not reg.is_once_in_lifetime(raw_key) and existing_session_ids.get(raw_key) == session_id:
                skipped_keys.append(raw_key)
                continue

            # Use raw_key directly — MongoDB interprets "." as nested path.
            set_fields[raw_key] = val

            # Provenance key: flatten dots to "__" so it is a valid Mongo field.
            prov_key = f"_provenance.{raw_key.replace('.', '__')}"
            set_fields[prov_key] = {
                "original_key": raw_key,
                "session_id": session_id,
                "stage": stage,
                "turn_seq": turn_seq,
                "request_id": request_id,
                "generated_at": now,
            }
            written_keys.append(raw_key)

        if not written_keys:
            logger.info(
                "dynamic_vars.no_write",
                extra={
                    "user_id": user_id,
                    "session_id": session_id,
                    "request_id": request_id,
                    "stage": stage,
                    "skipped_keys": skipped_keys,
                    "reason": "already_set_this_session_or_once_in_lifetime",
                },
            )
            return True  # skipped all — not an error

        coll.update_one(scoped({"user_id": user_id}), {"$set": set_fields}, upsert=True)

        logger.info(
            "dynamic_vars.saved",
            extra={
                "user_id": user_id,
                "session_id": session_id,
                "stage": stage,
                "turn_seq": turn_seq,
                "request_id": request_id,
                "written_keys": written_keys,
                "skipped_keys": skipped_keys,
                "generated_at": now,
            },
        )
        return True

    except Exception as exc:  # noqa: BLE001 — best-effort; never block a turn
        logger.warning(
            "dynamic_vars.save_failed",
            extra={
                "user_id": user_id,
                "session_id": session_id,
                "request_id": request_id,
                "stage": stage,
                "error": str(exc),
            },
        )
        return False


# ---------------------------------------------------------------------------
# Read
# ---------------------------------------------------------------------------


def read_dynamic_vars(user_id: str) -> Dict[str, Any]:
    """Return the stored dynamic variables for a user, ready to merge into
    user_context.  Internal bookkeeping keys (_id, user_id, _provenance,
    updated_at) are stripped.  Nested documents (e.g. coaching_style_context,
    user_blueprint) are returned as Python dicts — the PlaceholderResolver
    renders them as "key: val; …" strings in the prompt.

    Returns {} when the collection is unavailable or no document exists.
    Never raises.
    """
    coll = _collection()
    if coll is None or not user_id:
        return {}

    from app.request_context import request_id as _req_id_ctx, ctx_session_id as _sess_id_ctx
    rid = _req_id_ctx.get("")
    sid = _sess_id_ctx.get("")

    try:
        doc: Dict[str, Any] = coll.find_one(scoped({"user_id": user_id})) or {}
        result = {k: v for k, v in doc.items() if k not in _INTERNAL_KEYS}
        logger.info(
            "dynamic_vars.read",
            extra={
                "user_id": user_id,
                "session_id": sid,
                "request_id": rid,
                "keys_loaded": sorted(result.keys()),
            },
        )
        return result

    except Exception as exc:  # noqa: BLE001
        logger.warning(
            "dynamic_vars.read_failed",
            extra={
                "user_id": user_id,
                "session_id": sid,
                "request_id": rid,
                "error": str(exc),
            },
        )
        return {}
