"""The graph control plane: the parser the router reads, the guardrail wrapper, the
stage table, the checkpointer chain, and the off-path builders.

Everything below the LLM is real: the real prompt workbook, the real placeholder
resolver, the real agentic store (mongomock), the real compiled LangGraph, the real
SQLite checkpointer. The ONLY substituted boundary is the model call itself — because
every failure this file is about is a failure of OUR code to survive what a model (or a
database) does to it.

Three properties are load-bearing here, and each one has already cost a production
incident somewhere in a system shaped like this:

  1. parse_control is the router's ONLY sense organ. If it mis-reads a control flag the
     session advances a stage the user never finished; if it fails to read an envelope
     the user gets raw JSON in their chat bubble.
  2. A literal "{userName}" must never reach a user. Guardrails blanks what it cannot
     resolve — including when resolution itself explodes.
  3. A background builder must never be able to break a turn. It runs AFTER the reply
     has streamed; if it throws, the user must never know.
"""

import asyncio
import importlib
import json
import logging
import uuid
from concurrent.futures import Future
from types import SimpleNamespace

import pytest

import app.graph.build_graph as bg
import app.graph.builders as builders
import app.graph.guardrails as guardrails
import app.graph.runtime as runtime
import app.graph.state as gstate
import app.graph.tools as tools

# Imported EAGERLY, and it matters: `app.stores.dynamic_vars` does
# `from app.stores.mongo import get_client` at module level, and the only thing that
# imports it is `builders.dispatch_dynamic_vars` — lazily, inside the function, i.e. while
# the `mongo` fixture has already monkeypatched `mongo.get_client`. First import wins, so
# the module would bind THAT test's mongomock forever and every later test would assert
# against a store nothing writes to. Import it before any fixture patches anything.
import app.stores.dynamic_vars  # noqa: F401
from app import config as _config
from app.llm.responses_client import LLMResponse
from app.rag.placeholders import PLACEHOLDER_RE

# ═══════════════════════════════════════════════════════════════════════════════
# The one mocked boundary: the model call.
# ═══════════════════════════════════════════════════════════════════════════════


class _ScriptedLLM:
    """A deterministic stand-in for the LLM provider — the ONLY thing faked in this file.

    Per-stage scripting (`script`) with a `default` for everything else. A script value
    may be a dict (serialised to JSON), a raw string (to hand the parser malformed or
    truncated output verbatim), or an Exception instance (to make the model call blow up,
    which is how the builder-failure tests get a real exception out of a real call site).

    Records every call so a test can assert on the system prompt the graph actually
    composed — that is how we prove the guardrails wrapped a builder's prompt, without
    reaching inside the builder.
    """

    def __init__(self, script=None, default=None):
        self.script = script or {}
        self.default = default
        self.calls: list[SimpleNamespace] = []

    def stages(self) -> list[str]:
        return [c.stage for c in self.calls]

    def call_for(self, stage: str) -> SimpleNamespace:
        for c in self.calls:
            if c.stage == stage:
                return c
        raise AssertionError(f"the model was never called for {stage!r}; got {self.stages()}")

    def _text(self, stage: str) -> str:
        out = self.script.get(stage, self.default)
        if isinstance(out, Exception):
            raise out
        if isinstance(out, str):
            return out
        return json.dumps(out, ensure_ascii=False)

    def _run(self, stage, system_prompt, user_prompt, on_token=None) -> LLMResponse:
        self.calls.append(
            SimpleNamespace(stage=stage, system_prompt=system_prompt, user_prompt=user_prompt)
        )
        text = self._text(stage)  # may raise — deliberately, after the call is recorded
        if on_token:
            on_token(text)
        return LLMResponse(
            text=text, prompt_tokens=11, completion_tokens=7, total_tokens=18,
            model_latency_ms=1.0, cached_tokens=0, cost_usd=0.002, model="scripted",
        )

    def generate(self, system_prompt, user_prompt, model, reasoning_effort=None,
                 history=None, stage="", session_id="", user_id="") -> LLMResponse:
        return self._run(stage, system_prompt, user_prompt)

    def generate_stream(self, system_prompt, user_prompt, model, on_token=None,
                        reasoning_effort=None, history=None, stage="", session_id="",
                        user_id="", json_output=False) -> LLMResponse:
        return self._run(stage, system_prompt, user_prompt, on_token=on_token)


# A complete, well-formed envelope: a reply, a clean handoff, a routable path, captured
# variables, and one action + one insight. Every stage that isn't scripted gets this, so
# a session walks the whole arc one stage per turn.
_GOOD_ENVELOPE = {
    "response_to_user": "What would make this feel different by Friday?",
    "handoff_ready": True,
    "agent_complete": True,
    "coaching_path": "CIM",
    "context_update": {"coaching_path": "CIM", "current_step": "q1"},
    "variables_set": {"coachingHistory": "two years with an external coach"},
    "actions": [
        {
            "full_text": "Ask Dana for 15 minutes before Thursday's review.",
            "roi_metric": "Influence",
            "expected_outcome": "The disagreement is aired before the room sees it.",
            "confidence": 0.8,
        }
    ],
    "insights": [{"insight_title": "Avoidance costs more than the conflict",
                  "insight_body": "You pay for the delay twice."}],
    "simulation_route": "skip",
}

# pattern_agent answers BOTH of its invocations (in_session mirror + background_write) —
# the graph calls the same sheet twice per session with different invocation_modes.
_PATTERN_ENVELOPE = {
    "context_update": {
        "pattern_mirror_output": "You go quiet exactly when the stakes rise.",
        "pattern_cluster_surfaced": "conflict_avoidance",
        "pattern_facet_surfaced": "withdrawal",
    },
    "ic_profile": {"clusters": [{"name": "conflict_avoidance", "confidence": 0.7}]},
    "handoff_ready": True,
    "agent_complete": True,
    "response_to_user": "You go quiet exactly when the stakes rise.",
}

_SESSION_SCRIPT = {builders.PATTERN_AGENT: _PATTERN_ENVELOPE}


@pytest.fixture
def llm(monkeypatch):
    """Install a scripted model. `runtime.get_provider` is the single seam every node
    and every builder resolves its client through, so one patch reaches all of them."""

    def _install(script=None, default=None, prewarm=None):
        client = _ScriptedLLM(script, _GOOD_ENVELOPE if default is None else default)
        if prewarm is not None:
            client.prewarm = prewarm  # only providers that HAVE one get prewarmed
        monkeypatch.setattr(runtime, "get_provider", lambda: client)
        return client

    return _install


class _InlineExecutor:
    """Runs 'background' work on the calling thread.

    This substitutes the SCHEDULER, not the builders: every builder function still runs
    for real, against the real store, and the tests assert on what it actually persisted.
    A real ThreadPoolExecutor would force the test to either sleep (flaky) or assert
    nothing. Exceptions are captured into the Future exactly as ThreadPoolExecutor does,
    so an exploding builder still cannot surface in the caller — which is the property
    under test, and it would be worthless if the executor let it through.
    """

    def __init__(self, fail_on_submit: bool = False):
        self.submitted: list[str] = []
        self.fail_on_submit = fail_on_submit

    def submit(self, fn, *args, **kwargs):
        if self.fail_on_submit:
            # Exactly what a shut-down / saturated pool raises in production.
            raise RuntimeError("cannot schedule new futures after shutdown")
        self.submitted.append(getattr(fn, "__name__", str(fn)))
        fut: Future = Future()
        try:
            fut.set_result(fn(*args, **kwargs))
        except BaseException as exc:  # noqa: BLE001 — mirror ThreadPoolExecutor exactly
            fut.set_exception(exc)
        return fut


@pytest.fixture
def inline_builders(monkeypatch):
    """Make the off-path builders run inline (deterministically) — still off-path in
    the sense that matters: their failures are still captured in a Future, never raised."""
    ex = _InlineExecutor()
    monkeypatch.setattr(builders, "_EXECUTOR", ex)
    return ex


@pytest.fixture
def dead_executor(monkeypatch):
    """A pool that refuses work — the shape of a saturated/shut-down executor in prod."""
    ex = _InlineExecutor(fail_on_submit=True)
    monkeypatch.setattr(builders, "_EXECUTOR", ex)
    return ex


@pytest.fixture
def registry():
    """The real prompt registry (the bundled workbook)."""
    return runtime.get_registry()


# ═══════════════════════════════════════════════════════════════════════════════
# parse_control — the router's only sense organ
# ═══════════════════════════════════════════════════════════════════════════════


@pytest.mark.parametrize("envelope,expected", [
    ('{"coaching_path": "CH", "response_to_user": "x"}', "CH"),                    # snake, top
    ('{"coachingPath": "CH", "response_to_user": "x"}', "CH"),                     # camel, top
    ('{"context_update": {"coaching_path": "ch"}, "response_to_user": "x"}', "CH"),  # snake, nested
    ('{"context_update": {"coachingPath": "cbt"}, "response_to_user": "x"}', "CBT"),  # camel, nested
])
def test_the_routing_decision_survives_every_spelling_the_prompts_use(envelope, expected):
    """challenge_context picks the methodology for the WHOLE session. Different prompt
    revisions emit that decision under four different keys — snake/camel, top-level or
    nested in context_update. Miss one and the router silently falls back to CIM, so the
    user is coached with the wrong method for the entire session and nothing errors."""
    _, _, path = tools.parse_control(envelope)
    assert path == expected


@pytest.mark.parametrize("raw", [
    '{"coaching_path": "DBT", "response_to_user": "x"}',   # a method we don't implement
    '{"coaching_path": "", "response_to_user": "x"}',
    '{"context_update": "CH", "response_to_user": "x"}',   # context_update isn't a dict
    '{"response_to_user": "x"}',
])
def test_an_unroutable_path_is_reported_as_none_never_guessed(raw):
    """`None` means "no decision" and the router logs a visible CIM fallback. Anything
    else — a truthy garbage string — would be treated as a real routing choice."""
    _, _, path = tools.parse_control(raw)
    assert path is None


def test_a_fenced_json_block_is_read_not_shown_to_the_user():
    """Models wrap JSON in ```json fences constantly. Un-fenced, the whole block is
    prose to the parser and the user is shown ```json{"handoff_ready": true...}```."""
    reply, handoff, path = tools.parse_control(
        '```json\n{"response_to_user": "Say more about Dana.", '
        '"handoff_ready": true, "coaching_path": "CH"}\n```'
    )
    assert reply == "Say more about Dana."
    assert handoff is True and path == "CH"
    # And a bare fence with no language tag.
    reply2, _, _ = tools.parse_control('```\n{"response_to_user": "bare fence"}\n```')
    assert reply2 == "bare fence"


