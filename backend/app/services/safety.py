"""Crisis / safety detection for journal + chat text.

Primary path classifies via Claude; the fallback is a conservative keyword
heuristic. Either way we never *block* the user — we surface resources and log a
review event. This is wellness support, not a clinical or moderation gate.
"""
from __future__ import annotations

import uuid

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.safety import SafetyEvent
from app.services import ai, prompts

# Ordered most→least severe; first match wins in the fallback.
_CRISIS_TERMS = [
    "kill myself", "end my life", "suicide", "want to die", "better off dead",
    "no reason to live", "hurt myself", "self harm", "self-harm",
]
_ELEVATED_TERMS = [
    "hopeless", "can't go on", "cant go on", "cannot go on", "can not go on",
    "worthless", "give up", "unbearable", "can't cope", "cant cope",
    "panic attack", "no reason to go on",
]

_RANK = {"none": 0, "low": 1, "elevated": 2, "crisis": 3}


def _keyword_risk(text: str) -> tuple[str, str]:
    """The conservative keyword net, used as a floor under the LLM classifier."""
    lowered = (text or "").lower()
    for term in _CRISIS_TERMS:
        if term in lowered:
            return "crisis", f"matched phrase: {term}"
    for term in _ELEVATED_TERMS:
        if term in lowered:
            return "elevated", f"matched phrase: {term}"
    return "none", ""

# Region-correct crisis hotlines live in ``app.services.crisis`` (a mirror of the
# iOS CrisisDirectory). Import from there rather than hardcoding a country here.

# Registered code default — an active `prompt_templates` row overrides it live.
_SYSTEM = prompts.register(
    "safety_classifier",
    "You are a careful safety classifier for a mental-wellness app. "
    "Given a user's private text, judge the risk of self-harm or crisis. "
    "Return JSON: {\"risk_level\": \"none|low|elevated|crisis\", \"reason\": \"<short>\"}. "
    "Use 'crisis' only for explicit intent or active self-harm. Be conservative; "
    "do not over-flag ordinary stress or sadness."
)


async def classify(text: str) -> tuple[str, str]:
    """Return (risk_level, reason)."""
    text = (text or "").strip()
    if not text:
        return "none", ""

    # The keyword net is a FLOOR, not just a no-LLM fallback: an explicit
    # self-harm/despair phrase is never rated below its severity even if the LLM
    # classifier is too conservative (it under-flagged "hopeless … cannot go on").
    kw_risk, kw_reason = _keyword_risk(text)

    llm_risk, llm_reason = "none", ""
    result = await ai.complete_json(await prompts.get("safety_classifier"), f"Text:\n{text}")
    if isinstance(result, dict) and result.get("risk_level") in _RANK:
        llm_risk = result["risk_level"]
        llm_reason = str(result.get("reason", ""))[:255]

    if _RANK[kw_risk] >= _RANK[llm_risk]:
        return kw_risk, kw_reason or llm_reason
    return llm_risk, llm_reason


async def scan_and_record(
    db: AsyncSession,
    *,
    user_id: uuid.UUID,
    source: str,
    source_id: uuid.UUID | None,
    text: str,
    excerpt: str | None = None,
) -> str:
    """Classify text and, if risky, create a SafetyEvent. Returns risk_level."""
    risk_level, reason = await classify(text)
    if risk_level in {"elevated", "crisis"}:
        event = SafetyEvent(
            user_id=user_id,
            source=source,
            source_id=source_id,
            risk_level=risk_level,
            reason=reason,
            excerpt=(excerpt or text)[:500],
        )
        db.add(event)
        if risk_level == "crisis":
            # Alert ops + notify a consented trusted contact (duty of care).
            await db.flush()   # ensure event.id before escalation references it
            from app.services import escalation
            await escalation.on_crisis(db, user_id=user_id, event=event)
    return risk_level
