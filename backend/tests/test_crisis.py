"""Region-aware crisis resources (services/crisis)."""
from app.services import crisis


def test_known_regions_use_local_lines():
    assert "988" in crisis.reply_suffix("US")
    assert "999" in crisis.reply_suffix("GB")
    assert "116 123" in crisis.reply_suffix("GB")
    # Tele-MANAS leads India's lines (Tele-MANAS-first on every crisis surface);
    # the suffix renders the top two lines, so 14416 + emergency 112.
    assert "Tele-MANAS" in crisis.reply_suffix("IN")
    assert "14416" in crisis.reply_suffix("IN")
    assert "000" in crisis.reply_suffix("AU")


def test_region_is_case_insensitive():
    assert crisis.reply_suffix("gb") == crisis.reply_suffix("GB")
    assert crisis.lines_for("us") == crisis.lines_for("US")


def test_unknown_or_empty_region_falls_back_to_international():
    for region in ("", None, "ZZ", "XX"):
        suffix = crisis.reply_suffix(region)
        assert "112" in suffix
        assert "findahelpline.com" in suffix


def test_resources_payload_shape():
    payload = crisis.resources_for("US")
    assert payload["region"] == "US"
    assert payload["message"]
    assert payload["lines"][0]["number"] == "911"
    assert all({"name", "number"} <= set(line) for line in payload["lines"])


def test_no_region_leaks_india_default():
    # Regression: crisis replies must not hardcode India for non-IN users.
    assert "KIRAN" not in crisis.reply_suffix("US")
    assert "1800-599-0019" not in crisis.reply_suffix("GB")
