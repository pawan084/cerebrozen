"""Finding an old entry.

Journaling only pays off if you can read it back — the module audit already
caught a version where you could write entries and never see one. Search closes
the second half of that: the client holds only the pages it has fetched, so
"I wrote about this months ago" is exactly the search that would miss if it ran
on-device.
"""
import uuid


async def _signup(client, prefix="jsearch"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": addr, "password": "password123", "name": "J"})
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return addr


async def _write(client, title, body="", tags=None):
    r = await client.post(
        "/journal", json={"title": title, "body": body, "tags": tags or [], "symbol": "book"}
    )
    assert r.status_code == 201, r.text
    return r.json()


async def test_search_matches_title_and_body_case_insensitively(client):
    await _signup(client)
    await _write(client, "Interview day", "felt Nervous but it went fine")
    await _write(client, "Quiet morning", "tea and nothing else")

    titles = [e["title"] for e in (await client.get("/journal", params={"q": "nervous"})).json()]
    assert titles == ["Interview day"], "body text must be searchable, not just the title"

    titles = [e["title"] for e in (await client.get("/journal", params={"q": "QUIET"})).json()]
    assert titles == ["Quiet morning"]


async def test_search_finds_nothing_gracefully(client):
    await _signup(client)
    await _write(client, "Something")
    assert (await client.get("/journal", params={"q": "zzzzz"})).json() == []


async def test_tag_filter_is_exact(client):
    await _signup(client)
    await _write(client, "Gym", tags=["body"])
    await _write(client, "Reading", tags=["mind", "evening"])

    titles = [e["title"] for e in (await client.get("/journal", params={"tag": "mind"})).json()]
    assert titles == ["Reading"]
    # A prefix must not match — "mind" is not "mindfulness".
    assert (await client.get("/journal", params={"tag": "min"})).json() == []


async def test_search_and_tag_combine(client):
    await _signup(client)
    await _write(client, "Walk", "long walk by the river", tags=["body"])
    await _write(client, "Swim", "long swim", tags=["body"])
    await _write(client, "Walk", "walk with a friend", tags=["people"])

    found = (await client.get("/journal", params={"q": "long", "tag": "body"})).json()
    assert sorted(e["title"] for e in found) == ["Swim", "Walk"]


async def test_tags_endpoint_lists_what_was_actually_used(client):
    await _signup(client)
    await _write(client, "One", tags=["evening", "work"])
    await _write(client, "Two", tags=["work", " "])

    tags = (await client.get("/journal/tags")).json()
    assert tags == ["evening", "work"], "de-duplicated, sorted, and no blank tag"


async def test_search_cannot_reach_another_account(client):
    await _signup(client, "owner")
    await _write(client, "Private thing", "a secret")

    await _signup(client, "stranger")
    assert (await client.get("/journal", params={"q": "secret"})).json() == []
    assert (await client.get("/journal/tags")).json() == []


async def test_a_search_term_that_looks_like_sql_is_just_text(client):
    await _signup(client)
    await _write(client, "Normal entry")
    r = await client.get("/journal", params={"q": "'; DROP TABLE journal_entries; --"})
    assert r.status_code == 200
    assert r.json() == []
    assert len((await client.get("/journal")).json()) == 1, "the table is still there"


async def test_wildcards_in_a_search_term_are_not_operators(client):
    """A user searching for "100%" means the characters, not "match everything"."""
    await _signup(client)
    await _write(client, "Alpha")
    await _write(client, "Battery at 100%", "nearly full")

    assert (await client.get("/journal", params={"q": "%"})).json() != [], "the literal % is findable"
    titles = [e["title"] for e in (await client.get("/journal", params={"q": "100%"})).json()]
    assert titles == ["Battery at 100%"]
    assert (await client.get("/journal", params={"q": "_"})).json() == [], (
        "the single-character wildcard is text too"
    )
