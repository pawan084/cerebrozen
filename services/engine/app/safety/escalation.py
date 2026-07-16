"""Crisis escalation — getting a human involved.

## The gap this closes

The detection layer decides a person may be at risk. Until now, that decision reached
exactly one place: the person themselves, in the form of a helpline. **Nobody else was
told.** A coaching product used inside an employer, that hears a disclosure of self-harm and
tells no one, is relying entirely on the user acting on a phone number at the worst moment of
their life.

Products in this category routinely promise buyers that a disclosure "alerts a designated
contact". This module is what that promise costs.

## The design, and the two things it refuses to do

**It sends a SIGNAL, never the disclosure.** The payload carries a user id, a session id, a
timestamp, and the fact that the screen fired. It does not carry the message, the transcript,
or the reason the classifier flagged it. The designated contact needs to know *that* someone
needs a human — they do not need to read what that person said at midnight, and a coaching
product that forwards the confession has broken the thing that made the confession possible.
The whole product rests on people believing the conversation is theirs.

**It never blocks the turn.** Every failure — no endpoint configured, the endpoint down, a
timeout, a bad response — is logged and swallowed. A user in crisis must get their helpline
reply even if the escalation webhook is on fire. The reply is the part that helps them
*right now*; the escalation is the part that helps them tomorrow, and it does not get to
take the first one hostage.

## What this is NOT

It is not a clinical intervention, it is not a duty-of-care system, and it is not a
substitute for one. It notifies an endpoint the deployment nominates. **Who is on the other
end, what they are trained to do, and how fast they do it is the client's programme, not our
feature** — and any client who has not answered those three questions should not be told
they have crisis escalation just because this posts a webhook.

Off unless configured. A silently-unconfigured safety feature is worse than an absent one,
so `/v1/health` reports whether it is armed.
"""

from __future__ import annotations

import json
import logging
import os
from datetime import datetime, timezone
from typing import Optional

logger = logging.getLogger("cerebrozen.safety")


def endpoint() -> str:
    """The designated contact's HTTPS endpoint. Empty = escalation is OFF."""
    return os.environ.get("CEREBROZEN_CRISIS_ESCALATION_URL", "").strip()


def armed() -> bool:
    return bool(endpoint())


def _org_id() -> str:
    """The active tenant, so an escalation is attributable to one org's safety queue.

    Best-effort: the signal must never be lost to a tenancy import problem — a crisis
    turn is the worst possible moment to raise.
    """
    try:
        from app.tenancy import current_org

        return current_org()
    except Exception:  # noqa: BLE001
        return "default"


def _timeout_s() -> float:
    try:
        return float(os.environ.get("CEREBROZEN_CRISIS_ESCALATION_TIMEOUT_S", "5"))
    except ValueError:
        return 5.0


def escalate(*, user_id: str, session_id: str, detected_by: str = "") -> bool:
    """Tell the designated contact that a person needs a human. Never raises.

    Returns True only if the endpoint accepted it — the caller ignores the result, but the
    RECORD does not, because "we notified someone" is a claim that has to survive an audit.
    """
    record = {
        "event": "crisis_escalation",
        # The tenant this belongs to, so an org sees only its own safety queue. Stamped
        # here (not in the transport) because the record outlives the webhook call and
        # an org-less escalation would be invisible to per-tenant scoping.
        "org_id": _org_id(),
        "user_id": user_id,
        "session_id": session_id,
        # `detected_by` is the LAYER (lexicon / classifier), not the reason. The reason
        # would be a summary of what the person disclosed, and that is not ours to send.
        "detected_by": detected_by or "screen",
        "at": datetime.now(timezone.utc).isoformat(),
    }

    if not armed():
        # Loud, and at ERROR. An unconfigured escalation is not a neutral default — it is a
        # deployment that believes it has a safety net and does not. This line is what a
        # buyer's audit will look for, and it is what stops us claiming a feature we have
        # not been given the endpoint for.
        logger.error(
            "safety.escalation_not_configured",
            extra={**record, "detail": (
                "a crisis was detected and NO designated contact is configured — nobody was "
                "notified. Set CEREBROZEN_CRISIS_ESCALATION_URL."
            )},
        )
        _persist(record, delivered=False)
        return False

    try:
        import httpx

        resp = httpx.post(
            endpoint(),
            json=record,
            timeout=_timeout_s(),
            headers={"content-type": "application/json"},
        )
        resp.raise_for_status()
        logger.warning("safety.escalated", extra={**record, "status": resp.status_code})
        _persist(record, delivered=True)
        return True

    except Exception as exc:  # noqa: BLE001
        # The user still gets their helpline. This failure must never reach them.
        logger.error(
            "safety.escalation_failed",
            extra={**record, "error": str(exc), "detail": (
                "the designated contact was NOT reached. The user received crisis support; "
                "no human was told."
            )},
        )
        _persist(record, delivered=False)
        return False


