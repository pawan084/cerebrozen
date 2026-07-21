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

- **Latin scripts** match against a diacritic-stripped copy of the message, so "suicídio",
  "suicidio", and "SUICÍDIO" all hit the same term. Each letter also accepts its leetspeak
  substitutes and each space accepts `._-`, so "su1c1de" and "k1ll_myself" hit them too
  (see `_LEET`). Boundaries still matter — without them "die" fires inside "diet" and
  "audience" — but they are letter/digit lookarounds rather than `\b`, because `\b` cannot
  see the start of "$uicide".
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
from functools import lru_cache
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
#
# EVERY LANGUAGE THE SCREEN DETECTS NOW HAS A REPLY. That closes the gap where a Japanese
# speaker was flagged correctly and answered in English — but "has a reply" and "has a
# reply someone who speaks it has read" are different claims, and only the second is worth
# selling. `_NATIVE_REVIEWED` below is the difference, and it is what the marketing copy
# is allowed to describe (docs/CLAIMS_MAP.md holds that row).
#
# The `{line}` insert stays in whatever language CEREBROZEN_CRISIS_LINE is written in — so
# an unset deployment ends a Thai sentence with an English directory name. Better than no
# number; still a reason to set the env var per market.
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
    "af": (
        "Ek is bly dat jy dit vir my gesê het, en ek wil seker maak dat jy nou die regte "
        "hulp kry — meer as wat ek as afrigter kan bied. As jy daaraan dink om jouself "
        "seer te maak, of as jy in 'n krisis is, kontak asseblief onmiddellik {line}. Jy "
        "verdien dit om te praat met iemand wat jou kan help om veilig te bly. Ek is hier "
        "wanneer jy gereed is om verder te gesels."
    ),
    "zu": (
        "Ngiyabonga ngokungitshela lokhu. Ngifuna ukuqinisekisa ukuthi uthola usizo "
        "olufanele manje — olungaphezu kwalokho engingakunikeza njengomqeqeshi. Uma "
        "ucabanga ukuzilimaza noma usenkingeni, sicela uxhumane ngokushesha: {line}. "
        "Ufanelwe ukukhuluma nomuntu ongakusiza uhlale uphephile. Ngilapha ukuze "
        "siqhubeke sixoxe noma nini lapho usukulungele."
    ),
    "tr": (
        "Bunu bana anlattığın için gerçekten sevindim ve şu anda doğru desteği almanı "
        "istiyorum — bir koç olarak sunabileceğimden çok daha fazlasını. Kendine zarar "
        "vermeyi düşünüyorsan ya da kriz içindeysen, lütfen hemen şuraya ulaş: {line}. "
        "Güvende kalmana yardım edebilecek biriyle konuşmayı hak ediyorsun. Hazır "
        "olduğunda konuşmaya devam etmek için buradayım."
    ),
    "pl": (
        "Cieszę się, że mi o tym mówisz. Chcę, aby dotarło do Ciebie teraz właściwe "
        "wsparcie — większe, niż mogę zaoferować jako coach. Jeśli myślisz o zrobieniu "
        "sobie krzywdy lub jesteś w kryzysie, skontaktuj się natychmiast: {line}. "
        "Zasługujesz na rozmowę z kimś, kto pomoże Ci zachować bezpieczeństwo. Będę "
        "tutaj, żeby porozmawiać dalej, kiedy tylko zechcesz."
    ),
    "id": (
        "Terima kasih sudah menceritakan ini kepada saya. Saya ingin memastikan Anda "
        "mendapat dukungan yang tepat sekarang — lebih dari yang bisa saya berikan "
        "sebagai coach. Jika Anda sedang berpikir untuk menyakiti diri sendiri atau "
        "sedang dalam krisis, segera hubungi: {line}. Anda berhak berbicara dengan "
        "seseorang yang bisa membantu Anda tetap aman. Saya ada di sini kapan pun Anda "
        "siap melanjutkan percakapan."
    ),
    "vi": (
        "Cảm ơn bạn đã nói với tôi điều này. Tôi mong bạn nhận được sự hỗ trợ phù hợp "
        "ngay lúc này — nhiều hơn những gì tôi có thể mang lại với vai trò một người "
        "huấn luyện. Nếu bạn đang nghĩ đến việc làm tổn thương bản thân hoặc đang trong "
        "khủng hoảng, hãy liên hệ ngay: {line}. Bạn xứng đáng được trò chuyện với người "
        "có thể giúp bạn an toàn. Tôi vẫn ở đây, bất cứ khi nào bạn sẵn sàng nói tiếp."
    ),
    "ru": (
        "Спасибо, что рассказали мне об этом. Я хочу, чтобы вы прямо сейчас получили "
        "подходящую поддержку — больше, чем я могу дать как коуч. Если вы думаете о том, "
        "чтобы причинить себе вред, или находитесь в кризисе, пожалуйста, немедленно "
        "обратитесь: {line}. Вы заслуживаете разговора с тем, кто может помочь вам "
        "оставаться в безопасности. Я здесь и готов продолжить разговор, когда вы будете "
        "готовы."
    ),
    "he": (
        "אני שמח שסיפרת לי, ואני רוצה לוודא שתקבל/י עכשיו את התמיכה הנכונה — יותר ממה "
        "שאני יכול להציע כמאמן. אם עולות מחשבות לפגוע בעצמך, או אם את/ה במשבר, אנא פנה/י "
        "מיד אל: {line}. מגיע לך לדבר עם מישהו שיכול לעזור לך להישאר בטוח/ה. אני כאן "
        "כדי להמשיך לדבר מתי שתרצה/י."
    ),
    "ar": (
        "أنا سعيد لأنك أخبرتني بهذا، وأريد أن أتأكد من حصولك على الدعم المناسب الآن — "
        "وهو أكثر مما أستطيع تقديمه بصفتي مدرِّبًا. إذا كنت تفكر في إيذاء نفسك أو كنت في "
        "أزمة، فيرجى التواصل فورًا مع: {line}. أنت تستحق أن تتحدث مع شخص يستطيع مساعدتك "
        "على البقاء بأمان. سأبقى هنا لنكمل الحديث متى كنت مستعدًا."
    ),
    "hi": (
        "मुझे अच्छा लगा कि आपने मुझे यह बताया। अभी आपको सही तरह की मदद मिलनी चाहिए — "
        "उससे कहीं ज़्यादा, जितनी एक कोच दे सकता है। अगर आप खुद को नुकसान पहुँचाने के "
        "बारे में सोच रहे हैं, या आप संकट में हैं, तो कृपया तुरंत संपर्क करें: {line}। "
        "आप इसके हक़दार हैं कि किसी ऐसे व्यक्ति से बात करें जो आपको सुरक्षित रखने में "
        "मदद कर सके। जब भी आप तैयार हों, मैं यहाँ बात करने के लिए मौजूद हूँ।"
    ),
    "th": (
        "ขอบคุณที่บอกเรื่องนี้กับฉัน ตอนนี้ฉันอยากให้คุณได้รับความช่วยเหลือที่เหมาะสม "
        "จริง ๆ ซึ่งมากกว่าที่ฉันจะให้ได้ในฐานะโค้ช ถ้าคุณกำลังคิดทำร้ายตัวเอง หรือ"
        "กำลังอยู่ในภาวะวิกฤต โปรดติดต่อทันทีที่: {line} คุณสมควรได้พูดคุยกับคนที่ช่วย"
        "ให้คุณปลอดภัยได้ และเมื่อคุณพร้อม ฉันอยู่ตรงนี้เพื่อคุยกันต่อเสมอ"
    ),
    "zh": (
        "谢谢你愿意告诉我。我希望你现在能得到真正合适的帮助——这超出了我作为教练所能提供的"
        "范围。如果你正在想伤害自己，或者正处在危机之中，请立即联系：{line}。你值得和"
        "能够帮助你保持安全的人谈一谈。等你准备好了，我随时都在这里，我们可以继续聊。"
    ),
    "ja": (
        "話してくれて本当によかったです。今は、コーチとしての私にできること以上の、"
        "適切なサポートを受けてほしいと思っています。自分を傷つけることを考えていたり、"
        "危機的な状況にいる場合は、すぐにこちらへ連絡してください：{line}。"
        "あなたには、安全を守る手助けができる人と話す権利があります。"
        "準備ができたら、いつでもここで話を続けましょう。"
    ),
    "ko": (
        "이야기해 주셔서 정말 다행이에요. 지금은 제가 코치로서 드릴 수 있는 것보다 더 "
        "적절한 도움을 받으셨으면 합니다. 스스로를 해치는 생각이 들거나 위기 상황이라면, "
        "지금 바로 연락해 주세요: {line}. 당신은 안전을 지킬 수 있도록 도와줄 사람과 "
        "이야기할 자격이 있습니다. 준비되시면 언제든 여기서 계속 이야기해요."
    ),
}

