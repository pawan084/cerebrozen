"""Proactive nudge scheduling.

Creates gentle, context-aware reminders for a user. Real delivery would run a
periodic worker that selects due nudges and calls ``notifications.send_push``;
here we expose the scheduling logic + a dispatch pass that can be triggered
manually or by a cron.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, time, timedelta, timezone
from zoneinfo import ZoneInfo

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import utcnow
from app.models.mood import MoodLog
from app.models.nudge import Nudge
from app.models.sleep import SleepLog
from app.models.user import User
from app.services import email, notifications, webpush
from app.services.moods import is_difficult


def _next_at(hour: int, minute: int = 0) -> datetime:
    """Next occurrence of hour:minute in UTC (timezone-aware fields kept simple)."""
    now = utcnow()
    candidate = datetime.combine(now.date(), time(hour, minute, tzinfo=timezone.utc))
    if candidate <= now:
        candidate += timedelta(days=1)
    return candidate


async def schedule_default_nudges(db: AsyncSession, user: User) -> list[Nudge]:
    """Schedule a morning check-in + evening wind-down if not already pending."""
    pending = (
        await db.scalars(
            select(Nudge).where(Nudge.user_id == user.id, Nudge.status == "scheduled")
        )
    ).all()
    have_kinds = {n.kind for n in pending}

    created: list[Nudge] = []
    plan = [
        ("checkin", "A gentle check-in", "How are you arriving today? A 30-second mood note.", "cerebro://mood", 9),
        ("reminder", "Wind-down time", "Ease into the evening with a 3-minute reset.", "cerebro://breathe", 19),
    ]
    for kind, title, body, deeplink, hour in plan:
        if kind in have_kinds:
            continue
        nudge = Nudge(
            user_id=user.id,
            kind=kind,
            title=title,
            body=body,
            deeplink=deeplink,
            scheduled_for=_next_at(hour),
        )
        db.add(nudge)
        created.append(nudge)
    await db.flush()
    return created


async def schedule_contextual(db: AsyncSession, user: User) -> Nudge | None:
    """If the latest mood looks rough, queue a near-term supportive nudge."""
    last = await db.scalar(
        select(MoodLog).where(MoodLog.user_id == user.id).order_by(MoodLog.created_at.desc()).limit(1)
    )
    # One taxonomy (services/moods.py). The literal here used to omit
    # "overwhelmed", so the check-in most likely to want a supportive nudge was
    # the one that never scheduled it.
    if last is None or not is_difficult(last.mood):
        return None
    nudge = Nudge(
        user_id=user.id,
        kind="reset",
        title="A softer landing",
        body="Noticed today felt heavy. Want a 2-minute breathing reset?",
        deeplink="cerebro://breathe",
        scheduled_for=utcnow() + timedelta(hours=2),
    )
    db.add(nudge)
    await db.flush()
    return nudge


async def schedule_wind_down(db: AsyncSession, user: User) -> Nudge | None:
    """Anchor tonight's wind-down reminder ~45 min before the user's own typical
    bedtime (recent diary entries; needs at least two nights for a pattern).
    Times in the diary are wall-clock, so the target converts through the
    user's timezone before landing in the UTC scheduler."""
    bedtimes = (
        await db.scalars(
            select(SleepLog.bedtime)
            .where(SleepLog.user_id == user.id)
            .order_by(SleepLog.date.desc())
            .limit(7)
        )
    ).all()
    if len(bedtimes) < 2:
        return None

    # Noon-anchored average so 23:30 and 00:30 don't average to midday.
    anchored = [((t.hour * 60 + t.minute) - 720) % 1440 for t in bedtimes]
    avg_bed = (round(sum(anchored) / len(anchored)) + 720) % 1440
    target = (avg_bed - 45) % 1440

    try:
        tz = ZoneInfo(user.timezone or "UTC")
    except Exception:
        tz = timezone.utc
    now_local = datetime.now(tz)
    candidate = now_local.replace(hour=target // 60, minute=target % 60, second=0, microsecond=0)
    if candidate <= now_local:
        candidate += timedelta(days=1)
    scheduled_for = candidate.astimezone(timezone.utc)

    body = (
        f"Bed's been around {avg_bed // 60:02d}:{avg_bed % 60:02d} for you lately — "
        "a soft wind-down now sets the night up gently."
    )
    pending = await db.scalar(
        select(Nudge).where(Nudge.user_id == user.id, Nudge.kind == "wind_down", Nudge.status == "scheduled")
    )
    if pending:
        pending.scheduled_for = scheduled_for
        pending.body = body
        await db.flush()
        return pending

    nudge = Nudge(
        user_id=user.id,
        kind="wind_down",
        title="Wind down tonight",
        body=body,
        deeplink="cerebro://sleep",
        scheduled_for=scheduled_for,
    )
    db.add(nudge)
    await db.flush()
    return nudge


@dataclass(frozen=True)
class DispatchOutcome:
    """What one dispatch pass actually did.

    The loop below has always distinguished three endings — delivered, nobody to
    deliver to, and a registered device that refused — and wrote each one to
    ``Nudge.status`` honestly. It then returned only ``sent``, so the admin
    dashboard, whose Nudges tab promises "honest sent/skipped/failed outcomes",
    had two thirds of that sentence unavailable to it (register E58).

    ``skipped`` is not a failure and the two must not be added together: it means
    the person has no push token, no browser subscription and no email opt-in —
    an ops question about reach, not about delivery. ``failed`` means a device we
    hold a token for would not take it, which is the one an operator should chase.

    ``expired`` is the fourth, added with the retry work: the nudge was never
    delivered because it had become too LATE to mean anything. It is separated
    from ``skipped`` deliberately — "we were down" and "this person has no
    device" are different operational stories, and collapsing them would hide
    an outage inside a reach statistic.

    ``deferred`` counts the ones left for another pass: a transient failure that
    has not used up its attempts. They are still ``scheduled``, so they are not
    an ending at all, which is why they are excluded from ``considered``.
    """

    sent: int = 0
    skipped: int = 0
    failed: int = 0
    expired: int = 0
    deferred: int = 0

    @property
    def considered(self) -> int:
        """Nudges that reached an ENDING this pass. A deferred one has not."""
        return self.sent + self.skipped + self.failed + self.expired


#: How late a time-anchored nudge may be and still be worth sending.
#:
#: These are anchored to a time of day — a 09:00 check-in and a 19:00 wind-down
#: (see ``schedule_default_nudges``). "Ease into the evening" delivered at 11am
#: the next morning is not a late reminder, it is a wrong one, and a wellness
#: app that pushes wrong things is a notification people turn off. Two hours is
#: a judgement, not a measurement: long enough to survive a deploy or a restart,
#: short enough that the message still matches the hour it describes.
MAX_LATENESS = timedelta(hours=2)

#: Kinds delivered however late they are. A safety follow-up is worth having
#: at the wrong hour; a wind-down reminder is not.
ALWAYS_DELIVER = frozenset({"safety"})

#: Attempts before a delivery failure becomes final. Deliveries fail for
#: reasons that pass (an FCM blip, a mail host refusing a connection) and for
#: reasons that do not (a token the device revoked), and nothing in the response
#: reliably separates them — so it retries a bounded number of times and then
#: stops, rather than either giving up at the first blip or retrying forever.
MAX_ATTEMPTS = 3

#: Backoff between attempts. Deliberately minutes rather than seconds: the
#: dispatcher itself only ticks every few minutes, so anything shorter would
#: just mean "next tick" while pretending to be a schedule.
RETRY_BACKOFF = (timedelta(minutes=10), timedelta(minutes=30))


def _retry_delay(attempts: int) -> timedelta:
    """Backoff for the attempt about to be scheduled (1-indexed)."""
    index = min(max(attempts, 1), len(RETRY_BACKOFF)) - 1
    return RETRY_BACKOFF[index]


def too_late(nudge: Nudge, now: datetime) -> bool:
    """Whether this nudge has missed the point of its own timing."""
    if nudge.kind in ALWAYS_DELIVER:
        return False
    return (now - nudge.scheduled_for) > MAX_LATENESS


async def dispatch_due(db: AsyncSession) -> DispatchOutcome:
    """Send all scheduled nudges whose time has arrived.

    Returns the full :class:`DispatchOutcome`; callers that only care about
    deliveries read ``.sent``.
    """
    now = utcnow()
    due = (
        await db.scalars(
            select(Nudge)
            .where(
                Nudge.status == "scheduled",
                # A retry moves `next_attempt_at`, never `scheduled_for` — so
                # due-ness reads whichever is in play while lateness keeps
                # measuring against the time this was meant to arrive.
                func.coalesce(Nudge.next_attempt_at, Nudge.scheduled_for) <= now,
            )
            # Claim rows so concurrent workers/cron passes never double-send.
            .with_for_update(skip_locked=True)
        )
    ).all()
    for nudge in due:
        # Before anything else: is this still worth sending? Checked here and
        # not in the query so the row is claimed and given an honest ending,
        # rather than being left `scheduled` and reconsidered every tick for
        # the rest of its life.
        if too_late(nudge, now):
            nudge.status = "expired"
            continue
        user = await db.get(User, nudge.user_id)
        if user is None:
            nudge.status = "failed"
            continue
        # Native installs first (every registered iOS/Android device, plus the
        # legacy single push_token column), then the browser, then email.
        if await notifications.deliver(db, user, nudge):
            nudge.status = "sent"
            nudge.sent_at = now
            continue
        if not user.push_token:
            # No native install took it. Browser push next (subscriptions
            # registered via /users/me/push-subscriptions), then the email
            # opt-in (users.email_nudges, account-page toggle); otherwise
            # record honestly instead of faking "sent" — the admin safety/ops
            # views can query these.
            if await webpush.send_web_push(db, user, nudge):
                nudge.status = "sent"
                nudge.sent_at = now
            elif user.email_nudges:
                await email.send_email(user.email, nudge.title, nudge.body)
                nudge.status = "sent"
                nudge.sent_at = now
            else:
                nudge.status = "skipped"
            continue
        # A registered token that would not take it: a real delivery failure,
        # not a routing question. `deliver` already tried it — but one refusal
        # is not proof the device is gone, and treating it as proof is how a
        # single FCM blip used to lose a nudge permanently.
        nudge.attempts += 1
        if nudge.attempts < MAX_ATTEMPTS:
            nudge.next_attempt_at = now + _retry_delay(nudge.attempts)
            # Still `scheduled`: this pass reached no ending for it.
            continue
        nudge.status = "failed"
    # Tallied from the statuses rather than counted in the branches above: every
    # nudge leaves this loop with exactly one terminal status, so deriving the
    # numbers from the rows makes it impossible for the report to disagree with
    # the record an operator would go and read.
    outcome = DispatchOutcome(
        sent=sum(1 for n in due if n.status == "sent"),
        skipped=sum(1 for n in due if n.status == "skipped"),
        failed=sum(1 for n in due if n.status == "failed"),
        expired=sum(1 for n in due if n.status == "expired"),
        # Still scheduled after being looked at = left for another pass.
        deferred=sum(1 for n in due if n.status == "scheduled"),
    )
    await db.commit()
    return outcome
