"""Crisis pre-filter — the rule that runs before any model sees the message.

This is the highest-consequence code in the repo. A miss means a person disclosing
suicidal intent gets a coaching question back instead of a helpline.

Until now it was a single English regex. The product ships a `{language}` variable to
every agent and its speech-to-text runs in multilingual code-switching mode, so a user
writing "quiero morir" or "死にたい" was screened by a pattern that could not possibly
match, and the turn proceeded to normal coaching. That is not a localisation gap; it is a
safety defect, and it is the first thing to fix for any client outside the anglosphere.

## What this is, and what it is emphatically not

It is a **high-recall pre-filter**, not a classifier. A lexicon cannot understand context,
metaphor, sarcasm, or a disclosure phrased sideways ("I've been thinking about the pills
in the cabinet"). It catches the explicit cases, which is worth doing because the explicit
cases are common — and it will miss implicit ones, which is why the coaching prompts also
carry their own safety instructions. Do not let the existence of this file convince anyone
the problem is solved.

**Recall is bought at the cost of precision, deliberately.** A false positive shows a
helpline to someone who was speaking figuratively: mildly jarring, entirely survivable. A
false negative is the failure this system exists to prevent. Every judgement call in here
resolves toward flagging.

## Before you ship a language

The seed lexicon below was assembled without native-speaker review. **That is not good
enough to ship to a market.** Before enabling a locale, have the phrase list for that
language reviewed by someone who speaks it — an inaccurate list is worse than an absent
one, because it looks like coverage. `CEREBROZEN_CRISIS_TERMS_FILE` exists precisely so a
client can supply a reviewed list without touching this code.

## Matching mechanism

Two groups, because word boundaries are a Latin-script assumption:

- **Latin scripts** match with `\b` boundaries against a diacritic-stripped copy of the
  message, so "suicídio", "suicidio", and "SUICÍDIO" all hit the same term. Boundaries
  matter here: without them "die" fires inside "diet" and "audience".
- **Every other script** (CJK, Thai, Arabic, Devanagari, Cyrillic, Hangul, Hebrew) matches
  as a raw substring, because Chinese and Thai do not put spaces between words and `\b`
  would simply never fire mid-sentence.

Substring matching costs us the ability to see negation, so the two common negated forms
that would otherwise misfire are excluded explicitly (see `_NEGATIONS`) — Chinese 不想死
("I don't want to die") contains 想死 ("want to die"), and the Korean equivalent has the
same shape. These are still flagged as *ambiguous* rather than silently dropped, because
"I don't want to die" is not obviously a safe message either.
"""

from __future__ import annotations

import json
import logging
import os
import re
import unicodedata
from typing import Iterable, List, Tuple

logger = logging.getLogger("cerebrozen.crisis")


