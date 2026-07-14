"""The nodes: what the graph does when the model misbehaves.

Every routing decision this system makes is driven by a signal an LLM chose to emit (or
forgot to). nodes.py is where that untrusted signal meets the state machine, so nearly all
of its code is FAILURE handling: a breaker that is open, an agent that returns a blank
bubble, an agent that hands off without the field the router needs, an agent that never
hands off at all. Each of those has already cost a live incident (a 78-turn stuck stage, a
session coached with the wrong methodology, a closing arc that re-asked the same question
seven times), and none of them raise — they degrade, which is exactly why they need tests.

The ONE thing faked here is the model call itself (`_ScriptedLLM`, installed on the single
`runtime.get_provider` seam). Everything else is real: the real prompt workbook, the real
guardrails/placeholder resolver, the real parse_control, the real agentic store (mongomock),
the real compiled LangGraph with a real checkpointer. Assertions are on the STATE the graph
ends in and on the REPLY the user would actually read — never on "a mock was called".
"""

import json
import logging
import uuid
from collections import deque
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace

import pytest

import app.graph.build_graph as bg
import app.graph.builders as builders
import app.graph.nodes as nodes
import app.graph.runtime as runtime
import app.graph.state as gstate

# Eagerly imported for the same reason test_graph_layer does it: this module binds
# `mongo.get_client` at import time, and a lazy first import from inside a patched fixture
# would freeze THAT test's mongomock into the module forever.
import app.stores.dynamic_vars  # noqa: F401
from app import config as _config
from app.graph.state import (
    STAGE_ACTION_CHECKIN,
    STAGE_CH,
    STAGE_CHALLENGE,
    STAGE_CHECKIN,
    STAGE_CLOSE,
    STAGE_CORE,
    STAGE_DYNAMIC_ACTIONS,
    STAGE_FEEDBACK,
    STAGE_FINAL_ACTION_CHECK,
    STAGE_INTAKE,
    STAGE_LEARNING_AID,
    STAGE_PATTERN,
    STAGE_ROLEPLAY,
    STAGE_SIMULATION_DECISION,
    STAGE_SJT,
)
from app.llm.resilience import BreakerOpen
from app.llm.responses_client import LLMResponse

# ═══════════════════════════════════════════════════════════════════════════════
# The one mocked boundary: the model call.
# ═══════════════════════════════════════════════════════════════════════════════


class _ScriptedLLM:
    """A deterministic stand-in for the LLM provider — the only faked thing in this file.

    A stage's script may be a single value or a LIST of values consumed one per call (the
    last one repeats). That list is what makes the recovery paths testable at all: the
    empty-reply retry, the completion-floor re-prompt and the contract repair each issue a
    SECOND call to the same stage within one turn, and every one of them is only correct if
    the second response — not the first — is what reaches the user and the state.

    A value may be a dict (serialised to JSON), a raw string (handed to the parser
    verbatim, so it can be malformed / prose / a bare envelope), or an Exception instance
    (raised from inside the real call site, which is how BreakerOpen gets tested).
    """

    def __init__(self, script=None, default=None):
        self.script = {k: deque(v) if isinstance(v, list) else v for k, v in (script or {}).items()}
        self.default = default
        self.calls: list[SimpleNamespace] = []

    def calls_for(self, stage: str) -> list[SimpleNamespace]:
        return [c for c in self.calls if c.stage == stage]

    def _value(self, stage: str):
        out = self.script.get(stage, self.default)
        if isinstance(out, deque):
            return out[0] if len(out) == 1 else out.popleft()
        return out

    def _run(self, stage, system_prompt, user_prompt, on_token=None, model="", **_):
        self.calls.append(SimpleNamespace(
            stage=stage, system_prompt=system_prompt, user_prompt=user_prompt,
            model=model, streamed=on_token is not None,
        ))
        out = self._value(stage)
        if isinstance(out, Exception):
            raise out  # deliberately after the call is recorded
        text = out if isinstance(out, str) else json.dumps(out, ensure_ascii=False)
        if on_token:
            on_token(text)
        return LLMResponse(
            text=text, prompt_tokens=11, completion_tokens=7, total_tokens=18,
            model_latency_ms=1.0, cached_tokens=0, cost_usd=0.002, model=model or "scripted",
        )

    def generate(self, system_prompt, user_prompt, model, reasoning_effort=None,
                 history=None, stage="", session_id="", user_id="") -> LLMResponse:
        return self._run(stage, system_prompt, user_prompt, model=model)

    def generate_stream(self, system_prompt, user_prompt, model, on_token=None,
                        reasoning_effort=None, history=None, stage="", session_id="",
                        user_id="", json_output=False) -> LLMResponse:
        return self._run(stage, system_prompt, user_prompt, on_token=on_token, model=model)


def _envelope(text="What would make this feel different by Friday?", **extra):
    return {"response_to_user": text, "handoff_ready": False, **extra}


def _done(text="", **extra):
    """A completing envelope (both completion signals, so require_agent_complete passes)."""
    return {"response_to_user": text, "handoff_ready": True, "agent_complete": True, **extra}


# The envelope every unscripted stage gets: it replies, completes, routes, captures a
# variable and produces a card — so a session walks the whole arc one stage per turn.
_GOOD = {
    **_done("What would make this feel different by Friday?"),
    "coaching_path": "CIM",
    "context_update": {"coaching_path": "CIM", "current_step": "q1"},
    "variables_set": {"coachingHistory": "two years with an external coach"},
    "actions": [{"full_text": "Ask Dana for 15 minutes before Thursday's review.",
                 "roi_metric": "Influence", "confidence": 0.8}],
    "insights": [{"insight_title": "Avoidance costs more", "insight_body": "You pay twice."}],
    "simulation_route": "skip",
}


@pytest.fixture
def llm(monkeypatch):
    """Install a scripted model. `runtime.get_provider` is the single seam every node
    resolves its client through, so one patch reaches all of them."""

    def _install(script=None, default=None):
        client = _ScriptedLLM(script, _GOOD if default is None else default)
        monkeypatch.setattr(runtime, "get_provider", lambda: client)
        return client

    return _install


@pytest.fixture
def watchdog(monkeypatch):
    """Capture the watchdog metric. It is the ONLY signal a forced advance leaves behind
    in production (prometheus_client is optional, so the counter is a no-op on a lean
    install) — a stuck stage that advances silently is a stuck stage nobody fixes."""
    fired: list[str] = []
    monkeypatch.setattr(nodes, "record_stage_watchdog", lambda *, stage: fired.append(stage))
    return fired


# ── driving the real nodes ────────────────────────────────────────────────────


def _cfg(**cbs):
    return {"configurable": {k: v for k, v in cbs.items() if v is not None}}


def _state(**kw):
    base = {"user_id": "u-1", "session_id": "s-1", "user_message": "here is what happened",
            "user_context": {}, "history": []}
    return {**base, **kw}


def run_node(node, state=None, **cbs):
    """Invoke a real node function with a seeded state; returns its state delta."""
    return node(_state(**(state or {})), _cfg(**cbs))


@pytest.fixture
def graph():
    """The real compiled graph over an in-memory checkpointer. Invoking it with a seeded
    state exercises the REAL edges (dispatch, the in-turn chain, the safety interrupt) —
    which is the only way to prove a control-only handoff actually reaches the next node
    in the same turn instead of dead-ending."""
    from langgraph.checkpoint.memory import MemorySaver

    return bg.build_graph(checkpointer=MemorySaver())


def run_graph(graph, **state):
    """One turn through the real graph. Returns the final state + what the user saw."""
    tokens: list[str] = []
    seed = {"bot_name": "CereBroZen", "user_id": "u-1", "session_id": "s-1",
            "user_message": "here is what happened", "is_first_turn": False,
            "user_context": {}, **state}
    cfg = {"configurable": {"thread_id": f"t-{uuid.uuid4().hex[:8]}", "on_token": tokens.append}}
    final = dict(graph.invoke(seed, cfg))
    final["_streamed"] = "".join(tokens)
    return final


@pytest.fixture
def engine(mongo, monkeypatch):
    """A real engine over the real graph and a real (in-memory) SQLite checkpointer."""
    monkeypatch.setattr(_config, "MONGO_TIMEOUT_MS", 1)
    from app.graph.engine import CereBroZenEngine

    return CereBroZenEngine()


class _Inline:
    """Runs the off-path builders on the calling thread — the SCHEDULER is substituted,
    every builder still runs for real. Exceptions are captured into the Future exactly as
    ThreadPoolExecutor does, so a failing builder still cannot surface in the turn."""

    def __init__(self):
        self.submitted: list[str] = []

    def submit(self, fn, *args, **kwargs):
        from concurrent.futures import Future

        self.submitted.append(getattr(fn, "__name__", str(fn)))
        fut: Future = Future()
        try:
            fut.set_result(fn(*args, **kwargs))
        except BaseException as exc:  # noqa: BLE001 — mirror ThreadPoolExecutor exactly
            fut.set_exception(exc)
        return fut


@pytest.fixture
def inline_builders(monkeypatch):
    ex = _Inline()
    monkeypatch.setattr(builders, "_EXECUTOR", ex)
    return ex


# ═══════════════════════════════════════════════════════════════════════════════
# _run_stage — the shared LLM turn, and every way it degrades
# ═══════════════════════════════════════════════════════════════════════════════


def test_an_open_breaker_degrades_the_turn_instead_of_500ing_it(engine, user_id, llm,
                                                                inline_builders, caplog):
    """OpenAI is persistently down and the circuit breaker is open. The turn must come back
    as an availability MESSAGE, not an exception: a 500 loses the user's message, and the
    stage must not advance or they'd resume a step they never had."""
    llm(default=BreakerOpen("openai is down"))
    caplog.set_level(logging.WARNING, logger="cerebrozen.nodes")
    session_id = f"s-{uuid.uuid4().hex[:8]}"
    tokens: list[str] = []

    result = engine.run_turn_stream(
        user_id=user_id, session_id=session_id, bot_name="CereBroZen",
        user_message="I keep avoiding a hard conversation", on_token=tokens.append,
    )

    assert result["response_to_user"] == nodes.BREAKER_REPLY
    assert result["handoff_ready"] is False
    assert result["stage"] == STAGE_INTAKE, "the breaker advanced the user past a step they never took"
    assert "".join(tokens) == "", "the breaker short-circuits before any token is generated"
    assert "node.breaker_open" in {r.message for r in caplog.records}

    # And the next turn resumes on the SAME stage — the user can simply try again.
    assert engine.run_turn_stream(
        user_id=user_id, session_id=session_id, bot_name="CereBroZen", user_message="retry",
    )["stage"] == STAGE_INTAKE


def test_prose_written_around_a_control_envelope_is_salvaged_not_re_generated(llm, caplog):
    """Live incident (2026-07-06, action_checkin): the model wrote a greeting and THEN the
    raw handoff JSON. The greeting had already streamed to the screen, but no user-text key
    existed in the envelope, so the silent retry recorded a DIFFERENT message — screen and
    transcript diverged. Salvage the prose: the recorded reply is what the user actually
    read, and a 20-30s retry is skipped."""
    caplog.set_level(logging.INFO, logger="cerebrozen.nodes")
    greeting = "Welcome back — good to see you again. Let's pick up where we left off."
    client = llm(script={STAGE_INTAKE: f'{greeting}\n```json\n{{"handoff_ready": false, '
                                       f'"context_update": {{"current_step": "q1"}}}}\n```'})

    out = run_node(nodes.intake_node)

    assert out["reply_text"] == greeting
    assert out["handoff_ready"] is False
    assert len(client.calls_for(STAGE_INTAKE)) == 1, "a salvageable turn still paid for a retry"
    assert "node.empty_reply_prose_salvaged" in {r.message for r in caplog.records}


def test_a_blank_reply_is_retried_once_and_the_retry_drives_the_whole_turn(llm, caplog):
    """Round-1 bug #1: a non-handoff turn with no user-facing text is a BLANK chat bubble.
    Retry once — and when it recovers, the retry's envelope must drive everything
    downstream (reply, captured variables, progress), not just the text. Adopting the text
    but parsing the DEAD first response is how a recovered turn loses the answer the user
    just gave."""
    caplog.set_level(logging.INFO, logger="cerebrozen.nodes")
    client = llm(script={STAGE_INTAKE: [
        {"handoff_ready": False},  # pure envelope: nothing to say, nothing to salvage
        _envelope("What does 'good' look like by Friday?",
                  variables_set={"userRoleContext": "EM, 8 reports"}),
    ]})

    out = run_node(nodes.intake_node)

    assert out["reply_text"] == "What does 'good' look like by Friday?"
    assert out["captured_variables"] == {"userRoleContext": "EM, 8 reports"}, \
        "the recovered turn parsed the dead first response, losing the user's answer"
    assert out["history"][-1] == {"role": "assistant", "content": out["reply_text"]}
    retry = client.calls_for(STAGE_INTAKE)[1]
    assert nodes._EMPTY_REPLY_RETRY_NUDGE in retry.user_prompt
    assert retry.streamed is False, "the recovered turn was streamed twice to the client"
    assert "node.empty_reply_recovered" in {r.message for r in caplog.records}


