"""The crisis helpline directory, per region.

WHY THIS IS CODE, NOT CONFIG OR CONTENT (docs/SECURITY.md, CLAUDE.md rule 4): handing a
person in crisis a number is a safety mechanism, not coaching craft. It must not be
editable from the prompt workbook, and it must not be reachable by a prompt author.

WHY IT IS PER-REGION: it shipped hard-coded to one country's numbers, which is worse than
useless to everyone else — it hands someone in crisis a number that does not answer.
``app/llm/prompts.py`` already makes this point for the scripted reply's single line; this
module is the same commitment for the *client surfaces*, which need several rows, not a
sentence.

THE LAST ROW IS LOAD-BEARING. Every region ends with the international finder, and an
unknown region resolves to a list that is *only* neutral entries. There is deliberately no
way to get an empty list out of this module: a crisis screen with nothing on it is the
worst possible outcome, so ``for_region`` is total — every input returns something
dialable. Tests pin exactly that.

WHAT WE DO NOT DO: guess the region from an IP or a SIM. The person's own choice
(``User.region``) and their org's default (``Org.crisis_region``) are the only inputs, both
resolved on the platform. Guessing wrong here is not a UX blemish, it is the failure mode
this module exists to prevent.
"""

from __future__ import annotations

from typing import TypedDict


class Helpline(TypedDict):
    """One dialable row. ``target`` is what the client acts on — a phone number to dial
    or a URL to open; ``kind`` says which, so a client never has to parse it."""

    name: str
    detail: str
    target: str
    kind: str  # "tel" | "url"


# Region-neutral, and therefore the fallback for everyone. "Your local emergency number"
# is deliberately not a number: we do not know the caller's country, and inventing one is
# the exact bug this module fixes. findahelpline.com routes to the caller's own region.
_INTERNATIONAL: list[Helpline] = [
    {
        "name": "Find a helpline in your country",
        "detail": "International directory · routes to your region",
        "target": "https://findahelpline.com",
        "kind": "url",
    },
]

# Per-region rows, most-direct-first. Each list is *prepended* to _INTERNATIONAL by
# for_region(), so the finder is always reachable even where we have local numbers.
#
# Sources are each country's official/most-established national line. Adding a region is a
# safety change: verify the number answers, verify it is national (not a city service), and
# add a test. Do not add a region you cannot verify — the international finder already
# covers it correctly, and a wrong number is worse than one more tap.
_BY_REGION: dict[str, list[Helpline]] = {
    "IN": [
        {"name": "Tele-MANAS", "detail": "Free government mental-health line · 24/7",
         "target": "14416", "kind": "tel"},
        {"name": "Emergency services", "detail": "Police · ambulance · fire",
         "target": "112", "kind": "tel"},
        {"name": "KIRAN", "detail": "National mental-health helpline · 24/7",
         "target": "1800-599-0019", "kind": "tel"},
    ],
    "US": [
        {"name": "988 Suicide & Crisis Lifeline", "detail": "Call or text 988 · 24/7",
         "target": "988", "kind": "tel"},
        {"name": "Emergency services", "detail": "Police · ambulance · fire",
         "target": "911", "kind": "tel"},
    ],
    "GB": [
        {"name": "Samaritans", "detail": "Free, 24/7, any reason",
         "target": "116123", "kind": "tel"},
        {"name": "Emergency services", "detail": "Police · ambulance · fire",
         "target": "999", "kind": "tel"},
    ],
    "CA": [
        {"name": "9-8-8 Suicide Crisis Helpline", "detail": "Call or text 988 · 24/7",
         "target": "988", "kind": "tel"},
        {"name": "Emergency services", "detail": "Police · ambulance · fire",
         "target": "911", "kind": "tel"},
    ],
    "AU": [
        {"name": "Lifeline", "detail": "Crisis support · 24/7",
         "target": "131114", "kind": "tel"},
        {"name": "Emergency services", "detail": "Police · ambulance · fire",
         "target": "000", "kind": "tel"},
    ],
    "NZ": [
        {"name": "1737 Need to talk?", "detail": "Free call or text · 24/7",
         "target": "1737", "kind": "tel"},
        {"name": "Emergency services", "detail": "Police · ambulance · fire",
         "target": "111", "kind": "tel"},
    ],
    # The EU has no single mental-health line; 112 is the one genuinely pan-EU number, so
    # this region claims only that plus the finder rather than pretending otherwise.
    "EU": [
        {"name": "Emergency services", "detail": "The EU-wide emergency number",
         "target": "112", "kind": "tel"},
    ],
}


def regions() -> list[str]:
    """Regions with local rows. Not the set of accepted inputs — every input is accepted
    (see ``for_region``); this is only what we can be *specific* about."""
    return sorted(_BY_REGION)


def for_region(region: str | None) -> list[Helpline]:
    """The helplines to show someone in ``region``. Total: never empty, never raises.

    An unknown, empty, or None region is not an error — it is the common case (the person
    hasn't chosen, and their org hasn't either). It resolves to the neutral international
    list, which is correct everywhere, rather than to a guess, which is correct in one
    country and dangerous in the rest.
    """
    key = (region or "").strip().upper()
    return [*_BY_REGION.get(key, []), *_INTERNATIONAL]
