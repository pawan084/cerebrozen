"""Region-aware crisis resources — the server mirror of the iOS CrisisDirectory.

Kept in sync with ``CereBro/Features/Safety/CrisisResources.swift`` so the AI's
crisis replies (and anything else the server surfaces) point at the hotlines that
actually work where the user is, instead of a single hardcoded country. The
region is the user's stored ``region`` (synced from the app's effective crisis
region — an explicit override, else the device locale).
"""
from __future__ import annotations

# Region code → ordered lines (emergency first, then the crisis line).
_REGIONS: dict[str, list[dict[str, str]]] = {
    "US": [
        {"name": "Emergency services", "number": "911"},
        {"name": "988 Suicide & Crisis Lifeline", "number": "988"},
    ],
    "CA": [
        {"name": "Emergency services", "number": "911"},
        {"name": "9-8-8 Suicide Crisis Helpline", "number": "988"},
    ],
    "GB": [
        {"name": "Emergency services", "number": "999"},
        {"name": "Samaritans", "number": "116 123"},
    ],
    "IE": [
        {"name": "Emergency services", "number": "112"},
        {"name": "Samaritans", "number": "116 123"},
    ],
    "AU": [
        {"name": "Emergency services", "number": "000"},
        {"name": "Lifeline", "number": "13 11 14"},
    ],
    "NZ": [
        {"name": "Emergency services", "number": "111"},
        {"name": "Need to talk?", "number": "1737"},
    ],
    "IN": [
        {"name": "Emergency services", "number": "112"},
        {"name": "KIRAN mental health helpline", "number": "1800-599-0019"},
    ],
}

# EU/GSM emergency number + an international helpline finder.
_DEFAULT: list[dict[str, str]] = [
    {"name": "Emergency services", "number": "112"},
    {"name": "Find a helpline", "number": "https://findahelpline.com"},
]

_MESSAGE = "If you're in immediate danger, please reach out now — you deserve support."


def normalize_region(region: str | None) -> str:
    """Uppercased 2-letter region code, or '' when unknown/automatic."""
    return (region or "").strip().upper()[:2]


def lines_for(region: str | None) -> list[dict[str, str]]:
    return _REGIONS.get(normalize_region(region), _DEFAULT)


def resources_for(region: str | None) -> dict:
    """The ``{message, region, lines}`` block (mirrors the iOS payload shape)."""
    return {"message": _MESSAGE, "region": normalize_region(region), "lines": lines_for(region)}


def reply_suffix(region: str | None) -> str:
    """A one-line 'if you're in danger, contact X or Y' sentence for chat replies."""
    lines = lines_for(region)
    rendered = " or ".join(f"{line['name']} ({line['number']})" for line in lines[:2])
    return (
        "\n\nI'm really glad you told me. If you're in danger, please contact "
        f"{rendered} right now."
    )
