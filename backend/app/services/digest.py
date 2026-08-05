"""Weekly digest — delivering the insight that was already being computed.

`services/insights.compute_weekly` has always produced a real weekly summary,
but nothing ever *sent* it: the dispatcher only ever carried contextual and
bedtime nudges, so the summary existed only if a user happened to open the
Insights screen.

This module closes that loop, and in doing so finally uses the `Insight` model,
which was declared, exported and wiped but never written by anything. One row
per ISO week per user is the snapshot; the nudge is the delivery.

Everything degrades: with no push keys, no VAPID pair and no SMTP the digest
still snapshots (so `/insights/digest` works in-app) and the dispatcher records
an honest `skipped` rather than pretending to send.
"""
from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import utcnow
from app.models.insight import Insight
from app.models.nudge import Nudge
from app.models.user import User
from app.services import insights as insights_service

logger = logging.getLogger("cerebro.digest")

# Monday 09:00 in the user's own timezone: a week's summary lands at the start
# of the next week, not at the end of a Sunday night.
DIGEST_WEEKDAY = 0
DIGEST_HOUR = 9


def week_key(moment: datetime) -> str:
    """ISO year-week, e.g. `2026-W31`. One digest per user per week."""
    iso = moment.isocalendar()
    return f"{iso.year}-W{iso.week:02d}"


async def _already_snapshotted(db: AsyncSession, user: User, key: str) -> Insight | None:
    return await db.scalar(
        select(Insight).where(Insight.user_id == user.id, Insight.period == key)
    )


async def snapshot_week(db: AsyncSession, user: User, *, now: datetime | None = None) -> Insight:
    """Freeze this week's computed insight as a row.

    A snapshot is what makes history meaningful — recomputing later would
    silently rewrite what a user was told last week as their data changed.
    Idempotent per ISO week.
    """
    now = now or utcnow()
    key = week_key(now)
    existing = await _already_snapshotted(db, user, key)
    if existing is not None:
        return existing

    computed = await insights_service.compute_weekly(db, user)
    row = Insight(
        user_id=user.id,
        period=key,
        headline=computed["headline"],
        summary=computed["summary"],
        metrics=computed["metrics"],
    )
    db.add(row)
    try:
        await db.flush()
    except IntegrityError:
        # Another worker snapshotted this week between our check and flush
        # (register C53) — theirs is the snapshot; return it.
        await db.rollback()
        existing = await _already_snapshotted(db, user, key)
        if existing is not None:
            return existing
        raise
    return row


def _next_digest_time(user: User, now: datetime) -> datetime:
    """Next Monday 09:00 in the user's timezone, as UTC."""
    try:
        from zoneinfo import ZoneInfo

        tz = ZoneInfo(user.timezone or "UTC")
    except Exception:
        tz = timezone.utc
    local = now.astimezone(tz)
    ahead = (DIGEST_WEEKDAY - local.weekday()) % 7
    candidate = (local + timedelta(days=ahead)).replace(
        hour=DIGEST_HOUR, minute=0, second=0, microsecond=0
    )
    if candidate <= local:
        candidate += timedelta(days=7)
    return candidate.astimezone(timezone.utc)


async def schedule_digest(db: AsyncSession, user: User, *, now: datetime | None = None) -> Nudge | None:
    """Snapshot the week and queue its delivery.

    Returns None when there is nothing worth sending — a user with no activity
    gets silence rather than a summary of nothing, which is the difference
    between a digest and a re-engagement ping.
    """
    now = now or utcnow()
    snapshot = await snapshot_week(db, user, now=now)

    # "A fresh start" is compute_weekly's empty-week headline. Sending it would
    # be nagging someone about a week they chose not to use the app.
    if snapshot.headline == "A fresh start":
        return None

    pending = await db.scalar(
        select(Nudge).where(
            Nudge.user_id == user.id,
            Nudge.kind == "weekly_digest",
            Nudge.status == "scheduled",
        )
    )
    body = snapshot.summary
    scheduled_for = _next_digest_time(user, now)
    if pending is not None:
        pending.body = body
        pending.scheduled_for = scheduled_for
        await db.flush()
        return pending

    nudge = Nudge(
        user_id=user.id,
        kind="weekly_digest",
        title=f"Your week: {snapshot.headline.lower()}",
        body=body,
        deeplink="cerebro://insights",
        scheduled_for=scheduled_for,
    )
    db.add(nudge)
    await db.flush()
    return nudge


async def run_weekly_pass(db: AsyncSession, *, now: datetime | None = None) -> int:
    """Snapshot + schedule for every active user. Returns how many were queued.

    Consent is inherited rather than re-implemented: `compute_weekly` already
    gates each data category on its own flag, so a user who switched
    `mood_history` off gets a digest computed without it.
    """
    now = now or utcnow()
    users = (await db.scalars(select(User).where(User.is_active.is_(True)))).all()
    queued = 0
    for user in users:
        try:
            if await schedule_digest(db, user, now=now) is not None:
                queued += 1
        except Exception:  # noqa: BLE001 - one user must not break the pass
            logger.exception("Weekly digest failed for user %s", user.id)
    await db.commit()
    return queued
