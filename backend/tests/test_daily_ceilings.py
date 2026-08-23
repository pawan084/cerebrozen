"""Per-account daily ceilings on the calls that cost money (WC-89 follow-on).

The per-minute limits in `core/ratelimit.py` bound a burst, not a day, and the
gap is not small: plan generation at 10/minute allows 14,400 calls per account
per day, TTS at 60/minute allows 86,400. An account can sit at the per-minute
limit forever without ever tripping it, because it refills every minute. Until
now `services/usage.py` was the only daily cap in the product and it covers chat
alone.

Two things are worth testing rather than assuming, and they are the two the
implementation was shaped around:

* **The increment is atomic.** A read-then-write ceiling holds under sequential
  load and fails under exactly the traffic an abuser produces. The last class
  fires concurrent requests at one account and counts what got through.
* **It is not a plan feature.** The numbers are identical for free and paid on
  purpose. Making them differ would be a pricing decision, and CLAIMS_MAP has a
  banned phrase sitting there for implying tier gates the backend does not have.
"""

from __future__ import annotations

import asyncio
import uuid

import pytest
from fastapi import HTTPException
from sqlalchemy import select, text, update

from app.core.database import SessionLocal, utcnow
from app.models.daily_usage import DailyUsage
from app.models.user import User
from app.services import usage


async def _user(prefix: str = "meter") -> User:
    async with SessionLocal() as db:
        u = User(
            email=f"{prefix}-{uuid.uuid4().hex[:10]}@test.app",
            hashed_password="x",
            name="Meter",
        )
        db.add(u)
        await db.commit()
        await db.refresh(u)
        return u


async def _count(user_id, feature: str) -> int:
    async with SessionLocal() as db:
        row = await db.scalar(
            select(DailyUsage.count).where(
                DailyUsage.user_id == user_id,
                DailyUsage.feature == feature,
                DailyUsage.day == utcnow().date(),
            )
        )
        return int(row or 0)


class TestTheCounter:
    @pytest.mark.asyncio
    async def test_each_call_moves_it_by_one(self):
        user = await _user()
        async with SessionLocal() as db:
            assert await usage.consume(db, user, "plan_generate") == 1
            assert await usage.consume(db, user, "plan_generate") == 2
        assert await _count(user.id, "plan_generate") == 2

    @pytest.mark.asyncio
    async def test_features_are_counted_separately(self):
        """One ceiling per feature, so a busy voice day cannot exhaust planning."""
        user = await _user()
        async with SessionLocal() as db:
            await usage.consume(db, user, "voice_tts")
            await usage.consume(db, user, "voice_tts")
            assert await usage.consume(db, user, "plan_generate") == 1

    @pytest.mark.asyncio
    async def test_accounts_are_counted_separately(self):
        first, second = await _user("a"), await _user("b")
        async with SessionLocal() as db:
            await usage.consume(db, first, "plan_generate")
            assert await usage.consume(db, second, "plan_generate") == 1

    @pytest.mark.asyncio
    async def test_an_unknown_feature_is_a_programming_error_not_a_free_pass(self):
        """A typo'd feature key must not silently mean 'unmetered'.

        The failure mode this avoids is quiet: `CEILINGS.get(...)` returning
        None and the call sailing through unbounded, on the endpoint somebody
        was in the middle of protecting.
        """
        user = await _user()
        async with SessionLocal() as db:
            with pytest.raises(ValueError):
                await usage.consume(db, user, "voice_ttss")