# Which of the above a native speaker has actually read.
#
# This is an HONESTY registry, not a gate: by default an unreviewed reply is still served,
# because a drafted message in the person's own language beats a fluent one they cannot
# read, and the payload that matters — the helpline — is a number or a directory either
# way. What the registry always gates is the CLAIM: "crisis support in N languages" may
# only count what is in here, and `reply_languages()` reports both numbers so ops and the
# claims table cannot drift apart.
#
# A deployment under clinical governance can take the stricter posture — docs/SECURITY.md
# carried it as a standing commitment, inherited from the reference product — by setting
# CEREBROZEN_CRISIS_REVIEWED_ONLY=1, which serves English for anything unreviewed. Both
# postures are defensible and they fail in opposite directions: the strict one guarantees
# a fluent message that a monolingual Thai speaker cannot read; the default one risks a
# clumsy sentence at the worst possible moment. Neither is a matter of taste — pick it per
# market, with whoever owns clinical risk.
#
# Move a language into the registry when, and only when, a speaker signs it off. The
# client override file (CEREBROZEN_CRISIS_MESSAGES_FILE) is the path for a reviewed set
# that should not wait on a release, and overrides are treated as reviewed by definition:
# it is the client's own translation.
_NATIVE_REVIEWED: frozenset = frozenset({"en"})