# ── Latin-script terms (matched with word boundaries, diacritics stripped) ────
# Write them WITHOUT diacritics: the message is stripped the same way before matching,
# so "quero morrer" here catches "quero morrer" and "querò morrèr" alike.
_LATIN: dict[str, List[str]] = {
    "en": [
        "kill myself", "killing myself", "end my life", "ending my life",
        "take my own life", "take my life", "suicide", "suicidal",
        "want to die", "wanna die", "better off dead",
        "self harm", "self-harm", "selfharm", "hurt myself", "hurting myself",
        # INFLECTIONS. Found by the adversarial red team, not by review: the list had
        # "cut myself" and "hurting myself" but NOT "cutting myself", so
        # "I have been cutting myself again" — an explicit self-harm disclosure, in
        # English, the one language this screen was supposed to be good at — was scored
        # `ok` and sent through to normal coaching. A keyword list is only as good as its
        # worst-remembered conjugation, which is the argument for not relying on one.
        "cut myself", "cutting myself", "cuts myself",
        "harm myself", "harming myself",
        "no reason to live", "cant go on", "can not go on",
        "dont want to be here", "do not want to be here", "end it all",
    ],
    "es": [
        "suicidio", "suicidarme", "suicidarse", "matarme", "quitarme la vida",
        "quiero morir", "quiero morirme", "no quiero vivir", "acabar con mi vida",
        "hacerme dano", "mejor muerto",
    ],
    "pt": [
        "suicidio", "me matar", "me suicidar", "tirar minha vida", "quero morrer",
        "nao quero viver", "acabar com a minha vida", "me machucar",
    ],
    "fr": [
        "suicide", "me suicider", "me tuer", "mettre fin a mes jours",
        "envie de mourir", "je veux mourir", "me faire du mal", "plus envie de vivre",
    ],
    "de": [
        "selbstmord", "suizid", "mich umbringen", "mir das leben nehmen",
        "will sterben", "nicht mehr leben", "mich selbst verletzen",
    ],
    "it": [
        "suicidio", "uccidermi", "farla finita", "voglio morire", "togliermi la vita",
        "non voglio piu vivere",
    ],
    "nl": [
        "zelfmoord", "zelfdoding", "wil dood", "wil niet meer leven", "mezelf pijn doen",
    ],
    "af": [  # Afrikaans — the incumbent client's market is South Africa
        "selfmoord", "maak myself dood", "wil dood wees", "wil nie meer lewe nie",
    ],
    "zu": [  # isiZulu
        "ukuzibulala", "ngifuna ukufa", "angisafuni ukuphila",
    ],
    "tr": [
        "intihar", "kendimi oldurmek", "olmek istiyorum", "yasamak istemiyorum",
    ],
    "pl": [
        "samobojstwo", "zabic sie", "chce umrzec", "nie chce zyc",
    ],
    "id": [  # Indonesian / Malay
        "bunuh diri", "ingin mati", "mau mati", "tidak ingin hidup",
    ],
    "vi": [
        "muon chet", "khong muon song", "tu tu",
    ],
    "hi-latn": [  # romanised Hindi — extremely common in practice
        "aatmahatya", "atmahatya", "khudkushi", "marna chahta", "marna chahti",
        "jeena nahi chahta", "jina nahi chahta",
    ],
}

# ── Non-Latin scripts (matched as raw substrings — no word boundaries exist) ──
_OTHER: dict[str, List[str]] = {
    "zh": ["自杀", "自殺", "想死", "不想活", "结束生命", "結束生命", "活不下去"],
    "ja": ["自殺", "死にたい", "消えたい", "生きたくない"],
    "ko": ["자살", "죽고 싶", "죽고싶", "살고 싶지 않", "살기 싫"],
    "th": ["ฆ่าตัวตาย", "อยากตาย", "ไม่อยากมีชีวิตอยู่"],
    "ar": ["انتحار", "اقتل نفسي", "أقتل نفسي", "اريد ان اموت", "أريد أن أموت", "لا اريد العيش"],
    "hi": ["आत्महत्या", "मरना चाहता", "मरना चाहती", "जीना नहीं चाहता", "खुद को मार"],
    "ru": ["самоубийств", "убить себя", "покончить с собой", "хочу умереть", "не хочу жить"],
    "he": ["התאבדות", "רוצה למות", "לשים סוף לחיי"],
}

# Substring matching cannot see negation. These are the forms where a crisis term is
# literally contained inside its own negation — flagging them anyway is defensible (a
# person saying "I don't want to die" is rarely fine), so they are NOT suppressed; they
# are recorded so the reason is visible in the logs rather than looking like a bug.
_NEGATIONS: Tuple[Tuple[str, str], ...] = (
    ("不想死", "zh: 'I don't want to die' contains 想死"),
    ("죽고 싶지 않", "ko: 'I don't want to die' contains 죽고 싶"),
)


# Apostrophes are stripped, not preserved, before Latin matching. This is not cosmetic:
# the lexicon lists "cant go on", and a user types "can't go on" — or "can’t go on" with
# the curly quote every phone keyboard and word processor inserts by default. Matching on
# the literal form would miss all three variants of the most common phrasing in the list.
# Found by testing the new screen against a message the OLD English-only regex caught
# (it spelled it `can'?t`); the rewrite had silently dropped that coverage.
_APOSTROPHES = str.maketrans({"'": "", "’": "", "ʼ": "", "`": "", "´": ""})


