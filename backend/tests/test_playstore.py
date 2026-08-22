"""Google Play purchase verification (WC-15).

Before this, `POST /users/me/subscription/verify` understood Apple only, so an
Android client's entitlement was whatever it claimed. These tests are about the
forgery that closes and — just as important — the two holes a signature check
does NOT close on its own, which is why the route does more than call verify.

A real RSA keypair is generated per test and a real signature is made over a
real payload: the point is the cryptography, and a mocked verifier would pass
against a version that never checked anything.
"""

from __future__ import annotations

import base64
import json
import uuid
from datetime import timedelta

import pytest
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

from app.core.config import settings
from app.core.database import SessionLocal, utcnow
from app.core.security import hash_password
from app.models.user import User
from app.services import playstore

PACKAGE = "com.cerebrozen.app"


def _keypair():
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    der = key.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    return key, base64.b64encode(der).decode()


def _purchase(**overrides) -> str:
    body = {
        "orderId": "GPA.1234-5678-9012-34567",
        "packageName": PACKAGE,
        "productId": "com.cerebrozen.premium.monthly",
        "purchaseTime": 1755000000000,
        "purchaseState": 0,
        "purchaseToken": f"tok-{uuid.uuid4().hex}",
        "autoRenewing": True,
    }
    body.update(overrides)
    return json.dumps(body)


def _sign(key, payload: str, algorithm=None) -> str:
    algorithm = algorithm or hashes.SHA1()
    return base64.b64encode(
        key.sign(payload.encode(), padding.PKCS1v15(), algorithm)
    ).decode()


@pytest.fixture
def play(monkeypatch):
    """A configured server, with the key a real signature will match."""
    key, pub = _keypair()
    monkeypatch.setattr(settings, "play_license_key", pub)
    monkeypatch.setattr(settings, "play_package_name", PACKAGE)
    return key


class TestTheSignatureIsTheWholePoint:
    def test_a_real_signature_verifies_and_returns_the_payload(self, play):
        payload = _purchase()
        result = playstore.verify_purchase(payload, _sign(play, payload))
        assert result["productId"] == "com.cerebrozen.premium.monthly"

    def test_an_edited_payload_is_refused(self, play):
        # The forgery this exists to stop: buy the cheap tier, edit the JSON.
        payload = _purchase(productId="com.cerebrozen.premium.monthly")
        signature = _sign(play, payload)
        forged = payload.replace(
            "com.cerebrozen.premium.monthly", "com.cerebrozen.premiumhuman.annual"
        )
        with pytest.raises(playstore.ReceiptError):
            playstore.verify_purchase(forged, signature)

    def test_a_signature_from_another_key_is_refused(self, play):
        # Somebody else's Play account, or a self-signed payload.
        other, _ = _keypair()
        payload = _purchase()
        with pytest.raises(playstore.ReceiptError):
            playstore.verify_purchase(payload, _sign(other, payload))

    def test_sha256_signatures_verify_too(self, play):
        # So the day Play moves off SHA-1 this needs no edit.
        payload = _purchase()
        signature = _sign(play, payload, hashes.SHA256())
        assert playstore.verify_purchase(payload, signature)["packageName"] == PACKAGE

    def test_every_signature_failure_reads_the_same(self, play):
        # Telling a forger whether the KEY or the PAYLOAD was wrong tells them
        # which half to keep working on.
        other, _ = _keypair()
        payload = _purchase()
        with pytest.raises(playstore.ReceiptError) as wrong_key:
            playstore.verify_purchase(payload, _sign(other, payload))
        with pytest.raises(playstore.ReceiptError) as edited:
            # A different payload than the one that was signed.
            playstore.verify_purchase(_purchase(), _sign(play, payload))
        assert str(wrong_key.value) == str(edited.value)

    def test_a_purchase_for_another_app_is_refused(self, play):
        # A valid signature over the wrong app's purchase is still valid.
        payload = _purchase(packageName="com.someone.else")
        with pytest.raises(playstore.ReceiptError, match="different application"):
            playstore.verify_purchase(payload, _sign(play, payload))

    @pytest.mark.parametrize("bad", ["not base64!!", ""])
    def test_a_malformed_signature_is_an_error_not_a_crash(self, play, bad):
        payload = _purchase()
        with pytest.raises(playstore.ReceiptError):
            playstore.verify_purchase(payload, bad)

    def test_a_signed_non_json_payload_is_refused(self, play):
        payload = "definitely not json"
        with pytest.raises(playstore.ReceiptError, match="not JSON"):
            playstore.verify_purchase(payload, _sign(play, payload))

    def test_a_signed_json_array_is_refused(self, play):
        payload = json.dumps(["not", "an", "object"])
        with pytest.raises(playstore.ReceiptError, match="not an object"):
            playstore.verify_purchase(payload, _sign(play, payload))

    def test_a_purchase_with_no_token_is_refused(self, play):
        # Without a token there is nothing to make unique, so the replay
        # protection one level up would silently do nothing.
        payload = _purchase(purchaseToken="")
        with pytest.raises(playstore.ReceiptError, match="purchaseToken"):
            playstore.verify_purchase(payload, _sign(play, payload))


