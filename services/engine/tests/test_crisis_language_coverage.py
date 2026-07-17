"""The crisis-reply safety invariant across every detected language.

The screen DETECTS ~20 languages and has native-reviewed replies written for a handful; the
rest fall back to English + a region-routing helpline. That gap is real and logged, and the
fix for it is native-speaker-reviewed translations supplied via CEREBROZEN_CRISIS_MESSAGES_
FILE — NOT machine translation, because a wrong crisis message is worse than an English one
(see the warning at the top of app/graph/crisis.py).

What must never regress, and is pinned here: whatever language the screen flags, the person
gets a NON-EMPTY reply that CONTAINS A WAY TO GET HELP. Fallback is a feature; silence is a
bug. A future edit that dropped English, mangled the `{line}` placeholder, or let an
unscripted language return "" would trip this immediately.
"""

from __future__ import annotations

from app.graph.crisis import _MESSAGES, languages_covered, safe_response
from app.llm.prompts import CRISIS_LINE


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


def test_an_unknown_language_still_gets_the_safe_english_reply():
    """A language we do not detect at all (so _matched_language returned something odd)
    must still fall back safely, never raise, never blank."""
    reply = safe_response("xx-nonexistent")
    assert reply and CRISIS_LINE in reply
    assert reply == _MESSAGES["en"].format(line=CRISIS_LINE)


def test_scripted_languages_answer_in_their_own_language_not_english():
    """The languages we DO have reviewed replies for must actually serve them — proof the
    scripted path works and hasn't silently collapsed to the English fallback."""
    english = safe_response("en")
    for lang in _MESSAGES:
        if lang == "en":
            continue
        reply = safe_response(lang)
        assert reply != english, f"[{lang}] fell through to the English reply despite being scripted"
        assert CRISIS_LINE in reply
