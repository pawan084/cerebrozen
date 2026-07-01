"""Crisis / safety detection for journal + chat text.

Primary path classifies via Claude; the fallback is a conservative keyword
heuristic. Either way we never *block* the user — we surface resources and log a
review event. This is wellness support, not a clinical or moderation gate.
"""
from __future__ import annotations

import uuid

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.safety import SafetyEvent
from app.services import ai

# Ordered most→least severe; first match wins in the fallback.
_CRISIS_TERMS = [
    "kill myself", "end my life", "suicide", "want to die", "better off dead",
    "no reason to live", "hurt myself", "self harm", "self-harm",
]
_ELEVATED_TERMS = [
    "hopeless", "can't go on", "cant go on", "worthless", "give up",
    "unbearable", "can't cope", "cant cope", "panic attack",
]

# Region-correct crisis hotlines live in ``app.services.crisis`` (a mirror of the
# iOS CrisisDirectory). Import from there rather than hardcoding a country here.

_SYSTEM = (
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

    result = await ai.complete_json(_SYSTEM, f"Text:\n{text}")
    if isinstance(result, dict) and result.get("risk_level") in {"none", "low", "elevated", "crisis"}:
        return result["risk_level"], str(result.get("reason", ""))[:255]

    lowered = text.lower()
    for term in _CRISIS_TERMS:
        if term in lowered:
            return "crisis", f"matched phrase: {term}"
    for term in _ELEVATED_TERMS:
        if term in lowered:
            return "elevated", f"matched phrase: {term}"
    return "none", ""


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
        db.add(
            SafetyEvent(
                user_id=user_id,
                source=source,
                source_id=source_id,
                risk_level=risk_level,
                reason=reason,
                excerpt=(excerpt or text)[:500],
            )
        )
    return risk_level
