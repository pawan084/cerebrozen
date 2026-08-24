"""The companion's voice contract (WC-144/145), pinned at the prompt layer.

The behaviour these rules produce is judged by the opt-in eval suite
(RUN_LLM_TESTS=1); what CAN be pinned hermetically is that every reply path
actually carries the rules — the failure mode being one persona getting a
style fix while the other two keep the tic. `RESPONSE_STYLE` exists precisely
so this cannot drift per-prompt; these tests make removing it from any path a
red build rather than a quiet regression.
"""
from app.agent.graph import SYSTEM_PROMPT
from app.api.routes.chat import _CALM_GUIDE, _SCIENTIFIC
from app.services import prompts

ALL_PERSONAS = {
    "oracle": SYSTEM_PROMPT,
    "calm": _CALM_GUIDE,
    "scientific": _SCIENTIFIC,
}


def test_every_persona_carries_the_shared_style_block():
    for name, prompt in ALL_PERSONAS.items():
        assert prompts.RESPONSE_STYLE in prompt, f"{name} lost RESPONSE_STYLE"


def test_length_is_calibrated_not_capped():
    # The old flat cap answered a long, careful message with a fortune cookie.
    assert "Match your length to theirs" in prompts.RESPONSE_STYLE
    for name, prompt in ALL_PERSONAS.items():
        assert "Keep replies to 1" not in prompt, f"{name} still carries the flat cap"


def test_the_banned_openers_are_named():
    # Named literally so the model is told the exact tics, and so this test
    # fails if someone "simplifies" the list away.
    for tic in ("'I hear you'", "'It sounds like'", "'That sounds'", "'It seems", "'I understand'"):
        assert tic in prompts.RESPONSE_STYLE
    # The judged suite's first run caught the model swapping a banned opener
    # for its unlisted sibling ("It seems like"), so the rule now bans the
    # CLASS — this pin keeps the closing clause that makes it a class ban.
    assert "any variant" in prompts.RESPONSE_STYLE


def test_ai_disclaimers_are_unvolunteered_never_concealed():
    # Both halves matter: the tic goes, the honesty stays. A rule that said
    # "never mention being an AI" would contradict the product's own disclosure
    # banner and the claims map.
    assert "Don't volunteer disclaimers" in prompts.RESPONSE_STYLE
    assert "if asked directly, answer honestly" in prompts.RESPONSE_STYLE


def test_oracle_kept_its_tool_and_crisis_rules():
    # The style block replaced only the length sentence — the agentic and
    # safety halves of the oracle prompt must survive it.
    assert "ACT WITH TOOLS" in SYSTEM_PROMPT
    assert "do NOT name hotline numbers" in SYSTEM_PROMPT
