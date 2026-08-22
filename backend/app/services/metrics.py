"""First-party, privacy-preserving product metrics (admin analytics).

The "no third-party trackers" promise holds: everything here is aggregate SQL
over our own tables — counts, cohorts, and rates computed server-side. No
events leave the platform and no message/journal content is read
(docs/INVESTOR_READINESS.md gap #1).
"""
from __future__ import annotations

import uuid
from datetime import date, timedelta

from sqlalchemy import func, select, union_all
from sqlalchemy.ext.asyncio import AsyncSession

from app.core import localtime
from app.core.database import utcnow
from app.models.chat import ChatMessage
from app.models.journal import JournalEntry
from app.models.mood import MoodLog
from app.models.plan import Plan, PlanStep
from app.models.product_event import ProductEvent
from app.models.sleep import SleepLog
from app.models.user import User

#: "Active" = wrote any of: mood, journal, sleep entry, or a user chat message.
_WINDOW_DAYS = 35


async def _activity_days(db: AsyncSession) -> dict[uuid.UUID, set[date]]:
    """Distinct (user, day) activity pairs over the retention window."""
    since = utcnow() - timedelta(days=_WINDOW_DAYS)
    selects = [
        select(m.user_id, func.date(m.created_at)).where(m.created_at >= since)
        for m in (MoodLog, JournalEntry, SleepLog)
    ]
    selects.append(
        select(ChatMessage.user_id, func.date(ChatMessage.created_at)).where(
            ChatMessage.created_at >= since, ChatMessage.role == "user"
        )
    )
    rows = (await db.execute(union_all(*selects))).all()
    days: dict[uuid.UUID, set[date]] = {}
    for user_id, day in rows:
        days.setdefault(user_id, set()).add(day)
    return days


async def user_streak(db: AsyncSession, user_id: uuid.UUID, tz: str = "") -> dict:
    """Server mirror of the iOS "mindful days" streak (AppState.currentStreak):
    consecutive active days up to today, forgiving ONE missed day inside the
    run; today itself is optional. Window-capped like the iOS 120-day store.
    Keep the rules in sync with apps/ios CereBroApp.swift (cross-stack contract).

    Days are the USER's local days (register C61): the clients count local
    days, so UTC bucketing disagreed with them by one for any user whose
    evening check-in landed after UTC midnight.
    """
    since = utcnow() - timedelta(days=120)
    zone = str(localtime.tz_for(tz))

    def day_of(col):
        # (created_at AT TIME ZONE zone)::date — the user's calendar day.
        return func.date(func.timezone(zone, col))

    selects = [
        select(day_of(m.created_at)).where(m.user_id == user_id, m.created_at >= since)
        for m in (MoodLog, JournalEntry, SleepLog)
    ]
    selects.append(
        select(day_of(ChatMessage.created_at)).where(
            ChatMessage.user_id == user_id,
            ChatMessage.created_at >= since,
            ChatMessage.role == "user",
        )
    )
    days = {row[0] for row in (await db.execute(union_all(*selects))).all()}

    today = localtime.local_today(tz)
    day = today if today in days else today - timedelta(days=1)
    current, grace_used = 0, False
    while True:
        if day in days:
            current += 1
        elif not grace_used and current > 0:
            grace_used = True
        else:
            break
        day -= timedelta(days=1)

    best = 0
    for d in days:
        if d - timedelta(days=1) in days:
            continue  # not a run start
        length, cursor = 0, d
        while cursor in days:
            length += 1
            cursor += timedelta(days=1)
        best = max(best, length)
    best = max(best, current)

    week = [
        {"date": (today - timedelta(days=offset)).isoformat(),
         "active": (today - timedelta(days=offset)) in days}
        for offset in range(6, -1, -1)
    ]
    return {"current": current, "best": best, "week": week}


#: Below this many people, a "went quiet" rate is noise dressed as a finding.
#: The same instinct as the trends correlation withholding itself under seven
#: nights: a number computed from four users is not a smaller truth, it is a
#: different kind of statement.
_MIN_QUIET_COHORT = 20


def quiet_users(
    activity: dict[uuid.UUID, set[date]],
    today: date,
    *,
    recent: int = 14,
    prior: int = 45,
) -> dict:
    """People who were using CereBro and have stopped — WITHOUT calling it churn.

    WC-16 asks for churn to be observable before money is spent on acquisition,
    and this is the measurement. The name is deliberate, and so is the caveat
    that travels with it in the payload.

    On a subscription tool, a user who stops is a failure by definition. On a
    mental-health companion they may have **got better** — which is the outcome
    the product exists for. Reporting the same number as "churn" would quietly
    make recovery look like loss, and a team that optimises against it ends up
    building the nagging this codebase refuses everywhere else (the dismissible
    upsell, the streak that cannot be broken, the notification that arrives
    whether or not it is true).

    So: a count and a rate, named for the behaviour rather than for a business
    interpretation, with the ambiguity stated in the response rather than in a
    footnote nobody reads. Aggregate only, over activity days already computed
    for retention — no new collection, nothing per-person.
    """
    recent_floor = today - timedelta(days=recent - 1)
    prior_floor = today - timedelta(days=prior - 1)

    was_active = 0
    went_quiet = 0
    for days in activity.values():
        # "Was here before" means active in the earlier stretch of the window,
        # not merely having an account: a signup who never returned is an
        # activation problem, and counting them here would hide that inside a
        # retention one.
        if not any(prior_floor <= d < recent_floor for d in days):
            continue
        was_active += 1
        if not any(d >= recent_floor for d in days):
            went_quiet += 1

    if was_active < _MIN_QUIET_COHORT:
        return {
            "cohort": was_active,
            "quiet": None,
            "rate": None,
            "reason": "not_enough_people",
            "means": _QUIET_MEANS,
        }
    return {
        "cohort": was_active,
        "quiet": went_quiet,
        "rate": round(went_quiet / was_active, 3),
        "reason": None,
        "means": _QUIET_MEANS,
    }


