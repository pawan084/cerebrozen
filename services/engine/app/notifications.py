"""Check-in nudge delivery — closing the loop the scheduler opens.

``checkin_scheduler`` decides WHO is due for a 7-day check-in; until now nothing
delivered a reminder (the scheduler computed eligibility, and it went nowhere).
This module scans due check-ins across users and emits a **content-free nudge
signal** per user to a configured delivery endpoint. It formats that signal for
the target chat surface — **Slack** (Block Kit) or **Microsoft Teams**
(MessageCard) when the endpoint is one of their incoming webhooks, or the raw
signal record for a deployment's own receiver (``generic``). The visible message
is always a count and a link, never a commitment body.

NOTE: this is one-way delivery into a channel — the reminder half. A two-way
conversational coach *inside* Slack/Teams (an OAuth bot that holds a session in a
DM) is a larger build that needs an app registration and a deliberate decision
about content living in a chat client's history; it is intentionally NOT this.

It deliberately mirrors ``app/safety/escalation.py``:

- **Signal, never content.** The payload says *that* N commitments from M sessions
  are due to check in on — never a commitment body, never a transcript. "Counts,
  never content" holds at the delivery layer, not just the projection.
- **Off unless configured.** No ``CEREBROZEN_NUDGE_DELIVERY_URL`` → a logged no-op,
  never an exception. A reminder that cannot be sent must not break a dispatch run.
- **Fire-and-forget.** A delivery failure is recorded (delivered=False) and swallowed.

Dispatch is trigger-based: an external cron (or an operator) calls
``POST /v1/nudges/dispatch``. That is a system-wide sweep — it scans every tenant's
due check-ins and stamps each nudge with the user's own ``org_id`` — so it is not
scoped to one org the way a per-request read is.
"""

from __future__ import annotations

import logging
import os
from datetime import date, datetime, timezone
from typing import List, Optional

logger = logging.getLogger("cerebrozen.notifications")


def endpoint() -> str:
    """The nudge delivery endpoint. Empty = nudge delivery is OFF."""
    return os.environ.get("CEREBROZEN_NUDGE_DELIVERY_URL", "").strip()


def armed() -> bool:
    return bool(endpoint())


def _timeout_s() -> float:
    try:
        return float(os.environ.get("CEREBROZEN_NUDGE_DELIVERY_TIMEOUT_S", "5"))
    except ValueError:
        return 5.0


def channel() -> str:
    """Which chat surface the delivery endpoint is: ``slack``, ``teams``, or ``generic``.

    Explicit ``CEREBROZEN_NUDGE_CHANNEL`` wins; otherwise inferred from the endpoint host so
    a Slack/Teams incoming webhook Just Works. ``generic`` posts the raw signal record —
    the original behaviour, unchanged, for a deployment's own receiver."""
    c = os.environ.get("CEREBROZEN_NUDGE_CHANNEL", "").strip().lower()
    if c in ("slack", "teams", "generic"):
        return c
    url = endpoint()
    if "hooks.slack.com" in url:
        return "slack"
    if "webhook.office.com" in url or "office.com/webhook" in url:
        return "teams"
    return "generic"


def _format_payload(record: dict) -> dict:
    """Shape the wire payload for the target chat surface.

    Rule 5 holds at the boundary: the human-visible message is a COUNT and a link, never a
    commitment body, journal, or session id. The generic path is byte-for-byte the signal
    record (a deployment's own endpoint may want the ids); Slack/Teams get a card whose
    visible text carries nothing but the count."""
    ch = channel()
    if ch == "generic":
        return record

    from app import config

    n = record.get("due_count", 0)
    brand = getattr(config, "BRAND_NAME", None) or "your coach"
    link = os.environ.get("CEREBROZEN_APP_DEEP_LINK", "").strip()
    text = (
        f"You have {n} coaching check-in{'s' if n != 1 else ''} due. "
        f"Open {brand} to follow through."
    )

    if ch == "slack":
        blocks: list = [{"type": "section", "text": {"type": "mrkdwn", "text": f":bell: {text}"}}]
        if link:
            blocks.append({"type": "actions", "elements": [
                {"type": "button", "text": {"type": "plain_text", "text": "Open"}, "url": link}]})
        return {"text": text, "blocks": blocks}

    # Teams MessageCard (incoming-webhook connector format).
    card: dict = {
        "@type": "MessageCard",
        "@context": "http://schema.org/extensions",
        "summary": "Coaching check-in due",
        "themeColor": "EF5B5B",
        "text": text,
    }
    if link:
        card["potentialAction"] = [{
            "@type": "OpenUri", "name": "Open",
            "targets": [{"os": "default", "uri": link}],
        }]
    return card