class TestTheCeiling:
    @pytest.mark.asyncio
    async def test_the_call_that_crosses_it_is_refused(self, monkeypatch):
        monkeypatch.setitem(usage.CEILINGS, "plan_generate", 3)
        user = await _user()
        async with SessionLocal() as db:
            for _ in range(3):
                await usage.consume(db, user, "plan_generate")
            with pytest.raises(HTTPException) as info:
                await usage.consume(db, user, "plan_generate")
        assert info.value.status_code == 429
        assert info.value.detail["code"] == usage.DAILY_CEILING_CODE
        assert info.value.detail["feature"] == "plan_generate"
        assert info.value.detail["limit"] == 3

    @pytest.mark.asyncio
    async def test_it_says_come_back_tomorrow_not_upgrade(self):
        """Distinguishable from the free-tier chat cap, which means the opposite.

        `FREE_LIMIT_CODE` means "upgrade and this goes away". This one means
        "come back tomorrow" — the ceiling is the same on every tier — and
        showing an upgrade prompt for it would be selling a fix that is not for
        sale.
        """
        assert usage.DAILY_CEILING_CODE != usage.FREE_LIMIT_CODE

    @pytest.mark.asyncio
    async def test_a_refused_call_still_counts(self, monkeypatch):
        """Otherwise hammering a refused endpoint keeps the counter low enough
        to slip back under the ceiling."""
        monkeypatch.setitem(usage.CEILINGS, "plan_generate", 1)
        user = await _user()
        async with SessionLocal() as db:
            await usage.consume(db, user, "plan_generate")
            for _ in range(3):
                with pytest.raises(HTTPException):
                    await usage.consume(db, user, "plan_generate")
        assert await _count(user.id, "plan_generate") == 4

    @pytest.mark.asyncio
    async def test_yesterdays_calls_do_not_count_against_today(self):
        user = await _user()
        async with SessionLocal() as db:
            await usage.consume(db, user, "plan_generate")
            # Age the row by a day rather than waiting for one.
            await db.execute(
                text("UPDATE daily_usage SET day = day - INTERVAL '1 day' "
                     "WHERE user_id = :uid"),
                {"uid": user.id},
            )
            await db.commit()
            assert await usage.consume(db, user, "plan_generate") == 1


class TestItIsNotAPlanFeature:
    """The ceilings are identical on every tier, deliberately.

    Making them differ would make Premium materially better at voice and
    planning — a pricing decision, not an engineering one — and CLAIMS_MAP bans
    "Pricing 'Premium' beside anything the backend does not gate on tier"
    precisely because an implied gate is the most expensive kind of false claim.
    """

    @pytest.mark.asyncio
    async def test_a_paid_account_gets_the_same_ceiling(self, monkeypatch):
        monkeypatch.setitem(usage.CEILINGS, "plan_generate", 2)
        paid = await _user("paid")
        async with SessionLocal() as db:
            await db.execute(
                update(User).where(User.id == paid.id).values(subscription_tier="premium")
            )
            await db.commit()
            fresh = await db.get(User, paid.id)
            for _ in range(2):
                await usage.consume(db, fresh, "plan_generate")
            with pytest.raises(HTTPException) as info:
                await usage.consume(db, fresh, "plan_generate")
        assert info.value.detail["limit"] == 2, (
            "a paid account met a different ceiling — that is a pricing change"
        )

    def test_the_shipped_numbers_are_far_above_a_real_day(self):
        """Sanity on the constants themselves, so a later edit that turns an
        abuse ceiling into a product limit has to argue with a test.

        Every one of these is at least five times a heavy day of genuine use.
        If one ever needs to come down near real usage, it has stopped being an
        abuse ceiling and become a plan feature, which is a different
        conversation and a different file.
        """
        floors = {
            "voice_tts": 1000,       # sentence-by-sentence; a heavy day is a few hundred
            "voice_stt": 300,        # one per voice turn
            "plan_generate": 50,     # a deliberate act; a heavy day is ten
            "goal_decompose": 50,
            "assessment_topics": 100,
            "oracle_turn": 200,
        }
        assert set(floors) == set(usage.CEILINGS), "a metered feature is missing a floor"
        for feature, floor in floors.items():
            assert usage.CEILINGS[feature] >= floor, (
                f"{feature} at {usage.CEILINGS[feature]} is close enough to real usage "
                "that genuine users will meet it"
            )

    def test_every_ceiling_is_below_what_the_minute_limits_allow(self):
        """The whole point: a day must be bounded more tightly than 1440 minutes
        of the per-minute limit, or the ceiling changes nothing."""
        per_minute = {
            "voice_tts": 60, "voice_stt": 20, "plan_generate": 10,
            "goal_decompose": 10, "assessment_topics": 20, "oracle_turn": 30,
        }
        for feature, rpm in per_minute.items():
            unbounded = rpm * 60 * 24
            assert usage.CEILINGS[feature] < unbounded, (
                f"{feature}: the daily ceiling does not bind — the minute limit "
                f"already allows {unbounded}/day"
            )


