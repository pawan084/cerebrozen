"""The crisis pre-filter.

The most consequential rule in the system: a miss means someone disclosing suicidal
intent gets a coaching question back instead of a helpline. It was English-only while the
product shipped a `{language}` variable to every agent and ran multilingual speech-to-text
— so a user writing "quiero morir" was screened by a pattern that could not match, and the
turn proceeded to normal coaching.

These tests are deliberately lopsided. The recall tests (does it catch a real disclosure)
are the point. The precision tests exist only to stop the lexicon from flagging ordinary
sentences so often that the feature gets disabled — a screen someone has switched off
protects nobody.
"""

import json

import pytest

from app.graph.crisis import crisis_screen, languages_covered, reload_terms


# ── recall: an explicit disclosure must be caught, whatever the language ─────

@pytest.mark.parametrize(
    "lang,message",
    [
        ("en", "I want to kill myself"),
        ("en", "honestly some days I think everyone would be better off dead"),
        ("en", "I've been hurting myself again"),
        ("es", "quiero morir, ya no aguanto mas"),
        ("es", "he pensado en quitarme la vida"),
        ("pt", "eu quero morrer"),
        ("fr", "j'ai envie de mourir"),
        ("fr", "je pense au suicide"),
        ("de", "ich will sterben"),
        ("de", "ich denke an Selbstmord"),
        ("it", "voglio morire"),
        ("nl", "ik denk aan zelfmoord"),
        ("af", "ek dink aan selfmoord"),
        ("tr", "intihar etmeyi düşünüyorum"),
        ("pl", "chcę umrzeć"),
        ("id", "saya ingin bunuh diri"),
        ("hi-latn", "main marna chahta hoon"),
        ("hi", "मैं आत्महत्या करना चाहता हूं"),
        ("zh", "我想死"),
        ("zh", "我不想活了"),
        ("ja", "死にたい"),
        ("ko", "죽고 싶어"),
        ("th", "อยากตาย"),
        ("ar", "اريد ان اموت"),
        ("ru", "я хочу умереть"),
    ],
)
def test_an_explicit_disclosure_is_flagged(lang, message):
    assert crisis_screen(message) == "crisis", f"[{lang}] not detected: {message!r}"


@pytest.mark.parametrize(
    "message",
    [
        "I can't go on",          # straight apostrophe
        "I can’t go on",          # curly — what every phone keyboard actually inserts
        "I cant go on",           # none at all
        "I don't want to be here anymore",
        "I don’t want to be here anymore",
    ],
)
def test_apostrophes_do_not_defeat_the_screen(message):
    """A regression this rewrite introduced and this test caught: the old English-only
    regex spelled it `can'?t`, so it matched "can't go on". The first cut of the
    multilingual lexicon listed the bare "cant go on" and therefore missed every
    apostrophised spelling — including the curly quote a phone inserts by default, which
    is how most people would actually type it. The screen folds them all away now."""
    assert crisis_screen(message) == "crisis", f"not detected: {message!r}"


def test_diacritics_do_not_defeat_the_screen():
    """'suicídio' and 'suicidio' are the same word. A screen that only matches one of them
    is a screen that fails on correctly-typed Portuguese."""
    assert crisis_screen("estou pensando em suicídio") == "crisis"
    assert crisis_screen("estou pensando em suicidio") == "crisis"
    assert crisis_screen("SUICÍDIO") == "crisis"


def test_word_boundaries_are_not_assumed_for_unspaced_scripts():
    """Chinese and Thai do not put spaces between words, so a \\b-anchored pattern never
    fires mid-sentence. This is the bug that would make CJK support look present and be
    useless."""
    assert crisis_screen("我最近工作压力很大，有时候真的想死") == "crisis"
    assert crisis_screen("ผมไม่อยากมีชีวิตอยู่แล้วครับ") == "crisis"


def test_negated_forms_still_flag_rather_than_silently_pass():
    """Substring matching cannot see negation: 不想死 ("I don't want to die") contains
    想死 ("want to die"). We flag it anyway and record why — a person saying "I don't want
    to die" is not obviously safe, and the cost of being wrong in that direction is a
    helpline shown to someone who did not need it."""
    assert crisis_screen("我不想死") == "crisis"


# ── precision: don't flag ordinary coaching talk ─────────────────────────────