# ── the reply ────────────────────────────────────────────────────────────────
#
# Detecting a crisis in Spanish and answering in English is half a fix: it proves the
# screen fired and still leaves the person reading a language they may not speak, at the
# worst possible moment. Live testing caught exactly that — "quiero morir" was flagged
# correctly and answered in English.
#
# `{line}` is CEREBROZEN_CRISIS_LINE (default: findahelpline.com, which routes
# internationally — a hard-coded US 988 is worse than useless in the wrong market).
#
# SAME CAVEAT AS THE LEXICON, and it matters more here: these were written without
# native-speaker review. A clumsy translation of a crisis message is worse than an English
# one, because it can read as flippant at the moment a person is most vulnerable. Have
# each language reviewed before you enable it, and override via
# CEREBROZEN_CRISIS_MESSAGES_FILE ({"es": "...", ...}) rather than editing this table.
_MESSAGES: dict[str, str] = {
    "en": (
        "I'm really glad you told me, and I want to make sure you get the right kind of "
        "support right now — more than I can offer as a coach. If you're thinking about "
        "harming yourself or you're in crisis, please reach out immediately to {line}. "
        "You deserve to talk to someone who can help you stay safe. I'm here to keep "
        "talking whenever you're ready."
    ),
    "es": (
        "Me alegra mucho que me lo hayas contado, y quiero asegurarme de que recibas el "
        "apoyo adecuado ahora mismo — más del que puedo ofrecerte como coach. Si estás "
        "pensando en hacerte daño o estás en crisis, por favor contacta de inmediato con "
        "{line}. Mereces hablar con alguien que pueda ayudarte a estar a salvo. Estaré "
        "aquí para seguir hablando cuando quieras."
    ),
    "pt": (
        "Fico muito grato por você me contar, e quero garantir que receba o apoio certo "
        "agora — mais do que posso oferecer como coach. Se você está pensando em se "
        "machucar ou está em crise, procure imediatamente {line}. Você merece falar com "
        "alguém que possa ajudar a mantê-lo seguro. Estarei aqui quando quiser conversar."
    ),
    "fr": (
        "Je suis vraiment heureux que vous m'en parliez, et je veux m'assurer que vous "
        "receviez le bon soutien maintenant — bien plus que ce que je peux offrir en tant "
        "que coach. Si vous pensez à vous faire du mal ou si vous êtes en crise, "
        "contactez immédiatement {line}. Vous méritez de parler à quelqu'un qui peut vous "
        "aider à rester en sécurité. Je reste là quand vous serez prêt à reparler."
    ),
    "de": (
        "Ich bin froh, dass Sie es mir sagen, und ich möchte sicherstellen, dass Sie "
        "jetzt die richtige Unterstützung bekommen — mehr, als ich als Coach bieten kann. "
        "Wenn Sie daran denken, sich zu verletzen, oder in einer Krise sind, wenden Sie "
        "sich bitte sofort an {line}. Sie verdienen es, mit jemandem zu sprechen, der "
        "Ihnen helfen kann, sicher zu bleiben. Ich bin hier, wenn Sie weiterreden möchten."
    ),
    "it": (
        "Sono davvero contento che me l'abbia detto, e voglio assicurarmi che riceva il "
        "supporto giusto adesso — molto più di quanto io possa offrire come coach. Se sta "
        "pensando di farsi del male o è in crisi, contatti subito {line}. Merita di "
        "parlare con qualcuno che possa aiutarla a stare al sicuro. Resto qui quando "
        "vorrà riprendere a parlare."
    ),
    "nl": (
        "Ik ben blij dat je het me vertelt, en ik wil ervoor zorgen dat je nu de juiste "
        "steun krijgt — meer dan ik als coach kan bieden. Als je eraan denkt jezelf pijn "
        "te doen of in crisis bent, neem dan onmiddellijk contact op met {line}. Je "
        "verdient het om te praten met iemand die je kan helpen veilig te blijven. Ik ben "
        "hier wanneer je verder wilt praten."
    ),
}


