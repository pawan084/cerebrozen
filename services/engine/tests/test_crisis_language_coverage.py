"""The crisis-reply safety invariant across every detected language.

Every language the screen DETECTS now has a written reply (backlog #1). That is a
different claim from every language having a reply a speaker of it has read, and this file
pins both: the invariant that nobody is answered with silence, and the honesty registry
(`_NATIVE_REVIEWED`) that stops "N languages" being quoted at the drafted number.

What must never regress: whatever language the screen flags, the person gets a NON-EMPTY
reply, IN THAT LANGUAGE'S SCRIPT, that CONTAINS A WAY TO GET HELP. English fallback stays
wired for anything unrecognised — fallback is a feature; silence is a bug. A future edit
that dropped a language, mangled the `{line}` placeholder, or pasted English text under a
non-Latin key trips this immediately.
"""

from __future__ import annotations

import logging

import pytest

from app.graph.crisis import (
    _AI_DISCLOSURE,
    _MESSAGES,
    _NATIVE_REVIEWED,
    languages_covered,
    reply_languages,
    safe_response,
)
from app.llm.prompts import CRISIS_LINE


def _english_reply() -> str:
    """The full English reply as served: body + the appended AI disclosure."""
    return _MESSAGES["en"].format(line=CRISIS_LINE) + " " + _AI_DISCLOSURE["en"]


def test_the_crisis_line_actually_resolves():
    """The helpline every reply points at must be a real, non-empty target."""
    assert CRISIS_LINE and CRISIS_LINE.strip(), "the crisis line resolved to nothing"


def test_every_detected_language_gets_a_reply_that_names_help():
    """The core invariant: no language the screen can flag may produce an empty or
    helpline-less reply. English fallback counts — silence does not."""
    for lang in languages_covered():
        reply = safe_response(lang)
        assert reply and len(reply) > 40, f"[{lang}] crisis reply was empty or truncated"
        assert CRISIS_LINE in reply, f"[{lang}] crisis reply did not carry the helpline"


def test_no_detected_language_falls_back_to_english_any_more():
    """Backlog #1: detection spanned ~20 languages while replies existed for 7, so a
    Japanese speaker was flagged correctly and answered in English. Closed — and this test
    is what keeps it closed when the next language is added to the lexicon.

    `hi-latn` (romanised Hindi) is the one intentional alias: `_matched_language` splits
    the region off before replying, so it is served by `hi`.
    """
    english = safe_response("en")
    missing = [
        lang
        for lang in languages_covered()
        if lang != "en" and safe_response(lang.split("-")[0]) == english
    ]
    assert not missing, f"detected but answered in English: {missing}"


@pytest.mark.parametrize(
    "lang,sample",
    [
        ("zh", "你"),      # CJK
        ("ja", "あ"),      # hiragana
        ("ko", "이"),      # hangul
        ("th", "ค"),      # thai
        ("ar", "أ"),      # arabic
        ("he", "א"),      # hebrew
        ("hi", "आ"),      # devanagari
        ("ru", "С"),      # cyrillic
    ],
)
def test_a_non_latin_reply_is_actually_in_its_own_script(lang, sample):
    """Guards the paste error a reviewer cannot see: English text filed under `ja` would
    satisfy every other assertion here — same length, same helpline, not the English
    fallback string — and ship as 'Japanese crisis support'."""
    reply = safe_response(lang)
    assert sample in reply, f"[{lang}] reply does not contain the script it claims to be in"


def test_an_unknown_language_still_gets_the_safe_english_reply():
    """A language we do not detect at all (a client term file in a new language, or an
    odd locale tag) must still fall back safely, never raise, never blank."""
    reply = safe_response("xx-nonexistent")
    assert reply and CRISIS_LINE in reply
    assert reply == _english_reply()


def test_scripted_languages_answer_in_their_own_language_not_english():
    """The languages we have replies for must actually serve them — proof the scripted
    path works and hasn't silently collapsed to the English fallback."""
    english = safe_response("en")
    for lang in _MESSAGES:
        if lang == "en":
            continue
        reply = safe_response(lang)
        assert reply != english, f"[{lang}] fell through to the English reply despite being scripted"
        assert CRISIS_LINE in reply