def due_nudges(today: Optional[date] = None, due_days: Optional[int] = None) -> List[dict]:
    """Scan every user's agentic doc and return a content-free nudge per user
    with a due check-in.

    A system-wide sweep (all tenants); each record carries the user's own
    ``org_id``. Best-effort: no store returns an empty list, never raises. Only
    counts and session ids leave this function — never a commitment body.
    """
    try:
        from app import config
        from app.checkin_scheduler import eligible_checkin_actions, eligible_session_ids
        from app.stores.mongo import get_client

        if today is None:
            today = datetime.now(timezone.utc).date()
        if due_days is None:
            due_days = config.CHECKIN_DUE_DAYS

        client = get_client()
        if client is None:
            return []
        coll = client[config.MONGO_BACKEND_DB][config.MONGO_AGENTIC_COLLECTION]
        projection = {"user_id": 1, "org_id": 1, "actions": 1, "checkin_complete_sessions": 1}
        out: List[dict] = []
        for doc in coll.find({}, projection):
            uid = doc.get("user_id")
            if not uid:
                continue
            eligible = eligible_checkin_actions(
                doc.get("actions", []),
                today,
                due_days=due_days,
                checked_in_sessions=doc.get("checkin_complete_sessions", []),
            )
            if not eligible:
                continue
            out.append({
                "event": "checkin_nudge",
                "org_id": doc.get("org_id", "default"),
                "user_id": uid,
                # Counts and batch ids only — never a commitment body.
                "due_count": len(eligible),
                "session_ids": eligible_session_ids(eligible),
                "at": datetime.now(timezone.utc).isoformat(),
            })
        return out
    except Exception as exc:  # noqa: BLE001 — a dispatch sweep must never raise
        logger.error("nudge.scan_failed", extra={"error": str(exc)})
        return []


def deliver_nudge(record: dict) -> bool:
    """Send one content-free nudge signal. Never raises.

    Returns True only if the endpoint accepted it. Records the attempt either way,
    because "we reminded someone" (or tried and could not) is what an audit needs.
    """
    if not armed():
        logger.warning(
            "nudge.not_configured",
            extra={**_signal(record), "detail": (
                "a check-in nudge is due and NO delivery endpoint is configured — "
                "nobody was reminded. Set CEREBROZEN_NUDGE_DELIVERY_URL."
            )},
        )
        _persist(record, delivered=False)
        return False
    try:
        import httpx

        resp = httpx.post(
            endpoint(), json=_format_payload(record), timeout=_timeout_s(),
            headers={"content-type": "application/json"},
        )
        resp.raise_for_status()
        logger.info(
            "nudge.delivered",
            extra={**_signal(record), "channel": channel(), "status": resp.status_code},
        )
        _persist(record, delivered=True)
        return True
    except Exception as exc:  # noqa: BLE001
        logger.error("nudge.delivery_failed", extra={**_signal(record), "error": str(exc)})
        _persist(record, delivered=False)
        return False


def dispatch(today: Optional[date] = None) -> dict:
    """Scan due check-ins and deliver a nudge for each. The cron entry point.

    Returns a summary (armed / scanned-due / delivered) — never the records, so a
    dispatch log never carries who-owes-what beyond counts.
    """
    records = due_nudges(today=today)
    delivered = sum(1 for r in records if deliver_nudge(r))
    summary = {"armed": armed(), "due": len(records), "delivered": delivered}
    logger.info("nudge.dispatch", extra=summary)
    return summary


def list_nudges(limit: int = 100, all_orgs: bool = False) -> list:
    """Recent nudge deliveries, newest first (signal-only).

    The observability read behind an admin "nudges" view — same shape as the
    delivered record. Best-effort; a store problem returns an empty list.

    ``all_orgs`` reads across tenants, and the route passes it for the same reason the
    safety queue does: each nudge is stamped with the USER's own org (see ``run_nudges``,
    which sweeps every tenant), while the only role allowed to read this is
    ``internal_admin``, whose token carries ``org_id="internal"``. Scoped to the caller's
    org it matched nothing, ever — the queue was dead. Scoped by default so the reach is
    always something a caller asked for.
    """
    try:
        from app import config
        from app.stores.mongo import get_client
        from app.tenancy import scoped

        client = get_client()
        if client is None:
            return []
        coll = client[config.MONGO_BACKEND_DB]["checkin_nudges"]
        projection = {
            "_id": 0, "org_id": 1, "user_id": 1, "due_count": 1,
            "session_ids": 1, "at": 1, "delivered": 1,
        }
        rows = list(coll.find({} if all_orgs else scoped({}), projection))
    except Exception as exc:  # noqa: BLE001
        logger.error("nudge.list_failed", extra={"error": str(exc)})
        return []
    rows.sort(key=lambda r: r.get("at") or "", reverse=True)
    return rows[: max(0, limit)]


def _signal(record: dict) -> dict:
    """The loggable signal — ids and counts, never a body (there is none here)."""
    return {
        "user_id": record.get("user_id"),
        "org_id": record.get("org_id"),
        "due_count": record.get("due_count"),
    }


def _persist(record: dict, *, delivered: bool) -> None:
    """Record the nudge attempt (delivered or not). Best-effort; never raises."""
    try:
        from app import config
        from app.stores.mongo import get_client

        client = get_client()
        if client is None:
            logger.error("nudge.not_recorded", extra={"reason": "no store"})
            return
        coll = client[config.MONGO_BACKEND_DB]["checkin_nudges"]
        row = {**record, "delivered": delivered}
        coll.insert_one(row) if hasattr(coll, "insert_one") else coll.update_one(
            {"_id": f"{record.get('user_id')}:{record.get('at')}"},
            {"$set": row}, upsert=True,
        )
    except Exception as exc:  # noqa: BLE001
        logger.error("nudge.not_recorded", extra={"error": str(exc)})


def health() -> dict:
    """For /v1/health — a nudge channel that is silently off should be visible."""
    return {"nudge_delivery_armed": armed()}