@pytest.mark.parametrize("second", [
    {"handoff_ready": False},                       # the retry is blank too
    RuntimeError("the model is on fire"),           # the retry itself explodes
])
def test_the_user_never_gets_a_blank_bubble_even_when_the_retry_fails(llm, second, caplog):
    """The floor under the recovery: two dead responses (or a retry that raises) must still
    end in a real, sendable sentence. An empty reply_text is a turn the user cannot answer."""
    caplog.set_level(logging.ERROR, logger="cerebrozen.nodes")
    llm(script={STAGE_INTAKE: [{"handoff_ready": False}, second]})

    out = run_node(nodes.intake_node)

    assert out["reply_text"] == nodes._EMPTY_REPLY_FALLBACK
    assert out["reply_text"].strip()
    assert out["handoff_ready"] is False
    assert {"node.empty_reply_fallback", "node.empty_reply_retry_failed"} & \
        {r.message for r in caplog.records}


def test_a_reply_streams_to_the_client_as_it_arrives(llm):
    """The user watches the answer type itself. The node streams the JSON through
    UserTextStreamer, which forwards only the user-facing VALUE — a raw envelope on screen
    would be worse than silence."""
    llm(script={STAGE_INTAKE: _envelope("Tell me about the last time it went well.")})
    tokens: list[str] = []

    out = run_node(nodes.intake_node, on_token=tokens.append)

    assert "".join(tokens) == "Tell me about the last time it went well."
    assert out["reply_text"] == "".join(tokens), "the screen and the transcript disagree"


def test_each_stage_runs_on_the_model_its_catalog_row_names(llm, monkeypatch):
    """Model choice is per-stage and it belongs to the workbook Catalog (hot-path stages run
    a fast non-reasoning model; core coaching stays on the reasoning one). Resolve it from
    anywhere else and a prompt engineer's model change silently does nothing — the stage
    keeps running on the old model and nobody finds out until the bill or the latency."""
    monkeypatch.delenv("CEREBROZEN_MODEL_OVERRIDE", raising=False)   # the pin-one-model hatch
    registry = runtime.get_registry()
    monkeypatch.setitem(registry._models, STAGE_INTAKE, "gpt-4o-mini")
    monkeypatch.setitem(registry._models, STAGE_CORE, "gpt-5-mini")
    client = llm()

    run_node(nodes.intake_node)
    run_node(nodes.core_coaching_node, {"stage": STAGE_CORE, "coaching_path": "CIM"})

    assert client.calls_for(STAGE_INTAKE)[0].model == "gpt-4o-mini"
    assert client.calls_for(STAGE_CORE)[0].model == "gpt-5-mini"


def test_a_test_user_is_served_a_static_reply_without_paying_for_a_model_call(llm,
                                                                              monkeypatch):
    """The load/security-test users must never reach the model — a perf run would otherwise
    bill real tokens on every synthetic turn — but they must still get a routable turn."""
    monkeypatch.setattr(nodes, "CEREBROZEN_TEST_USERS", {"perf-bot"})
    client = llm()

    out = run_node(nodes.intake_node, {"user_id": "perf-bot"})

    assert out["reply_text"] == "[Static reply — test user, no LLM call]"
    assert out["handoff_ready"] is True and out["stage"] == STAGE_CHALLENGE
    assert out["cost_usd"] == 0.0
    assert out["history"][-1]["content"] == out["reply_text"]
    assert client.calls == [], "a synthetic test user paid for a real model call"


def test_a_stray_handoff_without_agent_complete_never_ends_the_simulation_early(llm, caplog):
    """role_play flips handoff_ready true on its persona-build turn. Honoured, that skips
    the ENTIRE rehearsal after its first question and fires the post-simulation pattern
    mirror against it. Only `agent_complete` may complete these stages."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.nodes")
    llm(script={STAGE_ROLEPLAY: {"response_to_user": "Who are we rehearsing with?",
                                 "handoff_ready": True, "current_step": "persona_build"}})

    out = run_node(nodes.role_play_node, {"stage": STAGE_ROLEPLAY})

    assert out["handoff_ready"] is False
    assert "stage" not in out, "a persona-build turn advanced the session past the rehearsal"
    assert out["reply_text"] == "Who are we rehearsing with?"
    warned = [r for r in caplog.records if r.message == "node.premature_handoff_ignored"]
    assert warned and warned[0].current_step == "persona_build"


@pytest.mark.parametrize("flag", ["false", "no", "tru", "pending", "0"])
def test_a_stringified_agent_complete_never_ends_a_simulation_early(llm, flag):
    """`bool("false")` is True. A model that emits `"agent_complete": "false"` was therefore
    read as DONE and the rehearsal ended on its first turn — the user is yanked out of the
    simulation into the post-coaching layer mid-arc, and nothing errors.

    This is NOT a hypothetical model quirk. json_repair — which salvages output truncated at
    the token ceiling — repairs a cut-off `"agent_complete": tru` into the STRING "tru", which
    bool() also reads as True. So a response that merely ran out of tokens manufactures a
    spurious handoff. An unrecognised flag must be FALSE: an ambiguous signal may never
    complete a stage.

    (The floor gate is exhausted here on purpose, so it cannot mask the bug by deferring the
    completion for its own reasons.)"""
    llm(script={STAGE_ROLEPLAY: {"response_to_user": "Dana pushes back. What now?",
                                 "handoff_ready": True, "agent_complete": flag}})

    out = run_node(nodes.role_play_node, {
        "stage": STAGE_ROLEPLAY,
        "gate_reprompts": {STAGE_ROLEPLAY: nodes._MAX_FLOOR_REPROMPTS},
        "gate_turns": {STAGE_ROLEPLAY: 5},
    })

    assert out["handoff_ready"] is False, f'agent_complete="{flag}" completed the simulation'
    assert "stage" not in out, "the session advanced past a rehearsal that never finished"
    assert out["reply_text"] == "Dana pushes back. What now?"


@pytest.mark.parametrize("flag", ["false", "tru"])
def test_a_stringified_agent_complete_in_the_RECOVERY_retry_is_read_by_value_too(llm, flag):
    """The same flag, on the empty-reply recovery path — which re-reads `agent_complete`
    itself rather than going back through parse_control, so it needs the same by-value parse.
    A blank turn followed by a TRUNCATED retry ("agent_complete": tru) is the exact pairing
    that ends a simulation the user never finished: two model failures in one turn, and the
    second one silently advances the stage."""
    llm(script={STAGE_ROLEPLAY: [
        {"handoff_ready": False},                          # blank turn → triggers the retry
        {"response_to_user": "Dana crosses her arms. Your move.",
         "handoff_ready": True, "agent_complete": flag},   # the retry, truncated/stringified
    ]})

    out = run_node(nodes.role_play_node, {
        "stage": STAGE_ROLEPLAY,
        "gate_reprompts": {STAGE_ROLEPLAY: nodes._MAX_FLOOR_REPROMPTS},
        "gate_turns": {STAGE_ROLEPLAY: 5},
    })

    assert out["reply_text"] == "Dana crosses her arms. Your move."   # the retry was adopted…
    assert out["handoff_ready"] is False, f'agent_complete="{flag}" completed the simulation'
    assert "stage" not in out                                          # …but it did NOT complete


def test_ch_completes_on_its_phase_3_milestone_even_without_agent_complete(llm):
    """CH's genuine Phase-3 completion is signalled by current_step, and the model does not
    reliably also set agent_complete. Requiring both strands every CH session at the end of
    its arc — the coaching finishes and the user is never handed to the closing layer."""
    llm(script={STAGE_CH: {"response_to_user": "You've built the full blueprint.",
                           "handoff_ready": True, "current_step": "phase_3_complete",
                           "phase": "3"}})

    out = run_node(nodes.capability_coaching_node,
                   {"stage": STAGE_CORE, "coaching_path": "CH", "active_phase": "2"})

    assert out["handoff_ready"] is True
    assert out["stage"] == STAGE_DYNAMIC_ACTIONS      # → the full post-coaching layer
    assert out["actions_next_stage"] == STAGE_SIMULATION_DECISION
    assert out["ch_awaiting_transition"] is False


# ── the completion FLOOR ──────────────────────────────────────────────────────


def test_an_agent_that_completes_far_too_early_is_deferred_and_told_to_continue(llm, caplog):
    """Round-1 bug #2: role_play self-certified `agent_complete` right after persona_build,
    skipping the rounds entirely. Below the floor a completion is DEFERRED — the turn
    re-prompts the agent to continue, and the user reads THAT instead of being dumped out of
    a rehearsal that never happened."""
    caplog.set_level(logging.INFO, logger="cerebrozen.nodes")
    llm(script={STAGE_ROLEPLAY: [
        _done("Great work, that's the rehearsal done."),          # on turn 1 of 4
        _envelope("Dana says: 'I don't think that's fair.' What do you say back?"),
    ]})

    out = run_node(nodes.role_play_node, {"stage": STAGE_ROLEPLAY})

    assert out["handoff_ready"] is False, "the simulation ended on its persona-build turn"
    assert out["reply_text"] == "Dana says: 'I don't think that's fair.' What do you say back?"
    assert "stage" not in out
    assert out["gate_reprompts"] == {STAGE_ROLEPLAY: 1}
    assert out["gate_turns"] == {STAGE_ROLEPLAY: 1}
    assert "node.completion_floor_deferred" in {r.message for r in caplog.records}


def test_the_floor_relents_so_a_finished_user_is_never_trapped_in_a_rehearsal(llm):
    """A FLOOR, not a script: after _MAX_FLOOR_REPROMPTS deferrals the completion is
    honoured. A user who is genuinely done (or declining) must be able to leave."""
    llm(script={STAGE_ROLEPLAY: _done("That's the rehearsal done.")})

    out = run_node(nodes.role_play_node, {
        "stage": STAGE_ROLEPLAY,
        "gate_reprompts": {STAGE_ROLEPLAY: nodes._MAX_FLOOR_REPROMPTS},
    })

    assert out["handoff_ready"] is True
    assert out["stage"] == STAGE_DYNAMIC_ACTIONS
    assert out["actions_next_stage"] == STAGE_PATTERN, "the reflect beat was skipped"
    assert out["action_agent_type"] == STAGE_ROLEPLAY


@pytest.mark.parametrize("reprompt,expected_reply", [
    ({"handoff_ready": False}, nodes._EMPTY_REPLY_FALLBACK),      # the re-prompt says nothing
    (RuntimeError("boom"), "Great work, that's done."),            # the re-prompt explodes
])
def test_a_failed_floor_reprompt_still_refuses_the_early_completion(llm, reprompt,
                                                                    expected_reply, caplog):
    """The gate itself may fail — the model can return nothing or blow up. Either way the
    ONE thing it must not do is let the premature completion through, and the user still
    has to get a sendable turn."""
    caplog.set_level(logging.ERROR, logger="cerebrozen.nodes")
    llm(script={STAGE_ROLEPLAY: [_done("Great work, that's done."), reprompt]})

    out = run_node(nodes.role_play_node, {"stage": STAGE_ROLEPLAY})

    assert out["handoff_ready"] is False
    assert out["reply_text"] == expected_reply
    assert out["gate_reprompts"] == {STAGE_ROLEPLAY: 1}


# ── the completion CEILING ────────────────────────────────────────────────────


def test_a_closing_arc_stuck_on_one_step_is_forced_to_close(llm, mongo, watchdog, caplog):
    """Live: the CH closing arc re-asked the commitment scale turn after turn (QA-user-1's
    emotion loop repeated 7x). A stage only advances when its own prompt says so, so the
    session could never reach `close` and mood/feedback was never captured. Past the ceiling
    of consecutive same-step turns, the handoff is FORCED."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.nodes")
    llm(script={STAGE_FEEDBACK: _envelope("On a scale of 1-10, how committed are you?",
                                          current_step="C1")})

    out = run_node(nodes.feedback_node, {
        "coaching_path": "CIM", "stage": STAGE_FEEDBACK,
        "feedback_last_step": "C1", "feedback_step_repeats": nodes._COMPLETION_CEILING[
            STAGE_FEEDBACK] - 1,
    })

    assert out["handoff_ready"] is True, "the user is still trapped in the wrap-up"
    assert out["stage"] == STAGE_CLOSE
    assert out["feedback_step_repeats"] == nodes._COMPLETION_CEILING[STAGE_FEEDBACK]
    assert out["reply_text"].strip(), "the forced close swallowed the agent's last words"
    assert "node.feedback_completion_ceiling" in {r.message for r in caplog.records}


