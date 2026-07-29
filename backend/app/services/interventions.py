"""Intervention engine — suggest something, and say what prompted it.

Adapted from the `workspace/cerebro` sibling build's rule engine. Two things
were taken and one was deliberately left behind:

* Taken — **the rationale**. Every recommendation carries a plain-language
  ``reason`` computed from the user's own data ("2 of your last 5 logged nights
  were rated 2 or lower"), frozen at fire time. The app already nudges; it never
  said what it noticed.
* Taken — **the escalation tier**. Self-support first; a real person only for
  the crisis path.
* Left behind — the reference's DB-backed, admin-editable rule rows keyed off
  ACE/ZER scores from a spec this product has no equivalent of. Rules here are
  **code-defined** over signals we actually hold (mood check-ins, the sleep
  diary, safety events), following the prompt registry's code-default
  precedent. DB-backed overrides can come later if rules need tuning without a
  deploy.

Three constraints this engine must respect, all of them pre-existing product
decisions rather than new inventions:

1. **Consent gates the signals.** Mood rules require `mood_history`, sleep rules
   require `sleep_history`. A user who switched a category off does not get
   recommendations derived from it — the same enforcement every other read site
   applies.
2. **Nothing diagnostic.** Reasons state counts the user themselves logged. No
   rule infers a condition, and no copy implies one.
3. **No pressure, no loss framing.** There is deliberately NO "you haven't
   checked in for N days" rule. Re-engagement guilt is exactly the pattern
   REDESIGN removed when streaks became presence framing, and adding it back
   through a side door would undo that decision.
"""
from __future__ import annotations

import uuid
from collections.abc import Sequence
from dataclasses import dataclass, field
from datetime import date, timedelta

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import utcnow
from app.models.consent import consent_allows
from app.models.intervention import InterventionRecommendation
from app.models.mood import MoodLog
from app.models.safety import SafetyEvent
from app.models.sleep import SleepLog
from app.models.user import User
from app.services.sleep import _minutes_since_noon

SELF_SUPPORT = "self_support"
HUMAN_SUPPORT = "human_support"

# How long before the same rule may fire again. Long on purpose: a suggestion
# that reappears the moment it is dismissed reads as nagging, which is the
# OECD dark-pattern indicator the paywall work already had to avoid.
DEFAULT_COOLDOWN_HOURS = 72


@dataclass(frozen=True)
class Signals:
    """Everything the rules are allowed to look at, already consent-filtered.

    Fields are None (not 0) when the user has switched that category off, so a
    rule can tell "no data" apart from "data says zero" and stay silent rather
    than firing on an absence.
    """

    # Sleep (None when sleep_history consent is off)
    nights_logged: int | None = None
    low_quality_nights: int | None = None
    bedtime_spread_min: int | None = None
    # Mood (None when mood_history consent is off)
    high_intensity_days: int | None = None
    low_mood_days: int | None = None
    # Safety is never consent-gated — it is the escalation path.
    open_crisis: bool = False


@dataclass(frozen=True)
class Offer:
    """What a matched rule proposes."""

    reason: str
    action_kind: str
    action_target: str
    state: dict = field(default_factory=dict)


# ── pure signal helpers (unit-tested directly) ──────────────────────────
def bedtime_spread_minutes(bedtimes: Sequence[int]) -> int:
    """Spread between the earliest and latest bedtime, in minutes.

    Inputs are minutes-since-noon (see `services.sleep._minutes_since_noon`) so
    23:00 and 01:00 stay two hours apart instead of twenty-two. Same anchoring
    the iOS/Android "Your rhythm" cards use — a bedtime range is only meaningful
    if midnight doesn't split it.
    """
    if len(bedtimes) < 2:
        return 0
    return max(bedtimes) - min(bedtimes)


def _plural(n: int, word: str) -> str:
    return f"{n} {word}" if n == 1 else f"{n} {word}s"


def _hm(minutes: int) -> str:
    h, m = divmod(int(minutes), 60)
    if h and m:
        return f"{h}h {m}m"
    return f"{h}h" if h else f"{m}m"


