"""The context package (tools.profile_read) is a deterministic, no-LLM tool, and it
is the ONLY thing standing between a prompt's {placeholders} and the user.

Its contract: every placeholder a prompt can reference resolves to *something*, even
for a brand-new user with no Mongo doc — because an unresolved token either leaks to
the user as a literal "{coachingHistory}" or, worse, reads as a non-empty value to a
prompt's field-presence gate (which is how a first-timer got treated as a repeat user).
"""
from app.graph.guardrails import build_system_prompt
from app.graph.tools import crisis_screen, parse_control, profile_read
from app.rag.placeholders import PLACEHOLDER_RE


def test_fresh_user_gets_a_complete_context_package():
    ctx = profile_read("brand-new-user", "s-1")

    # Repeat-user gate inputs are always defined, even with no Mongo doc behind them.
    assert ctx["userRepeatFresh"] == "fresh"
    assert ctx["previousUserActions"] == []
    assert ctx["checkinDue"] is False

    # Placeholders with no data source for a fresh user still resolve (to empty),
    # so no prompt can render a literal {token}.
    for key in ("timeAvailable", "session_goal", "presenting_issue_summary",
                "pastConversation", "coaching_style_context", "committed_action",
                "idp_competencies", "currentPhase"):
        assert key in ctx, f"{key} must be defaulted for a fresh user"


def test_profile_read_never_raises_without_mongo():
    """Mongo is absent in tests; the read must degrade to a fresh-user package."""
    ctx = profile_read("", "")
    assert isinstance(ctx, dict)
    assert ctx["userRepeatFresh"] == "fresh"


def test_unresolved_placeholders_never_reach_the_model():
    """The guardrail layer resolves context + RAG tokens and BLANKS anything left
    over — a raw {token} must never survive into the composed system prompt."""
    node_prompt = (
        "Coach {userName} on {currentChallenge}. "
        "Their history: {coachingHistory}. Unknown: {a_token_with_no_source}."
    )
    composed = build_system_prompt(
        environment_prompt="Be warm. Session at {Time}.",
        node_prompt=node_prompt,
        coaching_path="CIM",
        user_context=profile_read("fresh-user", "s-2"),
        query_context={"user_message": "I avoid conflict"},
    )
    leftovers = PLACEHOLDER_RE.findall(composed)
    assert leftovers == [], f"unresolved placeholders leaked: {leftovers}"


def test_guardrails_apply_the_path_specific_identity():
    ctx = profile_read("u-identity", "s-3")
    ch = build_system_prompt("", "node prompt", "CH", ctx)
    cim = build_system_prompt("", "node prompt", "CIM", ctx)
    assert "Capability mode" in ch
    assert "Coaching-in-the-Moment" in cim


def test_parse_control_extracts_the_user_text_and_control_fields():
    """Every agent speaks through a JSON control envelope; the graph routes on the
    fields parse_control lifts out of it."""
    reply, handoff, path = parse_control(
        '{"response_to_user": "What matters most here?", '
        '"handoff_ready": true, "coaching_path": "CH"}'
    )
    assert reply == "What matters most here?"
    assert handoff is True
    assert path == "CH"


def test_parse_control_survives_a_non_json_reply():
    """A model that answers in prose must still produce a usable reply rather than
    an empty turn."""
    reply, handoff, _ = parse_control("Just plain prose, no JSON here.")
    assert reply.strip()
    assert handoff is False


def test_crisis_screen_is_a_pure_rule_no_llm():
    assert crisis_screen("I want to kill myself") == "crisis"
    assert crisis_screen("I want to nail this presentation") == "ok"


def test_system_prompt_prefix_is_stable_across_turns():
    """The composed system prompt must be BYTE-IDENTICAL across turns of a session.

    It is ~21,000 tokens. If any part of it changes per turn, the LLM prompt cache can
    never hit and the whole thing is re-encoded every single turn — and a local/offline
    model (Ollama KV prefix reuse) becomes unusable outright.

    This regressed once already: {Time} resolved to datetime.now() with MICROSECOND
    precision, 130 tokens in, so every turn had a unique prefix and ~0% was cacheable.
    """
    import time as _t

    from app.graph.guardrails import build_system_prompt
    from app.graph.runtime import get_registry

    reg = get_registry()
    ctx = profile_read("stable-prefix-user", "s-1")

    def compose(msg):
        return build_system_prompt(
            reg.environment, reg.get("core_coaching_agent"), "CIM", ctx,
            {"user_message": msg},
        )

    first = compose("what I said on turn one")
    _t.sleep(1.1)                                   # cross a wall-clock second
    second = compose("something completely different on turn two")

    assert first == second, (
        "the system prompt changed between turns — the prompt cache is now dead. "
        "Something volatile (a timestamp? the user message?) leaked into the system "
        "prompt; volatile values belong in the user turn, not the cached prefix."
    )