def test_a_repeated_step_is_nudged_to_advance_before_it_is_forced(llm):
    """Before the blunt force there is a nudge: from _FEEDBACK_NUDGE_AT repeats the agent is
    told the user has already answered. The nudge has to reach the MODEL — a counter that
    only increments changes nothing about the loop."""
    client = llm(script={STAGE_FEEDBACK: _envelope("How committed are you?", current_step="C1")})

    out = run_node(nodes.feedback_node, {
        "coaching_path": "CIM", "feedback_last_step": "C1",
        "feedback_step_repeats": nodes._FEEDBACK_NUDGE_AT,
    })

    assert nodes._FEEDBACK_ADVANCE_NUDGE in client.calls_for(STAGE_FEEDBACK)[0].system_prompt
    assert out["feedback_step_repeats"] == nodes._FEEDBACK_NUDGE_AT + 1
    assert out["handoff_ready"] is False  # still under the ceiling: nudged, not forced


def test_moving_to_a_new_step_resets_the_loop_counter(llm):
    """The ceiling counts CONSECUTIVE repeats of ONE step. If a normal step change didn't
    reset it, a long-but-healthy closing arc would be force-closed mid-way."""
    llm(script={STAGE_FEEDBACK: _envelope("What will you take away?", current_step="F1")})

    out = run_node(nodes.feedback_node, {
        "coaching_path": "CIM", "feedback_last_step": "M2", "feedback_step_repeats": 3,
    })

    assert (out["feedback_last_step"], out["feedback_step_repeats"]) == ("F1", 1)
    assert out["handoff_ready"] is False


# ── the stage-stuck WATCHDOG ──────────────────────────────────────────────────


@pytest.mark.parametrize("stage,node", [
    (STAGE_CHALLENGE, nodes.challenge_node),   # the 78-turn incident stage (cap 15)
    (STAGE_INTAKE, nodes.intake_node),         # cap 20
])
def test_the_watchdog_forces_the_handoff_once_a_stage_passes_its_turn_cap(
    stage, node, llm, watchdog, caplog
):
    """A stage advances ONLY when its own prompt signals completion — so a prompt that never
    emits one pins the session forever. A live voice session sat in challenge_context for 78
    consecutive turns. Past the per-stage cap the handoff is forced: the user still gets this
    turn's reply, and the NEXT turn starts on the next stage."""
    caplog.set_level(logging.ERROR, logger="cerebrozen.nodes")
    cap = nodes._stage_turn_cap(stage)
    llm(script={stage: _envelope("Tell me more about that.", coaching_path="CIM")})

    stuck = run_node(node, {"stage": stage, "gate_turns": {stage: cap - 2}})
    assert stuck["handoff_ready"] is False, "the watchdog fired before the cap"
    assert "stage" not in stuck
    assert watchdog == []

    forced = run_node(node, {"stage": stage, "gate_turns": {stage: cap - 1}})
    assert forced["handoff_ready"] is True, f"{stage} pinned the session past {cap} turns"
    assert forced["stage"] != stage, "the forced handoff did not advance the session"
    assert forced["reply_text"].strip(), "the forced advance ate the user's reply"
    assert forced["gate_turns"] == {stage: cap}
    assert watchdog == [stage], "a stuck stage advanced with no metric — invisible in prod"

    logged = [r for r in caplog.records if r.message == "node.stage_watchdog_forced_advance"]
    assert logged and logged[0].turns == cap and logged[0].cap == cap


def test_every_stage_has_a_finite_cap_and_the_long_arcs_get_room_to_finish(llm):
    """The caps are per-stage on purpose: too low and a legitimately long arc (the 3-phase
    CH conversation) is amputated mid-coaching; too high and the stuck stage is never
    caught. Both failures are silent, so the table is the contract."""
    assert nodes._stage_turn_cap(STAGE_CHALLENGE) < 78      # the incident that created this
    assert nodes._stage_turn_cap(STAGE_CH) > nodes._stage_turn_cap(STAGE_CHALLENGE)
    assert nodes._stage_turn_cap(STAGE_SIMULATION_DECISION) < 10   # a 2-turn sticky gate
    assert nodes._stage_turn_cap("a_stage_with_no_row") == nodes._STAGE_MAX_TURNS_DEFAULT
    # Every floor sits well below its stage's cap, or the floor could never be satisfied.
    for stage, floor in nodes._COMPLETION_FLOOR_TURNS.items():
        assert floor < nodes._stage_turn_cap(stage)


# ── CONTRACT REPAIR ───────────────────────────────────────────────────────────


def test_a_challenge_handoff_with_no_coaching_path_is_repaired_before_it_is_honoured(
    llm, caplog
):
    """Measured ~1 turn in 6 on the live stack: challenge_context completes without
    `coaching_path`. Nothing errors — the router just falls back to CIM, and the user is
    coached with the wrong methodology for their ENTIRE session. Re-prompt once, naming the
    missing field, and adopt the corrected decision."""
    caplog.set_level(logging.INFO, logger="cerebrozen.nodes")
    client = llm(script={STAGE_CHALLENGE: [
        _done("Got it — that's a capability you want to build.", context_update={
            "session_goal": "get better at leading through conflict"}),   # no coaching_path
        _done("Got it — that's a capability you want to build.",
              context_update={"coaching_path": "CH"}),
    ]})

    out = run_node(nodes.challenge_node, {"stage": STAGE_CHALLENGE})

    assert out["coaching_path"] == "CH"
    assert out["handoff_ready"] is True
    assert bg._coaching_route(out) == "capability", \
        "the repaired path did not reach the router — the session is still coached as CIM"
    assert nodes._CONTRACT_REPAIR["challenge_no_coaching_path"] in \
        client.calls_for(STAGE_CHALLENGE)[1].user_prompt
    assert "node.contract_repaired" in {r.message for r in caplog.records}


def test_a_repair_that_fails_gives_up_loudly_rather_than_trapping_the_user(llm, caplog):
    """The repair is bounded to ONE extra call. When the model still won't emit a path, the
    session must move on (a trapped user is worse than a mis-routed one) — but the give-up
    has to be visible, because from here on the CIM fallback is indistinguishable from a
    real CIM decision."""
    caplog.set_level(logging.ERROR, logger="cerebrozen.nodes")
    llm(script={STAGE_CHALLENGE: _done("Right, let's coach on that.")})   # never emits a path

    out = run_node(nodes.challenge_node, {"stage": STAGE_CHALLENGE})

    assert out["handoff_ready"] is True and out["stage"] == STAGE_CORE
    assert not out.get("coaching_path")
    assert bg._coaching_route(out) == "core"       # the logged last-resort fallback
    failed = [r for r in caplog.records if r.message == "node.contract_repair_failed"]
    assert failed and failed[0].violation == "challenge_no_coaching_path"


def test_a_repair_call_that_explodes_never_breaks_the_turn(llm, caplog):
    """The repair is an extra network call on a turn the user is already waiting on. If it
    raises, the user must still get the reply the agent already produced."""
    caplog.set_level(logging.ERROR, logger="cerebrozen.nodes")
    llm(script={STAGE_CHALLENGE: [_done("Right, let's coach on that."),
                                  RuntimeError("the repair call died")]})

    out = run_node(nodes.challenge_node, {"stage": STAGE_CHALLENGE})

    assert out["reply_text"] == "Right, let's coach on that."
    assert out["handoff_ready"] is True
    assert "node.contract_repair_errored" in {r.message for r in caplog.records}


