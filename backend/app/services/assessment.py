"""Self-reflection assessment + personalized conversation-topic generation.

The *assessment* is a small, fixed taxonomy the user picks from during
onboarding: **motivations** (psychological drivers, chosen at the category
level) and **goals** (concrete practices, grouped into categories). From the
selected subset we generate a handful of short, tappable *conversation
topics* that seed the companion chat.

Generation uses the configured LLM when available (see :mod:`app.services.ai`)
and otherwise falls back to a deterministic, curated map keyed off the same
labels — so the feature works fully offline / key-free, like the rest of the
backend.
"""
from __future__ import annotations

from app.services import ai

DEFAULT_TOPIC_COUNT = 8
MIN_TOPIC_COUNT = 4
MAX_TOPIC_COUNT = 12

# ── Canonical taxonomy (shared shape with the iOS onboarding) ───────────────
# Motivations are selected at the *category* level (e.g. "Focus"); the
# sub-traits give the model richer grounding. Goals are selected at the *item*
# level and grouped into categories.
MOTIVATIONS: dict[str, list[str]] = {
    "Focus": ["Deep work", "Mental clarity", "Single-tasking"],
    "Calm": ["Emotional balance", "Letting go", "Steady under pressure"],
    "Confidence": ["Self-belief", "Inner strength", "Resilience"],
    "Discipline": ["Will power", "Consistency", "Follow-through"],
    "Connection": ["Belonging", "Openness", "Trust"],
}

GOALS: dict[str, list[str]] = {
    "Daily Rituals": ["Reduce stress", "Sleep better", "Practice meditation", "Calmer mornings"],
    "Personal Development": [
        "Stop overthinking",
        "Build confidence",
        "Feel less alone",
        "Strengthen will power",
    ],
}

# Flat lookup of every valid goal item, for membership checks.
_ALL_GOALS = [item for items in GOALS.values() for item in items]

ASSESSMENT_STRUCTURE = {"motivations": MOTIVATIONS, "goals": GOALS}

# ── Deterministic fallback topics ───────────────────────────────────────────
# Each label maps to a few ≤6-word, specific (non-generic) conversation
# openers. Used when no LLM is configured or the call fails.
_TOPIC_SEEDS: dict[str, list[str]] = {
    # Motivations
    "Focus": ["What keeps pulling my focus", "One distraction I keep fighting"],
    "Calm": ["A worry that won't settle", "Where tension lives today"],
    "Confidence": ["A doubt I keep replaying", "When I sell myself short"],
    "Discipline": ["A promise I broke to myself", "Why follow-through feels hard"],
    "Connection": ["Feeling unseen lately", "Reaching out feels heavy"],
    # Goals
    "Reduce stress": ["What's quietly draining me", "My biggest stress this week"],
    "Sleep better": ["Why my mind races at night", "Winding down before bed"],
    "Practice meditation": ["Sitting still feels impossible", "Making space to just breathe"],
    "Calmer mornings": ["Mornings feel rushed", "Starting the day calmer"],
    "Stop overthinking": ["A thought stuck on loop", "Replaying the same scenario"],
    "Build confidence": ["Trusting my own decision", "Owning a recent win"],
    "Feel less alone": ["Carrying this on my own", "Wanting to feel understood"],
    "Strengthen will power": ["A habit I keep skipping", "Staying with hard things"],
}

_GENERAL_TOPICS = [
    "What's weighing on me today",
    "Something small that went right",
    "What I need more of",
    "An emotion I can't name",
    "A boundary I want to keep",
    "What's draining my energy",
    "Something I keep avoiding",
    "What rest would look like",
    "A win I brushed past",
    "What I want to let go",
]

_SYSTEM = (
    "You are a careful psychological conversation planner for CereBro, a calm, "
    "non-clinical mental-wellness app. From a person's self-reflection (the "
    "motivations and goals they chose), propose short conversation topics they "
    "can tap to start talking with their companion. You never diagnose, label, "
    "give advice, or invent events the person did not mention — you only open "
    "doors for reflection."
)


