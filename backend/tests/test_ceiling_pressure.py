"""Can anyone tell whether the daily ceilings have ever refused anybody?

`models/daily_usage.py` says an account that reaches one is worth knowing
about. Before this surface existed that was a sentence in a docstring with no
mechanism behind it — the ceilings would have refused calls in silence, and
nobody could have told whether they had ever needed to, which is also how a
ceiling set too low goes unnoticed until it reaches support.

The tests are split by the two ways this could be wrong: reporting too little to
act on, and reporting more about people than an abuse control needs.
"""

from __future__ import annotations

import uuid

import pytest
from sqlalchemy import text

from app.core.database import SessionLocal, utcnow
from app.models.user import User
from app.services import metrics, usage


async def _user(prefix: str) -> User:
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


async def _use(user: User, feature: str, times: int) -> None:
    """Drive the counter to `times` without tripping the ceiling's refusal."""
    async with SessionLocal() as db:
        await db.execute(
            text(
                "INSERT INTO daily_usage (id, user_id, feature, day, count) "
                "VALUES (:id, :uid, :feature, :day, :n)"
            ),
            {
                "id": uuid.uuid4(),
                "uid": user.id,
                "feature": feature,
                "day": utcnow().date(),
                "n": times,
            },
        )
        await db.commit()


@pytest.fixture(autouse=True)
async def _clean_day():
    """Other tests in the suite also meter calls; this surface reports the whole
    day, so each test here starts from an empty table."""
    async with SessionLocal() as db:
        await db.execute(text("DELETE FROM daily_usage"))
        await db.commit()
    yield


class TestItReportsEnoughToActOn:
    @pytest.mark.asyncio
    async def test_an_account_at_the_ceiling_is_named(self, monkeypatch):
        """An abuse control you can see but cannot act on is theatre.

        Acting means knowing which account, so the identifier is included —
        but only for accounts that have crossed the line the product itself
        set. Everyone below it stays a number.
        """
        monkeypatch.setitem(usage.CEILINGS, "plan_generate", 10)
        heavy = await _user("heavy")
        await _use(heavy, "plan_generate", 10)

        async with SessionLocal() as db:
            report = await metrics.ceiling_pressure(db)

        row = report["features"]["plan_generate"]
        assert row["accounts_at_ceiling"] == 1
        assert row["at_ceiling"] == [str(heavy.id)]
        assert row["busiest_count"] == 10

    @pytest.mark.asyncio
    async def test_a_crowd_approaching_shows_up_before_anybody_is_refused(
        self, monkeypatch
    ):
        """The signal that a ceiling is set too low.

        If this only reported accounts that had already been refused, the first
        evidence of a bad number would be a support queue. Half is a judgement
        — early enough to notice, late enough that ordinary use does not fill
        the list.
        """
        monkeypatch.setitem(usage.CEILINGS, "voice_tts", 100)
        for i in range(3):
            await _use(await _user(f"near{i}"), "voice_tts", 60)

        async with SessionLocal() as db:
            report = await metrics.ceiling_pressure(db)

        row = report["features"]["voice_tts"]
        assert row["accounts_approaching"] == 3
        assert row["accounts_at_ceiling"] == 0

    @pytest.mark.asyncio
    async def test_every_metered_feature_appears_even_with_no_traffic(self):
        """A feature missing from the report reads as 'no pressure', which is
        the same shape as 'the meter is broken'. They must not look alike."""
        async with SessionLocal() as db:
            report = await metrics.ceiling_pressure(db)
        assert set(report["features"]) == set(usage.CEILINGS)
        for feature, row in report["features"].items():
            assert row["ceiling"] == usage.CEILINGS[feature]
            assert row["accounts_at_ceiling"] == 0
            assert row["busiest_count"] == 0

    @pytest.mark.asyncio
    async def test_features_do_not_bleed_into_each_other(self, monkeypatch):
        monkeypatch.setitem(usage.CEILINGS, "voice_tts", 5)
        monkeypatch.setitem(usage.CEILINGS, "oracle_turn", 5)
        busy = await _user("busy")
        await _use(busy, "voice_tts", 5)

        async with SessionLocal() as db:
            report = await metrics.ceiling_pressure(db)

        assert report["features"]["voice_tts"]["accounts_at_ceiling"] == 1
        assert report["features"]["oracle_turn"]["accounts_at_ceiling"] == 0


class TestItDoesNotReportMoreThanItNeeds:
    @pytest.mark.asyncio
    async def test_an_ordinary_account_is_never_named(self, monkeypatch):
        """Below the line, everybody is a count.

        The admin surface's whole posture is counts and account state, not a
        per-person behavioural record. Somebody using the product normally has
        not tripped anything and has no business appearing in an abuse report.
        """
        monkeypatch.setitem(usage.CEILINGS, "plan_generate", 100)
        ordinary = await _user("ordinary")
        await _use(ordinary, "plan_generate", 3)

        async with SessionLocal() as db:
            report = await metrics.ceiling_pressure(db)

        row = report["features"]["plan_generate"]
        assert row["at_ceiling"] == []
        assert str(ordinary.id) not in str(report), (
            "an account well below the ceiling was named in an abuse report"
        )

    @pytest.mark.asyncio
    async def test_an_approaching_account_is_counted_but_not_named(self, monkeypatch):
        """Approaching a ceiling is not crossing one. The count is the signal
        that a number is wrong; the identity is not needed to fix a number."""
        monkeypatch.setitem(usage.CEILINGS, "voice_stt", 100)
        near = await _user("near")
        await _use(near, "voice_stt", 70)

        async with SessionLocal() as db:
            report = await metrics.ceiling_pressure(db)

        assert report["features"]["voice_stt"]["accounts_approaching"] == 1
        assert str(near.id) not in str(report)

    @pytest.mark.asyncio
    async def test_it_says_out_loud_that_nobody_is_being_paged(self):
        """There is no alerting in this product (INCIDENT_RUNBOOK is honest
        about it), and a dashboard is very good at implying otherwise. Stated
        in the payload so a console cannot render this as a monitored system.
        """
        async with SessionLocal() as db:
            report = await metrics.ceiling_pressure(db)
        assert report["alerting"] is False


class TestTheEndpoint:
    @pytest.mark.asyncio
    async def test_an_admin_can_read_it(self, admin_client):
        r = await admin_client.get("/admin/metrics/ceilings")
        assert r.status_code == 200, r.text
        assert set(r.json()["features"]) == set(usage.CEILINGS)

    @pytest.mark.asyncio
    async def test_an_ordinary_account_cannot(self, client):
        email = f"nosy-{uuid.uuid4().hex[:8]}@test.app"
        r = await client.post(
            "/auth/signup", json={"email": email, "password": "password123", "name": "N"}
        )
        client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
        assert (await client.get("/admin/metrics/ceilings")).status_code in (401, 403)