def test_only_a_routing_violation_is_repaired_others_are_merely_recorded(llm, mongo, caplog):
    """A repair costs a whole extra LLM call, so it must buy CORRECTNESS, not tidiness. The
    actions gate emitting no cards is a real violation — it is counted, never re-prompted."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.contracts")
    llm(script={STAGE_LEARNING_AID: _done("Here's the idea to try.")})

    out = run_node(nodes.learning_aid_node, {"stage": STAGE_LEARNING_AID})

    assert out["handoff_ready"] is True     # not deferred, not re-prompted
    violations = [r for r in caplog.records if r.message == "agent.contract_violation"]
    assert not violations or "challenge_no_coaching_path" not in violations[0].violations


def test_a_contract_monitor_that_explodes_never_breaks_the_turn_it_watches(llm, monkeypatch,
                                                                           caplog):
    """The monitor is advisory. A bug in the checker itself (a shape it can't read) must
    cost telemetry, never the user's reply."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.nodes")

    def _boom(*a, **kw):
        raise RuntimeError("the contract checker is broken")

    monkeypatch.setattr(nodes, "check_turn_contract", _boom)
    llm(script={STAGE_INTAKE: _envelope("What's the hardest part?")})

    out = run_node(nodes.intake_node)

    assert out["reply_text"] == "What's the hardest part?"
    assert "node.contract_check_failed" in {r.message for r in caplog.records}


# ── the arc-progress carry-over (why sessions stop repeating themselves) ──────


def test_progress_survives_history_truncation_so_the_coach_stops_re_asking_q1(llm):
    """Root cause of the CIM "repeats questions, never ends" loop: the arc position lived
    ONLY in the transcript, and the transcript is capped at 40 messages. Carried progress is
    re-injected as an authoritative block, so the node continues from where it is even when
    its early turns have scrolled out of the window."""
    client = llm(script={STAGE_CORE: _envelope("And what did you try then?",
                                               context_update={"current_question_number": 5})})

    out = run_node(nodes.core_coaching_node, {
        "stage": STAGE_CORE, "coaching_path": "CIM",
        "coaching_progress": {"behavioral_intake_complete": True,
                              "current_question_number": 4, "selected_model": "GROW"},
    })

    prompt = client.calls_for(STAGE_CORE)[0].system_prompt
    assert "CARRY-OVER SESSION PROGRESS (authoritative)" in prompt
    assert '"current_question_number": 4' in prompt and '"selected_model": "GROW"' in prompt
    # Monotonic: this turn's delta merges ONTO the carried state; nothing already true is lost.
    assert out["coaching_progress"]["behavioral_intake_complete"] is True
    assert out["coaching_progress"]["current_question_number"] == 5
    assert out["coaching_progress"]["selected_model"] == "GROW"


def test_an_empty_progress_delta_can_never_erase_progress_already_made(llm):
    """The model omits a field it set two turns ago (or regresses a completion flag back to
    false). Merged naively, that ERASES the fact that behavioural intake is done and the
    coach re-runs it. Progress is monotonic."""
    llm(script={STAGE_CORE: _envelope("Say more.", context_update={
        "behavioral_intake_complete": False, "selected_model": "",
        "behavioral_context": {"energy": "low"}})})

    out = run_node(nodes.core_coaching_node, {
        "stage": STAGE_CORE, "coaching_path": "CIM",
        "coaching_progress": {"behavioral_intake_complete": True, "selected_model": "GROW",
                              "behavioral_context": {"tone": "flat"}},
    })

    progress = out["coaching_progress"]
    assert progress["behavioral_intake_complete"] is True   # a false never clobbers a true
    assert progress["selected_model"] == "GROW"             # an "" never clobbers a value
    assert progress["behavioral_context"] == {"tone": "flat", "energy": "low"}  # merged 1 deep


def test_the_learning_aid_is_seeded_on_its_first_turn_so_it_cannot_skip_the_delivery(llm):
    """By the time the learning aid runs, the history already looks "done" (coaching covered
    the topic, actions were committed) — so the agent collapsed straight to `commit` and the
    user never saw the aid. Its arc lives in its OWN channel, seeded on turn one."""
    client = llm(script={STAGE_LEARNING_AID: _envelope("Here's a frame that may help…",
                                                       context_update={"current_step": "grasp"})})

    first = run_node(nodes.learning_aid_node, {"stage": STAGE_LEARNING_AID})
    assert "FIRST turn of the learning-aid delivery" in \
        client.calls_for(STAGE_LEARNING_AID)[0].system_prompt
    assert first["learning_aid_progress"]["current_step"] == "grasp"

    client2 = llm(script={STAGE_LEARNING_AID: _envelope("How would you apply it?")})
    run_node(nodes.learning_aid_node, {"stage": STAGE_LEARNING_AID,
                                       "learning_aid_progress": {"current_step": "grasp"}})
    later = client2.calls_for(STAGE_LEARNING_AID)[0].system_prompt
    assert "CARRY-OVER LEARNING-AID PROGRESS (authoritative)" in later
    assert '"current_step": "grasp"' in later


def test_the_ch_closing_arc_is_told_phase_1_already_ran_upstream(llm, mongo):
    """CH runs Commitment & Support inside the coaching arc, so the closing agent must SKIP
    its Phase 1. Asserting that only on the first turn lost to the prompt's own script on
    later turns and re-opened the commitment loop (live, 2026-07-09) — so it is asserted on
    every turn, and hard-seeded into the carried state where the model cannot drop it."""
    client = llm(script={STAGE_FEEDBACK: _envelope("Picture yourself a month from now…",
                                                   current_step="V1")})

    out = run_node(nodes.feedback_node, {"coaching_path": "CH", "stage": STAGE_FEEDBACK})

    prompt = client.calls_for(STAGE_FEEDBACK)[0].system_prompt
    assert "Phase 1 (Commitment & Support) already ran" in prompt
    assert out["feedback_progress"]["commitment_support"] == {"skipped": True}
    assert out["feedback_progress"]["commitment_support_complete"] is True

    # And on the NEXT turn the carried state says so too — the model can't drift back in.
    client2 = llm(script={STAGE_FEEDBACK: _envelope("What stands out?", current_step="M1")})
    run_node(nodes.feedback_node, {"coaching_path": "CH",
                                   "feedback_progress": out["feedback_progress"]})
    carried = client2.calls_for(STAGE_FEEDBACK)[0].system_prompt
    assert "CARRY-OVER FEEDBACK PROGRESS (authoritative)" in carried
    assert '"skipped": true' in carried


def test_the_checkin_arc_remembers_the_rating_it_already_asked_for(llm):
    """action_checkin has a FLAT contract and infers its step from which fields are filled.
    The OSCAR reflection tail pushes the rating out of the history window, so it re-asked it.
    Its step-completion fields ride their own channel and are re-injected."""
    llm(script={STAGE_ACTION_CHECKIN: _envelope("What got in the way?",
                                                satisfaction_rating=7, story_shared=True)})

    first = run_node(nodes.action_checkin_node, {"stage": STAGE_ACTION_CHECKIN})
    assert first["checkin_progress"] == {"satisfaction_rating": 7, "story_shared": True}

    client2 = llm(script={STAGE_ACTION_CHECKIN: _envelope("And what will you try next?")})
    run_node(nodes.action_checkin_node, {"stage": STAGE_ACTION_CHECKIN,
                                         "checkin_progress": first["checkin_progress"]})
    prompt = client2.calls_for(STAGE_ACTION_CHECKIN)[0].system_prompt
    assert "CARRY-OVER CHECK-IN PROGRESS (authoritative)" in prompt
    assert '"satisfaction_rating": 7' in prompt


@pytest.mark.parametrize("progress", [
    {},                                                   # turn one
    {"current_step": "", "selected_model": None},          # a turn that captured nothing real
])
def test_a_stage_with_no_real_progress_yet_gets_no_carry_over_block(llm, progress):
    """An empty block would be pure prompt noise — and worse, an authoritative "here is your
    progress: {}" invites the model to treat a fresh arc as a resumed one and skip its
    opening. A progress dict full of blanks is the same thing wearing a hat."""
    client = llm(script={STAGE_CORE: _envelope("Where would you like to start?")})

    out = run_node(nodes.core_coaching_node, {"stage": STAGE_CORE, "coaching_path": "CIM",
                                              "coaching_progress": progress})

    assert "CARRY-OVER SESSION PROGRESS" not in client.calls_for(STAGE_CORE)[0].system_prompt
    assert out["reply_text"] == "Where would you like to start?"


def test_a_model_that_returns_only_whitespace_is_recovered_like_any_other_blank_turn(llm):
    """Not every dead turn arrives as an envelope: a model can return literal whitespace.
    There is no JSON to salvage prose from, so the recovery has to fall straight through to
    the retry rather than trying to reason about a document that isn't there."""
    llm(script={STAGE_INTAKE: ["   \n  ", _envelope("Let's start with what happened.")]})

    out = run_node(nodes.intake_node)

    assert out["reply_text"] == "Let's start with what happened."
    assert out["handoff_ready"] is False


def test_a_checkin_that_drops_its_json_contract_still_answers_the_user(llm):
    """A prompt that replies in prose emits no step fields at all. That is not an error — but
    it must not be mistaken for progress either, or a half-parsed step lands in the carried
    state and the agent skips a question the user never answered."""
    llm(script={STAGE_ACTION_CHECKIN: "So — how did it actually go with Dana?"})

    out = run_node(nodes.action_checkin_node, {"stage": STAGE_ACTION_CHECKIN,
                                               "checkin_action": {}})

    assert out["reply_text"] == "So — how did it actually go with Dana?"
    assert "checkin_progress" not in out


# ── captured variables: the bridge from context_update to the next session ────


def test_context_update_fields_reach_both_the_prompt_and_the_store(llm, mongo, user_id):
    """challenge_context/core/CH emit their captures in `context_update`, not
    `variables_set`. Without this bridge they were held in-session only and never written —
    so a brand-new session had no way to know the coaching style was already chosen, and
    asked again. The registry sheet (not a hand-kept whitelist in code) decides what is
    lifted."""
    llm(script={STAGE_CH: _envelope("What does mastery look like to you?", context_update={
        "confirmed_competency": "influencing without authority",
        "userStrengths": {"self_reported": "listening"},   # the CH contract's camelCase key
        "short_term_goal": "run the next review myself",   # legacy, unregistered
    })})

    out = run_node(nodes.capability_coaching_node, {
        "stage": STAGE_CORE, "coaching_path": "CH", "user_id": user_id,
        # Captured on EARLIER turns of this session — the bridge must re-expose them to the
        # placeholder resolver every turn, not just the turn they were emitted on.
        "coaching_progress": {"current_phase": "2",
                              "coaching_style_context": {"selected_style": "direct"},
                              "time_context": {"available_time": "20 minutes"},
                              "session_goal": "lead through conflict",
                              "userGaps": {"self_reported": "avoids conflict"}},
    })

    captured = out["captured_variables"]
    assert captured["confirmed_competency"] == "influencing without authority"
    assert captured["user_strengths"] == {"self_reported": "listening"}, \
        "the camelCase spelling was dropped — the value never reaches Mongo"
    assert captured["short_term_goal"] == "run the next review myself"
    # …and the same values are in user_context, so THIS session's later agents resolve them.
    assert out["user_context"]["confirmed_competency"] == "influencing without authority"
    assert out["user_context"]["session_goal"] == "lead through conflict"
    # The aliases exist because the prompts spell these three differently from the state:
    # {currentPhase} (camel), {coaching_style_context.selected_style} (dot-notation, which the
    # placeholder regex cannot resolve) and {timeAvailable}. No alias → a blank in the prompt.
    assert out["user_context"]["currentPhase"] == "2"
    assert out["user_context"]["coaching_style_context_selected_style"] == "direct"
    assert out["user_context"]["timeAvailable"] == "20 minutes"
    # The CH contract writes these three in camelCase; the prompts read them in snake_case.
    assert out["user_context"]["user_gaps"] == {"self_reported": "avoids conflict"}


def test_intake_variables_never_null_out_an_answer_the_user_already_gave(llm):
    """intake's schema emits EVERY field on every turn, with null for anything not yet
    answered. Spread naively over user_context, that null punches out a value restored from
    Mongo — silently re-opening a question the user answered in a previous session."""
    llm(script={STAGE_INTAKE: _envelope("And how do you like to be challenged?", variables_set={
        "userRoleContext": "EM, 8 reports", "coachingHistory": None, "ci_openness": "",
    })})

    out = run_node(nodes.intake_node, {
        "user_context": {"coachingHistory": "two years with an external coach"},
    })

    assert out["user_context"]["coachingHistory"] == "two years with an external coach"
    assert out["user_context"]["userRoleContext"] == "EM, 8 reports"
    assert out["captured_variables"]["userRoleContext"] == "EM, 8 reports"


@pytest.mark.parametrize("emitted,expected", [(True, True), ("true", True), ("false", False)])
def test_the_simulation_routing_signal_is_lifted_out_of_the_coaching_envelope(llm, emitted,
                                                                              expected):
    """The coaching agent infers during Q1-Q2 whether a specific PERSON is involved; the
    deterministic simulation edge reads it from top-level state. Left buried in
    context_update (or left as the string "true"), every session defaults to SJT and nobody
    ever gets to rehearse the conversation they actually came in about."""
    llm(script={STAGE_CORE: _envelope("What would you say to them?",
                                      context_update={"specific_person_identified": emitted})})

    out = run_node(nodes.core_coaching_node, {"stage": STAGE_CORE, "coaching_path": "CIM"})

    assert out["specific_person_identified"] is expected
    assert nodes._route_simulation(out) == (STAGE_ROLEPLAY if expected else STAGE_SJT)


# ═══════════════════════════════════════════════════════════════════════════════
# capability_coaching_node — the CH phase machine
# ═══════════════════════════════════════════════════════════════════════════════


def test_save_and_exit_closes_the_session_in_the_same_turn(graph, llm, mongo, user_id,
                                                           inline_builders):
    """Business rule (2026-07-10): Save & Exit means exit — straight to Home, no pattern
    reflection, no feedback ritual. The empty reply must CHAIN into the terminal node inside
    this turn, or the client navigates away on a blank message."""
    llm()
    final = run_graph(graph, user_id=user_id, stage=STAGE_CORE, coaching_path="CH",
                      session_continued="save_and_exit", user_message="saved")

    assert final["stage"] == STAGE_CLOSE
    assert final["ch_early_exit"] is True
    assert final["response_to_user" if "response_to_user" in final else "reply_text"] == \
        nodes.SESSION_COMPLETE_REPLY
    assert final["active_node"] == "session_complete"
    assert final["ch_awaiting_transition"] is False


def test_an_inlay_ack_at_a_phase_boundary_never_reaches_the_model(llm, caplog):
    """While the user is parked at a phase boundary the client sends the card outcomes as a
    bare text turn ("saved|saved|delete"). Fed to a CH model still standing at that boundary,
    it improvised: re-emitting phase_1_complete (QA-user-1 got the Phase-1 cards THREE times)
    or drifting into a "Welcome back" opening mid-session. Swallow it, keep the boundary
    parked, and never re-send the buttons (the client stacks each payload as a new row)."""
    caplog.set_level(logging.INFO, logger="cerebrozen.nodes")
    client = llm()

    out = run_node(nodes.capability_coaching_node, {
        "stage": STAGE_CORE, "coaching_path": "CH", "active_phase": "1",
        "ch_awaiting_transition": True, "user_message": "saved|saved|delete",
    })

    assert client.calls == [], "an action-card ack was fed to the coaching model"
    assert "Phase 2" in out["reply_text"] and "Save & Exit" in out["reply_text"]
    assert out["handoff_ready"] is False
    assert out["stage"] == STAGE_CORE           # still parked in the coaching slot
    assert out["phase_buttons"] == [], "the transition buttons were re-sent and duplicated"
    assert out["ch_awaiting_transition"] is True
    assert "history" not in out, "an ack is not conversation and must not enter the transcript"
    assert "node.ch_boundary_ack_swallowed" in {r.message for r in caplog.records}


def test_a_real_message_at_a_phase_boundary_still_reaches_the_coach(llm):
    """The ack swallow must be surgical: only save/skip/delete words. A user who types a real
    sentence while the buttons are on screen is CONTINUING to coach, and their turn must go
    to the model — and the pending-transition flag must clear, or a later `save_and_exit`
    (which the client also sends when resuming after a break) reads as a genuine exit."""
    llm(script={STAGE_CH: _envelope("Say more about what feels unfinished.", phase="1")})

    out = run_node(nodes.capability_coaching_node, {
        "stage": STAGE_CORE, "coaching_path": "CH", "active_phase": "1",
        "ch_awaiting_transition": True, "user_message": "I saved it but I'm not done yet",
    })

    assert out["reply_text"] == "Say more about what feels unfinished."
    assert out["ch_awaiting_transition"] is False
    assert out["handoff_ready"] is False


def test_completing_a_phase_fires_that_phase_s_action_beat_exactly_once(graph, llm, mongo,
                                                                        user_id, inline_builders):
    """Phase 1/2 completion is not the end of coaching — it opens the Action beat that
    captures the phase's actions, and the cards carry the Continue / Save & Exit buttons. The
    beat must fire ONCE: parked at the boundary, the model keeps re-emitting the milestone on
    every turn (that IS its contract — it is still there), and unguarded each re-emit
    regenerated a fresh set of cards (QA-user-1 saw three different Phase-1 card sets)."""
    llm(script={STAGE_CH: {"response_to_user": "That's Phase 1 complete.", "phase": "1",
                           "current_step": "phase_1_complete",
                           "awaiting_phase_transition": True}})

    first = run_graph(graph, user_id=user_id, stage=STAGE_CORE, coaching_path="CH",
                      user_message="that makes sense")

    # The CH bubble is suppressed and the turn chains into the action gate, which speaks.
    assert first["active_node"] == STAGE_DYNAMIC_ACTIONS
    assert first["reply_text"] == builders.ACTION_CARD_TITLE
    assert first["generated_actions"], "the phase completed with no action cards"
    assert first["ch_beats_fired"] == ["1"]
    assert first["ch_awaiting_transition"] is True
    assert first["actions_next_stage"] == STAGE_CORE, "the session left the CH coaching slot"
    assert [b["user_selection"] for b in first["phase_buttons"]] == \
        ["continue_to_phase_2", "save_and_exit"]

    # The same milestone re-emitted while parked: no second beat, no duplicate button row.
    again = run_node(nodes.capability_coaching_node, {
        "user_id": user_id, "stage": STAGE_CORE, "coaching_path": "CH",
        "active_phase": "1", "ch_beats_fired": ["1"], "ch_awaiting_transition": True,
        "user_message": "ok",
    })
    assert again["handoff_ready"] is False, "a re-emitted milestone re-ran the Action beat"
    assert again["stage"] != STAGE_DYNAMIC_ACTIONS if "stage" in again else True
    assert again["phase_buttons"] == []
    assert again["reply_text"] == "That's Phase 1 complete."


def test_a_phase_that_completes_in_prose_still_gets_its_action_beat(llm, caplog):
    """The CH model drops its JSON contract and answers in plain prose fairly often, so the
    phase_N_complete milestone never arrives — and the phase's Action beat and Continue
    buttons silently never fire (live: QA-user-1 lost Phase 2 entirely; the `phase` digit
    jumped 1 -> 3). The digit is the one signal we can trust: ARRIVING at a later phase means
    the previous one is done, so recover its beat deterministically."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.nodes")
    llm(script={STAGE_CH: _envelope("Now let's design the practice.", phase="3")})

    out = run_node(nodes.capability_coaching_node, {
        "stage": STAGE_CORE, "coaching_path": "CH",
        "active_phase": "1", "ch_beats_fired": ["1"],      # phase 2 ran entirely in prose
    })

    assert out["handoff_ready"] is True
    assert out["stage"] == STAGE_DYNAMIC_ACTIONS       # the recovered Action beat
    assert out["reply_text"] == "", "the stray next-phase bubble was not suppressed"
    assert out["actions_next_stage"] == STAGE_CORE
    assert out["ch_beats_fired"] == ["1", "2"]
    assert out["ch_awaiting_transition"] is True
    assert [b["user_selection"] for b in out["phase_buttons"]] == \
        ["continue_to_phase_3", "save_and_exit"]
    recovered = [r for r in caplog.records if r.message == "node.ch_phase_beat_recovered"]
    assert recovered and recovered[0].recovered_phase == "2"


def test_completing_phase_2_offers_the_phase_3_choice(llm):
    """Each boundary offers the NEXT phase. Offering "Continue to Phase 2" at the end of
    Phase 2 sends the user backwards through coaching they have already done."""
    llm(script={STAGE_CH: {"response_to_user": "That's Phase 2 complete.", "phase": "2",
                           "current_step": "phase_2_complete"}})

    out = run_node(nodes.capability_coaching_node, {
        "stage": STAGE_CORE, "coaching_path": "CH", "active_phase": "1",
        "ch_beats_fired": ["1"],
    })

    assert [b["user_selection"] for b in out["phase_buttons"]] == \
        ["continue_to_phase_3", "save_and_exit"]
    assert [b["label"] for b in out["phase_buttons"]] == ["Continue to Phase 3", "Save & Exit"]
    assert out["ch_beats_fired"] == ["1", "2"]
    assert out["stage"] == STAGE_DYNAMIC_ACTIONS


def test_the_prompt_can_name_its_own_transition_buttons(llm):
    """`transition_options`, when the prompt sends it, WINS over the phase-derived default —
    the button set belongs to the prompt, not to code. And a boundary that arrives without a
    usable `phase` digit still opens the beat: it just can't record which phase it was for."""
    llm(script={STAGE_CH: {"response_to_user": "Ready to move on?",
                           "awaiting_phase_transition": True,
                           "transition_options": ["continue_to_phase_2", "save_and_exit"]}})

    out = run_node(nodes.capability_coaching_node, {
        "stage": STAGE_CORE, "coaching_path": "CH",
    })

    assert [b["user_selection"] for b in out["phase_buttons"]] == \
        ["continue_to_phase_2", "save_and_exit"]
    assert out["handoff_ready"] is True and out["stage"] == STAGE_DYNAMIC_ACTIONS
    assert "ch_beats_fired" not in out, "a beat was recorded for a phase nobody named"


def test_a_normally_completed_phase_is_not_recovered_twice(llm):
    """The safety net must stay dormant for a phase that already fired its beat, or the user
    gets the Phase-1 cards again the moment Phase 2 opens."""
    llm(script={STAGE_CH: _envelope("Welcome to Phase 2.", phase="2")})

    out = run_node(nodes.capability_coaching_node, {
        "stage": STAGE_CORE, "coaching_path": "CH",
        "active_phase": "1", "ch_beats_fired": ["1"],   # phase 1's beat already ran
    })

    assert out["handoff_ready"] is False
    assert out["reply_text"] == "Welcome to Phase 2."
    assert out["active_phase"] == "2"


def test_pressing_continue_clears_the_pending_transition(llm):
    """The flag is what makes a later `save_and_exit` a genuine exit. Left set after the user
    chose to CONTINUE, a resume-after-break signal would close their session."""
    llm(script={STAGE_CH: _envelope("Phase 2: let's map the gaps.", phase="2")})

    out = run_node(nodes.capability_coaching_node, {
        "stage": STAGE_CORE, "coaching_path": "CH", "session_continued": "continue_to_phase_2",
        "active_phase": "2", "ch_beats_fired": ["1"], "ch_awaiting_transition": True,
    })

    assert out["ch_awaiting_transition"] is False
    assert out["handoff_ready"] is False


def test_an_unauthored_path_prompt_falls_back_to_cim_instead_of_no_coaching(llm, monkeypatch,
                                                                            caplog):
    """"Wire now, drop the prompt in later": a path whose workbook cell is empty must still
    coach the user (through CIM), not hand them an empty agent. And the fallback must run
    WITHOUT the agent_complete gate — core's contract leans on handoff_ready alone, so gating
    it there would strand every fallback session at the end of its arc."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.nodes")
    registry = runtime.get_registry()
    monkeypatch.setitem(registry._prompts, STAGE_CH, "")
    client = llm(script={STAGE_CORE: _done("Let's work it through together.")})

    out = run_node(nodes.capability_coaching_node, {"stage": STAGE_CORE, "coaching_path": "CH"})

    assert [c.stage for c in client.calls] == [STAGE_CORE], "the empty CH prompt was still sent"
    assert out["reply_text"] == "Let's work it through together."
    assert out["handoff_ready"] is True and out["stage"] == STAGE_DYNAMIC_ACTIONS
    assert "node.path_prompt_missing_fallback_cim" in {r.message for r in caplog.records}


# ═══════════════════════════════════════════════════════════════════════════════
# the post-coaching layer: actions gate, simulation, pattern, learning aid
# ═══════════════════════════════════════════════════════════════════════════════


def test_the_actions_gate_delivers_cards_then_hands_off_on_the_next_turn(llm, mongo, user_id):
    """A two-shot gate: the first invocation generates + persists the cards and ENDS the turn
    (so the user can act on them); the second, on their next message, is a pure control hop to
    whichever stage the triggering node booked. If the second invocation re-ran the LLM it
    would regenerate cards the user has already been shown."""
    llm()

    first = run_node(nodes.dynamic_actions_insights_node, {
        "user_id": user_id, "stage": STAGE_DYNAMIC_ACTIONS,
        "action_agent_type": STAGE_CORE, "actions_next_stage": STAGE_LEARNING_AID,
    })
    assert first["handoff_ready"] is False       # the turn ends on the cards
    assert first["reply_text"] == builders.ACTION_CARD_TITLE
    assert first["generated_actions"] and first["actions_builder_done"] is True

    second = run_node(nodes.dynamic_actions_insights_node, {
        "user_id": user_id, "stage": STAGE_DYNAMIC_ACTIONS, "actions_builder_done": True,
        "actions_next_stage": STAGE_LEARNING_AID,
    })
    assert second["handoff_ready"] is True and second["stage"] == STAGE_LEARNING_AID
    assert second["reply_text"] == ""            # control-only: it chains, it doesn't speak
    assert second["generated_actions"] == [], "stale cards were re-sent on a later turn"
    assert second["action_agent_type"] == "", "the trigger leaked into the next beat"


def test_the_gate_says_so_when_nobody_told_it_where_to_go(llm, mongo, user_id, caplog):
    """The caller node always books its successor. If it didn't, the gate defaults to the
    closing layer — which is exactly how a learning aid gets skipped after a simulation. It
    still routes (never dead-end a turn), but the rare case has to be greppable."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.nodes")
    llm()

    out = run_node(nodes.dynamic_actions_insights_node, {
        "user_id": user_id, "actions_builder_done": True,
    })

    assert out["stage"] == STAGE_FEEDBACK
    assert "node.dynamic_actions_no_next_stage" in {r.message for r in caplog.records}