def _strip_diacritics(text: str) -> str:
    """Fold a message to its bare Latin skeleton: no diacritics, no apostrophes.

    So 'suicídio' == 'suicidio' and "can’t go on" == "cant go on".

    Applied only to the copy used for LATIN matching. Running it over Devanagari or
    Arabic would mangle those scripts (the vowel marks are not decoration), which is
    exactly why the non-Latin terms match against the untouched text instead.
    """
    decomposed = unicodedata.normalize("NFKD", text.translate(_APOSTROPHES))
    return "".join(ch for ch in decomposed if not unicodedata.combining(ch))


def _client_terms() -> Tuple[List[str], List[str]]:
    """Extra terms supplied by the deployment, as {"latin": [...], "other": [...]}.

    A client shipping into a language we did not seed — or one whose native-speaker review
    corrected our guesses — must be able to fix their crisis lexicon without a code change
    and without waiting for us. A malformed file must never take the screen offline: it is
    logged and ignored, and the built-in terms keep working.
    """
    path = os.environ.get("CEREBROZEN_CRISIS_TERMS_FILE", "").strip()
    if not path:
        return [], []
    try:
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
        latin = [str(t).strip().casefold() for t in data.get("latin", []) if str(t).strip()]
        other = [str(t).strip().casefold() for t in data.get("other", []) if str(t).strip()]
        logger.info("crisis.terms_file_loaded", extra={"path": path, "latin": len(latin), "other": len(other)})
        return latin, other
    except Exception as exc:  # noqa: BLE001 — a bad file must not disable the screen
        logger.error("crisis.terms_file_unreadable", extra={"path": path, "error": str(exc)})
        return [], []


def _compile() -> Tuple[re.Pattern, re.Pattern]:
    extra_latin, extra_other = _client_terms()
    latin = sorted({t for terms in _LATIN.values() for t in terms} | set(extra_latin))
    other = sorted({t for terms in _OTHER.values() for t in terms} | set(extra_other))

    # Longest-first so "self-harm" is preferred over a shorter overlapping term; re
    # alternation is first-match, not longest-match.
    latin_alt = "|".join(re.escape(t) for t in sorted(latin, key=len, reverse=True))
    other_alt = "|".join(re.escape(t) for t in sorted(other, key=len, reverse=True))
    return (
        re.compile(rf"\b({latin_alt})\b", re.IGNORECASE),
        re.compile(rf"({other_alt})"),
    )


_LATIN_RE, _OTHER_RE = _compile()


def reload_terms() -> None:
    """Recompile after CEREBROZEN_CRISIS_TERMS_FILE changes (used by tests and ops)."""
    global _LATIN_RE, _OTHER_RE
    _LATIN_RE, _OTHER_RE = _compile()


def languages_covered() -> Iterable[str]:
    return sorted(set(_LATIN) | set(_OTHER))


def safe_response(lang: str = "en") -> str:
    """The crisis reply, in the user's language where we have one — English otherwise.

    English is the fallback, never an error: a message the person might not read is still
    infinitely better than no message at the moment the screen has just fired. A client
    supplies reviewed translations via CEREBROZEN_CRISIS_MESSAGES_FILE.
    """
    from app.llm.prompts import CRISIS_LINE

    overrides: dict = {}
    path = os.environ.get("CEREBROZEN_CRISIS_MESSAGES_FILE", "").strip()
    if path:
        try:
            with open(path, encoding="utf-8") as fh:
                overrides = {str(k): str(v) for k, v in json.load(fh).items()}
        except Exception as exc:  # noqa: BLE001 — a bad file must not blank the reply
            logger.error("crisis.messages_file_unreadable", extra={"path": path, "error": str(exc)})

    template = overrides.get(lang) or _MESSAGES.get(lang)
    if template is None:
        # The screen DETECTS ~20 languages but we only have a reply written in a handful.
        # So a Japanese speaker is correctly flagged and then answered in English. That is
        # a real gap, and it must be loud rather than silent: this line names the exact
        # language a client needs translated, at the exact moment it mattered to a user.
        # Alert on it — see docs/OPERATIONS.md.
        logger.error("crisis.reply_language_unavailable", extra={"lang": lang, "served": "en"})
        template = _MESSAGES["en"]
    try:
        return template.format(line=CRISIS_LINE)
    except Exception:  # noqa: BLE001 — a malformed override must not produce an empty reply
        logger.error("crisis.message_template_invalid", extra={"lang": lang})
        return _MESSAGES["en"].format(line=CRISIS_LINE)