def test_an_envelope_buried_in_prose_is_still_read():
    """A chatty model prefixes its envelope with "Sure! Here you go:". The envelope is
    still the contract — extract it rather than shipping the preamble as the reply."""
    reply, _, path = tools.parse_control(
        'Sure! Here is my response:\n'
        '{"response_to_user": "What did Dana actually say?", "coaching_path": "CH"}\n'
        'Let me know if you want to go deeper.'
    )
    assert reply == "What did Dana actually say?"
    assert path == "CH"


@pytest.mark.parametrize("broken,expected_reply", [
    ('{"response_to_user": "trailing comma",}', "trailing comma"),
    ("{'response_to_user': 'single quotes', 'handoff_ready': true}", "single quotes"),
    ('{response_to_user: "unquoted key", "handoff_ready": true}', "unquoted key"),
    ('{"response_to_user": "unescaped \\"quote\\" inside"}', 'unescaped "quote" inside'),
])
def test_malformed_json_is_repaired_rather_than_dumped_on_the_user(broken, expected_reply):
    """Every one of these is real model output. Un-repaired, `_safe_json` returns None and
    parse_control's prose fallback prints the raw brace-soup into the chat bubble."""
    reply, _, _ = tools.parse_control(broken)
    assert reply == expected_reply


def test_an_envelope_truncated_at_the_token_ceiling_still_yields_its_text():
    """A model that hits max_tokens mid-JSON emits an envelope with no closing brace.
    The user must get the partial REPLY, not `{"response_to_user": "I hear how much` —
    which is what the raw-prose fallback hands them when the repair never runs.

    Regression: `_safe_json` only attempted a repair when it found BOTH braces
    (`0 <= start < end`), so the single most common malformed shape — the truncated one,
    the exact case json_repair exists for — skipped repair entirely and leaked."""
    truncated = '{"response_to_user": "I hear how much this is costing you'
    reply, handoff, _ = tools.parse_control(truncated)
    assert reply == "I hear how much this is costing you"
    assert "{" not in reply and "response_to_user" not in reply
    assert handoff is False  # a truncated envelope never asserts completion


def test_prose_with_a_stray_brace_is_still_delivered_as_prose():
    """The truncation repair must not eat a real prose reply: json_repair turns
    "…a thought {" into `{}`, and an empty envelope means a BLANK chat bubble — a worse
    failure than the leak it was added to fix."""
    reply, handoff, path = tools.parse_control("Here is a thought {")
    assert reply == "Here is a thought {"
    assert handoff is False and path is None


def test_user_text_key_precedence_is_most_specific_first():
    """Six keys can carry the user-facing text; different agents use different ones (CBT
    coaching emits under the bare `question` — without it that path replied with silence).
    When a model emits several, the most specific must win, or the user gets the agent's
    internal scratch text instead of its answer."""
    ladder = ["response_to_user", "next_question", "clarifying_question",
              "message", "question", "response"]
    assert list(tools._USER_TEXT_KEYS) == ladder, "the precedence order is the contract"
    for i, winner in enumerate(ladder):
        envelope = {key: f"text-from-{key}" for key in ladder[i:]}  # winner + all weaker keys
        reply, _, _ = tools.parse_control(json.dumps(envelope))
        assert reply == f"text-from-{winner}"


def test_a_blank_or_non_string_user_text_key_falls_through_to_the_next_one():
    """A key that is present but empty (or an object) is NOT an answer. Taking it because
    it exists is how the user gets an empty bubble while a real reply sits one key down."""
    reply, _, _ = tools.parse_control(
        '{"response_to_user": "   ", "next_question": {"text": "nested"}, '
        '"clarifying_question": "the real question"}'
    )
    assert reply == "the real question"


def test_a_control_only_envelope_never_dumps_its_json_to_the_user():
    """A pure handoff carries no user text. The reply must be "" — the graph then chains
    to the next stage in the same turn. Dumping the envelope would show the user the
    routing internals."""
    reply, handoff, path = tools.parse_control(
        '{"handoff_ready": true, "context_update": {"coachingPath": "CH"}}'
    )
    assert reply == ""
    assert handoff is True and path == "CH"


def test_plain_prose_still_reaches_the_user():
    """A prompt that ignores its JSON contract must not produce a dead turn."""
    reply, handoff, path = tools.parse_control("  Just prose, no envelope at all.  ")
    assert reply == "Just prose, no envelope at all."
    assert handoff is False and path is None
    assert tools.parse_control("") == ("", False, None)


@pytest.mark.parametrize("envelope,expected", [
    ('{"handoff_ready": true}', True),
    ('{"agent_complete": true}', True),
    ('{"status": "Complete"}', True),      # case-insensitive
    ('{"status": "handoff"}', True),
    ('{"status": "in_progress"}', False),
    ('{"handoff_ready": false}', False),
    ('{}', False),
])
def test_handoff_is_read_from_any_of_the_three_completion_signals(envelope, expected):
    _, handoff, _ = tools.parse_control(envelope)
    assert handoff is expected


@pytest.mark.parametrize("value,expected", [
    ("false", False), ("no", False), ("tru", False), ("pending", False), ("0", False),
    ("true", True), ("TRUE", True), ("yes", True), ("1", True),
])
def test_a_stringified_completion_flag_is_parsed_by_value_not_by_emptiness(value, expected):
    """`bool("false")` is True. A model that says `"handoff_ready": "false"` was therefore
    read as DONE and the router advanced the stage mid-arc — the user's coaching stage
    ended while they were still in it.

    This is not a hypothetical model quirk: json_repair, which salvages output truncated
    at the token ceiling, repairs a cut-off `"handoff_ready": tru` into the STRING "tru".
    An unrecognised string must therefore be FALSE — an ambiguous flag may never advance
    the session."""
    for key in ("handoff_ready", "agent_complete"):
        _, handoff, _ = tools.parse_control(json.dumps({key: value, "response_to_user": "x"}))
        assert handoff is expected, f"{key}={value!r}"


def test_a_json_array_is_not_an_envelope():
    """Only an object is a control envelope. A list has no control fields, so treating it
    as one would silently drop handoff/path; it falls back to prose."""
    reply, handoff, path = tools.parse_control('[{"response_to_user": "nope"}]')
    assert handoff is False and path is None
    assert reply  # surfaced as-is rather than swallowed


# ── the other parsers over the same envelope ─────────────────────────────────


def test_extract_variables_lifts_only_a_dict_block():
    """`variables_set` feeds user_context for the NEXT session's placeholders. A
    non-dict must be dropped, not merged — it would corrupt the whole context bag."""
    assert tools.extract_variables('{"variables_set": {"userRoleContext": "EM, 8 reports"}}') == {
        "userRoleContext": "EM, 8 reports"
    }
    assert tools.extract_variables('{"variables_set": ["not", "a", "dict"]}') == {}
    assert tools.extract_variables('{"no_variables": 1}') == {}
    assert tools.extract_variables("not json at all") == {}


def test_extract_progress_flattens_context_update_and_current_step():
    """Arc position is re-injected each turn so completion survives history truncation —
    the root cause of the "coach re-asks question 1 forever" loop. If this returns {},
    the node restarts its arc every time the window scrolls."""
    progress = tools.extract_progress(
        '{"context_update": {"behavioral_intake_complete": true, '
        '"current_question_number": 3}, "current_step": "  q3  "}'
    )
    assert progress == {
        "behavioral_intake_complete": True,
        "current_question_number": 3,
        "current_step": "q3",  # trimmed
    }
    # A non-dict context_update or a non-string step contributes nothing (and never raises).
    assert tools.extract_progress('{"context_update": "done", "current_step": 5}') == {}
    assert tools.extract_progress("prose") == {}


# ═══════════════════════════════════════════════════════════════════════════════
# profile_read — the context package
# ═══════════════════════════════════════════════════════════════════════════════


def test_captured_variables_survive_into_the_next_session(mongo, user_id):
    """The whole point of capturing variables_set: they must come BACK on the user's next
    session. This walks the real read path (dynamic-vars collection → user_context),
    including a dot-notation variable, which is stored nested in Mongo and would miss a
    flat lookup."""
    coll = mongo[_config.MONGO_BACKEND_DB][_config.MONGO_DYNAMIC_VARS_COLLECTION]
    coll.insert_one({
        "user_id": user_id,
        "coachingHistory": "two years with an external coach",
        "coaching_style_context": {"selected_style": "direct"},
    })

    ctx = tools.profile_read(user_id, "s-return")

    assert ctx["coachingHistory"] == "two years with an external coach"
    assert ctx["coaching_style_context"]["selected_style"] == "direct"
    # The fresh-user defaults still stand for everything that was never captured.
    assert ctx["userRepeatFresh"] == "fresh"
    assert ctx["committed_action"] == ""


def test_profile_read_never_raises_and_always_defines_the_gate_inputs(user_id):
    """Called on every turn, before anything else. It must degrade to a complete
    fresh-user package rather than raise — a raise here kills the turn at the door."""
    ctx = tools.profile_read(user_id, "s-1")
    assert ctx["checkinDue"] is False
    assert ctx["previousUserActions"] == []
    assert ctx["coaching_style_context"] == ""


# ═══════════════════════════════════════════════════════════════════════════════
# guardrails.build_system_prompt — the always-on wrapper
# ═══════════════════════════════════════════════════════════════════════════════


def test_no_placeholder_survives_into_the_prompt_sent_to_the_model():
    """A literal `{userName}` in the system prompt is a leak with two faces: the model
    parrots it back into the chat bubble, and a prompt's field-presence gate reads the
    non-empty token "{coaching_style_context}" as a real value — which is how a
    first-time user got treated as a returning one.

    Resolve what we can; BLANK what we cannot. Never pass a token through."""
    composed = guardrails.build_system_prompt(
        environment_prompt="Be warm. It is {Time}.",
        node_prompt=(
            "Coach {userName} ({userPosition}) at {orgId}. History: {coachingHistory}. "
            "Nothing maps to {a_token_with_no_source_anywhere}."
        ),
        coaching_path="CIM",
        user_context={"userName": "Dana", "userPosition": "EM", "orgId": "acme"},
        query_context={"user_message": "I avoid conflict"},
    )
    assert PLACEHOLDER_RE.findall(composed) == [], "a raw {token} reached the model"
    assert "Dana" in composed and "EM" in composed  # what we CAN resolve, we resolve
    assert "a_token_with_no_source_anywhere" not in composed


