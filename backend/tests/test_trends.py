"""Trends must never invent a reading.

The 2026-07-31 insights audit found a mood reading computed from no check-ins.
This surface is the same hazard with a chart attached: a day with no data drawn
as zero reads as "I felt terrible", and two points always correlate perfectly.
Every test here is about refusing to say something the data does not support.
"""
import uuid
from datetime import timedelta

from tests.dates import account_day

from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.mood import MoodLog
from app.models.sleep import SleepLog
from app.models.user import User
from app.services import insights, moods, trends


async def _signup(client, prefix="trend"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": addr, "password": "password123", "name": "T"})
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    # Opt in to the data categories (fresh accounts grant nothing) — the same
    # PATCH a real client sends at the end of onboarding.
    r = await client.patch("/users/me/consent", json={
        "mood_history": True, "journal_memory": True, "sleep_history": True})
    assert r.status_code == 200
    return addr


async def _user(email) -> User:
    async with SessionLocal() as db:
        return await db.scalar(select(User).where(User.email == email))


def test_the_neg_mood_taxonomy_matches_the_pattern_dashboard():
    """Two surfaces calling the same feeling "difficult" differently would be a
    contradiction the user can see.

    This used to compare trends and insights only — and those were the two
    copies that had NOT drifted. The copies in agentic.py and nudges.py were
    invisible to it and had both dropped "overwhelmed", so the strongest signal
    a user can send produced a steady-baseline plan and no supportive nudge.
    All four now read one set, and the test checks the behaviour, not just the
    two literals it happened to know about.
    """
    assert trends.NEG_MOODS == insights._NEG_MOODS == moods.DIFFICULT


def test_every_client_mood_that_means_distress_is_treated_as_distress():
    """The check-in vocabulary the clients offer, against the server's reading.

    Android/iOS/web each hand-duplicate this list (CLAUDE.md cross-stack rule),
    so a label one of them can send that the server shrugs at is a silent
    product failure rather than a crash.
    """
    for label in ("Anxious", "Low", "Tired", "Overwhelmed"):
        assert moods.is_difficult(label), f"{label} must read as a hard feeling"
        # Case is not guaranteed: the label arrives as the client typed it.
        assert moods.is_difficult(label.lower())
        assert moods.is_difficult(label.upper())

    # "Not sure" is the answer for someone who cannot name a feeling. It must
    # be neither distress nor contentment — scoring it either way would invent
    # a reading the user explicitly declined to give.
    assert not moods.is_difficult("Not sure")
    assert not moods.is_difficult("Good")
    assert not moods.is_difficult(None)
    assert not moods.is_difficult("")

    # An unknown label from a future client is neutral, never guessed at.
    assert not moods.is_difficult("bewildered")


def test_ease_score_inverts_difficult_moods():
    """Intensity is not a magnitude on its own: 'Anxious at 5' and 'Good at 5'
    are opposite ends of the same axis."""
    assert trends.ease_score("Good", 5) == 5.0
    assert trends.ease_score("Anxious", 5) == 1.0
    assert trends.ease_score("Anxious", 1) == 5.0
    # An unfamiliar label is treated as neutral rather than guessed at.
    assert trends.ease_score("Contemplative", 3) == 3.0
    assert trends.ease_score("Good", 99) == 5.0, "out-of-range intensity is clamped, not trusted"


async def test_an_empty_account_reports_no_data_rather_than_zeros(client):
    await _signup(client)
    body = (await client.get("/insights/trends")).json()

    assert body["mood"]["points"] == []
    assert body["mood"]["enough_data"] is False
    assert body["mood"]["average_ease"] is None, "a zero average would read as 'I felt terrible'"
    assert body["sleep"]["enough_data"] is False
    assert body["sleep"]["avg_duration_min"] is None
    assert body["correlation"]["available"] is False
    assert body["correlation"]["reason"] == "needs_more_days"


async def test_days_without_a_check_in_are_absent_not_zero(client):
    email = await _signup(client)
    user = await _user(email)
    async with SessionLocal() as db:
        # Two check-ins today, none yesterday.
        db.add(MoodLog(user_id=user.id, mood="Good", intensity=4))
        db.add(MoodLog(user_id=user.id, mood="Good", intensity=2))
        await db.commit()

    body = (await client.get("/insights/trends")).json()
    assert len(body["mood"]["points"]) == 1, "one day of data is one point, not a padded series"
    assert body["mood"]["points"][0]["checkins"] == 2
    assert body["mood"]["points"][0]["ease"] == 3.0, "a day is the mean of its own check-ins"
    assert body["mood"]["enough_data"] is False, "one day is not a trend"


async def test_a_summary_appears_once_there_is_enough(client):
    email = await _signup(client)
    user = await _user(email)
    from app.core.database import utcnow

    async with SessionLocal() as db:
        for offset in range(4):
            log = MoodLog(user_id=user.id, mood="Good", intensity=4)
            log.created_at = utcnow() - timedelta(days=offset)
            db.add(log)
        await db.commit()

    body = (await client.get("/insights/trends")).json()
    assert body["mood"]["days_logged"] == 4
    assert body["mood"]["enough_data"] is True
    assert body["mood"]["average_ease"] == 4.0


async def test_correlation_is_withheld_until_enough_nights_overlap(client):
    email = await _signup(client)
    user = await _user(email)
    from datetime import time

    from app.core.database import utcnow

    async with SessionLocal() as db:
        for offset in range(3):
            day = account_day(offset + 1)
            db.add(SleepLog(
                user_id=user.id, date=day, bedtime=time(23, 0), wake_time=time(7, 0),
                quality=4, awakenings=0, source="manual", note="",
            ))
            mood = MoodLog(user_id=user.id, mood="Good", intensity=4)
            mood.created_at = utcnow() - timedelta(days=offset)
            db.add(mood)
        await db.commit()

    body = (await client.get("/insights/trends")).json()
    assert body["correlation"]["available"] is False
    assert body["correlation"]["pairs"] < trends.MIN_CORRELATION_PAIRS
    assert body["correlation"]["coefficient"] is None, "three nights must not produce a finding"


async def test_a_flat_series_reports_no_variation_rather_than_zero(client):
    """Someone who logged the same thing every day has no correlation to
    report — and 0.0 would read as 'no link found', which is a different claim."""
    email = await _signup(client)
    user = await _user(email)
    from datetime import time

    from app.core.database import utcnow

    async with SessionLocal() as db:
        for offset in range(1, 10):
            day = account_day(offset)
            db.add(SleepLog(
                user_id=user.id, date=day, bedtime=time(23, 0), wake_time=time(7, 0),
                quality=4, awakenings=0, source="manual", note="",
            ))
            mood = MoodLog(user_id=user.id, mood="Good", intensity=4)
            mood.created_at = utcnow() - timedelta(days=offset - 1)
            db.add(mood)
        await db.commit()

    body = (await client.get("/insights/trends")).json()
    assert body["correlation"]["pairs"] >= trends.MIN_CORRELATION_PAIRS
    assert body["correlation"]["available"] is False
    assert body["correlation"]["reason"] == "no_variation"


async def test_a_real_link_is_reported_with_its_direction(client):
    email = await _signup(client)
    user = await _user(email)
    from datetime import datetime, time, timezone

    async with SessionLocal() as db:
        # Longer nights paired with easier days. A diary date is the WAKE
        # morning, so the night and the day it affects share one date
        # (register C63 — this used to place the mood a day later, matching
        # the off-by-one the fix removed).
        for offset in range(1, 11):
            day = account_day(offset)
            hours = 5 if offset % 2 else 9
            db.add(SleepLog(
                user_id=user.id, date=day, bedtime=time(23, 0),
                wake_time=time((23 + hours) % 24, 0),
                quality=3, awakenings=0, source="manual", note="",
            ))
            mood = MoodLog(user_id=user.id, mood="Good", intensity=2 if offset % 2 else 5)
            mood.created_at = datetime.combine(day, time(9, 0), tzinfo=timezone.utc)
            db.add(mood)
        await db.commit()

    body = (await client.get("/insights/trends")).json()
    assert body["correlation"]["available"] is True
    assert body["correlation"]["coefficient"] > 0.5
    assert body["correlation"]["direction"] == "better_sleep_easier_days"


async def test_withdrawn_consent_empties_the_series(client):
    """Turning mood memory off must stop the computation, not just the display."""
    email = await _signup(client)
    user = await _user(email)
    async with SessionLocal() as db:
        for _ in range(5):
            db.add(MoodLog(user_id=user.id, mood="Good", intensity=4))
        await db.commit()

    r = await client.patch("/users/me/consent", json={"mood_history": False})
    assert r.status_code == 200, r.text
    body = (await client.get("/insights/trends")).json()
    assert body["mood"]["points"] == []
    assert body["mood"]["enough_data"] is False


async def test_the_window_is_clamped(client):
    await _signup(client)
    assert (await client.get("/insights/trends", params={"days": 5000})).json()["days"] == 180
    assert (await client.get("/insights/trends", params={"days": 1})).json()["days"] == 7
