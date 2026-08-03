"""Per-device push: one account, several installs, and dead tokens that stop costing sends.

`User.push_token` held exactly one iOS token, so a user with a phone and a
tablet lost whichever registered first and Android had nowhere to put an FCM
token at all. These tests pin the replacement's behaviour, including the parts
that only matter months later: a rotated token adopting its row, and an
uninstalled app being marked dead instead of retried forever.
"""
import uuid

from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.device_token import DeviceToken
from app.models.nudge import Nudge
from app.models.user import User
from app.services import fcm, notifications


async def _signup(client, prefix="dev"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": addr, "password": "password123", "name": "D"})
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return addr


async def test_a_phone_and_a_tablet_both_stay_registered(client):
    await _signup(client)
    for token in ("token-phone-aaaa", "token-tablet-bbbb"):
        r = await client.post(
            "/users/me/devices",
            json={"token": token, "platform": "android", "app_version": "0.1.0"},
        )
        assert r.status_code == 201, r.text

    status = (await client.get("/users/me/devices")).json()
    assert status["devices"] == 2
    # No FCM credentials in CI, so the client is told the truth rather than
    # being offered a toggle that would silently do nothing.
    assert status["enabled"] is False


async def test_re_registering_the_same_token_adopts_the_row(client):
    await _signup(client)
    first = (await client.post(
        "/users/me/devices", json={"token": "token-rotate-1", "platform": "android"}
    )).json()
    second = (await client.post(
        "/users/me/devices", json={"token": "token-rotate-1", "platform": "android", "app_version": "0.2.0"}
    )).json()

    assert first["id"] == second["id"], "a cold-start re-register must not duplicate the install"
    assert second["app_version"] == "0.2.0"
    assert second["last_seen_at"] >= first["last_seen_at"]


async def test_a_bad_platform_is_refused(client):
    await _signup(client)
    r = await client.post("/users/me/devices", json={"token": "token-x", "platform": "windows"})
    assert r.status_code == 422, "an unroutable platform must fail loudly at registration"


async def test_unregister_is_scoped_to_the_caller(client):
    owner = await _signup(client, "owner")
    await client.post("/users/me/devices", json={"token": "token-owned", "platform": "ios"})

    await _signup(client, "stranger")
    r = await client.delete("/users/me/devices", params={"token": "token-owned"})
    assert r.status_code == 204  # no information about whose token it is

    async with SessionLocal() as db:
        row = await db.scalar(select(DeviceToken).where(DeviceToken.token == "token-owned"))
        assert row is not None, "another account must not be able to unregister this install"
        assert (await db.scalar(select(User).where(User.email == owner))) is not None


async def test_sign_out_removes_only_this_install(client):
    await _signup(client)
    await client.post("/users/me/devices", json={"token": "token-keep", "platform": "android"})
    await client.post("/users/me/devices", json={"token": "token-drop", "platform": "android"})

    assert (await client.delete("/users/me/devices", params={"token": "token-drop"})).status_code == 204
    assert (await client.get("/users/me/devices")).json()["devices"] == 1


async def test_deliver_fans_out_and_buries_dead_tokens(client, monkeypatch):
    email = await _signup(client)
    await client.post("/users/me/devices", json={"token": "token-live", "platform": "android"})
    await client.post("/users/me/devices", json={"token": "token-gone", "platform": "android"})

    async def fake_send(token, nudge):
        return fcm.DEAD if token == "token-gone" else fcm.OK

    monkeypatch.setattr(fcm, "send", fake_send)

    async with SessionLocal() as db:
        user = await db.scalar(select(User).where(User.email == email))
        nudge = Nudge(user_id=user.id, kind="checkin", title="Hi", body="there")
        delivered = await notifications.deliver(db, user, nudge)
        await db.commit()

        assert delivered is True, "one live install is enough for the nudge to count as sent"
        rows = {r.token: r for r in (await db.scalars(select(DeviceToken).where(DeviceToken.user_id == user.id))).all()}
        assert rows["token-gone"].failed_at is not None, "an uninstalled app must stop being retried"
        assert rows["token-live"].failed_at is None


async def test_deliver_reports_failure_when_every_install_refuses(client, monkeypatch):
    email = await _signup(client)
    await client.post("/users/me/devices", json={"token": "token-flaky", "platform": "android"})

    async def fake_send(token, nudge):
        return fcm.RETRY

    monkeypatch.setattr(fcm, "send", fake_send)

    async with SessionLocal() as db:
        user = await db.scalar(select(User).where(User.email == email))
        nudge = Nudge(user_id=user.id, kind="checkin", title="Hi", body="there")
        assert await notifications.deliver(db, user, nudge) is False
        rows = (await db.scalars(select(DeviceToken).where(DeviceToken.user_id == user.id))).all()
        assert rows[0].failed_at is None, "a transient failure must not bury the install"


async def test_deliver_falls_back_to_the_legacy_column(client):
    """iOS still sets `User.push_token`; accounts that only have that keep working."""
    email = await _signup(client)
    async with SessionLocal() as db:
        user = await db.scalar(select(User).where(User.email == email))
        user.push_token = "legacy-ios-token"
        await db.commit()
        nudge = Nudge(user_id=user.id, kind="checkin", title="Hi", body="there")
        # APNs is unconfigured in CI → the log-only path reports success.
        assert await notifications.deliver(db, user, nudge) is True


async def test_fcm_message_is_a_data_message_on_the_app_channel():
    """Sent as data, not `notification`: the app builds the notification itself,
    so foreground/background/killed all render the same and the deeplink survives."""
    nudge = Nudge(kind="reminder", title="Wind down", body="3-minute reset", deeplink="cerebro://breathe")
    message = fcm.build_message("abc123", nudge)["message"]

    assert message["token"] == "abc123"
    assert "notification" not in message, "a display notification would drop the deeplink payload"
    assert message["data"]["deeplink"] == "cerebro://breathe"
    assert message["android"]["notification"]["channel_id"] == "cerebro_nudges"
    assert message["android"]["priority"] == "normal", "a wellness nudge is never an alarm"


def test_fcm_classifies_provider_answers():
    assert fcm.classify(200, "{}") == fcm.OK
    assert fcm.classify(404, "{}") == fcm.DEAD
    assert fcm.classify(400, '{"error":{"status":"INVALID_ARGUMENT"}}') == fcm.DEAD
    assert fcm.classify(403, '{"error":{"status":"UNREGISTERED"}}') == fcm.DEAD
    assert fcm.classify(503, "unavailable") == fcm.RETRY, "an outage must be retried, not buried"


async def test_fcm_send_logs_instead_of_failing_without_credentials():
    """CI runs with blank keys — delivery must no-op cleanly, not raise."""
    nudge = Nudge(kind="checkin", title="Hi", body="there")
    assert await fcm.send("some-token", nudge) == fcm.OK


# ── The configured path, with the network stubbed ────────────────────────
class _Resp:
    def __init__(self, status_code=200, text="{}", payload=None):
        self.status_code = status_code
        self.text = text
        self._payload = payload if payload is not None else {}

    def json(self):
        return self._payload


class _Client:
    """Minimal httpx.AsyncClient stand-in: token exchange then message send."""

    responses: list = []
    calls: list = []

    def __init__(self, *a, **k):
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, *a):
        return False

    async def post(self, url, **kwargs):
        _Client.calls.append((url, kwargs))
        return _Client.responses.pop(0)