class TestItRefusesRatherThanGuessingWhenUnconfigured:
    def test_no_key_means_no_verification(self, monkeypatch):
        # The degrade-without-keys rule. For a paywall the clean no-op is
        # "unverified, therefore not premium" — never "assume it is fine".
        monkeypatch.setattr(settings, "play_license_key", "")
        assert playstore.configured() is False
        with pytest.raises(playstore.ReceiptError, match="not configured"):
            playstore.verify_purchase(_purchase(), "sig")

    def test_a_nonsense_key_is_an_error_not_a_crash(self, monkeypatch):
        monkeypatch.setattr(settings, "play_license_key", "!!!not base64!!!")
        with pytest.raises(playstore.ReceiptError, match="public key"):
            playstore.verify_purchase(_purchase(), "sig")


class TestTierMapping:
    def test_a_known_product_grants_its_tier(self):
        assert playstore.tier_for(json.loads(_purchase()))[0] == "premium"

    def test_the_human_tier_is_its_own_product(self):
        payload = json.loads(_purchase(productId="com.cerebrozen.premiumhuman.annual"))
        assert playstore.tier_for(payload)[0] == "premium_human"

    def test_an_unknown_product_is_free_rather_than_a_guess(self):
        payload = json.loads(_purchase(productId="com.someone.else.pro"))
        assert playstore.tier_for(payload)[0] == "free"

    @pytest.mark.parametrize("state,expected", [(0, "premium"), (1, "free"), (2, "free")])
    def test_only_a_completed_purchase_pays(self, state, expected):
        # 1 is cancelled; 2 is pending — someone mid-payment at a kiosk is not
        # yet a subscriber, and is not an error either.
        payload = json.loads(_purchase(purchaseState=state))
        assert playstore.tier_for(payload)[0] == expected

    def test_an_expired_subscription_falls_back_to_free(self):
        past = int((utcnow() - timedelta(days=1)).timestamp() * 1000)
        payload = json.loads(_purchase(expiryTimeMillis=past))
        tier, expires = playstore.tier_for(payload)
        assert tier == "free"
        assert expires is not None

    def test_a_live_subscription_keeps_its_tier_and_expiry(self):
        future = int((utcnow() + timedelta(days=20)).timestamp() * 1000)
        payload = json.loads(_purchase(expiryTimeMillis=future))
        tier, expires = playstore.tier_for(payload)
        assert tier == "premium"
        assert expires is not None

    def test_a_nonsense_expiry_does_not_crash_the_paywall(self):
        payload = json.loads(_purchase(expiryTimeMillis="soon"))
        assert playstore.tier_for(payload)[0] == "premium"


class TestTheHolesASignatureCannotClose:
    """The route's job, not the verifier's — and the reason it exists."""

    async def _user(self, session) -> User:
        user = User(
            email=f"play-{uuid.uuid4().hex[:10]}@test.app",
            hashed_password=hash_password("x"),
            name="Play",
        )
        session.add(user)
        await session.flush()
        return user

    @pytest.mark.asyncio
    async def test_a_replayed_purchase_cannot_unlock_a_second_account(self, play):
        # A signed payload verifies forever, on any account. Uniqueness on the
        # token is what makes "first verifier owns it" enforceable.
        token = f"tok-{uuid.uuid4().hex}"
        async with SessionLocal() as s:
            first = await self._user(s)
            first.play_purchase_token = token
            await s.commit()

            second = await self._user(s)
            second.play_purchase_token = token
            with pytest.raises(Exception):
                await s.commit()
            await s.rollback()

    @pytest.mark.asyncio
    async def test_the_same_account_can_re_verify_its_own_purchase(self, play):
        # Re-verification happens on every launch; it must not collide with
        # itself, or a returning subscriber loses premium on app restart.
        token = f"tok-{uuid.uuid4().hex}"
        async with SessionLocal() as s:
            user = await self._user(s)
            user.play_purchase_token = token
            await s.commit()
            user.play_purchase_token = token
            await s.commit()
            assert user.play_purchase_token == token


