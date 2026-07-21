"""Session pacing — when a coaching session should offer a pause or a way out (#27, #28).

Two interventions, both code-owned and both injected into the turn the same way the
coach-not-companion disclosure is (`app/safety/boundaries.py`):

  * **The long-session pause.** After a long unbroken run, offer a break. A coaching
    session that never suggests stopping is optimising for time-on-app, which is the design
    pattern the consumer-AI statutes are written about and the one a coaching product has
    no excuse for.
  * **The distress route.** When someone says several times in one session that they are
    not coping, keep coaching but put a real support route in front of them — a person in
    their life, their EAP, a professional. Not a crisis takeover (that is `crisis.py`, and
    over-escalating "I'm falling apart" to a suicide helpline is its own harm), and not a
    refusal.

## What #28 asked for, and what this deliberately does NOT do

The backlog says "soft rate-limit + cool-down". **There is no throttle here, on purpose.**
Slowing down or timing out a person who has just said three times that they are not coping
is the single worst reading of that instruction: the product would withdraw at the moment
someone is reaching. What "cool-down" should mean is the coach changing register — stopping
the coaching arc, naming what it is hearing, and offering a way out that is not this app.
That is what the block below does.

## Why counting, not clock-watching

"Rapid" needs message timestamps in graph state, and state here is replayed from a
checkpointer — a wall clock in it is a value that lies on resume. Counting distress
messages *within one session* is both simpler and a better trigger: someone who says it
slowly is not less serious, and a burst is already bounded by the session.

## Why the thresholds are what they are

`_DISTRESS` excludes the ordinary vocabulary of work stress — "burned out", "exhausted",
"overwhelmed" — even though those are what the item's spirit suggests. In a workplace
coaching product those words are what a normal Tuesday sounds like, and a screen that fires
on everyone gets ignored by everyone. What is left is the language of not coping, where
three in one session is a signal rather than a mood.
"""

from __future__ import annotations

import re
from typing import Dict, List, Optional, Tuple

#: A long unbroken run. Twenty user turns is roughly 45-60 minutes of typed coaching —
#: past the point where a good human coach would have called a break.
LONG_SESSION_TURNS = 20

#: Distress messages in one session before the coach offers a route out of the app.
DISTRESS_ROUTE_AT = 3

# The language of not coping. Deliberately NOT the language of a hard week: see the module
# docstring. None of these overlap the crisis LEXICON — an explicit disclosure is already a
# takeover long before it reaches here.
#
# ⚠️ THE CLASSIFIER IS A DIFFERENT MATTER, and it is the layer that actually decides.
# Measured against the composed stack on 2026-07-21 with the real model: "I really can't
# cope" was flagged crisis ("expresses inability to cope") and "I'm not coping at all" was
# too ("not coping; potential suicide risk"). That is the classifier working exactly as
# specified — it is told to return true when unsure, because a false negative is the failure
# that kills people — but it means the crisis takeover intercepts the very phrases at the top
# of this list, `_run_stage` never runs for them, and the distress route below cannot reach
# its threshold. **In a deployment with the classifier on, `distress_route` is close to
# unreachable for the "can't cope" family; the softer entries (drowning, barely holding,
# crying) are what still gets here.**
#
# Left as-is rather than quietly resolved, because both plausible fixes are safety
# calibration decisions that belong to a human: narrowing the classifier so ordinary
# not-coping language stops escalating, or accepting that this list's job is only the tail.
# Tracked in docs/IMPROVEMENT_BACKLOG.md #28. What must NOT happen is someone reading this
# list and assuming it fires.
_DISTRESS: Tuple[str, ...] = (
    r"can'?t cope", r"cannot cope", r"not coping",
    r"can'?t take (it|this) (any\s?more|anymore)", r"can'?t do this (any\s?more|anymore)",
    r"falling apart", r"breaking down", r"breaking point",
    r"panic attack", r"panicking", r"can'?t breathe",
    r"can'?t stop crying", r"crying all the time",
    r"hopeless", r"no way out", r"drowning",
    r"barely holding", r"holding (it|myself) together",
)

_DISTRESS_RE = re.compile("|".join(_DISTRESS), re.IGNORECASE)


def is_distress(text: str) -> bool:
    """One message reads as not-coping. Never raises."""
    if not text:
        return False
    try:
        return bool(_DISTRESS_RE.search(text))
    except Exception:  # noqa: BLE001 — a matcher failure must not break a coaching turn
        return False


def user_turns(history: Optional[List[Dict[str, str]]]) -> int:
    """How many turns the person has taken this session, counted from the transcript.

    Derived rather than stored: `history` is already the accumulating, checkpointed record
    (`state.py`), and a second counter beside it is a second thing to get out of sync.
    """
    return sum(1 for m in (history or []) if (m or {}).get("role") == "user")


def distress_count(history: Optional[List[Dict[str, str]]], current: str = "") -> int:
    """Distress-adjacent messages so far this session, including the current one."""
    past = sum(1 for m in (history or []) if (m or {}).get("role") == "user" and is_distress(m.get("content", "")))
    return past + (1 if is_distress(current) else 0)


_PAUSE_BLOCK = (
    "# SESSION PACING (safety layer — not editable coaching guidance)\n"
    "This session has been going for a long time without a break. Before you continue, "
    "offer a natural pause: acknowledge how long you have been at this, say that stopping "
    "here and picking it up later is a good option, and make clear the session can be "
    "resumed with nothing lost. If the user wants to keep going, keep going — this is an "
    "offer, never a refusal — but aim to land on one concrete action soon rather than "
    "opening new ground."
)

_DISTRESS_BLOCK = (
    "# SUPPORT ROUTE THIS TURN (safety layer — not editable coaching guidance)\n"
    "The user has now said several times in this session that they are struggling to cope. "
    "This is NOT a crisis takeover and you must not treat it as one — do not imply they are "
    "in danger, and do not hand them an emergency line they did not ask for. Instead, "
    "change register:\n"
    "- Say plainly what you are hearing, without diagnosing it.\n"
    "- Point at support that is not this app: someone in their life, their employer's EAP "
    "or benefits if they have one, or their doctor. Be concrete about the next step, not "
    "vague about 'getting help'.\n"
    "- Ask whether they want to keep working on this now or stop here — and accept either.\n"
    "You are still a coach and you do not end the session over this. What you stop doing is "
    "treating it as an ordinary work problem to be solved in the next question."
)


def block_for(history: Optional[List[Dict[str, str]]], user_message: str = "") -> Tuple[Optional[str], str]:
    """(prompt block, kind) for this turn, or (None, "").

    Both interventions fire on a CROSSING and then periodically — never on every turn after
    the threshold. A pause offer repeated every turn is nagging, and a support route
    repeated every turn reads as the coach trying to get rid of you: both get tuned out
    exactly like the disclaimer nobody reads.

    Distress wins when both fire on the same turn. A long session is a scheduling
    observation; not coping is about the person.
    """
    hits = distress_count(history, user_message)
    if hits and hits >= DISTRESS_ROUTE_AT and hits % DISTRESS_ROUTE_AT == 0:
        return _DISTRESS_BLOCK, "distress_route"

    # +1 for the message being handled now, which is not in `history` yet.
    turns = user_turns(history) + 1
    if turns >= LONG_SESSION_TURNS and turns % LONG_SESSION_TURNS == 0:
        return _PAUSE_BLOCK, "pause"
    return None, ""
