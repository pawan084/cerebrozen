"""Two dispatchers, one nudge, one delivery (WC-24).

`_nudge_dispatcher` in `main.py` carries a load-bearing claim in its docstring:

    Safe with multiple workers: dispatch_due claims due rows with
    FOR UPDATE SKIP LOCKED, so each nudge is sent exactly once.

Nothing tested it. The claim is the thing standing between the current
in-process loop and running more than one API instance, and if it is wrong the
symptom is a wellness app sending the same "time to check in" twice — spam, from
the product whose whole posture is that it does not nag.

These run two real dispatchers against one real Postgres row, concurrently, and
count the deliveries. They need the live database the rest of the suite already
uses; there is no way to test a row lock against a mock.
"""

import asyncio
import uuid
from datetime import timedelta

import pytest

from app.core.database import SessionLocal, utcnow
from app.core.security import hash_password
from app.models.nudge import Nudge
from app.models.user import User
from app.services import email as email_service
from app.services import notifications, nudges


async def _user_who_takes_email_nudges(session) -> User:
    """The simplest delivery path with an observable side effect.

    Email is last in the chain (native push, then browser, then email), so a
    user with no push token and `email_nudges` on lands there deterministically
    — and `send_email` is one function to count.
    """
    user = User(
        email=f"conc-{uuid.uuid4().hex[:10]}@test.app",
        hashed_password=hash_password("x"),
        name="Conc",
        email_nudges=True,
    )
    session.add(user)
    await session.flush()
    return user


async def _due_nudge(session, user) -> uuid.UUID:
    nudge = Nudge(
        user_id=user.id,
        kind="checkin",
        title="time to check in",
        body="b",
        deeplink="cerebro://x",
        scheduled_for=utcnow() - timedelta(minutes=5),
    )
    session.add(nudge)
    await session.commit()
    return nudge.id


@pytest.mark.asyncio
async def test_two_workers_send_one_nudge_once(monkeypatch):
    sent: list[str] = []

    async def counting_send(to: str, subject: str, body: str) -> bool:
        # A real delivery takes time, and the lock must be held across it —
        # this is where a naive "read the rows, then send" implementation lets
        # the second worker in.
        await asyncio.sleep(0.05)
        sent.append(subject)
        return True

    monkeypatch.setattr(email_service, "send_email", counting_send)

    async with SessionLocal() as s:
        user = await _user_who_takes_email_nudges(s)
        await s.commit()
        nudge_id = await _due_nudge(s, user)

    async def worker():
        async with SessionLocal() as s:
            return await nudges.dispatch_due(s)

    # Two independent sessions, genuinely overlapping.
    first, second = await asyncio.wait_for(
        asyncio.gather(worker(), worker()), timeout=30
    )

    assert len(sent) == 1, f"the nudge went out {len(sent)} times"
    # Exactly one worker claimed it; the other found nothing and said so
    # honestly rather than reporting a phantom pass.
    assert {first.considered, second.considered} == {1, 0}

    async with SessionLocal() as s:
        row = await s.get(Nudge, nudge_id)
        assert row.status == "sent"
        assert row.sent_at is not None


@pytest.mark.asyncio
async def test_the_loser_does_not_block_waiting_for_the_winner(monkeypatch):
    """SKIP LOCKED, not FOR UPDATE.

    Without `skip_locked` the second worker would BLOCK until the first
    committed — every instance serialising behind the slowest delivery, which
    on a bad SMTP day is the whole dispatch interval. It should find nothing
    and leave immediately.
    """

    async def slow_send(to: str, subject: str, body: str) -> bool:
        await asyncio.sleep(0.6)
        return True

    monkeypatch.setattr(email_service, "send_email", slow_send)

    async with SessionLocal() as s:
        user = await _user_who_takes_email_nudges(s)
        await s.commit()
        await _due_nudge(s, user)

    async def timed_worker():
        started = asyncio.get_running_loop().time()
        async with SessionLocal() as s:
            outcome = await nudges.dispatch_due(s)
        return outcome, asyncio.get_running_loop().time() - started

    (out_a, took_a), (out_b, took_b) = await asyncio.wait_for(
        asyncio.gather(timed_worker(), timed_worker()), timeout=30
    )

    winner_took = took_a if out_a.considered else took_b
    loser_took = took_b if out_a.considered else took_a
    assert winner_took >= 0.5, "the winner should have waited on the delivery"
    assert loser_took < winner_took, (
        "the second worker blocked on the row lock instead of skipping it — "
        f"loser {loser_took:.2f}s vs winner {winner_took:.2f}s"
    )


