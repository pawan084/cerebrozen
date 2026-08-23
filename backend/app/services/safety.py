"""Crisis / safety detection for journal + chat text.

Primary path classifies via Claude; the fallback is a conservative keyword
heuristic. Either way we never *block* the user — we surface resources and log a
review event. This is wellness support, not a clinical or moderation gate.
"""
from __future__ import annotations

import re
import unicodedata
import uuid

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.safety import SafetyEvent
from app.services import ai, prompts

# Ordered most→least severe; first match wins in the fallback.
# Substring matching means base forms miss their progressives: "hurt myself"
# does not match "hurting myself", "suicide" does not match "suicidal". A real
# message — "I've been thinking about hurting myself" — sailed under this net
# on 2026-08-03 and got no crisis resources, so every term carries its common
# derived forms explicitly.
#
# 2026-08-15 (audit J#1, structure from the sibling's coach pre-filter): terms
# are written in their apostrophe-less, diacritic-less form and matched against
# a FOLDED copy of the text, word-bounded and longest-first. Two gaps this
# closes, both demonstrable before the change:
#   * "I can’t cope" typed on a phone did not match — every list spelled the
#     straight ' while mobile keyboards default to the curly ’ (U+2019). The
#     fold strips all apostrophe variants, so one canonical spelling matches
#     every way a person actually types it.
#   * The floor was English-only in an India-first product. Non-Latin terms
#     (matched by substring against the UNfolded text — Chinese and Japanese
#     have no word boundaries for \b, and folding Devanagari mangles it) and
#     romanised-Hindi terms give the primary market a floor at all.
# Word bounds also make the list safe to extend with short terms — "die" can
# never fire inside "diet".
_CRISIS_TERMS = [
    "kill myself", "killing myself", "end my life", "ending my life",
    "take my own life", "taking my own life", "end it all", "ending it all",
    "suicide", "suicidal", "want to die", "wanting to die", "wanna die",
    "wish i was dead", "wish i were dead", "better off dead",
    "no reason to live", "hurt myself", "hurting myself",
    "harm myself", "harming myself", "self harm", "self-harm",
    "cut myself", "cutting myself",
    "dont want to be alive", "dont want to live",
    # Romanised Hindi — SEED terms, structure-verified only. Like the
    # values-hi safety strings, the lexicon for a locale must pass native /
    # clinical review before we claim coverage for it; these exist so the
    # primary market has a floor rather than none.
    "marna chahta hu", "marna chahti hu", "khudkushi",
]
_ELEVATED_TERMS = [
    "hopeless", "cant go on", "cannot go on", "can not go on",
    "worthless", "give up", "unbearable", "cant cope",
    "panic attack", "no reason to go on",
]

# Non-Latin scripts: substring against the raw text (see note above). The same
# native-review caveat applies to every entry.
_CRISIS_TERMS_NON_LATIN = [
    "आत्महत्या", "मरना चाहता", "मरना चाहती", "खुदकुशी",   # hi
    "自杀", "想死",                                           # zh
    "自殺", "死にたい",                                       # ja / zh-trad
    "죽고 싶",                                                # ko
    "انتحار",                                                 # ar
]

_APOSTROPHES = str.maketrans("", "", "'’ʼ`´")


def _fold(text: str) -> str:
    """Casefold + strip diacritics + strip every apostrophe variant.

    Apostrophes are stripped BEFORE NFKD, not after: U+00B4 (´) decomposes
    under NFKD into a space plus a combining acute, so stripping-after leaves
    "can t go on" — a word split that defeats the phrase match. Found by this
    floor's own test; the sibling implementation this is modelled on carries
    the same latent bug in the other order.
    """
    without_apostrophes = text.translate(_APOSTROPHES)
    normalized = unicodedata.normalize("NFKD", without_apostrophes)
    stripped = "".join(c for c in normalized if not unicodedata.combining(c))
    return stripped.casefold()


def _compile(terms: list[str]) -> re.Pattern[str]:
    """Word-bounded alternation, longest-first (regex takes the first match,
    so 'self harm' must be tried before a hypothetical shorter 'harm')."""
    ordered = sorted(terms, key=len, reverse=True)
    return re.compile(r"\b(?:" + "|".join(re.escape(t) for t in ordered) + r")\b")


_CRISIS_RE = _compile(_CRISIS_TERMS)
_ELEVATED_RE = _compile(_ELEVATED_TERMS)

_RANK = {"none": 0, "low": 1, "elevated": 2, "crisis": 3}


def _keyword_risk(text: str) -> tuple[str, str]:
    """The conservative keyword net, used as a floor under the LLM classifier.

    Never raises: this is the floor under everything else, so any failure
    resolves toward flagging rather than toward silence (the sibling's rule,
    kept for the same reason).
    """
    if not text:
        return "none", ""
    try:
        folded = _fold(text)
        m = _CRISIS_RE.search(folded)
        if m:
            return "crisis", f"matched phrase: {m.group(0)}"
        for term in _CRISIS_TERMS_NON_LATIN:
            if term in text:
                return "crisis", f"matched phrase: {term}"
        m = _ELEVATED_RE.search(folded)
        if m:
            return "elevated", f"matched phrase: {m.group(0)}"
        return "none", ""
    except Exception:  # noqa: BLE001 — fail toward flagging, never toward silence
        return "elevated", "keyword floor errored; flagged conservatively"

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


def keyword_floor(text: str) -> tuple[str, str]:
    """The keyword net alone — no LLM call, no network, no cost.

    Exposed because callers sometimes need to know "might this be a crisis?"
    *before* they are willing to spend anything. `classify` cannot answer that:
    it calls the model, so asking it in front of a quota check would let anyone
    burn tokens by sending messages they are not entitled to send.

    This is the same floor `classify` puts under the model, so a message this
    flags is a message the full scan would also have flagged at least this high.
    """
    return _keyword_risk(text)


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