# ── rules, in priority order (first match wins) ─────────────────────────
def _rule_human_support(s: Signals) -> Offer | None:
    """An unresolved crisis flag in the last day. The one rule that offers a
    person rather than an exercise — suggesting a breathing game to someone who
    just wrote something frightening is the wrong answer, and the ≤2-tap crisis
    rule already says a human must be close."""
    if not s.open_crisis:
        return None
    return Offer(
        reason="You flagged something heavy recently. Talking to a person is an option, any time.",
        action_kind="support",
        action_target="human_support",
        state={"open_crisis": True},
    )


def _rule_rough_sleep(s: Signals) -> Offer | None:
    """Several rough nights in a row → the CBT-I program, not a one-off tip."""
    if s.nights_logged is None or s.nights_logged < 3:
        return None
    low = s.low_quality_nights or 0
    if low < 2:
        return None
    return Offer(
        reason=(
            f"{_plural(low, 'night')} of your last {s.nights_logged} logged "
            "were rated 2 or lower."
        ),
        action_kind="program",
        action_target="Sleep Reset",
        state={"nights_logged": s.nights_logged, "low_quality_nights": low},
    )


def _rule_irregular_bedtime(s: Signals) -> Offer | None:
    """Consistency beats duration (the CBT-I stimulus-control principle already
    cited on the Sleep tab), so a wide bedtime spread is worth naming."""
    if s.nights_logged is None or s.nights_logged < 4:
        return None
    spread = s.bedtime_spread_min or 0
    if spread < 90:
        return None
    return Offer(
        reason=(
            f"Your bedtime varied by about {_hm(spread)} across your last "
            f"{s.nights_logged} logged nights."
        ),
        action_kind="widget",
        action_target="sleep_checkin",
        state={"nights_logged": s.nights_logged, "bedtime_spread_min": spread},
    )


def _rule_stress_spike(s: Signals) -> Offer | None:
    if s.high_intensity_days is None or s.high_intensity_days < 2:
        return None
    return Offer(
        reason=(
            f"You logged a high-intensity feeling on {_plural(s.high_intensity_days, 'day')} "
            "in the last three."
        ),
        action_kind="widget",
        action_target="grounding",
        state={"high_intensity_days": s.high_intensity_days},
    )


def _rule_low_mood_run(s: Signals) -> Offer | None:
    if s.low_mood_days is None or s.low_mood_days < 3:
        return None
    return Offer(
        reason=(
            f"You've logged a low feeling on {_plural(s.low_mood_days, 'day')} "
            "in the last week."
        ),
        action_kind="widget",
        action_target="mini_journal",
        state={"low_mood_days": s.low_mood_days},
    )


@dataclass(frozen=True)
class Rule:
    slug: str
    tier: str
    match: object          # Callable[[Signals], Offer | None]
    cooldown_hours: int = DEFAULT_COOLDOWN_HOURS


# Priority is list order: the first rule that matches wins, and lower tiers are
# listed after the escalation rule so a user is never offered a self-help
# exercise when the crisis path is live.
RULES: list[Rule] = [
    Rule("human_support", HUMAN_SUPPORT, _rule_human_support, cooldown_hours=24),
    Rule("rough_sleep", SELF_SUPPORT, _rule_rough_sleep),
    Rule("irregular_bedtime", SELF_SUPPORT, _rule_irregular_bedtime),
    Rule("stress_spike", SELF_SUPPORT, _rule_stress_spike),
    Rule("low_mood_run", SELF_SUPPORT, _rule_low_mood_run),
]


def first_match(signals: Signals) -> tuple[Rule, Offer] | None:
    """Run the rules in priority order. Pure — the whole decision is testable
    without a database."""
    for rule in RULES:
        offer = rule.match(signals)   # type: ignore[operator]
        if offer is not None:
            return rule, offer
    return None