# ── the AI disclosure, appended to every crisis reply ────────────────────────
#
# California and New York both require an AI to say what it is, and the crisis takeover is
# the moment where a person is least able to infer it and most harmed by getting it wrong:
# the reply is warm, it is scripted, and it arrives right after a disclosure that a human
# would have had to react to. Someone could reasonably read it as a person on the other end
# deciding to help.
#
# Kept SEPARATE from `_MESSAGES`, which is the part a client may override. A client supplies
# the reply body; the disclosure is ours and is appended either way. That is deliberate —
# the override file exists so a clinical team can improve the wording, and "improving" a
# legally-required sentence out of the message is exactly the edit it must not be able to
# make.
#
# It goes LAST, not first. A person in crisis may read two sentences: those two must be
# "I'm glad you told me" and the helpline, not a disclaimer about what I am. Conspicuous
# does not have to mean first, and putting it first would trade real safety for the
# appearance of compliance.
_AI_DISCLOSURE: dict[str, str] = {
    "en": "I'm an AI coach — not a person, and not a crisis service.",
    "es": "Soy un coach de IA: no soy una persona ni un servicio de emergencia.",
    "pt": "Sou um coach de IA: não sou uma pessoa nem um serviço de emergência.",
    "fr": "Je suis un coach IA : je ne suis ni une personne ni un service d'urgence.",
    "de": "Ich bin ein KI-Coach – kein Mensch und kein Krisendienst.",
    "it": "Sono un coach IA: non sono una persona né un servizio di emergenza.",
    "nl": "Ik ben een AI-coach — geen mens en geen crisisdienst.",
    "af": "Ek is 'n KI-afrigter — nie 'n mens nie, en nie 'n krisisdiens nie.",
    "zu": "Ngingumqeqeshi we-AI — angisiyena umuntu, futhi angiyona insizakalo yezimo eziphuthumayo.",
    "tr": "Ben bir yapay zekâ koçuyum — bir insan ya da kriz hattı değilim.",
    "pl": "Jestem coachem AI — nie jestem człowiekiem ani służbą kryzysową.",
    "id": "Saya adalah coach AI — bukan manusia, dan bukan layanan darurat.",
    "vi": "Tôi là một huấn luyện viên AI — không phải con người, và không phải dịch vụ khẩn cấp.",
    "ru": "Я — ИИ-коуч, не человек и не кризисная служба.",
    "he": "אני מאמן מבוסס בינה מלאכותית — לא אדם, ולא שירות חירום.",
    "ar": "أنا مدرِّب يعمل بالذكاء الاصطناعي — لست إنسانًا ولست خدمة طوارئ.",
    "hi": "मैं एक AI कोच हूँ — कोई व्यक्ति नहीं, और न ही कोई आपातकालीन सेवा।",
    "th": "ฉันเป็นโค้ช AI ไม่ใช่มนุษย์ และไม่ใช่บริการช่วยเหลือฉุกเฉิน",
    "zh": "我是一个 AI 教练，不是真人，也不是危机干预服务。",
    "ja": "私はAIのコーチで、人間でも危機対応サービスでもありません。",
    "ko": "저는 AI 코치이며, 사람도 위기 대응 서비스도 아닙니다.",
}


