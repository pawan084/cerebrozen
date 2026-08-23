"""What a confirmed address unlocks — and what it must never stand in front of.

`email_verified` had been on `User` from the beginning, set by the Apple and
Google flows and by `POST /auth/verify`, and **nothing read it**. Signup did not
even send the message, so it was a column no ordinary user could set and no gate
could be built on. A new account made with any string that parses as an address
drew its full free allowance of real LLM completion at once — the half of bot
protection `botcheck.py` cannot do, since a challenge proves a human was present
for half a minute while a delivered email proves somebody owns a mailbox.

The tests are split by what would go wrong, because the two directions are not
symmetric. Gating too little costs money. Gating too much costs somebody the
thing they came here for, and the file that matters most is the last class.
"""

from __future__ import annotations

import uuid

import pytest
from sqlalchemy import select, update

from app.core.config import settings
from app.core.database import SessionLocal
from app.models.chat import ChatMessage
from app.models.user import User
from app.services import email as email_service
from app.services import usage, verification


@pytest.fixture
def gate_on(monkeypatch):
    """Turn the gate on. It is inert without SMTP, and CI has none.

    That is the correct production behaviour — you cannot demand a proof you are
    unable to deliver — but it means every test below has to say so out loud
    rather than inheriting it.
    """
    monkeypatch.setattr(verification.settings, "smtp_host", "smtp.example.com", raising=False)


async def _signup(client, prefix: str = "unverified") -> tuple[str, str]:
    email = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": email, "password": "password123", "name": "T"}
    )
    assert r.status_code == 201, r.text
    return email, r.json()["access_token"]


async def _user_by(email: str) -> User:
    async with SessionLocal() as db:
        return await db.scalar(select(User).where(User.email == email))


async def _set(email: str, **values) -> None:
    async with SessionLocal() as db:
        await db.execute(update(User).where(User.email == email).values(**values))
        await db.commit()


# ── The prerequisite: an address nobody can confirm is not a gate ────────
class TestSignupSendsSomethingToConfirm:
    @pytest.mark.asyncio
    async def test_signing_up_sends_a_verification_link(self, client):
        """Until this landed, `/auth/verify/request` was the only way to get a
        link — an endpoint a signed-in user had to know existed and ask for."""
        email_service.sent_outbox.clear()
        address, _ = await _signup(client)

        sent = [m for m in email_service.sent_outbox if m["to"] == address]
        assert sent, "signup sent nothing to confirm"
        assert "/verify?token=" in sent[0]["body"]

    @pytest.mark.asyncio
    async def test_a_mail_failure_cannot_fail_the_registration(self, client, monkeypatch):
        """`send_email` never raises by contract; this pins the contract at the
        call site, because a bounced welcome must not cost somebody an account."""
        async def _explode(*a, **kw):
            raise RuntimeError("smtp down")

        monkeypatch.setattr(email_service, "send_email", _explode)
        r = await client.post(
            "/auth/signup",
            json={"email": f"x-{uuid.uuid4().hex[:8]}@test.app",
                  "password": "password123", "name": "T"},
        )
        assert r.status_code == 201, r.text


