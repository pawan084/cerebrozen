"""The crisis helpline directory.

These tests exist because the failure they guard against is not a bug report — it is a
person in crisis dialling a number that does not answer in their country. The app shipped
with one country's numbers hardcoded into the client, while asking the user which region
they were in and ignoring the answer. So the invariants pinned here are blunt:

  - every input returns something dialable (there is no empty crisis screen);
  - the international finder is always reachable, in every region;
  - an unknown region gets neutral entries ONLY — never another country's numbers;
  - no local number leaks into the neutral fallback.
"""

from __future__ import annotations

import pytest

from app.safety.helplines import Helpline, for_region, regions


def _targets(rows: list[Helpline]) -> list[str]:
    return [r["target"] for r in rows]


# ── totality: the property that matters most ─────────────────────────────────


@pytest.mark.parametrize(
    "region",
    ["", None, "  ", "ZZ", "XX", "not-a-region", "IN ", " in", "in", "iN", "🙂", "US;DROP"],
)
def test_every_input_returns_something_dialable(region):
    """for_region is TOTAL. A crisis surface with nothing on it is the worst outcome
    available, so no input — junk, empty, None, whitespace, wrong case — may produce one."""
    rows = for_region(region)
    assert rows, f"{region!r} produced an empty crisis screen"
    assert all(r["target"].strip() for r in rows), f"{region!r} produced a row with no target"


def test_it_never_raises_on_hostile_input():
    # A crisis path must not have an exception in it. Whatever a client sends, answer.
    for junk in ["\x00", "\n", "A" * 500, "../../etc/passwd", "%%%"]:
        assert for_region(junk), f"{junk!r} broke the directory"


# ── the fallback is neutral, not somebody else's country ─────────────────────


def test_an_unknown_region_gets_neutral_entries_only():
    """The whole point. Someone whose region we don't know must NOT be handed India's or
    the US's numbers — that is the bug this module fixes, and it would be silent."""
    rows = for_region("ZZ")
    assert _targets(rows) == ["https://findahelpline.com"]


def test_the_neutral_fallback_contains_no_local_number():
    neutral = for_region(None)
    every_local = {t for r in regions() for t in _targets(for_region(r)) if not t.startswith("http")}
    assert every_local, "sanity: there should be local numbers to leak"
    assert not (set(_targets(neutral)) & every_local), "a country's number leaked into the neutral list"


def test_no_region_hardcodes_a_bare_emergency_number_as_the_only_row():
    # "112"-only would be a regression to the original bug in a different costume.
    for r in regions():
        assert len(for_region(r)) >= 2, f"{r} has no fallback beyond its local rows"


# ── the finder is always reachable ───────────────────────────────────────────


@pytest.mark.parametrize("region", [*regions(), "", "ZZ"])
def test_the_international_finder_is_always_present(region):
    """Even where we have good local numbers: they can be busy, or wrong for this person
    (a visitor), or the person may not want a government line."""
    assert any(r["target"] == "https://findahelpline.com" for r in for_region(region))


@pytest.mark.parametrize("region", regions())
def test_local_rows_come_before_the_finder(region):
    rows = for_region(region)
    assert rows[-1]["target"] == "https://findahelpline.com", "the finder must be the last resort"
    assert len(rows) > 1, f"{region} claims localization but has no local rows"


# ── shape: clients render this without parsing ───────────────────────────────


@pytest.mark.parametrize("region", [*regions(), "ZZ"])
def test_every_row_is_renderable_without_guessing(region):
    """`kind` exists so a client never has to sniff whether a target is a number or a
    URL. A client that guesses will eventually guess wrong on a crisis screen."""
    for row in for_region(region):
        assert set(row) == {"name", "detail", "target", "kind"}, row
        assert row["kind"] in {"tel", "url"}, row
        assert row["name"].strip() and row["detail"].strip(), row
        if row["kind"] == "url":
            assert row["target"].startswith("https://"), row
        else:
            # dialable: digits and dashes only — no words a dialer would choke on
            assert row["target"].replace("-", "").isdigit(), row


def test_case_and_whitespace_do_not_change_the_answer():
    assert for_region("in") == for_region("IN") == for_region("  In  ")


def test_regions_lists_only_localized_regions():
    for r in regions():
        assert len(for_region(r)) > len(for_region("ZZ")), f"{r} is listed but adds nothing"


# ── the HTTP surface ─────────────────────────────────────────────────────────


def _client():
    from fastapi.testclient import TestClient

    from app.main import create_app

    return TestClient(create_app(), raise_server_exceptions=False)


def test_endpoint_serves_the_region_the_caller_asked_for():
    body = _client().get("/v1/safety/helplines", params={"region": "GB"}).json()
    assert body["requested"] == "GB"
    assert body["localized"] is True
    assert "116123" in [h["target"] for h in body["helplines"]]


def test_endpoint_says_when_it_is_not_localized():
    """The client needs to know, so it can say "showing international" rather than
    implying these are the person's own country's numbers."""
    body = _client().get("/v1/safety/helplines", params={"region": "ZZ"}).json()
    assert body["localized"] is False
    assert [h["target"] for h in body["helplines"]] == ["https://findahelpline.com"]


def test_endpoint_with_no_region_is_a_200_not_a_422():
    # The common case: nobody has chosen a region. That is not a client error.
    r = _client().get("/v1/safety/helplines")
    assert r.status_code == 200
    assert r.json()["helplines"], "no region must still return something dialable"


def test_endpoint_does_not_500_on_an_over_long_region():
    # max_length=8 → FastAPI 422s rather than the handler blowing up mid-crisis.
    r = _client().get("/v1/safety/helplines", params={"region": "A" * 400})
    assert r.status_code == 422


def test_endpoint_advertises_the_regions_it_can_localize():
    body = _client().get("/v1/safety/helplines").json()
    assert set(body["regions"]) == set(regions())
    assert "IN" in body["regions"] and "US" in body["regions"]
