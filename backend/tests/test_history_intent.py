"""The history-question classifier that forces get_weekly_insights (WC-149 kin).

`wants_history_tool` decides when the agent's tool call stops being the
model's choice: the eval suite measured "how has my mood been lately?"
answered from vibes on ~half of sampled turns, a prompt exemplar failed to
hold in-suite, and tool_choice is the lever that cannot flake. That power is
why this table exists — a classifier that force-calls a tool must be tight,
and both directions are goldens: the history questions it must catch, and the
feeling-statements it must NEVER catch, because forcing insights over
"I feel low lately" would override the prompt's own anxiety -> activity rule.
"""
from app.agent.graph import wants_history_tool

CATCH = [
    "how has my mood been lately?",
    "what did my sleep look like this week",
    "How have I been doing?",
    "am I sleeping better than last month?",
    "show me my patterns",
    "what's my progress been like",
]

FREE = [
    "I feel really low lately",
    "I'm anxious and can't sleep",
    "my mood is terrible right now",       # a state, not a history question
    "help me sleep better tonight",
    "I want to journal",
    "what is box breathing?",
]


def test_history_questions_force_the_tool():
    missed = [t for t in CATCH if not wants_history_tool(t)]
    assert not missed, f"history questions the classifier missed: {missed}"


def test_feeling_statements_stay_free():
    grabbed = [t for t in FREE if wants_history_tool(t)]
    assert not grabbed, (
        f"feeling-statements wrongly forced to insights (this overrides the "
        f"anxiety->activity rule): {grabbed}"
    )
