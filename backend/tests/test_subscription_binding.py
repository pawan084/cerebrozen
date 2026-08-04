"""Register C1-C3: the subscription belongs to its buyer, replays are inert,
and oracle threads are namespaced per user.

One signed StoreKit transaction used to grant premium on unlimited accounts
(nothing compared appAccountToken, nothing recorded originalTransactionId),
and the App Store webhook had no replay guard while the Stripe handler next
to it did. These tests pin the closures.
"""
import uuid
from datetime import datetime, timedelta, timezone

from app.services import appstore
from tests.test_webhooks import _chain, _jws


def _future_ms() -> int:
    return int((datetime.now(timezone.utc) + timedelta(days=30)).timestamp() * 1000)


async def _second_user(client) -> str:
    """Sign a second account in (replacing the client's auth header) and
    return its id."""
    email = f"user-{uuid.uuid4().hex[:10]}@test.app"
    resp = await client.post(
        "/auth/signup", json={"email": email, "password": "password123", "name": "Second"}
    )
    assert resp.status_code == 201, resp.text
    client.headers["Authorization"] = f"Bearer {resp.json()['access_token']}"
    me = await client.get("/users/me")
    return me.json()["id"]


async def test_receipt_with_someone_elses_token_is_refused(auth_client, tmp_path, monkeypatch):
    leaf_key, x5c, root_pem = _chain()
    (tmp_path / "root.pem").write_bytes(root_pem)
    monkeypatch.setattr(appstore.settings, "appstore_root_cert_path", str(tmp_path / "root.pem"))

    txn = _jws(
        {
            "productId": "com.cerebrozen.premium.monthly",
            "expiresDate": _future_ms(),
            "appAccountToken": str(uuid.uuid4()),   # not this caller
        },
        leaf_key, x5c,
    )
    r = await auth_client.post("/users/me/subscription/verify", json={"signed_transaction": txn})
    assert r.status_code == 403
    assert (await auth_client.get("/users/me")).json()["subscription_tier"] == "free"


async def test_one_transaction_grants_one_account(auth_client, tmp_path, monkeypatch):
    # No appAccountToken (the older-client path) — the originalTransactionId
    # binding is what stands between one receipt and unlimited accounts.
    leaf_key, x5c, root_pem = _chain()
    (tmp_path / "root.pem").write_bytes(root_pem)
    monkeypatch.setattr(appstore.settings, "appstore_root_cert_path", str(tmp_path / "root.pem"))

    txn = _jws(
        {
            "productId": "com.cerebrozen.premium.monthly",
            "expiresDate": _future_ms(),
            "originalTransactionId": f"OT-{uuid.uuid4().hex[:12]}",
        },
        leaf_key, x5c,
    )
    r = await auth_client.post("/users/me/subscription/verify", json={"signed_transaction": txn})
    assert r.status_code == 200
    assert r.json()["subscription_tier"] == "premium"

    # A second account presenting the SAME transaction gets nothing.
    await _second_user(auth_client)
    r = await auth_client.post("/users/me/subscription/verify", json={"signed_transaction": txn})
    assert r.status_code == 409
    assert (await auth_client.get("/users/me")).json()["subscription_tier"] == "free"


async def test_rebinding_your_own_transaction_is_fine(auth_client, tmp_path, monkeypatch):
    # Re-verifying on the same account (reinstall, refresh) must keep working.
    leaf_key, x5c, root_pem = _chain()
    (tmp_path / "root.pem").write_bytes(root_pem)
    monkeypatch.setattr(appstore.settings, "appstore_root_cert_path", str(tmp_path / "root.pem"))

    txn = _jws(
        {
            "productId": "com.cerebrozen.premium.monthly",
            "expiresDate": _future_ms(),
            "originalTransactionId": f"OT-{uuid.uuid4().hex[:12]}",
        },
        leaf_key, x5c,
    )
    for _ in range(2):
        r = await auth_client.post(
            "/users/me/subscription/verify", json={"signed_transaction": txn}
        )
        assert r.status_code == 200
        assert r.json()["subscription_tier"] == "premium"


async def test_appstore_webhook_replay_is_ignored(client, auth_client, tmp_path, monkeypatch):
    me = (await auth_client.get("/users/me")).json()
    leaf_key, x5c, root_pem = _chain()
    (tmp_path / "root.pem").write_bytes(root_pem)
    monkeypatch.setattr(appstore.settings, "appstore_root_cert_path", str(tmp_path / "root.pem"))

    txn = _jws(
        {
            "productId": "com.cerebrozen.premium.monthly",
            "expiresDate": _future_ms(),
            "appAccountToken": me["id"],
        },
        leaf_key, x5c,
    )
    note_uuid = str(uuid.uuid4())
    outer = _jws(
        {
            "notificationType": "DID_RENEW",
            "notificationUUID": note_uuid,
            "data": {"signedTransactionInfo": txn},
        },
        leaf_key, x5c,
    )

    first = await client.post("/webhooks/appstore", json={"signedPayload": outer})
    assert first.status_code == 200 and first.json()["handled"] is True

    # The identical delivery again — at-least-once is the protocol's normal
    # behaviour — must be a no-op, exactly like the Stripe handler.
    replay = await client.post("/webhooks/appstore", json={"signedPayload": outer})
    assert replay.status_code == 200
    assert replay.json() == {"handled": False, "reason": "duplicate"}


def test_oracle_threads_are_namespaced_per_user():
    from app.api.routes.oracle import scoped_thread_id

    # The bare default and the caller's own id stay unchanged — every
    # existing conversation is preserved.
    assert scoped_thread_id("caller", None) == "caller"
    assert scoped_thread_id("caller", "") == "caller"
    assert scoped_thread_id("caller", "  ") == "caller"
    assert scoped_thread_id("caller", "caller") == "caller"
    # A foreign id lands inside the CALLER'S namespace — another user's UUID
    # resumes nothing of theirs.
    assert scoped_thread_id("caller", "victim-uuid") == "caller:victim-uuid"
    assert scoped_thread_id("caller", "caller:x") == "caller:caller:x"
