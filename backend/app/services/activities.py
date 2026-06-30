"""Chat-driven activity routing.

Ported from the reference app's `chat_routing` + inline-widget pattern: each
companion reply can attach an inline **activity widget** (breathing, grounding,
mood check, journal) plus a few quick-reply **suggestion** chips. The client
renders the widget in the transcript and launches the native activity.

Deterministic by design — keyword + risk rules, no LLM round-trip — so the
feature works identically with or without an AI key.
"""
from __future__ import annotations

from app.schemas.content_data import Suggestion, WidgetSpec

# widget_kind values the iOS client maps to native activity screens.
_BREATHING = WidgetSpec(
    widget_kind="breathing",
    title="2-minute breathing",
    description="A guided breath to settle your body before we keep talking.",
    params={"cycles": 4},
)
_GROUNDING = WidgetSpec(
    widget_kind="grounding",
    title="5-4-3-2-1 grounding",
    description="Anchor to your senses and come back to the present.",
    params={},
)
_MOOD = WidgetSpec(
    widget_kind="mood_check",
    title="Quick mood check-in",
    description="Name how you feel — it shapes your next best step.",
    params={},
)
_JOURNAL = WidgetSpec(
    widget_kind="mini_journal",
    title="Write it down",
    description="Get the thought out of your head and onto the page.",
    params={},
)
_ONE_GOOD_THING = WidgetSpec(
    widget_kind="one_good_thing",
    title="One good thing",
    description="Name a single small thing that went right today.",
    params={},
)
_INTENTION = WidgetSpec(
    widget_kind="intention_set",
    title="Set an intention",
    description="Choose one clear, kind intention to carry forward.",
    params={},
)
_DBT_SKILL = WidgetSpec(
    widget_kind="dbt_skill",
    title="Try a DBT skill",
    description="A quick TIPP / opposite-action reset for intense moments.",
    params={},
)

#: kind → widget, for the Oracle agent's suggest_activity tool.
WIDGETS: dict[str, WidgetSpec] = {
    "breathing": _BREATHING,
    "grounding": _GROUNDING,
    "mood_check": _MOOD,
    "mini_journal": _JOURNAL,
    "one_good_thing": _ONE_GOOD_THING,
    "intention_set": _INTENTION,
    "dbt_skill": _DBT_SKILL,
}


def widget_for(kind: str) -> WidgetSpec | None:
    return WIDGETS.get(kind)


# Ordered (kind, keywords). First match wins.
_RULES: list[tuple[WidgetSpec, tuple[str, ...]]] = [
    (_BREATHING, ("anxious", "anxiety", "panic", "stress", "overwhelm", "tense",
                  "nervous", "racing", "can't breathe", "cant breathe", "freaking out")),
    (_GROUNDING, ("overthink", "spiral", "can't focus", "cant focus", "distracted",
                  "racing thoughts", "dissociat", "not present", "ground")),
    (_JOURNAL, ("vent", "journal", "write", "get it out", "process", "off my chest")),
    (_DBT_SKILL, ("angry", "furious", "rage", "urge", "craving", "self harm", "self-harm",
                  "impulse", "want to scream", "overwhelmed by emotion")),
    (_ONE_GOOD_THING, ("grateful", "gratitude", "good thing", "went well", "thankful",
                       "small win", "proud")),
    (_INTENTION, ("tomorrow", "goal", "intention", "focus on", "want to be", "plan for the day")),
    (_MOOD, ("how i feel", "check in", "checkin", "mood", "feeling", "sad", "down",
             "low", "lonely", "empty", "numb")),
]


def route(text: str, risk: str) -> tuple[WidgetSpec | None, list[Suggestion]]:
    """Return an optional inline widget + up to 3 quick-reply suggestions."""
    low = text.lower()
    widget: WidgetSpec | None = None
    for spec, keywords in _RULES:
        if any(k in low for k in keywords):
            widget = spec
            break

    suggestions: list[Suggestion] = []
    if risk in {"elevated", "crisis"}:
        suggestions.append(Suggestion(label="Urgent support", action="crisis"))

    # Offer complementary activities to whatever was (or wasn't) surfaced.
    kind = widget.widget_kind if widget else None
    alternates = {
        "breathing": [("Try grounding", "grounding"), ("Write it down", "journal")],
        "grounding": [("Breathe with me", "breathing"), ("Write it down", "journal")],
        "mini_journal": [("Breathe with me", "breathing"), ("Check in", "mood_check")],
        "mood_check": [("Breathe with me", "breathing"), ("Write it down", "journal")],
        "one_good_thing": [("Set an intention", "intention_set"), ("Check in", "mood_check")],
        "intention_set": [("One good thing", "one_good_thing"), ("Breathe with me", "breathing")],
        "dbt_skill": [("Breathe with me", "breathing"), ("Try grounding", "grounding")],
        None: [("Breathe with me", "breathing"), ("Check in", "mood_check")],
    }
    for label, action in alternates.get(kind, alternates[None]):
        suggestions.append(Suggestion(label=label, action=action))

    return widget, suggestions[:3]