def test_a_placeholder_resolver_that_explodes_still_cannot_leak_a_token(caplog):
    """The last-resort sanitisation. If resolution itself throws (here: a context value
    whose str() raises — a Mongo document with something unstringifiable in it), the
    turn must still go out with every token BLANKED, not with the raw prompt."""

    class _Unstringifiable:
        def __str__(self):  # noqa: D105
            raise RuntimeError("this value cannot be rendered")

        __repr__ = __str__

    caplog.set_level(logging.ERROR, logger="cerebrozen.guardrails")
    composed = guardrails.build_system_prompt(
        environment_prompt="",
        node_prompt="Coach {userName} on {currentChallenge}.",
        coaching_path="CIM",
        user_context={"userName": _Unstringifiable()},
    )
    assert PLACEHOLDER_RE.findall(composed) == []
    assert "{userName}" not in composed and "{currentChallenge}" not in composed
    assert "Coach  on ." in composed  # blanked, and the rest of the prompt survived
    assert any(r.message == "guardrails.placeholder_resolution_failed" for r in caplog.records)


def test_the_environment_prompt_is_framed_as_constraints_and_the_node_prompt_as_identity():
    """The environment prompt literally says "You are environment_system_agent". Prepended
    as identity, the model answers AS the guardrail agent and emits an environment
    envelope — the coaching stage never speaks. Order and framing are the fix."""
    composed = guardrails.build_system_prompt(
        environment_prompt="ENV-RULES-HERE", node_prompt="NODE-ROLE-HERE",
        coaching_path=None, user_context={},
    )
    assert composed.index("OPERATING CONSTRAINTS") < composed.index("YOUR ROLE")
    assert composed.index("ENV-RULES-HERE") < composed.index("NODE-ROLE-HERE")
    assert "NOT your identity" in composed
    assert "you ARE the agent defined below" in composed
    # And the deterministic-flow note: the model must never pick its own next agent.
    assert "no loop-back" in composed and "next_agent" in composed


@pytest.mark.parametrize("path,marker", [
    ("CIM", "Coaching-in-the-Moment"),
    ("CBT", "CBT mode"),
    ("CH", "Capability mode"),
    (None, "warm, incisive professional coach"),
    ("nonsense", "warm, incisive professional coach"),  # unknown path → neutral identity
])
def test_the_identity_line_follows_the_coaching_path(path, marker):
    composed = guardrails.build_system_prompt("", "node", path, {})
    assert marker in composed


def test_the_coach_name_is_configuration_not_a_hardcoded_string():
    """A second client is a config change, not a fork. Every identity line must carry the
    configured brand — if one is hardcoded, the new client's coach introduces itself with
    the old client's name."""
    original = _config.BRAND_NAME
    try:
        _config.BRAND_NAME = "Zephyr"
        importlib.reload(guardrails)
        assert all("Zephyr" in line for line in guardrails.IDENTITY.values())
        composed = guardrails.build_system_prompt("", "node", "CH", {})
        assert "Zephyr" in composed
        assert original not in composed, f"the hardcoded {original!r} leaked through"
    finally:
        _config.BRAND_NAME = original
        importlib.reload(guardrails)
    assert all(original in line for line in guardrails.IDENTITY.values())


def test_the_context_bag_aliases_every_name_the_prompts_actually_use():
    """The API, Mongo and the prompts spell the same field three ways. The resolver does a
    flat lookup, so "user_name" never matches "userName" — each alias has to be built
    explicitly, or the prompt renders a blank where the user's role should be."""
    composed = guardrails.build_system_prompt(
        environment_prompt="",
        node_prompt=("[{user_name}][{org_id}][{user_level}][{user_role}]"
                     "[{userRoleContext}][{coachingPath}][{coaching_path}][{user_challenge}]"),
        coaching_path="CH",
        user_context={"name": "Dana", "orgId": "acme", "level": "senior",
                      "userPosition": "Engineering Manager"},
        query_context={"user_message": "I keep avoiding my skip-level"},
    )
    for expected in ("[Dana]", "[acme]", "[senior]", "[Engineering Manager]",
                     "[CH]", "[I keep avoiding my skip-level]"):
        assert expected in composed, f"{expected} missing from:\n{composed}"
    # userRoleContext has no value of its own → falls back to the role, never blank.
    assert "[Engineering Manager][CH][CH]" in composed


# ═══════════════════════════════════════════════════════════════════════════════
# state — the reducers routing reads
# ═══════════════════════════════════════════════════════════════════════════════


def test_captured_variables_accumulate_across_turns_instead_of_replacing():
    """`captured_variables` uses merge_dict as its reducer. With a replacing reducer, the
    turn that captures the coaching style would wipe the turn that captured the role."""
    assert gstate.merge_dict({}, {"a": 1}) == {"a": 1}
    assert gstate.merge_dict({"a": 1}, {}) == {"a": 1}          # an empty turn erases nothing
    assert gstate.merge_dict({"a": 1}, None) == {"a": 1}
    assert gstate.merge_dict({"a": 1, "b": 2}, {"b": 3, "c": 4}) == {"a": 1, "b": 3, "c": 4}
    # The reducer must not alias the caller's dict — a later turn would mutate history.
    existing = {"a": 1}
    assert gstate.merge_dict({}, existing) is not existing


def test_next_stage_walks_the_pipeline_and_clamps_at_the_end():
    """The linear spine. `close` is terminal: it must resolve to itself, not walk off the
    end of the list (an IndexError here dead-ends the session at its final turn)."""
    assert gstate.next_stage(gstate.STAGE_INTAKE) == gstate.STAGE_CHALLENGE
    assert gstate.next_stage(gstate.STAGE_CHALLENGE) == gstate.STAGE_CORE
    assert gstate.next_stage(gstate.STAGE_CLOSE) == gstate.STAGE_CLOSE


def test_only_the_closing_layer_can_end_a_session():
    """Every substantive stage — coaching, simulation, learning aid — funnels into the
    feedback layer, and ONLY feedback resolves to `close`. Keeping that in one place is
    what stops a routing bug in any single flow from terminating a session early."""
    for stage in (gstate.STAGE_CORE, gstate.STAGE_CH, gstate.STAGE_ROLEPLAY,
                  gstate.STAGE_SJT, gstate.STAGE_LEARNING_AID):
        assert gstate.next_stage(stage) == gstate.STAGE_FEEDBACK
    assert gstate.next_stage(gstate.STAGE_FEEDBACK) == gstate.STAGE_CLOSE


def test_a_corrupted_stage_recovers_into_coaching_and_says_so(caplog):
    """State was corrupted, or a stage was added with no successor rule. Recovering into
    the coaching slot is right (never terminate a session on a routing bug) — but it MUST
    be visible, or a systematic mis-route reads as a legitimate choice forever."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.graph")
    assert gstate.next_stage("a_stage_nobody_defined") == gstate.STAGE_CORE
    warned = [r for r in caplog.records if r.message == "state.next_stage_unknown_fallback_core"]
    assert warned, "the session silently recovered from a corrupted stage"
    assert warned[0].stage == "a_stage_nobody_defined"


# ═══════════════════════════════════════════════════════════════════════════════
# build_graph — the stage table and the checkpointer chain
# ═══════════════════════════════════════════════════════════════════════════════


def test_multipath_off_pins_every_turn_to_cim(monkeypatch):
    """The CIM-only validation switch. While it's off, a CH decision from
    challenge_context must be IGNORED — otherwise "CIM-only mode" quietly isn't."""
    monkeypatch.setattr(_config, "ENABLE_MULTIPATH", False)
    for path in ("CH", "CBT", "CIM", None):
        assert bg._coaching_route({"coaching_path": path}) == "core"
    assert bg._dispatch_stage({"stage": gstate.STAGE_CH, "coaching_path": "CH"}) == "core"


def test_a_missing_routing_decision_falls_back_to_cim_out_loud(caplog):
    """challenge_context emits no coaching_path roughly 1 turn in 6 (measured). The router
    then coaches the user with CIM for the whole session — a legitimate code path, so
    NOTHING errors. The warning is the only trace that a real decision was never made."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.graph")

    assert bg._coaching_route({"coaching_path": "not-a-method"}) == "core"
    assert bg._coaching_route({}) == "core"

    fallbacks = [r for r in caplog.records
                 if r.message == "route.coaching_path_unset_fallback_cim"]
    assert len(fallbacks) == 2, "a garbled coaching path was masked as a real choice"
    assert fallbacks[0].coaching_path == "not-a-method"


def test_an_unknown_stage_at_turn_entry_recovers_into_coaching_out_loud(caplog):
    """A garbled checkpoint (a renamed stage, a bad migration) must not dead-end the
    turn — but the recovery has to be visible, or every user on the stale stage is
    silently re-routed."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.graph")
    assert bg._dispatch_stage({"stage": "a_stage_from_an_older_deploy"}) == "core"
    assert [r.stage for r in caplog.records
            if r.message == "route.unknown_stage_fallback_coaching"] == \
        ["a_stage_from_an_older_deploy"]


def test_every_stage_in_the_table_is_a_node_the_compiled_graph_can_actually_reach():
    """The table and the graph are maintained separately. A stage whose node was never
    registered — or registered but never given an edge — dead-ends a live session."""
    compiled = bg.build_graph().get_graph()
    nodes, edges = set(compiled.nodes), compiled.edges
    destinations = {e.target for e in edges}
    for stage, node in bg.STAGE_NODE.items():
        resolved = bg._node_for_stage({"stage": stage, "coaching_path": "CIM"}, stage)
        assert resolved in nodes, f"{stage} resolves to unregistered node {resolved!r}"
        assert resolved in destinations, f"nothing routes to {resolved!r}"
    assert bg._node_for_stage({}, "a_stage_that_does_not_exist") is None


def test_a_gated_agent_switched_off_in_the_catalog_advances_the_session(registry):
    """Catalog toggles are how prompt engineers disable an agent with no deploy. A
    disabled stage must move the session FORWARD; re-running the previous stage or
    dead-ending would strand a user whose checkpoint sits on it.

    Uses the real registry's own override — the Catalog is the switch under test."""
    original = {s: registry.is_enabled(s) for s in (gstate.STAGE_CHECKIN, gstate.STAGE_LEARNING_AID)}
    try:
        registry.set_enabled(gstate.STAGE_CHECKIN, False)
        registry.set_enabled(gstate.STAGE_LEARNING_AID, False)
        assert bg._node_for_stage({}, gstate.STAGE_CHECKIN) == "challenge"
        # A disabled learning aid must NOT smuggle the session past the mandatory final
        # action check — routing it straight to `feedback` let sessions close with zero
        # saved actions.
        assert bg._node_for_stage({}, gstate.STAGE_LEARNING_AID) == "final_action_check"
        assert bg._node_for_stage(
            {"final_action_check_done": True}, gstate.STAGE_LEARNING_AID
        ) == "feedback"
    finally:
        for stage, was in original.items():
            registry.set_enabled(stage, was)