class TestTheRouteIsTheServerDecidingEntitlement:
    """End to end: the client sends a purchase, the SERVER sets the tier."""

    async def _signed_in(self, client) -> tuple[dict, str]:
        email = f"playroute-{uuid.uuid4().hex[:10]}@test.app"
        await client.post("/auth/signup", json={"email": email, "password": "Passw0rd!x", "name": "P"})
        resp = await client.post(
            "/auth/login",
            data={"username": email, "password": "Passw0rd!x"},
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )
        token = resp.json()["access_token"]
        me = await client.get("/auth/me", headers={"Authorization": f"Bearer {token}"})
        return me.json(), token

    @pytest.mark.asyncio
    async def test_a_valid_purchase_sets_the_tier_server_side(self, client, play):
        me, token = await self._signed_in(client)
        payload = _purchase(obfuscatedAccountId=me["id"])
        resp = await client.post(
            "/users/me/subscription/verify-play",
            json={"purchase_payload": payload, "purchase_signature": _sign(play, payload)},
            headers={"Authorization": f"Bearer {token}"},
        )
        assert resp.status_code == 200, resp.text
        assert resp.json()["subscription_tier"] == "premium"

    @pytest.mark.asyncio
    async def test_a_forged_payload_leaves_the_account_free(self, client, play):
        # The hole this closes: before it, the client simply asserted its tier.
        me, token = await self._signed_in(client)
        payload = _purchase(obfuscatedAccountId=me["id"])
        signature = _sign(play, payload)
        forged = payload.replace("premium.monthly", "premiumhuman.annual")
        resp = await client.post(
            "/users/me/subscription/verify-play",
            json={"purchase_payload": forged, "purchase_signature": signature},
            headers={"Authorization": f"Bearer {token}"},
        )
        assert resp.status_code == 400
        me_after = await client.get("/auth/me", headers={"Authorization": f"Bearer {token}"})
        assert me_after.json()["subscription_tier"] == "free"

    @pytest.mark.asyncio
    async def test_somebody_elses_purchase_is_refused(self, client, play):
        # Play stamps the buyer's id when the client sets it; a purchase
        # carrying another account's id is another account's purchase.
        _me, token = await self._signed_in(client)
        payload = _purchase(obfuscatedAccountId=str(uuid.uuid4()))
        resp = await client.post(
            "/users/me/subscription/verify-play",
            json={"purchase_payload": payload, "purchase_signature": _sign(play, payload)},
            headers={"Authorization": f"Bearer {token}"},
        )
        assert resp.status_code == 403

    @pytest.mark.asyncio
    async def test_one_purchase_cannot_unlock_two_accounts(self, client, play):
        # The replay hole, closed at the route: first verifier owns it.
        first, first_token = await self._signed_in(client)
        payload = _purchase(obfuscatedAccountId=first["id"])
        signature = _sign(play, payload)
        ok = await client.post(
            "/users/me/subscription/verify-play",
            json={"purchase_payload": payload, "purchase_signature": signature},
            headers={"Authorization": f"Bearer {first_token}"},
        )
        assert ok.status_code == 200

        # The same signed purchase, replayed by a second account. It cannot
        # carry the second account's id — that would break the signature — so
        # the 403 fires first, which is itself the point: BOTH guards work.
        _second, second_token = await self._signed_in(client)
        replay = await client.post(
            "/users/me/subscription/verify-play",
            json={"purchase_payload": payload, "purchase_signature": signature},
            headers={"Authorization": f"Bearer {second_token}"},
        )
        assert replay.status_code in (403, 409)

    @pytest.mark.asyncio
    async def test_an_unstamped_purchase_is_owned_by_its_first_verifier(self, client, play):
        # Older clients do not set obfuscatedAccountId, so the token uniqueness
        # is the only thing standing between one purchase and many accounts.
        _first, first_token = await self._signed_in(client)
        payload = _purchase()          # no obfuscatedAccountId
        signature = _sign(play, payload)
        ok = await client.post(
            "/users/me/subscription/verify-play",
            json={"purchase_payload": payload, "purchase_signature": signature},
            headers={"Authorization": f"Bearer {first_token}"},
        )
        assert ok.status_code == 200

        _second, second_token = await self._signed_in(client)
        replay = await client.post(
            "/users/me/subscription/verify-play",
            json={"purchase_payload": payload, "purchase_signature": signature},
            headers={"Authorization": f"Bearer {second_token}"},
        )
        assert replay.status_code == 409
