"""Proactive nudge scheduling.

Creates gentle, context-aware reminders for a user. Real delivery would run a
periodic worker that selects due nudges and calls ``notifications.send_push``;
here we expose the scheduling logic + a dispatch pass that can be triggered
manually or by a cron.
"""
from __future__ import annotations

from datetime import datetime, time, timedelta, timezone
from zoneinfo import ZoneInfo

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import utcnow
from app.models.mood import MoodLog
from app.models.nudge import Nudge
from app.models.sleep import SleepLog
from app.models.user import User
from app.services import email, notifications, webpush


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
    if last is None or last.mood.lower() not in {"anxious", "low", "tired"}:
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


async def dispatch_due(db: AsyncSession) -> int:
    """Send all scheduled nudges whose time has arrived. Returns count sent."""
    now = utcnow()
    due = (
        await db.scalars(
            select(Nudge)
            .where(Nudge.status == "scheduled", Nudge.scheduled_for <= now)
            # Claim rows so concurrent workers/cron passes never double-send.
            .with_for_update(skip_locked=True)
        )
    ).all()
    sent = 0
    for nudge in due:
        user = await db.get(User, nudge.user_id)
        if user is None:
            nudge.status = "failed"
            continue
        if not user.push_token:
            # Web-only users: browser push first (subscriptions registered via
            # /users/me/push-subscriptions), then the email opt-in
            # (users.email_nudges, account-page toggle); otherwise record
            # honestly instead of faking "sent" — the admin safety/ops views
            # can query these.
            if await webpush.send_web_push(db, user, nudge):
                nudge.status = "sent"
                nudge.sent_at = now
                sent += 1
            elif user.email_nudges:
                await email.send_email(user.email, nudge.title, nudge.body)
                nudge.status = "sent"
                nudge.sent_at = now
                sent += 1
            else:
                nudge.status = "skipped"
            continue
        if await notifications.send_push(user, nudge):
            nudge.status = "sent"
            nudge.sent_at = now
            sent += 1
        else:
            nudge.status = "failed"
    await db.commit()
    return sent