def test_a_textless_handoff_chains_on_but_a_reply_ends_the_turn():
    """One streamed reply per turn. A control-only handoff must chain to the next stage
    inside the same turn (else the user gets a dead turn); a stage that already spoke must
    stop (else the user gets two replies in one turn)."""
    textless = {"stage": gstate.STAGE_CHALLENGE, "handoff_ready": True, "reply_text": ""}
    assert bg._after_stage(textless) == "challenge"
    assert bg._after_stage({**textless, "reply_text": "already said something"}) == "end"
    assert bg._after_stage({**textless, "handoff_ready": False}) == "end"
    # An unknown stage mid-turn stops rather than inventing a destination.
    assert bg._after_stage({**textless, "stage": "bogus"}) == "end"


# ── the checkpointer fallback chain: Postgres → Mongo → SQLite → memory ───────
#
# Every fall-back is a REAL loss of durability, and the last one silently drops every
# session on restart. So each must be LOUD. These tests assert on the log record because
# the log IS the product here — a quiet fallback is precisely the bug (a misconfigured
# Mongo hiding behind a working-looking app whose sessions vanish on redeploy).


@pytest.fixture
def no_durable_stores(monkeypatch):
    """Neither Postgres nor Mongo configured — the dev-box baseline."""
    monkeypatch.delenv("POSTGRES_URL", raising=False)
    monkeypatch.setattr(_config, "POSTGRES_URL", "", raising=False)
    monkeypatch.setattr(_config, "MONGO_DB_URL", "")
    monkeypatch.setenv("SQLITE_CHECKPOINT_PATH", ":memory:")


def test_with_no_remote_store_configured_sessions_still_land_on_durable_sqlite(
    no_durable_stores, caplog
):
    caplog.set_level(logging.INFO, logger="cerebrozen.graph")
    saver = bg.get_checkpointer()
    assert type(saver).__name__ == "AsyncWrappedSqliteSaver"
    assert "checkpointer.sqlite" in [r.message for r in caplog.records]


async def test_the_sqlite_checkpointer_round_trips_a_session_through_the_async_api(
    no_durable_stores
):
    """The graph is driven async; SqliteSaver is sync, so every one of its methods is
    wrapped in a to_thread hop. Those wrappers are what LangGraph actually calls — a
    broken `aput` silently persists nothing (the session resumes from scratch next turn)
    and a broken `alist` makes the checkpoint history, which the edit/regenerate fork
    walks, unreadable."""
    from langgraph.checkpoint.base import empty_checkpoint

    saver = bg.get_checkpointer()
    cfg = {"configurable": {"thread_id": "t-1", "checkpoint_ns": ""}}

    assert await saver.aget_tuple(cfg) is None            # nothing stored yet
    assert [s async for s in saver.alist(cfg)] == []

    written = await saver.aput(cfg, empty_checkpoint(), {"source": "input", "step": 0}, {})
    await saver.aput_writes(written, [("history", {"role": "user", "content": "hi"})], "task-1")

    restored = await saver.aget_tuple(written)
    assert restored is not None, "the turn was written and could not be read back"
    assert [s async for s in saver.alist(cfg)], "the checkpoint history came back empty"


def test_a_misconfigured_postgres_falls_back_loudly_and_never_takes_the_app_down(
    monkeypatch, caplog
):
    """POSTGRES_URL set means Postgres was EXPECTED to serve checkpoints. Failing to
    reach it is a durability incident, so it logs at ERROR and names its fallback — it
    must not raise (that would take the whole app's startup with it).

    The failure is induced through a bad pool size rather than an unreachable host on
    purpose: psycopg_pool retries an unreachable host for its full 30-second timeout, so
    that version of this test would be network-dependent and take half a minute. Both
    land in the identical `except` branch. (That 30s stall is itself worth knowing about:
    get_checkpointer runs at import/startup.)"""
    monkeypatch.setenv("POSTGRES_URL", "postgresql://coach:pw@pg.internal:5432/cerebrozen")
    monkeypatch.setenv("CEREBROZEN_PG_POOL_MAX", "0")
    monkeypatch.setattr(_config, "MONGO_DB_URL", "")
    monkeypatch.setenv("SQLITE_CHECKPOINT_PATH", ":memory:")
    caplog.set_level(logging.ERROR, logger="cerebrozen.graph")

    saver = bg.get_checkpointer()

    assert type(saver).__name__ == "AsyncWrappedSqliteSaver"  # fell through, still durable
    failure = [r for r in caplog.records
               if r.message == "checkpointer.postgres_unavailable_falling_back"]
    assert failure, "a Postgres that was configured but unusable fell back SILENTLY"
    assert failure[0].levelno >= logging.ERROR
    assert failure[0].fallback == "mongo/sqlite"


def test_an_unreachable_mongo_falls_back_loudly(monkeypatch, caplog):
    """MONGO_DB_URL set and unreachable is the exact misconfiguration that used to hide
    behind a working-looking app whose sessions vanished on every redeploy."""
    monkeypatch.delenv("POSTGRES_URL", raising=False)
    monkeypatch.setattr(_config, "MONGO_DB_URL", "mongodb://127.0.0.1:1/cerebrozen")
    monkeypatch.setattr(_config, "MONGO_TIMEOUT_MS", 50)
    monkeypatch.setenv("SQLITE_CHECKPOINT_PATH", ":memory:")
    caplog.set_level(logging.ERROR, logger="cerebrozen.graph")

    saver = bg.get_checkpointer()

    assert type(saver).__name__ == "AsyncWrappedSqliteSaver"
    failure = [r for r in caplog.records
               if r.message == "checkpointer.mongo_unavailable_falling_back"]
    assert failure and failure[0].levelno >= logging.ERROR
    assert failure[0].fallback == "sqlite"


def test_the_memory_checkpointer_is_the_loudest_thing_in_the_chain(monkeypatch, caplog):
    """A MemorySaver means every session dies on the next restart. It is never acceptable
    in production, so reaching it must be an ERROR that says so in words — this is the
    one fallback a human has to notice."""
    monkeypatch.delenv("POSTGRES_URL", raising=False)
    monkeypatch.setattr(_config, "MONGO_DB_URL", "")
    # A path SQLite cannot open — a read-only volume / bad mount in a container.
    monkeypatch.setenv("SQLITE_CHECKPOINT_PATH", "/no/such/directory/cerebrozen.db")
    caplog.set_level(logging.ERROR, logger="cerebrozen.graph")

    from langgraph.checkpoint.memory import MemorySaver

    saver = bg.get_checkpointer()

    assert isinstance(saver, MemorySaver)
    last_resort = [r for r in caplog.records if r.message == "checkpointer.memory_no_durability"]
    assert last_resort, "the app fell back to a volatile store without saying a word"
    assert last_resort[0].levelno >= logging.ERROR
    assert last_resort[0].impact == "sessions are lost on restart"


# ═══════════════════════════════════════════════════════════════════════════════
# builders — the always-on agents that run OFF the request path
# ═══════════════════════════════════════════════════════════════════════════════
#
# The contract for everything below: it runs AFTER the user's reply has streamed, so a
# failure here is invisible to the user BY DESIGN. That design only holds if the failure
# is actually swallowed — which is what most of these tests are about.


def test_a_builder_whose_model_call_explodes_returns_empty_and_never_raises(
    mongo, user_id, llm, caplog
):
    """THE property. The builder runs after the reply has gone out; if it raises, the
    exception surfaces in the turn's thread and the user's `done` event never lands.
    It must degrade to "no cards this turn", loudly in the log and silently to the user."""
    llm(default=RuntimeError("the model is on fire"))
    caplog.set_level(logging.WARNING, logger="cerebrozen.builders")

    actions, insights, reply = builders._generate_and_store(
        user_id, "s-1", [{"role": "user", "content": "hi"}], "core_coaching_agent"
    )

    assert (actions, insights, reply) == ([], [], "")
    failed = [r for r in caplog.records if r.message == "builder.actions_insights_failed"]
    assert failed, "the builder swallowed its failure WITHOUT logging it — invisible in prod"
    assert "the model is on fire" in failed[0].error


def test_every_off_path_builder_swallows_a_model_that_explodes(mongo, user_id, llm, caplog):
    """The same guarantee for the other two background agents: the user-context model
    (session close) and the pattern agent (both invocations). None of them may raise."""
    llm(default=RuntimeError("boom"))
    caplog.set_level(logging.WARNING, logger="cerebrozen.builders")

    builders._run_context_builder(user_id, "s-1", [{"role": "user", "content": "x"}], {})
    assert builders._call_pattern_agent(user_id, "s-1", [], "in_session") is None
    assert builders.run_pattern_mirror(user_id, "s-1", []) == ""   # no mirror, no crash
    builders._run_pattern_write(user_id, "s-1", [])                 # no write, no crash

    logged = {r.message for r in caplog.records}
    assert {"builder.context_failed", "builder.pattern_failed"} <= logged
    # Nothing half-written: a failed builder must not leave a partial doc behind.
    assert builders.agentic.load(user_id).get("user_context_model") is None


def test_a_cleared_prompt_cell_stops_the_builder_instead_of_calling_the_model(
    mongo, user_id, llm, monkeypatch, registry, caplog
):
    """Prompts are business-editable. An emptied sheet must be a no-op, not a model call
    with an empty system prompt (which bills tokens and returns garbage)."""
    llm()
    caplog.set_level(logging.WARNING, logger="cerebrozen.builders")
    monkeypatch.setitem(registry._prompts, builders.ACTIONS_INSIGHTS_AGENT, "")
    monkeypatch.setitem(registry._prompts, builders.USER_CONTEXT_AGENT, "")
    monkeypatch.setitem(registry._prompts, builders.PATTERN_AGENT, "")

    assert builders._generate_and_store(user_id, "s", [], "core_coaching_agent") == ([], [], "")
    builders._run_context_builder(user_id, "s", [], {})
    assert builders._call_pattern_agent(user_id, "s", [], "in_session") is None

    logged = {r.message for r in caplog.records}
    assert {"builder.actions_prompt_missing", "builder.context_prompt_missing",
            "builder.pattern_prompt_missing"} <= logged


def test_a_missing_catalog_model_stops_the_builder(mongo, user_id, llm, monkeypatch,
                                                   registry, caplog):
    """The Catalog is the single source of truth for model selection — there is no
    default to fall back on. A blank model cell must abort the builder, not send the call
    with model=None."""
    llm()
    caplog.set_level(logging.ERROR, logger="cerebrozen.builders")
    for agent in (builders.ACTIONS_INSIGHTS_AGENT, builders.USER_CONTEXT_AGENT,
                  builders.PATTERN_AGENT):
        monkeypatch.setitem(registry._models, agent, "")

    assert builders._generate_and_store(user_id, "s", [], "core_coaching_agent") == ([], [], "")
    builders._run_context_builder(user_id, "s", [], {})
    assert builders._call_pattern_agent(user_id, "s", [], "background_write") is None

    missing = [r for r in caplog.records if r.message == "builder.model_missing"]
    assert {r.agent for r in missing} == {builders.ACTIONS_INSIGHTS_AGENT,
                                          builders.USER_CONTEXT_AGENT, builders.PATTERN_AGENT}


