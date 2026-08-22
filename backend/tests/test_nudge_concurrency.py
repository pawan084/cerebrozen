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
from app.services import nudges


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
