"""The second safety layer: the classifier, and the escalation behind it.

`test_crisis_redteam.py` measures the LEXICON — the 1ms, free, offline floor — and pins it
at its real, bad number (1/22). This file tests the layer that makes the screen actually
work, and the thing that finally tells a human.

Measured on the real provider, against the same red team:

    lexicon alone .............  1 / 22
    lexicon + classifier ...... 20 / 22
    false positives ...........  1 / 7 ordinary workplace idiom

That measurement needs a live model, so it is not what this file does. What this file
guards is everything that must hold *regardless* of how good the model is — the wiring, the
ordering, the fail-safes, and the two properties that are the entire safety architecture:

  1. **The takeover stays deterministic.** The classifier decides IF. It never decides WHAT
     to say, and the coaching model is never consulted about whether to escalate.
  2. **Nothing here may ever cost a user their reply.** Every failure path — provider down,
     timeout, junk JSON, webhook on fire — degrades to the lexicon's verdict and lets the
     turn continue.
"""

import json

import pytest

from app.graph import crisis, crisis_classifier
from app.safety import escalation


class FakeResp:
    def __init__(self, text):
        self.text = text
        self.prompt_tokens = self.completion_tokens = self.total_tokens = 0
        self.cost_usd = 0.0
        self.model = "fake"


class FakeProvider:
    """Scripted, so the test asserts OUR behaviour rather than a model's opinion."""

    def __init__(self, text="", raises=None):
        self.text, self.raises, self.calls = text, raises, []

    def generate(self, *, system_prompt, user_prompt, model, **kw):
        self.calls.append({"system": system_prompt, "user": user_prompt, "model": model})
        if self.raises:
            raise self.raises
        return FakeResp(self.text)


@pytest.fixture
def provider(monkeypatch):
    def _install(text="", raises=None):
        p = FakeProvider(text, raises)
        monkeypatch.setattr("app.llm.providers.get_provider", lambda: p)
        monkeypatch.setenv("CEREBROZEN_CRISIS_CLASSIFIER", "true")
        return p

    return _install


# ── ordering: the lexicon is a FLOOR, not a fallback ─────────────────────────

def test_the_lexicon_short_circuits_and_the_model_is_never_called(provider):
    """An explicit disclosure must be caught in 1ms, for free, with no network.

    If we asked the model for a second opinion on "I want to kill myself", we would be
    paying money and adding 300ms to be told what a word list already knew — and we would
    have made the most reliable path in the system depend on a provider being up.
    """
    p = provider(json.dumps({"crisis": False, "reason": "wrong"}))

    flag, _lang, why = crisis.full_screen("I want to kill myself")

    assert flag == "crisis"
    assert why == "lexicon"
    assert p.calls == [], "the model was consulted about a message the lexicon had already caught"


def test_the_classifier_catches_what_the_lexicon_cannot(provider):
    """The entire reason this layer exists. No keyword appears in this sentence."""
    p = provider(json.dumps({"crisis": True, "reason": "passive ideation"}))

    flag, _lang, why = crisis.full_screen("I just want to go to sleep and not wake up")

    assert flag == "crisis"
    assert why == "passive ideation"
    assert len(p.calls) == 1, "the classifier never ran on a message the lexicon missed"


def test_ordinary_distress_is_not_escalated(provider):
    """A screen that fires on burnout gets switched off within a week — and a screen someone
    has switched off protects nobody."""
    provider(json.dumps({"crisis": False, "reason": "work frustration"}))

    flag, _lang, why = crisis.full_screen("this deadline is killing me and I'm exhausted")

    assert flag == "ok"
    assert why == "classifier_clear"


# ── fail-safe: the direction of failure is the whole design ──────────────────

@pytest.mark.parametrize("failure,label", [
    (TimeoutError("provider timed out"), "timeout"),
    (RuntimeError("provider is down"), "outage"),
])
def test_a_provider_failure_degrades_to_the_lexicon_and_never_raises(provider, failure, label):
    """A crisis screen that can take down a turn is a crisis screen someone will switch off.

    On any provider failure we return the LEXICON's verdict — so the screen degrades to
    exactly what we had before this layer existed, rather than to nothing, and the user still
    gets a reply.
    """
    provider(raises=failure)

    flag, _lang, why = crisis.full_screen("I just want to go to sleep and not wake up")

    assert flag == "ok", f"the {label} should degrade to the lexicon verdict, not raise"
    assert why == "classifier_error"


def test_the_takeover_fires_end_to_end_even_with_the_provider_down(provider):
    """#21: a lexicon-detectable crisis must take over the turn with NO working model. The
    lexicon is the zero-token floor, so the provider being down cannot cost a person their
    helpline — full_screen returns 'crisis' via the lexicon and the scripted reply is served."""
    provider(raises=RuntimeError("provider is down"))

    flag, lang, why = crisis.full_screen("i want to end my life")

    assert flag == "crisis" and why == "lexicon", "the takeover must not depend on the model"
    assert crisis.safe_response(lang).strip(), "the scripted reply is served regardless"


def test_junk_from_the_model_degrades_safely(provider):
    """Models return prose, markdown fences and half-JSON. None of that may reach a user as
    a stack trace."""
    provider("I'm sorry, I can't help with that request.")

    flag, _lang, why = crisis.full_screen("I've been giving my things away")

    assert flag == "ok" and why == "classifier_error"