def _configure_fcm(monkeypatch, tmp_path):
    from app.core.config import settings

    creds = tmp_path / "service-account.json"
    creds.write_text(
        '{"client_email": "svc@project.iam.gserviceaccount.com", "private_key": "KEY"}'
    )
    monkeypatch.setattr(settings, "fcm_credentials_path", str(creds))
    monkeypatch.setattr(settings, "fcm_project_id", "cerebro-test")
    monkeypatch.setattr(fcm.jwt, "encode", lambda *a, **k: "signed.assertion")
    monkeypatch.setattr(fcm.httpx, "AsyncClient", _Client)
    fcm._token_cache.clear()
    _Client.calls = []


async def test_configured_send_mints_a_token_then_posts_the_message(monkeypatch, tmp_path):
    _configure_fcm(monkeypatch, tmp_path)
    _Client.responses = [
        _Resp(200, payload={"access_token": "ya29.stub"}),
        _Resp(200),
    ]
    nudge = Nudge(kind="checkin", title="Hi", body="there", deeplink="cerebro://mood")

    assert await fcm.send("device-token", nudge) == fcm.OK
    token_url, send_url = _Client.calls[0][0], _Client.calls[1][0]
    assert token_url == fcm._TOKEN_URL
    assert send_url.endswith("/v1/projects/cerebro-test/messages:send")
    assert _Client.calls[1][1]["headers"]["Authorization"] == "Bearer ya29.stub"