def _reviewed_only() -> bool:
    return os.environ.get("CEREBROZEN_CRISIS_REVIEWED_ONLY", "").strip().lower() in {"1", "true", "yes", "on"}


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


# ── obfuscation tolerance (leetspeak + separator padding) ────────────────────
#
# People obfuscate crisis words, and not only to evade filters: platforms have trained a
# decade of users that "unalive" and "k1ll" are how you say this without being suppressed,
# so the habit arrives with them. A screen that reads "suicide" and not "su1c1de" fails
# on the exact population most practised at self-censoring.
#
# Done as a CHARACTER CLASS PER LETTER rather than by normalising digits back to letters
# in the message. Normalising has to guess — "1" is `i` in "k1ll" and `l` in "ki11" — and a
# single guess drops half the variants. A class matches every reading at once, and it can
# only ever ADD matches to the same term list, so the precision it costs is bounded by
# what the lexicon already contains: no new words become flaggable, only new spellings of
# the words that already were.
#
# It costs ~4× the plain alternation — measured at ~0.1 ms for a long message, against
# ~0.02 ms — which is still three orders of magnitude under the model call it precedes,
# and it is paid on every turn rather than only on crisis ones. Worth it; worth knowing.
_LEET: dict[str, str] = {
    "a": "a4@", "b": "b8", "c": "c(", "e": "e3", "g": "g9", "i": "i1!|",
    "l": "l1|", "o": "o0", "s": "s5$", "t": "t7+", "z": "z2",
}
# Spaces in a term also match dots/underscores/hyphens ("k1ll_myself", "end.it.all") and
# runs of whitespace.
_SEPARATORS = r"[\s._\-]+"


def _leet_regex(term: str) -> str:
    """A term as a spelling-tolerant pattern, with letter-only boundaries.

    `\\b` cannot be used: it asks whether the neighbouring character is a word character,
    and "$uicide" starts with one that is not — so the boundary the term needs would never
    fire on precisely the obfuscated forms this exists to catch.
    """
    body = "".join(
        _SEPARATORS if ch == " " else f"[{re.escape(_LEET[ch])}]" if ch in _LEET else re.escape(ch)
        for ch in term
    )
    return rf"(?<![0-9a-zA-Z]){body}(?![0-9a-zA-Z])"


@lru_cache(maxsize=4096)
def _leet_pattern(term: str) -> re.Pattern:
    return re.compile(_leet_regex(term), re.IGNORECASE)


def _compile() -> Tuple[re.Pattern, re.Pattern]:
    extra_latin, extra_other = _client_terms()
    latin = sorted({t for terms in _LATIN.values() for t in terms} | set(extra_latin))
    other = sorted({t for terms in _OTHER.values() for t in terms} | set(extra_other))

    # Longest-first so "self-harm" is preferred over a shorter overlapping term; re
    # alternation is first-match, not longest-match.
    latin_alt = "|".join(_leet_regex(t) for t in sorted(latin, key=len, reverse=True))
    other_alt = "|".join(re.escape(t) for t in sorted(other, key=len, reverse=True))
    return (
        re.compile(rf"({latin_alt})", re.IGNORECASE),
        re.compile(rf"({other_alt})"),
    )


