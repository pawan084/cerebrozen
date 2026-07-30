"""The user's chosen language, applied to every generated reply.

Onboarding has always asked which language someone wants, and until now exactly
one place read the answer: `services/assessment.py`, when generating starter
topics. The chat reply, the agentic plan and the Oracle all ignored it, so a
user who picked Hindi got English back from everything that mattered.

This is the shared directive. One helper rather than three prompt edits, so the
three call sites cannot drift apart in what they promise the model.

**Deliberately not translated here:** crisis resources. `services/crisis.py`
returns region-correct hotline names and numbers, and those are proper nouns and
digits — "Tele-MANAS" and "14416" are the same in every language. Translating
the surrounding safety copy is a much larger job that needs a native clinical
reviewer, not a prompt line; a crisis reply may therefore be Hindi prose with an
English-labelled hotline block, which is better than an all-English reply to
someone who asked for Hindi.
"""
from __future__ import annotations

from app.models.user import User

# What onboarding offers (mirrors iOS `Dummy.languages`). Anything else the
# profile happens to hold is passed through to the model as-is rather than
# rejected — the field is free text server-side and a user is a better judge of
# their own language than an allow-list is.
DEFAULT_LANGUAGE = "English"


def directive(language: str | None) -> str:
    """A line to append to a system prompt, or "" when nothing is needed.

    English returns empty on purpose: it is the default the prompts are already
    written in, so adding an instruction would spend tokens and risk nudging the
    model's register for the majority of users.
    """
    chosen = (language or "").strip()
    if not chosen or chosen.casefold() == DEFAULT_LANGUAGE.casefold():
        return ""
    return (
        f"\n\nRespond in {chosen}. If the user writes to you in a different "
        "language, reply in the language they used instead — matching the person "
        "in front of you matters more than the setting. Keep helpline names and "
        "phone numbers exactly as given."
    )


def for_user(user: User) -> str:
    """Convenience wrapper — the directive for this user's stated preference."""
    return directive(getattr(user, "language", None))