async def test_the_access_token_is_reused_across_sends(monkeypatch, tmp_path):
    """Google issues 1-hour tokens; minting one per nudge would be a request
    per delivery on top of every delivery."""
    _configure_fcm(monkeypatch, tmp_path)
    _Client.responses = [
        _Resp(200, payload={"access_token": "ya29.stub"}),
        _Resp(200),
        _Resp(200),
    ]
    nudge = Nudge(kind="checkin", title="Hi", body="there")

    assert await fcm.send("token-a", nudge) == fcm.OK
    assert await fcm.send("token-b", nudge) == fcm.OK
    assert sum(1 for url, _ in _Client.calls if url == fcm._TOKEN_URL) == 1


async def test_a_refused_token_exchange_is_a_retry_not_a_dead_install(monkeypatch, tmp_path):
    _configure_fcm(monkeypatch, tmp_path)
    _Client.responses = [_Resp(401, text="invalid_grant")]
    nudge = Nudge(kind="checkin", title="Hi", body="there")

    assert await fcm.send("device-token", nudge) == fcm.RETRY, (
        "our credentials being wrong must never bury the user's install"
    )


async def test_an_unregistered_install_comes_back_dead(monkeypatch, tmp_path):
    _configure_fcm(monkeypatch, tmp_path)
    _Client.responses = [
        _Resp(200, payload={"access_token": "ya29.stub"}),
        _Resp(404, text='{"error":{"status":"UNREGISTERED"}}'),
    ]
    nudge = Nudge(kind="checkin", title="Hi", body="there")

    assert await fcm.send("device-token", nudge) == fcm.DEAD


async def test_an_unreadable_service_account_degrades_to_retry(monkeypatch, tmp_path):
    from app.core.config import settings

    monkeypatch.setattr(settings, "fcm_credentials_path", str(tmp_path / "missing.json"))
    monkeypatch.setattr(settings, "fcm_project_id", "cerebro-test")
    fcm._token_cache.clear()
    nudge = Nudge(kind="checkin", title="Hi", body="there")

    assert await fcm.send("device-token", nudge) == fcm.RETRY


async def test_apns_reports_a_gone_install_as_dead(monkeypatch, tmp_path):
    """The other provider speaks the same three words, or `deliver` cannot
    treat them alike."""
    from app.core.config import settings

    key_file = tmp_path / "AuthKey.p8"
    key_file.write_text("-----BEGIN PRIVATE KEY-----\nx\n-----END PRIVATE KEY-----\n")
    monkeypatch.setattr(settings, "apns_key_path", str(key_file))
    monkeypatch.setattr(settings, "apns_key_id", "KEY123")
    monkeypatch.setattr(settings, "apns_team_id", "TEAM123")
    monkeypatch.setattr(notifications.jwt, "encode", lambda *a, **k: "signed.jwt")
    monkeypatch.setattr(notifications.httpx, "AsyncClient", _Client)
    notifications._token_cache.clear()
    _Client.calls = []
    _Client.responses = [_Resp(410, text="Unregistered")]

    nudge = Nudge(kind="checkin", title="Hi", body="there")
    assert await notifications.send_apns("gone-token", nudge) == fcm.DEAD