def _prompt(motivations: list[str], goals: list[str], language: str, count: int) -> str:
    import json

    return (
        "Generate conversation topics from this self-reflection assessment.\n\n"
        f"FULL ASSESSMENT TAXONOMY (for context):\n{json.dumps(ASSESSMENT_STRUCTURE, ensure_ascii=False)}\n\n"
        f"SELECTED MOTIVATIONS: {motivations or 'none'}\n"
        f"SELECTED GOALS: {goals or 'none'}\n"
        f"LANGUAGE: {language}\n\n"
        "RULES:\n"
        f"- Return exactly {count} topics.\n"
        "- Each topic is 6 words or fewer and reads as a tappable chip.\n"
        "- Each topic must connect to at least one SELECTED motivation or goal.\n"
        "- Be specific, not generic. Avoid bland labels.\n"
        '  AVOID: "Self improvement", "Mental health", "Stress".\n'
        '  PREFER: "Managing pressure before meetings", "Why I keep overthinking".\n'
        "- No two topics may restate the same underlying idea.\n"
        f"- Write every topic in {language}.\n"
        "- Warm and reflective; never diagnose, advise, or invent specific events.\n\n"
        'Return ONLY valid JSON: {"topics": [{"id": 1, "topic": "..."}]}'
    )


def normalize_selection(
    motivations: list[str] | None, goals: list[str] | None
) -> tuple[list[str], list[str]]:
    """Keep only selections that exist in the taxonomy (validate ⊆ available).

    Falls back to a gentle default when nothing valid is selected, so topic
    generation always has something to work with.
    """
    mots = [m for m in (motivations or []) if m in MOTIVATIONS]
    gls = [g for g in (goals or []) if g in _ALL_GOALS]
    if not mots and not gls:
        gls = ["Reduce stress"]
    return mots, gls


def _word_count(s: str) -> int:
    return len(s.split())


def _fallback_topics(motivations: list[str], goals: list[str], count: int) -> list[str]:
    """Interleave curated seeds across the selected labels, deduped, padded."""
    # Interleave motivation + goal seeds so the mix reflects the whole selection.
    pools = [_TOPIC_SEEDS.get(label, []) for label in (*goals, *motivations)]
    out: list[str] = []
    seen: set[str] = set()
    depth = max((len(p) for p in pools), default=0)
    for i in range(depth):
        for pool in pools:
            if i < len(pool) and pool[i].lower() not in seen:
                out.append(pool[i])
                seen.add(pool[i].lower())
                if len(out) >= count:
                    return out
    for topic in _GENERAL_TOPICS:
        if len(out) >= count:
            break
        if topic.lower() not in seen:
            out.append(topic)
            seen.add(topic.lower())
    return out[:count]


async def generate_topics(
    motivations: list[str] | None,
    goals: list[str] | None,
    language: str = "English",
    count: int = DEFAULT_TOPIC_COUNT,
) -> tuple[list[dict], str]:
    """Return ``([{id, topic}, …], source)`` where source is ``"ai"`` or ``"rule"``.

    Always returns at least the deterministic set so the caller never has to
    handle an empty result.
    """
    count = max(MIN_TOPIC_COUNT, min(MAX_TOPIC_COUNT, count))
    mots, gls = normalize_selection(motivations, goals)

    topics: list[str] = []
    source = "rule"
    data = await ai.complete_json(_SYSTEM, _prompt(mots, gls, language, count), max_tokens=500)
    if isinstance(data, dict) and isinstance(data.get("topics"), list):
        seen: set[str] = set()
        for item in data["topics"]:
            text = ""
            if isinstance(item, dict):
                text = str(item.get("topic", "")).strip()
            elif isinstance(item, str):
                text = item.strip()
            # Enforce the ≤6-word, non-empty, deduped contract the model can drift on.
            if text and _word_count(text) <= 6 and text.lower() not in seen:
                topics.append(text)
                seen.add(text.lower())
        if topics:
            source = "ai"

    if not topics:
        topics = _fallback_topics(mots, gls, count)

    return [{"id": i + 1, "topic": t} for i, t in enumerate(topics[:count])], source
