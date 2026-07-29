"""The media catalogue: the keyed sounds/videos clients resolve, the admin CRUD +
upload that attaches real bytes to a key, and the /media route-ordering guarantee.

The load-bearing behaviour under test is the empty-url contract: a seeded key with
no bytes is a *valid* row that clients answer with their bundled fallback. Drop that
and every un-uploaded sound goes silent instead of degrading.
"""
import uuid
from pathlib import Path

from app.core.config import settings
from app.services import media as media_service


def _key() -> str:
    return f"game.test-{uuid.uuid4().hex[:8]}"


async def _create(admin_client, **overrides):
    payload = {"key": _key(), "kind": "game", "title": "Test sound", **overrides}
    r = await admin_client.post("/admin/media", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


# ── Public catalogue ────────────────────────────────────────────────────
async def test_catalog_is_public_and_resolves_before_the_static_mount(client):
    """/media/catalog must hit the API router, not the StaticFiles mount that
    shares the prefix. If someone reorders app.main, this fails loudly."""
    r = await client.get("/media/catalog")
    assert r.status_code == 200
    assert isinstance(r.json(), list)


async def test_catalog_exposes_seeded_keys_with_empty_urls(client, admin_client):
    """A key with no bytes is the point: it tells the client the sound exists so
    it can play its bundled/synthesized fallback."""
    asset = await _create(admin_client, title="Unuploaded")
    rows = (await client.get("/media/catalog")).json()
    match = next(a for a in rows if a["key"] == asset["key"])
    assert match["url"] == ""
    assert match["loop"] is False


async def test_catalog_filters_by_kind_and_hides_unpublished(client, admin_client):
    shown = await _create(admin_client, kind="ambience", loop=True)
    hidden = await _create(admin_client, kind="ambience", published=False)

    rows = (await client.get("/media/catalog", params={"kind": "ambience"})).json()
    keys = {a["key"] for a in rows}
    assert shown["key"] in keys
    assert hidden["key"] not in keys
    assert all(a["kind"] == "ambience" for a in rows)


# ── Admin CRUD ──────────────────────────────────────────────────────────
async def test_media_admin_routes_require_admin(auth_client):
    assert (await auth_client.get("/admin/media")).status_code == 403
    assert (await auth_client.post("/admin/media", json={"key": "a.b", "kind": "game"})).status_code == 403


async def test_create_rejects_bad_key_and_unknown_kind(admin_client):
    bad_key = await admin_client.post("/admin/media", json={"key": "../etc/passwd", "kind": "game"})
    assert bad_key.status_code == 422

    bad_kind = await admin_client.post("/admin/media", json={"key": _key(), "kind": "podcast"})
    assert bad_kind.status_code == 422


async def test_create_rejects_duplicate_key(admin_client):
    asset = await _create(admin_client)
    dupe = await admin_client.post("/admin/media", json={"key": asset["key"], "kind": "game"})
    assert dupe.status_code == 409


async def test_patch_updates_and_validates_kind(admin_client):
    asset = await _create(admin_client)
    ok = await admin_client.patch(f"/admin/media/{asset['id']}", json={"title": "Renamed", "loop": True})
    assert ok.status_code == 200
    assert ok.json()["title"] == "Renamed"
    assert ok.json()["loop"] is True

    bad = await admin_client.patch(f"/admin/media/{asset['id']}", json={"kind": "nonsense"})
    assert bad.status_code == 422


async def test_patch_and_delete_unknown_asset_404(admin_client):
    assert (await admin_client.patch(f"/admin/media/{uuid.uuid4()}", json={"title": "x"})).status_code == 404
    assert (await admin_client.delete(f"/admin/media/{uuid.uuid4()}")).status_code == 404


# ── Upload ──────────────────────────────────────────────────────────────
async def test_upload_attaches_bytes_and_serves_them(admin_client, client):
    asset = await _create(admin_client, kind="ambience", loop=True)

    r = await admin_client.post(
        f"/admin/media/{asset['id']}/upload",
        files={"file": ("rain.m4a", b"fake-m4a-bytes", "audio/mp4")},
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["url"] == f"/media/assets/{asset['key']}.m4a"
    assert body["mime"] == "audio/mp4"

    # It landed on disk…
    disk = Path(settings.media_root) / "assets" / f"{asset['key']}.m4a"
    assert disk.read_bytes() == b"fake-m4a-bytes"

    # …the public mount streams it without auth…
    served = await client.get(body["url"])
    assert served.status_code == 200
    assert served.content == b"fake-m4a-bytes"

    # …and it now shows up in the catalogue with a real URL.
    rows = (await client.get("/media/catalog")).json()
    assert next(a for a in rows if a["key"] == asset["key"])["url"] == body["url"]

    # Deleting the row cleans the file up.
    assert (await admin_client.delete(f"/admin/media/{asset['id']}")).status_code == 204
    assert not disk.exists()


async def test_reupload_with_a_new_extension_drops_the_old_file(admin_client):
    """Otherwise MEDIA_ROOT accretes orphans nothing references."""
    asset = await _create(admin_client, kind="ambience")
    await admin_client.post(
        f"/admin/media/{asset['id']}/upload",
        files={"file": ("bed.m4a", b"first", "audio/mp4")},
    )
    old = Path(settings.media_root) / "assets" / f"{asset['key']}.m4a"
    assert old.exists()

    r = await admin_client.post(
        f"/admin/media/{asset['id']}/upload",
        files={"file": ("bed.ogg", b"second", "audio/ogg")},
    )
    assert r.status_code == 200
    assert r.json()["url"].endswith(".ogg")
    assert not old.exists()
    assert (Path(settings.media_root) / "assets" / f"{asset['key']}.ogg").read_bytes() == b"second"


async def test_upload_rejects_unsupported_format(admin_client):
    asset = await _create(admin_client)
    r = await admin_client.post(
        f"/admin/media/{asset['id']}/upload",
        files={"file": ("payload.exe", b"MZ", "application/octet-stream")},
    )
    assert r.status_code == 415


async def test_upload_rejects_empty_file(admin_client):
    asset = await _create(admin_client)
    r = await admin_client.post(
        f"/admin/media/{asset['id']}/upload",
        files={"file": ("silence.mp3", b"", "audio/mpeg")},
    )
    assert r.status_code == 400


async def test_upload_rejects_oversized_file(admin_client):
    from app.api.routes import admin as admin_routes

    asset = await _create(admin_client)
    too_big = b"x" * (admin_routes._MAX_ASSET_BYTES + 1)
    r = await admin_client.post(
        f"/admin/media/{asset['id']}/upload",
        files={"file": ("huge.mp4", too_big, "video/mp4")},
    )
    assert r.status_code == 413


async def test_upload_unknown_asset_404(admin_client):
    r = await admin_client.post(
        f"/admin/media/{uuid.uuid4()}/upload",
        files={"file": ("a.mp3", b"x", "audio/mpeg")},
    )
    assert r.status_code == 404


# ── Key hygiene (keys become filenames — this is the traversal guard) ────
def test_valid_key_rejects_traversal_and_separators():
    assert media_service.valid_key("ambience.rain")
    assert media_service.valid_key("game.pad.0")
    assert not media_service.valid_key("../escape")
    assert not media_service.valid_key("a/b")
    assert not media_service.valid_key("a\\b")
    assert not media_service.valid_key("Ambience.Rain")   # uppercase
    assert not media_service.valid_key("")
    assert not media_service.valid_key(".hidden")         # must start alphanumeric


# ── Scene video on content items ────────────────────────────────────────
async def test_content_item_carries_video_url(admin_client, client):
    r = await admin_client.post(
        "/admin/content",
        json={
            "title": f"Scene item {uuid.uuid4().hex[:6]}",
            "kind": "sleep",
            "video_url": "/media/assets/scene.night_lake.mp4",
        },
    )
    assert r.status_code == 201
    item = r.json()
    assert item["video_url"] == "/media/assets/scene.night_lake.mp4"

    pub = (await client.get("/content", params={"q": item["title"]})).json()
    assert next(c for c in pub if c["id"] == item["id"])["video_url"] == item["video_url"]


async def test_content_video_url_defaults_empty(admin_client):
    """Empty = clients render their generative artwork; we ship no video yet."""
    r = await admin_client.post(
        "/admin/content",
        json={"title": f"No scene {uuid.uuid4().hex[:6]}", "kind": "meditation"},
    )
    assert r.status_code == 201
    assert r.json()["video_url"] == ""