def test_a_fenced_json_reply_is_still_understood(provider):
    """Models fence JSON in markdown even when told not to. That is not a failure and must
    not be treated as one — it would silently halve the recall of the safety layer."""
    provider('```json\n{"crisis": true, "reason": "planning"}\n```')

    flag, _lang, why = crisis.full_screen("I've been giving my things away")

    assert flag == "crisis" and why == "planning"


def test_the_classifier_can_be_switched_off_but_it_is_ON_by_default(provider, monkeypatch):
    """Off-by-default for a safety layer is a decision nobody remembers making."""
    assert crisis_classifier.enabled() is True

    p = provider(json.dumps({"crisis": True, "reason": "x"}))
    monkeypatch.setenv("CEREBROZEN_CRISIS_CLASSIFIER", "false")

    flag, _lang, why = crisis.full_screen("I wish I could just disappear")

    assert flag == "ok" and why == "classifier_off"
    assert p.calls == []


def test_the_prompt_tells_the_model_to_err_towards_flagging(provider):
    """The calibration IS the safety property. A false positive shows a helpline to someone
    speaking figuratively — jarring, survivable. A false negative is what this exists to
    prevent. If someone 'tunes down the noise', this test fails."""
    p = provider(json.dumps({"crisis": False, "reason": "x"}))
    crisis.full_screen("I don't see the point of any of this anymore")

    sent = p.calls[0]["system"].lower()
    assert "unsure" in sent and "true" in sent, "the err-toward-flagging instruction is gone"


def test_only_a_truncated_message_is_sent_and_never_the_history(provider):
    """The classifier sees ONE message, capped. It is not given the transcript — a screen
    does not need a person's history, and every extra token is more of their disclosure
    sitting in a provider's logs."""
    p = provider(json.dumps({"crisis": False, "reason": "x"}))
    crisis.full_screen("x" * 5000)

    assert len(p.calls[0]["user"]) <= 2000


# ── escalation: the part that finally tells a human ──────────────────────────

def test_an_unconfigured_escalation_screams_rather_than_passing_quietly(caplog, monkeypatch):
    """THE test. A deployment that believes it has a safety net and does not is worse than
    one that knows it has none — so an unconfigured escalation logs at ERROR, every time it
    would have fired, and `armed()` is False so /health can say so out loud."""
    monkeypatch.delenv("CEREBROZEN_CRISIS_ESCALATION_URL", raising=False)

    assert escalation.armed() is False
    delivered = escalation.escalate(user_id="u1", session_id="s1", detected_by="classifier")

    assert delivered is False
    assert "safety.escalation_not_configured" in caplog.text


def test_the_payload_carries_a_signal_and_never_the_disclosure(monkeypatch):
    """The designated contact needs to know THAT someone needs a human. They do not need to
    read what that person said at midnight.

    A coaching product that forwards the confession has broken the thing that made the
    confession possible — and the whole product rests on people believing the conversation is
    theirs. This test is the guard on that promise.
    """
    sent = {}

    class FakeHttpx:
        @staticmethod
        def post(url, json=None, timeout=None, headers=None):
            sent.update({"url": url, "body": json})

            class R:
                status_code = 200

                @staticmethod
                def raise_for_status():
                    return None

            return R()

    monkeypatch.setenv("CEREBROZEN_CRISIS_ESCALATION_URL", "https://contact.example/hook")
    monkeypatch.setitem(__import__("sys").modules, "httpx", FakeHttpx)

    escalation.escalate(user_id="u1", session_id="s1", detected_by="classifier")

    body = sent["body"]
    assert body["user_id"] == "u1" and body["session_id"] == "s1"
    assert body["detected_by"] == "classifier"   # the LAYER, not the reason
    blob = json.dumps(body).lower()
    for leaked in ("message", "transcript", "text", "content", "reason_detail"):
        assert leaked not in blob, f"the escalation payload leaked `{leaked}` — that is the disclosure"


def test_a_failed_escalation_never_costs_the_user_their_reply(monkeypatch, caplog):
    """The reply is what helps them right now. The escalation is what helps them tomorrow.
    It does not get to take the first one hostage."""
    class Boom:
        @staticmethod
        def post(*a, **kw):
            raise RuntimeError("webhook is on fire")

    monkeypatch.setenv("CEREBROZEN_CRISIS_ESCALATION_URL", "https://contact.example/hook")
    monkeypatch.setitem(__import__("sys").modules, "httpx", Boom)

    delivered = escalation.escalate(user_id="u1", session_id="s1")   # must not raise

    assert delivered is False
    assert "safety.escalation_failed" in caplog.text


def test_health_reports_whether_the_safety_layers_are_actually_armed(monkeypatch):
    """A silently-disabled safety feature is the worst kind. /health has to be able to say
    'the classifier is on and nobody is listening for escalations'."""
    monkeypatch.delenv("CEREBROZEN_CRISIS_ESCALATION_URL", raising=False)
    h = escalation.health()

    assert h["crisis_escalation_armed"] is False
    assert h["crisis_classifier_enabled"] is True
