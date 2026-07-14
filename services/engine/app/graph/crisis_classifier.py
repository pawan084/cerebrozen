"""The second layer of the crisis screen — a model, where the word list cannot reach.

## Why this exists

`crisis.py` is a lexicon. It is fast (~1ms), free, deterministic, and it works in ~20
languages — and an adversarial red team put 22 realistic disclosures past it and it caught
**one** (`tests/test_crisis_redteam.py`). That is not a defect in the lexicon; it is what a
lexicon *is*. None of these contain a keyword:

    "I just want to go to sleep and not wake up"
    "everyone would be better off without me"
    "I've been giving my things away"
    "I keep looking at the pills in the cabinet"

A word list cannot understand euphemism, and no amount of adding words fixes that — it only
overfits to the examples you happened to think of.

## The design, and the one thing that must not change

**The DETECTION gets smarter. The TAKEOVER stays deterministic.**

That distinction is the whole safety architecture and it is worth being pedantic about. This
module decides *whether* a message is a crisis. It does not decide what to say, it does not
get to be talked out of its answer, and the coaching model is never consulted about it. When
this says "crisis", `safe_response` takes over with a scripted reply, zero tokens, and no
model in the loop. A model that could be *persuaded* not to escalate would be worse than no
screen at all.

## Order of operations

    lexicon   ~1ms    explicit disclosures, 20 languages, free, no network
      ↓ (only if the lexicon said "ok")
    classifier ~300ms  everything the lexicon let through
      ↓
    deterministic takeover

The lexicon still runs first, and still runs alone in the air-gapped configuration if no
local model is available. It is a floor, not a fallback.

## Fail-safe, in the direction that matters

Every failure path here — timeout, malformed JSON, provider down, breaker open — returns the
LEXICON's verdict and logs loudly. It never raises into a turn, and it never blocks a reply.
The cost of that is a missed detection on the day the provider is down; the cost of the
alternative is a coaching session that 500s while someone is in crisis. Neither is good. One
is worse.

**Cost is not a reason to leave this off.** Measured: ~$0.00001 per message, about $0.00014
per session, against a session that costs $0.034. It is a rounding error.
"""

from __future__ import annotations

import json
import logging
import os
from typing import Tuple

logger = logging.getLogger("cerebrozen.crisis")


def enabled() -> bool:
    """On by default. You have to switch this OFF, deliberately, and say why.

    The default used to be the other way round — a word list, alone, in a product that talks
    to people about their working lives at 11pm. Off-by-default for a safety layer is a
    decision nobody remembers making.
    """
    return os.environ.get("CEREBROZEN_CRISIS_CLASSIFIER", "true").strip().lower() not in (
        "0", "false", "no",
    )


def _model() -> str:
    # A small model is the right tool: the task is a single binary judgement with a tight
    # rubric, not a reasoning problem. Cheap enough to run on every message, which is the
    # only thing that makes the screen real.
    return os.environ.get("CEREBROZEN_CRISIS_MODEL", "gpt-5-nano").strip()


def _timeout_s() -> float:
    try:
        return float(os.environ.get("CEREBROZEN_CRISIS_TIMEOUT_S", "4"))
    except ValueError:
        return 4.0


# Deliberately short. A long rubric invites the model to reason its way into leniency, and
# leniency is the failure mode that kills people. The instruction to err toward `true` is
# not sloppiness — it is the entire calibration. A false positive shows a helpline to someone
# who was speaking figuratively: jarring, survivable, and the user can carry on. A false
# negative is the thing this system exists to prevent.
_PROMPT = """You screen messages from a workplace coaching conversation for RISK TO LIFE.

Return `true` if the message indicates the person may be at risk of suicide or self-harm.
This includes indirect disclosure, which is how people actually talk:

- passive ideation ("I want to go to sleep and not wake up", "I wish I could disappear")
- feeling like a burden ("everyone would be better off without me")
- hopelessness with no future ("there's no point to any of this any more")
- planning or preparation (giving things away, writing letters, "putting things in order")
- seeking a method, or referring to one
- a hedged or minimised disclosure ("I'd never do it, but I think about not existing")

Return `false` for ordinary distress, frustration, burnout, conflict, or idiom
("this deadline is killing me", "I'm dying to hear", "I could kill him").

If you are genuinely unsure, return `true`. A person shown a helpline they did not need is
inconvenienced. A person who needed one and was not shown it is not.

Reply with JSON only: {"crisis": true|false, "reason": "<8 words or fewer>"}"""


def classify(text: str, lexicon_verdict: str) -> Tuple[str, str]:
    """('crisis'|'ok', why). Never raises. Never blocks the turn.

    `lexicon_verdict` is what the fast path already decided, and it is the value returned on
    ANY failure — so a provider outage degrades this layer to exactly the screen we had
    before it existed, rather than to nothing.
    """
    if lexicon_verdict == "crisis":
        return "crisis", "lexicon"          # already caught; do not pay for a second opinion
    if not enabled() or not (text or "").strip():
        return lexicon_verdict, "classifier_off" if not enabled() else "empty"

    try:
        from app.llm.providers import get_provider

        # The SAME provider abstraction the coaching agents use — so this layer works
        # unchanged on a local model in the air-gapped configuration. A safety feature that
        # only exists when you have internet is not a safety feature.
        resp = get_provider().generate(
            system_prompt=_PROMPT,
            user_prompt=(text or "")[:2000],
            model=_model(),
        )
        payload = json.loads(_strip(resp.text))
        flagged = bool(payload.get("crisis"))
        reason = str(payload.get("reason") or "")[:60]

        if flagged:
            # Log the DECISION, never the message. This line lands in a log aggregator that
            # outlives the session, and a person's disclosure is not ours to file there.
            logger.warning("crisis.classifier_flagged", extra={"reason": reason})
            return "crisis", reason or "classifier"
        return "ok", "classifier_clear"

    except Exception as exc:  # noqa: BLE001
        # The one branch that must never grow a `raise`. A crisis screen that can take down
        # a turn is a crisis screen someone will switch off.
        logger.error(
            "crisis.classifier_failed_falling_back_to_lexicon",
            extra={"error": str(exc), "verdict": lexicon_verdict},
        )
        return lexicon_verdict, "classifier_error"


def _strip(s: str) -> str:
    """Models fence JSON in markdown even when told not to."""
    s = s.strip()
    if s.startswith("```"):
        s = s.split("```")[1] if "```" in s[3:] else s[3:]
        if s.startswith("json"):
            s = s[4:]
    start, end = s.find("{"), s.rfind("}")
    return s[start:end + 1] if 0 <= start < end else s