@pytest.mark.asyncio
async def test_a_second_pass_after_the_first_finishes_sends_nothing(monkeypatch):
    """The sequential case the interval actually produces.

    Two instances rarely tick at the same millisecond; far more often one
    finishes and the next starts. A nudge already marked `sent` must not be due
    again — the status filter, not the lock, is what stops that one.
    """
    sent: list[str] = []

    async def counting_send(to: str, subject: str, body: str) -> bool:
        sent.append(subject)
        return True

    monkeypatch.setattr(email_service, "send_email", counting_send)

    async with SessionLocal() as s:
        user = await _user_who_takes_email_nudges(s)
        await s.commit()
        await _due_nudge(s, user)

    async with SessionLocal() as s:
        first = await nudges.dispatch_due(s)
    async with SessionLocal() as s:
        second = await nudges.dispatch_due(s)

    assert first.sent == 1
    assert second.considered == 0
    assert len(sent) == 1


# ── Durability: the other half of WC-24 ─────────────────────────────────
#
# The concurrency proof above says a nudge is never sent twice. These say it is
# not silently lost either — by one blip, or by arriving so late it means the
# wrong thing.


async def _nudge(session, user, *, kind="reminder", minutes_late=5) -> uuid.UUID:
    nudge = Nudge(
        user_id=user.id,
        kind=kind,
        title="Wind-down time",
        body="Ease into the evening.",
        deeplink="cerebro://breathe",
        scheduled_for=utcnow() - timedelta(minutes=minutes_late),
    )
    session.add(nudge)
    await session.commit()
    return nudge.id


@pytest.mark.asyncio
async def test_a_transient_failure_is_retried_not_buried(monkeypatch):
    """One refused delivery is not proof the device is gone.

    Before this, a single FCM blip marked the nudge `failed` forever and the
    person simply never got it.
    """
    async def refuse(db, user, nudge) -> bool:
        return False

    monkeypatch.setattr(notifications, "deliver", refuse)

    async with SessionLocal() as s:
        user = User(
            email=f"retry-{uuid.uuid4().hex[:10]}@test.app",
            hashed_password=hash_password("x"),
            name="R",
            push_token="a-registered-device",
        )
        s.add(user)
        await s.flush()
        await s.commit()
        nudge_id = await _nudge(s, user)

    async with SessionLocal() as s:
        outcome = await nudges.dispatch_due(s)

    assert outcome.deferred == 1
    assert outcome.failed == 0
    assert outcome.considered == 0, "a deferred nudge has not reached an ending"

    async with SessionLocal() as s:
        row = await s.get(Nudge, nudge_id)
        assert row.status == "scheduled", "it must stay in the queue"
        assert row.attempts == 1
        assert row.next_attempt_at is not None
        assert row.next_attempt_at > utcnow(), "and not be retried immediately"


@pytest.mark.asyncio
async def test_a_retry_does_not_move_the_time_it_was_meant_to_arrive(monkeypatch):
    """`scheduled_for` is the intent; `next_attempt_at` is the schedule.

    If a retry moved `scheduled_for`, lateness would reset every attempt and a
    nudge could crawl forward all day, arriving hours wrong but never expiring.
    """
    monkeypatch.setattr(notifications, "deliver", lambda db, user, nudge: _false())

    async with SessionLocal() as s:
        user = User(
            email=f"intent-{uuid.uuid4().hex[:10]}@test.app",
            hashed_password=hash_password("x"),
            name="I",
            push_token="a-registered-device",
        )
        s.add(user)
        await s.flush()
        await s.commit()
        nudge_id = await _nudge(s, user)
        original = (await s.get(Nudge, nudge_id)).scheduled_for

    async with SessionLocal() as s:
        await nudges.dispatch_due(s)

    async with SessionLocal() as s:
        row = await s.get(Nudge, nudge_id)
        assert row.scheduled_for == original


async def _false() -> bool:
    return False


@pytest.mark.asyncio
async def test_retries_are_bounded_and_then_it_is_honestly_failed(monkeypatch):
    """Retry forever and the queue fills with nudges nobody can receive."""
    monkeypatch.setattr(notifications, "deliver", lambda db, user, nudge: _false())

    async with SessionLocal() as s:
        user = User(
            email=f"bound-{uuid.uuid4().hex[:10]}@test.app",
            hashed_password=hash_password("x"),
            name="B",
            push_token="a-registered-device",
        )
        s.add(user)
        await s.flush()
        await s.commit()
        nudge_id = await _nudge(s, user, kind="safety")   # never expires

    for attempt in range(nudges.MAX_ATTEMPTS):
        async with SessionLocal() as s:
            row = await s.get(Nudge, nudge_id)
            row.next_attempt_at = utcnow() - timedelta(seconds=1)   # its turn again
            await s.commit()
        async with SessionLocal() as s:
            outcome = await nudges.dispatch_due(s)

    assert outcome.failed == 1, "the last attempt is an ending, not another retry"

    async with SessionLocal() as s:
        row = await s.get(Nudge, nudge_id)
        assert row.status == "failed"
        assert row.attempts == nudges.MAX_ATTEMPTS