# ── signal gathering (consent-gated) ────────────────────────────────────
async def gather_signals(db: AsyncSession, user: User, *, today: date | None = None) -> Signals:
    today = today or date.today()
    now = utcnow()

    nights_logged = low_nights = spread = None
    if consent_allows(user, "sleep_history"):
        rows = (
            await db.scalars(
                select(SleepLog)
                .where(SleepLog.user_id == user.id, SleepLog.date >= today - timedelta(days=4))
                .order_by(SleepLog.date.asc())
            )
        ).all()
        nights_logged = len(rows)
        low_nights = sum(1 for r in rows if r.quality <= 2)
        spread = bedtime_spread_minutes([_minutes_since_noon(r.bedtime) for r in rows])

    high_days = low_days = None
    if consent_allows(user, "mood_history"):
        recent = (
            await db.scalars(
                select(MoodLog).where(
                    MoodLog.user_id == user.id,
                    MoodLog.created_at >= now - timedelta(days=7),
                )
            )
        ).all()
        # Count DAYS, not entries: five check-ins in one hard afternoon is one
        # day, and shouldn't read as a week-long pattern.
        high_days = len({m.created_at.date() for m in recent
                         if m.intensity >= 4 and m.created_at >= now - timedelta(days=3)})
        low_days = len({m.created_at.date() for m in recent if m.intensity <= 2})

    open_crisis = bool(
        await db.scalar(
            select(SafetyEvent.id).where(
                SafetyEvent.user_id == user.id,
                SafetyEvent.risk_level == "crisis",
                SafetyEvent.resolved.is_(False),
                SafetyEvent.created_at >= now - timedelta(days=1),
            )
        )
    )

    return Signals(
        nights_logged=nights_logged,
        low_quality_nights=low_nights,
        bedtime_spread_min=spread,
        high_intensity_days=high_days,
        low_mood_days=low_days,
        open_crisis=open_crisis,
    )


# ── persistence ─────────────────────────────────────────────────────────
async def open_recommendation(db: AsyncSession, user: User) -> InterventionRecommendation | None:
    """The one offer currently on the table, if any."""
    return await db.scalar(
        select(InterventionRecommendation)
        .where(
            InterventionRecommendation.user_id == user.id,
            InterventionRecommendation.accepted_at.is_(None),
            InterventionRecommendation.dismissed_at.is_(None),
            InterventionRecommendation.completed_at.is_(None),
        )
        .order_by(InterventionRecommendation.created_at.desc())
    )


async def _in_cooldown(db: AsyncSession, user: User, rule: Rule) -> bool:
    cutoff = utcnow() - timedelta(hours=rule.cooldown_hours)
    return bool(
        await db.scalar(
            select(InterventionRecommendation.id).where(
                InterventionRecommendation.user_id == user.id,
                InterventionRecommendation.rule_slug == rule.slug,
                InterventionRecommendation.created_at >= cutoff,
            )
        )
    )


async def evaluate(
    db: AsyncSession, user: User, *, today: date | None = None
) -> InterventionRecommendation | None:
    """Return the user's open offer, creating one if a rule fires.

    At most ONE offer is open at a time: a stack of competing suggestions is
    noise, and the priority order already encodes which matters most.
    """
    existing = await open_recommendation(db, user)
    if existing is not None:
        return existing

    signals = await gather_signals(db, user, today=today)
    matched = first_match(signals)
    if matched is None:
        return None
    rule, offer = matched
    if await _in_cooldown(db, user, rule):
        return None

    rec = InterventionRecommendation(
        user_id=user.id,
        rule_slug=rule.slug,
        tier=rule.tier,
        reason=offer.reason[:300],
        action_kind=offer.action_kind,
        action_target=offer.action_target,
        state_snapshot=offer.state,
    )
    db.add(rec)
    await db.commit()
    await db.refresh(rec)
    return rec


async def resolve(
    db: AsyncSession, user: User, rec_id: uuid.UUID, *, action: str
) -> InterventionRecommendation | None:
    """Mark an offer accepted / dismissed / completed. Returns None if it isn't
    this user's (never leak another account's row through a guessed id)."""
    rec = await db.scalar(
        select(InterventionRecommendation).where(
            InterventionRecommendation.id == rec_id,
            InterventionRecommendation.user_id == user.id,
        )
    )
    if rec is None:
        return None
    stamp = utcnow()
    if action == "accept":
        rec.accepted_at = stamp
    elif action == "dismiss":
        rec.dismissed_at = stamp
    elif action == "complete":
        rec.completed_at = stamp
        if rec.accepted_at is None:
            rec.accepted_at = stamp     # completing implies taking it up
    await db.commit()
    await db.refresh(rec)
    return rec
