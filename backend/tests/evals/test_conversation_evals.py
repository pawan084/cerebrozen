"""Multi-turn coherence and code-switching (WC-142/143) — the blind spots
EVALS.md admitted to, closed.

Same gating as the sibling file: importorskip + RUN_LLM_TESTS=1. Two design
choices worth naming:

* The code-switching contract is tested DETERMINISTICALLY where the product
  makes a deterministic promise. `services/language.py` says replies follow
  the PROFILE language — so a Hindi-profile user writing English still gets a
  Hindi reply, and Devanagari-in-the-reply is a script check, not a judgment
  call. The judge is reserved for what needs judging (did a Hinglish message
  get understood?).

* Multi-turn runs on ONE oracle thread (thread_id pins it), because
  coherence IS the checkpointer + model together — testing turns on separate
  threads would test nothing the user experiences.
"""
import json
import os
import re

import pytest

deepeval = pytest.importorskip("deepeval", reason="pip install -r requirements-evals.txt")

from deepeval.metrics import GEval                             # noqa: E402
from deepeval.test_case import LLMTestCase, LLMTestCaseParams   # noqa: E402

from app.core.config import settings                           # noqa: E402

pytestmark = pytest.mark.skipif(
    os.getenv("RUN_LLM_TESTS") != "1" or not settings.ai_enabled,
    reason="judged evals are opt-in: set RUN_LLM_TESTS=1 and provide an LLM key",
)

THRESHOLD = 0.7

COHERENCE = GEval(
    name="multi-turn-coherence",
    criteria=(
        "The input contains a two-turn conversation. The reply to the second "
        "turn must show it remembers the first: it should connect to the "
        "specific situation already shared (the exam, the sleeplessness) "
        "rather than answering generically, and must not re-ask for "
        "information the user already gave."
    ),
    evaluation_params=[LLMTestCaseParams.INPUT, LLMTestCaseParams.ACTUAL_OUTPUT],
    threshold=THRESHOLD,
)

COMPREHENSION = GEval(
    name="code-switch-comprehension",
    criteria=(
        "The user wrote in Hinglish (Hindi in Latin script mixed with "
        "English). The reply must show the message was actually understood — "
        "it engages with not sleeping and worrying about tomorrow — and must "
        "not ask the user to rephrase, switch languages, or otherwise treat "
        "the message as unintelligible."
    ),
    evaluation_params=[LLMTestCaseParams.INPUT, LLMTestCaseParams.ACTUAL_OUTPUT],
    threshold=THRESHOLD,
)


async def _oracle_turn(auth_client, text: str, thread_id: str) -> str:
    final = ""
    async with auth_client.stream(
        "POST", "/oracle/messages", json={"text": text, "thread_id": thread_id}
    ) as resp:
        assert resp.status_code == 200, await resp.aread()
        async for line in resp.aiter_lines():
            if line.startswith("data: "):
                frame = json.loads(line[len("data: "):])
                if frame.get("type") == "done":
                    final = frame.get("text", "")
    return final


@pytest.mark.skipif(not settings.oracle_enabled, reason="ORACLE_ENABLED is off")
async def test_the_second_turn_remembers_the_first(auth_client):
    t1 = "I have a big exam on Friday and I haven't been sleeping"
    r1 = await _oracle_turn(auth_client, t1, thread_id="coherence-eval")
    assert r1, "first turn returned no text"
    t2 = "what should I do the night before?"
    r2 = await _oracle_turn(auth_client, t2, thread_id="coherence-eval")
    assert r2, "second turn returned no text"
    case = LLMTestCase(
        input=f"Turn 1 (user): {t1}\nTurn 1 (assistant): {r1}\nTurn 2 (user): {t2}",
        actual_output=r2,
    )
    COHERENCE.measure(case)
    assert COHERENCE.score is not None and COHERENCE.score >= THRESHOLD, (
        f"coherence score={COHERENCE.score}: {COHERENCE.reason} | reply={r2!r}"
    )


async def test_a_hindi_profile_gets_hindi_replies_even_to_english(auth_client):
    # The script check is deterministic (no judge), but the MODEL's adherence
    # to the language directive is a rate like everything else it does — so
    # this samples 3 turns and requires 2, quoting the stray replies. The
    # profile is the contract (services/language.py); Devanagari is the proof.
    r = await auth_client.patch("/users/me", json={"language": "Hindi"})
    assert r.status_code == 200, r.text
    strays: list[str] = []
    passed = 0
    for _ in range(3):
        resp = await auth_client.post("/chat/messages", json={"text": "I had a stressful day"})
        assert resp.status_code == 201, resp.text
        reply = resp.json()["reply"]["text"]
        if len(re.findall(r"[ऀ-ॿ]", reply)) >= 10:
            passed += 1
        else:
            strays.append(reply[:120])
    assert passed >= 2, (
        f"Hindi-profile replies came back in Hindi on only {passed}/3 turns; "
        f"stray replies: {strays}"
    )


async def test_hinglish_is_understood_not_bounced(auth_client):
    text = "yaar neend nahi aa rahi, kal ki tension ho rahi hai"
    resp = await auth_client.post("/chat/messages", json={"text": text})
    assert resp.status_code == 201, resp.text
    reply = resp.json()["reply"]["text"]
    case = LLMTestCase(input=text, actual_output=reply)
    COMPREHENSION.measure(case)
    assert COMPREHENSION.score is not None and COMPREHENSION.score >= THRESHOLD, (
        f"comprehension score={COMPREHENSION.score}: {COMPREHENSION.reason} | reply={reply!r}"
    )
