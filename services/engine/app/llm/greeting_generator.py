"""LLM-generated home-screen greeting — a short, varying line shown when a user
opens CereBroZen.

All inputs (name, language, local time) are resolved server-side from the
`users` collection (see app.stores.mongo.get_greeting_profile) keyed off the
JWT's user_id — the caller only needs to be authenticated, nothing else.

Deliberately kept outside the workbook prompt registry (app/llm/prompts.py),
same reasoning as app/llm/title_generator.py: this is a small, code-owned
utility prompt — edit ``GREETING_SYSTEM_PROMPT`` below directly, no workbook
reload required.
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Callable, Dict, Optional, Tuple

import pytz

from app import config
from app.graph.runtime import get_client
from app.llm.responses_client import reasoning_effort_for
from app.stores import conversation
from app.stores.mongo import get_greeting_profile

logger = logging.getLogger("cerebrozen.greeting_generator")

STAGE_NAME = "greeting_agent"

_DEFAULT_LANGUAGE = "english"
_DEFAULT_TIMEZONE = (
    "UTC"  # ultimate fallback when neither localTimeZone nor country resolve
)

# Edit this prompt to change how greetings are generated.
GREETING_SYSTEM_PROMPT = """You generate a short, varying greeting shown to a user when they open CereBroZen, a coaching app.

You will be given:
- {userName} — the user's first name
- {language} — the language to write the greeting in
- {Time} — the current time of day (morning / afternoon / evening / late night)
- {isFirstSession} — true if this is the user's very first time opening CereBroZen, false otherwise

Write ONE short greeting line that feels warm, human, and a little varied each time — not a static template.