# ── Who the gate applies to ──────────────────────────────────────────────
class TestWhoIsExempt:
    @pytest.mark.asyncio
    async def test_it_is_inert_when_we_cannot_send_email_at_all(self, client):
        """No SMTP configured means no message exists to act on.

        Gating on a proof we are incapable of delivering is not strictness, it
        is a product that cannot be used. The capability is the switch.
        """
        address, _ = await _signup(client)
        async with SessionLocal() as db:
            user = await db.scalar(select(User).where(User.email == address))
            assert user.email_verified is False
            assert verification.gate_active() is False
            assert await verification.is_exempt(db, user) is True

    @pytest.mark.asyncio
    async def test_a_confirmed_address_is_past_it(self, client, gate_on):
        address, _ = await _signup(client)
        await _set(address, email_verified=True)
        async with SessionLocal() as db:
            user = await db.scalar(select(User).where(User.email == address))
            assert await verification.is_exempt(db, user) is True

    @pytest.mark.asyncio
    async def test_a_paying_account_is_past_it_unconfirmed(self, client, gate_on):
        """A card is a stronger proof of a person than an email.

        Walling a subscriber out of a feature they have paid for, over an
        address they never got round to confirming, is indefensible.
        """
        address, _ = await _signup(client)
        await _set(address, subscription_tier="premium")
        async with SessionLocal() as db:
            user = await db.scalar(select(User).where(User.email == address))
            assert user.email_verified is False
            assert await verification.is_exempt(db, user) is True

    @pytest.mark.asyncio
    async def test_an_account_that_predates_the_gate_is_past_it(self, client, gate_on):
        """The deploy-safety property, and the reason migration b2e9f47c1a08 exists.

        `email_verified` defaulted to false and signup sent nothing to confirm
        until this release, so EVERY account in production carries false — not
        because anyone failed a check but because there was no check to fail.
        Shipping the gate without this would have dropped every existing free
        user to the unverified allowance and 403'd them out of five features, on
        contact, with no client rendering the code that explains why.
        """
        address, _ = await _signup(client)
        await _set(address, verification_grandfathered=True)
        async with SessionLocal() as db:
            user = await db.scalar(select(User).where(User.email == address))
            assert user.email_verified is False
            assert await verification.is_exempt(db, user) is True

    @pytest.mark.asyncio
    async def test_a_new_account_is_not_grandfathered(self, client, gate_on):
        """Otherwise the migration would exempt everybody forever and the gate
        would be decoration. The column defaults false; only the migration's
        one-off UPDATE sets it."""
        address, _ = await _signup(client)
        async with SessionLocal() as db:
            user = await db.scalar(select(User).where(User.email == address))
            assert user.verification_grandfathered is False

    @pytest.mark.asyncio
    async def test_the_flag_does_not_claim_the_address_was_confirmed(self, client, gate_on):
        """Grandfathering is not verification, and the data must not say it is.

        Backfilling `email_verified = true` would have been the one-line version
        and a lie every later reader inherits — a password-reset path or a
        compliance answer about which addresses are reachable would trust it.
        """
        address, _ = await _signup(client)
        await _set(address, verification_grandfathered=True)
        async with SessionLocal() as db:
            user = await db.scalar(select(User).where(User.email == address))
            assert user.email_verified is False, (
                "grandfathering must not be recorded as a confirmed address"
            )

    @pytest.mark.asyncio
    async def test_an_unconfirmed_free_account_is_not(self, client, gate_on):
        address, _ = await _signup(client)
        async with SessionLocal() as db:
            user = await db.scalar(select(User).where(User.email == address))
            assert await verification.is_exempt(db, user) is False


# ── What it stands in front of ───────────────────────────────────────────
class TestTheProviderBackedFeatures:
    #: Every endpoint whose call spends somebody else's money, with the body it
    #: needs to get past validation. Chat is deliberately absent — see the last
    #: class in this file.
    GATED = [
        ("POST", "/voice/tts", {"text": "hello"}),
        ("POST", "/plans/generate", {}),
        ("POST", "/assessment/topics", {}),
        ("POST", "/oracle/messages", {"text": "hello"}),
    ]

    @pytest.mark.parametrize("method,path,body", GATED, ids=[p for _, p, _ in GATED])
    @pytest.mark.asyncio
    async def test_an_unconfirmed_account_is_refused_with_a_code_and_a_feature_name(
        self, client, gate_on, method, path, body
    ):
        _address, token = await _signup(client)
        client.headers["Authorization"] = f"Bearer {token}"

        r = await client.request(method, path, json=body)
        assert r.status_code == 403, f"{path} -> {r.status_code}: {r.text[:200]}"
        detail = r.json()["detail"]
        assert detail["code"] == verification.UNVERIFIED_CODE
        # Named so a client can say WHICH thing is waiting rather than showing a
        # generic wall.
        assert detail["feature"]

    @pytest.mark.asyncio
    async def test_confirming_the_address_opens_them(self, client, gate_on):
        address, token = await _signup(client)
        client.headers["Authorization"] = f"Bearer {token}"
        assert (await client.post("/plans/generate", json={})).status_code == 403

        await _set(address, email_verified=True)
        # 403 is now gone. Anything else (503 with no LLM key configured, 200
        # with one) means the gate let it through, which is all this asserts.
        assert (await client.post("/plans/generate", json={})).status_code != 403