@pytest.mark.parametrize(
    "message",
    [
        "I want to nail this presentation",
        "my manager is killing me with these deadlines",       # idiom, no self-reference
        "I'm dying to hear what they thought of the proposal",
        "we need to kill this project before it eats the quarter",
        "the audience was dead silent",
        "I'm on a diet and it's going badly",                   # 'die' inside 'diet'
        "I want to end my dependence on him for approval",      # 'end my' + not 'life'
        "quiero mejorar mi liderazgo",
        "ich will besser werden",
        "我想改善我的领导能力",
    ],
)
def test_ordinary_messages_are_not_flagged(message):
    assert crisis_screen(message) == "ok", f"false positive on: {message!r}"


# ── operational properties ───────────────────────────────────────────────────

def test_a_client_can_add_terms_without_a_code_change(tmp_path, monkeypatch):
    """A client shipping into a language we did not seed — or one whose native-speaker
    review corrected our guesses — must be able to fix their own crisis lexicon. Waiting
    on our release cycle for this is not an acceptable answer."""
    terms = tmp_path / "crisis.json"
    terms.write_text(json.dumps({"latin": ["ma mise fine"], "other": ["終わりにしたい"]}), encoding="utf-8")
    monkeypatch.setenv("CEREBROZEN_CRISIS_TERMS_FILE", str(terms))
    reload_terms()
    try:
        assert crisis_screen("ma mise fine") == "crisis"
        assert crisis_screen("もう終わりにしたい") == "crisis"
        assert crisis_screen("I want a promotion") == "ok"   # built-ins still behave
    finally:
        monkeypatch.delenv("CEREBROZEN_CRISIS_TERMS_FILE")
        reload_terms()


def test_an_unreadable_terms_file_does_not_disable_the_screen(tmp_path, monkeypatch):
    """A typo in a config path must not silently switch off crisis detection. It logs and
    falls back to the built-in lexicon — the failure mode of a broken config here is
    'no extra terms', never 'no screening'."""
    monkeypatch.setenv("CEREBROZEN_CRISIS_TERMS_FILE", str(tmp_path / "does-not-exist.json"))
    reload_terms()
    try:
        assert crisis_screen("I want to kill myself") == "crisis"
    finally:
        monkeypatch.delenv("CEREBROZEN_CRISIS_TERMS_FILE")
        reload_terms()


def test_the_screen_fails_towards_flagging(monkeypatch):
    """If the matcher itself explodes, the message must be treated as a crisis. Failing
    open here would route a disclosure into normal coaching — the exact outcome this
    module exists to prevent."""
    import app.graph.crisis as c

    monkeypatch.setattr(c, "_strip_diacritics", lambda t: (_ for _ in ()).throw(RuntimeError("boom")))
    assert c.crisis_screen("anything at all") == "crisis"


def test_it_covers_the_languages_the_product_actually_serves():
    covered = set(languages_covered())
    # Not an arbitrary list: these are the scripts where a \b-based English regex was
    # guaranteed to fail, plus the incumbent client's market (af/zu — South Africa).
    for lang in ("en", "es", "pt", "fr", "de", "zh", "ja", "ar", "hi", "ru", "af"):
        assert lang in covered, f"no crisis coverage for {lang}"


def test_empty_and_none_are_safe():
    assert crisis_screen("") == "ok"
    assert crisis_screen(None) == "ok"


# ── the reply must be in the language the person wrote in ────────────────────

@pytest.mark.parametrize(
    "message,expect_lang",
    [
        ("quiero morir", "es"),
        ("je veux mourir", "fr"),
        ("ich will sterben", "de"),
        ("死にたい", "ja"),
        ("I want to kill myself", "en"),
    ],
)
def test_the_screen_reports_which_language_it_matched(message, expect_lang):
    """Detecting a crisis in Spanish and replying in English is half a fix. It proves the
    screen fired, and still leaves the person reading a language they may not speak at the
    worst possible moment — which is exactly what live testing caught."""
    from app.graph.crisis import screen

    flag, lang = screen(message)
    assert flag == "crisis"
    assert lang == expect_lang


def test_the_reply_is_localised_and_keeps_the_helpline():
    from app.graph.crisis import safe_response

    es = safe_response("es")
    assert "coach" in es and "findahelpline.com" in es
    assert es != safe_response("en"), "the Spanish reply is just the English one"


def test_an_unknown_language_falls_back_to_english_not_to_silence():
    """A reply the person might not read still beats no reply at all."""
    from app.graph.crisis import safe_response

    assert safe_response("xx") == safe_response("en")
    assert safe_response("xx").strip()