@pytest.mark.parametrize("agent_type,expected_log", [
    (STAGE_CORE, "node.dynamic_actions_missing_action_after_coaching"),   # contract breach
    (STAGE_ROLEPLAY, "node.dynamic_actions_skip"),                        # a valid null
])
def test_an_actions_gate_that_produces_nothing_advances_but_grades_the_failure(
    llm, mongo, monkeypatch, user_id, agent_type, expected_log, caplog
):
    """An empty beat must never dead-end the session. But after a COACHING agent the action
    contract mandates >=1 action, so an empty result there is a failure (unauthored prompt,
    model error) — not the benign skip it is after a simulation. Same routing, different
    severity, because only one of them is a bug."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.nodes")
    monkeypatch.setitem(runtime.get_registry()._prompts, builders.ACTIONS_INSIGHTS_AGENT, "")
    llm()

    out = run_node(nodes.dynamic_actions_insights_node, {
        "user_id": user_id, "action_agent_type": agent_type,
        "actions_next_stage": STAGE_PATTERN,
    })

    assert out["handoff_ready"] is True and out["stage"] == STAGE_PATTERN
    assert out["reply_text"] == ""
    assert out["generated_actions"] == []
    assert expected_log in {r.message for r in caplog.records}


def test_the_simulation_gate_routes_the_users_yes_to_the_right_rehearsal(llm):
    """simulation_decision offers the simulation, then routes on the answer. An unroutable /
    disabled target must land on the reflect beat, never on a dead end."""
    for route, expected in [("role_play_agent", STAGE_ROLEPLAY),
                            ("SJT_simulation_agent", STAGE_SJT),
                            ("skip", STAGE_PATTERN),
                            ("something_the_model_invented", STAGE_PATTERN)]:
        llm(script={STAGE_SIMULATION_DECISION: _done("Let's do it.", simulation_route=route)})
        out = run_node(nodes.simulation_decision_node, {"stage": STAGE_SIMULATION_DECISION})
        assert out["handoff_ready"] is True
        assert out["stage"] == expected, f"simulation_route={route!r} routed to {out['stage']}"


def test_a_disabled_simulation_gate_skips_to_the_reflect_beat_without_a_bare_llm_call(
    llm, monkeypatch, caplog
):
    """A node whose prompt sheet is empty must not be run with an empty system prompt — that
    bills tokens and returns garbage the router then acts on."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.nodes")
    monkeypatch.setitem(runtime.get_registry()._prompts, STAGE_SIMULATION_DECISION, "")
    client = llm()

    out = run_node(nodes.simulation_decision_node, {"stage": STAGE_SIMULATION_DECISION})

    assert client.calls == []
    assert out["handoff_ready"] is True and out["stage"] == STAGE_PATTERN
    assert "node.simulation_decision_unavailable_skip" in {r.message for r in caplog.records}


def test_after_coaching_the_session_goes_to_the_simulation_gate_when_it_is_authored(llm):
    """Every coaching path funnels into simulation_decision when it is enabled — that agent,
    not the code, decides whether to offer a rehearsal."""
    llm()
    out = run_node(nodes.core_coaching_node, {"stage": STAGE_CORE, "coaching_path": "CIM"})

    assert out["stage"] == STAGE_DYNAMIC_ACTIONS          # cards first…
    assert out["actions_next_stage"] == STAGE_SIMULATION_DECISION   # …then the gate
    assert out["action_agent_type"] == STAGE_CORE


