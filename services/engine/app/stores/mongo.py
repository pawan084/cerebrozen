"""Read-only Mongo access for profile_read — a deterministic function, no LLM.

This replaces the old ~19s LLM "user_profile_retrieval_agent" with a plain DB
read (constitution: data is tools, not agents; own Mongo stays in-process). It
is defensive by design: if Mongo is unset, unreachable, or the user_id is not a
real ObjectId, it returns {} so the graph runs anyway (dev box has no Mongo).
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any, Dict, Optional, Tuple

from app import config
from app.checkin_scheduler import eligible_checkin_actions, eligible_session_ids

logger = logging.getLogger("cerebrozen.stores")

_client = None  # cached MongoClient (or False if unavailable)


def _get_client():
    global _client
    if _client is not None:
        return _client or None
    if not config.MONGO_DB_URL:
        logger.info("mongo.disabled", extra={"reason": "MONGO_DB_URL unset"})
        _client = False
        return None
    try:
        from pymongo import MongoClient

        client = MongoClient(
            config.MONGO_DB_URL, serverSelectionTimeoutMS=config.MONGO_TIMEOUT_MS
        )
        client.admin.command("ping")  # fail fast if unreachable
        _client = client
        logger.info("mongo.connected")
        return client
    except Exception as exc:  # noqa: BLE001
        logger.warning("mongo.unavailable", extra={"error": str(exc)})
        _client = False
        return None


def get_client():
    """The document-store client (None when unavailable).

    THE SINGLE SEAM between Mongo and Postgres. When POSTGRES_URL is set this returns a
    Postgres-backed client that speaks the same `client[db][collection]` shape (see
    stores/pg.py), so every store module — and the read path that feeds profile_read —
    works unchanged. One seam, not one patch per store.
    """
    from app.stores import pg

    _pg = pg.client()
    if _pg is not None:
        return _pg
    return _get_client()


def _to_object_id(user_id: str) -> Optional[Any]:
    try:
        from bson import ObjectId

        return ObjectId(user_id)
    except Exception:  # noqa: BLE001 — non-ObjectId UI ids are expected
        return None


# Whole Brain (NBI) quadrant fields, as stored in nbi_report. Output keys/labels
# use the same names lower-cased (l1, l2, r1, r2); the score is the source of truth.
_NBI_QUADRANTS = ("L1", "L2", "R1", "R2")


def _format_nbi(doc: Dict[str, Any]) -> Tuple[Optional[str], Dict[str, int]]:
    """Render an nbi_report doc into (readable text, {quadrant: score}). Reads the
    uppercase DB fields; emits lower-cased quadrant names. Scores are stored as
    strings; non-numeric/missing quadrants are skipped."""
    scores: Dict[str, int] = {}
    for code in _NBI_QUADRANTS:
        try:
            scores[code.lower()] = int(float(doc.get(code)))
        except (TypeError, ValueError):
            continue
    if not scores:
        return None, {}
    parts = [f"{code.lower()}: {scores[code.lower()]}" for code in _NBI_QUADRANTS if code.lower() in scores]
    dominant = max(scores, key=scores.get)
    text = (
        "Whole Brain (NBI) thinking preferences — "
        + "; ".join(parts)
        + f". Dominant quadrant: {dominant}."
    )
    return text, scores


def _format_disc(doc: Dict[str, Any]) -> Tuple[Optional[str], str]:
    """Render a user_disc_scores doc into (readable text, high-traits string)."""
    ds = doc.get("disc_scores") or {}
    high = [t for t in (ds.get("HIGH_TRAITS") or []) if t]
    low = [t for t in (ds.get("LOW_TRAITS") or []) if t]
    if not (high or low):
        return None, ""
    bits = []
    if high:
        bits.append("High: " + ", ".join(high))
    if low:
        bits.append("Low: " + ", ".join(low))
    text = "DISC behavioral profile — " + "; ".join(bits) + "."
    return text, ", ".join(high)


def get_greeting_profile(user_id: str) -> Dict[str, str]:
    """Raw `users`-collection fields for the home-screen greeting: `name`,
    `language`, `timezone` (from `localTimeZone`), `country`. Reads the same
    collection as read_user_context's live-profile block, but standalone (no
    agentic/dynamic-vars merge — the greeting doesn't need it).

    `language` isn't populated in the DB yet (no such field exists as of this
    writing) — reading it now means no code change is needed once it lands.
    Missing fields are simply absent from the returned dict; the caller applies
    fallbacks. Never raises; returns {} when Mongo/user_id is unavailable.
    """
    client = get_client()          # public accessor → honours the Postgres seam
    if client is None or not user_id:
        return {}
    oid = _to_object_id(user_id)
    if oid is None:
        return {}
    try:
        db = client[config.MONGO_BACKEND_DB]
        doc = db[config.MONGO_USERS_COLLECTION].find_one(
            {"_id": oid},
            {"username": 1, "localTimeZone": 1, "language": 1, "country": 1},
        )
        if not doc:
            return {}
        out: Dict[str, str] = {}
        if doc.get("username"):
            out["name"] = doc["username"]
        if doc.get("localTimeZone"):
            out["timezone"] = doc["localTimeZone"]
        if doc.get("language"):
            out["language"] = doc["language"]
        if doc.get("country"):
            out["country"] = doc["country"]
        return out
    except Exception as exc:  # noqa: BLE001
        logger.warning(
            "mongo.greeting_profile_read_failed",
            extra={"user_id": user_id, "error": str(exc)},
        )
        return {}


def read_user_context(user_id: str, session_id: str = "") -> Dict[str, Any]:
    """Merge the durable agentic context + dynamic session vars + live profile.

    Read order (later sources can overwrite earlier ones):
      1. dynamic session vars  (agentic_session_dynamic_variables — all captured vars)
      2. agentic doc           (ic_profile, actions, intake_vars, user_context_model, …)
      3. live profile          (users collection — name, timezone, level, orgId)
      4. NBI + DISC            (assessment collections)

    `session_id` (the current session) lets the check-in scheduler exclude this
    session's own actions (BRD R4) when deriving check-in eligibility.
    """
    client = get_client()          # public accessor → honours the Postgres seam
    if client is None or not user_id:
        return {}

    ctx: Dict[str, Any] = {}
    db = client[config.MONGO_BACKEND_DB]

    # 0. Dynamic session variables (agentic_session_dynamic_variables).
    #    Merged FIRST so that intake_vars (read below) can still override where
    #    both collections carry the same key — keeping the legacy path authoritative
    #    for backward compat while all new vars come from the dynamic collection.
    from app.request_context import request_id as _req_id_ctx
    _dyn_rid = _req_id_ctx.get("")
    try:
        dyn_doc = db[config.MONGO_DYNAMIC_VARS_COLLECTION].find_one({"user_id": user_id}) or {}
        dyn_doc.pop("_id", None)
        dyn_doc.pop("user_id", None)
        dyn_doc.pop("_provenance", None)
        dyn_doc.pop("updated_at", None)
        if dyn_doc:
            ctx.update(dyn_doc)
            # Bidirectional aliases: intake may emit snake_case or camelCase depending
            # on the prompt version. Expose both so either spelling resolves correctly.
            if "coachability_detail" in dyn_doc and "coachabilityDetail" not in dyn_doc:
                ctx["coachabilityDetail"] = dyn_doc["coachability_detail"]
            if "coachabilityDetail" in dyn_doc and "coachability_detail" not in dyn_doc:
                ctx["coachability_detail"] = dyn_doc["coachabilityDetail"]
            if "coachability_score" in dyn_doc and "coachabilityScore" not in dyn_doc:
                ctx["coachabilityScore"] = dyn_doc["coachability_score"]
            if "coachabilityScore" in dyn_doc and "coachability_score" not in dyn_doc:
                ctx["coachability_score"] = dyn_doc["coachabilityScore"]
            logger.info(
                "dynamic_vars.merged_into_context",
                extra={
                    "user_id": user_id,
                    "session_id": session_id,
                    "request_id": _dyn_rid,
                    "keys_added": sorted(dyn_doc.keys()),
                },
            )
            # Snake_case ↔ camelCase bidirectional aliases for CH-specific vars.
            # The CH prompt uses snake_case placeholders; NBI/intake may write camelCase.
            # Primary: snake_case. Fallback: camelCase so older prompt versions still work.
            for _snake, _camel in (
                ("user_thinking_preference", "userThinkingPreference"),
                ("user_role_context", "userRoleContext"),
                ("user_motivations", "userMotivations"),
            ):
                if _snake in ctx and _camel not in ctx:
                    ctx[_camel] = ctx[_snake]
                if _camel in ctx and _snake not in ctx:
                    ctx[_snake] = ctx[_camel]
    except Exception as exc:  # noqa: BLE001 — never let this break the read
        logger.warning(
            "mongo.dynamic_vars_read_failed",
            extra={
                "user_id": user_id,
                "session_id": session_id,
                "request_id": _dyn_rid,
                "error": str(exc),
            },
        )

    # The transcript store (written every turn) gives two things the close-time
    # builders can't reliably provide for a returning user, each on its OWN signal:
    #   (1) repeat-user routing — keyed on a COMPLETED (ended) session: a user who
    #       abandoned before close stays "fresh" and is re-onboarded through intake;
    #   (2) cross-session memory — the VERBATIM prior-session transcript, injected
    #       via {pastConversation}. This stays on ANY prior session (not just ended
    #       ones) pending a business decision on whether an abandoned chat should be
    #       remembered; gating it on completion too is a one-line change if they say so.
    has_prior_session = False       # any earlier session — drives pastConversation
    has_completed_session = False   # only an ended session — drives the repeat flag
    past_conversation = ""
    try:
        from app.stores import conversation

        has_prior_session = conversation.has_prior_sessions(user_id, session_id)
        has_completed_session = conversation.has_completed_session(user_id, session_id)
        if has_prior_session:
            past_conversation = conversation.get_prior_transcripts(
                user_id, session_id, config.PAST_CONVERSATION_MAX_CHARS
            )
    except Exception:  # noqa: BLE001 — never let this break the read
        pass
    ctx["pastConversation"] = past_conversation

    # 1. Durable cross-session coaching context (keyed by user_id string). This is
    #    what the background builders write; the pre-session agents read it back.
    try:
        doc = db[config.MONGO_AGENTIC_COLLECTION].find_one({"user_id": user_id})
        if doc:
            doc.pop("_id", None)
            ctx.update(doc)
            # Derived placeholders the pre-session agents consume
            # (user_profile_retrieval + repeat_user_checkin):
            # Only saved actions feed the coaching context — skipped/unconfirmed
            # ('deleted') actions are excluded so the LLM doesn't treat them as
            # committed and can suggest them again in a future session.
            # Legacy 'active' status from pre-change data is treated as saved.
            ctx["previousUserActions"] = [
                a.get("full_text")
                for a in doc.get("actions", [])
                if a.get("full_text") and a.get("status") in ("saved", "active")
            ]
            ctx["previousUserInsights"] = [
                f"{i.get('insight_title', '')}: {i.get('insight_body', '')}"
                for i in doc.get("insights", [])
                if i.get("insight_title")
            ]
            ctx["previousUserContext"] = doc.get("user_context_model", {})
            # Pattern table (IC profile) from pattern_agent — what next session's
            # pattern_agent reads as {prev_pattern_table}, and the registry's "Pattern".
            if doc.get("ic_profile"):
                ctx["ic_profile"] = doc["ic_profile"]  # env prompt's {ic_profile}
                ctx["prev_pattern_table"] = doc["ic_profile"]
                ctx["pattern"] = doc["ic_profile"]
            # Flat intake vars captured by coaching_intake_agent in a prior session
            # (userRoleContext, coachingHistory, coaching_style_preference,
            # coachability_score, …). Flatten them to the top level so the same
            # placeholders the intake agent SET resolve for a returning user, and so
            # check-in/challenge see prior coaching preferences. Stored values win
            # over nothing; the live profile/NBI below can still override.
            intake_vars = doc.get("intake_vars")
            if isinstance(intake_vars, dict):
                for k, v in intake_vars.items():
                    if v not in (None, "", [], {}):
                        ctx[k] = v
            # "repeat" ONLY once the user has actually COMPLETED a session — either
            # the agentic close-counter fired (sessions_completed, bumped only at
            # session close) or the transcript store holds an earlier ended session.
            # Mid-session artefacts (intake_vars/actions/user_context_model/ic_profile)
            # are deliberately NOT counted: a user who abandoned before close stays
            # "fresh" and is re-onboarded through intake.
            completed_before = bool(doc.get("sessions_completed")) or has_completed_session
            ctx["userRepeatFresh"] = "repeat" if completed_before else "fresh"
            # Expose session counter and last session timestamp as top-level
            # placeholders so prompts can reference {session_count} and
            # {last_session_at} directly. sessions_completed is the canonical
            # internal field; both aliases are published for prompt flexibility.
            if doc.get("sessions_completed") is not None:
                ctx["session_count"] = doc["sessions_completed"]
                ctx["sessions_completed"] = doc["sessions_completed"]
            if doc.get("last_session_at"):
                ctx["last_session_at"] = doc["last_session_at"]

            # The 7-day check-in scheduler (BRD §3, R1–R6): which prior-session
            # actions are DUE for a check-in today. This — not mere existence of
            # history — is the gate the graph routes on. `checkinDue` is the
            # code-side replacement for the prompt's deleted eligibility gate;
            # `checkinEligibleActions` is the overdue batch the prompt recaps
            # (Step 2); `checkinSessionIds` are the batches to mark complete (R3).
            eligible = eligible_checkin_actions(
                doc.get("actions", []),
                today=datetime.now(timezone.utc).date(),
                current_session_id=session_id,
                due_days=config.CHECKIN_DUE_DAYS,
                checked_in_sessions=doc.get("checkin_complete_sessions", []),
            )
            ctx["checkinEligibleActions"] = [
                a.get("full_text") for a in eligible if a.get("full_text")
            ]
            ctx["checkinSessionIds"] = eligible_session_ids(eligible)
            # Only DUE when there is at least one overdue action with recap text.
            # An overdue action whose `full_text` is empty/missing gives the check-in
            # nothing to close the loop on, so it must NOT trigger a check-in (which
            # would surface an empty recap). Gate on the recap list, not mere existence.
            ctx["checkinDue"] = bool(ctx["checkinEligibleActions"])
        else:
            # No agentic doc — repeat only if the transcript store shows an earlier
            # COMPLETED (ended) session; an abandoned-only user stays "fresh".
            ctx["userRepeatFresh"] = "repeat" if has_completed_session else "fresh"
    except Exception as exc:  # noqa: BLE001
        logger.warning("mongo.agentic_read_failed", extra={"error": str(exc)})

    # 2. Live profile (keyed by ObjectId(_id)); UI-generated ids won't match.
    oid = _to_object_id(user_id)
    if oid is not None:
        try:
            doc = db[config.MONGO_USERS_COLLECTION].find_one(
                {"_id": oid},
                {
                    "username": 1, "localTimeZone": 1, "level": 1, "orgId": 1,
                    "idp_competencies": 1, "deep_link_skill": 1,
                },
            )
            if doc:
                # The users collection stores the display name as `username`
                # (lower-case). Surface it as both `name` and `userName` so the
                # prompt placeholder {userName} (resolver reads `userName`) fills.
                if doc.get("username"):
                    ctx.setdefault("name", doc["username"])
                    ctx.setdefault("userName", doc["username"])
                if doc.get("localTimeZone"):
                    ctx["timezone"] = doc["localTimeZone"]
                for key in ("level", "orgId"):
                    if doc.get(key) is not None:
                        ctx[key] = doc[key]
                # CH-specific user profile fields
                if doc.get("idp_competencies"):
                    ctx["idp_competencies"] = doc["idp_competencies"]
                if doc.get("deep_link_skill"):
                    ctx["deep_link_skill"] = doc["deep_link_skill"]
        except Exception as exc:  # noqa: BLE001
            logger.warning("mongo.profile_read_failed", extra={"error": str(exc)})

    # Fetch human-readable org name for {organizationName} placeholder.
    if ctx.get("orgId"):
        try:
            org_doc = db[config.MONGO_ORG_COLLECTION].find_one(
                {"orgId": ctx["orgId"]},
                {"name": 1, "orgName": 1, "organization_name": 1},
            )
            if org_doc:
                org_name = (
                    org_doc.get("name")
                    or org_doc.get("orgName")
                    or org_doc.get("organization_name")
                    or ""
                )
                if org_name:
                    ctx["organizationName"] = str(org_name).strip()
        except Exception:  # noqa: BLE001
            pass

    # 3. Whole Brain (NBI) thinking preference — `nbi_report`, keyed by `userId`
    #    (the user_id string, NOT an ObjectId). Latest report wins. Feeds the
    #    {userThinkingPreference} placeholder; raw quadrant scores kept for prompts
    #    that want the numbers.
    try:
        nbi = db[config.MONGO_NBI_COLLECTION].find_one(
            {"userId": user_id}, sort=[("insertedDateUTC", -1)]
        )
        if nbi:
            text, scores = _format_nbi(nbi)
            if text:
                ctx["userThinkingPreference"] = text
                ctx["user_thinking_preference"] = text  # snake_case alias for CH prompt
                ctx["nbi_scores"] = scores
    except Exception as exc:  # noqa: BLE001
        logger.warning("mongo.nbi_read_failed", extra={"error": str(exc)})

    # 4. DISC behavioral profile — `user_disc_scores`, keyed by `userId`. Latest
    #    scored upload wins. Feeds {userBehavioralPreference} (full summary) and
    #    {userBehavioralChoices} (the high traits).
    try:
        disc = db[config.MONGO_DISC_COLLECTION].find_one(
            {"userId": user_id}, sort=[("timestamp", -1)]
        )
        if disc:
            text, choices = _format_disc(disc)
            if text:
                ctx["userBehavioralPreference"] = text
                if choices:
                    ctx["userBehavioralChoices"] = choices
    except Exception as exc:  # noqa: BLE001
        logger.warning("mongo.disc_read_failed", extra={"error": str(exc)})

    return ctx