# ── the honesty registry ─────────────────────────────────────────────────────

def test_the_review_registry_cannot_claim_a_language_we_do_not_have():
    """`_NATIVE_REVIEWED` is what the marketing number is drawn from, so it may only name
    languages that exist in the table. A reviewed-but-absent language is a claim with no
    mechanism behind it (rule 6)."""
    assert _NATIVE_REVIEWED <= set(_MESSAGES), (
        f"claimed as reviewed but not written: {sorted(_NATIVE_REVIEWED - set(_MESSAGES))}"
    )


def test_reviewed_and_drafted_together_are_exactly_the_table():
    reviewed, drafted = reply_languages()
    assert set(reviewed) | set(drafted) == set(_MESSAGES)
    assert not set(reviewed) & set(drafted)
    assert "en" in reviewed, "the source language must count as reviewed"


def test_serving_an_unreviewed_language_is_logged_not_suppressed(caplog):
    """The drafted reply is SERVED — a message in the person's own language beats a
    fluent one they cannot read — and the fact that it was drafted is recorded, so the
    translation queue is ordered by real crises rather than by sales interest."""
    with caplog.at_level(logging.WARNING, logger="cerebrozen.crisis"):
        reply = safe_response("ja")
    assert reply and CRISIS_LINE in reply
    assert any(r.msg == "crisis.reply_language_unreviewed" for r in caplog.records)


def test_a_reviewed_language_is_served_silently(caplog):
    with caplog.at_level(logging.WARNING, logger="cerebrozen.crisis"):
        safe_response("en")
    assert not [r for r in caplog.records if r.msg == "crisis.reply_language_unreviewed"]


def test_a_malformed_override_falls_back_instead_of_blanking(tmp_path, monkeypatch, caplog):
    """A client's translation with a typo'd placeholder ({helpline} instead of {line}) is a
    config mistake made months earlier, discovered at the one moment it cannot be fixed.
    It must degrade to the English reply, never to an exception or an empty string."""
    import json

    path = tmp_path / "messages.json"
    path.write_text(json.dumps({"ja": "連絡先: {helpline}"}), encoding="utf-8")
    monkeypatch.setenv("CEREBROZEN_CRISIS_MESSAGES_FILE", str(path))

    with caplog.at_level(logging.ERROR, logger="cerebrozen.crisis"):
        reply = safe_response("ja")
    assert reply == _english_reply()
    assert any(r.msg == "crisis.message_template_invalid" for r in caplog.records)


def test_the_strict_posture_serves_english_for_anything_unreviewed(monkeypatch, caplog):
    """CEREBROZEN_CRISIS_REVIEWED_ONLY is docs/SECURITY.md's inherited commitment, kept
    available for a deployment under clinical governance: no unreviewed sentence reaches a
    person in crisis, at the cost of a message they may not be able to read. It must still
    reply — the strict posture narrows the language, never the response."""
    monkeypatch.setenv("CEREBROZEN_CRISIS_REVIEWED_ONLY", "1")
    with caplog.at_level(logging.WARNING, logger="cerebrozen.crisis"):
        reply = safe_response("ja")
    assert reply == safe_response("en"), "the strict posture must fall back, not blank"
    assert CRISIS_LINE in reply
    assert any(r.msg == "crisis.reply_language_suppressed_unreviewed" for r in caplog.records), (
        "suppressing a drafted reply must be distinguishable in the log from not having one"
    )


def test_the_strict_posture_still_serves_a_client_reviewed_override(tmp_path, monkeypatch):
    """The override file IS the client's reviewed translation, so the strict posture must
    not discard it — otherwise the one path to a properly-reviewed non-English reply is
    switched off by the setting that exists to demand one."""
    import json

    path = tmp_path / "messages.json"
    path.write_text(json.dumps({"ja": "確認済みの返信です。{line} に連絡してください。"}), encoding="utf-8")
    monkeypatch.setenv("CEREBROZEN_CRISIS_MESSAGES_FILE", str(path))
    monkeypatch.setenv("CEREBROZEN_CRISIS_REVIEWED_ONLY", "1")

    reply = safe_response("ja")
    assert "確認済み" in reply and CRISIS_LINE in reply