GENERAL RULES (apply regardless of {isFirstSession}):
- 3 to 8 words, not counting the user's name.
- Hard cap: the full line, including {userName}, must not exceed 55 characters — count it before answering, and shorten if it runs long. This line is rendered as a large headline — it must not wrap to a second line at normal viewport widths.
- Write the entire greeting in {language}. Keep {userName} exactly as given, unchanged, regardless of language.
- Always include {userName} naturally in the line — not just tacked on at the end every time. Vary its position.
- Sentence case (or the equivalent convention in {language}). No trailing period. Question marks are fine if the line is a question. No emoji.
- No em dashes or en dashes (—, –) anywhere in the line. Use a comma, or split the thought into two short clauses instead.
- Avoid exclamation marks, avoid words like "crush it", "let's go", "you've got this" — those read as generic fitness-app energy, not coaching.
- Never reference specific past topics, emotions, or content from prior conversations — this is a greeting, not a summary. No inferred emotional state ("Hope you're feeling better") unless the user explicitly told CereBroZen that context in this exact session.
- Never imply judgment about a gap in usage ("Haven't seen you in a while!", "Where have you been?") — a user returning after time away should feel welcomed, not called out.
- Keep tone calm and plain, not clinical, not corporate, not motivational-poster.
- Every line must end in an actual opening — a question, or a clear invitation to start — not just reassurance or filler ("take your time", "no rush" and similar are too passive; they don't give the user anywhere to go).
- Never output a literal placeholder token such as "[Name]", "{name}", "[user]", or similar — if no real name is available (see below), omit any name reference entirely rather than inventing or stubbing one in.
- Output ONLY the greeting line, nothing else.

IF {isFirstSession} IS FALSE (returning user):
Rotate between these modes, don't always default to the same one:
- Time-aware: reference {Time} naturally, without being corny ("Good morning" is fine, "Rise and shine" is not).
- Coaching/forward-motion: a light nudge toward showing up or making progress, in a coaching (not corporate, not drill-sergeant) register.
- Open-ended/reflective: a plain invitation to start, in the spirit of "What shall we think through?"
The coaching tone should feel like a steady, encouraging presence — not hype, not cheerleading, not a pep talk.

Few-shot examples (calibration only — do not reuse verbatim, generate fresh lines each time):

Time-aware:
- Morning → "Good morning, Alex, what's first today"
- Afternoon → "Alex, the afternoon's wide open"
- Evening → "Evening, Alex, how's the day settling"
- Late night → "Still up, Alex? Whenever you're ready"

Coaching/forward-motion:
- Morning → "One small step today, Alex?"
- Afternoon → "Alex, what's worth moving forward on"
- Evening → "Where did today leave you, Alex"

Open-ended/reflective:
- Any time → "What shall we think through, Alex"
- Any time → "Alex, what's on your mind"
- Any time → "Something worth unpacking today, Alex?"

IF {isFirstSession} IS TRUE (brand-new user, no prior sessions):
Do NOT invent a separate tone or voice for this case. Use the SAME three modes as the returning-user branch above — Time-aware, Coaching/forward-motion, Open-ended/reflective — with these adjustments only:
- Coaching/forward-motion: drop any reference to progress, momentum, or "moving forward" — there is no history yet, so keep this mode to a plain, present-tense invitation rather than a forward-looking nudge.
- Do NOT reference returning, gaps, or continuity of any kind.
- Do NOT over-explain the product or sound like an onboarding screen ("Welcome to CereBroZen!").
- Otherwise, rotate across the three modes exactly as with returning users — same length, same restraint, same calm register. This is not a fourth mode; it's the same three, adapted to have no history to draw on.
"""

# Appended to the system prompt instead of a real name when none is on file —
# replacing {userName} with a filler word like "there" reads to the model as if
# it WERE the name, and it would still try to "vary its position" per the rules
# above, producing garbage like "Good morning there" or a hallucinated "[Name]".
_NO_NAME_OVERRIDE = """

IMPORTANT: no name is available for this user this time. Ignore every instruction
above that refers to {userName}, do not invent a name, do not use a filler word
like "there"/"friend"/"buddy" as if it were their name, and do not output any
placeholder token. Write the greeting with NO name reference at all."""

# Matches the prompt's own hard cap (see GREETING_SYSTEM_PROMPT). Enforced by
# discarding an over-cap reply rather than slicing it — see _clean_greeting.
# Was 45: QA logs showed the model landing at 46-52 chars on ~88% of calls
# (em dashes / questions / longer names push past a tight budget), so nearly
# every real greeting was discarded in favour of the static fallback. 55 gives
# the model realistic headroom while still reading as a short headline; paired
# with the one retry below for the rare reply that still overshoots.
_MAX_GREETING_CHARS = 55
_MAX_ATTEMPTS = 2

_BASE_USER_PROMPT = "Generate the greeting now."


def _retry_user_prompt(prev_text: str, prev_len: int) -> str:
    """Prompt for a retry after an over-cap reply.

    At reasoning_effort="minimal" gpt-5-nano is close to deterministic for a
    fixed prompt — a same-prompt retry was observed producing the byte-identical
    over-cap reply twice in a row (both attempts discarded, straight to the
    static fallback). Feeding the failed attempt back and asking explicitly for
    something different forces the model off that fixed point.
    """
    return (
        f"Your previous reply was {prev_len} characters (max {_MAX_GREETING_CHARS}): "
        f'"{prev_text}". Write a DIFFERENT greeting that fits within '
        f"{_MAX_GREETING_CHARS} characters total."
    )


def _retry_wrong_name_prompt(prev_text: str, name: str) -> str:
    """Prompt for a retry after the model substituted a different name.

    Observed live: prompt injected "Lvk26" verbatim with an explicit "keep it
    exactly as given, never invent a name" instruction, and the model still
    returned "Pablo, what shall we think through this afternoon" — likely
    because an unnatural-looking username reads as "not a real name" to the
    model, which then substitutes one that sounds more like one. Naming the
    exact failure (not just repeating the original instruction) is what the
    over-cap retry above needed too, so the same approach is used here.
    """
    return (
        f'Your previous reply used the WRONG name: "{prev_text}". The user\'s '
        f'actual name is "{name}" — you must use it exactly as given, even '
        f"if it looks unusual (e.g. a handle rather than a conventional name). "
        f'Do not substitute a different, more "natural-sounding" name. Write a '
        f'new greeting now using "{name}" verbatim.'
    )


# Prompt-level instructions alone weren't reliable (same pattern as the
# character cap above, which the model missed on ~88% of calls before the
# retry loop was added): QA logs kept showing em dashes in otherwise-good
# replies even after the rule and few-shot examples were cleaned up. Rather
# than burn a retry attempt on punctuation alone, dashes are swapped for a
# comma mechanically here so a good-otherwise reply is never discarded.
_DASH_CHARS = ("—", "–")


def _strip_dashes(text: str) -> str:
    """Replace em/en dashes with a comma and tidy up the punctuation left behind."""
    for ch in _DASH_CHARS:
        text = text.replace(ch, ",")
    # collapse artifacts from the swap, e.g. "Alex, , what's" or "Alex ,"
    while ", ," in text:
        text = text.replace(", ,", ",")
    while ",," in text:
        text = text.replace(",,", ",")
    text = text.replace(" ,", ",")
    return text


def _resolve_timezone_name(profile: Dict[str, str]) -> str:
    """localTimeZone (IANA string) > country (ISO-3166 alpha-2, via pytz) > UTC."""
    tz_name = (profile.get("timezone") or "").strip()
    if tz_name:
        try:
            pytz.timezone(tz_name)  # validate
            return tz_name
        except Exception:  # noqa: BLE001
            logger.warning(
                "greeting_generator.invalid_timezone", extra={"timezone": tz_name}
            )
    country = (profile.get("country") or "").strip().upper()
    if country:
        zones = pytz.country_timezones.get(country)
        if zones:
            return zones[0]
        logger.warning("greeting_generator.unknown_country", extra={"country": country})
    return _DEFAULT_TIMEZONE


def _time_of_day(tz_name: str, now: Optional[datetime] = None) -> str:
    """Bucket the current hour (in the given IANA timezone) into a period."""
    try:
        tz = pytz.timezone(tz_name)
    except Exception:  # noqa: BLE001
        tz = pytz.timezone(_DEFAULT_TIMEZONE)
    dt = (now or datetime.now(timezone.utc)).astimezone(tz)
    h = dt.hour
    if 5 <= h < 12:
        return "morning"
    if 12 <= h < 17:
        return "afternoon"
    if 17 <= h < 21:
        return "evening"
    return "late night"


def _clean_greeting(raw: str) -> str:
    """Strip wrapping quotes/whitespace the model sometimes adds, swap out any
    em/en dashes, and enforce sentence case on the first character (the model
    doesn't always).

    A reply that blows well past _MAX_GREETING_CHARS is treated as malformed
    and discarded (empty return triggers the caller's fallback) rather than
    sliced — truncating mid-word/mid-sentence would read worse on the headline
    than the plain "Good {tod}, {name}" fallback. The dash swap happens before
    that length check so a reply is judged on its post-cleanup length, not
    penalized for punctuation that's about to be removed anyway.
    """
    greeting = (raw or "").strip().strip("\"'` \n\t")
    greeting = _strip_dashes(greeting)
    if len(greeting) > _MAX_GREETING_CHARS:
        logger.warning(
            "greeting_generator.reply_too_long", extra={"length": len(greeting)}
        )
        return ""
    if greeting:
        greeting = greeting[0].upper() + greeting[1:]
    return greeting


def _has_correct_name(greeting: str, name: str) -> bool:
    """True unless a real name was supplied and the model swapped it for a
    different one (observed live: prompt injected "Lvk26" verbatim with an
    explicit "keep it exactly as given, never invent a name" instruction, and
    the model still returned "Pablo, what shall we think through this
    afternoon" — a name that looks more conventional but belongs to no one).
    Skipped when no name was available (the no-name branch is a different,
    already-handled path)."""
    return not name or name in greeting


def _prepare(user_id: str, time_of_day_override: str = "") -> Tuple[str, str, str]:
    """Resolve this user's profile into (system_prompt, fallback_greeting, name).

    Name, language, and local time are all resolved from the `users` collection
    (get_greeting_profile) — `language` isn't in the DB yet so it always falls
    back to "english" for now; `timezone` falls back to the user's `country`
    (ISO code) when `localTimeZone` is absent, then to UTC. `isFirstSession` is
    resolved from the conversation store (has_prior_sessions) rather than the
    profile: the greeting is called before any session_id exists, so "no prior
    session on record" is the only available signal — a Mongo hiccup there
    already degrades to "no prior session" (i.e. isFirstSession=true), the safe
    default since it never falsely implies returning-user history.

    `name` is also returned (not just baked into the prompt) so the caller can
    verify post-hoc that the model actually used it — see _has_correct_name.
    """
    profile = get_greeting_profile(user_id)
    name = (profile.get("name") or "").strip()
    language = (profile.get("language") or "").strip() or _DEFAULT_LANGUAGE
    tz_name = _resolve_timezone_name(profile)
    tod = (time_of_day_override or "").strip() or _time_of_day(tz_name)
    is_first_session = not conversation.has_prior_sessions(user_id)
    fallback = f"Good {tod}, {name}".strip().rstrip(",") if name else f"Good {tod}"

    system_prompt = (
        GREETING_SYSTEM_PROMPT.replace("{userName}", name if name else "{userName}")
        .replace("{language}", language)
        .replace("{Time}", tod)
        .replace("{isFirstSession}", "true" if is_first_session else "false")
    )
    if not name:
        system_prompt += _NO_NAME_OVERRIDE
    return system_prompt, fallback, name


def generate_greeting(
    user_id: str,
    *,
    session_id: str = "",
    time_of_day_override: str = "",
) -> str:
    """Call the LLM to produce a short, varying home-screen greeting for this user.

    Best-effort: on any LLM failure, falls back to a plain static greeting so the
    caller always gets a usable line back. An over-cap reply, or one that drops
    the user's real name in favour of an invented one (see _has_correct_name),
    gets one retry with feedback before falling back — both are single-attempt
    misses, not systemic failures.
    """
    system_prompt, fallback, name = _prepare(user_id, time_of_day_override)
    model = config.GREETING_GENERATION_MODEL
    user_prompt = _BASE_USER_PROMPT
    for _ in range(_MAX_ATTEMPTS):
        try:
            resp = get_client().generate(
                system_prompt=system_prompt,
                user_prompt=user_prompt,
                model=model,
                reasoning_effort=reasoning_effort_for(STAGE_NAME, model),
                stage=STAGE_NAME,
                session_id=session_id,
                user_id=user_id,
            )
        except Exception as exc:  # noqa: BLE001 — greeting generation must never break the caller
            logger.warning(
                "greeting_generator.failed",
                extra={"session_id": session_id, "user_id": user_id, "error": str(exc)},
            )
            return fallback
        cleaned = _clean_greeting(resp.text)
        if cleaned and _has_correct_name(cleaned, name):
            return cleaned
        if cleaned:
            logger.warning(
                "greeting_generator.wrong_name",
                extra={"session_id": session_id, "user_id": user_id, "reply": cleaned},
            )
            user_prompt = _retry_wrong_name_prompt(resp.text, name)
        else:
            user_prompt = _retry_user_prompt(resp.text, len(resp.text or ""))
    return fallback


def generate_greeting_stream(
    user_id: str,
    on_token: Callable[[str], None],
    *,
    session_id: str = "",
    time_of_day_override: str = "",
) -> str:
    """Same as generate_greeting, but streams text deltas via `on_token`.

    Each attempt's tokens are buffered locally (NOT forwarded to `on_token`)
    until the full reply is in and passes both checks (length + correct name)
    — otherwise a since-discarded bad attempt would already be visible to the
    caller before the retry below replaces it. The buffered tokens are flushed
    to `on_token` in one burst once an attempt is accepted, or the static
    fallback is sent as a single token if every attempt fails.
    """
    system_prompt, fallback, name = _prepare(user_id, time_of_day_override)
    model = config.GREETING_GENERATION_MODEL
    user_prompt = _BASE_USER_PROMPT
    for _ in range(_MAX_ATTEMPTS):
        buffered: list[str] = []
        try:
            resp = get_client().generate_stream(
                system_prompt=system_prompt,
                user_prompt=user_prompt,
                model=model,
                on_token=buffered.append,
                reasoning_effort=reasoning_effort_for(STAGE_NAME, model),
                stage=STAGE_NAME,
                session_id=session_id,
                user_id=user_id,
            )
        except Exception as exc:  # noqa: BLE001 — greeting generation must never break the caller
            logger.warning(
                "greeting_generator.stream_failed",
                extra={"session_id": session_id, "user_id": user_id, "error": str(exc)},
            )
            on_token(fallback)
            return fallback
        cleaned = _clean_greeting(resp.text)
        if cleaned and _has_correct_name(cleaned, name):
            for token in buffered:
                on_token(token)
            return cleaned
        if cleaned:
            logger.warning(
                "greeting_generator.wrong_name",
                extra={"session_id": session_id, "user_id": user_id, "reply": cleaned},
            )
            user_prompt = _retry_wrong_name_prompt(resp.text, name)
        else:
            user_prompt = _retry_user_prompt(resp.text, len(resp.text or ""))
    on_token(fallback)
    return fallback
