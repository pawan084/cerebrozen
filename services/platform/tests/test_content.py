"""The rest-and-recovery catalog: scenes, soundscapes, wind-down, media keys, and
multi-day program enrolment.

The catalog is static app configuration serving the phone's OWN bundled audio, so these
tests pin the CONTRACT the app parses (the exact keys, the null envelope, the derived
day) rather than any particular scene — a title can change; the shape cannot.
"""

from datetime import datetime, timedelta, timezone

import pytest

from app import catalog


async def _member(client, org_with_admin):
    from app.db import SessionLocal
    from app.models import Invitation
    from app.security import new_opaque_token

    raw, token_hash = new_opaque_token()
    async with SessionLocal() as session:
        session.add(
            Invitation(
                org_id=org_with_admin["org"]["id"],
                email="rester@acme.example",
                role="user",
                token_hash=token_hash,
                expires_at=datetime.now(timezone.utc) + timedelta(days=1),
            )
        )
        await session.commit()
    r = await client.post(
        "/auth/accept-invitation",
        json={"token": raw, "name": "Rester", "password": "hunter2hunter2"},
    )
    assert r.status_code == 201, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


# ── the sleep/soundscape/wind-down lists ─────────────────────────────────────


@pytest.mark.parametrize("kind", ["sleep", "soundscape", "wind_down"])
async def test_a_scene_list_comes_back_populated_and_in_the_app_shape(client, org_with_admin, kind):
    """This is the fix: these kinds used to 404, and the app printed the raw 'Not Found'
    where the list should be. Now they return a populated list in the shape ContentList
    parses (title/subtitle/duration_min/premium/image_url/audio_url)."""
    headers = await _member(client, org_with_admin)

    r = await client.get(f"/content?kind={kind}", headers=headers)

    assert r.status_code == 200
    items = r.json()
    assert len(items) >= 3, f"{kind} came back empty"
    for item in items:
        assert set(item) >= {"title", "subtitle", "duration_min", "premium", "image_url", "audio_url"}
        # audio_url blank on purpose — the phone plays its bundled ambient bed, so the
        # scene works with no licensed media. The day real narration exists, its URL
        # drops in here and nothing else changes.
        assert item["audio_url"] == ""
        assert item["premium"] is False


async def test_an_unknown_kind_is_an_empty_list_not_an_error(client, org_with_admin):
    """A kind the catalog doesn't know yet must not 500 the screen — the app renders a
    friendly empty state from `[]`, and adding a kind client-first stays safe."""
    headers = await _member(client, org_with_admin)

    r = await client.get("/content?kind=nonsense", headers=headers)

    assert r.status_code == 200 and r.json() == []


async def test_content_needs_a_session(client):
    assert (await client.get("/content?kind=sleep")).status_code == 401


# ── the keyed media catalogue ────────────────────────────────────────────────


async def test_the_media_catalogue_lists_the_keys_with_blank_urls(client, org_with_admin):
    """MediaCatalog used to 404, so the phone silently fell back for every sound. Now it
    loads — every key present, urls blank (bundled/synth fallback), loop flags set. When
    real files exist, only the urls change."""
    headers = await _member(client, org_with_admin)

    r = await client.get("/media/catalog", headers=headers)

    assert r.status_code == 200
    entries = r.json()
    keys = {e["key"] for e in entries}
    assert {"ambience.rain", "scene.night_lake", "game.pad.0", "chime.timer_bell"} <= keys
    for entry in entries:
        assert set(entry) == {"key", "url", "loop"}
        assert entry["url"] == ""
    beds = {e["key"]: e["loop"] for e in entries}
    assert beds["ambience.rain"] is True, "a bed must loop"
    assert beds["game.pad.0"] is False, "a one-shot must not loop"


# ── programs (the Journeys tab) ──────────────────────────────────────────────


async def test_the_program_list_names_the_authored_journeys(client, org_with_admin):
    headers = await _member(client, org_with_admin)

    r = await client.get("/content?kind=program", headers=headers)

    assert r.status_code == 200
    programs = {p["id"]: p for p in r.json()}
    # The two originals plus the three the Journeys tab promises.
    assert {"better-sleep", "steady-focus", "the-feedback-conversation",
            "delegation-that-sticks", "quiet-influence"} <= set(programs)
    for program in programs.values():
        assert set(program) == {"id", "title", "subtitle"}, "the app parses exactly these"


def test_every_program_is_well_formed():
    """A malformed program is a broken journey in someone's hands — a blank day guide, a
    zero-day program, a title the app can't show. Assert the shape for ALL of them so a
    future addition can't slip a hole through."""
    assert len(catalog.PROGRAMS) >= 5
    for pid, program in catalog.PROGRAMS.items():
        assert program["title"].strip() and program["subtitle"].strip(), f"{pid} missing copy"
        days = program["days"]
        assert len(days) >= 3, f"{pid} is too short to be a multi-day journey"
        for i, day in enumerate(days):
            assert day["title"].strip(), f"{pid} day {i + 1} has no title"
            assert len(day["body"].strip()) >= 20, f"{pid} day {i + 1} body is thin or blank"
        # Every day must be reachable: payload for the LAST day resolves and completes.
        from datetime import timedelta

        far_back = datetime.now(timezone.utc) - timedelta(days=len(days) + 5)
        payload = catalog.program_payload(pid, far_back)
        assert payload["day"] == len(days) and payload["completed"] is True


