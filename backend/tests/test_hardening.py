"""Coverage for the hardening pass: honest nudge dispatch statuses, midnight-UTC
quota semantics, and Apple sign-in keyed by the stable `sub` (private relay)."""
import uuid
from datetime import timedelta

from sqlalchemy import select

from app.core.database import SessionLocal, utcnow
from app.core.security import hash_password
from app.models.chat import ChatMessage
from app.models.nudge import Nudge
from app.models.user import User
from app.services import apple, nudges, usage


async def _make_user(session, *, push_token=None):
    user = User(
        email=f"hard-{uuid.uuid4().hex[:10]}@test.app",
        hashed_password=hash_password("x"),
        name="Hard",
        push_token=push_token,
    )
    session.add(user)
    await session.flush()
    return user


# ── Nudge dispatch is honest about outcomes ─────────────────────────────
async def test_dispatch_marks_tokenless_as_skipped_not_sent():
    async with SessionLocal() as s:
        user = await _make_user(s)   # no push token
        nudge = Nudge(user_id=user.id, kind="checkin", title="t", body="b",
                      deeplink="cerebro://x", scheduled_for=utcnow() - timedelta(minutes=5))
        s.add(nudge)
        await s.commit()
        nudge_id = nudge.id

    async with SessionLocal() as s:
        await nudges.dispatch_due(s)

    async with SessionLocal() as s:
        row = await s.get(Nudge, nudge_id)
        assert row.status == "skipped"
        assert row.sent_at is None


# ── Quota counts since midnight UTC, not a rolling 24 h window ──────────
async def test_quota_window_resets_at_midnight_utc():
    async with SessionLocal() as s:
        user = await _make_user(s)
        midnight = utcnow().replace(hour=0, minute=0, second=0, microsecond=0)
        # One message "late yesterday" (would count under the old rolling
        # window) and one today.
        yesterday = ChatMessage(user_id=user.id, role="user", text="old")
        s.add(yesterday)
        await s.flush()
        yesterday.created_at = midnight - timedelta(hours=1)
        s.add(ChatMessage(user_id=user.id, role="user", text="new"))
        await s.commit()

        assert await usage.messages_today(s, user.id) == 1


# ── Apple sign-in: stable `sub` keys the account; email optional ────────
async def test_apple_sign_in_without_email_uses_sub(client, monkeypatch):
    sub = f"nomail-{uuid.uuid4().hex}"

    async def fake_verify(_token):
        return {"sub": sub}   # private-relay edge: token carries no email claim

    monkeypatch.setattr(apple, "verify_identity_token", fake_verify)

    r1 = await client.post("/auth/apple", json={"identity_token": "x", "name": "Relay"})
    assert r1.status_code == 200, r1.text

    # Same sub on a later sign-in resolves to the same account.
    r2 = await client.post("/auth/apple", json={"identity_token": "x"})
    assert r2.status_code == 200
    me1 = await client.get("/auth/me", headers={"Authorization": f"Bearer {r1.json()['access_token']}"})
    me2 = await client.get("/auth/me", headers={"Authorization": f"Bearer {r2.json()['access_token']}"})
    assert me1.json()["id"] == me2.json()["id"]

    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.apple_sub == sub))
        assert user is not None and user.email.startswith("apple_")


async def test_apple_sign_in_adopts_sub_on_legacy_email_account(client, monkeypatch):
    email = f"legacy-{uuid.uuid4().hex[:8]}@example.com"
    sub = f"legacy-sub-{uuid.uuid4().hex}"

    # First sign-in long ago stored no sub (simulate by creating the row).
    async with SessionLocal() as s:
        s.add(User(email=email, hashed_password=hash_password("x"), name="Legacy"))
        await s.commit()

    async def fake_verify(_token):
        return {"sub": sub, "email": email}

    monkeypatch.setattr(apple, "verify_identity_token", fake_verify)
    r = await client.post("/auth/apple", json={"identity_token": "x"})
    assert r.status_code == 200

    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == email))
        assert user.apple_sub == sub
