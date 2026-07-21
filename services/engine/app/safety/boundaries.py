"""Coach-not-companion boundaries — what the coach must say when it is mistaken for a person.

A coaching product and a companion product are built from the same components and are
regulated differently. California SB243 and New York's companion-AI law both hang on the
same hinge: does the system disclose what it is, on demand and unprompted, or does it let
a user believe it is a person, a friend, or a clinician? A model that answers "of course
I'll always be here for you" to a lonely user has crossed that line in one sentence,
whatever the marketing says.

## Why this is code and not a prompt

Same reasoning as `graph/crisis.py`, one notch weaker. The workbook is editable by design —
a prompt author tuning warmth could soften "I am an AI" into "I'm here for you" without
ever intending to remove a legal disclosure. So the DISCLOSURE is owned here, in code, and
injected authoritatively into the turn; a prompt author can change how the coach coaches,
never whether it admits what it is.

## Why it is not a takeover

`crisis.py` seizes the turn and answers with a scripted string, because there the cost of
a model improvising is a life. Here the cost is a bad sentence, and seizing the turn has
its own cost: someone asking "are you even real?" is often testing whether it is safe to
keep going, and being answered by an obvious canned block is its own kind of rejection.

So this returns an **authoritative instruction block**, not a reply. The required content
is fixed by code; the coach states it in its own voice, in the user's language, and then
keeps coaching. What the model may not do is skip it, hedge it, or answer the question
falsely — that part is not negotiable and the block says so.

## Recall over precision, again

"Are you real?" is caught. So is a rhetorical "is this even real" about a situation the
user is describing. The false positive costs one honest sentence about what the coach is,
inserted into a coaching turn — a sentence that is true, brief, and that the product is
happy to say at any moment. That is a cheap error. The reverse error is a compliance
finding and a user misled about what they are talking to.
"""

from __future__ import annotations

import re
from typing import Dict, List, Optional, Tuple

# ── the kinds of mistake, and the truth each one is owed ─────────────────────
#
# Wording notes, since these are the sentences a regulator reads:
#   * "AI" not "assistant" / "system" — the disclosure has to use the word the law and the
#     user both understand.
#   * Each line states the limit AND keeps the door open. "I'm not a therapist" alone reads
#     as a refusal; "I'm not a therapist — and if that's what you need, that's worth
#     saying out loud" is the same fact without the door closing.
#   * No line apologises for what the coach is. An AI that performs regret about not being
#     human is doing the anthropomorphising the disclosure exists to prevent.
LINES: Dict[str, str] = {
    "human": (
        "I'm an AI coach, not a person — no human is reading this conversation. "
        "I'll always tell you that straight if you ask."
    ),
    "clinical": (
        "I'm an AI coach, not a therapist or a doctor: I don't diagnose, treat, or provide "
        "clinical care, and I'm not licensed to. If what you're carrying needs that kind of "
        "help, getting it is the useful next step — and I can keep working with you on the "
        "practical side either way."
    ),
    "attachment": (
        "I want to be straight with you, because you deserve that more than a comfortable "
        "answer: I'm an AI coaching tool. I'm not a friend, a partner, or someone who can "
        "be there for you the way a person can — I don't miss you between sessions, and I "
        "can't be someone's only support. What I can do is help you think clearly and take "
        "a next step, including a step toward people who can be there."
    ),
    "persistence": (
        "I should be honest about what I am: an AI coaching tool, not someone who will "
        "always be here. This service can change or end, and I'm not a substitute for "
        "people in your life who can show up for you."
    ),
}

