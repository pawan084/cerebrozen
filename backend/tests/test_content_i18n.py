"""The catalogue speaks the profile's language (2026-08-25).

The Hindi device walks' one untouchable finding: every programme, sound and
wind-down title arrived from the server in English inside fully-Hindi chrome.
`content_items.i18n` + `services/content_i18n.py` close it as a DISPLAY
overlay — and these tests pin the overlay's four load-bearing properties:
English stays canonical (search, admin), the profile is the only switch,
absence degrades field-by-field, and the enrollment's English snapshot is
overlaid at read time, never rewritten.
"""
import uuid

import pytest

from app.models.content import ContentItem
from app.services import content_i18n


async def _author(admin_client, **overrides):
    """Create a catalogue item the way an operator would — through the admin
    API, which is also what proves the i18n field is authorable at all.

    Titles carry a per-call suffix: the test database persists across the
    session, so a fixed title accumulates one row per test and a search
    assertion meets its own siblings (which is exactly how this suite's first
    run failed)."""
    tag = uuid.uuid4().hex[:6]
    payload = {
        "title": f"Evening unwind {tag}", "subtitle": "Wind down · 10 min",
        "kind": "meditation", "symbol": "moon", "image_url": "",
        "duration_min": 10, "premium": False, "published": True,
        "i18n": {"hi": {"title": f"शाम का विश्राम {tag}", "subtitle": "दिन धीमा करें · 10 मिनट"}},
    }
    payload.update(overrides)
    r = await admin_client.post("/admin/content", json=payload)
    assert r.status_code == 201, r.text
    return r.json()


@pytest.fixture()
async def bilingual_item(admin_client):
    return await _author(admin_client)


async def _set_language(auth_client, language):
    r = await auth_client.patch("/users/me", json={"language": language})
    assert r.status_code == 200, r.text


async def test_a_hindi_profile_sees_the_hindi_title(auth_client, bilingual_item):
    await _set_language(auth_client, "Hindi")
    r = await auth_client.get("/content")
    titles = [c["title"] for c in r.json()]
    assert bilingual_item["i18n"]["hi"]["title"] in titles
    assert bilingual_item["title"] not in titles


async def test_an_english_profile_sees_the_canonical_row(auth_client, bilingual_item):
    r = await auth_client.get("/content")
    rows = {c["title"]: c for c in r.json()}
    assert bilingual_item["title"] in rows
    assert rows[bilingual_item["title"]]["subtitle"] == "Wind down · 10 min"


async def test_a_language_with_no_translations_falls_back(auth_client, bilingual_item):
    # Punjabi has no i18n entries anywhere: the overlay maps it to no code
    # rather than to a guess, and the canonical row serves.
    await _set_language(auth_client, "Punjabi")
    r = await auth_client.get("/content")
    assert bilingual_item["title"] in [c["title"] for c in r.json()]


async def test_search_matches_the_english_row_for_a_hindi_profile(auth_client, bilingual_item):
    # English is canonical: a Hindi-profile user searching the English title
    # still finds the item — and receives it displayed in Hindi.
    await _set_language(auth_client, "Hindi")
    r = await auth_client.get("/content", params={"q": bilingual_item["title"]})
    titles = [c["title"] for c in r.json()]
    assert titles == [bilingual_item["i18n"]["hi"]["title"]]


async def test_partial_translations_degrade_by_field(auth_client, admin_client):
    await _author(
        admin_client,
        title=f"Night rain {uuid.uuid4().hex[:6]}", subtitle="Soundscape · 30 min", kind="soundscape",
        symbol="rain", duration_min=30,
        i18n={"hi": {"title": "रात की बारिश", "subtitle": ""}},   # blank must not blank
    )
    await _set_language(auth_client, "Hindi")
    r = await auth_client.get("/content")
    rows = {c["title"]: c["subtitle"] for c in r.json()}
    assert [v for k, v in rows.items() if k.startswith("रात की बारिश")] == ["Soundscape · 30 min"]


async def test_the_active_program_title_is_overlaid_not_rewritten(auth_client, admin_client):
    prog = await _author(
        admin_client,
        title=f"Reset week {uuid.uuid4().hex[:6]}", subtitle="7-day plan", kind="program",
        duration_min=0, i18n={"hi": {"title": "रीसेट सप्ताह", "subtitle": ""}},
    )
    r = await auth_client.post("/programs/enroll", json={"content_id": prog["id"], "days": 7})
    assert r.status_code == 201, r.text

    await _set_language(auth_client, "Hindi")
    r = await auth_client.get("/programs/active")
    assert r.json()["program"]["title"] == "रीसेट सप्ताह"

    # The overlay never rewrote the snapshot: back in English, English serves.
    await _set_language(auth_client, "English")
    r = await auth_client.get("/programs/active")
    assert r.json()["program"]["title"] == prog["title"]


def test_the_overlay_never_blanks_and_never_invents():
    item = ContentItem(title="X", subtitle="Y", kind="k", symbol="s",
                       image_url="", duration_min=1, premium=False, i18n=None)
    assert content_i18n.overrides(item, "hi") == {}
    item.i18n = {"hi": {"title": "  ", "subtitle": None}}
    assert content_i18n.overrides(item, "hi") == {}
    item.i18n = {"hi": {"title": "ठीक"}}
    assert content_i18n.overrides(item, None) == {}