async def test_not_enrolled_is_a_null_envelope(client, org_with_admin):
    """The app reads `optJSONObject("program")` — null means 'not enrolled', and the
    hero simply doesn't render. A bare null or a 404 would both be wrong."""
    headers = await _member(client, org_with_admin)

    r = await client.get("/programs/active", headers=headers)

    assert r.status_code == 200 and r.json() == {"program": None}


async def test_enrolling_starts_the_program_on_day_one(client, org_with_admin):
    headers = await _member(client, org_with_admin)

    r = await client.post("/programs/enroll", json={"content_id": "better-sleep"}, headers=headers)

    assert r.status_code == 200
    program = r.json()["program"]
    assert program["day"] == 1
    assert program["days"] == 7
    assert program["completed"] is False
    assert program["today_guide"]["title"] and program["today_guide"]["body"]
    # And it persists to the next read.
    active = (await client.get("/programs/active", headers=headers)).json()["program"]
    assert active["id"] == "better-sleep" and active["day"] == 1


async def test_the_current_day_is_derived_from_the_enrolment_date(client, org_with_admin):
    """Day climbs by the calendar, not by an endpoint — so it can't drift. Enrol, then
    backdate the start three days and the program is on day 4 with the matching guide."""
    from sqlalchemy import select

    from app.db import SessionLocal
    from app.models import User

    headers = await _member(client, org_with_admin)
    await client.post("/programs/enroll", json={"content_id": "better-sleep"}, headers=headers)

    async with SessionLocal() as session:
        user = (
            await session.execute(select(User).where(User.email == "rester@acme.example"))
        ).scalar_one()
        user.program_started_at = datetime.now(timezone.utc) - timedelta(days=3)
        await session.commit()

    program = (await client.get("/programs/active", headers=headers)).json()["program"]
    assert program["day"] == 4
    expected = catalog.PROGRAMS["better-sleep"]["days"][3]["title"]
    assert program["today_guide"]["title"] == expected


async def test_a_finished_program_is_marked_completed_and_stays_on_the_last_day(
    client, org_with_admin
):
    from sqlalchemy import select

    from app.db import SessionLocal
    from app.models import User

    headers = await _member(client, org_with_admin)
    await client.post("/programs/enroll", json={"content_id": "steady-focus"}, headers=headers)

    async with SessionLocal() as session:
        user = (
            await session.execute(select(User).where(User.email == "rester@acme.example"))
        ).scalar_one()
        user.program_started_at = datetime.now(timezone.utc) - timedelta(days=30)
        await session.commit()

    program = (await client.get("/programs/active", headers=headers)).json()["program"]
    assert program["completed"] is True
    assert program["day"] == program["days"], "the day never runs past the end"


async def test_leaving_clears_the_enrolment(client, org_with_admin):
    headers = await _member(client, org_with_admin)
    await client.post("/programs/enroll", json={"content_id": "better-sleep"}, headers=headers)

    r = await client.delete("/programs/active", headers=headers)

    assert r.status_code == 204
    assert (await client.get("/programs/active", headers=headers)).json() == {"program": None}


async def test_enrolling_in_a_program_that_does_not_exist_is_a_404(client, org_with_admin):
    headers = await _member(client, org_with_admin)

    r = await client.post("/programs/enroll", json={"content_id": "no-such-program"}, headers=headers)

    assert r.status_code == 404


async def test_deleting_the_account_clears_the_enrolment(client, org_with_admin):
    from sqlalchemy import select

    from app.db import SessionLocal
    from app.models import User

    headers = await _member(client, org_with_admin)
    await client.post("/programs/enroll", json={"content_id": "better-sleep"}, headers=headers)

    await client.delete("/users/me?confirm=true", headers=headers)

    async with SessionLocal() as session:
        rows = (await session.execute(select(User))).scalars().all()
    scrubbed = [u for u in rows if not u.is_active]
    assert scrubbed and all(u.active_program_id == "" for u in scrubbed)


# ── the catalog module's own logic (no HTTP) ─────────────────────────────────


def test_program_payload_is_none_when_not_enrolled():
    assert catalog.program_payload("", None) is None
    assert catalog.program_payload("better-sleep", None) is None
    assert catalog.program_payload("ghost", datetime.now(timezone.utc)) is None


def test_a_naive_start_date_is_treated_as_utc_not_a_crash():
    """SQLite hands back naive datetimes; the day math must not blow up on a missing
    tzinfo."""
    naive = datetime.now().replace(tzinfo=None)
    payload = catalog.program_payload("better-sleep", naive)
    assert payload is not None and payload["day"] == 1
