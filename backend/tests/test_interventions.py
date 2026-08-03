"""Intervention engine — the rule logic, the consent gates, and the endpoints.

The decision half is pure (`Signals` → `Offer`), so most of this needs no
database at all; that is the point of splitting it out.
"""
import uuid
from datetime import date, datetime, timedelta, timezone

import pytest_asyncio
from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.consent import Consent
from app.models.intervention import InterventionRecommendation
from app.models.mood import MoodLog
from app.models.safety import SafetyEvent
from app.models.sleep import SleepLog
from app.models.user import User
from app.services import interventions
from app.services.interventions import Signals


@pytest_asyncio.fixture
async def db():
    async with SessionLocal() as session:
        yield session


@pytest_asyncio.fixture
async def user(db) -> User:
    """An opted-in user: the engine's inputs are consent-gated (off ⇒ None),
    so a user who granted nothing produces no signals and no rule ever fires.
    The tests about the gate itself flip these flags off explicitly."""
    u = User(email=f"iv-{uuid.uuid4().hex[:10]}@test.app", hashed_password="x", name="T")
    u.consent = Consent(mood_history=True, sleep_history=True)
    db.add(u)
    await db.commit()
    await db.refresh(u)
    return u


# ── pure rule logic ─────────────────────────────────────────────────────
def test_no_signals_fires_nothing():
    # A brand-new user with no data must not be told anything about themselves.
    assert interventions.first_match(Signals()) is None


def test_crisis_outranks_every_self_help_rule():
    # Someone who just flagged something heavy should be offered a person, not
    # a breathing exercise — even though the sleep data would also match.
    s = Signals(nights_logged=5, low_quality_nights=4, bedtime_spread_min=200,
                high_intensity_days=3, low_mood_days=5, open_crisis=True)
    rule, offer = interventions.first_match(s)
    assert rule.slug == "human_support"
    assert rule.tier == "human_support"
    assert offer.action_kind == "support"


def test_rough_sleep_needs_three_logged_nights():
    # Two bad nights out of two is not a pattern worth naming yet.
    assert interventions.first_match(Signals(nights_logged=2, low_quality_nights=2)) is None
    match = interventions.first_match(Signals(nights_logged=3, low_quality_nights=2))
    assert match is not None and match[0].slug == "rough_sleep"


def test_rough_sleep_reason_quotes_the_users_own_counts():
    _, offer = interventions.first_match(Signals(nights_logged=5, low_quality_nights=3))
    # Non-diagnostic: it restates what the user logged, it does not interpret.
    assert offer.reason == "3 nights of your last 5 logged were rated 2 or lower."
    assert offer.action_kind == "program"
    assert offer.action_target == "Sleep Reset"
    assert offer.state == {"nights_logged": 5, "low_quality_nights": 3}


def test_singular_plural_reads_correctly():
    _, offer = interventions.first_match(Signals(high_intensity_days=2))
    assert "2 days" in offer.reason
    _, offer = interventions.first_match(Signals(nights_logged=4, low_quality_nights=2))
    assert "2 nights" in offer.reason


def test_irregular_bedtime_needs_four_nights_and_a_real_spread():
    assert interventions.first_match(
        Signals(nights_logged=4, low_quality_nights=0, bedtime_spread_min=60)
    ) is None
    match = interventions.first_match(
        Signals(nights_logged=4, low_quality_nights=0, bedtime_spread_min=130)
    )
    assert match is not None and match[0].slug == "irregular_bedtime"
    assert "2h 10m" in match[1].reason


def test_low_mood_run_needs_three_days():
    assert interventions.first_match(Signals(low_mood_days=2)) is None
    match = interventions.first_match(Signals(low_mood_days=3))
    assert match is not None and match[0].slug == "low_mood_run"


def test_there_is_no_re_engagement_rule():
    """Deliberate absence, pinned so it isn't 'helpfully' added later.

    A "you haven't checked in for N days" nudge is the loss/pressure framing
    REDESIGN removed when streaks became presence framing. No rule may key off
    absence of activity.
    """
    slugs = {r.slug for r in interventions.RULES}
    assert not {"inactive", "re_engagement", "streak_broken", "comeback"} & slugs
    # And no rule fires on an empty Signals (which is what absence looks like).
    assert interventions.first_match(Signals()) is None