def test_with_the_simulation_agents_switched_off_the_session_still_reaches_the_close(llm):
    """Catalog toggles are how an agent is switched off with no deploy. Turning simulation off
    must fall through to the support/closing layer, not strand the session on a node that
    won't run."""
    llm()
    registry = runtime.get_registry()
    was = {s: registry.is_enabled(s) for s in (STAGE_SIMULATION_DECISION, STAGE_ROLEPLAY,
                                               STAGE_SJT, STAGE_LEARNING_AID)}
    try:
        # No decision agent → the old deterministic gate: no specific person → SJT.
        registry.set_enabled(STAGE_SIMULATION_DECISION, False)
        out = run_node(nodes.core_coaching_node, {"stage": STAGE_CORE, "coaching_path": "CIM"})
        assert out["actions_next_stage"] == STAGE_SJT

        # …and when the agent the gate picked is off, the OTHER one runs rather than
        # skipping the rehearsal entirely.
        registry.set_enabled(STAGE_SJT, False)
        out = run_node(nodes.core_coaching_node, {"stage": STAGE_CORE, "coaching_path": "CIM"})
        assert out["actions_next_stage"] == STAGE_ROLEPLAY

        registry.set_enabled(STAGE_ROLEPLAY, False)
        out = run_node(nodes.core_coaching_node, {"stage": STAGE_CORE, "coaching_path": "CIM"})
        assert out["actions_next_stage"] == STAGE_LEARNING_AID

        registry.set_enabled(STAGE_LEARNING_AID, False)
        out = run_node(nodes.core_coaching_node, {"stage": STAGE_CORE, "coaching_path": "CIM"})
        assert out["actions_next_stage"] == STAGE_FEEDBACK, "the session dead-ended"
    finally:
        for stage, enabled in was.items():
            registry.set_enabled(stage, enabled)


def test_a_garbled_coaching_path_still_lands_the_session_somewhere_real(llm):
    """A path the router doesn't recognise (a mis-repaired handoff, a prompt typo) must not
    book a simulation for a methodology that doesn't exist — it falls through to the support
    layer, which every path can reach."""
    llm()
    out = run_node(nodes.core_coaching_node, {"stage": STAGE_CORE, "coaching_path": "DBT"})

    assert out["actions_next_stage"] == STAGE_LEARNING_AID


def test_with_the_learning_aid_off_a_skipped_simulation_lands_on_the_closing_layer(llm,
                                                                                   monkeypatch):
    """Two gated agents off at once is the case that dead-ends: the simulation is skipped and
    the support node it would have jumped to isn't running either."""
    registry = runtime.get_registry()
    was = registry.is_enabled(STAGE_LEARNING_AID)
    monkeypatch.setitem(registry._prompts, STAGE_ROLEPLAY, "")
    llm()
    try:
        registry.set_enabled(STAGE_LEARNING_AID, False)
        out = run_node(nodes.role_play_node, {"stage": STAGE_ROLEPLAY})
    finally:
        registry.set_enabled(STAGE_LEARNING_AID, was)

    assert out["handoff_ready"] is True
    assert out["stage"] == STAGE_FEEDBACK


def test_an_unauthored_simulation_prompt_skips_the_beat_instead_of_running_empty(llm,
                                                                                 monkeypatch,
                                                                                 caplog):
    """No prompt → no simulation, and no action/pattern beats hanging off one that never
    happened: go straight to the support layer."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.nodes")
    monkeypatch.setitem(runtime.get_registry()._prompts, STAGE_SJT, "")
    client = llm()

    out = run_node(nodes.sjt_simulation_node, {"stage": STAGE_SJT})

    assert client.calls == []
    assert out["handoff_ready"] is True and out["stage"] == STAGE_LEARNING_AID
    assert "actions_next_stage" not in out, "an action beat was booked for a simulation that never ran"
    assert "node.simulation_prompt_missing_skip" in {r.message for r in caplog.records}


def test_a_completed_simulation_books_the_action_beat_then_the_reflect_beat(llm):
    """Order is the spec: the Action agent runs IMMEDIATELY after the rehearsal, and the
    pattern mirror comes after the capture — not before it."""
    llm(script={STAGE_SJT: _done("That's the last scenario — nicely handled.")})

    out = run_node(nodes.sjt_simulation_node, {
        "stage": STAGE_SJT, "gate_turns": {STAGE_SJT: 3},
    })

    assert out["handoff_ready"] is True
    assert out["stage"] == STAGE_DYNAMIC_ACTIONS
    assert out["actions_next_stage"] == STAGE_PATTERN
    assert out["action_agent_type"] == STAGE_SJT


def test_the_pattern_mirror_is_surfaced_as_its_own_turn_and_recorded(llm, mongo, user_id):
    """The reflect beat is a user-facing message, not telemetry: it must stream like any
    other reply and enter the transcript, or the close-time cumulative scan never sees the
    pattern the user was just shown."""
    llm(script={builders.PATTERN_AGENT: {"context_update": {
        "pattern_mirror_output": "You go quiet exactly when the stakes rise."}}})
    tokens: list[str] = []

    out = run_node(nodes.pattern_node, {"user_id": user_id, "stage": STAGE_PATTERN},
                   on_token=tokens.append)

    assert out["reply_text"] == "You go quiet exactly when the stakes rise."
    assert "".join(tokens) == out["reply_text"], "the mirror never animated on screen"
    assert out["history"] == [{"role": "assistant", "content": out["reply_text"]}]
    assert out["handoff_ready"] is True and out["stage"] == STAGE_LEARNING_AID


def test_a_null_pattern_signal_forwards_silently_instead_of_sending_a_blank_bubble(llm,
                                                                                   mongo,
                                                                                   user_id):
    """No pattern cleared the Potential gate. Inventing one would put a fabricated
    psychological read in front of a user; sending an empty one would be a dead turn. Forward
    with no text so the graph chains straight on."""
    llm(script={builders.PATTERN_AGENT: {"context_update": {"pattern_mirror_output": ""}}})

    out = run_node(nodes.pattern_node, {"user_id": user_id, "stage": STAGE_PATTERN})

    assert out["reply_text"] == ""
    assert "history" not in out
    assert out["handoff_ready"] is True


def test_a_model_that_explodes_under_the_reflect_beat_never_breaks_the_turn(llm, mongo,
                                                                            user_id):
    """The mirror is a nice-to-have on the way to the close. A model failure under it must
    cost the mirror, not the session — the beat forwards with no text and the graph chains on."""
    llm(default=RuntimeError("the pattern agent is on fire"))

    out = run_node(nodes.pattern_node, {"user_id": user_id, "stage": STAGE_PATTERN})

    assert out["reply_text"] == "" and out["handoff_ready"] is True
    assert out["stage"] == STAGE_LEARNING_AID


def test_a_client_that_hangs_up_mid_mirror_still_gets_a_completed_turn(llm, mongo, user_id):
    """The mirror can't stream its own tokens (its raw output is JSON), so the finished text
    is pushed onto the same token channel in one shot. If the client has gone, that push
    raises — and the turn must still complete and be recorded."""
    llm(script={builders.PATTERN_AGENT: {"context_update": {
        "pattern_mirror_output": "You go quiet when the stakes rise."}}})

    def _sink_explodes(_t):
        raise RuntimeError("the client hung up mid-stream")

    out = run_node(nodes.pattern_node, {"user_id": user_id, "stage": STAGE_PATTERN},
                   on_token=_sink_explodes)

    assert out["reply_text"] == "You go quiet when the stakes rise."
    assert out["handoff_ready"] is True


def test_a_reflect_beat_that_blows_its_own_guard_is_still_survivable(llm, mongo, monkeypatch,
                                                                     user_id, caplog):
    """The builder swallows its own failures — but it is a separately-owned unit, and the node
    cannot assume that. If it ever raises (a store error, a serialisation bug in a future
    edit), the session must still reach the closing layer."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.nodes")

    def _mirror_explodes(*a, **kw):
        raise RuntimeError("the mirror builder blew its own guard")

    monkeypatch.setattr(builders, "run_pattern_mirror", _mirror_explodes)
    llm()

    out = run_node(nodes.pattern_node, {"user_id": user_id, "stage": STAGE_PATTERN})

    assert out["reply_text"] == "" and out["handoff_ready"] is True
    assert out["stage"] == STAGE_LEARNING_AID
    assert "node.pattern_mirror_failed" in {r.message for r in caplog.records}


def test_a_ch_early_exit_takes_the_light_close_and_skips_the_learning_aid(llm, mongo, user_id):
    """Save & Exit is a LIGHT close by design — the user asked to leave. Routing them through
    the learning-aid beat on the way out is precisely the "no further processing" the business
    rule forbids."""
    llm(script={builders.PATTERN_AGENT: {"context_update": {"pattern_mirror_output": "x"}}})

    out = run_node(nodes.pattern_node, {"user_id": user_id, "stage": STAGE_PATTERN,
                                        "ch_early_exit": True})

    assert out["stage"] == STAGE_FEEDBACK


def test_an_unauthored_learning_aid_still_books_the_closing_layer(llm, monkeypatch, caplog):
    """A missing prompt skips the aid — but the session must still pass through the actions
    gate and land on the close, never dead-end on a node that cannot run."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.nodes")
    monkeypatch.setitem(runtime.get_registry()._prompts, STAGE_LEARNING_AID, "")
    client = llm()

    out = run_node(nodes.learning_aid_node, {"stage": STAGE_LEARNING_AID})

    assert client.calls == []
    assert out["handoff_ready"] is True and out["stage"] == STAGE_DYNAMIC_ACTIONS
    assert out["actions_next_stage"] == STAGE_FEEDBACK
    assert "node.learning_aid_prompt_missing_skip" in {r.message for r in caplog.records}


# ═══════════════════════════════════════════════════════════════════════════════
# the closing layer: the final action check, feedback, and the terminal close
# ═══════════════════════════════════════════════════════════════════════════════


def _action(session_id, status, text="Ask Dana for 15 minutes"):
    return {"full_text": text, "status": status, "session_id": session_id,
            "action_id": f"a-{status}", "created_at": datetime.now(timezone.utc).isoformat()}


def test_the_final_action_check_waves_through_a_user_who_saved_something(graph, agentic_coll,
                                                                         user_id, llm, mongo,
                                                                         inline_builders,
                                                                         caplog):
    """It is a gate, not a toll booth: a user who saved an action must reach the closing
    layer with no interruption, and the pass must chain into feedback within the SAME turn."""
    caplog.set_level(logging.INFO, logger="cerebrozen.nodes")
    session_id = "s-saved"
    agentic_coll.insert_one({"user_id": user_id, "actions": [_action(session_id, "saved")]})
    llm(script={STAGE_FEEDBACK: _envelope("How are you feeling about the session?")})

    final = run_graph(graph, user_id=user_id, session_id=session_id, stage=STAGE_FEEDBACK,
                      coaching_path="CIM")

    assert final["active_node"] == STAGE_FEEDBACK, "the check intercepted a user who had saved"
    assert final["reply_text"] == "How are you feeling about the session?"
    assert final["final_action_check_done"] is True
    assert final["generated_actions"] == []
    assert "node.final_action_check_pass" in {r.message for r in caplog.records}


def test_a_user_who_skipped_every_action_is_nudged_with_the_cards_they_already_have(
    graph, agentic_coll, user_id, llm, mongo, inline_builders
):
    """The "all actions skipped" edge case. The gate BLOCKS the close and re-surfaces the
    session's existing cards — reused, never regenerated (regenerating them here is how the
    user ends up with a second, different set of actions at the door)."""
    session_id = "s-nudge"
    agentic_coll.insert_one({"user_id": user_id, "actions": [
        _action(session_id, "active", "Ask Dana for 15 minutes"),
        _action(session_id, "deleted", "A card they threw away"),
    ]})
    client = llm()

    final = run_graph(graph, user_id=user_id, session_id=session_id, stage=STAGE_FEEDBACK,
                      coaching_path="CIM")

    assert final["reply_text"] == nodes.FINAL_ACTION_CHECK_NUDGE
    assert final["handoff_ready"] is False, "the session closed with zero saved actions"
    assert final["stage"] == STAGE_FINAL_ACTION_CHECK
    assert [c["full_text"] for c in final["generated_actions"]] == ["Ask Dana for 15 minutes"]
    assert client.calls == [], "the gate regenerated cards instead of re-surfacing them"
    assert final["gate_turns"][STAGE_FINAL_ACTION_CHECK] == 1


def test_the_final_action_check_nudges_then_relents_so_it_cannot_trap_a_session(
    agentic_coll, user_id, llm, mongo, watchdog, caplog
):
    """This gate is a NON-LLM node: it never passes through _run_stage, so it inherits
    neither the completion ceiling nor the stage watchdog. Unbounded, a user who simply does
    not want an action gets the same canned nudge forever, the closing layer is never reached
    and their mood/feedback is never captured — a worse outcome than closing with zero
    actions. So: nudge, then relent."""
    caplog.set_level(logging.ERROR, logger="cerebrozen.nodes")
    session_id = "s-bound"
    agentic_coll.insert_one({"user_id": user_id, "actions": [_action(session_id, "active")]})
    llm()

    out = run_node(nodes.final_action_check_node, {
        "user_id": user_id, "session_id": session_id, "stage": STAGE_FINAL_ACTION_CHECK,
        "gate_turns": {STAGE_FINAL_ACTION_CHECK: nodes._FINAL_ACTION_CHECK_MAX_NUDGES},
    })

    assert out["handoff_ready"] is True, "the gate can trap a session forever"
    assert out["stage"] == STAGE_FEEDBACK
    assert out["final_action_check_done"] is True
    assert out["generated_actions"] == []
    assert watchdog == [STAGE_FINAL_ACTION_CHECK]
    assert "node.final_action_check_watchdog_pass" in {r.message for r in caplog.records}


def test_with_nothing_to_pick_from_the_check_never_hangs_the_user(user_id, llm, mongo):
    """No cards were ever generated (the action agent failed, or the flow produced none).
    Nudging someone to "pick one" when there is nothing to pick is an infinite loop."""
    llm()
    out = run_node(nodes.final_action_check_node,
                   {"user_id": user_id, "session_id": "s-empty"})

    assert out["handoff_ready"] is True and out["stage"] == STAGE_FEEDBACK
    assert out["reply_text"] == ""


def test_an_unreadable_action_store_lets_the_user_close_rather_than_blocking_them(
    monkeypatch, user_id, llm, mongo, caplog
):
    """The gate reads the store twice (saved count + cards). A store hiccup must not block a
    user at the door of the closing layer — it degrades to "nothing to pick", and passes."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.nodes")
    from app.stores import agentic

    def _boom(_user_id):
        raise RuntimeError("mongo is unreachable")

    monkeypatch.setattr(agentic, "load", _boom)
    llm()

    out = run_node(nodes.final_action_check_node,
                   {"user_id": user_id, "session_id": "s-dead-store"})

    assert out["handoff_ready"] is True and out["stage"] == STAGE_FEEDBACK
    assert "node.final_action_check_read_failed" in {r.message for r in caplog.records}