def _persist(record: dict, *, delivered: bool) -> None:
    """Write the escalation to the store, delivered or not.

    A failed notification that leaves no trace is indistinguishable from one that never
    happened — and "we tried to reach someone and could not" is precisely the fact an
    incident review needs, and precisely the one a webhook alone will not give you.

    Best-effort by design: if the store is unavailable we log and move on rather than raise,
    because we are already inside a crisis turn and nothing here may cost the user a reply.
    """
    try:
        from app.stores.mongo import get_client
        from app import config

        client = get_client()
        if client is None:
            logger.error("safety.escalation_not_recorded", extra={"reason": "no store"})
            return
        coll = client[config.MONGO_BACKEND_DB]["crisis_escalations"]
        # An explicit, unique, DETERMINISTIC id: session + instant. One crisis, one record.
        #
        # This used to read `insert_one(...) if hasattr(coll, "insert_one") else
        # update_one({"_id": f"{session_id}:{at}"}, ...)` — a workaround for the Postgres
        # shim not having insert_one. The hasattr branch is what protected us: when the
        # shim gained the method, a naive insert_one would have fallen back to its key
        # GUESS (_id -> user_id -> session_id), keyed every escalation on the USER, and
        # silently overwritten a person's previous crisis with their next one. The id is
        # named here rather than left to any store's guess.
        coll.insert_one({
            **record,
            "_id": f"{record['session_id']}:{record['at']}",
            "delivered": delivered,
            # Open until an operator says otherwise. The queue was read-only, so it never
            # drained: a handled escalation stayed open forever and kept re-notifying.
            "acknowledged_at": None,
            "acknowledged_by": "",
        })
    except Exception as exc:  # noqa: BLE001
        logger.error("safety.escalation_not_recorded", extra={"error": str(exc)})


def _record_key(record_id: str):
    """Turn the id the console sends back into the `_id` the store actually holds.

    `list_escalations` stringifies `_id`, because records written before `_persist` named
    its own carry a Mongo ObjectId. That round-trip has to survive: an ObjectId row would
    otherwise render in the queue and be impossible to resolve — the operator would click
    Resolve, get a 404, and the row would sit there forever, which is the exact failure
    acknowledgement exists to end.

    A 24-hex string is only ever an ObjectId here (ours are `session_id:at` or a uuid4
    hex, which is 32), so this cannot shadow a real id. Postgres `_id`s are always text,
    and bson ships with pymongo, but neither is worth an exception on a safety path.
    """
    if len(record_id) != 24:
        return record_id
    try:
        from bson import ObjectId

        return ObjectId(record_id)
    except Exception:  # noqa: BLE001
        return record_id


def acknowledge(record_id: str, *, actor: str, all_orgs: bool = False) -> bool:
    """Mark an escalation handled. Returns True only if this call did it.

    A STATUS and a NAME — never a note, never a reason, never a summary. The reference's
    admin has an "Excerpt" column showing the flagged text; this queue exists precisely so
    that column can never be built (CLAUDE.md rule 5), and an "outcome" free-text field
    would be the same leak wearing a different hat.

    Idempotent, and the FIRST responder wins: two operators clicking Resolve must not fight,
    and an incident review asks who actually handled it — not who clicked last.

    ``all_orgs`` reaches across tenants — see ``list_escalations``. Same rule: only a caller
    that has already proven ``internal_admin`` may pass it. Scoped by default so the reach
    is never something a caller gets by forgetting an argument.
    """
    if not record_id:
        return False
    try:
        from app import config
        from app.stores.mongo import get_client
        from app.tenancy import scoped

        client = get_client()
        if client is None:
            logger.error("safety.escalation_ack_failed", extra={"reason": "no store"})
            return False
        coll = client[config.MONGO_BACKEND_DB]["crisis_escalations"]
        key = _record_key(record_id)
        flt = {"_id": key} if all_orgs else scoped({"_id": key})
        row = coll.find_one(flt)
        if not row:
            return False
        if row.get("acknowledged_at"):
            return True  # already handled; the first responder stands
        coll.update_one(
            flt,
            {"$set": {
                "acknowledged_at": datetime.now(timezone.utc).isoformat(),
                "acknowledged_by": actor or "unknown",
            }},
        )
    except Exception as exc:  # noqa: BLE001
        logger.error("safety.escalation_ack_failed", extra={"error": str(exc)})
        return False
    logger.warning(
        "safety.escalation_acknowledged",
        extra={"record_id": record_id, "actor": actor or "unknown"},
    )
    return True