def test_bedtime_spread_anchors_at_noon():
    # 23:00 and 01:00 are two hours apart, not twenty-two.
    from app.services.sleep import _minutes_since_noon

    late = _minutes_since_noon(datetime(2026, 1, 1, 23, 0).time())
    early = _minutes_since_noon(datetime(2026, 1, 1, 1, 0).time())
    assert interventions.bedtime_spread_minutes([late, early]) == 120
    assert interventions.bedtime_spread_minutes([late]) == 0


# ── consent gating ──────────────────────────────────────────────────────
async def test_sleep_signals_are_withheld_without_sleep_consent(db, user):
    today = date.today()
    for i in range(4):
        db.add(SleepLog(user_id=user.id, date=today - timedelta(days=i),
                        bedtime=datetime(2026, 1, 1, 23, 0).time(),
                        wake_time=datetime(2026, 1, 1, 7, 0).time(),
                        quality=1, awakenings=0, source="manual"))
    user.consent.sleep_history = False
    await db.commit()
    await db.refresh(user)

    signals = await interventions.gather_signals(db, user, today=today)
    # None, not 0 — "you turned this off" must be distinguishable from "no bad
    # nights", or the engine would fire on an absence it isn't allowed to see.
    assert signals.nights_logged is None
    assert interventions.first_match(signals) is None


async def test_mood_signals_are_withheld_without_mood_consent(db, user):
    now = datetime.now(timezone.utc)
    for i in range(3):
        db.add(MoodLog(user_id=user.id, mood="low", intensity=1,
                       created_at=now - timedelta(days=i)))
    user.consent.mood_history = False
    await db.commit()
    await db.refresh(user)

    signals = await interventions.gather_signals(db, user, today=date.today())
    assert signals.low_mood_days is None


async def test_crisis_is_never_consent_gated(db, user):
    """Safety is the escalation path, not a data-analysis feature."""
    user.consent.mood_history = False
    user.consent.sleep_history = False
    db.add(SafetyEvent(user_id=user.id, source="journal", risk_level="crisis",
                       reason="test", excerpt="", resolved=False))
    await db.commit()
    await db.refresh(user)

    signals = await interventions.gather_signals(db, user)
    assert signals.open_crisis is True
    rule, _ = interventions.first_match(signals)
    assert rule.slug == "human_support"


async def test_mood_counts_days_not_entries(db, user):
    """Five check-ins in one hard afternoon is one day, not a week's pattern."""
    now = datetime.now(timezone.utc)
    for _ in range(5):
        db.add(MoodLog(user_id=user.id, mood="low", intensity=1, created_at=now))
    await db.commit()
    await db.refresh(user)

    signals = await interventions.gather_signals(db, user)
    assert signals.low_mood_days == 1
    assert interventions.first_match(signals) is None


# ── persistence + lifecycle ─────────────────────────────────────────────
async def _seed_rough_sleep(db, user):
    today = date.today()
    for i in range(4):
        db.add(SleepLog(user_id=user.id, date=today - timedelta(days=i),
                        bedtime=datetime(2026, 1, 1, 23, 0).time(),
                        wake_time=datetime(2026, 1, 1, 7, 0).time(),
                        quality=1, awakenings=0, source="manual"))
    await db.commit()
    await db.refresh(user)


async def test_evaluate_creates_one_offer_and_reuses_it(db, user):
    await _seed_rough_sleep(db, user)
    first = await interventions.evaluate(db, user)
    assert first is not None and first.rule_slug == "rough_sleep"
    # Asking again must not stack a second offer — one at a time by design.
    again = await interventions.evaluate(db, user)
    assert again.id == first.id
    rows = (await db.scalars(
        select(InterventionRecommendation).where(InterventionRecommendation.user_id == user.id)
    )).all()
    assert len(rows) == 1