# ── shape normalisation ───────────────────────────────────────────────────────


@pytest.mark.parametrize("raw,actions,insights", [
    # combined shape — what the action agent emits when it detects both
    ({"actions": {"actions": [{"full_text": "a"}]}, "insights": {"insights": [{"insight_title": "i"}]}},
     1, 1),
    ({"actions": [{"full_text": "a"}]}, 1, 0),           # actions-only
    ({"insights": [{"insight_title": "i"}]}, 0, 1),      # insights-only
    ({"actions": None, "insights": None}, 0, 0),         # explicit nulls
    ({"actions": {"actions": None}}, 0, 0),              # nested null
    ({}, 0, 0),
    ("not a dict at all", 0, 0),
    ({"actions": ["a string, not an action"]}, 0, 0),    # non-dict members dropped
])
def test_every_output_shape_the_action_agent_emits_normalises(raw, actions, insights):
    """One prompt, four output shapes across revisions. A shape we fail to read is a turn
    that silently produces no cards."""
    a, i = builders.extract_actions_insights(raw)
    assert (len(a), len(i)) == (actions, insights)


@pytest.mark.parametrize("raw,expected", [
    ({"roi_metric": "Influence"}, ["Influence"]),          # prompt emits a string…
    ({"roi_metric": ["Influence", " Trust "]}, ["Influence", "Trust"]),
    ({"roi_metric": ""}, []),
    ({"roi_metric": None}, []),
    ({"roi_metrics": ["Influence"], "roi_metric": "stale"}, ["Influence"]),  # legacy dropped
    ({"roi_metrics": "Influence"}, ["Influence"]),         # …a newer one emits a bare string
    ({}, []),
])
def test_the_development_area_is_always_stored_as_a_list(raw, expected):
    """The UI assigns MULTIPLE Development Areas per action, so `roi_metrics` is a list —
    but the prompt emits a singular string. Store the string and the UI iterates its
    characters."""
    assert builders._normalize_action_roi({"full_text": "x", **raw})["roi_metrics"] == expected
    assert "roi_metric" not in builders._normalize_action_roi({"full_text": "x", **raw})


def test_cards_carry_the_id_the_save_api_resolves_them_by():
    """The UI renders a card and then calls save/delete on it in the SAME session. Without
    `id` bound to the stored action_id, the save call has nothing to address."""
    shaped = builders._shape_for_payload([
        {"action_id": "abc123", "full_text": "Talk to Dana"},
        {"full_text": "No id — derive a stable one"},
        {"full_text": "", "action_id": "ghost"},   # no text → no card
    ])
    assert [a["id"] for a in shaped] == [
        "abc123", builders.agentic.stable_id("No id — derive a stable one")
    ]
    insights = builders._shape_insights_for_payload([
        {"insight_title": "T", "insight_body": "B"}, {"insight_body": "orphan"},
    ])
    assert len(insights) == 1 and insights[0]["id"] == builders.agentic.stable_id("T")


# ── the actions/insights builder, end to end against the real store ──────────


def test_generated_actions_are_persisted_and_returned_as_cards(mongo, agentic_coll, user_id, llm):
    """The happy path, through the real store: the model's output becomes a stored action
    with a stable id, and the same action comes back shaped for the UI."""
    client = llm()
    actions, insights, reply = builders.run_actions_insights_for_node(
        user_id, "s-1", [{"role": "user", "content": "I avoid Dana"}],
        "core_coaching_agent", user_context={"userName": "Sam"}, coaching_path="CIM",
    )

    assert reply == builders.ACTION_CARD_TITLE
    assert actions[0]["full_text"].startswith("Ask Dana")
    assert actions[0]["roi_metrics"] == ["Influence"]      # coerced from roi_metric
    assert actions[0]["id"] == actions[0]["action_id"]
    assert insights[0]["insight_title"].startswith("Avoidance")

    stored = agentic_coll.find_one({"user_id": user_id})
    assert [a["full_text"] for a in stored["actions"]] == [actions[0]["full_text"]]
    assert stored["actions"][0]["session_id"] == "s-1"
    assert stored["actions"][0]["agent_name"] == "core_coaching_agent"
    assert stored["actions"][0]["status"] == "active"      # not yet saved by the user

    # The guardrails wrapped the builder's own prompt, and the verb for this agent
    # ("explore" for core coaching) was resolved into it — not left as a literal token.
    call = client.call_for(builders.ACTIONS_INSIGHTS_AGENT)
    assert "OPERATING CONSTRAINTS" in call.system_prompt
    assert PLACEHOLDER_RE.findall(call.system_prompt) == []
    assert "I avoid Dana" in call.user_prompt                # the transcript went in


def test_the_action_card_title_is_only_sent_when_a_card_will_actually_render(
    mongo, user_id, llm
):
    """Later beats in a session re-extract from the same history and regenerate actions
    the user has already been shown. Those dedup away — and if the title were keyed off
    the raw extraction, the user would get a bare "Suggested Action. Please review and
    save" bubble with ZERO cards under it."""
    llm()
    history = [{"role": "user", "content": "I avoid Dana"}]

    first = builders.run_actions_insights_for_node(user_id, "s-1", history, "core_coaching_agent")
    second = builders.run_actions_insights_for_node(user_id, "s-1", history, "learning_aid_agent")

    assert first[0] and first[2] == builders.ACTION_CARD_TITLE
    assert second[0] == [] and second[2] == "", "a beat with zero new cards shipped a card title"


def test_the_action_cap_applies_before_persistence_not_after(mongo, agentic_coll, user_id, llm):
    """QA 2026-07-10: a CH session persisted 8 actions and showed 5. The three the user
    never saw then leaked out later through the final-action-check carousel. Cap at the
    store, not at the payload."""
    llm(default={**_GOOD_ENVELOPE, "actions": [
        {"full_text": f"Action number {n}", "roi_metric": "Influence"} for n in range(5)
    ]})

    shown, _, _ = builders.run_actions_insights_for_node(
        user_id, "s-1", [], "CH_coaching_agent", max_actions=1
    )

    assert len(shown) == 1
    stored = agentic_coll.find_one({"user_id": user_id})["actions"]
    assert len(stored) == 1, f"the store kept {len(stored)} actions the user never saw"
    assert stored[0]["full_text"] == shown[0]["full_text"]


def test_the_builder_dedups_against_saved_actions_only(mongo, agentic_coll, user_id, llm):
    """Only what the user CONFIRMED is permanent. An action they ignored (or deleted) must
    be free to resurface in a future session — the prompt is told about the saved ones so
    it doesn't repeat them, and told nothing about the rest."""
    agentic_coll.insert_one({"user_id": user_id, "actions": [
        {"full_text": "SAVED-ACTION", "status": "saved", "session_id": "old"},
        {"full_text": "IGNORED-ACTION", "status": "active", "session_id": "old"},
        {"full_text": "DELETED-ACTION", "status": "deleted", "session_id": "old"},
    ]})
    client = llm()

    builders.run_actions_insights_for_node(user_id, "s-new", [], "core_coaching_agent")

    sent = json.loads(client.call_for(builders.ACTIONS_INSIGHTS_AGENT).user_prompt)
    assert sent["action_response"] == ["SAVED-ACTION"]
    assert "IGNORED-ACTION" not in sent["action_response"]
    assert "DELETED-ACTION" not in sent["action_response"]
    # Field names are the prompt's input contract — rename them and the model can no
    # longer see the dedup inputs, so it re-surfaces cards the user already has.
    assert {"conversation_history", "agent_type", "action_response", "insight_history"} == set(sent)


def test_the_foreground_collector_is_gated_to_its_trigger_agents(mongo, user_id, llm, monkeypatch):
    """`collect_actions_insights` fires only for agents in BUILDER_TRIGGER_AGENTS. Running
    it after (say) a simulation turn would attribute the action to the wrong agent and
    surface cards mid-rehearsal."""
    llm()
    state = {"active_node": "core_coaching_agent", "history": [], "user_context": {},
             "coaching_path": "CIM"}

    monkeypatch.setattr(_config, "BUILDER_TRIGGER_AGENTS", {"core_coaching_agent"})
    assert builders.collect_actions_insights(user_id, "s-1", state)  # trigger agent → cards
    assert builders.collect_actions_insights(user_id, "s-1", state, "role_play_agent") == []

    monkeypatch.setattr(_config, "ENABLE_BUILDERS", False)
    assert builders.collect_actions_insights(user_id, "s-2", state) == []
    assert builders.collect_actions_insights("", "s-2", state) == []  # no user → nothing


def test_the_off_path_dispatch_persists_without_returning_anything(
    mongo, agentic_coll, user_id, llm, inline_builders, monkeypatch
):
    """The request-path entry: fired after the reply has streamed, so it returns None and
    its whole product is what lands in the store."""
    llm()
    monkeypatch.setattr(_config, "BUILDER_TRIGGER_AGENTS", {"core_coaching_agent"})
    state = {"active_node": "core_coaching_agent", "history": [{"role": "user", "content": "x"}],
             "user_context": {}, "coaching_path": "CIM"}

    assert builders.dispatch_actions_insights(user_id, "s-1", state) is None
    assert agentic_coll.find_one({"user_id": user_id})["actions"]

    # Gates: a non-trigger agent, no user, and the kill switch each dispatch nothing.
    inline_builders.submitted.clear()
    builders.dispatch_actions_insights(user_id, "s-1", state, "sjt_simulation_agent")
    builders.dispatch_actions_insights("", "s-1", state)
    monkeypatch.setattr(_config, "ENABLE_BUILDERS", False)
    builders.dispatch_actions_insights(user_id, "s-1", state)
    assert inline_builders.submitted == []


# ── the non-LLM dispatches ────────────────────────────────────────────────────


def test_intake_variables_are_persisted_off_path(mongo, agentic_coll, user_id, inline_builders,
                                                 monkeypatch):
    """A single field-level upsert, but still dispatched async: a flaky-Mongo write must
    never delay the user's reply."""
    builders.dispatch_intake_vars(user_id, {"userRoleContext": "EM, 8 reports"})
    assert agentic_coll.find_one({"user_id": user_id})["intake_vars"]["userRoleContext"] == \
        "EM, 8 reports"

    inline_builders.submitted.clear()
    builders.dispatch_intake_vars(user_id, {})      # nothing captured → nothing written
    builders.dispatch_intake_vars("", {"a": 1})
    monkeypatch.setattr(_config, "ENABLE_BUILDERS", False)
    builders.dispatch_intake_vars(user_id, {"a": 1})
    assert inline_builders.submitted == []