@pytest.mark.asyncio
async def test_a_nudge_too_late_to_mean_anything_expires(monkeypatch):
    """"Ease into the evening" at 11am is not a late reminder, it is a wrong one."""
    sent: list[str] = []

    async def deliver(db, user, nudge) -> bool:
        sent.append(nudge.title)
        return True

    monkeypatch.setattr(notifications, "deliver", deliver)

    async with SessionLocal() as s:
        user = User(
            email=f"stale-{uuid.uuid4().hex[:10]}@test.app",
            hashed_password=hash_password("x"),
            name="S",
            push_token="a-registered-device",
        )
        s.add(user)
        await s.flush()
        await s.commit()
        late = int(nudges.MAX_LATENESS.total_seconds() // 60) + 30
        nudge_id = await _nudge(s, user, minutes_late=late)

    async with SessionLocal() as s:
        outcome = await nudges.dispatch_due(s)

    assert sent == [], "nothing should have been delivered"
    assert outcome.expired == 1
    assert outcome.sent == 0

    async with SessionLocal() as s:
        row = await s.get(Nudge, nudge_id)
        # An ending, not a nudge left to be reconsidered on every future tick.
        assert row.status == "expired"


@pytest.mark.asyncio
async def test_expired_is_its_own_ending_not_skipped(monkeypatch):
    """"We were down" and "this person has no device" are different stories."""
    async def deliver(db, user, nudge) -> bool:
        return True

    monkeypatch.setattr(notifications, "deliver", deliver)

    async with SessionLocal() as s:
        user = User(
            email=f"sep-{uuid.uuid4().hex[:10]}@test.app",
            hashed_password=hash_password("x"),
            name="Sep",
            push_token="a-registered-device",
        )
        s.add(user)
        await s.flush()
        await s.commit()
        late = int(nudges.MAX_LATENESS.total_seconds() // 60) + 30
        await _nudge(s, user, minutes_late=late)

    async with SessionLocal() as s:
        outcome = await nudges.dispatch_due(s)

    assert outcome.expired == 1
    assert outcome.skipped == 0
    assert outcome.failed == 0


@pytest.mark.asyncio
async def test_a_safety_nudge_is_delivered_however_late(monkeypatch):
    """The exception, and the reason there is a list rather than one rule."""
    sent: list[str] = []

    async def deliver(db, user, nudge) -> bool:
        sent.append(nudge.kind)
        return True

    monkeypatch.setattr(notifications, "deliver", deliver)

    async with SessionLocal() as s:
        user = User(
            email=f"safety-{uuid.uuid4().hex[:10]}@test.app",
            hashed_password=hash_password("x"),
            name="Safe",
            push_token="a-registered-device",
        )
        s.add(user)
        await s.flush()
        await s.commit()
        await _nudge(s, user, kind="safety", minutes_late=60 * 24)

    async with SessionLocal() as s:
        outcome = await nudges.dispatch_due(s)

    assert sent == ["safety"]
    assert outcome.sent == 1
    assert outcome.expired == 0


@pytest.mark.asyncio
async def test_a_nudge_only_a_little_late_still_goes(monkeypatch):
    """The window has to be usable: a deploy or a restart must not lose nudges."""
    sent: list[str] = []

    async def deliver(db, user, nudge) -> bool:
        sent.append(nudge.kind)
        return True

    monkeypatch.setattr(notifications, "deliver", deliver)

    async with SessionLocal() as s:
        user = User(
            email=f"ontime-{uuid.uuid4().hex[:10]}@test.app",
            hashed_password=hash_password("x"),
            name="On",
            push_token="a-registered-device",
        )
        s.add(user)
        await s.flush()
        await s.commit()
        await _nudge(s, user, minutes_late=int(nudges.MAX_LATENESS.total_seconds() // 60) - 10)

    async with SessionLocal() as s:
        outcome = await nudges.dispatch_due(s)

    assert outcome.sent == 1
    assert sent == ["reminder"]


@pytest.mark.asyncio
async def test_a_deferred_nudge_is_not_due_again_until_its_backoff_passes(monkeypatch):
    """Otherwise "retry" means "hammer it on every tick"."""
    attempts: list[int] = []

    async def refuse(db, user, nudge) -> bool:
        attempts.append(1)
        return False

    monkeypatch.setattr(notifications, "deliver", refuse)

    async with SessionLocal() as s:
        user = User(
            email=f"backoff-{uuid.uuid4().hex[:10]}@test.app",
            hashed_password=hash_password("x"),
            name="Back",
            push_token="a-registered-device",
        )
        s.add(user)
        await s.flush()
        await s.commit()
        await _nudge(s, user, kind="safety")

    async with SessionLocal() as s:
        await nudges.dispatch_due(s)
    async with SessionLocal() as s:
        second = await nudges.dispatch_due(s)

    assert len(attempts) == 1, "the second pass must not have tried again"
    assert second.considered == 0
    assert second.deferred == 0, "it is not even due, so it is not deferred either"