class TestConcurrentCallsCannotSlipPast:
    @pytest.mark.asyncio
    async def test_twenty_at_once_land_on_one_row(self):
        """The reason the increment is a single upsert.

        A read-then-write would let several of these read the same count and all
        decide they were under the ceiling. Concurrency is not an edge case
        here — it is the shape of the traffic the ceiling exists to stop.
        """
        user = await _user("burst")

        async def one():
            async with SessionLocal() as db:
                try:
                    await usage.consume(db, user, "voice_tts")
                    return "ok"
                except HTTPException:
                    return "refused"

        results = await asyncio.gather(*(one() for _ in range(20)))
        assert results.count("ok") == 20
        assert await _count(user.id, "voice_tts") == 20, (
            "concurrent increments were lost — the counter is racing"
        )

    @pytest.mark.asyncio
    async def test_a_concurrent_burst_at_the_ceiling_admits_exactly_the_remainder(
        self, monkeypatch
    ):
        """Ten at once against three remaining: three succeed, seven refused."""
        monkeypatch.setitem(usage.CEILINGS, "voice_stt", 3)
        user = await _user("edge")

        async def one():
            async with SessionLocal() as db:
                try:
                    await usage.consume(db, user, "voice_stt")
                    return "ok"
                except HTTPException:
                    return "refused"

        results = await asyncio.gather(*(one() for _ in range(10)))
        assert results.count("ok") == 3, f"admitted {results.count('ok')}, expected 3"
        assert results.count("refused") == 7


class TestEveryMeteredRouteActuallyCallsIt:
    """A ceiling nothing calls is a ceiling that does not exist.

    Added after a mutation sweep: deleting `usage.consume` from a route broke no
    test at all, because everything above exercises the service directly. The
    service being right and the service being *wired* are separate claims and
    only one of them was being made.
    """

    #: Endpoint path + method → the feature key it must meter.
    METERED = {
        ("POST", "/voice/stt"): "voice_stt",
        ("POST", "/voice/tts"): "voice_tts",
        ("POST", "/plans/generate"): "plan_generate",
        ("POST", "/goals/{goal_id}/decompose"): "goal_decompose",
        ("POST", "/assessment/topics"): "assessment_topics",
        ("POST", "/oracle/messages"): "oracle_turn",
        ("POST", "/oracle/confirm"): "oracle_turn",
    }

    def test_the_call_is_present_on_every_one(self):
        """Read from the LIVE route table, not from a file.

        Grepping the source would pass on a handler that is no longer routed,
        or miss one registered from somewhere unexpected. `route.endpoint` is
        the function the app will actually run.
        """
        import inspect

        import app.main  # noqa: F401
        from app.main import app as fastapi_app

        by_key = {}
        for route in fastapi_app.routes:
            for method in getattr(route, "methods", None) or []:
                by_key[(method, getattr(route, "path", ""))] = route

        missing = []
        for key, feature in self.METERED.items():
            route = by_key.get(key)
            if route is None:
                missing.append(f"{key[0]} {key[1]} is not routed at all")
                continue
            source = inspect.getsource(route.endpoint)
            if f'usage.consume(db, user, "{feature}")' not in source:
                missing.append(
                    f"{key[0]} {key[1]} does not meter {feature} — one account "
                    "can call it without any daily bound"
                )
        assert not missing, "\n".join(missing)

    @pytest.mark.asyncio
    async def test_it_really_fires_when_the_endpoint_is_called(self, client, monkeypatch):
        """And the wiring works at runtime, not just in the source.

        Driven through the real request path, so a meter placed after an early
        return — or behind a dependency that raises first — would show up here
        as a call that never happened.
        """
        seen: list[str] = []
        real = usage.consume

        async def spy(db, user, feature):
            seen.append(feature)
            return await real(db, user, feature)

        monkeypatch.setattr(usage, "consume", spy)

        email = f"wired-{uuid.uuid4().hex[:8]}@test.app"
        r = await client.post(
            "/auth/signup", json={"email": email, "password": "password123", "name": "W"}
        )
        assert r.status_code == 201
        client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"

        # The response itself may be anything (503 with no LLM key configured,
        # 200 with one). What is asserted is that the meter ran first.
        await client.post("/plans/generate", json={})
        assert "plan_generate" in seen, "the endpoint ran without metering the call"
