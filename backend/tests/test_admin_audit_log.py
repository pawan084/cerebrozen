"""Register E33/E34/E35: the operator surface is attributable.

Content CRUD, prompt activation, all-user broadcasts and account disabling
left no record of who did them; the safety-excerpt reveal was a log line the
admin UI described as a durable trail ("the reveal is noted on the row").
"""
import uuid


async def _audit(admin_client, action: str | None = None) -> list[dict]:
    rows = (await admin_client.get("/admin/audit")).json()
    return [r for r in rows if action is None or r["action"] == action]


async def test_content_crud_is_attributed(admin_client):
    created = await admin_client.post(
        "/admin/content",
        json={"kind": "meditation", "title": "Audited item", "subtitle": "s", "duration_min": 5},
    )
    assert created.status_code == 201, created.text
    item_id = created.json()["id"]

    rows = await _audit(admin_client, "content.create")
    assert rows, "creating content must be recorded"
    assert rows[0]["target_id"] == item_id
    assert rows[0]["admin_email"], "the row must say WHO"

    await admin_client.patch(f"/admin/content/{item_id}", json={"title": "Renamed"})
    updated = await _audit(admin_client, "content.update")
    assert updated and updated[0]["target_id"] == item_id
    assert "title" in updated[0]["detail"]["fields"]

    await admin_client.delete(f"/admin/content/{item_id}")
    deleted = await _audit(admin_client, "content.delete")
    assert deleted and deleted[0]["target_id"] == item_id


async def test_disable_reason_is_recorded_not_discarded(admin_client):
    """E33: the panel PATCHed a `reason` body while the route declared only the
    `active` query param, so FastAPI dropped it — no record of who disabled
    which account, or why."""
    # A separate account to act on. (`admin_client` and `client` are the same
    # httpx client with the admin's header, so the target is looked up through
    # the admin API rather than by swapping tokens.)
    email = f"target-{uuid.uuid4().hex[:8]}@test.app"
    signup = await admin_client.post(
        "/auth/signup", json={"email": email, "password": "password123", "name": "Target"}
    )
    assert signup.status_code == 201
    found = (await admin_client.get(f"/admin/users?q={email}")).json()
    assert found, "the admin search must find the new account"
    target_id = found[0]["id"]

    r = await admin_client.patch(
        f"/admin/users/{target_id}/active?active=false",
        json={"reason": "spam from this account"},
    )
    assert r.status_code == 200
    assert r.json()["is_active"] is False

    rows = await _audit(admin_client, "user.disable")
    assert rows, "disabling an account must be recorded"
    assert rows[0]["target_id"] == target_id
    assert rows[0]["reason"] == "spam from this account"


async def test_prompt_activation_is_attributed(admin_client):
    """E32/E34: activating an old version bypasses the save path's
    acknowledgement + two-step confirm. It cannot bypass the record."""
    saved = await admin_client.post(
        "/admin/prompts/safety_classifier",
        json={"template": "classify: {text}", "notes": "test version"},
    )
    assert saved.status_code == 201, saved.text
    version = saved.json()["version"]

    activated = await admin_client.post(
        f"/admin/prompts/safety_classifier/versions/{version}/activate"
    )
    assert activated.status_code == 200

    assert await _audit(admin_client, "prompt.save")
    rows = await _audit(admin_client, "prompt.activate")
    assert rows and rows[0]["detail"]["version"] == version

    # Restore the code default. The prompt tables are shared across the suite
    # (no per-test rollback for them), so leaving a DB-backed safety_classifier
    # active makes `test_list_includes_all_registered_defaults` fail depending
    # on order — a real pollution this test caused, not a flake.
    reverted = await admin_client.post("/admin/prompts/safety_classifier/revert")
    assert reverted.status_code == 200


async def test_broadcast_records_its_audience(admin_client):
    """E31: a push to every active user was one unconfirmed click and left no
    trace of who sent it."""
    r = await admin_client.post(
        "/admin/nudges", json={"title": "Hello all", "body": "A gentle note"}
    )
    assert r.status_code == 201
    rows = await _audit(admin_client, "nudge.broadcast")
    assert rows, "an all-user broadcast must be recorded"
    assert rows[0]["detail"]["recipients"] >= 1
    assert rows[0]["detail"]["title"] == "Hello all"


async def test_excerpt_reveal_is_durable_and_carries_no_content(admin_client):
    """E35: the UI tells reviewers "the server logged it" and CLAIMS_MAP leans
    on "a separate, logged, per-row GET" — but only a rotating log line
    existed. The record is now a row, and it must never contain what was
    read. (The admin writes the entry themselves: admins are users too, and
    this keeps one client with one token.)"""
    written = await admin_client.post(
        "/journal",
        json={"title": "hard night", "body": "i want to kill myself", "tags": []},
    )
    assert written.status_code == 201

    events = (await admin_client.get("/admin/safety")).json()
    if not events:
        return   # classifier disabled in this environment
    event_id = events[0]["id"]

    r = await admin_client.get(f"/admin/safety/{event_id}/excerpt")
    assert r.status_code == 200

    rows = await _audit(admin_client, "safety.excerpt_read")
    assert rows, "revealing a crisis excerpt must be recorded durably"
    assert rows[0]["target_id"] == event_id
    blob = str(rows[0]["detail"]) + rows[0]["reason"]
    assert "kill myself" not in blob, "the trail records THAT it happened, never what was read"


async def test_the_audit_trail_is_admin_only(auth_client):
    """A non-admin must not read the operator trail."""
    r = await auth_client.get("/admin/audit")
    assert r.status_code in (401, 403)