def test_dynamic_variables_are_persisted_with_the_provenance_of_the_turn(
    mongo, user_id, inline_builders, monkeypatch
):
    """Provenance (session + stage + turn) is what makes a captured variable auditable in
    CloudWatch. The request_id must be read in the CALLING thread — read it inside the
    background task and it belongs to whatever request that thread last served."""
    from app.request_context import request_id as _rid

    token = _rid.set("req-abc")
    try:
        builders.dispatch_dynamic_vars(
            user_id, "s-1", {"coachingHistory": "two years"},
            stage="coaching_intake_agent", turn_seq=3,
        )
    finally:
        _rid.reset(token)

    doc = mongo[_config.MONGO_BACKEND_DB][_config.MONGO_DYNAMIC_VARS_COLLECTION].find_one(
        {"user_id": user_id}
    )
    assert doc["coachingHistory"] == "two years"
    prov = doc["_provenance"]["coachingHistory"]
    assert prov["session_id"] == "s-1"
    assert prov["stage"] == "coaching_intake_agent"
    assert prov["turn_seq"] == 3
    assert prov["request_id"] == "req-abc"

    inline_builders.submitted.clear()
    builders.dispatch_dynamic_vars(user_id, "s-1", {})
    monkeypatch.setattr(_config, "ENABLE_BUILDERS", False)
    builders.dispatch_dynamic_vars(user_id, "s-1", {"a": 1})
    assert inline_builders.submitted == []


def test_closing_a_checkin_batch_is_idempotent(mongo, agentic_coll, user_id, inline_builders):
    """BRD R3: a checked-in action never comes back for check-in. The write uses $addToSet
    so a harness retry (or a duplicated handoff) can't double-record — and the scheduler
    excludes these sessions forever."""
    builders.dispatch_checkin_complete(user_id, ["s-1", "s-2"])
    builders.dispatch_checkin_complete(user_id, ["s-2", ""])   # retry + a junk id

    assert sorted(agentic_coll.find_one({"user_id": user_id})["checkin_complete_sessions"]) == \
        ["s-1", "s-2"]

    inline_builders.submitted.clear()
    builders.dispatch_checkin_complete(user_id, [])
    builders.dispatch_checkin_complete(user_id, [""])
    builders.dispatch_checkin_complete("", ["s-3"])
    assert inline_builders.submitted == []


# ── the user-context model (session close) ────────────────────────────────────


@pytest.mark.parametrize("shape", [
    {"user_context_model": {"dimension_1": "drives toward autonomy"}},   # wrapped
    {"dimension_1": "drives toward autonomy"},                            # bare
])
def test_the_user_context_model_is_persisted_in_either_output_shape(
    mongo, agentic_coll, user_id, llm, shape
):
    """Read back next session by profile_read as the continuity data the coach opens
    with. The builder accepts the wrapped and the bare shape because both prompt
    revisions exist."""
    llm(script={builders.USER_CONTEXT_AGENT: shape})
    builders._run_context_builder(user_id, "s-1", [{"role": "user", "content": "hi"}], {})
    stored = agentic_coll.find_one({"user_id": user_id})["user_context_model"]
    assert stored["dimension_1"] == "drives toward autonomy"


def test_the_context_builder_sees_the_transcript_and_the_prior_model_with_no_raw_tokens(
    mongo, agentic_coll, user_id, llm
):
    """Its inputs arrive as PLACEHOLDERS in its own sheet. If they don't resolve, the
    builder rebuilds the model from nothing every session and continuity is lost — and a
    RAG token in a background sheet must NOT fire a retrieval off the request path."""
    agentic_coll.insert_one({
        "user_id": user_id,
        "user_context_model": {"dimension_1": "prior"},
        "actions": [{"full_text": "SAVED-ONE", "status": "saved"}],
    })
    client = llm(script={builders.USER_CONTEXT_AGENT: {"user_context_model": {"d": "new"}}})

    builders._run_context_builder(
        user_id, "s-1",
        [{"role": "user", "content": "I ducked the conversation again"}],
        {"userName": "Dana"},
    )

    prompt = client.call_for(builders.USER_CONTEXT_AGENT).system_prompt
    assert "I ducked the conversation again" in prompt   # {session_transcript}
    assert "prior" in prompt                             # {previousUserContext}
    assert "SAVED-ONE" in prompt                         # {previousUserActions}
    assert PLACEHOLDER_RE.findall(prompt) == [], "an unresolved token reached the builder"


def test_a_context_value_that_cannot_be_rendered_degrades_the_prompt_not_the_build(
    mongo, agentic_coll, user_id, llm
):
    """The builder resolves its placeholders itself, so a Mongo document carrying
    something whose str() blows up would kill the whole session-close build. It must fall
    back to blanking every token — a prompt with less context, never a lost user-context
    model, and never a raw {token} in the prompt either."""

    class _Unstringifiable:
        def __str__(self):  # noqa: D105
            raise RuntimeError("unrenderable")

        __repr__ = __str__

    client = llm(script={builders.USER_CONTEXT_AGENT: {"user_context_model": {"d": "built"}}})

    builders._run_context_builder(
        user_id, "s-1", [{"role": "user", "content": "x"}],
        {"coachingHistory": _Unstringifiable()},   # a real token in that sheet
    )

    assert agentic_coll.find_one({"user_id": user_id})["user_context_model"] == {"d": "built"}
    assert PLACEHOLDER_RE.findall(client.call_for(builders.USER_CONTEXT_AGENT).system_prompt) == []


def test_an_empty_user_context_model_is_never_written_over_a_good_one(
    mongo, agentic_coll, user_id, llm, caplog
):
    """A model that returns `{}` (or prose) must leave the previous session's context
    model untouched. Overwriting it with nothing destroys the user's whole continuity."""
    agentic_coll.insert_one({"user_id": user_id, "user_context_model": {"dimension_1": "keep me"}})
    caplog.set_level(logging.WARNING, logger="cerebrozen.builders")
    llm(script={builders.USER_CONTEXT_AGENT: {}})

    builders._run_context_builder(user_id, "s-1", [], {})

    assert agentic_coll.find_one({"user_id": user_id})["user_context_model"] == \
        {"dimension_1": "keep me"}
    assert "builder.context_empty" in {r.message for r in caplog.records}


def test_the_context_builder_dispatch_is_gated(mongo, user_id, llm, inline_builders, monkeypatch):
    llm()
    builders.dispatch_context_builder(user_id, "s-1", [{"role": "user", "content": "x"}], {})
    assert inline_builders.submitted == ["_run_context_builder"]

    inline_builders.submitted.clear()
    builders.dispatch_context_builder("", "s-1", [], {})
    monkeypatch.setattr(_config, "ENABLE_BUILDERS", False)
    builders.dispatch_context_builder(user_id, "s-1", [], {})
    assert inline_builders.submitted == []


# ── the pattern agent (two invocations, one sheet) ────────────────────────────


def test_the_in_session_mirror_is_surfaced_and_recorded(mongo, agentic_coll, user_id, llm):
    """The reflect beat after a simulation: ONE pattern, mirrored back to the user as its
    own turn. The text is what the user reads, so an empty return means a dead beat."""
    client = llm(script={builders.PATTERN_AGENT: _PATTERN_ENVELOPE})

    mirror = builders.run_pattern_mirror(
        user_id, "s-1", [{"role": "user", "content": "I said nothing in the room"}]
    )

    assert mirror == "You go quiet exactly when the stakes rise."
    assert agentic_coll.find_one({"user_id": user_id})["pattern_mirror"] == mirror
    # The mode is passed in the USER turn (the sheet reads it at call time), and the
    # transcript reaches the prompt through its own {conversation_history} placeholder.
    call = client.call_for(builders.PATTERN_AGENT)
    assert "invocation_mode: in_session" in call.user_prompt
    assert "mirror block" in call.user_prompt
    assert "I said nothing in the room" in call.system_prompt


def test_a_null_pattern_signal_is_a_valid_outcome_not_a_failure(mongo, agentic_coll, user_id,
                                                                llm, caplog):
    """No pattern cleared the Potential gate. The beat produces no mirror and writes
    nothing — inventing one would put a fabricated psychological read in front of a user."""
    caplog.set_level(logging.INFO, logger="cerebrozen.builders")
    llm(script={builders.PATTERN_AGENT: {"context_update": {"pattern_mirror_output": ""}}})

    assert builders.run_pattern_mirror(user_id, "s-1", []) == ""
    assert (agentic_coll.find_one({"user_id": user_id}) or {}).get("pattern_mirror") is None
    assert "builder.pattern_mirror_null" in {r.message for r in caplog.records}


def test_the_mirror_is_skipped_entirely_when_builders_are_off(mongo, user_id, llm, monkeypatch):
    client = llm()
    monkeypatch.setattr(_config, "ENABLE_BUILDERS", False)
    assert builders.run_pattern_mirror(user_id, "s-1", []) == ""
    assert builders.run_pattern_mirror("", "s-1", []) == ""
    assert client.calls == [], "the kill switch still paid for a model call"


def test_the_first_session_sends_a_null_ic_profile_and_later_ones_send_the_stored_model(
    mongo, agentic_coll, user_id, llm
):
    """{ic_profile} is the cumulative Pattern Intelligence Model. On session one there
    isn't one — the sheet expects the string "null", not an empty token."""
    client = llm(script={builders.PATTERN_AGENT: _PATTERN_ENVELOPE})

    builders._call_pattern_agent(user_id, "s-1", [], "background_write")
    assert "null" in client.call_for(builders.PATTERN_AGENT).system_prompt

    agentic_coll.update_one({"user_id": user_id},
                            {"$set": {"ic_profile": '{"clusters": ["avoidance"]}'}}, upsert=True)
    client2 = llm(script={builders.PATTERN_AGENT: _PATTERN_ENVELOPE})
    builders._call_pattern_agent(user_id, "s-2", [], "background_write")
    prompt = client2.call_for(builders.PATTERN_AGENT).system_prompt
    assert "avoidance" in prompt
    assert PLACEHOLDER_RE.findall(prompt) == []


def test_a_pattern_model_stored_as_a_document_is_re_serialised_for_the_prompt(
    mongo, agentic_coll, user_id, llm
):
    """Older writes put the model in Mongo as a nested DOCUMENT, not a JSON string. The
    prompt's {ic_profile} needs text — hand it a dict and the placeholder renders Python's
    "key: value; key: value" soup, which the sheet cannot parse as a prior model."""
    agentic_coll.insert_one({"user_id": user_id,
                             "ic_profile": {"clusters": [{"name": "avoidance"}]}})
    client = llm(script={builders.PATTERN_AGENT: _PATTERN_ENVELOPE})

    builders._call_pattern_agent(user_id, "s-1", [], "background_write")

    prompt = client.call_for(builders.PATTERN_AGENT).system_prompt
    assert '{"clusters": [{"name": "avoidance"}]}' in prompt