_LATIN_RE, _OTHER_RE = _compile()


def reload_terms() -> None:
    """Recompile after CEREBROZEN_CRISIS_TERMS_FILE changes (used by tests and ops)."""
    global _LATIN_RE, _OTHER_RE
    _LATIN_RE, _OTHER_RE = _compile()


def languages_covered() -> Iterable[str]:
    return sorted(set(_LATIN) | set(_OTHER))


def reply_languages() -> Tuple[List[str], List[str]]:
    """(native-reviewed, drafted-but-unreviewed) reply languages — the honest split.

    Detection coverage (`languages_covered`) and reply coverage are different numbers, and
    reply coverage splits again into "a speaker signed this off" and "we drafted it". The
    claims table (docs/CLAIMS_MAP.md) may only quote the first list; ops reads the second
    as the translation queue. Client overrides are per-deployment and unknowable here, so
    this describes the built-in table only.
    """
    reviewed = sorted(lang for lang in _MESSAGES if lang in _NATIVE_REVIEWED)
    drafted = sorted(lang for lang in _MESSAGES if lang not in _NATIVE_REVIEWED)
    return reviewed, drafted


def safe_response(lang: str = "en") -> str:
    """The crisis reply, in the user's language where we have one — English otherwise.

    English is the fallback, never an error: a message the person might not read is still
    infinitely better than no message at the moment the screen has just fired. A client
    supplies reviewed translations via CEREBROZEN_CRISIS_MESSAGES_FILE.

    Precedence: client override → built-in table → English. Under
    CEREBROZEN_CRISIS_REVIEWED_ONLY the built-in step is skipped for anything not in
    `_NATIVE_REVIEWED` (see the note there — it is a real trade-off, not a safety switch
    with an obviously-correct setting).
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

    template = overrides.get(lang)
    if template is None and lang not in _NATIVE_REVIEWED and _reviewed_only():
        # Suppressed BY POLICY, not missing — the distinction matters to whoever reads the
        # log, because the fix for one is a translation and the fix for the other is a
        # config decision. `lang` is reassigned so the AI disclosure appended below falls
        # back with the body: a deployment that refuses unreviewed sentences must not get
        # one smuggled in through the disclosure.
        logger.warning("crisis.reply_language_suppressed_unreviewed", extra={"lang": lang, "served": "en"})
        template, lang = _MESSAGES["en"], "en"
    elif template is None:
        template = _MESSAGES.get(lang)
        if template is None:
            # Every language the SCREEN detects now has a reply, so reaching here means
            # the caller passed a language the lexicon cannot even flag — a client term
            # file in a new language, or a locale tag we do not normalise. Still loud
            # rather than silent: this line names the exact language a client needs
            # translated, at the exact moment it mattered to a user. Alert on it — see
            # docs/OPERATIONS.md.
            logger.error("crisis.reply_language_unavailable", extra={"lang": lang, "served": "en"})
            template = _MESSAGES["en"]
        elif lang not in _NATIVE_REVIEWED:
            # Served, not suppressed (see _NATIVE_REVIEWED). Counted so the translation
            # queue is ordered by which languages people are actually in crisis in, rather
            # than by which markets sales is excited about.
            logger.warning("crisis.reply_language_unreviewed", extra={"lang": lang})
    try:
        body = template.format(line=CRISIS_LINE)
    except Exception:  # noqa: BLE001 — a malformed override must not produce an empty reply
        logger.error("crisis.message_template_invalid", extra={"lang": lang})
        body, lang = _MESSAGES["en"].format(line=CRISIS_LINE), "en"
    # Non-negotiable, and appended AFTER the override path so a client cannot drop it.
    return f"{body} {_AI_DISCLOSURE.get(lang) or _AI_DISCLOSURE['en']}"


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
            # Same spelling tolerance as the screen itself — otherwise "su1c1d1o" is
            # flagged but answered in English, which is the bug this whole table exists
            # to prevent, reintroduced through the back door.
            if _leet_pattern(t).search(folded):
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
