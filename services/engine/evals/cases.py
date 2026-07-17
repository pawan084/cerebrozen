"""Golden cases — the behaviours the graph ROUTES on.

This is deliberately NOT a coaching-quality benchmark. It tests the contract: does the
agent emit the structured fields the deterministic graph needs to route correctly? A
warm, insightful reply that omits `coaching_path` still sends every session down the
wrong path, silently.

Each case is `(stage, user_message, context, expectations)`. Expectations are checked
against the SAME parser production uses (`tools.parse_control`), against a system prompt
composed by the SAME code (`guardrails.build_system_prompt`) — so a pass here means the
real pipeline works, not a mock of it.

Add a case every time a routing bug escapes. That is what makes this a regression gate
rather than a vanity metric.
"""

from __future__ import annotations

from typing import Any, Dict, List

# ── challenge_context: the single highest-stakes routing decision in the system ──
# It picks the coaching path. Get it wrong and the user gets the wrong methodology for
# the whole session; omit it and EVERY session silently falls back to CIM.
#
# CH  = building a capability over time (competency, development plan, phases, months)
# CIM = a live, present-tense problem to work through right now
_PATH_CASES: List[Dict[str, Any]] = [
    # --- clearly CH: capability development over time ---
    dict(id="path-ch-1", stage="challenge_context_agent",
         message="I want to develop strategic influence as a capability over the next few months.",
         expect={"coaching_path": "CH"}),
    dict(id="path-ch-2", stage="challenge_context_agent",
         message="I need a structured development plan to grow into a director role.",
         expect={"coaching_path": "CH"}),
    dict(id="path-ch-3", stage="challenge_context_agent",
         message="Help me build my competency in leading through influence, over time.",
         expect={"coaching_path": "CH"}),
    dict(id="path-ch-4", stage="challenge_context_agent",
         message="I'd like to work on my leadership capability across several phases, not a quick fix.",
         expect={"coaching_path": "CH"}),

    # --- clearly CIM: a live problem, now ---
    dict(id="path-cim-1", stage="challenge_context_agent",
         message="I have a hard conversation with my manager tomorrow and I'm dreading it.",
         expect={"coaching_path": "CIM"}),
    dict(id="path-cim-2", stage="challenge_context_agent",
         message="I froze in the leadership meeting this morning and said nothing.",
         expect={"coaching_path": "CIM"}),
    dict(id="path-cim-3", stage="challenge_context_agent",
         message="My co-founder blindsided me in front of the team yesterday and I'm still angry.",
         expect={"coaching_path": "CIM"}),

    # --- AMBIGUOUS: the honest test. Published benchmarks put open-weight value-accuracy
    #     well below 100%, and this is where that shows up. We assert only that a VALID
    #     path is chosen — not which one — because a human coach could argue either.
    dict(id="path-ambig-1", stage="challenge_context_agent",
         message="I keep avoiding difficult conversations. I want to get better at this.",
         expect={"coaching_path": "ANY"}),
    dict(id="path-ambig-2", stage="challenge_context_agent",
         message="My delegation is poor and it's been a problem for a year.",
         expect={"coaching_path": "ANY"}),
]

# ── every conversational agent must return usable user-facing text ──
# An empty reply is a dead turn: the user sees nothing. This has happened in production
# (an agent spent its whole budget on hidden reasoning and returned "").
_REPLY_CASES: List[Dict[str, Any]] = [
    dict(id=f"reply-{stage.split('_')[0]}", stage=stage,
         message="I keep avoiding a hard conversation with my manager.",
         expect={"non_empty_reply": True})
    for stage in (
        "coaching_intake_agent",
        "challenge_context_agent",
        "core_coaching_agent",
        "CH_coaching_agent",
        "feedback_mood_capture_agent",
    )
]

# ── no raw placeholder may reach the user ──
# A literal "{userName}" in a reply is user-visible breakage, and worse, a literal
# "{coaching_style_context}" reads as a non-empty VALUE to a prompt's field-presence gate.
_LEAK_CASES: List[Dict[str, Any]] = [
    dict(id="leak-core", stage="core_coaching_agent",
         message="I avoid conflict with my manager.",
         expect={"no_placeholder_leak": True}),
    dict(id="leak-intake", stage="coaching_intake_agent",
         message="Hello.",
         expect={"no_placeholder_leak": True}),
]

# ── instruction adherence: one question at a time ───────────────────────────
#
# NOT a coaching-quality benchmark either — quality is taste, and PROMPTS_SPEC.md is
# explicit that the method and the words belong to a qualified coach whose sign-off is a
# release condition. Nothing here substitutes for that.
#
# What this DOES test is objective: the prompts state "One question at a time, always.
# Never stack questions. Ask. Wait. Respond." verbatim, in eight agents AND in the
# always-on `environment` wrapper that is composed into the top of every call. So a turn
# that stacks three questions violates a rule the product wrote down twice. Counting "?"
# is not taste.
#
# It exists to test one specific hypothesis (docs/PROMPTS_SPEC.md §"The budget, measured"):
# **does a big prompt dilute a small model's attention to its own instructions?** The
# agents state the same rule at very different depths, which makes them a natural
# experiment — no new prompts needed, just the ones that ship:
#
#     action_checkin_agent      2,531 tok   rule at   9% depth
#     feedback_mood_capture     4,156 tok   rule at  21%
#     core_coaching_agent       8,992 tok   rule at  26%
#     coaching_intake_agent     6,712 tok   rule at  46%
#     pattern_agent             5,270 tok   rule at  83%
#     CH_coaching_agent        16,576 tok   rule at  88%   <- deepest, in the biggest
#
# If size/depth dilutes attention, CH breaks the rule and action_checkin does not — and
# the gap should widen on an 8B local model versus the cloud. If adherence is flat, the
# ≤8K budget loses its last candidate justification (the other three are already measured
# and dead).
_CRAFT_CASES: List[Dict[str, Any]] = [
    dict(id=f"oneq-{stage.split('_')[0]}", stage=stage,
         message="I keep avoiding a hard conversation with my manager and I don't know why.",
         expect={"one_question": True, "non_empty_reply": True})
    for stage in (
        "action_checkin_agent",
        "feedback_mood_capture_agent",
        "core_coaching_agent",
        "coaching_intake_agent",
        "pattern_agent",
        "CH_coaching_agent",
    )
]

CASES: List[Dict[str, Any]] = _PATH_CASES + _REPLY_CASES + _LEAK_CASES + _CRAFT_CASES


def default_context() -> Dict[str, Any]:
    """A realistic repeat-user profile — the same shape profile_read produces."""
    return {
        "userName": "Alex",
        "name": "Alex",
        "userRoleContext": "Engineering manager, 6 direct reports",
        "coachingHistory": "One prior engagement",
        "coachability_score": 78,
        "coaching_style_preference": "stretching",
        "userRepeatFresh": "repeat",
        "language": "english",
    }