def test_the_cumulative_pattern_model_is_persisted_as_json(mongo, agentic_coll, user_id, llm,
                                                           inline_builders, caplog):
    """background_write at session close. A dict is serialised; an empty/absent profile is
    a no-op that says so, rather than clobbering the accumulated model with nothing."""
    llm(script={builders.PATTERN_AGENT: _PATTERN_ENVELOPE})
    builders.dispatch_pattern_write(user_id, "s-1", [{"role": "user", "content": "x"}])

    stored = agentic_coll.find_one({"user_id": user_id})["ic_profile"]
    assert json.loads(stored) == _PATTERN_ENVELOPE["ic_profile"]

    caplog.set_level(logging.WARNING, logger="cerebrozen.builders")
    llm(script={builders.PATTERN_AGENT: {"ic_profile": None}})
    builders._run_pattern_write(user_id, "s-2", [])
    assert agentic_coll.find_one({"user_id": user_id})["ic_profile"] == stored  # untouched
    assert "builder.pattern_write_empty" in {r.message for r in caplog.records}


def test_a_pattern_model_returned_as_a_string_is_stored_verbatim(mongo, agentic_coll, user_id,
                                                                 llm, inline_builders):
    llm(script={builders.PATTERN_AGENT: {"ic_profile": "  cluster: avoidance  "}})
    builders._run_pattern_write(user_id, "s-1", [])
    assert agentic_coll.find_one({"user_id": user_id})["ic_profile"] == "cluster: avoidance"


def test_a_store_that_is_down_at_session_close_is_a_clean_no_op(user_id, llm, caplog):
    """No `mongo` fixture here: the document store is simply unavailable, which is exactly
    what a session close looks like during a Mongo incident. The builder still runs, the
    write returns False, and nothing is logged as a success — a "pattern_write_done" line
    for a write that never landed is worse than no line at all."""
    llm(script={builders.PATTERN_AGENT: _PATTERN_ENVELOPE})
    caplog.set_level(logging.INFO, logger="cerebrozen.builders")

    builders._run_pattern_write(user_id, "s-1", [])   # must not raise

    assert "builder.pattern_write_done" not in {r.message for r in caplog.records}


def test_the_pattern_write_dispatch_is_gated(mongo, user_id, llm, inline_builders, monkeypatch):
    llm()
    monkeypatch.setattr(_config, "ENABLE_BUILDERS", False)
    builders.dispatch_pattern_write(user_id, "s-1", [])
    builders.dispatch_pattern_write("", "s-1", [])
    assert inline_builders.submitted == []


# ── KV-cache prewarm (offline backend only) ───────────────────────────────────


def test_prewarm_loads_the_next_stage_prompt_into_the_model_cache(llm, inline_builders):
    """Only fired when the stage CHANGED. A local model caches ONE prefix at a time, so
    prewarming mid-stage would EVICT the prefix the user is about to reuse and make their
    next turn slower, not faster. What gets warmed must be the exact prompt the next turn
    will send — same guardrails, same resolved placeholders — or the cache never hits."""
    warmed: list[tuple[str, str]] = []
    llm(prewarm=lambda system_prompt, model: warmed.append((system_prompt, model)))

    builders.dispatch_prewarm(gstate.STAGE_CORE, {"userName": "Dana"}, "CIM")

    assert len(warmed) == 1
    system_prompt, model = warmed[0]
    assert "OPERATING CONSTRAINTS" in system_prompt
    assert "Coaching-in-the-Moment" in system_prompt          # the CIM identity
    assert PLACEHOLDER_RE.findall(system_prompt) == []
    assert model


def test_prewarm_is_a_no_op_on_a_provider_that_has_no_cache(llm, inline_builders):
    """OpenAI caches server-side. Calling a prewarm that doesn't exist would raise inside
    a background thread on every single stage change."""
    client = llm()                       # no `prewarm` attribute
    builders.dispatch_prewarm(gstate.STAGE_CORE, {}, "CIM")
    assert client.calls == []            # nothing sent, nothing raised


def test_a_failing_prewarm_never_touches_the_turn(llm, inline_builders, caplog):
    """It runs while the user is reading the previous reply. It may fail; it may not be
    heard doing it."""
    caplog.set_level(logging.WARNING, logger="cerebrozen.builders")

    def _explode(system_prompt, model):
        raise RuntimeError("model not loaded")

    llm(prewarm=_explode)
    builders.dispatch_prewarm(gstate.STAGE_CORE, {}, "CIM")   # must not raise

    failed = [r for r in caplog.records if r.message == "prewarm.failed"]
    assert failed and failed[0].stage == gstate.STAGE_CORE


def test_prewarm_is_gated_by_its_flag_and_an_empty_stage(llm, inline_builders, monkeypatch):
    warmed: list = []
    llm(prewarm=lambda system_prompt, model: warmed.append(model))

    builders.dispatch_prewarm("", {}, "CIM")                   # no next stage to warm
    monkeypatch.setattr(_config, "ENABLE_PREWARM", False)
    builders.dispatch_prewarm(gstate.STAGE_CORE, {}, "CIM")
    assert warmed == []

    monkeypatch.setattr(_config, "ENABLE_PREWARM", True)
    builders.dispatch_prewarm("a_stage_with_no_prompt_sheet", {}, "")
    assert warmed == [], "prewarmed an empty prompt — that would evict a live prefix"


# ═══════════════════════════════════════════════════════════════════════════════
# engine — the per-turn run
# ═══════════════════════════════════════════════════════════════════════════════


@pytest.fixture
def engine(mongo, monkeypatch):
    """A real engine over a real compiled graph and a real (in-memory) SQLite
    checkpointer. `mongo` is active, so the checkpointer chain would try to reach it —
    1ms is enough for it to fail and fall through without slowing the suite."""
    monkeypatch.setattr(_config, "MONGO_TIMEOUT_MS", 1)
    from app.graph.engine import CereBroZenEngine

    return CereBroZenEngine()


def _play(engine, user_id, session_id, turns=25, **kw):
    """Drive a session until it closes. Returns every turn's result."""
    out = []
    for i in range(turns):
        tokens: list[str] = []
        result = engine.run_turn_stream(
            user_id=user_id, session_id=session_id, bot_name="CereBroZen",
            user_message=f"turn {i}: here is what happened", on_token=tokens.append, **kw
        )
        result["_streamed"] = "".join(tokens)
        out.append(result)
        if result["stage"] == gstate.STAGE_CLOSE:
            break
    return out


def test_a_whole_session_reaches_a_natural_close_and_fires_the_close_builders(
    engine, agentic_coll, user_id, llm, inline_builders
):
    """The full journey, one stage per turn, through the real graph. At the natural close
    (the feedback layer firing EndOfConversation) BOTH session-close builders must run —
    the user-context model and the cumulative pattern model. They are what the NEXT
    session opens with; if the close doesn't fire them, every session starts from zero."""
    llm(script=_SESSION_SCRIPT)
    session_id = f"s-{uuid.uuid4().hex[:8]}"

    turns = _play(engine, user_id, session_id)

    assert turns[-1]["stage"] == gstate.STAGE_CLOSE, \
        f"the session never closed; it stalled on {turns[-1]['stage']}"
    assert turns[0]["is_first_turn"] is True
    assert all(t["is_first_turn"] is False for t in turns[1:])
    assert all(t["safety_flag"] == "ok" for t in turns)
    # Every turn either speaks to the user or is the terminal close.
    assert all(t["response_to_user"].strip() for t in turns[:-1])

    doc = agentic_coll.find_one({"user_id": user_id})
    assert doc["user_context_model"], "the session closed without building a user-context model"
    assert json.loads(doc["ic_profile"]) == _PATTERN_ENVELOPE["ic_profile"]
    assert "_run_context_builder" in inline_builders.submitted
    assert "_run_pattern_write" in inline_builders.submitted


def test_action_cards_ship_inline_with_the_catalogue_needed_to_render_them(
    engine, agentic_coll, user_id, llm, inline_builders
):
    """Cards are generated in-turn and ride the `done` payload so the client renders them
    immediately. The Development-Area picker on the card needs the catalogue — shipped
    only on the turns that actually carry cards."""
    llm(script=_SESSION_SCRIPT)
    turns = _play(engine, user_id, f"s-{uuid.uuid4().hex[:8]}")

    carded = [t for t in turns if t["actions"]]
    assert carded, "the whole session produced no action cards"
    assert all("available_roi_metrics" in t for t in carded)
    assert all(t["available_roi_metrics"] for t in carded)
    assert all("available_roi_metrics" not in t for t in turns if not t["actions"])

    card = carded[0]["actions"][0]
    assert card["id"] and card["full_text"] and card["roi_metrics"] == ["Influence"]
    # Cards are cleared on the following turn — a stale card must never be re-sent.
    assert turns[-1]["actions"] == []


def test_the_mirror_beat_reaches_the_user_as_its_own_turn(engine, user_id, llm, inline_builders):
    """The pattern mirror is a user-facing reflect beat, not telemetry. It must arrive as
    a normal reply on the turn the pattern node is the terminal stage."""
    llm(script=_SESSION_SCRIPT)
    turns = _play(engine, user_id, f"s-{uuid.uuid4().hex[:8]}")

    mirrored = [t for t in turns if t["pattern_mirror"]]
    assert mirrored, "the reflect beat never surfaced a mirror"
    assert mirrored[0]["response_to_user"] == _PATTERN_ENVELOPE["context_update"][
        "pattern_mirror_output"
    ]
    assert mirrored[0]["active_node"] == gstate.STAGE_PATTERN
    assert all(not t["pattern_mirror"] for t in turns if t["active_node"] != gstate.STAGE_PATTERN)


def test_captured_variables_are_persisted_the_moment_the_model_returns_them(
    engine, mongo, user_id, llm, inline_builders
):
    """Not at the end of the turn — the moment the node emits them. A turn that errors
    later must not lose the variables the user just gave us."""
    llm(script=_SESSION_SCRIPT)
    session_id = f"s-{uuid.uuid4().hex[:8]}"

    engine.run_turn_stream(user_id=user_id, session_id=session_id, bot_name="CereBroZen",
                           user_message="I manage eight engineers")

    doc = mongo[_config.MONGO_BACKEND_DB][_config.MONGO_DYNAMIC_VARS_COLLECTION].find_one(
        {"user_id": user_id}
    )
    assert doc["coachingHistory"] == "two years with an external coach"
    assert doc["_provenance"]["coachingHistory"]["session_id"] == session_id
    # And the legacy intake-vars store still gets them (other readers query it).
    assert builders.agentic.load(user_id)["intake_vars"]["coachingHistory"]


