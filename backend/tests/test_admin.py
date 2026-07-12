"""Admin route coverage (stats, users, content CRUD, safety queue, nudges)."""
import uuid


async def test_admin_requires_admin(auth_client):
    # A normal (non-admin) user is forbidden.
    assert (await auth_client.get("/admin/stats")).status_code == 403


async def test_admin_stats(admin_client):
    r = await admin_client.get("/admin/stats")
    assert r.status_code == 200
    for key in ("users", "mood_logs", "journal_entries", "content_items", "open_safety_events"):
        assert key in r.json()


async def test_admin_list_and_toggle_user(admin_client):
    # Use a separate target user so we never disable the admin's own session.
    email = f"target-{uuid.uuid4().hex[:8]}@test.app"
    signup = await admin_client.post("/auth/signup", json={"email": email, "password": "password123"})
    target_token = signup.json()["access_token"]
    me = await admin_client.get("/auth/me", headers={"Authorization": f"Bearer {target_token}"})
    target_id = me.json()["id"]

    r = await admin_client.get("/admin/users")
    assert r.status_code == 200 and len(r.json()) >= 1

    r2 = await admin_client.patch(f"/admin/users/{target_id}/active", params={"active": False})
    assert r2.status_code == 200 and r2.json()["is_active"] is False
    # Unknown user → 404.
    assert (await admin_client.patch(f"/admin/users/{uuid.uuid4()}/active", params={"active": True})).status_code == 404


async def test_admin_content_crud(admin_client):
    create = await admin_client.post("/admin/content", json={
        "title": "Test meditation", "subtitle": "calm", "kind": "meditation",
        "symbol": "leaf", "image_url": "", "duration_min": 5, "premium": False, "published": True,
        "narration_script": "Close your eyes and follow the breath.",
    })
    assert create.status_code == 201
    assert create.json()["narration_script"] == "Close your eyes and follow the breath."
    item_id = create.json()["id"]

    assert (await admin_client.get("/admin/content")).status_code == 200

    upd = await admin_client.patch(f"/admin/content/{item_id}", json={"title": "Renamed"})
    assert upd.status_code == 200 and upd.json()["title"] == "Renamed"
    # Untouched fields survive partial updates.
    assert upd.json()["narration_script"] == "Close your eyes and follow the breath."

    # The published item now shows in the public catalogue.
    pub = await admin_client.get("/content", params={"q": "Renamed"})
    assert any(c["id"] == item_id for c in pub.json())

    assert (await admin_client.delete(f"/admin/content/{item_id}")).status_code == 204
    assert (await admin_client.patch(f"/admin/content/{uuid.uuid4()}", json={"title": "x"})).status_code == 404
    assert (await admin_client.delete(f"/admin/content/{uuid.uuid4()}")).status_code == 404


async def test_admin_content_day_guides_edit(admin_client):
    guides = [{"title": f"Day {i}", "body": f"Guide {i}"} for i in range(1, 4)]
    create = await admin_client.post("/admin/content", json={
        "title": "Guides program", "kind": "program", "day_guides": guides,
    })
    assert create.status_code == 201 and create.json()["day_guides"] == guides
    item_id = create.json()["id"]

    # Round-trip an edit; untouched fields survive the partial update.
    new_guides = [{"title": "Day 1", "body": "Rewritten"}]
    upd = await admin_client.patch(f"/admin/content/{item_id}", json={"day_guides": new_guides})
    assert upd.status_code == 200 and upd.json()["day_guides"] == new_guides
    assert upd.json()["title"] == "Guides program"

    # Invalid shape (a guide without a title) → 422, nothing written.
    bad = await admin_client.patch(f"/admin/content/{item_id}", json={"day_guides": [{"body": "no title"}]})
    assert bad.status_code == 422
    listing = (await admin_client.get("/admin/content")).json()
    assert next(c for c in listing if c["id"] == item_id)["day_guides"] == new_guides

    # Explicit null clears the guides (back to a non-program row).
    cleared = await admin_client.patch(f"/admin/content/{item_id}", json={"day_guides": None})
    assert cleared.status_code == 200 and cleared.json()["day_guides"] is None

    assert (await admin_client.delete(f"/admin/content/{item_id}")).status_code == 204


async def test_admin_safety_queue_and_resolve(admin_client):
    # A crisis-flagged journal entry creates a safety event.
    await admin_client.post("/journal", json={"title": "dark", "body": "I want to end my life", "tags": []})
    listing = await admin_client.get("/admin/safety", params={"resolved": False})
    assert listing.status_code == 200
    events = listing.json()
    assert events, "expected a flagged safety event"
    event_id = events[0]["id"]
    resolved = await admin_client.patch(f"/admin/safety/{event_id}/resolve")
    assert resolved.status_code == 200 and resolved.json()["resolved"] is True
    assert (await admin_client.patch(f"/admin/safety/{uuid.uuid4()}/resolve")).status_code == 404


async def test_admin_waitlist_and_nudge_dispatch(admin_client, client):
    await client.post("/waitlist", json={"email": f"x-{uuid.uuid4().hex[:6]}@test.app"})
    assert (await admin_client.get("/admin/waitlist")).status_code == 200
    # Dispatch nudges (signup scheduled some; sends via the log fallback).
    r = await admin_client.post("/admin/nudges/dispatch")
    assert r.status_code == 200 and "sent" in r.json()