def test_the_closing_agent_captures_the_mood_and_pairs_it_with_the_question_asked(
    agentic_coll, user_id, llm, mongo
):
    """Mood capture is the product of the closing ritual. The stored record must pair the
    EXPLORATION question the bot asked (step M2) with the answer the user gave — an answer
    with no question is uninterpretable months later, and the question scrolls out of the
    history window long before the arc completes."""
    # The M2 step carries its exploration question in the `question` field of the envelope.
    llm(script={STAGE_FEEDBACK: {"question": "Which of those feels strongest right now?",
                                 "current_step": "M2", "handoff_ready": False,
                                 "context_update": {"mood_capture": {
                                     "positive_emotions": ["relief"]}}}})

    m2 = run_node(nodes.feedback_node, {"user_id": user_id, "coaching_path": "CIM"})
    assert m2["feedback_exp_questions"] == {"pos_q": "Which of those feels strongest right now?"}

    llm(script={STAGE_FEEDBACK: _done("Thank you — take care.", context_update={
        "mood_capture": {"raw_user_response": "Relieved, mostly.",
                         "mapped_emotions": ["relief"]}})})
    done = run_node(nodes.feedback_node, {
        "user_id": user_id, "session_id": "s-mood", "coaching_path": "CIM",
        "feedback_exp_questions": m2["feedback_exp_questions"],
        # The closing ritual is 8 steps and carries its own completion floor (Round-1 bug #5:
        # it self-certified `agent_complete` after four of them), so a completion is only
        # honoured once the arc has actually been walked.
        "gate_turns": {STAGE_FEEDBACK: nodes._COMPLETION_FLOOR_TURNS[STAGE_FEEDBACK] - 1},
    })

    assert done["handoff_ready"] is True and done["stage"] == STAGE_CLOSE
    assert "mood_capture_data" not in done, "an internal payload leaked into graph state"
    mood = agentic_coll.find_one({"user_id": user_id})["moods"][0]
    assert mood["session_id"] == "s-mood"
    assert mood["responses"][0]["responses"] == [
        {"question": "Which of those feels strongest right now?", "answer": "Relieved, mostly."}
    ]


def test_the_closing_agent_dropping_its_json_contract_does_not_break_the_close(llm, mongo,
                                                                               user_id):
    """The mood/feedback parsing reaches straight into the raw envelope. A prompt that answers
    in plain prose (they do) has no envelope at all — that must be a turn with no captured
    mood, not an exception at the last step of the session."""
    llm(script={STAGE_FEEDBACK: "So — how are you feeling about all that?"})

    out = run_node(nodes.feedback_node, {"user_id": user_id, "coaching_path": "CIM"})

    assert out["reply_text"] == "So — how are you feeling about all that?"
    assert "feedback_exp_questions" not in out


def test_a_completion_with_nothing_to_record_saves_nothing(llm, agentic_coll, mongo, user_id):
    """The closing agent completes but its mood block is empty (it never got an answer). An
    empty mood record is worse than none: it looks like a captured mood in every downstream
    report and dashboard."""
    llm(script={STAGE_FEEDBACK: _done("Thanks — take care.",
                                      context_update={"mood_capture": {}})})

    out = run_node(nodes.feedback_node, {
        "user_id": user_id, "session_id": "s-empty-mood", "coaching_path": "CIM",
        "gate_turns": {STAGE_FEEDBACK: nodes._COMPLETION_FLOOR_TURNS[STAGE_FEEDBACK] - 1},
    })

    assert out["handoff_ready"] is True and out["stage"] == STAGE_CLOSE
    assert (agentic_coll.find_one({"user_id": user_id}) or {}).get("moods") is None


@pytest.mark.parametrize("emotions,expected_key", [
    ({"negative_emotions": ["frustration"]}, "neg_q"),
    ({}, "pos_q"),      # no emotion read yet → recorded, not dropped
])
def test_the_exploration_question_is_filed_under_the_valence_it_explored(llm, mongo, user_id,
                                                                         emotions, expected_key):
    """One M2 question, two possible valences. File it under the wrong one (or drop it when
    the emotion read hasn't landed yet) and the saved mood record has an answer with no
    question."""
    llm(script={STAGE_FEEDBACK: {"question": "Say more about that feeling?",
                                 "current_step": "M2", "handoff_ready": False,
                                 "context_update": {"mood_capture": emotions}}})

    out = run_node(nodes.feedback_node, {"user_id": user_id, "coaching_path": "CIM"})

    assert out["feedback_exp_questions"] == {expected_key: "Say more about that feeling?"}


def test_a_missing_closing_prompt_holds_the_session_open_rather_than_ending_it(llm,
                                                                               monkeypatch,
                                                                               caplog):
    """The closing agent is the SOLE legitimate path to a terminal close. If its prompt is
    unauthored we must not silently end the session (mood + feedback would never be captured
    and the user's session would just… stop). Hold the session open, loudly."""
    caplog.set_level(logging.ERROR, logger="cerebrozen.nodes")
    monkeypatch.setitem(runtime.get_registry()._prompts, STAGE_FEEDBACK, "")
    client = llm()

    out = run_node(nodes.feedback_node, {"stage": STAGE_FEEDBACK})

    assert client.calls == []
    assert out["reply_text"] == nodes.FEEDBACK_UNAVAILABLE_REPLY
    assert out["handoff_ready"] is False
    assert out["stage"] == STAGE_FEEDBACK, "the session closed with no closing agent"
    assert "node.feedback_prompt_missing_no_close" in {r.message for r in caplog.records}


def test_a_message_after_the_session_closed_does_not_reopen_coaching(graph, llm, user_id):
    """`close` is terminal. A late "thanks!" must get the closing note — resuming coaching
    from a finished session is how a user ends up in a stage whose context is gone."""
    llm()
    final = run_graph(graph, user_id=user_id, stage=STAGE_CLOSE, user_message="thanks!")

    assert final["reply_text"] == nodes.SESSION_COMPLETE_REPLY
    assert final["active_node"] == "session_complete"
    assert final["stage"] == STAGE_CLOSE


# ═══════════════════════════════════════════════════════════════════════════════
# the standalone action check-in (its own entry point, outside the coaching arc)
# ═══════════════════════════════════════════════════════════════════════════════


def test_the_tapped_action_is_what_the_checkin_agent_talks_about(llm, mongo, user_id):
    """The user tapped "Action Check-In" on ONE card. Its text has to reach the prompt's
    {action_item}/{action_outcome} placeholders every turn, or the agent runs a 15-step
    reflection on an action it cannot name."""
    client = llm(script={STAGE_ACTION_CHECKIN: _envelope("How did it go with Dana?")})

    out = run_node(nodes.action_checkin_node, {
        "user_id": user_id, "stage": STAGE_ACTION_CHECKIN,
        "checkin_action": {"action_item": "Ask Dana for 15 minutes",
                           "action_outcome": "The disagreement is aired"},
    })

    prompt = client.calls_for(STAGE_ACTION_CHECKIN)[0].system_prompt
    assert "Ask Dana for 15 minutes" in prompt and "The disagreement is aired" in prompt
    assert out["reply_text"] == "How did it go with Dana?"

    # A card with no expected outcome (older cards have none) must still name the action —
    # merging only the fields that exist, never blanking one the card didn't carry.
    client2 = llm(script={STAGE_ACTION_CHECKIN: _envelope("How did it go?")})
    run_node(nodes.action_checkin_node, {
        "user_id": user_id, "stage": STAGE_ACTION_CHECKIN,
        "checkin_action": {"action_outcome": "The disagreement is aired"},
    })
    assert "The disagreement is aired" in client2.calls_for(STAGE_ACTION_CHECKIN)[0].system_prompt


def test_the_checkin_closes_straight_out_never_through_the_coaching_close(llm, mongo, user_id):
    """It is a self-contained mini-session: on completion it goes to `close`, bypassing the
    feedback ritual and the final-action check (which belong to a coaching session)."""
    llm(script={STAGE_ACTION_CHECKIN: _done("Good work — that's the loop closed.")})

    out = run_node(nodes.action_checkin_node, {
        "user_id": user_id, "stage": STAGE_ACTION_CHECKIN, "checkin_action": {},
    })

    assert out["handoff_ready"] is True and out["stage"] == STAGE_CLOSE


def test_a_disabled_checkin_agent_says_so_instead_of_running_a_bare_llm(llm, caplog):
    """Activation is the Catalog flag AND an authored prompt. With either off, the user who
    tapped the card must get an honest message — not an agent with an empty system prompt."""
    caplog.set_level(logging.ERROR, logger="cerebrozen.nodes")
    registry = runtime.get_registry()
    was = registry.is_enabled(STAGE_ACTION_CHECKIN)
    client = llm()
    try:
        registry.set_enabled(STAGE_ACTION_CHECKIN, False)
        out = run_node(nodes.action_checkin_node, {"stage": STAGE_ACTION_CHECKIN})
    finally:
        registry.set_enabled(STAGE_ACTION_CHECKIN, was)

    assert client.calls == []
    assert "isn't available right now" in out["reply_text"]
    assert out["handoff_ready"] is True and out["stage"] == STAGE_CLOSE
    assert "node.action_checkin_unavailable" in {r.message for r in caplog.records}


# ═══════════════════════════════════════════════════════════════════════════════
# profile_read + the entry routing it decides
# ═══════════════════════════════════════════════════════════════════════════════


def test_a_returning_user_with_a_history_is_not_dragged_back_through_intake(
    engine, agentic_coll, user_id, llm, mongo, inline_builders
):
    """Round-1 bug #2: a repeat user re-entered intake, which surfaced the "coaching
    readiness Snapshot" greeting and then self-skipped the questions — leaving a dangling
    intro with no questions. A returning user goes exactly where a completed intake would
    have sent them: the check-in when one is DUE, otherwise straight to challenge."""
    old = (datetime.now(timezone.utc) - timedelta(days=30)).isoformat()
    agentic_coll.insert_one({
        "user_id": user_id, "sessions_completed": 2,
        "intake_vars": {"userRoleContext": "EM, 8 reports"},
        "actions": [{"full_text": "Ask Dana for 15 minutes", "status": "saved",
                     "session_id": "s-old", "ts": old}],
    })
    llm(script={STAGE_CHECKIN: _envelope("Last time you were going to talk to Dana — did you?")})

    result = engine.run_turn_stream(user_id=user_id, session_id=f"s-{uuid.uuid4().hex[:8]}",
                                    bot_name="CereBroZen", user_message="hi again")

    assert result["active_node"] == STAGE_CHECKIN, "a returning user was re-onboarded"
    assert result["response_to_user"].startswith("Last time")

    # Inside the 7-day window nothing is due, so there is NO pre-session touch by design:
    # the same returning user goes straight to challenge — still never back through intake.
    agentic_coll.update_one({"user_id": user_id}, {"$set": {"actions.0.ts": datetime.now(
        timezone.utc).isoformat()}})
    fresh_session = engine.run_turn_stream(user_id=user_id,
                                           session_id=f"s-{uuid.uuid4().hex[:8]}",
                                           bot_name="CereBroZen", user_message="hi again")
    assert fresh_session["active_node"] == STAGE_CHALLENGE