def test_the_turn_streams_its_reply_and_reports_the_flow(engine, user_id, llm, inline_builders):
    """The SSE contract: tokens stream as they arrive, each node announces itself, and the
    flow view gets a structured `end` event per node carrying the routing it took."""
    llm(script=_SESSION_SCRIPT)
    tokens, statuses, node_events = [], [], []

    result = engine.run_turn_stream(
        user_id=user_id, session_id=f"s-{uuid.uuid4().hex[:8]}", bot_name="CereBroZen",
        user_message="I keep avoiding a hard conversation",
        on_token=tokens.append, on_status=statuses.append, on_node=node_events.append,
    )

    assert "".join(tokens).strip(), "nothing streamed to the user"
    assert statuses, "no node announced itself — the UI shows a dead spinner"

    # Nodes emit `start` themselves; the engine emits `end` from each node's state delta.
    ends = [e for e in node_events if e["phase"] == "end"]
    assert [e for e in node_events if e["phase"] == "start"], "no node-start events"
    assert [e["seq"] for e in ends] == list(range(1, len(ends) + 1)), "the flow view lost its order"
    assert {"safety", "profile_read", "intake"} <= {e["node"] for e in ends}
    intake = next(e for e in ends if e["node"] == "intake")
    assert intake["replied"] is True and intake["handoff_ready"] is True
    assert intake["stage"] == gstate.STAGE_CHALLENGE, "the flow view reports the branch taken"
    assert result["prompt_tokens"] > 0 and result["cost_usd"] > 0


def test_a_dead_builder_pool_cannot_break_the_users_turn(
    engine, agentic_coll, user_id, llm, dead_executor, registry, caplog
):
    """Every off-path dispatch is wrapped by the engine because the pool ITSELF can fail —
    a saturated or shut-down ThreadPoolExecutor raises on submit(), before any builder
    code runs and therefore outside every builder's own try/except. The user's reply has
    already streamed by then; the turn must still complete and hand back its `done`.

    Seeded with an overdue action so the check-in dispatch is in play too — that is the
    fifth and last off-path hand-off in a turn."""
    agentic_coll.insert_one({"user_id": user_id, "actions": [{
        "full_text": "Ask Dana for 15 minutes", "status": "saved",
        "session_id": "s-last-week", "session_date": "2020-01-01",
    }]})
    was_enabled = registry.is_enabled(gstate.STAGE_CHECKIN)
    registry.set_enabled(gstate.STAGE_CHECKIN, True)
    caplog.set_level(logging.WARNING, logger="cerebrozen.engine")
    try:
        llm(script=_SESSION_SCRIPT)
        turns = _play(engine, user_id, f"s-{uuid.uuid4().hex[:8]}")
    finally:
        registry.set_enabled(gstate.STAGE_CHECKIN, was_enabled)

    assert turns[-1]["stage"] == gstate.STAGE_CLOSE, "a failing builder pool killed the session"
    assert all(t["response_to_user"].strip() for t in turns[:-1])
    # And it was not silent: the engine logged every dispatch it could not schedule.
    logged = {r.message for r in caplog.records}
    assert {"engine.dynamic_vars_dispatch_failed", "engine.intake_vars_dispatch_failed",
            "engine.checkin_complete_dispatch_failed",
            "engine.natural_close_dispatch_failed"} <= logged


def test_a_crisis_message_never_reaches_the_coaching_stages(engine, user_id, llm):
    """The safety interrupt is the first edge in the graph. No LLM stage may run."""
    client = llm(script=_SESSION_SCRIPT)
    result = engine.run_turn_stream(
        user_id=user_id, session_id=f"s-{uuid.uuid4().hex[:8]}", bot_name="CereBroZen",
        user_message="I want to kill myself",
    )
    assert result["safety_flag"] == "crisis"
    assert result["active_node"] == "safe_response"
    assert client.calls == [], "a coaching agent ran on a crisis turn"


def test_the_standalone_action_checkin_is_seeded_at_entry(engine, user_id, llm, inline_builders):
    """Tapping "Action Check-In" on a card starts a self-contained mini-session. No
    coaching edge routes to it, so the entry seed IS the routing — miss it and the user
    lands in the normal intake flow instead of the check-in on their action."""
    llm(script=_SESSION_SCRIPT)
    result = engine.run_turn_stream(
        user_id=user_id, session_id=f"s-{uuid.uuid4().hex[:8]}", bot_name="CereBroZen",
        user_message="I did it", on_token=lambda t: None,
        checkin_action={"action_item": "Ask Dana for 15 minutes",
                        "action_outcome": "The disagreement is aired"},
    )
    assert result["active_node"] == gstate.STAGE_ACTION_CHECKIN
    assert result["response_to_user"].strip()


def test_a_redis_outage_costs_a_cache_hit_not_the_session(engine, user_id, llm, monkeypatch,
                                                          inline_builders):
    """The Redis "seen" marker is a latency cache, not a source of truth. When Redis is
    down the engine must fall back to the authoritative checkpoint read — misjudging a
    continuing turn as a FIRST turn restarts the user's session from intake, mid-session.
    And a failing write-back must not take the turn down with it."""
    import app.stores.redis_state as redis_state

    llm(script=_SESSION_SCRIPT)
    session_id = f"s-{uuid.uuid4().hex[:8]}"
    assert engine.is_first_turn(session_id) is True

    def _down(*_a, **_kw):
        raise ConnectionError("redis is unreachable")

    monkeypatch.setattr(redis_state, "is_session_seen", _down)
    monkeypatch.setattr(redis_state, "mark_session_seen", _down)

    result = engine.run_turn_stream(user_id=user_id, session_id=session_id, bot_name="CereBroZen",
                                    user_message="first", on_token=lambda t: None)
    assert result["response_to_user"].strip(), "a Redis outage killed the turn"

    assert engine.is_first_turn(session_id) is False, \
        "a Redis outage restarted the user's session from scratch"


def test_a_client_callback_that_throws_cannot_break_the_turn(engine, user_id, llm,
                                                             inline_builders):
    """`on_node` is a telemetry hook the caller supplies. Telemetry never breaks the thing
    it watches — a raising callback must cost the flow view, not the user's reply."""
    llm(script=_SESSION_SCRIPT)

    def _explode(_event):
        raise ValueError("the flow-view consumer is broken")

    result = engine.run_turn_stream(
        user_id=user_id, session_id=f"s-{uuid.uuid4().hex[:8]}", bot_name="CereBroZen",
        user_message="I keep avoiding a hard conversation",
        on_token=lambda t: None, on_node=_explode,
    )
    assert result["response_to_user"].strip()
    assert result["active_node"]


def test_the_repeat_user_checkin_permanently_closes_the_batch_it_checked_in(
    engine, agentic_coll, user_id, llm, inline_builders, registry
):
    """BRD R3, one-time-only: once the check-in has closed the loop on a prior session's
    actions, that batch may NEVER be surfaced for check-in again. The engine marks it
    from the CODE-computed session ids (not ids echoed by the model), and $addToSet keeps
    a retry idempotent — otherwise the user is asked about the same action every week."""
    agentic_coll.insert_one({"user_id": user_id, "actions": [{
        "full_text": "Ask Dana for 15 minutes",
        "status": "saved",
        "session_id": "s-last-week",
        "session_date": "2020-01-01",      # long overdue → the 7-day rule fires
    }]})
    was_enabled = registry.is_enabled(gstate.STAGE_CHECKIN)
    registry.set_enabled(gstate.STAGE_CHECKIN, True)
    try:
        llm(script=_SESSION_SCRIPT)
        turns = _play(engine, user_id, f"s-{uuid.uuid4().hex[:8]}", turns=3)
    finally:
        registry.set_enabled(gstate.STAGE_CHECKIN, was_enabled)

    assert any(t["active_node"] == gstate.STAGE_CHECKIN for t in turns), \
        "an overdue action never triggered the repeat-user check-in"
    assert agentic_coll.find_one({"user_id": user_id})["checkin_complete_sessions"] == \
        ["s-last-week"]


def test_session_state_returns_the_checkpointed_transcript(engine, user_id, llm, inline_builders):
    """What the session-close context builder reads. An empty state here means the close
    builders get no transcript and the user-context model is built from nothing."""
    llm(script=_SESSION_SCRIPT)
    session_id = f"s-{uuid.uuid4().hex[:8]}"
    assert engine.session_state(session_id) == {}

    engine.run_turn_stream(user_id=user_id, session_id=session_id, bot_name="CereBroZen",
                           user_message="I avoid conflict with Dana")

    state = engine.session_state(session_id)
    assert state["user_id"] == user_id
    assert any("Dana" in h["content"] for h in state["history"])
    assert state["user_context"]


def test_the_last_turn_can_be_forked_and_re_run_with_an_edited_message(
    engine, user_id, llm, inline_builders
):
    """Edit-and-regenerate. The fork point is the checkpoint at the END of the previous
    turn — fork from the wrong one and the edited message is appended to the history it
    was meant to REPLACE, so the model sees the user say both things."""
    llm(script=_SESSION_SCRIPT)
    session_id = f"s-{uuid.uuid4().hex[:8]}"
    assert engine.find_edit_fork(session_id) is None, "there is no turn to edit yet"

    engine.run_turn_stream(user_id=user_id, session_id=session_id, bot_name="CereBroZen",
                           user_message="I avoid conflict")

    # Editing the FIRST message is the special case: there is no previous turn to fork
    # from, so the fork is that turn's own input checkpoint and it re-runs as a first turn.
    first_fork, is_first = engine.find_edit_fork(session_id)
    assert first_fork and is_first is True

    engine.run_turn_stream(user_id=user_id, session_id=session_id, bot_name="CereBroZen",
                           user_message="a typo I want to fix")

    fork_id, first = engine.find_edit_fork(session_id)
    assert first is False and fork_id

    engine.run_turn_stream(
        user_id=user_id, session_id=session_id, bot_name="CereBroZen",
        user_message="what I actually meant to say", from_checkpoint_id=fork_id,
        is_first_turn_override=False,
    )

    transcript = [h["content"] for h in engine.session_state(session_id)["history"]]
    assert "what I actually meant to say" in transcript
    assert "a typo I want to fix" not in transcript, "the edited turn was appended, not replaced"


def test_a_checkpointer_that_cannot_be_read_still_lets_the_turn_run(
    engine, user_id, llm, inline_builders, monkeypatch
):
    """The entry-stage probe (used only to decide whether to prewarm) reads the
    checkpoint. A store that is briefly unreadable must cost the user a prewarm, not
    their turn."""
    llm(script=_SESSION_SCRIPT)

    def _boom(self, *a, **kw):
        raise RuntimeError("checkpoint store unreadable")

    monkeypatch.setattr(type(engine.graph), "get_state", _boom)

    result = engine.run_turn_stream(
        user_id=user_id, session_id=f"s-{uuid.uuid4().hex[:8]}", bot_name="CereBroZen",
        user_message="hello", is_first_turn_override=True, on_token=lambda t: None,
    )
    assert result["response_to_user"].strip()
    assert result["active_node"]