class TestWhatIsDeliberatelyNotGated:
    """Confirming an address is a condition for spending our money.

    It is never a condition for reaching your own words, or for leaving. A gate
    that drifted onto these would turn an unconfirmed address into a hostage
    situation over somebody's journal.
    """

    OPEN = [
        ("GET", "/users/me", None),
        ("GET", "/journal", None),
        ("GET", "/moods", None),
        ("GET", "/users/me/export", None),
        ("GET", "/safety-plan/me", None),
    ]

    @pytest.mark.parametrize("method,path,body", OPEN, ids=[p for _, p, _ in OPEN])
    @pytest.mark.asyncio
    async def test_it_stays_open_to_an_unconfirmed_account(
        self, client, gate_on, method, path, body
    ):
        _address, token = await _signup(client)
        client.headers["Authorization"] = f"Bearer {token}"
        r = await client.request(method, path, json=body)
        assert r.status_code != 403, f"{path} was walled off: {r.text[:200]}"


# ── The part that must never close ───────────────────────────────────────
class TestChatIsNeverLockedShut:
    """Chat is the safety surface, so the gate takes a different shape there.

    An unconfirmed account gets a SMALLER daily allowance, not a locked door —
    and on top of that a waiver whenever the free keyword floor flags the text.
    """

    @pytest.mark.asyncio
    async def test_an_unconfirmed_account_can_still_talk(self, client, gate_on):
        _address, token = await _signup(client)
        client.headers["Authorization"] = f"Bearer {token}"
        r = await client.post("/chat/messages", json={"text": "hello there"})
        assert r.status_code != 403, "chat must not be walled off"

    @pytest.mark.asyncio
    async def test_the_allowance_is_smaller_but_not_zero(self, client, gate_on):
        address, _ = await _signup(client)
        async with SessionLocal() as db:
            user = await db.scalar(select(User).where(User.email == address))
            allowance = await verification.daily_message_allowance(db, user)
        assert 0 < allowance < settings.free_daily_messages

    @pytest.mark.asyncio
    async def test_a_confirmed_account_gets_the_full_free_allowance(self, client, gate_on):
        address, _ = await _signup(client)
        await _set(address, email_verified=True)
        async with SessionLocal() as db:
            user = await db.scalar(select(User).where(User.email == address))
            allowance = await verification.daily_message_allowance(db, user)
        assert allowance == settings.free_daily_messages

    @pytest.mark.asyncio
    async def test_the_daily_cap_does_not_stand_in_front_of_a_crisis(self, client):
        """The bug this whole change had to fix before it could add any gate.

        `enforce_quota` ran before anything looked at the text, so a person at
        message 51 typing the worst sentence of their life met a 429 and an
        upgrade prompt: never scanned, no safety event, no escalation. A billing
        rule was standing between somebody and the sentence they were trying to
        send.

        The keyword floor is local and free — no model call — so consulting it
        first costs nothing and cannot be used to burn tokens: only a message it
        already flags gets through the cap.
        """
        address, _ = await _signup(client)
        user = await _user_by(address)

        async with SessionLocal() as db:
            # Put the account well past any allowance.
            for _ in range(settings.free_daily_messages + 2):
                db.add(ChatMessage(user_id=user.id, role="user", text="filler"))
            await db.commit()

            fresh = await db.get(User, user.id)
            # Ordinary chatter is refused, which is the quota working.
            with pytest.raises(Exception) as refused:
                await usage.enforce_quota(db, fresh)
            assert getattr(refused.value, "status_code", None) == 429

            # The same account, a message the floor flags: allowed through.
            floor, _reason = __import__(
                "app.services.safety", fromlist=["safety"]
            ).keyword_floor("i want to kill myself")
            assert floor == "crisis", "the floor must catch an explicit phrase"
            await usage.enforce_quota(db, fresh, exempt=True)   # must not raise

    @pytest.mark.asyncio
    async def test_the_waiver_is_wired_into_the_endpoint_not_just_the_service(
        self, client
    ):
        """The service accepting `exempt=True` proves nothing on its own — the
        route has to pass it. Driven end to end, over the cap."""
        address, token = await _signup(client)
        user = await _user_by(address)
        async with SessionLocal() as db:
            for _ in range(settings.free_daily_messages + 2):
                db.add(ChatMessage(user_id=user.id, role="user", text="filler"))
            await db.commit()

        client.headers["Authorization"] = f"Bearer {token}"
        ordinary = await client.post("/chat/messages", json={"text": "what's for lunch"})
        assert ordinary.status_code == 429, "the cap should still refuse ordinary chatter"

        urgent = await client.post("/chat/messages", json={"text": "i want to kill myself"})
        assert urgent.status_code != 429, (
            "a message the keyword floor flags met the daily cap — safety never blocks"
        )
