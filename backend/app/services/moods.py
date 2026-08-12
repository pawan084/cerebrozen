"""The mood taxonomy, in one place.

CROSS-STACK CONTRACT. The check-in vocabulary is duplicated by hand into every
client (CLAUDE.md); this module is the server's half and the authority for how
a label is *interpreted*:

    Android  ui/screens/TodayScreen.kt  MOODS
    iOS      Models/DummyData.swift     moods
    Web      app/(authed)/home/page.tsx MOODS

Why this file exists
--------------------
"Which moods mean someone is having a hard time" was written out four separate
times — insights._NEG_MOODS, trends.NEG_MOODS, and inline literals in
agentic.py and nudges.py — and the two inline copies had drifted, omitting
"overwhelmed". The effect was not cosmetic: a user who checked in as
Overwhelmed was read as *not* struggling, so the stress-aware plan and the
wind-down nudge both stayed silent for the person most likely to need them.
One definition, imported everywhere, is what stops that recurring.

Unknown labels
--------------
Anything not in DIFFICULT is treated as neutral, never guessed at. That is what
makes "Not sure" safe to offer: it is the answer for someone who cannot name a
feeling, and it must not be scored as either distress or contentment. A future
client can add a label without this module lying about it.
"""
from __future__ import annotations

# Moods that mean "this is hard right now".
#
# Includes the words the CLIENTS offer plus the ones the LLM and older rows use
# ("stressed", "sad", "heavy"), because this set is matched against stored
# strings, not against a fixed enum.
DIFFICULT: frozenset[str] = frozenset(
    {"anxious", "low", "tired", "overwhelmed", "stressed", "sad", "heavy"}
)

# The subset the ACTIVE-INTERVENTION paths key on — a plan that reshapes itself
# and a nudge that fires at wind-down time. Deliberately the same set: an
# earlier, narrower copy is exactly the bug this module was written to end.
STRESS_SIGNALS: frozenset[str] = DIFFICULT


def is_difficult(mood: str | None) -> bool:
    """True when a stored mood label reads as a hard feeling.

    Case-insensitive and None-safe, because it is called against database rows
    and LLM output as well as client payloads.
    """
    return (mood or "").strip().lower() in DIFFICULT