def _matched_language(normalized: str, folded: str) -> str:
    """Which lexicon caught it — used to answer in the language the person wrote in.

    Best-effort and honest about it: a term like "suicidio" is in both the Spanish and
    Italian lists, and the first match wins. Getting this slightly wrong costs a helpline
    message in the wrong Romance language; not having it at all costs an English message
    to someone who does not read English, which is worse.
    """
    for lang, terms in _OTHER.items():
        if any(t.casefold() in normalized for t in terms):
            return lang
    for lang, terms in _LATIN.items():
        for t in terms:
            if re.search(rf"\b{re.escape(t)}\b", folded):
                return lang.split("-")[0]  # hi-latn -> hi
    return "en"


def crisis_screen(text: str) -> str:
    """'crisis' if the message trips the pre-filter, else 'ok'. Never raises.

    A crash here would fail the screen open and let a crisis message through to normal
    coaching, so every failure mode resolves to flagging rather than to an exception.

    NOTE: this is the LEXICON ONLY. It is the zero-latency floor — 1ms, free, no network,
    ~20 languages, and it works in the air-gapped configuration with no model at all. On its
    own it catches roughly 1 implicit disclosure in 22, which is what a word list is worth.
    The full screen (`full_screen`) runs the classifier behind it. Callers in the graph use
    the full screen; this function stays for the many places that want a cheap, pure,
    dependency-free check.
    """
    return screen(text)[0]


def full_screen(text: str) -> Tuple[str, str, str]:
    """The REAL screen: lexicon first, then the classifier on whatever it let through.

    Returns (flag, language, why).

    The order matters and is not an optimisation. The lexicon runs first because it is
    free, instant and offline — so an explicit disclosure is caught even if the model
    provider is on fire. The classifier then handles euphemism, planning and hedged
    disclosure, which no word list can reach.

    What does NOT change: the takeover. When this returns "crisis", `safe_response` replies
    with a scripted helpline, zero tokens, no model in the loop. The detection got smarter;
    the response did not become negotiable. A model that could be talked out of escalating
    would be worse than no screen at all.
    """
    flag, lang = screen(text)
    if flag == "crisis":
        return "crisis", lang, "lexicon"

    from app.graph import crisis_classifier

    flag2, why = crisis_classifier.classify(text, flag)
    return flag2, lang, why


def screen(text: str) -> Tuple[str, str]:
    """('crisis'|'ok', detected_language). The language is 'en' when nothing matched."""
    if not text:
        return "ok", "en"
    try:
        normalized = unicodedata.normalize("NFC", text).casefold()
        folded = _strip_diacritics(normalized)
        if _LATIN_RE.search(folded) or _OTHER_RE.search(normalized):
            for negation, why in _NEGATIONS:
                if negation.casefold() in normalized:
                    # Still a crisis flag — see _NEGATIONS. Logged so the match is
                    # explicable rather than looking like a false positive nobody can
                    # account for.
                    logger.info("crisis.matched_via_negated_form", extra={"note": why})
                    break
            return "crisis", _matched_language(normalized, folded)
        return "ok", "en"
    except Exception as exc:  # noqa: BLE001
        logger.error("crisis.screen_failed_flagging_anyway", extra={"error": str(exc)})
        return "crisis", "en"