async def test_dismissing_starts_the_cooldown(db, user):
    await _seed_rough_sleep(db, user)
    rec = await interventions.evaluate(db, user)
    await interventions.resolve(db, user, rec.id, action="dismiss")

    # The signals still match, but a waved-away suggestion must not bounce back.
    assert await interventions.evaluate(db, user) is None


async def test_cooldown_expiry_lets_the_rule_fire_again(db, user):
    await _seed_rough_sleep(db, user)
    rec = await interventions.evaluate(db, user)
    await interventions.resolve(db, user, rec.id, action="dismiss")

    rec.created_at = datetime.now(timezone.utc) - timedelta(hours=100)
    await db.commit()
    revived = await interventions.evaluate(db, user)
    assert revived is not None and revived.id != rec.id


async def test_completing_implies_accepting(db, user):
    await _seed_rough_sleep(db, user)
    rec = await interventions.evaluate(db, user)
    done = await interventions.resolve(db, user, rec.id, action="complete")
    assert done.completed_at is not None
    assert done.accepted_at is not None


async def test_reason_is_frozen_not_recomputed(db, user):
    await _seed_rough_sleep(db, user)
    rec = await interventions.evaluate(db, user)
    original = rec.reason
    # More bad nights arrive; the already-shown offer keeps its wording, so
    # history reflects what the user was actually told.
    db.add(SleepLog(user_id=user.id, date=date.today() - timedelta(days=9),
                    bedtime=datetime(2026, 1, 1, 23, 0).time(),
                    wake_time=datetime(2026, 1, 1, 7, 0).time(),
                    quality=1, awakenings=0, source="manual"))
    await db.commit()
    assert (await interventions.evaluate(db, user)).reason == original


async def test_another_users_offer_cannot_be_resolved(db, user):
    await _seed_rough_sleep(db, user)
    rec = await interventions.evaluate(db, user)
    other = User(email=f"other-{uuid.uuid4().hex[:8]}@test.app", hashed_password="x")
    db.add(other)
    await db.commit()
    await db.refresh(other)
    assert await interventions.resolve(db, other, rec.id, action="accept") is None


# ── endpoints ───────────────────────────────────────────────────────────
async def test_active_returns_null_when_nothing_fires(auth_client):
    r = await auth_client.get("/interventions/active")
    assert r.status_code == 200
    # Null, not 404: having no suggestion is the healthy case.
    assert r.json() is None


async def test_endpoints_require_auth(client):
    assert (await client.get("/interventions/active")).status_code == 401


async def test_active_accept_and_history_round_trip(auth_client, db):
    me = await auth_client.get("/users/me")
    user = await db.get(User, uuid.UUID(me.json()["id"]))
    await _seed_rough_sleep(db, user)

    active = await auth_client.get("/interventions/active")
    assert active.status_code == 200
    body = active.json()
    assert body["rule_slug"] == "rough_sleep"
    assert body["reason"].endswith("were rated 2 or lower.")
    assert body["state_snapshot"]["low_quality_nights"] >= 2

    accepted = await auth_client.post(f"/interventions/{body['id']}/accept")
    assert accepted.status_code == 200
    assert accepted.json()["accepted_at"] is not None

    hist = await auth_client.get("/interventions")
    assert hist.status_code == 200
    assert any(r["id"] == body["id"] for r in hist.json())


async def test_resolving_an_unknown_id_is_404(auth_client):
    r = await auth_client.post(f"/interventions/{uuid.uuid4()}/dismiss")
    assert r.status_code == 404


async def test_offers_die_with_the_user(auth_client, db):
    me = await auth_client.get("/users/me")
    user_id = uuid.UUID(me.json()["id"])
    user = await db.get(User, user_id)
    await _seed_rough_sleep(db, user)
    assert (await auth_client.get("/interventions/active")).json() is not None

    assert (await auth_client.delete("/users/me")).status_code == 204
    db.expire_all()
    left = (await db.scalars(
        select(InterventionRecommendation).where(InterventionRecommendation.user_id == user_id)
    )).all()
    assert left == []
