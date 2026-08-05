"""Wave 19 pins: bounded admin lists, readable attribution, renderable URLs.

Register E41-E44, E53-E54 — the operator surface stops growing without limit,
names its resolvers, and refuses content URLs that would run instead of render.
"""


async def test_admin_lists_accept_and_clamp_limits(admin_client):
    # Each list takes ?limit= and clamps garbage instead of 500ing.
    for path in (
        "/admin/users?limit=-5",
        "/admin/safety?limit=-5",
        "/admin/content?limit=-5",
        "/admin/media?limit=-5",
        "/admin/waitlist?limit=-5",
    ):
        r = await admin_client.get(path)
        assert r.status_code == 200, path


async def test_admin_users_search_matches_user_id(admin_client):
    me = (await admin_client.get("/users/me")).json()
    r = await admin_client.get(f"/admin/users?q={me['id']}")
    assert r.status_code == 200
    assert any(u["id"] == me["id"] for u in r.json())


async def test_safety_list_names_the_resolver(admin_client):
    # Create a flagged event via a crisis-worded journal entry, resolve it,
    # then the list must carry the resolver's EMAIL, not only the UUID.
    me = (await admin_client.get("/users/me")).json()
    r = await admin_client.post(
        "/journal", json={"title": "t", "body": "I want to end my life tonight"}
    )
    assert r.status_code == 201
    events = (await admin_client.get("/admin/safety?resolved=false")).json()
    assert events, "the crisis entry should have produced a safety event"
    event_id = events[0]["id"]
    r = await admin_client.patch(
        f"/admin/safety/{event_id}/resolve", json={"note": "reached out via test"}
    )
    assert r.status_code == 200, r.text
    resolved = (await admin_client.get("/admin/safety?resolved=true")).json()
    row = next(e for e in resolved if e["id"] == event_id)
    assert row["resolved_by_email"] == me["email"]


async def test_content_urls_must_be_renderable(admin_client):
    base = {"title": "t", "kind": "meditation"}
    bad = await admin_client.post(
        "/admin/content", json={**base, "image_url": "javascript:alert(1)"}
    )
    assert bad.status_code == 422
    ok = await admin_client.post(
        "/admin/content", json={**base, "image_url": "https://example.com/a.png", "audio_url": "/media/x.mp3"}
    )
    assert ok.status_code == 201