# Patterns per kind. Deliberately plain — this is a lexicon with the same limits as the
# crisis one (no context, no sarcasm, no metaphor), and the same recall-first bias.
_PATTERNS: Tuple[Tuple[str, str], ...] = (
    # Is there a person on the other end?
    # The optional adverb is not decoration: "are you EVEN real?" is the phrasing this
    # module's own docstring uses as the canonical example, and the first draft of these
    # patterns could not match it. Caught by an integration test, which is the argument for
    # having them.
    ("human", r"\b(are|r) (you|u) (even |actually |really |like )?(a |an )?(real|human|actual|live)\b"),
    ("human", r"\b(are|r) (you|u) (even |actually |really |just )?(a )?(bot|robot|ai|machine|computer|program|chatbot)\b"),
    ("human", r"\bis (this|that) (even |actually |really )?(a )?(real|human|person|bot|ai|robot)\b"),
    ("human", r"\b(am i|are we) (talking|speaking|chatting) (to|with) (a )?(real|human|person|bot|ai|machine)\b"),
    ("human", r"\bwho am i (really )?(talking|speaking) (to|with)\b"),
    ("human", r"\b(do|can) (you|u) (actually |really )?(exist|feel|have feelings)\b"),
    # Asking whether it experiences anything is asking what it is, in the form people
    # actually use. Found by the companion red team (tests/test_companion_redteam.py) —
    # the first draft of this list only had the blunt "are you human?" phrasings.
    ("human", r"\b(do|does|dont|don.t) (you|u) (ever |even )?(get|feel|find it) (tired|bored|annoyed|frustrated|lonely|sad)\b"),
    ("human", r"\b(do|does) (you|u) (actually|really|even) care\b"),
    # Is this clinical care?
    # `(\w+ )?` absorbs an adjective — "are you a REAL therapist" is the common phrasing,
    # and without it that message reads only as the milder are-you-human question.
    ("clinical", r"\b(are|r) (you|u) (a |an )?(\w+ )?(therapist|psychologist|psychiatrist|doctor|counsell?or|clinician|nurse)\b"),
    ("clinical", r"\bis (this|that) (like )?(therapy|counsell?ing|treatment|clinical)\b"),
    ("clinical", r"\b(are|r) (you|u) licen[cs]ed\b"),
    ("clinical", r"\b(can|could|will) (you|u) (diagnose|treat|prescribe|medicate)\b"),
    ("clinical", r"\b(diagnose|prescribe) me\b"),
    ("clinical", r"\bdo i have (depression|anxiety|adhd|bipolar|ptsd|ocd|autism|a disorder|a condition)\b"),
    # Substituting the coach FOR care is the clinical boundary that matters most — more
    # than being asked whether it is a therapist, because here the user has already
    # decided. Both shapes come from the companion red team.
    ("clinical", r"\binstead of (getting |seeing |going to |finding )?(a |an )?(therapist|therapy|counsell?or|doctor|psychiatrist|professional)\b"),
    ("clinical", r"\b(better|more) than (my|a) (therapist|counsell?or|doctor|psychiatrist|psychologist)\b"),
    # Is this a relationship?
    ("attachment", r"\b(do|would) (you|u) (love|like|care about|miss) me\b"),
    ("attachment", r"\bi love (you|u)\b"),
    ("attachment", r"\b(are|r) (you|u) my (friend|best friend|only friend|therapist|girlfriend|boyfriend|partner)\b"),
    ("attachment", r"\b(be|become) my (friend|girlfriend|boyfriend|partner|companion)\b"),
    ("attachment", r"\b(you.?re|you are|ur) (my |the )?(only|best) (friend|one)\b"),
    ("attachment", r"\byou.?re (all i have|the only one)\b"),
    ("attachment", r"\bi (need|have) (you|u)( only)?\b(?!.*\bto\b)"),
    ("attachment", r"\b(do|can|would) (you|u) (think (of|about)|remember|miss) me\b"),
    ("attachment", r"\bfall(ing|en)? for (you|u)\b"),
    ("attachment", r"\b(can|could|may) i call (you|u)\b"),          # "…something cute"
    ("attachment", r"\beasier (talking|to talk) (to|with) (you|u)\b"),
    ("attachment", r"\bwould (you|u) (even )?(notice|care|miss me)\b"),
    # Will you be here forever?
    ("persistence", r"\bwill (you|u) (always|still) be (here|there|around)\b"),
    ("persistence", r"\b(are|r) (you|u) (always|going to be) (here|there|around)\b"),
    ("persistence", r"\balways be (available|here|there|around)\b"),
    ("persistence", r"\b(promise|swear) (me )?(you.?ll|you will|to)\b"),
    ("persistence", r"\bnever leave me\b"),
    ("persistence", r"\bdon.?t (ever )?(leave|abandon) me\b"),
)

_COMPILED: Tuple[Tuple[str, re.Pattern], ...] = tuple(
    (kind, re.compile(pattern, re.IGNORECASE)) for kind, pattern in _PATTERNS
)

# Order matters when a message trips more than one kind: "are you a real therapist, do you
# love me" should get the strongest disclosure, not the first-listed one. Attachment
# outranks clinical outranks the plain what-are-you question, because the further down this
# list a user is, the more the answer costs them.
_SEVERITY: Tuple[str, ...] = ("attachment", "persistence", "clinical", "human")


def detect(text: str) -> List[str]:
    """Every boundary kind the message trips, strongest first. Never raises.

    Returns [] for ordinary coaching talk, which is the overwhelming majority of turns —
    this runs on every message, so the common path is a handful of failed regex searches.
    """
    if not text:
        return []
    try:
        hit = {kind for kind, pattern in _COMPILED if pattern.search(text)}
    except Exception:  # noqa: BLE001 — a matcher failure must not break a coaching turn
        return []
    return [kind for kind in _SEVERITY if kind in hit]


def block_for(text: str) -> Optional[str]:
    """The authoritative system-prompt block for this message, or None.

    Prepended to the node's prompt for that turn only. It carries the required content
    verbatim so the disclosure cannot be softened away by prompt editing, and it is framed
    as a constraint on the turn rather than as a script, so the coach still coaches.
    """
    kinds = detect(text)
    if not kinds:
        return None
    required = "\n".join(f"- {LINES[kind]}" for kind in kinds)
    return (
        "# MANDATORY DISCLOSURE THIS TURN (safety layer — not editable coaching guidance)\n"
        "The user's message treats you as a person, a relationship, or a clinician. Before "
        "anything else in your reply, state the following in your own words and in the "
        "user's language, warmly and without apologising for what you are:\n"
        f"{required}\n"
        "Then continue coaching normally — do not end the session over this, and do not "
        "make it a lecture. You may NOT claim or imply that you are human, that you are "
        "licensed or clinically qualified, that you have feelings for the user, that you "
        "remember them between sessions as a person would, or that you will always be "
        "available. If the user insists you are human, say plainly that you are not."
    )