def test_a_client_override_is_never_flagged_as_unreviewed(tmp_path, monkeypatch, caplog):
    """The override file is how a client supplies THEIR OWN reviewed translation. Warning
    that it is unreviewed would be false, and would train ops to ignore the warning that
    is true."""
    import json

    path = tmp_path / "messages.json"
    path.write_text(json.dumps({"ja": "確認済みの返信です。{line} に連絡してください。"}), encoding="utf-8")
    monkeypatch.setenv("CEREBROZEN_CRISIS_MESSAGES_FILE", str(path))

    with caplog.at_level(logging.WARNING, logger="cerebrozen.crisis"):
        reply = safe_response("ja")
    assert CRISIS_LINE in reply
    assert "確認済み" in reply, "the client's reviewed reply was not the one served"
    assert not [r for r in caplog.records if r.msg == "crisis.reply_language_unreviewed"]


# ── #22: the AI disclosure inside the takeover reply (CA/NY mandate) ─────────

def test_every_crisis_reply_discloses_that_it_is_an_AI():
    """The takeover is the moment a person is least able to infer what they are talking to
    and most harmed by getting it wrong: warm, immediate, and arriving right after a
    disclosure a human would have had to react to. Every language, no exceptions."""
    for lang in languages_covered():
        served = lang.split("-")[0]
        reply = safe_response(served)
        assert _AI_DISCLOSURE[served] in reply, f"[{lang}] crisis reply never says it is an AI"


def test_the_disclosure_comes_last_not_first():
    """A person in crisis may read two sentences. Those two must be "I'm glad you told me"
    and the helpline — not a disclaimer about what I am. Conspicuous need not mean first."""
    reply = safe_response("en")
    assert reply.endswith(_AI_DISCLOSURE["en"])
    assert reply.index(CRISIS_LINE) < reply.index(_AI_DISCLOSURE["en"])


def test_a_client_cannot_override_the_disclosure_away(tmp_path, monkeypatch):
    """The override file exists so a clinical team can improve the reply body. Editing a
    legally-required sentence out of the message is the one change it must not be able to
    make — so the disclosure is appended AFTER the override, not part of what it replaces."""
    import json

    path = tmp_path / "messages.json"
    path.write_text(json.dumps({"en": "Call {line}."}), encoding="utf-8")
    monkeypatch.setenv("CEREBROZEN_CRISIS_MESSAGES_FILE", str(path))

    reply = safe_response("en")
    assert reply.startswith("Call ")                      # the client's body won
    assert _AI_DISCLOSURE["en"] in reply                  # and the disclosure survived


def test_the_strict_posture_does_not_smuggle_in_an_unreviewed_disclosure(monkeypatch):
    """CEREBROZEN_CRISIS_REVIEWED_ONLY refuses unreviewed sentences. The disclosure is a
    sentence too — it must fall back to English with the body, not arrive in Japanese
    attached to an English reply."""
    monkeypatch.setenv("CEREBROZEN_CRISIS_REVIEWED_ONLY", "1")
    reply = safe_response("ja")
    assert _AI_DISCLOSURE["en"] in reply
    assert _AI_DISCLOSURE["ja"] not in reply


@pytest.mark.parametrize("lang", sorted(_AI_DISCLOSURE))
def test_the_disclosure_exists_for_every_language_we_reply_in(lang):
    """Symmetry guard: a language with a reply but no disclosure would silently serve the
    English one appended to a Thai sentence."""
    assert lang in _MESSAGES, f"[{lang}] has a disclosure but no reply"
    assert _AI_DISCLOSURE[lang].strip()


def test_every_reply_language_has_a_disclosure():
    missing = sorted(set(_MESSAGES) - set(_AI_DISCLOSURE))
    assert not missing, f"reply written but no AI disclosure: {missing}"