def list_escalations(limit: int = 100, status: str = "open", all_orgs: bool = False) -> list:
    """The safety queue: escalation records, newest first.

    Signal-only by construction. The stored record never held the disclosure — only
    that the screen fired, for whom, in which session, and whether the contact was
    reached (see ``escalate``/``_persist``). This is "counts, never content" at the
    storage layer, not merely a projection over sensitive data.

    ``status``: "open" (the default — unacknowledged), "resolved", or "all". Open by
    default because a queue whose default view includes everything ever handled is a queue
    nobody reads, and this is the one an operator must actually read.

    ``all_orgs`` READS ACROSS TENANTS. It exists because this queue was otherwise dead:
    ``escalate`` stamps the record with the CUSTOMER's org, while the only role allowed to
    read it — ``internal_admin``, CereBroZen's own operators — carries ``org_id="internal"``
    and belongs to no customer org, so ``scoped()`` matched nothing, ever. A record was
    written for every crisis and the queue showed zero, while the `armed` pill read healthy.
    Measured against the composed stack; unit tests missed it because they set the same org
    they stamped.

    Only a caller that has ALREADY proven ``internal_admin`` may pass it — today that is
    ``routers/safety.py``, whose dependency does exactly that. It is an argument rather than
    a role check in here because this module is also called from a crisis turn, where the
    active identity is the person in crisis, not an operator.

    Scoped by default, so cross-tenant reach is never something a caller gets by forgetting
    an argument. The default org also sees legacy records written before org stamping.
    Best-effort: a store problem returns an empty queue, never an exception into the admin
    surface.
    """
    try:
        from app import config
        from app.stores.mongo import get_client
        from app.tenancy import scoped

        client = get_client()
        if client is None:
            return []
        coll = client[config.MONGO_BACKEND_DB]["crisis_escalations"]
        # `_id` is now RETURNED (as `id`): the console has to name a record to acknowledge
        # it. It is `session_id:at` — signal, the same two facts already in the row, so
        # exposing it discloses nothing new.
        projection = {
            "_id": 1, "org_id": 1, "user_id": 1, "session_id": 1,
            "detected_by": 1, "at": 1, "delivered": 1,
            "acknowledged_at": 1, "acknowledged_by": 1,
        }
        rows = list(coll.find({} if all_orgs else scoped({}), projection))
    except Exception as exc:  # noqa: BLE001
        logger.error("safety.escalation_list_failed", extra={"error": str(exc)})
        return []

    out = []
    for r in rows:
        # Records written before acknowledgement existed have neither field; they are open.
        acked = r.get("acknowledged_at")
        if status == "open" and acked:
            continue
        if status == "resolved" and not acked:
            continue
        out.append({
            # str(): records written before `_persist` named its own `_id` carry a Mongo
            # ObjectId, which is not JSON-serialisable — returning it raw 500s the whole
            # queue for exactly the deployments that have the most history in it. The id is
            # an opaque handle to `ack`; its type is not part of the contract.
            "id": str(r.get("_id", "")),
            "org_id": r.get("org_id", ""),
            "user_id": r.get("user_id", ""),
            "session_id": r.get("session_id", ""),
            "detected_by": r.get("detected_by", ""),
            "at": r.get("at", ""),
            "delivered": r.get("delivered", False),
            "acknowledged_at": acked,
            "acknowledged_by": r.get("acknowledged_by", ""),
        })
    out.sort(key=lambda r: r.get("at") or "", reverse=True)
    return out[: max(0, limit)]


def health() -> dict:
    """For /v1/health. A safety feature that is silently off must be visible."""
    return {
        "crisis_escalation_armed": armed(),
        "crisis_classifier_enabled": _classifier_on(),
    }


def _classifier_on() -> bool:
    from app.graph import crisis_classifier

    return crisis_classifier.enabled()
