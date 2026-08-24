"""Localized display fields for catalogue content.

The last leg of the Hindi story. Resources localized the clients
(2026-08-24); this localizes what the server sends INTO them — the programme,
sound and wind-down titles that sat in English inside fully-Hindi chrome.

The design is a display overlay, not a translation model:

* **English is canonical.** Search matches it, admin edits it, the claims gate
  scans it, enrollments snapshot it. A translation changes only what a
  matching-profile user sees.
* **The profile is the switch** — the same `user.language` contract the reply
  directive follows (`services/language.py`), so the model's replies and the
  catalogue flip together, never separately.
* **Absence degrades to English**, field by field: a row translated only in
  title shows a Hindi title over an English subtitle rather than nothing.

Clients needed zero changes: the localized value arrives in the same `title`
field they already render.
"""
from __future__ import annotations

from app.models.content import ContentItem
from app.models.user import User

# Profile language -> i18n key. Mirrors the reply-directive vocabulary; a
# language with no resource bundle anywhere (Hinglish reads Latin, Punjabi and
# Tamil have no translations yet) maps to no overlay rather than to a guess.
_CODES = {"hindi": "hi"}


def code_for(user: User | None) -> str | None:
    """The i18n key for this caller, or None for canonical English."""
    if user is None:
        return None
    return _CODES.get((user.language or "").strip().casefold())


def overrides(item: ContentItem, code: str | None) -> dict[str, str]:
    """Display-field overrides for [item] in [code], possibly empty.

    Only `title` and `subtitle` are localizable, and only non-blank values
    override — a sloppy `{"title": ""}` must not blank a screen.
    """
    if not code or not item.i18n:
        return {}
    entry = item.i18n.get(code) or {}
    out: dict[str, str] = {}
    for field in ("title", "subtitle"):
        value = str(entry.get(field) or "").strip()
        if value:
            out[field] = value
    return out


def localized_title(item: ContentItem | None, code: str | None) -> str | None:
    """Just the title, for call sites that carry only a snapshot to fix up."""
    if item is None:
        return None
    return overrides(item, code).get("title")
