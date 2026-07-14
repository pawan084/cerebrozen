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
        coll.insert_one({**record, "delivered": delivered}) if hasattr(coll, "insert_one") \
            else coll.update_one(
                {"_id": f"{record['session_id']}:{record['at']}"},
                {"$set": {**record, "delivered": delivered}},
                upsert=True,
            )
    except Exception as exc:  # noqa: BLE001
        logger.error("safety.escalation_not_recorded", extra={"error": str(exc)})


def health() -> dict:
    """For /v1/health. A safety feature that is silently off must be visible."""
    return {
        "crisis_escalation_armed": armed(),
        "crisis_classifier_enabled": _classifier_on(),
    }


def _classifier_on() -> bool:
    from app.graph import crisis_classifier

    return crisis_classifier.enabled()
