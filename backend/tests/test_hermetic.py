"""The suite must never reach a real model provider.

`services/ai.complete` selects its provider from **key presence alone** — it does
not consult `TESTING` — so before `conftest` blanked the keys, anyone with a
working `backend/.env` ran the entire suite against live OpenAI. That cost real
money per run, made results non-deterministic, and broke the two tests written to
pin the "everything degrades without keys" contract, so that contract was only
ever verified on CI (where the keys happen to be blank) and looked broken on
every developer machine.

The blanking lives in `conftest` and is therefore easy to delete by accident.
These tests are the tripwire: they assert the *effect*, not the mechanism, so
they still fire if someone changes how provider selection works rather than
removing the two lines.
"""
import os

from app.core.config import settings
from app.services import ai


def test_no_model_provider_is_configured_under_the_suite():
    """The property that matters, stated in the app's own terms."""
    assert settings.ai_provider == "none", (
        f"the suite resolved a live provider ({settings.ai_provider}) — tests will "
        "bill a real account and stop being deterministic"
    )
    assert settings.ai_enabled is False


def test_the_agent_is_unavailable_too():
    """Oracle needs a key as well as its flag, so blanking must take it down."""
    assert settings.oracle_available is False


async def test_completion_degrades_to_none_instead_of_calling_out():
    """The degrade path every caller is written against, exercised directly."""
    assert await ai.complete("system", "prompt") is None
    assert await ai.complete_json("system", "prompt") is None


def test_an_exported_key_in_the_developers_shell_loses_to_the_suite():
    """`conftest` sets rather than setdefaults, and this is why.

    A developer who exports OPENAI_API_KEY for a script in the same shell must
    not silently re-arm the network path for the whole suite.
    """
    assert os.environ["OPENAI_API_KEY"] == ""
    assert os.environ["ANTHROPIC_API_KEY"] == ""