#: Shipped WITH the number, every time, because the number is ambiguous and the
#: ambiguity is load-bearing.
_QUIET_MEANS = (
    "Active in the earlier part of the window and not since. On a wellness "
    "product this is not the same as churn: some of these people are better."
)


async def overview(db: AsyncSession) -> dict:
    now = utcnow()
    today = now.date()
    activity = await _activity_days(db)

    def actives(window: int) -> int:
        floor = today - timedelta(days=window - 1)
        return sum(1 for days in activity.values() if any(d >= floor for d in days))

    # Signups.
    async def signups_since(days: int | None) -> int:
        stmt = select(func.count()).select_from(User)
        if days is not None:
            stmt = stmt.where(User.created_at >= now - timedelta(days=days))
        return (await db.scalar(stmt)) or 0

    # Classic Dn retention: of users old enough to have a day-n, how many were
    # active exactly n days after signup.
    signup_rows = (
        await db.execute(
            select(User.id, func.date(User.created_at)).where(
                User.created_at >= now - timedelta(days=_WINDOW_DAYS)
            )
        )
    ).all()

    def retention(n: int) -> dict:
        cohort = [(uid, d) for uid, d in signup_rows if d <= today - timedelta(days=n)]
        retained = sum(1 for uid, d in cohort if d + timedelta(days=n) in activity.get(uid, set()))
        return {
            "cohort": len(cohort),
            "retained": retained,
            "rate": round(retained / len(cohort), 3) if cohort else None,
        }

    # Engagement volume, trailing 7 days.
    week_ago = now - timedelta(days=7)

    async def count_since(model, *extra) -> int:
        stmt = select(func.count()).select_from(model).where(model.created_at >= week_ago, *extra)
        return (await db.scalar(stmt)) or 0

    steps_done_7d = (
        await db.scalar(
            select(func.count())
            .select_from(PlanStep)
            .join(Plan, Plan.id == PlanStep.plan_id)
            .where(PlanStep.done.is_(True), PlanStep.done_at >= week_ago)
        )
    ) or 0

    # Lifetime activation funnel (distinct users who ever did each thing).
    async def ever(model) -> int:
        return (await db.scalar(select(func.count(func.distinct(model.user_id))))) or 0

    premium = (
        await db.scalar(
            select(func.count()).select_from(User).where(
                User.subscription_tier.in_(["premium", "premium_human"])
            )
        )
    ) or 0

    return {
        "actives": {"dau": actives(1), "wau": actives(7), "mau": actives(30)},
        "signups": {
            "d7": await signups_since(7),
            "d30": await signups_since(30),
            "total": await signups_since(None),
        },
        "retention": {"d1": retention(1), "d7": retention(7), "d30": retention(30)},
        # The other side of retention: who was here and is not now.
        "quiet": quiet_users(activity, today),
        "engagement_7d": {
            "mood_logs": await count_since(MoodLog),
            "journal_entries": await count_since(JournalEntry),
            "chat_messages": await count_since(ChatMessage, ChatMessage.role == "user"),
            "sleep_logs": await count_since(SleepLog),
            "plan_steps_done": steps_done_7d,
        },
        "funnel": {
            "signups": await signups_since(None),
            "mood": await ever(MoodLog),
            "journal": await ever(JournalEntry),
            "sleep": await ever(SleepLog),
            "premium": premium,
        },
    }


#: Canonical onboarding step order (mirrors iOS OnboardingFlow's switch).
ONBOARDING_STEPS = [
    "welcome", "age_gate", "disclosure", "language", "state_check",
    "first_reset", "first_plan", "signup", "consent", "notifications",
]


async def onboarding_funnel(db: AsyncSession, days: int = 30) -> dict:
    """Pre-account onboarding funnel from the anonymous event stream: unique
    installs reaching each step, completions, and paywall interest. Aggregates
    only — the anon ids never leave this function."""
    since = utcnow() - timedelta(days=days)

    async def uniques(name: str, step: str | None = None) -> int:
        q = select(func.count(func.distinct(ProductEvent.anon_id))).where(
            ProductEvent.name == name, ProductEvent.created_at >= since
        )
        if step is not None:
            q = q.where(ProductEvent.step == step)
        return (await db.scalar(q)) or 0

    return {
        "days": days,
        "steps": [
            {"step": s, "installs": await uniques("onboarding_step", s)}
            for s in ONBOARDING_STEPS
        ],
        "completed": await uniques("onboarding_done"),
        "paywall_views": await uniques("paywall_view"),
        "paywall_taps": await uniques("paywall_cta"),
    }