def test_a_completed_checkin_permanently_closes_the_actions_it_asked_about(
    engine, agentic_coll, user_id, llm, mongo, inline_builders
):
    """BRD R3: an action that has been checked in never comes back for check-in. The engine
    marks the batch closed the moment the check-in agent hands off — miss it and the user is
    asked about the same action every session, forever."""
    old = (datetime.now(timezone.utc) - timedelta(days=30)).isoformat()
    agentic_coll.insert_one({
        "user_id": user_id, "sessions_completed": 1,
        "intake_vars": {"userRoleContext": "EM"},
        "actions": [{"full_text": "Ask Dana for 15 minutes", "status": "saved",
                     "session_id": "s-old", "ts": old}],
    })
    llm(script={STAGE_CHECKIN: _done("Great — let's move on to today.")})

    result = engine.run_turn_stream(user_id=user_id, session_id=f"s-{uuid.uuid4().hex[:8]}",
                                    bot_name="CereBroZen", user_message="I did it")

    assert result["stage"] == STAGE_CHALLENGE, "check-in handed back to intake"
    assert agentic_coll.find_one({"user_id": user_id})["checkin_complete_sessions"] == ["s-old"]


def test_the_session_language_reaches_the_prompt_on_the_first_turn_and_when_it_changes(llm):
    """The request's language is the only place we learn it (retrieval cannot know it), and
    the environment prompt has a {language} token. It must land on turn one AND be re-synced
    when a continuing turn arrives with a different one."""
    llm()
    first = run_node(nodes.profile_read_node, {"is_first_turn": True, "user_language": "es"})
    assert first["user_context"]["language"] == "es"

    changed = run_node(nodes.profile_read_node, {
        "is_first_turn": False, "user_language": "fr",
        "user_context": {"language": "es", "conversation_mode": "text"},
    })
    assert changed["user_context"]["language"] == "fr"


def test_a_mid_session_switch_to_voice_re_syncs_every_turn(llm):
    """One session_id spans typing and talking (voice-flow-architecture.md). The
    {conversation_mode} token drives response style (short, markdown-free for TTS), so a
    stale "text" on a voice turn is read aloud as markdown."""
    llm()
    out = run_node(nodes.profile_read_node, {
        "is_first_turn": False, "conversation_mode": "voice",
        "user_context": {"conversation_mode": "text"}, "stage": STAGE_CORE,
    })
    assert out["user_context"]["conversation_mode"] == "voice"

    # A turn that changes nothing must not rewrite user_context at all.
    unchanged = run_node(nodes.profile_read_node, {
        "is_first_turn": False, "conversation_mode": "text", "stage": STAGE_CORE,
        "user_context": {"conversation_mode": "text", "language": "", "session_continued": ""},
    })
    assert "user_context" not in unchanged


def test_a_ch_phase_button_press_reaches_the_prompt_on_the_turn_it_is_pressed(llm):
    """{session_continued} is how the CH prompt learns which transition the user chose. It is
    per-turn metadata — if it isn't propagated into the checkpointed context, the model never
    sees the button press and re-offers the same choice."""
    llm()
    out = run_node(nodes.profile_read_node, {
        "is_first_turn": False, "session_continued": "continue_to_phase_2",
        "user_context": {"session_continued": ""}, "stage": STAGE_CORE,
    })
    assert out["user_context"]["session_continued"] == "continue_to_phase_2"


def test_a_users_broken_timezone_never_kills_their_turn(mongo, llm, caplog):
    """{Time} is pinned at session start from the user's profile timezone. A junk value there
    (a hand-edited profile, a migrated field) would raise inside profile_read — which runs on
    EVERY turn, before anything else, so it would kill the turn at the door."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.nodes")
    from bson import ObjectId

    oid_user = "5f2b1c8e9d3a4b5c6d7e8f90"
    mongo[_config.MONGO_BACKEND_DB][_config.MONGO_USERS_COLLECTION].insert_one(
        {"_id": ObjectId(oid_user), "localTimeZone": "Middle/Earth"}
    )
    llm()

    out = run_node(nodes.profile_read_node, {"user_id": oid_user, "is_first_turn": True})

    assert out["user_context"]["Time"], "the turn died on a bad timezone"
    assert datetime.fromisoformat(out["user_context"]["Time"]).utcoffset() == timedelta(0)
    invalid = [r for r in caplog.records if r.message == "profile_read.invalid_timezone"]
    assert invalid and invalid[0].fallback == "UTC"


# ═══════════════════════════════════════════════════════════════════════════════
# crisis: the reply must be in a language the person can read
# ═══════════════════════════════════════════════════════════════════════════════


def test_a_crisis_in_another_language_is_answered_in_that_language(graph, llm, user_id):
    """Detecting a crisis in Spanish and replying in English is half a fix: it proves the
    screen fired and still leaves someone reading a language they may not speak, at the worst
    possible moment. With no declared session language, the lexicon that MATCHED is the one
    we answer in."""
    from app.graph.crisis import safe_response

    llm()
    final = run_graph(graph, user_id=user_id, user_message="quiero suicidarme")

    assert final["safety_flag"] == "crisis"
    assert final["active_node"] == "safe_response"
    assert final["reply_text"] == safe_response("es")
    assert final["reply_text"] != safe_response("en")


def test_the_declared_session_language_outranks_the_lexicon_that_matched(graph, llm, user_id):
    """A French speaker who writes one crisis line in English must still get the French
    reply — the session's declared language is authoritative and survives a code-switch."""
    from app.graph.crisis import safe_response

    llm()
    final = run_graph(graph, user_id=user_id, user_message="I want to kill myself",
                      user_language="fr")

    assert final["safety_flag"] == "crisis"
    assert final["reply_text"] == safe_response("fr")


# ═══════════════════════════════════════════════════════════════════════════════
# telemetry callbacks may never break a turn
# ═══════════════════════════════════════════════════════════════════════════════


def test_a_client_that_hangs_up_mid_status_does_not_kill_the_turn(llm, mongo):
    """on_status / on_node are the SSE side-channels to a browser that may have closed. They
    are best-effort by contract — a raise from either would take the coaching turn with it.
    Both the "which agent is running" announcement and the one-off step-completion notice
    (profile_read's "User Profile Retrieval done") go out on that channel."""
    llm(script={STAGE_INTAKE: _envelope("What's the hardest part?")})

    def _dead(_x):
        raise RuntimeError("the client disconnected")

    out = run_node(nodes.intake_node, on_status=_dead, on_node=_dead)
    assert out["reply_text"] == "What's the hardest part?"

    profile = run_node(nodes.profile_read_node, {"is_first_turn": True},
                       on_status=_dead, on_node=_dead)
    assert profile["user_context"], "a dead status channel cost the turn its profile"
    assert profile["stage"] == STAGE_INTAKE


def test_the_flow_view_survives_a_callback_that_explodes(engine, user_id, llm,
                                                         inline_builders):
    """The engine's per-node `end` events feed the flow view. Same contract: telemetry never
    breaks a turn."""
    llm()

    def _dead(_event):
        raise RuntimeError("the flow view is gone")

    result = engine.run_turn_stream(user_id=user_id, session_id=f"s-{uuid.uuid4().hex[:8]}",
                                    bot_name="CereBroZen", user_message="hello", on_node=_dead)

    assert result["response_to_user"].strip()


def test_a_dead_redis_marker_never_breaks_the_turn(engine, user_id, llm, monkeypatch,
                                                   inline_builders):
    """The "seen" marker is a latency cache written after every turn. Redis being down must
    cost a get_state probe on the next turn — not the turn the user is in."""
    import app.stores.redis_state as redis_state

    llm()

    def _down(_session_id):
        raise ConnectionError("redis is unreachable")

    monkeypatch.setattr(redis_state, "mark_session_seen", _down)
    result = engine.run_turn_stream(user_id=user_id, session_id=f"s-{uuid.uuid4().hex[:8]}",
                                    bot_name="CereBroZen", user_message="hello")

    assert result["response_to_user"].strip()


# ═══════════════════════════════════════════════════════════════════════════════
# the test-only escape hatches (they must stay OFF in production)
# ═══════════════════════════════════════════════════════════════════════════════


def test_the_stubbed_challenge_is_a_code_router_not_an_llm_call(llm, monkeypatch):
    """CEREBROZEN_STUB_CHALLENGE is the Phase-1 CIM-only switch. Stubbed, it must not call the
    model at all — a wasted ~9s call and a confusing mid-turn "challenge invoked" was the
    whole reason it exists."""
    monkeypatch.setattr(nodes, "STUB_CHALLENGE", True)
    client = llm()

    out = run_node(nodes.challenge_node, {"stage": STAGE_CHALLENGE})

    assert client.calls == []
    assert out["coaching_path"] == "CIM"
    assert out["handoff_ready"] is True
    assert out["reply_text"] == ""          # chains straight into CIM this turn
    assert out["stage"] == STAGE_CORE


def test_the_force_handoff_switch_advances_a_stage_the_prompt_did_not_finish(llm, monkeypatch):
    """The smoke-test override that lets a live run reach simulation/close without playing the
    full arc. It is a no-op in prod (set empty) — but if it ever leaked in, it would advance
    every stage on its first turn, so its behaviour is pinned here."""
    monkeypatch.setattr(nodes, "FORCE_HANDOFF_STAGES", {"__all__"})
    llm(script={STAGE_INTAKE: _envelope("Tell me more.")})

    out = run_node(nodes.intake_node)

    assert out["handoff_ready"] is True
    assert out["stage"] == STAGE_CHALLENGE
    assert not _config.FORCE_HANDOFF_STAGES, "the force-handoff override is ON in this config"


# ═══════════════════════════════════════════════════════════════════════════════
# build_graph: the checkpointer chain picks the durable store it was configured for
# ═══════════════════════════════════════════════════════════════════════════════


def test_postgres_is_used_when_it_is_configured(monkeypatch, caplog):
    """The Postgres branch (the backend for clients who don't run Mongo). The DRIVER is
    faked; the selection logic is real — including two details that were each a live bug: a
    connection POOL (from_conn_string yields a connection that is closed the moment the
    generator is collected, so the saver dies on its first query) and autocommit (the saver
    issues DDL outside a transaction)."""
    import langgraph.checkpoint.postgres as pg_mod
    import psycopg_pool

    caplog.set_level(logging.INFO, logger="cerebrozen.graph")
    made = {}

    class _Pool:
        def __init__(self, conninfo, max_size, kwargs, open):
            made.update(conninfo=conninfo, max_size=max_size, kwargs=kwargs, open=open)
            self.max_size = max_size

    class _Saver:
        def __init__(self, pool):
            self.pool = pool

        def setup(self):
            made["setup"] = True

    monkeypatch.setattr(psycopg_pool, "ConnectionPool", _Pool)
    monkeypatch.setattr(pg_mod, "PostgresSaver", _Saver)
    monkeypatch.setenv("POSTGRES_URL", "postgresql://coach:pw@pg.internal:5432/cerebrozen")

    saver = bg.get_checkpointer()

    assert isinstance(saver, _Saver)
    assert made["conninfo"] == "postgresql://coach:pw@pg.internal:5432/cerebrozen"
    assert made["kwargs"]["autocommit"] is True and made["open"] is True
    assert made["setup"] is True, "the checkpoint tables were never created"
    assert "checkpointer.postgres" in {r.message for r in caplog.records}


def test_mongo_is_used_when_it_is_configured_and_reachable(monkeypatch, caplog):
    """The default durable store. It is only chosen after a real `ping` — a configured but
    unreachable Mongo must fall through (that path is already covered); this is the half that
    proves a REACHABLE one is actually used, and with the checkpoint DB it was given."""
    import langgraph.checkpoint.mongodb as mongo_mod
    import pymongo

    caplog.set_level(logging.INFO, logger="cerebrozen.graph")
    made = {}

    class _Client:
        def __init__(self, url, serverSelectionTimeoutMS=None):
            made.update(url=url, timeout=serverSelectionTimeoutMS)
            self.admin = SimpleNamespace(command=lambda cmd: made.update(pinged=cmd))

    class _Saver:
        def __init__(self, client, db_name):
            made.update(db_name=db_name)

    monkeypatch.delenv("POSTGRES_URL", raising=False)
    monkeypatch.setattr(_config, "POSTGRES_URL", "", raising=False)
    monkeypatch.setattr(_config, "MONGO_DB_URL", "mongodb://mongo.internal:27017")
    monkeypatch.setattr(pymongo, "MongoClient", _Client)
    monkeypatch.setattr(mongo_mod, "MongoDBSaver", _Saver)

    saver = bg.get_checkpointer()

    assert isinstance(saver, _Saver)
    assert made["pinged"] == "ping", "a Mongo was accepted without ever being reached"
    assert made["db_name"] == _config.MONGO_CHECKPOINT_DB
    assert "checkpointer.mongo" in {r.message for r in caplog.records}
